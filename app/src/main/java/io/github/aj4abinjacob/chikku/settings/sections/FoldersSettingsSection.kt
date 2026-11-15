package io.github.aj4abinjacob.chikku.settings.sections

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import android.graphics.drawable.GradientDrawable
import android.widget.ImageView
import androidx.compose.ui.res.stringResource
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.settings.previews.FolderPagingPreview
import io.github.aj4abinjacob.chikku.settings.previews.FolderWindowPreview
import io.github.aj4abinjacob.chikku.settings.previews.FolderScenePreview
import io.github.aj4abinjacob.chikku.core.model.settings.FolderIconBackground
import io.github.aj4abinjacob.chikku.core.model.settings.FolderPreviewStyle
import io.github.aj4abinjacob.chikku.core.model.settings.FolderWindowStyle
import io.github.aj4abinjacob.chikku.core.model.settings.FolderSortOrder
import io.github.aj4abinjacob.chikku.core.model.settings.FolderScrollStyle
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.settings.components.LabelSettingsControls
import io.github.aj4abinjacob.chikku.settings.components.SettingsIntSliderRow
import io.github.aj4abinjacob.chikku.settings.components.SettingsChoiceRow
import io.github.aj4abinjacob.chikku.settings.components.SettingsStepRow
import io.github.aj4abinjacob.chikku.settings.components.SettingsColorRow
import io.github.aj4abinjacob.chikku.settings.components.SettingsInfoText
import io.github.aj4abinjacob.chikku.settings.components.SettingsPage
import io.github.aj4abinjacob.chikku.settings.components.SettingsSectionHeader
import io.github.aj4abinjacob.chikku.settings.components.SettingsSliderRow
import io.github.aj4abinjacob.chikku.settings.components.dpValueLabel
import io.github.aj4abinjacob.chikku.settings.components.percentageValueLabel
import io.github.aj4abinjacob.chikku.settings.components.SettingsSwitchRow
import io.github.aj4abinjacob.chikku.home.folder.FolderIconDrawable

/** Folder appearance and interaction preferences, persisted by the shared settings repository. */
@Composable
fun FoldersSettingsSection(settings: LauncherSettings, update: ((LauncherSettings) -> LauncherSettings) -> Unit) {
    val folders = settings.folders
    // The open-window preview follows sliders while they are dragged; each setting is saved on release.
    var live by remember(folders) { mutableStateOf(folders) }
    val effectiveIconScale = if (live.matchDesktopSize) settings.home.iconScale else live.iconScale
    Column(Modifier.fillMaxSize()) {
        FolderScenePreview(live.copy(iconScale = effectiveIconScale), settings.night, settings.home)
        SettingsPage(Modifier.weight(1f)) {
            SettingsSectionHeader(stringResource(R.string.folders_refresh_icon))
            SettingsChoiceRow(
                title = stringResource(R.string.folders_settings_preview),
                options = FolderPreviewStyle.entries,
                selected = folders.previewStyle,
                optionLabel = { stringResource(previewLabel(it)) },
                optionPreview = { FolderIconPreview(it, folders.iconBackground, folders.iconBackgroundColor, folders.previewCount, Modifier.size(64.dp)) },
                onSelect = { style -> update { it.copy(folders = it.folders.copy(previewStyle = style)) } },
                icon = R.drawable.ic_settings_row_folder,
            )
            if (folders.previewStyle == FolderPreviewStyle.GRID) SettingsIntSliderRow(
                title = stringResource(R.string.folders_custom_preview_count),
                value = folders.previewCount,
                range = 1..9,
                enabled = folders.previewStyle == FolderPreviewStyle.GRID,
                onValueChangeFinished = { count -> update { it.copy(folders = it.folders.copy(previewCount = count)) } },
                onValueChanging = { count -> live = live.copy(previewCount = count) },
                icon = R.drawable.ic_settings_row_apps,
            )
            SettingsChoiceRow(
                title = stringResource(R.string.folders_settings_icon_background),
                options = FolderIconBackground.entries,
                selected = folders.iconBackground,
                optionLabel = { stringResource(backgroundLabel(it)) },
                optionPreview = { FolderIconPreview(folders.previewStyle, it, folders.iconBackgroundColor, folders.previewCount, Modifier.size(64.dp)) },
                onSelect = { bg -> update { it.copy(folders = it.folders.copy(iconBackground = bg)) } },
                icon = R.drawable.ic_settings_row_format_color_fill,
            )
            if (folders.iconBackground != FolderIconBackground.NONE) {
                SettingsColorRow(
                    title = stringResource(R.string.folders_icon_background_color),
                    color = folders.iconBackgroundColor,
                    onColorChange = { color -> update { it.copy(folders = it.folders.copy(iconBackgroundColor = color)) } },
                    icon = R.drawable.ic_settings_row_palette,
                )
                SettingsInfoText(stringResource(R.string.folders_icon_color_summary))
            }
            SettingsSectionHeader(stringResource(R.string.folders_refresh_window))
            SettingsChoiceRow(
                title = stringResource(R.string.folders_custom_window),
                options = FolderWindowStyle.entries,
                selected = folders.windowStyle,
                optionLabel = { stringResource(if (it == FolderWindowStyle.FLOATING) R.string.folders_custom_floating else R.string.folders_custom_immersive) },
                optionSummary = { stringResource(if (it == FolderWindowStyle.FLOATING) R.string.hint_folder_window_floating else R.string.hint_folder_window_immersive) },
                optionPreview = { FolderWindowPreview(it) },
                onSelect = { value -> update { it.copy(folders = it.folders.copy(windowStyle = value)) } },
                icon = R.drawable.ic_settings_row_web_asset,
            )
            SettingsColorRow(
                title = stringResource(R.string.folders_settings_background_color),
                color = folders.backgroundColor,
                onColorChange = { color -> update { it.copy(folders = it.folders.copy(backgroundColor = color)) } },
                icon = R.drawable.ic_settings_row_palette,
            )
            SettingsSliderRow(
                title = stringResource(R.string.folders_settings_background_alpha),
                value = folders.backgroundAlpha,
                onValueChangeFinished = { alpha -> update { it.copy(folders = it.folders.copy(backgroundAlpha = alpha)) } },
                valueRange = 0f..1f,
                valueLabel = percentageValueLabel(),
                onValueChanging = { alpha -> live = live.copy(backgroundAlpha = alpha) },
                icon = R.drawable.ic_settings_row_opacity,
            )

            SettingsSwitchRow(
                title = stringResource(R.string.folders_custom_show_name),
                checked = folders.showName,
                onCheckedChange = { value -> update { it.copy(folders = it.folders.copy(showName = value)) } },
                icon = R.drawable.ic_settings_row_title,
            )
            SettingsSliderRow(
                title = stringResource(R.string.folders_custom_corner_radius),
                value = folders.cornerRadiusDp,
                valueRange = 0f..48f,
                valueLabel = dpValueLabel(),
                onValueChangeFinished = { value -> update { it.copy(folders = it.folders.copy(cornerRadiusDp = value)) } },
                onValueChanging = { value -> live = live.copy(cornerRadiusDp = value) },
                icon = R.drawable.ic_settings_row_rounded_corner,
            )
            SettingsSwitchRow(
                title = stringResource(R.string.folders_custom_shadow),
                checked = folders.windowShadow,
                onCheckedChange = { value -> update { it.copy(folders = it.folders.copy(windowShadow = value)) } },
                icon = R.drawable.ic_settings_row_gradient,
            )
            SettingsSectionHeader(stringResource(R.string.folders_refresh_layout))
            SettingsStepRow(
                title = stringResource(R.string.folders_custom_columns),
                options = (0..6).toList(),
                selected = folders.columns,
                optionLabel = { if (it == 0) stringResource(R.string.folders_custom_auto) else it.toString() },
                onSelect = { value -> update { it.copy(folders = it.folders.copy(columns = value)) } },
                icon = R.drawable.ic_settings_row_view_column,
            )
            SettingsChoiceRow(
                title = stringResource(R.string.folders_paging_direction),
                options = FolderScrollStyle.entries,
                selected = folders.scrollStyle,
                optionLabel = { stringResource(if (it == FolderScrollStyle.VERTICAL) R.string.folders_paging_vertical else R.string.folders_paging_horizontal) },
                optionPreview = { FolderPagingPreview(it) },
                onSelect = { value -> update { it.copy(folders = it.folders.copy(scrollStyle = value)) } },
                icon = R.drawable.ic_settings_row_view_carousel,
            )
            if (folders.scrollStyle == FolderScrollStyle.HORIZONTAL_PAGED) {
                SettingsIntSliderRow(
                    title = stringResource(R.string.folders_paging_rows),
                    value = folders.rows,
                    range = 1..6,
                    onValueChangeFinished = { value -> update { it.copy(folders = it.folders.copy(rows = value)) } },
                    onValueChanging = { value -> live = live.copy(rows = value) },
                    icon = R.drawable.ic_settings_row_table_rows,
                )
                SettingsInfoText(stringResource(R.string.folders_paging_fit_summary))
            }
            SettingsChoiceRow(
                title = stringResource(R.string.folders_custom_sort),
                options = FolderSortOrder.entries,
                selected = folders.sortOrder,
                optionLabel = { stringResource(if (it == FolderSortOrder.MANUAL) R.string.folders_custom_manual else R.string.folders_custom_alphabetical) },
                optionSummary = { stringResource(if (it == FolderSortOrder.MANUAL) R.string.hint_folder_sort_manual else R.string.hint_folder_sort_alphabetical) },
                onSelect = { value -> update { it.copy(folders = it.folders.copy(sortOrder = value)) } },
                icon = R.drawable.ic_settings_row_sort_by_alpha,
            )
            SettingsInfoText(stringResource(R.string.folders_custom_sort_summary))
            SettingsSectionHeader(stringResource(R.string.folders_custom_icon_layout))
            SettingsSwitchRow(
                title = stringResource(R.string.folders_custom_match_desktop),
                checked = folders.matchDesktopSize,
                onCheckedChange = { value -> update { it.copy(folders = it.folders.copy(matchDesktopSize = value)) } },
                icon = R.drawable.ic_settings_row_aspect_ratio,
            )
            if (!folders.matchDesktopSize) SettingsSliderRow(
                title = stringResource(R.string.folders_custom_icon_size),
                value = if (folders.matchDesktopSize) settings.home.iconScale else folders.iconScale,
                valueRange = LauncherSettings.MIN_SCALE..LauncherSettings.MAX_SCALE,
                valueLabel = percentageValueLabel(),
                enabled = !folders.matchDesktopSize,
                onValueChangeFinished = { value -> update { it.copy(folders = it.folders.copy(iconScale = value)) } },
                onValueChanging = { value -> live = live.copy(iconScale = value) },
                icon = R.drawable.ic_settings_row_photo_size_select_large,
            )
            SettingsSectionHeader(stringResource(R.string.folders_refresh_labels))
            SettingsSwitchRow(
                title = stringResource(R.string.folders_custom_labels),
                checked = folders.showLabels,
                onCheckedChange = { value -> update { it.copy(folders = it.folders.copy(showLabels = value)) } },
                icon = R.drawable.ic_settings_row_label,
            )
            if (folders.showLabels) LabelSettingsControls(
                labelScale = folders.labelScale,
                labelFont = folders.labelFont,
                labelShadow = folders.labelShadow,
                labelSingleLine = folders.labelSingleLine,
                labelColor = folders.labelColor,
                onScaleChange = { value -> update { it.copy(folders = it.folders.copy(labelScale = value)) } },
                onFontChange = { value -> update { it.copy(folders = it.folders.copy(labelFont = value)) } },
                onShadowChange = { value -> update { it.copy(folders = it.folders.copy(labelShadow = value)) } },
                onSingleLineChange = { value -> update { it.copy(folders = it.folders.copy(labelSingleLine = value)) } },
                onColorChange = { value -> update { it.copy(folders = it.folders.copy(labelColor = value)) } },
                enabled = folders.showLabels,
                icons = true,
                onScaleChanging = { value -> live = live.copy(labelScale = value) },
            )

            SettingsSectionHeader(stringResource(R.string.folders_settings_behaviour))
            SettingsSwitchRow(
                title = stringResource(R.string.folders_custom_close_on_launch),
                summary = stringResource(R.string.folders_custom_close_summary),
                checked = folders.closeOnLaunch,
                onCheckedChange = { value -> update { it.copy(folders = it.folders.copy(closeOnLaunch = value)) } },
                icon = R.drawable.ic_settings_row_close_fullscreen,
            )
            SettingsSwitchRow(
                title = stringResource(R.string.folders_settings_gestures),
                summary = stringResource(R.string.folders_settings_gestures_summary),
                checked = folders.swipeActionsEnabled,
                onCheckedChange = { on -> update { it.copy(folders = it.folders.copy(swipeActionsEnabled = on)) } },
                icon = R.drawable.ic_settings_row_touch_app,
            )
            SettingsInfoText(stringResource(R.string.folders_settings_info), details = stringResource(R.string.folders_settings_info_details))
        }
    }
}

/** The real folder icon renderer over generic coloured dots, as used on the home screen. */
@Composable
private fun FolderIconPreview(
    style: FolderPreviewStyle,
    background: FolderIconBackground,
    backgroundColor: Int?,
    count: Int,
    modifier: Modifier,
) {
    val description = stringResource(R.string.preview_df_closed_folder_description, count)
    AndroidView(
        factory = { ImageView(it) },
        update = { view ->
            val palette = listOf(0xFF6177CF.toInt(), 0xFF4B9A88.toInt(), 0xFFBD7966.toInt(), 0xFF9981BC.toInt())
            val previews = List(count) { index ->
                GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(palette[index % palette.size])
                }
            }
            view.setImageDrawable(FolderIconDrawable(previews, style, background,
                backgroundColor ?: ContextCompat.getColor(view.context, R.color.folder_icon_background)))
        },
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
    )
}

private fun previewLabel(style: FolderPreviewStyle): Int = when (style) {
    FolderPreviewStyle.GRID -> R.string.folders_settings_preview_grid
    FolderPreviewStyle.STACK -> R.string.folders_settings_preview_stack
    FolderPreviewStyle.FAN -> R.string.folders_settings_preview_fan
    FolderPreviewStyle.LINE -> R.string.folders_settings_preview_line
}

private fun backgroundLabel(background: FolderIconBackground): Int = when (background) {
    FolderIconBackground.NONE -> R.string.folders_settings_icon_background_none
    FolderIconBackground.CIRCLE -> R.string.folders_settings_icon_background_circle
    FolderIconBackground.ROUNDED_SQUARE -> R.string.folders_settings_icon_background_rounded
}
