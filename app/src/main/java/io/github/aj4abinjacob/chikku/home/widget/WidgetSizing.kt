package io.github.aj4abinjacob.chikku.home.widget

import io.github.aj4abinjacob.chikku.core.model.CellPlacement
import io.github.aj4abinjacob.chikku.core.model.ComponentKey
import io.github.aj4abinjacob.chikku.core.model.ContainerType
import io.github.aj4abinjacob.chikku.core.model.GridSpec
import io.github.aj4abinjacob.chikku.core.model.Workspace
import io.github.aj4abinjacob.chikku.core.model.layout.WorkspaceLayoutEngine
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Size hints of a widget provider in pixels, copied from `AppWidgetProviderInfo` so sizing stays
 * pure and testable. A value of 0 means "not declared".
 */
data class WidgetSizeSpec(
    val minWidth: Int,
    val minHeight: Int,
    val minResizeWidth: Int = 0,
    val minResizeHeight: Int = 0,
    val maxResizeWidth: Int = 0,
    val maxResizeHeight: Int = 0,
    /** API 31+ `targetCellWidth`/`targetCellHeight`; 0 when not declared. */
    val targetCellWidth: Int = 0,
    val targetCellHeight: Int = 0,
    val resizeHorizontal: Boolean = false,
    val resizeVertical: Boolean = false,
) {
    val isResizable: Boolean get() = resizeHorizontal || resizeVertical
}

/** Inclusive span limits for resizing one widget placement. */
data class SpanRange(val minX: Int, val maxX: Int, val minY: Int, val maxY: Int)

/** Pure widget span arithmetic: default spans, resize limits and option sizes. */
object WidgetSizing {

    /** Cells needed to show [sizePx] in cells of [cellPx], at least 1 and at most [maxSpan]. */
    fun spanFor(sizePx: Int, cellPx: Int, maxSpan: Int): Int {
        if (cellPx <= 0 || sizePx <= 0) return 1
        return ceil(sizePx.toDouble() / cellPx).toInt().coerceIn(1, maxSpan.coerceAtLeast(1))
    }

    /** Default span for a newly added widget: the target cells if declared, else its minimum size. */
    fun defaultSpan(spec: WidgetSizeSpec, cellWidth: Int, cellHeight: Int, grid: GridSpec): Pair<Int, Int> {
        val x = if (spec.targetCellWidth > 0) {
            spec.targetCellWidth.coerceIn(1, grid.columns)
        } else {
            spanFor(spec.minWidth, cellWidth, grid.columns)
        }
        val y = if (spec.targetCellHeight > 0) {
            spec.targetCellHeight.coerceIn(1, grid.rows)
        } else {
            spanFor(spec.minHeight, cellHeight, grid.rows)
        }
        return x to y
    }

    /**
     * Resize limits for a widget at [placement]: down to one cell, up to the provider's maximum
     * resize size if declared, and the full grid. The provider's minimum size is deliberately not
     * enforced: providers often declare generous minimums, and the widget is told its
     * real size through the size options. Each dragged edge is also constrained by its opposite
     * edge in the resize frame; the origin can move. An axis the provider does not allow to resize
     * is pinned to the current span.
     */
    fun resizeRange(
        spec: WidgetSizeSpec,
        cellWidth: Int,
        cellHeight: Int,
        grid: GridSpec,
        placement: CellPlacement,
    ): SpanRange {
        fun axis(resizable: Boolean, current: Int, maxResize: Int, cell: Int, room: Int): Pair<Int, Int> {
            if (!resizable) return current to current
            val upper = room.coerceAtLeast(1)
            val maxSpan = if (maxResize > 0 && cell > 0) {
                floor(maxResize.toDouble() / cell).toInt().coerceIn(1, upper)
            } else {
                upper
            }
            return 1 to maxSpan
        }
        val (minX, maxX) = axis(spec.resizeHorizontal, placement.spanX, spec.maxResizeWidth, cellWidth, grid.columns)
        val (minY, maxY) = axis(spec.resizeVertical, placement.spanY, spec.maxResizeHeight, cellHeight, grid.rows)
        return SpanRange(minX, maxX, minY, maxY)
    }

    /** Clamps a requested span into [range]. */
    fun clampResize(range: SpanRange, spanX: Int, spanY: Int): Pair<Int, Int> =
        spanX.coerceIn(range.minX, range.maxX) to spanY.coerceIn(range.minY, range.maxY)

    /** Snaps a dragged pixel size to the nearest whole span inside [min]..[max]. */
    fun snap(sizePx: Float, cellPx: Int, min: Int, max: Int): Int {
        if (cellPx <= 0) return min
        return (sizePx / cellPx).roundToInt().coerceIn(min, max)
    }

    /** Size in dp of a [span]-cell widget minus padding on both sides, for widget options. */
    fun spanSizeDp(span: Int, cellPx: Int, paddingPx: Int, density: Float): Int {
        if (density <= 0f) return 0
        return ((span * cellPx - 2 * paddingPx).coerceAtLeast(0) / density).roundToInt()
    }

    /**
     * Where to put a new widget: the free spot nearest the top-start of [preferredPage], then the
     * first free spot on any page. Null means every page is full (the caller adds a page).
     */
    fun findSpot(workspace: Workspace, preferredPage: Int, spanX: Int, spanY: Int): CellPlacement? {
        val grid = workspace.desktopGrid
        val sx = spanX.coerceIn(1, grid.columns)
        val sy = spanY.coerceIn(1, grid.rows)
        val pages = maxOf(workspace.pageCount, 1)
        val page = preferredPage.coerceIn(0, pages - 1)
        return WorkspaceLayoutEngine.findNearestFree(workspace.items, grid, ContainerType.DESKTOP, page, 0, 0, sx, sy)
            ?: WorkspaceLayoutEngine.findFirstFree(workspace.items, grid, ContainerType.DESKTOP, pages, sx, sy)
    }
}

/** What a widget placement can show right now. */
sealed interface WidgetDisplayState {
    /** The id is bound to the persisted provider: host the live widget. */
    data object Hosted : WidgetDisplayState

    /** Show the "Widget unavailable" placeholder; tapping it rebinds a fresh id. */
    data class NeedsRebind(val reason: Reason) : WidgetDisplayState

    enum class Reason {
        /** Id 0: never bound on this device (e.g. restored) or binding was lost. */
        NO_ID,

        /** The id is not bound to anything: provider uninstalled, disabled or id from another device. */
        NOT_BOUND,

        /** The id is bound to a different provider than the placement expects (stale restored id). */
        PROVIDER_CHANGED,
    }
}

/**
 * Decides whether a placement can be hosted. [bound] is the provider the platform reports for
 * [appWidgetId] (null if unbound). The placement is never deleted here; every failure is a placeholder.
 */
fun widgetDisplayState(appWidgetId: Int, persisted: ComponentKey, bound: ComponentKey?): WidgetDisplayState = when {
    appWidgetId == 0 -> WidgetDisplayState.NeedsRebind(WidgetDisplayState.Reason.NO_ID)
    bound == null -> WidgetDisplayState.NeedsRebind(WidgetDisplayState.Reason.NOT_BOUND)
    bound.packageName != persisted.packageName || bound.className != persisted.className ||
        bound.userSerial != persisted.userSerial -> WidgetDisplayState.NeedsRebind(WidgetDisplayState.Reason.PROVIDER_CHANGED)
    else -> WidgetDisplayState.Hosted
}
