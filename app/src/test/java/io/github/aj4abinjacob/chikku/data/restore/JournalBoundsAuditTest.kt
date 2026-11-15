package io.github.aj4abinjacob.chikku.data.restore

import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.CellPlacement
import io.github.aj4abinjacob.chikku.core.model.ComponentKey
import io.github.aj4abinjacob.chikku.core.model.ContainerType
import io.github.aj4abinjacob.chikku.core.model.ItemTarget
import io.github.aj4abinjacob.chikku.core.model.WorkspaceContent
import io.github.aj4abinjacob.chikku.core.model.WorkspaceItem
import io.github.aj4abinjacob.chikku.core.model.WorkspaceLimits
import io.github.aj4abinjacob.chikku.core.model.backup.RestoreCheckpoint
import io.github.aj4abinjacob.chikku.core.model.backup.RestoreRecord
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettingsCodec
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
class JournalBoundsAuditTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun historicalPageAndGeometryRetainWidgetIdentityAcrossReopen() = runTest {
        val workspace = WorkspaceContent(
            WorkspaceLimits.MAX_PAGES + 1,
            listOf(
                WorkspaceItem(
                    "synthetic-widget",
                    CellPlacement(ContainerType.DESKTOP, WorkspaceLimits.MAX_PAGES, 70, 0),
                    ItemTarget.Widget(77, ComponentKey("synthetic.clock", "Widget", 0)),
                ),
            ),
        )
        val record =
            RestoreRecord(
                RestoreCheckpoint(
                    LauncherSettingsCodec.encode(LauncherSettings()),
                    workspace,
                    emptyList(),
                    emptyList(),
                ),
            )
        val directory = temp.newFolder()
        assertThat(FileRestoreJournal(directory, Dispatchers.IO).write(record)).isTrue()
        assertThat(FileRestoreJournal(directory, Dispatchers.IO).read()).isEqualTo(record)
    }
}
