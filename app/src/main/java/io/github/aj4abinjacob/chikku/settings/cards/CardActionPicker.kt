package io.github.aj4abinjacob.chikku.settings.cards

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.GestureAction
import io.github.aj4abinjacob.chikku.core.model.LauncherApp
import io.github.aj4abinjacob.chikku.core.model.ProfileType
import io.github.aj4abinjacob.chikku.settings.LocalHomeDependencies
import io.github.aj4abinjacob.chikku.settings.components.SettingsFastScroller
import io.github.aj4abinjacob.chikku.settings.components.ShortcutListPage
import io.github.aj4abinjacob.chikku.settings.components.listKey
import io.github.aj4abinjacob.chikku.settings.components.settingsFastScrollerPadding
import io.github.aj4abinjacob.chikku.settings.components.simpleGestureActionLabel

private enum class PickerPage { MAIN, SHORTCUT_APPS, SHORTCUTS }

/**
 * Bottom sheet for choosing what a card button does, laid out like an app drawer: launcher actions
 * as icon tiles, then every allowed app with its icon and a search box, plus a page for app
 * shortcuts. [actions] are the launcher actions offered (empty for the featured item). The search
 * text stays on this screen and is never logged.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CardActionPickerSheet(
    title: String,
    current: GestureAction?,
    apps: List<LauncherApp>?,
    actions: List<GestureAction>,
    onPick: (GestureAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val lock = LocalHomeDependencies.current.appLock
    val lockState by lock.state.collectAsState()
    val readableApps = remember(apps, lockState) { apps?.filter { lock.mayRead(it.key.packageName, it.key.userSerial) } }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var page by rememberSaveable { mutableStateOf(PickerPage.MAIN) }
    var shortcutApp by remember { mutableStateOf<LauncherApp?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    val maxHeight = with(LocalDensity.current) { (LocalWindowInfo.current.containerSize.height * 0.8f).toDp() }
    val colors = MaterialTheme.colorScheme

    val actionLabels = actions.associateWith { stringResource(simpleGestureActionLabel(it) ?: R.string.gesture_action_none) }

    fun pick(action: GestureAction) {
        onPick(action)
        onDismiss()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        BackHandler(enabled = page != PickerPage.MAIN) {
            page = if (page == PickerPage.SHORTCUTS) PickerPage.SHORTCUT_APPS else PickerPage.MAIN
        }
        Column(Modifier.fillMaxWidth().heightIn(max = maxHeight)) {
            Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                if (page != PickerPage.MAIN) {
                    IconButton(onClick = { page = if (page == PickerPage.SHORTCUTS) PickerPage.SHORTCUT_APPS else PickerPage.MAIN }) {
                        Icon(painterResource(R.drawable.ic_settings_back), stringResource(R.string.gesture_picker_back))
                    }
                } else Spacer(Modifier.width(16.dp))
                Text(
                    when (page) {
                        PickerPage.MAIN -> title
                        PickerPage.SHORTCUT_APPS -> stringResource(R.string.gesture_picker_choose_app_title)
                        PickerPage.SHORTCUTS -> shortcutApp?.let { appName(it) }.orEmpty()
                    },
                    style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            if (page != PickerPage.SHORTCUTS) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it.take(80) },
                    // Name exactly what the field filters: actions only appear on the first page, and only for some callers.
                    placeholder = {
                        Text(stringResource(if (page == PickerPage.MAIN && actions.isNotEmpty()) R.string.cards_picker_search else R.string.cards_picker_search_apps))
                    },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_search), null) },
                    singleLine = true, shape = CircleShape,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            val filtered = (if (page == PickerPage.SHORTCUT_APPS) readableApps else apps)?.filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }
            when (page) {
                PickerPage.MAIN -> Box {
                    val gridState = rememberLazyGridState()
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 80.dp),
                        state = gridState,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = settingsFastScrollerPadding(bottom = 24.dp, otherSide = 8.dp),
                    ) {
                        val launcher = actions.filter {
                            query.isBlank() || actionLabels.getValue(it).contains(query.trim(), ignoreCase = true)
                        }
                        if (launcher.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) { GridHeader(stringResource(R.string.cards_picker_launcher)) }
                            items(launcher, key = { "simple:" + GestureAction.encode(it) }) { action ->
                                Tile(actionLabels.getValue(action), action == current, { pick(action) }) { CardActionIcon(action, 48.dp) }
                            }
                        }
                        // Stays while typing: the query carries over, so typing an app's name leads to its shortcuts.
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp).clip(RoundedCornerShape(20.dp))
                                    .background(colors.surfaceContainerHigh)
                                    .clickable(role = Role.Button) { page = PickerPage.SHORTCUT_APPS }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(painterResource(R.drawable.ic_menu_more), null, tint = colors.primary)
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(stringResource(R.string.cards_picker_shortcuts), style = MaterialTheme.typography.titleSmall)
                                    Text(stringResource(R.string.cards_picker_shortcuts_hint), style = MaterialTheme.typography.bodySmall,
                                        color = colors.onSurfaceVariant)
                                }
                                Icon(painterResource(R.drawable.ic_settings_chevron), null, tint = colors.onSurfaceVariant)
                            }
                        }
                        item(span = { GridItemSpan(maxLineSpan) }) { GridHeader(stringResource(R.string.cards_picker_apps)) }
                        when {
                            filtered == null -> item(span = { GridItemSpan(maxLineSpan) }) { Info(stringResource(R.string.gesture_picker_loading)) }
                            filtered.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) { Info(stringResource(R.string.gesture_picker_no_apps)) }
                            else -> items(filtered, key = { it.key.listKey() }) { app ->
                                val action = GestureAction.LaunchApp(app.key)
                                Tile(appName(app), action == current, { pick(action) }, maxLines = 1) { CardActionIcon(action, 48.dp) }
                            }
                        }
                    }
                    SettingsFastScroller(gridState)
                }
                PickerPage.SHORTCUT_APPS -> Box {
                    val listState = rememberLazyListState()
                    LazyColumn(state = listState, contentPadding = settingsFastScrollerPadding(bottom = 24.dp)) {
                        when {
                            filtered == null -> item { Info(stringResource(R.string.gesture_picker_loading)) }
                            filtered.isEmpty() -> item { Info(stringResource(R.string.gesture_picker_no_apps)) }
                            else -> items(filtered, key = { it.key.listKey() }) { app ->
                                ListRow(appName(app), selected = false, onClick = { shortcutApp = app; page = PickerPage.SHORTCUTS }) {
                                    CardActionIcon(GestureAction.LaunchApp(app.key), 40.dp)
                                }
                            }
                        }
                    }
                    SettingsFastScroller(listState)
                }
                PickerPage.SHORTCUTS -> ShortcutPage(shortcutApp, current, ::pick)
            }
        }
    }
}

@Composable
private fun ShortcutPage(app: LauncherApp?, current: GestureAction?, onPick: (GestureAction) -> Unit) {
    ShortcutListPage(
        app, current, onPick,
        info = { Info(it) },
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
    ) { label, action, selected, onClick ->
        ListRow(label, selected = selected, onClick = onClick) { CardActionIcon(action, 40.dp) }
    }
}

/** An icon over a two-line label; the current choice gets a ring. */
@Composable
private fun Tile(label: String, selected: Boolean, onClick: () -> Unit, maxLines: Int = 2, icon: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.padding(4.dp).clip(RoundedCornerShape(16.dp))
            .then(if (selected) Modifier.background(colors.secondaryContainer) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(if (selected) Modifier.border(2.dp, colors.primary, CircleShape).padding(3.dp) else Modifier.padding(5.dp)) { icon() }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = maxLines,
            overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ListRow(label: String, selected: Boolean, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onClick)
            .semantics { this.selected = selected }
            .then(if (selected) Modifier.background(MaterialTheme.colorScheme.secondaryContainer) else Modifier)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun GridHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp))
}

@Composable
private fun Info(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(24.dp))
}

@Composable
private fun appName(app: LauncherApp): String =
    if (app.profileType == ProfileType.WORK) stringResource(R.string.gesture_picker_work_app, app.label) else app.label
