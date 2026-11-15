package io.github.aj4abinjacob.chikku.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.DeviceTestSupport
import io.github.aj4abinjacob.chikku.core.model.CellPlacement
import io.github.aj4abinjacob.chikku.core.model.ContainerType
import io.github.aj4abinjacob.chikku.core.model.EditResult
import io.github.aj4abinjacob.chikku.core.model.ItemTarget
import io.github.aj4abinjacob.chikku.core.model.Workspace
import io.github.aj4abinjacob.chikku.core.model.WorkspaceState
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real SQLite on the device's filesystem, using test-only database files (never the production
 * `chikku-workspace.db`) and a test-only recovery directory (never the app's real
 * `filesDir/recovery`, whose marker would show the corrupt-layout notice in the debug app).
 *
 * ChikkuDatabaseFactory.create defaults to [ChikkuDatabase.NAME] and the production recovery directory, so the
 * corruption test rebuilds the same wiring (CorruptionPreservingOpenHelperFactory + MetadataCallback)
 * around a custom name and copy destination.
 */
@RunWith(AndroidJUnit4::class)
class WorkspacePersistenceDeviceTest {
    private val context: Context get() = DeviceTestSupport.targetContext
    private lateinit var dbName: String
    private lateinit var recoveryDir: File
    private val openDatabases = mutableListOf<ChikkuDatabase>()

    @Before
    fun setUp() {
        dbName = "qa-test-${UUID.randomUUID()}.db"
        recoveryDir = File(context.cacheDir, "qa-recovery-${UUID.randomUUID()}")
    }

    @After
    fun tearDown() {
        openDatabases.forEach { runCatching { it.close() } }
        context.deleteDatabase(dbName)
        recoveryDir.deleteRecursively()
    }

    /** Same open-helper wiring as production, with a test name and a test copy destination. */
    private fun openDatabase(): ChikkuDatabase {
        val factory = CorruptionPreservingOpenHelperFactory(FrameworkSQLiteOpenHelperFactory()) { path ->
            recoveryDir.mkdirs()
            File(path).copyTo(File(recoveryDir, "corrupt.db"), overwrite = true)
        }
        return Room.databaseBuilder(context, ChikkuDatabase::class.java, dbName)
            .openHelperFactory(factory)
            .addCallback(ChikkuDatabaseFactory.MetadataCallback)
            .build()
            .also { openDatabases += it }
    }

    private fun ready(repository: RoomWorkspaceRepository): Workspace = runBlocking {
        withTimeout(DeviceTestSupport.LOAD_TIMEOUT_MS) {
            val state = repository.workspace.first { it !is WorkspaceState.Loading }
            check(state is WorkspaceState.Ready) { "Workspace not ready: $state" }
            state.workspace
        }
    }

    @Test
    fun addedAppSurvivesDatabaseCloseAndReopen() = runBlocking {
        val key = DeviceTestSupport.settingsKey()
        val placement = CellPlacement(ContainerType.DESKTOP, page = 0, x = 2, y = 3)

        val firstDb = openDatabase()
        val result = RoomWorkspaceRepository(firstDb, Dispatchers.IO).addApp(key, placement)
        assertThat(result).isInstanceOf(EditResult.Applied::class.java)
        val itemId = (result as EditResult.Applied).itemId
        // Simulates process death: the connection and every in-memory cache are gone.
        firstDb.close()
        openDatabases -= firstDb

        val reopened = ready(RoomWorkspaceRepository(openDatabase(), Dispatchers.IO))
        val item = reopened.items.single()
        assertThat(item.id).isEqualTo(itemId)
        assertThat(item.placement).isEqualTo(placement)
        assertThat(item.target).isEqualTo(ItemTarget.App(key))
        assertThat(context.getDatabasePath(dbName).exists()).isTrue()
    }

    @Test
    fun removalUndoSnapshotSurvivesReopen() = runBlocking {
        val key = DeviceTestSupport.settingsKey()
        val placement = CellPlacement(ContainerType.DOCK, page = 0, x = 1, y = 0)
        val firstDb = openDatabase()
        val first = RoomWorkspaceRepository(firstDb, Dispatchers.IO)
        val itemId = (first.addApp(key, placement) as EditResult.Applied).itemId
        assertThat(first.removeItem(itemId)).isEqualTo(EditResult.Applied(itemId))
        firstDb.close()
        openDatabases -= firstDb

        val second = RoomWorkspaceRepository(openDatabase(), Dispatchers.IO)
        assertThat(ready(second).items).isEmpty()
        assertThat(second.undoLastEdit()).isTrue()
        val restored = ready(second).items.single()
        assertThat(restored.id).isEqualTo(itemId)
        assertThat(restored.placement).isEqualTo(placement)
    }

    @Test
    fun corruptFileIsPreservedNeverSilentlyLost() {
        val garbage = ByteArray(8192) { (it * 31 + 7).toByte() }
        val dbFile = context.getDatabasePath(dbName).apply { parentFile?.mkdirs(); writeBytes(garbage) }

        val repository = RoomWorkspaceRepository(openDatabase(), Dispatchers.IO)
        val state = runBlocking {
            withTimeout(DeviceTestSupport.LOAD_TIMEOUT_MS) { repository.workspace.first { it !is WorkspaceState.Loading } }
        }

        // Invariant: the damaged bytes still exist somewhere, either as a preserved copy or untouched.
        val copy = File(recoveryDir, "corrupt.db")
        val preserved = copy.exists() && copy.readBytes().contentEquals(garbage)
        val untouched = dbFile.exists() && dbFile.readBytes().contentEquals(garbage)
        assertThat(preserved || untouched).isTrue()

        when (state) {
            // Recovered: only acceptable after the copy was preserved, and the new layout is empty.
            is WorkspaceState.Ready -> {
                assertThat(preserved).isTrue()
                assertThat(state.workspace.items).isEmpty()
            }
            // Could not recover: the failure is surfaced, not replaced by an empty layout.
            is WorkspaceState.Failed -> assertThat(state.reason).doesNotContain(dbName)
            WorkspaceState.Loading -> error("unreachable")
        }
    }
}
