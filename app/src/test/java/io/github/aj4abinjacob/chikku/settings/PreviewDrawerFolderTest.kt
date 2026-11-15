package io.github.aj4abinjacob.chikku.settings

import io.github.aj4abinjacob.chikku.core.model.settings.DrawerFastScrollerSide
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerSearchBarPosition
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerSettings
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerStyle
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerTabBarPosition
import io.github.aj4abinjacob.chikku.core.model.settings.FolderScrollStyle
import io.github.aj4abinjacob.chikku.core.model.settings.FolderSettings
import io.github.aj4abinjacob.chikku.core.model.settings.FolderSortOrder
import io.github.aj4abinjacob.chikku.settings.previews.LIST_GROUP_GUTTER_DP
import io.github.aj4abinjacob.chikku.settings.previews.drawerPreviewLayout
import io.github.aj4abinjacob.chikku.settings.previews.previewFolderOrder
import io.github.aj4abinjacob.chikku.settings.previews.previewNextPage
import io.github.aj4abinjacob.chikku.settings.previews.previewSceneHeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewDrawerFolderTest {
    @Test fun landscapeAndLargeFontKeepRoomForTheSettingsControls() {
        assertEquals(188f, previewSceneHeight(800, 1f, 188f), .01f)
        assertEquals(86.4f, previewSceneHeight(360, 1f, 188f), .01f)
        assertEquals(112f, previewSceneHeight(800, 2f, 188f), .01f)
    }
    @Test fun autoColumnsUseTheSpaceLeftByTheSelectedScroller() {
        val withoutRail = layout(DrawerSettings(showFastScroller = false), width = 330f)
        val rightRail = layout(DrawerSettings(showFastScroller = true, fastScrollerGapDp = 8), width = 330f)
        val leftRail = layout(DrawerSettings(showFastScroller = true, fastScrollerGapDp = 8, fastScrollerSide = DrawerFastScrollerSide.LEFT), width = 330f)
        assertEquals(4, withoutRail.columns)
        assertEquals(3, rightRail.columns)
        assertEquals(rightRail.columns, leftRail.columns)
        assertEquals(rightRail.grid.width, leftRail.grid.width, .01f)
        assertTrue(leftRail.grid.left > rightRail.grid.left)
    }

    @Test fun fixedRowsFitTheViewportWhileLargeContentRetainsItsMinimumHeight() {
        val requested = DrawerSettings(rows = 6, automaticRowSpacing = false, showFastScroller = false)
        assertEquals(6, layout(requested).visibleRows)
        val smallerScreen = layout(requested, height = 420f)
        assertEquals(80f, smallerScreen.rowHeight, .01f)
        assertEquals(3, smallerScreen.visibleRows)
        val largeFont = drawerPreviewLayout(requested, 360f, 700f, 180f, 72f, 1f)
        assertEquals(180f, largeFont.rowHeight, .01f)
        assertEquals(3, largeFont.visibleRows)
    }

    @Test fun manualRowsTrimLeadingSlotSpaceAndKeepTheRequestedVisibleRowCount() {
        val requested = DrawerSettings(rows = 4, automaticRowSpacing = false, searchBarPosition = DrawerSearchBarPosition.HIDDEN)
        val spaced = drawerPreviewLayout(requested, 360f, 1048f, 68f, 88f, 1f)
        assertEquals(274f, spaced.rowHeight, .01f)
        assertEquals(95f, spaced.firstRowTrim, .01f)
        assertEquals(4, spaced.visibleRows)
        assertEquals(0f, layout(requested.copy(automaticRowSpacing = true)).firstRowTrim, .01f)
    }

    @Test fun tabsAndSearchReserveSpaceAtTheirConfiguredEdges() {
        val top = layout(DrawerSettings(groupsEnabled = true, tabBarPosition = DrawerTabBarPosition.TOP,
            searchBarPosition = DrawerSearchBarPosition.TOP))
        val bottom = layout(DrawerSettings(groupsEnabled = true, tabBarPosition = DrawerTabBarPosition.BOTTOM,
            searchBarPosition = DrawerSearchBarPosition.BOTTOM))
        assertEquals(top.grid.height, bottom.grid.height, .01f)
        assertTrue(top.tabsTop!! < top.grid.top)
        assertTrue(top.searchTop!! < top.grid.top)
        assertTrue(bottom.tabsTop!! >= bottom.grid.bottom)
        assertTrue(bottom.searchTop!! >= bottom.grid.bottom)
        val noChrome = layout(DrawerSettings(searchBarPosition = DrawerSearchBarPosition.HIDDEN))
        assertTrue(noChrome.grid.height > top.grid.height)
    }

    @Test fun listAndPagedPreviewsHonorTheirDifferentNavigationGeometry() {
        val list = layout(DrawerSettings(style = DrawerStyle.LIST, columns = 6))
        assertEquals(1, list.columns)
        // Rows start after the group-letter column, as in the drawer.
        assertEquals(layout(DrawerSettings()).grid.left + LIST_GROUP_GUTTER_DP, list.grid.left, .01f)
        val paged = layout(DrawerSettings(style = DrawerStyle.HORIZONTAL_PAGED, columns = 4, rows = 5))
        assertEquals(20, paged.capacity)
        assertEquals(null, paged.scrollerLeft)
        assertEquals(0, previewNextPage(2, 1, 3, true))
        assertEquals(2, previewNextPage(0, -1, 3, true))
        assertEquals(2, previewNextPage(2, 1, 3, false))
    }

    @Test fun alphabeticalDemonstrationReordersOnlyItsTransientPresentation() {
        val labels = listOf("Zulu", "Alpha", "Beta")
        assertEquals(listOf(0, 1, 2), previewFolderOrder(FolderSortOrder.MANUAL, labels))
        assertEquals(listOf(1, 2, 0), previewFolderOrder(FolderSortOrder.ALPHABETICAL, labels))
        assertEquals(listOf("Zulu", "Alpha", "Beta"), labels)
    }

    private fun layout(drawer: DrawerSettings, width: Float = 360f, height: Float = 700f) =
        drawerPreviewLayout(drawer, width, height, 80f, 72f, 1f)
}
