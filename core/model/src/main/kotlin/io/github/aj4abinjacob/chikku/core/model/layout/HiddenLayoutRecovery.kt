package io.github.aj4abinjacob.chikku.core.model.layout

import io.github.aj4abinjacob.chikku.core.model.CellPlacement
import io.github.aj4abinjacob.chikku.core.model.ContainerType
import io.github.aj4abinjacob.chikku.core.model.Workspace
import io.github.aj4abinjacob.chikku.core.model.WorkspaceContent
import io.github.aj4abinjacob.chikku.core.model.WorkspaceItem
import io.github.aj4abinjacob.chikku.core.model.WorkspaceLimits

data class HiddenItemsRecoveryResult(val moved: Int, val remaining: Int)

/** Pure plan; storage applies it in one transaction, preserving IDs, spans, folders and actions. */
object HiddenLayoutRecovery {
    data class Plan(val content: WorkspaceContent, val result: HiddenItemsRecoveryResult)

    fun hidden(workspace: Workspace): List<WorkspaceItem> = workspace.items.filter { item ->
        val grid = if (item.placement.container == ContainerType.DESKTOP) workspace.desktopGrid else workspace.dockGrid
        !WorkspaceLayoutEngine.validate(item.placement, grid)
    }

    fun plan(workspace: Workspace): Plan {
        val hidden = hidden(workspace)
        val items = workspace.items.toMutableList()
        val grid = workspace.desktopGrid
        var pages = workspace.pageCount.coerceAtLeast(1)
        var moved = 0
        for (item in hidden) {
            val span = item.placement
            if (span.spanX > grid.columns || span.spanY > grid.rows) continue
            val others = items.filter { it.id != item.id }
            val target = WorkspaceLayoutEngine.findFirstFree(others, grid, ContainerType.DESKTOP, pages.coerceAtMost(WorkspaceLimits.MAX_PAGES), span.spanX, span.spanY)
                ?: if (pages < WorkspaceLimits.MAX_PAGES) CellPlacement(ContainerType.DESKTOP, pages++, 0, 0, span.spanX, span.spanY)
                else continue
            val index = items.indexOfFirst { it.id == item.id }
            items[index] = item.copy(placement = target)
            moved++
        }
        return Plan(WorkspaceContent(pages, items), HiddenItemsRecoveryResult(moved, hidden.size - moved))
    }
}
