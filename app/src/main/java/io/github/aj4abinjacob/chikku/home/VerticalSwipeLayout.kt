package io.github.aj4abinjacob.chikku.home

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.LinearLayout
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.home.gestures.DoubleTapTracker
import io.github.aj4abinjacob.chikku.home.gestures.HomeGestureListener
import io.github.aj4abinjacob.chikku.home.gestures.classifyMultiTouch
import io.github.aj4abinjacob.chikku.home.gestures.isSwipeCommitted
import io.github.aj4abinjacob.chikku.home.gestures.rotationDeltaDegrees
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Vertical [LinearLayout] that tracks a swipe up or down anywhere inside it, including over
 * children such as icons, the dock or a list. Children keep taps and long-presses; once a
 * movement is clearly vertical the gesture is taken from them (they receive ACTION_CANCEL) and
 * every further movement is reported to [listener], so the UI can follow the finger.
 *
 * A child that starts a horizontal gesture (the workspace pager) or a scroll (the drawer list)
 * calls `requestDisallowInterceptTouchEvent`, which keeps that gesture away from this layout.
 *
 * With a [gestureListener] it also recognises two-finger pinches, swipes and rotations, plus empty
 * space double taps and tap-then-drag actions. Assigned horizontal actions claim empty space;
 * other horizontal gestures stay with the pager and children.
 */
class VerticalSwipeLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    interface Listener {
        /**
         * Whether a swipe in [direction] may start now, e.g. false while a list can still scroll.
         * [touchDownTarget] is available while this is called.
         */
        fun canStartSwipe(direction: SwipeDirection): Boolean

        fun onSwipeStart(direction: SwipeDirection)

        /** [offsetY] is the signed finger movement since the swipe was recognised (up is negative). */
        fun onSwipeMove(offsetY: Float)

        /** [velocityY] in px/s (up is negative); [cancelled] when the system took the gesture away. */
        fun onSwipeEnd(velocityY: Float, cancelled: Boolean)
    }

    var listener: Listener? = null

    /** Additional home gestures; null (the drawer) disables them. */
    var gestureListener: HomeGestureListener? = null

    /** The clickable descendant (icon, button) under the first finger of the current gesture, if any. */
    var touchDownTarget: View? = null
        private set

    private val config = ViewConfiguration.get(context)
    private val touchSlop = config.scaledTouchSlop.toFloat()
    private val multiTouchDistance = touchSlop * MULTI_TOUCH_SLOP_FACTOR
    private val commitDistance = resources.getDimension(R.dimen.gestures_min_swipe_distance)
    private val commitVelocity = config.scaledMinimumFlingVelocity * 4f
    private val tapTracker = DoubleTapTracker(
        maxTapDurationMs = ViewConfiguration.getLongPressTimeout().toLong(),
        doubleTapTimeoutMs = ViewConfiguration.getDoubleTapTimeout().toLong(),
        touchSlop = touchSlop,
        doubleTapSlop = config.scaledDoubleTapSlop.toFloat(),
    )

    private var downX = 0f
    private var downY = 0f
    private var startY = 0f
    private var direction = SwipeDirection.NONE
    private var velocityTracker: VelocityTracker? = null
    private var multi: MultiTouch? = null
    private var extra: ExtraSwipe? = null
    private var secondTapCandidate = false
    private var extraAborted = false
    private var latestX = 0f
    private var latestY = 0f

    private sealed interface ExtraSwipe {
        data class Horizontal(val left: Boolean) : ExtraSwipe
        data class DoubleTapVertical(val direction: SwipeDirection) : ExtraSwipe
    }

    /** The two tracked pointers of a two-finger gesture and where they started. */
    private class MultiTouch(val idA: Int, val idB: Int, val startSpan: Float, val startCx: Float, val startCy: Float,
        var previousAngle: Float,
    ) {
        var fired = false
        var rotationDegrees = 0f
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) touchDownTarget = clickableDescendantAt(this, ev.x, ev.y)
        if (gestureListener != null) observeTaps(ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        track(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> begin(ev)
            MotionEvent.ACTION_POINTER_DOWN -> maybeBeginMultiTouch(ev)
            MotionEvent.ACTION_MOVE -> if (direction == SwipeDirection.NONE && multi == null && extra == null) recognise(ev)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (direction == SwipeDirection.NONE && extra == null) reset()
        }
        return direction != SwipeDirection.NONE || multi != null || extra != null
    }

    // Swipes are also exposed as buttons/Back for accessibility, so no performClick is needed here.
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        track(ev)
        val multiTouch = multi
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> begin(ev)
            MotionEvent.ACTION_POINTER_DOWN -> maybeBeginMultiTouch(ev)
            MotionEvent.ACTION_POINTER_UP -> multi?.fired = true
            MotionEvent.ACTION_MOVE -> when {
                multiTouch != null -> updateMultiTouch(ev, multiTouch)
                extra != null -> Unit
                direction == SwipeDirection.NONE -> recognise(ev)
                else -> listener?.onSwipeMove(ev.y - startY)
            }
            MotionEvent.ACTION_UP -> finish(cancelled = false)
            MotionEvent.ACTION_CANCEL -> finish(cancelled = true)
        }
        // Keep receiving the gesture over empty areas that no child consumed.
        return true
    }

    private fun begin(ev: MotionEvent) {
        downX = ev.x
        downY = ev.y
        direction = SwipeDirection.NONE
        multi = null
        extra = null
        extraAborted = false
    }

    private fun recognise(ev: MotionEvent) {
        if (touchDownTarget == null && gestureListener != null) {
            val vertical = classifyVerticalSwipe(ev.x - downX, ev.y - downY, touchSlop)
            extra = when {
                secondTapCandidate && vertical != SwipeDirection.NONE &&
                    gestureListener?.canStartDoubleTapSwipe(vertical) == true -> ExtraSwipe.DoubleTapVertical(vertical)
                abs(ev.x - downX) > touchSlop && abs(ev.x - downX) > abs(ev.y - downY) &&
                    gestureListener?.canStartHorizontalSwipe(ev.x < downX) == true -> ExtraSwipe.Horizontal(ev.x < downX)
                else -> null
            }
            if (extra != null) {
                tapTracker.cancel()
                parent?.requestDisallowInterceptTouchEvent(true)
                return
            }
        }
        val candidate = classifyVerticalSwipe(ev.x - downX, ev.y - downY, touchSlop)
        if (candidate == SwipeDirection.NONE || listener?.canStartSwipe(candidate) != true) return
        direction = candidate
        // Measure from here so the UI does not jump by the touch slop.
        startY = ev.y
        parent?.requestDisallowInterceptTouchEvent(true)
        listener?.onSwipeStart(candidate)
    }

    private fun maybeBeginMultiTouch(ev: MotionEvent) {
        if (extra != null) extraAborted = true
        if (ev.pointerCount != 2) {
            multi?.fired = true // A third pointer makes the two-finger gesture ambiguous.
            return
        }
        if (multi != null || direction != SwipeDirection.NONE || extra != null) return
        if (gestureListener?.canStartMultiTouch() != true) return
        val (cx, cy) = centroid(ev, 0, 1)
        multi = MultiTouch(ev.getPointerId(0), ev.getPointerId(1), span(ev, 0, 1), cx, cy, angle(ev, 0, 1))
        parent?.requestDisallowInterceptTouchEvent(true)
    }

    private fun updateMultiTouch(ev: MotionEvent, state: MultiTouch) {
        if (state.fired) return
        val a = ev.findPointerIndex(state.idA)
        val b = ev.findPointerIndex(state.idB)
        if (a < 0 || b < 0) return
        val (cx, cy) = centroid(ev, a, b)
        val angle = angle(ev, a, b)
        state.rotationDegrees += rotationDeltaDegrees(state.previousAngle, angle)
        state.previousAngle = angle
        val gesture = classifyMultiTouch(state.startSpan, span(ev, a, b), cx - state.startCx, cy - state.startCy,
            multiTouchDistance, state.rotationDegrees)
            ?: return
        state.fired = true
        gestureListener?.onMultiTouchGesture(gesture)
    }

    private fun finish(cancelled: Boolean) {
        val extraSwipe = extra
        extra = null // Mark complete before an action can synchronously change the touch stream.
        if (extraSwipe != null && !cancelled && !extraAborted) {
            velocityTracker?.computeCurrentVelocity(1000)
            when (extraSwipe) {
                is ExtraSwipe.Horizontal -> {
                    val offset = latestX - downX
                    val velocity = velocityTracker?.xVelocity ?: 0f
                    val signedOffset = if (extraSwipe.left) -offset else offset
                    val signedVelocity = if (extraSwipe.left) -velocity else velocity
                    if (signedOffset >= commitDistance || (signedOffset > 0 && signedVelocity > commitVelocity)) {
                        gestureListener?.onHorizontalSwipe(extraSwipe.left)
                    }
                }
                is ExtraSwipe.DoubleTapVertical -> if (isSwipeCommitted(extraSwipe.direction,
                    latestY - downY, velocityTracker?.yVelocity ?: 0f, commitDistance, commitVelocity)) {
                    gestureListener?.onDoubleTapSwipe(extraSwipe.direction)
                }
            }
        }
        if (direction != SwipeDirection.NONE) {
            val tracker = velocityTracker
            tracker?.computeCurrentVelocity(1000)
            listener?.onSwipeEnd(tracker?.yVelocity ?: 0f, cancelled)
        }
        reset()
    }

    private fun observeTaps(ev: MotionEvent) {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                tapTracker.onDown(ev.x, ev.y, ev.eventTime, overInteractiveChild = touchDownTarget != null)
                secondTapCandidate = tapTracker.isSecondTap
            }
            MotionEvent.ACTION_MOVE -> tapTracker.onMove(ev.x, ev.y)
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> {
                tapTracker.cancel()
                secondTapCandidate = false
            }
            MotionEvent.ACTION_UP -> {
                if (tapTracker.onUp(ev.eventTime)) gestureListener?.onDoubleTap()
                if (tapTracker.lastUpWasTap) gestureListener?.onEmptyTap(ev.rawX.toInt(), ev.rawY.toInt())
            }
        }
    }

    private fun track(ev: MotionEvent) {
        latestX = ev.x
        latestY = ev.y
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) velocityTracker?.clear()
        (velocityTracker ?: VelocityTracker.obtain().also { velocityTracker = it }).addMovement(ev)
    }

    private fun reset() {
        direction = SwipeDirection.NONE
        multi = null
        extra = null
        velocityTracker?.recycle()
        velocityTracker = null
    }

    private companion object {
        /** Two-finger gestures need a bit more travel than a tap's slop to tell pinch from swipe. */
        const val MULTI_TOUCH_SLOP_FACTOR = 3f

        fun angle(ev: MotionEvent, a: Int, b: Int): Float =
            Math.toDegrees(atan2(ev.getY(b) - ev.getY(a), ev.getX(b) - ev.getX(a)).toDouble()).toFloat()

        fun span(ev: MotionEvent, a: Int, b: Int): Float = hypot(ev.getX(a) - ev.getX(b), ev.getY(a) - ev.getY(b))

        fun centroid(ev: MotionEvent, a: Int, b: Int): Pair<Float, Float> =
            (ev.getX(a) + ev.getX(b)) / 2f to (ev.getY(a) + ev.getY(b)) / 2f

        /**
         * Deepest visible clickable or long-clickable view at ([x], [y]) in [group]'s coordinates.
         * Grid pages ([CellLayout]) are empty space even though they long-click for the home menu.
         */
        fun clickableDescendantAt(group: ViewGroup, x: Float, y: Float): View? {
            for (i in group.childCount - 1 downTo 0) {
                val child = group.getChildAt(i)
                if (child.visibility != View.VISIBLE) continue
                val cx = x + group.scrollX - child.left - child.translationX
                val cy = y + group.scrollY - child.top - child.translationY
                if (cx < 0f || cy < 0f || cx >= child.width || cy >= child.height) continue
                if (child is ViewGroup) clickableDescendantAt(child, cx, cy)?.let { return it }
                if (child !is CellLayout && (child.isClickable || child.isLongClickable)) return child
            }
            return null
        }
    }
}

enum class SwipeDirection { NONE, UP, DOWN }

/** Classifies a movement as a vertical swipe once it passes [slop] and is mostly vertical. */
internal fun classifyVerticalSwipe(dx: Float, dy: Float, slop: Float): SwipeDirection = when {
    abs(dy) <= slop || abs(dy) <= abs(dx) -> SwipeDirection.NONE
    dy < 0 -> SwipeDirection.UP
    else -> SwipeDirection.DOWN
}
