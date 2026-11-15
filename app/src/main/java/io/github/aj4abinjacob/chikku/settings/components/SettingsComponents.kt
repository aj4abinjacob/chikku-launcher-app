package io.github.aj4abinjacob.chikku.settings.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Stable
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import kotlinx.coroutines.flow.first
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aj4abinjacob.chikku.R
import kotlin.math.roundToInt

/*
 * Shared settings building blocks. Every row is at least 56dp tall, uses Material type (sp, so it
 * follows font scale) and exposes a single accessible node with its role and state.
 *
 * Settings use compact, flat preference lists with muted section labels and selected-value
 * dialogs. The shared segment infrastructure also works for lazy lists and previews.
 */

private val RowMinHeight = 56.dp
/** Vertical inset of a row's text: two lines of text plus this stays near [RowMinHeight]. */
private val RowVerticalPadding = 8.dp
private val HorizontalPadding = 16.dp
private val RowPadding = 16.dp
private val CardMargin = 0.dp
private val SegmentGap = 0.dp
private val OuterCorner = 0.dp
private val InnerCorner = 0.dp

private val SoloShape = RoundedCornerShape(OuterCorner)
private val TopShape = RoundedCornerShape(OuterCorner, OuterCorner, InnerCorner, InnerCorner)
private val MiddleShape = RoundedCornerShape(InnerCorner)
private val BottomShape = RoundedCornerShape(InnerCorner, InnerCorner, OuterCorner, OuterCorner)

/** Card segment state of one row; [SettingsPage] assigns [shape] by the row's position in its run. */
@Stable
private class CardSlot {
    var shape: Shape? by mutableStateOf(null)
}

/** Tags a [SettingsSectionHeader] so [SettingsPage] can drop its leading gap when it opens the page. */
private object SectionHeaderSlot

private val PageTopPadding = 4.dp
private val SectionHeaderGap = 8.dp

/** Background of settings pages. */
@Composable
fun settingsPageColor(): Color =
    MaterialTheme.colorScheme.surface

/** Fill of card segments, a step above [settingsPageColor]. */
@Composable
fun settingsCardColor(): Color =
    MaterialTheme.colorScheme.surface

/** Marks a row as a card segment: tagged for [SettingsPage], clipped and filled once it has a shape. */
@Composable
internal fun Modifier.cardSegment(): Modifier {
    LocalSegmentPosition.current?.let { (index, count) -> return settingsSegment(index, count) }
    val slot = remember { CardSlot() }
    val color = settingsCardColor()
    return this
        .layoutId(slot)
        .graphicsLayer {
            val shape = slot.shape
            if (shape != null) {
                this.shape = shape
                clip = true
            }
        }
        .drawBehind { if (slot.shape != null) drawRect(color) }
}

private fun segmentShape(first: Boolean, last: Boolean): Shape = when {
    first && last -> SoloShape
    first -> TopShape
    last -> BottomShape
    else -> MiddleShape
}

/** Position of the enclosing [SettingsListSegment] item in its card, for rows inside lazy lists. */
private val LocalSegmentPosition = compositionLocalOf<Pair<Int, Int>?> { null }

/**
 * Card segment for item [index] of [count] in a lazy list, where [SettingsPage] can't group rows.
 * Applies the card margin, gap, shape and fill.
 */
@Composable
fun Modifier.settingsSegment(index: Int, count: Int): Modifier =
    this
        .padding(start = CardMargin, end = CardMargin, top = if (index > 0) SegmentGap else 0.dp)
        .clip(segmentShape(index == 0, index == count - 1))
        .background(settingsCardColor())

/** Draws the standard rows in [content] as segment [index] of a [count]-row card inside a lazy list. */
@Composable
fun SettingsListSegment(index: Int, count: Int, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalSegmentPosition provides (index to count), content = content)
}

/**
 * Scrollable body of a settings section. Sections that need a LazyColumn should not use this.
 * Groups consecutive rows into cards; everything else spans the page.
 */
@Composable
fun SettingsPage(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(
        content = content,
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) { measurables, constraints ->
        val margin = CardMargin.roundToPx()
        val gap = SegmentGap.roundToPx()
        val loose = constraints.copy(minWidth = 0, minHeight = 0, maxHeight = Constraints.Infinity)
        val cardConstraints = loose.copy(maxWidth = (loose.maxWidth - 2 * margin).coerceAtLeast(0))
        val slots = measurables.map { it.layoutId as? CardSlot }
        val placeables = measurables.mapIndexed { i, m -> m.measure(if (slots[i] != null) cardConstraints else loose) }
        val ys = IntArray(placeables.size)
        // A leading section header sits flush under the top bar; its gap only separates groups.
        val startsWithHeader = measurables.firstOrNull()?.layoutId === SectionHeaderSlot
        var y = if (startsWithHeader) -SectionHeaderGap.roundToPx() else PageTopPadding.roundToPx()
        placeables.forEachIndexed { i, p ->
            if (i > 0 && slots[i] != null && slots[i - 1] != null) y += gap
            ys[i] = y
            y += p.height
        }
        // Report at least the minimum height: a smaller size would be centered by the parent.
        layout(constraints.maxWidth, y.coerceAtLeast(0).coerceAtLeast(constraints.minHeight)) {
            placeables.forEachIndexed { i, p ->
                val slot = slots[i]
                if (slot != null) {
                    val first = slots.getOrNull(i - 1) == null
                    val last = slots.getOrNull(i + 1) == null
                    slot.shape = segmentShape(first, last)
                }
                p.place(if (slot != null) margin else 0, ys[i])
            }
        }
    }
}

/**
 * Group label: a hairline and a small muted caption, so groups read as separate without a heavy
 * band. A softened accent keeps it apart from row titles without competing with icons and controls.
 */
@Composable
fun SettingsSectionHeader(title: String, modifier: Modifier = Modifier) {
    val divider = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        // A blue-grey between the muted text color and the accent: distinct from titles, not loud.
        color = lerp(MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.primary, 0.45f).copy(alpha = 0.85f),
        modifier = modifier
            .layoutId(SectionHeaderSlot)
            .fillMaxWidth()
            // Line at the top of the gap: a header opening the page is shifted up by the gap, so
            // its line is scrolled out of view instead of doubling as a top bar border.
            .drawBehind { drawRect(divider, size = size.copy(height = 1.dp.toPx())) }
            .padding(top = SectionHeaderGap)
            .padding(start = RowPadding, end = HorizontalPadding, top = 14.dp, bottom = 4.dp)
            .semantics { heading() },
    )
}

/**
 * Explanatory footnote, e.g. why a permission is needed. Kept small so controls stay the focus;
 * longer [details] stay folded behind "Learn more" until asked for.
 */
@Composable
fun SettingsInfoText(text: String, modifier: Modifier = Modifier, details: String? = null) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = CardMargin + RowPadding, end = CardMargin + RowPadding, top = 10.dp, bottom = 6.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_settings_info),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 1.dp, end = 10.dp).size(16.dp),
        )
        Column(Modifier.animateContentSize()) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (details != null) {
                if (expanded) {
                    Text(
                        text = details,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                Text(
                    text = stringResource(if (expanded) R.string.settings_show_less else R.string.settings_learn_more),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clickable(role = Role.Button) { expanded = !expanded }
                        .wrapContentHeight(Alignment.CenterVertically),
                )
            }
        }
    }
}

/**
 * A row's leading icon: what the row is about at a glance. Same size and tint as the section icons
 * on the settings home, so main pages read as one list. Decorative; the title says it in words.
 */
@Composable
private fun RowIcon(@DrawableRes icon: Int) {
    Box(Modifier.size(SettingsRowIconBox), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(SettingsRowIconSize))
    }
    Spacer(Modifier.width(16.dp))
}

/** Leading icon slot and glyph size, shared with the section icons on the settings home. */
val SettingsRowIconBox = 32.dp
val SettingsRowIconSize = 20.dp

/** Width of [RowIcon] and its gap, so a control below the title (a slider track) lines up with it. */
private val RowIconInset = SettingsRowIconBox + 16.dp

/** Row descriptions: dimmer than titles so names lead, still above 4.5:1 on the page surface. */
@Composable
fun settingsSummaryColor(): Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)

@Composable
private fun RowTexts(title: String, summary: String?, modifier: Modifier = Modifier) {
    // Tighter line heights than the type scale's defaults keep name and description together.
    Column(modifier = modifier) {
        Text(title, style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 21.sp))
        if (summary != null) {
            Text(summary, style = MaterialTheme.typography.bodySmall.copy(lineHeight = 16.sp), color = settingsSummaryColor())
        }
    }
}

@Composable
fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
    @DrawableRes icon: Int? = null,
) {
    Row(
        modifier = modifier
            .cardSegment()
            .fillMaxWidth()
            .heightIn(min = RowMinHeight)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .padding(horizontal = RowPadding, vertical = RowVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) RowIcon(icon)
        RowTexts(title, summary, Modifier.weight(1f))
        Spacer(Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            thumbContent = if (checked) {
                {
                    // The default check color (onPrimaryContainer) can equal the thumb (onPrimary) with a custom accent.
                    Icon(painterResource(R.drawable.ic_settings_check), null, Modifier.size(SwitchDefaults.IconSize),
                        tint = MaterialTheme.colorScheme.primary)
                }
            } else {
                null
            },
        )
    }
}

/** Read-only row inside a card, e.g. a status line. */
@Composable
fun SettingsTextRow(title: String, modifier: Modifier = Modifier, summary: String? = null, @DrawableRes icon: Int? = null) {
    Row(
        modifier = modifier
            .cardSegment()
            .fillMaxWidth()
            .heightIn(min = RowMinHeight)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = RowPadding, vertical = RowVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) RowIcon(icon)
        RowTexts(title, summary, Modifier.weight(1f))
    }
}

/** Opens another screen, page or dialog; [leading] is an optional icon slot, [value] a current-value pill. */
@Composable
fun SettingsNavigationRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    value: String? = null,
    @DrawableRes icon: Int? = null,
) {
    Row(
        modifier = modifier
            .cardSegment()
            .fillMaxWidth()
            .heightIn(min = RowMinHeight)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .padding(horizontal = RowPadding, vertical = RowVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(16.dp))
        } else if (icon != null) {
            RowIcon(icon)
        }
        RowTexts(title, summary, Modifier.weight(1f))
        if (value != null) {
            Spacer(Modifier.width(16.dp))
            ValuePill(value)
        }
        if (trailing != null) {
            Spacer(Modifier.width(16.dp))
            trailing()
        }
    }
}

/** Small tonal pill showing a row's current value. */
@Composable
private fun ValuePill(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .widthIn(max = 200.dp)
            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/**
 * Slider that reports [onValueChangeFinished] once the drag ends, so storage is written once per
 * gesture instead of per frame.
 */
@Composable
fun SettingsSliderRow(
    title: String,
    value: Float,
    onValueChangeFinished: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    valueLabel: (Float) -> String,
    enabled: Boolean = true,
    /** Called with every value while dragging, for a live preview; the setting is saved on release. */
    onValueChanging: ((Float) -> Unit)? = null,
    /** Words for the two ends of the track (e.g. Subtle, Full) when a number alone doesn't say which way is which. */
    rangeLabels: Pair<String, String>? = null,
    @DrawableRes icon: Int? = null,
) {
    var dragging by remember { mutableStateOf(false) }
    var local by remember { mutableFloatStateOf(value) }
    val shown = if (dragging) local else value
    Column(
        modifier = modifier
            .cardSegment()
            .fillMaxWidth()
            .heightIn(min = RowMinHeight)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .padding(start = RowPadding, end = RowPadding, top = 10.dp, bottom = 2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) RowIcon(icon)
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            ValuePill(valueLabel(shown))
        }
        val inset = if (icon != null) RowIconInset else 0.dp
        Slider(
            value = shown,
            onValueChange = {
                dragging = true
                local = it
                onValueChanging?.invoke(it)
            },
            onValueChangeFinished = {
                dragging = false
                onValueChangeFinished(local)
            },
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
            modifier = Modifier.padding(start = inset).semantics {
                contentDescription = title
                stateDescription = valueLabel(shown)
            },
        )
        if (rangeLabels != null) {
            // Decorative: the slider's own description and value already say this to TalkBack.
            Row(Modifier.fillMaxWidth().padding(start = inset, bottom = 10.dp).clearAndSetSemantics {}) {
                Text(rangeLabels.first, style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text(rangeLabels.second, style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Integer slider, e.g. grid rows/columns or dock icon count. */
@Composable
fun SettingsIntSliderRow(
    title: String,
    value: Int,
    onValueChangeFinished: (Int) -> Unit,
    range: IntRange,
    modifier: Modifier = Modifier,
    valueLabel: (Int) -> String = { it.toString() },
    enabled: Boolean = true,
    onValueChanging: ((Int) -> Unit)? = null,
    rangeLabels: Pair<String, String>? = null,
    @DrawableRes icon: Int? = null,
) {
    SettingsSliderRow(
        title = title,
        value = value.toFloat(),
        onValueChangeFinished = { onValueChangeFinished(it.roundToInt().coerceIn(range)) },
        valueRange = range.first.toFloat()..range.last.toFloat(),
        modifier = modifier,
        steps = (range.last - range.first - 1).coerceAtLeast(0),
        valueLabel = { valueLabel(it.roundToInt()) },
        enabled = enabled,
        onValueChanging = onValueChanging?.let { live -> { live(it.roundToInt().coerceIn(range)) } },
        rangeLabels = rangeLabels,
        icon = icon,
    )
}

/**
 * Stepped slider over ordered [options] (sizes, spacings, durations), so a value is one drag or tap
 * instead of a dialog. The pill shows the full [optionLabel] of the step under the thumb.
 */
@Composable
fun <T> SettingsStepRow(
    title: String,
    options: List<T>,
    selected: T,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValueChanging: ((T) -> Unit)? = null,
    @DrawableRes icon: Int? = null,
) {
    if (options.isEmpty()) return
    val labels = options.map { optionLabel(it) }
    val last = options.lastIndex
    SettingsIntSliderRow(
        title = title,
        value = options.indexOf(selected).coerceAtLeast(0),
        onValueChangeFinished = { index -> options[index].let { if (it != selected) onSelect(it) } },
        range = 0..last,
        modifier = modifier,
        valueLabel = { labels[it.coerceIn(0, last)] },
        enabled = enabled && last > 0,
        onValueChanging = onValueChanging?.let { live -> { index -> live(options[index.coerceIn(0, last)]) } },
        icon = icon,
    )
}

/**
 * Single choice from [options]. A few short options show inline as segmented buttons; longer lists
 * open a [ChoiceSheet], with the current choice in a pill. Set [inlineChoices] to false for dynamic
 * lists whose size can change, such as installed icon packs. [optionSummary] adds a one-line hint
 * under each option; [optionPreview] turns the sheet into a grid of thumbnails showing what each does.
 *
 * The sheet uses the same rows as the gesture action picker ([GestureActionRow]): tinted
 * selection with a check mark, opened with the current choice in view. [optionIcon] adds a leading
 * icon, [optionSection] groups consecutive options under a heading, and [optionTextStyle] draws an
 * option's label in its own style (e.g. a font choice shown in that font).
 */
@Composable
fun <T> SettingsChoiceRow(
    title: String,
    options: List<T>,
    selected: T,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    inlineChoices: Boolean = false,
    optionSummary: (@Composable (T) -> String?)? = null,
    optionPreview: (@Composable (T) -> Unit)? = null,
    @DrawableRes icon: Int? = null,
    optionIcon: (@Composable (T) -> Unit)? = null,
    optionSection: (@Composable (T) -> String?)? = null,
    optionTextStyle: (@Composable (T) -> TextStyle?)? = null,
) {
    val labels = options.map { optionLabel(it) }
    val decorated = optionSummary != null || optionPreview != null || optionIcon != null || optionSection != null || optionTextStyle != null
    if (inlineChoices && !decorated && fitsSegmented(labels)) {
        SegmentedChoiceRow(title, options, labels, selected, onSelect, modifier, enabled, icon)
        return
    }
    var open by rememberSaveable { mutableStateOf(false) }
    val selectedLabel = optionLabel(selected)
    SettingsNavigationRow(
        title = title,
        onClick = { open = true },
        modifier = modifier.semantics { stateDescription = selectedLabel },
        enabled = enabled,
        trailing = { ValuePill(selectedLabel) },
        icon = icon,
    )
    if (open) {
        ChoiceSheet(
            title = title,
            options = options,
            selected = selected,
            optionLabel = optionLabel,
            onSelect = onSelect,
            onDismiss = { open = false },
            optionSummary = optionSummary,
            optionIcon = optionIcon,
            optionSection = optionSection,
            optionTextStyle = optionTextStyle,
            optionPreview = optionPreview,
        )
    }
}

/** Segments get cramped past these: up to 4 options, each label short enough for its share of a phone width. */
private fun fitsSegmented(labels: List<String>): Boolean {
    val maxChars = when (labels.size) {
        2 -> 16
        3 -> 11
        4 -> 8
        else -> return false
    }
    return labels.all { it.length <= maxChars }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> SegmentedChoiceRow(
    title: String,
    options: List<T>,
    labels: List<String>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    @DrawableRes icon: Int?,
) {
    Column(
        modifier = modifier
            .cardSegment()
            .fillMaxWidth()
            .heightIn(min = RowMinHeight)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .padding(start = RowPadding, end = RowPadding, top = 10.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) RowIcon(icon)
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.semantics { heading() })
        }
        Spacer(Modifier.height(12.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(start = if (icon != null) RowIconInset else 0.dp)) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == selected,
                    onClick = { if (option != selected) onSelect(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    enabled = enabled,
                    icon = {},
                    label = { Text(labels[index], maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            }
        }
    }
}

/** ARGB swatches offered by [SettingsColorRow]. */
val DefaultSettingsPalette: List<Int> = listOf(
    0xFF000000, 0xFF212121, 0xFF616161, 0xFFFFFFFF, 0xFFB71C1C, 0xFFE91E63, 0xFF9C27B0, 0xFF3F51B5,
    0xFF2196F3, 0xFF009688, 0xFF4CAF50, 0xFFCDDC39, 0xFFFFC107, 0xFFFF5722, 0xFF795548,
).map { it.toInt() }

/**
 * Pick a color from [palette] in a bottom sheet; with [allowDefault], a "Default" row above the
 * swatches maps to null (follow theme).
 */
@Composable
fun SettingsColorRow(
    title: String,
    color: Int?,
    onColorChange: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    palette: List<Int> = DefaultSettingsPalette,
    allowDefault: Boolean = true,
    enabled: Boolean = true,
    @DrawableRes icon: Int? = null,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val defaultLabel = stringResource(R.string.settings_color_default)
    Row(
        modifier = modifier
            .cardSegment()
            .fillMaxWidth()
            .heightIn(min = RowMinHeight)
            .clickable(enabled = enabled, role = Role.Button) { open = true }
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .padding(horizontal = RowPadding, vertical = RowVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) RowIcon(icon)
        RowTexts(title, if (color == null) defaultLabel else null, Modifier.weight(1f))
        Swatch(color, selected = false, size = 32.dp)
    }
    if (open) {
        SettingsBottomSheet(title, onDismiss = { open = false }) { close ->
            ColorSheetOptions(palette, color, allowDefault, defaultLabel) { picked ->
                onColorChange(picked)
                close()
            }
        }
    }
}

/** Contents of the [SettingsColorRow] sheet: a "Default" row (when allowed) above the swatch grid. */
@Composable
internal fun ColumnScope.ColorSheetOptions(
    palette: List<Int>,
    color: Int?,
    allowDefault: Boolean,
    defaultLabel: String,
    onPick: (Int?) -> Unit,
) {
    Column(
        Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState()).selectableGroup(),
    ) {
        if (allowDefault) {
            Box(Modifier.padding(horizontal = SheetListPadding)) {
                GestureActionRow(
                    text = defaultLabel,
                    selected = color == null,
                    leading = { Swatch(null, selected = false, size = 28.dp) },
                ) { onPick(null) }
            }
            Spacer(Modifier.height(12.dp))
        }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = SheetPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            palette.chunked(SWATCHES_PER_ROW).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    row.forEach { swatch ->
                        Box(
                            Modifier
                                .clip(CircleShape)
                                .selectable(selected = swatch == color, role = Role.RadioButton) { onPick(swatch) }
                                .semantics { contentDescription = colorName(swatch) },
                        ) { Swatch(swatch, selected = swatch == color, size = 48.dp) }
                    }
                    // Keep a short last row on the same columns as the rows above.
                    repeat(SWATCHES_PER_ROW - row.size) { Spacer(Modifier.size(48.dp)) }
                }
            }
        }
    }
}

@Composable
private fun Swatch(color: Int?, selected: Boolean, size: Dp = 40.dp) {
    val outline = MaterialTheme.colorScheme.outlineVariant
    Box(
        Modifier
            .size(size)
            .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else outline, CircleShape)
            .padding(if (selected) 5.dp else 3.dp)
            .background(color?.let { Color(it) } ?: MaterialTheme.colorScheme.surfaceVariant, CircleShape),
    )
}

private fun colorName(argb: Int) = "#%08X".format(argb)

private const val DISABLED_ALPHA = 0.38f
private const val SWATCHES_PER_ROW = 5
