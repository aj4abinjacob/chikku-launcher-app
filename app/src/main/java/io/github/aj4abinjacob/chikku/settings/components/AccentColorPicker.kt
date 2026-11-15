package io.github.aj4abinjacob.chikku.settings.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.settings.ChikkuSettingsTheme

/** Chikku's own accent, used by the settings theme when no accent is chosen (null). */
const val CHIKKU_DEFAULT_ACCENT: Int = 0xFFB94318.toInt()

/** A named accent preset; [color] null means "Chikku default". */
private data class AccentPreset(val color: Int?, @StringRes val name: Int)

private val AccentPresets = listOf(
    AccentPreset(null, R.string.accent_chikku),
    AccentPreset(0xFFC62828.toInt(), R.string.accent_cherry),
    AccentPreset(0xFFC2185B.toInt(), R.string.accent_rose),
    AccentPreset(0xFF7B1FA2.toInt(), R.string.accent_grape),
    AccentPreset(0xFF3949AB.toInt(), R.string.accent_indigo),
    AccentPreset(0xFF1976D2.toInt(), R.string.accent_ocean),
    AccentPreset(0xFF00838F.toInt(), R.string.accent_teal),
    AccentPreset(0xFF2E7D32.toInt(), R.string.accent_forest),
    AccentPreset(0xFF827717.toInt(), R.string.accent_olive),
    AccentPreset(0xFFF9A825.toInt(), R.string.accent_amber),
    AccentPreset(0xFF6D4C41.toInt(), R.string.accent_cocoa),
    AccentPreset(0xFF546E7A.toInt(), R.string.accent_slate),
)

private val SwatchSize = 44.dp
private val SwatchGap = 10.dp

/**
 * Accent changer: named presets plus a custom color. [accent] null follows Chikku's default.
 * Changes apply at once; the settings theme recolors live, so the page itself is the preview.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccentColorPicker(accent: Int?, onAccentChange: (Int?) -> Unit, modifier: Modifier = Modifier) {
    var customOpen by rememberSaveable { mutableStateOf(false) }
    val preset = AccentPresets.firstOrNull { it.color == accent }
    val isCustom = preset == null
    val currentName = if (preset != null) stringResource(preset.name) else stringResource(R.string.accent_custom)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(settingsCardColor())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.look_accent_color), style = MaterialTheme.typography.bodyLarge)
            Text(
                if (isCustom && accent != null) "$currentName · ${hex(accent)}" else currentName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // An even grid across the full card width: as many columns as fit, each swatch centered in
        // its cell, so the spare width is shared out instead of collecting on the trailing edge.
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = ((maxWidth + SwatchGap) / (SwatchSize + SwatchGap)).toInt().coerceAtLeast(1)
            val cell = Modifier.width(maxWidth / columns)
            FlowRow(
                maxItemsInEachRow = columns,
                verticalArrangement = Arrangement.spacedBy(SwatchGap),
            ) {
                AccentPresets.forEach { item ->
                    val swatch = Color(item.color ?: CHIKKU_DEFAULT_ACCENT)
                    Box(cell, contentAlignment = Alignment.Center) { AccentSwatch(
                        fill = SolidColor(swatch),
                        checkTint = checkTint(swatch),
                        selected = item.color == accent,
                        description = stringResource(item.name),
                        onClick = { onAccentChange(item.color) },
                    ) }
                }
                Box(cell, contentAlignment = Alignment.Center) { AccentSwatch(
                    fill = if (isCustom && accent != null) {
                        SolidColor(Color(accent))
                    } else {
                        Brush.sweepGradient(HueStops)
                    },
                    checkTint = if (accent != null) checkTint(Color(accent)) else Color.White,
                    selected = isCustom,
                    description = stringResource(R.string.accent_custom),
                    onClick = { customOpen = true },
                    showPlus = !isCustom,
                ) }
            }
        }
    }

    if (customOpen) {
        CustomAccentDialog(
            initial = accent ?: CHIKKU_DEFAULT_ACCENT,
            onDismiss = { customOpen = false },
            onConfirm = { color ->
                customOpen = false
                onAccentChange(color)
            },
        )
    }
}

@Composable
private fun AccentSwatch(
    fill: Brush,
    checkTint: Color,
    selected: Boolean,
    description: String,
    onClick: () -> Unit,
    showPlus: Boolean = false,
) {
    val selectedState = stringResource(if (selected) R.string.accent_selected else R.string.accent_not_selected)
    Box(
        Modifier
            .size(SwatchSize)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                shape = CircleShape,
            )
            .padding(if (selected) 5.dp else 2.dp)
            .background(fill, CircleShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics {
                contentDescription = description
                stateDescription = selectedState
            },
        contentAlignment = Alignment.Center,
    ) {
        when {
            selected -> Icon(painterResource(R.drawable.ic_settings_check), null, Modifier.size(20.dp), tint = checkTint)
            showPlus -> Icon(painterResource(R.drawable.ic_action_add), null, Modifier.size(20.dp), tint = Color.White)
        }
    }
}

/** HSV picker for an accent outside the presets. */
@Composable
private fun CustomAccentDialog(initial: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val start = FloatArray(3).also { android.graphics.Color.colorToHSV(initial, it) }
    var hue by rememberSaveable { mutableFloatStateOf(start[0]) }
    var saturation by rememberSaveable { mutableFloatStateOf(start[1]) }
    var value by rememberSaveable { mutableFloatStateOf(start[2]) }
    val color = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.accent_custom_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AccentThemeLivePreview(color)
                Column(Modifier.heightIn(max = (LocalConfiguration.current.screenHeightDp * .3f).dp)
                    .verticalScroll(rememberScrollState())) {
                AccentSlider(
                    label = stringResource(R.string.accent_hue),
                    value = hue,
                    range = 0f..360f,
                    track = Brush.horizontalGradient(HueStops),
                    onChange = { hue = it },
                )
                AccentSlider(
                    label = stringResource(R.string.accent_saturation),
                    value = saturation,
                    range = 0f..1f,
                    track = Brush.horizontalGradient(
                        listOf(hsv(hue, 0f, value), hsv(hue, 1f, value)),
                    ),
                    onChange = { saturation = it },
                )
                AccentSlider(
                    label = stringResource(R.string.accent_brightness),
                    value = value,
                    range = 0f..1f,
                    track = Brush.horizontalGradient(
                        listOf(hsv(hue, saturation, 0f), hsv(hue, saturation, 1f)),
                    ),
                    onChange = { value = it },
                )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(color) }) { Text(stringResource(R.string.settings_done)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) } },
    )
}

/** The same theme generator as Settings, applied only to decorative sample controls until Done. */
@Composable
internal fun AccentThemeLivePreview(color: Int) {
    val description = stringResource(R.string.preview_accent_description, hex(color))
    ChikkuSettingsTheme(accent = color) {
        val colors = MaterialTheme.colorScheme
        Surface(Modifier.fillMaxWidth().height(112.dp).clearAndSetSemantics { contentDescription = description },
            color = colors.surface, shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.SpaceEvenly) {
                Text(stringResource(R.string.preview_accent_label) + " · " + hex(color),
                    style = MaterialTheme.typography.labelMedium, color = colors.onSurface, maxLines = 1)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = colors.primary, contentColor = colors.onPrimary, shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.preview_accent_button), Modifier.padding(8.dp),
                            style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                    Switch(checked = true, onCheckedChange = null)
                }
            }
        }
    }
}

@Composable
private fun AccentSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    track: Brush,
    onChange: (Float) -> Unit,
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .height(10.dp)
                    .background(track, CircleShape),
            )
            Slider(
                value = value,
                onValueChange = onChange,
                valueRange = range,
                modifier = Modifier.semantics { contentDescription = label },
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent,
                ),
            )
        }
    }
}

private val HueStops = listOf(0f, 60f, 120f, 180f, 240f, 300f, 360f).map { hsv(it, 1f, 1f) }

private fun hsv(h: Float, s: Float, v: Float) = Color(android.graphics.Color.HSVToColor(floatArrayOf(h, s, v)))

private fun checkTint(fill: Color) = if (fill.luminance() > 0.4f) Color.Black else Color.White

private fun hex(argb: Int) = "#%06X".format(Color(argb).toArgb() and 0xFFFFFF)
