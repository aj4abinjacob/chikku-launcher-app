package io.github.aj4abinjacob.chikku.onboarding

import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.CellPlacement
import io.github.aj4abinjacob.chikku.core.model.ContainerType
import io.github.aj4abinjacob.chikku.core.model.ItemTarget
import io.github.aj4abinjacob.chikku.core.model.Workspace
import io.github.aj4abinjacob.chikku.core.model.WorkspaceItem
import io.github.aj4abinjacob.chikku.core.model.onboarding.LayoutPreset
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.settings.components.newlyHiddenItems
import org.junit.Test

class QuickStartHiddenPlacementAuditTest {
    @Test fun presetWarningCountsDesktopAndDockAndKeepsOriginalPlacements() {
        val settings = LauncherSettings()
        val items = listOf(
            WorkspaceItem(
                "desktop",
                CellPlacement(ContainerType.DESKTOP, 0, 4, 4),
                ItemTarget.App(AppKey("synthetic", "synthetic.Main", 0)),
            ),
            WorkspaceItem(
                "dock",
                CellPlacement(ContainerType.DOCK, 0, 4, 0),
                ItemTarget.App(AppKey("synthetic", "synthetic.Main", 0)),
            ),
        )
        val workspace = Workspace(settings.desktopGrid, settings.dockStorageGrid, 1, items)
        val proposed = LayoutPreset.EXTRA_LARGE.applyTo(settings)
        assertThat(newlyHiddenItems(workspace, settings, proposed)).isEqualTo(2)
        assertThat(workspace.items).containsExactlyElementsIn(items)
        assertThat(newlyHiddenItems(workspace.copy(items = emptyList()), settings, proposed)).isEqualTo(0)
        assertThat(newlyHiddenItems(workspace, proposed, proposed)).isEqualTo(0)
    }
}
