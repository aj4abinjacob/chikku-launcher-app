package io.github.aj4abinjacob.chikku.settings.components

import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.drawer.DrawerSection
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerFastScrollerSide
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerSettings
import io.github.aj4abinjacob.chikku.home.drawer.DrawerFastScroller
import io.github.aj4abinjacob.chikku.platform.LocaleSectionIndex
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Pixel scroll metrics of a lazy list, estimated from the items currently laid out (a lazy list
 * only measures what is on screen). Rows of one list are close enough in height that the average
 * visible item stands in for the rest.
 */
internal data class LazyScrollEstimate(val offset: Int, val extent: Int, val range: Int) {

    /** Item index and offset within it that put [fraction] of the scrollable range at the top. */
    fun target(fraction: Float, itemCount: Int): Pair<Int, Int> {
        if (itemCount <= 0 || range <= extent) return 0 to 0
        if (fraction >= 1f) return (itemCount - 1) to 0
        val px = (fraction.coerceIn(0f, 1f) * (range - extent))
        val itemSize = range.toFloat() / itemCount
        val index = (px / itemSize).toInt().coerceIn(0, itemCount - 1)
        return index to (px - index * itemSize).roundToInt()
    }

    companion object {
        /** [visible] holds (index, size) of each laid-out item; [firstOffset] is how far the first one is scrolled off. */
        fun of(visible: List<Pair<Int, Int>>, firstOffset: Int, itemCount: Int, viewport: Int): LazyScrollEstimate {
            if (visible.isEmpty() || itemCount <= 0 || viewport <= 0) return LazyScrollEstimate(0, viewport.coerceAtLeast(0), viewport.coerceAtLeast(0))
            val average = visible.sumOf { it.second }.toFloat() / visible.size
            val range = (average * itemCount).roundToInt().coerceAtLeast(viewport)
            val offset = (visible.first().first * average + firstOffset).roundToInt().coerceIn(0, range - viewport)
            return LazyScrollEstimate(offset, viewport, range)
        }
    }
}

/**
 * The drawer's fast-scroller settings, which every [SettingsFastScroller] follows; provided by each
 * window that hosts settings-style lists (settings, home dialogs, icon picker).
 */
val LocalDrawerScrollSettings = compositionLocalOf { DrawerSettings() }

/**
 * The app drawer's scroll thumb ([DrawerFastScroller] in thumb mode) beside a long settings list,
 * so a long app list is as quick to cross here as in the drawer. Side, colour, letter bubble and
 * touch gap follow the drawer's own fast-scroller settings. [sections] give the letters TalkBack
 * and switch access can jump to (item indices of [state]); [letterAt] names the item at the top
 * for the bubble. Nothing is drawn while the list fits on screen.
 *
 * Like the drawer, left and right are physical sides, also in right-to-left languages. Give the
 * list [settingsFastScrollerPadding], so the strip, which captures every touch on it, never covers
 * a row's controls.
 */
@Composable
fun BoxScope.SettingsFastScroller(
    state: LazyListState,
    drawer: DrawerSettings = LocalDrawerScrollSettings.current,
    sections: List<DrawerSection> = emptyList(),
    letterAt: (index: Int) -> String? = { null },
    modifier: Modifier = Modifier,
) {
    val target = remember(state) { ListTarget(state) }
    FastScrollerStrip(target, drawer, sections, letterAt, modifier)
}

/** [SettingsFastScroller] for a vertical grid, such as the icon picker's thousands of icons. */
@Composable
fun BoxScope.SettingsFastScroller(
    state: LazyGridState,
    drawer: DrawerSettings = LocalDrawerScrollSettings.current,
    sections: List<DrawerSection> = emptyList(),
    letterAt: (index: Int) -> String? = { null },
    modifier: Modifier = Modifier,
) {
    val target = remember(state) { GridTarget(state) }
    FastScrollerStrip(target, drawer, sections, letterAt, modifier)
}

@Composable
private fun BoxScope.FastScrollerStrip(
    target: ScrollTarget,
    drawer: DrawerSettings,
    sections: List<DrawerSection>,
    letterAt: (index: Int) -> String?,
    modifier: Modifier,
) {
    val scope = rememberCoroutineScope()
    val left = drawer.fastScrollerSide == DrawerFastScrollerSide.LEFT
    val currentLetterAt by rememberUpdatedState(letterAt)
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val activeColor = drawer.fastScrollerColor ?: MaterialTheme.colorScheme.primary.toArgb()
    var view by remember { mutableStateOf<DrawerFastScroller?>(null) }

    // Sized by the list beside it, so a wrap-content list in a dialog keeps its own height.
    Box(modifier.matchParentSize()) {
        AndroidView(
            factory = { ctx ->
                DrawerFastScroller(ctx).apply {
                    thumbMode = true
                    // The strip is only as wide as itself, so the bubble draws over its whole window
                    // instead (the dialog's own window when the list is in a dialog).
                    addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                        override fun onViewAttachedToWindow(v: View) { popupParent = v.rootView as? ViewGroup }
                        override fun onViewDetachedFromWindow(v: View) { popupParent = null }
                    })
                    onThumbGestureStarted = { scope.launch { target.stopScroll() } }
                    onSectionSelected = { position -> scope.launch { target.scrollToItem(position, 0) } }
                    onThumbDragged = { fraction ->
                        val (index, offset) = target.itemAt(fraction)
                        scope.launch { target.scrollToItem(index, offset) }
                    }
                    view = this
                }
            },
            update = {
                it.thumbOnLeft = left
                it.popupEnabled = drawer.fastScrollerPopup
                it.setSections(sections, textColor, activeColor)
            },
            onRelease = { view = null },
            modifier = Modifier
                .align(if (left) AbsoluteAlignment.CenterLeft else AbsoluteAlignment.CenterRight)
                .fillMaxHeight()
                .width(settingsFastScrollerWidth(drawer)),
        )
    }

    LaunchedEffect(target, view) {
        val scroller = view ?: return@LaunchedEffect
        snapshotFlow { target.estimate() to target.firstVisibleItem() }
            .distinctUntilChanged()
            .collect { (metrics, first) ->
                scroller.updateThumb(metrics.offset, metrics.extent, metrics.range, currentLetterAt(first))
            }
    }
}

/**
 * Letters for an alphabetical run of [apps] that starts at lazy item [start] (after any header
 * items): the jump targets and the bubble letter of [SettingsFastScroller]. [labelOf] gives the
 * sort label; apps before [alphabeticalFrom] (a leading group, such as locked apps) get a bubble
 * letter but no jump target.
 */
class AppScrollIndex<T> internal constructor(
    private val apps: List<T>,
    private val start: Int,
    private val labelOf: (T) -> String,
    alphabeticalFrom: Int,
    private val gap: Int = 0,
    private val sectionIndex: LocaleSectionIndex,
) {
    private val alphabeticalStart = alphabeticalFrom

    val sections: List<DrawerSection> = apps.drop(alphabeticalFrom)
        .map(labelOf)
        .withIndex()
        .distinctBy { sectionIndex.sectionOf(it.value) }
        .map { DrawerSection(sectionIndex.sectionOf(it.value), start + alphabeticalFrom + gap + it.index) }

    fun letterAt(index: Int): String? {
        val rel = index - start
        // A group header between the leading group and the alphabetical part is not an app.
        val position = when {
            rel < alphabeticalStart -> rel
            rel < alphabeticalStart + gap -> return null
            else -> rel - gap
        }
        return apps.getOrNull(position)?.let { sectionIndex.sectionOf(labelOf(it)) }
    }
}

@Composable
fun <T> rememberAppScrollIndex(
    apps: List<T>,
    start: Int,
    alphabeticalFrom: Int = 0,
    alphabeticalGap: Int = 0,
    labelOf: (T) -> String,
): AppScrollIndex<T> {
    val sectionIndex = remember { LocaleSectionIndex() }
    return remember(apps, start, alphabeticalFrom, alphabeticalGap) { AppScrollIndex(apps, start, labelOf, alphabeticalFrom, alphabeticalGap, sectionIndex) }
}

/** What the strip needs from a lazy list or grid; grids are measured in rows. */
private interface ScrollTarget {
    fun estimate(): LazyScrollEstimate
    fun firstVisibleItem(): Int
    /** Item index and pixel offset for [fraction] of the scroll range. */
    fun itemAt(fraction: Float): Pair<Int, Int>
    suspend fun scrollToItem(index: Int, offset: Int)
    suspend fun stopScroll()
}

private class ListTarget(private val state: LazyListState) : ScrollTarget {
    override fun estimate(): LazyScrollEstimate {
        val info = state.layoutInfo
        return LazyScrollEstimate.of(
            // A sticky header stays "visible" with its own low index after it scrolls off; it
            // would read as the first item and freeze the thumb, so only count items from the real first.
            visible = info.visibleItemsInfo
                .filter { it.index >= state.firstVisibleItemIndex }
                .map { it.index to it.size },
            firstOffset = state.firstVisibleItemScrollOffset,
            itemCount = info.totalItemsCount,
            viewport = info.viewportEndOffset - info.viewportStartOffset,
        )
    }
    override fun firstVisibleItem() = state.firstVisibleItemIndex
    override fun itemAt(fraction: Float) = estimate().target(fraction, state.layoutInfo.totalItemsCount)
    override suspend fun scrollToItem(index: Int, offset: Int) = state.scrollToItem(index, offset)
    override suspend fun stopScroll() = state.stopScroll()
}

private class GridTarget(private val state: LazyGridState) : ScrollTarget {
    // The widest visible row, so a full-width header row does not read as a one-column grid.
    private fun columns(): Int =
        state.layoutInfo.visibleItemsInfo.groupingBy { it.row }.eachCount().values.maxOrNull()?.coerceAtLeast(1) ?: 1

    private fun rows(): Int = (state.layoutInfo.totalItemsCount + columns() - 1) / columns()

    override fun estimate(): LazyScrollEstimate {
        val info = state.layoutInfo
        val rowHeights = info.visibleItemsInfo.groupBy { it.row }
            .map { (row, items) -> row to items.maxOf { it.size.height } }
        return LazyScrollEstimate.of(
            visible = rowHeights,
            firstOffset = state.firstVisibleItemScrollOffset,
            itemCount = rows(),
            viewport = info.viewportEndOffset - info.viewportStartOffset,
        )
    }
    override fun firstVisibleItem() = state.firstVisibleItemIndex
    override fun itemAt(fraction: Float): Pair<Int, Int> {
        val (row, offset) = estimate().target(fraction, rows())
        return (row * columns()).coerceAtMost((state.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)) to offset
    }
    override suspend fun scrollToItem(index: Int, offset: Int) = state.scrollToItem(index, offset)
    override suspend fun stopScroll() = state.stopScroll()
}

/**
 * List padding that keeps rows clear of the [SettingsFastScroller] strip, with [otherSide] on the
 * opposite edge and [bottom] below the last row.
 */
@Composable
fun settingsFastScrollerPadding(
    bottom: Dp = 0.dp,
    otherSide: Dp = 0.dp,
    drawer: DrawerSettings = LocalDrawerScrollSettings.current,
): PaddingValues {
    val strip = settingsFastScrollerWidth(drawer)
    return if (drawer.fastScrollerSide == DrawerFastScrollerSide.LEFT) {
        PaddingValues.Absolute(left = strip, right = otherSide, bottom = bottom)
    } else {
        PaddingValues.Absolute(left = otherSide, right = strip, bottom = bottom)
    }
}

/** Width the scroll strip takes: the drawer's thumb plus its configured touch gap. */
@Composable
private fun settingsFastScrollerWidth(drawer: DrawerSettings): Dp {
    val thumb = LocalContext.current.resources.getDimension(R.dimen.drawer_fast_scroller_thumb_width) /
        LocalContext.current.resources.displayMetrics.density
    return (thumb + drawer.fastScrollerGapDp.coerceAtLeast(MIN_GAP_DP)).dp
}

// A strip narrower than this is hard to grab on a settings page, whatever the drawer uses.
private const val MIN_GAP_DP = 12
