package io.github.aj4abinjacob.chikku.platform.icons

import io.github.aj4abinjacob.chikku.core.model.settings.IconShape
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/** One outline command in a square of side `size`, origin top-left. Pure data so it can be unit tested. */
sealed interface ShapeOp {
    data class MoveTo(val x: Float, val y: Float) : ShapeOp
    data class LineTo(val x: Float, val y: Float) : ShapeOp
    data class CubicTo(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val x: Float, val y: Float) : ShapeOp
    data object Close : ShapeOp
}

/**
 * Icon mask outlines for [IconShape]. Every outline is closed, fits in `[0, size]` on both axes and
 * touches all four edges. [IconShape.SYSTEM] has no outline (the platform's adaptive mask is used).
 */
object ShapeGeometry {

    /** Bezier handle length for a quarter circle of radius 1. */
    internal const val CIRCLE_HANDLE = 0.5522848f

    /** Corner radius of [IconShape.ROUNDED_SQUARE] as a fraction of the size. */
    internal const val ROUNDED_SQUARE_RADIUS = 0.25f

    /** The small (bottom-right) corner of [IconShape.TEARDROP] as a fraction of the size. */
    internal const val TEARDROP_SMALL_RADIUS = 0.1f

    /** Superellipse exponent for [IconShape.SQUIRCLE]. */
    internal const val SQUIRCLE_EXPONENT = 4.0
    internal const val SQUIRCLE_SEGMENTS = 64

    fun outline(shape: IconShape, size: Float): List<ShapeOp>? {
        if (size <= 0f) return null
        val half = size / 2f
        return when (shape) {
            IconShape.SYSTEM -> null
            IconShape.CIRCLE -> roundedRect(size, half, half, half, half)
            IconShape.SQUARE -> roundedRect(size, 0f, 0f, 0f, 0f)
            IconShape.ROUNDED_SQUARE -> (size * ROUNDED_SQUARE_RADIUS).let { r -> roundedRect(size, r, r, r, r) }
            IconShape.TEARDROP -> roundedRect(size, half, half, size * TEARDROP_SMALL_RADIUS, half)
            IconShape.SQUIRCLE -> squircle(size)
        }
    }

    /**
     * Rectangle with per-corner radii, clockwise from top-left: [topLeft], [topRight],
     * [bottomRight], [bottomLeft]. Radii are clamped to half the size.
     */
    fun roundedRect(size: Float, topLeft: Float, topRight: Float, bottomRight: Float, bottomLeft: Float): List<ShapeOp> {
        val max = size / 2f
        val tl = topLeft.coerceIn(0f, max)
        val tr = topRight.coerceIn(0f, max)
        val br = bottomRight.coerceIn(0f, max)
        val bl = bottomLeft.coerceIn(0f, max)
        val k = CIRCLE_HANDLE
        val ops = ArrayList<ShapeOp>(10)
        ops += ShapeOp.MoveTo(tl, 0f)
        ops += ShapeOp.LineTo(size - tr, 0f)
        if (tr > 0f) ops += ShapeOp.CubicTo(size - tr + tr * k, 0f, size, tr - tr * k, size, tr)
        ops += ShapeOp.LineTo(size, size - br)
        if (br > 0f) ops += ShapeOp.CubicTo(size, size - br + br * k, size - br + br * k, size, size - br, size)
        ops += ShapeOp.LineTo(bl, size)
        if (bl > 0f) ops += ShapeOp.CubicTo(bl - bl * k, size, 0f, size - bl + bl * k, 0f, size - bl)
        ops += ShapeOp.LineTo(0f, tl)
        if (tl > 0f) ops += ShapeOp.CubicTo(0f, tl - tl * k, tl - tl * k, 0f, tl, 0f)
        ops += ShapeOp.Close
        return ops
    }

    /** Superellipse |x|^n + |y|^n = r^n sampled as a polygon (smooth at icon sizes). */
    fun squircle(size: Float, segments: Int = SQUIRCLE_SEGMENTS): List<ShapeOp> {
        val r = size / 2.0
        val exponent = 2.0 / SQUIRCLE_EXPONENT
        val ops = ArrayList<ShapeOp>(segments + 2)
        for (i in 0 until segments) {
            val t = 2.0 * Math.PI * i / segments
            val c = cos(t)
            val s = sin(t)
            val x = (r + r * sign(c) * abs(c).pow(exponent)).toFloat().coerceIn(0f, size)
            val y = (r + r * sign(s) * abs(s).pow(exponent)).toFloat().coerceIn(0f, size)
            ops += if (i == 0) ShapeOp.MoveTo(x, y) else ShapeOp.LineTo(x, y)
        }
        ops += ShapeOp.Close
        return ops
    }
}
