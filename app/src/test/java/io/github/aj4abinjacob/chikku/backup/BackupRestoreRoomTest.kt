package io.github.aj4abinjacob.chikku.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.AppCustomization
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.CellPlacement
import io.github.aj4abinjacob.chikku.core.model.ComponentKey
import io.github.aj4abinjacob.chikku.core.model.ContainerType
import io.github.aj4abinjacob.chikku.core.model.EditResult
import io.github.aj4abinjacob.chikku.core.model.FolderSource
import io.github.aj4abinjacob.chikku.core.model.GestureAction
import io.github.aj4abinjacob.chikku.core.model.ItemActions
import io.github.aj4abinjacob.chikku.core.model.ItemTarget
import io.github.aj4abinjacob.chikku.core.model.Workspace
import io.github.aj4abinjacob.chikku.core.model.WorkspaceState
import io.github.aj4abinjacob.chikku.core.model.backup.BackupCodec
import io.github.aj4abinjacob.chikku.core.model.backup.BackupCreateResult
import io.github.aj4abinjacob.chikku.core.model.backup.BackupDecodeResult
import io.github.aj4abinjacob.chikku.core.model.backup.BackupOperationResult
import io.github.aj4abinjacob.chikku.core.model.backup.BackupService
import io.github.aj4abinjacob.chikku.core.model.backup.MemoryRestoreJournal
import io.github.aj4abinjacob.chikku.core.model.backup.RestoreJournal
import io.github.aj4abinjacob.chikku.core.model.backup.RestoreRecord
import io.github.aj4abinjacob.chikku.data.ChikkuDatabase
import io.github.aj4abinjacob.chikku.data.RoomAppCustomizationRepository
import io.github.aj4abinjacob.chikku.data.RoomDrawerGroupRepository
import io.github.aj4abinjacob.chikku.data.RoomWorkspaceRepository
import io.github.aj4abinjacob.chikku.data.WorkspaceGrids
import io.github.aj4abinjacob.chikku.home.FakeSettingsRepository
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

/** Backup -> file text -> restore across the real Room stores (in-memory database). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BackupRestoreRoomTest {
    private lateinit var db: ChikkuDatabase
    private lateinit var workspace: RoomWorkspaceRepository
    private lateinit var customizations: RoomAppCustomizationRepository
    private lateinit var groups: RoomDrawerGroupRepository
    private val settings = FakeSettingsRepository()
    private lateinit var service: BackupService

    private val installed = AppKey("com.here", "com.here.Main", 0)
    private val missing = AppKey("com.not.installed", "com.not.installed.Main", 0)
    private val clock = ComponentKey("com.clock", "com.clock.Widget", 0)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ChikkuDatabase::class.java).allowMainThreadQueries().build()
        workspace = RoomWorkspaceRepository(db, Dispatchers.IO, MutableStateFlow(WorkspaceGrids.DEFAULT))
        customizations = RoomAppCustomizationRepository(db, Dispatchers.IO)
        groups = RoomDrawerGroupRepository(db, Dispatchers.IO)
        service = BackupService(settings, workspace, customizations, groups, appVersion = "test")
    }

    @After
    fun tearDown() = db.close()

    private suspend fun ready(): Workspace =
        (workspace.workspace.first { it is WorkspaceState.Ready } as WorkspaceState.Ready).workspace

    private fun desk(x: Int, y: Int, sx: Int = 1, sy: Int = 1) = CellPlacement(ContainerType.DESKTOP, 0, x, y, sx, sy)

    /** Builds a layout with an app, a folder holding a missing app, and a bound widget; returns its backup text. */
    private suspend fun backupText(): String {
        val anchor = (workspace.addApp(installed, desk(0, 0)) as EditResult.Applied).itemId
        workspace.createFolder(anchor, FolderSource.Target(ItemTarget.App(missing)), "Mixed")
        workspace.addApp(missing, desk(1, 0))
        workspace.addWidget(appWidgetId = 314, provider = clock, placement = desk(0, 2, 2, 2))
        customizations.setHidden(missing, true)
        groups.createGroup("Tools")
        settings.update { it.copy(home = it.home.copy(desktopRows = 6)) }
        return BackupCodec.encode((service.create() as BackupCreateResult.Success).backup)
    }

    @Test
    fun removedFolderTapTargetFallbackSurvivesPortableRestore() = runTest {
        val anchor = (workspace.addApp(installed, desk(0, 0)) as EditResult.Applied).itemId
        val shortcut = ItemTarget.Shortcut("com.example.links", "route:home", 0)
        val folderId = (workspace.createFolder(anchor, FolderSource.Target(shortcut), "Links") as EditResult.Applied).itemId
        workspace.setItemActions(folderId, ItemActions(
            tap = GestureAction.LaunchShortcut(shortcut.packageName, shortcut.shortcutId, shortcut.userSerial),
            swipeUp = GestureAction.OpenFolder,
        ))
        val removed = (ready().items.single().target as ItemTarget.Folder).entries.last().id
        assertThat(workspace.removeFromFolder(folderId, removed)).isEqualTo(EditResult.Applied(folderId))
        val expected = ready().items

        val text = BackupCodec.encode((service.create() as BackupCreateResult.Success).backup)
        service.resetToDefault(emptyList()) { "seed" }
        val backup = (BackupCodec.decode(text) as BackupDecodeResult.Success).backup
        assertThat(service.restore(backup)).isEqualTo(BackupOperationResult.Applied)

        assertThat(ready().items).isEqualTo(expected)
        assertThat(ready().items.single().actions.tap).isEqualTo(GestureAction.LaunchApp(installed))
    }

    @Test
    fun restoreOnCleanDeviceKeepsMissingAppsAndNeedsWidgetRebind() = runTest {
        val text = backupText()

        // "New device": wipe everything, then restore from the file text.
        service.resetToDefault(emptyList()) { "x" }
        assertThat(ready().items).isEmpty()
        val backup = (BackupCodec.decode(text) as BackupDecodeResult.Success).backup
        assertThat(backup.summary(installed = setOf(installed)).missingApps).isEqualTo(1)
        assertThat(service.restore(backup)).isEqualTo(BackupOperationResult.Applied)

        val items = ready().items
        assertThat(items).hasSize(3)
        val folder = items.single { it.target is ItemTarget.Folder }.target as ItemTarget.Folder
        assertThat(folder.entries.map { it.target }).containsExactly(ItemTarget.App(installed), ItemTarget.App(missing)).inOrder()
        assertThat(items.map { it.target }).contains(ItemTarget.App(missing))
        val widget = items.single { it.target is ItemTarget.Widget }
        assertThat(widget.target).isEqualTo(ItemTarget.Widget(0, clock))
        assertThat(widget.placement).isEqualTo(desk(0, 2, 2, 2))
        assertThat(customizations.all()).containsExactly(AppCustomization(missing, hidden = true))
        assertThat(groups.all()!!.map { it.name }).containsExactly("Tools")
        assertThat(settings.state.value.home.desktopRows).isEqualTo(6)
    }

    @Test
    fun cleanRestoreKeepsFirefoxUrlAndOpaqueIdsWithAppearanceAndFolderPlacement() = runTest {
        val url = ItemTarget.Shortcut(
            "org.mozilla.firefox", "https://example.com/a%20page?q=one%26two#part", 0,
            "Synthetic page", "com.example.icons", "sample_site",
        )
        val homeShortcut = (workspace.addShortcut(url, desk(0, 0)) as EditResult.Applied).itemId
        assertThat(workspace.setShortcutIcon(homeShortcut, null, "com.example.other", "chosen_home"))
            .isEqualTo(EditResult.Applied(homeShortcut))
        val anchor = (workspace.addApp(installed, desk(1, 0)) as EditResult.Applied).itemId
        val opaque = ItemTarget.Shortcut("org.mozilla.firefox", "d531749f-800c-4419-aa33-e5a2ce0a662d", 0, "Other page")
        val folderId = (workspace.createFolder(anchor, FolderSource.Target(opaque), "Sites") as EditResult.Applied).itemId
        val entryId = (ready().items.single { it.id == folderId }.target as ItemTarget.Folder)
            .entries.single { it.target is ItemTarget.Shortcut }.id
        assertThat(workspace.setShortcutIcon(entryId, folderId, "com.example.other", "chosen_folder"))
            .isEqualTo(EditResult.Applied(entryId))
        val before = workspace.exportContent()
        val text = BackupCodec.encode((service.create() as BackupCreateResult.Success).backup)
        service.resetToDefault(emptyList()) { "seed" }
        val restored = (BackupCodec.decode(text) as BackupDecodeResult.Success).backup

        assertThat(service.restore(restored)).isEqualTo(BackupOperationResult.Applied)
        assertThat(workspace.exportContent()).isEqualTo(before)
        assertThat(restored.summary(emptySet()).shortcuts).isEqualTo(2)
    }

    @Test
    fun restoreIsUndoableAndRepositoryUndoAlsoWorks() = runTest {
        val text = backupText()
        val before = ready().items
        service.resetToDefault(listOf(installed)) { "seed" }
        val reset = ready().items
        assertThat(reset.map { it.target }).containsExactly(ItemTarget.App(installed))
        assertThat(reset.single().placement.container).isEqualTo(ContainerType.DOCK)
        assertThat(customizations.all()).isEmpty()
        assertThat(groups.all()).isEmpty()
        assertThat(settings.state.value.home.desktopRows).isEqualTo(5)

        // Undo of the reset brings back the live layout, widget id included (same device).
        assertThat(service.undo()).isTrue()
        assertThat(ready().items.toSet()).isEqualTo(before.toSet())
        assertThat(settings.state.value.home.desktopRows).isEqualTo(6)

        // Restore also leaves a workspace snapshot, so HOME's own undo can revert it.
        val backup = (BackupCodec.decode(text) as BackupDecodeResult.Success).backup
        service.restore(backup)
        assertThat(workspace.undoLastEdit()).isTrue()
        assertThat(ready().items.toSet()).isEqualTo(before.toSet())
    }

    @Test fun failedCommitRecoveryNeverMakesTheRejectedWorkspaceAnUndoTarget() = runTest {
        val text = backupText()
        val before = workspace.exportContent()
        val memory = MemoryRestoreJournal()
        val journal = object : RestoreJournal by memory {
            override suspend fun write(record: RestoreRecord): Boolean = if (record.committed) false else memory.write(record)
        }
        val failing = BackupService(settings, workspace, customizations, groups, "test", journal = journal)
        val target = (BackupCodec.decode(text) as BackupDecodeResult.Success).backup.copy(
            workspace = io.github.aj4abinjacob.chikku.core.model.WorkspaceContent(3, emptyList()))
        assertThat(failing.restore(target)).isEqualTo(BackupOperationResult.Failed(BackupOperationResult.Step.JOURNAL))
        assertThat(workspace.exportContent()).isEqualTo(before)
        assertThat(workspace.undoLastEdit()).isTrue()
        assertThat(workspace.exportContent()).isEqualTo(before)
    }

    @Test
    fun damagedFileNeverTouchesCurrentLayout() = runTest {
        val text = backupText()
        val before = ready().items
        listOf(text.dropLast(10), text.replace("\"DESKTOP\"", "\"NOWHERE\""), text.replace("\"formatVersion\":1", "\"formatVersion\":2"))
            .forEach { damaged -> assertThat(BackupCodec.decode(damaged)).isInstanceOf(BackupDecodeResult.Failure::class.java) }
        assertThat(ready().items).isEqualTo(before)
    }
}
