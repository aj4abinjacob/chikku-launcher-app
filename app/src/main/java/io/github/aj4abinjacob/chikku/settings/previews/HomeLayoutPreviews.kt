package io.github.aj4abinjacob.chikku.settings.previews

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.settings.PaddingSize
import io.github.aj4abinjacob.chikku.home.HomeLayoutMetrics
import io.github.aj4abinjacob.chikku.settings.components.dpValueLabel
import io.github.aj4abinjacob.chikku.settings.components.previewInk
import io.github.aj4abinjacob.chikku.settings.components.spacingLabel

/*
 * Pinned live previews for Settings › Home › Grid and › Widgets. Both draw generic shapes over the
 * same wallpaper gradient as the dock and icon previews; never the user's apps or widgets.
 */

/**
 * A miniature home page: [columns] × [rows] placeholder icons inside the edge space set by
 * [horizontalPadding] and [verticalPadding] (tinted, and drawn larger than life so small sizes stay
 * visible). The sample dock icons use [dockHorizontalPadding], or the home side space when it is
 * null, as the launcher's dock does. Fill the height you give it; the phone keeps its aspect ratio,
 * so the controls below never move while a slider is dragged.
 */
@Composable
fun HomeGridPreview(
    columns: Int,
    rows: Int,
    horizontalPadding: PaddingSize,
    verticalPadding: PaddingSize,
    modifier: Modifier = Modifier,
    showEdgeShadow: Boolean = false,
    dockHorizontalPadding: PaddingSize? = null,
    dockIconCount: Int = 5,
    iconScale: Float = 1f,
    labelScale: Float = 1f,
) {
    val ink = previewInk()
    val description = stringResource(R.string.settings_home_grid_preview, columns, rows) + ". " + stringResource(
        R.string.preview_fix_home_grid_edges,
        spacingLabel(horizontalPadding),
        spacingLabel(verticalPadding),
        spacingLabel(dockHorizontalPadding ?: horizontalPadding),
        stringResource(if (showEdgeShadow) R.string.settings_state_on else R.string.settings_state_off),
    )
    val presetDescription = stringResource(R.string.onboarding_layout_preview_details,
        dockIconCount, (iconScale * 100).toInt(), (labelScale * 100).toInt())
    Canvas(modifier.semantics { contentDescription = "$description. $presetDescription" }) {
        val screen = phoneScreen(ink.frame)
        drawRoundRect(wallpaperBrush(screen), screen.topLeft, screen.size, CornerRadius(screen.width * 0.1f))

        val unit = screen.width / 10f
        val scene = homeGridGeometry(screen, horizontalPadding, verticalPadding, dockHorizontalPadding)
        // Status strip and the dock: context only, so the page area reads as the home grid.
        drawRoundRect(Color.White.copy(alpha = 0.35f), Offset(screen.left + unit * 3.5f, screen.top + unit * 0.5f),
            Size(unit * 3f, unit * 0.35f), CornerRadius(unit))
        val dock = scene.dockBar
        drawRoundRect(Color.White.copy(alpha = 0.16f), dock.topLeft, dock.size, CornerRadius(unit * 0.8f))

        val page = scene.page
        val grid = scene.grid
        val h = grid.left - page.left
        val v = grid.top - page.top
        val band = ink.accent.copy(alpha = 0.45f)
        if (h > 0f) {
            drawRect(band, Offset(page.left, page.top), Size(h, page.height))
            drawRect(band, Offset(page.right - h, page.top), Size(h, page.height))
        }
        if (v > 0f) {
            drawRect(band, Offset(grid.left, page.top), Size(grid.width, v))
            drawRect(band, Offset(grid.left, page.bottom - v), Size(grid.width, v))
        }
        drawRect(Color.White.copy(alpha = 0.45f), grid.topLeft, grid.size,
            style = Stroke(width = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))))
        // The dock's side space, shaded like the page's, so following the home grid is visible.
        if (scene.dockSide > 0f) {
            drawRect(band, Offset(screen.left, dock.top), Size(scene.dockSide, dock.height))
            drawRect(band, Offset(screen.right - scene.dockSide, dock.top), Size(scene.dockSide, dock.height))
        }
        val dockCount = dockIconCount.coerceAtLeast(1)
        val dockWidth = (screen.width - scene.dockSide * 2).coerceAtLeast(1f)
        repeat(dockCount) { index ->
            val center = Offset(screen.left + scene.dockSide + dockWidth * (index + .5f) / dockCount, dock.center.y)
            drawCircle(SAMPLE_COLORS[index % SAMPLE_COLORS.size],
                minOf(scene.dockIconRadius * iconScale, dockWidth / dockCount * .4f), center)
        }

        val cols = columns.coerceAtLeast(1)
        val rowCount = rows.coerceAtLeast(1)
        val cellW = grid.width / cols
        val cellH = grid.height / rowCount
        val radius = minOf(cellW, cellH) * 0.3f * iconScale
        val showLabel = cellH > radius * 3.2f
        for (row in 0 until rowCount) for (col in 0 until cols) {
            val cx = grid.left + cellW * (col + 0.5f)
            val cy = grid.top + cellH * (row + 0.5f) - if (showLabel) radius * 0.45f else 0f
            drawCircle(SAMPLE_COLORS[(row * cols + col) % SAMPLE_COLORS.size], radius, Offset(cx, cy))
            if (showLabel) {
                val labelW = minOf(cellW * 0.9f, radius * 2.4f * labelScale)
                drawRoundRect(Color.White.copy(alpha = 0.6f), Offset(cx - labelW / 2f, cy + radius * 1.35f),
                    Size(labelW, radius * 0.4f), CornerRadius(radius))
            }
        }
        if (showEdgeShadow) {
            val shadeHeight = unit * 1.3f
            drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .65f), Color.Transparent),
                startY = screen.top, endY = screen.top + shadeHeight),
                screen.topLeft, Size(screen.width, shadeHeight))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .65f)),
                startY = screen.bottom - shadeHeight, endY = screen.bottom),
                Offset(screen.left, screen.bottom - shadeHeight), Size(screen.width, shadeHeight))
        }
    }
}

/** Where [HomeGridPreview] draws the page, the grid inside its edge space, and the dock icons. */
internal data class HomeGridGeometry(
    val page: Rect,
    val grid: Rect,
    val dockBar: Rect,
    /** Side space before the first and after the last dock icon, measured from the screen edge. */
    val dockSide: Float,
    val dockIconCenters: List<Offset>,
    val dockIconRadius: Float,
)

/**
 * Geometry of the miniature home screen in [screen]. Like `WorkspaceController`, the page is inset
 * by the home edge space on each side, and the dock icons by the dock's own side space or, when
 * [dockHorizontalPadding] is null, by the home side space.
 */
internal fun homeGridGeometry(
    screen: Rect,
    horizontalPadding: PaddingSize,
    verticalPadding: PaddingSize,
    dockHorizontalPadding: PaddingSize?,
): HomeGridGeometry {
    val unit = screen.width / 10f
    val dockTop = screen.bottom - unit * 2.2f
    val dockBar = Rect(screen.left + unit * 0.6f, dockTop, screen.right - unit * 0.6f, dockTop + unit * 1.6f)
    val page = Rect(screen.left, screen.top + unit * 1.3f, screen.right, dockTop - unit * 0.3f)
    val perDp = screen.width / REFERENCE_SCREEN_DP * EDGE_EXAGGERATION
    val h = HomeLayoutMetrics.paddingDp(horizontalPadding) * perDp
    val v = HomeLayoutMetrics.paddingDp(verticalPadding) * perDp
    val grid = Rect(page.left + h, page.top + v, page.right - h, page.bottom - v)
    val dockSide = HomeLayoutMetrics.paddingDp(dockHorizontalPadding ?: horizontalPadding) * perDp
    val dockCell = (screen.width - dockSide * 2f) / DOCK_SAMPLE_ICONS
    val centers = List(DOCK_SAMPLE_ICONS) { index ->
        Offset(screen.left + dockSide + dockCell * (index + 0.5f), dockBar.center.y)
    }
    return HomeGridGeometry(page, grid, dockBar, dockSide, centers, minOf(dockCell, dockBar.height) * 0.3f)
}

/**
 * A home-screen strip at real size: a 2 × 2 sample widget beside four sample icons. The dashed
 * outline is the widget's cell area; [padding] insets the widget inside it and [cornerRadiusDp]
 * rounds it (zero shows a sample provider's own corners, as the setting does on the home screen).
 */
@Composable
fun HomeWidgetPreview(
    cornerRadiusDp: Float,
    padding: PaddingSize,
    modifier: Modifier = Modifier,
    allowOverlap: Boolean = false,
) {
    val corners = if (cornerRadiusDp <= 0f) stringResource(R.string.settings_home_page_widgets_summary_own_corners)
        else dpValueLabel()(cornerRadiusDp)
    val description = stringResource(if (allowOverlap) R.string.preview_home_widget_overlap_on else R.string.preview_home_widget_overlap_off) +
        " " + stringResource(R.string.preview_fix_home_widget_shape, corners, spacingLabel(padding))
    Canvas(modifier.semantics { contentDescription = description }) {
        val side = 16.dp.toPx()
        val area = Rect(side, 0f, size.width - side, size.height)
        drawRoundRect(wallpaperBrush(area), area.topLeft, area.size, CornerRadius(16.dp.toPx()))

        val inner = Rect(area.left + 8.dp.toPx(), area.top + 8.dp.toPx(), area.right - 8.dp.toPx(), area.bottom - 8.dp.toPx())
        val cellW = inner.width / 4f
        val cellH = inner.height / 2f

        val cell = Rect(inner.left, inner.top, inner.left + cellW * 2f, inner.bottom)
        drawRoundRect(Color.White.copy(alpha = 0.5f), cell.topLeft, cell.size, CornerRadius(4.dp.toPx()),
            style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))))
        val inset = HomeLayoutMetrics.paddingDp(padding).dp.toPx().coerceAtMost(minOf(cell.width, cell.height) * .35f)
        val widget = Rect(cell.left + inset, cell.top + inset, cell.right - inset, cell.bottom - inset)
        val requested = if (cornerRadiusDp <= 0f) PROVIDER_CORNER_DP else cornerRadiusDp
        val radius = requested.dp.toPx().coerceAtMost(minOf(widget.width, widget.height) / 2f)
        drawRoundRect(Color(0xF2F4F1FA), widget.topLeft, widget.size, CornerRadius(radius))

        // Sample content: a weather-style sun and two text lines, kept clear of the largest corners.
        val content = Color(0xFF3B3F4C)
        val u = minOf(widget.width, widget.height) / 10f
        drawCircle(Color(0xFFE7A53A), u * 1.6f, Offset(widget.center.x, widget.top + widget.height * 0.38f))
        drawRoundRect(content, Offset(widget.center.x - u * 2.6f, widget.top + widget.height * 0.62f),
            Size(u * 5.2f, u * 0.8f), CornerRadius(u))
        drawRoundRect(content.copy(alpha = 0.45f), Offset(widget.center.x - u * 1.8f, widget.top + widget.height * 0.74f),
            Size(u * 3.6f, u * 0.6f), CornerRadius(u))

        if (allowOverlap) {
            // A second original sample widget, placed over the first only in the demonstration.
            val second = Rect(widget.center.x, widget.center.y, inner.left + cellW * 2.6f, inner.bottom - inset)
            drawRoundRect(Color(0xEA273D5B), second.topLeft, second.size, CornerRadius(radius.coerceAtMost(second.height / 2f)))
            val lineHeight = (second.height / 9f).coerceAtLeast(1f)
            repeat(3) { line ->
                drawRoundRect(Color.White.copy(alpha = .8f),
                    Offset(second.left + second.width * .18f, second.top + second.height * (.25f + line * .18f)),
                    Size(second.width * .64f, lineHeight), CornerRadius(lineHeight))
            }
        }
        val iconRadius = minOf(cellW, cellH) * 0.26f
        for (row in 0 until 2) for (col in 0 until 2) {
            if (allowOverlap && row == 1 && col == 0) continue
            val cx = inner.left + cellW * (col + 2.5f)
            val cy = inner.top + cellH * (row + 0.5f) - iconRadius * 0.3f
            drawCircle(SAMPLE_COLORS[(row * 2 + col) % SAMPLE_COLORS.size], iconRadius, Offset(cx, cy))
            drawRoundRect(Color.White.copy(alpha = 0.6f), Offset(cx - iconRadius, cy + iconRadius * 1.3f),
                Size(iconRadius * 2f, iconRadius * 0.32f), CornerRadius(iconRadius))
        }
    }
}

/** Phone outline centred in the canvas; returns the screen area inside the bezel. */
private fun DrawScope.phoneScreen(frame: Color): Rect {
    val h = size.height
    val w = minOf(size.width, h * 0.56f)
    val left = (size.width - w) / 2f
    drawRoundRect(frame, Offset(left, 0f), Size(w, h), CornerRadius(w * 0.14f))
    val bezel = w * 0.04f
    return Rect(left + bezel, bezel, left + w - bezel, h - bezel)
}

/** The wallpaper stand-in of [wallpaperBackground], as a Compose brush across [area]. */
private fun wallpaperBrush(area: Rect) = Brush.linearGradient(
    PREVIEW_WALLPAPER.map { Color(it) },
    start = area.topLeft,
    end = area.bottomRight,
)

/** Typical phone width in dp, to scale real edge space onto the miniature screen. */
private const val REFERENCE_SCREEN_DP = 360f

/** Edge space is drawn this many times larger than life, or 4dp would vanish on a 100dp phone. */
private const val EDGE_EXAGGERATION = 2.5f

/** Sample icons in the miniature dock. */
private const val DOCK_SAMPLE_ICONS = 4

/** Corners of the sample widget when the setting keeps the provider's own corners. */
private const val PROVIDER_CORNER_DP = 16f

private val SAMPLE_COLORS = PREVIEW_SAMPLE_COLORS.map { Color(it) }
