package io.github.aj4abinjacob.chikku.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import java.io.File
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Guards the exported schema. Unit tests run with the module directory as working directory.
 * MigrationTestHelper is not used here because it reads schemas from instrumentation assets,
 * which the unit-test classpath does not include.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ChikkuDatabaseSchemaTest {
    private val schemaDir = File("schemas/${ChikkuDatabase::class.java.name}")
    private val schemaFile = File(schemaDir, "4.json")

    @Test
    fun everyPastSchemaIsKeptForMigrationTests() {
        assertThat(File(schemaDir, "1.json").exists()).isTrue()
        assertThat(File(schemaDir, "2.json").exists()).isTrue()
        assertThat(File(schemaDir, "3.json").exists()).isTrue()
    }

    @Test
    fun exportedSchemaMatchesCompiledDatabase() {
        assertThat(schemaFile.exists()).isTrue()
        val database = JSONObject(schemaFile.readText()).getJSONObject("database")
        assertThat(database.getInt("version")).isEqualTo(4)
        val entities = database.getJSONArray("entities")
        val tables = (0 until entities.length()).map { entities.getJSONObject(it).getString("tableName") }
        assertThat(tables).containsExactly(
            "workspace_items", "workspace_meta", "workspace_snapshots",
            "folder_items", "app_customizations", "drawer_groups", "drawer_group_members",
        )

        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, ChikkuDatabase::class.java).allowMainThreadQueries().build()
        try {
            val hash = db.openHelper.writableDatabase.query("SELECT identity_hash FROM room_master_table").use {
                it.moveToFirst()
                it.getString(0)
            }
            assertThat(hash).isEqualTo(database.getString("identityHash"))
        } finally {
            db.close()
        }
    }
}
