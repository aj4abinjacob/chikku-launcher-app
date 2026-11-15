package io.github.aj4abinjacob.chikku.settings.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.flow.first
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.Icon
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.AppsState
import io.github.aj4abinjacob.chikku.core.model.GestureAction
import io.github.aj4abinjacob.chikku.core.model.LauncherApp
import io.github.aj4abinjacob.chikku.core.model.ProfileType
import io.github.aj4abinjacob.chikku.platform.AppLockGuard
import io.github.aj4abinjacob.chikku.platform.LauncherAppsShortcuts
import io.github.aj4abinjacob.chikku.settings.LocalHomeDependencies
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/*
 * Reusable gesture-action picker (home gestures, icon swipes, folder swipe/tap actions).
 *
 *     GestureActionPickerDialog(
 *         title = "Swipe up",
 *         current = action,          // null = "use default" when allowDefault
 *         onPick = { picked -> ... },// GestureAction, or null for "use default"
 *         onDismiss = { ... },
 *     )
 *
 * Needs LocalHomeDependencies (provided by SettingsActivity) for the app list. Labels for a stored
 * action come from gestureActionLabel().
 */

/** String resource naming a simple (argument-free) action; null for app/shortcut actions. */
@StringRes
fun simpleGestureActionLabel(action: GestureAction): Int? = when (action) {
    GestureAction.None -> R.string.gesture_action_none
    GestureAction.OpenDrawer -> R.string.gesture_action_open_drawer
    GestureAction.OpenSearch -> R.string.gesture_action_open_search
    GestureAction.OpenCards -> R.string.gesture_action_open_cards
    GestureAction.ExpandNotifications -> R.string.gesture_action_expand_notifications
    GestureAction.ExpandQuickSettings -> R.string.gesture_action_expand_quick_settings
    GestureAction.LockScreen -> R.string.gesture_action_lock_screen
    GestureAction.Recents -> R.string.gesture_action_recents
    GestureAction.OpenLauncherSettings -> R.string.gesture_action_open_launcher_settings
    GestureAction.Assistant -> R.string.gesture_action_assistant
    GestureAction.VoiceSearch -> R.string.gesture_action_voice_search
    GestureAction.ToggleDock -> R.string.gesture_action_toggle_dock
    GestureAction.OpenFolder -> R.string.folder_action_open
    GestureAction.GoToDefaultPage -> R.string.gesture_action_default_page
    is GestureAction.LaunchApp, is GestureAction.LaunchShortcut -> null
}

/** Display name of [action]; [defaultLabel] is shown for null ("use default"). */
@Composable
fun gestureActionLabel(action: GestureAction?, apps: List<LauncherApp>?, defaultLabel: String? = null): String {
    val unknown = stringResource(R.string.gesture_action_unknown_app)
    return when (action) {
        null -> defaultLabel ?: stringResource(R.string.gesture_action_use_default)
        is GestureAction.LaunchApp -> stringResource(
            R.string.gesture_action_launch_app,
            apps?.firstOrNull { it.key == action.key }?.let { appName(it) } ?: unknown,
        )
        is GestureAction.LaunchShortcut -> stringResource(
            R.string.gesture_action_launch_shortcut,
            apps?.firstOrNull { it.key.packageName == action.packageName && it.key.userSerial == action.userSerial }?.label ?: unknown,
        )
        else -> stringResource(simpleGestureActionLabel(action) ?: R.string.gesture_action_none)
    }
}

/** Current launchable apps from the app's repository; null while loading or after a failure. */
@Composable
fun collectLauncherApps(): List<LauncherApp>? {
    val deps = LocalHomeDependencies.current
    val flow = remember(deps) {
        deps.appsRepository.apps.map { (it as? AppsState.Loaded)?.apps }.catch { emit(null) }
    }
    return flow.collectAsState(null).value
}

@Composable
private fun appName(app: LauncherApp): String =
    if (app.profileType == ProfileType.WORK) stringResource(R.string.gesture_picker_work_app, app.label) else app.label

internal enum class PickerPage { ACTIONS, APPS, SHORTCUT_APPS, SHORTCUTS }

/** Which picker page is shown; shared by the dialog and the swipe-actions sheet. */
@Stable
internal class GestureActionPickerState(pageState: MutableState<PickerPage>) {
    var page by pageState
    var shortcutApp by mutableStateOf<LauncherApp?>(null)

    /** Steps back one page; false when already on the first page. */
    fun back(): Boolean {
        if (page == PickerPage.ACTIONS) return false
        page = if (page == PickerPage.SHORTCUTS && shortcutApp != null) PickerPage.SHORTCUT_APPS else PickerPage.ACTIONS
        return true
    }

    fun reset() {
        page = PickerPage.ACTIONS
        shortcutApp = null
    }
}

@Composable
internal fun rememberGestureActionPickerState(): GestureActionPickerState {
    val page = rememberSaveable { mutableStateOf(PickerPage.ACTIONS) }
    return remember { GestureActionPickerState(page) }
}

/** Title of the current picker page; [title] names the first page. */
@Composable
internal fun gestureActionPickerTitle(state: GestureActionPickerState, title: String): String = when (state.page) {
    PickerPage.ACTIONS -> title
    PickerPage.APPS, PickerPage.SHORTCUT_APPS -> stringResource(R.string.gesture_picker_choose_app_title)
    PickerPage.SHORTCUTS -> stringResource(R.string.gesture_picker_choose_shortcut_title)
}

/** Sections of the first picker page, in display order. Apps are always offered. */
internal enum class PickerSection(@StringRes val title: Int) {
    LAUNCHER(R.string.gesture_picker_section_launcher),
    APPS(R.string.gesture_picker_section_apps),
    SYSTEM(R.string.gesture_picker_section_system),
    VOICE(R.string.gesture_picker_section_voice),
}

/** One row (or heading) of the first picker page. */
internal sealed interface PickerEntry {
    val key: String

    data object UseDefault : PickerEntry { override val key = "default" }
    data class Heading(val section: PickerSection) : PickerEntry { override val key = "heading|$section" }
    data class Simple(val action: GestureAction) : PickerEntry { override val key = "action|" + GestureAction.encode(action) }
    data object ChooseApp : PickerEntry { override val key = "app" }
    data object ChooseShortcut : PickerEntry { override val key = "shortcut" }

    fun isSelected(current: GestureAction?): Boolean = when (this) {
        UseDefault -> current == null
        is Heading -> false
        is Simple -> action == current
        ChooseApp -> current is GestureAction.LaunchApp
        ChooseShortcut -> current is GestureAction.LaunchShortcut
    }
}

private fun sectionOf(action: GestureAction): PickerSection? = when (action) {
    GestureAction.None -> null
    GestureAction.OpenDrawer, GestureAction.OpenSearch, GestureAction.OpenCards, GestureAction.GoToDefaultPage, GestureAction.ToggleDock,
    GestureAction.OpenLauncherSettings, GestureAction.OpenFolder -> PickerSection.LAUNCHER
    GestureAction.ExpandNotifications, GestureAction.ExpandQuickSettings, GestureAction.Recents,
    GestureAction.LockScreen -> PickerSection.SYSTEM
    GestureAction.Assistant, GestureAction.VoiceSearch -> PickerSection.VOICE
    is GestureAction.LaunchApp, is GestureAction.LaunchShortcut -> PickerSection.APPS
}

/**
 * The first picker page: "Use default" and "Do nothing" on top, then the offered [actions] grouped
 * into sections (in [GestureAction.simpleActions] order), with App…/App shortcut… under Apps.
 */
internal fun pickerEntries(actions: List<GestureAction>, allowDefault: Boolean): List<PickerEntry> = buildList {
    if (allowDefault) add(PickerEntry.UseDefault)
    if (GestureAction.None in actions) add(PickerEntry.Simple(GestureAction.None))
    val order = GestureAction.simpleActions
    for (section in PickerSection.entries) {
        val inSection = actions.filter { sectionOf(it) == section }
            .sortedBy { order.indexOf(it).let { i -> if (i < 0) -1 else i } }
        if (section != PickerSection.APPS && inSection.isEmpty()) continue
        add(PickerEntry.Heading(section))
        if (section == PickerSection.APPS) {
            add(PickerEntry.ChooseApp)
            add(PickerEntry.ChooseShortcut)
        } else {
            inSection.forEach { add(PickerEntry.Simple(it)) }
        }
    }
}

/**
 * Icon shown beside a simple action in pickers. Card tiles use [io.github.aj4abinjacob.chikku.home.cards.cardActionGlyph],
 * which differs on purpose for None, OpenDrawer and the app/shortcut placeholders; keep the two in step.
 */
@DrawableRes
internal fun gestureActionIcon(action: GestureAction): Int = when (action) {
    GestureAction.None -> R.drawable.ic_picker_none
    GestureAction.OpenDrawer -> R.drawable.ic_settings_drawer
    GestureAction.OpenSearch -> R.drawable.ic_search
    GestureAction.OpenCards -> R.drawable.ic_settings_cards
    GestureAction.GoToDefaultPage -> R.drawable.ic_settings_home
    GestureAction.ToggleDock -> R.drawable.ic_settings_dock
    GestureAction.OpenLauncherSettings -> R.drawable.ic_menu_tune
    GestureAction.OpenFolder -> R.drawable.ic_settings_folder
    GestureAction.ExpandNotifications -> R.drawable.ic_action_notifications
    GestureAction.ExpandQuickSettings -> R.drawable.ic_action_quick_settings
    GestureAction.Recents -> R.drawable.ic_action_recents
    GestureAction.LockScreen -> R.drawable.ic_settings_lock
    GestureAction.Assistant -> R.drawable.ic_action_assistant
    GestureAction.VoiceSearch -> R.drawable.ic_action_mic
    is GestureAction.LaunchApp -> R.drawable.ic_picker_app
    is GestureAction.LaunchShortcut -> R.drawable.ic_picker_shortcut
}

/** Actions that only work with Chikku's optional accessibility service; their rows say so. */
private fun needsAccessibility(action: GestureAction) = action == GestureAction.LockScreen || action == GestureAction.Recents

/**
 * The current picker page's list; [onPick] gets the chosen action (null = "use default").
 * [actionsHeader] adds caller-specific choices above the first page's actions, in the same lazy
 * list, so a long set of them never squeezes the rest into a nested scroll. [defaultSummary] says
 * what "Use default" currently does, when the caller knows. The first page opens with the selected
 * row in view.
 */
@Composable
internal fun GestureActionPickerBody(
    state: GestureActionPickerState,
    current: GestureAction?,
    apps: List<LauncherApp>?,
    onPick: (GestureAction?) -> Unit,
    allowDefault: Boolean,
    actions: List<GestureAction>,
    actionsHeader: LazyListScope.() -> Unit = {},
    /** Behind the pinned app search field: the surface this list sits on (dialog or sheet). */
    searchBackground: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    defaultSummary: String? = null,
) {
    when (state.page) {
        PickerPage.ACTIONS -> {
            val entries = remember(actions, allowDefault) { pickerEntries(actions, allowDefault) }
            val listState = rememberLazyListState()
            LaunchedEffect(Unit) {
                val selected = entries.indexOfFirst { it.isSelected(current) }
                if (selected < 0) return@LaunchedEffect
                val layout = snapshotFlow { listState.layoutInfo }.first { it.totalItemsCount > 0 && it.viewportEndOffset > 0 }
                // Caller rows (actionsHeader) come first; they are whatever the list has beyond ours.
                val index = layout.totalItemsCount - entries.size + selected
                val shown = layout.visibleItemsInfo.firstOrNull { it.index == index }
                val fullyShown = shown != null && shown.offset >= layout.viewportStartOffset &&
                    shown.offset + shown.size <= layout.viewportEndOffset
                if (!fullyShown) listState.scrollToItem((index - 1).coerceAtLeast(0))
            }
            LazyColumn(state = listState) {
                actionsHeader()
                items(entries, key = { it.key }) { entry ->
                    val selected = entry.isSelected(current)
                    when (entry) {
                        is PickerEntry.Heading -> GestureActionSectionLabel(stringResource(entry.section.title))
                        PickerEntry.UseDefault -> GestureActionRow(
                            text = stringResource(R.string.gesture_action_use_default),
                            selected = selected,
                            icon = R.drawable.ic_picker_default,
                            supporting = defaultSummary,
                        ) { onPick(null) }
                        is PickerEntry.Simple -> GestureActionRow(
                            text = stringResource(simpleGestureActionLabel(entry.action) ?: R.string.gesture_action_none),
                            selected = selected,
                            icon = gestureActionIcon(entry.action),
                            supporting = if (needsAccessibility(entry.action)) stringResource(R.string.gesture_picker_needs_accessibility) else null,
                        ) { onPick(entry.action) }
                        PickerEntry.ChooseApp -> GestureActionRow(
                            text = stringResource(R.string.gesture_picker_choose_app),
                            selected = selected,
                            icon = R.drawable.ic_picker_app,
                            supporting = if (selected) gestureActionLabel(current, apps) else null,
                            navigates = true,
                        ) { state.page = PickerPage.APPS }
                        PickerEntry.ChooseShortcut -> GestureActionRow(
                            text = stringResource(R.string.gesture_picker_choose_shortcut),
                            selected = selected,
                            icon = R.drawable.ic_picker_shortcut,
                            supporting = if (selected) gestureActionLabel(current, apps) else null,
                            navigates = true,
                        ) { state.page = PickerPage.SHORTCUT_APPS }
                    }
                }
            }
        }
        PickerPage.APPS, PickerPage.SHORTCUT_APPS -> AppList(apps, current, state.page == PickerPage.SHORTCUT_APPS, searchBackground) { app ->
            if (state.page == PickerPage.APPS) {
                onPick(GestureAction.LaunchApp(app.key))
            } else {
                state.shortcutApp = app
                state.page = PickerPage.SHORTCUTS
            }
        }
        PickerPage.SHORTCUTS -> ShortcutList(
            state.shortcutApp?.takeIf { selected -> apps.orEmpty().any { it.key == selected.key } }, current,
        ) { onPick(it) }
    }
}

/** Section heading inside an action picker. */
@Composable
internal fun GestureActionSectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 12.dp, top = 16.dp, bottom = 4.dp).semantics { heading() },
    )
}

/**
 * One choice in an action picker: leading [icon] (a drawable or [leading] content), label, optional
 * [supporting] line, and a check mark plus tint when [selected]. A [navigates] row opens another
 * page (App…, App shortcut…) and ends in a chevron.
 */
@Composable
internal fun GestureActionRow(
    text: String,
    selected: Boolean,
    @DrawableRes icon: Int? = null,
    supporting: String? = null,
    navigates: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    /** Overrides the label's style (merged over bodyLarge), e.g. to show a font option in its font. */
    textStyle: TextStyle? = null,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val content = if (selected) colors.onSecondaryContainer else colors.onSurface
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) colors.secondaryContainer else Color.Transparent)
            .selectable(selected = selected, role = if (navigates) Role.Button else Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null || icon != null) {
            Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                if (leading != null) {
                    leading()
                } else if (icon != null) {
                    Icon(
                        painterResource(icon),
                        contentDescription = null,
                        tint = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(text, style = MaterialTheme.typography.bodyLarge.merge(textStyle), color = content)
            if (supporting != null) {
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
                )
            }
        }
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Icon(painterResource(R.drawable.ic_settings_check), contentDescription = null, tint = colors.primary, modifier = Modifier.size(24.dp))
        }
        if (navigates) {
            Spacer(Modifier.width(4.dp))
            Icon(painterResource(R.drawable.ic_settings_chevron), contentDescription = null, tint = content, modifier = Modifier.size(24.dp).graphicsLayer { if (rtl) scaleX = -1f })
        }
    }
}

/**
 * Choose a simple action, an app, or one of an app's shortcuts, in a [SettingsBottomSheet].
 * [actions] limits the simple actions offered (e.g. a folder may drop
 * [GestureAction.GoToDefaultPage]); with [allowDefault] a "Use default" entry picks null.
 *
 * The App… and App shortcut… pages replace the sheet's content; their header leads with a back
 * arrow, and system back steps back a page before it dismisses the sheet. Picking an action calls
 * [onPick] and slides the sheet away; [onDismiss] follows once it is gone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GestureActionPickerDialog(
    title: String,
    current: GestureAction?,
    onPick: (GestureAction?) -> Unit,
    onDismiss: () -> Unit,
    allowDefault: Boolean = false,
    actions: List<GestureAction> = GestureAction.simpleActions,
    appFilter: (LauncherApp) -> Boolean = { true },
) {
    val state = rememberGestureActionPickerState()
    val apps = collectLauncherApps()?.filter(appFilter)
    var picked by remember { mutableStateOf(false) }

    SettingsBottomSheet(
        title = gestureActionPickerTitle(state, title),
        onDismiss = onDismiss,
        onBack = state.headerBack(),
    ) { close ->
        GestureActionPickerSheetContent {
            GestureActionPickerBody(
                state = state,
                current = current,
                apps = apps,
                onPick = { action ->
                    // One pick per sheet: a second tap while it slides away changes nothing.
                    if (!picked) {
                        picked = true
                        onPick(action)
                        close()
                    }
                },
                allowDefault = allowDefault,
                actions = actions,
                searchBackground = BottomSheetDefaults.ContainerColor,
            )
        }
    }
}

/** The sheet header's back action: steps back a page on sub-pages, none on the first page. */
internal fun GestureActionPickerState.headerBack(): (() -> Unit)? =
    if (page == PickerPage.ACTIONS) null else ({ back() })

/**
 * Sizes a picker page inside a sheet: every page hugs its rows (no gap below the last one) and
 * scrolls only when taller than the sheet's cap, less the keyboard, which it stays above. The app
 * lists keep their unfiltered height while searching (see [keepUnfilteredHeight]), so the search
 * field does not move as results drop out.
 */
@Composable
internal fun ColumnScope.GestureActionPickerSheetContent(body: @Composable () -> Unit) {
    Box(
        Modifier
            .weight(1f, fill = false)
            .fillMaxWidth()
            .imePadding()
            .padding(horizontal = SheetListPadding),
    ) { body() }
}

@Composable
private fun AppList(
    apps: List<LauncherApp>?,
    current: GestureAction?,
    shortcutsOnly: Boolean,
    searchBackground: Color,
    onClick: (LauncherApp) -> Unit,
) {
    val context = LocalContext.current
    val lock = LocalHomeDependencies.current.appLock
    val lockState by lock.state.collectAsState()
    val safeApps = if (shortcutsOnly) apps?.filter { lock.mayRead(it.key.packageName, it.key.userSerial) } else apps
    // Apps without shortcuts would only lead to "This app has no shortcuts"; null = could not tell, list all.
    val withShortcuts by produceState<Set<AppKey>?>(null, safeApps, shortcutsOnly, lockState) {
        value = if (!shortcutsOnly || safeApps == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                LauncherAppsShortcuts(context.applicationContext, AppLockGuard { _, _ -> false }, lock).appsWithShortcuts(safeApps.map { it.key })
            }
        }
    }
    val listed = withShortcuts?.let { keys -> safeApps?.filter { it.key in keys } } ?: safeApps
    AppPickerList(
        apps = listed,
        isSelected = { app ->
            when (current) {
                is GestureAction.LaunchApp -> !shortcutsOnly && current.key == app.key
                is GestureAction.LaunchShortcut -> shortcutsOnly && current.packageName == app.key.packageName &&
                    current.userSerial == app.key.userSerial
                else -> false
            }
        },
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        style = if (shortcutsOnly) AppPickerStyle.NAVIGATION else AppPickerStyle.RADIO,
        fillHeight = false,
        inset = SheetAppListInset,
        searchInset = 0.dp,
        pinnedTitle = stringResource(R.string.gesture_picker_current),
        searchBackground = searchBackground,
        allTitle = stringResource(R.string.gesture_picker_all_apps),
        label = { appName(it) },
        summary = if (shortcutsOnly) {
            { app ->
                stringResource(R.string.gesture_picker_current).takeIf {
                    current is GestureAction.LaunchShortcut && current.packageName == app.key.packageName &&
                        current.userSerial == app.key.userSerial
                }
            }
        } else {
            null
        },
        headerCount = if (withShortcuts != null) 1 else 0,
        header = {
            if (withShortcuts != null) {
                item(key = "shortcuts-hint") { InfoLine(stringResource(R.string.gesture_picker_shortcut_apps_hint)) }
            }
        },
    )
}

@Composable
private fun ShortcutList(app: LauncherApp?, current: GestureAction?, onPick: (GestureAction) -> Unit) {
    ShortcutListPage(app, current, onPick, info = { InfoLine(it) }) { label, _, selected, onClick ->
        GestureActionRow(label, selected = selected, onClick = onClick)
    }
}

@Composable
private fun InfoLine(text: String) {
    // In a SheetListPadding list, like the rows' own 12dp, so the text lines up with their labels.
    Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp))
}

