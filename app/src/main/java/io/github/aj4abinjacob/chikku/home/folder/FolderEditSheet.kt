package io.github.aj4abinjacob.chikku.home.folder

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.GestureAction
import io.github.aj4abinjacob.chikku.core.model.ItemActions
import io.github.aj4abinjacob.chikku.core.model.settings.FolderCover
import io.github.aj4abinjacob.chikku.core.model.settings.FolderCoverMode
import io.github.aj4abinjacob.chikku.core.model.settings.FolderCoverShortcut
import io.github.aj4abinjacob.chikku.platform.ShortcutIconLoader
import io.github.aj4abinjacob.chikku.settings.LocalHomeDependencies
import io.github.aj4abinjacob.chikku.settings.components.GestureActionPickerBody
import io.github.aj4abinjacob.chikku.settings.components.SheetListPadding
import io.github.aj4abinjacob.chikku.settings.components.SheetPadding
import io.github.aj4abinjacob.chikku.settings.components.GestureActionRow
import io.github.aj4abinjacob.chikku.settings.components.AppIconImage
import io.github.aj4abinjacob.chikku.settings.components.GestureActionPickerPage
import io.github.aj4abinjacob.chikku.settings.components.GestureActionSectionLabel
import io.github.aj4abinjacob.chikku.settings.components.GestureValueRow
import io.github.aj4abinjacob.chikku.settings.components.SectionLabel
import io.github.aj4abinjacob.chikku.settings.components.collectLauncherApps
import io.github.aj4abinjacob.chikku.settings.previews.FolderSwipePracticePreview
import io.github.aj4abinjacob.chikku.settings.components.gestureActionIcon
import io.github.aj4abinjacob.chikku.settings.components.gestureActionLabel
import io.github.aj4abinjacob.chikku.settings.components.rememberGestureActionPickerState
import io.github.aj4abinjacob.chikku.settings.previews.FolderSwipePracticePreview
import kotlinx.coroutines.launch

/** An app inside the folder that can be its cover or a gesture target. Labels are shown, never logged. */
/** An available folder entry the editor can use as cover or gesture target; labels are shown, never logged. */
sealed interface FolderEditEntry {
    val label: String

    /** The gesture that opens this entry. */
    val launch: GestureAction

    /** The cover that shows exactly this entry. */
    val asCover: FolderCover
}

data class FolderEditApp(val key: AppKey, override val label: String, val isWork: Boolean) : FolderEditEntry {
    override val launch get() = GestureAction.LaunchApp(key)
    override val asCover get() = FolderCover(FolderCoverMode.SELECTED_APP, app = key)
}

/** A pinned shortcut in the folder. */
data class FolderEditShortcut(
    override val launch: GestureAction.LaunchShortcut,
    override val label: String,
    val iconPack: String? = null,
    val iconName: String? = null,
) : FolderEditEntry {
    override val asCover get() = FolderCover(
        FolderCoverMode.SELECTED_APP,
        shortcut = FolderCoverShortcut(launch.packageName, launch.shortcutId, launch.userSerial),
    )
}

/** What the folder editor shows and how it saves; built by [FolderController]. */
class FolderEditRequest(
    val title: String,
    /** Available apps and pinned shortcuts in folder order. */
    val entries: List<FolderEditEntry>,
    val actions: ItemActions,
    val cover: FolderCover,
    val gesturesEnabled: Boolean,
    /** Staged result; returns false when it could not be saved (e.g. layout locked), keeping the sheet open. */
    val onSave: (title: String, actions: ItemActions, cover: FolderCover) -> Boolean,
)

private enum class Gesture { TAP, SWIPE_UP, SWIPE_DOWN }

/**
 * Edits one folder in a bottom sheet that follows Chikku settings: the settings theme and accent,
 * tonal rows with value pills, and picker pages that slide in instead of stacked dialogs. Edits
 * are staged and saved when the sheet closes, whether by Done, back, swiping down or tapping
 * outside; only Discard drops them, so leaving the sheet never silently loses work. The cover is
 * chosen visually, and one switch makes a tap open the cover (the "swipe to open" folder), so the
 * icon and its tap never disagree.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderEditSheet(request: FolderEditRequest, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(request.title) }
    var actions by remember { mutableStateOf(request.actions) }
    var cover by remember { mutableStateOf(request.cover) }
    var picking by remember { mutableStateOf<Gesture?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val picker = rememberGestureActionPickerState()
    val launcherApps = collectLauncherApps()
    val coverEntry = coverEntry(request.entries, cover)
    val focus = LocalFocusManager.current
    fun hasChanges() = name.trim() != request.title || actions != request.actions || cover != request.cover
    val changed = hasChanges()
    // Set once the outcome (saved or discarded) is decided, so a closing animation cannot save twice.
    var finished by remember { mutableStateOf(false) }

    /** Writes staged edits; false when they could not be saved (e.g. layout locked). */
    fun commit(): Boolean = !hasChanges() || request.onSave(name.trim(), actions, cover)

    // HOME and other host events remove the sheet without a dismiss request; keep the edits then too.
    val latestCommit by rememberUpdatedState(::commit)
    DisposableEffect(Unit) {
        onDispose {
            if (!finished) {
                finished = true
                latestCommit()
            }
        }
    }

    fun close() {
        finished = true
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    fun done() {
        if (commit()) close()
    }

    fun closePicker() {
        picking = null
        picker.reset()
    }

    /** A new cover keeps a linked tap ("tap opens the cover app") pointing at the cover. */
    fun changeCover(next: FolderCover) {
        val linked = coverEntry != null && actions.tap == coverEntry.launch
        cover = next
        if (linked) actions = actions.copy(tap = coverEntry(request.entries, next)?.launch)
    }

    // Back, swipe-down and scrim taps keep the edits, like the rest of Chikku's settings.
    ModalBottomSheet(
        onDismissRequest = {
            if (!finished) {
                finished = true
                commit()
            }
            onDismiss()
        },
        sheetState = sheetState,
    ) {
        BackHandler(enabled = picking != null) { if (!picker.back()) closePicker() }
        AnimatedContent(
            targetState = picking,
            transitionSpec = {
                val forward = targetState != null
                (slideInHorizontally { if (forward) it / 4 else -it / 4 } + fadeIn()) togetherWith
                    (slideOutHorizontally { if (forward) -it / 4 else it / 4 } + fadeOut()) using
                    SizeTransform(clip = false)
            },
            label = "folderEditPage",
        ) { gesture ->
            if (gesture == null) {
                Column(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier
                            .weight(1f, fill = false)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
                    ) {
                        Header(coverEntry, name.ifBlank { stringResource(R.string.home_folder_unnamed) })
                        Spacer(Modifier.height(20.dp))
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text(stringResource(R.string.folder_name_hint)) },
                            placeholder = { Text(stringResource(R.string.home_folder_unnamed)) },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        SectionLabel(stringResource(R.string.folder_edit_refresh_cover))
                        CoverPicker(request.entries, cover, ::changeCover)
                        // The row keeps the last cover app while it animates out, so it collapses
                        // with its content instead of emptying first and then jumping.
                        var rowCover by remember { mutableStateOf(coverEntry) }
                        if (coverEntry != null && rowCover != coverEntry) rowCover = coverEntry
                        AnimatedVisibility(visible = coverEntry != null) {
                            rowCover?.let { shown ->
                                val linked = actions.tap == shown.launch
                                LinkTapRow(
                                    appName = displayName(shown),
                                    checked = linked,
                                    onCheckedChange = { on ->
                                        actions = actions.copy(tap = if (on) shown.launch else null)
                                    },
                                )
                            }
                        }

                        SectionLabel(stringResource(R.string.folder_edit_refresh_actions))
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            GestureValueRow(
                                icon = R.drawable.ic_settings_gestures,
                                label = stringResource(R.string.folder_edit_tap),
                                value = actionLabel(actions.tap, true, request.entries, launcherApps),
                                isDefault = actions.tap == null,
                                shape = RoundedCornerShape(OuterCorner, OuterCorner, InnerCorner, InnerCorner),
                                onClick = { picking = Gesture.TAP },
                                maxPillWidth = 200.dp,
                            )
                            GestureValueRow(
                                icon = R.drawable.ic_swipe_up,
                                label = stringResource(R.string.folder_edit_swipe_up),
                                value = actionLabel(actions.swipeUp, false, request.entries, launcherApps),
                                isDefault = actions.swipeUp == null,
                                shape = RoundedCornerShape(InnerCorner),
                                onClick = { picking = Gesture.SWIPE_UP },
                                maxPillWidth = 200.dp,
                            )
                            GestureValueRow(
                                icon = R.drawable.ic_swipe_down,
                                label = stringResource(R.string.folder_edit_swipe_down),
                                value = actionLabel(actions.swipeDown, false, request.entries, launcherApps),
                                isDefault = actions.swipeDown == null,
                                shape = RoundedCornerShape(InnerCorner, InnerCorner, OuterCorner, OuterCorner),
                                onClick = { picking = Gesture.SWIPE_DOWN },
                                maxPillWidth = 200.dp,
                            )
                        }
                        FolderSwipePracticePreview(actions, request.gesturesEnabled, Modifier.padding(top = 12.dp))
                        // The link row already explains swipe-to-open; repeat it only when that row is hidden.
                        if (!request.gesturesEnabled) {
                            Hint(stringResource(R.string.folder_edit_refresh_gestures_off))
                        } else if (coverEntry == null) {
                            Hint(stringResource(R.string.folder_edit_swipe_hint))
                        }
                    }
                    // Pinned below the scrolling content so the way out is always in view.
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(if (changed) R.string.folder_edit_saves_on_close else R.string.folder_edit_no_changes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                        )
                        if (changed) {
                            TextButton(onClick = ::close) { Text(stringResource(R.string.folder_edit_discard)) }
                            Spacer(Modifier.width(8.dp))
                        }
                        Button(onClick = ::done) { Text(stringResource(R.string.folder_edit_done)) }
                    }
                }
            } else {
                val title = stringResource(
                    when (gesture) {
                        Gesture.TAP -> R.string.folder_edit_tap
                        Gesture.SWIPE_UP -> R.string.folder_edit_swipe_up
                        Gesture.SWIPE_DOWN -> R.string.folder_edit_swipe_down
                    },
                )
                val current = when (gesture) {
                    Gesture.TAP -> actions.tap
                    Gesture.SWIPE_UP -> actions.swipeUp
                    Gesture.SWIPE_DOWN -> actions.swipeDown
                }
                fun pick(action: GestureAction?) {
                    actions = when (gesture) {
                        Gesture.TAP -> actions.copy(tap = action)
                        Gesture.SWIPE_UP -> actions.copy(swipeUp = action)
                        Gesture.SWIPE_DOWN -> actions.copy(swipeDown = action)
                    }
                    closePicker()
                }
                // One lazy list: the folder's own entries come first, and however many there are,
                // the general actions and the app list scroll with them instead of in a nested box.
                GestureActionPickerPage(picker, title, ::closePicker, Modifier.heightIn(max = PickerMaxHeight)) {
                    GestureActionPickerBody(
                        state = picker,
                        current = current,
                        apps = launcherApps,
                        onPick = ::pick,
                        allowDefault = true,
                        actions = GestureAction.simpleActions,
                        searchBackground = BottomSheetDefaults.ContainerColor,
                        actionsHeader = {
                            item(key = "open-folder") {
                                GestureActionRow(
                                    stringResource(R.string.folder_action_open),
                                    selected = current == GestureAction.OpenFolder,
                                    icon = gestureActionIcon(GestureAction.OpenFolder),
                                ) { pick(GestureAction.OpenFolder) }
                            }
                            if (request.entries.isNotEmpty()) {
                                item(key = "in-folder") { GestureActionSectionLabel(stringResource(R.string.folder_picker_in_folder)) }
                                items(request.entries, key = { "entry|" + it.asCover }) { entry ->
                                    GestureActionRow(
                                        text = stringResource(R.string.folder_gesture_launch_app, displayName(entry)),
                                        selected = current == entry.launch,
                                        leading = { EntryIcon(entry, 28.dp) },
                                    ) { pick(entry.launch) }
                                }
                            }
                            item(key = "more") { GestureActionSectionLabel(stringResource(R.string.folder_picker_more)) }
                        },
                    )
                }
            }
        }
    }
}

private fun coverEntry(entries: List<FolderEditEntry>, cover: FolderCover): FolderEditEntry? = when (cover.mode) {
    FolderCoverMode.PREVIEW -> null
    FolderCoverMode.FIRST_APP -> entries.firstOrNull()
    FolderCoverMode.LAST_APP -> entries.lastOrNull()
    FolderCoverMode.SELECTED_APP -> entries.firstOrNull { it.asCover == cover }
}

@Composable
private fun displayName(entry: FolderEditEntry): String =
    if (entry is FolderEditApp && entry.isWork) stringResource(R.string.folder_cover_work_app, entry.label) else entry.label

/** Names an action; folder entries by name, so "first app" never hides which app it is. */
@Composable
private fun actionLabel(
    action: GestureAction?,
    isTap: Boolean,
    entries: List<FolderEditEntry>,
    apps: List<io.github.aj4abinjacob.chikku.core.model.LauncherApp>?,
): String {
    if (action == null) return stringResource(if (isTap) R.string.folder_gesture_default_tap else R.string.folder_gesture_default_swipe)
    entries.firstOrNull { it.launch == action }?.let { return stringResource(R.string.folder_gesture_launch_app, displayName(it)) }
    return gestureActionLabel(action, apps)
}

@Composable
private fun Header(cover: FolderEditEntry?, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // An app cover is shown as is (it carries its own tile); the folder glyph gets a tonal tile.
        Box(
            Modifier.size(HeaderIconSize).clip(RoundedCornerShape(16.dp))
                .then(if (cover == null) Modifier.background(MaterialTheme.colorScheme.secondaryContainer) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            if (cover != null) {
                EntryIcon(cover, HeaderIconSize)
            } else {
                Icon(
                    painterResource(R.drawable.ic_settings_folder),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(R.string.folder_edit_sheet_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Hint(text: String) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Icon(
            painterResource(R.drawable.ic_settings_info),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 1.dp, end = 10.dp).size(16.dp),
        )
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Dynamic covers as chips, then the folder's apps and shortcuts as icons to pick one directly. */
@Composable
private fun CoverPicker(entries: List<FolderEditEntry>, cover: FolderCover, onChange: (FolderCover) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            FolderCoverMode.PREVIEW to R.string.folder_cover_preview,
            FolderCoverMode.FIRST_APP to R.string.folder_cover_first_short,
            FolderCoverMode.LAST_APP to R.string.folder_cover_last_short,
        ).forEach { (mode, label) ->
            FilterChip(
                selected = cover.mode == mode,
                onClick = { onChange(FolderCover(mode)) },
                label = { Text(stringResource(label)) },
            )
        }
    }
    if (entries.isEmpty()) return
    Text(
        stringResource(R.string.folder_cover_pick_app),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
    )
    // Opens scrolled to the chosen cover, so it is visible even in a folder of dozens of apps.
    val rowState = rememberLazyListState(initialFirstVisibleItemIndex = entries.indexOfFirst { it.asCover == cover }.coerceAtLeast(0))
    LazyRow(state = rowState, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        items(entries, key = { it.asCover.toString() }) { entry ->
            val selected = cover == entry.asCover
            val description = stringResource(R.string.folder_cover_app_description, displayName(entry))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(CoverTileWidth)
                    .clip(RoundedCornerShape(16.dp))
                    .selectable(selected = selected, role = Role.RadioButton) {
                        onChange(entry.asCover)
                    }
                    .semantics { contentDescription = description }
                    .padding(vertical = 8.dp),
            ) {
                Box(
                    Modifier
                        .size(CoverIconSize + 8.dp)
                        .border(
                            BorderStroke(2.dp, if (selected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent),
                            CircleShape,
                        )
                        .padding(4.dp),
                    contentAlignment = Alignment.Center,
                ) { EntryIcon(entry, CoverIconSize) }
                Spacer(Modifier.height(6.dp))
                Text(
                    displayName(entry),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** "Tap opens <cover app>": links the tap to the cover so the icon launches what it shows. */
@Composable
private fun LinkTapRow(appName: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(OuterCorner))
            .background(colors.surfaceContainerHigh)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.folder_tap_opens_cover, appName), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.folder_tap_opens_cover_summary),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = null,
            thumbContent = if (checked) {
                {
                    // The default check color (onPrimaryContainer) can equal the thumb (onPrimary) with a custom accent.
                    Icon(painterResource(R.drawable.ic_settings_check), null, Modifier.size(SwitchDefaults.IconSize),
                        tint = MaterialTheme.colorScheme.primary)
                }
            } else {
                null
            },
        )
    }
}

@Composable
private fun EntryIcon(entry: FolderEditEntry, size: Dp) = when (entry) {
    is FolderEditApp -> AppIcon(entry.key, size)
    is FolderEditShortcut -> ShortcutIcon(entry, size)
}

@Composable
private fun AppIcon(key: AppKey, size: Dp) {
    AppIconImage(key, size, LocalHomeDependencies.current.iconProvider)
}

@Composable
private fun ShortcutIcon(shortcut: FolderEditShortcut, size: Dp) {
    val context = LocalContext.current
    val deps = LocalHomeDependencies.current
    val lock = deps.appLock
    val packs = deps.iconPackRepository
    val loader = remember(context, lock, packs) { ShortcutIconLoader(context, lock, sharedPacks = packs) }
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val bitmap by produceState<ImageBitmap?>(initialValue = null, shortcut, sizePx) {
        val a = shortcut.launch
        value = loader.load(a.packageName, a.shortcutId, a.userSerial, shortcut.iconPack, shortcut.iconName)
            ?.toBitmap(sizePx, sizePx)?.asImageBitmap()
    }
    Box(Modifier.size(size)) {
        bitmap?.let { Image(it, contentDescription = null, modifier = Modifier.size(size)) }
    }
}

private val HeaderIconSize = 56.dp
private val CoverIconSize = 48.dp
private val CoverTileWidth = 76.dp
private val PickerMaxHeight = 480.dp
private val OuterCorner = 24.dp
private val InnerCorner = 6.dp
