package io.github.aj4abinjacob.chikku.home

import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.GridSpec
import org.junit.Test

class CellMathTest {

    private val grid = GridSpec(columns = 4, rows = 5)

    // Content box: 10px left/top padding, 400 x 500 -> 100 x 100 cells.
    private fun cellAt(x: Float, y: Float, rtl: Boolean = false, spanX: Int = 1, spanY: Int = 1) =
        CellMath.cellAt(x, y, 10, 10, 400, 500, grid, rtl, spanX, spanY)

    @Test
    fun pointMapsToContainingCell() {
        assertThat(cellAt(15f, 15f)).isEqualTo(Cell(0, 0))
        assertThat(cellAt(150f, 260f)).isEqualTo(Cell(1, 2))
        assertThat(cellAt(409f, 509f)).isEqualTo(Cell(3, 4))
    }

    @Test
    fun cellBoundaryBelongsToNextCell() {
        assertThat(cellAt(110f, 110f)).isEqualTo(Cell(1, 1))
        assertThat(cellAt(109.9f, 109.9f)).isEqualTo(Cell(0, 0))
    }

    @Test
    fun pointsOutsideClampToEdgeCells() {
        assertThat(cellAt(-50f, -50f)).isEqualTo(Cell(0, 0))
        assertThat(cellAt(5000f, 5000f)).isEqualTo(Cell(3, 4))
    }

    @Test
    fun rtlMirrorsColumnsButNotRows() {
        assertThat(cellAt(15f, 15f, rtl = true)).isEqualTo(Cell(3, 0))
        assertThat(cellAt(409f, 260f, rtl = true)).isEqualTo(Cell(0, 2))
    }

    @Test
    fun spannedItemCentredOnPointAndKeptInsideGrid() {
        // A 2x2 item centred on the corner shared by cells (1,1)-(2,2) starts at (1,1).
        assertThat(cellAt(210f, 210f, spanX = 2, spanY = 2)).isEqualTo(Cell(1, 1))
        // Near the far edge it is pulled back so the whole span fits.
        assertThat(cellAt(405f, 505f, spanX = 2, spanY = 2)).isEqualTo(Cell(2, 3))
        // RTL: visual leftmost two columns are model x = 2..3.
        assertThat(cellAt(15f, 15f, rtl = true, spanX = 2)).isEqualTo(Cell(2, 0))
    }

    @Test
    fun spanLargerThanGridIsClamped() {
        assertThat(cellAt(200f, 200f, spanX = 9, spanY = 9)).isEqualTo(Cell(0, 0))
    }

    @Test
    fun emptyContentBoxDoesNotDivideByZero() {
        assertThat(CellMath.cellAt(5f, 5f, 0, 0, 0, 0, grid, rtl = false)).isEqualTo(Cell(0, 0))
    }

    @Test
    fun visualColumnMirrorsInRtl() {
        assertThat(CellMath.visualColumn(0, 1, grid, rtl = false)).isEqualTo(0)
        assertThat(CellMath.visualColumn(0, 1, grid, rtl = true)).isEqualTo(3)
        assertThat(CellMath.visualColumn(1, 2, grid, rtl = true)).isEqualTo(1)
    }
}
