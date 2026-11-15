package io.github.aj4abinjacob.chikku.core.model.layout

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.CellPlacement
import io.github.aj4abinjacob.chikku.core.model.ContainerType
import io.github.aj4abinjacob.chikku.core.model.GridSpec
import io.github.aj4abinjacob.chikku.core.model.ItemTarget
import io.github.aj4abinjacob.chikku.core.model.WorkspaceItem
import java.math.BigInteger
import kotlin.random.Random
import org.junit.Test

/** Deterministic stress coverage for adversarial persisted coordinates and crowded page sets. */
class ReliabilityLayoutStressTest {
    @Test
    fun seededCrowdedPagesAlwaysReturnValidNonOverlappingPlacements() {
        val seed = 20261012
        val random = Random(seed)
        val grid = GridSpec(12, 12)
        val items = mutableListOf<WorkspaceItem>()

        // 20 pages, including pages filled by a mixture of 1x1 and multi-cell items.
        repeat(20) { page ->
            repeat(150) { sequence ->
                val spanX = random.nextInt(1, 4)
                val spanY = random.nextInt(1, 4)
                val targetPage = random.nextInt(20)
                val placement = WorkspaceLayoutEngine.findNearestFree(
                    items,
                    grid,
                    ContainerType.DESKTOP,
                    targetPage,
                    random.nextInt(-12, 24),
                    random.nextInt(-12, 24),
                    spanX,
                    spanY,
                ) ?: WorkspaceLayoutEngine.findFirstFree(items, grid, ContainerType.DESKTOP, 20, spanX, spanY)

                if (placement != null) {
                    assertWithMessage("seed=$seed page=$page sequence=$sequence placement=$placement")
                        .that(WorkspaceLayoutEngine.validate(placement, grid)).isTrue()
                    assertWithMessage("seed=$seed page=$page sequence=$sequence placement=$placement")
                        .that(WorkspaceLayoutEngine.isFree(items, placement)).isTrue()
                    items += WorkspaceItem(
                        id = "stress-$page-$sequence",
                        placement = placement,
                        target = ItemTarget.App(AppKey("stress.$page.$sequence", "Main", page.toLong())),
                    )
                }
            }
        }

        assertThat(WorkspaceLayoutEngine.findOverlaps(items)).isEmpty()
        assertThat(items.map { it.id }.toSet()).hasSize(items.size)
        for (page in 0 until 20) {
            assertWithMessage("seed=$seed page=$page should be full after 3,000 placement attempts")
                .that(
                    WorkspaceLayoutEngine.findNearestFree(
                        items, grid, ContainerType.DESKTOP, page, 0, 0,
                    ),
                ).isNull()
        }
    }

    @Test
    fun boundsCheckDoesNotOverflowForMalformedLargeSpan() {
        val extreme = CellPlacement(ContainerType.DESKTOP, 0, 1, 0, Int.MAX_VALUE, 1)
        val smallGrid = GridSpec(12, 12)

        assertThat(WorkspaceLayoutEngine.validate(extreme, smallGrid)).isFalse()
    }

    @Test
    fun overlapCheckDoesNotOverflowForMalformedLargeSpan() {
        assertThat(
            WorkspaceLayoutEngine.overlaps(
                CellPlacement(ContainerType.DESKTOP, 0, 1, 0, Int.MAX_VALUE, 1),
                CellPlacement(ContainerType.DESKTOP, 0, 2, 0),
            ),
        ).isTrue()
    }

    @Test
    fun nearestFreeMatchesBigIntegerOracleForExtremePreferencesHolesAndTies() {
        val grid = GridSpec(4, 4)
        val occupied = listOf(
            CellPlacement(ContainerType.DESKTOP, 0, 0, 0),
            CellPlacement(ContainerType.DESKTOP, 0, 1, 0),
            CellPlacement(ContainerType.DESKTOP, 0, 1, 1),
            CellPlacement(ContainerType.DESKTOP, 0, 2, 2, spanX = 2, spanY = 2),
        ).mapIndexed { index, placement ->
            WorkspaceItem("occupied-$index", placement, ItemTarget.App(AppKey("pkg.$index", "Main", 0)))
        }
        val preferences = listOf(
            Int.MIN_VALUE to Int.MIN_VALUE,
            Int.MAX_VALUE to Int.MIN_VALUE,
            Int.MIN_VALUE to Int.MAX_VALUE,
            Int.MAX_VALUE to Int.MAX_VALUE,
            -1 to 1, // equal-distance candidates exercise row then column ordering
            1 to 1,
            9 to -8,
        )

        for ((preferredX, preferredY) in preferences) {
            val expected = (0 until grid.rows).flatMap { y ->
                (0 until grid.columns).map { x -> CellPlacement(ContainerType.DESKTOP, 0, x, y) }
            }.filter { candidate -> occupied.none { overlapsByLong(it.placement, candidate) } }
                .minWithOrNull(
                    compareBy<CellPlacement>({ exactDistance(it.x, it.y, preferredX, preferredY) }, { it.y }, { it.x }),
                )

            assertWithMessage("preferred=($preferredX,$preferredY)")
                .that(WorkspaceLayoutEngine.findNearestFree(
                    occupied, grid, ContainerType.DESKTOP, 0, preferredX, preferredY,
                ))
                .isEqualTo(expected)
        }
    }

    private fun exactDistance(x: Int, y: Int, preferredX: Int, preferredY: Int): BigInteger {
        val dx = BigInteger.valueOf(x.toLong() - preferredX.toLong())
        val dy = BigInteger.valueOf(y.toLong() - preferredY.toLong())
        return dx.multiply(dx).add(dy.multiply(dy))
    }

    private fun overlapsByLong(a: CellPlacement, b: CellPlacement): Boolean =
        a.container == b.container && a.page == b.page &&
            a.x.toLong() < b.x.toLong() + b.spanX && b.x.toLong() < a.x.toLong() + a.spanX &&
            a.y.toLong() < b.y.toLong() + b.spanY && b.y.toLong() < a.y.toLong() + a.spanY
}
