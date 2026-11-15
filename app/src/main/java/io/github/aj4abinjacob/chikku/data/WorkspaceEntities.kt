package io.github.aj4abinjacob.chikku.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One placed workspace item. Geometry is stored in half-cell units (whole cells x 2) so subgrid
 * placement can arrive later without a column change. Rows are never deleted because their app is
 * uninstalled; the UI renders a repair placeholder instead.
 *
 * Column use per [kind]:
 * - [KIND_APP]: packageName/className/userSerial = the activity.
 * - [KIND_FOLDER]: title = folder name; packageName/className are "" and userSerial 0; entries live in `folder_items`.
 * - [KIND_WIDGET]: appWidgetId (device-local), providerPackage/providerClass = provider component,
 *   packageName/className mirror the provider so per-package queries find it; userSerial = provider profile.
 * - [KIND_SHORTCUT]: packageName/userSerial = owning app, shortcutId, title = cached label, className "".
 * swipeUpAction/swipeDownAction/tapAction hold `GestureAction.encode` strings (null = default).
 * widgetPadding holds a `PaddingSize` name for widgets (null = follow the global setting).
 */
@Entity(
    tableName = "workspace_items",
    indices = [Index(value = ["container", "page"])],
)
data class WorkspaceItemEntity(
    @PrimaryKey val id: String,
    /** [CONTAINER_DESKTOP] or [CONTAINER_DOCK]. */
    val container: String,
    val page: Int,
    val x2: Int,
    val y2: Int,
    val width2: Int,
    val height2: Int,
    /** [KIND_APP] in M0. */
    val kind: String,
    val packageName: String,
    val className: String,
    val userSerial: Long,
    val createdAt: Long,
    val updatedAt: Long,
    // --- Added in schema version 2 ---
    val title: String? = null,
    val appWidgetId: Int? = null,
    val providerPackage: String? = null,
    val providerClass: String? = null,
    val shortcutId: String? = null,
    val swipeUpAction: String? = null,
    val swipeDownAction: String? = null,
    val tapAction: String? = null,
    @ColumnInfo(defaultValue = "0") val zIndex: Int = 0,
    val shortcutIconPack: String? = null,
    val shortcutIconDrawable: String? = null,
    // --- Added in schema version 4 ---
    /** [KIND_WIDGET] only: a `PaddingSize` name overriding the global widget inner space; null = default. */
    val widgetPadding: String? = null,
) {
    companion object {
        const val CONTAINER_DESKTOP = "desktop"
        const val CONTAINER_DOCK = "dock"
        const val KIND_APP = "app"
        const val KIND_FOLDER = "folder"
        const val KIND_WIDGET = "widget"
        const val KIND_SHORTCUT = "shortcut"
    }
}

/**
 * One entry of a folder ([WorkspaceItemEntity.KIND_FOLDER] row [folderId]), ordered by [rank].
 * [kind] is [WorkspaceItemEntity.KIND_APP] or [WorkspaceItemEntity.KIND_SHORTCUT] with the same
 * column meaning as on the workspace row. No foreign key: the repository deletes entries with their
 * folder inside the same transaction and snapshots include them, so undo restores both.
 */
@Entity(tableName = "folder_items", indices = [Index(value = ["folderId"])])
data class FolderItemEntity(
    @PrimaryKey val id: String,
    val folderId: String,
    val rank: Int,
    val kind: String,
    val packageName: String,
    val className: String,
    val userSerial: Long,
    val shortcutId: String? = null,
    /** Cached shortcut label. */
    val title: String? = null,
    val shortcutIconPack: String? = null,
    val shortcutIconDrawable: String? = null,
)

/** Per-app customization, keyed by component and profile. Rows equal to the default are deleted. */
@Entity(tableName = "app_customizations", primaryKeys = ["packageName", "className", "userSerial"])
data class AppCustomizationEntity(
    val packageName: String,
    val className: String,
    val userSerial: Long,
    @ColumnInfo(defaultValue = "0") val hidden: Boolean = false,
    val customLabel: String? = null,
    val iconPackPackage: String? = null,
    val iconDrawableName: String? = null,
    val swipeUpAction: String? = null,
    val swipeDownAction: String? = null,
)

/** A drawer group (tab). [sortMode] is a `DrawerSortMode` name. */
@Entity(tableName = "drawer_groups")
data class DrawerGroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val rank: Int,
    val sortMode: String,
)

/** Membership of an app in a drawer group; [rank] orders members for manual sort. */
@Entity(
    tableName = "drawer_group_members",
    primaryKeys = ["groupId", "packageName", "className", "userSerial"],
)
data class DrawerGroupMemberEntity(
    val groupId: String,
    val packageName: String,
    val className: String,
    val userSerial: Long,
    @ColumnInfo(defaultValue = "0") val rank: Int = 0,
)

/** Small key/value store for workspace-wide facts such as page count and one-shot flags. */
@Entity(tableName = "workspace_meta")
data class WorkspaceMetaEntity(
    @PrimaryKey val key: String,
    val value: String,
)

/** Bounded undo/recovery snapshot of the whole workspace taken before an edit. */
@Entity(tableName = "workspace_snapshots")
data class WorkspaceSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    val reason: String,
    @ColumnInfo(name = "payload") val payloadJson: String,
)
