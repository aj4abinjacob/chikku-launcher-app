package io.github.aj4abinjacob.chikku.home.folder

import android.content.Context
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.settings.AnimationSpeed
import io.github.aj4abinjacob.chikku.theme.Motion
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Grid content-change motion on a real [FolderGridLayout]; no device frame timing is claimed. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FolderGridMotionTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun grid(count: Int = 4) = FolderGridLayout(context, 100, 100).apply {
        columns = 2
        repeat(count) { addView(View(context)) }
        measure(View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        layout(0, 0, 200, 200)
    }

    @Test fun nothingAnimatesWithoutMotionOrOffScreen() {
        val grid = grid()
        val motion = FolderGridMotion()
        // Not attached to a window yet.
        assertThat(motion.canAnimate(grid)).isFalse()
        motion.motion = Motion(AnimationSpeed.NONE)
        assertThat(motion.canAnimate(grid)).isFalse()
    }

    @Test fun captureReadsDrawnPositionIncludingTranslation() {
        val grid = grid()
        grid.getChildAt(1).translationX = -30f
        val visuals = FolderGridMotion().capture(grid)
        assertThat(visuals[grid.getChildAt(0)]).isEqualTo(0f to 0f)
        assertThat(visuals[grid.getChildAt(1)]).isEqualTo(70f to 0f)
    }

    @Test fun unanimatedChangeRemovesGoneViewsAndClearsOffsetsExceptSkipped() {
        val grid = grid()
        val motion = FolderGridMotion()
        val gone = grid.getChildAt(3)
        val skipped = grid.getChildAt(0)
        grid.getChildAt(1).translationX = 40f
        skipped.translationX = 25f
        motion.leave(grid, listOf(gone), pageDirection = 0, animate = false)
        motion.play(grid, emptyMap(), emptyList(), skipped, animate = false)
        assertThat(grid.childCount).isEqualTo(3)
        assertThat(grid.getChildAt(1).translationX).isEqualTo(0f)
        assertThat(skipped.translationX).isEqualTo(25f)
    }

    @Test fun cancelAllLeavesAddedViewsAtRest() {
        val grid = grid()
        val motion = FolderGridMotion()
        val added = grid.getChildAt(2).apply { alpha = 0.38f }
        motion.prepareEnter(grid, added, pageDirection = 1)
        assertThat(added.alpha).isEqualTo(0f)
        assertThat(added.translationX).isNotEqualTo(0f)
        motion.cancelAll()
        assertThat(added.alpha).isEqualTo(0.38f)
        assertThat(added.translationX).isEqualTo(0f)
        assertThat(added.scaleX).isEqualTo(1f)

        motion.prepareEnter(grid, added, pageDirection = 0)
        assertThat(added.scaleX).isLessThan(1f)
        motion.finishStale(grid)
        assertThat(added.scaleX).isEqualTo(1f)
        assertThat(added.alpha).isEqualTo(0.38f)
    }
}
