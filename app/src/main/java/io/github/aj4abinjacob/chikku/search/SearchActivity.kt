package io.github.aj4abinjacob.chikku.search

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aj4abinjacob.chikku.applock.AppUnlockPrompt
import io.github.aj4abinjacob.chikku.home.HomeActivity
import io.github.aj4abinjacob.chikku.home.HomeDependencies
import io.github.aj4abinjacob.chikku.home.homeDependencies
import io.github.aj4abinjacob.chikku.prompt.ChikkuPrompts

/**
 * Universal search over HOME (non-exported; open it through [SearchLauncher]). Results come from
 * [SearchViewModel]; this activity only renders them and performs the tapped row's action. It
 * finishes after handing off to another app, so HOME is underneath when the user returns.
 */
class SearchActivity : ComponentActivity() {

    private lateinit var deps: HomeDependencies
    private lateinit var actions: SearchResultActions
    private val notices = SearchNotices()

    /** The shortcut-change wiring is created with the ViewModel and closed when it is cleared. */
    private val viewModel: SearchViewModel by viewModels {
        SearchHostWiring.viewModelFactory(applicationContext, deps, intent?.getStringExtra(SearchLauncher.EXTRA_QUERY))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        deps = homeDependencies()
        val prompts = ChikkuPrompts(this) { viewModel.settings.value?.lookFeel?.accentColor }
        actions = SearchResultActions(this, deps, AppUnlockPrompt(this, deps.appLock, prompts), ::finish, notices::show, currentQuery = { viewModel.query.value }) { folderId ->
            startActivity(Intent(this, HomeActivity::class.java).setAction(ACTION_OPEN_FOLDER)
                .putExtra(EXTRA_FOLDER_ID, folderId)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val initialQuery = viewModel.query.value
        setContent {
            val state = viewModel.collectUiState()
            val settings = state.settings
            val notice by notices.current.collectAsStateWithLifecycle()
            SearchTheme(settings) {
                SearchScreen(
                    initialQuery = initialQuery,
                    results = state.results,
                    suggestions = state.suggestions,
                    settings = settings?.search ?: io.github.aj4abinjacob.chikku.core.model.settings.SearchSettings(),
                    iconProvider = deps.iconProvider,
                    onQueryChange = viewModel::setQuery,
                    onRowClick = actions::perform,
                    onPersonAction = actions::performPerson,
                    onClose = ::finish,
                    notice = notice,
                    onNoticeShown = notices::shown,
                    historyControls = actions.historyControls,
                    tapOrigin = actions.tapOrigin,
                )
            }
        }
    }

    companion object {
        const val ACTION_OPEN_FOLDER = "io.github.aj4abinjacob.chikku.OPEN_FOLDER"
        const val EXTRA_FOLDER_ID = "folder.itemId"
    }
}
