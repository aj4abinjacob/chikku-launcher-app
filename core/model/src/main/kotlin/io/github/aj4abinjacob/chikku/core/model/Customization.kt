package io.github.aj4abinjacob.chikku.core.model

import kotlinx.coroutines.flow.Flow

/**
 * Per-app user customization, keyed by [key] (component + profile, never package alone).
 * Hiding is cosmetic: it removes the app from the drawer and search only and is not app security.
 */
data class AppCustomization(
    val key: AppKey,
    val hidden: Boolean = false,
    val customLabel: String? = null,
    /** Icon override: drawable [iconDrawableName] from icon pack [iconPackPackage]. */
    val iconPackPackage: String? = null,
    val iconDrawableName: String? = null,
    /** Swipe actions on this app's icon wherever it appears (workspace item actions take precedence). */
    val swipeUpAction: GestureAction? = null,
    val swipeDownAction: GestureAction? = null,
) {
    /** True when nothing differs from the default, so the row can be deleted. */
    val isDefault: Boolean
        get() = !hidden && customLabel == null && iconPackPackage == null && iconDrawableName == null &&
            swipeUpAction == null && swipeDownAction == null
}

/** Storage-backed customizations. Writes return false on storage failure and change nothing. */
interface AppCustomizationRepository {
    /** All customizations keyed by app; emits again after every change. */
    val customizations: Flow<Map<AppKey, AppCustomization>>

    suspend fun get(key: AppKey): AppCustomization?

    /** Applies [transform] to the current (or default) customization; a default result deletes the row. */
    suspend fun update(key: AppKey, transform: (AppCustomization) -> AppCustomization): Boolean

    suspend fun setHidden(key: AppKey, hidden: Boolean): Boolean = update(key) { it.copy(hidden = hidden) }

    suspend fun clear(key: AppKey): Boolean

    /** Everything, for backup. Null when storage could not be read. */
    suspend fun all(): List<AppCustomization>?

    /** Replaces everything (restore) in one transaction. */
    suspend fun replaceAll(customizations: List<AppCustomization>): Boolean
}

enum class DrawerSortMode { ALPHABETICAL, MANUAL }

/** A user-defined drawer group (tab/folder of similar apps). [members] is ordered for MANUAL sort. */
data class DrawerGroup(
    val id: String,
    val name: String,
    val rank: Int,
    val sortMode: DrawerSortMode = DrawerSortMode.ALPHABETICAL,
    val members: List<AppKey> = emptyList(),
)

/** Storage-backed drawer groups, ordered by rank. Writes return false/null on failure and change nothing. */
interface DrawerGroupRepository {
    val groups: Flow<List<DrawerGroup>>

    /** Appends a group and returns its id. */
    suspend fun createGroup(name: String, sortMode: DrawerSortMode = DrawerSortMode.ALPHABETICAL): String?

    suspend fun renameGroup(groupId: String, name: String): Boolean

    suspend fun setSortMode(groupId: String, sortMode: DrawerSortMode): Boolean

    suspend fun deleteGroup(groupId: String): Boolean

    /** [orderedGroupIds] must be a permutation of all group ids. */
    suspend fun reorderGroups(orderedGroupIds: List<String>): Boolean

    /** Replaces the group's members (duplicates dropped, order kept). */
    suspend fun setMembers(groupId: String, members: List<AppKey>): Boolean

    suspend fun addMember(groupId: String, key: AppKey): Boolean

    suspend fun removeMember(groupId: String, key: AppKey): Boolean

    /** Everything, for backup. Null when storage could not be read. */
    suspend fun all(): List<DrawerGroup>?

    /** Replaces everything (restore) in one transaction. */
    suspend fun replaceAll(groups: List<DrawerGroup>): Boolean
}
