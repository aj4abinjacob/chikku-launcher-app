package io.github.aj4abinjacob.chikku.home.gestures

import android.content.Context
import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.home.SwipeDirection
import io.github.aj4abinjacob.chikku.home.VerticalSwipeLayout
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Synthetic touch routing through the real View; physical gesture feel remains separate evidence. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExtraGestureTouchTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val surface = VerticalSwipeLayout(context).apply { layout(0, 0, 600, 1000) }
    private var horizontalEnabled = true
    private var doubleSwipeEnabled = true
    private val horizontal = mutableListOf<Boolean>()
    private val doubleSwipes = mutableListOf<SwipeDirection>()
    private val rotations = mutableListOf<MultiTouchGesture>()
    private var doubleTaps = 0
    private var ordinarySwipes = 0
    private var clock = 0L
    private var downTime = 0L

    init {
        surface.gestureListener = object : HomeGestureListener {
            override fun canStartMultiTouch() = true
            override fun onMultiTouchGesture(gesture: MultiTouchGesture) { rotations += gesture }
            override fun onDoubleTap() { doubleTaps++ }
            override fun canStartHorizontalSwipe(left: Boolean) = horizontalEnabled
            override fun onHorizontalSwipe(left: Boolean) { horizontal += left }
            override fun canStartDoubleTapSwipe(direction: SwipeDirection) = doubleSwipeEnabled
            override fun onDoubleTapSwipe(direction: SwipeDirection) { doubleSwipes += direction }
        }
        surface.listener = object : VerticalSwipeLayout.Listener {
            override fun canStartSwipe(direction: SwipeDirection) = true
            override fun onSwipeStart(direction: SwipeDirection) { ordinarySwipes++ }
            override fun onSwipeMove(offsetY: Float) = Unit
            override fun onSwipeEnd(velocityY: Float, cancelled: Boolean) = Unit
        }
    }

    private fun touch(action: Int, vararg points: Pair<Float, Float>) {
        clock += 50
        if (action == MotionEvent.ACTION_DOWN) downTime = clock
        val props = Array(points.size) { i -> MotionEvent.PointerProperties().apply {
            id = i; toolType = MotionEvent.TOOL_TYPE_FINGER
        } }
        val coords = Array(points.size) { i -> MotionEvent.PointerCoords().apply {
            x = points[i].first; y = points[i].second; pressure = 1f; size = 1f
        } }
        val event = MotionEvent.obtain(downTime, clock, action, points.size, props, coords,
            0, 0, 1f, 1f, 0, 0, 0, 0)
        try { surface.dispatchTouchEvent(event) } finally { event.recycle() }
    }

    @Test
    fun horizontalActionCommitsOnceAndCancelledOrReversedSwipeDoesNothing() {
        touch(MotionEvent.ACTION_DOWN, 400f to 400f)
        touch(MotionEvent.ACTION_MOVE, 300f to 400f)
        assertThat(horizontal).isEmpty()
        touch(MotionEvent.ACTION_MOVE, 200f to 400f)
        touch(MotionEvent.ACTION_UP, 200f to 400f)
        assertThat(horizontal).containsExactly(true)
        touch(MotionEvent.ACTION_DOWN, 200f to 400f)
        touch(MotionEvent.ACTION_MOVE, 400f to 400f)
        touch(MotionEvent.ACTION_CANCEL, 400f to 400f)
        touch(MotionEvent.ACTION_DOWN, 400f to 400f)
        touch(MotionEvent.ACTION_MOVE, 200f to 400f)
        touch(MotionEvent.ACTION_UP, 410f to 400f)
        assertThat(horizontal).containsExactly(true)
        assertThat(ordinarySwipes).isEqualTo(0)
    }

    @Test
    fun horizontalActionLeavesInteractiveChildrenTheirTouchStream() {
        val childTouches = mutableListOf<Int>()
        surface.addView(View(context).apply {
            isClickable = true
            setOnTouchListener { _, event -> childTouches += event.actionMasked; true }
            layout(0, 0, 600, 1000)
        })
        touch(MotionEvent.ACTION_DOWN, 400f to 400f)
        touch(MotionEvent.ACTION_MOVE, 200f to 400f)
        touch(MotionEvent.ACTION_UP, 200f to 400f)
        assertThat(horizontal).isEmpty()
        assertThat(childTouches).containsExactly(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP).inOrder()
    }

    @Test
    fun unassignedHorizontalSwipeDoesNotFireOrBecomeVertical() {
        horizontalEnabled = false
        touch(MotionEvent.ACTION_DOWN, 400f to 400f)
        touch(MotionEvent.ACTION_MOVE, 200f to 410f)
        touch(MotionEvent.ACTION_UP, 200f to 410f)
        assertThat(horizontal).isEmpty()
        assertThat(ordinarySwipes).isEqualTo(0)
    }

    @Test
    fun secondTapDragSuppressesOrdinaryDoubleTapAndVerticalSwipe() {
        touch(MotionEvent.ACTION_DOWN, 300f to 400f)
        touch(MotionEvent.ACTION_UP, 300f to 400f)
        touch(MotionEvent.ACTION_DOWN, 300f to 400f)
        touch(MotionEvent.ACTION_MOVE, 300f to 350f)
        touch(MotionEvent.ACTION_MOVE, 300f to 250f)
        touch(MotionEvent.ACTION_UP, 300f to 250f)
        assertThat(doubleSwipes).containsExactly(SwipeDirection.UP)
        assertThat(doubleTaps).isEqualTo(0)
        assertThat(ordinarySwipes).isEqualTo(0)
    }

    @Test
    fun unmappedSecondTapDragRetainsOrdinarySwipeAndCancelledMappedDragDoesNotFire() {
        doubleSwipeEnabled = false
        touch(MotionEvent.ACTION_DOWN, 300f to 400f)
        touch(MotionEvent.ACTION_UP, 300f to 400f)
        touch(MotionEvent.ACTION_DOWN, 300f to 400f)
        touch(MotionEvent.ACTION_MOVE, 300f to 250f)
        touch(MotionEvent.ACTION_UP, 300f to 250f)
        assertThat(ordinarySwipes).isEqualTo(1)
        doubleSwipeEnabled = true
        touch(MotionEvent.ACTION_DOWN, 300f to 400f)
        touch(MotionEvent.ACTION_UP, 300f to 400f)
        touch(MotionEvent.ACTION_DOWN, 300f to 400f)
        touch(MotionEvent.ACTION_MOVE, 300f to 250f)
        touch(MotionEvent.ACTION_CANCEL, 300f to 250f)
        assertThat(doubleSwipes).isEmpty()
        assertThat(doubleTaps).isEqualTo(0)
    }

    @Test
    fun rotationFiresOnceAndThirdFingerCancelsAmbiguousRotation() {
        val secondDown = MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        touch(MotionEvent.ACTION_DOWN, 200f to 400f)
        touch(secondDown, 200f to 400f, 400f to 400f)
        touch(MotionEvent.ACTION_MOVE, 229f to 329f, 371f to 471f)
        touch(MotionEvent.ACTION_MOVE, 250f to 310f, 350f to 490f)
        touch(MotionEvent.ACTION_UP, 250f to 310f)
        assertThat(rotations).containsExactly(MultiTouchGesture.ROTATE_CLOCKWISE)
        touch(MotionEvent.ACTION_DOWN, 200f to 400f)
        touch(secondDown, 200f to 400f, 400f to 400f)
        touch(MotionEvent.ACTION_POINTER_DOWN or (2 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            200f to 400f, 400f to 400f, 300f to 500f)
        touch(MotionEvent.ACTION_MOVE, 229f to 329f, 371f to 471f, 300f to 500f)
        touch(MotionEvent.ACTION_UP, 229f to 329f)
        assertThat(rotations).containsExactly(MultiTouchGesture.ROTATE_CLOCKWISE)
        assertThat(doubleTaps).isEqualTo(0)
    }
}
