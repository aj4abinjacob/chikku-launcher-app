package io.github.aj4abinjacob.chikku.message

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.VisibleForTesting
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import io.github.aj4abinjacob.chikku.R
import kotlin.math.max

/**
 * Chikku's message bar for View-based screens (HOME, and any activity without its own surface):
 * the undo bar's look (rounded, inverse surface), placed at the bottom above the navigation/gesture
 * bar and the keyboard. One message at a time; it hides itself after a while (longer when an
 * accessibility service asks for more time) and its text is a polite live region, so TalkBack reads
 * each message. If [stackAbove] (HOME's undo bar) is showing, the message sits just above it so an
 * Undo is never covered.
 */
class ChikkuMessageBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs), ChikkuMessages {

    /** A bar at the same bottom position (HOME's undo bar) that this bar stays above while it shows. */
    var stackAbove: View? = null

    private val message: TextView
    private val actionButton: Button
    private var action: MessageAction? = null
    private val margin = resources.getDimensionPixelSize(R.dimen.home_panel_margin)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { hide() }
    private val stackListener = ViewTreeObserver.OnGlobalLayoutListener { updateStacking() }
    private var listening = false

    init {
        LayoutInflater.from(context).inflate(R.layout.view_message_bar, this, true)
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundResource(R.drawable.bg_home_undo)
        minimumHeight = resources.getDimensionPixelSize(R.dimen.home_min_touch_target)
        elevation = 6 * resources.displayMetrics.density
        message = findViewById(R.id.message_bar_text)
        actionButton = findViewById(R.id.message_bar_action)
        actionButton.setOnClickListener {
            val chosen = action
            hide()
            chosen?.onClick?.invoke()
        }
        // The text, not the container, is the live region: setting it after the bar is visible
        // makes TalkBack read every message, including one repeated while the bar is up.
        message.accessibilityLiveRegion = ACCESSIBILITY_LIVE_REGION_POLITE
        ViewCompat.replaceAccessibilityAction(this, AccessibilityActionCompat.ACTION_DISMISS,
            context.getString(R.string.message_dismiss)) { _, _ -> hide(); true }
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            applyInsets(insets)
            insets
        }
        visibility = GONE
    }

    /** The message showing now, or null; for tests. */
    @get:VisibleForTesting
    val shownText: CharSequence? get() = if (visibility == VISIBLE) message.text else null

    override fun show(text: Int, action: MessageAction?, long: Boolean) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { show(text, action, long) }
            return
        }
        this.action = action
        actionButton.visibility = if (action != null) VISIBLE else GONE
        action?.let { actionButton.text = context.getString(it.label) }
        ViewCompat.getRootWindowInsets(this)?.let(::applyInsets)
        visibility = VISIBLE
        bringToFront()
        if (!listening) {
            viewTreeObserver.addOnGlobalLayoutListener(stackListener)
            listening = true
        }
        updateStacking()
        // Clear first so the same message shown twice is still a text change TalkBack announces.
        message.text = ""
        message.text = context.getString(text)
        mainHandler.removeCallbacks(hideRunnable)
        mainHandler.postDelayed(hideRunnable, timeoutMillis(long || action != null, action != null))
    }

    /** Hides the bar now (HOME does this when an undo bar takes over). */
    fun hide() {
        mainHandler.removeCallbacks(hideRunnable)
        action = null
        if (listening) {
            viewTreeObserver.removeOnGlobalLayoutListener(stackListener)
            listening = false
        }
        visibility = GONE
        message.text = ""
        translationY = 0f
    }

    override fun onDetachedFromWindow() {
        hide()
        super.onDetachedFromWindow()
    }

    private fun timeoutMillis(long: Boolean, hasControls: Boolean): Long {
        val base = if (long) LONG_MS else SHORT_MS
        val am = context.getSystemService(AccessibilityManager::class.java) ?: return base
        val flags = AccessibilityManager.FLAG_CONTENT_TEXT or (if (hasControls) AccessibilityManager.FLAG_CONTENT_CONTROLS else 0)
        val recommended = am.getRecommendedTimeoutMillis(base.toInt(), flags).toLong()
        // Like the undo bar: a screen reader gets at least twice as long, to reach the action.
        return if (am.isTouchExplorationEnabled) max(recommended, base * 2) else max(recommended, base)
    }

    private fun applyInsets(insets: WindowInsetsCompat) {
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
        setMargins(bars, ime)
    }

    private fun setMargins(bars: Insets, ime: Insets) {
        val params = layoutParams as? ViewGroup.MarginLayoutParams ?: return
        val bottom = margin + max(bars.bottom, ime.bottom)
        val left = margin + bars.left
        val right = margin + bars.right
        if (params.bottomMargin == bottom && params.leftMargin == left && params.rightMargin == right) return
        params.bottomMargin = bottom
        params.leftMargin = left
        params.rightMargin = right
        layoutParams = params
    }

    private fun updateStacking() {
        val above = stackAbove
        val target = if (above != null && above.visibility == VISIBLE && above.height > 0) {
            -(above.height + margin / 2).toFloat()
        } else {
            0f
        }
        if (translationY != target) translationY = target
    }

    companion object {
        private const val SHORT_MS = 4_000L
        private const val LONG_MS = 7_000L

        /** Adds a hidden bar at the bottom of [parent]. */
        fun attach(parent: FrameLayout, stackAbove: View? = null): ChikkuMessageBarView =
            ChikkuMessageBarView(parent.context).also { bar ->
                bar.stackAbove = stackAbove
                parent.addView(bar, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM,
                ).apply { setMargins(bar.margin, bar.margin, bar.margin, bar.margin) })
            }
    }
}
