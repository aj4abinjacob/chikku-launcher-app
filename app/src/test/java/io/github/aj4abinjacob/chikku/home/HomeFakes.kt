package io.github.aj4abinjacob.chikku.home

import android.app.Application
import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import io.github.aj4abinjacob.chikku.applock.AppLockManager
import io.github.aj4abinjacob.chikku.applock.AppLockStore
import io.github.aj4abinjacob.chikku.applock.StoredAppLock
import io.github.aj4abinjacob.chikku.core.model.AppCustomization
import io.github.aj4abinjacob.chikku.core.model.AppCustomizationRepository
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.AppSearcher
import io.github.aj4abinjacob.chikku.core.model.AppsRepository
import io.github.aj4abinjacob.chikku.core.model.AppsState
import io.github.aj4abinjacob.chikku.core.model.CellPlacement
import io.github.aj4abinjacob.chikku.core.model.ComponentKey
import io.github.aj4abinjacob.chikku.core.model.DrawerGroup
import io.github.aj4abinjacob.chikku.core.model.DrawerGroupRepository
import io.github.aj4abinjacob.chikku.core.model.DrawerSortMode
import io.github.aj4abinjacob.chikku.core.model.EditResult
import io.github.aj4abinjacob.chikku.core.model.FolderSource
import io.github.aj4abinjacob.chikku.core.model.ItemActions
import io.github.aj4abinjacob.chikku.core.model.ItemTarget
import io.github.aj4abinjacob.chikku.core.model.LauncherApp
import io.github.aj4abinjacob.chikku.core.model.ProfileType
import io.github.aj4abinjacob.chikku.core.model.RejectReason
import io.github.aj4abinjacob.chikku.core.model.WorkspaceContent
import io.github.aj4abinjacob.chikku.core.model.WorkspaceRepository
import io.github.aj4abinjacob.chikku.core.model.WorkspaceState
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettingsCodec
import io.github.aj4abinjacob.chikku.core.model.settings.SettingsImportResult
import io.github.aj4abinjacob.chikku.core.model.settings.SettingsRepository
import io.github.aj4abinjacob.chikku.platform.AppLauncher
import io.github.aj4abinjacob.chikku.platform.HomeRoleController
import io.github.aj4abinjacob.chikku.platform.IconProvider
import io.github.aj4abinjacob.chikku.platform.LaunchResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart

fun app(label: String, pkg: String = "pkg.${label.lowercase()}", profile: ProfileType = ProfileType.PERSONAL) =
    LauncherApp(AppKey(pkg, "$pkg.Main", if (profile == ProfileType.WORK) 10L else 0L), label, profile)

class FakeAppsRepository(initial: AppsState = AppsState.Loading) : AppsRepository {
    val state = MutableStateFlow(initial)
    var refreshCount = 0
    override val apps: Flow<AppsState> = state
    override fun refresh() {
        refreshCount++
    }
}

/** Case-insensitive substring search that records every query it was asked to run. */
class RecordingSearcher : AppSearcher {
    val queries = mutableListOf<String>()
    override fun search(apps: List<LauncherApp>, query: String): List<LauncherApp> {
        queries += query
        return apps.filter { it.label.contains(query, ignoreCase = true) }
    }
}

class FakeWorkspaceRepository(initial: WorkspaceState = WorkspaceState.Loading) : WorkspaceRepository {
    val state = MutableStateFlow(initial)
    var activeCollectors = 0
    var totalSubscriptions = 0
    var nextResult: EditResult? = null
    val moves = mutableListOf<Pair<String, CellPlacement>>()
    val removals = mutableListOf<String>()
    val adds = mutableListOf<Pair<AppKey, CellPlacement?>>()
    var undoCount = 0

    override val workspace: Flow<WorkspaceState> = state
        .onStart {
            activeCollectors++
            totalSubscriptions++
        }
        .onCompletion { activeCollectors-- }

    override suspend fun addApp(key: AppKey, preferred: CellPlacement?): EditResult {
        adds += key to preferred
        return nextResult ?: EditResult.Applied("new")
    }

    override suspend fun moveItem(itemId: String, to: CellPlacement, allowOverlap: Boolean): EditResult {
        moves += itemId to to
        return nextResult ?: EditResult.Applied(itemId)
    }

    override suspend fun removeItem(itemId: String): EditResult {
        removals += itemId
        return nextResult ?: EditResult.Applied(itemId)
    }

    override suspend fun undoLastEdit(): Boolean {
        undoCount++
        return true
    }

    override suspend fun addPage(): Int = 1

    /** Every call to a method below is recorded by name, e.g. "createFolder". */
    val calls = mutableListOf<String>()

    private fun record(name: String, id: String): EditResult {
        calls += name
        return nextResult ?: EditResult.Applied(id)
    }

    override suspend fun createFolder(anchorItemId: String, other: FolderSource, title: String) =
        record("createFolder", "folder")

    override suspend fun addToFolder(folderId: String, source: FolderSource, rank: Int?) = record("addToFolder", folderId)

    override suspend fun removeFromFolder(folderId: String, entryId: String, to: CellPlacement?) =
        record("removeFromFolder", folderId)

    override suspend fun renameFolder(folderId: String, title: String) = record("renameFolder", folderId)

    override suspend fun reorderFolder(folderId: String, orderedEntryIds: List<String>) = record("reorderFolder", folderId)

    override suspend fun addWidget(appWidgetId: Int, provider: ComponentKey, placement: CellPlacement, allowOverlap: Boolean) =
        record("addWidget", "widget")

    override suspend fun updateWidgetId(itemId: String, appWidgetId: Int) = record("updateWidgetId", itemId)

    override suspend fun addShortcut(shortcut: ItemTarget.Shortcut, preferred: CellPlacement?) = record("addShortcut", "shortcut")

    override suspend fun resizeItem(itemId: String, spanX: Int, spanY: Int, allowOverlap: Boolean) = record("resizeItem", itemId)

    override suspend fun setItemActions(itemId: String, actions: ItemActions) = record("setItemActions", itemId)

    override suspend fun exportContent(): WorkspaceContent? =
        (state.value as? WorkspaceState.Ready)?.workspace?.let { WorkspaceContent(it.pageCount, it.items) }

    override suspend fun replaceAll(content: WorkspaceContent) = record("replaceAll", "")

    companion object {
        val Rejected = EditResult.Rejected(RejectReason.OCCUPIED)
    }
}

class FakeSettingsRepository(initial: LauncherSettings = LauncherSettings()) : SettingsRepository {
    val state = MutableStateFlow(initial)
    override val settings: Flow<LauncherSettings> = state
    override suspend fun update(transform: (LauncherSettings) -> LauncherSettings) {
        state.value = transform(state.value).coerced()
    }

    override suspend fun reset() {
        state.value = LauncherSettings()
    }

    override suspend fun exportJson(): String = LauncherSettingsCodec.encode(state.value)

    override suspend fun importJson(json: String): SettingsImportResult {
        val decoded = LauncherSettingsCodec.decodeOrNull(json) ?: return SettingsImportResult.Rejected("invalid")
        state.value = decoded
        return SettingsImportResult.Applied
    }
}

class FakeAppCustomizationRepository : AppCustomizationRepository {
    val state = MutableStateFlow<Map<AppKey, AppCustomization>>(emptyMap())
    override val customizations: Flow<Map<AppKey, AppCustomization>> = state
    override suspend fun get(key: AppKey): AppCustomization? = state.value[key]
    override suspend fun update(key: AppKey, transform: (AppCustomization) -> AppCustomization): Boolean {
        val next = transform(state.value[key] ?: AppCustomization(key)).copy(key = key)
        state.value = if (next.isDefault) state.value - key else state.value + (key to next)
        return true
    }

    override suspend fun clear(key: AppKey): Boolean {
        state.value = state.value - key
        return true
    }

    override suspend fun all(): List<AppCustomization> = state.value.values.toList()
    override suspend fun replaceAll(customizations: List<AppCustomization>): Boolean {
        state.value = customizations.filterNot { it.isDefault }.associateBy { it.key }
        return true
    }
}

class FakeDrawerGroupRepository : DrawerGroupRepository {
    val state = MutableStateFlow<List<DrawerGroup>>(emptyList())
    override val groups: Flow<List<DrawerGroup>> = state
    private var nextId = 0

    private fun change(groupId: String, transform: (DrawerGroup) -> DrawerGroup): Boolean {
        if (state.value.none { it.id == groupId }) return false
        state.value = state.value.map { if (it.id == groupId) transform(it) else it }
        return true
    }

    override suspend fun createGroup(name: String, sortMode: DrawerSortMode): String {
        val id = "group${nextId++}"
        state.value = state.value + DrawerGroup(id, name, state.value.size, sortMode)
        return id
    }

    override suspend fun renameGroup(groupId: String, name: String) = change(groupId) { it.copy(name = name) }
    override suspend fun setSortMode(groupId: String, sortMode: DrawerSortMode) = change(groupId) { it.copy(sortMode = sortMode) }
    override suspend fun deleteGroup(groupId: String): Boolean {
        val before = state.value.size
        state.value = state.value.filterNot { it.id == groupId }
        return state.value.size != before
    }

    override suspend fun reorderGroups(orderedGroupIds: List<String>): Boolean {
        val byId = state.value.associateBy { it.id }
        if (orderedGroupIds.toSet() != byId.keys || orderedGroupIds.size != byId.size) return false
        state.value = orderedGroupIds.mapIndexed { rank, id -> byId.getValue(id).copy(rank = rank) }
        return true
    }

    override suspend fun setMembers(groupId: String, members: List<AppKey>) = change(groupId) { it.copy(members = members.distinct()) }
    override suspend fun addMember(groupId: String, key: AppKey) =
        change(groupId) { if (key in it.members) it else it.copy(members = it.members + key) }

    override suspend fun removeMember(groupId: String, key: AppKey) = change(groupId) { it.copy(members = it.members - key) }
    override suspend fun all(): List<DrawerGroup> = state.value
    override suspend fun replaceAll(groups: List<DrawerGroup>): Boolean {
        state.value = groups
        return true
    }
}

class FakeAppLauncher : AppLauncher {
    val launched = mutableListOf<AppKey>()
    var result: LaunchResult = LaunchResult.Started
    override fun launch(key: AppKey, sourceBounds: Rect?): LaunchResult {
        launched += key
        return result
    }

    override fun openAppDetails(key: AppKey, sourceBounds: Rect?): LaunchResult = result
}

/** [AppLockStore] in memory; [failReads]/[failWrites] simulate storage errors. */
class InMemoryAppLockStore(
    var value: StoredAppLock = StoredAppLock(),
    var failReads: Boolean = false,
    var failWrites: Boolean = false,
) : AppLockStore {
    override suspend fun read(): StoredAppLock = if (failReads) throw java.io.IOException("unreadable") else value
    override suspend fun write(value: StoredAppLock): Boolean {
        if (failWrites) return false
        this.value = value
        return true
    }
}

class FakeIconProvider : IconProvider {
    override suspend fun icon(key: AppKey): Drawable = ColorDrawable(Color.GRAY)
    override fun invalidate(packageName: String) = Unit
}

class FakeHomeRoleController(var isDefault: Boolean = true) : HomeRoleController {
    override fun isDefaultHome(): Boolean = isDefault
}

class FakeHomeDependencies(
    override val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    override val appsRepository: FakeAppsRepository = FakeAppsRepository(),
    override val appSearcher: RecordingSearcher = RecordingSearcher(),
    override val workspaceRepository: FakeWorkspaceRepository = FakeWorkspaceRepository(),
    override val appLauncher: FakeAppLauncher = FakeAppLauncher(),
    override val iconProvider: IconProvider = FakeIconProvider(),
    override val homeRoleController: FakeHomeRoleController = FakeHomeRoleController(),
    override val corruptLayoutNotice: MutableStateFlow<Boolean> = MutableStateFlow(false),
    override val settingsRepository: FakeSettingsRepository = FakeSettingsRepository(),
    override val appCustomizationRepository: FakeAppCustomizationRepository = FakeAppCustomizationRepository(),
    override val drawerGroupRepository: FakeDrawerGroupRepository = FakeDrawerGroupRepository(),
    val appLockStore: InMemoryAppLockStore = InMemoryAppLockStore(),
    override val appLock: AppLockManager = AppLockManager(appLockStore, appScope) { 0L },
) : HomeDependencies {
    var acknowledgements = 0

    override suspend fun acknowledgeCorruptLayout() {
        acknowledgements++
        corruptLayoutNotice.value = false
    }

    override fun homeRoleRequestIntent(): Intent? = null
    override fun homeSettingsIntent(): Intent = Intent("android.settings.HOME_SETTINGS")
}

/**
 * Test Application used via `@Config(application = TestHomeApplication::class)`. It implements
 * [HomeDependenciesProvider], so `Context.homeDependencies()` returns these fakes instead of the
 * production container.
 */
class TestHomeApplication : Application(), HomeDependenciesProvider {
    override var homeDependencies: HomeDependencies = FakeHomeDependencies()
}
