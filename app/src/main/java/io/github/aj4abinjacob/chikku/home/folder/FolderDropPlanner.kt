package io.github.aj4abinjacob.chikku.home.folder

import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.CellPlacement
import io.github.aj4abinjacob.chikku.core.model.ContainerType
import io.github.aj4abinjacob.chikku.core.model.FolderSource
import io.github.aj4abinjacob.chikku.core.model.ItemTarget
import io.github.aj4abinjacob.chikku.core.model.WorkspaceItem
import io.github.aj4abinjacob.chikku.core.model.layout.WorkspaceLayoutEngine
import io.github.aj4abinjacob.chikku.home.DragPayload
import io.github.aj4abinjacob.chikku.home.HomeItemUi
import io.github.aj4abinjacob.chikku.home.ItemContent
import io.github.aj4abinjacob.chikku.home.WorkspaceUi

/** What a drop on the workspace or dock should do. Pure data; executed by [FolderController]. */
sealed interface DropPlan {
    /** Not a folder matter: the host's plain move/add handles it. */
    data object Default : DropPlan

    /** Dropped an app on an app (or shortcut): turn the target into a folder holding both. */
    data class CreateFolder(val anchorItemId: String, val source: FolderSource) : DropPlan

    /** Dropped an app on a folder. */
    data class AddToFolder(val folderId: String, val source: FolderSource) : DropPlan

    /** A folder entry dragged out to [to] (a free cell). */
    data class MoveOutOfFolder(val folderId: String, val entryId: String, val to: CellPlacement) : DropPlan

    /** Nothing to do, e.g. an entry dropped back on its own folder or no free cell. */
    data object Ignore : DropPlan
}

/**
 * Decides folder drops. A single-cell drag that lands on an app or shortcut creates a folder, on a
 * folder adds to it; every other drop stays a plain move. Folders never nest.
 */
object FolderDropPlanner {

    fun plan(payload: DragPayload, target: CellPlacement, ui: WorkspaceUi.Ready?): DropPlan {
        if (ui == null) return DropPlan.Default
        val occupant = occupantAt(ui, target)
        return when (payload) {
            is DragPayload.Existing -> {
                val dragged = ui.allItems().firstOrNull { it.id == payload.itemId } ?: return DropPlan.Default
                if (occupant == null || occupant.id == dragged.id || !dragged.content.isFolderMember()) {
                    return DropPlan.Default
                }
                planOnto(occupant, FolderSource.Item(dragged.id))
            }
            is DragPayload.NewWidget -> DropPlan.Default
            is DragPayload.NewApp ->
                if (occupant == null) DropPlan.Default else planOnto(occupant, FolderSource.Target(ItemTarget.App(payload.key)))
            is DragPayload.FromFolder -> when {
                occupant == null -> DropPlan.MoveOutOfFolder(payload.folderId, payload.entryId, target)
                occupant.id == payload.folderId -> DropPlan.Ignore
                else -> nearestFree(ui, target)?.let { DropPlan.MoveOutOfFolder(payload.folderId, payload.entryId, it) }
                    ?: DropPlan.Ignore
            }
        }
    }

    private fun planOnto(occupant: HomeItemUi, source: FolderSource): DropPlan = when {
        occupant.content is ItemContent.Folder -> DropPlan.AddToFolder(occupant.id, source)
        occupant.content.isFolderMember() -> DropPlan.CreateFolder(occupant.id, source)
        else -> DropPlan.Default // widgets: the repository rejects or overlaps as usual
    }

    /** The item covering [cell] (a 1x1 placement), if any. */
    fun occupantAt(ui: WorkspaceUi.Ready, cell: CellPlacement): HomeItemUi? {
        val candidates = when (cell.container) {
            ContainerType.DESKTOP -> ui.pages.getOrNull(cell.page).orEmpty()
            ContainerType.DOCK -> ui.dock
        }
        val probe = cell.copy(spanX = 1, spanY = 1)
        // Highest zIndex wins when widgets overlap.
        return candidates.filter { WorkspaceLayoutEngine.overlaps(it.placement, probe) }.maxByOrNull { it.zIndex }
    }

    /** Nearest free cell on the same page/container, or null when it is full. */
    fun nearestFree(ui: WorkspaceUi.Ready, near: CellPlacement): CellPlacement? {
        val grid = if (near.container == ContainerType.DOCK) ui.dockGrid else ui.desktopGrid
        return WorkspaceLayoutEngine.findNearestFree(ui.asItems(), grid, near.container, near.page, near.x, near.y)
    }

    /** First free desktop cell from [startPage] on (wrapping), for "Move to home screen". */
    fun firstFreeDesktopCell(ui: WorkspaceUi.Ready, startPage: Int = 0): CellPlacement? {
        val items = ui.asItems()
        val order = ui.pages.indices.sortedBy { (it - startPage).mod(ui.pages.size) }
        for (page in order) {
            WorkspaceLayoutEngine.findNearestFree(items, ui.desktopGrid, ContainerType.DESKTOP, page, 0, 0)
                ?.let { return it }
        }
        return null
    }

    private fun ItemContent.isFolderMember() = this is ItemContent.App || this is ItemContent.Shortcut

    private fun WorkspaceUi.Ready.allItems(): List<HomeItemUi> = pages.flatten() + dock

    /** Occupancy only: the layout engine needs placements, never the targets. */
    private fun WorkspaceUi.Ready.asItems(): List<WorkspaceItem> =
        allItems().map { WorkspaceItem(it.id, it.placement, PLACEHOLDER_TARGET) }

    private val PLACEHOLDER_TARGET = ItemTarget.Folder("")

    /** The app a launch-first-app folder tap would open: the first available app entry. */
    fun firstApp(folder: ItemContent.Folder): AppKey? =
        folder.entries.asSequence().map { it.content }.filterIsInstance<ItemContent.App>().firstOrNull()?.key
}
