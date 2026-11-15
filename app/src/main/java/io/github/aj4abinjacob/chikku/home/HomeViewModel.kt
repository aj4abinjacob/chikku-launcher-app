package io.github.aj4abinjacob.chikku.home

import android.graphics.Rect
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.AppsState
import io.github.aj4abinjacob.chikku.core.model.CellPlacement
import io.github.aj4abinjacob.chikku.core.model.EditResult
import io.github.aj4abinjacob.chikku.core.model.RejectReason
import io.github.aj4abinjacob.chikku.core.model.WorkspaceRepository
import io.github.aj4abinjacob.chikku.core.model.WorkspaceState
import io.github.aj4abinjacob.chikku.core.model.Workspace
import io.github.aj4abinjacob.chikku.core.model.search.FolderSearch
import io.github.aj4abinjacob.chikku.core.model.drawer.DrawerModel
import io.github.aj4abinjacob.chikku.core.model.search.CustomLabelSearch
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.platform.LaunchResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One-shot UI feedback that must not be replayed after recreation. */
sealed interface HomeEvent {
    data object ItemRemoved : HomeEvent
    data object PageRemoved : HomeEvent
    data class PageAdded(val page: Int) : HomeEvent
    data class EditRejected(val reason: RejectReason) : HomeEvent
    data object AddedToHome : HomeEvent
    data object UndoUnavailable : HomeEvent
    data object AppLaunched : HomeEvent
    data object LaunchUnavailable : HomeEvent

    /** The app is locked: HOME asks the system to authenticate, then runs [retry]. */
    class AppLocked(val locked: LaunchResult.Locked, val retry: () -> Unit) : HomeEvent

    /** An edit was refused because the home-screen layout is locked (G17). */
    data object LayoutLocked : HomeEvent
}

/**
 * Combines apps, workspace, search query and startup state into one [HomeUiState]. Owns no
 * authoritative positions: edits go to [io.github.aj4abinjacob.chikku.core.model.WorkspaceRepository]
 * and the UI re-renders from what it emits.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class HomeViewModel(
    private val deps: HomeDependencies,
    private val savedState: SavedStateHandle,
    private val startupGuard: StartupGuard,
    /** Result of [StartupGuard.recordStartup] for this start, or null if not recorded (recreation). */
    private val initialSafeMode: Boolean?,
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val events = Channel<HomeEvent>(Channel.BUFFERED)
    val homeEvents: Flow<HomeEvent> = events.receiveAsFlow()

    private val query: StateFlow<String> = savedState.getStateFlow(KEY_QUERY, "")
    private val drawerOpen: StateFlow<Boolean> = savedState.getStateFlow(KEY_DRAWER_OPEN, false)
    private val bannerDismissed: StateFlow<Boolean> = savedState.getStateFlow(KEY_BANNER_DISMISSED, false)

    private val safeMode = MutableStateFlow<Boolean?>(null)
    private val workspaceRetry = MutableStateFlow(0)
    private val isDefaultHome = MutableStateFlow<Boolean?>(null)

    private val apps: StateFlow<AppsState> = deps.appsRepository.apps
        .catch { emit(AppsState.Failed(it.javaClass.simpleName)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AppsState.Loading)

    private val workspaceState: Flow<WorkspaceState?> =
        combine(safeMode, workspaceRetry) { safe, attempt -> safe to attempt }
            .flatMapLatest { (safe, _) ->
                when (safe) {
                    null -> flowOf(WorkspaceState.Loading)
                    true -> flowOf(null) // safe mode: do not touch the layout at all
                    false -> deps.workspaceRepository.workspace
                        .catch { emit(WorkspaceState.Failed(it.javaClass.simpleName)) }
                }
            }

    /**
     * The resolved workspace together with the snapshot it was built from. Shared so folder search
     * validation ([searchableFolderItems]) reuses this build instead of resolving the workspace again.
     */
    private val resolvedWorkspace: Flow<ResolvedWorkspace> = combine(workspaceState, apps) { ws, appsState ->
        val ui = when (ws) {
            null -> WorkspaceUi.Disabled
            WorkspaceState.Loading -> WorkspaceUi.Loading
            is WorkspaceState.Failed -> WorkspaceUi.Failed(ws.reason)
            is WorkspaceState.Ready -> buildWorkspaceUi(ws.workspace, appsState)
        }
        ResolvedWorkspace((ws as? WorkspaceState.Ready)?.workspace, appsState, ui)
    }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), replay = 1)

    private val workspaceUi: Flow<WorkspaceUi> = resolvedWorkspace.map { it.ui }.distinctUntilChanged()

    /**
     * Folders that a search result may open, keyed by item ID, resolved from the same snapshot as
     * [uiState]'s workspace; null until the workspace and the app list are loaded. Cold: only HOME's
     * folder-search request collects it.
     */
    internal val searchableFolderItems: Flow<Map<String, HomeItemUi>?> =
        combine(resolvedWorkspace, deps.appCustomizationRepository.customizations.map(DrawerModel::hiddenKeys)) { resolved, hidden ->
            searchableFolders(resolved.stored, resolved.apps, resolved.ui, hidden)
        }.catch { emit(emptyMap()) }.flowOn(computeDispatcher)

    /** Apps for the drawer and search: hidden apps removed (cosmetic only; the workspace keeps them). */
    private val drawerApps: Flow<AppsState> = combine(
        apps,
        deps.appCustomizationRepository.customizations
            .map(DrawerModel::hiddenKeys)
            .catch { emit(emptySet()) }
            .distinctUntilChanged(),
        DrawerModel::withoutHidden,
    )

    /** Custom app names (style settings "Edit"), so the drawer search finds renamed apps. */
    private val customLabels: Flow<Map<AppKey, String>> = deps.appCustomizationRepository.customizations
        .map(CustomLabelSearch::customLabels)
        .catch { emit(emptyMap()) }
        .distinctUntilChanged()

    private val drawerUi: Flow<Pair<String, DrawerUi>> =
        combine(
            drawerApps,
            query.debounce { if (it.isBlank()) 0L else SEARCH_DEBOUNCE_MS }.distinctUntilChanged(),
            customLabels,
        ) { appsState, q, labels -> Triple(appsState, q, labels) }
            .mapLatest { (appsState, q, labels) -> q to drawerContent(appsState, q, labels) } // newest query wins
            .distinctUntilChanged()

    /** Preferences for every HOME surface; feature code reads `uiState.settings`. */
    val settings: StateFlow<LauncherSettings> = deps.settingsRepository.settings
        .catch { emit(LauncherSettings()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LauncherSettings())

    private val chrome =
        combine(safeMode, deps.corruptLayoutNotice, isDefaultHome, bannerDismissed, settings) { safe, notice, isDefault, dismissed, prefs ->
            Chrome(safe, notice, isDefault == false && !dismissed, prefs)
        }

    // Once HOME is shown, keep its model current until this ViewModel is cleared. Stopping this
    // subscription in the background restarts Room and LauncherApps with Loading on return;
    // UNKNOWN app availability briefly replaces folder covers with previews and redraws icons.
    // Continuous observation also processes package/profile invalidations while HOME is hidden.
    val uiState: StateFlow<HomeUiState> =
        combine(workspaceUi, drawerUi, query, drawerOpen, chrome) { ws, drawer, q, open, chrome ->
            HomeUiState(
                workspace = ws,
                // A query change keeps the previous content until the new result is ready:
                // Loading is only for the app list itself, and local search must not blank the grid.
                drawer = drawer.second,
                drawerQuery = drawer.first,
                query = q,
                drawerOpen = open,
                safeMode = chrome.safeMode,
                showCorruptLayoutNotice = chrome.corruptNotice,
                showDefaultHomeBanner = chrome.showBanner,
                settings = chrome.settings,
            )
        }.stateIn(viewModelScope, SharingStarted.Lazily, HomeUiState(query = query.value, drawerOpen = drawerOpen.value))

    init {
        viewModelScope.launch {
            safeMode.value = try {
                initialSafeMode ?: startupGuard.isSafeMode()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false // the guard itself must never block HOME
            }
        }
    }

    private suspend fun drawerContent(appsState: AppsState, q: String, customLabels: Map<AppKey, String>): DrawerUi = when (appsState) {
        AppsState.Loading -> DrawerUi.Loading
        is AppsState.Failed -> DrawerUi.Failed(appsState.reason)
        is AppsState.Loaded -> when {
            appsState.apps.isEmpty() -> DrawerUi.NoApps
            q.isBlank() -> DrawerUi.Apps(appsState.apps)
            else -> {
                val results = withContext(computeDispatcher) {
                    try {
                        CustomLabelSearch.search(deps.appSearcher, appsState.apps, q.trim(), customLabels)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        emptyList()
                    }
                }
                if (results.isEmpty()) DrawerUi.NoMatches else DrawerUi.Apps(results)
            }
        }
    }

    // --- Drawer and search -------------------------------------------------------------------

    fun setQuery(value: String) {
        savedState[KEY_QUERY] = value
    }

    fun openDrawer() {
        savedState[KEY_DRAWER_OPEN] = true
    }

    fun closeDrawer() {
        savedState[KEY_DRAWER_OPEN] = false
        savedState[KEY_QUERY] = ""
    }

    /** First result of the current search, for the IME "go" action. */
    fun firstResult(): AppKey? = if (isDrawerResultCurrent()) {
        (uiState.value.drawer as? DrawerUi.Apps)?.apps?.firstOrNull()?.key
    } else null

    /** Also gates the IME no-match handoff while debounce/computation is pending. */
    fun isDrawerResultCurrent(): Boolean =
        uiState.value.drawerQuery == query.value && uiState.value.drawer != DrawerUi.Loading

    fun retryApps() = deps.appsRepository.refresh()

    // --- Launching -----------------------------------------------------------------------------

    fun launchApp(key: AppKey, sourceBounds: Rect?) {
        val result = try {
            deps.appLauncher.launch(key, sourceBounds)
        } catch (_: Exception) {
            LaunchResult.Unavailable(LaunchResult.Reason.UNKNOWN)
        }
        when (result) {
            LaunchResult.Started -> events.trySend(HomeEvent.AppLaunched)
            is LaunchResult.Locked -> events.trySend(HomeEvent.AppLocked(result) { launchApp(key, sourceBounds) })
            is LaunchResult.Unavailable -> events.trySend(HomeEvent.LaunchUnavailable)
        }
    }

    fun openAppInfo(key: AppKey, sourceBounds: Rect?) {
        val result = try {
            deps.appLauncher.openAppDetails(key, sourceBounds)
        } catch (_: Exception) {
            LaunchResult.Unavailable(LaunchResult.Reason.UNKNOWN)
        }
        when (result) {
            LaunchResult.Started -> Unit
            is LaunchResult.Locked -> events.trySend(HomeEvent.AppLocked(result) { openAppInfo(key, sourceBounds) })
            is LaunchResult.Unavailable -> events.trySend(HomeEvent.LaunchUnavailable)
        }
    }

    // --- Workspace edits (run in the app scope so a configuration change cannot cancel them) ---

    fun moveItem(itemId: String, to: CellPlacement) {
        if (rejectIfLayoutLocked()) return
        edit(onApplied = null) { deps.workspaceRepository.moveItem(itemId, to, allowOverlap = settings.value.home.allowWidgetOverlap) }
    }

    fun removeItem(itemId: String) {
        if (rejectIfLayoutLocked()) return
        edit(onApplied = HomeEvent.ItemRemoved) { deps.workspaceRepository.removeItem(itemId) }
    }

    fun addApp(key: AppKey, preferred: CellPlacement?) {
        if (rejectIfLayoutLocked()) return
        edit(onApplied = HomeEvent.AddedToHome) { deps.workspaceRepository.addApp(key, preferred) }
    }

    /**
     * Layout lock (G17): true, and a [HomeEvent.LayoutLocked] message, when the layout is locked.
     * Every workspace edit (move, add, remove, resize, folder and widget changes) checks this first.
     */
    fun rejectIfLayoutLocked(): Boolean {
        if (!settings.value.home.layoutLocked) return false
        events.trySend(HomeEvent.LayoutLocked)
        return true
    }

    /** Any other workspace edit (folders, …), with the same error and undo feedback as the built-in ones. */
    fun editWorkspace(onApplied: HomeEvent? = null, block: suspend (WorkspaceRepository) -> EditResult) =
        edit(onApplied) { block(deps.workspaceRepository) }

    /** Adds an empty page after the last one; HOME then slides to it and offers undo. */
    fun addPage() {
        if (rejectIfLayoutLocked()) return
        deps.appScope.launch {
            val page = try {
                deps.workspaceRepository.addPage()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                -1
            }
            events.trySend(if (page >= 0) HomeEvent.PageAdded(page) else HomeEvent.EditRejected(RejectReason.STORAGE_ERROR))
        }
    }

    /**
     * Removes a desktop page, with the same undo bar as removing an item. A page with items goes only
     * when [deleteItems] is true (the caller has confirmed); one undo brings the page and items back.
     */
    fun removePage(page: Int, deleteItems: Boolean = false) {
        if (rejectIfLayoutLocked()) return
        edit(onApplied = HomeEvent.PageRemoved) {
            deps.workspaceRepository.removePage(page, deleteItems)
        }
    }

    fun undoLastEdit() {
        deps.appScope.launch {
            val undone = try {
                deps.workspaceRepository.undoLastEdit()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
            if (!undone) events.trySend(HomeEvent.UndoUnavailable)
        }
    }

    private fun edit(onApplied: HomeEvent?, block: suspend () -> EditResult) {
        deps.appScope.launch {
            val result = try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                EditResult.Rejected(RejectReason.STORAGE_ERROR)
            }
            when (result) {
                is EditResult.Applied -> onApplied?.let { events.trySend(it) }
                is EditResult.Rejected -> events.trySend(HomeEvent.EditRejected(result.reason))
            }
        }
    }

    // --- Recovery, safe mode, notices ------------------------------------------------------------

    fun retryWorkspace() {
        workspaceRetry.value += 1
    }

    private var startupStableReported = false

    /** Called once the first frame has stayed up for [StartupGuard.STABLE_DELAY_MS]. */
    fun onStartupStable() {
        if (startupStableReported) return
        startupStableReported = true
        viewModelScope.launch {
            try {
                startupGuard.onStartupStable()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Best effort: a failed write only means the next start counts once more.
            }
        }
    }

    fun leaveSafeMode() {
        viewModelScope.launch {
            try {
                startupGuard.leaveSafeMode()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Leave safe mode for this session even if the flag could not be persisted.
            }
            safeMode.value = false
        }
    }

    fun dismissCorruptNotice() {
        deps.appScope.launch {
            try {
                deps.acknowledgeCorruptLayout()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Showing the notice again next time is harmless.
            }
        }
    }

    /** Re-reads the HOME role holder; called on every resume. Never changes the role. */
    fun refreshDefaultHomeStatus() {
        viewModelScope.launch {
            isDefaultHome.value = try {
                withContext(ioDispatcher) { deps.homeRoleController.isDefaultHome() }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null // unknown: do not nag
            }
        }
    }

    fun dismissDefaultHomeBanner() {
        savedState[KEY_BANNER_DISMISSED] = true
    }

    private data class Chrome(
        val safeMode: Boolean?,
        val corruptNotice: Boolean,
        val showBanner: Boolean,
        val settings: LauncherSettings,
    )

    companion object {
        const val SEARCH_DEBOUNCE_MS = 100L
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val KEY_QUERY = "home.query"
        private const val KEY_DRAWER_OPEN = "home.drawerOpen"
        private const val KEY_BANNER_DISMISSED = "home.bannerDismissed"

        fun factory(
            deps: HomeDependencies,
            startupGuard: StartupGuard,
            initialSafeMode: Boolean?,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { HomeViewModel(deps, createSavedStateHandle(), startupGuard, initialSafeMode) }
        }
    }
}

/** A resolved workspace and the stored snapshot and app list it was resolved from. */
private class ResolvedWorkspace(val stored: Workspace?, val apps: AppsState, val ui: WorkspaceUi)

/**
 * Personal folders a search result may open (see [FolderSearch.visibleFolders]), as their resolved
 * items in [ui]; null until [stored] and a loaded app list are available.
 */
internal fun searchableFolders(stored: Workspace?, apps: AppsState, ui: WorkspaceUi, hidden: Set<AppKey>): Map<String, HomeItemUi>? {
    val listed = apps as? AppsState.Loaded ?: return null
    if (stored == null || ui !is WorkspaceUi.Ready) return null
    val visibleIds = FolderSearch.visibleFolders(stored.items, listed.apps, hidden).mapTo(HashSet()) { it.itemId }
    return (ui.pages.flatten() + ui.dock).filter { it.id in visibleIds }.associateBy { it.id }
}
