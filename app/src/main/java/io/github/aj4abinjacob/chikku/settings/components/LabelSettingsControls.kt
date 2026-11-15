package io.github.aj4abinjacob.chikku.settings.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.settings.DockLabelFont
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.home.labelFontSpec

/** Shared controls, while each surface owns and persists its independent label preferences. */
@Composable
fun LabelSettingsControls(
    labelScale: Float,
    labelFont: DockLabelFont,
    labelShadow: Boolean,
    labelSingleLine: Boolean,
    labelColor: Int?,
    onScaleChange: (Float) -> Unit,
    onFontChange: (DockLabelFont) -> Unit,
    onShadowChange: (Boolean) -> Unit,
    onSingleLineChange: (Boolean) -> Unit,
    onColorChange: (Int?) -> Unit,
    enabled: Boolean = true,
    /** Every label-size value while the slider is dragged, for a live preview; saved on release. */
    onScaleChanging: ((Float) -> Unit)? = null,
    /** Leading row icons, for a main page whose other rows have them. */
    icons: Boolean = false,
) {
    SettingsSliderRow(
        title = stringResource(R.string.settings_home_label_size),
        value = labelScale.coerceIn(LauncherSettings.MIN_SCALE, LauncherSettings.MAX_SCALE),
        onValueChangeFinished = onScaleChange,
        valueRange = LauncherSettings.MIN_SCALE..LauncherSettings.MAX_SCALE,
        steps = 9,
        valueLabel = percentageValueLabel(),
        enabled = enabled,
        onValueChanging = onScaleChanging,
        icon = if (icons) R.drawable.ic_settings_row_format_size else null,
    )
    LabelFontChoiceRow(selected = labelFont, onSelect = onFontChange, enabled = enabled,
        icon = if (icons) R.drawable.ic_settings_row_font_download else null)
    SettingsColorRow(
        title = stringResource(R.string.settings_label_color),
        color = labelColor,
        onColorChange = onColorChange,
        enabled = enabled,
        icon = if (icons) R.drawable.ic_settings_row_palette else null,
    )
    SettingsSwitchRow(
        title = stringResource(R.string.settings_dock_label_shadow),
        checked = labelShadow,
        onCheckedChange = onShadowChange,
        enabled = enabled,
        icon = if (icons) R.drawable.ic_settings_row_blur_on else null,
    )
    SettingsSwitchRow(
        title = stringResource(R.string.settings_dock_single_line),
        checked = labelSingleLine,
        onCheckedChange = onSingleLineChange,
        enabled = enabled,
        icon = if (icons) R.drawable.ic_settings_row_wrap_text else null,
    )
}

/**
 * The label font chooser used by every surface (home, dock, drawer, folders). Each option's name is
 * drawn in the font it selects, from the same [labelFontSpec] home labels use; the text stays the
 * font's name, so TalkBack announces it normally.
 */
@Composable
fun LabelFontChoiceRow(
    selected: DockLabelFont,
    onSelect: (DockLabelFont) -> Unit,
    enabled: Boolean = true,
    @DrawableRes icon: Int? = null,
) {
    SettingsChoiceRow(
        title = stringResource(R.string.settings_dock_label_font),
        options = DockLabelFont.entries,
        selected = selected,
        optionLabel = { stringResource(it.labelResource()) },
        onSelect = onSelect,
        enabled = enabled,
        optionTextStyle = { labelFontTextStyle(it) },
        icon = icon,
    )
}

/** The Compose text style that draws [font] the way home's View labels do. */
fun labelFontTextStyle(font: DockLabelFont): TextStyle {
    val spec = font.labelFontSpec()
    val weight = FontWeight(spec.weight)
    return TextStyle(fontFamily = FontFamily(Font(DeviceFontFamilyName(spec.familyName), weight)), fontWeight = weight)
}

@StringRes
private fun DockLabelFont.labelResource(): Int = when (this) {
    DockLabelFont.NORMAL -> R.string.settings_dock_font_normal
    DockLabelFont.MEDIUM -> R.string.settings_dock_font_medium
    DockLabelFont.CONDENSED -> R.string.settings_dock_font_condensed
    DockLabelFont.LIGHT -> R.string.settings_dock_font_light
}
