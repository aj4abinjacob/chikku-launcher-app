package io.github.aj4abinjacob.chikku.settings.sections

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.settings.*
import io.github.aj4abinjacob.chikku.settings.components.*
import io.github.aj4abinjacob.chikku.settings.previews.EdgeFeedbackPreview
import io.github.aj4abinjacob.chikku.settings.previews.HorizontalPagingLivePreview
import io.github.aj4abinjacob.chikku.settings.previews.PageIndicatorPreview
import io.github.aj4abinjacob.chikku.settings.previews.PageTransitionPreview
import io.github.aj4abinjacob.chikku.settings.previews.SwipeSensitivityPreview
import io.github.aj4abinjacob.chikku.settings.previews.pagingEdgeLabel
import io.github.aj4abinjacob.chikku.settings.previews.pagingEffectLabel
import io.github.aj4abinjacob.chikku.settings.previews.pagingIndicatorLabel
import io.github.aj4abinjacob.chikku.settings.previews.pagingSensitivityLabel

/**
 * Controls are independent per surface; drafts update the pinned native sample before slider release.
 * Choices are inline thumbnail tiles rather than dialogs, and controls that don't apply are dimmed
 * in place instead of removed, so nothing moves under the finger while trying options.
 */
@Composable
internal fun HorizontalScrollingSettingsPage(
    settings: LauncherSettings,
    drawer: Boolean,
    update: ((LauncherSettings) -> LauncherSettings) -> Unit,
) {
    var live by remember(settings) { mutableStateOf(settings) }
    var strengthDraft by remember(settings) { mutableStateOf(false) }
    val edit: ((LauncherSettings) -> LauncherSettings) -> Unit = { transform -> live = transform(live); update(transform) }
    val home = settings.home
    val apps = settings.drawer
    val effect = if (drawer) apps.pageTransitionEffect else home.transitionEffect
    val strength = if (drawer) apps.pageEffectStrength else home.pageEffectStrength
    val sensitivity = if (drawer) apps.pageSwipeSensitivity else home.pageSwipeSensitivity
    val duration = if (drawer) apps.pageSnapDurationMs else home.pageSnapDurationMs
    val edge = if (drawer) apps.pageEdgeFeedback else home.pageEdgeFeedback
    val indicator = if (drawer) apps.pageIndicatorStyle else home.pageIndicatorStyle
    val autoHide = if (drawer) apps.pageIndicatorAutoHide else home.pageIndicatorAutoHide
    val milliseconds = stringResource(R.string.paging_duration_value)
    Column(Modifier.fillMaxSize()) {
        HorizontalPagingLivePreview(live, drawer, showEffectFrame = strengthDraft)
        SettingsPage(Modifier.weight(1f)) {
            if (drawer && apps.style != DrawerStyle.HORIZONTAL_PAGED) SettingsInfoText(stringResource(R.string.paging_drawer_mode_hint))

            SettingsSectionHeader(stringResource(R.string.paging_section_swipe))
            SettingsTileChoiceRow(
                title = stringResource(R.string.paging_sensitivity),
                options = PageSwipeSensitivity.entries,
                selected = sensitivity,
                optionLabel = { stringResource(pagingSensitivityLabel(it)) },
                optionSummary = { stringResource(when (it) {
                    PageSwipeSensitivity.LOW -> R.string.paging_sensitivity_low_summary
                    PageSwipeSensitivity.NORMAL -> R.string.paging_sensitivity_normal_summary
                    PageSwipeSensitivity.HIGH -> R.string.paging_sensitivity_high_summary
                }) },
                optionPreview = { SwipeSensitivityPreview(it) },
                onSelect = { value -> edit { if (drawer) it.copy(drawer = it.drawer.copy(pageSwipeSensitivity = value)) else it.copy(home = it.home.copy(pageSwipeSensitivity = value)) } },
            )
            SettingsIntSliderRow(
                title = stringResource(R.string.paging_duration), value = duration, range = 100..500,
                valueLabel = { milliseconds.format(it) },
                rangeLabels = stringResource(R.string.paging_duration_quick) to stringResource(R.string.paging_duration_gentle),
                onValueChanging = { value -> live = if (drawer) live.copy(drawer = live.drawer.copy(pageSnapDurationMs = value)) else live.copy(home = live.home.copy(pageSnapDurationMs = value)) },
                onValueChangeFinished = { value -> edit { if (drawer) it.copy(drawer = it.drawer.copy(pageSnapDurationMs = value)) else it.copy(home = it.home.copy(pageSnapDurationMs = value)) } },
            )
            SettingsTileChoiceRow(
                title = stringResource(R.string.paging_edge_feedback), options = PageEdgeFeedback.entries,
                selected = edge,
                optionLabel = { stringResource(pagingEdgeLabel(it)) },
                optionSummary = { stringResource(if (it == PageEdgeFeedback.NONE) R.string.paging_edge_none_summary else R.string.paging_edge_resistance_summary) },
                optionPreview = { EdgeFeedbackPreview(it) },
                onSelect = { value -> edit { if (drawer) it.copy(drawer = it.drawer.copy(pageEdgeFeedback = value)) else it.copy(home = it.home.copy(pageEdgeFeedback = value)) } },
            )
            SettingsInfoText(stringResource(R.string.paging_duration_hint))

            SettingsSectionHeader(stringResource(R.string.paging_section_transition))
            SettingsTileChoiceRow(
                title = stringResource(R.string.paging_effect), options = PageTransitionEffect.entries,
                selected = effect, optionPreview = { PageTransitionPreview(it) },
                optionLabel = { stringResource(pagingEffectLabel(it)) },
                onSelect = { value -> edit { if (drawer) it.copy(drawer = it.drawer.copy(pageTransitionEffect = value)) else it.copy(home = it.home.copy(transitionEffect = value)) } },
            )
            SettingsSliderRow(
                title = stringResource(R.string.paging_strength), value = strength, valueRange = 0f..1f,
                valueLabel = percentageValueLabel(), enabled = effect != PageTransitionEffect.SLIDE,
                rangeLabels = stringResource(R.string.paging_strength_subtle) to stringResource(R.string.paging_strength_full),
                onValueChanging = { value -> strengthDraft = true; live = if (drawer) live.copy(drawer = live.drawer.copy(pageEffectStrength = value)) else live.copy(home = live.home.copy(pageEffectStrength = value)) },
                onValueChangeFinished = { value -> strengthDraft = false; edit { if (drawer) it.copy(drawer = it.drawer.copy(pageEffectStrength = value)) else it.copy(home = it.home.copy(pageEffectStrength = value)) } },
            )
            SettingsInfoText(stringResource(R.string.paging_strength_slide_hint))

            SettingsSectionHeader(stringResource(R.string.paging_section_pages))
            SettingsSwitchRow(
                title = stringResource(R.string.drawer_settings_infinite_scroll),
                summary = stringResource(if (!drawer && home.googleDiscoverEnabled) R.string.settings_home_infinite_scroll_summary_discover else R.string.paging_loop_summary),
                checked = if (drawer) apps.infiniteScroll else home.infiniteScroll,
                icon = R.drawable.ic_settings_row_all_inclusive,
                onCheckedChange = { value -> edit { if (drawer) it.copy(drawer = it.drawer.copy(infiniteScroll = value)) else it.copy(home = it.home.copy(infiniteScroll = value)) } },
            )
            SettingsTileChoiceRow(
                title = stringResource(R.string.settings_home_page_indicator), options = PageIndicatorStyle.entries,
                selected = indicator, optionPreview = { PageIndicatorPreview(it) },
                optionLabel = { stringResource(pagingIndicatorLabel(it)) },
                onSelect = { value -> edit { if (drawer) it.copy(drawer = it.drawer.copy(pageIndicatorStyle = value)) else it.copy(home = it.home.copy(pageIndicatorStyle = value)) } },
            )
            SettingsSwitchRow(
                title = stringResource(R.string.settings_home_page_indicator_auto_hide),
                summary = stringResource(R.string.settings_home_page_indicator_auto_hide_summary), checked = autoHide,
                enabled = indicator != PageIndicatorStyle.NONE,
                icon = R.drawable.ic_settings_row_visibility_off,
                onCheckedChange = { value -> edit { if (drawer) it.copy(drawer = it.drawer.copy(pageIndicatorAutoHide = value)) else it.copy(home = it.home.copy(pageIndicatorAutoHide = value)) } },
            )
            SettingsInfoText(stringResource(R.string.paging_indicator_hint))

            SettingsNavigationRow(title = stringResource(R.string.paging_reset), icon = R.drawable.ic_menu_refresh, onClick = {
                edit {
                    if (drawer) it.copy(drawer = it.drawer.copy(pageSwipeSensitivity = PageSwipeSensitivity.NORMAL,
                        pageSnapDurationMs = 250, pageEffectStrength = 1f, pageEdgeFeedback = PageEdgeFeedback.RESISTANCE,
                        pageTransitionEffect = PageTransitionEffect.SLIDE, pageIndicatorStyle = PageIndicatorStyle.DOTS,
                        pageIndicatorAutoHide = false, infiniteScroll = false))
                    else it.copy(home = it.home.copy(pageSwipeSensitivity = PageSwipeSensitivity.NORMAL,
                        pageSnapDurationMs = 250, pageEffectStrength = 1f, pageEdgeFeedback = PageEdgeFeedback.RESISTANCE,
                        transitionEffect = PageTransitionEffect.SLIDE, pageIndicatorStyle = PageIndicatorStyle.DOTS,
                        pageIndicatorAutoHide = false, infiniteScroll = false))
                }
            })
        }
    }
}
