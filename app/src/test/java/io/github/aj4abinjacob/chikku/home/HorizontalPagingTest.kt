package io.github.aj4abinjacob.chikku.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.settings.PageEdgeFeedback
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HorizontalPagingTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun layout(view: View, height: Int = 300) {
        view.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 400, height)
    }
    private fun pager(rtl: Boolean = false) = WorkspacePager(context).apply {
        wallpaperUpdatesEnabled = false
        layoutDirection = if (rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        listOf(Color.RED, Color.GREEN, Color.BLUE).forEach { color -> addView(View(context).apply { setBackgroundColor(color) }) }
        layout(this)
    }
    private fun touch(view: View, action: Int, x: Float, time: Long, y: Float = 100f) {
        MotionEvent.obtain(0, time, action, x, y, 0).also { view.dispatchTouchEvent(it); it.recycle() }
    }
    private fun settle(pager: WorkspacePager) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        pager.computeScroll()
        pager.computeScroll()
    }

    @Test fun wrappingDrawsTheOriginalAdjacentPageAndSettlesInBothReadingDirections() {
        for (rtl in listOf(false, true)) {
            val pager = pager(rtl).apply { infiniteScroll = true }
            val pages = (0 until pager.childCount).map(pager::getChildAt)
            val start = if (rtl) 800 else 0
            pager.scrollTo(start + if (rtl) 200 else -200, 0)
            val bitmap = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888)
            pager.draw(Canvas(bitmap))
            assertThat(bitmap.getPixel(if (rtl) 300 else 100, 150)).isEqualTo(Color.BLUE)
            assertThat(pager.getChildAt(2).translationX).isEqualTo(if (rtl) 1200f else -1200f)
            pager.snapToPage(2)
            // The animation continues outwards rather than traversing all stored pages backwards.
            settle(pager)
            assertThat(pager.currentPage).isEqualTo(2)
            assertThat(pager.scrollX).isEqualTo(if (rtl) 0 else 800)
            assertThat((0 until pager.childCount).map(pager::getChildAt)).containsExactlyElementsIn(pages).inOrder()
            assertThat(pages.map { it.translationX }).containsExactly(0f, 0f, 0f)
            bitmap.recycle()
        }
    }

    @Test fun cancelledWrapReturnsToTheStartingPageAndDisabledMotionNeverLeavesEdgeOffsets() {
        val pager = pager().apply { infiniteScroll = true; snapDurationScale = 0f }
        touch(pager, MotionEvent.ACTION_DOWN, 80f, 0)
        touch(pager, MotionEvent.ACTION_MOVE, 160f, 100)
        touch(pager, MotionEvent.ACTION_MOVE, 380f, 200)
        assertThat(pager.scrollX).isLessThan(0)
        touch(pager, MotionEvent.ACTION_CANCEL, 380f, 300)
        assertThat(pager.currentPage).isEqualTo(0)
        assertThat(pager.scrollX).isEqualTo(0)
        pager.infiniteScroll = false
        pager.edgeFeedback = PageEdgeFeedback.RESISTANCE
        touch(pager, MotionEvent.ACTION_DOWN, 80f, 400)
        touch(pager, MotionEvent.ACTION_MOVE, 160f, 500)
        touch(pager, MotionEvent.ACTION_MOVE, 380f, 600)
        assertThat(pager.scrollX).isEqualTo(0)
    }

    @Test fun diagonalSwipeDoesNotMovePagesAndInterruptedSettleDoesNotJumpToItsTarget() {
        val pager = pager()
        touch(pager, MotionEvent.ACTION_DOWN, 350f, 0)
        touch(pager, MotionEvent.ACTION_MOVE, 250f, 100, 190f)
        touch(pager, MotionEvent.ACTION_UP, 250f, 200, 190f)
        assertThat(pager.currentPage).isEqualTo(0)
        assertThat(pager.scrollX).isEqualTo(0)
        pager.snapToPage(2)
        touch(pager, MotionEvent.ACTION_DOWN, 200f, 400)
        assertThat(pager.scrollX).isEqualTo(0)
        touch(pager, MotionEvent.ACTION_CANCEL, 200f, 500)
        settle(pager)
        assertThat(pager.currentPage).isEqualTo(0)
    }

    @Test fun clearingPagesDuringASwipeHandlesReleaseAndCancellationAndAllowsRecovery() {
        for (rtl in listOf(false, true)) for (wrap in listOf(false, true)) {
            for (terminal in listOf(MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL)) {
                val pager = pager(rtl).apply { infiniteScroll = wrap; snapDurationScale = 0f }
                val from = if (rtl) 50f else 350f
                val to = if (rtl) 300f else 100f
                touch(pager, MotionEvent.ACTION_DOWN, from, 0)
                touch(pager, MotionEvent.ACTION_MOVE, to, 100)
                pager.removeAllViews()
                touch(pager, terminal, to, 200)
                assertThat(pager.pageCount).isEqualTo(0)
                assertThat(pager.currentPage).isEqualTo(0)
                assertThat(pager.scrollX).isEqualTo(0)

                repeat(3) { pager.addView(View(context)) }
                layout(pager)
                touch(pager, MotionEvent.ACTION_DOWN, from, 300)
                touch(pager, MotionEvent.ACTION_MOVE, to, 400)
                touch(pager, MotionEvent.ACTION_UP, to, 500)
                assertThat(pager.currentPage).isEqualTo(1)
            }
        }
    }

    @Test fun coalescedSingleMoveSwipeKeepsItsDistanceAndAmbiguousStartCanBecomeHorizontal() {
        val pager = pager().apply { snapDurationScale = 0f }
        touch(pager, MotionEvent.ACTION_DOWN, 350f, 0)
        touch(pager, MotionEvent.ACTION_MOVE, 100f, 100)
        touch(pager, MotionEvent.ACTION_UP, 100f, 200)
        assertThat(pager.currentPage).isEqualTo(1)
        pager.snapToPage(0, animate = false)
        touch(pager, MotionEvent.ACTION_DOWN, 350f, 300)
        touch(pager, MotionEvent.ACTION_MOVE, 330f, 400, 119f)
        touch(pager, MotionEvent.ACTION_MOVE, 100f, 500, 120f)
        touch(pager, MotionEvent.ACTION_UP, 100f, 600, 120f)
        assertThat(pager.currentPage).isEqualTo(1)
    }

    @Test fun clearingDuringAnimatedSettlingCannotScrollTheRecoveredPages() {
        val pager = pager()
        pager.snapToPage(2)
        pager.removeAllViews()
        repeat(3) { pager.addView(View(context)) }
        layout(pager)
        settle(pager)
        assertThat(pager.currentPage).isEqualTo(0)
        assertThat(pager.scrollX).isEqualTo(0)
    }

    @Test fun indicatorSupportsTapScrubRtlAndAccessibleDirectSelection() {
        for (rtl in listOf(false, true)) {
            val indicator = PageIndicatorView(context).apply {
                layoutDirection = if (rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
                setPages(0, 3)
            }
            layout(indicator, 48)
            val selected = mutableListOf<Int>()
            indicator.onPageSelected = { selected += it; indicator.setPages(it, 3) }
            val end = if (rtl) 0f else 400f
            touch(indicator, MotionEvent.ACTION_DOWN, end, 0)
            touch(indicator, MotionEvent.ACTION_UP, end, 100)
            assertThat(selected.last()).isEqualTo(2)
            touch(indicator, MotionEvent.ACTION_DOWN, end, 200)
            touch(indicator, MotionEvent.ACTION_MOVE, if (rtl) 400f else 0f, 300)
            touch(indicator, MotionEvent.ACTION_UP, if (rtl) 400f else 0f, 400)
            assertThat(selected.last()).isEqualTo(0)
            val arguments = Bundle().apply { putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, 2f) }
            assertThat(indicator.performAccessibilityAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id, arguments)).isTrue()
            assertThat(selected.last()).isEqualTo(1)
            indicator.setScrollPosition(1.5f)
            assertThat(indicator.scrollPosition).isEqualTo(1.5f)
        }
    }

    @Test fun autoHiddenIndicatorOnlyTakesTheRoomItsDotsNeed() {
        val indicator = PageIndicatorView(context).apply { setPadding(0, 10, 0, 10); setPages(0, 3) }
        val unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        indicator.measure(unspecified, unspecified)
        val touchTarget = (48 * context.resources.displayMetrics.density).toInt()
        assertThat(indicator.measuredHeight).isEqualTo(touchTarget)
        indicator.autoHide = true
        assertThat(indicator.isLayoutRequested).isTrue()
        indicator.measure(unspecified, unspecified)
        assertThat(indicator.measuredHeight).isLessThan(touchTarget)
        assertThat(indicator.measuredHeight).isAtLeast(20 + (2 * context.resources.getDimension(io.github.aj4abinjacob.chikku.R.dimen.home_page_dot_size)).toInt())
    }

    @Test fun activeDotStretchesTowardTheNextPageAndSettlesOnIt() {
        assertThat(PageIndicatorView.activeDotSpan(0f)).isEqualTo(0f to 0f)
        assertThat(PageIndicatorView.activeDotSpan(.25f)).isEqualTo(0f to .5f)
        assertThat(PageIndicatorView.activeDotSpan(.5f)).isEqualTo(0f to 1f)
        assertThat(PageIndicatorView.activeDotSpan(.75f)).isEqualTo(.5f to 1f)
        assertThat(PageIndicatorView.activeDotSpan(1f)).isEqualTo(1f to 1f)
    }
}
