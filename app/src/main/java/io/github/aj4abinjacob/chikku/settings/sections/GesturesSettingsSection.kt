package io.github.aj4abinjacob.chikku.settings.sections

import android.content.ActivityNotFoundException
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import io.github.aj4abinjacob.chikku.settings.previews.GesturePracticePreview
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import io.github.aj4abinjacob.chikku.settings.components.AppPickerList
import io.github.aj4abinjacob.chikku.settings.components.AppPickerRow
import io.github.aj4abinjacob.chikku.settings.components.AppPickerStyle
import io.github.aj4abinjacob.chikku.settings.components.AppSearchField
import io.github.aj4abinjacob.chikku.settings.components.rememberAppIconCache
import io.github.aj4abinjacob.chikku.settings.iconpicker.IconBitmapCache
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.message.rememberChikkuMessages
import io.github.aj4abinjacob.chikku.core.model.AppCustomization
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.ContainerType
import io.github.aj4abinjacob.chikku.core.model.EditResult
import io.github.aj4abinjacob.chikku.core.model.GestureAction
import io.github.aj4abinjacob.chikku.core.model.ItemTarget
import io.github.aj4abinjacob.chikku.core.model.LauncherApp
import io.github.aj4abinjacob.chikku.core.model.WorkspaceItem
import io.github.aj4abinjacob.chikku.core.model.WorkspaceState
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerOpenMethod
import io.github.aj4abinjacob.chikku.core.model.settings.GestureSettings
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.platform.ChikkuAccessibilityService
import io.github.aj4abinjacob.chikku.home.gestures.accessibilityDisclosure
import io.github.aj4abinjacob.chikku.prompt.PromptSheet
import io.github.aj4abinjacob.chikku.settings.LocalHomeDependencies
import io.github.aj4abinjacob.chikku.settings.components.GestureActionPickerDialog
import io.github.aj4abinjacob.chikku.settings.components.SettingsBottomSheet
import io.github.aj4abinjacob.chikku.settings.components.SettingsFastScroller
import io.github.aj4abinjacob.chikku.settings.components.SheetAppListInset
import io.github.aj4abinjacob.chikku.settings.components.SheetListPadding
import io.github.aj4abinjacob.chikku.settings.components.SheetPadding
import io.github.aj4abinjacob.chikku.settings.components.keepUnfilteredHeight
import io.github.aj4abinjacob.chikku.settings.components.rememberUnfilteredHeight
import androidx.compose.foundation.layout.ColumnScope
import io.github.aj4abinjacob.chikku.settings.components.SettingsInfoText
import io.github.aj4abinjacob.chikku.settings.components.SettingsNavigationRow
import io.github.aj4abinjacob.chikku.settings.components.SettingsPage
import io.github.aj4abinjacob.chikku.settings.components.SettingsSectionHeader
import io.github.aj4abinjacob.chikku.settings.components.SettingsSwitchRow
import io.github.aj4abinjacob.chikku.settings.components.SwipeActionsEditorDialog
import io.github.aj4abinjacob.chikku.settings.components.collectLauncherApps
import io.github.aj4abinjacob.chikku.settings.components.gestureActionLabel
import io.github.aj4abinjacob.chikku.settings.components.settingsFastScrollerPadding
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/** A home gesture and where its action lives in [GestureSettings]. */
internal enum class HomeGesture(
    @StringRes val title: Int,
    val get: (GestureSettings) -> GestureAction,
    val set: (GestureSettings, GestureAction) -> GestureSettings,
) {
    SWIPE_UP(R.string.gestures_swipe_up, { it.swipeUp }, { g, a -> g.copy(swipeUp = a) }),
    SWIPE_DOWN(R.string.gestures_swipe_down, { it.swipeDown }, { g, a -> g.copy(swipeDown = a) }),
    SWIPE_LEFT(R.string.gestures_swipe_left, { it.swipeLeft }, { g, a -> g.copy(swipeLeft = a) }),
    SWIPE_RIGHT(R.string.gestures_swipe_right, { it.swipeRight }, { g, a -> g.copy(swipeRight = a) }),
    DOUBLE_TAP(R.string.gestures_double_tap, { it.doubleTap }, { g, a -> g.copy(doubleTap = a) }),
    DOUBLE_TAP_SWIPE_UP(R.string.gestures_double_tap_swipe_up, { it.doubleTapSwipeUp }, { g, a -> g.copy(doubleTapSwipeUp = a) }),
    DOUBLE_TAP_SWIPE_DOWN(R.string.gestures_double_tap_swipe_down, { it.doubleTapSwipeDown }, { g, a -> g.copy(doubleTapSwipeDown = a) }),
    PINCH_IN(R.string.gestures_pinch_in, { it.pinchIn }, { g, a -> g.copy(pinchIn = a) }),
    PINCH_OUT(R.string.gestures_pinch_out, { it.pinchOut }, { g, a -> g.copy(pinchOut = a) }),
    TWO_FINGER_SWIPE_UP(R.string.gestures_two_finger_swipe_up, { it.twoFingerSwipeUp }, { g, a -> g.copy(twoFingerSwipeUp = a) }),
    TWO_FINGER_SWIPE_DOWN(R.string.gestures_two_finger_swipe_down, { it.twoFingerSwipeDown }, { g, a -> g.copy(twoFingerSwipeDown = a) }),
    ROTATE_CLOCKWISE(R.string.gestures_rotate_clockwise, { it.rotateClockwise }, { g, a -> g.copy(rotateClockwise = a) }),
    ROTATE_COUNTERCLOCKWISE(R.string.gestures_rotate_counterclockwise, { it.rotateCounterclockwise }, { g, a -> g.copy(rotateCounterclockwise = a) }),
    HOME_BUTTON(R.string.gestures_home_button, { it.homeGesture }, { g, a -> g.copy(homeGesture = a) }),
}

/** The row icon for a gesture on the main page: the motion it stands for. */
@DrawableRes
private fun HomeGesture.rowIcon(): Int = when (this) {
    HomeGesture.SWIPE_UP -> R.drawable.ic_settings_row_swipe_up
    HomeGesture.SWIPE_DOWN -> R.drawable.ic_settings_row_swipe_down
    HomeGesture.SWIPE_LEFT -> R.drawable.ic_settings_row_swipe_left
    HomeGesture.SWIPE_RIGHT -> R.drawable.ic_settings_row_swipe_right
    HomeGesture.DOUBLE_TAP -> R.drawable.ic_settings_row_touch_app
    HomeGesture.DOUBLE_TAP_SWIPE_UP -> R.drawable.ic_settings_row_swipe_up_alt
    HomeGesture.DOUBLE_TAP_SWIPE_DOWN -> R.drawable.ic_settings_row_swipe_down_alt
    HomeGesture.PINCH_IN -> R.drawable.ic_settings_row_pinch
    HomeGesture.PINCH_OUT -> R.drawable.ic_settings_row_open_in_full
    HomeGesture.TWO_FINGER_SWIPE_UP -> R.drawable.ic_settings_row_keyboard_double_arrow_up
    HomeGesture.TWO_FINGER_SWIPE_DOWN -> R.drawable.ic_settings_row_keyboard_double_arrow_down
    HomeGesture.ROTATE_CLOCKWISE -> R.drawable.ic_settings_row_rotate_right
    HomeGesture.ROTATE_COUNTERCLOCKWISE -> R.drawable.ic_settings_row_rotate_left
    HomeGesture.HOME_BUTTON -> R.drawable.ic_settings_row_home
}

/** Used every day, always visible. The rest sit under "More gestures". */
internal val everydayGestures: List<HomeGesture> = listOf(
    HomeGesture.SWIPE_UP, HomeGesture.SWIPE_DOWN, HomeGesture.SWIPE_LEFT, HomeGesture.SWIPE_RIGHT,
    HomeGesture.DOUBLE_TAP, HomeGesture.HOME_BUTTON,
)

/** Less common gestures, grouped by the motion; shown when the "More gestures" group is open. */
internal val moreGestureGroups: List<Pair<Int, List<HomeGesture>>> = listOf(
    R.string.gestures_group_double_tap_swipe to listOf(HomeGesture.DOUBLE_TAP_SWIPE_UP, HomeGesture.DOUBLE_TAP_SWIPE_DOWN),
    R.string.gestures_group_two_fingers to listOf(
        HomeGesture.PINCH_IN, HomeGesture.PINCH_OUT, HomeGesture.TWO_FINGER_SWIPE_UP, HomeGesture.TWO_FINGER_SWIPE_DOWN,
    ),
    R.string.gestures_group_rotate to listOf(HomeGesture.ROTATE_CLOCKWISE, HomeGesture.ROTATE_COUNTERCLOCKWISE),
)

internal val moreGestures: List<HomeGesture> = moreGestureGroups.flatMap { it.second }

/** Actions that only work through Chikku's optional accessibility service. */
internal fun GestureAction.needsAccessibility(): Boolean = this == GestureAction.LockScreen || this == GestureAction.Recents

/** True when any home gesture is bound to an action that needs the accessibility service. */
internal fun GestureSettings.anyNeedsAccessibility(): Boolean = HomeGesture.entries.any { it.get(this).needsAccessibility() }

/** Gestures: home gestures, home button, icon swipe actions and the optional accessibility service (G07, G10). */
@Composable
fun GesturesSettingsSection(settings: LauncherSettings, update: ((LauncherSettings) -> LauncherSettings) -> Unit) {
    val context = LocalContext.current
    val messages = rememberChikkuMessages()
    val apps = collectLauncherApps()
    val deps = LocalHomeDependencies.current
    var practice by rememberSaveable { mutableStateOf(HomeGesture.SWIPE_UP) }
    var practiceOnDefaultPage by rememberSaveable { mutableStateOf(true) }
    var editing by rememberSaveable { mutableStateOf<HomeGesture?>(null) }
    var showIcons by rememberSaveable { mutableStateOf(false) }
    var showAppDefaults by rememberSaveable { mutableStateOf(false) }
    var moreOpen by rememberSaveable { mutableStateOf(false) }
    var accessibilityOn by remember { mutableStateOf(false) }
    var showAccessibilityDisclosure by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        accessibilityOn = ChikkuAccessibilityService.isEnabled(context)
        onPauseOrDispose { }
    }
    if (showAccessibilityDisclosure) {
        PromptSheet(accessibilityDisclosure(context, messages)) {
            showAccessibilityDisclosure = false
        }
    }
    // Counts for the icon swipe rows; both fall back to zero if a store cannot be read.
    val workspace by remember(deps) { deps.workspaceRepository.workspace.catch { emit(WorkspaceState.Failed(it.javaClass.simpleName)) } }
        .collectAsState(WorkspaceState.Loading)
    val customizations by remember(deps) { deps.appCustomizationRepository.customizations.catch { emit(emptyMap()) } }
        .collectAsState(emptyMap<AppKey, AppCustomization>())
    val iconsWithActions = (workspace as? WorkspaceState.Ready)?.workspace?.items.orEmpty()
        .count { it.target is ItemTarget.App && (it.actions.swipeUp != null || it.actions.swipeDown != null) }
    val appsWithDefaults = customizations.values.count { it.swipeUpAction != null || it.swipeDownAction != null }
    // Icon and app-default swipes can also be bound to Lock screen or Recent apps.
    val iconSwipeNeedsService = (workspace as? WorkspaceState.Ready)?.workspace?.items.orEmpty()
        .any { it.actions.swipeUp?.needsAccessibility() == true || it.actions.swipeDown?.needsAccessibility() == true } ||
        customizations.values.any { it.swipeUpAction?.needsAccessibility() == true || it.swipeDownAction?.needsAccessibility() == true }

    val notSet = stringResource(R.string.gestures_not_set)
    val moreState = stringResource(if (moreOpen) R.string.gestures_more_state_expanded else R.string.gestures_more_state_collapsed)
    val badgeText = stringResource(if (accessibilityOn) R.string.gestures_needs_accessibility_on else R.string.gestures_needs_accessibility_off)
    @Composable
    fun GestureRow(gesture: HomeGesture) {
        val action = gesture.get(settings.gestures)
        val value = if (action == GestureAction.None) notSet else gestureActionLabel(action, apps)
        val summary = listOfNotNull(
            if (gesture == HomeGesture.HOME_BUTTON) stringResource(R.string.gestures_home_button_summary) else null,
            if (gesture == HomeGesture.SWIPE_UP && action == GestureAction.OpenDrawer && settings.drawer.openMethod == DrawerOpenMethod.BUTTON) {
                stringResource(R.string.gestures_swipe_up_drawer_off)
            } else {
                null
            },
            if (action.needsAccessibility()) badgeText else null,
        ).joinToString("\n").ifEmpty { null }
        SettingsNavigationRow(title = stringResource(gesture.title), value = value, summary = summary,
            onClick = { practice = gesture; editing = gesture }, icon = gesture.rowIcon())
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.heightIn(max = (LocalConfiguration.current.screenHeightDp * .4f).coerceAtLeast(120f).dp)
            .verticalScroll(rememberScrollState())) {
            GesturePracticePreview(settings, practice,
                onDetected = { practice = it; if (it in moreGestures) moreOpen = true },
                onDefaultPage = practiceOnDefaultPage, onDefaultPageChange = { practiceOnDefaultPage = it })
        }
        SettingsPage(Modifier.weight(1f)) {
            SettingsSectionHeader(stringResource(R.string.gestures_settings_home_header))
            everydayGestures.forEach { GestureRow(it) }
            SettingsSwitchRow(
                title = stringResource(R.string.gestures_home_button_only_default_page),
                summary = stringResource(R.string.gestures_home_button_only_default_page_summary),
                checked = settings.gestures.homeButtonOnlyOnDefaultPage,
                onCheckedChange = { value ->
                    // The switch only matters away from the default page; show the home button there.
                    practice = HomeGesture.HOME_BUTTON
                    practiceOnDefaultPage = false
                    update { it.copy(gestures = it.gestures.copy(homeButtonOnlyOnDefaultPage = value)) }
                },
                icon = R.drawable.ic_settings_row_first_page,
            )
            SettingsInfoText(stringResource(R.string.gestures_extra_scope))

            val setMore = moreGestures.count { it.get(settings.gestures) != GestureAction.None }
            SettingsNavigationRow(
                title = stringResource(R.string.gestures_more_header, setMore, moreGestures.size),
                summary = stringResource(if (moreOpen) R.string.gestures_more_expanded_hint else R.string.gestures_more_collapsed_hint),
                onClick = { moreOpen = !moreOpen },
                modifier = Modifier.semantics {
                    stateDescription = moreState
                },
                icon = if (moreOpen) R.drawable.ic_settings_row_keyboard_double_arrow_up else R.drawable.ic_settings_row_keyboard_double_arrow_down,
            )
            if (moreOpen) {
                moreGestureGroups.forEach { (title, gestures) ->
                    SettingsSectionHeader(stringResource(title))
                    gestures.forEach { GestureRow(it) }
                }
            }

            SettingsSectionHeader(stringResource(R.string.gestures_settings_icons_header))
            SettingsNavigationRow(
                title = stringResource(R.string.gestures_home_icons),
                summary = pluralStringResource(R.plurals.gestures_icons_with_actions, iconsWithActions, iconsWithActions),
                onClick = { showIcons = true },
                icon = R.drawable.ic_settings_row_apps,
            )
            SettingsNavigationRow(
                title = stringResource(R.string.gestures_app_defaults),
                summary = pluralStringResource(R.plurals.gestures_apps_with_defaults, appsWithDefaults, appsWithDefaults),
                onClick = { showAppDefaults = true },
                icon = R.drawable.ic_settings_row_settings_applications,
            )
            SettingsInfoText(stringResource(R.string.gestures_icon_swipes_info), details = stringResource(R.string.gestures_icon_swipes_info_details))

            SettingsSectionHeader(stringResource(R.string.gestures_settings_access_header))
            val needsService = settings.gestures.anyNeedsAccessibility() || iconSwipeNeedsService
            val openServiceSettings = {
                if (!accessibilityOn) {
                    showAccessibilityDisclosure = true
                } else try {
                    context.startActivity(ChikkuAccessibilityService.settingsIntent(context))
                } catch (_: ActivityNotFoundException) {
                    messages.show(R.string.gesture_accessibility_settings_unavailable)
                } catch (_: SecurityException) {
                    messages.show(R.string.gesture_accessibility_settings_unavailable)
                }
            }
            when {
                // Used and off: say what is broken and offer the fix right here.
                needsService && !accessibilityOn -> {
                    SettingsInfoText(stringResource(R.string.gestures_accessibility_card_title))
                    SettingsNavigationRow(
                        title = stringResource(R.string.gestures_accessibility_turn_on),
                        summary = stringResource(R.string.gestures_accessibility_card_off),
                        onClick = openServiceSettings,
                        icon = R.drawable.ic_settings_row_accessibility,
                    )
                }
                needsService -> SettingsNavigationRow(
                    title = stringResource(R.string.gestures_accessibility_status_on),
                    summary = stringResource(R.string.gestures_accessibility_on_summary),
                    onClick = openServiceSettings,
                    icon = R.drawable.ic_settings_row_accessibility,
                )
                // Not in use: always reachable, never nagging.
                else -> SettingsNavigationRow(
                    title = stringResource(R.string.gestures_accessibility_unused),
                    summary = stringResource(R.string.gestures_accessibility_unused_summary),
                    onClick = openServiceSettings,
                    icon = R.drawable.ic_settings_row_accessibility,
                )
            }
            SettingsInfoText(stringResource(R.string.gestures_access_info), details = stringResource(R.string.gestures_access_info_details))
        }
    }

    editing?.let { gesture ->
        GestureActionPickerDialog(
            title = stringResource(gesture.title),
            current = gesture.get(settings.gestures),
            onPick = { picked ->
                val action = picked ?: GestureAction.None
                update { it.copy(gestures = gesture.set(it.gestures, action)) }
            },
            onDismiss = { editing = null },
        )
    }
    if (showIcons) WorkspaceIconsDialog(apps) { showIcons = false }
    if (showAppDefaults) AppDefaultsDialog(apps) { showAppDefaults = false }
}

/** App icons on the desktop and dock, each with its own swipe actions (ItemActions). */
@Composable
private fun WorkspaceIconsDialog(apps: List<LauncherApp>?, onDismiss: () -> Unit) {
    val deps = LocalHomeDependencies.current
    val messages = rememberChikkuMessages()
    val flow = remember(deps) { deps.workspaceRepository.workspace.catch { emit(WorkspaceState.Failed(it.javaClass.simpleName)) } }
    val state by flow.collectAsState(WorkspaceState.Loading)
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    val labels = remember(apps) { apps.orEmpty().associate { it.key to it.label } }
    val unknown = stringResource(R.string.home_unknown_app)
    val items = (state as? WorkspaceState.Ready)?.workspace?.items.orEmpty()
        .filter { it.target is ItemTarget.App }
        .sortedWith(compareBy<WorkspaceItem>({ it.placement.container != ContainerType.DOCK }, { it.placement.page }, { it.placement.y }, { it.placement.x }))
    fun labelOf(item: WorkspaceItem) = labels[(item.target as ItemTarget.App).key] ?: unknown
    val needle = query.trim()
    val shown = if (needle.isEmpty()) items else items.filter { labelOf(it).contains(needle, ignoreCase = true) }
    // Icons that already have actions lead, so they can be found again without scrolling.
    val configured = if (needle.isEmpty()) items.filter { it.actions.swipeUp != null || it.actions.swipeDown != null } else emptyList()
    val defaultLabel = stringResource(R.string.gestures_app_defaults)

    // The editor sheet opens over this list, which stays (with its search and scroll) for the next icon.
    ListSheet(
        title = stringResource(R.string.gestures_home_icons),
        onDismiss = onDismiss,
        emptyText = when (state) {
            WorkspaceState.Loading -> stringResource(R.string.gesture_picker_loading)
            is WorkspaceState.Failed -> stringResource(R.string.gestures_icons_unavailable)
            is WorkspaceState.Ready -> stringResource(R.string.gestures_no_icons)
        }.takeIf { items.isEmpty() },
        query = query,
        onQueryChange = { query = it },
    ) { cache ->
        @Composable
        fun IconRow(item: WorkspaceItem) {
            val location = if (item.placement.container == ContainerType.DOCK) {
                stringResource(R.string.gestures_icon_location_dock)
            } else {
                stringResource(R.string.gestures_icon_location_page, item.placement.page + 1)
            }
            val actions = if (item.actions.swipeUp != null || item.actions.swipeDown != null) {
                stringResource(
                    R.string.gestures_app_default_summary,
                    gestureActionLabel(item.actions.swipeUp, apps, defaultLabel),
                    gestureActionLabel(item.actions.swipeDown, apps, defaultLabel),
                )
            } else {
                null
            }
            AppPickerRow(
                appKey = (item.target as ItemTarget.App).key,
                title = labelOf(item),
                summary = listOfNotNull(location, actions).joinToString(" · "),
                selected = false,
                style = AppPickerStyle.NAVIGATION,
                cache = cache,
                inset = SheetAppListInset,
            ) { editingId = item.id }
        }
        if (configured.isNotEmpty()) {
            item(key = "configured-label") { ListSheetLabel(stringResource(R.string.gestures_with_actions)) }
            items(configured, key = { "configured|" + it.id }) { IconRow(it) }
            item(key = "all-label") { ListSheetLabel(stringResource(R.string.gestures_all_icons)) }
        }
        if (shown.isEmpty()) {
            item(key = "no-match") { ListSheetInfo(stringResource(R.string.app_picker_no_matches)) }
        }
        items(shown, key = { it.id }) { IconRow(it) }
    }

    val editingItem = items.firstOrNull { it.id == editingId }
    if (editingItem != null) {
        SwipeActionsEditorDialog(
            title = labelOf(editingItem),
            swipeUp = editingItem.actions.swipeUp,
            swipeDown = editingItem.actions.swipeDown,
            apps = apps,
            defaultLabel = defaultLabel,
            onChange = { up, down ->
                deps.appScope.launch {
                    val result = deps.workspaceRepository.setItemActions(
                        editingItem.id,
                        editingItem.actions.copy(swipeUp = up, swipeDown = down),
                    )
                    if (result !is EditResult.Applied) {
                        messages.show(R.string.gestures_save_failed)
                    }
                }
            },
            onDismiss = { editingId = null },
            appKey = (editingItem.target as ItemTarget.App).key,
        )
    }
}

/** Per-app default swipe actions (AppCustomization), used wherever the app's icon has none of its own. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppDefaultsDialog(apps: List<LauncherApp>?, onDismiss: () -> Unit) {
    val deps = LocalHomeDependencies.current
    val messages = rememberChikkuMessages()
    val flow = remember(deps) { deps.appCustomizationRepository.customizations.catch { emit(emptyMap()) } }
    val customizations by flow.collectAsState(emptyMap<AppKey, AppCustomization>())
    var editingKey by remember { mutableStateOf<AppKey?>(null) }
    val noneLabel = stringResource(R.string.gesture_action_none)
    fun configured(app: LauncherApp) = customizations[app.key]?.let { it.swipeUpAction != null || it.swipeDownAction != null } == true

    // The editor sheet opens over this list, which stays (with its search and scroll) for the next app.
    SettingsBottomSheet(stringResource(R.string.gestures_app_defaults), onDismiss) {
        AppPickerList(
            apps = apps,
            isSelected = ::configured,
            onClick = { editingKey = it.key },
            modifier = Modifier.weight(1f, fill = false).listSheetContent(),
            style = AppPickerStyle.NAVIGATION,
            fillHeight = false,
            inset = SheetAppListInset,
            searchInset = 0.dp,
            // Apps that already have defaults lead, so they can be found again without scrolling.
            pinnedTitle = stringResource(R.string.gestures_with_actions),
            allTitle = stringResource(R.string.gesture_picker_all_apps),
            searchBackground = BottomSheetDefaults.ContainerColor,
            summary = { app ->
                val custom = customizations[app.key]
                if (configured(app)) {
                    stringResource(
                        R.string.gestures_app_default_summary,
                        gestureActionLabel(custom?.swipeUpAction, apps, noneLabel),
                        gestureActionLabel(custom?.swipeDownAction, apps, noneLabel),
                    )
                } else {
                    null
                }
            },
        )
    }

    editingKey?.let { key ->
        val custom = customizations[key]
        SwipeActionsEditorDialog(
            title = appLabel(apps, key),
            swipeUp = custom?.swipeUpAction,
            swipeDown = custom?.swipeDownAction,
            apps = apps,
            defaultLabel = noneLabel,
            onChange = { up, down ->
                deps.appScope.launch {
                    val saved = deps.appCustomizationRepository.update(key) { it.copy(swipeUpAction = up, swipeDownAction = down) }
                    if (!saved) messages.show(R.string.gestures_save_failed)
                }
            },
            onDismiss = { editingKey = null },
            appKey = key,
            defaultUsesAppDefaults = false,
        )
    }
}

/**
 * A searchable list in a [SettingsBottomSheet]; [content] gets the shared icon cache for its rows.
 * [emptyText] replaces it while there is nothing to list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListSheet(
    title: String,
    onDismiss: () -> Unit,
    emptyText: String?,
    query: String,
    onQueryChange: (String) -> Unit,
    content: LazyListScope.(IconBitmapCache) -> Unit,
) {
    SettingsBottomSheet(title, onDismiss) {
        ListSheetContent(emptyText, query, onQueryChange, content)
    }
}

/**
 * The body of [ListSheet], without the sheet window (so tests can draw it): a search field over the
 * list. It hugs its rows (no gap below a short list) up to the sheet's cap, less the keyboard, which
 * it stays above, and scrolls past that; while [query] filters it keeps its unfiltered height, so
 * the search field does not move as rows drop out.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ColumnScope.ListSheetContent(
    emptyText: String?,
    query: String,
    onQueryChange: (String) -> Unit,
    content: LazyListScope.(IconBitmapCache) -> Unit,
) {
    val cache = rememberAppIconCache()
    if (emptyText != null) {
        Text(emptyText, modifier = Modifier.padding(horizontal = SheetPadding, vertical = 12.dp))
        return
    }
    val unfilteredHeight = rememberUnfilteredHeight()
    Column(Modifier.weight(1f, fill = false).listSheetContent().keepUnfilteredHeight(unfilteredHeight, query.isNotBlank())) {
        // Keep the focused field outside the lazy items, whose count and keys change as it filters.
        AppSearchField(query, onQueryChange, BottomSheetDefaults.ContainerColor)
        Box(Modifier.weight(1f, fill = false)) {
            val listState = rememberLazyListState()
            LazyColumn(Modifier.fillMaxWidth(), state = listState, contentPadding = settingsFastScrollerPadding()) {
                content(cache)
            }
            SettingsFastScroller(listState)
        }
    }
}

/** A list sheet's searchable content, in the shared sheet list inset and kept above the keyboard. */
private fun Modifier.listSheetContent(): Modifier = this
    .fillMaxWidth()
    .imePadding()
    .padding(horizontal = SheetListPadding)

@Composable
private fun ListSheetLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        // Lines up with the rows' icons (SheetAppListInset + AppPickerRowPadding inside the list).
        modifier = Modifier.padding(start = SheetPadding - SheetListPadding, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun ListSheetInfo(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = SheetPadding - SheetListPadding, vertical = 12.dp),
    )
}

@Composable
private fun appLabel(apps: List<LauncherApp>?, key: AppKey): String =
    apps?.firstOrNull { it.key == key }?.label ?: stringResource(R.string.home_unknown_app)
