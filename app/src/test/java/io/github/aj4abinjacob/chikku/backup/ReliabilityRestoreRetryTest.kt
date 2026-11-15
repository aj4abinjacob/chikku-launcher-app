package io.github.aj4abinjacob.chikku.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.CellPlacement
import io.github.aj4abinjacob.chikku.core.model.ComponentKey
import io.github.aj4abinjacob.chikku.core.model.ContainerType
import io.github.aj4abinjacob.chikku.core.model.EditResult
import io.github.aj4abinjacob.chikku.core.model.ItemTarget
import io.github.aj4abinjacob.chikku.core.model.WorkspaceContent
import io.github.aj4abinjacob.chikku.core.model.WorkspaceItem
import io.github.aj4abinjacob.chikku.core.model.WorkspaceRepository
import io.github.aj4abinjacob.chikku.core.model.backup.BackupOperationResult
import io.github.aj4abinjacob.chikku.core.model.backup.BackupCreateResult
import io.github.aj4abinjacob.chikku.core.model.backup.BackupService
import io.github.aj4abinjacob.chikku.core.model.backup.RestoreAccess
import io.github.aj4abinjacob.chikku.data.ChikkuDatabase
import io.github.aj4abinjacob.chikku.data.RoomAppCustomizationRepository
import io.github.aj4abinjacob.chikku.data.RoomDrawerGroupRepository
import io.github.aj4abinjacob.chikku.data.RoomWorkspaceRepository
import io.github.aj4abinjacob.chikku.data.SharedPreferencesSettingsRepository
import io.github.aj4abinjacob.chikku.data.restore.FileRestoreJournal
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Storage-backed failed rollback followed by a fresh repository/service retry. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReliabilityRestoreRetryTest {
    @Test fun failedRollbackSurvivesReopenAndRetryPreservesTheOriginalWidgetId() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val token = UUID.randomUUID().toString()
        val dbName = "reliability-retry-$token"
        val prefsName = "reliability-retry-$token"
        val journalDir = File(context.cacheDir, "reliability-retry-$token").apply { mkdirs() }
        val journal = FileRestoreJournal(journalDir, Dispatchers.IO)
        var db: ChikkuDatabase? = null
        try {
            fun open(): Triple<SharedPreferencesSettingsRepository, RoomWorkspaceRepository, RoomAppCustomizationRepository> {
                val settings = SharedPreferencesSettingsRepository(context, Dispatchers.IO, prefsName)
                db = Room.databaseBuilder(context, ChikkuDatabase::class.java, dbName).build()
                return Triple(settings, RoomWorkspaceRepository(db!!, Dispatchers.IO,
                    settings.settings.map { io.github.aj4abinjacob.chikku.data.WorkspaceGrids(it.desktopGrid, it.dockStorageGrid) }),
                    RoomAppCustomizationRepository(db!!, Dispatchers.IO))
            }
            var (settings, workspace, customizations) = open()
            var groups = RoomDrawerGroupRepository(db!!, Dispatchers.IO)
            val app = AppKey("synthetic.recovery", "Main", 0)
            val provider = ComponentKey("synthetic.clock", "Widget", 0)
            val original = WorkspaceContent(1, listOf(
                WorkspaceItem("app", CellPlacement(ContainerType.DESKTOP, 0, 0, 0), ItemTarget.App(app)),
                WorkspaceItem("widget", CellPlacement(ContainerType.DESKTOP, 0, 1, 0), ItemTarget.Widget(7301, provider)),
            ))
            assertThat(workspace.replaceAll(original)).isInstanceOf(EditResult.Applied::class.java)
            assertThat(customizations.update(app) { it.copy(hidden = true) }).isTrue()
            assertThat(groups.createGroup("Synthetic recovery group")).isNotNull()
            val beforeSettings = settings.exportJson()
            var failRollback = true
            val failingWorkspace = object : WorkspaceRepository by workspace {
                override suspend fun replaceAll(content: WorkspaceContent): EditResult =
                    if (content.items.isEmpty()) EditResult.Rejected(io.github.aj4abinjacob.chikku.core.model.RejectReason.STORAGE_ERROR)
                    else workspace.replaceAll(content)

                override suspend fun recoverContent(content: WorkspaceContent): EditResult =
                    if (failRollback) {
                        failRollback = false
                        EditResult.Rejected(io.github.aj4abinjacob.chikku.core.model.RejectReason.STORAGE_ERROR)
                    } else workspace.recoverContent(content)
            }
            val backup = (BackupService(settings, workspace, customizations, groups, "test", journal = journal).create()
                as BackupCreateResult.Success).backup
            val failed = BackupService(settings, failingWorkspace, customizations, groups, "test", journal = journal)
                .restore(backup.copy(workspace = WorkspaceContent(1, emptyList())))
            assertThat(failed).isEqualTo(BackupOperationResult.Failed(BackupOperationResult.Step.WORKSPACE, recoveryRequired = true))
            assertThat(journal.read()!!.committed).isFalse()

            db!!.close(); db = null
            val reopened = open()
            settings = reopened.first
            workspace = reopened.second
            customizations = reopened.third
            groups = RoomDrawerGroupRepository(db!!, Dispatchers.IO)
            lateinit var retry: BackupService
            val access = RestoreAccess { retry.recoverPending() }
            retry = BackupService(settings, workspace, customizations, groups, "test", journal = journal, access = access)
            assertThat(retry.prepare()).isTrue()
            assertThat(workspace.exportContent()).isEqualTo(original)
            assertThat(settings.exportJson()).isEqualTo(beforeSettings)
            assertThat(customizations.all()).containsExactly(io.github.aj4abinjacob.chikku.core.model.AppCustomization(app, hidden = true))
            assertThat(groups.all()!!.map { it.name }).containsExactly("Synthetic recovery group")
            assertThat(journal.read()).isNull()
            assertThat(retry.prepare()).isTrue()
            assertThat(workspace.exportContent()!!.items.single { it.id == "widget" }.target).isEqualTo(ItemTarget.Widget(7301, provider))
        } finally {
            db?.close()
            context.deleteDatabase(dbName)
            context.deleteSharedPreferences(prefsName)
            journalDir.deleteRecursively()
        }
    }
}
