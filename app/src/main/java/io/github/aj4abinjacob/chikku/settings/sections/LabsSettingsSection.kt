package io.github.aj4abinjacob.chikku.settings.sections

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.settings.components.SettingsInfoText
import io.github.aj4abinjacob.chikku.settings.components.SettingsNavigationRow
import io.github.aj4abinjacob.chikku.settings.components.SettingsPage
import io.github.aj4abinjacob.chikku.settings.components.SettingsSwitchRow
import io.github.aj4abinjacob.chikku.settings.previews.DebugOverlayLivePreview

/**
 * Labs (G11): experimental flags, listed only while `labs.unlocked` (tap the version number in
 * About 7 times). Extended grid changes the available grid ranges; faster icon cache is not implemented.
 */
@Composable
fun LabsSettingsSection(settings: LauncherSettings, update: ((LauncherSettings) -> LauncherSettings) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        DebugOverlayLivePreview(settings.labs.debugOverlay)
    SettingsPage(Modifier.weight(1f)) {
        SettingsInfoText(stringResource(R.string.labs_note))
        SettingsSwitchRow(
            title = stringResource(R.string.settings_labs_debug_overlay),
            summary = stringResource(R.string.labs_debug_overlay_summary),
            checked = settings.labs.debugOverlay,
            onCheckedChange = { on -> update { it.copy(labs = it.labs.copy(debugOverlay = on)) } },
            icon = R.drawable.ic_settings_row_bug_report,
        )
        SettingsSwitchRow(
            title = stringResource(R.string.labs_faster_icon_cache),
            summary = stringResource(R.string.labs_not_active_summary),
            checked = settings.labs.fasterIconCache,
            onCheckedChange = { on -> update { it.copy(labs = it.labs.copy(fasterIconCache = on)) } },
            enabled = false,
            icon = R.drawable.ic_settings_row_cached,
        )
        SettingsSwitchRow(
            title = stringResource(R.string.labs_extended_grid),
            summary = stringResource(R.string.settings_labs_extended_grid_summary),
            checked = settings.labs.extendedGrid,
            onCheckedChange = { on -> update { it.copy(labs = it.labs.copy(extendedGrid = on)) } },
            icon = R.drawable.ic_settings_row_grid_on,
        )
        SettingsNavigationRow(
            title = stringResource(R.string.labs_hide),
            summary = stringResource(R.string.labs_hide_summary),
            onClick = { update { it.copy(labs = it.labs.copy(unlocked = false)) } },
            icon = R.drawable.ic_settings_row_visibility_off,
        )
    }
    }
}
