package io.github.aj4abinjacob.chikku.settings.cards

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.badges.BadgeAccess
import io.github.aj4abinjacob.chikku.platform.cards.bluetooth.AndroidCardBluetoothSource
import io.github.aj4abinjacob.chikku.platform.cards.media.CardMediaSource
import io.github.aj4abinjacob.chikku.settings.components.SettingsInfoText
import io.github.aj4abinjacob.chikku.settings.components.SettingsNavigationRow
import io.github.aj4abinjacob.chikku.settings.components.SettingsSwitchRow
import kotlinx.coroutines.launch

@Composable
internal fun CardBluetoothConditionControl(checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean) {
    val context = LocalContext.current
    val source = remember(context) { AndroidCardBluetoothSource(context) }
    var granted by remember { mutableStateOf(source.hasPermission()) }
    var denied by remember { mutableStateOf(false) }
    // Also picks up access granted or revoked in Android settings while away.
    LifecycleResumeEffect(source) {
        granted = source.hasPermission()
        if (granted) denied = false
        onPauseOrDispose { }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        granted = allowed && source.hasPermission()
        denied = !granted
        onChange(granted)
    }
    fun request() {
        granted = source.hasPermission()
        if (granted) onChange(true)
        else if (Build.VERSION.SDK_INT >= 31) permission.launch(Manifest.permission.BLUETOOTH_CONNECT)
    }
    SettingsSwitchRow(stringResource(R.string.card_bluetooth_audio), checked, { wanted ->
        if (!wanted) { onChange(false); denied = false } else request()
    }, summary = stringResource(R.string.card_bluetooth_summary), enabled = enabled)
    if (checked && !granted) {
        SettingsInfoText(stringResource(R.string.card_bluetooth_missing))
        SettingsNavigationRow(stringResource(R.string.card_bluetooth_permission), { request() }, enabled = enabled)
    }
    if (denied) SettingsInfoText(stringResource(R.string.card_bluetooth_denied))
}

@Composable
internal fun MediaCardEditorControls(source: CardMediaSource, enabled: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var granted by remember(source) { mutableStateOf(false) }
    var saveFirst by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var opening by remember { mutableStateOf(false) }
    // Access is granted in Android settings, so re-check every time the user comes back.
    LifecycleResumeEffect(source) {
        val job = scope.launch { granted = source.hasAccess() }
        onPauseOrDispose { job.cancel() }
    }
    SettingsInfoText(stringResource(R.string.card_media_summary))
    if (granted) SettingsInfoText(stringResource(R.string.card_media_access_granted))
    else SettingsNavigationRow(stringResource(R.string.card_media_access), {
        scope.launch {
            opening = true
            try {
                saveFirst = !source.prepareAccess()
                if (!saveFirst) failed = BadgeAccess(context).settingsIntents().none { intent ->
                    runCatching { context.startActivity(intent); true }.getOrDefault(false)
                }
            } finally { opening = false }
        }
    }, enabled = enabled && !opening)
    if (saveFirst) SettingsInfoText(stringResource(R.string.card_media_save_first))
    if (failed) SettingsInfoText(stringResource(R.string.card_media_access_failed))
}
