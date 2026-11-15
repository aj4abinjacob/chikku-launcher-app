package io.github.aj4abinjacob.chikku.core.model.layout

import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.CellPlacement
import io.github.aj4abinjacob.chikku.core.model.ContainerType.DESKTOP
import io.github.aj4abinjacob.chikku.core.model.ContainerType.DOCK
import io.github.aj4abinjacob.chikku.core.model.GridSpec
import io.github.aj4abinjacob.chikku.core.model.ItemTarget
import io.github.aj4abinjacob.chikku.core.model.WorkspaceItem
import org.junit.Test

class WorkspaceLayoutEngineTest {
    private val engine = WorkspaceLayoutEngine
    private val grid = GridSpec(5, 5)

    private fun item(id: String, x: Int, y: Int, spanX: Int = 1, spanY: Int = 1, page: Int = 0) =
        WorkspaceItem(id, CellPlacement(DESKTOP, page, x, y, spanX, spanY), ItemTarget.App(AppKey("p.$id", "C", 0)))

    @Test
    fun validate_rejectsPlacementsCrossingTheEdge() {
        assertThat(engine.validate(CellPlacement(DESKTOP, 0, 4, 4), grid)).isTrue()
        assertThat(engine.validate(CellPlacement(DESKTOP, 0, 5, 0), grid)).isFalse()
        assertThat(engine.validate(CellPlacement(DESKTOP, 0, 3, 0, spanX = 3), grid)).isFalse()
        assertThat(engine.validate(CellPlacement(DESKTOP, 0, 0, 4, spanY = 2), grid)).isFalse()
    }

    @Test
    fun isFree_considersSpansPagesContainersAndIgnoredItem() {
        val items = listOf(item("a", 1, 1, spanX = 2, spanY = 2))
        assertThat(engine.isFree(items, CellPlacement(DESKTOP, 0, 2, 2))).isFalse()
        assertThat(engine.isFree(items, CellPlacement(DESKTOP, 0, 3, 3))).isTrue()
        assertThat(engine.isFree(items, CellPlacement(DESKTOP, 1, 2, 2))).isTrue()
        assertThat(engine.isFree(items, CellPlacement(DOCK, 0, 2, 2))).isTrue()
        assertThat(engine.isFree(items, CellPlacement(DESKTOP, 0, 0, 0, spanX = 2, spanY = 2))).isFalse()
        assertThat(engine.isFree(items, CellPlacement(DESKTOP, 0, 1, 1), ignoreItemId = "a")).isTrue()
    }

    @Test
    fun findNearestFree_returnsPreferredCellWhenFree() {
        assertThat(engine.findNearestFree(emptyList(), grid, DESKTOP, 0, 2, 3))
            .isEqualTo(CellPlacement(DESKTOP, 0, 2, 3))
    }

    @Test
    fun findNearestFree_breaksTiesByRowThenColumn() {
        // (2,2) taken; (2,1), (1,2), (3,2), (2,3) are all distance 1. Row 1 wins.
        val result = engine.findNearestFree(listOf(item("a", 2, 2)), grid, DESKTOP, 0, 2, 2)
        assertThat(result).isEqualTo(CellPlacement(DESKTOP, 0, 2, 1))
        // With (2,1) also taken, row 2 wins and column 1 beats column 3.
        val second = engine.findNearestFree(listOf(item("a", 2, 2), item("b", 2, 1)), grid, DESKTOP, 0, 2, 2)
        assertThat(second).isEqualTo(CellPlacement(DESKTOP, 0, 1, 2))
    }

    @Test
    fun findNearestFree_returnsNullWhenPageFullOrSpanTooLarge() {
        val full = (0 until 5).flatMap { y -> (0 until 5).map { x -> item("$x-$y", x, y) } }
        assertThat(engine.findNearestFree(full, grid, DESKTOP, 0, 0, 0)).isNull()
        assertThat(engine.findNearestFree(emptyList(), grid, DESKTOP, 0, 0, 0, spanX = 6)).isNull()
    }

    @Test
    fun findFirstFree_scansPagesInOrder() {
        val page0Full = (0 until 5).flatMap { y -> (0 until 5).map { x -> item("$x-$y", x, y) } }
        val items = page0Full + item("p1", 0, 0, page = 1)
        assertThat(engine.findFirstFree(items, grid, DESKTOP, pageCount = 2))
            .isEqualTo(CellPlacement(DESKTOP, 1, 1, 0))
        assertThat(engine.findFirstFree(page0Full, grid, DESKTOP, pageCount = 1)).isNull()
    }

    @Test
    fun findOverlaps_reportsEachPairOnceInIdOrder() {
        val items = listOf(item("c", 0, 0, spanX = 2), item("a", 1, 0), item("b", 3, 3))
        assertThat(engine.findOverlaps(items)).containsExactly(Overlap("a", "c"))
        assertThat(engine.findOverlaps(listOf(item("a", 0, 0), item("b", 0, 0, page = 1)))).isEmpty()
    }

    @Test
    fun property_enginePlacementsNeverOverlapOrLeaveTheGrid() {
        for (seed in 1..300) {
            val random = kotlin.random.Random(seed)
            val g = GridSpec(random.nextInt(1, 8), random.nextInt(1, 8))
            val pageCount = random.nextInt(1, 4)
            val items = mutableListOf<WorkspaceItem>()
            repeat(random.nextInt(1, 60)) { n ->
                val spanX = random.nextInt(1, 3)
                val spanY = random.nextInt(1, 3)
                val placement = if (random.nextBoolean()) {
                    engine.findNearestFree(
                        items, g, DESKTOP, random.nextInt(pageCount),
                        random.nextInt(-2, g.columns + 2), random.nextInt(-2, g.rows + 2), spanX, spanY,
                    )
                } else {
                    engine.findFirstFree(items, g, DESKTOP, pageCount, spanX, spanY)
                }
                if (placement != null) {
                    assertThat(engine.validate(placement, g)).isTrue()
                    assertThat(placement.page).isLessThan(pageCount)
                    assertThat(engine.isFree(items, placement)).isTrue()
                    items += WorkspaceItem("i$n", placement, ItemTarget.App(AppKey("p$n", "C", 0)))
                }
                assertThat(engine.findOverlaps(items)).isEmpty()
            }
        }
    }

    @Test
    fun property_findNearestFreeMatchesBruteForceAndIsDeterministic() {
        for (seed in 1..500) {
            val random = kotlin.random.Random(seed)
            val g = GridSpec(random.nextInt(1, 7), random.nextInt(1, 7))
            val items = (0 until random.nextInt(0, 20)).map { n ->
                item("r$n", random.nextInt(g.columns), random.nextInt(g.rows), random.nextInt(1, 3), random.nextInt(1, 3))
            }
            val px = random.nextInt(-1, g.columns + 1)
            val py = random.nextInt(-1, g.rows + 1)
            val spanX = random.nextInt(1, 3)
            val spanY = random.nextInt(1, 3)

            val result = engine.findNearestFree(items, g, DESKTOP, 0, px, py, spanX, spanY)
            assertThat(engine.findNearestFree(items.reversed(), g, DESKTOP, 0, px, py, spanX, spanY)).isEqualTo(result)

            val expected = (0 until g.rows).flatMap { y -> (0 until g.columns).map { x -> CellPlacement(DESKTOP, 0, x, y, spanX, spanY) } }
                .filter { engine.validate(it, g) && engine.isFree(items, it) }
                .minWithOrNull(compareBy<CellPlacement>({ sq(it.x - px) + sq(it.y - py) }, { it.y }, { it.x }))
            assertThat(result).isEqualTo(expected)
        }
    }

    private fun sq(v: Int) = v * v
}
