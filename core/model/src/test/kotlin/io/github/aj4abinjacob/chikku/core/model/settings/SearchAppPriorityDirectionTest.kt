package io.github.aj4abinjacob.chikku.core.model.settings

import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.WorkspaceContent
import io.github.aj4abinjacob.chikku.core.model.backup.BackupCodec
import io.github.aj4abinjacob.chikku.core.model.backup.BackupDecodeResult
import io.github.aj4abinjacob.chikku.core.model.backup.ChikkuBackup
import org.junit.Test

class SearchAppPriorityDirectionTest {
    @Test
    fun everyDirectionSurvivesSettingsAndPortableBackup() {
        for (direction in SearchAppPriorityDirection.entries) {
            val settings = LauncherSettings(search = SearchSettings(maxAppResults = 5, appPriorityDirection = direction))
            assertThat(LauncherSettingsCodec.decode(LauncherSettingsCodec.encode(settings))).isEqualTo(settings)
            val backup = ChikkuBackup(1, "test", settings, WorkspaceContent(1, emptyList()), emptyList(), emptyList())
            val decoded = BackupCodec.decode(BackupCodec.encode(backup)) as BackupDecodeResult.Success
            assertThat(decoded.backup.settings).isEqualTo(settings)
        }
    }

    @Test
    fun missingAndFutureValuesKeepExistingLanguageDirection() {
        for (payload in listOf("{}", """{"search":{"maxAppResults":5}}""",
            """{"search":{"appPriorityDirection":"future"}}""")) {
            assertThat(LauncherSettingsCodec.decode(payload).search.appPriorityDirection)
                .isEqualTo(SearchAppPriorityDirection.FOLLOW_LANGUAGE)
        }
    }
}
