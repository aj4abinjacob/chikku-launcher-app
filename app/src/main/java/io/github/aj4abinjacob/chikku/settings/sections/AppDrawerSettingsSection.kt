package io.github.aj4abinjacob.chikku.settings.sections

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.GestureAction
import io.github.aj4abinjacob.chikku.core.model.drawer.DrawerModel
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerFastScrollerSide
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerFastScrollerStyle
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerOpenMethod
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerSearchBarPosition
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerSearchBarStyle
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerSettings
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerStyle
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerTabBarPosition
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerTabStyle
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerTransition
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.settings.components.LabelSettingsControls
import io.github.aj4abinjacob.chikku.settings.components.SettingsChoiceRow
import io.github.aj4abinjacob.chikku.settings.components.SettingsColorRow
import io.github.aj4abinjacob.chikku.settings.components.SettingsInfoText
import io.github.aj4abinjacob.chikku.settings.components.SettingsIntSliderRow
import io.github.aj4abinjacob.chikku.settings.components.SettingsMainSwitch
import io.github.aj4abinjacob.chikku.settings.components.SettingsPage
import io.github.aj4abinjacob.chikku.settings.components.SettingsSectionHeader
import io.github.aj4abinjacob.chikku.settings.components.SettingsSliderRow
import io.github.aj4abinjacob.chikku.settings.components.SettingsSubPage
import io.github.aj4abinjacob.chikku.settings.components.SettingsSubPageLink
import io.github.aj4abinjacob.chikku.settings.components.SettingsSubPageRow
import io.github.aj4abinjacob.chikku.settings.components.SettingsSwitchRow
import io.github.aj4abinjacob.chikku.settings.components.percentageValueLabel
import io.github.aj4abinjacob.chikku.settings.components.rememberSettingsSubPage
import io.github.aj4abinjacob.chikku.settings.components.settingsSummary
import io.github.aj4abinjacob.chikku.settings.drawer.DrawerGridPreview
import io.github.aj4abinjacob.chikku.settings.drawer.DrawerGroupMembersScreen
import io.github.aj4abinjacob.chikku.settings.drawer.DrawerSearchBarSample
import io.github.aj4abinjacob.chikku.settings.drawer.DrawerTabBarSample
import io.github.aj4abinjacob.chikku.settings.drawer.drawerSampleTabTitles
import io.github.aj4abinjacob.chikku.settings.drawer.DrawerGroupsScreen
import io.github.aj4abinjacob.chikku.settings.drawer.HiddenAppsScreen
import io.github.aj4abinjacob.chikku.settings.previews.DrawerLayoutLivePreview
import io.github.aj4abinjacob.chikku.settings.previews.FastScrollerLivePreview
import io.github.aj4abinjacob.chikku.settings.previews.DrawerOpenPreview
import io.github.aj4abinjacob.chikku.settings.previews.DrawerSamplePreview
import io.github.aj4abinjacob.chikku.settings.previews.DrawerSearchPositionPreview
import io.github.aj4abinjacob.chikku.settings.previews.DrawerStylePreview
import io.github.aj4abinjacob.chikku.settings.previews.DrawerTabPositionPreview
import io.github.aj4abinjacob.chikku.settings.previews.DrawerTabStylePreview
import io.github.aj4abinjacob.chikku.settings.previews.DrawerTransitionPreview
import io.github.aj4abinjacob.chikku.settings.previews.FastScrollerPreview
import kotlin.math.roundToInt

/** Sub-pages of the App drawer section; also the targets of settings search results. */
internal object DrawerPages {
    val Scrolling = SettingsSubPageLink("scrolling", R.string.paging_settings_title)
    val VerticalScrolling = SettingsSubPageLink("vertical_scrolling", R.string.vertical_scroll_settings_title)
    val Grid = SettingsSubPageLink("grid", R.string.drawer_page_grid)
    val FastScroller = SettingsSubPageLink("fast_scroller", R.string.drawer_settings_fast_scroller)
    val Tabs = SettingsSubPageLink("tabs", R.string.drawer_page_tabs)
    val Search = SettingsSubPageLink("search", R.string.drawer_page_search)
    val Opening = SettingsSubPageLink("opening", R.string.drawer_page_opening)
    val Background = SettingsSubPageLink("background", R.string.drawer_settings_appearance)
    val HiddenApps = SettingsSubPageLink("hidden", R.string.drawer_settings_hidden_apps)
    val Groups = SettingsSubPageLink("groups", R.string.drawer_settings_groups)

    /** One group's members; the route is [GROUP_PREFIX] + group id and its parent is [Groups]. */
    const val GROUP_PREFIX = "group:"

    val all = listOf(Grid, Scrolling, VerticalScrolling, FastScroller, Tabs, Search, Opening, Background, HiddenApps, Groups)
}

private typealias SettingsUpdate = ((LauncherSettings) -> LauncherSettings) -> Unit

/**
 * App drawer (G13/G14). A short main page (scrolling style plus one row per feature showing its
 * current state) opens one sub-page per feature; Back returns to the main page.
 */
@Composable
fun AppDrawerSettingsSection(settings: LauncherSettings, update: SettingsUpdate) {
    var page by rememberSettingsSubPage()
    val route = page
    val groupId = route?.takeIf { it.startsWith(DrawerPages.GROUP_PREFIX) }?.removePrefix(DrawerPages.GROUP_PREFIX)
    val link = if (groupId != null) DrawerPages.Groups else DrawerPages.all.firstOrNull { it.route == route }
    if (link == null) {
        DrawerMainPage(settings, update, open = { page = it.route })
        return
    }
    SettingsSubPage(
        title = stringResource(link.title),
        onBack = { page = if (groupId != null) DrawerPages.Groups.route else null },
    ) {
        when {
            groupId != null -> DrawerGroupMembersScreen(groupId, settings) { page = DrawerPages.Groups.route }
            link == DrawerPages.Scrolling -> HorizontalScrollingSettingsPage(settings, true, update)
            link == DrawerPages.VerticalScrolling -> VerticalScrollingSettingsPage(settings, update, fastScrollerSummary(settings.drawer)) {
                page = DrawerPages.FastScroller.route
            }
            link == DrawerPages.Grid -> DrawerGridPage(settings, update)
            link == DrawerPages.FastScroller -> DrawerFastScrollerPage(settings, update)
            link == DrawerPages.Tabs -> DrawerTabsPage(settings, update)
            link == DrawerPages.Search -> DrawerSearchPage(settings, update)
            link == DrawerPages.Opening -> DrawerOpeningPage(settings, update)
            link == DrawerPages.Background -> DrawerBackgroundPage(settings, update)
            link == DrawerPages.HiddenApps -> HiddenAppsScreen(settings.drawer, settings.home.iconScale)
            link == DrawerPages.Groups -> DrawerGroupsScreen(settings, update) { id -> page = DrawerPages.GROUP_PREFIX + id }
            else -> Unit
        }
    }
}

private fun SettingsUpdate.drawer(transform: (DrawerSettings) -> DrawerSettings) =
    this { it.copy(drawer = transform(it.drawer)) }

@Composable
private fun DrawerMainPage(settings: LauncherSettings, update: SettingsUpdate, open: (SettingsSubPageLink) -> Unit) {
    val drawer = settings.drawer
    Column(Modifier.fillMaxSize()) {
        DrawerLayoutLivePreview(settings)
        SettingsPage(Modifier.weight(1f)) {
            SettingsSectionHeader(stringResource(R.string.drawer_settings_layout))
            SettingsChoiceRow(
                title = stringResource(R.string.drawer_settings_style),
                optionPreview = { DrawerStylePreview(it) },
                options = DrawerStyle.entries.toList(),
                icon = R.drawable.ic_settings_row_swap_vert,
                selected = drawer.style,
                optionLabel = { stringResource(styleLabel(it)) },
                onSelect = { style -> update.drawer { it.copy(style = style) } },
            )
            if (drawer.style == DrawerStyle.HORIZONTAL_PAGED) {
                SettingsSubPageRow(stringResource(R.string.paging_settings_title), stringResource(R.string.paging_settings_summary), icon = R.drawable.ic_settings_row_view_carousel) { open(DrawerPages.Scrolling) }
                SettingsSwitchRow(
                    title = stringResource(R.string.drawer_settings_infinite_scroll),
                    summary = stringResource(R.string.drawer_settings_infinite_scroll_summary),
                    checked = drawer.infiniteScroll,
                    onCheckedChange = { on -> update.drawer { it.copy(infiniteScroll = on) } },
                    icon = R.drawable.ic_settings_row_all_inclusive,
                )
            } else {
                SettingsSubPageRow(stringResource(R.string.vertical_scroll_settings_title), stringResource(R.string.vertical_scroll_settings_summary),
                    icon = R.drawable.ic_settings_row_vertical_scroll) { open(DrawerPages.VerticalScrolling) }
            }
            SettingsSubPageRow(stringResource(R.string.drawer_page_grid), gridSummary(settings), icon = R.drawable.ic_settings_row_grid_view) { open(DrawerPages.Grid) }

            SettingsSectionHeader(stringResource(R.string.drawer_settings_customize))
            SettingsSubPageRow(stringResource(R.string.drawer_settings_fast_scroller), fastScrollerSummary(drawer), icon = R.drawable.ic_settings_row_sort_by_alpha) {
                open(DrawerPages.FastScroller)
            }
            SettingsSubPageRow(stringResource(R.string.drawer_page_tabs), tabsSummary(drawer), icon = R.drawable.ic_settings_row_tab) { open(DrawerPages.Tabs) }
            SettingsSubPageRow(stringResource(R.string.drawer_page_search), stringResource(searchPositionLabel(drawer.searchBarPosition)), icon = R.drawable.ic_search) {
                open(DrawerPages.Search)
            }
            SettingsSubPageRow(
                stringResource(R.string.drawer_page_opening),
                settingsSummary(stringResource(openMethodLabel(drawer.openMethod)), stringResource(transitionLabel(drawer.transition))),
                icon = R.drawable.ic_settings_row_swipe_up,
            ) { open(DrawerPages.Opening) }
            SettingsSubPageRow(
                stringResource(R.string.drawer_settings_appearance),
                stringResource(R.string.drawer_summary_background, (drawer.backgroundAlpha * 100).roundToInt()),
                icon = R.drawable.ic_settings_row_format_color_fill,
            ) { open(DrawerPages.Background) }

            SettingsSectionHeader(stringResource(R.string.drawer_settings_organize))
            SettingsSubPageRow(stringResource(R.string.drawer_settings_hidden_apps), stringResource(R.string.drawer_settings_hidden_apps_summary), icon = R.drawable.ic_settings_row_visibility_off) {
                open(DrawerPages.HiddenApps)
            }
            SettingsSubPageRow(stringResource(R.string.drawer_settings_groups), stringResource(R.string.drawer_settings_groups_summary), icon = R.drawable.ic_settings_row_category) {
                open(DrawerPages.Groups)
            }
        }
    }
}

@Composable
private fun gridSummary(settings: LauncherSettings): String? {
    val drawer = settings.drawer
    val columns = when {
        drawer.style == DrawerStyle.LIST -> stringResource(R.string.drawer_settings_style_list)
        drawer.columns == DrawerSettings.AUTO_COLUMNS -> stringResource(R.string.drawer_summary_auto_columns)
        else -> pluralStringResource(R.plurals.drawer_summary_columns, drawer.columns, drawer.columns)
    }
    val scale = if (drawer.matchDesktopSize) settings.home.iconScale else drawer.iconScale
    return settingsSummary(columns, stringResource(R.string.drawer_summary_icon_size, (scale * 100).roundToInt()))
}

@Composable
internal fun fastScrollerSummary(drawer: DrawerSettings): String? = when {
    drawer.style == DrawerStyle.HORIZONTAL_PAGED -> stringResource(R.string.drawer_summary_fast_scroller_paged)
    !drawer.showFastScroller -> stringResource(R.string.settings_state_off)
    else -> settingsSummary(
        stringResource(scrollerStyleLabel(drawer.fastScrollerStyle)),
        stringResource(scrollerSideLabel(drawer.fastScrollerSide)),
    )
}

@Composable
private fun tabsSummary(drawer: DrawerSettings): String? =
    if (!drawer.groupsEnabled && !drawer.showWorkTab) {
        stringResource(R.string.settings_state_off)
    } else {
        settingsSummary(
            stringResource(R.string.drawer_summary_tabs_groups).takeIf { drawer.groupsEnabled },
            stringResource(R.string.drawer_summary_tabs_work).takeIf { drawer.showWorkTab },
            stringResource(tabPositionLabel(drawer.tabBarPosition)),
        )
    }

/** Columns, rows, icon size and labels. */
@Composable
private fun DrawerGridPage(settings: LauncherSettings, update: SettingsUpdate) {
    val drawer = settings.drawer
    val paged = drawer.style == DrawerStyle.HORIZONTAL_PAGED
    val listStyle = drawer.style == DrawerStyle.LIST
    val autoColumns = drawer.columns == DrawerSettings.AUTO_COLUMNS
    val manualRows = paged || (!listStyle && !drawer.automaticRowSpacing)
    val columnsRange = gridRange(settings, drawer.columns)
    val rowsRange = gridRange(settings, drawer.rows)
    // Follows the icon and label size sliders while dragging; each setting is saved on release.
    var live by remember(drawer) { mutableStateOf(drawer) }
    Column(Modifier.fillMaxSize()) {
        // Actual size keeps icon/label steps legible; row changes switch to the full grid so every row shows.
        DrawerGridPreview(live, settings.night, settings.home.iconScale)
        SettingsPage(Modifier.weight(1f)) {
            SettingsSectionHeader(stringResource(R.string.drawer_grid_header_grid))
            if (listStyle) {
                SettingsInfoText(stringResource(R.string.drawer_grid_list_note))
            } else {
                SettingsSwitchRow(
                    title = stringResource(R.string.drawer_settings_auto_columns),
                    checked = autoColumns,
                    summary = stringResource(R.string.settings_drawer_auto_columns_summary),
                    onCheckedChange = { auto ->
                        update.drawer { it.copy(columns = if (auto) DrawerSettings.AUTO_COLUMNS else DEFAULT_FIXED_COLUMNS) }
                    },
                )
                if (!autoColumns) SettingsIntSliderRow(
                    title = stringResource(R.string.drawer_settings_columns),
                    value = drawer.columns.coerceIn(columnsRange),
                    onValueChangeFinished = { columns -> update.drawer { it.copy(columns = columns) } },
                    onValueChanging = { columns -> live = live.copy(columns = columns) },
                    range = columnsRange,
                )
            }
            if (!paged && !listStyle) {
                // Stored as automaticRowSpacing; the switch names the outcome of turning it off.
                SettingsSwitchRow(
                    title = stringResource(R.string.drawer_settings_fit_rows),
                    summary = stringResource(R.string.drawer_settings_fit_rows_summary),
                    checked = !drawer.automaticRowSpacing,
                    onCheckedChange = { fit -> update.drawer { it.copy(automaticRowSpacing = !fit) } },
                )
            }
            if (manualRows) SettingsIntSliderRow(
                title = stringResource(if (paged) R.string.drawer_settings_rows else R.string.drawer_settings_visible_rows),
                value = drawer.rows.coerceIn(rowsRange),
                onValueChangeFinished = { rows -> update.drawer { it.copy(rows = rows) } },
                onValueChanging = { rows -> live = live.copy(rows = rows) },
                range = rowsRange,
            )
            if (!paged && manualRows) {
                SettingsInfoText(stringResource(R.string.drawer_settings_rows_summary))
            }
            if (!settings.labs.extendedGrid &&
                ((!autoColumns && !listStyle && drawer.columns > NORMAL_GRID_MAX) || (manualRows && drawer.rows > NORMAL_GRID_MAX))
            ) {
                SettingsInfoText(stringResource(R.string.settings_grid_saved_size_summary, NORMAL_GRID_MAX))
            }

            SettingsSectionHeader(stringResource(R.string.drawer_grid_header_icons))
            SettingsSwitchRow(
                title = stringResource(R.string.drawer_settings_match_desktop),
                summary = stringResource(R.string.drawer_settings_match_desktop_summary).takeIf { drawer.matchDesktopSize },
                checked = drawer.matchDesktopSize,
                onCheckedChange = { on -> update.drawer { it.copy(matchDesktopSize = on) } },
            )
            if (!drawer.matchDesktopSize) SettingsSliderRow(
                title = stringResource(R.string.drawer_settings_icon_size),
                value = drawer.iconScale,
                onValueChangeFinished = { scale -> update.drawer { it.copy(iconScale = (scale * 20).roundToInt() / 20f) } },
                valueRange = LauncherSettings.MIN_SCALE..LauncherSettings.MAX_SCALE,
                steps = 19,
                valueLabel = percentageValueLabel(),
                onValueChanging = { scale -> live = live.copy(iconScale = scale) },
            )

            SettingsSectionHeader(stringResource(R.string.drawer_grid_header_labels))
            SettingsSwitchRow(
                title = stringResource(R.string.drawer_settings_labels),
                checked = drawer.showLabels,
                onCheckedChange = { on -> update.drawer { it.copy(showLabels = on) } },
            )
            if (drawer.showLabels) LabelSettingsControls(
                labelScale = drawer.labelScale,
                labelFont = drawer.labelFont,
                labelShadow = drawer.labelShadow,
                labelSingleLine = drawer.labelSingleLine,
                labelColor = drawer.labelColor,
                onScaleChange = { value -> update.drawer { it.copy(labelScale = value) } },
                onFontChange = { value -> update.drawer { it.copy(labelFont = value) } },
                onShadowChange = { value -> update.drawer { it.copy(labelShadow = value) } },
                onSingleLineChange = { value -> update.drawer { it.copy(labelSingleLine = value) } },
                onColorChange = { value -> update.drawer { it.copy(labelColor = value) } },
                onScaleChanging = { value -> live = live.copy(labelScale = value) },
            )
        }
    }
}

/** Main switch, live placement preview, style, side, spacing, letter bubble and accent. */
@Composable
private fun DrawerFastScrollerPage(settings: LauncherSettings, update: SettingsUpdate) {
    val drawer = settings.drawer
    var liveGap by remember(drawer.fastScrollerGapDp) { mutableIntStateOf(drawer.fastScrollerGapDp) }
    Column(Modifier.fillMaxSize()) {
        if (drawer.showFastScroller && drawer.style != DrawerStyle.HORIZONTAL_PAGED) {
            FastScrollerLivePreview(drawer.copy(fastScrollerGapDp = liveGap), settings.night, speed = settings.lookFeel.animationSpeed)
        }
        SettingsPage(Modifier.weight(1f)) {
            if (drawer.style == DrawerStyle.HORIZONTAL_PAGED) {
                SettingsInfoText(stringResource(R.string.drawer_settings_fast_scroller_paged_hint))
            } else {
                FastScrollerControls(drawer, update) { liveGap = it }
            }
        }
    }
}

@Composable
private fun FastScrollerControls(drawer: DrawerSettings, update: SettingsUpdate, onGapChanging: (Int) -> Unit) {
    SettingsMainSwitch(
        title = stringResource(R.string.drawer_settings_fast_scroller_use),
        checked = drawer.showFastScroller,
        onCheckedChange = { on -> update.drawer { it.copy(showFastScroller = on) } },
    )
    if (!drawer.showFastScroller) {
        SettingsInfoText(stringResource(R.string.drawer_settings_fast_scroller_off_hint))
        return
    }
    val resources = LocalResources.current
    SettingsChoiceRow(
        title = stringResource(R.string.drawer_settings_scroller_style),
        optionPreview = { FastScrollerPreview(it) },
        optionSummary = { stringResource(if (it == DrawerFastScrollerStyle.ALPHABET) R.string.hint_scroller_alphabet else R.string.hint_scroller_thumb) },
        options = DrawerFastScrollerStyle.entries,
        selected = drawer.fastScrollerStyle,
        optionLabel = { stringResource(scrollerStyleLabel(it)) },
        onSelect = { value -> update.drawer { it.copy(fastScrollerStyle = value) } },
    )
    SettingsChoiceRow(
        title = stringResource(R.string.drawer_settings_scroller_side),
        options = DrawerFastScrollerSide.entries,
        selected = drawer.fastScrollerSide,
        optionLabel = { stringResource(scrollerSideLabel(it)) },
        onSelect = { value -> update.drawer { it.copy(fastScrollerSide = value) } },
        inlineChoices = true,
    )
    SettingsIntSliderRow(
        title = stringResource(R.string.drawer_settings_scroller_gap),
        value = drawer.fastScrollerGapDp,
        onValueChangeFinished = { value -> update.drawer { it.copy(fastScrollerGapDp = value) } },
        onValueChanging = onGapChanging,
        range = DrawerSettings.FAST_SCROLLER_GAP_RANGE_DP,
        valueLabel = { resources.getString(R.string.drawer_settings_scroller_gap_value, it) },
    )
    SettingsSwitchRow(
        title = stringResource(R.string.drawer_settings_scroller_popup),
        summary = stringResource(R.string.drawer_settings_scroller_popup_summary),
        checked = drawer.fastScrollerPopup,
        onCheckedChange = { on -> update.drawer { it.copy(fastScrollerPopup = on) } },
    )
    SettingsColorRow(
        title = stringResource(R.string.drawer_settings_fast_scroller_color),
        color = drawer.fastScrollerColor,
        onColorChange = { color -> update.drawer { it.copy(fastScrollerColor = color) } },
    )
}

/** What becomes a tab, then how the tab bar looks once there is one. */
@Composable
private fun DrawerTabsPage(settings: LauncherSettings, update: SettingsUpdate) {
    val drawer = settings.drawer
    Column(Modifier.fillMaxSize()) {
        // The scene shows where the bar sits; the true-size strip shows its tabs, style and accent.
        DrawerLayoutLivePreview(settings, description = tabsPreviewDescription(drawer), pageControls = false)
        DrawerTabBarSample(drawer, settings.night)
        SettingsPage(Modifier.weight(1f)) {
            SettingsSwitchRow(
                title = stringResource(R.string.drawer_settings_groups_enabled),
                summary = stringResource(R.string.drawer_groups_explanation),
                checked = drawer.groupsEnabled,
                onCheckedChange = { on -> update.drawer { it.copy(groupsEnabled = on) } },
            )
            SettingsSwitchRow(
                title = stringResource(R.string.drawer_settings_work_tab),
                summary = stringResource(R.string.drawer_settings_work_tab_summary),
                checked = drawer.showWorkTab,
                onCheckedChange = { on -> update.drawer { it.copy(showWorkTab = on) } },
            )
            if (!drawer.groupsEnabled && !drawer.showWorkTab) {
                SettingsInfoText(stringResource(R.string.drawer_tabs_off_hint))
            } else {
                TabBarControls(drawer, update)
            }
        }
    }
}

@Composable
private fun TabBarControls(drawer: DrawerSettings, update: SettingsUpdate) {
    SettingsChoiceRow(
        title = stringResource(R.string.drawer_settings_tab_position),
        optionPreview = { DrawerTabPositionPreview(it) },
        options = DrawerTabBarPosition.entries,
        selected = drawer.tabBarPosition,
        optionLabel = { stringResource(tabPositionLabel(it)) },
        onSelect = { value -> update.drawer { it.copy(tabBarPosition = value) } },
    )
    SettingsChoiceRow(
        title = stringResource(R.string.drawer_settings_tab_style),
        optionPreview = { DrawerTabStylePreview(it) },
        optionSummary = { stringResource(if (it == DrawerTabStyle.COMPACT) R.string.hint_drawer_tabs_compact else R.string.hint_drawer_tabs_segmented) },
        options = DrawerTabStyle.entries,
        selected = drawer.tabStyle,
        optionLabel = { stringResource(if (it == DrawerTabStyle.COMPACT) R.string.drawer_tab_style_compact else R.string.drawer_tab_style_segmented) },
        onSelect = { value -> update.drawer { it.copy(tabStyle = value) } },
    )
    SettingsColorRow(
        title = stringResource(R.string.drawer_settings_tab_accent),
        color = drawer.tabAccentColor,
        onColorChange = { color -> update.drawer { it.copy(tabAccentColor = color) } },
    )
}

@Composable
private fun DrawerSearchPage(settings: LauncherSettings, update: SettingsUpdate) {
    val drawer = settings.drawer
    Column(Modifier.fillMaxSize()) {
        // The scene shows position and pull-to-search; the true-size strip shows the field style.
        DrawerLayoutLivePreview(settings, searchInteraction = true, description = searchPreviewDescription(drawer), pageControls = false)
        DrawerSearchBarSample(drawer, settings.night)
        SettingsPage(Modifier.weight(1f)) {
            SettingsChoiceRow(
                title = stringResource(R.string.drawer_settings_search_position),
                optionPreview = { DrawerSearchPositionPreview(it) },
                options = DrawerSearchBarPosition.entries.toList(),
                selected = drawer.searchBarPosition,
                optionLabel = { stringResource(searchPositionLabel(it)) },
                onSelect = { position -> update.drawer { it.copy(searchBarPosition = position) } },
            )
            if (drawer.searchBarPosition == DrawerSearchBarPosition.HIDDEN) {
                SettingsInfoText(stringResource(R.string.drawer_search_hidden_hint))
            } else {
                SettingsChoiceRow(
                    title = stringResource(R.string.drawer_settings_search_style),
                    options = DrawerSearchBarStyle.entries.toList(),
                    selected = drawer.searchBarStyle,
                    optionLabel = { stringResource(searchStyleLabel(it)) },
                    optionSummary = {
                        stringResource(
                            when (it) {
                                DrawerSearchBarStyle.SUBTLE -> R.string.drawer_settings_search_style_subtle_summary
                                DrawerSearchBarStyle.OUTLINED -> R.string.drawer_settings_search_style_outlined_summary
                                DrawerSearchBarStyle.HIGH_CONTRAST -> R.string.drawer_settings_search_style_high_contrast_summary
                            },
                        )
                    },
                    onSelect = { style -> update.drawer { it.copy(searchBarStyle = style) } },
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_drawer_open_keyboard),
                    checked = drawer.openKeyboardOnOpen,
                    onCheckedChange = { on -> update.drawer { it.copy(openKeyboardOnOpen = on) } },
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.drawer_settings_pull_to_search),
                    summary = stringResource(R.string.drawer_settings_pull_to_search_summary),
                    checked = drawer.pullToSearch,
                    onCheckedChange = { on -> update.drawer { it.copy(pullToSearch = on) } },
                )
            }
        }
    }
}

@Composable
private fun DrawerOpeningPage(settings: LauncherSettings, update: SettingsUpdate) {
    val drawer = settings.drawer
    val swipeOpensDrawer = settings.gestures.swipeUp == GestureAction.OpenDrawer
    Column(Modifier.fillMaxSize()) {
        DrawerLayoutLivePreview(settings, opening = true)
        SettingsPage(Modifier.weight(1f)) {
            SettingsChoiceRow(
                title = stringResource(R.string.drawer_settings_open_method),
                optionPreview = { DrawerOpenPreview(it) },
                options = DrawerOpenMethod.entries.toList(),
                selected = drawer.openMethod,
                optionLabel = { stringResource(openMethodLabel(it)) },
                onSelect = { method -> update.drawer { it.copy(openMethod = method) } },
            )
            if (drawer.openMethod == DrawerOpenMethod.SWIPE_UP) {
                SettingsInfoText(stringResource(R.string.drawer_settings_open_swipe_note), details = stringResource(R.string.drawer_settings_open_swipe_note_details))
            }
            SettingsSwitchRow(
                title = stringResource(R.string.drawer_settings_show_arrow),
                summary = stringResource(
                    if (swipeOpensDrawer) R.string.drawer_settings_show_arrow_summary else R.string.drawer_settings_open_swipe_note,
                ),
                checked = DrawerModel.showsOpenButton(drawer.openMethod, swipeOpensDrawer),
                onCheckedChange = { show ->
                    update.drawer { it.copy(openMethod = if (show) DrawerOpenMethod.BOTH else DrawerOpenMethod.SWIPE_UP) }
                },
                enabled = swipeOpensDrawer,
            )
            SettingsChoiceRow(
                title = stringResource(R.string.drawer_settings_transition),
                optionPreview = { DrawerTransitionPreview(it) },
                optionSummary = { transition ->
                    stringResource(
                        when (transition) {
                            DrawerTransition.SLIDE -> R.string.hint_drawer_transition_slide
                            DrawerTransition.FADE -> R.string.hint_drawer_transition_fade
                            DrawerTransition.SCALE -> R.string.hint_drawer_transition_zoom
                        },
                    )
                },
                options = DrawerTransition.entries,
                selected = drawer.transition,
                optionLabel = { stringResource(transitionLabel(it)) },
                onSelect = { value -> update.drawer { it.copy(transition = value) } },
            )
            SettingsSwitchRow(
                title = stringResource(R.string.drawer_settings_automatically_close),
                checked = drawer.automaticallyClose,
                onCheckedChange = { on -> update.drawer { it.copy(automaticallyClose = on) } },
            )
            SettingsSwitchRow(
                title = stringResource(R.string.drawer_settings_remember_position),
                summary = stringResource(R.string.drawer_settings_remember_position_summary),
                checked = drawer.rememberScrollPosition,
                onCheckedChange = { on -> update.drawer { it.copy(rememberScrollPosition = on) } },
            )
        }
    }
}

@Composable
private fun DrawerBackgroundPage(settings: LauncherSettings, update: SettingsUpdate) {
    val drawer = settings.drawer
    // Follows the transparency slider while dragging; the setting is saved on release.
    var liveAlpha by remember(drawer.backgroundAlpha) { mutableFloatStateOf(drawer.backgroundAlpha) }
    Column(Modifier.fillMaxSize()) {
        DrawerSamplePreview(
            drawer.copy(backgroundAlpha = liveAlpha),
            settings.night,
            settings.home.iconScale,
            animationSpeed = settings.lookFeel.animationSpeed,
            lookFeel = settings.lookFeel,
        )
        SettingsPage(Modifier.weight(1f)) {
            SettingsColorRow(
                title = stringResource(R.string.drawer_settings_background_color),
                color = drawer.backgroundColor,
                onColorChange = { color -> update.drawer { it.copy(backgroundColor = color) } },
            )
            SettingsSliderRow(
                title = stringResource(R.string.drawer_settings_background_alpha),
                value = drawer.backgroundAlpha,
                onValueChangeFinished = { alpha -> update.drawer { it.copy(backgroundAlpha = (alpha * 20).roundToInt() / 20f) } },
                valueRange = 0f..1f,
                steps = 19,
                valueLabel = percentageValueLabel(),
                onValueChanging = { alpha -> liveAlpha = alpha },
            )
        }
    }
}

@Composable
private fun tabsPreviewDescription(drawer: DrawerSettings): String {
    val titles = drawerSampleTabTitles(LocalContext.current, drawer)
    if (titles.isEmpty()) return stringResource(R.string.preview_drawer_tabs_off_description)
    return stringResource(R.string.preview_drawer_tabs_description, titles.joinToString(),
        stringResource(if (drawer.tabStyle == DrawerTabStyle.COMPACT) R.string.drawer_tab_style_compact else R.string.drawer_tab_style_segmented),
        stringResource(tabPositionLabel(drawer.tabBarPosition)))
}

@Composable
private fun searchPreviewDescription(drawer: DrawerSettings): String =
    if (drawer.searchBarPosition == DrawerSearchBarPosition.HIDDEN) stringResource(R.string.preview_drawer_search_hidden_description)
    else stringResource(R.string.preview_drawer_search_description, stringResource(searchPositionLabel(drawer.searchBarPosition)),
        stringResource(searchStyleLabel(drawer.searchBarStyle)))

private fun searchStyleLabel(style: DrawerSearchBarStyle): Int = when (style) {
    DrawerSearchBarStyle.SUBTLE -> R.string.drawer_settings_search_style_subtle
    DrawerSearchBarStyle.OUTLINED -> R.string.drawer_settings_search_style_outlined
    DrawerSearchBarStyle.HIGH_CONTRAST -> R.string.drawer_settings_search_style_high_contrast
}

private fun styleLabel(style: DrawerStyle): Int = when (style) {
    DrawerStyle.VERTICAL -> R.string.drawer_settings_style_vertical
    DrawerStyle.HORIZONTAL_PAGED -> R.string.drawer_settings_style_paged
    DrawerStyle.LIST -> R.string.drawer_settings_style_list
}

private fun scrollerStyleLabel(style: DrawerFastScrollerStyle): Int =
    if (style == DrawerFastScrollerStyle.ALPHABET) R.string.drawer_scroller_alphabet else R.string.drawer_scroller_thumb

private fun scrollerSideLabel(side: DrawerFastScrollerSide): Int =
    if (side == DrawerFastScrollerSide.RIGHT) R.string.drawer_scroller_side_right else R.string.drawer_scroller_side_left

private fun tabPositionLabel(position: DrawerTabBarPosition): Int =
    if (position == DrawerTabBarPosition.TOP) R.string.drawer_settings_search_top else R.string.drawer_settings_search_bottom

private fun searchPositionLabel(position: DrawerSearchBarPosition): Int = when (position) {
    DrawerSearchBarPosition.TOP -> R.string.drawer_settings_search_top
    DrawerSearchBarPosition.BOTTOM -> R.string.drawer_settings_search_bottom
    DrawerSearchBarPosition.HIDDEN -> R.string.drawer_settings_search_hidden
}

private fun openMethodLabel(method: DrawerOpenMethod): Int = when (method) {
    DrawerOpenMethod.SWIPE_UP -> R.string.drawer_settings_open_swipe
    DrawerOpenMethod.BUTTON -> R.string.drawer_settings_open_button
    DrawerOpenMethod.BOTH -> R.string.drawer_settings_open_both
}

private fun transitionLabel(transition: DrawerTransition): Int = when (transition) {
    DrawerTransition.SLIDE -> R.string.drawer_settings_transition_slide
    DrawerTransition.FADE -> R.string.drawer_settings_transition_fade
    DrawerTransition.SCALE -> R.string.drawer_settings_transition_scale
}

private const val DEFAULT_FIXED_COLUMNS = 5

