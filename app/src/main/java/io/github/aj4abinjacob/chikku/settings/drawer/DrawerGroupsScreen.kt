package io.github.aj4abinjacob.chikku.settings.drawer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import io.github.aj4abinjacob.chikku.home.Haptics
import io.github.aj4abinjacob.chikku.settings.components.animateItemWith
import io.github.aj4abinjacob.chikku.theme.LocalMotion
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.AppsState
import io.github.aj4abinjacob.chikku.core.model.DrawerGroup
import io.github.aj4abinjacob.chikku.core.model.DrawerSortMode
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.settings.LocalHomeDependencies
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.settings.previews.DrawerGroupsLivePreview
import io.github.aj4abinjacob.chikku.settings.previews.SyntheticPreviewIdentities
import io.github.aj4abinjacob.chikku.settings.components.AppPickerList
import io.github.aj4abinjacob.chikku.settings.components.AppPickerStyle
import io.github.aj4abinjacob.chikku.settings.components.SettingsChoiceRow
import io.github.aj4abinjacob.chikku.settings.components.settingsPageColor
import io.github.aj4abinjacob.chikku.settings.components.SettingsInfoText
import io.github.aj4abinjacob.chikku.settings.components.SettingsListSegment
import io.github.aj4abinjacob.chikku.settings.components.settingsSegment
import io.github.aj4abinjacob.chikku.settings.components.SettingsNavigationRow
import io.github.aj4abinjacob.chikku.settings.components.SettingsSectionHeader
import io.github.aj4abinjacob.chikku.settings.components.SettingsSwitchRow
import io.github.aj4abinjacob.chikku.settings.components.settingsFastScrollerPadding

/** Moves [id] one place in [ids] by [delta]; returns null when it cannot move. */
internal fun moveGroup(ids: List<String>, id: String, delta: Int): List<String>? {
    val from = ids.indexOf(id)
    val to = from + delta
    if (from < 0 || to !in ids.indices) return null
    return ids.toMutableList().apply { add(to, removeAt(from)) }
}

/** Group list: enable tabs, create, reorder, open one to rename/delete/pick apps. */
@Composable
fun DrawerGroupsScreen(
    settings: LauncherSettings,
    update: ((LauncherSettings) -> LauncherSettings) -> Unit,
    onOpenGroup: (groupId: String) -> Unit,
) {
    val deps = LocalHomeDependencies.current
    val context = LocalContext.current
    val groups by rememberDrawerGroups(deps)
    var creating by rememberSaveable { mutableStateOf(false) }
    val motion = LocalMotion.current
    val view = LocalView.current

    val list = groups.orEmpty().sortedBy { it.rank }
    val sampleIdentities = remember { SyntheticPreviewIdentities<String>() }
    val sampleOrder = sampleIdentities.order(list.map { it.id })
    Column(Modifier.fillMaxSize()) {
        DrawerGroupsLivePreview(settings, sampleOrder)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                SettingsListSegment(0, 2) {
                    SettingsSwitchRow(
                        title = stringResource(R.string.drawer_settings_groups_enabled),
                        checked = settings.drawer.groupsEnabled,
                        onCheckedChange = { on -> update { it.copy(drawer = it.drawer.copy(groupsEnabled = on)) } },
                    )
                }
            }
            item {
                SettingsListSegment(1, 2) {
                    SettingsNavigationRow(title = stringResource(R.string.drawer_groups_add), onClick = { creating = true })
                }
            }
            item { SettingsInfoText(stringResource(R.string.drawer_groups_explanation)) }
            item { SettingsSectionHeader(stringResource(R.string.drawer_settings_groups)) }
            if (groups != null && list.isEmpty()) {
                item { SettingsInfoText(stringResource(R.string.drawer_groups_none)) }
            }
            itemsIndexed(list, key = { _, group -> group.id }) { index, group ->
                val ids = list.map { it.id }
                GroupRow(
                    modifier = animateItemWith(motion).settingsSegment(index, list.size),
                    group = group,
                    canMoveUp = index > 0,
                    canMoveDown = index < list.lastIndex,
                    onOpen = { onOpenGroup(group.id) },
                    onMove = { delta ->
                        moveGroup(ids, group.id, delta)?.let { order ->
                            Haptics.tick(view)
                            deps.write(context) { deps.drawerGroupRepository.reorderGroups(order) }
                        }
                    },
                )
            }
        }
    }

    if (creating) {
        GroupNameDialog(
            title = stringResource(R.string.drawer_groups_add),
            initial = "",
            confirmLabel = stringResource(R.string.drawer_groups_create),
            onDismiss = { creating = false },
            onConfirm = { name ->
                creating = false
                deps.write(context) { deps.drawerGroupRepository.createGroup(name) != null }
            },
        )
    }
}

@Composable
private fun GroupRow(
    modifier: Modifier,
    group: DrawerGroup,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onOpen: () -> Unit,
    onMove: (Int) -> Unit,
) {
    Row(modifier.fillMaxWidth().heightIn(min = 56.dp).padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(
            Modifier
                .weight(1f)
                .heightIn(min = 56.dp)
                .clickable(role = Role.Button, onClick = onOpen)
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Text(group.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                pluralStringResource(R.plurals.drawer_groups_members, group.members.size, group.members.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val up = stringResource(R.string.drawer_groups_move_up, group.name)
        val down = stringResource(R.string.drawer_groups_move_down, group.name)
        TextButton(onClick = { onMove(-1) }, enabled = canMoveUp, modifier = Modifier.semantics { contentDescription = up }) {
            Text("↑")
        }
        TextButton(onClick = { onMove(1) }, enabled = canMoveDown, modifier = Modifier.semantics { contentDescription = down }) {
            Text("↓")
        }
    }
}

/** One group: rename, order, delete and the app checklist. Leaves via [onBack] once deleted. */
@Composable
fun DrawerGroupMembersScreen(groupId: String, settings: LauncherSettings, onBack: () -> Unit) {
    val deps = LocalHomeDependencies.current
    val context = LocalContext.current
    val groups by rememberDrawerGroups(deps)
    val appsState by rememberAppsState(deps)
    var renaming by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }

    val loaded = groups ?: return
    val group = loaded.firstOrNull { it.id == groupId }
    if (group == null) {
        LaunchedEffect(groupId) { onBack() }
        return
    }
    val members = group.members.toSet()
    val groupIdentities = remember { SyntheticPreviewIdentities<String>() }
    val memberIdentities = remember(groupId) { SyntheticPreviewIdentities<AppKey>() }
    val groupOrder = groupIdentities.order(loaded.sortedBy { it.rank }.map { it.id })
    val memberOrder = memberIdentities.order(group.members)
    Column(Modifier.fillMaxSize()) {
        DrawerGroupsLivePreview(settings, groupOrder, memberOrder, group.sortMode,
            selectedGroup = groupOrder[loaded.sortedBy { it.rank }.indexOfFirst { it.id == groupId }])
        AppPickerList(
            apps = when (val state = appsState) {
                is AppsState.Loaded -> state.apps
                is AppsState.Failed -> emptyList()
                AppsState.Loading -> null
            },
            emptyText = stringResource(R.string.drawer_apps_unavailable).takeIf { appsState is AppsState.Failed },
            isSelected = { it.key in members },
            onClick = { app ->
                deps.write(context) {
                    if (app.key in members) deps.drawerGroupRepository.removeMember(group.id, app.key)
                    else deps.drawerGroupRepository.addMember(group.id, app.key)
                }
            },
            modifier = Modifier.weight(1f),
            style = AppPickerStyle.CHECKBOX,
            // Current members lead, so a group's contents can be checked without scrolling every app.
            pinnedTitle = stringResource(R.string.drawer_groups_in_group),
            allTitle = stringResource(R.string.drawer_groups_all_apps),
            label = { appTitle(it) },
            searchBackground = settingsPageColor(),
            contentPadding = settingsFastScrollerPadding(bottom = 24.dp),
            inset = 12.dp,
            headerCount = MEMBER_ROWS_START,
            header = {
                item { SettingsSectionHeader(group.name) }
                item {
                    SettingsListSegment(0, 3) {
                        SettingsNavigationRow(title = stringResource(R.string.drawer_groups_rename), summary = group.name, onClick = { renaming = true })
                    }
                }
                item {
                    SettingsListSegment(1, 3) {
                    SettingsChoiceRow(
                        title = stringResource(R.string.drawer_groups_sort),
                        options = DrawerSortMode.entries.toList(),
                        selected = group.sortMode,
                        optionLabel = {
                            stringResource(
                                when (it) {
                                    DrawerSortMode.ALPHABETICAL -> R.string.drawer_groups_sort_alphabetical
                                    DrawerSortMode.MANUAL -> R.string.drawer_groups_sort_manual
                                },
                            )
                        },
                        onSelect = { mode -> deps.write(context) { deps.drawerGroupRepository.setSortMode(group.id, mode) } },
                    )
                    }
                }
                item {
                    SettingsListSegment(2, 3) {
                        SettingsNavigationRow(title = stringResource(R.string.drawer_groups_delete), onClick = { deleting = true })
                    }
                }
                item { SettingsSectionHeader(stringResource(R.string.drawer_groups_edit_apps)) }
                item { SettingsInfoText(stringResource(R.string.drawer_groups_members_hint)) }
            },
        )
    }

    if (renaming) {
        GroupNameDialog(
            title = stringResource(R.string.drawer_groups_rename),
            initial = group.name,
            confirmLabel = stringResource(R.string.drawer_groups_save),
            onDismiss = { renaming = false },
            onConfirm = { name ->
                renaming = false
                deps.write(context) { deps.drawerGroupRepository.renameGroup(group.id, name) }
            },
        )
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(stringResource(R.string.drawer_groups_delete_title)) },
            text = { Text(stringResource(R.string.drawer_groups_delete_message, group.name)) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = false
                    deps.write(context) { deps.drawerGroupRepository.deleteGroup(group.id) }
                }) { Text(stringResource(R.string.drawer_groups_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.settings_cancel)) }
            },
        )
    }
}

@Composable
private fun GroupNameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial) }
    val trimmed = name.trim()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(MAX_GROUP_NAME) },
                label = { Text(stringResource(R.string.drawer_groups_name)) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(trimmed) }, enabled = trimmed.isNotEmpty()) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) } },
    )
}

private const val MAX_GROUP_NAME = 40

/** Lazy items the members list's header adds: group header, rename, sort, delete, apps header, hint. */
private const val MEMBER_ROWS_START = 6
