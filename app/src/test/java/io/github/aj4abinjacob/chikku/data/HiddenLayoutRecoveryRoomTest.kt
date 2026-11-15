package io.github.aj4abinjacob.chikku.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class HiddenLayoutRecoveryRoomTest {
    private fun original() = WorkspaceContent(1, listOf(
        WorkspaceItem("visible", CellPlacement(ContainerType.DESKTOP, 0, 0, 0), ItemTarget.App(AppKey("visible", "Main", 0))),
        WorkspaceItem("folder", CellPlacement(ContainerType.DESKTOP, 0, 5, 0), ItemTarget.Folder("Synthetic", listOf(FolderEntry("member", ItemTarget.App(AppKey("member", "Main", 0)))))),
        WorkspaceItem("widget", CellPlacement(ContainerType.DOCK, 0, 7, 0), ItemTarget.Widget(77, ComponentKey("synthetic", "Widget", 0))),
    ))
    private suspend fun fixture(block: suspend (ChikkuDatabase, RoomWorkspaceRepository) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, ChikkuDatabase::class.java).build()
        try {
            val repo = RoomWorkspaceRepository(db, Dispatchers.IO, flowOf(WorkspaceGrids(GridSpec(2, 2), GridSpec(2, 1))))
            repo.replaceAll(original())
            block(db, repo)
        } finally { db.close() }
    }
    @Test fun oneUndoRestoresEveryRelocatedItemAndFolderMember() = runTest {
        fixture { _, repo ->
            assertThat(repo.relocateHiddenItems()!!.moved).isEqualTo(2)
            val moved = repo.exportContent()!!
            assertThat(moved.items.map { it.target }).containsExactlyElementsIn(original().items.map { it.target })
            assertThat(repo.undoHiddenRelocation()).isTrue()
            assertThat(repo.exportContent()).isEqualTo(original())
            assertThat(repo.undoHiddenRelocation()).isFalse()
        }
    }
    @Test fun lateStorageFailureRollsBackAllMovementAndUndoSnapshot() = runTest {
        fixture { db, repo ->
            val snapshot = db.workspaceDao().latestSnapshot()
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_hidden_move BEFORE UPDATE ON workspace_items WHEN NEW.id = 'widget' BEGIN SELECT RAISE(ABORT, 'injected'); END")
            assertThat(repo.relocateHiddenItems()).isNull()
            assertThat(repo.exportContent()).isEqualTo(original())
            assertThat(db.workspaceDao().latestSnapshot()).isEqualTo(snapshot)
        }
    }
    @Test fun recoveryUndoCannotConsumeAnUnrelatedLaterEdit() = runTest {
        fixture { _, repo ->
            repo.relocateHiddenItems()
            repo.renameFolder("folder", "Later edit")
            val saved = repo.exportContent()
            assertThat(repo.undoHiddenRelocation()).isFalse()
            assertThat(repo.exportContent()).isEqualTo(saved)
            assertThat(repo.undoLastEdit()).isTrue()
        }
    }
}
