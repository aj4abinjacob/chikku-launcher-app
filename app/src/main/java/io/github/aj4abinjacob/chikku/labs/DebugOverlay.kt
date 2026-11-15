package io.github.aj4abinjacob.chikku.labs

import android.graphics.Color
import android.content.Context
import android.util.TypedValue
import android.view.Choreographer
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.WorkspaceState
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.core.model.settings.SettingsRepository
import io.github.aj4abinjacob.chikku.core.model.WorkspaceRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

/** Shared by HOME and its synthetic settings sample; this creates no frame observer. */
internal fun createDebugOverlayText(context: Context): TextView = TextView(context).apply {
    setTextColor(Color.WHITE)
    setBackgroundColor(0xB0000000.toInt())
    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
    val pad = (6 * resources.displayMetrics.density).toInt()
    setPadding(pad, pad, pad, pad)
    isClickable = false
    isFocusable = false
    visibility = View.GONE
}

/**
 * Labs "debug overlay" on HOME: grid, dock, page and item counts, screen size and frame timing.
 * It never shows app labels, packages or anything else that identifies installed apps. The frame
 * callback runs only while the overlay is enabled and HOME is started.
 */
class DebugOverlay private constructor(private val activity: ComponentActivity) {

    private val text = createDebugOverlayText(activity)

    private var settings: LauncherSettings? = null
    private var pages = 0
    private var items = 0

    // Frame timing over a rolling window, reset every refresh.
    private var lastFrameNanos = 0L
    private var frameCount = 0
    private var frameTotalNanos = 0L
    private var slowFrames = 0
    private var lastAverageMs = 0.0
    private var lastRefreshNanos = 0L
    private var running = false

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            if (lastFrameNanos != 0L) {
                val delta = frameTimeNanos - lastFrameNanos
                frameTotalNanos += delta
                frameCount++
                if (delta > SLOW_FRAME_NANOS) slowFrames++
            }
            lastFrameNanos = frameTimeNanos
            if (frameTimeNanos - lastRefreshNanos > REFRESH_NANOS) {
                lastAverageMs = if (frameCount > 0) frameTotalNanos / frameCount / 1_000_000.0 else 0.0
                frameCount = 0
                frameTotalNanos = 0
                lastRefreshNanos = frameTimeNanos
                render()
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private fun setRunning(on: Boolean) {
        if (on == running) return
        running = on
        lastFrameNanos = 0L
        if (on) Choreographer.getInstance().postFrameCallback(frameCallback) else Choreographer.getInstance().removeFrameCallback(frameCallback)
    }

    private fun render() {
        val s = settings ?: return
        val metrics = activity.resources.displayMetrics
        text.text = activity.getString(
            R.string.labs_overlay_text,
            s.home.desktopColumns, s.home.desktopRows, s.dock.iconCount, pages, items,
            (metrics.widthPixels / metrics.density).toInt(), (metrics.heightPixels / metrics.density).toInt(),
            metrics.density, lastAverageMs, slowFrames,
        )
    }

    private fun update(settings: LauncherSettings, workspace: WorkspaceState) {
        this.settings = settings
        if (workspace is WorkspaceState.Ready) {
            pages = workspace.workspace.pageCount
            items = workspace.workspace.items.size
        }
        val enabled = settings.labs.debugOverlay
        text.visibility = if (enabled) View.VISIBLE else View.GONE
        setRunning(enabled)
        if (enabled) render()
    }

    companion object {
        private const val REFRESH_NANOS = 500_000_000L
        private const val SLOW_FRAME_NANOS = 33_000_000L

        /** Adds the overlay to [activity]'s content view; it shows itself only when enabled in Labs. */
        @OptIn(ExperimentalCoroutinesApi::class)
        fun attach(activity: ComponentActivity, settingsRepository: SettingsRepository, workspaceRepository: WorkspaceRepository) {
            val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
            val overlay = DebugOverlay(activity)
            content.addView(
                overlay.text,
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL),
            )
            ViewCompat.setOnApplyWindowInsetsListener(overlay.text) { view, insets ->
                val top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
                view.updateLayoutParams<FrameLayout.LayoutParams> { topMargin = top }
                insets
            }
            activity.lifecycleScope.launch {
                activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    try {
                        // The workspace is observed only while the overlay is on.
                        settingsRepository.settings
                            .flatMapLatest { s ->
                                if (s.labs.debugOverlay) {
                                    workspaceRepository.workspace.onStart { emit(WorkspaceState.Loading) }.map { s to it }
                                } else {
                                    flowOf(s to WorkspaceState.Loading)
                                }
                            }
                            .catch { /* diagnostics must never take HOME down */ }
                            .collect { (s, w) -> overlay.update(s, w) }
                    } finally {
                        overlay.setRunning(false)
                    }
                }
            }
        }
    }
}
