package io.github.aj4abinjacob.chikku.data.restore

import io.github.aj4abinjacob.chikku.core.model.AppCustomization
import io.github.aj4abinjacob.chikku.core.model.AppCustomizationRepository
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.CellPlacement
import io.github.aj4abinjacob.chikku.core.model.ComponentKey
import io.github.aj4abinjacob.chikku.core.model.DrawerGroup
import io.github.aj4abinjacob.chikku.core.model.DrawerGroupRepository
import io.github.aj4abinjacob.chikku.core.model.DrawerSortMode
import io.github.aj4abinjacob.chikku.core.model.EditResult
import io.github.aj4abinjacob.chikku.core.model.FolderSource
import io.github.aj4abinjacob.chikku.core.model.ItemActions
import io.github.aj4abinjacob.chikku.core.model.ItemTarget
import io.github.aj4abinjacob.chikku.core.model.RejectReason
import io.github.aj4abinjacob.chikku.core.model.WorkspaceContent
import io.github.aj4abinjacob.chikku.core.model.WorkspaceRepository
import io.github.aj4abinjacob.chikku.core.model.WorkspaceState
import io.github.aj4abinjacob.chikku.core.model.backup.RestoreAccess
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.core.model.settings.PaddingSize
import io.github.aj4abinjacob.chikku.core.model.settings.SettingsImportResult
import io.github.aj4abinjacob.chikku.core.model.settings.SettingsRepository
import java.io.IOException

/** All UI/feature access uses these decorators; BackupService alone uses the underlying stores. */
class RecoverySettings(private val source: SettingsRepository, private val access: RestoreAccess) : SettingsRepository {
    override val settings = access.observe(source.settings, LauncherSettings())
    override suspend fun update(transform: (LauncherSettings) -> LauncherSettings) {
        if (!access.run(false) { source.update(transform); true }) throw IOException("Restore recovery is required")
    }
    override suspend fun reset() = update { LauncherSettings() }
    override suspend fun exportJson(): String = access.run<String?>(null) { source.exportJson() }
        ?: throw IOException("Restore recovery is required")
    override suspend fun importJson(json: String): SettingsImportResult = access.run<SettingsImportResult>(SettingsImportResult.Rejected("Restore recovery is required")) { source.importJson(json) }
}

class RecoveryCustomizations(private val source: AppCustomizationRepository, private val access: RestoreAccess) : AppCustomizationRepository {
    override val customizations = access.observe(source.customizations, emptyMap())
    override suspend fun get(key: AppKey) = access.run<AppCustomization?>(null) { source.get(key) }
    override suspend fun update(key: AppKey, transform: (AppCustomization) -> AppCustomization) = access.run(false) { source.update(key, transform) }
    override suspend fun clear(key: AppKey) = access.run(false) { source.clear(key) }
    override suspend fun all() = access.run<List<AppCustomization>?>(null) { source.all() }
    override suspend fun replaceAll(customizations: List<AppCustomization>) = access.run(false) { source.replaceAll(customizations) }
}

class RecoveryGroups(private val source: DrawerGroupRepository, private val access: RestoreAccess) : DrawerGroupRepository {
    override val groups = access.observe(source.groups, emptyList())
    override suspend fun createGroup(name: String, sortMode: DrawerSortMode) = access.run<String?>(null) { source.createGroup(name, sortMode) }
    override suspend fun renameGroup(groupId: String, name: String) = access.run(false) { source.renameGroup(groupId, name) }
    override suspend fun setSortMode(groupId: String, sortMode: DrawerSortMode) = access.run(false) { source.setSortMode(groupId, sortMode) }
    override suspend fun deleteGroup(groupId: String) = access.run(false) { source.deleteGroup(groupId) }
    override suspend fun reorderGroups(orderedGroupIds: List<String>) = access.run(false) { source.reorderGroups(orderedGroupIds) }
    override suspend fun setMembers(groupId: String, members: List<AppKey>) = access.run(false) { source.setMembers(groupId, members) }
    override suspend fun addMember(groupId: String, key: AppKey) = access.run(false) { source.addMember(groupId, key) }
    override suspend fun removeMember(groupId: String, key: AppKey) = access.run(false) { source.removeMember(groupId, key) }
    override suspend fun all() = access.run<List<DrawerGroup>?>(null) { source.all() }
    override suspend fun replaceAll(groups: List<DrawerGroup>) = access.run(false) { source.replaceAll(groups) }
}

class RecoveryWorkspace(private val source: WorkspaceRepository, private val access: RestoreAccess) : WorkspaceRepository {
    private val rejected = EditResult.Rejected(RejectReason.STORAGE_ERROR)
    override val workspace = access.observe(source.workspace, WorkspaceState.Failed("Restore recovery is required"))
    private suspend fun edit(block: suspend () -> EditResult): EditResult = access.run<EditResult>(rejected, block)
    override suspend fun addApp(key: AppKey, preferred: CellPlacement?) = edit { source.addApp(key, preferred) }
    override suspend fun addInstalledApp(key: AppKey) = edit { source.addInstalledApp(key) }
    override suspend fun moveItem(itemId: String, to: CellPlacement, allowOverlap: Boolean) = edit { source.moveItem(itemId, to, allowOverlap) }
    override suspend fun removeItem(itemId: String) = edit { source.removeItem(itemId) }
    override suspend fun undoLastEdit() = access.restoring(false) { source.undoLastEdit() }
    override suspend fun relocateHiddenItems() = access.run<io.github.aj4abinjacob.chikku.core.model.layout.HiddenItemsRecoveryResult?>(null) { source.relocateHiddenItems() }
    override suspend fun undoHiddenRelocation() = access.run(false) { source.undoHiddenRelocation() }
    override suspend fun addPage() = access.run(-1) { source.addPage() }
    override suspend fun removePage(page: Int, deleteItems: Boolean) = access.restoring<EditResult>(rejected) { source.removePage(page, deleteItems) }
    override suspend fun createFolder(anchorItemId: String, other: FolderSource, title: String) = edit { source.createFolder(anchorItemId, other, title) }
    override suspend fun addToFolder(folderId: String, source: FolderSource, rank: Int?) = edit { this.source.addToFolder(folderId, source, rank) }
    override suspend fun removeFromFolder(folderId: String, entryId: String, to: CellPlacement?) = edit { source.removeFromFolder(folderId, entryId, to) }
    override suspend fun renameFolder(folderId: String, title: String) = edit { source.renameFolder(folderId, title) }
    override suspend fun reorderFolder(folderId: String, orderedEntryIds: List<String>) = edit { source.reorderFolder(folderId, orderedEntryIds) }
    override suspend fun addWidget(appWidgetId: Int, provider: ComponentKey, placement: CellPlacement, allowOverlap: Boolean) = edit { source.addWidget(appWidgetId, provider, placement, allowOverlap) }
    override suspend fun updateWidgetId(itemId: String, appWidgetId: Int) = edit { source.updateWidgetId(itemId, appWidgetId) }
    override suspend fun rebindWidget(itemId: String, expected: ItemTarget.Widget, appWidgetId: Int) = edit { source.rebindWidget(itemId, expected, appWidgetId) }
    override suspend fun addShortcut(shortcut: ItemTarget.Shortcut, preferred: CellPlacement?) = edit { source.addShortcut(shortcut, preferred) }
    override suspend fun setShortcutIcon(itemId: String, folderId: String?, iconPack: String?, drawableName: String?) =
        edit { source.setShortcutIcon(itemId, folderId, iconPack, drawableName) }
    override suspend fun resizeItem(itemId: String, spanX: Int, spanY: Int, allowOverlap: Boolean) = edit { source.resizeItem(itemId, spanX, spanY, allowOverlap) }
    override suspend fun setItemActions(itemId: String, actions: ItemActions) = edit { source.setItemActions(itemId, actions) }
    override suspend fun setWidgetPadding(itemId: String, padding: PaddingSize?) = edit { source.setWidgetPadding(itemId, padding) }
    override suspend fun exportContent() = access.run<WorkspaceContent?>(null) { source.exportContent() }
    override suspend fun retainedWidgetIds() = access.run<Set<Int>?>(null) { source.retainedWidgetIds() }
    override suspend fun replaceAll(content: WorkspaceContent) = edit { source.replaceAll(content) }
    override suspend fun recoverContent(content: WorkspaceContent) = edit { source.recoverContent(content) }
}
