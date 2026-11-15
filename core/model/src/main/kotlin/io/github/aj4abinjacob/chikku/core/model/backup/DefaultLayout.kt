package io.github.aj4abinjacob.chikku.core.model.backup

import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.CellPlacement
import io.github.aj4abinjacob.chikku.core.model.ContainerType
import io.github.aj4abinjacob.chikku.core.model.ItemTarget
import io.github.aj4abinjacob.chikku.core.model.WorkspaceContent
import io.github.aj4abinjacob.chikku.core.model.WorkspaceItem

/** The layout "Reset to default" re-seeds: one empty desktop page and the suggested dock apps. */
object DefaultLayout {
    /**
     * [dockApps] (e.g. default dialer, SMS, browser, camera) go left to right into the first
     * [dockColumns] dock cells; duplicates are dropped. [newId] supplies stable item ids.
     */
    fun content(dockApps: List<AppKey>, dockColumns: Int, newId: () -> String): WorkspaceContent =
        WorkspaceContent(
            pageCount = 1,
            items = dockApps.distinct().take(dockColumns.coerceAtLeast(0)).mapIndexed { x, key ->
                WorkspaceItem(
                    id = newId(),
                    placement = CellPlacement(ContainerType.DOCK, page = 0, x = x, y = 0),
                    target = ItemTarget.App(key),
                )
            },
        )
}
