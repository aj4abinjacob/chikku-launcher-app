package io.github.aj4abinjacob.chikku.home

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.View
import androidx.core.graphics.ColorUtils
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.settings.IconShape
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.settings.components.CHIKKU_DEFAULT_ACCENT
import io.github.aj4abinjacob.chikku.theme.Motion

private const val FOOTPRINT_FADE_MS = 160L

/** Accent fill of a footprint at full strength (16%). */
internal const val FOOTPRINT_FILL_ALPHA = 0x29

/** A view that draws an app or folder icon; menus and footprints hug the icon instead of the whole view. */
interface MenuAnchorIcon {
    /** Where the icon is drawn, in this view's coordinates. */
    fun iconBounds(out: Rect)

    /** Bottom of the drawn content (icon and label), in this view's coordinates; a menu below the item starts there. */
    fun contentBottom(): Int

    /** Draws [footprint] under this view's own content (null removes it), so it never hides the icon or label. */
    fun setFootprint(footprint: Drawable?)
}

/**
 * Where [anchor]'s footprint goes, in the anchor's coordinates: its icon plus a small margin, with
 * corners following the icon shape; the whole view (slightly inset) for items without an icon,
 * such as widgets.
 */
internal class FootprintShape(val rect: RectF, val radius: Float, val icon: RectF?, val iconRadius: Float)

internal fun footprintShape(anchor: View, shape: IconShape): FootprintShape {
    val res = anchor.resources
    val icon = Rect()
    if (anchor is MenuAnchorIcon) anchor.iconBounds(icon)
    if (icon.isEmpty) {
        val inset = res.getDimension(R.dimen.home_footprint_inset)
        val rect = RectF(0f, 0f, anchor.width.toFloat(), anchor.height.toFloat()).apply { inset(inset, inset) }
        return FootprintShape(rect, res.getDimension(R.dimen.home_footprint_radius), null, 0f)
    }
    val pad = res.getDimension(R.dimen.home_footprint_icon_padding)
    val iconRadius = icon.width() * iconCornerFraction(shape)
    return FootprintShape(RectF(icon).apply { inset(-pad, -pad) }, iconRadius + pad, RectF(icon), iconRadius)
}

/** Approximate corner radius of [shape] as a fraction of the icon size (the outline itself is in ShapeGeometry). */
internal fun iconCornerFraction(shape: IconShape): Float = when (shape) {
    IconShape.CIRCLE -> 0.5f
    IconShape.ROUNDED_SQUARE -> 0.25f
    IconShape.SQUARE -> 0.06f
    IconShape.TEARDROP -> 0.4f
    IconShape.SQUIRCLE, IconShape.SYSTEM -> 0.32f
}

/**
 * Marks [anchor] while its long-press menu is open. An icon sits on a tile in the menu card's own
 * fill and edge, so the press answers at once in the menu's look; an item without an icon (a
 * widget) gets an accent outline and tint over its whole tappable area. The workspace grid and
 * dock draw it behind the item; elsewhere (drawer, search results, folder panels) the item draws
 * it under its content. Returns the call that fades it out again.
 */
internal fun showItemFootprint(anchor: View, settings: LauncherSettings): () -> Unit {
    val fadeMs = Motion.current(settings.lookFeel.animationSpeed).duration(FOOTPRINT_FADE_MS)
    val shape = footprintShape(anchor, settings.lookFeel.iconShape)
    val colors = footprintColors(anchor.context, settings, hasIcon = shape.icon != null)
    val grid = anchor.parent as? CellLayout
    if (grid != null) {
        grid.showFootprint(anchor, shape, colors, fadeMs)
        return { grid.clearFootprint(anchor, fadeMs) }
    }
    val item = anchor as? MenuAnchorIcon
    val footprint = FootprintDrawable(anchor, shape, colors)
    if (item != null) item.setFootprint(footprint) else anchor.overlay.add(footprint)
    footprint.fade(1f, fadeMs)
    return { footprint.fade(0f, fadeMs) { if (item != null) item.setFootprint(null) else anchor.overlay.remove(footprint) } }
}

/** A footprint's fill and outline at full strength. */
internal class FootprintColors(val fill: Int, val stroke: Int, val strokeWidth: Float)

/** The menu card's fill and edge for an icon; the accent tint and outline for an item without one. */
internal fun footprintColors(context: Context, settings: LauncherSettings, hasIcon: Boolean): FootprintColors {
    if (!hasIcon) {
        val accent = footprintAccent(settings)
        return FootprintColors(
            ColorUtils.setAlphaComponent(accent, FOOTPRINT_FILL_ALPHA),
            ColorUtils.setAlphaComponent(accent, 0xFF),
            context.resources.getDimension(R.dimen.home_footprint_stroke),
        )
    }
    val menu = LauncherMenuStyle(context, settings).colors
    return FootprintColors(
        LauncherMenuStyle.cardFill(menu, customBackground = settings.lookFeel.menuBackgroundColor != null),
        LauncherMenuStyle.cardEdge(menu),
        LauncherMenuStyle.edgeWidth(context).toFloat(),
    )
}

/**
 * [CellLayout]'s footprint look for a single view: drawn by a [MenuAnchorIcon] under its own
 * content, or over any other view (which has no icon or label to hide).
 */
private class FootprintDrawable(private val anchor: View, private val shape: FootprintShape, colors: FootprintColors) : Drawable() {
    private val fillColor = colors.fill
    private val strokeColor = colors.stroke
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = colors.strokeWidth
    }
    /** The outline lies inside the footprint the way the menu card's lies inside the card. */
    private val edgeRect = RectF(shape.rect).apply { inset(colors.strokeWidth / 2f, colors.strokeWidth / 2f) }
    private val edgeRadius = (shape.radius - colors.strokeWidth / 2f).coerceAtLeast(0f)

    /** Redraws: the drawable has no callback when its item draws it directly. */
    private fun refresh() {
        invalidateSelf()
        anchor.invalidate()
    }
    private var strength = 0f
    private var animator: ValueAnimator? = null

    fun fade(target: Float, durationMs: Long, end: () -> Unit = {}) {
        animator?.cancel()
        animator = null
        if (durationMs <= 0L || !anchor.isAttachedToWindow) {
            strength = target
            refresh()
            end()
            return
        }
        animator = ValueAnimator.ofFloat(strength, target).apply {
            duration = durationMs
            addUpdateListener {
                strength = it.animatedValue as Float
                refresh()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: android.animation.Animator) { cancelled = true }
                override fun onAnimationEnd(animation: android.animation.Animator) { if (!cancelled) end() }
            })
            start()
        }
    }

    override fun draw(canvas: Canvas) {
        if (strength <= 0f || shape.rect.isEmpty) return
        // Sized to the anchor; keep the bounds current if it was laid out again.
        setBounds(0, 0, anchor.width, anchor.height)
        fill.color = scaled(fillColor)
        stroke.color = scaled(strokeColor)
        canvas.drawRoundRect(shape.rect, shape.radius, shape.radius, fill)
        canvas.drawRoundRect(edgeRect, edgeRadius, edgeRadius, stroke)
    }

    private fun scaled(color: Int) = ColorUtils.setAlphaComponent(color, (Color.alpha(color) * strength).toInt())

    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/**
 * The footprint color: the Style accent, else Chikku's default accent. Never the window
 * theme's `colorAccent`, which on OEM builds (One UI, MIUI) is the device skin's color.
 */
internal fun footprintAccent(settings: LauncherSettings): Int =
    settings.lookFeel.accentColor ?: CHIKKU_DEFAULT_ACCENT
