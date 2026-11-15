package io.github.aj4abinjacob.chikku.data

import android.content.Context
import android.content.ContextWrapper
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith

/** Native SQLite + production factory in isolated storage. No HOME, real inventory or widget IDs. */
@RunWith(AndroidJUnit4::class)
class MigrationRecoveryDeviceTest {
    private class Fixture : AutoCloseable {
        private val instrumentation = InstrumentationRegistry.getInstrumentation()
        private val base = instrumentation.targetContext
        private val name = "migration-recovery-${UUID.randomUUID()}.db"
        private val directory = File(base.cacheDir, name).apply { mkdirs() }
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = directory
        }
        val path: File get() = context.getDatabasePath(name)
        var db: ChikkuDatabase? = null

        fun createOld(version: Int) {
            val schema = instrumentation.context.assets.open("${ChikkuDatabase::class.java.name}/$version.json")
                .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
            val callback = object : SupportSQLiteOpenHelper.Callback(version) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    val entities = schema.getJSONArray("entities")
                    for (index in 0 until entities.length()) {
                        val entity = entities.getJSONObject(index)
                        val table = entity.getString("tableName")
                        db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                        val indices = entity.optJSONArray("indices") ?: continue
                        for (j in 0 until indices.length()) {
                            db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                        }
                    }
                    val setup = schema.getJSONArray("setupQueries")
                    for (index in 0 until setup.length()) db.execSQL(setup.getString(index))
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }
            val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name).callback(callback).build())
            try {
                val old = helper.writableDatabase
                old.execSQL("INSERT INTO workspace_meta(`key`,value) VALUES ('pageCount','2'),('placementUnitsPerCell','2'),('defaultsSeeded','true')")
                old.execSQL("INSERT INTO workspace_items(id,container,page,x2,y2,width2,height2,kind,packageName,className,userSerial,createdAt,updatedAt) " +
                    "VALUES ('app','desktop',1,4,2,2,2,'app','synthetic.app','Main',10,1,1)")
                if (version >= 2) {
                    old.execSQL("INSERT INTO workspace_items(id,container,page,x2,y2,width2,height2,kind,packageName,className,userSerial,createdAt,updatedAt,title) " +
                        "VALUES ('folder','desktop',0,0,0,2,2,'folder','','',0,1,1,'Synthetic folder')")
                    old.execSQL("INSERT INTO folder_items(id,folderId,rank,kind,packageName,className,userSerial,shortcutId,title) " +
                        "VALUES ('member','folder',0,'shortcut','synthetic.browser','',0,'site','Synthetic site')")
                    old.execSQL("INSERT INTO workspace_items(id,container,page,x2,y2,width2,height2,kind,packageName,className,userSerial,createdAt,updatedAt,appWidgetId,providerPackage,providerClass) " +
                        "VALUES ('widget','desktop',0,2,0,4,2,'widget','synthetic.widget','Provider',0,1,1,77,'synthetic.widget','Provider')")
                    old.execSQL("INSERT INTO app_customizations(packageName,className,userSerial,hidden,customLabel) VALUES ('synthetic.app','Main',10,1,'Synthetic alias')")
                    old.execSQL("INSERT INTO drawer_groups(id,name,rank,sortMode) VALUES ('group','Synthetic group',0,'MANUAL')")
                    old.execSQL("INSERT INTO drawer_group_members(groupId,packageName,className,userSerial,rank) VALUES ('group','synthetic.app','Main',10,0)")
                }
                if (version >= 3) old.execSQL("UPDATE folder_items SET shortcutIconPack='synthetic.icons',shortcutIconDrawable='site' WHERE id='member'")
                val saved = SnapshotPayload(2, listOf(newEntity("undo-app", AppKey("synthetic.undo", "Main", 0),
                    CellPlacement(ContainerType.DESKTOP, 1, 2, 1), 1)))
                val json = JSONObject(WorkspaceSnapshotCodec.encode(saved)).put("formatVersion", 1).toString()
                old.execSQL("INSERT INTO workspace_snapshots(createdAt,reason,payload) VALUES (1,'move',?)", arrayOf(json))
            } finally { helper.close() }
        }

        fun open(): ChikkuDatabase {
            db?.close()
            return ChikkuDatabaseFactory.create(context, databaseName = name).also { db = it }
        }

        override fun close() {
            db?.close()
            base.deleteDatabase(name)
            directory.deleteRecursively()
        }
    }

    @Test fun schema1MigratesToCurrentWithoutLosingPlacementsOrUndo(): Unit = verifyMigration(1)
    @Test fun schema2MigratesToCurrentWithoutLosingFoldersWidgetsOrCustomization(): Unit = verifyMigration(2)
    @Test fun schema3MigratesToCurrentWithoutLosingShortcutArtwork(): Unit = verifyMigration(3)

    private fun verifyMigration(version: Int) = runBlocking {
        Fixture().use { fixture ->
            fixture.createOld(version)
            var db = fixture.open()
            var repository = RoomWorkspaceRepository(db, Dispatchers.IO)
            val content = checkNotNull(repository.exportContent())
            assertThat(content.pageCount).isEqualTo(2)
            val app = content.items.single { it.id == "app" }
            assertThat(app.placement).isEqualTo(CellPlacement(ContainerType.DESKTOP, 1, 2, 1))
            assertThat(app.target).isEqualTo(ItemTarget.App(AppKey("synthetic.app", "Main", 10)))
            assertThat(db.workspaceDao().meta("placementUnitsPerCell")).isEqualTo("2")
            if (version >= 2) {
                assertThat((content.items.single { it.id == "widget" }.target as ItemTarget.Widget).appWidgetId).isEqualTo(77)
                val shortcut = (content.items.single { it.id == "folder" }.target as ItemTarget.Folder).entries.single().target as ItemTarget.Shortcut
                assertThat(shortcut.shortcutId).isEqualTo("site")
                assertThat(shortcut.iconPackPackage).isEqualTo(if (version >= 3) "synthetic.icons" else null)
                assertThat(db.customizationDao().all().single().hidden).isTrue()
                val group = RoomDrawerGroupRepository(db, Dispatchers.IO).all()!!.single()
                assertThat(group.id).isEqualTo("group")
                assertThat(group.sortMode).isEqualTo(DrawerSortMode.MANUAL)
                assertThat(group.members).containsExactly(AppKey("synthetic.app", "Main", 10))
                assertThat(content.items.single { it.id == "widget" }.widgetPadding).isNull()
            }
            val snapshot = db.workspaceDao().latestSnapshot()
            db = fixture.open()
            repository = RoomWorkspaceRepository(db, Dispatchers.IO)
            assertThat(repository.exportContent()).isEqualTo(content)
            assertThat(repository.seedDefaultsOnce(listOf(AppKey("synthetic.new", "Main", 0)))).isFalse()
            assertThat(db.workspaceDao().latestSnapshot()).isEqualTo(snapshot)
            assertThat(repository.undoLastEdit()).isTrue()
            assertThat(repository.exportContent()!!.items.single().id).isEqualTo("undo-app")
        }
    }

    @Test fun newerSchemaFailsWithoutResettingItsData(): Unit = runBlocking {
        Fixture().use { fixture ->
            fixture.createOld(3)
            val path = fixture.path
            SQLiteDatabase.openDatabase(path.path, null, SQLiteDatabase.OPEN_READWRITE).use { it.version = 99 }
            val repository = RoomWorkspaceRepository(fixture.open(), Dispatchers.IO)
            assertThat(repository.workspace.first { it !is WorkspaceState.Loading }).isInstanceOf(WorkspaceState.Failed::class.java)
            assertThat(repository.exportContent()).isNull()
            fixture.db?.close()
            SQLiteDatabase.openDatabase(path.path, null, SQLiteDatabase.OPEN_READONLY).use { raw ->
                assertThat(raw.version).isEqualTo(99)
                raw.rawQuery("SELECT id FROM workspace_items ORDER BY id", null).use { cursor ->
                    val ids = mutableListOf<String>()
                    while (cursor.moveToNext()) ids += cursor.getString(0)
                    assertThat(ids).containsExactly("app", "folder", "widget")
                }
            }
        }
    }

    @Test fun damagedUndoRetainsBothCurrentRowsAndTheRecoveryRecord(): Unit = runBlocking {
        Fixture().use { fixture ->
            val db = fixture.open()
            val repository = RoomWorkspaceRepository(db, Dispatchers.IO)
            repository.addApp(AppKey("synthetic.app", "Main", 0), null)
            val before = repository.exportContent()
            val row = db.workspaceDao().items().single().copy(kind = "unknown")
            val id = db.workspaceDao().insertSnapshot(WorkspaceSnapshotEntity(createdAt = 1, reason = "move",
                payloadJson = WorkspaceSnapshotCodec.encode(SnapshotPayload(1, listOf(row)))))
            val snapshot = db.workspaceDao().latestSnapshot()
            assertThat(repository.undoLastEdit()).isFalse()
            assertThat(repository.exportContent()).isEqualTo(before)
            assertThat(db.workspaceDao().latestSnapshot()).isEqualTo(snapshot)
            assertThat(repository.retainedWidgetIds()).isNull()
            db.workspaceDao().deleteSnapshot(id)
            assertThat(repository.retainedWidgetIds()).isEmpty()
        }
    }
}
