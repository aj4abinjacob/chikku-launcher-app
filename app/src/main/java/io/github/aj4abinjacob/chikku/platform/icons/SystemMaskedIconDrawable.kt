package io.github.aj4abinjacob.chikku.platform.icons

import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import androidx.core.graphics.withClip
import io.github.aj4abinjacob.chikku.core.model.settings.IconShape
import kotlin.math.abs

/**
 * Draws a raw [AdaptiveIconDrawable] when the device's own icon mask cannot be trusted. Some ROMs
 * (MIUI) configure `config_icon_mask` as a plain square, which turns a raw adaptive icon (such as
 * an app's daily calendar icon, for which the ROM supplies no pre-rendered bitmap) into an opaque
 * square plate, and the platform draw leaves a stray outline offset from the icon.
 * [IconShaper.systemSafe] wraps such icons in this drawable, which draws the layers itself,
 * relative to its own bounds, clipped to [IconShape.CIRCLE].
 */
internal class SystemMaskedIconDrawable(
    private val source: AdaptiveIconDrawable,
    private var drawAlpha: Int = 255,
    private var drawFilter: ColorFilter? = null,
) : Drawable() {

    private var path: Path? = null

    override fun onBoundsChange(bounds: Rect) {
        // The 108dp layers extend 25% past the 72dp visible area on every side.
        val dx = bounds.width() / 4
        val dy = bounds.height() / 4
        val layers = Rect(bounds.left - dx, bounds.top - dy, bounds.right + dx, bounds.bottom + dy)
        source.background?.bounds = layers
        source.foreground?.bounds = layers
        val size = minOf(bounds.width(), bounds.height())
        path = IconShaper.path(IconShape.CIRCLE, size.toFloat())?.apply {
            offset(bounds.left.toFloat(), bounds.top.toFloat())
        }
    }

    override fun draw(canvas: Canvas) {
        val clip = path ?: return
        listOfNotNull(source.background, source.foreground).forEach {
            it.alpha = drawAlpha
            it.colorFilter = drawFilter
        }
        canvas.withClip(clip) {
            source.background?.draw(this)
            source.foreground?.draw(this)
        }
    }

    override fun setAlpha(alpha: Int) {
        drawAlpha = alpha
        invalidateSelf()
    }

    override fun getAlpha(): Int = drawAlpha

    override fun setColorFilter(colorFilter: ColorFilter?) {
        drawFilter = colorFilter
        invalidateSelf()
    }

    override fun getColorFilter(): ColorFilter? = drawFilter

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getIntrinsicWidth(): Int = source.intrinsicWidth

    override fun getIntrinsicHeight(): Int = source.intrinsicHeight

    override fun getConstantState(): ConstantState? {
        val sourceState = source.constantState ?: return null
        val alpha = drawAlpha
        val filter = drawFilter
        return object : ConstantState() {
            override fun newDrawable(): Drawable = from(sourceState.newDrawable())

            override fun newDrawable(res: Resources?): Drawable = from(sourceState.newDrawable(res))

            override fun getChangingConfigurations(): Int = sourceState.changingConfigurations

            private fun from(drawable: Drawable): Drawable =
                (drawable as? AdaptiveIconDrawable)?.let { SystemMaskedIconDrawable(it, alpha, filter) } ?: drawable
        }
    }
}

/** The platform's icon mask path data (a 100x100 viewport), or null when it cannot be read. */
internal fun systemIconMaskData(): String? = try {
    val system = Resources.getSystem()
    val id = system.getIdentifier("config_icon_mask", "string", "android")
    if (id == 0) null else system.getString(id)
} catch (_: RuntimeException) {
    null
}

/**
 * True when [pathData] (a 100x100 mask) is a straight-edged polygon (M, L and Z only) covering
 * nearly the whole viewport, i.e. a square mask, which is not a usable adaptive-icon shape.
 */
internal fun isSquareMask(pathData: String?): Boolean {
    if (pathData == null) return false
    if (pathData.any { it.isLetter() && it.uppercaseChar() !in "MLZ" }) return false
    // Relative moves are not parsed.
    if (pathData.any { it == 'm' || it == 'l' }) return false
    val numbers = Regex("-?\\d+(\\.\\d+)?").findAll(pathData).map { it.value.toFloat() }.toList()
    if (numbers.size < 6 || numbers.size % 2 != 0) return false
    val points = numbers.chunked(2)
    var twiceArea = 0f
    for (i in points.indices) {
        val (x1, y1) = points[i]
        val (x2, y2) = points[(i + 1) % points.size]
        twiceArea += x1 * y2 - x2 * y1
    }
    return abs(twiceArea) / 2f >= SQUARE_AREA_FRACTION * 100f * 100f
}

private const val SQUARE_AREA_FRACTION = 0.95f
