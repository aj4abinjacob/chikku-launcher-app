package io.github.aj4abinjacob.chikku.home

import android.content.Context
import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WorkspacePagerDiscoverTest {
    private fun pager(pages: Int = 1, rtl: Boolean = false): WorkspacePager =
        WorkspacePager(ApplicationProvider.getApplicationContext<Context>()).apply {
            layoutDirection = if (rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
            repeat(pages) { addView(CellLayout(context)) }
            measure(exact(1000), exact(1200))
            layout(0, 0, 1000, 1200)
        }

    private fun exact(size: Int) = View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)

    private fun swipe(pager: WorkspacePager, from: Float, to: Float, cancel: Boolean = false) {
        listOf(
            Triple(MotionEvent.ACTION_DOWN, from, 0L),
            Triple(MotionEvent.ACTION_MOVE, (from + to) / 2, 100L),
            Triple(MotionEvent.ACTION_MOVE, to, 200L),
            Triple(if (cancel) MotionEvent.ACTION_CANCEL else MotionEvent.ACTION_UP, to, 300L),
        ).forEach { (action, x, time) ->
            val event = MotionEvent.obtain(0, time, action, x, 400f, 0)
            pager.onTouchEvent(event)
            event.recycle()
        }
    }

    @Test
    fun outwardSwipeOpensFeedEvenWithOnePageAndDoesNotChangeWorkspace() {
        val pager = pager()
        var opens = 0
        pager.onStartEdgeSwipe = { opens++ }
        swipe(pager, 200f, 700f)
        assertThat(opens).isEqualTo(1)
        assertThat(pager.currentPage).isEqualTo(0)
        assertThat(pager.pageCount).isEqualTo(1)
        assertThat(pager.scrollX).isEqualTo(0)
    }

    @Test
    fun feedTakesPrecedenceOverInfiniteWrapOnlyAtPageZero() {
        val pager = pager(pages = 3)
        var opens = 0
        pager.onStartEdgeSwipe = { opens++ }
        pager.infiniteScroll = true
        swipe(pager, 200f, 800f)
        assertThat(opens).isEqualTo(1)
        pager.snapToPage(1, animate = false)
        swipe(pager, 200f, 800f)
        assertThat(opens).isEqualTo(1)
        assertThat(pager.currentPage).isEqualTo(0)
    }

    @Test
    fun disabledFeedKeepsInfiniteWrapAndInwardSwipeKeepsPaging() {
        val pager = pager(pages = 3)
        pager.infiniteScroll = true
        swipe(pager, 200f, 800f)
        assertThat(pager.currentPage).isEqualTo(2)
        pager.snapToPage(0, animate = false)
        var opens = 0
        pager.onStartEdgeSwipe = { opens++ }
        swipe(pager, 800f, 200f)
        assertThat(opens).isEqualTo(0)
        assertThat(pager.currentPage).isEqualTo(1)
    }

    @Test
    fun cancelledSwipeNeverOpensFeedAndRtlMirrorsItsDirection() {
        val pager = pager(rtl = true)
        var opens = 0
        pager.onStartEdgeSwipe = { opens++ }
        swipe(pager, 800f, 200f, cancel = true)
        assertThat(opens).isEqualTo(0)
        swipe(pager, 800f, 200f)
        assertThat(opens).isEqualTo(1)
    }
}
