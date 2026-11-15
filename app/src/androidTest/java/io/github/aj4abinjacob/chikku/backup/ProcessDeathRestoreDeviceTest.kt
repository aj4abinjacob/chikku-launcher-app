package io.github.aj4abinjacob.chikku.backup

import android.content.Context
import android.os.Process
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.*
import io.github.aj4abinjacob.chikku.core.model.backup.*
import io.github.aj4abinjacob.chikku.core.model.settings.*
import io.github.aj4abinjacob.chikku.data.*
import io.github.aj4abinjacob.chikku.data.restore.FileRestoreJournal
import io.github.aj4abinjacob.chikku.data.restore.RecoveryWorkspace
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeNotNull
import org.junit.Test
import org.junit.runner.RunWith

/** Two explicit invocations: pause after a durable store write, force-stop, then recover in a new PID.
 * Skips without an orchestrator-supplied UUID; ordinary connected suites must never hang or kill HOME.
 */
@RunWith(AndroidJUnit4::class)
class ProcessDeathRestoreDeviceTest {
    private class Fixture(val token: String) : AutoCloseable {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "restore-process-$token"
        val directory = File(context.noBackupFilesDir, name).apply { mkdirs() }
        val db = Room.databaseBuilder(context, ChikkuDatabase::class.java, name).addMigrations(*ChikkuMigrations.ALL).build()
        val settings = SharedPreferencesSettingsRepository(context, Dispatchers.IO, name)
        val workspace = RoomWorkspaceRepository(db, Dispatchers.IO)
        val custom = RoomAppCustomizationRepository(db, Dispatchers.IO)
        val groups = RoomDrawerGroupRepository(db, Dispatchers.IO)
        val journal = FileRestoreJournal(directory, Dispatchers.IO)
        fun service() = BackupService(settings, workspace, custom, groups, "process-fixture", journal = journal)
        override fun close() { db.close() }
        fun delete() {
            db.close()
            context.deleteDatabase(name)
            context.deleteSharedPreferences(name)
            directory.deleteRecursively()
        }
    }

    private fun fixture(): Fixture {
        val token = InstrumentationRegistry.getArguments().getString("fixtureId")
        assumeNotNull(token)
        val normalized = UUID.fromString(token).toString()
        require(normalized == token) { "Expected a canonical fixture UUID" }
        return Fixture(normalized)
    }

    @Test fun pauseRestoreForExternalProcessKill(): Unit = runBlocking {
        fixture().use { f ->
            val stage = InstrumentationRegistry.getArguments().getString("cutAfterStore")!!.toInt()
            require(stage in 0..3)
            val app = AppKey("synthetic.process", "Main", 0)
            val original = WorkspaceContent(1, listOf(
                WorkspaceItem("app", CellPlacement(ContainerType.DESKTOP, 0, 0, 0), ItemTarget.App(app)),
                WorkspaceItem("widget", CellPlacement(ContainerType.DESKTOP, 0, 1, 0),
                    ItemTarget.Widget(77, ComponentKey("synthetic.widget", "Provider", 0))),
            ))
            assertThat(f.workspace.replaceAll(original)).isInstanceOf(EditResult.Applied::class.java)
            assertThat(f.custom.setHidden(app, true)).isTrue()
            assertThat(f.groups.createGroup("Synthetic group")).isNotNull()
            f.settings.update { it.copy(home = it.home.copy(desktopColumns = 6)) }
            val saved = (f.service().create() as BackupCreateResult.Success).backup
            val incoming = saved.copy(settings = saved.settings.copy(home = saved.settings.home.copy(desktopColumns = 4)),
                workspace = WorkspaceContent(2, emptyList()), customizations = emptyList(), drawerGroups = emptyList())
            suspend fun pause(): Nothing {
                // Published only after the actual store write and journal preparation have returned.
                FileOutputStream(File(f.directory, "cut.marker")).use {
                    it.write("${Process.myPid()}:$stage".toByteArray(Charsets.UTF_8)); it.fd.sync()
                }
                awaitCancellation()
            }
            val settings = object : SettingsRepository by f.settings {
                override suspend fun importJson(json: String): SettingsImportResult {
                    val result = f.settings.importJson(json)
                    assertThat(result).isEqualTo(SettingsImportResult.Applied)
                    if (stage == 0) pause()
                    return result
                }
            }
            val custom = object : AppCustomizationRepository by f.custom {
                override suspend fun replaceAll(customizations: List<AppCustomization>): Boolean {
                    val result = f.custom.replaceAll(customizations)
                    assertThat(result).isTrue()
                    if (stage == 1) pause()
                    return result
                }
            }
            val groups = object : DrawerGroupRepository by f.groups {
                override suspend fun replaceAll(groups: List<DrawerGroup>): Boolean {
                    val result = f.groups.replaceAll(groups)
                    assertThat(result).isTrue()
                    if (stage == 2) pause()
                    return result
                }
            }
            val workspace = object : WorkspaceRepository by f.workspace {
                override suspend fun replaceAll(content: WorkspaceContent): EditResult {
                    val result = f.workspace.replaceAll(content)
                    assertThat(result).isInstanceOf(EditResult.Applied::class.java)
                    if (stage == 3) pause()
                    return result
                }
            }
            BackupService(settings, workspace, custom, groups, "process-fixture", journal = f.journal).restore(incoming)
            error("External kill fixture unexpectedly completed")
        }
    }

    @Test fun recoverAfterExternalProcessKill() = runBlocking {
        val f = fixture()
        try {
            val marker = File(f.directory, "cut.marker").readText().split(':')
            assertThat(Process.myPid()).isNotEqualTo(marker[0].toInt())
            val record = checkNotNull(f.journal.read())
            assertThat(record.committed).isFalse()
            assertThat(record.previous.workspace.items.map { it.id }).containsExactly("app", "widget")
            lateinit var service: BackupService
            val access = RestoreAccess { service.recoverPending() }
            service = BackupService(f.settings, f.workspace, f.custom, f.groups, "process-fixture", journal = f.journal, access = access)
            val first = RecoveryWorkspace(f.workspace, access).workspace.first { it !is WorkspaceState.Loading }
            assertThat(first).isInstanceOf(WorkspaceState.Ready::class.java)
            assertThat(f.workspace.exportContent()).isEqualTo(record.previous.workspace)
            assertThat(f.settings.exportJson()).isEqualTo(record.previous.settingsJson)
            assertThat(f.custom.all()).containsExactlyElementsIn(record.previous.customizations)
            assertThat(f.groups.all()).containsExactlyElementsIn(record.previous.groups)
            assertThat(f.journal.read()).isNull()
            assertThat(service.canUndo).isFalse()
        } finally { f.delete() }
    }
}
