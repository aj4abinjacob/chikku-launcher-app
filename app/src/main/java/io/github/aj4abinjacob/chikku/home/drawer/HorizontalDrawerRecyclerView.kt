package io.github.aj4abinjacob.chikku.home.drawer

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.animation.ValueAnimator
import androidx.core.graphics.withTranslation
import androidx.recyclerview.widget.RecyclerView
import io.github.aj4abinjacob.chikku.core.model.settings.PageEdgeFeedback
import io.github.aj4abinjacob.chikku.core.model.settings.PageMotion
import io.github.aj4abinjacob.chikku.core.model.settings.PageSwipeSensitivity
import kotlin.math.abs

/** Direction gating happens before RecyclerView takes a gesture away from an icon or the vertical drawer. */
class HorizontalDrawerRecyclerView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : RecyclerView(context, attrs) {
    var swipeSensitivity = PageSwipeSensitivity.NORMAL
    var snapDurationMs = 250
    var animationScale = 1f
        set(value) {
            if (field == value) return
            field = value
            if (value <= 0f) {
                edgeAnimator?.cancel()
                edgeOffset = 0f
                stopScroll()
                invalidate()
            }
        }
    var edgeFeedback = PageEdgeFeedback.RESISTANCE
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var axis = Axis.WAITING
    private var edgeOffset = 0f
    private var edgeAnimator: ValueAnimator? = null
    private val slop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private enum class Axis { WAITING, HORIZONTAL, VERTICAL, BLOCKED }

    init { overScrollMode = OVER_SCROLL_NEVER }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                edgeAnimator?.cancel()
                edgeOffset = 0f
                invalidate()
                downX = event.x
                downY = event.y
                lastX = event.x
                axis = Axis.WAITING
            }
            MotionEvent.ACTION_MOVE -> if (axis == Axis.WAITING) {
                val dx = event.x - downX
                val dy = event.y - downY
                if (PageMotion.isHorizontal(dx, dy, slop, swipeSensitivity)) axis = Axis.HORIZONTAL
                else if (abs(dy) > slop && abs(dy) > abs(dx)) axis = Axis.VERTICAL
            }
            MotionEvent.ACTION_POINTER_DOWN -> axis = Axis.BLOCKED
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            val intercepted = super.onInterceptTouchEvent(event)
            // Touching a settling page interrupts at its rendered position.
            if (intercepted) axis = Axis.HORIZONTAL
            return intercepted
        }
        return if (axis == Axis.HORIZONTAL || event.actionMasked == MotionEvent.ACTION_CANCEL || event.actionMasked == MotionEvent.ACTION_UP) {
            super.onInterceptTouchEvent(event)
        } else false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (axis == Axis.BLOCKED || axis == Axis.VERTICAL) {
            val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
            super.onTouchEvent(cancel)
            cancel.recycle()
            releaseEdge()
            return true
        }
        if (event.actionMasked == MotionEvent.ACTION_MOVE && axis == Axis.WAITING) return true
        if (event.actionMasked == MotionEvent.ACTION_MOVE && axis == Axis.HORIZONTAL) {
            val delta = event.x - lastX
            if (animationScale > 0f && edgeFeedback == PageEdgeFeedback.RESISTANCE &&
                !canScrollHorizontally(if (delta < 0f) 1 else -1)) {
                edgeOffset = (edgeOffset + delta * .22f).coerceIn(-width * .12f, width * .12f)
            } else edgeOffset = 0f
            invalidate()
        }
        lastX = event.x
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) releaseEdge()
        val handled = super.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP && axis == Axis.WAITING) performClick()
        return handled
    }

    override fun performClick(): Boolean = super.performClick()

    override fun dispatchDraw(canvas: Canvas) {
        canvas.withTranslation(edgeOffset, 0f) { super.dispatchDraw(this) }
    }

    private fun releaseEdge() {
        edgeAnimator?.cancel()
        if (animationScale <= 0f || edgeOffset == 0f) { edgeOffset = 0f; invalidate(); return }
        edgeAnimator = ValueAnimator.ofFloat(edgeOffset, 0f).apply {
            duration = (snapDurationMs * animationScale).toLong()
            interpolator = DecelerateInterpolator(1.5f)
            addUpdateListener { edgeOffset = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun smoothScrollBy(dx: Int, dy: Int) {
        if (animationScale <= 0f) scrollBy(dx, dy)
        else smoothScrollBy(dx, dy, DecelerateInterpolator(1.5f),
            (PageMotion.duration(dx, width, snapDurationMs) * animationScale).toInt().coerceAtLeast(1))
    }

    override fun onDetachedFromWindow() {
        edgeAnimator?.cancel()
        edgeAnimator = null
        edgeOffset = 0f
        super.onDetachedFromWindow()
    }
}
