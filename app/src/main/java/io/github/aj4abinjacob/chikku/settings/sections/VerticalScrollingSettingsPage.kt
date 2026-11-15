package io.github.aj4abinjacob.chikku.settings.sections

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerSearchBarPosition
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerSettings
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerStyle
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.core.model.settings.ListEdgeEffect
import io.github.aj4abinjacob.chikku.core.model.settings.ListScrollSpeed
import io.github.aj4abinjacob.chikku.settings.components.SettingsInfoText
import io.github.aj4abinjacob.chikku.settings.components.SettingsNavigationRow
import io.github.aj4abinjacob.chikku.settings.components.SettingsPage
import io.github.aj4abinjacob.chikku.settings.components.SettingsSectionHeader
import io.github.aj4abinjacob.chikku.settings.components.SettingsSubPageRow
import io.github.aj4abinjacob.chikku.settings.components.SettingsSwitchRow
import io.github.aj4abinjacob.chikku.settings.components.SettingsTileChoiceRow
import io.github.aj4abinjacob.chikku.settings.previews.ListEdgeEffectPreview
import io.github.aj4abinjacob.chikku.settings.previews.ListScrollSpeedPreview
import io.github.aj4abinjacob.chikku.settings.previews.VerticalScrollingLivePreview
import io.github.aj4abinjacob.chikku.settings.previews.listEdgeEffectLabel
import io.github.aj4abinjacob.chikku.settings.previews.listScrollSpeedLabel

/**
 * Vertical and List drawer scrolling. Speed and the end-of-list effect are new; remember-position and
 * pull-to-search are the same settings shown on Opening & closing and Search bar, gathered here.
 */
@Composable
internal fun VerticalScrollingSettingsPage(
    settings: LauncherSettings,
    update: ((LauncherSettings) -> LauncherSettings) -> Unit,
    fastScrollerSummary: String?,
    openFastScroller: () -> Unit,
) {
    val drawer = settings.drawer
    val editDrawer: ((DrawerSettings) -> DrawerSettings) -> Unit =
        { transform -> update { it.copy(drawer = transform(it.drawer)) } }
    Column(Modifier.fillMaxSize()) {
        VerticalScrollingLivePreview(settings)
        SettingsPage(Modifier.weight(1f)) {
            if (drawer.style == DrawerStyle.HORIZONTAL_PAGED) SettingsInfoText(stringResource(R.string.vertical_scroll_paged_hint))

            SettingsSectionHeader(stringResource(R.string.vertical_scroll_section_motion))
            SettingsTileChoiceRow(
                title = stringResource(R.string.vertical_scroll_speed),
                options = ListScrollSpeed.entries,
                selected = drawer.listScrollSpeed,
                optionLabel = { stringResource(listScrollSpeedLabel(it)) },
                optionSummary = { stringResource(when (it) {
                    ListScrollSpeed.SLOW -> R.string.vertical_scroll_speed_slow_summary
                    ListScrollSpeed.NORMAL -> R.string.vertical_scroll_speed_normal_summary
                    ListScrollSpeed.FAST -> R.string.vertical_scroll_speed_fast_summary
                }) },
                optionPreview = { ListScrollSpeedPreview(it) },
                onSelect = { value -> editDrawer { it.copy(listScrollSpeed = value) } },
            )
            SettingsInfoText(stringResource(R.string.vertical_scroll_speed_hint))
            SettingsTileChoiceRow(
                title = stringResource(R.string.vertical_scroll_edge),
                options = ListEdgeEffect.entries,
                selected = drawer.listEdgeEffect,
                optionLabel = { stringResource(listEdgeEffectLabel(it)) },
                optionSummary = { stringResource(if (it == ListEdgeEffect.SYSTEM) R.string.vertical_scroll_edge_system_summary else R.string.vertical_scroll_edge_none_summary) },
                optionPreview = { ListEdgeEffectPreview(it) },
                onSelect = { value -> editDrawer { it.copy(listEdgeEffect = value) } },
            )

            SettingsSectionHeader(stringResource(R.string.vertical_scroll_section_navigation))
            SettingsSubPageRow(stringResource(R.string.drawer_settings_fast_scroller), fastScrollerSummary,
                icon = R.drawable.ic_settings_row_sort_by_alpha, onClick = openFastScroller)
            SettingsSwitchRow(
                title = stringResource(R.string.drawer_settings_remember_position),
                summary = stringResource(R.string.drawer_settings_remember_position_summary),
                checked = drawer.rememberScrollPosition,
                icon = R.drawable.ic_settings_row_swap_vert,
                onCheckedChange = { on -> editDrawer { it.copy(rememberScrollPosition = on) } },
            )
            val searchShown = drawer.searchBarPosition != DrawerSearchBarPosition.HIDDEN
            SettingsSwitchRow(
                title = stringResource(R.string.drawer_settings_pull_to_search),
                summary = stringResource(if (searchShown) R.string.drawer_settings_pull_to_search_summary else R.string.drawer_search_hidden_hint),
                checked = drawer.pullToSearch && searchShown,
                enabled = searchShown,
                icon = R.drawable.ic_search,
                onCheckedChange = { on -> editDrawer { it.copy(pullToSearch = on) } },
            )

            SettingsNavigationRow(title = stringResource(R.string.vertical_scroll_reset), icon = R.drawable.ic_menu_refresh, onClick = {
                editDrawer { it.copy(listScrollSpeed = ListScrollSpeed.NORMAL, listEdgeEffect = ListEdgeEffect.SYSTEM) }
            })
        }
    }
}
