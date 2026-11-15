package io.github.aj4abinjacob.chikku.home.folder

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import androidx.core.graphics.withRotation
import io.github.aj4abinjacob.chikku.core.model.settings.FolderIconBackground
import io.github.aj4abinjacob.chikku.core.model.settings.FolderPreviewStyle
import kotlin.math.min
import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * Folder icon: an optional circle/rounded-square background with up to nine preview icons laid out
 * as [FolderPreviewStyle] (a compact grid, diagonal stack, fan or a line). Each preview must be a drawable
 * owned by this icon (not shared), because its bounds are set while drawing.
 */
class FolderIconDrawable(
    private val previews: List<Drawable>,
    private val style: FolderPreviewStyle,
    private val background: FolderIconBackground,
    backgroundColor: Int,
) : Drawable() {

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = backgroundColor }
    private val rect = RectF()

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val size = min(b.width(), b.height()).toFloat()
        val left = b.exactCenterX() - size / 2f
        val top = b.exactCenterY() - size / 2f
        rect.set(left, top, left + size, top + size)
        when (background) {
            FolderIconBackground.NONE -> Unit
            FolderIconBackground.CIRCLE -> canvas.drawOval(rect, backgroundPaint)
            FolderIconBackground.ROUNDED_SQUARE -> {
                val radius = size * ROUNDED_RADIUS
                canvas.drawRoundRect(rect, radius, radius, backgroundPaint)
            }
        }
        // Keep previews inside a circular background's inscribed square.
        val inset = if (background == FolderIconBackground.CIRCLE) size * CIRCLE_INSET else size * SQUARE_INSET
        rect.inset(inset, inset)
        when (style) {
            FolderPreviewStyle.GRID -> drawGrid(canvas)
            FolderPreviewStyle.STACK -> drawStack(canvas)
            FolderPreviewStyle.FAN -> drawFan(canvas)
            FolderPreviewStyle.LINE -> drawLine(canvas)
        }
    }

    private fun drawGrid(canvas: Canvas) {
        val shown = previews.take(GRID_COUNT)
        if (shown.isEmpty()) return
        val columns = ceil(sqrt(shown.size.toDouble())).toInt()
        val gap = rect.width() * GRID_GAP / columns
        val cell = (rect.width() - gap * (columns - 1)) / columns
        shown.forEachIndexed { i, d ->
            val x = rect.left + (i % columns) * (cell + gap)
            val y = rect.top + (i / columns) * (cell + gap)
            drawAt(canvas, d, x, y, cell)
        }
    }

    private fun drawStack(canvas: Canvas) {
        val shown = previews.take(STACK_COUNT)
        if (shown.isEmpty()) return
        val icon = rect.width() * STACK_ICON
        val step = if (shown.size > 1) (rect.width() - icon) / (shown.size - 1) else 0f
        // Back to front: the first entry ends up on top, bottom-left.
        shown.indices.reversed().forEach { i ->
            val x = rect.left + step * (shown.size - 1 - i)
            val y = rect.top + step * i
            drawAt(canvas, shown[i], x, y, icon)
        }
    }

    private fun drawFan(canvas: Canvas) {
        val shown = previews.take(STACK_COUNT)
        if (shown.isEmpty()) return
        val icon = rect.width() * FAN_ICON
        val x = rect.centerX() - icon / 2f
        val y = rect.centerY() - icon / 2f
        val pivotX = rect.centerX()
        val pivotY = rect.bottom + rect.height() * FAN_PIVOT_BELOW
        // Outer icons first so the first entry is drawn upright on top.
        val angles = when (shown.size) {
            1 -> listOf(0f)
            2 -> listOf(0f, FAN_ANGLE)
            else -> listOf(0f, -FAN_ANGLE, FAN_ANGLE)
        }
        shown.indices.reversed().forEach { i ->
            canvas.withRotation(angles[i], pivotX, pivotY) { drawAt(this, shown[i], x, y, icon) }
        }
    }

    private fun drawLine(canvas: Canvas) {
        val shown = previews.take(STACK_COUNT)
        if (shown.isEmpty()) return
        val gap = rect.width() * GRID_GAP
        val icon = (rect.width() - gap * (STACK_COUNT - 1)) / STACK_COUNT
        val total = shown.size * icon + (shown.size - 1) * gap
        var x = rect.centerX() - total / 2f
        val y = rect.centerY() - icon / 2f
        shown.forEach { d ->
            drawAt(canvas, d, x, y, icon)
            x += icon + gap
        }
    }

    private fun drawAt(canvas: Canvas, d: Drawable, x: Float, y: Float, size: Float) {
        d.setBounds(x.toInt(), y.toInt(), (x + size).toInt(), (y + size).toInt())
        d.draw(canvas)
    }

    override fun setAlpha(alpha: Int) {
        backgroundPaint.alpha = alpha
        previews.forEach { it.alpha = alpha }
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        previews.forEach { it.colorFilter = colorFilter }
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private companion object {
        const val ROUNDED_RADIUS = 0.28f
        const val CIRCLE_INSET = 0.18f
        const val SQUARE_INSET = 0.12f
        const val GRID_GAP = 0.08f
        const val GRID_COUNT = 9
        const val STACK_COUNT = 3
        const val STACK_ICON = 0.7f
        const val FAN_ICON = 0.62f
        const val FAN_PIVOT_BELOW = 0.6f
        const val FAN_ANGLE = 14f
    }
}
