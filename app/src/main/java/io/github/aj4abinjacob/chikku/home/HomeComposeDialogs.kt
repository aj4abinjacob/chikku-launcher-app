package io.github.aj4abinjacob.chikku.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.ItemActions
import io.github.aj4abinjacob.chikku.home.appmenu.AppEditRequest
import io.github.aj4abinjacob.chikku.home.appmenu.AppEditSheet
import io.github.aj4abinjacob.chikku.home.folder.FolderEditRequest
import io.github.aj4abinjacob.chikku.home.folder.FolderEditSheet
import io.github.aj4abinjacob.chikku.settings.components.SwipeActionsEditorDialog
import io.github.aj4abinjacob.chikku.settings.components.collectLauncherApps

/** The settings-styled folder editor over HOME (folders feature). */
fun ComposeDialogHost.showFolderEditor(request: FolderEditRequest) = show { dismiss ->
    FolderEditSheet(request, onDismiss = dismiss)
}

/** The app menu's name and icon editor over HOME. */
fun ComposeDialogHost.showAppEditor(request: AppEditRequest) = show { dismiss ->
    AppEditSheet(request, onDismiss = dismiss)
}

/**
 * The gestures feature's swipe-actions editor for one workspace icon, opened from the app menu.
 * Each change is saved at once through [onChange]; the dialog shows the edited values meanwhile.
 */
fun ComposeDialogHost.showSwipeActionsEditor(title: String, appKey: AppKey, initial: ItemActions, onChange: (ItemActions) -> Unit) = show { dismiss ->
    var actions by remember { mutableStateOf(initial) }
    SwipeActionsEditorDialog(
        title = title,
        swipeUp = actions.swipeUp,
        swipeDown = actions.swipeDown,
        apps = collectLauncherApps(),
        defaultLabel = stringResource(R.string.gestures_app_defaults),
        onChange = { up, down ->
            actions = actions.copy(swipeUp = up, swipeDown = down)
            onChange(actions)
        },
        onDismiss = dismiss,
        appKey = appKey,
    )
}
