package io.github.aj4abinjacob.chikku.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Reads only a user-selected, persistently granted document. Bounded decode on IO; no raw URI fetch. */
class DockImageLoader(private val context: Context, private val io: CoroutineDispatcher) {
    suspend fun load(value: String?): Bitmap? = withContext(io) {
        if (value == null || value.length > 2048) return@withContext null
        val uri = Uri.parse(value)
        if (uri.scheme != "content") return@withContext null
        try {
            val resolver = context.contentResolver
            if (resolver.persistedUriPermissions.none { it.uri == uri && it.isReadPermission }) return@withContext null
            if (resolver.getType(uri)?.startsWith("image/") != true) return@withContext null
            val bytes = resolver.openInputStream(uri)?.use { stream ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var count = 0
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    count += read
                    if (count > MAX_BYTES) return@withContext null
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            } ?: return@withContext null
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            if (options.outWidth !in 1..20000 || options.outHeight !in 1..20000) return@withContext null
            var sample = 1
            while (maxOf(options.outWidth, options.outHeight) / sample > 1024) sample *= 2
            options.inJustDecodeBounds = false
            options.inSampleSize = sample
            options.inPreferredConfig = Bitmap.Config.ARGB_8888
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    companion object { const val MAX_BYTES = 8 * 1024 * 1024 }
}

/**
 * Centre-crops a photo through the existing background's shape, inset and opacity mask. The masked
 * photo is composed once per size into one bitmap and drawn as-is afterwards, instead of an
 * offscreen layer on every frame.
 */
internal class DockImageDrawable(private val mask: Drawable, private val bitmap: Bitmap) : Drawable() {
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    }
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private var masked: Bitmap? = null

    /** Number of masked bitmaps composed; for tests. */
    internal var compositions = 0
        private set

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        val old = masked
        if (old != null && (old.width != bounds.width() || old.height != bounds.height())) masked = null
    }

    override fun draw(canvas: Canvas) {
        if (bounds.isEmpty) return
        val composed = masked ?: compose(bounds.width(), bounds.height())?.also { masked = it }
        if (composed != null) {
            canvas.drawBitmap(composed, bounds.left.toFloat(), bounds.top.toFloat(), paint)
            return
        }
        // Too large to keep composed: fall back to masking through a layer.
        val save = canvas.saveLayer(bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(), bounds.bottom.toFloat(), paint)
        drawMasked(canvas, bounds)
        canvas.restoreToCount(save)
    }

    private fun compose(width: Int, height: Int): Bitmap? {
        if (width > MAX_EDGE || height > MAX_EDGE) return null
        val result = try {
            createBitmap(width, height)
        } catch (_: OutOfMemoryError) {
            return null
        }
        drawMasked(Canvas(result), Rect(0, 0, width, height))
        compositions++
        return result
    }

    private fun drawMasked(canvas: Canvas, area: Rect) {
        mask.bounds = area
        mask.draw(canvas)
        val scale = maxOf(area.width().toFloat() / bitmap.width, area.height().toFloat() / bitmap.height)
        val width = (area.width() / scale).toInt().coerceIn(1, bitmap.width)
        val height = (area.height() / scale).toInt().coerceIn(1, bitmap.height)
        val left = (bitmap.width - width) / 2
        val top = (bitmap.height - height) / 2
        canvas.drawBitmap(bitmap, Rect(left, top, left + width, top + height), area, maskPaint)
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Suppress("DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private companion object {
        /** Larger than any dock; a bigger surface draws through a layer instead of a cached bitmap. */
        const val MAX_EDGE = 4096
    }
}
