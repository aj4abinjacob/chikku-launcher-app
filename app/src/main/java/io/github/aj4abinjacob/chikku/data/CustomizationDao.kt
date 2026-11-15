package io.github.aj4abinjacob.chikku.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomizationDao {
    @Query("SELECT * FROM app_customizations")
    fun observeAll(): Flow<List<AppCustomizationEntity>>

    @Query("SELECT * FROM app_customizations")
    suspend fun all(): List<AppCustomizationEntity>

    @Query("SELECT * FROM app_customizations WHERE packageName = :packageName AND className = :className AND userSerial = :userSerial")
    suspend fun get(packageName: String, className: String, userSerial: Long): AppCustomizationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AppCustomizationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<AppCustomizationEntity>)

    @Query("DELETE FROM app_customizations WHERE packageName = :packageName AND className = :className AND userSerial = :userSerial")
    suspend fun delete(packageName: String, className: String, userSerial: Long): Int

    @Query("DELETE FROM app_customizations")
    suspend fun deleteAll()
}

@Dao
interface DrawerGroupDao {
    @Query("SELECT * FROM drawer_groups ORDER BY rank, id")
    fun observeGroups(): Flow<List<DrawerGroupEntity>>

    @Query("SELECT * FROM drawer_groups ORDER BY rank, id")
    suspend fun groups(): List<DrawerGroupEntity>

    @Query("SELECT * FROM drawer_group_members ORDER BY groupId, rank")
    fun observeMembers(): Flow<List<DrawerGroupMemberEntity>>

    @Query("SELECT * FROM drawer_group_members ORDER BY groupId, rank")
    suspend fun members(): List<DrawerGroupMemberEntity>

    @Query("SELECT * FROM drawer_group_members WHERE groupId = :groupId ORDER BY rank")
    suspend fun membersOf(groupId: String): List<DrawerGroupMemberEntity>

    @Query("SELECT * FROM drawer_groups WHERE id = :id")
    suspend fun group(id: String): DrawerGroupEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGroup(group: DrawerGroupEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGroups(groups: List<DrawerGroupEntity>)

    @Query("DELETE FROM drawer_groups WHERE id = :id")
    suspend fun deleteGroup(id: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMembers(members: List<DrawerGroupMemberEntity>)

    @Query("DELETE FROM drawer_group_members WHERE groupId = :groupId")
    suspend fun deleteMembersOf(groupId: String)

    @Query(
        "DELETE FROM drawer_group_members WHERE groupId = :groupId AND packageName = :packageName " +
            "AND className = :className AND userSerial = :userSerial",
    )
    suspend fun deleteMember(groupId: String, packageName: String, className: String, userSerial: Long): Int

    @Query("DELETE FROM drawer_groups")
    suspend fun deleteAllGroups()

    @Query("DELETE FROM drawer_group_members")
    suspend fun deleteAllMembers()
}
