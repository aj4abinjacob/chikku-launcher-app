package io.github.aj4abinjacob.chikku.data.restore

import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.CellPlacement
import io.github.aj4abinjacob.chikku.core.model.ComponentKey
import io.github.aj4abinjacob.chikku.core.model.ContainerType
import io.github.aj4abinjacob.chikku.core.model.ItemTarget
import io.github.aj4abinjacob.chikku.core.model.WorkspaceContent
import io.github.aj4abinjacob.chikku.core.model.WorkspaceItem
import io.github.aj4abinjacob.chikku.core.model.backup.RestoreCheckpoint
import io.github.aj4abinjacob.chikku.core.model.backup.RestoreRecord
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettingsCodec
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FileRestoreJournalTest {
    @get:Rule val temp = TemporaryFolder()
    private fun record() = RestoreRecord(RestoreCheckpoint(
        LauncherSettingsCodec.encode(LauncherSettings()),
        WorkspaceContent(1, listOf(WorkspaceItem("widget", CellPlacement(ContainerType.DESKTOP, 0, 0, 0),
            ItemTarget.Widget(77, ComponentKey("synthetic.clock", "Widget", 0))))), emptyList(), emptyList(),
    ))

    @Test fun reopenPreservesPendingAndCommittedRecordsIncludingLiveWidgetIdentity() = runTest {
        val directory = temp.newFolder()
        val journal = FileRestoreJournal(directory, Dispatchers.IO)
        val record = record()
        assertThat(journal.write(record)).isTrue()
        assertThat(FileRestoreJournal(directory, Dispatchers.IO).read()).isEqualTo(record)
        assertThat(journal.write(record.copy(committed = true))).isTrue()
        assertThat(FileRestoreJournal(directory, Dispatchers.IO).read()).isEqualTo(record.copy(committed = true))
        assertThat(journal.clear()).isTrue()
        assertThat(journal.read()).isNull()
    }

    @Test fun damagedJournalIsPreservedAndNeverTreatedAsNoRecoveryNeeded() = runTest {
        val directory = temp.newFolder()
        val file = File(directory, "restore-journal.json").apply { writeText("truncated") }
        try { FileRestoreJournal(directory, Dispatchers.IO).read(); error("expected damaged read") } catch (_: IOException) { }
        assertThat(file.readText()).isEqualTo("truncated")
    }

    @Test fun failedPreparationDoesNotReplaceExistingRecoveryRecord() = runTest {
        val directory = temp.newFolder()
        val journal = FileRestoreJournal(directory, Dispatchers.IO)
        assertThat(journal.write(record())).isTrue()
        assertThat(journal.write(record().copy(previous = record().previous.copy(settingsJson = "invalid")))).isFalse()
        assertThat(journal.read()).isEqualTo(record())
    }
}
