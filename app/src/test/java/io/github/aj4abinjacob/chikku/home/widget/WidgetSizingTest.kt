package io.github.aj4abinjacob.chikku.home.widget

import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.CellPlacement
import io.github.aj4abinjacob.chikku.core.model.ComponentKey
import io.github.aj4abinjacob.chikku.core.model.ContainerType.DESKTOP
import io.github.aj4abinjacob.chikku.core.model.GridSpec
import io.github.aj4abinjacob.chikku.core.model.ItemTarget
import io.github.aj4abinjacob.chikku.core.model.Workspace
import io.github.aj4abinjacob.chikku.core.model.WorkspaceItem
import org.junit.Test

class WidgetSizingTest {
    private val grid = GridSpec(5, 5)
    private val provider = ComponentKey("com.w", "com.w.Clock", 0)

    @Test
    fun spanRoundsUpToWholeCellsAndClampsToGrid() {
        assertThat(WidgetSizing.spanFor(100, 100, 5)).isEqualTo(1)
        assertThat(WidgetSizing.spanFor(101, 100, 5)).isEqualTo(2)
        assertThat(WidgetSizing.spanFor(0, 100, 5)).isEqualTo(1)
        assertThat(WidgetSizing.spanFor(900, 100, 5)).isEqualTo(5)
        assertThat(WidgetSizing.spanFor(300, 0, 5)).isEqualTo(1)
    }

    @Test
    fun defaultSpanUsesMinSizeOrDeclaredTargetCells() {
        val spec = WidgetSizeSpec(minWidth = 250, minHeight = 110)
        assertThat(WidgetSizing.defaultSpan(spec, 100, 120, grid)).isEqualTo(3 to 1)
        val target = spec.copy(targetCellWidth = 4, targetCellHeight = 9)
        assertThat(WidgetSizing.defaultSpan(target, 100, 120, grid)).isEqualTo(4 to 5)
    }

    @Test
    fun resizeRangeGoesDownToOneCellAndRespectsProviderMaxAndFullGrid() {
        val spec = WidgetSizeSpec(
            minWidth = 250, minHeight = 250,
            minResizeWidth = 110, minResizeHeight = 0,
            maxResizeWidth = 380, maxResizeHeight = 0,
            resizeHorizontal = true, resizeVertical = true,
        )
        val placement = CellPlacement(DESKTOP, 0, 1, 2, 3, 3)
        val range = WidgetSizing.resizeRange(spec, 100, 100, grid, placement)
        // Provider minimums are not enforced deliberately: every resizable axis goes down to 1.
        assertThat(range.minX).isEqualTo(1)
        // max: floor(380/100) = 3 although the full grid allows 5.
        assertThat(range.maxX).isEqualTo(3)
        assertThat(range.minY).isEqualTo(1)
        // No maxResizeHeight: the full grid allows 5.
        assertThat(range.maxY).isEqualTo(5)

        assertThat(WidgetSizing.clampResize(range, 9, 0)).isEqualTo(3 to 1)
        assertThat(WidgetSizing.clampResize(range, 1, 9)).isEqualTo(1 to 5)
    }

    @Test
    fun nonResizableAxisIsPinnedToCurrentSpan() {
        val spec = WidgetSizeSpec(minWidth = 100, minHeight = 100, resizeVertical = true)
        val range = WidgetSizing.resizeRange(spec, 100, 100, grid, CellPlacement(DESKTOP, 0, 0, 0, 2, 1))
        assertThat(range.minX).isEqualTo(2)
        assertThat(range.maxX).isEqualTo(2)
        assertThat(range.maxY).isEqualTo(5)
        assertThat(spec.isResizable).isTrue()
        assertThat(WidgetSizeSpec(100, 100).isResizable).isFalse()
    }

    @Test
    fun originNearEdgeDoesNotPreventExpansionTowardsStart() {
        val spec = WidgetSizeSpec(minWidth = 400, minHeight = 100, resizeHorizontal = true)
        val range = WidgetSizing.resizeRange(spec, 100, 100, grid, CellPlacement(DESKTOP, 0, 3, 0, 2, 1))
        assertThat(range.minX).isAtMost(range.maxX)
        assertThat(range.minX).isEqualTo(1)
        assertThat(range.maxX).isEqualTo(5)
    }

    @Test
    fun snapRoundsToNearestCellWithinLimits() {
        assertThat(WidgetSizing.snap(149f, 100, 1, 4)).isEqualTo(1)
        assertThat(WidgetSizing.snap(151f, 100, 1, 4)).isEqualTo(2)
        assertThat(WidgetSizing.snap(900f, 100, 1, 4)).isEqualTo(4)
        assertThat(WidgetSizing.snap(10f, 100, 2, 4)).isEqualTo(2)
    }

    @Test
    fun spanSizeDpSubtractsPadding() {
        assertThat(WidgetSizing.spanSizeDp(2, 200, 10, 2f)).isEqualTo(190)
        assertThat(WidgetSizing.spanSizeDp(1, 10, 50, 2f)).isEqualTo(0)
    }

    @Test
    fun findSpotPrefersPageThenAnyPage() {
        val full = (0 until 5).flatMap { y -> (0 until 5).map { x -> item("p0-$x-$y", 0, x, y) } }
        val ws = Workspace(grid, GridSpec(5, 1), 2, full + item("p1", 1, 0, 0))
        assertThat(WidgetSizing.findSpot(ws, 0, 2, 2)).isEqualTo(CellPlacement(DESKTOP, 1, 1, 0, 2, 2))
        assertThat(WidgetSizing.findSpot(ws, 1, 9, 1)).isEqualTo(CellPlacement(DESKTOP, 1, 0, 1, 5, 1))
        val allFull = Workspace(grid, GridSpec(5, 1), 1, full)
        assertThat(WidgetSizing.findSpot(allFull, 0, 1, 1)).isNull()
    }

    @Test
    fun displayStateNeverDropsAPlacement() {
        assertThat(widgetDisplayState(0, provider, null))
            .isEqualTo(WidgetDisplayState.NeedsRebind(WidgetDisplayState.Reason.NO_ID))
        assertThat(widgetDisplayState(7, provider, null))
            .isEqualTo(WidgetDisplayState.NeedsRebind(WidgetDisplayState.Reason.NOT_BOUND))
        assertThat(widgetDisplayState(7, provider, provider.copy(className = "com.w.Other")))
            .isEqualTo(WidgetDisplayState.NeedsRebind(WidgetDisplayState.Reason.PROVIDER_CHANGED))
        assertThat(widgetDisplayState(7, provider, provider.copy(userSerial = 10)))
            .isEqualTo(WidgetDisplayState.NeedsRebind(WidgetDisplayState.Reason.PROVIDER_CHANGED))
        assertThat(widgetDisplayState(7, provider, provider)).isEqualTo(WidgetDisplayState.Hosted)
    }

    @Test
    fun providersGroupByAppAndProfileSorted() {
        fun e(pkg: String, label: String, serial: Long = 0) =
            WidgetProviderEntry(ComponentKey(pkg, "$pkg.$label", serial), label, 1, 1)
        val groups = groupWidgetProviders(
            listOf(e("b.clock", "Zed"), e("a.cal", "Month"), e("b.clock", "Alarm"), e("a.cal", "Agenda", 10)),
            appLabel = { pkg, _ -> if (pkg == "a.cal") "Calendar" else "Clock" },
            mainSerial = 0,
            collator = naturalOrder(),
        )
        assertThat(groups.map { it.appLabel to it.userSerial })
            .containsExactly("Calendar" to 0L, "Calendar" to 10L, "Clock" to 0L).inOrder()
        assertThat(groups[1].isOtherProfile).isTrue()
        assertThat(groups[2].entries.map { it.label }).containsExactly("Alarm", "Zed").inOrder()
    }

    private fun item(id: String, page: Int, x: Int, y: Int) = WorkspaceItem(
        id, CellPlacement(DESKTOP, page, x, y), ItemTarget.Widget(1, provider),
    )
}
