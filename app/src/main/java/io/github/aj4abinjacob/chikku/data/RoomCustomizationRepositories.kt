package io.github.aj4abinjacob.chikku.data

import androidx.room.withTransaction
import io.github.aj4abinjacob.chikku.core.model.AppCustomization
import io.github.aj4abinjacob.chikku.core.model.AppCustomizationRepository
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.DrawerGroup
import io.github.aj4abinjacob.chikku.core.model.DrawerGroupRepository
import io.github.aj4abinjacob.chikku.core.model.DrawerSortMode
import io.github.aj4abinjacob.chikku.core.model.GestureAction
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Runs [block] in one transaction on [io]; storage failures return [onFailure] and roll back. */
private suspend fun <T> ChikkuDatabase.guarded(io: CoroutineDispatcher, onFailure: T, block: suspend () -> T): T = try {
    withContext(io) { withTransaction { block() } }
} catch (e: CancellationException) {
    throw e
} catch (_: Exception) {
    onFailure
}

/** Room-backed [AppCustomizationRepository]. A read failure emits an empty map rather than throwing. */
class RoomAppCustomizationRepository(
    private val db: ChikkuDatabase,
    private val io: CoroutineDispatcher,
) : AppCustomizationRepository {
    private val dao = db.customizationDao()

    override val customizations: Flow<Map<AppKey, AppCustomization>> = dao.observeAll()
        .map { rows -> rows.mapNotNull { it.toDomainOrNull() }.associateBy { it.key } }
        .distinctUntilChanged()
        .catch { emit(emptyMap()) }
        .flowOn(io)

    override suspend fun get(key: AppKey): AppCustomization? = db.guarded(io, null) {
        dao.get(key.packageName, key.className, key.userSerial)?.toDomainOrNull()
    }

    override suspend fun update(key: AppKey, transform: (AppCustomization) -> AppCustomization): Boolean =
        db.guarded(io, false) {
            val current = dao.get(key.packageName, key.className, key.userSerial)?.toDomainOrNull() ?: AppCustomization(key)
            val next = transform(current).copy(key = key)
            if (next.isDefault) dao.delete(key.packageName, key.className, key.userSerial) else dao.upsert(next.toEntity())
            true
        }

    override suspend fun clear(key: AppKey): Boolean = db.guarded(io, false) {
        dao.delete(key.packageName, key.className, key.userSerial)
        true
    }

    override suspend fun all(): List<AppCustomization>? = db.guarded(io, null) { dao.all().mapNotNull { it.toDomainOrNull() } }

    override suspend fun replaceAll(customizations: List<AppCustomization>): Boolean = db.guarded(io, false) {
        dao.deleteAll()
        dao.upsertAll(customizations.filterNot { it.isDefault }.map { it.toEntity() })
        true
    }
}

private fun AppCustomizationEntity.toDomainOrNull(): AppCustomization? = runCatching {
    AppCustomization(
        key = AppKey(packageName, className, userSerial),
        hidden = hidden,
        customLabel = customLabel,
        iconPackPackage = iconPackPackage,
        iconDrawableName = iconDrawableName,
        swipeUpAction = GestureAction.decode(swipeUpAction),
        swipeDownAction = GestureAction.decode(swipeDownAction),
    )
}.getOrNull()

private fun AppCustomization.toEntity() = AppCustomizationEntity(
    packageName = key.packageName,
    className = key.className,
    userSerial = key.userSerial,
    hidden = hidden,
    customLabel = customLabel,
    iconPackPackage = iconPackPackage,
    iconDrawableName = iconDrawableName,
    swipeUpAction = swipeUpAction?.let(GestureAction::encode),
    swipeDownAction = swipeDownAction?.let(GestureAction::encode),
)

/** Room-backed [DrawerGroupRepository]. */
class RoomDrawerGroupRepository(
    private val db: ChikkuDatabase,
    private val io: CoroutineDispatcher,
) : DrawerGroupRepository {
    private val dao = db.drawerGroupDao()

    override val groups: Flow<List<DrawerGroup>> = combine(dao.observeGroups(), dao.observeMembers(), ::assemble)
        .distinctUntilChanged()
        .catch { emit(emptyList()) }
        .flowOn(io)

    override suspend fun createGroup(name: String, sortMode: DrawerSortMode): String? = db.guarded(io, null) {
        val id = UUID.randomUUID().toString()
        val rank = (dao.groups().maxOfOrNull { it.rank } ?: -1) + 1
        dao.upsertGroup(DrawerGroupEntity(id, name, rank, sortMode.name))
        id
    }

    override suspend fun renameGroup(groupId: String, name: String): Boolean = modifyGroup(groupId) { it.copy(name = name) }

    override suspend fun setSortMode(groupId: String, sortMode: DrawerSortMode): Boolean =
        modifyGroup(groupId) { it.copy(sortMode = sortMode.name) }

    override suspend fun deleteGroup(groupId: String): Boolean = db.guarded(io, false) {
        dao.deleteMembersOf(groupId)
        dao.deleteGroup(groupId) > 0
    }

    override suspend fun reorderGroups(orderedGroupIds: List<String>): Boolean = db.guarded(io, false) {
        val groups = dao.groups().associateBy { it.id }
        if (orderedGroupIds.size != groups.size || orderedGroupIds.toSet() != groups.keys) return@guarded false
        dao.upsertGroups(orderedGroupIds.mapIndexed { rank, id -> groups.getValue(id).copy(rank = rank) })
        true
    }

    override suspend fun setMembers(groupId: String, members: List<AppKey>): Boolean = db.guarded(io, false) {
        if (dao.group(groupId) == null) return@guarded false
        dao.deleteMembersOf(groupId)
        dao.insertMembers(members.distinct().mapIndexed { rank, key -> key.member(groupId, rank) })
        true
    }

    override suspend fun addMember(groupId: String, key: AppKey): Boolean = db.guarded(io, false) {
        if (dao.group(groupId) == null) return@guarded false
        val existing = dao.membersOf(groupId)
        if (existing.none { it.toKey() == key }) {
            dao.insertMembers(listOf(key.member(groupId, (existing.maxOfOrNull { it.rank } ?: -1) + 1)))
        }
        true
    }

    override suspend fun removeMember(groupId: String, key: AppKey): Boolean = db.guarded(io, false) {
        dao.deleteMember(groupId, key.packageName, key.className, key.userSerial)
        true
    }

    override suspend fun all(): List<DrawerGroup>? = db.guarded(io, null) { assemble(dao.groups(), dao.members()) }

    override suspend fun replaceAll(groups: List<DrawerGroup>): Boolean = db.guarded(io, false) {
        dao.deleteAllMembers()
        dao.deleteAllGroups()
        dao.upsertGroups(groups.mapIndexed { rank, g -> DrawerGroupEntity(g.id, g.name, rank, g.sortMode.name) })
        dao.insertMembers(groups.flatMap { g -> g.members.distinct().mapIndexed { rank, key -> key.member(g.id, rank) } })
        true
    }

    private suspend fun modifyGroup(groupId: String, change: (DrawerGroupEntity) -> DrawerGroupEntity): Boolean =
        db.guarded(io, false) {
            val group = dao.group(groupId) ?: return@guarded false
            dao.upsertGroup(change(group))
            true
        }

    private fun assemble(groups: List<DrawerGroupEntity>, members: List<DrawerGroupMemberEntity>): List<DrawerGroup> {
        val byGroup = members.groupBy { it.groupId }
        return groups.map { g ->
            DrawerGroup(
                id = g.id,
                name = g.name,
                rank = g.rank,
                sortMode = DrawerSortMode.entries.firstOrNull { it.name == g.sortMode } ?: DrawerSortMode.ALPHABETICAL,
                members = byGroup[g.id].orEmpty().sortedBy { it.rank }.mapNotNull { runCatching { it.toKey() }.getOrNull() },
            )
        }
    }
}

private fun AppKey.member(groupId: String, rank: Int) =
    DrawerGroupMemberEntity(groupId, packageName, className, userSerial, rank)

private fun DrawerGroupMemberEntity.toKey() = AppKey(packageName, className, userSerial)
