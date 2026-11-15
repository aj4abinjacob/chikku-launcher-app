package io.github.aj4abinjacob.chikku.core.model

import io.github.aj4abinjacob.chikku.core.model.settings.PaddingSize

/** Grid dimensions in whole cells. */
data class GridSpec(val columns: Int, val rows: Int) {
    init {
        require(columns in 1..MAX_DIMENSION && rows in 1..MAX_DIMENSION) { "invalid grid $columns x $rows" }
    }

    companion object {
        const val MAX_DIMENSION = 12
    }
}

/** Where an item lives. The dock is a single row with its own grid. */
enum class ContainerType { DESKTOP, DOCK }

/** Position and span of an item, in whole cells, on one page of one container. */
data class CellPlacement(
    val container: ContainerType,
    val page: Int,
    val x: Int,
    val y: Int,
    val spanX: Int = 1,
    val spanY: Int = 1,
) {
    init {
        require(page >= 0 && x >= 0 && y >= 0) { "negative placement" }
        require(spanX >= 1 && spanY >= 1) { "span must be positive" }
    }
}

/**
 * Identity of an Android component (widget provider, activity) in one user profile. Unlike
 * [AppKey] the class may be blank for targets that are identified by package only.
 */
data class ComponentKey(
    val packageName: String,
    val className: String,
    val userSerial: Long,
) {
    init {
        require(packageName.isNotBlank()) { "packageName must not be blank" }
    }
}

/** What a workspace item opens or shows. */
sealed interface ItemTarget {
    data class App(val key: AppKey) : ItemTarget

    /**
     * A folder. [entries] are in display order (rank 0 first) and contain only [App] and
     * [Shortcut] targets. A folder may be empty while it is being edited; it is never auto-deleted.
     */
    data class Folder(val title: String, val entries: List<FolderEntry> = emptyList()) : ItemTarget

    /**
     * A bound AppWidget. [appWidgetId] is device-local and must be re-allocated on restore;
     * [provider] stays persisted so a lost or uninstalled provider still leaves a resolvable placement.
     */
    data class Widget(val appWidgetId: Int, val provider: ComponentKey) : ItemTarget

    /** A pinned app shortcut. [label] is cached so the item can render while the app is unavailable. */
    data class Shortcut(
        val packageName: String,
        val shortcutId: String,
        val userSerial: Long,
        val label: String? = null,
        /** Optional appearance only; package/id/profile remain the launch identity. */
        val iconPackPackage: String? = null,
        val iconDrawableName: String? = null,
    ) : ItemTarget {
        init {
            require(packageName.isNotBlank() && shortcutId.isNotEmpty()) { "invalid shortcut" }
        }
    }
}

/** One item inside a folder, with a stable id independent of its rank. */
data class FolderEntry(val id: String, val target: ItemTarget) {
    init {
        require(target is ItemTarget.App || target is ItemTarget.Shortcut) { "folders hold apps and shortcuts only" }
    }
}

/**
 * Per-item gesture overrides (icon/folder swipe actions). Null means the default behaviour:
 * tap launches the app/opens the folder, swipes do nothing.
 */
data class ItemActions(
    val tap: GestureAction? = null,
    val swipeUp: GestureAction? = null,
    val swipeDown: GestureAction? = null,
)

/** A persisted workspace item with a stable id that does not change when it moves. */
data class WorkspaceItem(
    val id: String,
    val placement: CellPlacement,
    val target: ItemTarget,
    val actions: ItemActions = ItemActions(),
    /** Drawing order when overlap is allowed; higher draws on top. */
    val zIndex: Int = 0,
    /** Widget inner space override; null follows `home.widgetPadding`. Ignored for non-widgets. */
    val widgetPadding: PaddingSize? = null,
)

/** Persisted workspace content, for backup/restore via [WorkspaceRepository.exportContent]/[WorkspaceRepository.replaceAll]. */
data class WorkspaceContent(
    val pageCount: Int,
    val items: List<WorkspaceItem>,
)

/** Immutable snapshot of the whole workspace for rendering. */
data class Workspace(
    val desktopGrid: GridSpec,
    val dockGrid: GridSpec,
    val pageCount: Int,
    val items: List<WorkspaceItem>,
)

sealed interface WorkspaceState {
    data object Loading : WorkspaceState
    data class Ready(val workspace: Workspace) : WorkspaceState

    /** Persistent layout could not be read. Data is kept; the UI shows a recovery view. */
    data class Failed(val reason: String) : WorkspaceState
}

/** Result of a workspace edit. Rejected edits leave persisted state unchanged. */
sealed interface EditResult {
    data class Applied(val itemId: String) : EditResult
    data class Rejected(val reason: RejectReason) : EditResult
}

/** [INVALID] covers malformed requests, e.g. a folder anchor that is a widget or a bad reorder permutation. */
enum class RejectReason { OCCUPIED, OUT_OF_BOUNDS, NO_SPACE, UNKNOWN_ITEM, DUPLICATE, STORAGE_ERROR, INVALID }

/** Where a folder member comes from: an item already on the workspace, or a new target (e.g. from the drawer). */
sealed interface FolderSource {
    data class Item(val itemId: String) : FolderSource
    data class Target(val target: ItemTarget) : FolderSource
}

/**
 * Persistent workspace. Every edit is one transaction that stores an undo snapshot first; a
 * rejected edit or storage failure leaves persisted state unchanged.
 */
interface WorkspaceRepository {
    val workspace: kotlinx.coroutines.flow.Flow<WorkspaceState>

    /** Places [key] at [preferred] if free, otherwise at the nearest free cell on that page/container. */
    suspend fun addApp(key: AppKey, preferred: CellPlacement?): EditResult

    /** Install-event placement; production storage also rejects duplicates inside folders atomically. */
    suspend fun addInstalledApp(key: AppKey): EditResult = addApp(key, null)

    /**
     * Moves an item atomically. Fails with [RejectReason.OCCUPIED] rather than overlapping unless
     * [allowOverlap] (home.allowWidgetOverlap) is set.
     */
    suspend fun moveItem(itemId: String, to: CellPlacement, allowOverlap: Boolean = false): EditResult

    /** Removes an item (a folder together with its entries) after storing an undo snapshot. */
    suspend fun removeItem(itemId: String): EditResult

    /** Restores the state saved before the most recent edit. Returns false if none exists. */
    suspend fun undoLastEdit(): Boolean

    /** Moves recoverable out-of-grid items together, with one undo snapshot. Null means no write. */
    suspend fun relocateHiddenItems(): io.github.aj4abinjacob.chikku.core.model.layout.HiddenItemsRecoveryResult? = null

    /** Only consumes an immediately preceding hidden-item relocation, never an unrelated edit. */
    suspend fun undoHiddenRelocation(): Boolean = false

    /** Adds an empty desktop page at the end and returns its index. */
    suspend fun addPage(): Int

    /**
     * Removes a desktop [page]; later pages shift down by one. A page that still holds items is
     * rejected as occupied unless [deleteItems] is true, which removes those items (and the contents of
     * any folders on it) in the same edit. The last remaining page is always rejected. One undo
     * restores the page and everything on it.
     */
    suspend fun removePage(page: Int, deleteItems: Boolean = false): EditResult = EditResult.Rejected(RejectReason.INVALID)

    // --- Folders ---------------------------------------------------------------------------------

    /**
     * Turns the app/shortcut item [anchorItemId] into a folder at the same placement containing the
     * anchor followed by [other]. An [FolderSource.Item] is removed from the workspace. Applied id is
     * the new folder's item id.
     */
    suspend fun createFolder(anchorItemId: String, other: FolderSource, title: String): EditResult

    /** Adds [source] to a folder at [rank] (end when null). Applied id is the folder id. */
    suspend fun addToFolder(folderId: String, source: FolderSource, rank: Int? = null): EditResult

    /**
     * Removes [entryId] from a folder. With [to], the entry becomes a workspace item there (must be
     * free); without, it is deleted. The folder itself stays even if it becomes empty or single.
     * A tap action launching that member follows the first remaining member in stored order,
     * or returns to opening the folder when empty. Membership and tap change share one undo step.
     * Applied id is the new workspace item id when [to] is given, else the folder id.
     */
    suspend fun removeFromFolder(folderId: String, entryId: String, to: CellPlacement? = null): EditResult

    suspend fun renameFolder(folderId: String, title: String): EditResult

    /** [orderedEntryIds] must be a permutation of the folder's entry ids. */
    suspend fun reorderFolder(folderId: String, orderedEntryIds: List<String>): EditResult

    // --- Widgets and shortcuts -------------------------------------------------------------------

    /** Places a bound widget. [placement] must be in bounds and free unless [allowOverlap]. */
    suspend fun addWidget(
        appWidgetId: Int,
        provider: ComponentKey,
        placement: CellPlacement,
        allowOverlap: Boolean = false,
    ): EditResult

    /** Replaces a widget's id (e.g. re-bound after restore or provider update). */
    suspend fun updateWidgetId(itemId: String, appWidgetId: Int): EditResult

    /** Atomically replace only this exact binding; implementations without this guarantee reject. */
    suspend fun rebindWidget(itemId: String, expected: ItemTarget.Widget, appWidgetId: Int): EditResult =
        EditResult.Rejected(RejectReason.INVALID)

    /** Places a pinned shortcut like [addApp]. */
    suspend fun addShortcut(shortcut: ItemTarget.Shortcut, preferred: CellPlacement?): EditResult

    /**
     * Sets a pinned shortcut's icon to [drawableName] from [iconPack], or with both null back to the
     * shortcut's own icon. [itemId] is the workspace item, or with [folderId] that folder's entry.
     * Undoable like other layout edits; anything that is not a shortcut rejects.
     */
    suspend fun setShortcutIcon(itemId: String, folderId: String?, iconPack: String?, drawableName: String?): EditResult =
        EditResult.Rejected(RejectReason.INVALID)

    /** Changes an item's span keeping its origin; must stay in bounds and free unless [allowOverlap]. */
    suspend fun resizeItem(itemId: String, spanX: Int, spanY: Int, allowOverlap: Boolean = false): EditResult

    suspend fun setItemActions(itemId: String, actions: ItemActions): EditResult

    /** Sets a widget's inner space; null returns it to the global default. Non-widgets reject. */
    suspend fun setWidgetPadding(itemId: String, padding: PaddingSize?): EditResult =
        EditResult.Rejected(RejectReason.INVALID)

    // --- Backup / restore ------------------------------------------------------------------------

    /** Current persisted content, or null if storage could not be read. */
    suspend fun exportContent(): WorkspaceContent?

    /** Widget IDs needed by retained edit-Undo snapshots; null means cleanup must be deferred. */
    suspend fun retainedWidgetIds(): Set<Int>? = emptySet()

    /**
     * Replaces the whole workspace with [content] (restore) after storing an undo snapshot, so the
     * previous layout stays recoverable with [undoLastEdit]. Widget ids must already be fresh.
     */
    suspend fun replaceAll(content: WorkspaceContent): EditResult

    /** Journal rollback: restore exact content without adding a half-applied layout to the undo stack. */
    suspend fun recoverContent(content: WorkspaceContent): EditResult = replaceAll(content)
}
