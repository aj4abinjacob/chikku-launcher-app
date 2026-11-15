package io.github.aj4abinjacob.chikku.platform.icons

import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.settings.IconShape
import org.junit.Test
import kotlin.math.hypot

/** Pure geometry: no Android classes involved. */
class ShapeGeometryTest {

    private val size = 100f

    private fun points(ops: List<ShapeOp>): List<Pair<Float, Float>> = ops.flatMap { op ->
        when (op) {
            is ShapeOp.MoveTo -> listOf(op.x to op.y)
            is ShapeOp.LineTo -> listOf(op.x to op.y)
            is ShapeOp.CubicTo -> listOf(op.x1 to op.y1, op.x2 to op.y2, op.x to op.y)
            ShapeOp.Close -> emptyList()
        }
    }

    @Test
    fun systemShapeHasNoOutline() {
        assertThat(ShapeGeometry.outline(IconShape.SYSTEM, size)).isNull()
    }

    @Test
    fun nonPositiveSizeHasNoOutline() {
        IconShape.entries.forEach { assertThat(ShapeGeometry.outline(it, 0f)).isNull() }
    }

    @Test
    fun everyOutlineIsClosedFitsAndTouchesAllEdges() {
        IconShape.entries.filter { it != IconShape.SYSTEM }.forEach { shape ->
            val ops = requireNotNull(ShapeGeometry.outline(shape, size))
            assertThat(ops.first()).isInstanceOf(ShapeOp.MoveTo::class.java)
            assertThat(ops.last()).isEqualTo(ShapeOp.Close)
            assertThat(ops.count { it is ShapeOp.MoveTo }).isEqualTo(1)
            val pts = points(ops)
            pts.forEach { (x, y) ->
                assertThat(x).isAtLeast(0f)
                assertThat(x).isAtMost(size)
                assertThat(y).isAtLeast(0f)
                assertThat(y).isAtMost(size)
            }
            val tolerance = 0.01f
            assertThat(pts.minOf { it.first }).isWithin(tolerance).of(0f)
            assertThat(pts.maxOf { it.first }).isWithin(tolerance).of(size)
            assertThat(pts.minOf { it.second }).isWithin(tolerance).of(0f)
            assertThat(pts.maxOf { it.second }).isWithin(tolerance).of(size)
        }
    }

    @Test
    fun squareIsTheBoundingBox() {
        val corners = points(requireNotNull(ShapeGeometry.outline(IconShape.SQUARE, size))).toSet()
        assertThat(corners).containsAtLeast(0f to 0f, size to 0f, size to size, 0f to size)
        assertThat(ShapeGeometry.outline(IconShape.SQUARE, size)!!.none { it is ShapeOp.CubicTo }).isTrue()
    }

    @Test
    fun circleCornersAreQuarterArcs() {
        val ops = requireNotNull(ShapeGeometry.outline(IconShape.CIRCLE, size))
        assertThat(ops.count { it is ShapeOp.CubicTo }).isEqualTo(4)
        // Every on-curve point of a circle lies on the radius.
        ops.filterIsInstance<ShapeOp.CubicTo>().forEach { arc ->
            assertThat(hypot(arc.x - 50f, arc.y - 50f)).isWithin(0.01f).of(50f)
        }
    }

    @Test
    fun roundedSquareUsesItsCornerRadius() {
        val ops = requireNotNull(ShapeGeometry.outline(IconShape.ROUNDED_SQUARE, size))
        val r = size * ShapeGeometry.ROUNDED_SQUARE_RADIUS
        assertThat(ops.first()).isEqualTo(ShapeOp.MoveTo(r, 0f))
        assertThat(ops).contains(ShapeOp.LineTo(size - r, 0f))
    }

    @Test
    fun teardropHasOneSmallCorner() {
        val ops = requireNotNull(ShapeGeometry.outline(IconShape.TEARDROP, size))
        val small = size * ShapeGeometry.TEARDROP_SMALL_RADIUS
        // Right edge runs down to where the small bottom-right corner starts.
        assertThat(ops).contains(ShapeOp.LineTo(size, size - small))
        assertThat(ops.count { it is ShapeOp.CubicTo }).isEqualTo(4)
    }

    @Test
    fun squircleBulgesBeyondTheCircle() {
        val ops = ShapeGeometry.squircle(size, segments = 8)
        assertThat(ops).hasSize(9) // 8 points + close
        // At 45 degrees a superellipse (n=4) reaches further out than a circle.
        val diagonal = ops[1] as ShapeOp.LineTo
        assertThat(hypot(diagonal.x - 50f, diagonal.y - 50f)).isGreaterThan(50f)
    }

    @Test
    fun radiiAreClampedToHalfTheSize() {
        val ops = ShapeGeometry.roundedRect(size, 500f, 500f, 500f, 500f)
        assertThat(ops).isEqualTo(ShapeGeometry.roundedRect(size, 50f, 50f, 50f, 50f))
    }
}
