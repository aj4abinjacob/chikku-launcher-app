package io.github.aj4abinjacob.chikku.home

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Drawable

/**
 * Decorative contrast behind system bars; does not receive input or read wallpaper pixels.
 * The two gradients are built once per bounds and reused on every frame.
 */
internal class ScreenEdgeShadow(private val depthPx: Float, private val color: Int = DEFAULT_COLOR) : Drawable() {
    private val topPaint = Paint()
    private val bottomPaint = Paint()
    private val shadedBounds = Rect()
    private var depth = 0f

    /** Number of times the gradients were (re)built; for tests. */
    internal var shaderBuilds = 0
        private set

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        shadedBounds.setEmpty() // rebuild on next draw
    }

    override fun draw(canvas: Canvas) {
        val rect = bounds
        if (rect != shadedBounds) buildShaders(rect)
        if (depth <= 0) return
        canvas.drawRect(rect.left.toFloat(), rect.top.toFloat(), rect.right.toFloat(), rect.top + depth, topPaint)
        canvas.drawRect(rect.left.toFloat(), rect.bottom - depth, rect.right.toFloat(), rect.bottom.toFloat(), bottomPaint)
    }

    private fun buildShaders(rect: Rect) {
        shadedBounds.set(rect)
        depth = depthPx.coerceAtMost(rect.height() / 3f)
        if (depth <= 0) return
        shaderBuilds++
        topPaint.shader = LinearGradient(0f, rect.top.toFloat(), 0f, rect.top + depth, color, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        bottomPaint.shader = LinearGradient(0f, rect.bottom - depth, 0f, rect.bottom.toFloat(), Color.TRANSPARENT, color, Shader.TileMode.CLAMP)
    }

    override fun setAlpha(alpha: Int) {
        topPaint.alpha = alpha
        bottomPaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        topPaint.colorFilter = colorFilter
        bottomPaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Android")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private companion object {
        const val DEFAULT_COLOR = 0x66000000
    }
}
