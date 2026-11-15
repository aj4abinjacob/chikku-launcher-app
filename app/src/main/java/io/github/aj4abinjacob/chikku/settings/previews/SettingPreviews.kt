package io.github.aj4abinjacob.chikku.settings.previews

import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import io.github.aj4abinjacob.chikku.core.model.settings.AppMenuStyle
import io.github.aj4abinjacob.chikku.core.model.settings.BadgePosition
import io.github.aj4abinjacob.chikku.core.model.settings.BadgeStyle
import io.github.aj4abinjacob.chikku.core.model.settings.DockBackground
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerFastScrollerStyle
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerOpenMethod
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerSearchBarPosition
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerStyle
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerTabBarPosition
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerTabStyle
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerTransition
import io.github.aj4abinjacob.chikku.core.model.settings.FolderScrollStyle
import io.github.aj4abinjacob.chikku.core.model.settings.FolderWindowStyle
import io.github.aj4abinjacob.chikku.core.model.settings.HomeSearchBarPosition
import io.github.aj4abinjacob.chikku.core.model.settings.IconShape
import io.github.aj4abinjacob.chikku.core.model.settings.ListEdgeEffect
import io.github.aj4abinjacob.chikku.core.model.settings.ListScrollSpeed
import io.github.aj4abinjacob.chikku.core.model.settings.PageEdgeFeedback
import io.github.aj4abinjacob.chikku.core.model.settings.PageIndicatorStyle
import io.github.aj4abinjacob.chikku.core.model.settings.PageSwipeSensitivity
import io.github.aj4abinjacob.chikku.core.model.settings.PageTransitionEffect
import io.github.aj4abinjacob.chikku.core.model.settings.SearchPresentation
import io.github.aj4abinjacob.chikku.core.model.settings.SearchResultsAnchor
import io.github.aj4abinjacob.chikku.core.model.settings.StatusBarMode
import io.github.aj4abinjacob.chikku.platform.icons.IconShaper
import io.github.aj4abinjacob.chikku.settings.components.PreviewCanvas
import io.github.aj4abinjacob.chikku.settings.components.PreviewInk
import io.github.aj4abinjacob.chikku.settings.components.arrow
import io.github.aj4abinjacob.chikku.settings.components.dockRow
import io.github.aj4abinjacob.chikku.settings.components.dotGrid
import io.github.aj4abinjacob.chikku.settings.components.phone
import io.github.aj4abinjacob.chikku.settings.components.pill
import io.github.aj4abinjacob.chikku.settings.components.roundRect

/*
 * Thumbnails for settings choice dialogs, one function per setting. Each draws a generic phone
 * with dots for apps and highlights, in the accent colour, the part the option changes.
 */

/** Scales the existing alpha, so translucent theme colours stay translucent. */
private fun Color.fade(fraction: Float): Color = copy(alpha = alpha * fraction)

/** Drawing unit: a tenth of the screen width. */
private fun unitOf(screen: Rect): Float = screen.width / 10f

/** Home icons above the dock, leaving room for a status strip at the top. */
private fun DrawScope.homeGrid(screen: Rect, ink: PreviewInk, color: Color = ink.faint, rows: Int = 4) {
    val u = unitOf(screen)
    dotGrid(Rect(screen.left + u, screen.top + u * 3f, screen.right - u, screen.top + u * (3f + rows * 2.2f)), 4, rows, color)
}

// ---- Folders ----

@Composable
fun FolderWindowPreview(style: FolderWindowStyle) = PreviewCanvas { ink ->
    val s = phone(ink)
    val u = unitOf(s)
    if (style == FolderWindowStyle.FLOATING) {
        homeGrid(s, ink)
        dockRow(s, ink.faint)
    }
    val panel = if (style == FolderWindowStyle.FLOATING) {
        Rect(s.left + u * 1.2f, s.top + u * 5f, s.right - u * 1.2f, s.top + u * 12.5f)
    } else {
        Rect(s.left, s.top + u * 1.6f, s.right, s.bottom)
    }
    roundRect(panel, ink.panel, u * 1.4f)
    val inner = Rect(panel.left + u * 0.8f, panel.top + u, panel.right - u * 0.8f, panel.top + u * (if (style == FolderWindowStyle.FLOATING) 6.5f else 10f))
    dotGrid(inner, 3, if (style == FolderWindowStyle.FLOATING) 3 else 4, ink.accent)
}

@Composable
fun FolderPagingPreview(style: FolderScrollStyle) = PreviewCanvas { ink ->
    val s = phone(ink)
    val u = unitOf(s)
    val panel = Rect(s.left + u, s.top + u * 4f, s.right - u, s.top + u * 14f)
    roundRect(panel, ink.panel, u * 1.4f)
    val grid = Rect(panel.left + u * 0.6f, panel.top + u, panel.right - u * 1.6f, panel.top + u * 7.5f)
    dotGrid(grid, 3, 3, ink.muted)
    if (style == FolderScrollStyle.VERTICAL) {
        val x = panel.right - u * 0.9f
        arrow(Offset(x, panel.top + u * 2f), Offset(x, panel.bottom - u * 1.5f), ink.accent, u * 0.45f)
    } else {
        arrow(Offset(panel.left + u * 2f, panel.bottom - u * 2.6f), Offset(panel.right - u * 2f, panel.bottom - u * 2.6f), ink.accent, u * 0.45f)
        val y = panel.bottom - u * 1f
        listOf(-1f, 0f, 1f).forEachIndexed { i, dx ->
            drawCircle(if (i == 0) ink.accent else ink.faint, u * 0.35f, Offset(panel.center.x + dx * u * 1.2f, y))
        }
    }
}

// ---- App drawer ----

@Composable
fun DrawerStylePreview(style: DrawerStyle) = PreviewCanvas { ink ->
    val s = phone(ink)
    val u = unitOf(s)
    when (style) {
        DrawerStyle.VERTICAL -> {
            dotGrid(Rect(s.left + u * 0.6f, s.top + u * 2f, s.right - u * 1.8f, s.bottom - u * 1.5f), 4, 7, ink.muted)
            arrow(Offset(s.right - u * 0.9f, s.top + u * 3f), Offset(s.right - u * 0.9f, s.bottom - u * 3f), ink.accent, u * 0.45f)
        }
        DrawerStyle.HORIZONTAL_PAGED -> {
            dotGrid(Rect(s.left + u * 0.6f, s.top + u * 2f, s.right - u * 0.6f, s.bottom - u * 5f), 4, 6, ink.muted)
            arrow(Offset(s.left + u * 2.5f, s.bottom - u * 3.5f), Offset(s.right - u * 2.5f, s.bottom - u * 3.5f), ink.accent, u * 0.45f)
            listOf(-1f, 0f, 1f).forEachIndexed { i, dx ->
                drawCircle(if (i == 0) ink.accent else ink.faint, u * 0.4f, Offset(s.center.x + dx * u * 1.4f, s.bottom - u * 1.6f))
            }
        }
        DrawerStyle.LIST -> repeat(7) { row ->
            val y = s.top + u * (2.6f + row * 2.3f)
            drawCircle(ink.muted, u * 0.75f, Offset(s.left + u * 1.8f, y))
            pill(Rect(s.left + u * 3.3f, y - u * 0.35f, s.right - u * (1.2f + (row % 3) * 1.4f), y + u * 0.35f), if (row == 0) ink.accent else ink.faint)
        }
    }
}

@Composable
fun DrawerOpenPreview(method: DrawerOpenMethod) = PreviewCanvas { ink ->
    val s = phone(ink)
    val u = unitOf(s)
    homeGrid(s, ink)
    val dock = dockRow(s, ink.faint)
    if (method != DrawerOpenMethod.BUTTON) {
        val x = if (method == DrawerOpenMethod.BOTH) s.left + u * 2.4f else s.center.x
        drawCircle(ink.accent.copy(alpha = 0.3f), u * 1.1f, Offset(x, s.bottom - u * 1.2f))
        arrow(Offset(x, s.bottom - u * 1.2f), Offset(x, s.top + u * 9f), ink.accent, u * 0.5f)
    }
    if (method != DrawerOpenMethod.SWIPE_UP) {
        val y = dock.top - u * 1.2f
        val chevron = Path().apply {
            moveTo(s.center.x - u, y + u * 0.5f)
            lineTo(s.center.x, y - u * 0.5f)
            lineTo(s.center.x + u, y + u * 0.5f)
        }
        drawPath(chevron, ink.accent, style = Stroke(u * 0.5f))
    }
}

@Composable
fun DrawerTransitionPreview(transition: DrawerTransition) = PreviewCanvas { ink ->
    val s = phone(ink)
    val u = unitOf(s)
    homeGrid(s, ink)
    dockRow(s, ink.faint)
    when (transition) {
        DrawerTransition.SLIDE -> {
            val sheet = Rect(s.left, s.top + s.height * 0.45f, s.right, s.bottom)
            roundRect(sheet, ink.panel, u * 1.2f)
            dotGrid(Rect(sheet.left + u * 0.6f, sheet.top + u, sheet.right - u * 0.6f, sheet.bottom - u), 4, 3, ink.accent)
            arrow(Offset(s.center.x, sheet.top - u * 0.6f), Offset(s.center.x, sheet.top - u * 3.6f), ink.accent, u * 0.45f)
        }
        DrawerTransition.FADE -> {
            roundRect(s, ink.panel.fade(0.7f), u * 1.2f)
            dotGrid(Rect(s.left + u * 0.6f, s.top + u * 2f, s.right - u * 0.6f, s.bottom - u * 2f), 4, 6, ink.accent.copy(alpha = 0.55f))
        }
        DrawerTransition.SCALE -> {
            val inset = Rect(s.left + u * 1.2f, s.top + u * 2f, s.right - u * 1.2f, s.bottom - u * 2f)
            roundRect(inset, ink.panel, u * 1.2f)
            dotGrid(Rect(inset.left + u * 0.5f, inset.top + u, inset.right - u * 0.5f, inset.bottom - u), 4, 5, ink.accent)
            val w = u * 0.35f
            arrow(inset.topLeft + Offset(u * 0.4f, u * 0.4f), s.topLeft + Offset(u * 0.1f, u * 0.6f), ink.accent, w)
            arrow(inset.bottomRight - Offset(u * 0.4f, u * 0.4f), s.bottomRight - Offset(u * 0.1f, u * 0.6f), ink.accent, w)
        }
    }
}

@Composable
fun DrawerSearchPositionPreview(position: DrawerSearchBarPosition) = PreviewCanvas { ink ->
    val s = phone(ink)
    val u = unitOf(s)
    val top = position == DrawerSearchBarPosition.TOP
    val gridTop = if (top) s.top + u * 4.6f else s.top + u * 2f
    val gridBottom = if (position == DrawerSearchBarPosition.BOTTOM) s.bottom - u * 4.6f else s.bottom - u * 1.5f
    dotGrid(Rect(s.left + u * 0.6f, gridTop, s.right - u * 0.6f, gridBottom), 4, 6, ink.muted)
    if (position != DrawerSearchBarPosition.HIDDEN) {
        val y = if (top) s.top + u * 2f else s.bottom - u * 3.6f
        val bar = Rect(s.left + u * 0.8f, y, s.right - u * 0.8f, y + u * 1.8f)
        pill(bar, ink.accent)
        drawCircle(ink.onAccent, u * 0.4f, Offset(bar.left + u * 1.1f, bar.center.y), style = Stroke(u * 0.22f))
    }
}

@Composable
fun DrawerTabPositionPreview(position: DrawerTabBarPosition) = PreviewCanvas { ink ->
    val s = phone(ink)
    val u = unitOf(s)
    val top = position == DrawerTabBarPosition.TOP
    dotGrid(Rect(s.left + u * 0.6f, if (top) s.top + u * 4.6f else s.top + u * 2f, s.right - u * 0.6f, if (top) s.bottom - u * 1.5f else s.bottom - u * 4.6f), 4, 6, ink.muted)
    val y = if (top) s.top + u * 2f else s.bottom - u * 3.6f
    pill(Rect(s.left + u * 0.8f, y, s.left + u * 4.6f, y + u * 1.6f), ink.accent)
    pill(Rect(s.left + u * 5.2f, y + u * 0.55f, s.right - u * 1.4f, y + u * 1.05f), ink.faint)
}

@Composable
fun DrawerTabStylePreview(style: DrawerTabStyle) = PreviewCanvas { ink ->
    val s = phone(ink)
    val u = unitOf(s)
    dotGrid(Rect(s.left + u * 0.6f, s.top + u * 5f, s.right - u * 0.6f, s.bottom - u * 1.5f), 4, 6, ink.muted)
    val y = s.top + u * 2f
    if (style == DrawerTabStyle.COMPACT) {
        pill(Rect(s.left + u * 0.8f, y, s.left + u * 3.8f, y + u * 1.5f), ink.accent)
        pill(Rect(s.left + u * 4.4f, y + u * 0.5f, s.left + u * 6.6f, y + u), ink.faint)
        pill(Rect(s.left + u * 7.2f, y + u * 0.5f, s.left + u * 9f, y + u), ink.faint)
    } else {
        val bar = Rect(s.left + u * 0.6f, y - u * 0.2f, s.right - u * 0.6f, y + u * 2f)
        pill(bar, ink.panel)
        pill(Rect(bar.left, bar.top, bar.center.x, bar.bottom), ink.accent)
    }
}

@Composable
fun FastScrollerPreview(style: DrawerFastScrollerStyle) {
    val measurer = rememberTextMeasurer()
    PreviewCanvas { ink ->
        val s = phone(ink)
        val u = unitOf(s)
        dotGrid(Rect(s.left + u * 0.6f, s.top + u * 2f, s.right - u * 2.2f, s.bottom - u * 1.5f), 3, 7, ink.muted)
        val x = s.right - u * 1.1f
        val letterStyle = TextStyle(fontSize = (u * 1.25f).toSp(), fontWeight = FontWeight.Bold)
        if (style == DrawerFastScrollerStyle.ALPHABET) {
            "ABCDEFGH".forEachIndexed { i, c ->
                val layout = measurer.measure(c.toString(), letterStyle.copy(color = if (i == 2) ink.accent else ink.muted))
                drawText(layout, topLeft = Offset(x - layout.size.width / 2f, s.top + u * (2f + i * 2.1f)))
            }
        } else {
            val thumb = Rect(x - u * 0.35f, s.top + u * 6f, x + u * 0.35f, s.top + u * 9.5f)
            roundRect(thumb, ink.accent, u * 0.35f)
            val bubble = Offset(x - u * 3f, thumb.center.y)
            drawCircle(ink.accent, u * 1.5f, bubble)
            val layout = measurer.measure("C", letterStyle.copy(color = ink.onAccent))
            drawText(layout, topLeft = bubble - Offset(layout.size.width / 2f, layout.size.height / 2f))
        }
    }
}

// ---- Search ----

@Composable
fun SearchPresentationPreview(presentation: SearchPresentation) = PreviewCanvas { ink ->
    val s = phone(ink)
    val u = unitOf(s)
    val window = presentation == SearchPresentation.WINDOW
    if (window) {
        homeGrid(s, ink)
        dockRow(s, ink.faint)
        roundRect(s, Color.Black.copy(alpha = 0.3f), u * 1.2f)
    }
    val card = if (window) Rect(s.left + u * 0.6f, s.top + u * 2f, s.right - u * 0.6f, s.top + u * 11f) else s
    roundRect(card, ink.panel, u * 1.2f)
    val bar = Rect(card.left + u * 0.6f, card.top + u * (if (window) 0.6f else 2f), card.right - u * 0.6f, card.top + u * (if (window) 2.4f else 3.8f))
    pill(bar, ink.accent)
    repeat(if (window) 3 else 6) { row ->
        val y = bar.bottom + u * (1.5f + row * 2f)
        drawCircle(ink.muted, u * 0.6f, Offset(card.left + u * 1.4f, y))
        pill(Rect(card.left + u * 2.6f, y - u * 0.3f, card.right - u * (1f + row % 2 * 1.5f), y + u * 0.3f), ink.faint)
    }
}

/** Opened search with the keyboard up: result rows hang under the field or rest on the keyboard. */
@Composable
fun SearchResultsAnchorPreview(anchor: SearchResultsAnchor) = PreviewCanvas { ink ->
    val s = phone(ink)
    val u = unitOf(s)
    roundRect(s, ink.panel, u * 1.2f)
    val bar = Rect(s.left + u * 0.6f, s.top + u * 2f, s.right - u * 0.6f, s.top + u * 3.8f)
    pill(bar, ink.muted)
    val keyboard = Rect(s.left, s.bottom - u * 6f, s.right, s.bottom)
    roundRect(keyboard, ink.faint, u * 0.6f)
    repeat(3) { row ->
        val y = keyboard.top + u * (1.4f + row * 1.6f)
        pill(Rect(keyboard.left + u * 0.8f, y - u * 0.3f, keyboard.right - u * 0.8f, y + u * 0.3f), ink.muted)
    }
    // Row 0 is the best match: first under the field, or nearest the keyboard.
    repeat(3) { row ->
        val y = if (anchor == SearchResultsAnchor.KEYBOARD) keyboard.top - u * (1.3f + row * 2f) else bar.bottom + u * (1.5f + row * 2f)
        val color = if (row == 0) ink.accent else ink.muted
        drawCircle(color, u * 0.6f, Offset(s.left + u * 1.4f, y))
        pill(Rect(s.left + u * 2.6f, y - u * 0.3f, s.right - u * (1f + row % 2 * 1.5f), y + u * 0.3f), if (row == 0) color else ink.faint)
    }
}

@Composable
fun HomeSearchPositionPreview(position: HomeSearchBarPosition) = PreviewCanvas { ink ->
    val s = phone(ink)
    val u = unitOf(s)
    homeGrid(s, ink, ink.muted)
    val bar = when (position) {
        HomeSearchBarPosition.TOP -> Rect(s.left + u * 0.8f, s.top + u * 0.8f, s.right - u * 0.8f, s.top + u * 2.4f)
        HomeSearchBarPosition.BELOW_DOCK -> Rect(s.left + u * 0.8f, s.bottom - u * 2.2f, s.right - u * 0.8f, s.bottom - u * 0.6f)
        HomeSearchBarPosition.NONE -> null
    }
    val dockShift = if (position == HomeSearchBarPosition.BELOW_DOCK) u * 2f else 0f
    translate(top = -dockShift) { dockRow(s, ink.muted) }
    if (bar != null) pill(bar, ink.accent)
}

// ---- Style ----

@Composable
fun StatusBarPreview(mode: StatusBarMode) = PreviewCanvas { ink ->
    val s = phone(ink)
    val u = unitOf(s)
    // A tinted "wallpaper" shows the difference between a solid and a see-through bar.
    roundRect(s, ink.accent.copy(alpha = 0.18f), u * 1.2f)
    homeGrid(s, ink, ink.muted)
    dockRow(s, ink.muted)
    val strip = Rect(s.left, s.top, s.right, s.top + u * 1.8f)
    when (mode) {
        StatusBarMode.SHOWN -> clipRect(strip.left, strip.top, strip.right, strip.bottom) { roundRect(s, ink.panel, u * 1.2f) }
        StatusBarMode.TRANSPARENT, StatusBarMode.HIDDEN -> Unit
    }
    if (mode == StatusBarMode.HIDDEN) {
        arrow(Offset(s.center.x, s.top + u * 0.3f), Offset(s.center.x, s.top + u * 2.2f), ink.accent, u * 0.4f)
    } else {
        val y = strip.center.y
        pill(Rect(s.left + u * 1f, y - u * 0.3f, s.left + u * 3f, y + u * 0.3f), ink.accent)
        repeat(3) { i -> drawCircle(ink.accent, u * 0.32f, Offset(s.right - u * (1.2f + i * 1f), y)) }
    }
}

@Composable
fun AppMenuStylePreview(style: AppMenuStyle) = PreviewCanvas { ink ->
    val u = size.height / 12f
    val icon = Rect(center.x - u * 1.6f, 0f, center.x + u * 1.6f, u * 3.2f)
    roundRect(icon, ink.accent, u)
    val card = Rect(center.x - u * 5.2f, u * 4f, center.x + u * 5.2f, size.height)
    roundRect(card, ink.panel, u)
    if (style == AppMenuStyle.POPUP) {
        repeat(2) { row ->
            val y = card.top + u * (1.3f + row * 2f)
            drawCircle(ink.muted, u * 0.55f, Offset(card.left + u * 1.2f, y))
            pill(Rect(card.left + u * 2.3f, y - u * 0.3f, card.right - u * (1.5f + row * 1.5f), y + u * 0.3f), ink.faint)
        }
        val y = card.bottom - u * 1.4f
        repeat(4) { i -> drawCircle(ink.accent, u * 0.6f, Offset(card.left + u * (1.6f + i * 2.4f), y)) }
    } else {
        repeat(4) { row ->
            val y = card.top + u * (1.1f + row * 1.85f)
            drawCircle(if (row < 1) ink.muted else ink.accent, u * 0.5f, Offset(card.left + u * 1.2f, y + u * 0.35f))
            pill(Rect(card.left + u * 2.3f, y - u * 0.1f, card.right - u * 2.5f, y + u * 0.4f), ink.muted)
            pill(Rect(card.left + u * 2.3f, y + u * 0.65f, card.right - u * 1.2f, y + u * 0.95f), ink.faint)
        }
    }
}

/** Exact outline of each shape; [IconShape.SYSTEM] uses this device's own adaptive-icon mask. */
@Composable
fun IconShapePreview(shape: IconShape) {
    val systemMask = remember {
        // 100 x 100 viewport. A square device mask is drawn as the circle real icons fall back to.
        if (IconShaper.systemMaskIsSquare) IconShaper.path(IconShape.CIRCLE, 100f)!!.asComposePath()
        else AdaptiveIconDrawable(ColorDrawable(), ColorDrawable()).iconMask.asComposePath()
    }
    PreviewCanvas { ink ->
        val side = minOf(size.width, size.height) * 0.8f
        val origin = Offset((size.width - side) / 2f, (size.height - side) / 2f)
        val path = if (shape == IconShape.SYSTEM) systemMask else IconShaper.path(shape, 100f)?.asComposePath() ?: systemMask
        withTransform({
            translate(origin.x, origin.y)
            scale(side / 100f, side / 100f, pivot = Offset.Zero)
        }) { drawPath(path, ink.accent) }
    }
}

// ---- Home screen ----

/** A page of apps with the indicator under it, drawn large enough to read in small tiles. */
@Composable
fun PageIndicatorPreview(style: PageIndicatorStyle) = PreviewCanvas { ink ->
    val h = size.height
    val card = Rect(maxOf(0f, center.x - h * 0.85f), 0f, minOf(size.width, center.x + h * 0.85f), h)
    roundRect(card, ink.panel, h * 0.14f)
    dotGrid(Rect(card.left + card.width * 0.12f, h * 0.08f, card.right - card.width * 0.12f, h * 0.62f), 4, 2, ink.faint)
    val y = h * 0.8f
    val u = h * 0.06f
    when (style) {
        PageIndicatorStyle.DOTS -> listOf(-1f, 0f, 1f).forEachIndexed { i, dx ->
            drawCircle(if (i == 1) ink.accent else ink.muted, u, Offset(card.center.x + dx * u * 3.2f, y))
        }
        PageIndicatorStyle.LINE -> {
            pill(Rect(card.left + card.width * 0.22f, y - u * 0.6f, card.right - card.width * 0.22f, y + u * 0.6f), ink.muted)
            pill(Rect(card.center.x - card.width * 0.1f, y - u * 0.6f, card.center.x + card.width * 0.1f, y + u * 0.6f), ink.accent)
        }
        PageIndicatorStyle.NONE -> Unit
    }
}

/** A still frame half-way through swiping from one page to the next. */
@Composable
fun PageTransitionPreview(effect: PageTransitionEffect) = PreviewCanvas { ink ->
    // The incoming page enters from the reading-end side, so right-to-left mirrors the frame.
    if (layoutDirection == LayoutDirection.Rtl) withTransform({ scale(-1f, 1f, center) }) { pageTransitionFrame(effect, ink) }
    else pageTransitionFrame(effect, ink)
}

private fun DrawScope.pageTransitionFrame(effect: PageTransitionEffect, ink: PreviewInk) {
    val h = size.height
    val w = h * 0.5f
    val gap = w * 0.08f
    val cy = h / 2f
    fun page(rect: Rect, color: Color, dots: Color) {
        roundRect(rect, color, w * 0.12f)
        dotGrid(Rect(rect.left + rect.width * 0.12f, rect.top + rect.height * 0.12f, rect.right - rect.width * 0.12f, rect.top + rect.height * 0.7f), 3, 4, dots)
    }
    when (effect) {
        PageTransitionEffect.SLIDE -> {
            page(Rect(center.x - w - gap / 2f, 0f, center.x - gap / 2f, h), ink.panel, ink.muted)
            page(Rect(center.x + gap / 2f, 0f, center.x + w + gap / 2f, h), ink.panel, ink.accent)
        }
        PageTransitionEffect.FADE -> {
            page(Rect(center.x - w * 0.62f, 0f, center.x + w * 0.38f, h), ink.panel.fade(0.5f), ink.muted.copy(alpha = 0.3f))
            page(Rect(center.x - w * 0.38f, 0f, center.x + w * 0.62f, h), ink.panel.fade(0.75f), ink.accent.copy(alpha = 0.7f))
        }
        PageTransitionEffect.ZOOM -> {
            val small = w * 0.7f
            page(Rect(center.x - small - gap, cy - h * 0.35f, center.x - gap, cy + h * 0.35f), ink.panel.fade(0.6f), ink.muted)
            page(Rect(center.x + gap / 2f, 0f, center.x + w + gap / 2f, h), ink.panel, ink.accent)
        }
        PageTransitionEffect.CUBE -> {
            val edge = center.x
            val near = h * 0.08f
            val far = h * 0.2f
            fun face(x0: Float, x1: Float, inset0: Float, inset1: Float, color: Color, dots: Color) {
                val path = Path().apply {
                    moveTo(x0, inset0); lineTo(x1, inset1); lineTo(x1, h - inset1); lineTo(x0, h - inset0); close()
                }
                drawPath(path, color)
                val l = minOf(x0, x1)
                val r = maxOf(x0, x1)
                dotGrid(Rect(l + (r - l) * 0.18f, h * 0.28f, r - (r - l) * 0.18f, h * 0.68f), 2, 3, dots)
            }
            face(edge - w * 0.75f, edge, far, near, ink.panel, ink.muted)
            face(edge, edge + w * 0.75f, near, far, ink.panel.fade(0.8f), ink.accent)
            drawLine(ink.frame, Offset(edge, near), Offset(edge, h - near), w * 0.03f)
        }
    }
}

/**
 * How far a finger travels before the page follows: the accent arrow runs from the touch point to
 * the threshold tick, so Low sensitivity draws the longest swipe. Drawn in the swipe-to-next direction.
 */
@Composable
fun SwipeSensitivityPreview(sensitivity: PageSwipeSensitivity) = PreviewCanvas { ink ->
    val h = size.height
    val card = Rect(maxOf(0f, center.x - h * 0.85f), 0f, minOf(size.width, center.x + h * 0.85f), h)
    roundRect(card, ink.panel, h * 0.14f)
    dotGrid(Rect(card.left + card.width * 0.12f, h * 0.1f, card.right - card.width * 0.12f, h * 0.4f), 4, 1, ink.faint)
    val rtl = layoutDirection == LayoutDirection.Rtl
    val y = h * 0.7f
    val start = if (rtl) card.left + card.width * 0.1f else card.right - card.width * 0.1f
    val reach = card.width * 0.27f * sensitivity.slopMultiplier
    val end = start + if (rtl) reach else -reach
    drawLine(ink.frame, Offset(end, y - h * 0.15f), Offset(end, y + h * 0.15f), h * 0.035f, StrokeCap.Round)
    arrow(Offset(start, y), Offset(end, y), ink.accent, h * 0.045f)
    drawCircle(ink.accent.fade(0.3f), h * 0.11f, Offset(start, y))
    drawCircle(ink.accent, h * 0.055f, Offset(start, y))
}

/** Swiping past the last page: a hard stop against the edge, or a spring that pulls and returns. */
@Composable
fun EdgeFeedbackPreview(feedback: PageEdgeFeedback) = PreviewCanvas { ink ->
    val h = size.height
    val rtl = layoutDirection == LayoutDirection.Rtl
    val width = minOf(size.width, h * 1.7f)
    val left = (size.width - width) / 2f
    val wall = if (rtl) left + width * 0.06f else left + width * 0.94f
    val gap = if (feedback == PageEdgeFeedback.RESISTANCE) width * 0.22f else 0f
    val pageWidth = width * 0.62f
    val near = if (rtl) wall + gap else wall - gap
    val page = if (rtl) Rect(near, h * 0.08f, near + pageWidth, h * 0.92f) else Rect(near - pageWidth, h * 0.08f, near, h * 0.92f)
    roundRect(page, ink.panel, h * 0.12f)
    dotGrid(Rect(page.left + page.width * 0.15f, page.top + page.height * 0.15f, page.right - page.width * 0.15f, page.top + page.height * 0.7f), 3, 3, ink.muted)
    drawLine(if (feedback == PageEdgeFeedback.NONE) ink.accent else ink.frame, Offset(wall, 0f), Offset(wall, h), h * 0.05f, StrokeCap.Round)
    if (feedback == PageEdgeFeedback.RESISTANCE) {
        // A zigzag spring between the pulled page and the edge it returns to.
        val turns = 5
        val amplitude = h * 0.09f
        val path = Path().apply {
            moveTo(near, center.y)
            for (i in 1..turns) {
                val x = near + (wall - near) * (i - 0.5f) / turns
                lineTo(x, center.y + if (i % 2 == 0) amplitude else -amplitude)
            }
            lineTo(wall, center.y)
        }
        drawPath(path, ink.accent, style = Stroke(h * 0.035f, cap = StrokeCap.Round))
    }
}

/** A list with a flick: the accent arrow shows how far the same flick carries it at each speed. */
@Composable
fun ListScrollSpeedPreview(speed: ListScrollSpeed) = PreviewCanvas { ink ->
    val h = size.height
    val card = Rect(maxOf(0f, center.x - h * 0.85f), 0f, minOf(size.width, center.x + h * 0.85f), h)
    roundRect(card, ink.panel, h * 0.14f)
    val rows = 4
    for (row in 0 until rows) {
        val y = h * (0.16f + row * 0.22f)
        drawCircle(ink.faint, h * 0.06f, Offset(card.left + card.width * 0.14f, y))
        pill(Rect(card.left + card.width * 0.24f, y - h * 0.025f, card.left + card.width * 0.6f, y + h * 0.025f), ink.faint)
    }
    val x = card.right - card.width * 0.18f
    val top = h * 0.14f
    val length = h * (0.22f + 0.5f * (speed.flingMultiplier - 0.6f))
    arrow(Offset(x, top), Offset(x, top + length), ink.accent, h * 0.045f)
    drawCircle(ink.accent.fade(0.3f), h * 0.1f, Offset(x, top))
    drawCircle(ink.accent, h * 0.05f, Offset(x, top))
}

/** Pulling past the end of a list: Android's stretch spreads the last rows; Off stops them at the edge. */
@Composable
fun ListEdgeEffectPreview(effect: ListEdgeEffect) = PreviewCanvas { ink ->
    val h = size.height
    val card = Rect(maxOf(0f, center.x - h * 0.85f), 0f, minOf(size.width, center.x + h * 0.85f), h)
    roundRect(card, ink.panel, h * 0.14f)
    val stretch = effect == ListEdgeEffect.SYSTEM
    // Row centres; stretch pushes later rows further apart, as the platform effect does at the bottom.
    val ys = if (stretch) listOf(0.14f, 0.34f, 0.58f, 0.86f) else listOf(0.14f, 0.34f, 0.54f, 0.74f)
    ys.forEachIndexed { row, fraction ->
        val y = h * fraction
        val grow = if (stretch) 1f + row * 0.12f else 1f
        drawCircle(ink.muted, h * 0.06f * grow, Offset(card.left + card.width * 0.14f, y))
        pill(Rect(card.left + card.width * 0.24f, y - h * 0.025f * grow, card.left + card.width * 0.64f, y + h * 0.025f * grow), ink.muted)
    }
    if (!stretch) {
        val y = h * 0.88f
        drawLine(ink.accent, Offset(card.left + card.width * 0.08f, y), Offset(card.right - card.width * 0.08f, y), h * 0.05f, StrokeCap.Round)
    } else {
        arrow(Offset(card.right - card.width * 0.14f, h * 0.36f), Offset(card.right - card.width * 0.14f, h * 0.8f), ink.accent, h * 0.04f)
    }
}

@Composable
fun DockBackgroundPreview(background: DockBackground) = PreviewCanvas { ink ->
    val s = phone(ink)
    val u = unitOf(s)
    homeGrid(s, ink)
    val area = Rect(s.left, s.bottom - u * 4.4f, s.right, s.bottom)
    when (background) {
        DockBackground.NONE -> Unit
        DockBackground.RECTANGLE -> drawRect(ink.accent.copy(alpha = 0.45f), area.topLeft, area.size)
        DockBackground.ROUNDED -> roundRect(area.deflate(u * 0.5f), ink.accent.copy(alpha = 0.45f), u * 1.2f)
        DockBackground.CARD -> roundRect(Rect(area.left + u * 0.7f, area.top + u * 0.9f, area.right - u * 0.7f, area.bottom - u * 0.9f), ink.accent.copy(alpha = 0.45f), u * 1.6f)
        DockBackground.CUSTOM -> {
            val r = u * 1.8f
            val path = Path().apply {
                addRoundRect(androidx.compose.ui.geometry.RoundRect(area.deflate(u * 0.5f), CornerRadius(r), CornerRadius.Zero, CornerRadius(r), CornerRadius.Zero))
            }
            drawPath(path, ink.accent.copy(alpha = 0.45f))
        }
    }
    dotGrid(Rect(area.left + u, area.top + u, area.right - u, area.bottom - u), 4, 1, ink.muted, fill = 0.7f)
}

// ---- Badges ----

@Composable
fun BadgeStylePreview(style: BadgeStyle) {
    val measurer = rememberTextMeasurer()
    PreviewCanvas { ink -> badgedIcon(ink, style, BadgePosition.TOP_END) { origin, d ->
        val layout = measurer.measure("3", TextStyle(fontSize = (d * 0.6f).toSp(), fontWeight = FontWeight.Bold,
            color = if (ink.alert.luminance() > 0.5f) Color.Black else Color.White))
        drawText(layout, topLeft = origin - Offset(layout.size.width / 2f, layout.size.height / 2f))
    } }
}

@Composable
fun BadgePositionPreview(position: BadgePosition) = PreviewCanvas { ink -> badgedIcon(ink, BadgeStyle.DOT, position) { _, _ -> } }

private fun DrawScope.badgedIcon(ink: PreviewInk, style: BadgeStyle, position: BadgePosition, count: DrawScope.(Offset, Float) -> Unit) {
    val side = size.height * 0.7f
    val icon = Rect(Offset(center.x - side / 2f, center.y - side / 2f), Size(side, side))
    roundRect(icon, ink.muted, side * 0.25f)
    val end = position == BadgePosition.TOP_END || position == BadgePosition.BOTTOM_END
    val right = end == (layoutDirection == LayoutDirection.Ltr)
    val bottom = position == BadgePosition.BOTTOM_END || position == BadgePosition.BOTTOM_START
    val c = Offset(if (right) icon.right - side * 0.08f else icon.left + side * 0.08f, if (bottom) icon.bottom - side * 0.08f else icon.top + side * 0.08f)
    when (style) {
        BadgeStyle.OFF -> Unit
        BadgeStyle.DOT -> drawCircle(ink.alert, side * 0.13f, c)
        BadgeStyle.COUNT -> {
            drawCircle(ink.alert, side * 0.2f, c)
            count(c, side * 0.4f)
        }
        BadgeStyle.DYNAMIC -> {
            val r = side * 0.24f
            drawCircle(ink.accent, r, c)
            drawCircle(ink.onAccent, r * 0.38f, c - Offset(0f, r * 0.22f))
            drawArc(ink.onAccent, 180f, 180f, useCenter = true, topLeft = c + Offset(-r * 0.7f, r * 0.25f), size = Size(r * 1.4f, r * 1.2f))
            drawCircle(ink.screen, r, c, style = Stroke(side * 0.035f))
        }
    }
}
