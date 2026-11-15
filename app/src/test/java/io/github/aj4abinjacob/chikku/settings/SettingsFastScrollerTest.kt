package io.github.aj4abinjacob.chikku.settings

import android.content.Context
import android.os.LocaleList
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.drawer.DrawerSection
import io.github.aj4abinjacob.chikku.home.drawer.DrawerFastScroller
import io.github.aj4abinjacob.chikku.platform.LocaleSectionIndex
import io.github.aj4abinjacob.chikku.settings.components.AppScrollIndex
import io.github.aj4abinjacob.chikku.settings.components.LazyScrollEstimate
import java.util.Locale
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The settings lists' scroll thumb: estimated metrics, jump letters and the bubble's host. Synthetic labels only. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SettingsFastScrollerTest {

    @Test fun estimateFollowsVisibleItemsAndReachesBothEnds() {
        // 100 rows of 50px, a 500px viewport, scrolled to row 10 plus 20px.
        val visible = (10..20).map { it to 50 }
        val estimate = LazyScrollEstimate.of(visible, firstOffset = 20, itemCount = 100, viewport = 500)
        assertThat(estimate).isEqualTo(LazyScrollEstimate(offset = 520, extent = 500, range = 5000))

        assertThat(estimate.target(0f, 100)).isEqualTo(0 to 0)
        assertThat(estimate.target(1f, 100)).isEqualTo(99 to 0)
        // Half of the 4500px scrollable range: row 45, no remainder.
        assertThat(estimate.target(0.5f, 100)).isEqualTo(45 to 0)
        assertThat(estimate.target(0.51f, 100)).isEqualTo(45 to 45)
    }

    @Test fun listThatFitsHasNothingToScroll() {
        val estimate = LazyScrollEstimate.of(listOf(0 to 50, 1 to 50), firstOffset = 0, itemCount = 2, viewport = 500)
        assertThat(estimate.range).isEqualTo(estimate.extent)
        assertThat(estimate.target(0.7f, 2)).isEqualTo(0 to 0)
        assertThat(LazyScrollEstimate.of(emptyList(), 0, 0, 0)).isEqualTo(LazyScrollEstimate(0, 0, 0))
    }

    @Test fun lettersSkipHeaderItemsAndALeadingGroup() {
        val labels = listOf("Zeta", "Alpha", "Apex", "Beta", "Bravo", "Delta")
        // Three header items, then "Zeta" leads as a pinned (for example locked) app.
        val index = AppScrollIndex(labels, start = 3, labelOf = { it }, alphabeticalFrom = 1, sectionIndex = english())
        assertThat(index.sections).containsExactly(
            DrawerSection("A", 4), DrawerSection("B", 6), DrawerSection("D", 8),
        ).inOrder()
        assertThat(index.letterAt(2)).isNull()
        assertThat(index.letterAt(3)).isEqualTo("Z")
        assertThat(index.letterAt(7)).isEqualTo("B")
        assertThat(index.letterAt(9)).isNull()
    }

    @Test fun aHeaderBetweenTheLeadingGroupAndTheAlphabeticalPartIsSkipped() {
        val labels = listOf("Zeta", "Alpha", "Beta")
        // Header at 3, "Zeta" at 4, the "Other apps" header at 5, then Alpha 6 and Beta 7.
        val index = AppScrollIndex(labels, start = 4, labelOf = { it }, alphabeticalFrom = 1, gap = 1, sectionIndex = english())
        assertThat(index.sections).containsExactly(DrawerSection("A", 6), DrawerSection("B", 7)).inOrder()
        assertThat(index.letterAt(4)).isEqualTo("Z")
        assertThat(index.letterAt(5)).isNull()
        assertThat(index.letterAt(6)).isEqualTo("A")
        assertThat(index.letterAt(7)).isEqualTo("B")
    }

    @Test fun bubbleDrawsInTheGivenHostBesideTheStrip() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val host = FrameLayout(context)
        // As inside a Compose AndroidView: the strip's parent is only as wide as the strip.
        val narrow = FrameLayout(context)
        val scroller = DrawerFastScroller(context).apply {
            thumbMode = true
            popupParent = host
            setSections(listOf(DrawerSection("A", 0), DrawerSection("B", 10)), 0xFF000000.toInt())
        }
        narrow.addView(scroller, FrameLayout.LayoutParams(40, 1000))
        host.addView(narrow, FrameLayout.LayoutParams(40, 1000).apply { leftMargin = 960 })
        host.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY))
        host.layout(0, 0, 1000, 1000)
        scroller.updateThumb(offset = 0, extent = 1000, range = 10_000, letter = "A")

        val time = SystemClock.uptimeMillis()
        fun touch(action: Int, y: Float) = MotionEvent.obtain(time, time, action, 20f, y, 0).also {
            scroller.dispatchTouchEvent(it)
            it.recycle()
        }
        touch(MotionEvent.ACTION_DOWN, 10f)
        touch(MotionEvent.ACTION_MOVE, 400f)
        scroller.updateThumb(offset = 3000, extent = 1000, range = 10_000, letter = "B")

        val bubble = popupBounds(scroller)
        assertThat(bubble).isNotNull()
        // Left of the strip at x=960, inside the host rather than the 40px-wide parent.
        assertThat(bubble!!.right).isAtMost(960)
        assertThat(bubble.left).isAtLeast(0)
        touch(MotionEvent.ACTION_UP, 400f)
    }

    private fun popupBounds(scroller: DrawerFastScroller): android.graphics.Rect? {
        var bounds: android.graphics.Rect? = null
        scroller.onBubbleMoved = { bounds = it }
        // Re-run a move so the callback reports the bubble now shown.
        val time = SystemClock.uptimeMillis()
        MotionEvent.obtain(time, time, MotionEvent.ACTION_MOVE, 20f, 420f, 0).also {
            scroller.dispatchTouchEvent(it)
            it.recycle()
        }
        scroller.updateThumb(offset = 3100, extent = 1000, range = 10_000, letter = "B")
        return bounds
    }

    private fun english() = LocaleSectionIndex { LocaleList(Locale.ENGLISH) }
}
