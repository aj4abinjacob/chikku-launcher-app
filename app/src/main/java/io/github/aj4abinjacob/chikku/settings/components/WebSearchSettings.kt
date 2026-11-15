package io.github.aj4abinjacob.chikku.settings.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.search.WebSearchUrls
import io.github.aj4abinjacob.chikku.core.model.search.WebSuggestionProviders
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.core.model.settings.WebSearchEngine

/** Shared preferences on Dock and Search: one engine and one explicit network opt-in. */
@Composable
fun WebSearchSettings(settings: LauncherSettings, update: ((LauncherSettings) -> LauncherSettings) -> Unit) {
    val search = settings.search
    SettingsSwitchRow(
        title = stringResource(R.string.settings_search_web_row),
        summary = stringResource(R.string.settings_search_web_row_summary),
        checked = search.showWebSuggestionRow,
        onCheckedChange = { on -> update { it.copy(search = it.search.copy(showWebSuggestionRow = on)) } },
    )
    WebSearchEngineChoiceRow(
        selected = search.webEngine,
        onSelect = { engine -> update { it.copy(search = it.search.copy(webEngine = engine)) } },
        enabled = search.showWebSuggestionRow,
    )
    SettingsSwitchRow(
        title = stringResource(R.string.settings_search_online_suggestions),
        summary = stringResource(R.string.settings_search_online_suggestions_summary, WebSearchUrls.engineName(search.webEngine)),
        checked = search.onlineSuggestions,
        onCheckedChange = { on -> update { it.copy(search = it.search.copy(onlineSuggestions = on)) } },
        enabled = search.showWebSuggestionRow && WebSuggestionProviders.supports(search.webEngine),
    )
    if (!WebSuggestionProviders.supports(search.webEngine)) {
        SettingsInfoText(stringResource(R.string.settings_search_suggestions_supported))
    }
}

/**
 * The one web search engine chooser, used on both the Dock and Search settings pages so the two
 * can never drift apart. Each option names the engine and the web address searches go to. No
 * brand logos or colours, and no claims about third-party services.
 */
@Composable
fun WebSearchEngineChoiceRow(
    selected: WebSearchEngine,
    onSelect: (WebSearchEngine) -> Unit,
    enabled: Boolean = true,
) {
    SettingsChoiceRow(
        title = stringResource(R.string.settings_search_engine),
        options = WebSearchEngine.entries,
        selected = selected,
        optionLabel = { WebSearchUrls.engineName(it) },
        optionSummary = { WebSearchUrls.engineHost(it) },
        onSelect = onSelect,
        enabled = enabled,
    )
}
