package io.github.aj4abinjacob.chikku.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UndoSnapshotRecoveryTest {
    @Test fun malformedFolderArrayCannotBeMistakenForAnEmptyLegacySnapshot() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, ChikkuDatabase::class.java).build()
        try {
            val repository = RoomWorkspaceRepository(db, Dispatchers.IO)
            repository.addApp(AppKey("synthetic.app", "Main", 0), null)
            val before = repository.exportContent()
            val valid = WorkspaceSnapshotCodec.encode(SnapshotPayload(1, db.workspaceDao().items()))
            for (value in listOf<Any>("broken", JSONObject.NULL, JSONObject())) {
                val json = JSONObject(valid).put("folderItems", value).toString()
                val id = db.workspaceDao().insertSnapshot(WorkspaceSnapshotEntity(createdAt = 1, reason = "move", payloadJson = json))
                assertThat(repository.undoLastEdit()).isFalse()
                assertThat(repository.exportContent()).isEqualTo(before)
                assertThat(db.workspaceDao().latestSnapshot()!!.payloadJson).isEqualTo(json)
                assertThat(repository.retainedWidgetIds()).isNull()
                db.workspaceDao().deleteSnapshot(id)
            }
            val legacy = JSONObject(valid).put("formatVersion", 1).apply { remove("folderItems") }.toString()
            db.workspaceDao().insertSnapshot(WorkspaceSnapshotEntity(createdAt = 1, reason = "move", payloadJson = legacy))
            assertThat(repository.undoLastEdit()).isTrue()
            assertThat(repository.exportContent()).isEqualTo(before)
        } finally { db.close() }
    }

    @Test fun structurallyValidButDamagedUndoNeverReplacesTheLiveWorkspace() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, ChikkuDatabase::class.java).build()
        try {
            val repository = RoomWorkspaceRepository(db, Dispatchers.IO)
            repository.addApp(AppKey("synthetic.app", "Main", 0), null)
            val before = repository.exportContent()
            val row = db.workspaceDao().items().single()
            val folder = row.copy(id = "folder", kind = "folder", title = "Synthetic folder")
            val member = FolderItemEntity("member", "folder", 0, "app", "synthetic.member", "Main", 0)
            val valid = SnapshotPayload(1, listOf(folder), listOf(member))
            val damaged = listOf(
                valid.copy(pageCount = 0),
                valid.copy(items = listOf(folder.copy(kind = "unknown"))),
                valid.copy(items = listOf(folder.copy(x2 = -1))),
                valid.copy(items = listOf(folder.copy(width2 = 0))),
                valid.copy(items = listOf(folder.copy(page = 1))),
                valid.copy(items = listOf(folder.copy(container = "dock", page = 1))),
                valid.copy(items = listOf(folder.copy(kind = "shortcut", shortcutId = null))),
                valid.copy(items = listOf(folder.copy(tapAction = "unknown"))),
                valid.copy(folderItems = listOf(member.copy(folderId = "missing"))),
                valid.copy(folderItems = listOf(member.copy(rank = -1))),
                valid.copy(folderItems = listOf(member.copy(kind = "widget"))),
            )
            for (payload in damaged) {
                val id = db.workspaceDao().insertSnapshot(WorkspaceSnapshotEntity(
                    createdAt = 1, reason = "move", payloadJson = WorkspaceSnapshotCodec.encode(payload),
                ))
                val snapshot = db.workspaceDao().latestSnapshot()
                assertThat(repository.undoLastEdit()).isFalse()
                assertThat(repository.exportContent()).isEqualTo(before)
                assertThat(db.workspaceDao().latestSnapshot()).isEqualTo(snapshot)
                assertThat(repository.retainedWidgetIds()).isNull()
                db.workspaceDao().deleteSnapshot(id)
            }
        } finally { db.close() }
    }

    @Test fun undoPreservesOutOfViewportItemsAndHistoricalOverlap() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, ChikkuDatabase::class.java).build()
        try {
            val repository = RoomWorkspaceRepository(db, Dispatchers.IO)
            repository.addApp(AppKey("synthetic.app", "Main", 0), null)
            val row = db.workspaceDao().items().single()
            val outside = row.copy(x2 = 20, width2 = 8)
            val overlapping = outside.copy(id = "other")
            val payload = SnapshotPayload(1, listOf(outside, overlapping))
            db.workspaceDao().insertSnapshot(WorkspaceSnapshotEntity(
                createdAt = 1, reason = "resize", payloadJson = WorkspaceSnapshotCodec.encode(payload),
            ))
            assertThat(repository.undoLastEdit()).isTrue()
            assertThat(db.workspaceDao().items()).containsExactly(outside, overlapping)
        } finally { db.close() }
    }
}
