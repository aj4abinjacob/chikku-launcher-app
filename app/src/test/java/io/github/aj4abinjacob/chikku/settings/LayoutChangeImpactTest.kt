package io.github.aj4abinjacob.chikku.settings

import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.*
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.settings.components.newlyHiddenItems
import org.junit.Test

class LayoutChangeImpactTest {
    @Test fun countsOnlyNewlyHiddenPlacementsAcrossDesktopAndDockPages() {
        val settings = LauncherSettings().let { it.copy(dock = it.dock.copy(iconCount = 4, pages = 2)) }
        fun item(id: String, container: ContainerType, x: Int) = WorkspaceItem(id, CellPlacement(container, 0, x, 0), ItemTarget.App(AppKey(id, "Main", 0)))
        val workspace = Workspace(settings.desktopGrid, settings.dockStorageGrid, 1, listOf(
            item("desktop", ContainerType.DESKTOP, 4), item("already-hidden", ContainerType.DESKTOP, 9),
            item("dock", ContainerType.DOCK, 7), item("visible", ContainerType.DESKTOP, 0),
        ))
        assertThat(newlyHiddenItems(workspace, settings, settings.copy(home = settings.home.copy(desktopColumns = 3)))).isEqualTo(1)
        assertThat(newlyHiddenItems(workspace, settings, settings.copy(dock = settings.dock.copy(pages = 1)))).isEqualTo(1)
        assertThat(newlyHiddenItems(workspace, settings, settings.copy(home = settings.home.copy(desktopColumns = 10)))).isEqualTo(0)
    }
}
