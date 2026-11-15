package io.github.aj4abinjacob.chikku.settings.drawer

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.drawer.DrawerModel
import io.github.aj4abinjacob.chikku.core.model.settings.DockLabelFont
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerSearchBarPosition
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerSearchBarStyle
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerSettings
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerStyle
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerTabStyle
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.core.model.settings.NightSettings
import io.github.aj4abinjacob.chikku.home.AppIconView
import io.github.aj4abinjacob.chikku.home.DrawerAppearance
import io.github.aj4abinjacob.chikku.home.IconLabelStyle
import io.github.aj4abinjacob.chikku.home.drawer.DrawerChrome
import io.github.aj4abinjacob.chikku.settings.previews.DrawerPreviewLayout
import io.github.aj4abinjacob.chikku.settings.previews.DrawerSampleColors
import io.github.aj4abinjacob.chikku.settings.previews.PREVIEW_SAMPLES
import io.github.aj4abinjacob.chikku.settings.previews.WIDE_GRID_TOP_DP
import io.github.aj4abinjacob.chikku.settings.previews.drawerPreviewLayout
import io.github.aj4abinjacob.chikku.settings.previews.drawerSampleColors
import io.github.aj4abinjacob.chikku.settings.previews.drawerWideGrid
import io.github.aj4abinjacob.chikku.settings.previews.previewSampleIcon
import io.github.aj4abinjacob.chikku.settings.previews.wallpaperBackground
import io.github.aj4abinjacob.chikku.theme.LauncherSurface
import io.github.aj4abinjacob.chikku.theme.SurfaceColors
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Drawer grid at actual size, with a full-grid overview: real cells use the drawer's own
 * column, row-slot and first-row-trim rules (see [drawerPreviewLayout]), with every other row slot
 * shaded and slot edges dashed so row spacing reads even between identical icons. The mode follows
 * the edit (row changes show every row, icon and label size changes show actual size) and a button
 * switches it by hand. The height is fixed while settings or mode change. Sample apps only.
 */
@Composable
fun DrawerGridPreview(drawer: DrawerSettings, night: NightSettings, desktopIconScale: Float, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val config = LocalConfiguration.current
    val colors = drawerSampleColors(SurfaceColors.resolve(LauncherSurface.DRAWER, night, context), drawer)
    val iconScale = if (drawer.matchDesktopSize) desktopIconScale else drawer.iconScale
    val density = context.resources.displayMetrics.density
    val layout = drawerGridLayout(context, drawer, iconScale, config.screenWidthDp * density, config.screenHeightDp.toFloat())
    // Actual size by default: a whole fitted grid only fits the frame at about a quarter scale.
    var fullGrid by rememberSaveable { mutableStateOf(false) }
    OnDraftChange(drawer.rows to drawer.automaticRowSpacing) { fullGrid = true }
    OnDraftChange(iconScale to drawer.labelScale) { fullGrid = false }
    val percent = (iconScale * 100).roundToInt()
    val description = stringResource(if (fullGrid) R.string.preview_drawer_grid_overview_description else R.string.preview_drawer_grid_description,
        layout.columns, layout.visibleRows, percent)
    Column(modifier.fillMaxWidth()) {
        AndroidView(
            modifier = Modifier.fillMaxWidth().clipToBounds().clearAndSetSemantics { contentDescription = description },
            factory = { DrawerGridPreviewFrame(it) },
            update = { it.show(drawer, iconScale, colors, fullGrid) },
        )
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(if (fullGrid) R.string.preview_drawer_grid_overview_caption else R.string.preview_drawer_grid_caption,
                    layout.columns, layout.visibleRows),
                Modifier.weight(1f).padding(vertical = 8.dp),
                style = MaterialTheme.typography.labelSmall,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(onClick = { fullGrid = !fullGrid }) {
                Text(stringResource(if (fullGrid) R.string.preview_drawer_grid_actual_size else R.string.preview_drawer_grid_full_grid))
            }
        }
    }
}

/** The drawer layout for a grid [widthPx] wide on a screen [screenHeightDp] tall. */
internal fun drawerGridLayout(context: Context, drawer: DrawerSettings, iconScale: Float, widthPx: Float, screenHeightDp: Float): DrawerPreviewLayout {
    val res = context.resources
    val density = res.displayMetrics.density
    val content = DrawerAppearance.cellContentHeight(context, drawer, iconScale)
    return drawerPreviewLayout(drawer, (widthPx / density).coerceAtLeast(240f), screenHeightDp.coerceAtLeast(480f),
        content / density, res.getDimension(R.dimen.home_drawer_column_width) / density, iconScale)
}

/** Runs [onChange] after [key] changes from the value it last had, not on first composition. */
@Composable
private fun OnDraftChange(key: Any, onChange: () -> Unit) {
    val previous = remember { arrayOf(key) }
    val latest by rememberUpdatedState(onChange)
    LaunchedEffect(key) {
        if (previous[0] != key) {
            previous[0] = key
            latest()
        }
    }
}

@SuppressLint("ViewConstructor")
internal class DrawerGridPreviewFrame(context: Context) : ViewGroup(context) {
    private val density = resources.displayMetrics.density
    internal val cells = mutableListOf<AppIconView>()
    private var drawer = DrawerSettings()
    private var iconScale = 1f
    private var colors = DrawerSampleColors(0, 0xFFFFFFFF.toInt())
    private var bound: Triple<DrawerSettings, Float, DrawerSampleColors>? = null
    private var fullGrid = false
    internal var drawScale = 1f
        private set
    internal var drawOffsetX = 0f
        private set
    internal var drawOffsetY = 0f
        private set
    internal var sceneHeightPx = 0
        private set

    /** What the last measure laid out, for tests and drawing. */
    internal var layout: DrawerPreviewLayout? = null
        private set
    internal var rowHeightPx = 0
        private set
    internal var visibleCells = 0
        private set
    private var rowsShown = 0
    private var trimPx = 0
    private var topPx = 0
    private var gridLeftPx = 0
    private var cellWidthPx = 0

    private val band = Paint()
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density
        pathEffect = DashPathEffect(floatArrayOf(4 * density, 3 * density), 0f)
    }

    init {
        setWillNotDraw(false)
        // In overview mode native cells extend beyond this short ViewGroup. The canvas below
        // clips them after scaling, rather than discarding rows at the unscaled frame height.
        clipChildren = false
        clipToPadding = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }

    fun show(drawer: DrawerSettings, iconScale: Float, colors: DrawerSampleColors, fullGrid: Boolean = false) {
        val next = Triple(drawer, iconScale, colors)
        if (next == bound && this.fullGrid == fullGrid) return
        bound = next
        this.fullGrid = fullGrid
        this.drawer = drawer
        this.iconScale = iconScale
        this.colors = colors
        background = LayerDrawable(arrayOf(wallpaperBackground(), ColorDrawable(colors.background)))
        band.color = ColorUtils.setAlphaComponent(colors.content, BAND_ALPHA)
        edge.color = ColorUtils.setAlphaComponent(colors.content, EDGE_ALPHA)
        cells.forEach(::bindCell)
        requestLayout()
        invalidate()
    }

    private fun bindCell(cell: AppIconView) {
        IconLabelStyle.drawer(drawer, iconScale, drawer.labelColor ?: colors.content).applyTo(cell)
    }

    private fun newCell(index: Int) = AppIconView(context, AppIconView.Style.DRAWER).apply {
        isClickable = false
        isLongClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        icon = previewSampleIcon(context, index)
        label = context.getString(PREVIEW_SAMPLES[index % PREVIEW_SAMPLES.size].second)
        bindCell(this)
    }

    /** Fixed across modes/drafts: enough room to inspect the tallest allowed native cell. */
    internal fun frameHeight(): Int {
        val tallest = AppIconView.desiredHeight(context, AppIconView.Style.DRAWER, LauncherSettings.MAX_SCALE,
            LauncherSettings.MAX_SCALE, showLabel = true, singleLine = false, font = DockLabelFont.MEDIUM)
        val screen = resources.configuration.screenHeightDp * density
        val preferred = (screen * .26f).coerceIn(MIN_HEIGHT_DP * density, MAX_HEIGHT_DP * density)
        return max(tallest + (32 * density).roundToInt(), preferred.roundToInt())
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = resolveSize(frameHeight(), heightMeasureSpec)
        setMeasuredDimension(width, height)
        val result = drawerGridLayout(context, drawer, iconScale, width.toFloat(), resources.configuration.screenHeightDp.toFloat())
        layout = result
        rowHeightPx = (result.rowHeight * density).roundToInt().coerceAtLeast(1)
        trimPx = (result.firstRowTrim * density).roundToInt()
        topPx = if (drawerWideGrid(drawer)) (WIDE_GRID_TOP_DP * density).roundToInt() else 0
        gridLeftPx = (result.grid.left * density).roundToInt()
        cellWidthPx = (result.grid.width * density / result.columns).toInt().coerceAtLeast(1)
        rowsShown = if (fullGrid) result.visibleRows else
            ceil((height - topPx + trimPx).toFloat() / rowHeightPx).toInt().coerceAtLeast(1)
        sceneHeightPx = if (fullGrid) max(topPx + (result.grid.height * density).roundToInt(), rowTop(rowsShown)) else height
        val inset = if (fullGrid) min(8 * density, min(width, height) / 4f) else 0f
        drawScale = if (fullGrid) min((width - 2 * inset) / width.coerceAtLeast(1),
            (height - 2 * inset) / sceneHeightPx.coerceAtLeast(1)).coerceIn(0f, 1f) else 1f
        drawOffsetX = (width - width * drawScale) / 2f
        drawOffsetY = if (fullGrid) (height - sceneHeightPx * drawScale) / 2f else 0f
        visibleCells = result.columns * rowsShown
        while (cells.size < visibleCells) {
            val cell = newCell(cells.size)
            cells += cell
            addViewInLayout(cell, -1, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT), true)
        }
        val widthSpec = MeasureSpec.makeMeasureSpec(cellWidthPx, MeasureSpec.EXACTLY)
        val heightSpec = MeasureSpec.makeMeasureSpec(rowHeightPx, MeasureSpec.EXACTLY)
        cells.forEachIndexed { index, cell ->
            cell.visibility = if (index < visibleCells) View.VISIBLE else View.GONE
            if (index < visibleCells) cell.measure(widthSpec, heightSpec)
        }
    }

    private fun rowTop(row: Int) = topPx + row * rowHeightPx - trimPx

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val columns = layout?.columns ?: return
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        for (index in 0 until visibleCells) {
            val ltrLeft = gridLeftPx + index % columns * cellWidthPx
            val x = if (rtl) measuredWidth - ltrLeft - cellWidthPx else ltrLeft
            val y = rowTop(index / columns)
            cells[index].layout(x, y, x + cellWidthPx, y + rowHeightPx)
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        val columns = layout?.columns
        if (columns == null) { super.dispatchDraw(canvas); return }
        val save = canvas.save()
        canvas.clipRect(0, 0, measuredWidth, measuredHeight)
        canvas.translate(drawOffsetX, drawOffsetY)
        canvas.scale(drawScale, drawScale)
        canvas.clipRect(0, 0, measuredWidth, sceneHeightPx)
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        val gridWidth = cellWidthPx * columns
        val l = (if (rtl) measuredWidth - gridLeftPx - gridWidth else gridLeftPx).toFloat()
        val r = l + gridWidth
        // Every other row slot is shaded under the cells, so the slot height (the row spacing) shows.
        for (row in 0 until rowsShown) {
            if (row % 2 == 1) canvas.drawRect(l, rowTop(row).toFloat(), r, (rowTop(row) + rowHeightPx).toFloat(), band)
        }
        super.dispatchDraw(canvas)
        for (row in 0..rowsShown) {
            val y = rowTop(row).toFloat()
            if (y >= 0 && y <= sceneHeightPx) canvas.drawLine(l, y, r, y, edge)
        }
        if (drawer.style == DrawerStyle.LIST) drawGroupLetters(canvas, if (rtl) r else l, rtl)
        canvas.restoreToCount(save)
    }

    private val groupLetter = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = resources.getDimension(R.dimen.drawer_list_section_text_size)
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }

    /** As DrawerListGroups draws them: a letter beside the first row of each letter group. */
    private fun drawGroupLetters(canvas: Canvas, gridEdge: Float, rtl: Boolean) {
        val gutter = resources.getDimension(R.dimen.drawer_list_section_gutter)
        val x = if (rtl) gridEdge + gutter / 2f else gridEdge - gutter / 2f
        groupLetter.color = ColorUtils.setAlphaComponent(colors.content, GROUP_LETTER_ALPHA)
        val centreOffset = (groupLetter.descent() + groupLetter.ascent()) / 2f
        var previous: String? = null
        for (row in 0 until visibleCells) {
            val letter = DrawerModel.sectionLetter(cells[row].label)
            if (letter == previous) continue
            previous = letter
            canvas.drawText(letter, x, rowTop(row) + rowHeightPx / 2f - centreOffset, groupLetter)
        }
    }

    private companion object {
        const val BAND_ALPHA = 0x1F
        const val GROUP_LETTER_ALPHA = 0xB3 // as DrawerListGroups
        const val EDGE_ALPHA = 0x80
        const val MIN_HEIGHT_DP = 176f
        const val MAX_HEIGHT_DP = 208f
    }
}

/** Search field colours, as the drawer chooses them ([DrawerChrome.searchColors]). */
internal typealias DrawerSearchSampleColors = DrawerChrome.SearchColors

internal fun drawerSearchSampleColors(search: SurfaceColors, drawer: DrawerSettings, colors: DrawerSampleColors): DrawerSearchSampleColors =
    DrawerChrome.searchColors(search, drawer.backgroundColor != null, DrawerModel.prefersLightContent(colors.background),
        colors.content, drawer.searchBarStyle)

/**
 * The drawer's search field at true size on the drawer background, styled as the drawer styles it,
 * so Subtle / Outlined / High contrast differ visibly. Hidden in its semantics: the page's scene
 * preview carries the one description.
 */
@Composable
fun DrawerSearchBarSample(drawer: DrawerSettings, night: NightSettings, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val colors = drawerSampleColors(SurfaceColors.resolve(LauncherSurface.DRAWER, night, context), drawer)
    val search = drawerSearchSampleColors(SurfaceColors.resolve(LauncherSurface.SEARCH, night, context), drawer, colors)
    AndroidView(
        factory = { DrawerSearchSampleView(it) },
        update = { it.bind(drawer, colors, search) },
        modifier = modifier.fillMaxWidth().clearAndSetSemantics {},
    )
}

@SuppressLint("ViewConstructor")
internal class DrawerSearchSampleView(context: Context) : FrameLayout(context) {
    private val density = resources.displayMetrics.density
    internal val field = TextView(context).apply {
        // Single line first: setSingleLine resets the minimum height to one line.
        isSingleLine = true
        minHeight = resources.getDimensionPixelSize(R.dimen.home_min_touch_target)
        setPaddingRelative((16 * density).roundToInt(), 0, (20 * density).roundToInt(), 0)
        compoundDrawablePadding = resources.getDimensionPixelSize(R.dimen.drawer_search_icon_padding)
        setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.home_body_text_size))
        gravity = Gravity.CENTER_VERTICAL or Gravity.START
        isFocusable = false
        isClickable = false
    }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        val margin = resources.getDimensionPixelSize(R.dimen.home_panel_margin)
        addView(field, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { setMargins(margin, margin, margin, margin) })
    }

    fun bind(drawer: DrawerSettings, colors: DrawerSampleColors, search: DrawerSearchSampleColors) {
        background = LayerDrawable(arrayOf(wallpaperBackground(), ColorDrawable(colors.background)))
        val hidden = drawer.searchBarPosition == DrawerSearchBarPosition.HIDDEN
        val icon: Drawable? = if (hidden) null else ContextCompat.getDrawable(context, R.drawable.ic_search)?.mutate()?.apply { setTint(search.hint) }
        field.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null)
        field.text = context.getString(if (hidden) R.string.preview_drawer_search_hidden else R.string.home_search_hint)
        field.setTextColor(search.hint)
        field.gravity = if (hidden) Gravity.CENTER else Gravity.CENTER_VERTICAL or Gravity.START
        field.background = if (hidden) GradientDrawable().apply {
            cornerRadius = resources.getDimension(R.dimen.drawer_search_radius)
            // No field in the drawer: a dashed outline marks where it would have been.
            setStroke(density.roundToInt().coerceAtLeast(1), ColorUtils.setAlphaComponent(colors.content, 0x80), 6 * density, 4 * density)
        } else DrawerChrome.searchFieldBackground(resources, search)
    }
}

/**
 * The drawer's tab bar at true size, built with the drawer's own tab rules (titles, compact or
 * segmented shapes, accent and text colours). Hidden in its semantics like the search sample.
 */
@Composable
fun DrawerTabBarSample(drawer: DrawerSettings, night: NightSettings, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val colors = drawerSampleColors(SurfaceColors.resolve(LauncherSurface.DRAWER, night, context), drawer)
    AndroidView(
        factory = { DrawerTabSampleView(it) },
        update = { it.bind(drawer, colors) },
        modifier = modifier.fillMaxWidth().clearAndSetSemantics {},
    )
}

/** Sample tab titles in drawer order: All (or Personal and Work), then one sample group. */
internal fun drawerSampleTabTitles(context: Context, drawer: DrawerSettings): List<String> {
    if (!drawer.groupsEnabled && !drawer.showWorkTab) return emptyList()
    val segmented = drawer.tabStyle == DrawerTabStyle.SEGMENTED
    return buildList {
        if (drawer.showWorkTab) {
            add(context.getString(if (segmented) R.string.drawer_tab_apps else R.string.drawer_tab_personal))
            add(context.getString(R.string.drawer_tab_work))
        } else {
            add(context.getString(R.string.drawer_tab_all))
        }
        if (drawer.groupsEnabled) add(context.getString(R.string.preview_df_group_name))
    }
}

@SuppressLint("ViewConstructor")
internal class DrawerTabSampleView(context: Context) : FrameLayout(context) {
    private val density = resources.displayMetrics.density
    internal val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        val vertical = (8 * density).roundToInt()
        setPadding(0, vertical, 0, vertical)
        addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL))
    }

    private fun dp(value: Int) = (value * density).roundToInt()

    fun bind(drawer: DrawerSettings, colors: DrawerSampleColors) {
        background = LayerDrawable(arrayOf(wallpaperBackground(), ColorDrawable(colors.background)))
        row.removeAllViews()
        val titles = drawerSampleTabTitles(context, drawer)
        val touch = resources.getDimensionPixelSize(R.dimen.home_min_touch_target)
        if (titles.isEmpty()) {
            row.setPaddingRelative(0, 0, 0, 0)
            row.addView(TextView(context).apply {
                text = context.getString(R.string.preview_drawer_tabs_off)
                setTextColor(ColorUtils.setAlphaComponent(colors.content, 0xA0))
                setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.home_body_text_size))
                gravity = Gravity.CENTER
                minHeight = touch
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            return
        }
        // The drawer's own tab rules (DrawerChrome); no listeners, focus or pill transition here.
        val segmented = drawer.tabStyle == DrawerTabStyle.SEGMENTED
        val side = DrawerChrome.tabRowSidePadding(resources, segmented, resources.getDimensionPixelSize(R.dimen.home_panel_margin))
        row.setPaddingRelative(side, 0, side, 0)
        val count = titles.size
        titles.forEachIndexed { index, title ->
            val tab = TextView(context).apply {
                text = title
                isFocusable = false
                DrawerChrome.configureTab(this, resources, segmented)
                DrawerChrome.styleTab(this, resources, segmented, drawer.tabAccentColor, colors.content, selected = index == 0)
            }
            row.addView(tab, DrawerChrome.tabLayoutParams(resources, segmented, index, count))
        }
    }
}
