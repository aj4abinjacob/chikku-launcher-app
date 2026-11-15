package io.github.aj4abinjacob.chikku.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface WorkspaceDao {
    @Query("SELECT * FROM workspace_items ORDER BY container, page, y2, x2, id")
    suspend fun items(): List<WorkspaceItemEntity>

    @Query("SELECT * FROM workspace_items WHERE id = :id")
    suspend fun item(id: String): WorkspaceItemEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertItem(item: WorkspaceItemEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertItems(items: List<WorkspaceItemEntity>)

    @Update
    suspend fun updateItem(item: WorkspaceItemEntity): Int

    @Query("DELETE FROM workspace_items WHERE id = :id")
    suspend fun deleteItem(id: String): Int

    @Query("DELETE FROM workspace_items")
    suspend fun deleteAllItems()

    @Query("SELECT value FROM workspace_meta WHERE `key` = :key")
    suspend fun meta(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putMeta(meta: WorkspaceMetaEntity)

    @Query("DELETE FROM workspace_meta WHERE `key` = :key")
    suspend fun deleteMeta(key: String)

    @Query("DELETE FROM workspace_snapshots")
    suspend fun deleteAllSnapshots()

    @Insert
    suspend fun insertSnapshot(snapshot: WorkspaceSnapshotEntity): Long

    @Query("SELECT * FROM workspace_snapshots ORDER BY id DESC LIMIT 1")
    suspend fun latestSnapshot(): WorkspaceSnapshotEntity?

    @Query("SELECT * FROM workspace_snapshots ORDER BY id DESC")
    suspend fun retainedSnapshots(): List<WorkspaceSnapshotEntity>

    @Query("SELECT COUNT(*) FROM workspace_snapshots")
    suspend fun snapshotCount(): Int

    @Query("DELETE FROM workspace_snapshots WHERE id = :id")
    suspend fun deleteSnapshot(id: Long)

    @Query(
        "DELETE FROM workspace_snapshots WHERE id NOT IN " +
            "(SELECT id FROM workspace_snapshots ORDER BY id DESC LIMIT :keep)",
    )
    suspend fun trimSnapshots(keep: Int)

    // --- Folder entries (schema v2) ---

    @Query("SELECT * FROM folder_items ORDER BY folderId, rank, id")
    suspend fun folderItems(): List<FolderItemEntity>

    @Query("SELECT * FROM folder_items WHERE folderId = :folderId ORDER BY rank, id")
    suspend fun folderItems(folderId: String): List<FolderItemEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertFolderItems(items: List<FolderItemEntity>)

    @Update
    suspend fun updateFolderItems(items: List<FolderItemEntity>): Int

    @Query("DELETE FROM folder_items WHERE folderId = :folderId")
    suspend fun deleteFolderItemsOf(folderId: String): Int

    @Query("DELETE FROM folder_items")
    suspend fun deleteAllFolderItems()
}
