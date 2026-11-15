package io.github.aj4abinjacob.chikku.home.appmenu

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Long-press menu → drag on move, with icon swipe and folder swipe handling around it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LongPressMenuDragTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private class RecordingParent(context: android.content.Context) : FrameLayout(context) {
        var disallowed = false
        override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
            disallowed = disallowIntercept
            super.requestDisallowInterceptTouchEvent(disallowIntercept)
        }
    }

    private fun event(action: Int, x: Float, y: Float): MotionEvent {
        val now = SystemClock.uptimeMillis()
        return MotionEvent.obtain(now, now, action, x, y, 0).apply { setLocation(x, y) }
    }

    private class Setup(val parent: RecordingParent, val view: View, val helper: LongPressMenuDrag) {
        var menus = 0
        var dismissed = 0
        var drags = 0
    }

    private fun setup(canDrag: Boolean = true, withMenu: Boolean = true): Setup {
        val parent = RecordingParent(context)
        val view = View(context)
        parent.addView(view)
        lateinit var s: Setup
        val helper = LongPressMenuDrag.attach(
            view,
            showMenu = { if (withMenu) AppMenuHandle { s.dismissed++ }.also { s.menus++ } else null },
            startDrag = { s.drags++; true },
            canDragFromMenu = { canDrag },
        )
        s = Setup(parent, view, helper)
        return s
    }

    @Test
    fun longPressOpensMenuAndClaimsTheGestureThenMoveStartsDrag() {
        val s = setup()
        s.view.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, 10f, 10f))
        assertThat(s.view.performLongClick()).isTrue()
        assertThat(s.menus).isEqualTo(1)
        // Ancestors (gesture surface, pager) no longer intercept: no icon swipe action mid-drag.
        assertThat(s.parent.disallowed).isTrue()
        s.view.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE, 10f, 200f))
        assertThat(s.dismissed).isEqualTo(1)
        assertThat(s.drags).isEqualTo(1)
    }

    @Test
    fun withoutMenuLongPressDragsAtOnce() {
        val s = setup(withMenu = false)
        assertThat(s.view.performLongClick()).isTrue()
        assertThat(s.drags).isEqualTo(1)
    }

    @Test
    fun lockedLayoutKeepsTheMenuOpenOnMove() {
        val s = setup(canDrag = false)
        s.view.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, 10f, 10f))
        s.view.performLongClick()
        s.view.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE, 10f, 200f))
        assertThat(s.dismissed).isEqualTo(0)
        assertThat(s.drags).isEqualTo(0)
    }

    @Test
    fun secondaryTouchHandlerSeesEventsUntilAMenuIsOpen() {
        val s = setup()
        val seen = mutableListOf<Int>()
        s.helper.secondary = View.OnTouchListener { _, e -> seen += e.actionMasked; e.actionMasked == MotionEvent.ACTION_MOVE }
        val down = event(MotionEvent.ACTION_DOWN, 10f, 10f)
        val move = event(MotionEvent.ACTION_MOVE, 10f, 100f)
        assertThat(s.helper.onTouch(s.view, down)).isFalse()
        assertThat(s.helper.onTouch(s.view, move)).isTrue() // e.g. a folder swipe consumed it
        s.view.performLongClick()
        assertThat(s.helper.onTouch(s.view, move)).isFalse() // menu open: the menu/drag owns the gesture
        assertThat(seen).containsExactly(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE).inOrder()
    }
}
