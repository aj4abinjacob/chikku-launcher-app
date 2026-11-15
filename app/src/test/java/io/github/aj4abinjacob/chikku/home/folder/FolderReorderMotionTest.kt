package io.github.aj4abinjacob.chikku.home.folder

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Looper
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.settings.AnimationSpeed
import io.github.aj4abinjacob.chikku.core.model.settings.FolderSettings
import io.github.aj4abinjacob.chikku.home.AppIconView
import io.github.aj4abinjacob.chikku.home.Availability
import io.github.aj4abinjacob.chikku.home.FolderEntryUi
import io.github.aj4abinjacob.chikku.home.ItemContent
import io.github.aj4abinjacob.chikku.platform.IconProvider
import io.github.aj4abinjacob.chikku.theme.Motion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/** Reorder motion on a real [FolderGridLayout] with synthetic entries; no device gesture claim. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FolderReorderMotionTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val ids = listOf("a", "b", "c", "d")

    private fun grid(count: Int = ids.size) = FolderGridLayout(context, 100, 100).apply {
        columns = 2
        repeat(count) { addView(View(context)) }
        measure(View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        layout(0, 0, 200, 200)
    }

    private fun motion() = FolderReorderMotion().apply { motion = Motion(AnimationSpeed.NONE) }

    private fun waitForShift() = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(FolderReorderMotion.REORDER_DELAY_MS + 10))

    @Test fun previewCellsShiftEntriesBetweenSourceAndTarget() {
        assertThat(FolderReorderMotion.previewCells(4, 0, 2).toList()).containsExactly(2, 0, 1, 3).inOrder()
        assertThat(FolderReorderMotion.previewCells(4, 3, 1).toList()).containsExactly(0, 2, 3, 1).inOrder()
        assertThat(FolderReorderMotion.previewCells(4, 1, 1).toList()).containsExactly(0, 1, 2, 3).inOrder()
        assertThat(FolderReorderMotion.previewCells(4, -1, 2).toList()).containsExactly(0, 1, 2, 3).inOrder()
    }

    @Test fun draggedEntryLiftsOutAndOthersMakeRoomAfterHover() {
        val grid = grid()
        val reorder = motion()
        reorder.bind(grid, ids)
        reorder.start("a", canReorder = true)
        assertThat(grid.getChildAt(0).alpha).isEqualTo(0f)

        reorder.hover(3)
        // Passing over a cell does not shuffle at once.
        assertThat(grid.getChildAt(1).translationX).isEqualTo(0f)
        waitForShift()
        // b (cell 1) moves to cell 0, c (cell 2) to cell 1, d (cell 3) to cell 2.
        assertThat(grid.getChildAt(1).translationX).isEqualTo(-100f)
        assertThat(grid.getChildAt(2).translationX).isEqualTo(100f)
        assertThat(grid.getChildAt(2).translationY).isEqualTo(-100f)
        assertThat(grid.getChildAt(3).translationX).isEqualTo(-100f)
    }

    @Test fun dropReturnsShownCellAndRebuildSettlesDroppedEntry() {
        val grid = grid()
        val reorder = motion()
        reorder.bind(grid, ids)
        reorder.start("a", canReorder = true)
        reorder.hover(2)
        waitForShift()
        // Letting go outside the grid keeps the cell the others made room for.
        assertThat(reorder.drop(null)).isEqualTo(2)
        assertThat(reorder.isDragging).isFalse()

        val rebuilt = grid()
        reorder.bind(rebuilt, listOf("b", "c", "a", "d"))
        val dropped = rebuilt.getChildAt(2)
        assertThat(dropped.alpha).isEqualTo(1f)
        assertThat(dropped.scaleX).isEqualTo(1f)
        assertThat((0 until 4).map { rebuilt.getChildAt(it).translationX }).containsExactly(0f, 0f, 0f, 0f)
    }

    @Test fun droppedEntryIsVisibleAtItsNewCellBeforeTheStoredOrderArrives() {
        for (speed in listOf(AnimationSpeed.NONE, AnimationSpeed.NORMAL)) {
            val grid = grid()
            val reorder = FolderReorderMotion().apply { motion = Motion(speed) }
            reorder.bind(grid, ids)
            val dragged = grid.getChildAt(0)
            reorder.start("a", canReorder = true)
            reorder.hover(2)
            waitForShift()
            assertThat(dragged.alpha).isEqualTo(0f) // lifted: the shadow stands in for it
            assertThat(reorder.drop(2)).isEqualTo(2)
            // No empty cell between the shadow ending and the rebuild: it shows at the new cell now.
            assertThat(dragged.alpha).isEqualTo(1f)

            // The rebuild only drops the offset (same spot on screen); it does not restart or hide it.
            grid.removeView(dragged)
            grid.addView(dragged, 2)
            assertThat(reorder.bind(grid, listOf("b", "c", "a", "d"))).isEqualTo("a")
            assertThat(dragged.alpha).isEqualTo(1f)
            assertThat(dragged.translationX).isEqualTo(0f)
            assertThat(dragged.translationY).isEqualTo(0f)
        }
    }

    @Test fun droppedEntryThatIsNeverStoredGlidesBackVisibleAtItsRestingAlpha() {
        val grid = grid()
        val reorder = motion()
        reorder.bind(grid, ids)
        val dragged = grid.getChildAt(0)
        dragged.alpha = 0.55f // an unavailable app rests dimmed
        reorder.start("a", canReorder = true)
        reorder.hover(3)
        waitForShift()
        reorder.drop(3)
        assertThat(dragged.alpha).isWithin(0.001f).of(0.55f)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(FolderReorderMotion.SETTLE_WAIT_MS + 10))
        assertThat(dragged.translationX).isEqualTo(0f)
        assertThat(dragged.alpha).isWithin(0.001f).of(0.55f)
    }

    @Test fun aNewDragBeforeTheStoredOrderArrivesSettlesTheLandedEntryBack() {
        val grid = grid()
        val reorder = motion()
        reorder.bind(grid, ids)
        val dropped = grid.getChildAt(0)
        reorder.start("a", canReorder = true)
        reorder.hover(3)
        waitForShift()
        assertThat(reorder.drop(3)).isEqualTo(3)
        assertThat(grid.getChildAt(1).translationX).isNotEqualTo(0f) // the others still show the preview

        reorder.start("c", canReorder = true)
        // Neither the landed entry nor the others keep a stale preview offset, and nothing is left hidden.
        assertThat((0 until 4).map { grid.getChildAt(it).translationX }).containsExactly(0f, 0f, 0f, 0f)
        assertThat(dropped.alpha).isEqualTo(1f)
        assertThat(dropped.scaleX).isEqualTo(1f)
        assertThat(reorder.isDragging).isTrue()
    }

    @Test fun reusedViewsSettleWithoutPreviewOffsetsOrLostAlpha() {
        val grid = grid()
        val reorder = motion()
        reorder.bind(grid, ids)
        val dragged = grid.getChildAt(0)
        reorder.start("a", canReorder = true)
        reorder.hover(2)
        waitForShift()
        assertThat(reorder.drop(2)).isEqualTo(2)

        // The stored order arrives: the same views are reordered in place, not rebuilt.
        grid.removeView(dragged)
        grid.addView(dragged, 2)
        assertThat(reorder.bind(grid, listOf("b", "c", "a", "d"))).isEqualTo("a")
        assertThat(dragged.translationX).isEqualTo(0f)
        assertThat(dragged.translationY).isEqualTo(0f)
        assertThat(dragged.alpha).isEqualTo(1f)
        assertThat(dragged.scaleX).isEqualTo(1f)
    }

    @Test fun updateDuringDragKeepsHiddenViewRestingAlpha() {
        val grid = grid()
        val reorder = motion()
        reorder.bind(grid, ids)
        val dragged = grid.getChildAt(0)
        reorder.start("a", canReorder = true)
        assertThat(reorder.bind(grid, ids)).isEqualTo("a") // a reused, already hidden view
        assertThat(dragged.alpha).isEqualTo(0f)
        reorder.end()
        assertThat(dragged.alpha).isEqualTo(1f)
    }

    @Test fun cancelledDragAndUnchangedDropReturnEverythingToItsCell() {
        val grid = grid()
        val reorder = motion()
        reorder.bind(grid, ids)
        reorder.start("b", canReorder = true)
        reorder.hover(0)
        waitForShift()
        reorder.end()
        assertThat((0 until 4).map { grid.getChildAt(it).translationX }).containsExactly(0f, 0f, 0f, 0f)
        assertThat(grid.getChildAt(1).alpha).isEqualTo(1f)

        reorder.start("b", canReorder = true)
        assertThat(reorder.drop(1)).isNull()
        assertThat(grid.getChildAt(1).alpha).isEqualTo(1f)
    }

    @Test fun storedOrderThatNeverArrivesSettlesBackAfterTimeout() {
        val grid = grid()
        val reorder = motion()
        reorder.bind(grid, ids)
        reorder.start("a", canReorder = true)
        assertThat(reorder.drop(3)).isEqualTo(3)
        assertThat(grid.getChildAt(1).translationX).isNotEqualTo(0f)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(FolderReorderMotion.SETTLE_WAIT_MS + 10))
        assertThat((0 until 4).map { grid.getChildAt(it).translationX }).containsExactly(0f, 0f, 0f, 0f)
        assertThat(grid.getChildAt(0).alpha).isEqualTo(1f)
    }

    @Test fun alphabeticalFolderLiftsButNeverShuffles() {
        val grid = grid()
        val reorder = motion()
        reorder.bind(grid, ids)
        reorder.start("a", canReorder = false)
        reorder.hover(3)
        waitForShift()
        assertThat(grid.getChildAt(1).translationX).isEqualTo(0f)
        assertThat(reorder.drop(3)).isNull()
        assertThat(grid.getChildAt(0).alpha).isEqualTo(1f)
    }

    @Test fun folderPreviewRedrawsAfterIconRevisionChanges() {
        val revision = MutableStateFlow(0L)
        var color = Color.RED
        val provider = object : IconProvider {
            override suspend fun icon(key: AppKey): Drawable = ColorDrawable(color)
            override fun invalidate(packageName: String) = Unit
            override val iconRevision = revision
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val folder = ItemContent.Folder("Tools", listOf(FolderEntryUi("one", ItemContent.App(AppKey("pkg", "pkg.One", 0), "One", false, Availability.AVAILABLE))))
            val view = AppIconView(context, AppIconView.Style.WORKSPACE)
            val binder = FolderIconBinder(context, scope, provider)
            binder.bind(view, folder, FolderSettings(), "folder")
            val first = view.icon
            binder.bind(view, folder, FolderSettings(), "folder")
            assertThat(view.icon).isSameInstanceAs(first) // unchanged content keeps the drawn icon

            color = Color.BLUE
            revision.value = 1L // e.g. the user picked a new icon for an app in the folder
            binder.bind(view, folder, FolderSettings(), "folder")
            assertThat(view.icon).isNotSameInstanceAs(first)
        } finally {
            scope.cancel()
        }
    }
}
