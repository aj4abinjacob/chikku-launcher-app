package io.github.aj4abinjacob.chikku.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.CellPlacement
import io.github.aj4abinjacob.chikku.core.model.ContainerType
import io.github.aj4abinjacob.chikku.core.model.ItemActions
import io.github.aj4abinjacob.chikku.core.model.ItemTarget
import io.github.aj4abinjacob.chikku.core.model.WorkspaceState
import io.github.aj4abinjacob.chikku.core.model.settings.PaddingSize
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Fixture-based migration test. The v1 database is built from the exported 1.json (exactly what a
 * v1 install has on disk, including Room's identity hash), filled with rows, then opened through the
 * production factory so the real migration list runs and Room validates the result against the current schema.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ChikkuMigrationTest {
    private lateinit var context: Context
    private var db: ChikkuDatabase? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(ChikkuDatabase.NAME)
    }

    @After
    fun tearDown() {
        db?.close()
        context.deleteDatabase(ChikkuDatabase.NAME)
    }

    private fun createV1Database(version: Int = 1, fill: (SupportSQLiteDatabase) -> Unit) {
        val schema = JSONObject(File("schemas/${ChikkuDatabase::class.java.name}/$version.json").readText())
            .getJSONObject("database")
        val callback = object : SupportSQLiteOpenHelper.Callback(version) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                val entities = schema.getJSONArray("entities")
                for (i in 0 until entities.length()) {
                    val entity = entities.getJSONObject(i)
                    val table = entity.getString("tableName")
                    db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                    val indices = entity.optJSONArray("indices") ?: continue
                    for (j in 0 until indices.length()) {
                        db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                    }
                }
                val setup = schema.getJSONArray("setupQueries")
                for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
                db.execSQL("INSERT INTO workspace_meta(`key`, value) VALUES ('placementUnitsPerCell', '2')")
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(ChikkuDatabase.NAME).callback(callback).build(),
        )
        helper.writableDatabase.use(fill)
        helper.close()
    }

    private fun SupportSQLiteDatabase.insertV1Item(id: String, container: String, x2: Int, pkg: String) {
        insert(
            "workspace_items", SQLiteDatabase.CONFLICT_ABORT,
            ContentValues().apply {
                put("id", id); put("container", container); put("page", 0); put("x2", x2); put("y2", 0)
                put("width2", 2); put("height2", 2); put("kind", "app"); put("packageName", pkg)
                put("className", "$pkg.Main"); put("userSerial", 0L); put("createdAt", 1L); put("updatedAt", 1L)
            },
        )
    }

    @Test
    fun migration1To2KeepsEveryRowAndAddsEmptyTables() = runTest {
        // A v1 undo snapshot written by the v1 codec (formatVersion 1, app rows only).
        val v1Snapshot = """{"formatVersion":1,"pageCount":2,"items":[{"id":"old","container":"desktop","page":1,""" +
            """"x2":4,"y2":2,"width2":2,"height2":2,"kind":"app","packageName":"com.old","className":"com.old.Main",""" +
            """"userSerial":0,"createdAt":1,"updatedAt":1}]}"""
        createV1Database { v1 ->
            v1.insertV1Item("a", "desktop", 0, "com.a")
            v1.insertV1Item("b", "dock", 2, "com.b")
            v1.execSQL("INSERT INTO workspace_meta(`key`, value) VALUES ('pageCount', '2')")
            v1.insert(
                "workspace_snapshots", SQLiteDatabase.CONFLICT_ABORT,
                ContentValues().apply { put("createdAt", 5L); put("reason", "remove"); put("payload", v1Snapshot) },
            )
        }

        val migrated = ChikkuDatabaseFactory.create(context).also { db = it }
        val repo = RoomWorkspaceRepository(migrated, Dispatchers.IO)
        val workspace = (repo.workspace.first { it !is WorkspaceState.Loading } as WorkspaceState.Ready).workspace
        assertThat(workspace.pageCount).isEqualTo(2)
        assertThat(workspace.items.map { it.id to it.target }).containsExactly(
            "a" to ItemTarget.App(AppKey("com.a", "com.a.Main", 0)),
            "b" to ItemTarget.App(AppKey("com.b", "com.b.Main", 0)),
        )
        assertThat(workspace.items.single { it.id == "b" }.placement).isEqualTo(CellPlacement(ContainerType.DOCK, 0, 1, 0))
        assertThat(workspace.items.map { it.actions }.toSet()).containsExactly(ItemActions())
        assertThat(workspace.items.map { it.zIndex }.toSet()).containsExactly(0)

        assertThat(migrated.workspaceDao().folderItems()).isEmpty()
        assertThat(migrated.customizationDao().all()).isEmpty()
        assertThat(migrated.drawerGroupDao().groups()).isEmpty()
        assertThat(migrated.workspaceDao().meta("placementUnitsPerCell")).isEqualTo("2")

        // The pre-migration snapshot is still a valid undo target.
        assertThat(repo.undoLastEdit()).isTrue()
        val restored = (repo.workspace.first { it is WorkspaceState.Ready } as WorkspaceState.Ready).workspace
        assertThat(restored.items.map { it.id }).containsExactly("old")
        assertThat(restored.items.single().placement).isEqualTo(CellPlacement(ContainerType.DESKTOP, 1, 2, 1))
    }

    @Test
    fun migratedDatabaseAcceptsNewItemKinds() = runTest {
        createV1Database { v1 -> v1.insertV1Item("a", "desktop", 0, "com.a") }
        val migrated = ChikkuDatabaseFactory.create(context).also { db = it }
        val repo = RoomWorkspaceRepository(migrated, Dispatchers.IO)
        val folder = repo.createFolder(
            "a",
            io.github.aj4abinjacob.chikku.core.model.FolderSource.Target(ItemTarget.App(AppKey("com.b", "com.b.Main", 0))),
            "Tools",
        )
        assertThat(folder).isInstanceOf(io.github.aj4abinjacob.chikku.core.model.EditResult.Applied::class.java)
        val item = (repo.workspace.first { it is WorkspaceState.Ready } as WorkspaceState.Ready).workspace.items.single()
        assertThat((item.target as ItemTarget.Folder).entries.map { it.target })
            .containsExactly(ItemTarget.App(AppKey("com.a", "com.a.Main", 0)), ItemTarget.App(AppKey("com.b", "com.b.Main", 0)))
            .inOrder()
    }
    @Test fun migration2To3KeepsShortcutIdentityAndAppearanceSurvivesStorageAndUndoSnapshots() = runTest {
        createV1Database(2) { old ->
            old.insertV1Item("shortcut", "desktop", 0, "com.example.browser")
            old.execSQL("UPDATE workspace_items SET kind='shortcut', shortcutId='site-id', title='Site' WHERE id='shortcut'")
            old.execSQL("INSERT INTO workspace_meta(`key`, value) VALUES ('pageCount', '1')")
        }
        val migrated = ChikkuDatabaseFactory.create(context).also { db = it }
        val repo = RoomWorkspaceRepository(migrated, Dispatchers.IO)
        val content = checkNotNull(repo.exportContent())
        val original = content.items.single()
        assertThat(original.target).isEqualTo(ItemTarget.Shortcut("com.example.browser", "site-id", 0, "Site"))
        val customized = original.copy(target = (original.target as ItemTarget.Shortcut).copy(
            iconPackPackage = "com.example.icons", iconDrawableName = "site"))
        repo.replaceAll(content.copy(items = listOf(customized)))
        assertThat(repo.exportContent()!!.items.single()).isEqualTo(customized)
        val row = customized.toEntity(1)
        val entry = FolderItemEntity("entry", "folder", 0, "shortcut", "com.example.browser", "", 0,
            "site-id", "Site", "com.example.icons", "site")
        val snapshot = SnapshotPayload(1, listOf(row), listOf(entry))
        assertThat(WorkspaceSnapshotCodec.decode(WorkspaceSnapshotCodec.encode(snapshot))).isEqualTo(snapshot)
    }

    @Test fun migration3To4KeepsWidgetsOnTheGlobalPaddingAndAcceptsOverrides() = runTest {
        createV1Database(3) { old ->
            old.insertV1Item("widget", "desktop", 0, "com.example.clock")
            old.execSQL("UPDATE workspace_items SET kind='widget', appWidgetId=42, providerPackage='com.example.clock', " +
                "providerClass='com.example.clock.Provider' WHERE id='widget'")
            old.execSQL("INSERT INTO workspace_meta(`key`, value) VALUES ('pageCount', '1')")
        }
        val migrated = ChikkuDatabaseFactory.create(context).also { db = it }
        val repo = RoomWorkspaceRepository(migrated, Dispatchers.IO)
        val widget = checkNotNull(repo.exportContent()).items.single()
        assertThat((widget.target as ItemTarget.Widget).appWidgetId).isEqualTo(42)
        assertThat(widget.widgetPadding).isNull()

        assertThat(repo.setWidgetPadding("widget", PaddingSize.NONE))
            .isEqualTo(io.github.aj4abinjacob.chikku.core.model.EditResult.Applied("widget"))
        assertThat(repo.exportContent()!!.items.single().widgetPadding).isEqualTo(PaddingSize.NONE)
        val snapshot = SnapshotPayload(1, listOf(repo.exportContent()!!.items.single().toEntity(1)))
        assertThat(WorkspaceSnapshotCodec.decode(WorkspaceSnapshotCodec.encode(snapshot))).isEqualTo(snapshot)
    }

}
