package io.github.aj4abinjacob.chikku.home

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.theme.Motion

/**
 * Transient "Removed / Undo" bar. Auto-hides after [UNDO_TIMEOUT_MS]; the timeout is doubled when
 * an accessibility service is active so screen-reader users can reach the button.
 */
class UndoBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    private val message: TextView
    private val undoButton: Button
    private var onUndo: (() -> Unit)? = null
    private val hideRunnable = Runnable { hide() }
    private var shown = false

    /** Animation speed for the fade in and out; set by the host. */
    var motion: Motion = Motion.DEFAULT

    init {
        LayoutInflater.from(context).inflate(R.layout.view_undo_bar, this, true)
        orientation = HORIZONTAL
        message = findViewById(R.id.undo_message)
        undoButton = findViewById(R.id.undo_button)
        undoButton.setOnClickListener {
            val action = onUndo
            hide()
            action?.invoke()
        }
        accessibilityLiveRegion = ACCESSIBILITY_LIVE_REGION_POLITE
        visibility = GONE
    }

    fun show(text: CharSequence, undo: () -> Unit) {
        message.text = text
        onUndo = undo
        val wasShown = shown
        shown = true
        animate().cancel()
        val ms = motion.duration(FADE_MS)
        visibility = VISIBLE
        if (!wasShown && ms > 0L && isAttachedToWindow) {
            alpha = 0f
            animate().alpha(1f).setDuration(ms).start()
        } else {
            alpha = 1f
        }
        removeCallbacks(hideRunnable)
        val am = context.getSystemService(android.view.accessibility.AccessibilityManager::class.java)
        val timeout = if (am?.isEnabled == true) UNDO_TIMEOUT_MS * 2 else UNDO_TIMEOUT_MS
        postDelayed(hideRunnable, timeout)
    }

    fun hide() {
        removeCallbacks(hideRunnable)
        onUndo = null
        if (!shown) return
        shown = false
        animate().cancel()
        val ms = motion.duration(FADE_MS)
        if (ms <= 0L || !isAttachedToWindow || visibility != VISIBLE) {
            visibility = GONE
            alpha = 1f
            return
        }
        animate().alpha(0f).setDuration(ms).withEndAction {
            if (!shown) {
                visibility = GONE
                alpha = 1f
            }
        }.start()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(hideRunnable)
        animate().cancel()
        alpha = 1f
        if (!shown) visibility = GONE
        super.onDetachedFromWindow()
    }

    private companion object {
        const val UNDO_TIMEOUT_MS = 5_000L
        const val FADE_MS = 150L
    }
}
