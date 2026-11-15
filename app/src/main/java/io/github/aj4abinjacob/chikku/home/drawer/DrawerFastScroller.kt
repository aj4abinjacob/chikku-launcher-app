package io.github.aj4abinjacob.chikku.home.drawer

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.view.isEmpty
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.drawer.DrawerModel
import io.github.aj4abinjacob.chikku.core.model.drawer.DrawerSection
import io.github.aj4abinjacob.chikku.home.Haptics
import io.github.aj4abinjacob.chikku.platform.LocaleSectionIndex
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt

/**
 * Alphabetical letter rail or continuous scroll thumb for the vertical drawer. The thumb uses the
 * grid's pixel scroll range and shows the section actually visible at the top. Every letter stays
 * a separate button so TalkBack and switch access can jump directly ("Jump to A").
 */
class DrawerFastScroller @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    /** Called with the drawer position of the chosen section. */
    var onSectionSelected: ((position: Int) -> Unit)? = null
    /** Requested fraction of the grid's scrollable pixel range, from top to bottom. */
    var onThumbDragged: ((fraction: Float) -> Unit)? = null
    var onThumbGestureStarted: (() -> Unit)? = null
    /**
     * Letter of the row beside [y] (this view's coordinates) while the thumb is dragged, or null to
     * name the top row, so the bubble names the apps it sits next to.
     */
    var letterBeside: ((y: Float) -> String?)? = null

    /**
     * [io.github.aj4abinjacob.chikku.theme.Motion.scale] for the wave, settle and bubble: 0 snaps,
     * 1 is normal speed. Android's own "Remove animations" setting always wins.
     */
    var motionScale: Float = 1f
        set(value) { field = value; popup.motionScale = value }

    private var sections: List<DrawerSection> = emptyList()
    private var textColor: Int = 0
    private var activeColor: Int = 0
    private var activeIndex = -1
    /** Thumb appearance keeps the same accessible jump targets and draggable hit area. */
    var thumbMode: Boolean = false
        set(value) {
            if (field != value) cancelDrag()
            field = value
            minimumWidth = resources.getDimensionPixelSize(
                if (value) R.dimen.drawer_fast_scroller_thumb_width else R.dimen.drawer_fast_scroller_width,
            )
            highlight(-1)
            invalidate()
        }
    var thumbOnLeft: Boolean = false
        set(value) { field = value; invalidate() }

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbWidth = resources.getDimension(R.dimen.drawer_fast_scroller_thumb_width)
    private val minThumbHeight = 32f * resources.displayMetrics.density
    private var scrollOffset = 0
    private var scrollExtent = 0
    private var scrollRange = 0
    private var grabOffset = 0f
    private var dragY = 0f

    /** Bounds in this view's coordinates; the same geometry is drawn and used for dragging. */
    internal val thumbBounds: Rect
        get() {
            val length = thumbLength()
            val travel = (height - length).coerceAtLeast(0f)
            val top = if (scrollRange > scrollExtent && travel > 0f)
                travel * scrollOffset.toFloat() / (scrollRange - scrollExtent) else 0f
            val left = if (thumbOnLeft) 0f else width - thumbWidth
            return Rect(left.roundToInt(), top.roundToInt(), (left + thumbWidth).roundToInt(), (top + length).roundToInt())
        }

    fun updateThumb(offset: Int, extent: Int, range: Int, letter: String?) {
        scrollExtent = extent.coerceAtLeast(0)
        scrollRange = range.coerceAtLeast(scrollExtent)
        scrollOffset = offset.coerceIn(0, (scrollRange - scrollExtent).coerceAtLeast(0))
        invalidate()
        if (thumbMode && dragging) {
            if (letter == null) dismissPopup(immediately = true)
            else showPopup(letterBeside?.invoke(dragY) ?: letter, dragY)
        }
    }

    private fun thumbLength(): Float = if (height <= 0) 0f else
        (height.toFloat() * scrollExtent / scrollRange.coerceAtLeast(1))
            .coerceIn(minThumbHeight.coerceAtMost(height.toFloat()), height.toFloat())

    init { setWillNotDraw(false) }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!thumbMode || scrollRange <= scrollExtent || height <= 0) return
        val x = if (thumbOnLeft) thumbWidth / 2f else width - thumbWidth / 2f
        val radius = thumbWidth / 2f
        trackPaint.color = Color.argb(24, Color.red(textColor), Color.green(textColor), Color.blue(textColor))
        thumbPaint.color = activeColor
        canvas.drawRoundRect(x - radius, 0f, x + radius, height.toFloat(), radius, radius, trackPaint)
        val bounds = thumbBounds
        canvas.drawRoundRect(bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(), bounds.bottom.toFloat(), radius, radius, thumbPaint)
    }

    /**
     * Called with the bubble's bounds in the parent's coordinates while it is shown, and null when
     * the drag ends, so the list can move the apps under it out of the way.
     */
    var onBubbleMoved: ((Rect?) -> Unit)? = null

    /** Shows the letter bubble while dragging. */
    var popupEnabled: Boolean = true
        set(value) { field = value; if (!value) dismissPopup(immediately = true) }

    private val popup = FastScrollerPopup(
        size = resources.getDimension(R.dimen.drawer_fast_scroller_popup_size),
        textSize = resources.getDimension(R.dimen.drawer_fast_scroller_popup_text_size),
    ).apply { onHidden = { removeFromOverlay() } }
    private var popupHost: ViewGroup? = null

    /**
     * Where the bubble is drawn; the parent by default. Set it when the parent is only as wide as
     * the strip (a Compose `AndroidView`), so the bubble beside the strip is not clipped.
     */
    var popupParent: ViewGroup? = null

    /** The letter in the bubble while it is shown, for tests. */
    internal val popupLetter: String? get() = if (popupHost != null && popup.showing) popup.letter else null

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        val horizontal = resources.getDimensionPixelSize(R.dimen.drawer_fast_scroller_padding)
        setPadding(horizontal, 0, horizontal, 0)
        minimumWidth = resources.getDimensionPixelSize(R.dimen.drawer_fast_scroller_width)
    }

    fun setSections(sections: List<DrawerSection>, textColor: Int, activeColor: Int = textColor) {
        if (sections == this.sections && textColor == this.textColor && activeColor == this.activeColor) return
        cancelDrag()
        this.sections = sections
        this.textColor = textColor
        this.activeColor = activeColor
        activeIndex = -1
        removeAllViews()
        sections.forEach { section ->
            val letter = TextView(context).apply {
                text = section.letter
                gravity = Gravity.CENTER
                setTextColor(if (thumbMode) android.graphics.Color.TRANSPARENT else textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.drawer_fast_scroller_text_size))
                contentDescription = context.getString(R.string.drawer_fast_scroller_jump, spokenName(section.letter))
                isFocusable = true
                setOnClickListener { onSectionSelected?.invoke(section.position) }
            }
            addView(letter, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f).apply { gravity = Gravity.CENTER })
        }
        updateStride()
    }

    private fun spokenName(letter: String): String = when (letter) {
        DrawerModel.OTHER_SECTION -> context.getString(R.string.drawer_fast_scroller_other)
        LocaleSectionIndex.OVERFLOW_SECTION -> context.getString(R.string.drawer_fast_scroller_overflow)
        else -> letter
    }

    /**
     * With more entries than fit (several languages' letters), only every [stride]th label is
     * drawn, so labels never overlap. Every entry stays a drag target and an accessible button.
     */
    private var stride = 1

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (h != oldh) updateStride()
        invalidate()
    }

    private fun updateStride() {
        val usable = height - paddingTop - paddingBottom
        val line = (getChildAt(0) as? TextView)?.lineHeight ?: 0
        stride = if (usable <= 0 || line <= 0 || isEmpty()) 1 else ((line * childCount + usable - 1) / usable).coerceAtLeast(1)
        highlight(activeIndex)
    }

    private val waveShift = resources.getDimension(R.dimen.drawer_fast_scroller_wave_shift)
    /** Letters within this distance of the finger bulge toward the list, the nearest the most. */
    private val waveRadius = WAVE_RADIUS_DP * resources.displayMetrics.density

    /** Whether the letters bulge out under the finger; off when system animations are off. */
    private val waveEnabled get() = !thumbMode && motionScale > 0f && ValueAnimator.areAnimatorsEnabled()

    private var clipRestore: ViewGroup? = null
    private var clipWasEnabled = true

    /** Bulge of the letter at [index] for a finger at [y]: 1 under the finger, 0 from [waveRadius] away. */
    internal fun waveAt(index: Int, y: Float): Float {
        val child = getChildAt(index) ?: return 0f
        val distance = abs(child.top + child.height / 2f - y) / waveRadius
        return if (distance >= 1f) 0f else ((1f + cos(distance * Math.PI.toFloat())) / 2f)
    }

    private fun railOnRight(): Boolean {
        val host = parent as? View ?: return true
        return left + width / 2 > host.width / 2
    }

    private fun applyWave(y: Float) {
        if (!waveEnabled) return
        // Letters move out of this narrow strip, over the list's padding.
        (parent as? ViewGroup)?.let { host ->
            if (clipRestore !== host) {
                restoreClip()
                clipRestore = host
                clipWasEnabled = host.clipChildren
            }
            host.clipChildren = false
        }
        val direction = if (railOnRight()) -1f else 1f
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val bulge = waveAt(i, y)
            child.animate().cancel()
            child.translationX = direction * waveShift * bulge
            child.scaleX = 1f + WAVE_SCALE * bulge
            child.scaleY = child.scaleX
            // Hidden in-between letters (several alphabets) appear only where the wave opens them up.
            if (i != activeIndex && i % stride != 0) {
                (child as? TextView)?.setTextColor(ColorUtils.setAlphaComponent(textColor, (bulge * 255).toInt()))
            }
        }
    }

    /** Puts the parent's clipping back once the letters are home again. */
    private fun restoreClip() {
        clipRestore?.clipChildren = clipWasEnabled
        clipRestore = null
    }

    private fun settleWave(immediately: Boolean) {
        val animated = !immediately && motionScale > 0f && ValueAnimator.areAnimatorsEnabled()
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            child.animate().cancel()
            if (!animated) {
                child.translationX = 0f; child.scaleX = 1f; child.scaleY = 1f
            } else {
                val last = i == childCount - 1
                child.animate().translationX(0f).scaleX(1f).scaleY(1f).setDuration((WAVE_SETTLE_MS * motionScale).toLong())
                    .withEndAction { if (last && !pointerActive) restoreClip() }.start()
            }
        }
        if (!animated || childCount == 0) restoreClip()
    }

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downY = 0f
    private var dragging = false
    private var pointerActive = false

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = true

    // Each letter is a clickable view for accessibility; the drag is a pointer convenience.
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pointerActive = true
                // Keep the drawer from treating the drag as a swipe to close.
                parent?.requestDisallowInterceptTouchEvent(true)
                downY = event.y
                if (thumbMode) {
                    onThumbGestureStarted?.invoke()
                    val bounds = thumbBounds
                    grabOffset = if (event.y >= bounds.top && event.y <= bounds.bottom)
                        event.y - bounds.top else bounds.height() / 2f
                }
                // Letters are visible targets, so a tap jumps. The thumb has none: a stray tap
                // on its strip should not move the list, only a deliberate drag.
                dragging = !thumbMode
                if (dragging) select(indexAt(event.y), event.y)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!pointerActive) return true
                if (!dragging && abs(event.y - downY) > touchSlop) dragging = true
                if (dragging) {
                    if (thumbMode) dragThumb(event.y) else select(indexAt(event.y), event.y)
                }
            }
            MotionEvent.ACTION_UP -> finishDrag(immediately = false)
            MotionEvent.ACTION_CANCEL -> cancelDrag()
        }
        return true
    }

    private fun finishDrag(immediately: Boolean) {
        pointerActive = false
        dragging = false
        highlight(-1)
        settleWave(immediately)
        dismissPopup(immediately)
    }

    private fun cancelDrag() = finishDrag(immediately = true)

    internal fun cancelInteraction() = cancelDrag()

    private fun dragThumb(y: Float) {
        dragY = y
        val travel = (height - thumbLength()).coerceAtLeast(0f)
        if (travel <= 0f || scrollRange <= scrollExtent) return
        onThumbDragged?.invoke(((y - grabOffset) / travel).coerceIn(0f, 1f))
    }

    private fun indexAt(y: Float): Int {
        if (isEmpty()) return -1
        val usable = (height - paddingTop - paddingBottom).coerceAtLeast(1)
        return (((y - paddingTop) / usable) * childCount).toInt().coerceIn(0, childCount - 1)
    }

    private fun select(index: Int, y: Float) {
        if (index < 0) return
        // The bubble and wave follow the finger even within one letter; the list only moves on a change.
        val changed = index != activeIndex
        if (changed) highlight(index)
        applyWave(y)
        showPopup(sections[index].letter, y)
        if (!changed) return
        // A tick per letter, as on a detent; the first touch is not a change of letter.
        if (dragging && pointerActive && abs(y - downY) > 0f) Haptics.tick(this)
        sections.getOrNull(index)?.let { onSectionSelected?.invoke(it.position) }
    }

    private fun showPopup(letter: String, y: Float) {
        val host = popupParent ?: parent as? ViewGroup ?: return
        if (!popupEnabled) return
        if (popupHost !== host) {
            removeFromOverlay()
            host.overlay.add(popup)
            popupHost = host
        }
        popup.color = activeColor
        // This strip's position in the host's coordinates.
        val (x, top) = if (host === parent) left to top else {
            val self = IntArray(2).also(::getLocationInWindow)
            val other = IntArray(2).also(host::getLocationInWindow)
            (self[0] - other[0]) to (self[1] - other[1])
        }
        val railOnRight = x + width / 2 > host.width / 2
        // Beside the letters, not the empty gap side of the strip.
        val railEdge = if (railOnRight) x + paddingLeft else x + width - paddingRight
        // Clear of the letters bulging toward the list.
        val margin = resources.getDimension(R.dimen.drawer_fast_scroller_popup_margin) + if (waveEnabled) waveShift else 0f
        popup.place(letter, railEdge.toFloat(), top + y, railOnRight, host.height, margin)
        popup.show()
        onBubbleMoved?.invoke(popup.bounds)
    }

    private fun dismissPopup(immediately: Boolean = false) {
        if (popupHost == null) return
        onBubbleMoved?.invoke(null)
        if (immediately) removeFromOverlay() else popup.hide()
    }

    private fun removeFromOverlay() {
        popup.cancelAnimations()
        popupHost?.overlay?.remove(popup)
        popupHost = null
    }

    private companion object {
        const val WAVE_RADIUS_DP = 72f
        const val WAVE_SCALE = .7f
        const val WAVE_SETTLE_MS = 180L
    }

    override fun onDetachedFromWindow() {
        cancelInteraction()
        super.onDetachedFromWindow()
    }

    private fun highlight(index: Int) {
        activeIndex = index
        for (i in 0 until childCount) {
            (getChildAt(i) as? TextView)?.apply {
                setTypeface(null, if (i == index) Typeface.BOLD else Typeface.NORMAL)
                val drawn = !thumbMode && i % stride == 0
                setTextColor(if (i == index) activeColor else if (drawn) textColor else android.graphics.Color.TRANSPARENT)
            }
        }
    }
}
