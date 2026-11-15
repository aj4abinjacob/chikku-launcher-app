package io.github.aj4abinjacob.chikku.core.model.backup

import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.AppCustomization
import io.github.aj4abinjacob.chikku.core.model.AppCustomizationRepository
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.CellPlacement
import io.github.aj4abinjacob.chikku.core.model.ComponentKey
import io.github.aj4abinjacob.chikku.core.model.ContainerType
import io.github.aj4abinjacob.chikku.core.model.DrawerGroup
import io.github.aj4abinjacob.chikku.core.model.DrawerGroupRepository
import io.github.aj4abinjacob.chikku.core.model.DrawerSortMode
import io.github.aj4abinjacob.chikku.core.model.EditResult
import io.github.aj4abinjacob.chikku.core.model.FolderSource
import io.github.aj4abinjacob.chikku.core.model.ItemActions
import io.github.aj4abinjacob.chikku.core.model.ItemTarget
import io.github.aj4abinjacob.chikku.core.model.RejectReason
import io.github.aj4abinjacob.chikku.core.model.WorkspaceContent
import io.github.aj4abinjacob.chikku.core.model.WorkspaceItem
import io.github.aj4abinjacob.chikku.core.model.WorkspaceRepository
import io.github.aj4abinjacob.chikku.core.model.WorkspaceState
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettingsCodec
import io.github.aj4abinjacob.chikku.core.model.settings.SettingsImportResult
import io.github.aj4abinjacob.chikku.core.model.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class BackupServiceTest {

    private val mail = AppKey("com.mail", "com.mail.Main", 0)
    private val gone = AppKey("com.gone", "com.gone.Main", 0)
    private val dialer = AppKey("com.dialer", "com.dialer.Main", 0)

    private val settingsRepo = MemSettings()
    private val workspaceRepo = MemWorkspace()
    private val customRepo = MemCustomizations()
    private val groupRepo = MemGroups()
    private val service = BackupService(settingsRepo, workspaceRepo, customRepo, groupRepo, "test", clock = { 1234L })

    private val currentLayout = WorkspaceContent(
        1,
        listOf(
            WorkspaceItem("cur", CellPlacement(ContainerType.DESKTOP, 0, 0, 0), ItemTarget.App(mail)),
            WorkspaceItem("wid", CellPlacement(ContainerType.DESKTOP, 0, 1, 1, 2, 2), ItemTarget.Widget(77, ComponentKey("com.clock", "W", 0))),
        ),
    )

    private fun incoming(): ChikkuBackup = ChikkuBackup(
        createdAtMillis = 1,
        appVersion = "old",
        settings = LauncherSettings().let { it.copy(home = it.home.copy(desktopColumns = 4)) },
        workspace = WorkspaceContent(
            3,
            listOf(
                WorkspaceItem("n1", CellPlacement(ContainerType.DESKTOP, 2, 0, 0), ItemTarget.App(gone)),
                WorkspaceItem("n2", CellPlacement(ContainerType.DESKTOP, 0, 0, 0, 4, 2), ItemTarget.Widget(55, ComponentKey("com.gone", "W", 0))),
            ),
        ),
        customizations = listOf(AppCustomization(gone, customLabel = "x")),
        drawerGroups = listOf(DrawerGroup("g", "G", 0, DrawerSortMode.ALPHABETICAL, listOf(gone))),
    )

    private fun seedCurrent() {
        workspaceRepo.content = currentLayout
        customRepo.list = listOf(AppCustomization(mail, hidden = true))
        groupRepo.list = listOf(DrawerGroup("old", "Old", 0))
        settingsRepo.value = LauncherSettings().let { it.copy(onboarding = it.onboarding.copy(completed = true)) }
    }

    @Test
    fun createExportsWithoutWidgetIds() = runTest {
        seedCurrent()
        val backup = (service.create() as BackupCreateResult.Success).backup
        assertThat(backup.createdAtMillis).isEqualTo(1234L)
        assertThat(backup.customizations).isEqualTo(customRepo.list)
        val widget = backup.workspace.items.single { it.id == "wid" }.target as ItemTarget.Widget
        assertThat(widget.appWidgetId).isEqualTo(0)
    }

    @Test
    fun createFailsWhenStorageUnreadable() = runTest {
        workspaceRepo.content = null
        assertThat(service.create()).isEqualTo(BackupCreateResult.Failed(BackupOperationResult.Step.READ_CURRENT))
    }

    @Test
    fun restoreKeepsMissingAppsAndClearsWidgetIdsThenUndoes() = runTest {
        seedCurrent()
        assertThat(service.restore(incoming())).isEqualTo(BackupOperationResult.Applied)

        val items = workspaceRepo.content!!.items
        // The uninstalled app is kept as a placement (placeholder), never dropped.
        assertThat(items.map { it.target }).contains(ItemTarget.App(gone))
        val widget = items.single { it.id == "n2" }.target as ItemTarget.Widget
        assertThat(widget.appWidgetId).isEqualTo(0)
        assertThat(settingsRepo.value.home.desktopColumns).isEqualTo(4)
        assertThat(settingsRepo.value.onboarding.completed).isTrue()
        assertThat(customRepo.list.single().key).isEqualTo(gone)
        assertThat(groupRepo.list.single().id).isEqualTo("g")
        assertThat(service.canUndo).isTrue()

        assertThat(service.undo()).isTrue()
        assertThat(workspaceRepo.content).isEqualTo(currentLayout)
        assertThat(customRepo.list).containsExactly(AppCustomization(mail, hidden = true))
        assertThat(groupRepo.list.single().id).isEqualTo("old")
        assertThat(settingsRepo.value.home.desktopColumns).isEqualTo(5)
        assertThat(service.canUndo).isFalse()
    }

    @Test
    fun failingWorkspaceStepRollsBackEarlierSteps() = runTest {
        seedCurrent()
        val settingsBefore = settingsRepo.value
        workspaceRepo.rejectNext = true
        val result = service.restore(incoming())
        assertThat(result).isEqualTo(BackupOperationResult.Failed(BackupOperationResult.Step.WORKSPACE))
        assertThat(workspaceRepo.content).isEqualTo(currentLayout)
        assertThat(settingsRepo.value).isEqualTo(settingsBefore)
        assertThat(customRepo.list).containsExactly(AppCustomization(mail, hidden = true))
        assertThat(groupRepo.list.single().id).isEqualTo("old")
        assertThat(service.canUndo).isFalse()
    }

    @Test
    fun resetReseedsDefaultLayoutAndIsUndoable() = runTest {
        seedCurrent()
        settingsRepo.value = settingsRepo.value.let { it.copy(home = it.home.copy(desktopRows = 7)) }
        var n = 0
        assertThat(service.resetToDefault(listOf(dialer, mail)) { "seed${n++}" }).isEqualTo(BackupOperationResult.Applied)

        assertThat(settingsRepo.value).isEqualTo(LauncherSettings().let { it.copy(onboarding = it.onboarding.copy(completed = true)) })
        assertThat(customRepo.list).isEmpty()
        assertThat(groupRepo.list).isEmpty()
        val content = workspaceRepo.content!!
        assertThat(content.pageCount).isEqualTo(1)
        assertThat(content.items.map { it.target }).containsExactly(ItemTarget.App(dialer), ItemTarget.App(mail)).inOrder()
        assertThat(content.items.all { it.placement.container == ContainerType.DOCK }).isTrue()

        assertThat(service.undo()).isTrue()
        assertThat(workspaceRepo.content).isEqualTo(currentLayout)
        assertThat(settingsRepo.value.home.desktopRows).isEqualTo(7)
    }

    @Test fun interruptedWritesRecoverAllStoresBeforeUseAfterRestart() = runTest {
        for (stage in 0..3) {
            seedCurrent()
            val journal = MemoryRestoreJournal()
            fun restarted() = BackupService(settingsRepo, workspaceRepo, customRepo, groupRepo, "test", journal = journal)
            val crash: suspend () -> Unit = { throw SimulatedProcessStop() }
            when (stage) {
                0 -> settingsRepo.afterWrite = crash
                1 -> customRepo.afterWrite = crash
                2 -> groupRepo.afterWrite = crash
                3 -> workspaceRepo.afterWrite = crash
            }
            try { restarted().restore(incoming()); error("expected simulated stop") } catch (_: SimulatedProcessStop) { }
            settingsRepo.afterWrite = {}; customRepo.afterWrite = {}; groupRepo.afterWrite = {}; workspaceRepo.afterWrite = {}
            assertThat(journal.read()!!.committed).isFalse()
            assertThat(restarted().prepare()).isTrue()
            assertThat(workspaceRepo.content).isEqualTo(currentLayout) // live widget 77 survives recovery
            assertThat(customRepo.list).containsExactly(AppCustomization(mail, hidden = true))
            assertThat(groupRepo.list.single().id).isEqualTo("old")
            assertThat(settingsRepo.value.home.desktopColumns).isEqualTo(5)
            assertThat(journal.read()).isNull()
        }
    }

    @Test fun cancellationAfterWriteRollsBackWithoutWaitingForRestart() = runTest {
        seedCurrent()
        var first = true
        customRepo.afterWrite = { if (first) { first = false; throw CancellationException("stop") } }
        try { service.restore(incoming()); error("expected cancellation") } catch (_: CancellationException) { }
        assertThat(workspaceRepo.content).isEqualTo(currentLayout)
        assertThat(settingsRepo.value.home.desktopColumns).isEqualTo(5)
        assertThat(customRepo.list).containsExactly(AppCustomization(mail, hidden = true))
        assertThat(groupRepo.list.single().id).isEqualTo("old")
    }

    @Test fun failedRollbackIsExplicitAndRetainedForRetry() = runTest {
        seedCurrent()
        val journal = MemoryRestoreJournal()
        val service = BackupService(settingsRepo, workspaceRepo, customRepo, groupRepo, "test", journal = journal)
        workspaceRepo.rejectReplace = true
        assertThat(service.restore(incoming())).isEqualTo(BackupOperationResult.Failed(BackupOperationResult.Step.WORKSPACE, true))
        assertThat(journal.read()!!.committed).isFalse()
        assertThat(service.create()).isEqualTo(BackupCreateResult.Failed(BackupOperationResult.Step.RECOVERY))
        workspaceRepo.rejectReplace = false
        assertThat(service.prepare()).isTrue()
        assertThat(workspaceRepo.content).isEqualTo(currentLayout)
        assertThat(journal.read()).isNull()
    }

    @Test fun journalFailureBeforeWriteLeavesEverythingUntouchedAndCommitFailureRollsBack() = runTest {
        for (rejectCommit in listOf(false, true)) {
            seedCurrent()
            val memory = MemoryRestoreJournal()
            val failing = object : RestoreJournal by memory {
                override suspend fun write(record: RestoreRecord): Boolean =
                    if (!rejectCommit || record.committed) false else memory.write(record)
            }
            val service = BackupService(settingsRepo, workspaceRepo, customRepo, groupRepo, "test", journal = failing)
            assertThat(service.restore(incoming())).isEqualTo(BackupOperationResult.Failed(BackupOperationResult.Step.JOURNAL))
            assertThat(workspaceRepo.content).isEqualTo(currentLayout)
            assertThat(settingsRepo.value.home.desktopColumns).isEqualTo(5)
            assertThat(customRepo.list).containsExactly(AppCustomization(mail, hidden = true))
            assertThat(groupRepo.list.single().id).isEqualTo("old")
        }
    }

    @Test fun aWriteReportingFailureAfterPersistingTheCommitIsRecognizedAsCommitted() = runTest {
        seedCurrent()
        val memory = MemoryRestoreJournal()
        val uncertain = object : RestoreJournal by memory {
            override suspend fun write(record: RestoreRecord): Boolean { memory.write(record); return false }
        }
        val service = BackupService(settingsRepo, workspaceRepo, customRepo, groupRepo, "test", journal = uncertain)
        assertThat(service.restore(incoming())).isEqualTo(BackupOperationResult.Applied)
        assertThat(memory.read()!!.committed).isTrue()
        assertThat(service.canUndo).isTrue()
    }

    @Test fun committedUndoSurvivesServiceRestartAndUndoIsConsumed() = runTest {
        seedCurrent()
        val journal = MemoryRestoreJournal()
        fun restarted() = BackupService(settingsRepo, workspaceRepo, customRepo, groupRepo, "test", journal = journal)
        assertThat(restarted().restore(incoming())).isEqualTo(BackupOperationResult.Applied)
        val service = restarted()
        assertThat(service.prepare()).isTrue()
        assertThat(service.canUndo).isTrue()
        assertThat(service.undo()).isTrue()
        assertThat(workspaceRepo.content).isEqualTo(currentLayout)
        val again = restarted()
        assertThat(again.prepare()).isTrue()
        assertThat(again.canUndo).isFalse()
    }

    private class SimulatedProcessStop : Error()

    // --- In-memory stores ------------------------------------------------------------------------

    private class MemSettings : SettingsRepository {
        var value = LauncherSettings()
        var afterWrite: suspend () -> Unit = {}
        override val settings: Flow<LauncherSettings> get() = MutableStateFlow(value)
        override suspend fun update(transform: (LauncherSettings) -> LauncherSettings) {
            value = transform(value).coerced()
        }

        override suspend fun reset() {
            value = LauncherSettings()
        }

        override suspend fun exportJson(): String = LauncherSettingsCodec.encode(value)
        override suspend fun importJson(json: String): SettingsImportResult {
            value = LauncherSettingsCodec.decodeOrNull(json) ?: return SettingsImportResult.Rejected("bad")
            afterWrite()
            return SettingsImportResult.Applied
        }
    }

    private class MemCustomizations : AppCustomizationRepository {
        var list = emptyList<AppCustomization>()
        var afterWrite: suspend () -> Unit = {}
        override val customizations: Flow<Map<AppKey, AppCustomization>> = emptyFlow()
        override suspend fun get(key: AppKey) = list.firstOrNull { it.key == key }
        override suspend fun update(key: AppKey, transform: (AppCustomization) -> AppCustomization) = false
        override suspend fun clear(key: AppKey) = false
        override suspend fun all() = list
        override suspend fun replaceAll(customizations: List<AppCustomization>): Boolean {
            list = customizations
            afterWrite()
            return true
        }
    }

    private class MemGroups : DrawerGroupRepository {
        var list = emptyList<DrawerGroup>()
        var afterWrite: suspend () -> Unit = {}
        override val groups: Flow<List<DrawerGroup>> = emptyFlow()
        override suspend fun createGroup(name: String, sortMode: DrawerSortMode): String? = null
        override suspend fun renameGroup(groupId: String, name: String) = false
        override suspend fun setSortMode(groupId: String, sortMode: DrawerSortMode) = false
        override suspend fun deleteGroup(groupId: String) = false
        override suspend fun reorderGroups(orderedGroupIds: List<String>) = false
        override suspend fun setMembers(groupId: String, members: List<AppKey>) = false
        override suspend fun addMember(groupId: String, key: AppKey) = false
        override suspend fun removeMember(groupId: String, key: AppKey) = false
        override suspend fun all() = list
        override suspend fun replaceAll(groups: List<DrawerGroup>): Boolean {
            list = groups
            afterWrite()
            return true
        }
    }

    private class MemWorkspace : WorkspaceRepository {
        var content: WorkspaceContent? = WorkspaceContent(1, emptyList())
        var rejectReplace = false
        var rejectNext = false
        var afterWrite: suspend () -> Unit = {}
        private val unsupported = EditResult.Rejected(RejectReason.INVALID)
        override val workspace: Flow<WorkspaceState> = emptyFlow()
        override suspend fun addApp(key: AppKey, preferred: CellPlacement?) = unsupported
        override suspend fun moveItem(itemId: String, to: CellPlacement, allowOverlap: Boolean) = unsupported
        override suspend fun removeItem(itemId: String) = unsupported
        override suspend fun undoLastEdit() = false
        override suspend fun addPage() = -1
        override suspend fun createFolder(anchorItemId: String, other: FolderSource, title: String) = unsupported
        override suspend fun addToFolder(folderId: String, source: FolderSource, rank: Int?) = unsupported
        override suspend fun removeFromFolder(folderId: String, entryId: String, to: CellPlacement?) = unsupported
        override suspend fun renameFolder(folderId: String, title: String) = unsupported
        override suspend fun reorderFolder(folderId: String, orderedEntryIds: List<String>) = unsupported
        override suspend fun addWidget(appWidgetId: Int, provider: ComponentKey, placement: CellPlacement, allowOverlap: Boolean) = unsupported
        override suspend fun updateWidgetId(itemId: String, appWidgetId: Int) = unsupported
        override suspend fun addShortcut(shortcut: ItemTarget.Shortcut, preferred: CellPlacement?) = unsupported
        override suspend fun resizeItem(itemId: String, spanX: Int, spanY: Int, allowOverlap: Boolean) = unsupported
        override suspend fun setItemActions(itemId: String, actions: ItemActions) = unsupported
        override suspend fun exportContent() = content
        override suspend fun replaceAll(content: WorkspaceContent): EditResult {
            if (rejectNext) { rejectNext = false; return EditResult.Rejected(RejectReason.STORAGE_ERROR) }
            if (rejectReplace) return EditResult.Rejected(RejectReason.STORAGE_ERROR)
            this.content = content
            afterWrite()
            return EditResult.Applied("")
        }
    }
}
