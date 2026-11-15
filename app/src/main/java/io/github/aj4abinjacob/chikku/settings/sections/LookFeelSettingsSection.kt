package io.github.aj4abinjacob.chikku.settings.sections

import io.github.aj4abinjacob.chikku.core.model.settings.MenuActionChoice
import io.github.aj4abinjacob.chikku.core.model.settings.canAddMenuChoice
import io.github.aj4abinjacob.chikku.core.model.settings.isMenuChoiceOn
import io.github.aj4abinjacob.chikku.core.model.settings.menuChoicesFor
import io.github.aj4abinjacob.chikku.core.model.settings.withMenuChoice
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.settings.AnimationSpeed
import io.github.aj4abinjacob.chikku.core.model.settings.AppMenuStyle
import io.github.aj4abinjacob.chikku.core.model.settings.IconShape
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.core.model.settings.LookFeelSettings
import io.github.aj4abinjacob.chikku.core.model.settings.ScreenOrientation
import io.github.aj4abinjacob.chikku.core.model.settings.StatusBarMode
import io.github.aj4abinjacob.chikku.platform.icons.IconPackInfo
import io.github.aj4abinjacob.chikku.platform.icons.IconPackRepository
import io.github.aj4abinjacob.chikku.settings.components.AccentColorPicker
import io.github.aj4abinjacob.chikku.settings.components.AccentThemeLivePreview
import io.github.aj4abinjacob.chikku.settings.components.CHIKKU_DEFAULT_ACCENT
import io.github.aj4abinjacob.chikku.settings.components.SettingsChoiceRow
import io.github.aj4abinjacob.chikku.settings.components.SettingsColorRow
import io.github.aj4abinjacob.chikku.settings.components.SettingsInfoText
import io.github.aj4abinjacob.chikku.settings.components.SettingsNavigationRow
import io.github.aj4abinjacob.chikku.settings.components.SettingsPage
import io.github.aj4abinjacob.chikku.settings.components.SettingsSectionHeader
import io.github.aj4abinjacob.chikku.settings.components.SettingsSliderRow
import io.github.aj4abinjacob.chikku.settings.components.SettingsStepRow
import io.github.aj4abinjacob.chikku.settings.components.SettingsSubPage
import io.github.aj4abinjacob.chikku.settings.components.SettingsSubPageLink
import io.github.aj4abinjacob.chikku.settings.components.SettingsSubPageRow
import io.github.aj4abinjacob.chikku.settings.components.SettingsSwitchRow
import io.github.aj4abinjacob.chikku.settings.components.dpValueLabel
import io.github.aj4abinjacob.chikku.settings.components.rememberSettingsSubPage
import io.github.aj4abinjacob.chikku.settings.iconpicker.IconPickerActivity
import io.github.aj4abinjacob.chikku.settings.previews.AnimationSpeedLivePreview
import io.github.aj4abinjacob.chikku.settings.previews.AppMenuLivePreview
import io.github.aj4abinjacob.chikku.settings.previews.AppMenuStylePreview
import io.github.aj4abinjacob.chikku.settings.previews.AppMenuSurfaceSelector
import io.github.aj4abinjacob.chikku.settings.previews.AppearanceIconsLivePreview
import io.github.aj4abinjacob.chikku.settings.previews.AppearanceScreenLivePreview
import io.github.aj4abinjacob.chikku.settings.previews.IconShapePreview
import io.github.aj4abinjacob.chikku.settings.previews.StatusBarPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Routes are shared with Settings search; each task has only its own controls and preview. */
internal object AppearancePages {
    val Accent = SettingsSubPageLink("appearance_accent", R.string.look_header_accent)
    val Icons = SettingsSubPageLink("appearance_icons", R.string.look_header_icons)
    val Motion = SettingsSubPageLink("appearance_motion", R.string.look_header_motion)
    val Screen = SettingsSubPageLink("appearance_screen", R.string.look_header_window)
    val Menus = SettingsSubPageLink("appearance_menus", R.string.look_header_menus)
    val all = listOf(Accent, Icons, Motion, Screen, Menus)
}

private typealias AppearanceUpdate = ((LookFeelSettings) -> LookFeelSettings) -> Unit

/** Match the preview surface to the controls being edited, without changing saved preferences. */
internal fun menuPreviewSurfaceAfterEdit(before: LookFeelSettings, after: LookFeelSettings, current: Boolean): Boolean = when {
    menuChoicesFor(true).any { before.isMenuChoiceOn(it, true) != after.isMenuChoiceOn(it, true) } -> true
    menuChoicesFor(false).any { before.isMenuChoiceOn(it, false) != after.isMenuChoiceOn(it, false) } -> false
    else -> current
}

/**
 * The menu action the preview keeps in view after an edit: the one whose toggle changed. Style and
 * corner edits clear it, so the top of the card (and its corners) shows again.
 */
internal fun menuPreviewFocusAfterEdit(before: LookFeelSettings, after: LookFeelSettings, current: Int?): Int? {
    MenuActionChoice.entries.firstOrNull { choice ->
        listOf(false, true).any { drawer -> before.isMenuChoiceOn(choice, drawer) != after.isMenuChoiceOn(choice, drawer) }
    }?.let { return it.menuTitle() }
    return if (before.appMenuStyle != after.appMenuStyle || before.menuCornerRadiusDp != after.menuCornerRadiusDp) null else current
}

private fun MenuActionChoice.menuTitle() = when (this) {
    MenuActionChoice.EDIT -> R.string.app_menu_edit
    MenuActionChoice.SWIPE -> R.string.app_menu_swipe_actions
    MenuActionChoice.INFO -> R.string.app_menu_app_info
    MenuActionChoice.STORE -> R.string.app_menu_store
    MenuActionChoice.REMOVE -> R.string.app_menu_remove
    MenuActionChoice.HIDE -> R.string.app_menu_hide
    MenuActionChoice.UNINSTALL -> R.string.app_menu_uninstall
}

/** A short task list opens focused subpages; no unrelated preview crowds the overview. */
@Composable
fun LookFeelSettingsSection(settings: LauncherSettings, update: ((LauncherSettings) -> LauncherSettings) -> Unit) {
    var page by rememberSettingsSubPage()
    val open = AppearancePages.all.firstOrNull { it.route == page }
    val look = settings.lookFeel
    var menuDrawer by remember { mutableStateOf(false) }
    var menuFocus by remember { mutableStateOf<Int?>(null) }
    var liveMenuCorner by remember(look.menuCornerRadiusDp) { mutableFloatStateOf(look.menuCornerRadiusDp) }
    var liveSpeed by remember(look.animationSpeed) { mutableStateOf(look.animationSpeed) }
    val edit: AppearanceUpdate = { change ->
        menuDrawer = menuPreviewSurfaceAfterEdit(look, change(look), menuDrawer)
        menuFocus = menuPreviewFocusAfterEdit(look, change(look), menuFocus)
        update { it.copy(lookFeel = change(it.lookFeel)) }
    }
    if (open == null) {
        SettingsPage {
            AppearancePages.all.forEach { link ->
                val summary = when (link) {
                    AppearancePages.Accent -> R.string.look_page_accent_summary
                    AppearancePages.Icons -> R.string.look_page_icons_summary
                    AppearancePages.Motion -> R.string.look_page_motion_summary
                    AppearancePages.Screen -> R.string.look_page_screen_summary
                    else -> R.string.look_page_menus_summary
                }
                val icon = when (link) {
                    AppearancePages.Accent -> R.drawable.ic_settings_palette
                    AppearancePages.Icons -> R.drawable.ic_settings_row_grid_view
                    AppearancePages.Motion -> R.drawable.ic_settings_row_swipe_up
                    AppearancePages.Screen -> R.drawable.ic_settings_row_fullscreen
                    else -> R.drawable.ic_menu_edit
                }
                SettingsSubPageRow(stringResource(link.title), stringResource(summary), icon = icon) { page = link.route }
            }
        }
        return
    }
    SettingsSubPage(stringResource(open.title), onBack = { page = null }) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val previewHeight = minOf(208.dp, maxHeight * .32f)
            val menuSelector: @Composable () -> Unit = { AppMenuSurfaceSelector(menuDrawer, { menuDrawer = it }) }
            Column(Modifier.fillMaxSize()) {
                AppearancePreviewPane(previewHeight, open, captionTrailing = if (open == AppearancePages.Menus) menuSelector else null) {
                    when (open) {
                        AppearancePages.Accent -> AccentThemeLivePreview(look.accentColor ?: CHIKKU_DEFAULT_ACCENT)
                        AppearancePages.Icons -> AppearanceIconsLivePreview(look)
                        AppearancePages.Motion -> AnimationSpeedLivePreview(liveSpeed)
                        AppearancePages.Screen -> AppearanceScreenLivePreview(look)
                        AppearancePages.Menus -> AppMenuLivePreview(
                            settings.copy(lookFeel = look.copy(menuCornerRadiusDp = liveMenuCorner)),
                            fromDrawer = menuDrawer, onSurfaceChange = { menuDrawer = it }, menuHeight = 132.dp,
                            focusAction = menuFocus, showSurfaceSelector = false,
                        )
                    }
                }
                SettingsPage(Modifier.weight(1f).semantics { testTagsAsResourceId = true }
                    .testTag("appearance-settings-controls")) {
                    when (open) {
                        AppearancePages.Accent -> AccentControls(look, edit)
                        AppearancePages.Icons -> IconControls(look, edit)
                        AppearancePages.Motion -> MotionControls(look, edit) { liveSpeed = it }
                        AppearancePages.Screen -> ScreenControls(look, edit)
                        AppearancePages.Menus -> MenuControls(look, edit) { liveMenuCorner = it; menuFocus = null }
                    }
                }
            }
        }
    }
}

/**
 * Wraps the page's preview, capped by the window. It never shrinks while the page is open, so a
 * setting that makes the preview shorter does not pull the controls below up and down.
 */
@Composable
private fun AppearancePreviewPane(maxHeight: Dp, page: Any, captionTrailing: (@Composable () -> Unit)? = null, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    var tallest by remember(page) { mutableStateOf(0.dp) }
    Column(Modifier.fillMaxWidth().heightIn(min = minOf(tallest, maxHeight), max = maxHeight)
        .onSizeChanged { size -> with(density) { tallest = maxOf(tallest, size.height.toDp()) } }
        .verticalScroll(rememberScrollState())) {
        // A page's own preview switch shares the caption's line, leaving the height to the preview.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.preview_appearance_choose), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 8.dp))
            captionTrailing?.invoke()
        }
        content()
    }
}

@Composable
private fun AccentControls(look: LookFeelSettings, edit: AppearanceUpdate) {
    AccentColorPicker(
        accent = look.accentColor,
        onAccentChange = { color -> edit { it.copy(accentColor = color) } },
    )
    SettingsInfoText(stringResource(R.string.look_accent_info))
}

@Composable
private fun IconControls(look: LookFeelSettings, edit: AppearanceUpdate) {
    val context = LocalContext.current
    val packs by produceState<List<IconPackInfo>?>(null) {
        value = withContext(Dispatchers.IO) {
            try {
                IconPackRepository(context).installedPacks()
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
    val pkg = look.iconPackPackage
    val current = when {
        pkg == null -> stringResource(R.string.look_icon_pack_system)
        else -> packs?.firstOrNull { it.packageName == pkg }?.label
            ?: if (packs == null) pkg else stringResource(R.string.look_icon_pack_missing)
    }
    // A full-screen chooser: a dialog of radio buttons does not scale to dozens of installed packs.
    SettingsNavigationRow(
        title = stringResource(R.string.look_icon_pack),
        onClick = { context.startActivity(IconPickerActivity.packChooserIntent(context)) },
        modifier = Modifier.semantics { stateDescription = current },
        value = current,
    )
    if (packs?.isEmpty() == true) SettingsInfoText(stringResource(R.string.look_icon_pack_none_installed))
    SettingsChoiceRow(
        title = stringResource(R.string.look_icon_shape),
        optionPreview = { IconShapePreview(it) },
        optionSummary = { if (it == IconShape.SYSTEM) stringResource(R.string.hint_icon_shape_system) else null },
        options = IconShape.entries,
        selected = look.iconShape,
        optionLabel = { stringResource(iconShapeLabel(it)) },
        onSelect = { shape -> edit { it.copy(iconShape = shape) } },
    )
    SettingsSwitchRow(
        title = stringResource(R.string.look_reshape_legacy),
        summary = stringResource(R.string.look_reshape_legacy_summary),
        checked = look.reshapeLegacyIcons,
        onCheckedChange = { checked -> edit { it.copy(reshapeLegacyIcons = checked) } },
    )
    SettingsNavigationRow(
        title = stringResource(R.string.look_per_app_icons),
        summary = stringResource(R.string.look_per_app_icons_summary),
        onClick = { context.startActivity(IconPickerActivity.intent(context)) },
    )
    SettingsInfoText(stringResource(R.string.look_icon_pack_notice))
}

@Composable
private fun MotionControls(look: LookFeelSettings, edit: AppearanceUpdate, onSpeedChanging: (AnimationSpeed) -> Unit) {
    SettingsStepRow(
        title = stringResource(R.string.settings_look_animation_speed),
        options = AnimationSpeed.entries,
        selected = look.animationSpeed,
        optionLabel = { speed -> stringResource(animationSpeedLabel(speed)) },
        onSelect = { speed -> edit { it.copy(animationSpeed = speed) } },
        onValueChanging = onSpeedChanging,
    )
}

@Composable
private fun ScreenControls(look: LookFeelSettings, edit: AppearanceUpdate) {
    SettingsChoiceRow(
        title = stringResource(R.string.look_orientation),
        options = ScreenOrientation.entries,
        selected = look.orientation,
        optionLabel = {
            stringResource(
                when (it) {
                    ScreenOrientation.AUTO -> R.string.look_orientation_auto
                    ScreenOrientation.PORTRAIT -> R.string.look_orientation_portrait
                    ScreenOrientation.LANDSCAPE -> R.string.look_orientation_landscape
                },
            )
        },
        onSelect = { value -> edit { it.copy(orientation = value) } },
    )
    SettingsChoiceRow(
        title = stringResource(R.string.look_status_bar),
        optionPreview = { StatusBarPreview(it) },
        optionSummary = { if (it == StatusBarMode.TRANSPARENT) stringResource(R.string.hint_status_bar_transparent) else null },
        options = StatusBarMode.entries,
        selected = look.statusBar,
        optionLabel = {
            stringResource(
                when (it) {
                    StatusBarMode.SHOWN -> R.string.look_status_bar_shown
                    StatusBarMode.TRANSPARENT -> R.string.look_status_bar_transparent
                    StatusBarMode.HIDDEN -> R.string.look_status_bar_hidden
                },
            )
        },
        onSelect = { value -> edit { it.copy(statusBar = value) } },
    )
}

@Composable
private fun MenuControls(look: LookFeelSettings, edit: AppearanceUpdate, onCornerChanging: (Float) -> Unit) {
    SettingsChoiceRow(
        title = stringResource(R.string.look_app_menu_style),
        optionPreview = { AppMenuStylePreview(it) },
        optionSummary = { stringResource(if (it == AppMenuStyle.POPUP) R.string.hint_app_menu_popup else R.string.hint_app_menu_detailed) },
        options = AppMenuStyle.entries,
        selected = look.appMenuStyle,
        optionLabel = {
            stringResource(
                when (it) {
                    AppMenuStyle.POPUP -> R.string.look_app_menu_popup
                    AppMenuStyle.DETAILED -> R.string.look_app_menu_detailed
                },
            )
        },
        onSelect = { value -> edit { it.copy(appMenuStyle = value) } },
    )
    SettingsColorRow(
        title = stringResource(R.string.menu_settings_background_color),
        color = look.menuBackgroundColor,
        onColorChange = { color -> edit { it.copy(menuBackgroundColor = color) } },
    )
    SettingsColorRow(
        title = stringResource(R.string.menu_settings_text_color),
        color = look.menuTextColor,
        onColorChange = { color -> edit { it.copy(menuTextColor = color) } },
    )
    SettingsSliderRow(
        title = stringResource(R.string.menu_settings_corner_radius),
        value = look.menuCornerRadiusDp,
        onValueChangeFinished = { value -> edit { it.copy(menuCornerRadiusDp = value) } },
        onValueChanging = onCornerChanging,
        valueRange = 0f..48f,
        valueLabel = dpValueLabel(),
    )
    MenuActionToggles(look, fromDrawer = false, header = R.string.menu_settings_home_actions, edit = edit)
    MenuActionToggles(look, fromDrawer = true, header = R.string.menu_settings_drawer_actions, edit = edit)
    SettingsInfoText(stringResource(R.string.menu_settings_actions_info))
}

private fun iconShapeLabel(shape: IconShape): Int = when (shape) {
    IconShape.SYSTEM -> R.string.look_shape_system
    IconShape.CIRCLE -> R.string.look_shape_circle
    IconShape.SQUIRCLE -> R.string.look_shape_squircle
    IconShape.ROUNDED_SQUARE -> R.string.look_shape_rounded_square
    IconShape.SQUARE -> R.string.look_shape_square
    IconShape.TEARDROP -> R.string.look_shape_teardrop
}

private fun animationSpeedLabel(speed: AnimationSpeed): Int = when (speed) {
    AnimationSpeed.NONE -> R.string.settings_animation_none
    AnimationSpeed.FAST -> R.string.settings_animation_fast
    AnimationSpeed.NORMAL -> R.string.settings_animation_normal
    AnimationSpeed.SLOW -> R.string.settings_animation_slow
    AnimationSpeed.RELAXED -> R.string.settings_animation_relaxed
}

private fun MenuActionChoice.title() = when (this) {
    MenuActionChoice.EDIT -> R.string.menu_settings_edit
    MenuActionChoice.SWIPE -> R.string.menu_settings_swipe
    MenuActionChoice.INFO -> R.string.menu_settings_info
    MenuActionChoice.STORE -> R.string.menu_settings_store
    MenuActionChoice.REMOVE -> R.string.menu_settings_remove
    MenuActionChoice.HIDE -> R.string.menu_settings_hide
    MenuActionChoice.UNINSTALL -> R.string.menu_settings_uninstall
}

private fun MenuActionChoice.summary() = when (this) {
    MenuActionChoice.EDIT -> R.string.menu_settings_edit_summary
    MenuActionChoice.SWIPE -> R.string.menu_settings_swipe_summary
    MenuActionChoice.INFO -> R.string.menu_settings_info_summary
    MenuActionChoice.STORE -> R.string.menu_settings_store_summary
    MenuActionChoice.REMOVE -> R.string.menu_settings_remove_summary
    MenuActionChoice.HIDE -> R.string.menu_settings_hide_summary
    MenuActionChoice.UNINSTALL -> R.string.menu_settings_uninstall_summary
}

/** One switch per action for a surface; at [MAX_MENU_ACTIONS] the switches that are off wait until another is turned off. */
@Composable
private fun MenuActionToggles(look: LookFeelSettings, fromDrawer: Boolean, header: Int, edit: AppearanceUpdate) {
    SettingsSectionHeader(stringResource(header))
    val full = !look.canAddMenuChoice(fromDrawer)
    menuChoicesFor(fromDrawer).forEach { choice ->
        val on = look.isMenuChoiceOn(choice, fromDrawer)
        SettingsSwitchRow(
            stringResource(choice.title()), on,
            { value -> edit { it.withMenuChoice(choice, fromDrawer, value) } },
            modifier = menuActionModifier(fromDrawer, choice.title()),
            summary = stringResource(if (!on && full) R.string.menu_settings_limit_reached else choice.summary()),
            enabled = on || !full,
        )
    }
}

@Composable
private fun menuActionModifier(fromDrawer: Boolean, title: Int): Modifier {
    val description = stringResource(R.string.preview_menu_scoped_control,
        stringResource(if (fromDrawer) R.string.settings_section_drawer else R.string.settings_section_home), stringResource(title))
    return Modifier.semantics { contentDescription = description }
}
