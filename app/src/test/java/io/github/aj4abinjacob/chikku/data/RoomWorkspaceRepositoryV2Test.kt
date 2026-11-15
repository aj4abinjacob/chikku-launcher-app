package io.github.aj4abinjacob.chikku.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.CellPlacement
import io.github.aj4abinjacob.chikku.core.model.ComponentKey
import io.github.aj4abinjacob.chikku.core.model.ContainerType
import io.github.aj4abinjacob.chikku.core.model.ContainerType.DESKTOP
import io.github.aj4abinjacob.chikku.core.model.EditResult
import io.github.aj4abinjacob.chikku.core.model.FolderSource
import io.github.aj4abinjacob.chikku.core.model.GestureAction
import io.github.aj4abinjacob.chikku.core.model.GridSpec
import io.github.aj4abinjacob.chikku.core.model.ItemActions
import io.github.aj4abinjacob.chikku.core.model.ItemTarget
import io.github.aj4abinjacob.chikku.core.model.RejectReason
import io.github.aj4abinjacob.chikku.core.model.Workspace
import io.github.aj4abinjacob.chikku.core.model.WorkspaceState
import io.github.aj4abinjacob.chikku.core.model.settings.PaddingSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Folders, widgets, shortcuts, item actions, grids from settings and restore (schema v2). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RoomWorkspaceRepositoryV2Test {
    private lateinit var db: ChikkuDatabase
    private lateinit var repo: RoomWorkspaceRepository
    private val grids = MutableStateFlow(WorkspaceGrids.DEFAULT)
    private var now = 1_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ChikkuDatabase::class.java).allowMainThreadQueries().build()
        repo = RoomWorkspaceRepository(db, Dispatchers.IO, grids, clock = { now++ })
    }

    @After
    fun tearDown() = db.close()

    private fun key(n: Int) = AppKey("com.example.app$n", "com.example.app$n.Main", 0)
    private fun at(x: Int, y: Int, spanX: Int = 1, spanY: Int = 1) = CellPlacement(DESKTOP, 0, x, y, spanX, spanY)
    private fun EditResult.id() = (this as EditResult.Applied).itemId

    private suspend fun ready(): Workspace =
        (repo.workspace.first { it is WorkspaceState.Ready } as WorkspaceState.Ready).workspace

    private suspend fun folder(id: String) = ready().items.single { it.id == id }.target as ItemTarget.Folder

    @Test
    fun automaticInstallPlacementRejectsFolderDuplicatesWithoutBlockingManualCopies() = runTest {
        val a = repo.addApp(key(1), at(2, 2)).id()
        val folderId = repo.createFolder(a, FolderSource.Target(ItemTarget.App(key(2))), "F").id()
        assertThat(repo.addInstalledApp(key(1))).isEqualTo(EditResult.Rejected(RejectReason.DUPLICATE))
        assertThat(ready().items.single().id).isEqualTo(folderId)
        assertThat(repo.addApp(key(1), at(0, 0))).isInstanceOf(EditResult.Applied::class.java)
        assertThat(repo.addInstalledApp(key(1))).isEqualTo(EditResult.Rejected(RejectReason.DUPLICATE))
        assertThat(repo.addInstalledApp(key(3))).isInstanceOf(EditResult.Applied::class.java)
    }

    @Test
    fun createFolderFromTwoItemsKeepsAnchorPlacementAndUndoRestoresBoth() = runTest {
        val a = repo.addApp(key(1), at(2, 2)).id()
        val b = repo.addApp(key(2), at(0, 0)).id()
        val folderId = repo.createFolder(a, FolderSource.Item(b), "Work").id()

        val items = ready().items
        assertThat(items).hasSize(1)
        assertThat(items.single().placement).isEqualTo(at(2, 2))
        assertThat(folder(folderId).title).isEqualTo("Work")
        assertThat(folder(folderId).entries.map { it.target })
            .containsExactly(ItemTarget.App(key(1)), ItemTarget.App(key(2))).inOrder()

        assertThat(repo.undoLastEdit()).isTrue()
        assertThat(ready().items.map { it.id }).containsExactly(a, b)
        assertThat(db.workspaceDao().folderItems()).isEmpty()
    }

    @Test
    fun folderEditsAddRemoveRenameReorder() = runTest {
        val a = repo.addApp(key(1), at(0, 0)).id()
        val folderId = repo.createFolder(a, FolderSource.Target(ItemTarget.App(key(2))), "F").id()
        val c = repo.addApp(key(3), at(1, 0)).id()
        assertThat(repo.addToFolder(folderId, FolderSource.Item(c), rank = 0)).isEqualTo(EditResult.Applied(folderId))
        assertThat(ready().items.map { it.id }).containsExactly(folderId)
        assertThat(folder(folderId).entries.map { it.target })
            .containsExactly(ItemTarget.App(key(3)), ItemTarget.App(key(1)), ItemTarget.App(key(2))).inOrder()

        val ids = folder(folderId).entries.map { it.id }
        assertThat(repo.reorderFolder(folderId, ids.reversed())).isEqualTo(EditResult.Applied(folderId))
        assertThat(folder(folderId).entries.map { it.id }).isEqualTo(ids.reversed())
        assertThat(repo.reorderFolder(folderId, ids.drop(1))).isEqualTo(EditResult.Rejected(RejectReason.INVALID))

        assertThat(repo.renameFolder(folderId, "Renamed")).isEqualTo(EditResult.Applied(folderId))
        assertThat(folder(folderId).title).isEqualTo("Renamed")

        val moved = repo.removeFromFolder(folderId, ids.first(), to = at(4, 4)).id()
        assertThat(ready().items.single { it.id == moved }.target).isEqualTo(ItemTarget.App(key(3)))
        assertThat(folder(folderId).entries).hasSize(2)
        assertThat(repo.removeFromFolder(folderId, ids.last(), to = at(4, 4)))
            .isEqualTo(EditResult.Rejected(RejectReason.OCCUPIED))
        assertThat(repo.removeFromFolder(folderId, ids.last())).isEqualTo(EditResult.Applied(folderId))
        assertThat(folder(folderId).entries).hasSize(1) // single-entry folders are kept, never auto-dissolved
    }

    @Test
    fun removingFolderRemovesEntriesAndUndoBringsThemBack() = runTest {
        val a = repo.addApp(key(1), at(0, 0)).id()
        val folderId = repo.createFolder(a, FolderSource.Target(ItemTarget.App(key(2))), "F").id()
        repo.removeItem(folderId)
        assertThat(ready().items).isEmpty()
        assertThat(db.workspaceDao().folderItems()).isEmpty()
        assertThat(repo.undoLastEdit()).isTrue()
        assertThat(folder(folderId).entries).hasSize(2)
    }

    @Test
    fun foldersRejectWidgetsAndSelfReferences() = runTest {
        val w = repo.addWidget(7, ComponentKey("com.w", "com.w.Provider", 0), at(0, 0, 2, 2)).id()
        val a = repo.addApp(key(1), at(3, 3)).id()
        assertThat(repo.createFolder(w, FolderSource.Item(a), "x")).isEqualTo(EditResult.Rejected(RejectReason.INVALID))
        assertThat(repo.createFolder(a, FolderSource.Item(w), "x")).isEqualTo(EditResult.Rejected(RejectReason.INVALID))
        assertThat(repo.createFolder(a, FolderSource.Item(a), "x")).isEqualTo(EditResult.Rejected(RejectReason.INVALID))
        assertThat(ready().items).hasSize(2)
    }

    @Test
    fun widgetsRespectOccupancyUnlessOverlapIsAllowed() = runTest {
        val provider = ComponentKey("com.w", "com.w.Provider", 0)
        val w = repo.addWidget(7, provider, at(0, 0, 2, 2)).id()
        assertThat(ready().items.single().target).isEqualTo(ItemTarget.Widget(7, provider))
        assertThat(repo.addWidget(8, provider, at(1, 1, 2, 2))).isEqualTo(EditResult.Rejected(RejectReason.OCCUPIED))
        val overlap = repo.addWidget(8, provider, at(1, 1, 2, 2), allowOverlap = true).id()
        assertThat(ready().items.single { it.id == overlap }.zIndex).isGreaterThan(0)

        assertThat(repo.resizeItem(w, 3, 3)).isEqualTo(EditResult.Rejected(RejectReason.OCCUPIED))
        assertThat(repo.resizeItem(w, 6, 1)).isEqualTo(EditResult.Rejected(RejectReason.OUT_OF_BOUNDS))
        assertThat(repo.resizeItem(w, 3, 3, allowOverlap = true)).isEqualTo(EditResult.Applied(w))
        assertThat(ready().items.single { it.id == w }.placement).isEqualTo(at(0, 0, 3, 3))

        assertThat(repo.updateWidgetId(w, 99)).isEqualTo(EditResult.Applied(w))
        assertThat(ready().items.single { it.id == w }.target).isEqualTo(ItemTarget.Widget(99, provider))
    }

    @Test
    fun widgetPaddingOverridesPersistResetAndUndo() = runTest {
        val provider = ComponentKey("com.w", "com.w.Provider", 0)
        val w = repo.addWidget(7, provider, at(0, 0, 2, 2)).id()
        val app = repo.addApp(key(1), at(4, 4)).id()
        assertThat(ready().items.single { it.id == w }.widgetPadding).isNull()

        assertThat(repo.setWidgetPadding(w, PaddingSize.LARGE)).isEqualTo(EditResult.Applied(w))
        assertThat(ready().items.single { it.id == w }.widgetPadding).isEqualTo(PaddingSize.LARGE)
        // Padding is appearance only: identity and placement are untouched.
        assertThat(ready().items.single { it.id == w }.target).isEqualTo(ItemTarget.Widget(7, provider))

        assertThat(repo.setWidgetPadding(w, null)).isEqualTo(EditResult.Applied(w))
        assertThat(ready().items.single { it.id == w }.widgetPadding).isNull()
        assertThat(repo.undoLastEdit()).isTrue()
        assertThat(ready().items.single { it.id == w }.widgetPadding).isEqualTo(PaddingSize.LARGE)

        assertThat(repo.setWidgetPadding(app, PaddingSize.NONE)).isEqualTo(EditResult.Rejected(RejectReason.INVALID))
        assertThat(repo.setWidgetPadding("missing", PaddingSize.NONE)).isEqualTo(EditResult.Rejected(RejectReason.UNKNOWN_ITEM))
    }

    @Test
    fun widgetOriginAndSpanChangeAtomicallyAndUndoRestoresBoth() = runTest {
        val provider = ComponentKey("com.w", "com.w.Provider", 0)
        val original = at(1, 1, 2, 2)
        val id = repo.addWidget(7, provider, original).id()
        val resized = at(0, 0, 3, 3)
        assertThat(repo.moveItem(id, resized)).isEqualTo(EditResult.Applied(id))
        val item = ready().items.single()
        assertThat(item.placement).isEqualTo(resized)
        assertThat(item.target).isEqualTo(ItemTarget.Widget(7, provider))
        assertThat(repo.undoLastEdit()).isTrue()
        assertThat(ready().items.single().placement).isEqualTo(original)
    }

    @Test
    fun rejectedOriginAndSpanChangeLeavesPlacementAndUndoUnchanged() = runTest {
        val provider = ComponentKey("com.w", "com.w.Provider", 0)
        val original = at(1, 1, 2, 2)
        val id = repo.addWidget(7, provider, original).id()
        val blocker = repo.addApp(key(1), at(0, 0)).id()
        assertThat(repo.moveItem(id, at(0, 0, 3, 3))).isEqualTo(EditResult.Rejected(RejectReason.OCCUPIED))
        assertThat(repo.moveItem(id, at(1, 1, 5, 5))).isEqualTo(EditResult.Rejected(RejectReason.OUT_OF_BOUNDS))
        assertThat(ready().items.single { it.id == id }.placement).isEqualTo(original)
        assertThat(repo.undoLastEdit()).isTrue()
        assertThat(ready().items.map { it.id }).doesNotContain(blocker)
        assertThat(ready().items.single().placement).isEqualTo(original)
        assertThat(repo.moveItem(id, at(0, 0, 3, 3), allowOverlap = true)).isEqualTo(EditResult.Applied(id))
    }

    @Test
    fun shortcutsAndItemActionsPersist() = runTest {
        val shortcut = ItemTarget.Shortcut("com.dialer", "call:mum", 0, "Call Mum")
        val id = repo.addShortcut(shortcut, null).id()
        assertThat(repo.addShortcut(shortcut, null)).isEqualTo(EditResult.Rejected(RejectReason.DUPLICATE))
        assertThat(ready().items.single().target).isEqualTo(shortcut)

        val actions = ItemActions(
            swipeUp = GestureAction.OpenSearch,
            swipeDown = GestureAction.LaunchShortcut("com.dialer", "call:mum", 0),
        )
        assertThat(repo.setItemActions(id, actions)).isEqualTo(EditResult.Applied(id))
        assertThat(ready().items.single().actions).isEqualTo(actions)
    }

    @Test
    fun shortcutIconOnHomeAndInFoldersPersistsResetsAndUndoes() = runTest {
        val shortcut = ItemTarget.Shortcut("org.mozilla.firefox", "url:example", 0, "Example")
        val id = repo.addShortcut(shortcut, at(0, 0)).id()
        assertThat(repo.setShortcutIcon(id, null, "com.pack", "ic_web")).isEqualTo(EditResult.Applied(id))
        assertThat(ready().items.single { it.id == id }.target)
            .isEqualTo(shortcut.copy(iconPackPackage = "com.pack", iconDrawableName = "ic_web"))
        assertThat(repo.undoLastEdit()).isTrue()
        assertThat(ready().items.single { it.id == id }.target).isEqualTo(shortcut)

        // In a folder the entry keeps its own id and its icon moves with it.
        val app = repo.addApp(key(1), at(1, 0)).id()
        val folderId = repo.createFolder(app, FolderSource.Item(id), "Web").id()
        val entryId = folder(folderId).entries.single { it.target is ItemTarget.Shortcut }.id
        assertThat(repo.setShortcutIcon(entryId, folderId, "com.pack", "ic_web")).isEqualTo(EditResult.Applied(entryId))
        assertThat(folder(folderId).entries.single { it.id == entryId }.target)
            .isEqualTo(shortcut.copy(iconPackPackage = "com.pack", iconDrawableName = "ic_web"))
        assertThat(repo.setShortcutIcon(entryId, folderId, null, null)).isEqualTo(EditResult.Applied(entryId))
        assertThat(folder(folderId).entries.single { it.id == entryId }.target).isEqualTo(shortcut)
    }

    @Test
    fun shortcutIconRejectsOtherItemsAndHalfChoices() = runTest {
        val app = repo.addApp(key(1), at(0, 0)).id()
        val shortcut = repo.addShortcut(ItemTarget.Shortcut("com.android.chrome", "web", 0, "Web"), at(1, 0)).id()
        assertThat(repo.setShortcutIcon(app, null, "com.pack", "ic")).isEqualTo(EditResult.Rejected(RejectReason.INVALID))
        assertThat(repo.setShortcutIcon(shortcut, null, "com.pack", null)).isEqualTo(EditResult.Rejected(RejectReason.INVALID))
        assertThat(repo.setShortcutIcon("missing", null, null, null)).isEqualTo(EditResult.Rejected(RejectReason.UNKNOWN_ITEM))
        assertThat(repo.setShortcutIcon(shortcut, "missing", null, null)).isEqualTo(EditResult.Rejected(RejectReason.UNKNOWN_ITEM))
        assertThat(repo.setShortcutIcon(shortcut, null, null, null)).isEqualTo(EditResult.Applied(shortcut)) // unchanged
    }

    @Test
    fun gridsFromSettingsBoundEditsWithoutTouchingExistingItems() = runTest {
        val far = repo.addApp(key(1), at(4, 4)).id()
        grids.value = WorkspaceGrids(GridSpec(4, 4), GridSpec(4, 1))
        val shrunk = repo.workspace.first { it is WorkspaceState.Ready && it.workspace.desktopGrid == GridSpec(4, 4) }
        assertThat((shrunk as WorkspaceState.Ready).workspace.items.single().id).isEqualTo(far) // kept, not deleted
        assertThat(repo.addApp(key(2), at(4, 0))).isEqualTo(EditResult.Rejected(RejectReason.OUT_OF_BOUNDS))
        grids.value = WorkspaceGrids(GridSpec(6, 6), GridSpec(6, 1))
        assertThat(repo.addApp(key(3), at(5, 5))).isInstanceOf(EditResult.Applied::class.java)
    }

    @Test
    fun seedDefaultsFillOnlyTheFirstDockPage() = runTest {
        // Two dock pages of 5 are stored side by side in one 10-column row.
        grids.value = WorkspaceGrids(GridSpec(5, 5), GridSpec(10, 1), dockPageColumns = 5)
        assertThat(repo.seedDefaultsOnce((0 until 8).map(::key))).isTrue()
        val dock = ready().items.filter { it.placement.container == ContainerType.DOCK }
        assertThat(dock.map { it.placement.x }.sorted()).containsExactly(0, 1, 2, 3, 4).inOrder()
    }

    @Test
    fun exportAndReplaceAllRoundTripAndUndoRestoresPreviousLayout() = runTest {
        val a = repo.addApp(key(1), at(0, 0)).id()
        val folderId = repo.createFolder(a, FolderSource.Target(ItemTarget.App(key(2))), "F").id()
        repo.addWidget(5, ComponentKey("com.w", "com.w.P", 0), at(1, 0, 2, 1))
        repo.setItemActions(folderId, ItemActions(swipeUp = GestureAction.OpenDrawer))
        val exported = repo.exportContent()!!

        val other = repo.addApp(key(9), at(4, 4)).id()
        assertThat(repo.replaceAll(exported)).isInstanceOf(EditResult.Applied::class.java)
        val restored = ready()
        assertThat(restored.items.toSet()).isEqualTo(exported.items.toSet())
        assertThat(restored.items.none { it.id == other }).isTrue()

        assertThat(repo.undoLastEdit()).isTrue()
        assertThat(ready().items.any { it.id == other }).isTrue()
    }

    @Test
    fun replaceAllRejectsDuplicateIdsAndChangesNothing() = runTest {
        repo.addApp(key(1), at(0, 0))
        val before = ready().items
        val dup = repo.exportContent()!!.let { it.copy(items = it.items + it.items) }
        assertThat(repo.replaceAll(dup)).isEqualTo(EditResult.Rejected(RejectReason.DUPLICATE))
        assertThat(ready().items).isEqualTo(before)
    }

    @Test
    fun unknownKindSurfacesAsFailedAndRowsAreKept() = runTest {
        val bad = newEntity("bad", key(1), at(0, 0), 0).copy(kind = "hologram")
        db.workspaceDao().insertItem(bad)
        assertThat(repo.workspace.first { it !is WorkspaceState.Loading }).isInstanceOf(WorkspaceState.Failed::class.java)
        assertThat(db.workspaceDao().items()).containsExactly(bad)
    }
}
