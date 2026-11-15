package io.github.aj4abinjacob.chikku.settings.sections

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import io.github.aj4abinjacob.chikku.settings.previews.NightSurfacesLivePreview
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.core.model.settings.NightMode
import io.github.aj4abinjacob.chikku.core.model.settings.NightSettings
import io.github.aj4abinjacob.chikku.settings.components.SettingsChoiceRow
import io.github.aj4abinjacob.chikku.settings.components.SettingsInfoText
import io.github.aj4abinjacob.chikku.settings.components.SettingsPage
import io.github.aj4abinjacob.chikku.settings.components.SettingsSectionHeader
import io.github.aj4abinjacob.chikku.settings.components.SettingsSwitchRow

/** Night mode (G09): off / on / follow system, and which launcher surfaces go dark. */
@Composable
fun NightModeSettingsSection(settings: LauncherSettings, update: ((LauncherSettings) -> LauncherSettings) -> Unit) {
    val night = settings.night
    fun edit(transform: (NightSettings) -> NightSettings) = update { it.copy(night = transform(it.night)) }
    val scopeEnabled = night.mode != NightMode.OFF

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.heightIn(max = (LocalConfiguration.current.screenHeightDp * .4f).coerceAtLeast(120f).dp)
            .verticalScroll(rememberScrollState())) {
            NightSurfacesLivePreview(settings)
        }
        SettingsPage(Modifier.weight(1f)) {
            SettingsChoiceRow(
                title = stringResource(R.string.settings_night_mode),
                options = NightMode.entries,
                selected = night.mode,
                optionLabel = { mode ->
                    stringResource(
                        when (mode) {
                            NightMode.SYSTEM -> R.string.settings_night_system
                            NightMode.ON -> R.string.settings_night_on
                            NightMode.OFF -> R.string.settings_night_off
                        },
                    )
                },
                onSelect = { mode -> edit { it.copy(mode = mode) } },
                icon = R.drawable.ic_settings_row_dark_mode,
            )
            SettingsSectionHeader(stringResource(R.string.night_header_scope))
            SettingsSwitchRow(
                title = stringResource(R.string.night_dark_drawer),
                checked = night.darkDrawer,
                enabled = scopeEnabled,
                onCheckedChange = { value -> edit { it.copy(darkDrawer = value) } },
                icon = R.drawable.ic_settings_row_apps,
            )
            SettingsSwitchRow(
                title = stringResource(R.string.night_dark_folders),
                checked = night.darkFolders,
                enabled = scopeEnabled,
                onCheckedChange = { value -> edit { it.copy(darkFolders = value) } },
                icon = R.drawable.ic_settings_row_folder,
            )
            SettingsSwitchRow(
                title = stringResource(R.string.night_dark_search),
                checked = night.darkSearch,
                enabled = scopeEnabled,
                onCheckedChange = { value -> edit { it.copy(darkSearch = value) } },
                icon = R.drawable.ic_search,
            )
            SettingsSwitchRow(
                title = stringResource(R.string.night_dark_menus),
                checked = night.darkMenus,
                enabled = scopeEnabled,
                onCheckedChange = { value -> edit { it.copy(darkMenus = value) } },
                icon = R.drawable.ic_settings_row_menu,
            )
            SettingsSwitchRow(
                title = stringResource(R.string.night_pure_black),
                summary = stringResource(R.string.night_pure_black_summary),
                checked = night.pureBlack,
                enabled = scopeEnabled,
                onCheckedChange = { value -> edit { it.copy(pureBlack = value) } },
                icon = R.drawable.ic_settings_row_contrast,
            )
            SettingsInfoText(stringResource(R.string.night_info))
        }
    }
}
