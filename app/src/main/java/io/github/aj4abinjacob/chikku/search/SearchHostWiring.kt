package io.github.aj4abinjacob.chikku.search

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import io.github.aj4abinjacob.chikku.applock.excludingLocked
import io.github.aj4abinjacob.chikku.core.model.search.SearchResults
import io.github.aj4abinjacob.chikku.core.model.search.UniversalSearch
import io.github.aj4abinjacob.chikku.core.model.search.WebSuggestions
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.home.HomeDependencies
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine

/**
 * The one place that builds what every search host needs: the shortcut source and its change
 * signal, [UniversalSearch] (shortcuts of locked apps are excluded) and the [SearchViewModel] with
 * app lock wired in both ways: [SearchViewModel] gets `isLocked` and re-filters whenever the lock
 * state or the shortcut set changes. Hosts ([SearchActivity], [HomeSearchOverlay]) differ only in
 * how they present the results. [close] stops the shortcut change signal.
 */
internal class SearchHostWiring(context: Context, private val deps: HomeDependencies) : AutoCloseable {
    private val appContext = context.applicationContext
    private val shortcutSource = LauncherAppsShortcutSource(appContext, Dispatchers.IO)
    private val shortcutChanges = ShortcutChangeSignal(appContext, shortcutSource)
    private val universalSearch = UniversalSearch(
        deps.appSearcher,
        shortcutSource.excludingLocked(deps.appLock),
        ContactsSearchSource(appContext, Dispatchers.IO),
    )

    fun newViewModel(savedState: SavedStateHandle): SearchViewModel = SearchViewModel(
        savedState, deps.settingsRepository, deps.appsRepository, deps.appCustomizationRepository,
        universalSearch, Dispatchers.Default, deps.webSuggestionSource, deps.searchHistoryRepository, deps.workspaceRepository,
        isLocked = deps.appLock::isLocked,
        lockChanges = combine(deps.appLock.state, shortcutChanges.revision) { lock, revision -> lock to revision },
    )

    /** For a ViewModelStoreOwner with saved state; [initialQuery] seeds the query on first creation only. */
    fun factory(initialQuery: String?): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            val handle = extras.createSavedStateHandle()
            if (!handle.contains(SearchViewModel.KEY_QUERY) && initialQuery != null) handle[SearchViewModel.KEY_QUERY] = initialQuery
            return newViewModel(handle) as T
        }
    }

    override fun close() = shortcutChanges.close()

    companion object {
        /**
         * For a host whose ViewModel outlives its Activity (rotation): the wiring is built only when
         * the ViewModel is created and is closed with it (`onCleared`), exactly once. `viewModels { }`
         * runs its factory producer for every Activity instance, so the wiring must not be built there.
         */
        fun viewModelFactory(context: Context, deps: HomeDependencies, initialQuery: String?): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    val wiring = SearchHostWiring(context, deps)
                    return try {
                        wiring.factory(initialQuery).create(modelClass, extras).also { it.addCloseable(wiring) }
                    } catch (t: Throwable) {
                        wiring.close()
                        throw t
                    }
                }
            }
    }
}

/** What a search host renders, as collected from its [SearchViewModel]. */
internal class SearchUiState(
    val settings: LauncherSettings?,
    val results: SearchResults,
    val suggestions: WebSuggestions,
)

@Composable
internal fun SearchViewModel.collectUiState(): SearchUiState {
    val currentSettings by settings.collectAsStateWithLifecycle()
    val currentResults by results.collectAsStateWithLifecycle()
    val currentSuggestions by webSuggestions.collectAsStateWithLifecycle()
    return SearchUiState(currentSettings, currentResults, currentSuggestions)
}
