package io.github.aj4abinjacob.chikku.home

import android.content.Context
import android.os.Looper
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.settings.PageTransitionEffect
import java.time.Duration
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WorkspacePagerRemovalTest {
    private fun pager(pages: Int): WorkspacePager =
        WorkspacePager(ApplicationProvider.getApplicationContext<Context>()).apply {
            repeat(pages) { addView(CellLayout(context)) }
            measure(exact(1000), exact(1200))
            layout(0, 0, 1000, 1200)
        }

    private fun exact(size: Int) = View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)

    private fun advance(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    @Test
    fun removedPageFadesOutBeforeTheLayoutChangesAndTheNextPageSlidesIn() {
        val pager = pager(pages = 3)
        pager.snapToPage(1, animate = false)
        var committed = 0
        pager.animatePageRemoval(1) { committed++ }
        assertThat(committed).isEqualTo(0) // nothing changes until the page has visibly gone
        // An empty page has nothing to shrink, so an outline shows the page leaving.
        assertThat(pager.getChildAt(1).getTag(R.id.page_removal_outline)).isNotNull()
        advance(500)
        assertThat(committed).isEqualTo(1)
        assertThat(pager.getChildAt(1).alpha).isEqualTo(0f)

        pager.removeViewAt(2) // what WorkspaceController does when the layout has one page fewer
        val incoming = pager.getChildAt(1)
        assertThat(pager.currentPage).isEqualTo(1)
        assertThat(incoming.translationX).isGreaterThan(0f)
        val initialTranslation = incoming.translationX
        pager.measure(exact(1000), exact(1200))
        pager.layout(0, 0, 1000, 1200)
        assertThat(incoming.translationX).isEqualTo(initialTranslation)
        assertThat(incoming.alpha).isEqualTo(0f)
        advance(500)
        assertThat(incoming.translationX).isEqualTo(0f)
        assertThat(incoming.alpha).isEqualTo(1f)
        assertThat(incoming.scaleX).isEqualTo(1f)
        assertThat(incoming.getTag(R.id.page_removal_outline)).isNull()
    }

    @Test
    fun disablingMotionDuringIncomingSlideRestoresThePageImmediately() {
        val pager = pager(pages = 3)
        pager.snapToPage(1, animate = false)
        pager.animatePageRemoval(1) { }
        advance(500)
        pager.removeViewAt(2)
        val incoming = pager.getChildAt(1)
        assertThat(incoming.translationX).isGreaterThan(0f)
        pager.snapDurationScale = 0f
        assertThat(incoming.translationX).isEqualTo(0f)
        assertThat(incoming.alpha).isEqualTo(1f)
        advance(500)
        assertThat(incoming.translationX).isEqualTo(0f)
    }

    @Test
    fun incomingSlideSurvivesRtlLayoutAndReturnsControlToPageEffects() {
        val pager = pager(pages = 3).apply { layoutDirection = View.LAYOUT_DIRECTION_RTL }
        pager.measure(exact(1000), exact(1200))
        pager.layout(0, 0, 1000, 1200)
        pager.snapToPage(1, animate = false)
        pager.animatePageRemoval(1) { }
        advance(500)
        pager.removeViewAt(2)
        val incoming = pager.getChildAt(1)
        val translation = incoming.translationX
        assertThat(translation).isLessThan(0f)
        pager.measure(exact(1000), exact(1200))
        pager.layout(0, 0, 1000, 1200)
        assertThat(incoming.translationX).isEqualTo(translation)
        advance(500)
        assertThat(incoming.translationX).isEqualTo(0f)
        assertThat(incoming.alpha).isEqualTo(1f)

        pager.transitionEffect = PageTransitionEffect.CUBE
        pager.scrollTo(200, 0)
        assertThat(incoming.rotationY).isNotEqualTo(0f)
    }

    @Test
    fun navigationCancelsIncomingSlideWithoutLeavingOffsetsOrTransparency() {
        val pager = pager(pages = 3)
        pager.snapToPage(1, animate = false)
        pager.animatePageRemoval(1) { }
        advance(500)
        pager.removeViewAt(2)
        val incoming = pager.getChildAt(1)
        assertThat(incoming.translationX).isGreaterThan(0f)
        pager.snapToPage(0, animate = false)
        assertThat(incoming.translationX).isEqualTo(0f)
        assertThat(incoming.alpha).isEqualTo(1f)
        advance(500)
        assertThat(pager.currentPage).isEqualTo(0)
        assertThat(pager.scrollX).isEqualTo(0)
        assertThat(incoming.translationX).isEqualTo(0f)
    }

    @Test
    fun removingTheLastPageGlidesBackToThePreviousPage() {
        val pager = pager(pages = 3)
        pager.snapToPage(2, animate = false)
        pager.animatePageRemoval(2) { pager.removeViewAt(2) }
        advance(500)
        assertThat(pager.currentPage).isEqualTo(1)
        assertThat(pager.getChildAt(1).alpha).isEqualTo(1f)
    }

    @Test
    fun refusedRemovalBringsThePageBack() {
        val pager = pager(pages = 2)
        pager.snapToPage(1, animate = false)
        pager.animatePageRemoval(1) { }
        advance(500)
        pager.cancelPageRemoval()
        advance(500)
        val page = pager.getChildAt(1)
        assertThat(page.alpha).isEqualTo(1f)
        assertThat(page.scaleX).isEqualTo(1f)
        assertThat(page.getTag(R.id.page_removal_outline)).isNull()
    }

    @Test
    fun removalThatNeverArrivesRestoresThePageByItself() {
        val pager = pager(pages = 2)
        pager.animatePageRemoval(1) { }
        advance(3_000)
        assertThat(pager.getChildAt(1).alpha).isEqualTo(1f)
    }

    @Test
    fun withAnimationsOffTheRemovalCommitsImmediately() {
        val pager = pager(pages = 2)
        pager.snapDurationScale = 0f
        var committed = false
        pager.animatePageRemoval(1) { committed = true }
        assertThat(committed).isTrue()
    }

    @Test
    fun addedPageIsShownAndGrowsInFromAnOutlineThatThenGoes() {
        val pager = pager(pages = 2)
        pager.addView(CellLayout(pager.context)) // added in this frame: not laid out yet
        pager.showAddedPage(2)
        pager.measure(exact(1000), exact(1200))
        pager.layout(0, 0, 1000, 1200)
        assertThat(pager.currentPage).isEqualTo(2)
        val page = pager.getChildAt(2)
        assertThat(page.getTag(R.id.page_removal_outline)).isNotNull()
        assertThat(page.scaleX).isLessThan(1f)
        advance(2_000)
        assertThat(page.scaleX).isEqualTo(1f)
        assertThat(page.getTag(R.id.page_removal_outline)).isNull()
    }

    @Test
    fun withAnimationsOffAnAddedPageIsJustShown() {
        val pager = pager(pages = 2)
        pager.snapDurationScale = 0f
        pager.showAddedPage(1)
        assertThat(pager.currentPage).isEqualTo(1)
        assertThat(pager.getChildAt(1).getTag(R.id.page_removal_outline)).isNull()
    }
}
