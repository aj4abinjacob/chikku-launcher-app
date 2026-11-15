package io.github.aj4abinjacob.chikku.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import io.github.aj4abinjacob.chikku.core.model.settings.DockCorner
import io.github.aj4abinjacob.chikku.core.model.settings.DockCornerShape
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.settings.DockBackground
import io.github.aj4abinjacob.chikku.core.model.settings.DockSettings
import kotlin.math.roundToInt

/** Dock background from [DockSettings] (G03). */
object DockBackgroundStyle {

    /** [color] with its alpha multiplied by [alpha] (0..1). */
    fun applyAlpha(color: Int, alpha: Float): Int {
        val base = (color ushr 24) and 0xFF
        val a = (base * alpha.coerceIn(0f, 1f)).roundToInt().coerceIn(0, 255)
        return (a shl 24) or (color and 0x00FFFFFF)
    }

    /** Corner radius in px for a style; RECTANGLE and NONE are square. */
    fun cornerRadius(style: DockBackground, roundedRadius: Float, cardRadius: Float): Float = when (style) {
        DockBackground.NONE, DockBackground.RECTANGLE, DockBackground.CUSTOM -> 0f
        DockBackground.ROUNDED -> roundedRadius
        DockBackground.CARD -> cardRadius
    }

    /**
     * Label color for dock items: null keeps the wallpaper label color (white), which stays readable
     * on the default dark dock and on mostly see-through ones. A light, mostly opaque dock gets dark
     * labels instead of white on white.
     */
    fun labelColor(context: Context, dock: DockSettings): Int? {
        if (!dock.backgroundEnabled || dock.background == DockBackground.NONE) return null
        val base = dock.backgroundColor ?: ContextCompat.getColor(context, R.color.home_dock_background)
        val alpha = ((base ushr 24) and 0xFF) / 255f * dock.backgroundAlpha
        if (alpha < OPAQUE_ENOUGH) return null
        return if (ColorUtils.calculateLuminance(base or 0xFF000000.toInt()) > LIGHT_LUMINANCE) DARK_LABEL else null
    }

    private const val OPAQUE_ENOUGH = 0.5f
    private const val LIGHT_LUMINANCE = 0.4
    private const val DARK_LABEL = 0xFF1B1B1F.toInt()

    /** Builds the drawable, or null for [DockBackground.NONE]. */
    fun create(context: Context, dock: DockSettings): Drawable? {
        if (!dock.backgroundEnabled || dock.background == DockBackground.NONE) return null
        val res = context.resources
        val base = dock.backgroundColor ?: ContextCompat.getColor(context, R.color.home_dock_background)
        if (dock.background == DockBackground.CUSTOM) {
            val custom = CornerDockDrawable(applyAlpha(base, dock.backgroundAlpha), dock)
            return if (dock.backgroundInset) InsetDrawable(custom, (8 * res.displayMetrics.density).roundToInt()) else custom
        }
        val shape = GradientDrawable().apply {
            setColor(applyAlpha(base, dock.backgroundAlpha))
            cornerRadius = cornerRadius(
                dock.background,
                res.getDimension(R.dimen.home_dock_rounded_radius),
                res.getDimension(R.dimen.home_dock_card_radius),
            )
        }
        val styled = when (dock.background) {
            DockBackground.ROUNDED -> {
                val inset = res.getDimensionPixelSize(R.dimen.home_dock_rounded_inset)
                InsetDrawable(shape, inset)
            }
            DockBackground.CARD -> InsetDrawable(
                shape,
                res.getDimensionPixelSize(R.dimen.home_dock_card_inset_horizontal),
                res.getDimensionPixelSize(R.dimen.home_dock_card_inset_vertical),
                res.getDimensionPixelSize(R.dimen.home_dock_card_inset_horizontal),
                res.getDimensionPixelSize(R.dimen.home_dock_card_inset_vertical),
            )
            else -> shape
        }
        return if (dock.backgroundInset) InsetDrawable(styled, (8 * res.displayMetrics.density).roundToInt()) else styled
    }
}

/** Each corner occupies at most half of the shorter edge, so unequal corners cannot overlap. */
private class CornerDockDrawable(color: Int, private val dock: DockSettings) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    private val path = Path()

    override fun draw(canvas: Canvas) {
        val l = bounds.left.toFloat()
        val t = bounds.top.toFloat()
        val r = bounds.right.toFloat()
        val b = bounds.bottom.toFloat()
        val max = minOf(bounds.width(), bounds.height()) / 2f
        val tl = dock.topLeftCorner.size * max
        val tr = dock.topRightCorner.size * max
        val br = dock.bottomRightCorner.size * max
        val bl = dock.bottomLeftCorner.size * max
        path.reset()
        path.moveTo(l + tl, t)
        path.lineTo(r - tr, t)
        corner(dock.topRightCorner, r, t, r, t + tr)
        path.lineTo(r, b - br)
        corner(dock.bottomRightCorner, r, b, r - br, b)
        path.lineTo(l + bl, b)
        corner(dock.bottomLeftCorner, l, b, l, b - bl)
        path.lineTo(l, t + tl)
        corner(dock.topLeftCorner, l, t, l + tl, t)
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun corner(corner: DockCorner, x: Float, y: Float, endX: Float, endY: Float) {
        if (corner.shape == DockCornerShape.CUT) path.lineTo(endX, endY)
        else path.quadTo(x, y, endX, endY)
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Suppress("DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
