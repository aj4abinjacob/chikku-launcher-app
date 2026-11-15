package io.github.aj4abinjacob.chikku.settings.sections

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.search.WebSearchUrls
import io.github.aj4abinjacob.chikku.core.model.search.WebSuggestionProviders
import io.github.aj4abinjacob.chikku.core.model.settings.HomeSearchBarPosition
import io.github.aj4abinjacob.chikku.core.model.settings.HomeSearchBarStyle
import io.github.aj4abinjacob.chikku.core.model.settings.HomeSearchBarIcon
import io.github.aj4abinjacob.chikku.core.model.settings.HomeSearchBarButtonSlot
import io.github.aj4abinjacob.chikku.core.model.settings.HomeSearchBarTapAction
import io.github.aj4abinjacob.chikku.core.model.settings.WebSearchEngine
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import io.github.aj4abinjacob.chikku.search.glyphRes
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.core.model.settings.SearchPresentation
import io.github.aj4abinjacob.chikku.core.model.settings.SearchResultsAnchor
import io.github.aj4abinjacob.chikku.core.model.settings.SearchAppPriorityDirection
import io.github.aj4abinjacob.chikku.search.summaryRes
import io.github.aj4abinjacob.chikku.core.model.settings.SearchSettings
import io.github.aj4abinjacob.chikku.settings.LocalHomeDependencies
import io.github.aj4abinjacob.chikku.settings.components.SettingsChoiceRow
import io.github.aj4abinjacob.chikku.settings.components.SettingsColorRow
import io.github.aj4abinjacob.chikku.settings.components.SettingsInfoText
import io.github.aj4abinjacob.chikku.settings.components.SettingsIntSliderRow
import io.github.aj4abinjacob.chikku.settings.components.SettingsMainSwitch
import io.github.aj4abinjacob.chikku.settings.components.SettingsNavigationRow
import io.github.aj4abinjacob.chikku.settings.components.SettingsPage
import io.github.aj4abinjacob.chikku.settings.components.SettingsSectionHeader
import io.github.aj4abinjacob.chikku.settings.components.SettingsSliderRow
import io.github.aj4abinjacob.chikku.settings.components.WebSearchEngineChoiceRow
import io.github.aj4abinjacob.chikku.settings.components.SettingsSubPage
import io.github.aj4abinjacob.chikku.settings.components.SettingsSubPageLink
import io.github.aj4abinjacob.chikku.settings.components.SettingsSubPageRow
import io.github.aj4abinjacob.chikku.settings.components.SettingsSwitchRow
import io.github.aj4abinjacob.chikku.core.model.search.SearchHistoryRepository
import io.github.aj4abinjacob.chikku.prompt.PromptAction
import io.github.aj4abinjacob.chikku.prompt.PromptSheet
import io.github.aj4abinjacob.chikku.prompt.PromptSpec
import io.github.aj4abinjacob.chikku.prompt.PromptTone
import kotlinx.coroutines.flow.MutableStateFlow
import io.github.aj4abinjacob.chikku.settings.components.dpValueLabel
import io.github.aj4abinjacob.chikku.settings.components.rememberPermissionGranted
import io.github.aj4abinjacob.chikku.settings.components.rememberSettingsSubPage
import io.github.aj4abinjacob.chikku.settings.components.settingsSummary
import io.github.aj4abinjacob.chikku.settings.previews.SearchHistoryLivePreview
import io.github.aj4abinjacob.chikku.settings.previews.HomeSearchPositionPreview
import io.github.aj4abinjacob.chikku.settings.previews.SearchLivePreview
import io.github.aj4abinjacob.chikku.settings.previews.SearchPresentationPreview
import io.github.aj4abinjacob.chikku.settings.previews.SearchResultsAnchorPreview
import io.github.aj4abinjacob.chikku.settings.previews.SearchPreviewMode
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import io.github.aj4abinjacob.chikku.search.labelRes
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Sub-pages of Settings › Search; routes are shared with [io.github.aj4abinjacob.chikku.settings.SettingsSearchIndex]. */
internal object SearchPages {
    val Look = SettingsSubPageLink("look", R.string.search_page_look)
    val HomeBar = SettingsSubPageLink("home_bar", R.string.search_page_home_bar)
    val HomeBarSize = SettingsSubPageLink("home_bar_size", R.string.search_page_home_bar_size)
    val Apps = SettingsSubPageLink("apps", R.string.search_page_apps)
    val Suggestions = SettingsSubPageLink("suggestions", R.string.settings_search_history_header)
    val Contacts = SettingsSubPageLink("contacts", R.string.search_page_contacts)
    val Web = SettingsSubPageLink("web", R.string.search_page_web)
    val Answers = SettingsSubPageLink("answers", R.string.search_page_answers)
    val all = listOf(Look, HomeBar, HomeBarSize, Apps, Suggestions, Contacts, Web, Answers)
}

private typealias Update = ((LauncherSettings) -> LauncherSettings) -> Unit

/**
 * Search (G04-G06). The main page keeps the display choice and privacy inline; the opened-search
 * look, the home search bar and each result source (apps, contacts, web, quick answers) have their
 * own sub-page. READ_CONTACTS is requested only at the moment the user turns contacts on; turning
 * it off stops all contact queries (and, on Android 13+, asks the system to revoke the permission
 * when the app process next ends).
 */
@Composable
fun SearchSettingsSection(settings: LauncherSettings, update: Update) {
    var page by rememberSettingsSubPage()
    val link = SearchPages.all.firstOrNull { it.route == page }
    if (link == null) {
        SearchMainPage(settings, update, open = { page = it.route })
        return
    }
    val parent = if (link == SearchPages.HomeBarSize) SearchPages.HomeBar.route else null
    SettingsSubPage(title = stringResource(link.title), onBack = { page = parent }) {
        when (link) {
            SearchPages.Look -> SearchLookPage(settings, update)
            SearchPages.HomeBar -> HomeBarPage(settings, update, openSize = { page = SearchPages.HomeBarSize.route })
            SearchPages.HomeBarSize -> HomeBarSizePage(settings, update)
            SearchPages.Apps -> SearchAppsPage(settings, update)
            SearchPages.Suggestions -> SearchSuggestionsPage(settings, update)
            SearchPages.Contacts -> SearchContactsPage(settings, update)
            SearchPages.Web -> SearchWebPage(settings, update)
            SearchPages.Answers -> SearchAnswersPage(settings, update)
        }
    }
}

private fun Update.search(transform: (SearchSettings) -> SearchSettings) =
    invoke { it.copy(search = transform(it.search)) }

/**
 * A pinned [SearchLivePreview] above a scrolling page. The page edits a draft through `preview`
 * on every slider frame; the draft resets to the saved settings whenever they change.
 */
@Composable
private fun PreviewedSearchPage(
    settings: LauncherSettings,
    mode: SearchPreviewMode?,
    content: @Composable (preview: (SearchSettings.() -> SearchSettings) -> Unit) -> Unit,
) {
    var live by remember(settings) { mutableStateOf(settings) }
    val preview: (SearchSettings.() -> SearchSettings) -> Unit = { change -> live = live.copy(search = live.search.change()) }
    Column(Modifier.fillMaxSize()) {
        if (mode != null) SearchLivePreview(live, mode)
        SettingsPage(Modifier.weight(1f)) { content(preview) }
    }
}

/** The home bar is visible only when it is switched on and has a real position. */
private fun LauncherSettings.homeBarShown(): Boolean =
    home.showSearchBar && home.searchBarPosition != HomeSearchBarPosition.NONE

@Composable
private fun SearchMainPage(settings: LauncherSettings, update: Update, open: (SettingsSubPageLink) -> Unit) {
    val context = LocalContext.current
    val search = settings.search
    val off = stringResource(R.string.settings_state_off)
    val hasContactsPermission by rememberPermissionGranted(Manifest.permission.READ_CONTACTS)
    PreviewedSearchPage(settings, SearchPreviewMode.WINDOW) { _ ->
        SettingsSectionHeader(stringResource(R.string.search_settings_window_header))
        SettingsChoiceRow(
            title = stringResource(R.string.search_settings_presentation),
            optionPreview = { SearchPresentationPreview(it) },
            optionSummary = { stringResource(if (it == SearchPresentation.WINDOW) R.string.hint_search_window else R.string.hint_search_fullscreen) },
            options = SearchPresentation.entries,
            selected = search.presentation,
            optionLabel = { mode -> stringResource(if (mode == SearchPresentation.WINDOW) R.string.search_settings_window else R.string.search_settings_fullscreen) },
            onSelect = { mode -> update.search { it.copy(presentation = mode) } },
            icon = R.drawable.ic_settings_row_fullscreen,
        )
        SettingsChoiceRow(
            title = stringResource(R.string.search_settings_results_anchor),
            optionPreview = { SearchResultsAnchorPreview(it) },
            optionSummary = { stringResource(if (it == SearchResultsAnchor.KEYBOARD) R.string.hint_search_results_keyboard else R.string.hint_search_results_top) },
            options = SearchResultsAnchor.entries,
            selected = search.resultsAnchor,
            optionLabel = { stringResource(it.labelRes()) },
            onSelect = { anchor -> update.search { it.copy(resultsAnchor = anchor) } },
            icon = R.drawable.ic_settings_row_keyboard,
        )
        SettingsSubPageRow(
            title = stringResource(SearchPages.Look.title),
            summary = settingsSummary(
                stringResource(if (search.backgroundColor == null) R.string.search_summary_default_color else R.string.search_summary_custom_color),
                stringResource(R.string.search_summary_corners, search.cornerRadiusDp.roundToInt()),
            ),
            icon = R.drawable.ic_settings_row_rounded_corner,
            onClick = { open(SearchPages.Look) },
        )
        SettingsSubPageRow(
            title = stringResource(SearchPages.HomeBar.title),
            summary = if (settings.homeBarShown()) settingsSummary(
                stringResource(settings.home.searchBarPosition.labelRes()),
                if (search.widgetTapAction == HomeSearchBarTapAction.WEB_SEARCH) WebSearchUrls.engineName(search.webEngine)
                else stringResource(R.string.search_bar_tap_chikku),
            ) else off,
            icon = R.drawable.ic_settings_row_manage_search,
            onClick = { open(SearchPages.HomeBar) },
        )

        SettingsSectionHeader(stringResource(R.string.search_settings_results_header))
        SettingsSubPageRow(
            title = stringResource(SearchPages.Apps.title),
            summary = settingsSummary(
                if (search.maxAppResults > 0) pluralStringResource(R.plurals.search_summary_one_row, search.maxAppResults, search.maxAppResults)
                else stringResource(R.string.search_summary_all_apps),
            ),
            icon = R.drawable.ic_settings_row_apps,
            onClick = { open(SearchPages.Apps) },
        )
        SettingsSubPageRow(
            title = stringResource(SearchPages.Suggestions.title),
            summary = settingsSummary(
                stringResource(if (search.historySuggestionsEnabled) R.string.settings_state_on else R.string.settings_state_off),
                stringResource(if (search.historyLearningEnabled) R.string.preview_search_history_learning else R.string.preview_search_history_paused),
            ),
            icon = R.drawable.ic_search_history,
            onClick = { open(SearchPages.Suggestions) },
        )
        SettingsSubPageRow(
            title = stringResource(SearchPages.Contacts.title),
            summary = when {
                !search.contactsEnabled -> off
                hasContactsPermission -> stringResource(R.string.settings_state_on)
                else -> settingsSummary(off, stringResource(R.string.search_summary_needs_permission))
            },
            icon = R.drawable.ic_settings_row_contacts,
            onClick = { open(SearchPages.Contacts) },
        )
        SettingsSubPageRow(
            title = stringResource(SearchPages.Web.title),
            summary = if (!search.showWebSuggestionRow) off else settingsSummary(
                WebSearchUrls.engineName(search.webEngine),
                if (search.onlineSuggestions && WebSuggestionProviders.supports(search.webEngine)) {
                    stringResource(R.string.search_summary_online_suggestions)
                } else {
                    null
                },
            ),
            icon = R.drawable.ic_settings_row_public,
            onClick = { open(SearchPages.Web) },
        )
        SettingsSubPageRow(
            title = stringResource(SearchPages.Answers.title),
            summary = settingsSummary(
                if (search.calculatorEnabled) stringResource(R.string.search_summary_calculations) else null,
                if (search.microResultsEnabled) stringResource(R.string.search_summary_conversions) else null,
            ) ?: off,
            icon = R.drawable.ic_settings_row_calculate,
            onClick = { open(SearchPages.Answers) },
        )

        SettingsSectionHeader(stringResource(R.string.search_settings_privacy_header))
        SettingsSwitchRow(
            title = stringResource(R.string.search_settings_private_keyboard),
            summary = stringResource(R.string.search_settings_private_keyboard_summary),
            checked = search.privateKeyboard,
            onCheckedChange = { on -> update.search { it.copy(privateKeyboard = on) } },
            icon = R.drawable.ic_settings_row_keyboard,
        )
        SettingsInfoText(stringResource(R.string.settings_search_privacy_note))
    }
}

/** How opened search looks: its background and corner rounding. */
@Composable
private fun SearchLookPage(settings: LauncherSettings, update: Update) {
    val search = settings.search
    PreviewedSearchPage(settings, SearchPreviewMode.WINDOW) { preview ->
        SettingsColorRow(
            title = stringResource(R.string.search_settings_background),
            color = search.backgroundColor,
            onColorChange = { color -> update.search { it.copy(backgroundColor = color) } },
        )
        SettingsSliderRow(
            title = stringResource(R.string.search_settings_corner_radius),
            value = search.cornerRadiusDp,
            valueRange = 0f..48f,
            valueLabel = dpValueLabel(),
            onValueChangeFinished = { value -> update.search { it.copy(cornerRadiusDp = value) } },
            onValueChanging = { value -> preview { copy(cornerRadiusDp = value) } },
        )
        SettingsInfoText(stringResource(R.string.search_settings_corner_radius_summary))
    }
}

@Composable
private fun HomeBarPage(settings: LauncherSettings, update: Update, openSize: () -> Unit) {
    val search = settings.search
    val shown = settings.homeBarShown()
    PreviewedSearchPage(settings, SearchPreviewMode.HOME_BAR) { preview ->
        SettingsMainSwitch(
            title = stringResource(R.string.settings_search_home_bar),
            checked = shown,
            onCheckedChange = { on ->
                update {
                    val position = if (on && it.home.searchBarPosition == HomeSearchBarPosition.NONE) {
                        HomeSearchBarPosition.TOP
                    } else {
                        it.home.searchBarPosition
                    }
                    it.copy(home = it.home.copy(showSearchBar = on, searchBarPosition = position))
                }
            },
        )
        if (!shown) {
            SettingsInfoText(stringResource(R.string.search_home_bar_off))
            return@PreviewedSearchPage
        }
        SettingsChoiceRow(
            title = stringResource(R.string.settings_search_home_bar_position),
            optionPreview = { HomeSearchPositionPreview(it) },
            // "Hidden" is the main switch above.
            options = HomeSearchBarPosition.entries.filter { it != HomeSearchBarPosition.NONE },
            selected = settings.home.searchBarPosition,
            optionLabel = { stringResource(it.labelRes()) },
            onSelect = { position -> update { it.copy(home = it.home.copy(searchBarPosition = position)) } },
        )
        SettingsSectionHeader(stringResource(R.string.search_bar_actions_header))
        HomeBarActionSettings(search, update)
        SettingsSectionHeader(stringResource(R.string.search_bar_look_header))
        SettingsChoiceRow(
            title = stringResource(R.string.search_settings_bar_style),
            optionSummary = {
                stringResource(if (it == HomeSearchBarStyle.MINIMAL) R.string.hint_search_bar_minimal else R.string.hint_search_bar_provider)
            },
            options = HomeSearchBarStyle.entries,
            selected = search.widgetStyle,
            optionLabel = {
                stringResource(if (it == HomeSearchBarStyle.MINIMAL) R.string.search_settings_bar_style_minimal else R.string.search_settings_bar_style_provider)
            },
            onSelect = { value -> update.search { it.copy(widgetStyle = value) } },
        )
        // The glyph is drawn only on the Chikku button.
        if (search.widgetChikkuButton != HomeSearchBarButtonSlot.HIDDEN) {
            SettingsChoiceRow(
                title = stringResource(R.string.search_bar_chikku_icon),
                options = HomeSearchBarIcon.entries,
                selected = search.widgetIcon,
                optionLabel = { stringResource(it.labelRes()) },
                optionIcon = { Icon(painterResource(it.glyphRes()), contentDescription = null, Modifier.size(24.dp)) },
                onSelect = { value -> update.search { it.copy(widgetIcon = value) } },
            )
        }
        SettingsColorRow(
            title = stringResource(R.string.search_settings_bar_color),
            color = search.widgetColor,
            onColorChange = { color -> update.search { it.copy(widgetColor = color) } },
        )
        SettingsSliderRow(
            title = stringResource(R.string.search_settings_bar_corners),
            value = search.widgetCornerRadiusDp,
            valueRange = 0f..48f,
            valueLabel = dpValueLabel(),
            onValueChangeFinished = { value -> update.search { it.copy(widgetCornerRadiusDp = value) } },
            onValueChanging = { value -> preview { copy(widgetCornerRadiusDp = value) } },
        )
        SettingsInfoText(stringResource(R.string.search_settings_bar_corners_summary))
        SettingsSwitchRow(
            title = stringResource(R.string.search_settings_bar_shadow),
            checked = search.widgetShadow,
            onCheckedChange = { on -> update.search { it.copy(widgetShadow = on) } },
        )
        SettingsSubPageRow(
            title = stringResource(SearchPages.HomeBarSize.title),
            summary = settingsSummary(
                stringResource(R.string.search_summary_bar_width, search.widgetWidthPercent),
                stringResource(R.string.search_summary_bar_height, search.widgetHeightDp),
            ),
            onClick = openSize,
        )
    }
}

/**
 * What the home bar does: the tap on the bar itself, and where its two buttons sit. Segmented
 * choices, so every option is visible and one tap changes the pinned preview. The engine choice
 * appears here too whenever the bar uses the web, because the Web page hides it while the web
 * result is off.
 */
@Composable
private fun HomeBarActionSettings(search: SearchSettings, update: Update) {
    val engine = WebSearchUrls.engineName(search.webEngine)
    val usesWeb = search.widgetTapAction == HomeSearchBarTapAction.WEB_SEARCH || search.widgetWebButton != HomeSearchBarButtonSlot.HIDDEN
    SettingsChoiceRow(
        title = stringResource(R.string.search_bar_tap_action),
        options = HomeSearchBarTapAction.entries,
        selected = search.widgetTapAction,
        optionLabel = { if (it == HomeSearchBarTapAction.WEB_SEARCH) engine else stringResource(R.string.search_bar_tap_chikku) },
        onSelect = { action -> update.search { it.copy(widgetTapAction = action) } },
        inlineChoices = true,
    )
    SettingsInfoText(when {
        search.widgetTapAction == HomeSearchBarTapAction.CHIKKU_SEARCH -> stringResource(R.string.search_bar_tap_chikku_hint)
        search.webEngine == WebSearchEngine.GOOGLE -> stringResource(R.string.search_bar_tap_google_hint)
        else -> stringResource(R.string.search_bar_tap_web_hint, engine)
    })
    HomeBarButtonSlotRow(stringResource(R.string.search_bar_chikku_button), search.widgetChikkuButton) { slot ->
        update.search { it.copy(widgetChikkuButton = slot) }
    }
    HomeBarButtonSlotRow(stringResource(R.string.search_bar_web_button, engine), search.widgetWebButton) { slot ->
        update.search { it.copy(widgetWebButton = slot) }
    }
    if (search.widgetTapAction == HomeSearchBarTapAction.WEB_SEARCH && search.widgetChikkuButton == HomeSearchBarButtonSlot.HIDDEN) {
        SettingsInfoText(stringResource(R.string.search_bar_no_chikku_hint))
    } else {
        SettingsInfoText(stringResource(R.string.search_bar_buttons_hint, engine))
    }
    if (usesWeb) {
        WebSearchEngineChoiceRow(
            selected = search.webEngine,
            onSelect = { value -> update.search { it.copy(webEngine = value) } },
        )
    }
}

/** Left, Right or Off as the user sees them: in right-to-left layouts the start side is labelled Right. */
@Composable
private fun HomeBarButtonSlotRow(title: String, selected: HomeSearchBarButtonSlot, onSelect: (HomeSearchBarButtonSlot) -> Unit) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    SettingsChoiceRow(
        title = title,
        options = HomeSearchBarButtonSlot.entries,
        selected = selected,
        optionLabel = {
            stringResource(when (it) {
                HomeSearchBarButtonSlot.HIDDEN -> R.string.search_bar_button_off
                HomeSearchBarButtonSlot.START -> if (rtl) R.string.search_bar_button_right else R.string.search_bar_button_left
                HomeSearchBarButtonSlot.END -> if (rtl) R.string.search_bar_button_left else R.string.search_bar_button_right
            })
        },
        onSelect = onSelect,
        inlineChoices = true,
    )
}

@Composable
private fun HomeBarSizePage(settings: LauncherSettings, update: Update) {
    val resources = LocalResources.current
    val search = settings.search
    PreviewedSearchPage(settings, SearchPreviewMode.HOME_BAR_SIZE) { preview ->
        if (!settings.homeBarShown()) {
            SettingsInfoText(stringResource(R.string.search_home_bar_size_off))
            return@PreviewedSearchPage
        }
        SettingsIntSliderRow(
            title = stringResource(R.string.search_settings_bar_width),
            value = search.widgetWidthPercent,
            range = 40..100,
            valueLabel = { resources.getString(R.string.search_settings_bar_percent_value, it) },
            onValueChangeFinished = { value -> update.search { it.copy(widgetWidthPercent = value) } },
            onValueChanging = { value -> preview { copy(widgetWidthPercent = value) } },
        )
        SettingsIntSliderRow(
            title = stringResource(R.string.search_settings_bar_height),
            value = search.widgetHeightDp,
            range = 48..96,
            valueLabel = { resources.getString(R.string.search_settings_bar_dp_value, it) },
            onValueChangeFinished = { value -> update.search { it.copy(widgetHeightDp = value) } },
            onValueChanging = { value -> preview { copy(widgetHeightDp = value) } },
        )
        SettingsIntSliderRow(
            title = stringResource(R.string.search_settings_bar_horizontal_padding),
            value = search.widgetHorizontalPaddingDp,
            range = 0..48,
            valueLabel = { resources.getString(R.string.search_settings_bar_dp_value, it) },
            onValueChangeFinished = { value -> update.search { it.copy(widgetHorizontalPaddingDp = value) } },
            onValueChanging = { value -> preview { copy(widgetHorizontalPaddingDp = value) } },
        )
        SettingsIntSliderRow(
            title = stringResource(R.string.search_settings_bar_vertical_padding),
            value = search.widgetVerticalPaddingDp,
            range = 0..24,
            valueLabel = { resources.getString(R.string.search_settings_bar_dp_value, it) },
            onValueChangeFinished = { value -> update.search { it.copy(widgetVerticalPaddingDp = value) } },
            onValueChanging = { value -> preview { copy(widgetVerticalPaddingDp = value) } },
        )
        if (settings.home.searchBarPosition == HomeSearchBarPosition.BELOW_DOCK) {
            SettingsIntSliderRow(
                title = stringResource(R.string.search_settings_bar_dock_gap),
                value = search.widgetDockGapDp,
                range = 0..64,
                valueLabel = { resources.getString(R.string.search_settings_bar_dp_value, it) },
                onValueChangeFinished = { value -> update.search { it.copy(widgetDockGapDp = value) } },
                onValueChanging = { value -> preview { copy(widgetDockGapDp = value) } },
            )
        } else {
            SettingsInfoText(stringResource(R.string.search_home_bar_dock_gap_hidden))
        }
        SettingsInfoText(stringResource(R.string.search_settings_bar_sizing_summary))
        SettingsNavigationRow(
            title = stringResource(R.string.search_settings_bar_reset_size),
            onClick = {
                update.search {
                    val defaults = SearchSettings()
                    it.copy(
                        widgetWidthPercent = defaults.widgetWidthPercent,
                        widgetHeightDp = defaults.widgetHeightDp,
                        widgetHorizontalPaddingDp = defaults.widgetHorizontalPaddingDp,
                        widgetVerticalPaddingDp = defaults.widgetVerticalPaddingDp,
                        widgetDockGapDp = defaults.widgetDockGapDp,
                    )
                }
            },
        )
    }
}

@Composable
private fun SearchAppsPage(settings: LauncherSettings, update: Update) {
    val search = settings.search
    PreviewedSearchPage(settings, SearchPreviewMode.RESULTS) { preview ->
        SettingsSwitchRow(
            title = stringResource(R.string.search_settings_one_row),
            summary = stringResource(R.string.settings_search_compact_count_summary,
                if (search.maxAppResults > 0) search.maxAppResults else 5),
            checked = search.maxAppResults > 0,
            onCheckedChange = { on ->
                val maximum = if (on) 5 else 0
                preview { copy(maxAppResults = maximum) }
                update.search { it.copy(maxAppResults = maximum) }
            },
        )
        SettingsChoiceRow(
            title = stringResource(R.string.search_app_priority_direction),
            options = SearchAppPriorityDirection.entries,
            selected = search.appPriorityDirection,
            optionLabel = { stringResource(it.labelRes()) },
            optionSummary = { stringResource(it.summaryRes()) },
            enabled = search.maxAppResults > 0,
            onSelect = { direction ->
                preview { copy(appPriorityDirection = direction) }
                update.search { it.copy(appPriorityDirection = direction) }
            },
        )
        SettingsInfoText(stringResource(if (search.maxAppResults > 0) R.string.search_app_priority_one_row_only
            else R.string.search_app_priority_enable_row))
        SettingsSwitchRow(
            title = stringResource(R.string.search_settings_folders),
            summary = stringResource(R.string.search_settings_folders_summary),
            checked = search.foldersEnabled,
            onCheckedChange = { on -> update.search { it.copy(foldersEnabled = on) } },
        )
        SettingsSwitchRow(
            title = stringResource(R.string.settings_search_shortcuts),
            summary = stringResource(R.string.settings_search_shortcuts_summary),
            checked = search.shortcutsEnabled,
            onCheckedChange = { on -> update.search { it.copy(shortcutsEnabled = on) } },
        )
        SettingsInfoText(stringResource(R.string.settings_search_integrations), details = stringResource(R.string.settings_search_integrations_details))
        SettingsSwitchRow(
            title = stringResource(R.string.settings_search_play_store),
            checked = search.playStoreRow,
            onCheckedChange = { on -> update.search { it.copy(playStoreRow = on) } },
        )
    }
}

@Composable
private fun SearchSuggestionsPage(settings: LauncherSettings, update: Update) {
    val deps = LocalHomeDependencies.current
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        SearchHistoryLivePreview(settings)
        SettingsPage(Modifier.weight(1f)) {
            SearchHistorySettings(settings.search, { transform -> update.search(transform) }, deps.searchHistoryRepository, scope)
        }
    }
}

@Composable
private fun SearchContactsPage(settings: LauncherSettings, update: Update) {
    val context = LocalContext.current
    var contactsDenied by rememberSaveable { mutableStateOf(false) }
    // Observed, so granting access redraws this page even when contactsEnabled was already true.
    var hasPermission by rememberPermissionGranted(Manifest.permission.READ_CONTACTS)
    val contactsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        contactsDenied = !granted
        if (granted) update.search { it.copy(contactsEnabled = true) }
    }
    val enabled = settings.search.contactsEnabled && hasPermission
    // The real card with a sample contact, pinned above the settings so it updates as buttons change.
    Column(Modifier.fillMaxSize()) {
        SearchLivePreview(settings.copy(search = settings.search.copy(contactsEnabled = enabled)), SearchPreviewMode.CONTACTS)
        SettingsPage(Modifier.weight(1f)) {
            SettingsMainSwitch(
                title = stringResource(R.string.settings_search_contacts),
                checked = enabled,
                onCheckedChange = { on ->
                    if (!on) {
                        update.search { it.copy(contactsEnabled = false) }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && hasPermission) {
                            context.revokeSelfPermissionOnKill(Manifest.permission.READ_CONTACTS)
                        }
                    } else if (hasPermission) {
                        contactsDenied = false
                        update.search { it.copy(contactsEnabled = true) }
                    } else {
                        // The only place READ_CONTACTS is requested: the user is turning contacts on.
                        contactsPermission.launch(Manifest.permission.READ_CONTACTS)
                    }
                },
            )
            if (contactsDenied) {
                SettingsInfoText(stringResource(R.string.settings_search_contacts_denied))
                SettingsNavigationRow(
                    title = stringResource(R.string.settings_search_contacts_open_app_settings),
                    onClick = { context.openAppDetails() },
                )
            } else if (!enabled) {
                SettingsInfoText(stringResource(R.string.search_contacts_off))
            }
            if (enabled) ContactCardSettings(settings, update)
        }
    }
}

@Composable
private fun SearchWebPage(settings: LauncherSettings, update: Update) {
    val search = settings.search
    PreviewedSearchPage(settings, SearchPreviewMode.WEB) { _ ->
        SettingsMainSwitch(
            title = stringResource(R.string.search_web_main_switch),
            checked = search.showWebSuggestionRow,
            onCheckedChange = { on -> update.search { it.copy(showWebSuggestionRow = on) } },
        )
        if (!search.showWebSuggestionRow) {
            SettingsInfoText(stringResource(R.string.search_web_off))
            return@PreviewedSearchPage
        }
        SettingsInfoText(stringResource(R.string.settings_search_web_row_summary))
        WebSearchEngineChoiceRow(
            selected = search.webEngine,
            onSelect = { engine -> update.search { it.copy(webEngine = engine) } },
        )
        if (WebSuggestionProviders.supports(search.webEngine)) {
            SettingsSwitchRow(
                title = stringResource(R.string.settings_search_online_suggestions),
                summary = stringResource(R.string.settings_search_online_suggestions_summary, WebSearchUrls.engineName(search.webEngine)),
                checked = search.onlineSuggestions,
                onCheckedChange = { on -> update.search { it.copy(onlineSuggestions = on) } },
            )
        } else {
            SettingsInfoText(stringResource(R.string.settings_search_suggestions_supported))
        }
    }
}

@Composable
private fun SearchAnswersPage(settings: LauncherSettings, update: Update) {
    val search = settings.search
    PreviewedSearchPage(settings, SearchPreviewMode.ANSWERS) { _ ->
        SettingsSwitchRow(
            title = stringResource(R.string.settings_search_calculator),
            summary = stringResource(R.string.settings_search_calculator_summary),
            checked = search.calculatorEnabled,
            onCheckedChange = { on -> update.search { it.copy(calculatorEnabled = on) } },
        )
        SettingsSwitchRow(
            title = stringResource(R.string.settings_search_micro_results),
            summary = stringResource(R.string.settings_search_micro_results_summary),
            checked = search.microResultsEnabled,
            onCheckedChange = { on -> update.search { it.copy(microResultsEnabled = on) } },
        )
        SettingsInfoText(stringResource(R.string.settings_search_micro_examples), details = stringResource(R.string.settings_search_micro_details))
    }
}

/**
 * Two independent controls, as the user asked: learning (recording) and showing suggestions, plus
 * clearing. Turning learning off asks whether to keep or clear what was learned.
 */
@Composable
private fun SearchHistorySettings(
    search: SearchSettings,
    changeSearch: ((SearchSettings) -> SearchSettings) -> Unit,
    history: SearchHistoryRepository?,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    val entries by (history?.entries ?: androidx.compose.runtime.remember { MutableStateFlow(emptyList()) }).collectAsState()
    var prompt by rememberSaveable { mutableStateOf<String?>(null) }
    val hasHistory = entries.isNotEmpty()
    SettingsSectionHeader(stringResource(R.string.settings_search_history_header))
    SettingsSwitchRow(
        title = stringResource(R.string.settings_search_history_show),
        summary = stringResource(R.string.settings_search_history_show_summary),
        checked = search.historySuggestionsEnabled,
        onCheckedChange = { on -> changeSearch { copy(historySuggestionsEnabled = on) } },
    )
    SettingsSwitchRow(
        title = stringResource(R.string.settings_search_history_learn),
        summary = stringResource(
            if (!search.historyLearningEnabled && hasHistory) R.string.settings_search_history_learn_paused
            else R.string.settings_search_history_learn_summary,
        ),
        checked = search.historyLearningEnabled,
        onCheckedChange = { on ->
            if (on || !hasHistory) changeSearch { copy(historyLearningEnabled = on) } else prompt = PROMPT_PAUSE
        },
    )
    SettingsNavigationRow(
        title = stringResource(R.string.search_history_clear),
        enabled = hasHistory,
        onClick = { prompt = PROMPT_CLEAR },
    )
    SettingsInfoText(stringResource(R.string.settings_search_history_not_backed_up))
    val clear = { scope.launch { history?.clear() }; Unit }
    when (prompt) {
        PROMPT_CLEAR -> PromptSheet(
            PromptSpec(
                icon = R.drawable.ic_search_history,
                title = stringResource(R.string.search_history_clear_title),
                message = stringResource(R.string.search_history_clear_message),
                primary = PromptAction(stringResource(R.string.search_history_clear_confirm), clear),
                dismissLabel = stringResource(R.string.search_history_cancel),
                tone = PromptTone.DESTRUCTIVE,
            ),
        ) { prompt = null }
        PROMPT_PAUSE -> PromptSheet(
            PromptSpec(
                icon = R.drawable.ic_search_history,
                title = stringResource(R.string.settings_search_history_pause_title),
                message = stringResource(R.string.settings_search_history_pause_message),
                primary = PromptAction(stringResource(R.string.settings_search_history_pause_keep)) {
                    changeSearch { copy(historyLearningEnabled = false) }
                },
                secondary = PromptAction(stringResource(R.string.search_history_clear_confirm)) {
                    changeSearch { copy(historyLearningEnabled = false) }
                    clear()
                },
                dismissLabel = stringResource(R.string.search_history_cancel),
            ),
        ) { prompt = null }
    }
}

private const val PROMPT_CLEAR = "clear"
private const val PROMPT_PAUSE = "pause"

internal fun SearchResultsAnchor.labelRes(): Int = when (this) {
    SearchResultsAnchor.TOP -> R.string.search_settings_results_top
    SearchResultsAnchor.KEYBOARD -> R.string.search_settings_results_keyboard
}

private fun HomeSearchBarPosition.labelRes(): Int = when (this) {
    HomeSearchBarPosition.TOP -> R.string.settings_search_position_top
    HomeSearchBarPosition.BELOW_DOCK -> R.string.settings_search_position_below_dock
    HomeSearchBarPosition.NONE -> R.string.settings_search_position_none
}

private fun Context.openAppDetails() {
    try {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
    } catch (_: ActivityNotFoundException) {
        // No system settings screen; the info text already explains what to do.
    }
}
