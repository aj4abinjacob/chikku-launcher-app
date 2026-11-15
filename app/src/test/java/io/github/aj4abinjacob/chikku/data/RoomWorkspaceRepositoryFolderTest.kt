package io.github.aj4abinjacob.chikku.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.CellPlacement
import io.github.aj4abinjacob.chikku.core.model.ContainerType.DESKTOP
import io.github.aj4abinjacob.chikku.core.model.ContainerType.DOCK
import io.github.aj4abinjacob.chikku.core.model.EditResult
import io.github.aj4abinjacob.chikku.core.model.FolderSource
import io.github.aj4abinjacob.chikku.core.model.GestureAction
import io.github.aj4abinjacob.chikku.core.model.ItemActions
import io.github.aj4abinjacob.chikku.core.model.ItemTarget
import io.github.aj4abinjacob.chikku.core.model.RejectReason
import io.github.aj4abinjacob.chikku.core.model.Workspace
import io.github.aj4abinjacob.chikku.core.model.WorkspaceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Folder edits on a file-backed database: undo of every folder edit, duplicates, dock folders, process death. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RoomWorkspaceRepositoryFolderTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: ChikkuDatabase
    private lateinit var repo: RoomWorkspaceRepository

    @Before
    fun setUp() {
        context.deleteDatabase(DB_NAME)
        open()
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(DB_NAME)
    }

    /** A fresh database object and repository over the same file, as after process death. */
    private fun open() {
        db = Room.databaseBuilder(context, ChikkuDatabase::class.java, DB_NAME).allowMainThreadQueries().build()
        repo = RoomWorkspaceRepository(db, Dispatchers.IO)
    }

    private fun key(n: Int) = AppKey("com.example.app$n", "com.example.app$n.Main", 0)
    private fun at(x: Int, y: Int) = CellPlacement(DESKTOP, 0, x, y)
    private fun dock(x: Int) = CellPlacement(DOCK, 0, x, 0)
    private fun EditResult.id() = (this as EditResult.Applied).itemId

    private suspend fun ready(): Workspace =
        (repo.workspace.first { it is WorkspaceState.Ready } as WorkspaceState.Ready).workspace

    private suspend fun folder(id: String) = ready().items.single { it.id == id }.target as ItemTarget.Folder

    private suspend fun twoAppFolder(): String {
        val a = repo.addApp(key(1), at(1, 1)).id()
        val b = repo.addApp(key(2), at(2, 2)).id()
        return repo.createFolder(a, FolderSource.Item(b), "Nav").id()
    }

    @Test
    fun folderSurvivesProcessDeathWithOrderTitleAndActions() = runTest {
        val folderId = twoAppFolder()
        repo.addToFolder(folderId, FolderSource.Target(ItemTarget.App(key(3))))
        val ids = folder(folderId).entries.map { it.id }
        repo.reorderFolder(folderId, listOf(ids[2], ids[0], ids[1]))
        repo.renameFolder(folderId, "Navigation")
        val actions = ItemActions(tap = GestureAction.LaunchApp(key(3)), swipeDown = GestureAction.LaunchApp(key(1)))
        repo.setItemActions(folderId, actions)

        db.close()
        open()

        val item = ready().items.single()
        assertThat(item.id).isEqualTo(folderId)
        assertThat(item.placement).isEqualTo(at(1, 1))
        assertThat(item.actions).isEqualTo(actions)
        val restored = item.target as ItemTarget.Folder
        assertThat(restored.title).isEqualTo("Navigation")
        assertThat(restored.entries.map { it.target })
            .containsExactly(ItemTarget.App(key(3)), ItemTarget.App(key(1)), ItemTarget.App(key(2))).inOrder()
        // Undo history survives too: the last edit (actions) is reverted.
        assertThat(repo.undoLastEdit()).isTrue()
        assertThat(ready().items.single().actions).isEqualTo(ItemActions())
    }

    @Test
    fun everyFolderEditIsUndoable() = runTest {
        val folderId = twoAppFolder()
        val created = ready()

        repo.addToFolder(folderId, FolderSource.Target(ItemTarget.App(key(3))), rank = 0)
        assertThat(repo.undoLastEdit()).isTrue()
        assertThat(ready()).isEqualTo(created)

        val ids = folder(folderId).entries.map { it.id }
        repo.reorderFolder(folderId, ids.reversed())
        assertThat(repo.undoLastEdit()).isTrue()
        assertThat(folder(folderId).entries.map { it.id }).isEqualTo(ids)

        repo.renameFolder(folderId, "Other")
        assertThat(repo.undoLastEdit()).isTrue()
        assertThat(folder(folderId).title).isEqualTo("Nav")

        repo.removeFromFolder(folderId, ids[0], to = at(4, 4))
        assertThat(ready().items).hasSize(2)
        assertThat(repo.undoLastEdit()).isTrue()
        assertThat(ready()).isEqualTo(created)

        repo.removeFromFolder(folderId, ids[1])
        assertThat(repo.undoLastEdit()).isTrue()
        assertThat(ready()).isEqualTo(created)
    }

    @Test
    fun removingTapShortcutFallsBackToFirstStoredMemberAndUndoSurvivesReopen() = runTest {
        val folderId = twoAppFolder()
        val shortcut = ItemTarget.Shortcut("com.example.links", "route:home", 0)
        repo.addToFolder(folderId, FolderSource.Target(shortcut), rank = 0)
        val entries = folder(folderId).entries
        repo.reorderFolder(folderId, listOf(entries[0].id, entries[2].id, entries[1].id))
        val actions = ItemActions(
            tap = GestureAction.LaunchShortcut(shortcut.packageName, shortcut.shortcutId, shortcut.userSerial),
            swipeUp = GestureAction.OpenFolder,
            swipeDown = GestureAction.OpenSearch,
        )
        repo.setItemActions(folderId, actions)
        val before = ready()

        assertThat(repo.removeFromFolder(folderId, entries[0].id)).isEqualTo(EditResult.Applied(folderId))
        db.close()
        open()

        val item = ready().items.single()
        assertThat(item.actions).isEqualTo(actions.copy(tap = GestureAction.LaunchApp(key(2))))
        assertThat((item.target as ItemTarget.Folder).entries.map { it.target })
            .containsExactly(ItemTarget.App(key(2)), ItemTarget.App(key(1))).inOrder()
        assertThat(repo.undoLastEdit()).isTrue()
        assertThat(ready()).isEqualTo(before)
    }

    @Test
    fun movingTapAppOutOfDockFolderFallsBackToRemainingShortcut() = runTest {
        val appId = repo.addApp(key(1), dock(2)).id()
        val shortcut = ItemTarget.Shortcut("com.example.links", "route:home", 10)
        val folderId = repo.createFolder(appId, FolderSource.Target(shortcut), "Links").id()
        repo.setItemActions(folderId, ItemActions(tap = GestureAction.LaunchApp(key(1))))
        val before = ready()
        val entryId = folder(folderId).entries.first().id

        val movedId = repo.removeFromFolder(folderId, entryId, at(4, 4)).id()

        assertThat(ready().items.single { it.id == folderId }.actions.tap)
            .isEqualTo(GestureAction.LaunchShortcut(shortcut.packageName, shortcut.shortcutId, shortcut.userSerial))
        assertThat(ready().items.single { it.id == movedId }.target).isEqualTo(ItemTarget.App(key(1)))
        assertThat(repo.undoLastEdit()).isTrue()
        assertThat(ready()).isEqualTo(before)
    }

    @Test
    fun removingLastTapMemberReturnsToOpeningEmptyFolder() = runTest {
        val folderId = twoAppFolder()
        repo.setItemActions(folderId, ItemActions(tap = GestureAction.LaunchApp(key(1))))
        val entries = folder(folderId).entries
        repo.removeFromFolder(folderId, entries[1].id)
        assertThat(ready().items.single().actions.tap).isEqualTo(GestureAction.LaunchApp(key(1)))
        repo.removeFromFolder(folderId, entries[0].id)

        assertThat(folder(folderId).entries).isEmpty()
        assertThat(ready().items.single().actions.tap).isNull()
        assertThat(repo.undoLastEdit()).isTrue()
        assertThat(ready().items.single().actions.tap).isEqualTo(GestureAction.LaunchApp(key(1)))
    }

    @Test
    fun removalPreservesUnrelatedActionsAndFullAppIdentity() = runTest {
        val folderId = twoAppFolder()
        val removed = folder(folderId).entries.first()
        val taps = listOf(
            null, GestureAction.None, GestureAction.OpenFolder, GestureAction.OpenSearch,
            GestureAction.LaunchApp(key(2)),
            GestureAction.LaunchApp(key(9)),
            GestureAction.LaunchApp(key(1).copy(className = "com.example.app1.Other")),
            GestureAction.LaunchApp(key(1).copy(userSerial = 10)),
            GestureAction.LaunchShortcut(key(1).packageName, "route:home", 0),
        )
        for (tap in taps) {
            val actions = ItemActions(tap = tap, swipeUp = GestureAction.LaunchApp(key(1)))
            repo.setItemActions(folderId, actions)
            repo.removeFromFolder(folderId, removed.id)
            assertThat(ready().items.single().actions).isEqualTo(actions)
            assertThat(repo.undoLastEdit()).isTrue()
        }
    }

    @Test
    fun removalMatchesExactShortcutIdAndProfile() = runTest {
        val folderId = twoAppFolder()
        val shortcut = ItemTarget.Shortcut("com.example.links", "route:home", 0)
        repo.addToFolder(folderId, FolderSource.Target(shortcut))
        val entryId = folder(folderId).entries.last().id
        for (tap in listOf(
            GestureAction.LaunchShortcut(shortcut.packageName, "route:work", 0),
            GestureAction.LaunchShortcut(shortcut.packageName, shortcut.shortcutId, 10),
        )) {
            repo.setItemActions(folderId, ItemActions(tap = tap))
            repo.removeFromFolder(folderId, entryId)
            assertThat(ready().items.single().actions.tap).isEqualTo(tap)
            assertThat(repo.undoLastEdit()).isTrue()
        }
    }

    @Test
    fun rejectedMoveKeepsMemberTapAndPreviousUndo() = runTest {
        val folderId = twoAppFolder()
        val beforeActions = ready()
        repo.setItemActions(folderId, ItemActions(tap = GestureAction.LaunchApp(key(1))))
        val before = ready()
        val entryId = folder(folderId).entries.first().id

        assertThat(repo.removeFromFolder(folderId, entryId, at(1, 1)))
            .isEqualTo(EditResult.Rejected(RejectReason.OCCUPIED))
        assertThat(ready()).isEqualTo(before)
        assertThat(repo.undoLastEdit()).isTrue()
        assertThat(ready()).isEqualTo(beforeActions)
    }

    @Test
    fun duplicateAppsInOneFolderAreRejectedWithoutChanges() = runTest {
        val a = repo.addApp(key(1), at(0, 0)).id()
        assertThat(repo.createFolder(a, FolderSource.Target(ItemTarget.App(key(1))), ""))
            .isEqualTo(EditResult.Rejected(RejectReason.DUPLICATE))
        assertThat(ready().items.single().target).isEqualTo(ItemTarget.App(key(1)))

        val folderId = repo.createFolder(a, FolderSource.Target(ItemTarget.App(key(2))), "").id()
        val before = ready()
        assertThat(repo.addToFolder(folderId, FolderSource.Target(ItemTarget.App(key(2)))))
            .isEqualTo(EditResult.Rejected(RejectReason.DUPLICATE))
        assertThat(ready()).isEqualTo(before)
        // The same component in another profile is a different app.
        assertThat(repo.addToFolder(folderId, FolderSource.Target(ItemTarget.App(key(2).copy(userSerial = 10)))))
            .isEqualTo(EditResult.Applied(folderId))
        repo.undoLastEdit()
        assertThat(ready()).isEqualTo(before)
    }

    @Test
    fun folderInDockKeepsDockPlacementAndAcceptsMoreApps() = runTest {
        val a = repo.addApp(key(1), dock(2)).id()
        val b = repo.addApp(key(2), at(0, 0)).id()
        val folderId = repo.createFolder(a, FolderSource.Item(b), "").id()
        assertThat(ready().items.single().placement).isEqualTo(dock(2))
        val c = repo.addApp(key(3), dock(0)).id()
        assertThat(repo.addToFolder(folderId, FolderSource.Item(c))).isEqualTo(EditResult.Applied(folderId))
        assertThat(ready().items.map { it.id }).containsExactly(folderId)
        assertThat(folder(folderId).entries).hasSize(3)
        // An entry dragged out to a free dock cell becomes a dock item.
        val entry = folder(folderId).entries.last().id
        val out = repo.removeFromFolder(folderId, entry, to = dock(4)).id()
        assertThat(ready().items.single { it.id == out }.placement).isEqualTo(dock(4))
    }

    @Test
    fun foldersCannotBeNestedOrAddedToThemselves() = runTest {
        val folderId = twoAppFolder()
        val other = repo.addApp(key(5), at(3, 3)).id()
        val second = repo.createFolder(other, FolderSource.Target(ItemTarget.App(key(6))), "").id()
        assertThat(repo.addToFolder(folderId, FolderSource.Item(second)))
            .isEqualTo(EditResult.Rejected(RejectReason.INVALID))
        assertThat(repo.addToFolder(folderId, FolderSource.Item(folderId)))
            .isEqualTo(EditResult.Rejected(RejectReason.INVALID))
        assertThat(repo.createFolder(folderId, FolderSource.Target(ItemTarget.App(key(7))), ""))
            .isEqualTo(EditResult.Rejected(RejectReason.INVALID))
    }

    private companion object {
        const val DB_NAME = "folder-test.db"
    }
}
