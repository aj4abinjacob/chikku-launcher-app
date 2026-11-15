package io.github.aj4abinjacob.chikku.core.model.settings

import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.WorkspaceContent
import io.github.aj4abinjacob.chikku.core.model.backup.BackupCodec
import io.github.aj4abinjacob.chikku.core.model.backup.BackupDecodeResult
import io.github.aj4abinjacob.chikku.core.model.backup.ChikkuBackup
import org.junit.Test

class FolderColorIndependenceTest {
    @Test fun oldSharedColorMigratesWithoutChangingEitherAppearance() {
        val old = LauncherSettingsCodec.decode("""{"folders":{"backgroundColor":-65536}}""")
        assertThat(old.folders.backgroundColor).isEqualTo(0xFFFF0000.toInt())
        assertThat(old.folders.iconBackgroundColor).isEqualTo(old.folders.backgroundColor)
        val changed = old.copy(folders = old.folders.copy(backgroundColor = 0xFF0000FF.toInt()))
        val reloaded = LauncherSettingsCodec.decode(LauncherSettingsCodec.encode(changed))
        assertThat(reloaded.folders.iconBackgroundColor).isEqualTo(0xFFFF0000.toInt())
        assertThat(reloaded.folders.backgroundColor).isEqualTo(0xFF0000FF.toInt())
    }

    @Test fun iconDefaultAndIndependentOverrideSurviveSaveAndBackup() {
        for (iconColor in listOf(null, 0xFF00FF00.toInt())) {
            val settings = LauncherSettings(folders = FolderSettings(
                backgroundColor = 0xFF0000FF.toInt(), iconBackgroundColor = iconColor,
            ))
            assertThat(LauncherSettingsCodec.decode(LauncherSettingsCodec.encode(settings))).isEqualTo(settings)
            val backup = ChikkuBackup(1, "test", settings, WorkspaceContent(1, emptyList()), emptyList(), emptyList())
            val decoded = BackupCodec.decode(BackupCodec.encode(backup)) as BackupDecodeResult.Success
            assertThat(decoded.backup.settings).isEqualTo(settings)
        }
        assertThat(LauncherSettingsCodec.decode("{}").folders.iconBackgroundColor).isNull()
    }
}
