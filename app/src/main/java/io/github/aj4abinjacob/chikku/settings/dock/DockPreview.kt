package io.github.aj4abinjacob.chikku.settings.dock

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.viewinterop.AndroidView
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.GridSpec
import io.github.aj4abinjacob.chikku.core.model.settings.HomeSearchBarPosition
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.home.AppIconView
import io.github.aj4abinjacob.chikku.home.CellLayout
import io.github.aj4abinjacob.chikku.home.DockBackgroundStyle
import io.github.aj4abinjacob.chikku.home.DockImageDrawable
import io.github.aj4abinjacob.chikku.home.DockImageLoader
import io.github.aj4abinjacob.chikku.home.IconLabelStyle
import io.github.aj4abinjacob.chikku.home.applyDockContainer
import io.github.aj4abinjacob.chikku.home.HomeSearchBarMetrics
import io.github.aj4abinjacob.chikku.search.SearchWidgetView
import io.github.aj4abinjacob.chikku.settings.previews.PREVIEW_SAMPLES
import io.github.aj4abinjacob.chikku.settings.previews.addInertPreviewChildren
import io.github.aj4abinjacob.chikku.settings.previews.bindDockBackdrop
import io.github.aj4abinjacob.chikku.settings.previews.contentHeightWithPadding
import io.github.aj4abinjacob.chikku.settings.previews.rememberDockBackgroundImage
import io.github.aj4abinjacob.chikku.settings.previews.previewSampleIcon
import io.github.aj4abinjacob.chikku.settings.previews.wallpaperBackground
import kotlinx.coroutines.Dispatchers
import kotlin.math.roundToInt

/** Logical pixel geometry. The whole scene is uniformly scaled into a fixed-height preview. */
internal data class DockSearchPreviewGeometry(
    val dockTop: Int,
    val dockHeight: Int,
    val searchTop: Int?,
    val searchHeight: Int,
    val navigationTop: Int,
    val sceneHeight: Int,
)

/** Dock/bar measurements retain their proportions; reserved height stays constant during dragging. */
internal fun dockSearchPreviewGeometry(
    sceneHeight: Int,
    navigationHeight: Int,
    dockHeight: Int,
    searchHeight: Int,
    gap: Int,
    position: HomeSearchBarPosition,
    bottomMargin: Int = 0,
    topMargin: Int = 0,
): DockSearchPreviewGeometry {
    val nav = (sceneHeight - navigationHeight.coerceAtLeast(0)).coerceAtLeast(0)
    val searchTop = when (position) {
        HomeSearchBarPosition.TOP -> topMargin.coerceAtLeast(0)
        HomeSearchBarPosition.BELOW_DOCK -> (nav - searchHeight - bottomMargin.coerceAtLeast(0)).coerceAtLeast(0)
        HomeSearchBarPosition.NONE -> null
    }
    val dockBottom = if (position == HomeSearchBarPosition.BELOW_DOCK) searchTop!! - gap.coerceAtLeast(0) else nav
    return DockSearchPreviewGeometry((dockBottom - dockHeight).coerceAtLeast(0), dockHeight.coerceAtLeast(0),
        searchTop, searchHeight.coerceAtLeast(0), nav, sceneHeight.coerceAtLeast(0))
}

/**
 * Uses the actual dock/background/search renderers with original sample icons. No workspace,
 * provider, app inventory or widget is queried. Optional search shares this scene on both pages.
 */
@Composable
fun DockPreview(
    settings: LauncherSettings,
    modifier: Modifier = Modifier,
    samplePage: Int = 0,
    previousPage: Int = samplePage,
    pageProgress: Float = 1f,
    pageDirection: Int = 1,
    dockVisibility: Float = 1f,
    includeSearch: Boolean = false,
    accessibilityEnabled: Boolean = true,
) {
    val image by rememberDockBackgroundImage(settings)
    val shown = includeSearch && settings.home.showSearchBar && settings.home.searchBarPosition != HomeSearchBarPosition.NONE
    val description = if (includeSearch) stringResource(R.string.preview_dock_search_description,
        stringResource(when {
            !shown -> R.string.settings_search_position_none
            settings.home.searchBarPosition == HomeSearchBarPosition.TOP -> R.string.settings_search_position_top
            else -> R.string.settings_search_position_below_dock
        }), settings.search.widgetDockGapDp)
    else stringResource(R.string.preview_dock_description, samplePage + 1, settings.dock.effectivePages())
    AndroidView(
        modifier = modifier.clearAndSetSemantics { if (accessibilityEnabled) contentDescription = description },
        factory = { DockSearchPreviewFrame(it) },
        update = {
            it.contentDescription = description.takeIf { accessibilityEnabled }
            it.importantForAccessibility = if (accessibilityEnabled) View.IMPORTANT_FOR_ACCESSIBILITY_YES
                else View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            it.show(settings, image, samplePage, previousPage, pageProgress, pageDirection, dockVisibility, includeSearch)
        },
    )
}

/** Native renderers draw at a representative 360dp width; scaling never changes during a slider drag. */
internal class DockSearchPreviewFrame(context: Context) : ViewGroup(context) {
    private val backdrop = View(context)
    private val workspace = SampleWorkspace(context)
    internal val previousDock = CellLayout(context)
    internal val dock = CellLayout(context)
    internal val bar = SearchWidgetView(context)
    private val navigation = SampleNavigation(context)
    private var settings = LauncherSettings()
    private var hasSearch = false
    private var lastIcons: Triple<LauncherSettings, Int, Int>? = null
    internal var geometry = DockSearchPreviewGeometry(0, 0, null, 0, 0, 0)
        private set
    private var logicalWidth = 1
    private var logicalHeight = 1
    private var sceneScale = 1f
    private var sceneLeft = 0f
    private var sceneTop = 0f
    private var pageProgress = 1f
    private var pageDirection = 1
    private var samePage = true
    private var dockVisibility = 1f

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        background = wallpaperBackground()
        clipChildren = true
        addInertPreviewChildren(listOf(workspace, backdrop, previousDock, dock, bar, navigation), hideDescendants = true)
    }

    fun show(
        next: LauncherSettings,
        image: Bitmap?,
        samplePage: Int,
        previousPage: Int,
        progress: Float,
        direction: Int,
        visibility: Float,
        includeSearch: Boolean,
    ) {
        settings = next
        hasSearch = includeSearch && next.home.showSearchBar && next.home.searchBarPosition != HomeSearchBarPosition.NONE
        pageProgress = progress.coerceIn(0f, 1f)
        pageDirection = direction
        samePage = samplePage == previousPage
        dockVisibility = if (next.dock.enabled) visibility.coerceIn(0f, 1f) else 0f
        val key = Triple(next, samplePage, previousPage)
        if (key != lastIcons) {
            lastIcons = key
            bindSampleDock(dock, settings, samplePage)
            bindSampleDock(previousDock, settings, previousPage)
        }
        bindDockBackdrop(backdrop, context, next.dock, image)
        bar.applyAppearance(next.search)
        bar.visibility = if (hasSearch) VISIBLE else GONE
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(width, height)
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).roundToInt()
        logicalWidth = dp(360)
        val largestCell = AppIconView.desiredHeight(context, AppIconView.Style.WORKSPACE,
            LauncherSettings.MAX_SCALE, LauncherSettings.MAX_SCALE, true, false, settings.dock.labelFont)
        // All allowed slider extremes fit the same envelope: there is no rescaling while dragging.
        val maxDock = largestCell + dp(32)
        logicalHeight = maxDock + dp(if (hasSearch) 96 + 64 + 24 + 72 else 24 + 72)
        sceneScale = minOf(width.toFloat() / logicalWidth, height.toFloat() / logicalHeight).coerceAtMost(1f)
        sceneLeft = (width - logicalWidth * sceneScale) / 2f
        sceneTop = (height - logicalHeight * sceneScale) / 2f
        val dockHeight = if (settings.dock.enabled) dock.contentHeightWithPadding else 0
        val panelMargin = resources.getDimensionPixelSize(R.dimen.home_panel_margin)
        // The home screen's own bar rules, applied to the logical 360 dp scene.
        val metrics = HomeSearchBarMetrics.of(settings.search, density, logicalWidth, panelMargin, panelMargin,
            dp(HomeSearchBarMetrics.DEFAULT_BOTTOM_DP), belowDock = true)
        val searchHeight = metrics.height
        val position = if (hasSearch) settings.home.searchBarPosition else HomeSearchBarPosition.NONE
        geometry = dockSearchPreviewGeometry(logicalHeight, dp(24), dockHeight, searchHeight,
            metrics.dockGap, position, metrics.bottomMargin, dp(8))
        val dockWidthSpec = MeasureSpec.makeMeasureSpec(logicalWidth, MeasureSpec.EXACTLY)
        val dockHeightSpec = MeasureSpec.makeMeasureSpec(dockHeight, MeasureSpec.EXACTLY)
        dock.measure(dockWidthSpec, dockHeightSpec)
        previousDock.measure(dockWidthSpec, dockHeightSpec)
        bar.measure(MeasureSpec.makeMeasureSpec(metrics.width ?: metrics.availableWidth.coerceAtLeast(1), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(searchHeight, MeasureSpec.EXACTLY))
        val backgroundHeight = if (settings.dock.drawBehindNavigationBar) logicalHeight - geometry.dockTop else dockHeight
        backdrop.measure(dockWidthSpec, MeasureSpec.makeMeasureSpec(backgroundHeight, MeasureSpec.EXACTLY))
        workspace.measure(dockWidthSpec, MeasureSpec.makeMeasureSpec(
            if (settings.dock.displayAsOverlay) geometry.navigationTop else geometry.dockTop, MeasureSpec.EXACTLY))
        navigation.measure(dockWidthSpec, MeasureSpec.makeMeasureSpec(dp(24), MeasureSpec.EXACTLY))
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        fun place(view: View, x: Int, y: Int) {
            view.layout(x, y, x + view.measuredWidth, y + view.measuredHeight)
            view.pivotX = 0f
            view.pivotY = 0f
            view.scaleX = sceneScale
            view.scaleY = sceneScale
            view.translationX = sceneLeft + x * (sceneScale - 1f)
            view.translationY = sceneTop + y * (sceneScale - 1f)
        }
        place(workspace, 0, 0)
        workspace.searchBottom = if (hasSearch && settings.home.searchBarPosition == HomeSearchBarPosition.TOP)
            (geometry.searchTop ?: 0) + geometry.searchHeight else 0
        workspace.invalidate()
        place(backdrop, 0, geometry.dockTop)
        place(previousDock, 0, geometry.dockTop)
        place(dock, 0, geometry.dockTop)
        place(bar, (logicalWidth - bar.measuredWidth) / 2, geometry.searchTop ?: 0)
        place(navigation, 0, geometry.navigationTop)
        backdrop.alpha = dockVisibility
        dock.alpha = dockVisibility
        previousDock.alpha = dockVisibility
        val rtlDirection = pageDirection * if (layoutDirection == LAYOUT_DIRECTION_RTL) -1 else 1
        if (!samePage) {
            previousDock.translationX += -pageProgress * logicalWidth * sceneScale * rtlDirection
            dock.translationX += (1 - pageProgress) * logicalWidth * sceneScale * rtlDirection
        }
        previousDock.visibility = if (!samePage && pageProgress < 1 && dockVisibility > 0f) VISIBLE else INVISIBLE
        dock.visibility = if (dockVisibility > 0f) VISIBLE else INVISIBLE
        backdrop.visibility = dock.visibility
    }
}

/** Fills [layout] with the dock's real padding, cell height and icons, using original sample icons. */
internal fun bindSampleDock(layout: CellLayout, settings: LauncherSettings, page: Int) {
    val context = layout.context
    val preferences = settings.dock
    layout.applyDockContainer(context, preferences, settings.home, layout.resources.displayMetrics.density)
    layout.grid = GridSpec(preferences.iconCount, 1)
    val style = IconLabelStyle.dock(preferences, settings.home, DockBackgroundStyle.labelColor(context, preferences))
    layout.removeAllViews()
    repeat(preferences.iconCount) { index ->
        val sample = index + page
        layout.addView(AppIconView(context, AppIconView.Style.WORKSPACE).apply {
            style.applyTo(this)
            icon = previewSampleIcon(context, sample, settings.lookFeel.iconShape, settings.lookFeel.reshapeLegacyIcons)
            label = context.getString(PREVIEW_SAMPLES[sample % PREVIEW_SAMPLES.size].second)
            isClickable = false
            isLongClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, CellLayout.LayoutParams(index, 0))
    }
}

internal class SampleWorkspace(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    var searchBottom = 0
    override fun onDraw(canvas: Canvas) {
        val density = resources.displayMetrics.density
        val start = maxOf(24 * density, searchBottom + 16 * density)
        val radius = 13 * density
        paint.color = 0xCCFFFFFF.toInt()
        repeat(3) { row ->
            val y = start + radius + row * 72 * density
            if (y + radius < height) repeat(4) { col ->
                val x = width * (col + .5f) / 4
                canvas.drawCircle(x, y, radius, paint)
                canvas.drawRoundRect(x - radius, y + radius * 1.5f, x + radius, y + radius * 1.75f,
                    radius / 3, radius / 3, paint)
            }
        }
    }
}

internal class SampleNavigation(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xBBFFFFFF.toInt() }
    override fun onDraw(canvas: Canvas) {
        val pillWidth = width * .28f
        val pillHeight = resources.displayMetrics.density * 4
        val cy = height * .5f
        canvas.drawRoundRect((width - pillWidth) / 2, cy - pillHeight / 2, (width + pillWidth) / 2,
            cy + pillHeight / 2, pillHeight, pillHeight, paint)
    }
}
