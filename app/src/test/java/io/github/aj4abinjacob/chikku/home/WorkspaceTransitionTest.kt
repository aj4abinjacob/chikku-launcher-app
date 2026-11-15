package io.github.aj4abinjacob.chikku.home

import android.content.Context
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.settings.PageTransitionEffect
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WorkspaceTransitionTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val pager = WorkspacePager(context).apply {
        repeat(3) { addView(View(context)) }
        measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
        layout(0, 0, 400, 800)
    }

    private fun assertUntransformed() {
        repeat(pager.childCount) { index ->
            val page = pager.getChildAt(index)
            assertThat(page.alpha).isEqualTo(1f)
            assertThat(page.scaleX).isEqualTo(1f)
            assertThat(page.scaleY).isEqualTo(1f)
            assertThat(page.rotationY).isEqualTo(0f)
        }
    }

    @Test
    fun defaultSlidePreservesNormalPagingAndPageIdentity() {
        val pages = (0 until pager.childCount).map(pager::getChildAt)
        pager.scrollTo(200, 0)
        assertUntransformed()
        assertThat(pager.currentPage).isEqualTo(0)
        pager.snapToPage(1, animate = false)
        assertThat(pager.scrollX).isEqualTo(400)
        assertThat(pager.currentPage).isEqualTo(1)
        assertThat((0 until pager.childCount).map(pager::getChildAt)).containsExactlyElementsIn(pages).inOrder()
        assertUntransformed()
    }

    @Test
    fun reducedMotionClearsEveryEffectAndReenableRestoresSelectedEffect() {
        pager.scrollTo(200, 0)
        PageTransitionEffect.entries.filter { it != PageTransitionEffect.SLIDE }.forEach { effect ->
            pager.snapDurationScale = 1f
            pager.transitionEffect = effect
            val page = pager.getChildAt(0)
            assertThat(page.alpha != 1f || page.scaleX != 1f || page.rotationY != 0f).isTrue()
            pager.snapDurationScale = 0f
            assertUntransformed()
            assertThat(pager.scrollX).isEqualTo(200)
            assertThat(pager.transitionEffect).isEqualTo(effect)
            pager.snapDurationScale = 1f
            assertThat(page.alpha != 1f || page.scaleX != 1f || page.rotationY != 0f).isTrue()
        }
    }

    @Test
    fun changingEffectsRemovesTransformsFromPreviousEffect() {
        pager.scrollTo(200, 0)
        val page = pager.getChildAt(0)
        pager.transitionEffect = PageTransitionEffect.CUBE
        assertThat(page.rotationY).isNotEqualTo(0f)
        pager.transitionEffect = PageTransitionEffect.ZOOM
        assertThat(page.rotationY).isEqualTo(0f)
        assertThat(page.pivotX).isEqualTo(page.width / 2f)
        assertThat(page.scaleX).isLessThan(1f)
        pager.transitionEffect = PageTransitionEffect.FADE
        assertThat(page.scaleX).isEqualTo(1f)
        assertThat(page.scaleY).isEqualTo(1f)
        assertThat(page.alpha).isLessThan(1f)
        pager.transitionEffect = PageTransitionEffect.SLIDE
        assertUntransformed()
    }

    @Test
    fun disablingMotionAlsoMakesRequestedAnimatedPageSnapImmediate() {
        pager.transitionEffect = PageTransitionEffect.CUBE
        pager.snapDurationScale = 0f
        pager.snapToPage(2, animate = true)
        assertThat(pager.currentPage).isEqualTo(2)
        assertThat(pager.scrollX).isEqualTo(800)
        assertUntransformed()
        pager.computeScroll()
        assertThat(pager.scrollX).isEqualTo(800)
    }
}
