package io.github.aj4abinjacob.chikku.data

import androidx.room.withTransaction
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.CellPlacement
import io.github.aj4abinjacob.chikku.core.model.ComponentKey
import io.github.aj4abinjacob.chikku.core.model.ContainerType
import io.github.aj4abinjacob.chikku.core.model.EditResult
import io.github.aj4abinjacob.chikku.core.model.FolderEntry
import io.github.aj4abinjacob.chikku.core.model.FolderSource
import io.github.aj4abinjacob.chikku.core.model.GestureAction
import io.github.aj4abinjacob.chikku.core.model.GridSpec
import io.github.aj4abinjacob.chikku.core.model.ItemActions
import io.github.aj4abinjacob.chikku.core.model.ItemTarget
import io.github.aj4abinjacob.chikku.core.model.RejectReason
import io.github.aj4abinjacob.chikku.core.model.Workspace
import io.github.aj4abinjacob.chikku.core.model.WorkspaceContent
import io.github.aj4abinjacob.chikku.core.model.WorkspaceItem
import io.github.aj4abinjacob.chikku.core.model.WorkspaceLimits
import io.github.aj4abinjacob.chikku.core.model.WorkspaceRepository
import io.github.aj4abinjacob.chikku.core.model.WorkspaceState
import io.github.aj4abinjacob.chikku.core.model.layout.HiddenItemsRecoveryResult
import io.github.aj4abinjacob.chikku.core.model.layout.HiddenLayoutRecovery
import io.github.aj4abinjacob.chikku.core.model.layout.WorkspaceLayoutEngine
import io.github.aj4abinjacob.chikku.core.model.settings.PaddingSize
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

/** Desktop and dock grid currently in effect (from settings). */
/**
 * Grids placements are validated against. [dock] is the stored dock row (all dock pages side by
 * side); [dockPageColumns] is the width of one dock page, where defaults are seeded.
 */
data class WorkspaceGrids(val desktop: GridSpec, val dock: GridSpec, val dockPageColumns: Int = dock.columns) {
    companion object {
        val DEFAULT = WorkspaceGrids(GridSpec(5, 5), GridSpec(5, 1))
    }
}

/**
 * Room-backed [WorkspaceRepository].
 *
 * Every edit runs as one Room transaction on [ioDispatcher]: it reads the current state, validates
 * the change with [WorkspaceLayoutEngine], stores an undo snapshot and only then writes. Rejected
 * edits and storage failures leave the database unchanged. Items whose app is uninstalled are kept;
 * nothing here deletes user data implicitly. Grid size comes from [grids] (settings); shrinking the
 * grid never moves or deletes items, it only bounds new edits.
 */
class RoomWorkspaceRepository(
    private val db: ChikkuDatabase,
    private val ioDispatcher: CoroutineDispatcher,
    private val grids: Flow<WorkspaceGrids> = flowOf(WorkspaceGrids.DEFAULT),
    private val clock: () -> Long = System::currentTimeMillis,
    private val pageSettings: io.github.aj4abinjacob.chikku.core.model.settings.SettingsRepository? = null,
    private val onPageRecoveryRequired: () -> Unit = {},
) : WorkspaceRepository {

    private val dao = db.workspaceDao()
    private val edits = Mutex()
    private val pageJournal = pageSettings?.let { PageEditJournal(db, it, onPageRecoveryRequired) }

    /** Called by the shared access gate before any store is exposed after startup/failure. */
    suspend fun recoverPendingPageEdit(): Boolean = withContext(ioDispatcher) {
        (pageJournal?.recover() ?: (dao.meta("pendingPageEdit") == null)).also {
            if (!it) onPageRecoveryRequired()
        }
    }

    override val workspace: Flow<WorkspaceState> = flow {
        emit(WorkspaceState.Loading)
        combine(
            grids.distinctUntilChanged(),
            db.invalidationTracker.createFlow(ITEMS_TABLE, META_TABLE, FOLDER_ITEMS_TABLE),
        ) { g, _ -> g }.collect { g ->
            val current = db.withTransaction { loadCurrent(g) }
            emit(WorkspaceState.Ready(Workspace(g.desktop, g.dock, current.pageCount, current.items)))
        }
    }
        .distinctUntilChanged()
        // The reason is deliberately generic: no labels, package names or row contents.
        .catch { e -> emit(WorkspaceState.Failed("Workspace storage could not be read (${e.javaClass.simpleName})")) }
        .flowOn(ioDispatcher)

    override suspend fun addApp(key: AppKey, preferred: CellPlacement?): EditResult =
        addAppInternal(key, preferred, includeFolders = false)

    override suspend fun addInstalledApp(key: AppKey): EditResult =
        addAppInternal(key, null, includeFolders = true)

    private suspend fun addAppInternal(key: AppKey, preferred: CellPlacement?, includeFolders: Boolean): EditResult = edit { current ->
        if (current.items.any { item ->
                when (val target = item.target) {
                    is ItemTarget.App -> target.key == key
                    is ItemTarget.Folder -> includeFolders && target.entries.any { (it.target as? ItemTarget.App)?.key == key }
                    else -> false
                }
            }) {
            return@edit EditResult.Rejected(RejectReason.DUPLICATE)
        }
        val placement = resolvePlacement(current, preferred).getOrElse { return@edit it }
        saveSnapshot(current, "add")
        val now = clock()
        val id = UUID.randomUUID().toString()
        dao.insertItem(newEntity(id, key, placement, now))
        growPages(current, placement)
        EditResult.Applied(id)
    }

    override suspend fun moveItem(itemId: String, to: CellPlacement, allowOverlap: Boolean): EditResult = edit { current ->
        val entity = current.entity(itemId) ?: return@edit EditResult.Rejected(RejectReason.UNKNOWN_ITEM)
        if (!current.isInBounds(to)) return@edit EditResult.Rejected(RejectReason.OUT_OF_BOUNDS)
        if (!allowOverlap && !WorkspaceLayoutEngine.isFree(current.items, to, ignoreItemId = itemId)) {
            return@edit EditResult.Rejected(RejectReason.OCCUPIED)
        }
        saveSnapshot(current, "move")
        dao.updateItem(entity.withPlacement(to).copy(updatedAt = clock()))
        EditResult.Applied(itemId)
    }

    override suspend fun removeItem(itemId: String): EditResult = edit { current ->
        if (current.entity(itemId) == null) return@edit EditResult.Rejected(RejectReason.UNKNOWN_ITEM)
        saveSnapshot(current, "remove")
        dao.deleteItem(itemId)
        dao.deleteFolderItemsOf(itemId)
        EditResult.Applied(itemId)
    }

    /** Restores and consumes the newest snapshot. A malformed snapshot is kept and nothing changes. */
    override suspend fun undoLastEdit(): Boolean = undoSnapshot()

    override suspend fun undoHiddenRelocation(): Boolean = undoSnapshot("relocateHidden")

    private suspend fun undoSnapshot(requiredReason: String? = null): Boolean = pageOperation(false) {
        val snapshot = dao.latestSnapshot() ?: return@pageOperation false
        if (requiredReason != null && snapshot.reason != requiredReason) return@pageOperation false
        val payload = WorkspaceSnapshotCodec.decode(snapshot.payloadJson)
        validateRecoverySnapshot(payload)
        val journal = pageJournal
        if (journal != null && payload.defaultPageBefore != null) {
            val g = currentGrids() ?: return@pageOperation false
            val current = db.withTransaction { loadCurrent(g) }
            val selected = pageSettings!!.settings.first().home.defaultPage
            // A later explicit selection wins over the default captured by the deleted page.
            val restored = if (selected == payload.defaultPageAfter) payload.defaultPageBefore else selected
            journal.apply(current.payload(), selected, restored) {
                writeAll(payload)
                dao.deleteSnapshot(snapshot.id)
            }
        } else db.withTransaction {
            writeAll(payload)
            dao.deleteSnapshot(snapshot.id)
        }
        true
    }

    /** Returns the new page's index, or -1 if storage failed (nothing is changed in that case). */
    override suspend fun addPage(): Int {
        val g = currentGrids() ?: return -1
        return guard(-1) {
            val current = loadCurrent(g)
            saveSnapshot(current, "addPage")
            check(current.pageCount < WorkspaceLimits.MAX_PAGES)
            putPageCount(current.pageCount + 1)
            current.pageCount
        }
    }

    override suspend fun removePage(page: Int, deleteItems: Boolean): EditResult = pageOperation(EditResult.Rejected(RejectReason.STORAGE_ERROR)) {
        val g = currentGrids() ?: return@pageOperation EditResult.Rejected(RejectReason.STORAGE_ERROR)
        val current = db.withTransaction { loadCurrent(g) }
        if (current.pageCount <= 1 || page !in 0 until current.pageCount) {
            return@pageOperation EditResult.Rejected(RejectReason.INVALID)
        }
        val desktop = current.entities.filter { it.container == WorkspaceItemEntity.CONTAINER_DESKTOP }
        val onPage = desktop.filter { it.page == page }
        if (onPage.isNotEmpty() && !deleteItems) return@pageOperation EditResult.Rejected(RejectReason.OCCUPIED)
        val before = pageSettings?.settings?.first()?.home?.defaultPage
        val after = before?.let { if (page < it) it - 1 else it.coerceAtMost(current.pageCount - 2) }
        val change: suspend () -> EditResult = {
            saveSnapshot(current, "removePage", before, after)
            val now = clock()
            onPage.forEach {
                dao.deleteItem(it.id)
                dao.deleteFolderItemsOf(it.id)
            }
            desktop.filter { it.page > page }.forEach { dao.updateItem(it.copy(page = it.page - 1, updatedAt = now)) }
            putPageCount(current.pageCount - 1)
            EditResult.Applied("page:$page")
        }
        if (pageJournal != null) pageJournal.apply(current.payload(), before!!, after!!, change)
        else db.withTransaction { change() }
    }

    private suspend fun <T> pageOperation(onFailure: T, block: suspend () -> T): T = try {
        withContext(ioDispatcher) {
            edits.withLock {
                check(recoverPendingPageEdit())
                block()
            }
        }
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { onFailure }

    // --- Folders ---------------------------------------------------------------------------------

    override suspend fun createFolder(anchorItemId: String, other: FolderSource, title: String): EditResult =
        edit { current ->
            val anchor = current.entity(anchorItemId) ?: return@edit EditResult.Rejected(RejectReason.UNKNOWN_ITEM)
            if (anchor.kind !in FOLDER_MEMBER_KINDS) return@edit EditResult.Rejected(RejectReason.INVALID)
            val otherEntity = when (other) {
                is FolderSource.Item -> {
                    if (other.itemId == anchorItemId) return@edit EditResult.Rejected(RejectReason.INVALID)
                    val e = current.entity(other.itemId) ?: return@edit EditResult.Rejected(RejectReason.UNKNOWN_ITEM)
                    if (e.kind !in FOLDER_MEMBER_KINDS) return@edit EditResult.Rejected(RejectReason.INVALID)
                    e
                }
                is FolderSource.Target -> null
            }
            val now = clock()
            val folderId = UUID.randomUUID().toString()
            val entries = mutableListOf(anchor.toFolderItem(folderId, 0))
            val second = otherEntity?.toFolderItem(folderId, 1)
                ?: targetFolderItem((other as FolderSource.Target).target, folderId, 1)
                ?: return@edit EditResult.Rejected(RejectReason.INVALID)
            // The same app (or shortcut) twice in one folder is never what a drop means.
            if (second.sameTargetAs(entries.first())) return@edit EditResult.Rejected(RejectReason.DUPLICATE)
            entries += second
            saveSnapshot(current, "createFolder")
            dao.deleteItem(anchor.id)
            otherEntity?.let { dao.deleteItem(it.id) }
            dao.insertItem(
                anchor.copy(
                    id = folderId, kind = WorkspaceItemEntity.KIND_FOLDER, title = title,
                    packageName = "", className = "", userSerial = 0, shortcutId = null,
                    swipeUpAction = null, swipeDownAction = null, tapAction = null,
                    createdAt = now, updatedAt = now,
                ),
            )
            dao.insertFolderItems(entries)
            EditResult.Applied(folderId)
        }

    override suspend fun addToFolder(folderId: String, source: FolderSource, rank: Int?): EditResult = edit { current ->
        val folder = current.folder(folderId) ?: return@edit EditResult.Rejected(RejectReason.UNKNOWN_ITEM)
        val existing = current.folderItems[folderId].orEmpty()
        val sourceEntity = when (source) {
            is FolderSource.Item -> {
                val e = current.entity(source.itemId) ?: return@edit EditResult.Rejected(RejectReason.UNKNOWN_ITEM)
                if (e.kind !in FOLDER_MEMBER_KINDS) return@edit EditResult.Rejected(RejectReason.INVALID)
                e
            }
            is FolderSource.Target -> null
        }
        val newEntry = sourceEntity?.toFolderItem(folderId, 0)
            ?: targetFolderItem((source as FolderSource.Target).target, folderId, 0)
            ?: return@edit EditResult.Rejected(RejectReason.INVALID)
        if (existing.any { it.id == newEntry.id || it.sameTargetAs(newEntry) }) {
            return@edit EditResult.Rejected(RejectReason.DUPLICATE)
        }
        val ordered = existing.toMutableList().apply { add((rank ?: size).coerceIn(0, size), newEntry) }
        saveSnapshot(current, "addToFolder")
        sourceEntity?.let { dao.deleteItem(it.id) }
        dao.deleteFolderItemsOf(folderId)
        dao.insertFolderItems(ordered.reRanked())
        dao.updateItem(folder.copy(updatedAt = clock()))
        EditResult.Applied(folderId)
    }

    override suspend fun removeFromFolder(folderId: String, entryId: String, to: CellPlacement?): EditResult =
        edit { current ->
            val folder = current.folder(folderId) ?: return@edit EditResult.Rejected(RejectReason.UNKNOWN_ITEM)
            val entries = current.folderItems[folderId].orEmpty()
            val entry = entries.firstOrNull { it.id == entryId } ?: return@edit EditResult.Rejected(RejectReason.UNKNOWN_ITEM)
            if (to != null) {
                if (!current.isInBounds(to)) return@edit EditResult.Rejected(RejectReason.OUT_OF_BOUNDS)
                if (!WorkspaceLayoutEngine.isFree(current.items, to)) return@edit EditResult.Rejected(RejectReason.OCCUPIED)
            }
            val remaining = (entries - entry).sortedBy { it.rank }.reRanked()
            // Only retarget a tap that launched the removed member. Explicit actions for another
            // app (including apps outside this folder) and swipe overrides keep their meaning.
            val tapAction = if (GestureAction.decode(folder.tapAction) == entry.launchAction()) {
                remaining.firstOrNull()?.launchAction()?.let(GestureAction::encode)
            } else folder.tapAction
            saveSnapshot(current, "removeFromFolder")
            dao.deleteFolderItemsOf(folderId)
            dao.insertFolderItems(remaining)
            dao.updateItem(folder.copy(tapAction = tapAction, updatedAt = clock()))
            if (to == null) return@edit EditResult.Applied(folderId)
            val now = clock()
            val id = UUID.randomUUID().toString()
            dao.insertItem(entry.toWorkspaceEntity(id, now).withPlacement(to))
            EditResult.Applied(id)
        }

    override suspend fun renameFolder(folderId: String, title: String): EditResult = edit { current ->
        val folder = current.folder(folderId) ?: return@edit EditResult.Rejected(RejectReason.UNKNOWN_ITEM)
        saveSnapshot(current, "renameFolder")
        dao.updateItem(folder.copy(title = title, updatedAt = clock()))
        EditResult.Applied(folderId)
    }

    override suspend fun reorderFolder(folderId: String, orderedEntryIds: List<String>): EditResult = edit { current ->
        val folder = current.folder(folderId) ?: return@edit EditResult.Rejected(RejectReason.UNKNOWN_ITEM)
        val entries = current.folderItems[folderId].orEmpty()
        if (orderedEntryIds.size != entries.size || orderedEntryIds.toSet() != entries.map { it.id }.toSet()) {
            return@edit EditResult.Rejected(RejectReason.INVALID)
        }
        val byId = entries.associateBy { it.id }
        saveSnapshot(current, "reorderFolder")
        dao.updateFolderItems(orderedEntryIds.map { byId.getValue(it) }.reRanked())
        dao.updateItem(folder.copy(updatedAt = clock()))
        EditResult.Applied(folderId)
    }

    // --- Widgets and shortcuts -------------------------------------------------------------------

    override suspend fun addWidget(
        appWidgetId: Int,
        provider: ComponentKey,
        placement: CellPlacement,
        allowOverlap: Boolean,
    ): EditResult = edit { current ->
        if (!current.isInBounds(placement)) return@edit EditResult.Rejected(RejectReason.OUT_OF_BOUNDS)
        val free = WorkspaceLayoutEngine.isFree(current.items, placement)
        if (!allowOverlap && !free) return@edit EditResult.Rejected(RejectReason.OCCUPIED)
        saveSnapshot(current, "addWidget")
        val now = clock()
        val id = UUID.randomUUID().toString()
        val zIndex = if (free) 0 else (current.entities.maxOfOrNull { it.zIndex } ?: 0) + 1
        dao.insertItem(
            WorkspaceItemEntity(
                id = id, container = "", page = 0, x2 = 0, y2 = 0, width2 = 0, height2 = 0,
                kind = WorkspaceItemEntity.KIND_WIDGET,
                packageName = provider.packageName, className = provider.className, userSerial = provider.userSerial,
                createdAt = now, updatedAt = now,
                appWidgetId = appWidgetId, providerPackage = provider.packageName, providerClass = provider.className,
                zIndex = zIndex,
            ).withPlacement(placement),
        )
        EditResult.Applied(id)
    }

    override suspend fun updateWidgetId(itemId: String, appWidgetId: Int): EditResult = changeWidgetId(itemId, appWidgetId, null)

    override suspend fun retainedWidgetIds(): Set<Int>? = guard<Set<Int>?>(null) {
        dao.retainedSnapshots().flatMap {
            WorkspaceSnapshotCodec.decode(it.payloadJson).also(::validateRecoverySnapshot).items
        }
            .filter { it.kind == WorkspaceItemEntity.KIND_WIDGET }
            .mapNotNull { it.appWidgetId?.takeIf { id -> id != 0 } }.toSet()
    }

    override suspend fun rebindWidget(itemId: String, expected: ItemTarget.Widget, appWidgetId: Int): EditResult =
        changeWidgetId(itemId, appWidgetId, expected)

    private suspend fun changeWidgetId(itemId: String, appWidgetId: Int, expected: ItemTarget.Widget?): EditResult = edit { current ->
        val entity = current.entity(itemId) ?: return@edit EditResult.Rejected(RejectReason.UNKNOWN_ITEM)
        if (entity.kind != WorkspaceItemEntity.KIND_WIDGET) return@edit EditResult.Rejected(RejectReason.INVALID)
        if (expected != null && current.items.firstOrNull { it.id == itemId }?.target != expected) {
            return@edit EditResult.Rejected(RejectReason.INVALID)
        }
        saveSnapshot(current, "updateWidgetId")
        dao.updateItem(entity.copy(appWidgetId = appWidgetId, updatedAt = clock()))
        EditResult.Applied(itemId)
    }

    override suspend fun addShortcut(shortcut: ItemTarget.Shortcut, preferred: CellPlacement?): EditResult =
        edit { current ->
            val duplicate = current.items.any {
                val t = it.target as? ItemTarget.Shortcut
                t != null && t.packageName == shortcut.packageName && t.shortcutId == shortcut.shortcutId &&
                    t.userSerial == shortcut.userSerial
            }
            if (duplicate) return@edit EditResult.Rejected(RejectReason.DUPLICATE)
            val placement = resolvePlacement(current, preferred).getOrElse { return@edit it }
            saveSnapshot(current, "addShortcut")
            val id = UUID.randomUUID().toString()
            dao.insertItem(shortcutEntity(id, shortcut, clock()).withPlacement(placement))
            growPages(current, placement)
            EditResult.Applied(id)
        }

    override suspend fun resizeItem(itemId: String, spanX: Int, spanY: Int, allowOverlap: Boolean): EditResult =
        edit { current ->
            val entity = current.entity(itemId) ?: return@edit EditResult.Rejected(RejectReason.UNKNOWN_ITEM)
            if (spanX < 1 || spanY < 1) return@edit EditResult.Rejected(RejectReason.INVALID)
            val placement = current.items.first { it.id == itemId }.placement.copy(spanX = spanX, spanY = spanY)
            if (!current.isInBounds(placement)) return@edit EditResult.Rejected(RejectReason.OUT_OF_BOUNDS)
            if (!allowOverlap && !WorkspaceLayoutEngine.isFree(current.items, placement, ignoreItemId = itemId)) {
                return@edit EditResult.Rejected(RejectReason.OCCUPIED)
            }
            saveSnapshot(current, "resize")
            dao.updateItem(entity.withPlacement(placement).copy(updatedAt = clock()))
            EditResult.Applied(itemId)
        }

    override suspend fun setItemActions(itemId: String, actions: ItemActions): EditResult = edit { current ->
        val entity = current.entity(itemId) ?: return@edit EditResult.Rejected(RejectReason.UNKNOWN_ITEM)
        saveSnapshot(current, "setActions")
        dao.updateItem(entity.withActions(actions).copy(updatedAt = clock()))
        EditResult.Applied(itemId)
    }

    override suspend fun setShortcutIcon(itemId: String, folderId: String?, iconPack: String?, drawableName: String?): EditResult =
        edit { current ->
            val pack = iconPack?.takeIf { it.isNotBlank() }
            val name = drawableName?.takeIf { it.isNotBlank() }
            if ((pack == null) != (name == null)) return@edit EditResult.Rejected(RejectReason.INVALID)
            if (folderId == null) {
                val entity = current.entity(itemId) ?: return@edit EditResult.Rejected(RejectReason.UNKNOWN_ITEM)
                if (entity.kind != WorkspaceItemEntity.KIND_SHORTCUT) return@edit EditResult.Rejected(RejectReason.INVALID)
                if (entity.shortcutIconPack == pack && entity.shortcutIconDrawable == name) return@edit EditResult.Applied(itemId)
                saveSnapshot(current, "shortcutIcon")
                dao.updateItem(entity.copy(shortcutIconPack = pack, shortcutIconDrawable = name, updatedAt = clock()))
            } else {
                val folder = current.folder(folderId) ?: return@edit EditResult.Rejected(RejectReason.UNKNOWN_ITEM)
                val entry = current.folderItems[folderId]?.firstOrNull { it.id == itemId }
                    ?: return@edit EditResult.Rejected(RejectReason.UNKNOWN_ITEM)
                if (entry.kind != WorkspaceItemEntity.KIND_SHORTCUT) return@edit EditResult.Rejected(RejectReason.INVALID)
                if (entry.shortcutIconPack == pack && entry.shortcutIconDrawable == name) return@edit EditResult.Applied(itemId)
                saveSnapshot(current, "shortcutIcon")
                dao.updateFolderItems(listOf(entry.copy(shortcutIconPack = pack, shortcutIconDrawable = name)))
                dao.updateItem(folder.copy(updatedAt = clock()))
            }
            EditResult.Applied(itemId)
        }

    override suspend fun setWidgetPadding(itemId: String, padding: PaddingSize?): EditResult = edit { current ->
        val entity = current.entity(itemId) ?: return@edit EditResult.Rejected(RejectReason.UNKNOWN_ITEM)
        if (entity.kind != WorkspaceItemEntity.KIND_WIDGET) return@edit EditResult.Rejected(RejectReason.INVALID)
        if (entity.widgetPadding == padding?.name) return@edit EditResult.Applied(itemId)
        saveSnapshot(current, "widgetPadding")
        dao.updateItem(entity.copy(widgetPadding = padding?.name, updatedAt = clock()))
        EditResult.Applied(itemId)
    }

    // --- Backup / restore ------------------------------------------------------------------------

    override suspend fun exportContent(): WorkspaceContent? {
        val g = currentGrids() ?: return null
        return guard(null) { loadCurrent(g).let { WorkspaceContent(it.pageCount, it.items) } }
    }

    override suspend fun replaceAll(content: WorkspaceContent): EditResult = replaceContent(content, recordUndo = true)

    override suspend fun recoverContent(content: WorkspaceContent): EditResult = replaceContent(content, recordUndo = false)

    override suspend fun relocateHiddenItems(): HiddenItemsRecoveryResult? {
        val g = currentGrids() ?: return null
        return guard<HiddenItemsRecoveryResult?>(null) {
            val current = loadCurrent(g)
            val plan = HiddenLayoutRecovery.plan(Workspace(g.desktop, g.dock, current.pageCount, current.items))
            if (plan.result.moved > 0) {
                saveSnapshot(current, "relocateHidden")
                val now = clock()
                val byId = plan.content.items.associateBy { it.id }
                val previous = current.items.associate { it.id to it.placement }
                current.entities.forEach { entity ->
                    val target = byId.getValue(entity.id).placement
                    if (previous[entity.id] != target) {
                        dao.updateItem(entity.withPlacement(target).copy(updatedAt = now))
                    }
                }
                putPageCount(plan.content.pageCount)
            }
            plan.result
        }
    }

    private suspend fun replaceContent(content: WorkspaceContent, recordUndo: Boolean): EditResult {
        // Portable replacement must not create a model HOME cannot safely materialize.
        // Private rollback deliberately bypasses this cap to preserve historical content.
        if (recordUndo && (content.pageCount !in 1..WorkspaceLimits.MAX_PAGES ||
                content.items.any { it.placement.container == ContainerType.DESKTOP && it.placement.page >= WorkspaceLimits.MAX_PAGES })) {
            return EditResult.Rejected(RejectReason.OUT_OF_BOUNDS)
        }
        val ids = content.items.map { it.id }
        val entryIds = content.items.flatMap { (it.target as? ItemTarget.Folder)?.entries.orEmpty().map { e -> e.id } }
        if (ids.size != ids.toSet().size || entryIds.size != entryIds.toSet().size) {
            return EditResult.Rejected(RejectReason.DUPLICATE)
        }
        return edit { current ->
            if (recordUndo) saveSnapshot(current, "replaceAll")
            val now = clock()
            val usedPages = content.items.filter { it.placement.container == ContainerType.DESKTOP }
                .maxOfOrNull { it.placement.page + 1 } ?: 1
            writeAll(
                SnapshotPayload(
                    pageCount = maxOf(1, content.pageCount, usedPages),
                    items = content.items.map { it.toEntity(now) },
                    folderItems = content.items.flatMap { item ->
                        (item.target as? ItemTarget.Folder)?.entries.orEmpty().mapIndexed { rank, entry ->
                            targetFolderItem(entry.target, item.id, rank)!!.copy(id = entry.id)
                        }
                    },
                ),
            )
            EditResult.Applied(content.items.firstOrNull()?.id ?: "")
        }
    }

    /**
     * Places up to one dock page ([WorkspaceGrids.dockPageColumns]) of [dockApps] in the first dock
     * page, left to right, and records that
     * defaults were seeded in the same transaction. Once the flag exists this never runs again, even
     * if the user later empties the dock. Apps already on the workspace are skipped.
     *
     * @return true only if this call performed the seeding.
     */
    suspend fun seedDefaultsOnce(dockApps: List<AppKey>): Boolean {
        val g = currentGrids() ?: return false
        return guard(false) {
            if (dao.meta(META_DEFAULTS_SEEDED) != null) return@guard false
            val current = loadCurrent(g)
            val items = current.items.toMutableList()
            val now = clock()
            val firstPage = GridSpec(g.dockPageColumns.coerceIn(1, g.dock.columns), g.dock.rows)
            for (key in dockApps.distinct().take(firstPage.columns)) {
                if (items.any { (it.target as? ItemTarget.App)?.key == key }) continue
                val placement = WorkspaceLayoutEngine.findFirstFree(items, firstPage, ContainerType.DOCK, pageCount = 1)
                    ?: break
                val entity = newEntity(UUID.randomUUID().toString(), key, placement, now)
                dao.insertItem(entity)
                items += entity.toDomain(emptyList())
            }
            dao.putMeta(WorkspaceMetaEntity(META_DEFAULTS_SEEDED, "true"))
            true
        }
    }

    // --- Internals -------------------------------------------------------------------------------

    private suspend fun currentGrids(): WorkspaceGrids? = try {
        grids.first()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    private suspend fun edit(block: suspend (Current) -> EditResult): EditResult {
        val g = currentGrids() ?: return EditResult.Rejected(RejectReason.STORAGE_ERROR)
        return guard(EditResult.Rejected(RejectReason.STORAGE_ERROR)) { block(loadCurrent(g)) }
    }

    /** Runs [block] in one transaction on [ioDispatcher]; any storage failure rolls back to [onFailure]. */
    private suspend fun <T> guard(onFailure: T, block: suspend () -> T): T = try {
        withContext(ioDispatcher) {
            edits.withLock {
                check(recoverPendingPageEdit())
                db.withTransaction { block() }
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        onFailure
    }

    private suspend fun loadCurrent(g: WorkspaceGrids): Current {
        val entities = dao.items()
        val folderItems = dao.folderItems().groupBy { it.folderId }
        val items = entities.map { it.toDomain(folderItems[it.id].orEmpty()) }
        val storedPages = dao.meta(META_PAGE_COUNT)?.toIntOrNull() ?: 1
        val usedPages = items.filter { it.placement.container == ContainerType.DESKTOP }
            .maxOfOrNull { it.placement.page + 1 } ?: 1
        return Current(entities, folderItems, items, maxOf(1, storedPages, usedPages), g)
    }

    private suspend fun writeAll(payload: SnapshotPayload) {
        dao.deleteAllItems()
        dao.deleteAllFolderItems()
        dao.insertItems(payload.items)
        dao.insertFolderItems(payload.folderItems)
        putPageCount(payload.pageCount)
    }

    private suspend fun saveSnapshot(current: Current, reason: String, defaultBefore: Int? = null, defaultAfter: Int? = null) {
        val payload = WorkspaceSnapshotCodec.encode(
            current.payload().copy(defaultPageBefore = defaultBefore, defaultPageAfter = defaultAfter),
        )
        dao.insertSnapshot(WorkspaceSnapshotEntity(createdAt = clock(), reason = reason, payloadJson = payload))
        dao.trimSnapshots(MAX_SNAPSHOTS)
    }

    private fun Current.payload() = SnapshotPayload(pageCount, entities, folderItems.values.flatten())

    private suspend fun putPageCount(count: Int) =
        dao.putMeta(WorkspaceMetaEntity(META_PAGE_COUNT, count.toString()))

    private suspend fun growPages(current: Current, placement: CellPlacement) {
        if (placement.container == ContainerType.DESKTOP && placement.page >= current.pageCount) {
            check(placement.page < WorkspaceLimits.MAX_PAGES)
            putPageCount(placement.page + 1)
        }
    }

    /** Preferred cell if free, else nearest free on that page; null preferred = first free desktop cell. */
    private fun resolvePlacement(current: Current, preferred: CellPlacement?): Result<CellPlacement, EditResult.Rejected> {
        if (preferred == null) {
            val free = WorkspaceLayoutEngine.findFirstFree(current.items, current.grids.desktop,
                ContainerType.DESKTOP, current.pageCount.coerceAtMost(WorkspaceLimits.MAX_PAGES))
            if (free != null) return Result.Ok(free)
            if (current.pageCount >= WorkspaceLimits.MAX_PAGES) return Result.Err(EditResult.Rejected(RejectReason.NO_SPACE))
            return Result.Ok(CellPlacement(ContainerType.DESKTOP, page = current.pageCount, x = 0, y = 0))
        }
        if (!current.isInBounds(preferred)) return Result.Err(EditResult.Rejected(RejectReason.OUT_OF_BOUNDS))
        if (WorkspaceLayoutEngine.isFree(current.items, preferred)) return Result.Ok(preferred)
        return WorkspaceLayoutEngine.findNearestFree(
            current.items, current.gridFor(preferred.container), preferred.container, preferred.page,
            preferred.x, preferred.y, preferred.spanX, preferred.spanY,
        )?.let { Result.Ok(it) } ?: Result.Err(EditResult.Rejected(RejectReason.NO_SPACE))
    }

    private sealed interface Result<out T, out E> {
        data class Ok<T>(val value: T) : Result<T, Nothing>
        data class Err<E>(val error: E) : Result<Nothing, E>
    }

    private inline fun <T, E> Result<T, E>.getOrElse(onError: (E) -> T): T = when (this) {
        is Result.Ok -> value
        is Result.Err -> onError(error)
    }

    private class Current(
        val entities: List<WorkspaceItemEntity>,
        val folderItems: Map<String, List<FolderItemEntity>>,
        val items: List<WorkspaceItem>,
        val pageCount: Int,
        val grids: WorkspaceGrids,
    ) {
        fun entity(id: String) = entities.firstOrNull { it.id == id }

        fun folder(id: String) = entity(id)?.takeIf { it.kind == WorkspaceItemEntity.KIND_FOLDER }

        fun gridFor(container: ContainerType) = when (container) {
            ContainerType.DESKTOP -> grids.desktop
            ContainerType.DOCK -> grids.dock
        }

        fun isInBounds(placement: CellPlacement): Boolean {
            val pageOk = when (placement.container) {
                ContainerType.DESKTOP -> placement.page < pageCount
                ContainerType.DOCK -> placement.page == 0
            }
            return pageOk && WorkspaceLayoutEngine.validate(placement, gridFor(placement.container))
        }
    }

    companion object {
        /** Undo/recovery snapshots retained; older ones are trimmed inside the edit transaction. */
        const val MAX_SNAPSHOTS = 10
        internal const val META_PAGE_COUNT = "pageCount"
        internal const val META_DEFAULTS_SEEDED = "defaultsSeeded"
        private const val ITEMS_TABLE = "workspace_items"
        private const val META_TABLE = "workspace_meta"
        private const val FOLDER_ITEMS_TABLE = "folder_items"
        private val FOLDER_MEMBER_KINDS = setOf(WorkspaceItemEntity.KIND_APP, WorkspaceItemEntity.KIND_SHORTCUT)
    }
}

/** Placement geometry is persisted in half-cell units. */
internal const val UNITS_PER_CELL = 2

internal fun newEntity(id: String, key: AppKey, placement: CellPlacement, now: Long) = WorkspaceItemEntity(
    id = id,
    container = "",
    page = 0, x2 = 0, y2 = 0, width2 = 0, height2 = 0,
    kind = WorkspaceItemEntity.KIND_APP,
    packageName = key.packageName,
    className = key.className,
    userSerial = key.userSerial,
    createdAt = now,
    updatedAt = now,
).withPlacement(placement)

private fun shortcutEntity(id: String, s: ItemTarget.Shortcut, now: Long) = WorkspaceItemEntity(
    id = id, container = "", page = 0, x2 = 0, y2 = 0, width2 = 0, height2 = 0,
    kind = WorkspaceItemEntity.KIND_SHORTCUT,
    packageName = s.packageName, className = "", userSerial = s.userSerial,
    createdAt = now, updatedAt = now,
    shortcutId = s.shortcutId, title = s.label,
    shortcutIconPack = s.iconPackPackage, shortcutIconDrawable = s.iconDrawableName,
)

internal fun WorkspaceItemEntity.withPlacement(placement: CellPlacement) = copy(
    container = when (placement.container) {
        ContainerType.DESKTOP -> WorkspaceItemEntity.CONTAINER_DESKTOP
        ContainerType.DOCK -> WorkspaceItemEntity.CONTAINER_DOCK
    },
    page = placement.page,
    x2 = placement.x * UNITS_PER_CELL,
    y2 = placement.y * UNITS_PER_CELL,
    width2 = placement.spanX * UNITS_PER_CELL,
    height2 = placement.spanY * UNITS_PER_CELL,
)

internal fun WorkspaceItemEntity.withActions(actions: ItemActions) = copy(
    tapAction = actions.tap?.let(GestureAction::encode),
    swipeUpAction = actions.swipeUp?.let(GestureAction::encode),
    swipeDownAction = actions.swipeDown?.let(GestureAction::encode),
)

/** Domain item -> row, used by restore. Folder entries are written separately. */
internal fun WorkspaceItem.toEntity(now: Long): WorkspaceItemEntity {
    val base = WorkspaceItemEntity(
        id = id, container = "", page = 0, x2 = 0, y2 = 0, width2 = 0, height2 = 0,
        kind = WorkspaceItemEntity.KIND_APP, packageName = "", className = "", userSerial = 0,
        createdAt = now, updatedAt = now, zIndex = zIndex,
    )
    val typed = when (val t = target) {
        is ItemTarget.App -> base.copy(packageName = t.key.packageName, className = t.key.className, userSerial = t.key.userSerial)
        is ItemTarget.Folder -> base.copy(kind = WorkspaceItemEntity.KIND_FOLDER, title = t.title)
        is ItemTarget.Widget -> base.copy(
            kind = WorkspaceItemEntity.KIND_WIDGET,
            packageName = t.provider.packageName, className = t.provider.className, userSerial = t.provider.userSerial,
            appWidgetId = t.appWidgetId, providerPackage = t.provider.packageName, providerClass = t.provider.className,
            widgetPadding = widgetPadding?.name,
        )
        is ItemTarget.Shortcut -> shortcutEntity(id, t, now).copy(zIndex = zIndex)
    }
    return typed.withActions(actions).withPlacement(placement)
}

private fun WorkspaceItemEntity.toFolderItem(folderId: String, rank: Int) = FolderItemEntity(
    id = id, folderId = folderId, rank = rank, kind = kind,
    packageName = packageName, className = className, userSerial = userSerial,
    shortcutId = shortcutId, title = title,
    shortcutIconPack = shortcutIconPack, shortcutIconDrawable = shortcutIconDrawable,
)

/** Null when [target] cannot live in a folder. */
private fun targetFolderItem(target: ItemTarget, folderId: String, rank: Int): FolderItemEntity? = when (target) {
    is ItemTarget.App -> FolderItemEntity(
        UUID.randomUUID().toString(), folderId, rank, WorkspaceItemEntity.KIND_APP,
        target.key.packageName, target.key.className, target.key.userSerial,
    )
    is ItemTarget.Shortcut -> FolderItemEntity(
        UUID.randomUUID().toString(), folderId, rank, WorkspaceItemEntity.KIND_SHORTCUT,
        target.packageName, "", target.userSerial, shortcutId = target.shortcutId, title = target.label,
        shortcutIconPack = target.iconPackPackage, shortcutIconDrawable = target.iconDrawableName,
    )
    else -> null
}

private fun FolderItemEntity.toWorkspaceEntity(id: String, now: Long) = WorkspaceItemEntity(
    id = id, container = "", page = 0, x2 = 0, y2 = 0, width2 = 0, height2 = 0,
    kind = kind, packageName = packageName, className = className, userSerial = userSerial,
    createdAt = now, updatedAt = now, shortcutId = shortcutId, title = title,
    shortcutIconPack = shortcutIconPack, shortcutIconDrawable = shortcutIconDrawable,
)

/** Same app component (or shortcut) in the same profile, regardless of entry id or rank. */
private fun FolderItemEntity.sameTargetAs(other: FolderItemEntity) =
    kind == other.kind && packageName == other.packageName && className == other.className &&
        userSerial == other.userSerial && shortcutId == other.shortcutId

private fun List<FolderItemEntity>.reRanked() = mapIndexed { index, e -> e.copy(rank = index) }

private fun FolderItemEntity.launchAction(): GestureAction = when (kind) {
    WorkspaceItemEntity.KIND_APP -> GestureAction.LaunchApp(AppKey(packageName, className, userSerial))
    WorkspaceItemEntity.KIND_SHORTCUT -> GestureAction.LaunchShortcut(packageName, checkNotNull(shortcutId), userSerial)
    else -> error("unsupported folder entry kind")
}

private fun FolderItemEntity.toEntry(): FolderEntry = FolderEntry(
    id = id,
    target = when (kind) {
        WorkspaceItemEntity.KIND_APP -> ItemTarget.App(AppKey(packageName, className, userSerial))
        WorkspaceItemEntity.KIND_SHORTCUT -> ItemTarget.Shortcut(packageName, checkNotNull(shortcutId), userSerial, title, shortcutIconPack, shortcutIconDrawable)
        else -> error("unsupported folder entry kind")
    },
)

/**
 * Maps a row to the whole-cell domain model. Only even half-cell values are written; odd values from
 * a future subgrid build are floored. Unknown containers or kinds throw, which surfaces as
 * [WorkspaceState.Failed] while the rows stay untouched (never a silent reset).
 */
internal fun WorkspaceItemEntity.toDomain(folderEntries: List<FolderItemEntity> = emptyList()): WorkspaceItem {
    val container = when (container) {
        WorkspaceItemEntity.CONTAINER_DESKTOP -> ContainerType.DESKTOP
        WorkspaceItemEntity.CONTAINER_DOCK -> ContainerType.DOCK
        else -> error("unknown container")
    }
    val target = when (kind) {
        WorkspaceItemEntity.KIND_APP -> ItemTarget.App(AppKey(packageName, className, userSerial))
        WorkspaceItemEntity.KIND_FOLDER -> ItemTarget.Folder(title.orEmpty(), folderEntries.sortedBy { it.rank }.map { it.toEntry() })
        WorkspaceItemEntity.KIND_WIDGET -> ItemTarget.Widget(
            appWidgetId = appWidgetId ?: INVALID_APPWIDGET_ID,
            provider = ComponentKey(providerPackage ?: packageName, providerClass ?: className, userSerial),
        )
        WorkspaceItemEntity.KIND_SHORTCUT -> ItemTarget.Shortcut(packageName, checkNotNull(shortcutId), userSerial, title, shortcutIconPack, shortcutIconDrawable)
        else -> error("unsupported item kind")
    }
    return WorkspaceItem(
        id = id,
        placement = CellPlacement(
            container = container,
            page = page,
            x = x2 / UNITS_PER_CELL,
            y = y2 / UNITS_PER_CELL,
            spanX = maxOf(1, width2 / UNITS_PER_CELL),
            spanY = maxOf(1, height2 / UNITS_PER_CELL),
        ),
        target = target,
        actions = ItemActions(
            tap = GestureAction.decode(tapAction),
            swipeUp = GestureAction.decode(swipeUpAction),
            swipeDown = GestureAction.decode(swipeDownAction),
        ),
        zIndex = zIndex,
        // Unknown names (a newer build's size) fall back to the global default rather than failing.
        widgetPadding = widgetPadding?.takeIf { kind == WorkspaceItemEntity.KIND_WIDGET }
            ?.let { name -> PaddingSize.entries.firstOrNull { it.name == name } },
    )
}

/** Same value as `AppWidgetManager.INVALID_APPWIDGET_ID`: the placement exists but needs (re)binding. */
const val INVALID_APPWIDGET_ID = 0
