package io.github.aj4abinjacob.chikku.settings.sections

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.settings.DockBackground
import io.github.aj4abinjacob.chikku.core.model.settings.DockCorner
import io.github.aj4abinjacob.chikku.core.model.settings.DockCornerShape
import io.github.aj4abinjacob.chikku.core.model.settings.DockSettings
import io.github.aj4abinjacob.chikku.core.model.settings.HomeSearchBarPosition
import io.github.aj4abinjacob.chikku.core.model.settings.HomeSettings
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.core.model.settings.PaddingSize
import io.github.aj4abinjacob.chikku.home.DockImageLoader
import io.github.aj4abinjacob.chikku.home.DockPaging
import io.github.aj4abinjacob.chikku.home.HomeLayoutMetrics
import io.github.aj4abinjacob.chikku.message.rememberChikkuMessages
import io.github.aj4abinjacob.chikku.settings.components.LabelFontChoiceRow
import io.github.aj4abinjacob.chikku.settings.components.SettingsChoiceRow
import io.github.aj4abinjacob.chikku.settings.components.SettingsStepRow
import io.github.aj4abinjacob.chikku.settings.components.SettingsColorRow
import io.github.aj4abinjacob.chikku.settings.components.SettingsInfoText
import io.github.aj4abinjacob.chikku.settings.components.SettingsIntSliderRow
import io.github.aj4abinjacob.chikku.settings.components.SettingsMainSwitch
import io.github.aj4abinjacob.chikku.settings.components.SettingsNavigationRow
import io.github.aj4abinjacob.chikku.settings.components.SettingsPage
import io.github.aj4abinjacob.chikku.settings.components.SettingsSectionHeader
import io.github.aj4abinjacob.chikku.settings.components.SettingsSliderRow
import io.github.aj4abinjacob.chikku.settings.components.SettingsSubPage
import io.github.aj4abinjacob.chikku.settings.components.SettingsSubPageLink
import io.github.aj4abinjacob.chikku.settings.components.SettingsSubPageRow
import io.github.aj4abinjacob.chikku.settings.components.SettingsSwitchRow
import io.github.aj4abinjacob.chikku.settings.components.WebSearchSettings
import io.github.aj4abinjacob.chikku.settings.components.layoutChangeControl
import io.github.aj4abinjacob.chikku.settings.components.percentageValueLabel
import io.github.aj4abinjacob.chikku.settings.components.rememberSettingsSubPage
import io.github.aj4abinjacob.chikku.settings.components.settingsSummary
import io.github.aj4abinjacob.chikku.settings.components.spacingLabel
import io.github.aj4abinjacob.chikku.settings.dock.DockAppearancePreview
import io.github.aj4abinjacob.chikku.settings.dock.DockAppearancePreviewMode
import io.github.aj4abinjacob.chikku.settings.dock.DockSpacingPreview
import io.github.aj4abinjacob.chikku.settings.previews.DockSearchLivePreview
import io.github.aj4abinjacob.chikku.settings.previews.DockInteractionLivePreview
import io.github.aj4abinjacob.chikku.settings.previews.DockBackgroundPreview
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Sub-pages of Settings › Dock; routes are shared with the dock's `SettingsSearchIndex` entries. */
internal object DockPages {
    val Apps = SettingsSubPageLink("dock_apps", R.string.settings_dock_page_apps)
    val Icons = SettingsSubPageLink("dock_icons", R.string.settings_dock_page_icons)
    val Background = SettingsSubPageLink("dock_background", R.string.settings_dock_page_background)
    val Spacing = SettingsSubPageLink("dock_spacing", R.string.settings_dock_page_spacing)
    val Behavior = SettingsSubPageLink("dock_behavior", R.string.settings_dock_page_behavior)
    val Search = SettingsSubPageLink("dock_search", R.string.settings_dock_page_search)
    val all = listOf(Apps, Icons, Background, Spacing, Behavior, Search)
}

private typealias DockSettingsUpdate = ((LauncherSettings) -> LauncherSettings) -> Unit

/**
 * Dock controls. The main page holds the on/off choice and one row per task; each task opens its own sub-page.
 */
@Composable
fun DockSettingsSection(settings: LauncherSettings, update: ((LauncherSettings) -> LauncherSettings) -> Unit) {
    var page by rememberSettingsSubPage()
    val open = DockPages.all.firstOrNull { it.route == page }
    val chooseImage = rememberDockImagePicker(update)
    // Draft the pinned preview draws: sliders edit it on every drag frame, the setting is saved on
    // release, and it resets to the saved settings whenever they change.
    var live by remember(settings) { mutableStateOf(settings) }
    val preview: DockSettingsUpdate = { change -> live = change(live) }
    val body: @Composable () -> Unit = {
        Column(Modifier.fillMaxSize()) {
            if (settings.dock.enabled || open == DockPages.Search) {
                if (open == DockPages.Search) DockSearchLivePreview(live)
                else if (open == DockPages.Spacing) DockSpacingPreview(live)
                // Icons and Background draw the dock at actual size without the paging controls,
                // which only belong to Apps (pages) and Behavior (show, hide, open).
                else if (open == DockPages.Icons) DockAppearancePreview(live, DockAppearancePreviewMode.ICONS)
                else if (open == DockPages.Background) DockAppearancePreview(live, DockAppearancePreviewMode.BACKGROUND)
                else DockInteractionLivePreview(live, behavior = open == DockPages.Behavior)
            }
            SettingsPage(Modifier.weight(1f)) {
                when (open) {
                    null -> DockMainPage(settings, update) { page = it.route }
                    DockPages.Search -> DockSearchPage(settings, update, preview)
                    else -> if (!settings.dock.enabled) DockOffNotice(settings, update) else when (open) {
                        DockPages.Apps -> DockAppsPage(settings, update, preview)
                        DockPages.Icons -> DockIconsPage(settings, update, preview)
                        DockPages.Background -> DockBackgroundPage(settings, update, chooseImage, preview)
                        DockPages.Spacing -> DockSpacingPage(settings, update)
                        else -> DockBehaviorPage(settings, update)
                    }
                }
            }
        }
    }
    if (open == null) body() else SettingsSubPage(stringResource(open.title), onBack = { page = null }) { body() }
}

private fun DockSettingsUpdate.dock(change: (DockSettings) -> DockSettings) = this { it.copy(dock = change(it.dock)) }

/** Opens the system file picker; keeps the persisted read grant only if the image is readable. */
@Composable
private fun rememberDockImagePicker(update: DockSettingsUpdate): () -> Unit {
    val context = LocalContext.current
    val messages = rememberChikkuMessages()
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val granted = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    DockImageLoader(context, Dispatchers.IO).load(uri.toString()) != null
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) { false }
            }
            if (granted) update.dock { it.copy(backgroundImageUri = uri.toString(), backgroundEnabled = true,
                background = if (it.background == DockBackground.NONE) DockBackground.ROUNDED else it.background) }
            else messages.show(R.string.settings_dock_image_unavailable, long = true)
        }
    }
    return {
        try { launcher.launch(arrayOf("image/*")) }
        catch (_: android.content.ActivityNotFoundException) {
            messages.show(R.string.settings_dock_image_unavailable, long = true)
        }
    }
}

@Composable
private fun DockMainPage(settings: LauncherSettings, update: DockSettingsUpdate, open: (SettingsSubPageLink) -> Unit) {
    val dock = settings.dock
    DockEnabledSwitch(settings, update)
    if (!dock.enabled) {
        SettingsInfoText(stringResource(R.string.settings_dock_off_info))
    } else {
        SettingsSubPageRow(stringResource(DockPages.Apps.title), summary = appsSummary(dock), icon = R.drawable.ic_settings_row_apps, onClick = { open(DockPages.Apps) })
        SettingsSubPageRow(stringResource(DockPages.Icons.title), summary = iconsSummary(settings), icon = R.drawable.ic_settings_row_label, onClick = { open(DockPages.Icons) })
        SettingsSubPageRow(stringResource(DockPages.Background.title), summary = backgroundSummary(dock), icon = R.drawable.ic_settings_row_format_color_fill, onClick = { open(DockPages.Background) })
        SettingsSubPageRow(stringResource(DockPages.Spacing.title), summary = spacingSummary(settings), icon = R.drawable.ic_settings_row_padding, onClick = { open(DockPages.Spacing) })
        SettingsSubPageRow(stringResource(DockPages.Behavior.title), summary = behaviorSummary(dock), icon = R.drawable.ic_settings_row_visibility, onClick = { open(DockPages.Behavior) })
    }
    SettingsSectionHeader(stringResource(R.string.settings_dock_header_search))
    SettingsSubPageRow(stringResource(DockPages.Search.title), summary = searchSummary(settings), icon = R.drawable.ic_search, onClick = { open(DockPages.Search) })
}

@Composable
private fun DockEnabledSwitch(settings: LauncherSettings, update: DockSettingsUpdate) {
    SettingsMainSwitch(
        title = stringResource(R.string.settings_dock_enabled),
        checked = settings.dock.enabled,
        onCheckedChange = { value -> update.dock { it.copy(enabled = value) } },
    )
}

/** A dock sub-page opened (for example from search) while the dock is off. */
@Composable
private fun DockOffNotice(settings: LauncherSettings, update: DockSettingsUpdate) {
    DockEnabledSwitch(settings, update)
    SettingsInfoText(stringResource(R.string.settings_dock_off_info))
}

@Composable
private fun DockAppsPage(settings: LauncherSettings, update: DockSettingsUpdate, preview: DockSettingsUpdate) {
    val dock = settings.dock
    val locked = settings.home.layoutLocked
    val range = gridRange(settings, dock.iconCount)
    val gridChange = layoutChangeControl(settings, update)
    if (locked) SettingsInfoText(stringResource(R.string.settings_home_locked_disabled))
    SettingsIntSliderRow(
        title = stringResource(R.string.settings_dock_icon_count), value = dock.iconCount.coerceIn(range),
        onValueChangeFinished = { value ->
            // A cancelled confirmation must not leave the preview showing the dragged count.
            preview { settings }
            gridChange.request { it.copy(dock = it.dock.copy(iconCount = value)) }
        }, range = range,
        enabled = !locked && gridChange.ready,
        onValueChanging = { value -> preview.dock { it.copy(iconCount = value) } },
    )
    if (!settings.labs.extendedGrid && dock.iconCount > NORMAL_GRID_MAX) {
        SettingsInfoText(stringResource(R.string.settings_grid_saved_size_summary, NORMAL_GRID_MAX))
    }
    SettingsIntSliderRow(
        title = stringResource(R.string.settings_dock_pages), value = dock.pages,
        onValueChangeFinished = { value ->
            preview { settings }
            gridChange.request { it.copy(dock = it.dock.copy(pages = value)) }
        },
        range = 1..LauncherSettings.MAX_DOCK_PAGES, enabled = !locked && gridChange.ready,
        onValueChanging = { value -> preview.dock { it.copy(pages = value) } },
    )
    val effectivePages = DockPaging.effectivePages(dock)
    if (effectivePages < dock.pages) SettingsInfoText(stringResource(R.string.settings_dock_pages_limited, dock.iconCount, effectivePages))
    if (effectivePages > 1) {
        SettingsSwitchRow(stringResource(R.string.settings_home_infinite_scroll), dock.infiniteScroll,
            { value -> update.dock { it.copy(infiniteScroll = value) } })
        SettingsSwitchRow(stringResource(R.string.settings_dock_remember_position), dock.rememberPosition,
            { value -> update.dock { it.copy(rememberPosition = value) } })
    } else {
        SettingsInfoText(stringResource(R.string.settings_dock_single_page_info))
    }
}

@Composable
private fun DockIconsPage(settings: LauncherSettings, update: DockSettingsUpdate, preview: DockSettingsUpdate) {
    val dock = settings.dock
    SettingsSwitchRow(
        title = stringResource(R.string.settings_dock_match_desktop),
        checked = dock.matchDesktopSize,
        onCheckedChange = { value -> update.dock { it.copy(matchDesktopSize = value) } },
    )
    if (dock.matchDesktopSize) {
        SettingsInfoText(stringResource(R.string.settings_dock_match_info))
    } else {
        ScaleSlider(stringResource(R.string.settings_dock_icon_size), dock.effectiveIconScale(settings.home),
            { value -> update.dock { it.copy(iconScale = value) } },
            onChanging = { value -> preview.dock { it.copy(iconScale = value) } })
    }
    SettingsSwitchRow(
        title = stringResource(R.string.settings_dock_show_labels),
        checked = dock.showLabels,
        onCheckedChange = { value -> update.dock { it.copy(showLabels = value) } },
    )
    if (!dock.showLabels) {
        SettingsInfoText(stringResource(R.string.settings_dock_names_off_info))
        return
    }
    ScaleSlider(stringResource(R.string.settings_home_label_size), dock.effectiveLabelScale(settings.home),
        { value -> update.dock { it.copy(labelScale = value) } },
        onChanging = { value -> preview.dock { it.copy(labelScale = value) } })
    LabelFontChoiceRow(
        selected = dock.labelFont,
        onSelect = { value -> update.dock { it.copy(labelFont = value) } },
    )
    SettingsSwitchRow(stringResource(R.string.settings_dock_label_shadow), dock.labelShadow,
        { value -> update.dock { it.copy(labelShadow = value) } })
    SettingsSwitchRow(stringResource(R.string.settings_dock_single_line), dock.labelSingleLine,
        { value -> update.dock { it.copy(labelSingleLine = value) } })
}

@Composable
private fun DockBackgroundPage(
    settings: LauncherSettings,
    update: DockSettingsUpdate,
    chooseImage: () -> Unit,
    preview: DockSettingsUpdate,
) {
    val dock = settings.dock
    val hasBackground = dock.hasBackground()
    SettingsMainSwitch(
        title = stringResource(R.string.settings_dock_background),
        checked = hasBackground,
        onCheckedChange = { value -> update.dock {
            it.copy(backgroundEnabled = value, background = if (value && it.background == DockBackground.NONE) DockBackground.ROUNDED else it.background)
        } },
    )
    if (!hasBackground) {
        SettingsInfoText(stringResource(R.string.settings_dock_background_off_info))
        return
    }
    SettingsChoiceRow(
        title = stringResource(R.string.settings_dock_background_shape),
        optionPreview = { DockBackgroundPreview(it) },
        optionSummary = { if (it == DockBackground.CUSTOM) stringResource(R.string.hint_dock_custom) else null },
        options = DockBackground.entries.filter { it != DockBackground.NONE },
        selected = dock.background,
        optionLabel = { stringResource(it.labelResource()) },
        onSelect = { value -> update.dock { it.copy(background = value, backgroundEnabled = true) } },
    )
    SettingsColorRow(stringResource(R.string.settings_dock_background_color), dock.backgroundColor,
        { value -> update.dock { it.copy(backgroundColor = value) } })
    SettingsSliderRow(stringResource(R.string.settings_dock_background_alpha), dock.backgroundAlpha,
        { value -> update.dock { it.copy(backgroundAlpha = value) } }, 0f..1f, steps = 9, valueLabel = percentageValueLabel(),
        onValueChanging = { value -> preview.dock { it.copy(backgroundAlpha = value) } })
    SettingsNavigationRow(
        title = stringResource(R.string.settings_dock_image),
        summary = stringResource(if (dock.backgroundImageUri == null) R.string.settings_dock_image_choose else R.string.settings_dock_image_selected),
        onClick = chooseImage,
    )
    if (dock.backgroundImageUri != null) {
        SettingsNavigationRow(
            title = stringResource(R.string.settings_dock_image_remove),
            onClick = { update.dock { it.copy(backgroundImageUri = null) } },
        )
        SettingsInfoText(stringResource(R.string.settings_dock_image_restore_note))
    }
    SettingsSwitchRow(stringResource(R.string.settings_dock_behind_navigation), dock.drawBehindNavigationBar,
        { value -> update.dock { it.copy(drawBehindNavigationBar = value) } })
    SettingsSwitchRow(stringResource(R.string.settings_dock_background_padding), dock.backgroundInset,
        { value -> update.dock { it.copy(backgroundInset = value) } },
        summary = stringResource(R.string.settings_dock_inset_summary))
    if (dock.background == DockBackground.CUSTOM) {
        SettingsSectionHeader(stringResource(R.string.settings_dock_header_corners))
        CornerSettings(stringResource(R.string.settings_dock_top_left), dock.topLeftCorner,
            { value -> preview.dock { it.copy(topLeftCorner = value) } }) {
            value -> update.dock { it.copy(topLeftCorner = value) }
        }
        CornerSettings(stringResource(R.string.settings_dock_top_right), dock.topRightCorner,
            { value -> preview.dock { it.copy(topRightCorner = value) } }) {
            value -> update.dock { it.copy(topRightCorner = value) }
        }
        CornerSettings(stringResource(R.string.settings_dock_bottom_left), dock.bottomLeftCorner,
            { value -> preview.dock { it.copy(bottomLeftCorner = value) } }) {
            value -> update.dock { it.copy(bottomLeftCorner = value) }
        }
        CornerSettings(stringResource(R.string.settings_dock_bottom_right), dock.bottomRightCorner,
            { value -> preview.dock { it.copy(bottomRightCorner = value) } }) {
            value -> update.dock { it.copy(bottomRightCorner = value) }
        }
        SettingsInfoText(stringResource(R.string.settings_dock_corners_summary))
    }
}

@Composable
private fun DockSpacingPage(settings: LauncherSettings, update: DockSettingsUpdate) {
    val dock = settings.dock
    // Following the home grid is an on/off choice; the explicit sizes are a scale.
    val sideSpace = dock.horizontalPadding
    SettingsSwitchRow(paddingLabel(null, settings), sideSpace == null, { follow ->
        update.dock { it.copy(horizontalPadding = if (follow) null else settings.home.horizontalPadding) }
    }, summary = stringResource(R.string.settings_dock_side_space))
    if (sideSpace != null) {
        SettingsStepRow(stringResource(R.string.settings_dock_side_space), PaddingSize.entries,
            sideSpace, { spacingLabel(it) }, { value -> update.dock { it.copy(horizontalPadding = value) } })
    }
    SettingsStepRow(stringResource(R.string.settings_dock_vertical_space), PaddingSize.entries,
        dock.verticalPadding, { spacingLabel(it) }, { value -> update.dock { it.copy(verticalPadding = value) } })
    SettingsInfoText(stringResource(R.string.settings_dock_spacing_summary))
}

@Composable
private fun DockBehaviorPage(settings: LauncherSettings, update: DockSettingsUpdate) {
    val dock = settings.dock
    SettingsSwitchRow(stringResource(R.string.settings_dock_overlay), dock.displayAsOverlay,
        { value -> update.dock { it.copy(displayAsOverlay = value) } },
        summary = stringResource(R.string.settings_dock_overlay_summary))
    if (dock.displayAsOverlay) {
        SettingsSwitchRow(stringResource(R.string.settings_dock_auto_close), dock.automaticallyClose,
            { value -> update.dock { it.copy(automaticallyClose = value) } },
            summary = stringResource(R.string.settings_dock_auto_close_summary))
    } else {
        SettingsInfoText(stringResource(R.string.settings_dock_overlay_off_info))
    }
}

@Composable
private fun DockSearchPage(settings: LauncherSettings, update: DockSettingsUpdate, preview: DockSettingsUpdate) {
    val resources = LocalResources.current
    val belowDock = settings.home.searchBarBelowDock()
    SettingsMainSwitch(
        title = stringResource(R.string.settings_dock_search_bar),
        checked = belowDock,
        onCheckedChange = { on -> update { it.copy(home = it.home.copy(showSearchBar = on,
            searchBarPosition = if (on) HomeSearchBarPosition.BELOW_DOCK else HomeSearchBarPosition.NONE)) } },
    )
    if (!belowDock) {
        SettingsInfoText(stringResource(R.string.settings_dock_search_bar_summary))
        return
    }
    SettingsIntSliderRow(
        title = stringResource(R.string.search_settings_bar_dock_gap),
        value = settings.search.widgetDockGapDp,
        range = 0..64,
        valueLabel = { resources.getString(R.string.search_settings_bar_dp_value, it) },
        onValueChangeFinished = { gap -> update { it.copy(search = it.search.copy(widgetDockGapDp = gap)) } },
        onValueChanging = { gap -> preview { it.copy(search = it.search.copy(widgetDockGapDp = gap)) } },
    )
    WebSearchSettings(settings, update)
}

private fun DockSettings.hasBackground() = backgroundEnabled && background != DockBackground.NONE

private fun HomeSettings.searchBarBelowDock() =
    showSearchBar && searchBarPosition == HomeSearchBarPosition.BELOW_DOCK

@Composable
private fun appsSummary(dock: DockSettings): String {
    val pages = DockPaging.effectivePages(dock)
    return settingsSummary(
        pluralStringResource(R.plurals.settings_dock_apps_count, dock.iconCount, dock.iconCount),
        pluralStringResource(R.plurals.settings_dock_pages_count, pages, pages),
    ).orEmpty()
}

@Composable
private fun iconsSummary(settings: LauncherSettings): String {
    val dock = settings.dock
    val size = if (dock.matchDesktopSize) stringResource(R.string.settings_dock_icons_summary_match)
        else stringResource(R.string.settings_dock_icons_summary_size, percentageValueLabel()(dock.iconScale))
    val names = stringResource(if (dock.showLabels) R.string.settings_dock_names_on else R.string.settings_dock_names_off)
    return settingsSummary(size, names).orEmpty()
}

@Composable
private fun backgroundSummary(dock: DockSettings): String = when {
    !dock.hasBackground() -> stringResource(R.string.settings_state_off)
    dock.backgroundImageUri != null ->
        stringResource(R.string.settings_dock_background_summary_image, stringResource(dock.background.labelResource()))
    else -> stringResource(R.string.settings_dock_background_summary_opacity,
        stringResource(dock.background.labelResource()), percentageValueLabel()(dock.backgroundAlpha))
}

@Composable
private fun spacingSummary(settings: LauncherSettings): String = stringResource(R.string.settings_dock_padding_value,
    paddingLabel(settings.dock.horizontalPadding, settings), spacingLabel(settings.dock.verticalPadding))

@Composable
private fun behaviorSummary(dock: DockSettings): String = stringResource(when {
    !dock.displayAsOverlay -> R.string.settings_dock_behavior_always
    dock.automaticallyClose -> R.string.settings_dock_behavior_overlay_close
    else -> R.string.settings_dock_behavior_overlay
})

@Composable
private fun searchSummary(settings: LauncherSettings): String = stringResource(when {
    settings.home.searchBarBelowDock() -> R.string.settings_dock_search_summary_on
    settings.home.showSearchBar && settings.home.searchBarPosition != HomeSearchBarPosition.NONE ->
        R.string.settings_dock_search_summary_elsewhere
    else -> R.string.settings_state_off
})

@Composable
private fun paddingLabel(size: PaddingSize?, settings: LauncherSettings): String = if (size == null) {
    stringResource(R.string.settings_dock_follow_home_spacing, HomeLayoutMetrics.paddingDp(settings.home.horizontalPadding))
} else spacingLabel(size)

private fun DockBackground.labelResource(): Int = when (this) {
    DockBackground.NONE -> R.string.settings_dock_background_none
    DockBackground.RECTANGLE -> R.string.settings_dock_background_rectangle
    DockBackground.ROUNDED -> R.string.settings_dock_background_rounded
    DockBackground.CARD -> R.string.settings_dock_background_card
    DockBackground.CUSTOM -> R.string.settings_dock_background_custom
}

@Composable
private fun CornerSettings(title: String, corner: DockCorner, preview: (DockCorner) -> Unit, update: (DockCorner) -> Unit) {
    SettingsSliderRow(title, corner.size, { update(corner.copy(size = it)) }, 0f..1f, steps = 9, valueLabel = percentageValueLabel(),
        onValueChanging = { preview(corner.copy(size = it)) })
    SettingsChoiceRow(stringResource(R.string.settings_dock_corner_shape, title), DockCornerShape.entries, corner.shape,
        { stringResource(if (it == DockCornerShape.ROUND) R.string.settings_dock_corner_round else R.string.settings_dock_corner_cut) },
        { update(corner.copy(shape = it)) })
}
