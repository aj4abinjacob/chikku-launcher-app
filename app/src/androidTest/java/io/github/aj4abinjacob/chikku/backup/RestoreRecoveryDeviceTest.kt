package io.github.aj4abinjacob.chikku.backup

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.AppCustomization
import io.github.aj4abinjacob.chikku.core.model.AppCustomizationRepository
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.CellPlacement
import io.github.aj4abinjacob.chikku.core.model.ComponentKey
import io.github.aj4abinjacob.chikku.core.model.ContainerType
import io.github.aj4abinjacob.chikku.core.model.DrawerGroupRepository
import io.github.aj4abinjacob.chikku.core.model.EditResult
import io.github.aj4abinjacob.chikku.core.model.ItemTarget
import io.github.aj4abinjacob.chikku.core.model.WorkspaceContent
import io.github.aj4abinjacob.chikku.core.model.WorkspaceItem
import io.github.aj4abinjacob.chikku.core.model.WorkspaceRepository
import io.github.aj4abinjacob.chikku.core.model.WorkspaceState
import io.github.aj4abinjacob.chikku.core.model.backup.BackupCreateResult
import io.github.aj4abinjacob.chikku.core.model.backup.BackupOperationResult
import io.github.aj4abinjacob.chikku.core.model.backup.BackupService
import io.github.aj4abinjacob.chikku.core.model.backup.RestoreAccess
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.core.model.settings.SettingsImportResult
import io.github.aj4abinjacob.chikku.core.model.settings.SettingsRepository
import io.github.aj4abinjacob.chikku.data.ChikkuDatabase
import io.github.aj4abinjacob.chikku.data.RoomAppCustomizationRepository
import io.github.aj4abinjacob.chikku.data.RoomDrawerGroupRepository
import io.github.aj4abinjacob.chikku.data.RoomWorkspaceRepository
import io.github.aj4abinjacob.chikku.data.SharedPreferencesSettingsRepository
import io.github.aj4abinjacob.chikku.data.WorkspaceGrids
import io.github.aj4abinjacob.chikku.data.restore.FileRestoreJournal
import io.github.aj4abinjacob.chikku.data.restore.RecoveryWorkspace
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/** Uses native SQLite, preferences and AtomicFile in isolated fixtures; never edits the user's layout. */
@RunWith(AndroidJUnit4::class)
class RestoreRecoveryDeviceTest {
    private class Stopped : Error()
    private class Fixture : AutoCloseable {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "reliability-${UUID.randomUUID()}"
        val directory = File(context.noBackupFilesDir, name).apply { mkdirs() }
        lateinit var db: ChikkuDatabase
        lateinit var settings: SharedPreferencesSettingsRepository
        lateinit var workspace: RoomWorkspaceRepository
        lateinit var custom: RoomAppCustomizationRepository
        lateinit var groups: RoomDrawerGroupRepository
        fun reopen() {
            if (::db.isInitialized) db.close()
            settings = SharedPreferencesSettingsRepository(context, Dispatchers.IO, name)
            db = Room.databaseBuilder(context, ChikkuDatabase::class.java, name).build()
            workspace = RoomWorkspaceRepository(db, Dispatchers.IO, settings.settings.map { WorkspaceGrids(it.desktopGrid, it.dockStorageGrid) })
            custom = RoomAppCustomizationRepository(db, Dispatchers.IO)
            groups = RoomDrawerGroupRepository(db, Dispatchers.IO)
        }
        fun service() = BackupService(settings, workspace, custom, groups, "device-test", journal = FileRestoreJournal(directory, Dispatchers.IO))
        override fun close() {
            if (::db.isInitialized) db.close()
            context.deleteDatabase(name)
            context.deleteSharedPreferences(name)
            directory.deleteRecursively()
        }
    }
    private val app = AppKey("synthetic.audit", "Main", 0)
    private val original = WorkspaceContent(1, listOf(
        WorkspaceItem("original", CellPlacement(ContainerType.DESKTOP, 0, 0, 0), ItemTarget.App(app)),
        WorkspaceItem("widget", CellPlacement(ContainerType.DESKTOP, 0, 1, 0), ItemTarget.Widget(77, ComponentKey("synthetic.audit", "Widget", 0))),
    ))
    private suspend fun seed(f: Fixture) {
        f.workspace.replaceAll(original)
        f.custom.setHidden(app, true)
        f.groups.createGroup("Synthetic group")
        f.settings.update { LauncherSettings(onboarding = it.onboarding.copy(completed = true)) }
    }

    @Test fun interruptionAfterEachStoreRecoversOnReopenBeforeWorkspaceObservation() = runBlocking {
        for (stage in 0..3) Fixture().use { f ->
            f.reopen(); seed(f)
            val saved = (f.service().create() as BackupCreateResult.Success).backup
            val incoming = saved.copy(settings = saved.settings.copy(home = saved.settings.home.copy(desktopColumns = 4)),
                workspace = WorkspaceContent(2, emptyList()), customizations = emptyList(), drawerGroups = emptyList())
            val settings = object : SettingsRepository by f.settings {
                override suspend fun importJson(json: String): SettingsImportResult {
                    val result = f.settings.importJson(json); if (stage == 0) throw Stopped(); return result
                }
            }
            val custom = object : AppCustomizationRepository by f.custom {
                override suspend fun replaceAll(customizations: List<AppCustomization>): Boolean {
                    val result = f.custom.replaceAll(customizations); if (stage == 1) throw Stopped(); return result
                }
            }
            val groups = object : DrawerGroupRepository by f.groups {
                override suspend fun replaceAll(groups: List<io.github.aj4abinjacob.chikku.core.model.DrawerGroup>): Boolean {
                    val result = f.groups.replaceAll(groups); if (stage == 2) throw Stopped(); return result
                }
            }
            val workspace = object : WorkspaceRepository by f.workspace {
                override suspend fun replaceAll(content: WorkspaceContent): EditResult {
                    val result = f.workspace.replaceAll(content); if (stage == 3) throw Stopped(); return result
                }
            }
            val interrupted = BackupService(settings, workspace, custom, groups, "device-test", journal = FileRestoreJournal(f.directory, Dispatchers.IO))
            try { interrupted.restore(incoming); error("Expected injected interruption") } catch (_: Stopped) { }
            f.reopen()
            lateinit var recovering: BackupService
            val access = RestoreAccess { recovering.recoverPending() }
            recovering = BackupService(f.settings, f.workspace, f.custom, f.groups, "device-test",
                journal = FileRestoreJournal(f.directory, Dispatchers.IO), access = access)
            val state = RecoveryWorkspace(f.workspace, access).workspace.first { it is WorkspaceState.Ready } as WorkspaceState.Ready
            assertThat(state.workspace.items).containsExactlyElementsIn(original.items)
            assertThat(f.settings.settings.first().home.desktopColumns).isEqualTo(5)
            assertThat(f.custom.all()).containsExactly(AppCustomization(app, hidden = true))
            assertThat(f.groups.all()!!.single().name).isEqualTo("Synthetic group")
            assertThat(FileRestoreJournal(f.directory, Dispatchers.IO).read()).isNull()
        }
    }

    @Test fun successfulRestoreAndUndoSurviveReopeningTheStores() = runBlocking {
        Fixture().use { f ->
            f.reopen(); seed(f)
            val incoming = (f.service().create() as BackupCreateResult.Success).backup.copy(workspace = WorkspaceContent(2, emptyList()))
            assertThat(f.service().restore(incoming)).isEqualTo(BackupOperationResult.Applied)
            f.reopen()
            val restarted = f.service()
            assertThat(restarted.prepare()).isTrue()
            assertThat(restarted.canUndo).isTrue()
            assertThat(restarted.undo()).isTrue()
            assertThat(f.workspace.exportContent()).isEqualTo(original)
            f.reopen()
            val again = f.service()
            assertThat(again.prepare()).isTrue()
            assertThat(again.canUndo).isFalse()
        }
    }
}
