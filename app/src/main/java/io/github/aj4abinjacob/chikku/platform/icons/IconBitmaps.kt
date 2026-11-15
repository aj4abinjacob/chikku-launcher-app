package io.github.aj4abinjacob.chikku.platform.icons

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import androidx.core.graphics.createBitmap

/**
 * Turns a loaded icon (adaptive, shaped, vector, badged...) into one immutable ARGB_8888 bitmap of
 * [sizePx] square, so the icon's layers, masks and vector caches are released right after loading
 * and every view showing the icon shares the same pixels (and the same GPU texture).
 *
 * Software config on purpose: drag shadows, folder previews, menus and Compose `toBitmap` callers
 * draw icons into software canvases, which cannot read [Bitmap.Config.HARDWARE] bitmaps.
 * Call off the main thread.
 */
internal object IconRasterizer {
    fun rasterize(icon: Drawable, sizePx: Int): Bitmap {
        require(sizePx > 0) { "icon size must be positive" }
        val scratch = createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(scratch)
        val bounds = icon.copyBounds()
        icon.setBounds(0, 0, sizePx, sizePx)
        icon.draw(canvas)
        icon.bounds = bounds
        canvas.setBitmap(null)
        val shared = scratch.copy(Bitmap.Config.ARGB_8888, false) ?: return scratch
        scratch.recycle()
        shared.setHasMipMap(true) // drawer rows and folder previews draw it well below its size
        return shared
    }
}

/**
 * Light per-view drawable over a shared, immutable icon [bitmap]: each instance has its own bounds,
 * alpha and colour filter (no shared paint state to [mutate]), while the pixels are shared by every
 * instance made from the same bitmap. The bitmap is scaled into the bounds with filtering.
 */
class IconBitmapDrawable(val bitmap: Bitmap) : Drawable() {

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply { alpha = 0xFF }

    override fun draw(canvas: Canvas) {
        if (bitmap.isRecycled || bounds.isEmpty) return
        canvas.drawBitmap(bitmap, null, bounds, paint)
    }

    override fun setAlpha(alpha: Int) {
        if (paint.alpha != alpha) {
            paint.alpha = alpha
            invalidateSelf()
        }
    }

    override fun getAlpha(): Int = paint.alpha

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    override fun getColorFilter(): ColorFilter? = paint.colorFilter

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getIntrinsicWidth(): Int = bitmap.width

    override fun getIntrinsicHeight(): Int = bitmap.height

    /** Already private to this instance; only the immutable bitmap is shared. */
    override fun mutate(): Drawable = this

    override fun getConstantState(): ConstantState = State(bitmap)

    private class State(private val bitmap: Bitmap) : ConstantState() {
        override fun newDrawable(): Drawable = IconBitmapDrawable(bitmap)

        override fun getChangingConfigurations(): Int = 0
    }
}
