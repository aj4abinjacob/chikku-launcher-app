package io.github.aj4abinjacob.chikku.settings.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.aj4abinjacob.chikku.R
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/*
 * Choice dialogs with thumbnails. Previews are schematic drawings in theme colours: they show
 * where and how an option changes the launcher without loading wallpapers or the app inventory.
 */

/** Two-column grid of selectable tiles: thumbnail, label and optional one-line hint. */
@Composable
internal fun <T> ChoicePreviewGrid(
    options: List<T>,
    selected: T,
    optionLabel: @Composable (T) -> String,
    optionSummary: (@Composable (T) -> String?)?,
    optionPreview: @Composable (T) -> Unit,
    onPick: (T) -> Unit,
) {
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        options.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { option ->
                    ChoiceTile(
                        label = optionLabel(option),
                        summary = optionSummary?.invoke(option),
                        selected = option == selected,
                        onClick = { onPick(option) },
                        modifier = Modifier.weight(1f),
                    ) { optionPreview(option) }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/**
 * A few choices drawn inline as thumbnail tiles under the row title, for settings whose options are
 * easier to see than to name. One tap selects; there is no dialog, so the choice stays in view.
 * Keep it to two to four options with short labels so the tiles fit a phone width.
 */
@Composable
fun <T> SettingsTileChoiceRow(
    title: String,
    options: List<T>,
    selected: T,
    optionLabel: @Composable (T) -> String,
    optionPreview: @Composable (T) -> Unit,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    optionSummary: (@Composable (T) -> String?)? = null,
) {
    Column(
        modifier
            .cardSegment()
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                ChoiceTile(
                    label = optionLabel(option),
                    summary = optionSummary?.invoke(option),
                    selected = option == selected,
                    onClick = { if (option != selected) onSelect(option) },
                    modifier = Modifier.weight(1f),
                    previewHeight = InlinePreviewHeight,
                ) { optionPreview(option) }
            }
        }
    }
}

@Composable
private fun ChoiceTile(
    label: String,
    summary: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    previewHeight: Dp = PreviewHeight,
    preview: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(previewHeight)
                .clip(shape)
                .background(if (selected) colors.secondaryContainer else colors.surfaceContainerHighest)
                .border(if (selected) 2.dp else 1.dp, if (selected) colors.primary else colors.outlineVariant, shape)
                .clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.fillMaxSize().padding(10.dp), contentAlignment = Alignment.Center) { preview() }
            if (selected) {
                Icon(
                    painterResource(R.drawable.ic_settings_check),
                    contentDescription = null,
                    tint = colors.onPrimary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(18.dp)
                        .background(colors.primary, CircleShape)
                        .padding(3.dp),
                )
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) colors.primary else colors.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp),
        )
        if (summary != null) {
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp, bottom = 4.dp),
            )
        }
    }
}

private val PreviewHeight = 104.dp
private val InlinePreviewHeight = 72.dp

/** Theme colours for schematic previews. */
@Immutable
class PreviewInk(
    val frame: Color,
    val screen: Color,
    val panel: Color,
    val muted: Color,
    val faint: Color,
    val accent: Color,
    val onAccent: Color,
    val alert: Color,
)

@Composable
fun previewInk(): PreviewInk = MaterialTheme.colorScheme.let {
    PreviewInk(
        frame = it.outline,
        screen = it.surface,
        panel = it.onSurface.copy(alpha = 0.16f),
        muted = it.onSurfaceVariant.copy(alpha = 0.6f),
        faint = it.onSurfaceVariant.copy(alpha = 0.25f),
        accent = it.primary,
        onAccent = it.onPrimary,
        alert = it.error,
    )
}

/** A canvas filling the tile's thumbnail area; decorative, so it has no semantics of its own. */
@Composable
fun PreviewCanvas(modifier: Modifier = Modifier, draw: DrawScope.(PreviewInk) -> Unit) {
    val ink = previewInk()
    Canvas(modifier.fillMaxSize()) { draw(ink) }
}

/** Draws a phone outline centred in the canvas and returns its screen area. */
fun DrawScope.phone(ink: PreviewInk): Rect {
    val h = size.height
    val w = minOf(size.width, h * 0.58f)
    val left = (size.width - w) / 2f
    val r = w * 0.16f
    drawRoundRect(ink.frame, Offset(left, 0f), Size(w, h), CornerRadius(r))
    val b = w * 0.05f
    val screen = Rect(left + b, b, left + w - b, h - b)
    drawRoundRect(ink.screen, screen.topLeft, screen.size, CornerRadius(r - b))
    return screen
}

/** [cols] x [rows] dots evenly spread in [area]. */
fun DrawScope.dotGrid(area: Rect, cols: Int, rows: Int, color: Color, fill: Float = 0.62f) {
    val cellW = area.width / cols
    val cellH = area.height / rows
    val radius = minOf(cellW, cellH) * fill / 2f
    for (row in 0 until rows) for (col in 0 until cols) {
        drawCircle(color, radius, Offset(area.left + cellW * (col + 0.5f), area.top + cellH * (row + 0.5f)))
    }
}

/** A horizontal capsule. */
fun DrawScope.pill(area: Rect, color: Color) =
    drawRoundRect(color, area.topLeft, area.size, CornerRadius(area.height / 2f))

fun DrawScope.roundRect(area: Rect, color: Color, radius: Float) =
    drawRoundRect(color, area.topLeft, area.size, CornerRadius(radius))

/** A line with an arrow head at [to]. */
fun DrawScope.arrow(from: Offset, to: Offset, color: Color, width: Float) {
    drawLine(color, from, to, width, StrokeCap.Round)
    val angle = atan2(to.y - from.y, to.x - from.x)
    val head = width * 2.6f
    val path = Path().apply {
        moveTo(to.x + cos(angle - 2.5f) * head, to.y + sin(angle - 2.5f) * head)
        lineTo(to.x, to.y)
        lineTo(to.x + cos(angle + 2.5f) * head, to.y + sin(angle + 2.5f) * head)
    }
    drawPath(path, color, style = Stroke(width, cap = StrokeCap.Round))
}

/** Thin top strip and a row of four dock dots: the generic home-screen chrome. */
fun DrawScope.dockRow(screen: Rect, color: Color): Rect {
    val unit = screen.width / 10f
    val dock = Rect(screen.left + unit, screen.bottom - unit * 3.2f, screen.right - unit, screen.bottom - unit * 1.2f)
    dotGrid(dock, 4, 1, color, fill = 0.7f)
    return dock
}
