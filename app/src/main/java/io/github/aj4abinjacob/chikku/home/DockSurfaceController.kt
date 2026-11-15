package io.github.aj4abinjacob.chikku.home

import android.view.Gravity
import android.graphics.Bitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import io.github.aj4abinjacob.chikku.core.model.settings.DockSettings
import io.github.aj4abinjacob.chikku.theme.Motion

/** An in-app overlay; no system overlay permission or window is involved. */
internal class DockSurfaceController(
    private val dock: CellLayout,
    private val normalHost: FrameLayout,
    private val overlayHost: FrameLayout,
    hiddenInitially: Boolean = false,
    private val root: FrameLayout? = null,
    private val imageScope: CoroutineScope? = null,
    /** Decodes the selected photo; tests substitute one that needs no granted document. */
    private val loadImage: suspend (String) -> Bitmap? = DockImageLoader(dock.context.applicationContext, Dispatchers.IO)::load,
) {
    private var imageUri: String? = null
    private var image: Bitmap? = null
    private var imageJob: Job? = null

    var hidden: Boolean = hiddenInitially
        private set

    /** Animation speed for showing and hiding the dock; set by the host. */
    var motion: Motion = Motion.DEFAULT
    private var fadeToken = 0
    private var preferences = DockSettings()
    private var ready = false
    private var recoveryVisible = false
    private var lastReadyPreferences: DockSettings? = null
    private val backdrop: View? = root?.let { host ->
        View(dock.context).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            visibility = View.GONE
            host.addView(this, 0, FrameLayout.LayoutParams(0, 0))
        }
    }

    init {
        dock.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateBackdrop() }
        root?.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateBackdrop() }
    }

    fun render(settings: DockSettings, workspaceReady: Boolean, recoveryVisible: Boolean = false) {
        if (workspaceReady) {
            lastReadyPreferences?.let { previous ->
                if (previous.enabled != settings.enabled || previous.displayAsOverlay != settings.displayAsOverlay) hidden = false
            }
            lastReadyPreferences = settings
        }
        preferences = settings
        ready = workspaceReady
        this.recoveryVisible = recoveryVisible
        val host = if (settings.displayAsOverlay) overlayHost else normalHost
        if (dock.parent !== host) {
            (dock.parent as? ViewGroup)?.removeView(dock)
            host.addView(dock, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM,
            ))
        }
        val selected = settings.backgroundImageUri.takeIf { ready && settings.enabled && settings.backgroundEnabled && settings.background != io.github.aj4abinjacob.chikku.core.model.settings.DockBackground.NONE }
        if (selected != imageUri) {
            imageUri = selected
            image = null
            imageJob?.cancel()
            if (selected != null) imageJob = imageScope?.launch {
                val loaded = loadImage(selected)
                if (imageUri == selected) {
                    image = loaded
                    applyBackground()
                }
            }
        }
        // Each step reruns only when its inputs changed: a page swipe that remembers lastPage
        // re-emits the settings, and must not rebuild drawables or cancel a running fade.
        val backgroundChanged = applyBackground()
        if (wantedVisibility() != lastVisibility) updateVisibility()
        if (backgroundChanged) dock.post { updateBackdrop() }
    }

    /** True while the photo was released by [trimMemory] and not yet reloaded. */
    internal var imageTrimmed = false
        private set

    /**
     * Releases the decoded dock photo (and its composed copy) while HOME is hidden. The shape
     * background stays; [restoreAfterTrim] or the next [render] decodes the photo again.
     */
    fun trimMemory() {
        if (imageUri == null) return
        imageJob?.cancel()
        imageJob = null
        imageUri = null
        image = null
        imageTrimmed = true
        applyBackground()
    }

    /** Reloads a photo released by [trimMemory]; call before HOME becomes visible again. */
    fun restoreAfterTrim() {
        if (!imageTrimmed) return
        imageTrimmed = false
        render(preferences, ready)
    }

    private data class BackgroundKey(val settings: DockSettings, val image: Bitmap?, val uiMode: Int)
    private var backgroundKey: BackgroundKey? = null
    private var lastVisibility = -1

    /** Rebuilds the dock and backdrop drawables if their inputs changed; returns whether it did. */
    private fun applyBackground(): Boolean {
        val key = BackgroundKey(preferences.copy(lastPage = 0), image, dock.context.resources.configuration.uiMode)
        if (key == backgroundKey) return false
        backgroundKey = key
        fun background(): android.graphics.drawable.Drawable? {
            val shape = DockBackgroundStyle.create(dock.context, preferences) ?: return null
            return image?.let { DockImageDrawable(shape, it) } ?: shape
        }
        dock.background = if (preferences.drawBehindNavigationBar && backdrop != null) null else background()
        backdrop?.background = background()
        return true
    }

    fun toggle() {
        if (!preferences.enabled || !ready) return
        hidden = !hidden
        updateVisibility(animate = true)
    }

    fun onAppLaunched() {
        if (preferences.displayAsOverlay && preferences.automaticallyClose) {
            hidden = true
            updateVisibility(animate = true)
        }
    }

    /** [animate]: a user toggle fades the dock (and its backdrop) instead of flipping its visibility. */
    private fun wantedVisibility() = when {
        // Before the first Ready state, CellLayout still has its default five-row grid.
        // Reserving that invisible dock's height can clip the real recovery actions.
        !preferences.enabled || hidden || recoveryVisible -> View.GONE
        ready -> View.VISIBLE
        else -> View.INVISIBLE
    }

    private fun updateVisibility(animate: Boolean = false) {
        val wanted = wantedVisibility()
        lastVisibility = wanted
        val token = ++fadeToken
        dock.animate().cancel()
        backdrop?.animate()?.cancel()
        val ms = motion.duration(FADE_MS)
        val from = dock.visibility
        val fading = animate && ms > 0L && dock.isAttachedToWindow && from != wanted &&
            (wanted == View.VISIBLE || from == View.VISIBLE)
        dock.alpha = 1f
        backdrop?.alpha = 1f
        if (!fading) {
            dock.visibility = wanted
            updateBackdrop()
            return
        }
        val target = if (wanted == View.VISIBLE) 1f else 0f
        if (wanted == View.VISIBLE) {
            dock.alpha = 0f
            dock.visibility = View.VISIBLE
            updateBackdrop()
            backdrop?.alpha = 0f
        }
        backdrop?.animate()?.alpha(target)?.setDuration(ms)?.start()
        dock.animate().alpha(target).setDuration(ms).withEndAction {
            if (token != fadeToken) return@withEndAction
            dock.alpha = 1f
            backdrop?.alpha = 1f
            dock.visibility = wanted
            updateBackdrop()
        }.start()
    }

    private fun updateBackdrop() {
        val host = root ?: return
        val view = backdrop ?: return
        view.visibility = if (preferences.drawBehindNavigationBar && dock.visibility == View.VISIBLE && view.background != null) View.VISIBLE else View.GONE
        if (view.visibility != View.VISIBLE || !dock.isAttachedToWindow) return
        val dockLocation = IntArray(2)
        val rootLocation = IntArray(2)
        dock.getLocationInWindow(dockLocation)
        host.getLocationInWindow(rootLocation)
        val top = dockLocation[1] - rootLocation[1]
        val left = dockLocation[0] - rootLocation[0]
        val height = (host.height - top).coerceAtLeast(0)
        val params = view.layoutParams as FrameLayout.LayoutParams
        if (params.width != dock.width || params.height != height || params.topMargin != top || params.leftMargin != left) {
            params.width = dock.width
            params.height = height
            params.topMargin = top
            params.leftMargin = left
            view.layoutParams = params
        }
    }

    private companion object {
        const val FADE_MS = 160L
    }
}
