package io.github.aj4abinjacob.chikku.core.model.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DrawerTabStyleTest {
    @Test fun segmentedTabsRoundTripAndUnknownStyleKeepsTheDefault() {
        val settings = LauncherSettings(drawer = DrawerSettings(tabStyle = DrawerTabStyle.SEGMENTED, tabAccentColor = -9277517))
        assertThat(LauncherSettingsCodec.decode(LauncherSettingsCodec.encode(settings))).isEqualTo(settings)
        assertThat(LauncherSettingsCodec.decode("""{"drawer":{"tabStyle":"future"}}""").drawer.tabStyle)
            .isEqualTo(DrawerTabStyle.COMPACT)
        assertThat(LauncherSettingsCodec.decode("{}").drawer.tabStyle).isEqualTo(DrawerTabStyle.COMPACT)
    }
    @Test fun shortcutAppearanceBackupKeepsProviderIdentityAndOldBackupsStillDecode() {
        val target = io.github.aj4abinjacob.chikku.core.model.ItemTarget.Shortcut(
            "com.browser", "site-id", 0, "Site", "com.icons", "site")
        val item = io.github.aj4abinjacob.chikku.core.model.WorkspaceItem("id",
            io.github.aj4abinjacob.chikku.core.model.CellPlacement(
                io.github.aj4abinjacob.chikku.core.model.ContainerType.DESKTOP, 0, 0, 0), target)
        val backup = io.github.aj4abinjacob.chikku.core.model.backup.ChikkuBackup(1, "test", LauncherSettings(),
            io.github.aj4abinjacob.chikku.core.model.WorkspaceContent(1, listOf(item)), emptyList(), emptyList())
        val encoded = io.github.aj4abinjacob.chikku.core.model.backup.BackupCodec.encode(backup)
        val decoded = io.github.aj4abinjacob.chikku.core.model.backup.BackupCodec.decode(encoded)
            as io.github.aj4abinjacob.chikku.core.model.backup.BackupDecodeResult.Success
        assertThat(decoded.backup.workspace.items.single().target).isEqualTo(target)
    }

}
