package io.github.aj4abinjacob.chikku.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.graphics.ColorUtils
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.core.content.ContextCompat
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.settings.PageIndicatorStyle

/**
 * Page indicator in the [style] chosen in settings: a row of dots, a line with a moving segment,
 * or nothing. Mirrors in RTL so the active mark matches the pager's visual order. With [autoHide] it
 * stays transparent until the pages scroll, then fades out again after [HIDE_DELAY_MS].
 */
open class PageIndicatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val dotSize = resources.getDimension(R.dimen.home_page_dot_size)
    private val dotGap = resources.getDimension(R.dimen.home_page_dot_gap)
    private val lineHeight = resources.getDimension(R.dimen.home_page_line_height)
    private val lineSegment = resources.getDimension(R.dimen.home_page_line_segment)
    private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.chikku_on_wallpaper)
        setShadowLayer(dotSize / 2f, 0f, 0f, ContextCompat.getColor(context, R.color.home_label_shadow))
    }
    private val inactivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.home_page_dot_inactive)
    }
    private val rect = RectF()

    var style: PageIndicatorStyle = PageIndicatorStyle.DOTS
        set(value) {
            if (field == value) return
            field = value
            updateDescription()
            updateVisibility()
            invalidate()
        }

    var motionScale: Float = 1f

    var autoHide: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            removeCallbacks(fadeOut)
            animate().cancel()
            revealed = false
            alpha = if (value) 0f else 1f
            requestLayout()
        }

    /** True from a reveal until its fade-out starts, so a reveal per scroll frame does not restart the fade-in. */
    private var revealed = false

    private val fadeOut = Runnable {
        revealed = false
        if (motionScale <= 0f) alpha = 0f
        else animate().alpha(0f).setDuration((FADE_OUT_MS * motionScale).toLong()).start()
    }

    private var pageCount = 0
    private var current = 0
    internal var scrollPosition = 0f
        private set
    var onPageSelected: ((Int) -> Unit)? = null
        set(value) { field = value; isClickable = value != null; isFocusable = value != null }
    protected open val descriptionResource: Int get() = R.string.home_page_indicator_description
    private var downX = 0f
    private var scrubbing = false
    private var selected = 0
    private var gestureActive = false
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    fun setColor(color: Int) {
        activePaint.color = color
        inactivePaint.color = ColorUtils.setAlphaComponent(color, 0x66)
        invalidate()
    }

    fun setScrollPosition(position: Float) {
        scrollPosition = position
        invalidate()
    }

    /** True when something is drawn: more than one page and a visible style. */
    val isShowingPages: Boolean get() = pageCount > 1 && style != PageIndicatorStyle.NONE

    fun setPages(current: Int, count: Int) {
        if (this.current == current && pageCount == count) return
        this.current = current.coerceIn(0, (count - 1).coerceAtLeast(0))
        scrollPosition = this.current.toFloat()
        pageCount = count
        updateDescription()
        updateVisibility()
        invalidate()
    }

    /** Shows the indicator while the pages move; a no-op unless [autoHide] is on. */
    fun revealWhileScrolling() {
        if (!autoHide) return
        removeCallbacks(fadeOut)
        if (!revealed) {
            revealed = true
            animate().cancel()
            if (motionScale <= 0f) alpha = 1f
            else animate().alpha(1f).setDuration((FADE_IN_MS * motionScale).toLong()).start()
        }
        postDelayed(fadeOut, HIDE_DELAY_MS)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(fadeOut)
        animate().cancel()
        revealed = false
        if (autoHide) alpha = 0f
        super.onDetachedFromWindow()
    }

    private fun updateVisibility() {
        visibility = if (style == PageIndicatorStyle.NONE) GONE else if (pageCount > 1) VISIBLE else INVISIBLE
    }

    private fun updateDescription() {
        val shown = style != PageIndicatorStyle.NONE && pageCount > 0
        contentDescription = if (shown) {
            context.getString(descriptionResource, current + 1, pageCount)
        } else {
            null
        }
        importantForAccessibility = if (shown) IMPORTANT_FOR_ACCESSIBILITY_AUTO else IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Hidden-until-scrolling dots keep only the room they draw in, so they leave no empty band
        // above the dock; visible dots keep a full touch target for tapping a page.
        val dots = (paddingTop + paddingBottom + dotSize * 2).toInt()
        val wanted = if (autoHide) dots else maxOf(dots, (48 * resources.displayMetrics.density).toInt())
        setMeasuredDimension(
            resolveSize(suggestedMinimumWidth, widthMeasureSpec),
            resolveSize(wanted, heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!isShowingPages) return
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        val logical = if (pageCount > 0) ((scrollPosition % pageCount) + pageCount) % pageCount else 0f
        val rawVisual = if (rtl) pageCount - 1 - logical else logical
        val visualCurrent = ((rawVisual % pageCount) + pageCount) % pageCount
        when (style) {
            PageIndicatorStyle.DOTS -> drawDots(canvas, visualCurrent)
            PageIndicatorStyle.LINE -> drawLine(canvas, visualCurrent)
            PageIndicatorStyle.NONE -> Unit
        }
    }

    /**
     * Inactive dots stay put while the active mark follows the swipe: its leading edge reaches the
     * next dot in the first half of the swipe and its trailing edge catches up in the second, so it
     * stretches between the two dots and settles back to a single dot on the page.
     */
    private fun drawDots(canvas: Canvas, visualCurrent: Float) {
        val total = stripTotal()
        val left = stripLeft(total)
        val step = total / pageCount
        val cy = height / 2f
        val radius = minOf(dotSize / 2f, step * .3f)
        activePaint.alpha = INACTIVE_DOT_ALPHA
        for (visual in 0 until pageCount) {
            canvas.drawCircle(left + (visual + .5f) * step, cy, radius, activePaint)
        }
        activePaint.alpha = 255
        val from = visualCurrent.toInt().coerceIn(0, pageCount - 1)
        val fraction = (visualCurrent - from).coerceIn(0f, 1f)
        val fromX = left + (from + .5f) * step
        if (from == pageCount - 1 && fraction > 0f) {
            // Wrapping past the last page: shrink out of the last dot and grow into the first one.
            drawActiveDot(canvas, fromX, cy, radius, 1f - fraction)
            drawActiveDot(canvas, left + .5f * step, cy, radius, fraction)
        } else {
            val (tail, head) = activeDotSpan(fraction)
            rect.set(fromX + step * tail - radius, cy - radius, fromX + step * head + radius, cy + radius)
            canvas.drawRoundRect(rect, radius, radius, activePaint)
        }
    }

    private fun drawActiveDot(canvas: Canvas, x: Float, cy: Float, radius: Float, amount: Float) {
        if (amount <= 0f) return
        canvas.drawCircle(x, cy, radius * amount, activePaint)
    }

    private fun drawLine(canvas: Canvas, visualCurrent: Float) {
        val total = stripTotal()
        val left = stripLeft(total)
        val cy = height / 2f
        val radius = lineHeight / 2f
        rect.set(left, cy - radius, left + total, cy + radius)
        canvas.drawRoundRect(rect, radius, radius, inactivePaint)
        val segment = total / pageCount
        rect.set(left + segment * visualCurrent, cy - radius, minOf(left + total, left + segment * (visualCurrent + 1)), cy + radius)
        canvas.drawRoundRect(rect, radius, radius, activePaint)
        if (visualCurrent > pageCount - 1) {
            rect.set(left, cy - radius, left + segment * (visualCurrent + 1 - pageCount), cy + radius)
            canvas.drawRoundRect(rect, radius, radius, activePaint)
        }
    }

    private fun stripTotal(): Float {
        val step = if (style == PageIndicatorStyle.LINE) lineSegment else dotSize + dotGap
        return (pageCount * step).coerceAtMost((width - paddingLeft - paddingRight).toFloat().coerceAtLeast(1f))
    }

    private fun stripLeft(total: Float): Float = (width - total) / 2f

    private fun pageAt(x: Float): Int {
        val total = stripTotal()
        val left = stripLeft(total)
        val visual = (((x - left) / total) * pageCount - .5f).roundToInt().coerceIn(0, pageCount - 1)
        return if (layoutDirection == LAYOUT_DIRECTION_RTL) pageCount - 1 - visual else visual
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (onPageSelected == null || pageCount <= 1) return super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                gestureActive = true
                scrubbing = false
                selected = current
                parent?.requestDisallowInterceptTouchEvent(true)
                revealWhileScrolling()
            }
            MotionEvent.ACTION_MOVE -> {
                if (!gestureActive) return false
                if (abs(event.x - downX) > touchSlop) scrubbing = true
                if (scrubbing) selectPage(pageAt(event.x))
            }
            MotionEvent.ACTION_UP -> {
                if (!gestureActive) return false
                gestureActive = false
                if (!scrubbing) { selectPage(pageAt(event.x)); performClick() }
                parent?.requestDisallowInterceptTouchEvent(false)
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> {
                gestureActive = false
                parent?.requestDisallowInterceptTouchEvent(false)
                return false
            }
        }
        return true
    }

    private fun selectPage(page: Int) {
        if (selected == page) return
        selected = page
        onPageSelected?.invoke(page)
        revealWhileScrolling()
    }

    override fun performClick(): Boolean = super.performClick()

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        if (onPageSelected == null || pageCount <= 1) return
        info.className = android.widget.SeekBar::class.java.name
        info.rangeInfo = AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT,
            1f, pageCount.toFloat(), (current + 1).toFloat())
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS)
        if (current > 0) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
        if (current < pageCount - 1) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        if (onPageSelected == null || pageCount <= 1) return super.performAccessibilityAction(action, arguments)
        val target = when (action) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> (current + 1).coerceAtMost(pageCount - 1)
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> (current - 1).coerceAtLeast(0)
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id -> {
                val value = arguments?.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE) ?: return false
                if (!value.isFinite()) return false
                (value.roundToInt() - 1).coerceIn(0, pageCount - 1)
            }
            else -> return super.performAccessibilityAction(action, arguments)
        }
        onPageSelected?.invoke(target)
        revealWhileScrolling()
        return true
    }

    internal companion object {
        /** Tail and head of the active dot, in dot steps from the page being left, at swipe [fraction]. */
        fun activeDotSpan(fraction: Float): Pair<Float, Float> =
            ((fraction - .5f) * 2f).coerceIn(0f, 1f) to (fraction * 2f).coerceIn(0f, 1f)

        const val FADE_IN_MS = 120L
        const val FADE_OUT_MS = 300L
        const val HIDE_DELAY_MS = 700L
        const val INACTIVE_DOT_ALPHA = 102
    }
}
