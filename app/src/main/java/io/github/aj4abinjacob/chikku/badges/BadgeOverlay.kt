package io.github.aj4abinjacob.chikku.badges

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.ColorUtils
import io.github.aj4abinjacob.chikku.core.model.settings.BadgeStyle
import io.github.aj4abinjacob.chikku.core.model.settings.BadgePosition

/**
 * What [io.github.aj4abinjacob.chikku.home.AppIconView] draws over an icon corner.
 * Immutable; a new instance replaces the old one when the badge changes.
 */
class BadgeOverlay(
    val style: BadgeStyle,
    val count: Int,
    val color: Int,
    val avatar: Bitmap? = null,
    val position: BadgePosition = BadgePosition.TOP_END,
    badgeScale: Float = 1f,
) {
    val badgeScale: Float = if (badgeScale.isFinite()) badgeScale.coerceIn(0.5f, 1.5f) else 1f
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = this@BadgeOverlay.color }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = Color.WHITE
        this.style = Paint.Style.STROKE
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        this.color = if (ColorUtils.calculateLuminance(this@BadgeOverlay.color) > 0.5) Color.BLACK else Color.WHITE
    }
    private val avatarPaint = avatar?.let { bmp ->
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            shader = BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
    }
    // The overlay is immutable, so format the count once instead of on every draw.
    private val label = formatBadgeCount(count)
    private val matrix = Matrix()
    private val rect = RectF()

    /**
     * Draws relative to the icon square at ([iconLeft], [iconTop]) of [iconSize]. [rtl] mirrors the
     * start/end corner along with the layout direction.
     */
    fun draw(canvas: Canvas, iconLeft: Int, iconTop: Int, iconSize: Int, rtl: Boolean) {
        if (iconSize <= 0 || style == BadgeStyle.OFF) return
        val end = position == BadgePosition.TOP_END || position == BadgePosition.BOTTOM_END
        val onLeft = if (end) rtl else !rtl
        val bottom = position == BadgePosition.BOTTOM_END || position == BadgePosition.BOTTOM_START
        when {
            style == BadgeStyle.DYNAMIC && avatarPaint != null && avatar != null -> {
                val d = iconSize * AVATAR_FRACTION * badgeScale
                val cx = corner(iconLeft, iconSize, d, onLeft)
                val cy = if (bottom) iconTop + iconSize - d / 2f + iconSize * INSET_FRACTION else iconTop + d / 2f - iconSize * INSET_FRACTION
                val scale = d / minOf(avatar.width, avatar.height)
                matrix.setScale(scale, scale)
                matrix.postTranslate(cx - avatar.width * scale / 2f, cy - avatar.height * scale / 2f)
                avatarPaint.shader.setLocalMatrix(matrix)
                canvas.drawCircle(cx, cy, d / 2f, avatarPaint)
                ring.strokeWidth = d * RING_FRACTION
                canvas.drawCircle(cx, cy, d / 2f, ring)
            }
            (style == BadgeStyle.COUNT || style == BadgeStyle.DYNAMIC) && label.isNotEmpty() -> {
                val h = iconSize * COUNT_FRACTION * badgeScale
                text.textSize = h * TEXT_FRACTION
                val w = maxOf(h, text.measureText(label) + h * 0.5f)
                val cx = corner(iconLeft, iconSize, w, onLeft)
                val top = if (bottom) iconTop + iconSize - h + iconSize * INSET_FRACTION else iconTop - iconSize * INSET_FRACTION
                rect.set(cx - w / 2f, top, cx + w / 2f, top + h)
                canvas.drawRoundRect(rect, h / 2f, h / 2f, fill)
                val baseline = rect.centerY() - (text.descent() + text.ascent()) / 2f
                canvas.drawText(label, cx, baseline, text)
            }
            else -> {
                val d = iconSize * DOT_FRACTION * badgeScale
                val cx = corner(iconLeft, iconSize, d, onLeft)
                val cy = if (bottom) iconTop + iconSize - d / 2f else iconTop + d / 2f
                canvas.drawCircle(cx, cy, d / 2f, fill)
            }
        }
    }

    /** Centre x of a badge of width [w] hugging the chosen horizontal corner. */
    private fun corner(iconLeft: Int, iconSize: Int, w: Float, onLeft: Boolean): Float {
        val inset = iconSize * INSET_FRACTION
        return if (onLeft) iconLeft + w / 2f - inset else iconLeft + iconSize - w / 2f + inset
    }

    private companion object {
        const val DOT_FRACTION = 0.26f
        const val COUNT_FRACTION = 0.36f
        const val AVATAR_FRACTION = 0.44f
        const val TEXT_FRACTION = 0.68f
        const val RING_FRACTION = 0.08f
        const val INSET_FRACTION = 0.06f
    }
}
