package io.github.aj4abinjacob.chikku.settings.sections

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.github.aj4abinjacob.chikku.home.bridgePackage
import io.github.aj4abinjacob.chikku.home.messageRes
import io.github.aj4abinjacob.chikku.home.openAppDetails
import io.github.aj4abinjacob.chikku.platform.DiscoverHealth
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.message.rememberChikkuMessages
import io.github.aj4abinjacob.chikku.settings.previews.HomeGridPreview
import io.github.aj4abinjacob.chikku.settings.previews.HomeWidgetPreview
import io.github.aj4abinjacob.chikku.settings.previews.HomePagesLivePreview
import io.github.aj4abinjacob.chikku.settings.previews.DiscoverLivePreview
import io.github.aj4abinjacob.chikku.settings.previews.IconSampleLook
import io.github.aj4abinjacob.chikku.settings.previews.IconSamplePreview
import io.github.aj4abinjacob.chikku.settings.previews.PageIndicatorPreview
import io.github.aj4abinjacob.chikku.settings.previews.PageTransitionPreview
import io.github.aj4abinjacob.chikku.core.model.GridSpec
import io.github.aj4abinjacob.chikku.core.model.Workspace
import io.github.aj4abinjacob.chikku.core.model.WorkspaceState
import io.github.aj4abinjacob.chikku.core.model.settings.PageTransitionEffect
import io.github.aj4abinjacob.chikku.core.model.settings.HomeSettings
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.core.model.settings.PageIndicatorStyle
import io.github.aj4abinjacob.chikku.core.model.settings.PaddingSize
import io.github.aj4abinjacob.chikku.home.HiddenItems
import io.github.aj4abinjacob.chikku.settings.LocalHomeDependencies
import io.github.aj4abinjacob.chikku.settings.components.LabelSettingsControls
import io.github.aj4abinjacob.chikku.settings.components.SettingsChoiceRow
import io.github.aj4abinjacob.chikku.settings.components.SettingsStepRow
import io.github.aj4abinjacob.chikku.settings.components.SettingsMainSwitch
import io.github.aj4abinjacob.chikku.settings.components.SettingsSubPage
import io.github.aj4abinjacob.chikku.settings.components.SettingsSubPageLink
import io.github.aj4abinjacob.chikku.settings.components.SettingsSubPageRow
import io.github.aj4abinjacob.chikku.settings.components.rememberSettingsSubPage
import io.github.aj4abinjacob.chikku.settings.components.SettingsInfoText
import io.github.aj4abinjacob.chikku.settings.components.SettingsIntSliderRow
import io.github.aj4abinjacob.chikku.settings.components.SettingsNavigationRow
import io.github.aj4abinjacob.chikku.settings.components.SettingsPage
import io.github.aj4abinjacob.chikku.settings.components.SettingsSectionHeader
import io.github.aj4abinjacob.chikku.settings.components.SettingsSliderRow
import io.github.aj4abinjacob.chikku.settings.components.dpValueLabel
import io.github.aj4abinjacob.chikku.settings.components.percentageValueLabel
import io.github.aj4abinjacob.chikku.settings.components.SettingsSwitchRow
import io.github.aj4abinjacob.chikku.settings.components.spacingLabel
import io.github.aj4abinjacob.chikku.settings.components.layoutChangeControl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.Locale

/** Largest grid offered without Labs › Extended grid. */
internal const val NORMAL_GRID_MAX = 8

/** Preserve the visible saved size when extended grids are disabled or imported from a backup. */
internal fun gridRange(settings: LauncherSettings, savedSize: Int = 0): IntRange =
    LauncherSettings.MIN_GRID..(if (settings.labs.extendedGrid) GridSpec.MAX_DIMENSION
        else maxOf(NORMAL_GRID_MAX, savedSize.coerceAtMost(GridSpec.MAX_DIMENSION)))

/** Sub-pages of the home screen settings; also the deep-link targets of their search entries. */
internal object HomeScreenPages {
    val Grid = SettingsSubPageLink("home_grid", R.string.settings_home_page_grid)
    val Icons = SettingsSubPageLink("home_icons", R.string.settings_home_page_icons)
    val Widgets = SettingsSubPageLink("home_widgets", R.string.settings_home_header_widgets)
    val Pages = SettingsSubPageLink("home_pages", R.string.settings_home_header_pages)
    val Scrolling = SettingsSubPageLink("home_scrolling", R.string.paging_settings_title)
    val Discover = SettingsSubPageLink("home_discover", R.string.settings_home_google_discover)
    val all = listOf(Grid, Icons, Widgets, Pages, Scrolling, Discover)
}

/**
 * Home screen (G02, G12, G17): a short main page with the layout lock and new-app placement, and one
 * sub-page each for grid and spacing, icons and labels, widgets, pages and scrolling, and Discover.
 */
@Composable
fun HomeScreenSettingsSection(settings: LauncherSettings, update: ((LauncherSettings) -> LauncherSettings) -> Unit) {
    val updateHome: ((HomeSettings) -> HomeSettings) -> Unit = { change -> update { it.copy(home = change(it.home)) } }
    val deps = LocalHomeDependencies.current
    val workspaceState by remember(deps) { deps.workspaceRepository.workspace }.collectAsState(initial = WorkspaceState.Loading)
    val workspace = (workspaceState as? WorkspaceState.Ready)?.workspace
    var page by rememberSettingsSubPage()
    val link = HomeScreenPages.all.firstOrNull { it.route == page }
    if (link == null) {
        HomeMainPage(settings, updateHome, workspace, open = { page = it.route })
    } else {
        SettingsSubPage(title = stringResource(link.title), onBack = { page = if (link == HomeScreenPages.Scrolling) HomeScreenPages.Pages.route else null }) {
            when (link) {
                HomeScreenPages.Grid -> HomeGridPage(settings, update, workspace)
                HomeScreenPages.Icons -> HomeIconsPage(settings.home, settings.lookFeel, updateHome)
                HomeScreenPages.Widgets -> HomeWidgetsPage(settings.home, updateHome)
                HomeScreenPages.Pages -> HomePagesPage(settings, updateHome, workspace) { page = HomeScreenPages.Scrolling.route }
                HomeScreenPages.Scrolling -> HorizontalScrollingSettingsPage(settings, false, update)
                HomeScreenPages.Discover -> HomeDiscoverPage(settings.home, updateHome)
            }
        }
    }
}

@Composable
private fun HomeMainPage(
    settings: LauncherSettings,
    updateHome: ((HomeSettings) -> HomeSettings) -> Unit,
    workspace: Workspace?,
    open: (SettingsSubPageLink) -> Unit,
) {
    val home = settings.home
    val locked = home.layoutLocked
    val percent = percentageValueLabel()
    val dp = dpValueLabel()
    Column(Modifier.fillMaxSize()) {
        HomeGridPreview(home.desktopColumns, home.desktopRows, home.horizontalPadding, home.verticalPadding,
            Modifier.fillMaxWidth().height(128.dp).padding(vertical = 8.dp), showEdgeShadow = home.showEdgeShadow,
            dockHorizontalPadding = settings.dock.horizontalPadding)
        SettingsPage(Modifier.weight(1f)) {
            SettingsSwitchRow(
                title = stringResource(R.string.settings_home_lock_layout),
                summary = stringResource(R.string.settings_home_lock_layout_summary),
                checked = locked,
                onCheckedChange = { value -> updateHome { it.copy(layoutLocked = value) } },
                icon = R.drawable.ic_settings_row_lock,
            )
            if (locked) {
                SettingsInfoText(stringResource(R.string.settings_home_locked_main_info))
            } else {
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_home_new_apps),
                    summary = stringResource(R.string.settings_home_new_apps_summary),
                    checked = home.addNewApps,
                    onCheckedChange = { value -> updateHome { it.copy(addNewApps = value) } },
                    icon = R.drawable.ic_settings_row_add_to_home_screen,
                )
            }

            SettingsSectionHeader(stringResource(R.string.settings_home_header_customize))
            val columns = home.desktopColumns.coerceIn(gridRange(settings, home.desktopColumns))
            val rows = home.desktopRows.coerceIn(gridRange(settings, home.desktopRows))
            val hidden = remember(workspace) { workspace?.let { HiddenItems.find(it).size } ?: 0 }
            SettingsSubPageRow(
                title = stringResource(R.string.settings_home_page_grid),
                summary = if (hidden > 0) {
                    pluralStringResource(R.plurals.settings_home_page_grid_summary_hidden, hidden, columns, rows, hidden)
                } else {
                    stringResource(R.string.settings_home_page_grid_summary, columns, rows)
                },
                icon = R.drawable.ic_settings_row_grid_on,
                onClick = { open(HomeScreenPages.Grid) },
            )
            SettingsSubPageRow(
                title = stringResource(R.string.settings_home_page_icons),
                summary = stringResource(
                    if (home.showLabels) R.string.settings_home_page_icons_summary_labels
                    else R.string.settings_home_page_icons_summary_no_labels,
                    percent(home.iconScale),
                ),
                icon = R.drawable.ic_settings_row_label,
                onClick = { open(HomeScreenPages.Icons) },
            )
            val corners = if (home.widgetCornerRadiusDp <= 0f) {
                stringResource(R.string.settings_home_page_widgets_summary_own_corners)
            } else {
                dp(home.widgetCornerRadiusDp)
            }
            SettingsSubPageRow(
                title = stringResource(R.string.settings_home_header_widgets),
                summary = stringResource(
                    if (home.allowWidgetOverlap) R.string.settings_home_page_widgets_summary_overlap
                    else R.string.settings_home_page_widgets_summary_no_overlap,
                    corners,
                ),
                icon = R.drawable.ic_settings_row_widgets,
                onClick = { open(HomeScreenPages.Widgets) },
            )
            val transition = stringResource(transitionLabel(home.transitionEffect))
            SettingsSubPageRow(
                title = stringResource(R.string.settings_home_header_pages),
                summary = workspace?.let {
                    pluralStringResource(R.plurals.settings_home_page_pages_summary, it.pageCount, it.pageCount, transition)
                } ?: transition,
                icon = R.drawable.ic_settings_row_view_carousel,
                onClick = { open(HomeScreenPages.Pages) },
            )
            SettingsSubPageRow(
                title = stringResource(R.string.settings_home_google_discover),
                summary = stringResource(if (home.googleDiscoverEnabled) R.string.settings_state_on else R.string.settings_state_off),
                icon = R.drawable.ic_settings_row_dynamic_feed,
                onClick = { open(HomeScreenPages.Discover) },
            )
            SettingsSwitchRow(
                title = stringResource(R.string.settings_home_edge_shadow),
                summary = stringResource(R.string.settings_home_edge_shadow_summary),
                checked = home.showEdgeShadow,
                onCheckedChange = { value -> updateHome { it.copy(showEdgeShadow = value) } },
                icon = R.drawable.ic_settings_row_gradient,
            )
        }
    }
}

/** Grid size (blocked while the layout is locked), items that no longer fit, and edge spacing. */
@Composable
private fun HomeGridPage(
    settings: LauncherSettings,
    update: ((LauncherSettings) -> LauncherSettings) -> Unit,
    workspace: Workspace?,
) {
    val home = settings.home
    val locked = home.layoutLocked
    val columnsRange = gridRange(settings, home.desktopColumns)
    val rowsRange = gridRange(settings, home.desktopRows)
    val gridChange = layoutChangeControl(settings, update)
    val updateHome: ((HomeSettings) -> HomeSettings) -> Unit = { change -> update { it.copy(home = change(it.home)) } }
    // Dragged values while a slider moves; null shows the saved grid (also after a cancelled confirmation).
    var dragColumns by remember { mutableStateOf<Int?>(null) }
    var dragRows by remember { mutableStateOf<Int?>(null) }
    Column(Modifier.fillMaxSize()) {
        HomeGridPreview(
            columns = dragColumns ?: home.desktopColumns.coerceIn(columnsRange),
            rows = dragRows ?: home.desktopRows.coerceIn(rowsRange),
            horizontalPadding = home.horizontalPadding,
            verticalPadding = home.verticalPadding,
            showEdgeShadow = home.showEdgeShadow,
            dockHorizontalPadding = settings.dock.horizontalPadding,
            modifier = Modifier.fillMaxWidth().height(PREVIEW_HEIGHT).padding(vertical = 8.dp),
        )
        SettingsPage(Modifier.weight(1f)) {
            if (locked) {
                SettingsInfoText(stringResource(R.string.settings_home_grid_locked_info))
            } else {
                SettingsIntSliderRow(
                    title = stringResource(R.string.settings_home_columns),
                    value = home.desktopColumns.coerceIn(columnsRange),
                    onValueChangeFinished = { value ->
                        dragColumns = null
                        gridChange.request { it.copy(home = it.home.copy(desktopColumns = value)) }
                    },
                    range = columnsRange,
                    enabled = gridChange.ready,
                    onValueChanging = { dragColumns = it },
                )
                SettingsIntSliderRow(
                    title = stringResource(R.string.settings_home_rows),
                    value = home.desktopRows.coerceIn(rowsRange),
                    onValueChangeFinished = { value ->
                        dragRows = null
                        gridChange.request { it.copy(home = it.home.copy(desktopRows = value)) }
                    },
                    range = rowsRange,
                    enabled = gridChange.ready,
                    onValueChanging = { dragRows = it },
                )
                if (!settings.labs.extendedGrid && settings.labs.unlocked) {
                    SettingsInfoText(stringResource(R.string.settings_home_grid_extended_hint, GridSpec.MAX_DIMENSION))
                }
                if (!settings.labs.extendedGrid && maxOf(home.desktopColumns, home.desktopRows) > NORMAL_GRID_MAX) {
                    SettingsInfoText(stringResource(R.string.settings_grid_saved_size_summary, NORMAL_GRID_MAX))
                }
            }
            HiddenItemsRows(workspace?.let { HiddenItems.find(it).size }, locked)

            SettingsSectionHeader(stringResource(R.string.settings_home_header_edge_space))
            PaddingChoice(stringResource(R.string.settings_home_horizontal_padding), home.horizontalPadding) { value ->
                updateHome { it.copy(horizontalPadding = value) }
            }
            PaddingChoice(stringResource(R.string.settings_home_vertical_padding), home.verticalPadding) { value ->
                updateHome { it.copy(verticalPadding = value) }
            }
            SettingsInfoText(stringResource(R.string.settings_home_grid_spacing_info))
    }
    }
}

/** Icon size and position; label options only while labels are shown. A pinned sample follows every drag. */
@Composable
private fun HomeIconsPage(home: HomeSettings, look: io.github.aj4abinjacob.chikku.core.model.settings.LookFeelSettings, updateHome: ((HomeSettings) -> HomeSettings) -> Unit) {
    // Draft copy for the preview: sliders write each dragged value here, saving still happens on release.
    var live by remember(home) { mutableStateOf(home) }
    Column(Modifier.fillMaxSize()) {
        IconSamplePreview(
            look = IconSampleLook(
                iconScale = live.iconScale,
                showLabels = live.showLabels,
                labelScale = live.labelScale,
                labelFont = live.labelFont,
                labelShadow = live.labelShadow,
                labelSingleLine = live.labelSingleLine,
                labelColor = live.labelColor,
                labelGapDp = live.iconLabelGapDp,
                verticalOffsetDp = live.iconVerticalOffsetDp,
                iconShape = look.iconShape,
                reshapeLegacyIcons = look.reshapeLegacyIcons,
            ),
            background = null,
            modifier = Modifier.fillMaxWidth(),
        )
        SettingsPage(Modifier.weight(1f)) {
            ScaleSlider(
                title = stringResource(R.string.settings_home_icon_size),
                value = home.iconScale,
                onChange = { value -> updateHome { it.copy(iconScale = value) } },
                onChanging = { value -> live = live.copy(iconScale = value) },
            )
            SettingsIntSliderRow(
                title = stringResource(R.string.home_icon_vertical_offset),
                value = home.iconVerticalOffsetDp,
                range = 0..24,
                onValueChangeFinished = { value -> updateHome { it.copy(iconVerticalOffsetDp = value) } },
                onValueChanging = { value -> live = live.copy(iconVerticalOffsetDp = value) },
            )
            SettingsSwitchRow(
                title = stringResource(R.string.settings_home_show_labels),
                checked = home.showLabels,
                onCheckedChange = { value -> updateHome { it.copy(showLabels = value) } },
            )
            if (home.showLabels) {
                LabelSettingsControls(
                    labelScale = home.labelScale,
                    labelFont = home.labelFont,
                    labelShadow = home.labelShadow,
                    labelSingleLine = home.labelSingleLine,
                    labelColor = home.labelColor,
                    onScaleChange = { value -> updateHome { it.copy(labelScale = value) } },
                    onFontChange = { value -> updateHome { it.copy(labelFont = value) } },
                    onShadowChange = { value -> updateHome { it.copy(labelShadow = value) } },
                    onSingleLineChange = { value -> updateHome { it.copy(labelSingleLine = value) } },
                    onColorChange = { value -> updateHome { it.copy(labelColor = value) } },
                    onScaleChanging = { value -> live = live.copy(labelScale = value) },
                )
                SettingsIntSliderRow(
                    title = stringResource(R.string.home_icon_label_gap),
                    value = home.iconLabelGapDp,
                    range = 0..24,
                    onValueChangeFinished = { value -> updateHome { it.copy(iconLabelGapDp = value) } },
                    onValueChanging = { value -> live = live.copy(iconLabelGapDp = value) },
                )
            } else {
                SettingsInfoText(stringResource(R.string.settings_home_labels_off_info))
            }
    }
    }
}

/** Widget corners and inner space; overlap only while the layout is unlocked. */
@Composable
private fun HomeWidgetsPage(home: HomeSettings, updateHome: ((HomeSettings) -> HomeSettings) -> Unit) {
    var liveCorners by remember(home.widgetCornerRadiusDp) { mutableFloatStateOf(home.widgetCornerRadiusDp) }
    Column(Modifier.fillMaxSize()) {
        HomeWidgetPreview(
            cornerRadiusDp = liveCorners,
            padding = home.widgetPadding,
            allowOverlap = home.allowWidgetOverlap,
            modifier = Modifier.fillMaxWidth().height(PREVIEW_HEIGHT).padding(vertical = 8.dp),
        )
        SettingsPage(Modifier.weight(1f)) {
            SettingsSliderRow(
                title = stringResource(R.string.settings_home_widget_corners),
                value = home.widgetCornerRadiusDp,
                onValueChangeFinished = { value -> updateHome { it.copy(widgetCornerRadiusDp = value) } },
                valueRange = 0f..48f,
                valueLabel = dpValueLabel(),
                onValueChanging = { liveCorners = it },
            )
            SettingsInfoText(stringResource(R.string.settings_home_widget_corners_summary))
            PaddingChoice(stringResource(R.string.settings_home_widget_padding), home.widgetPadding) { value ->
                updateHome { it.copy(widgetPadding = value) }
            }
            SettingsInfoText(stringResource(R.string.settings_home_widget_padding_info))
            if (home.layoutLocked) {
                SettingsInfoText(stringResource(R.string.settings_home_widget_overlap_locked_info))
            } else {
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_home_widget_overlap),
                    summary = stringResource(R.string.settings_home_widget_overlap_summary),
                    checked = home.allowWidgetOverlap,
                    onCheckedChange = { value -> updateHome { it.copy(allowWidgetOverlap = value) } },
                )
            }
    }
    }
}

/** Page transition and indicator (with their live previews), scrolling, default page and adding pages. */
@Composable
private fun HomePagesPage(
    settings: LauncherSettings,
    updateHome: ((HomeSettings) -> HomeSettings) -> Unit,
    workspace: Workspace?,
    openScrolling: () -> Unit,
) {
    val home = settings.home
    var live by remember(home) { mutableStateOf(home) }
    val deps = LocalHomeDependencies.current
    val pageFormat = stringResource(R.string.settings_home_default_page_value)
    Column(Modifier.fillMaxSize()) {
        HomePagesLivePreview(settings.copy(home = live), workspace?.pageCount ?: (home.defaultPage + 1))
        SettingsPage(Modifier.weight(1f)) {
            SettingsSubPageRow(stringResource(R.string.paging_settings_title), stringResource(R.string.paging_settings_summary), onClick = openScrolling)
            SettingsChoiceRow(
                title = stringResource(R.string.settings_home_transition),
                optionPreview = { PageTransitionPreview(it) },
                options = PageTransitionEffect.entries,
                selected = home.transitionEffect,
                optionLabel = { effect -> stringResource(transitionLabel(effect)) },
                onSelect = { value -> updateHome { it.copy(transitionEffect = value) } },
            )
            SettingsChoiceRow(
                title = stringResource(R.string.settings_home_page_indicator),
                optionPreview = { PageIndicatorPreview(it) },
                options = PageIndicatorStyle.entries,
                selected = home.pageIndicatorStyle,
                optionLabel = { style ->
                    stringResource(
                        when (style) {
                            PageIndicatorStyle.DOTS -> R.string.settings_page_indicator_dots
                            PageIndicatorStyle.LINE -> R.string.settings_page_indicator_line
                            PageIndicatorStyle.NONE -> R.string.settings_page_indicator_none
                        },
                    )
                },
                onSelect = { value -> updateHome { it.copy(pageIndicatorStyle = value) } },
            )
            if (home.pageIndicatorStyle != PageIndicatorStyle.NONE) {
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_home_page_indicator_auto_hide),
                    summary = stringResource(R.string.settings_home_page_indicator_auto_hide_summary),
                    checked = home.pageIndicatorAutoHide,
                    onCheckedChange = { value -> updateHome { it.copy(pageIndicatorAutoHide = value) } },
                )
            }
            SettingsSwitchRow(
                title = stringResource(R.string.settings_home_wallpaper_scrolling),
                summary = stringResource(R.string.settings_home_wallpaper_scrolling_summary),
                checked = home.wallpaperScrolling,
                onCheckedChange = { value -> updateHome { it.copy(wallpaperScrolling = value) } },
            )
            SettingsSwitchRow(
                title = stringResource(R.string.settings_home_loop_pages),
                // Discover owns the swipe back from the first page, so only last → first can loop then.
                summary = stringResource(
                    if (home.googleDiscoverEnabled) R.string.settings_home_infinite_scroll_summary_discover
                    else R.string.settings_home_infinite_scroll_summary,
                ),
                checked = home.infiniteScroll,
                onCheckedChange = { value -> updateHome { it.copy(infiniteScroll = value) } },
            )
            val pageCount = workspace?.pageCount ?: (home.defaultPage + 1)
            if (pageCount > 1) {
                SettingsIntSliderRow(
                    title = stringResource(R.string.settings_home_default_page),
                    value = home.defaultPage.coerceIn(0, pageCount - 1) + 1,
                    onValueChangeFinished = { value -> updateHome { it.copy(defaultPage = value - 1) } },
                    range = 1..pageCount,
                    onValueChanging = { value -> live = live.copy(defaultPage = value - 1) },
                    valueLabel = { String.format(Locale.getDefault(), pageFormat, it) },
                )
            }
            if (workspace != null) {
                if (home.layoutLocked) {
                    SettingsInfoText(stringResource(R.string.settings_home_add_page_locked_info))
                } else {
                    SettingsNavigationRow(
                        title = stringResource(R.string.settings_home_add_page),
                        summary = stringResource(R.string.settings_home_page_count, workspace.pageCount),
                        onClick = { deps.appScope.launch { runCatchingEdit { deps.workspaceRepository.addPage() } } },
                    )
                }
            }
        }
    }
}

/** Main switch first; while off, only what turning it on does. */
@Composable
private fun HomeDiscoverPage(home: HomeSettings, updateHome: ((HomeSettings) -> HomeSettings) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        DiscoverLivePreview(home.googleDiscoverEnabled)
    SettingsPage(Modifier.weight(1f)) {
        SettingsMainSwitch(
            title = stringResource(R.string.settings_home_google_discover),
            checked = home.googleDiscoverEnabled,
            onCheckedChange = { value -> updateHome { it.copy(googleDiscoverEnabled = value) } },
        )
        if (home.googleDiscoverEnabled) {
            DiscoverStatusRow()
            SettingsInfoText(stringResource(R.string.settings_home_google_discover_summary))
            SettingsInfoText(
                stringResource(R.string.settings_home_google_discover_disclosure_short),
                details = stringResource(R.string.settings_home_google_discover_disclosure),
            )
        } else {
            SettingsInfoText(
                stringResource(R.string.settings_home_discover_off_info),
                details = stringResource(R.string.settings_home_google_discover_disclosure),
            )
        }
    }
    }
}

private fun transitionLabel(effect: PageTransitionEffect): Int = when (effect) {
    PageTransitionEffect.SLIDE -> R.string.settings_transition_slide
    PageTransitionEffect.FADE -> R.string.settings_transition_fade
    PageTransitionEffect.ZOOM -> R.string.settings_transition_zoom
    PageTransitionEffect.CUBE -> R.string.settings_transition_cube
}

@Composable
private fun HiddenItemsRows(hiddenCount: Int?, locked: Boolean) {
    if (hiddenCount == null) return
    val deps = LocalHomeDependencies.current
    val messages = rememberChikkuMessages()
    var outcome by remember { mutableStateOf<HiddenItems.Outcome?>(null) }
    var moving by remember { mutableStateOf(false) }
    if (hiddenCount == 0 && outcome == null) return
    if (hiddenCount > 0) SettingsInfoText(pluralStringResource(R.plurals.settings_home_hidden_items, hiddenCount, hiddenCount))
    if (locked) return
    SettingsNavigationRow(
        title = stringResource(R.string.settings_home_move_hidden),
        summary = outcome?.let { stringResource(R.string.settings_home_move_hidden_result, it.moved, it.remaining) }
            ?: stringResource(R.string.settings_home_move_hidden_summary),
        enabled = !moving && hiddenCount > 0,
        onClick = {
            deps.appScope.launch {
                moving = true
                try {
                    val result = runCatchingEdit { HiddenItems.relocate(deps.workspaceRepository) }
                    if (result == null) messages.show(R.string.home_edit_failed)
                    else outcome = result
                } finally { moving = false }
            }
        },
    )
    if ((outcome?.moved ?: 0) > 0) SettingsNavigationRow(
        title = stringResource(R.string.settings_home_undo_hidden),
        enabled = !moving,
        onClick = { deps.appScope.launch {
            if (runCatchingEdit { deps.workspaceRepository.undoHiddenRelocation() } == true) outcome = null
            else messages.show(R.string.home_edit_failed)
        } },
    )
}

/** Runs a storage edit; failures are reported by the repository's typed results, never thrown to UI. */
private suspend fun <T> runCatchingEdit(block: suspend () -> T): T? = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (_: Exception) {
    null
}

@Composable
internal fun ScaleSlider(
    title: String,
    value: Float,
    onChange: (Float) -> Unit,
    enabled: Boolean = true,
    onChanging: ((Float) -> Unit)? = null,
) {
    SettingsSliderRow(
        title = title,
        value = value.coerceIn(LauncherSettings.MIN_SCALE, LauncherSettings.MAX_SCALE),
        onValueChangeFinished = onChange,
        valueRange = LauncherSettings.MIN_SCALE..LauncherSettings.MAX_SCALE,
        steps = SCALE_STEPS,
        valueLabel = percentageValueLabel(),
        enabled = enabled,
        onValueChanging = onChanging,
    )
}

/** Height of the pinned grid and widget previews, padding included. */
private val PREVIEW_HEIGHT = 176.dp

/** 50% to 150% in 10% steps. */
private const val SCALE_STEPS = 9

@Composable
private fun PaddingChoice(title: String, selected: PaddingSize, onSelect: (PaddingSize) -> Unit) {
    SettingsStepRow(
        title = title,
        options = PaddingSize.entries,
        selected = selected,
        optionLabel = { size -> spacingLabel(size) },
        onSelect = onSelect,
    )
}

/**
 * Live Discover status, re-checked whenever this screen resumes (for example after the user changed
 * the bridge's battery settings). Tapping opens the bridge's app info when that can fix it.
 */
@Composable
private fun DiscoverStatusRow() {
    val checker = LocalHomeDependencies.current.discoverHealthChecker ?: return
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    var health by remember { mutableStateOf<DiscoverHealth?>(null) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    LaunchedEffect(refresh) { health = checker.check() }
    val current = health
    val bridge = current?.bridgePackage
    SettingsNavigationRow(
        title = stringResource(R.string.settings_home_google_discover_status),
        summary = stringResource(
            current?.messageRes(afterFailure = false) ?: R.string.settings_home_google_discover_status_checking,
        ),
        onClick = { if (bridge == null || !context.openAppDetails(bridge)) refresh++ },
    )
}
