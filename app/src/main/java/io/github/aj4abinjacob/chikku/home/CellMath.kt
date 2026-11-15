package io.github.aj4abinjacob.chikku.home

import io.github.aj4abinjacob.chikku.core.model.GridSpec

/** A cell coordinate in model space (x counts from the start edge, so it is RTL-independent). */
data class Cell(val x: Int, val y: Int)

/** Pure grid geometry shared by [CellLayout] layout and drop targeting. */
object CellMath {

    /**
     * Returns the top-start cell for an item of [spanX] x [spanY] whose centre is under the point
     * ([px], [py]), given the grid's content box. Points outside the box clamp to the nearest edge
     * cell, and the result always leaves room for the whole span inside the grid.
     *
     * In RTL, model x = 0 is the rightmost visual column.
     */
    fun cellAt(
        px: Float,
        py: Float,
        contentLeft: Int,
        contentTop: Int,
        contentWidth: Int,
        contentHeight: Int,
        grid: GridSpec,
        rtl: Boolean,
        spanX: Int = 1,
        spanY: Int = 1,
    ): Cell {
        val sx = spanX.coerceIn(1, grid.columns)
        val sy = spanY.coerceIn(1, grid.rows)
        val cellW = contentWidth.toFloat() / grid.columns
        val cellH = contentHeight.toFloat() / grid.rows
        if (cellW <= 0f || cellH <= 0f) return Cell(0, 0)

        // Shift the point from the item's centre to the centre of its first cell.
        val visualCol = ((px - contentLeft) / cellW - (sx - 1) / 2f).toFloorInt()
            .coerceIn(0, grid.columns - sx)
        val row = ((py - contentTop) / cellH - (sy - 1) / 2f).toFloorInt()
            .coerceIn(0, grid.rows - sy)
        val x = if (rtl) grid.columns - sx - visualCol else visualCol
        return Cell(x, row)
    }

    /** Visual column index, counted from the left, of a model cell; mirrors in RTL. */
    fun visualColumn(x: Int, spanX: Int, grid: GridSpec, rtl: Boolean): Int =
        if (rtl) grid.columns - x - spanX else x

    /**
     * True if an item at model ([x], [y]) with the given span lies entirely inside the visible
     * window: model columns `[columnOffset, columnOffset + grid.columns)` and rows `[0, grid.rows)`.
     */
    fun isInWindow(x: Int, y: Int, spanX: Int, spanY: Int, columnOffset: Int, grid: GridSpec): Boolean =
        x >= columnOffset && y >= 0 &&
            x + spanX <= columnOffset + grid.columns && y + spanY <= grid.rows

    private fun Float.toFloorInt(): Int = kotlin.math.floor(this).toInt()
}
