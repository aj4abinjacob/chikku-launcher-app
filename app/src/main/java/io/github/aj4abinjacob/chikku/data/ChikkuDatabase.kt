package io.github.aj4abinjacob.chikku.data

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Workspace database. Every schema change needs an explicit migration with a fixture test; this
 * project never uses destructive migration fallbacks (docs/DATA_MODEL.md "Migration strategy").
 */
@Database(
    entities = [
        WorkspaceItemEntity::class,
        WorkspaceMetaEntity::class,
        WorkspaceSnapshotEntity::class,
        FolderItemEntity::class,
        AppCustomizationEntity::class,
        DrawerGroupEntity::class,
        DrawerGroupMemberEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class ChikkuDatabase : RoomDatabase() {
    abstract fun workspaceDao(): WorkspaceDao
    abstract fun customizationDao(): CustomizationDao
    abstract fun drawerGroupDao(): DrawerGroupDao

    companion object {
        const val NAME = "chikku-workspace.db"
    }
}
