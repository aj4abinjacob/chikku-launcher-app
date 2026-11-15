package io.github.aj4abinjacob.chikku.settings.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.GestureAction
import io.github.aj4abinjacob.chikku.core.model.LauncherApp
import io.github.aj4abinjacob.chikku.platform.AppLockGuard
import io.github.aj4abinjacob.chikku.platform.LauncherAppsShortcuts
import io.github.aj4abinjacob.chikku.platform.ShortcutListResult
import io.github.aj4abinjacob.chikku.settings.LocalHomeDependencies
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The shortcuts of [app] as a pickable list, shared by the gesture and the card action pickers.
 * Shows nothing for a locked app: a launch grant never authorizes browsing private labels. The
 * pickers differ only in how a message ([info]) and a row ([row]) are drawn.
 */
@Composable
internal fun ShortcutListPage(
    app: LauncherApp?,
    current: GestureAction?,
    onPick: (GestureAction) -> Unit,
    info: @Composable (String) -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
    row: @Composable (label: String, action: GestureAction, selected: Boolean, onClick: () -> Unit) -> Unit,
) {
    if (app == null) return
    val context = LocalContext.current
    val lock = LocalHomeDependencies.current.appLock
    val lockState by lock.state.collectAsState()
    if (!lockState.mayRead(app.key.packageName, app.key.userSerial) || !lock.mayRead(app.key.packageName, app.key.userSerial)) return
    val shortcuts = remember(context, lock) { LauncherAppsShortcuts(context.applicationContext, AppLockGuard { _, _ -> false }, lock) }
    val result by produceState<Pair<AppKey, ShortcutListResult>?>(null, app.key, lockState) {
        value = null
        value = app.key to withContext(Dispatchers.IO) { shortcuts.shortcutsFor(app.key) }
    }
    when (val listed = result?.takeIf { it.first == app.key }?.second) {
        null -> info(stringResource(R.string.gesture_picker_loading))
        ShortcutListResult.NotDefaultLauncher -> info(stringResource(R.string.gesture_picker_shortcuts_need_default))
        ShortcutListResult.Failed -> info(stringResource(R.string.gesture_picker_shortcuts_failed))
        is ShortcutListResult.Listed ->
            if (listed.shortcuts.isEmpty()) {
                info(stringResource(R.string.gesture_picker_no_shortcuts))
            } else {
                LazyColumn(contentPadding = contentPadding) {
                    items(listed.shortcuts, key = { it.shortcutId }) { option ->
                        val action = GestureAction.LaunchShortcut(option.packageName, option.shortcutId, option.userSerial)
                        row(option.label, action, action == current) {
                            if (lock.mayRead(option.packageName, option.userSerial)) onPick(action)
                        }
                    }
                }
            }
    }
}
