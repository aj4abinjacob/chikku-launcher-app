package io.github.aj4abinjacob.chikku.home

import io.github.aj4abinjacob.chikku.core.model.CellPlacement
import io.github.aj4abinjacob.chikku.core.model.ContainerType
import io.github.aj4abinjacob.chikku.core.model.EditResult
import io.github.aj4abinjacob.chikku.core.model.GridSpec
import io.github.aj4abinjacob.chikku.core.model.Workspace
import io.github.aj4abinjacob.chikku.core.model.WorkspaceItem
import io.github.aj4abinjacob.chikku.core.model.WorkspaceRepository
import io.github.aj4abinjacob.chikku.core.model.WorkspaceState
import io.github.aj4abinjacob.chikku.core.model.layout.WorkspaceLayoutEngine
import io.github.aj4abinjacob.chikku.core.model.settings.DockSettings
import io.github.aj4abinjacob.chikku.core.model.settings.HomeSettings
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.core.model.settings.PaddingSize
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

/** Pixel values derived from [HomeSettings] (G02/G12). Pure, so it is unit-tested without views. */
object HomeLayoutMetrics {

    /** Padding in dp for a [PaddingSize]; MEDIUM equals the M0 page padding (8dp). */
    fun paddingDp(size: PaddingSize): Int = when (size) {
        PaddingSize.NONE -> 0
        PaddingSize.SMALL -> 4
        PaddingSize.MEDIUM -> 8
        PaddingSize.LARGE -> 16
    }

    /** Smallest width (dp) from which the home screen may rotate: tablets and an unfolded foldable. */
    const val ROTATABLE_SMALLEST_WIDTH_DP = 600

    /**
     * Phones keep the home screen in portrait: a landscape phone has ~390dp of height, too little for
     * the grid, dock and indicator. Re-evaluated on every start, so folding or unfolding a foldable
     * (which recreates the activity) switches between the two.
     */
    fun locksToPortrait(smallestScreenWidthDp: Int): Boolean = smallestScreenWidthDp < ROTATABLE_SMALLEST_WIDTH_DP

    fun paddingPx(size: PaddingSize, density: Float): Int = (paddingDp(size) * density).roundToInt()

    /**
     * Inset applied inside each widget's cell area. Exposed for the widgets feature, which owns
     * widget hosting; the home grid itself does not inset widgets.
     */
    fun widgetPaddingPx(home: HomeSettings, density: Float, override: PaddingSize? = null): Int =
        paddingPx(override ?: home.widgetPadding, density)
}

/**
 * The dock is stored as one row whose width is `iconCount * pages`; dock page `p` shows the columns
 * `[p * iconCount, (p + 1) * iconCount)`. This gives multiple dock pages without a schema change.
 * [GridSpec.MAX_DIMENSION] caps the total width, so large icon counts get fewer pages.
 */
object DockPaging {

    fun effectivePages(dock: DockSettings): Int = dock.effectivePages()

    /** Grid the repository validates dock placements against (`LauncherSettings.dockStorageGrid`). */
    fun storageGrid(dock: DockSettings): GridSpec = LauncherSettings(dock = dock).dockStorageGrid

    /** Visible dock pages for a stored dock width and the per-page column count. */
    fun pageCount(storedColumns: Int, pageColumns: Int): Int =
        if (pageColumns <= 0) 1 else ((storedColumns + pageColumns - 1) / pageColumns).coerceAtLeast(1)
}

/** Wallpaper parallax as pages scroll (G12). */
object WallpaperOffsets {

    const val CENTER = 0.5f

    /** Horizontal offset in [0, 1] for the pager's scroll position; centered when off or single page. */
    fun xOffset(scrollX: Int, pageWidth: Int, pageCount: Int, enabled: Boolean): Float {
        if (!enabled || pageCount <= 1 || pageWidth <= 0) return CENTER
        val max = (pageCount - 1).toFloat() * pageWidth
        return (scrollX / max).coerceIn(0f, 1f)
    }

    /** Offset delta between pages for `WallpaperManager.setWallpaperOffsetSteps`. */
    fun xStep(pageCount: Int, enabled: Boolean): Float =
        if (!enabled || pageCount <= 1) 1f else 1f / (pageCount - 1)
}

/**
 * Items outside the current grid, e.g. after the user shrank the desktop grid or the dock. They stay
 * in storage and are hidden from the grid until the grid grows again or [relocate] moves them onto
 * free desktop cells. Nothing here deletes anything.
 */
object HiddenItems {

    fun find(workspace: Workspace): List<WorkspaceItem> =
        io.github.aj4abinjacob.chikku.core.model.layout.HiddenLayoutRecovery.hidden(workspace)

    /** Result of [relocate]: how many items moved and how many are still hidden (too big, or failed). */
    data class Outcome(val moved: Int, val remaining: Int)

    /**
     * Moves every hidden item to the first free desktop cell, adding pages as needed. Items whose
     * span is larger than the grid (big widgets) stay hidden; they can be resized by their owner.
     */
    suspend fun relocate(repository: WorkspaceRepository): Outcome? =
        repository.relocateHiddenItems()?.let { Outcome(it.moved, it.remaining) }
}
