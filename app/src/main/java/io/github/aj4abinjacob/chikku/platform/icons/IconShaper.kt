package io.github.aj4abinjacob.chikku.platform.icons

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.withClip
import androidx.core.graphics.withTranslation
import io.github.aj4abinjacob.chikku.core.model.settings.IconShape

/**
 * Applies the user's icon shape. [IconShape.SYSTEM] keeps adaptive icons as the platform masks
 * them; any other shape clips the adaptive layers with [ShapeGeometry]. With `reshapeLegacy`,
 * non-adaptive icons are first wrapped in an adaptive container on a light background so they
 * take the same shape as everything else.
 */
object IconShaper {

    /** Inset of a legacy icon inside the 108dp adaptive foreground, so it sits in the visible 72dp area. */
    internal const val LEGACY_INSET_FRACTION = 0.22f
    internal const val LEGACY_BACKGROUND = 0xFFFFFFFF.toInt()

    fun apply(icon: Drawable, shape: IconShape, reshapeLegacy: Boolean): Drawable {
        val adaptive = when {
            icon is AdaptiveIconDrawable -> icon
            reshapeLegacy -> wrapLegacy(icon)
            else -> return icon
        }
        return if (shape == IconShape.SYSTEM) systemSafe(adaptive) else ShapedIconDrawable(adaptive, shape)
    }

    /** Whether this device's adaptive-icon mask is a plain square (read once; see [systemSafe]). */
    val systemMaskIsSquare: Boolean get() = squareMaskOverride ?: detectedSquareMask

    private val detectedSquareMask: Boolean by lazy { isSquareMask(systemIconMaskData()) }

    @Volatile private var squareMaskOverride: Boolean? = null

    /** Lets tests pretend the device mask is (or is not) square; null restores the real mask. */
    internal fun overrideSquareMaskForTest(square: Boolean?) {
        squareMaskOverride = square
    }

    /**
     * [icon] unchanged, except a raw [AdaptiveIconDrawable] on a ROM whose icon mask is a plain
     * square (MIUI): the platform would draw an opaque square plate plus a stray outline, so it is
     * drawn as a circle by [SystemMaskedIconDrawable]. Tests pass [squareMask] explicitly.
     */
    fun systemSafe(icon: Drawable, squareMask: Boolean = systemMaskIsSquare): Drawable =
        if (icon is AdaptiveIconDrawable && squareMask) SystemMaskedIconDrawable(icon) else icon

    /** Inset of a shortcut glyph on its plate: the glyph covers about 56% of the visible icon. */
    internal const val GLYPH_INSET_FRACTION = 0.31f

    /** Plate behind a light glyph, which would vanish on [LEGACY_BACKGROUND]. */
    internal const val DARK_PLATE = 0xFF3B3B42.toInt()

    /** Opaque-pixel share at or above which a non-adaptive icon already fills its own shape. */
    private const val FULL_BLEED_COVERAGE = 0.7f
    private const val SAMPLE_SIZE = 32

    /** Alpha below which a pixel lets the surface behind it show. */
    private const val OPAQUE_ALPHA = 0xF0

    /** Share of see-through pixels in the visible area at which an adaptive icon gets a plate. */
    private const val TRANSLUCENT_SHARE = 0.25f

    /**
     * Makes an app shortcut icon readable on any surface, as Launcher3 does. Shortcut icons are
     * often bare glyphs (a black "+" or clock) that the app expects the launcher to put on a plate;
     * drawn directly they disappear on a dark menu or wallpaper (or a white glyph on a light one).
     * Adaptive icons and full-bleed images (contact photos) keep their own look; glyphs go on a
     * white plate, or a dark one when the glyph itself is light. Rasterises a small sample, so
     * call it off the main thread.
     */
    fun normalizeShortcut(icon: Drawable, shape: IconShape): Drawable {
        if (icon is AdaptiveIconDrawable) return apply(plateTranslucent(icon), shape, reshapeLegacy = false)
        val glyph = analyze(icon) ?: return icon
        if (glyph.coverage >= FULL_BLEED_COVERAGE) return icon
        val plate = if (glyph.luminance > 0.6) DARK_PLATE else LEGACY_BACKGROUND
        return apply(AdaptiveIconDrawable(plate.toDrawable(), InsetDrawable(icon, GLYPH_INSET_FRACTION)), shape, reshapeLegacy = false)
    }

    /**
     * Adaptive bitmaps (browser site shortcuts) arrive with no background layer, and some are
     * partly transparent, so the wallpaper shows through. Puts such an icon on an opaque plate,
     * dark when its most opaque content is light; opaque adaptive icons are returned unchanged.
     */
    private fun plateTranslucent(icon: AdaptiveIconDrawable): AdaptiveIconDrawable {
        if (icon.background?.opacity == PixelFormat.OPAQUE) return icon
        // Sample the layers, not the masked composite: how the platform fills behind the layers
        // differs between versions. The central 40% of a 108dp layer lies inside every mask.
        val bitmap = createBitmap(SAMPLE_SIZE, SAMPLE_SIZE)
        try {
            val canvas = Canvas(bitmap)
            listOfNotNull(icon.background, icon.foreground).forEach { layer ->
                val bounds = layer.copyBounds()
                layer.setBounds(0, 0, SAMPLE_SIZE, SAMPLE_SIZE)
                layer.draw(canvas)
                layer.bounds = bounds
            }
        } catch (_: RuntimeException) {
            return icon
        }
        val from = (SAMPLE_SIZE * 0.3f).toInt()
        val size = SAMPLE_SIZE - 2 * from
        val pixels = IntArray(size * size)
        bitmap.getPixels(pixels, 0, size, from, from, size, size)
        val translucent = pixels.count { it ushr 24 < OPAQUE_ALPHA }
        if (translucent < pixels.size * TRANSLUCENT_SHARE) return icon
        val strongest = pixels.maxOf { it ushr 24 }
        if (strongest == 0) return icon
        val luminance = pixels.filter { it ushr 24 >= strongest * 0.8f }
            .map { ColorUtils.calculateLuminance(it or 0xFF000000.toInt()) }
            .average()
        val plate = if (luminance > 0.6) DARK_PLATE else LEGACY_BACKGROUND
        val content = LayerDrawable(listOfNotNull(icon.background, icon.foreground).toTypedArray())
        return AdaptiveIconDrawable(plate.toDrawable(), content)
    }

    private class GlyphSample(val coverage: Float, val luminance: Double)

    /** Opaque coverage and mean luminance of opaque pixels; null when there is nothing to sample. */
    private fun analyze(icon: Drawable): GlyphSample? {
        val bitmap = try {
            icon.toBitmap(SAMPLE_SIZE, SAMPLE_SIZE)
        } catch (_: RuntimeException) {
            return null
        }
        val pixels = IntArray(SAMPLE_SIZE * SAMPLE_SIZE)
        bitmap.getPixels(pixels, 0, SAMPLE_SIZE, 0, 0, SAMPLE_SIZE, SAMPLE_SIZE)
        var opaque = 0
        var luminance = 0.0
        pixels.forEach { pixel ->
            if (pixel ushr 24 >= 128) {
                opaque++
                luminance += ColorUtils.calculateLuminance(pixel or 0xFF000000.toInt())
            }
        }
        if (opaque == 0) return null
        return GlyphSample(opaque.toFloat() / pixels.size, luminance / opaque)
    }

    fun wrapLegacy(icon: Drawable): AdaptiveIconDrawable =
        AdaptiveIconDrawable(LEGACY_BACKGROUND.toDrawable(), InsetDrawable(icon, LEGACY_INSET_FRACTION))

    /** Android [Path] for [shape] in a square of [size], or null for [IconShape.SYSTEM]. */
    fun path(shape: IconShape, size: Float): Path? = ShapeGeometry.outline(shape, size)?.toPath()

    fun List<ShapeOp>.toPath(): Path = Path().also { path ->
        forEach { op ->
            when (op) {
                is ShapeOp.MoveTo -> path.moveTo(op.x, op.y)
                is ShapeOp.LineTo -> path.lineTo(op.x, op.y)
                is ShapeOp.CubicTo -> path.cubicTo(op.x1, op.y1, op.x2, op.y2, op.x, op.y)
                ShapeOp.Close -> path.close()
            }
        }
    }
}

/**
 * Draws an [AdaptiveIconDrawable]'s layers clipped to [shape]. Vector-based, so it stays sharp at
 * any icon size and keeps no bitmap in memory.
 */
class ShapedIconDrawable(
    private val source: AdaptiveIconDrawable,
    val shape: IconShape,
) : Drawable() {

    /** Shape outline in local coordinates: origin at the top-left of the bounds, square centred. */
    private var path: Path? = null

    override fun onBoundsChange(bounds: Rect) {
        // AdaptiveIconDrawable lays its layers out around the centre of a box that starts at
        // (0, 0): it renders them into an offscreen bitmap and blits that at bounds.left/top. Its
        // layers therefore ignore the bounds' offset, so give it a zero-based box of the same size
        // and move the canvas to the real position when drawing.
        source.setBounds(0, 0, bounds.width(), bounds.height())
        val size = minOf(bounds.width(), bounds.height())
        path = IconShaper.path(shape, size.toFloat())?.apply {
            offset((bounds.width() - size) / 2f, (bounds.height() - size) / 2f)
        }
    }

    override fun draw(canvas: Canvas) {
        val clip = path ?: return
        val b = bounds
        canvas.withTranslation(b.left.toFloat(), b.top.toFloat()) {
            withClip(clip) {
                source.background?.draw(this)
                source.foreground?.draw(this)
            }
        }
    }

    override fun setAlpha(alpha: Int) {
        source.alpha = alpha
        source.background?.alpha = alpha
        source.foreground?.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        source.colorFilter = colorFilter
        source.background?.colorFilter = colorFilter
        source.foreground?.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getIntrinsicWidth(): Int = source.intrinsicWidth

    override fun getIntrinsicHeight(): Int = source.intrinsicHeight

    override fun getConstantState(): ConstantState? {
        val sourceState = source.constantState ?: return null
        return State(sourceState, shape)
    }

    private class State(private val sourceState: ConstantState, private val shape: IconShape) : ConstantState() {
        override fun newDrawable(): Drawable = from(sourceState.newDrawable())

        override fun newDrawable(res: android.content.res.Resources?): Drawable = from(sourceState.newDrawable(res))

        override fun getChangingConfigurations(): Int = sourceState.changingConfigurations

        private fun from(drawable: Drawable): Drawable =
            (drawable as? AdaptiveIconDrawable)?.let { ShapedIconDrawable(it, shape) } ?: drawable
    }
}
