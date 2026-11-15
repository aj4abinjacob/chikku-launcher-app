package io.github.aj4abinjacob.chikku.core.model.layout

import io.github.aj4abinjacob.chikku.core.model.CellPlacement
import io.github.aj4abinjacob.chikku.core.model.ContainerType
import io.github.aj4abinjacob.chikku.core.model.GridSpec
import io.github.aj4abinjacob.chikku.core.model.WorkspaceItem

/** Two items whose placements share at least one cell. [firstId] sorts before [secondId]. */
data class Overlap(val firstId: String, val secondId: String)

/**
 * Pure, deterministic occupancy rules for workspace grids.
 *
 * Positions are whole cells on one page of one container. The engine never mutates its inputs and
 * never consults anything but its arguments, so equal inputs always produce equal outputs. Callers
 * (the repository) own persistence; UI animation never owns authoritative positions.
 */
object WorkspaceLayoutEngine {

    /** True when [placement] lies entirely inside [grid]. Page bounds are the caller's concern. */
    fun validate(placement: CellPlacement, grid: GridSpec): Boolean =
        placement.x.toLong() + placement.spanX <= grid.columns &&
            placement.y.toLong() + placement.spanY <= grid.rows

    /** True when [a] and [b] share at least one cell on the same page of the same container. */
    fun overlaps(a: CellPlacement, b: CellPlacement): Boolean =
        a.container == b.container &&
            a.page == b.page &&
            a.x.toLong() < b.x.toLong() + b.spanX && b.x.toLong() < a.x.toLong() + a.spanX &&
            a.y.toLong() < b.y.toLong() + b.spanY && b.y.toLong() < a.y.toLong() + a.spanY

    /** True when no item except [ignoreItemId] intersects [placement]. Bounds are not checked. */
    fun isFree(items: List<WorkspaceItem>, placement: CellPlacement, ignoreItemId: String? = null): Boolean =
        items.none { it.id != ignoreItemId && overlaps(it.placement, placement) }

    /**
     * Returns the free, in-bounds placement of size [spanX] x [spanY] on [page] of [container]
     * whose origin is closest to ([preferredX], [preferredY]), or null if the page has no room.
     *
     * Ties break by squared Euclidean distance, then row (y), then column (x). The preferred
     * coordinates may lie outside the grid; they only steer the search.
     */
    fun findNearestFree(
        items: List<WorkspaceItem>,
        grid: GridSpec,
        container: ContainerType,
        page: Int,
        preferredX: Int,
        preferredY: Int,
        spanX: Int = 1,
        spanY: Int = 1,
        ignoreItemId: String? = null,
    ): CellPlacement? {
        if (spanX > grid.columns || spanY > grid.rows) return null
        val pageItems = items.filter { it.placement.container == container && it.placement.page == page }
        var best: CellPlacement? = null
        var bestDistance = Long.MAX_VALUE
        // Row-major scan with a strict comparison already yields the row-then-column tie-break.
        for (y in 0..grid.rows - spanY) {
            for (x in 0..grid.columns - spanX) {
                // Remove the preferred-coordinate squares, which are constant for this scan.
                // GridSpec bounds x/y to 0..11, so this equivalent distance ordering stays
                // exact in Long even when either preferred coordinate is Int.MIN/MAX_VALUE.
                val distance = x.toLong() * x + y.toLong() * y -
                    2L * x * preferredX - 2L * y * preferredY
                if (distance >= bestDistance) continue
                val candidate = CellPlacement(container, page, x, y, spanX, spanY)
                if (isFree(pageItems, candidate, ignoreItemId)) {
                    best = candidate
                    bestDistance = distance
                }
            }
        }
        return best
    }

    /**
     * Returns the first free placement scanning pages 0 until [pageCount], then rows top to bottom,
     * then columns left to right; null when every page is full.
     */
    fun findFirstFree(
        items: List<WorkspaceItem>,
        grid: GridSpec,
        container: ContainerType,
        pageCount: Int,
        spanX: Int = 1,
        spanY: Int = 1,
    ): CellPlacement? {
        if (spanX > grid.columns || spanY > grid.rows) return null
        for (page in 0 until pageCount) {
            val pageItems = items.filter { it.placement.container == container && it.placement.page == page }
            for (y in 0..grid.rows - spanY) {
                for (x in 0..grid.columns - spanX) {
                    val candidate = CellPlacement(container, page, x, y, spanX, spanY)
                    if (isFree(pageItems, candidate)) return candidate
                }
            }
        }
        return null
    }

    /** Every pair of overlapping items, ordered deterministically. Empty means the invariant holds. */
    fun findOverlaps(items: List<WorkspaceItem>): List<Overlap> {
        val result = mutableListOf<Overlap>()
        val sorted = items.sortedBy { it.id }
        for (i in sorted.indices) {
            for (j in i + 1 until sorted.size) {
                if (overlaps(sorted[i].placement, sorted[j].placement)) {
                    result += Overlap(sorted[i].id, sorted[j].id)
                }
            }
        }
        return result
    }
}
