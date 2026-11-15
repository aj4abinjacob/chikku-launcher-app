package io.github.aj4abinjacob.chikku.home.drawer

import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

/**
 * Switches drawer tabs on a horizontal swipe over a drawer list. The swipe is only claimed when
 * the list itself cannot scroll that way (always for the vertical list; at the first/last page of the
 * paged drawer), so paging keeps working. [onSwipe] gets +1 for the next tab and -1 for the
 * previous one, in reading order (mirrored in RTL).
 */
internal class DrawerTabSwipe(
    private val enabled: () -> Boolean,
    private val onSwipe: (delta: Int) -> Unit,
) : RecyclerView.OnItemTouchListener {

    private var downX = 0f
    private var downY = 0f
    private var tracking = false
    private var claimed = false
    private var velocityTracker: VelocityTracker? = null

    override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
        track(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x
                downY = e.y
                claimed = false
                tracking = enabled()
            }
            MotionEvent.ACTION_MOVE -> if (tracking && !claimed) {
                val slop = ViewConfiguration.get(rv.context).scaledTouchSlop
                val dx = e.x - downX
                val dy = e.y - downY
                when {
                    abs(dx) > slop && abs(dx) > abs(dy) * DOMINANCE -> {
                        if (rv.canScrollHorizontally(if (dx < 0) 1 else -1)) {
                            tracking = false // the list pages itself
                        } else {
                            claimed = true
                            rv.parent?.requestDisallowInterceptTouchEvent(true)
                        }
                    }
                    abs(dy) > slop -> tracking = false
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (!claimed) reset()
        }
        return claimed
    }

    override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
        track(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_UP -> {
                val dx = e.x - downX
                val tracker = velocityTracker
                tracker?.computeCurrentVelocity(1000)
                val vx = tracker?.xVelocity ?: 0f
                val minFling = ViewConfiguration.get(rv.context).scaledMinimumFlingVelocity * FLING_MULTIPLIER
                val farEnough = abs(dx) > rv.width * DISTANCE_FRACTION
                val flung = abs(vx) > minFling && (vx < 0) == (dx < 0)
                if (farEnough || flung) {
                    val rtl = rv.layoutDirection == View.LAYOUT_DIRECTION_RTL
                    val towardEnd = dx < 0
                    onSwipe(if (towardEnd != rtl) 1 else -1)
                }
                reset()
            }
            MotionEvent.ACTION_CANCEL -> reset()
        }
    }

    override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) = Unit

    private fun track(e: MotionEvent) {
        if (e.actionMasked == MotionEvent.ACTION_DOWN) velocityTracker?.clear()
        (velocityTracker ?: VelocityTracker.obtain().also { velocityTracker = it }).addMovement(e)
    }

    private fun reset() {
        claimed = false
        tracking = false
        velocityTracker?.recycle()
        velocityTracker = null
    }

    private companion object {
        const val DOMINANCE = 1.5f
        const val DISTANCE_FRACTION = 0.2f
        const val FLING_MULTIPLIER = 4
    }
}
