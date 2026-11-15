package io.github.aj4abinjacob.chikku.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.aj4abinjacob.chikku.core.model.AppCustomizationRepository
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.AppsRepository
import io.github.aj4abinjacob.chikku.core.model.AppsState
import io.github.aj4abinjacob.chikku.core.model.LauncherApp
import io.github.aj4abinjacob.chikku.core.model.WorkspaceRepository
import io.github.aj4abinjacob.chikku.core.model.WorkspaceState
import io.github.aj4abinjacob.chikku.core.model.WorkspaceItem
import io.github.aj4abinjacob.chikku.core.model.search.ContactServices
import io.github.aj4abinjacob.chikku.core.model.search.FolderSearch
import io.github.aj4abinjacob.chikku.core.model.search.HistoryEntry
import io.github.aj4abinjacob.chikku.core.model.search.HistoryTarget
import io.github.aj4abinjacob.chikku.core.model.search.PreparedLabels
import io.github.aj4abinjacob.chikku.core.model.search.QueryLearning
import io.github.aj4abinjacob.chikku.core.model.search.RecentItem
import io.github.aj4abinjacob.chikku.core.model.search.SearchHistory
import io.github.aj4abinjacob.chikku.core.model.search.SearchHistoryRepository
import io.github.aj4abinjacob.chikku.core.model.search.SearchResults
import io.github.aj4abinjacob.chikku.core.model.search.ShortcutKey
import io.github.aj4abinjacob.chikku.core.model.search.UniversalSearch
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.core.model.settings.SearchSettings
import io.github.aj4abinjacob.chikku.core.model.settings.SettingsRepository
import io.github.aj4abinjacob.chikku.core.model.search.CustomLabelSearch
import io.github.aj4abinjacob.chikku.core.model.search.WebSuggestions
import io.github.aj4abinjacob.chikku.core.model.search.WebSuggestionSource
import io.github.aj4abinjacob.chikku.core.model.search.WebSuggestionResult
import io.github.aj4abinjacob.chikku.core.model.search.WebSuggestionProviders
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/**
 * Search query state is held by [SavedStateHandle]. Offline results and optional providers have
 * independent cancellation paths; stale provider results never merge into a newer input. Queries
 * are never logged. A query is only remembered (search history, opt-in) when the user sends it to
 * the web or Play Store; opening a result also teaches its first few typed characters
 * ([QueryLearning]), which then reorder typed results. See [SearchResultActions].
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val savedState: SavedStateHandle,
    settingsRepository: SettingsRepository,
    appsRepository: AppsRepository,
    customizations: AppCustomizationRepository,
    private val universalSearch: UniversalSearch,
    searchDispatcher: CoroutineDispatcher,
    suggestionSource: WebSuggestionSource = WebSuggestionSource { _, _ -> WebSuggestionResult.Unavailable },
    searchHistory: SearchHistoryRepository? = null,
    workspaceRepository: WorkspaceRepository? = null,
    /** App lock: locked apps and their shortcuts are never suggested. [lockChanges] re-filters. */
    private val isLocked: (AppKey) -> Boolean = { false },
    lockChanges: kotlinx.coroutines.flow.Flow<Any?> = kotlinx.coroutines.flow.flowOf(Unit),
    private val clock: () -> Long = System::currentTimeMillis,
    private val hourOf: (Long) -> Int = { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).hour },
) : ViewModel() {

    val query: StateFlow<String> = savedState.getStateFlow(KEY_QUERY, "")

    /** Null until loaded. */
    val settings: StateFlow<LauncherSettings?> = settingsRepository.settings
        .catch { emit(LauncherSettings()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val apps = appsRepository.apps
        .map { (it as? AppsState.Loaded)?.apps.orEmpty() }
        .catch { emit(emptyList()) }
        .onStart { emit(emptyList()) }
        .distinctUntilChanged()

    /** Hidden apps (excluded) and custom labels (searchable), from app customizations. */
    private val custom = customizations.customizations
        .map { all -> Custom(all.values.filter { it.hidden }.mapTo(HashSet()) { it.key }.toSet(), CustomLabelSearch.customLabels(all)) }
        .catch { emit(Custom()) }
        .onStart { emit(Custom()) }
        .distinctUntilChanged()

    private val searchSettings = settingsRepository.settings
        .map { it.search }
        .catch { emit(LauncherSettings().search) }
        .distinctUntilChanged()

    private val folderItems = (workspaceRepository?.workspace ?: kotlinx.coroutines.flow.flowOf(WorkspaceState.Loading))
        .map { (it as? WorkspaceState.Ready)?.workspace?.items.orEmpty() }
        .catch { emit(emptyList()) }
        .onStart { emit(emptyList()) }
        .distinctUntilChanged()

    // Safety filtering is independent of slow shortcut/contact reads: a removed/hidden/profile
    // change withholds an old folder title immediately while its replacement search is pending.
    private val visibleFolders = combine(folderItems, apps, custom, searchSettings) { items, currentApps, customization, settings ->
        if (settings.foldersEnabled) FolderSearch.visibleFolders(items, currentApps, customization.hidden).toSet() else emptySet()
    }

    private val historyEntries = (searchHistory?.entries ?: kotlinx.coroutines.flow.flowOf(emptyList<HistoryEntry>())).catch { emit(emptyList()) }

    // Normalized labels of the current app list and custom labels, computed lazily on the first typed
    // query and reused by every later keystroke; a new snapshot replaces it. In memory only, and
    // dropped with the search session (inputs are not shared while search is closed).
    private val snapshot = combine(apps, custom) { listed, customization -> Snapshot(listed, customization, PreparedLabels(listed, customization.labels)) }

    private val inputs = combine(
        query.debounce { if (it.isBlank()) 0L else DEBOUNCE_MILLIS }.distinctUntilChanged(),
        snapshot,
        searchSettings,
        historyEntries,
    ) { q, snap, settings, history -> Input(q, snap.apps, snap.custom, settings, history, labels = snap.labels) }
        .combine(folderItems) { input, items -> input.copy(folderItems = items) }
        .combine(lockChanges.catch { emit(Unit) }) { input, lock -> input.copy(lock = lock) }
        .flowOn(searchDispatcher)
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 0, replayExpirationMillis = 0), replay = 1)

    // These streams are independent: Android Binder calls cannot always be cancelled promptly.
    // Cancellation of optional providers must never delay the next query's offline matches.
    private val localResults = inputs.mapLatest { input ->
        val result = try {
            if (input.query.isBlank()) history(input, readProviders = false)
            else universalSearch.search(input.query, input.apps, input.custom.hidden,
                input.settings.copy(shortcutsEnabled = false, contactsEnabled = false), input.custom.labels, input.folderItems, shortcutWeights(input),
                labels = input.labels)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { SearchResults(query = input.query) }
        input to result
    }

    private val providerResults = inputs.mapLatest { input ->
        val providers = input.settings.shortcutsEnabled || input.settings.contactsEnabled
        val result = if (input.query.isBlank() && providers && input.history.any { it.target is HistoryTarget.Shortcut || it.target is HistoryTarget.Contact }) {
            try { history(input, readProviders = true) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { SearchResults(query = input.query) }
        } else if (input.query.isNotBlank() && providers) {
            // Only shortcuts and contacts are merged from this pass (see `results`); the offline part
            // was already computed once by `localResults`.
            try { universalSearch.providers(input.query, input.apps, input.custom.hidden, input.settings, shortcutWeights(input)) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { SearchResults(query = input.query) }
        } else SearchResults(query = input.query)
        input to result
    }.map<Pair<Input, SearchResults>, Pair<Input, SearchResults>?> { it }.onStart { emit(null) }

    private val unlockedState = combine(apps, custom, searchSettings, historyEntries.combine(lockChanges.catch { emit(Unit) }) { history, _ -> history }) { listed, customization, prefs, history ->
        Input("", listed, customization, prefs, unlocked(history, listed))
    }

    /** The current raw state; the query is swapped into the cached [unlockedState] per keystroke. */
    private val liveInput = combine(unlockedState, query) { state, q -> state.copy(query = q) }

    val results: StateFlow<SearchResults> = combine(localResults, providerResults) { local, provider ->
        val (input, result) = local
        when {
            provider?.first != input -> result
            input.query.isBlank() -> provider.second.copy(query = input.query).takeIf { it.recent.isNotEmpty() || it.apps.isNotEmpty() } ?: result
            else -> result.copy(shortcuts = provider.second.shortcuts, contacts = provider.second.contacts)
        }
    }
        .combine(visibleFolders) { result, visible ->
            result.copy(
                folders = result.folders.filter { it in visible },
                recent = result.recent.filter { it !is RecentItem.Folder || it.folder in visible },
            )
        }
        // Do not wait for debounce/provider cancellation to withhold removed or hidden identities.
        // This re-filter must stay on raw state (not on the debounced inputs): a hidden, locked or
        // turned-off source has to disappear at once. The lock-filtered history, however, depends on
        // the app list, settings, history and lock state only, so it is computed when those change
        // and not again for every keystroke; a raw keystroke just swaps the query in.
        .combine(liveInput) { result, current ->
            if (result.query != current.query) SearchResults(query = current.query)
            else {
                val visible = current.apps.filterNot { it.key in current.custom.hidden }
                val keys = visible.mapTo(HashSet()) { it.key }
                // Hidden or removed provider apps leave the card at once, like hidden apps.
                val services = if (current.settings.contactsEnabled) {
                    ContactServices.available(current.apps, current.custom.hidden)
                } else emptyList()
                val blank = current.query.isBlank()
                val showHistory = !blank || current.settings.historySuggestionsEnabled
                result.copy(
                    contactServices = services,
                    apps = if (showHistory) result.apps.filter { it.key in keys } else emptyList(),
                    appMatchRanks = result.appMatchRanks.filterKeys { it in keys },
                    recent = if (!showHistory) emptyList() else result.recent.filter { item ->
                        when (item) {
                            is RecentItem.Contact -> current.settings.contactsEnabled
                            is RecentItem.Shortcut -> current.settings.shortcutsEnabled && keys.any { it.packageName == item.shortcut.packageName && it.userSerial == item.shortcut.userSerial }
                            is RecentItem.Folder -> current.settings.foldersEnabled
                            is RecentItem.Query -> true
                        }
                    },
                    shortcuts = if (current.settings.shortcutsEnabled) result.shortcuts?.let {
                        UniversalSearch.filterShortcuts(it, current.query, visible, shortcutWeights(current))
                    } else null,
                    contacts = ContactServices.filter(result.contacts.takeIf { current.settings.contactsEnabled }, services),
                ).let { filtered ->
                    // Last, on what is actually shown: a learned habit only reorders visible matches.
                    // Contacts and shortcuts arrive later; a stronger habit there must not be pre-empted.
                    if (blank) filtered else QueryLearning.of(current.history, current.query, clock()).apply(filtered) { target ->
                        (target is HistoryTarget.Contact && current.settings.contactsEnabled && filtered.contacts == null) ||
                            (target is HistoryTarget.Shortcut && current.settings.shortcutsEnabled && filtered.shortcuts == null)
                    }
                }
            }
        }
        .distinctUntilChanged()
        .flowOn(searchDispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 0, replayExpirationMillis = 0), SearchResults.EMPTY)

    /** Independent of local results: offline matches never wait for a network request. */
    val webSuggestions: StateFlow<WebSuggestions> = combine(query, searchSettings) { q, settings -> q.trim() to settings }
        .distinctUntilChanged()
        .transformLatest { (q, settings) ->
            emit(WebSuggestions()) // clear stale suggestions immediately, including on disable/provider change
            if (!settings.onlineSuggestions || !settings.showWebSuggestionRow ||
                !WebSuggestionProviders.supports(settings.webEngine) || q.length !in 2..200
            ) return@transformLatest
            delay(SUGGESTION_DEBOUNCE_MILLIS)
            val result = try {
                suggestionSource.suggest(settings.webEngine, q)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                WebSuggestionResult.Unavailable
            }
            emit(WebSuggestions(q, settings.webEngine,
                values = (result as? WebSuggestionResult.Found)?.suggestions.orEmpty(),
                unavailable = result == WebSuggestionResult.Unavailable,
            ))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 0, replayExpirationMillis = 0), WebSuggestions())

    fun setQuery(value: String) {
        savedState[KEY_QUERY] = value
    }

    /** Blank-query suggestions: history ranked for now, resolved and filtered live. */
    /** Shortcuts used more often (per search history) rank first among equally good typed matches. */
    private fun shortcutWeights(input: Input): Map<ShortcutKey, Int> {
        if (input.history.isEmpty()) return emptyMap()
        val now = clock()
        return SearchHistory.shortcutWeights(SearchHistory.rank(input.history, now, hourOf(now)))
    }

    private suspend fun history(input: Input, readProviders: Boolean): SearchResults {
        if (!input.settings.historySuggestionsEnabled || input.history.isEmpty()) return SearchResults(query = input.query)
        val now = clock()
        return universalSearch.history(
            SearchHistory.rank(input.history, now, hourOf(now)), input.apps, input.custom.hidden, isLocked,
            input.settings, input.custom.labels, input.folderItems, readProviders,
        ).copy(query = input.query)
    }

    /** History without app-locked apps and their shortcuts, so a habit learned before locking never promotes them. */
    private fun unlocked(history: List<HistoryEntry>, listed: List<LauncherApp>): List<HistoryEntry> = history.filterNot { entry ->
        when (val target = entry.target) {
            is HistoryTarget.App -> isLocked(target.key)
            is HistoryTarget.Shortcut -> listed.any { it.key.packageName == target.packageName && it.key.userSerial == target.userSerial && isLocked(it.key) }
            else -> false
        }
    }

    private data class Input(
        val query: String,
        val apps: List<LauncherApp>,
        val custom: Custom,
        val settings: SearchSettings,
        val history: List<HistoryEntry>,
        val folderItems: List<WorkspaceItem> = emptyList(),
        val lock: Any? = null,
        /** Normalized labels of [apps] and the custom labels; identity-compared, replaced with the snapshot. */
        val labels: PreparedLabels? = null,
    )

    private class Snapshot(val apps: List<LauncherApp>, val custom: Custom, val labels: PreparedLabels)

    private data class Custom(val hidden: Set<AppKey> = emptySet(), val labels: Map<AppKey, String> = emptyMap())

    companion object {
        const val SUGGESTION_DEBOUNCE_MILLIS = 300L
        const val DEBOUNCE_MILLIS = 120L
        internal const val KEY_QUERY = "search.query"
    }
}
