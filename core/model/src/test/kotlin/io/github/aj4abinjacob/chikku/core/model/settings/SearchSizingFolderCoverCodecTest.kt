package io.github.aj4abinjacob.chikku.core.model.settings

import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.GestureAction
import io.github.aj4abinjacob.chikku.core.model.WorkspaceContent
import io.github.aj4abinjacob.chikku.core.model.backup.BackupCodec
import io.github.aj4abinjacob.chikku.core.model.backup.BackupDecodeResult
import io.github.aj4abinjacob.chikku.core.model.backup.ChikkuBackup
import org.junit.Test

class SearchSizingFolderCoverCodecTest {
    @Test
    fun sizeAndComponentProfileCoverSurviveStorageAndBackupMap() {
        val value = LauncherSettings(
            search = SearchSettings(widgetWidthPercent = 65, widgetHeightDp = 80,
                widgetHorizontalPaddingDp = 35, widgetVerticalPaddingDp = 15, widgetDockGapDp = 32),
            folders = FolderSettings(covers = mapOf(
                "first" to FolderCover(FolderCoverMode.FIRST_APP),
                "last" to FolderCover(FolderCoverMode.LAST_APP),
                "selected" to FolderCover(FolderCoverMode.SELECTED_APP, AppKey("a", "a.Main", 11)),
                "shortcut" to FolderCover(FolderCoverMode.SELECTED_APP, shortcut = FolderCoverShortcut("b", "pin-1", 11)),
            )),
        )
        assertThat(LauncherSettingsCodec.decode(LauncherSettingsCodec.encode(value))).isEqualTo(value)
        val backup = ChikkuBackup(1, "test", value, WorkspaceContent(1, emptyList()), emptyList(), emptyList())
        val restored = BackupCodec.decode(BackupCodec.encode(backup)) as BackupDecodeResult.Success
        assertThat(restored.backup.settings).isEqualTo(value)
        assertThat(GestureAction.decode(GestureAction.encode(GestureAction.OpenFolder))).isEqualTo(GestureAction.OpenFolder)
        assertThat(GestureAction.simpleActions).doesNotContain(GestureAction.OpenFolder)
    }

    @Test
    fun oldAndMalformedScalarSettingsKeepUsableDefaults() {
        assertThat(LauncherSettingsCodec.decode("{}")).isEqualTo(LauncherSettings())
        val decoded = LauncherSettingsCodec.decode("""{"search":{"widgetWidthPercent":-1,"widgetHeightDp":999,"widgetHorizontalPaddingDp":-8,"widgetVerticalPaddingDp":999,"widgetDockGapDp":999}}""")
        assertThat(decoded.search.widgetWidthPercent).isEqualTo(40)
        assertThat(decoded.search.widgetHeightDp).isEqualTo(96)
        assertThat(decoded.search.widgetHorizontalPaddingDp).isEqualTo(0)
        assertThat(decoded.search.widgetVerticalPaddingDp).isEqualTo(24)
        assertThat(decoded.search.widgetDockGapDp).isEqualTo(64)
        assertThat(LauncherSettingsCodec.decode("""{"search":{"widgetDockGapDp":-9}}""").search.widgetDockGapDp).isEqualTo(0)
        assertThat(LauncherSettingsCodec.decode("""{"search":{"widgetHeightDp":80}}""").search.widgetDockGapDp).isEqualTo(12)
        assertThat(decoded.coerced()).isEqualTo(decoded)
    }

    @Test
    fun malformedCoverIdentityRejectsRecoverablyRatherThanSilentlyResetting() {
        assertThat(LauncherSettingsCodec.decodeOrNull("""{"folders":{"covers":{"folder":{"mode":"SELECTED_APP"}}}}""")).isNull()
        assertThat(LauncherSettingsCodec.decodeOrNull("""{"folders":{"covers":{"folder":{"mode":"FUTURE"}}}}""")).isNull()
        assertThat(LauncherSettingsCodec.decodeOrNull("""{"folders":{"covers":{"folder":{"mode":"SELECTED_APP","shortcut":{"packageName":"b","userSerial":0}}}}}""")).isNull()
        assertThat(LauncherSettingsCodec.decodeOrNull("""{"folders":{"covers":{"folder":{"mode":"SELECTED_APP",""" +
            """"app":{"packageName":"a","className":"a.Main","userSerial":0},"shortcut":{"packageName":"b","shortcutId":"x","userSerial":0}}}}}""")).isNull()
    }
}
