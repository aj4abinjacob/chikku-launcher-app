package io.github.aj4abinjacob.chikku.core.model.settings

import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.WorkspaceContent
import io.github.aj4abinjacob.chikku.core.model.backup.BackupCodec
import io.github.aj4abinjacob.chikku.core.model.backup.BackupDecodeResult
import io.github.aj4abinjacob.chikku.core.model.backup.ChikkuBackup
import org.junit.Test

class HomeSearchBarStyleTest {
    @Test fun minimalStyleSurvivesSettingsAndPortableBackupWithoutChangingSearchOptIns() {
        val settings = LauncherSettings(search = SearchSettings(widgetStyle = HomeSearchBarStyle.MINIMAL))
        assertThat(LauncherSettingsCodec.decode(LauncherSettingsCodec.encode(settings))).isEqualTo(settings)
        val backup = ChikkuBackup(1, "test", settings, WorkspaceContent(1, emptyList()), emptyList(), emptyList())
        val decoded = BackupCodec.decode(BackupCodec.encode(backup)) as BackupDecodeResult.Success
        assertThat(decoded.backup.settings).isEqualTo(settings)
        assertThat(decoded.backup.settings.search.onlineSuggestions).isFalse()
        assertThat(LauncherSettingsCodec.decode("{}").search.widgetStyle).isEqualTo(HomeSearchBarStyle.PROVIDER)
        assertThat(LauncherSettingsCodec.decode("""{"search":{"widgetStyle":"future"}}""").search.widgetStyle)
            .isEqualTo(HomeSearchBarStyle.PROVIDER)
    }

    @Test fun barIconSurvivesSettingsAndPortableBackupAndOlderOrUnknownValuesFallBackToNose() {
        for (icon in HomeSearchBarIcon.entries) {
            val settings = LauncherSettings(search = SearchSettings(widgetIcon = icon))
            assertThat(LauncherSettingsCodec.decode(LauncherSettingsCodec.encode(settings))).isEqualTo(settings)
            val backup = ChikkuBackup(1, "test", settings, WorkspaceContent(1, emptyList()), emptyList(), emptyList())
            val decoded = BackupCodec.decode(BackupCodec.encode(backup)) as BackupDecodeResult.Success
            assertThat(decoded.backup.settings.search.widgetIcon).isEqualTo(icon)
        }
        assertThat(LauncherSettingsCodec.decode("""{"search":{"widgetStyle":"MINIMAL"}}""").search.widgetIcon)
            .isEqualTo(HomeSearchBarIcon.NOSE)
        assertThat(LauncherSettingsCodec.decode("""{"search":{"widgetIcon":"future"}}""").search.widgetIcon)
            .isEqualTo(HomeSearchBarIcon.NOSE)
    }
}
