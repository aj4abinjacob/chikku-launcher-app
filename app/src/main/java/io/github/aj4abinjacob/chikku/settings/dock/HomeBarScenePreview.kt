package io.github.aj4abinjacob.chikku.settings.dock

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.settings.HomeSearchBarPosition
import io.github.aj4abinjacob.chikku.core.model.settings.HomeSearchBarStyle
import io.github.aj4abinjacob.chikku.core.model.settings.HomeSearchBarButtonSlot
import io.github.aj4abinjacob.chikku.core.model.settings.HomeSearchBarTapAction
import io.github.aj4abinjacob.chikku.core.model.settings.SearchSettings
import io.github.aj4abinjacob.chikku.core.model.search.WebSearchUrls
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import io.github.aj4abinjacob.chikku.home.HomeSearchBarMetrics
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.home.CellLayout
import io.github.aj4abinjacob.chikku.home.DockBackgroundStyle
import io.github.aj4abinjacob.chikku.search.SearchWidgetView
import io.github.aj4abinjacob.chikku.search.labelRes
import io.github.aj4abinjacob.chikku.settings.previews.GapMarkPaints
import io.github.aj4abinjacob.chikku.settings.previews.addInertPreviewChildren
import io.github.aj4abinjacob.chikku.settings.previews.bindDockBackdrop
import io.github.aj4abinjacob.chikku.settings.previews.contentHeightWithPadding
import io.github.aj4abinjacob.chikku.settings.previews.wallpaperBackground
import kotlin.math.roundToInt

/**
 * The real home search bar at actual size and full screen width. Instead of shrinking a whole
 * phone into the preview, it shows the part of the home screen the bar is in: the top for a top
 * bar, or the bottom (dock, gap, bar, navigation bar) for a bar below the dock. The dock's top may
 * be cropped when the gap and bar are at their largest. With [markGap] the dock gap is shaded.
 * Original sample icons only; nothing can be opened.
 */
@Composable
fun HomeBarScenePreview(settings: LauncherSettings, height: Dp, markGap: Boolean, modifier: Modifier = Modifier) {
    val search = settings.search
    val position = homeBarScenePosition(settings)
    val description = if (markGap && position == HomeSearchBarPosition.BELOW_DOCK) {
        stringResource(R.string.preview_home_bar_gap_description, search.widgetDockGapDp.coerceIn(0, 64))
    } else stringResource(R.string.preview_home_bar_description,
        stringResource(when (position) {
            HomeSearchBarPosition.TOP -> R.string.settings_search_position_top
            HomeSearchBarPosition.BELOW_DOCK -> R.string.settings_search_position_below_dock
            HomeSearchBarPosition.NONE -> R.string.settings_search_position_none
        }), search.widgetWidthPercent.coerceIn(40, 100), search.widgetHeightDp.coerceIn(48, 96),
        homeBarActionsDescription(search))
    AndroidView(
        modifier = modifier.fillMaxWidth().height(height).clipToBounds().clearAndSetSemantics { contentDescription = description },
        factory = { HomeBarSceneFrame(it) },
        update = { it.show(settings, markGap) },
    )
}

/** What a tap on the bar opens, then each shown button and its side, for the preview's TalkBack description. */
@Composable
internal fun homeBarActionsDescription(search: SearchSettings): String {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val left = stringResource(R.string.preview_home_bar_side_left)
    val right = stringResource(R.string.preview_home_bar_side_right)
    fun side(slot: HomeSearchBarButtonSlot) = if ((slot == HomeSearchBarButtonSlot.START) != rtl) left else right
    val engine = WebSearchUrls.engineName(search.webEngine)
    val opens = if (search.widgetTapAction == HomeSearchBarTapAction.WEB_SEARCH) engine else stringResource(R.string.search_bar_tap_chikku)
    val icon = stringResource(search.widgetIcon.labelRes())
    val chikku = stringResource(R.string.preview_home_bar_chikku_button, icon, side(search.widgetChikkuButton))
    val web = stringResource(R.string.preview_home_bar_web_button, engine, side(search.widgetWebButton))
    val buttons = listOfNotNull(
        chikku.takeIf { search.widgetChikkuButton != HomeSearchBarButtonSlot.HIDDEN },
        web.takeIf { search.widgetWebButton != HomeSearchBarButtonSlot.HIDDEN },
    ).ifEmpty { listOf(stringResource(R.string.preview_home_bar_no_buttons)) }
    return (listOf(stringResource(R.string.preview_home_bar_tap, opens)) + buttons).joinToString(" ")
}

internal fun homeBarScenePosition(settings: LauncherSettings): HomeSearchBarPosition =
    if (settings.home.showSearchBar) settings.home.searchBarPosition else HomeSearchBarPosition.NONE

/** Measures exactly as `HomeSearchEntryPoints` sizes the bar; the frame's own height never changes. */
internal class HomeBarSceneFrame(context: Context) : ViewGroup(context) {
    private val workspace = SampleWorkspace(context)
    internal val backdrop = View(context)
    internal val dock = CellLayout(context)
    internal val bar = SearchWidgetView(context)
    private val navigation = SampleNavigation(context)
    private var settings = LauncherSettings()
    private var boundDock: Pair<Any, Any>? = null
    private var markGap = false
    private val density = resources.displayMetrics.density
    private fun dp(value: Int) = (value * density + .5f).toInt()
    private val marks = GapMarkPaints(density)
    private val band = marks.band
    private val edge = marks.edge

    /** Top of the bar in this frame, or null when no bar is shown. */
    internal var barTop: Int? = null
        private set
    internal var dockBottom = 0
        private set

    init {
        background = wallpaperBackground()
        setWillNotDraw(false)
        clipChildren = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        addInertPreviewChildren(listOf(workspace, backdrop, dock, bar, navigation), hideDescendants = true)
    }

    fun show(next: LauncherSettings, mark: Boolean) {
        settings = next
        markGap = mark
        // Only dock settings change the sample dock; search drafts don't rebuild its icons.
        val key = next.dock to next.home
        if (key != boundDock) {
            boundDock = key
            bindSampleDock(dock, next, 0)
        }
        bindDockBackdrop(backdrop, context, next.dock)
        bar.applyAppearance(next.search)
        requestLayout()
        invalidate()
    }

    /** The home screen's own bar rules (HomeSearchBarMetrics) for a bar below the dock in a frame [width] wide. */
    private fun barMetrics(width: Int): HomeSearchBarMetrics {
        val margin = resources.getDimensionPixelSize(R.dimen.home_panel_margin)
        return HomeSearchBarMetrics.of(settings.search, density, width, margin, margin, dp(HomeSearchBarMetrics.DEFAULT_BOTTOM_DP), belowDock = true)
    }

    private val position get() = homeBarScenePosition(settings)
    private val dockHeight get() = if (settings.dock.enabled) dock.contentHeightWithPadding else 0

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(width, height)
        val metrics = barMetrics(width)
        bar.measure(MeasureSpec.makeMeasureSpec(metrics.width ?: metrics.availableWidth.coerceAtLeast(1), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(metrics.height, MeasureSpec.EXACTLY))
        val widthSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY)
        dock.measure(widthSpec, MeasureSpec.makeMeasureSpec(dockHeight, MeasureSpec.EXACTLY))
        workspace.measure(widthSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
        navigation.measure(widthSpec, MeasureSpec.makeMeasureSpec(dp(NAVIGATION_DP), MeasureSpec.EXACTLY))
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val width = measuredWidth
        val height = measuredHeight
        val atTop = position == HomeSearchBarPosition.TOP
        val below = position == HomeSearchBarPosition.BELOW_DOCK
        // A top bar is shown in the top of the screen; everything else in its bottom.
        workspace.visibility = if (atTop) VISIBLE else GONE
        navigation.visibility = if (atTop) GONE else VISIBLE
        bar.visibility = if (atTop || below) VISIBLE else GONE
        val dockShown = !atTop && settings.dock.enabled
        dock.visibility = if (dockShown) VISIBLE else GONE
        backdrop.visibility = if (dockShown && backdrop.background != null) VISIBLE else GONE
        val barX = (width - bar.measuredWidth) / 2
        if (atTop) {
            val y = dp(8)
            bar.layout(barX, y, barX + bar.measuredWidth, y + bar.measuredHeight)
            workspace.searchBottom = y + bar.measuredHeight
            workspace.layout(0, 0, width, height)
            workspace.invalidate()
            barTop = y
            dockBottom = 0
            return
        }
        val navigationTop = height - navigation.measuredHeight
        navigation.layout(0, navigationTop, width, height)
        dockBottom = if (below) {
            val metrics = barMetrics(width)
            val barBottom = navigationTop - metrics.bottomMargin
            val y = barBottom - bar.measuredHeight
            bar.layout(barX, y, barX + bar.measuredWidth, barBottom)
            barTop = y
            y - metrics.dockGap
        } else {
            barTop = null
            navigationTop
        }
        dock.layout(0, dockBottom - dockHeight, width, dockBottom)
        // As on home, a background behind the navigation bar runs to the bottom of the screen.
        val backdropBottom = if (settings.dock.drawBehindNavigationBar) height else dockBottom
        backdrop.layout(0, dockBottom - dockHeight, width, backdropBottom)
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        val barY = barTop ?: return
        if (!markGap || position != HomeSearchBarPosition.BELOW_DOCK || !settings.dock.enabled) return
        val w = measuredWidth.toFloat()
        val gapTop = dockBottom.toFloat()
        canvas.drawRect(0f, gapTop, w, barY.toFloat(), band)
        val half = edge.strokeWidth / 2
        canvas.drawLine(0f, gapTop + half, w, gapTop + half, edge)
        canvas.drawLine(0f, barY - half, w, barY - half, edge)
    }

    private companion object {
        const val NAVIGATION_DP = 24
    }
}
