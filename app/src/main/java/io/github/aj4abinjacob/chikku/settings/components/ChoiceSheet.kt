package io.github.aj4abinjacob.chikku.settings.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.aj4abinjacob.chikku.R
import kotlinx.coroutines.launch

/**
 * The bottom sheet settings pickers share, styled like the other settings sheets (gesture and card
 * action pickers): full height on open, default drag handle and container colour, a titleLarge
 * header, and content capped at 80% of the window so long lists scroll inside the sheet.
 *
 * [content] gets a `close` callback that slides the sheet away and then calls [onDismiss]; swipe
 * down, back and a scrim tap dismiss it too, so a picker needs no Cancel button.
 *
 * A sheet with pages passes [onBack] while it shows a sub-page: the header then leads with a back
 * arrow, and system back steps back a page (calls [onBack]) instead of dismissing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsBottomSheet(
    title: String,
    onDismiss: () -> Unit,
    onBack: (() -> Unit)? = null,
    content: @Composable ColumnScope.(close: () -> Unit) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val close: () -> Unit = {
        scope.launch { sheetState.hide() }.invokeOnCompletion { if (!sheetState.isVisible) onDismiss() }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        BackHandler(enabled = onBack != null) { onBack?.invoke() }
        SettingsSheetBody(title, onBack) { content(close) }
    }
}

/** Header and height cap of [SettingsBottomSheet], without the sheet window (so tests can draw it). */
@Composable
internal fun SettingsSheetBody(title: String, content: @Composable ColumnScope.() -> Unit) =
    SettingsSheetBody(title, onBack = null, content)

/** [SettingsSheetBody] whose header leads with a back arrow calling [onBack], when it is set. */
@Composable
internal fun SettingsSheetBody(title: String, onBack: (() -> Unit)?, content: @Composable ColumnScope.() -> Unit) {
    val maxHeight = with(LocalDensity.current) { (LocalWindowInfo.current.containerSize.height * SheetMaxHeightFraction).toDp() }
    Column(Modifier.fillMaxWidth().heightIn(max = maxHeight).padding(bottom = 16.dp)) {
        val heading = Modifier.semantics { heading() }
        if (onBack == null) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = SheetPadding, end = SheetPadding, bottom = 12.dp).then(heading),
            )
        } else {
            // The arrow's 48dp button starts at SheetListPadding, so its glyph sits where the title would start.
            Row(
                Modifier.fillMaxWidth().padding(start = SheetListPadding, end = SheetPadding, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(painterResource(R.drawable.ic_settings_back), contentDescription = stringResource(R.string.gesture_picker_back))
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).then(heading),
                )
            }
        }
        content()
    }
}

/**
 * Single choice from [options] in a [SettingsBottomSheet]. Tapping an option calls [onSelect] and
 * closes the sheet; it opens with the current choice in view. The rows are the gesture picker's
 * ([GestureActionRow]): tinted selection with a check mark. [optionSummary] adds a line under each
 * option, [optionIcon] a leading icon, [optionSection] a heading before each run of options in a
 * section, and [optionTextStyle] draws a label in its own style (e.g. a font in that font). With
 * [optionPreview] the options show as a grid of thumbnails instead.
 */
@Composable
internal fun <T> ChoiceSheet(
    title: String,
    options: List<T>,
    selected: T,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    optionSummary: (@Composable (T) -> String?)? = null,
    optionIcon: (@Composable (T) -> Unit)? = null,
    optionSection: (@Composable (T) -> String?)? = null,
    optionTextStyle: (@Composable (T) -> TextStyle?)? = null,
    optionPreview: (@Composable (T) -> Unit)? = null,
) {
    SettingsBottomSheet(title, onDismiss) { close ->
        ChoiceSheetOptions(
            options, selected, optionLabel,
            onPick = { option ->
                onSelect(option)
                close()
            },
            optionSummary, optionIcon, optionSection, optionTextStyle, optionPreview,
        )
    }
}

/** The options of a [ChoiceSheet]: a list, or a thumbnail grid when [optionPreview] is set. */
@Composable
internal fun <T> ColumnScope.ChoiceSheetOptions(
    options: List<T>,
    selected: T,
    optionLabel: @Composable (T) -> String,
    onPick: (T) -> Unit,
    optionSummary: (@Composable (T) -> String?)? = null,
    optionIcon: (@Composable (T) -> Unit)? = null,
    optionSection: (@Composable (T) -> String?)? = null,
    optionTextStyle: (@Composable (T) -> TextStyle?)? = null,
    optionPreview: (@Composable (T) -> Unit)? = null,
) {
    // Fill what the content needs, up to the sheet's cap; the list or grid scrolls past that.
    val scrolling = Modifier.weight(1f, fill = false).fillMaxWidth()
    if (optionPreview != null) {
        Box(scrolling.padding(horizontal = SheetPadding)) {
            ChoicePreviewGrid(options, selected, optionLabel, optionSummary, optionPreview, onPick)
        }
        return
    }
    val sections = options.map { optionSection?.invoke(it) }
    val entries = remember(options, sections) { choiceEntries(options) { sections[options.indexOf(it)] } }
    val selectedIndex = entries.indexOfFirst { it is ChoiceEntry.Option && it.option == selected }
    // Open with the current choice in view, one row of context above it (clamped when all fit).
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (selectedIndex - 1).coerceAtLeast(0))
    LazyColumn(
        scrolling.selectableGroup(),
        state = listState,
        contentPadding = PaddingValues(horizontal = SheetListPadding),
    ) {
        items(entries.size) { i ->
            when (val entry = entries[i]) {
                is ChoiceEntry.Heading -> GestureActionSectionLabel(entry.title)
                is ChoiceEntry.Option -> GestureActionRow(
                    text = optionLabel(entry.option),
                    selected = entry.option == selected,
                    supporting = optionSummary?.invoke(entry.option),
                    leading = optionIcon?.let { icon -> { icon(entry.option) } },
                    textStyle = optionTextStyle?.invoke(entry.option),
                ) { onPick(entry.option) }
            }
        }
    }
}

/** One list entry of a choice sheet: a section heading or an option. */
internal sealed interface ChoiceEntry<out T> {
    data class Heading(val title: String) : ChoiceEntry<Nothing>
    data class Option<T>(val index: Int, val option: T) : ChoiceEntry<T>
}

/** Options in order, with a heading before each run of options whose section changes (null = none). */
internal fun <T> choiceEntries(options: List<T>, sectionOf: (T) -> String?): List<ChoiceEntry<T>> = buildList {
    var last: String? = null
    options.forEachIndexed { index, option ->
        val section = sectionOf(option)
        if (section != null && section != last) add(ChoiceEntry.Heading(section))
        last = section
        add(ChoiceEntry.Option(index, option))
    }
}

/*
 * Horizontal insets shared by every settings sheet (and the gesture and folder picker pages), so
 * titles, rows, labels and search fields line up the same way whichever sheet is open:
 *
 *   |<-12->| row highlight, search field, other full-width controls ............ |<-12->|
 *   |<----24---->| title, row icon or text, section label, info text
 *
 * Rows ([GestureActionRow]) carry their own 12dp inner padding, so a list of them sits in a
 * [SheetListPadding] container and its icons and labels land on [SheetPadding], under the title.
 * The search field's icon lands there too (it sits centred in a 48dp slot at the field's start).
 */

/** Side padding of sheet titles and of plain (not row) content such as info text. */
internal val SheetPadding = 24.dp

/** Side padding of sheet lists, search fields and other controls with their own edge or highlight. */
internal val SheetListPadding = 12.dp

/** [AppPickerList] / [AppPickerRow] inset that puts app icons and labels on [SheetPadding] in a [SheetListPadding] list. */
internal val SheetAppListInset = SheetPadding - SheetListPadding - AppPickerRowPadding

/**
 * Remembers the height a searchable sheet list had while unfiltered, for [keepUnfilteredHeight].
 * A plain field (not snapshot state): it is written and read only during measurement.
 */
internal class UnfilteredHeight {
    var px = 0
}

@Composable
internal fun rememberUnfilteredHeight(): UnfilteredHeight = remember { UnfilteredHeight() }

/**
 * Lets a searchable sheet list hug its rows (so a short list leaves no gap below its last row) while
 * not shrinking below the height it had unfiltered once [filtering] starts, so the sheet does not
 * jump, and its search field does not move, as rows drop out. Clearing the query lets it hug again.
 */
internal fun Modifier.keepUnfilteredHeight(height: UnfilteredHeight, filtering: Boolean): Modifier =
    layout { measurable, constraints ->
        val min = if (filtering) height.px.coerceIn(constraints.minHeight, constraints.maxHeight) else constraints.minHeight
        val placeable = measurable.measure(constraints.copy(minHeight = min))
        if (!filtering) height.px = placeable.height
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }

private const val SheetMaxHeightFraction = 0.8f
