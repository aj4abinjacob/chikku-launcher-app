package io.github.aj4abinjacob.chikku.home

import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.GridSpec
import io.github.aj4abinjacob.chikku.core.model.settings.DockBackground
import io.github.aj4abinjacob.chikku.core.model.settings.DockSettings
import io.github.aj4abinjacob.chikku.core.model.settings.HomeSettings
import io.github.aj4abinjacob.chikku.core.model.settings.PaddingSize
import org.junit.Test

/** Pure layout math for G02/G03/G12: padding, dock pages, wallpaper offsets, paging, dock colour. */
class HomeLayoutMathTest {

    @Test
    fun paddingSizesMapToDpAndScaleWithDensity() {
        assertThat(PaddingSize.entries.map(HomeLayoutMetrics::paddingDp)).containsExactly(0, 4, 8, 16).inOrder()
        assertThat(HomeLayoutMetrics.paddingPx(PaddingSize.LARGE, 2.5f)).isEqualTo(40)
        // MEDIUM keeps the M0 page padding.
        assertThat(HomeLayoutMetrics.paddingPx(PaddingSize.MEDIUM, 1f)).isEqualTo(8)
    }

    @Test
    fun widgetPaddingFollowsSetting() {
        val home = HomeSettings(widgetPadding = PaddingSize.LARGE)
        assertThat(HomeLayoutMetrics.widgetPaddingPx(home, 2f)).isEqualTo(32)
        assertThat(HomeLayoutMetrics.widgetPaddingPx(HomeSettings(widgetPadding = PaddingSize.NONE), 3f)).isEqualTo(0)
    }

    @Test
    fun dockPagesWidenStorageGridWithinMaxDimension() {
        assertThat(DockPaging.storageGrid(DockSettings(iconCount = 5, pages = 1))).isEqualTo(GridSpec(5, 1))
        assertThat(DockPaging.storageGrid(DockSettings(iconCount = 5, pages = 2))).isEqualTo(GridSpec(10, 1))
        // 3 pages of 5 would be 15 > 12 columns: capped to 2 pages.
        assertThat(DockPaging.effectivePages(DockSettings(iconCount = 5, pages = 3))).isEqualTo(2)
        assertThat(DockPaging.storageGrid(DockSettings(iconCount = 4, pages = 3))).isEqualTo(GridSpec(12, 1))
        assertThat(DockPaging.effectivePages(DockSettings(iconCount = 7, pages = 3))).isEqualTo(1)
        // Out-of-range stored values never produce an invalid grid.
        assertThat(DockPaging.storageGrid(DockSettings(iconCount = 99, pages = -4))).isEqualTo(GridSpec(12, 1))
    }

    @Test
    fun dockPageCountRoundsUp() {
        assertThat(DockPaging.pageCount(10, 5)).isEqualTo(2)
        assertThat(DockPaging.pageCount(5, 5)).isEqualTo(1)
        assertThat(DockPaging.pageCount(7, 5)).isEqualTo(2)
        assertThat(DockPaging.pageCount(5, 0)).isEqualTo(1)
    }

    @Test
    fun wallpaperOffsetTracksScrollWhenEnabled() {
        assertThat(WallpaperOffsets.xOffset(0, 1000, 3, enabled = true)).isEqualTo(0f)
        assertThat(WallpaperOffsets.xOffset(1000, 1000, 3, enabled = true)).isEqualTo(0.5f)
        assertThat(WallpaperOffsets.xOffset(2000, 1000, 3, enabled = true)).isEqualTo(1f)
        // Overscroll (infinite scroll pull) is clamped.
        assertThat(WallpaperOffsets.xOffset(-300, 1000, 3, enabled = true)).isEqualTo(0f)
        assertThat(WallpaperOffsets.xOffset(2400, 1000, 3, enabled = true)).isEqualTo(1f)
        assertThat(WallpaperOffsets.xStep(3, enabled = true)).isEqualTo(0.5f)
        assertThat(WallpaperOffsets.xStep(5, enabled = true)).isEqualTo(0.25f)
    }

    @Test
    fun wallpaperIsCenteredWhenDisabledOrSinglePage() {
        assertThat(WallpaperOffsets.xOffset(2000, 1000, 3, enabled = false)).isEqualTo(0.5f)
        assertThat(WallpaperOffsets.xOffset(0, 1000, 1, enabled = true)).isEqualTo(0.5f)
        assertThat(WallpaperOffsets.xOffset(0, 0, 3, enabled = true)).isEqualTo(0.5f)
        assertThat(WallpaperOffsets.xStep(3, enabled = false)).isEqualTo(1f)
    }

    @Test
    fun pagerSettlesWithAndWithoutWrap() {
        assertThat(PagerMath.settleVisual(3, 3, wrap = false)).isEqualTo(2)
        assertThat(PagerMath.settleVisual(-1, 3, wrap = false)).isEqualTo(0)
        assertThat(PagerMath.settleVisual(3, 3, wrap = true)).isEqualTo(0)
        assertThat(PagerMath.settleVisual(-1, 3, wrap = true)).isEqualTo(2)
        assertThat(PagerMath.settleVisual(1, 3, wrap = true)).isEqualTo(1)
        assertThat(PagerMath.step(2, +1, 3, wrap = true)).isEqualTo(0)
        assertThat(PagerMath.step(0, -1, 3, wrap = true)).isEqualTo(2)
        assertThat(PagerMath.step(2, +1, 3, wrap = false)).isEqualTo(2)
        assertThat(PagerMath.step(0, +1, 0, wrap = true)).isEqualTo(0)
    }

    @Test
    fun dockBackgroundAlphaMultipliesColorAlpha() {
        assertThat(DockBackgroundStyle.applyAlpha(0xFF102030.toInt(), 0.5f)).isEqualTo(0x80102030.toInt())
        assertThat(DockBackgroundStyle.applyAlpha(0x80102030.toInt(), 0.5f)).isEqualTo(0x40102030)
        assertThat(DockBackgroundStyle.applyAlpha(0xFF102030.toInt(), 0f)).isEqualTo(0x00102030)
        assertThat(DockBackgroundStyle.applyAlpha(0xFF102030.toInt(), 7f)).isEqualTo(0xFF102030.toInt())
    }

    @Test
    fun dockBackgroundCorners() {
        assertThat(DockBackgroundStyle.cornerRadius(DockBackground.RECTANGLE, 16f, 28f)).isEqualTo(0f)
        assertThat(DockBackgroundStyle.cornerRadius(DockBackground.ROUNDED, 16f, 28f)).isEqualTo(16f)
        assertThat(DockBackgroundStyle.cornerRadius(DockBackground.CARD, 16f, 28f)).isEqualTo(28f)
    }

    @Test
    fun windowContainsOnlyFullyVisibleItems() {
        val grid = GridSpec(4, 1)
        assertThat(CellMath.isInWindow(0, 0, 1, 1, 0, grid)).isTrue()
        assertThat(CellMath.isInWindow(4, 0, 1, 1, 0, grid)).isFalse()
        assertThat(CellMath.isInWindow(4, 0, 1, 1, 4, grid)).isTrue()
        assertThat(CellMath.isInWindow(3, 0, 2, 1, 0, grid)).isFalse() // straddles the window edge
        assertThat(CellMath.isInWindow(0, 1, 1, 1, 0, grid)).isFalse() // below a shrunk grid
    }
}
