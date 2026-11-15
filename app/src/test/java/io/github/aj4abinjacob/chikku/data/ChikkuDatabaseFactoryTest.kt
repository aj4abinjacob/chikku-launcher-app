package io.github.aj4abinjacob.chikku.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.WorkspaceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ChikkuDatabaseFactoryTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private class RecordingCallback : SupportSQLiteOpenHelper.Callback(1) {
        var corruptionHandled = false
        override fun onCreate(db: SupportSQLiteDatabase) = db.execSQL("CREATE TABLE t(v TEXT)")
        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        override fun onCorruption(db: SupportSQLiteDatabase) {
            corruptionHandled = true
        }
    }

    private fun openWrapped(
        callback: RecordingCallback,
        preserve: (String) -> Unit,
    ): Pair<SupportSQLiteOpenHelper, SupportSQLiteOpenHelper.Configuration> {
        lateinit var captured: SupportSQLiteOpenHelper.Configuration
        val capturing = SupportSQLiteOpenHelper.Factory { config ->
            captured = config
            FrameworkSQLiteOpenHelperFactory().create(config)
        }
        val helper = CorruptionPreservingOpenHelperFactory(capturing, preserve).create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name("wrapped.db").callback(callback).build(),
        )
        return helper to captured
    }

    @Test
    fun corruptionIsCopiedBeforeTheOriginalHandlerRuns() {
        val callback = RecordingCallback()
        var copiedBeforeHandler = false
        val (helper, config) = openWrapped(callback) { path ->
            ChikkuDatabaseFactory.preserveCorruptDatabase(context, path, timestamp = 42)
            copiedBeforeHandler = !callback.corruptionHandled
        }
        val db = helper.writableDatabase
        config.callback.onCorruption(db)

        assertThat(copiedBeforeHandler).isTrue()
        assertThat(callback.corruptionHandled).isTrue()
        assertThat(config.allowDataLossOnRecovery).isFalse()
        assertThat(ChikkuDatabaseFactory.hasPreservedCorruptCopy(context)).isTrue()
        val copy = ChikkuDatabaseFactory.corruptCopies(context).single()
        assertThat(copy.name).isEqualTo("workspace-corrupt-42.db")
        assertThat(copy.length()).isGreaterThan(0L)
        helper.close()
    }

    @Test
    fun failedCopySkipsTheDeletingHandler() {
        val callback = RecordingCallback()
        val (helper, config) = openWrapped(callback) { error("disk full") }
        config.callback.onCorruption(helper.writableDatabase)

        assertThat(callback.corruptionHandled).isFalse()
        assertThat(context.getDatabasePath("wrapped.db").exists()).isTrue()
        assertThat(ChikkuDatabaseFactory.hasPreservedCorruptCopy(context)).isFalse()
        helper.close()
    }

    @Test
    fun acknowledgeClearsNoticeButKeepsCopies() {
        val source = context.getDatabasePath("x.db").apply { parentFile!!.mkdirs(); writeText("data") }
        context.getDatabasePath("x.db-wal").writeText("wal")
        ChikkuDatabaseFactory.preserveCorruptDatabase(context, source.path, timestamp = 7)

        val copy = ChikkuDatabaseFactory.corruptCopies(context).single()
        assertThat(copy.readText()).isEqualTo("data")
        assertThat(java.io.File(copy.path + "-wal").readText()).isEqualTo("wal")
        ChikkuDatabaseFactory.acknowledgeCorruptCopies(context)
        assertThat(ChikkuDatabaseFactory.hasPreservedCorruptCopy(context)).isFalse()
        assertThat(ChikkuDatabaseFactory.corruptCopies(context)).containsExactly(copy)
    }

    @Test
    fun corruptDatabaseFileIsPreservedThenRecreated() = runTest {
        val file = context.getDatabasePath(ChikkuDatabase.NAME).apply { parentFile!!.mkdirs() }
        val garbage = ByteArray(8192) { (it % 251).toByte() }
        file.writeBytes(garbage)

        val db = ChikkuDatabaseFactory.create(context)
        try {
            val state = RoomWorkspaceRepository(db, Dispatchers.IO).workspace.first { it !is WorkspaceState.Loading }
            assertThat(state).isInstanceOf(WorkspaceState.Ready::class.java)
            assertThat(ChikkuDatabaseFactory.hasPreservedCorruptCopy(context)).isTrue()
            assertThat(ChikkuDatabaseFactory.corruptCopies(context).single().readBytes()).isEqualTo(garbage)
            assertThat(db.workspaceDao().meta("placementUnitsPerCell")).isEqualTo("2")
        } finally {
            db.close()
        }
    }
}
