package io.github.aj4abinjacob.chikku.home

import io.github.aj4abinjacob.chikku.core.model.settings.HomeSearchBarStyle
import io.github.aj4abinjacob.chikku.core.model.settings.SearchSettings

/**
 * Size and margins of the home search bar, in pixels. `HomeSearchEntryPoints` applies them to the
 * real bar; the Settings dock and search-bar previews call the same function, so a rule changes in
 * one place. The clamps repeat `LauncherSettings.coerced()` on purpose: previews are fed unsaved
 * drafts that have not been through it.
 */
internal data class HomeSearchBarMetrics(
    val startMargin: Int,
    val endMargin: Int,
    val bottomMargin: Int,
    val height: Int,
    /** Gap between the dock and a bar below it. */
    val dockGap: Int,
    /** Width inside the start and end margins. */
    val availableWidth: Int,
    /** Bar width in pixels, or null for "fill the available width". */
    val width: Int?,
) {
    companion object {
        /** Margin the Minimal style uses at the sides, and below a bar under the dock. */
        const val MINIMAL_SIDE_DP = 12
        const val MINIMAL_BOTTOM_DP = 21
        /** Bottom margin of the bar under the dock in the home layout. */
        const val DEFAULT_BOTTOM_DP = 8

        fun dpToPx(dp: Int, density: Float): Int = (dp * density + .5f).toInt()

        fun clampedWidthPercent(search: SearchSettings) = search.widgetWidthPercent.coerceIn(40, 100)
        fun clampedHeightDp(search: SearchSettings) = search.widgetHeightDp.coerceIn(48, 96)
        fun clampedDockGapDp(search: SearchSettings) = search.widgetDockGapDp.coerceIn(0, 64)

        /** Bar width for [percent] of [available]; the full width is exact. */
        fun widthForPercent(available: Int, percent: Int): Int =
            if (percent == 100) available else (available * percent / 100).coerceAtLeast(1)

        /**
         * [contentWidth] is the container's width less its own padding; [startMargin], [endMargin] and
         * [bottomMargin] are the layout's margins, which the Minimal style replaces. [belowDock] is the
         * bar under the dock (the only one that takes the minimal bottom margin).
         */
        fun of(
            search: SearchSettings,
            density: Float,
            contentWidth: Int,
            startMargin: Int,
            endMargin: Int,
            bottomMargin: Int,
            belowDock: Boolean,
        ): HomeSearchBarMetrics {
            val minimal = search.widgetStyle == HomeSearchBarStyle.MINIMAL
            val start = if (minimal) dpToPx(MINIMAL_SIDE_DP, density) else startMargin
            val end = if (minimal) start else endMargin
            val bottom = if (minimal && belowDock) dpToPx(MINIMAL_BOTTOM_DP, density) else bottomMargin
            val available = (contentWidth - start - end).coerceAtLeast(0)
            val percent = clampedWidthPercent(search)
            val width = if (percent == 100 || available == 0) null else widthForPercent(available, percent)
            return HomeSearchBarMetrics(start, end, bottom, dpToPx(clampedHeightDp(search), density),
                dpToPx(clampedDockGapDp(search), density), available, width)
        }
    }
}
