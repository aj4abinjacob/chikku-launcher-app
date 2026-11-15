package io.github.aj4abinjacob.chikku.settings.components

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.Workspace
import io.github.aj4abinjacob.chikku.core.model.WorkspaceState
import io.github.aj4abinjacob.chikku.core.model.layout.HiddenLayoutRecovery
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.settings.LocalHomeDependencies

internal fun newlyHiddenItems(workspace: Workspace, current: LauncherSettings, proposed: LauncherSettings): Int {
    val before = HiddenLayoutRecovery.hidden(workspace.copy(desktopGrid = current.desktopGrid, dockGrid = current.dockStorageGrid)).map { it.id }.toSet()
    return HiddenLayoutRecovery.hidden(workspace.copy(desktopGrid = proposed.desktopGrid, dockGrid = proposed.dockStorageGrid))
        .count { it.id !in before }
}

internal data class LayoutChangeControl(val ready: Boolean, val request: ((LauncherSettings) -> LauncherSettings) -> Unit)

/** Confirm before a grid change hides placements; settings writes retain their original positions. */
@Composable
internal fun layoutChangeControl(settings: LauncherSettings,
    update: ((LauncherSettings) -> LauncherSettings) -> Unit): LayoutChangeControl {
    val deps = LocalHomeDependencies.current
    val state by remember(deps) { deps.workspaceRepository.workspace }.collectAsState(WorkspaceState.Loading)
    val workspace = (state as? WorkspaceState.Ready)?.workspace
    var pending by remember { mutableStateOf<((LauncherSettings) -> LauncherSettings)?>(null) }
    val proposed = pending?.invoke(settings)?.coerced()
    val count = if (workspace != null && proposed != null) newlyHiddenItems(workspace, settings, proposed) else 0
    if (pending != null) AlertDialog(
        onDismissRequest = { pending = null },
        title = { Text(stringResource(R.string.settings_grid_change_title)) },
        text = { Text(pluralStringResource(R.plurals.settings_grid_change_summary, count, count),
            modifier = Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(enabled = workspace != null && !settings.home.layoutLocked, onClick = {
            val change = pending
            pending = null
            if (change != null) update { if (it.home.layoutLocked) it else change(it) }
        }) { Text(stringResource(R.string.settings_grid_change_apply)) } },
        dismissButton = { TextButton(onClick = { pending = null }) { Text(stringResource(R.string.home_cancel)) } },
    )
    return LayoutChangeControl(workspace != null) { change ->
        if (workspace != null && !settings.home.layoutLocked) {
            if (newlyHiddenItems(workspace, settings, change(settings).coerced()) > 0) pending = change
            else update { if (it.home.layoutLocked) it else change(it) }
        }
    }
}
