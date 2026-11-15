package io.github.aj4abinjacob.chikku.settings.previews

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.drawer.DrawerModel
import io.github.aj4abinjacob.chikku.core.model.settings.*
import io.github.aj4abinjacob.chikku.home.AppIconView
import io.github.aj4abinjacob.chikku.theme.LauncherSurface
import io.github.aj4abinjacob.chikku.theme.SurfaceColors
import kotlin.math.floor
import io.github.aj4abinjacob.chikku.home.drawer.DrawerChrome
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A synthetic full-size viewport, scaled only when drawn. Values are in dp. */
internal data class DrawerPreviewLayout(
    val grid: Rect,
    val columns: Int,
    val rowHeight: Float,
    val visibleRows: Int,
    val searchTop: Float?,
    val tabsTop: Float?,
    val scrollerLeft: Float?,
    val firstRowTrim: Float,
) {
    val capacity: Int get() = (columns * visibleRows).coerceAtLeast(1)
}

/** Uses the same auto-column rule and minimum content-height rule as DrawerController. */
internal fun drawerPreviewLayout(
    drawer: DrawerSettings,
    width: Float,
    height: Float,
    minimumRowHeight: Float,
    baseCellWidth: Float,
    effectiveIconScale: Float,
): DrawerPreviewLayout {
    val tabsShown = drawer.groupsEnabled || drawer.showWorkTab
    val tabHeight = if (drawer.tabStyle == DrawerTabStyle.SEGMENTED) 56f else 48f
    val searchHeight = 64f
    var top = 24f
    var bottom = height - 24f
    val tabsTop = if (!tabsShown) null else if (drawer.tabBarPosition == DrawerTabBarPosition.TOP) {
        top.also { top += tabHeight }
    } else {
        bottom -= tabHeight
        bottom
    }
    val searchTop = when (drawer.searchBarPosition) {
        DrawerSearchBarPosition.TOP -> top.also { top += searchHeight }
        DrawerSearchBarPosition.BOTTOM -> { bottom -= searchHeight; bottom }
        DrawerSearchBarPosition.HIDDEN -> null
    }
    val paged = drawer.style == DrawerStyle.HORIZONTAL_PAGED
    if (paged) bottom -= 20f
    val scrollerWidth = if (drawer.fastScrollerStyle == DrawerFastScrollerStyle.ALPHABET) 28f else 6f
    val scrollerShown = drawer.showFastScroller && !paged
    val reserved = if (scrollerShown) scrollerWidth + drawer.fastScrollerGapDp else 0f
    // List rows sit after the group-letter column (DrawerListGroups, drawer_list_section_gutter).
    val left = (if (scrollerShown && drawer.fastScrollerSide == DrawerFastScrollerSide.LEFT) reserved else 8f) +
        if (drawer.style == DrawerStyle.LIST) LIST_GROUP_GUTTER_DP else 0f
    val right = width - if (scrollerShown && drawer.fastScrollerSide == DrawerFastScrollerSide.RIGHT) reserved else 8f
    // DrawerController.updateBottom pads a segmented, fixed-row vertical grid by 16dp top / 24dp bottom.
    if (drawerWideGrid(drawer)) { top += WIDE_GRID_TOP_DP; bottom -= WIDE_GRID_BOTTOM_DP }
    val grid = Rect(left, top, right.coerceAtLeast(left + 1f), bottom.coerceAtLeast(top + 1f))
    val columns = if (drawer.style == DrawerStyle.LIST) 1 else DrawerModel.columns(
        drawer.columns, grid.width.toInt(), (baseCellWidth * effectiveIconScale.coerceAtLeast(.75f)).toInt(),
    )
    val gap = DrawerChrome.FIRST_ROW_GAP_DP.toFloat()
    val manualRows = drawer.style == DrawerStyle.VERTICAL && !drawer.automaticRowSpacing
    val rowHeight = when {
        paged -> max(grid.height / drawer.rows.coerceAtLeast(1), minimumRowHeight)
        manualRows -> max(DrawerChrome.manualRowSlot(grid.height, minimumRowHeight, gap, drawer.rows).roundToInt().toFloat(), minimumRowHeight)
        else -> minimumRowHeight
    }
    // Mirrors the owning drawer's first-row trim: keep the icons close to the search bar while
    // spreading the requested slots through the rest of the viewport.
    val trim = if (manualRows) DrawerChrome.firstRowTrim(rowHeight, minimumRowHeight, gap) else 0f
    // The fixed-slot equation can round the last row out by one pixel; it is still the requested
    // row. Once minimum content height takes precedence, report only the rows that actually fit.
    val visibleRows = if (manualRows && rowHeight > minimumRowHeight) drawer.rows.coerceAtLeast(1)
        else floor((grid.height + trim) / rowHeight).toInt().coerceAtLeast(1)
    return DrawerPreviewLayout(grid, columns, rowHeight, visibleRows,
        searchTop, tabsTop, if (!scrollerShown) null else if (drawer.fastScrollerSide == DrawerFastScrollerSide.LEFT) 2f else width - scrollerWidth - 2f, trim)
}

/** Same condition as DrawerController.updateBottom's wide grid padding. */
internal fun drawerWideGrid(drawer: DrawerSettings): Boolean =
    drawer.tabStyle == DrawerTabStyle.SEGMENTED && !drawer.automaticRowSpacing && drawer.style == DrawerStyle.VERTICAL

internal const val WIDE_GRID_TOP_DP = 16f
internal const val WIDE_GRID_BOTTOM_DP = 24f

internal fun previewSceneHeight(screenHeightDp: Int, fontScale: Float, maximum: Float): Float =
    (screenHeightDp * .24f).coerceIn(72f, if (fontScale > 1.6f) min(maximum, 112f) else maximum)

internal fun previewNextPage(current: Int, delta: Int, pages: Int, wrap: Boolean): Int {
    val count = pages.coerceAtLeast(1)
    return if (wrap) Math.floorMod(current + delta, count) else (current + delta).coerceIn(0, count - 1)
}

internal fun previewFolderOrder(order: FolderSortOrder, labels: List<String>): List<Int> =
    labels.indices.toList().let { indices ->
        if (order == FolderSortOrder.MANUAL) indices else {
            val collator = java.text.Collator.getInstance()
            indices.sortedWith { a, b -> collator.compare(labels[a], labels[b]) }
        }
    }

/** All names and colours below belong to the preview, never to the installed app inventory. */
private const val SAMPLE_TOTAL = 48

/**
 * Selected geometry, tabs, search position and safe finite scrolling/opening demonstrations.
 * [pageControls] false drops the previous/next page buttons on pages unrelated to scrolling.
 */
@Composable
fun DrawerLayoutLivePreview(settings: LauncherSettings, modifier: Modifier = Modifier, opening: Boolean = false,
    description: String? = null, searchInteraction: Boolean = false, pageControls: Boolean = true) {
    val context = LocalContext.current
    val drawer = settings.drawer
    val configuration = LocalConfiguration.current
    val width = configuration.screenWidthDp.toFloat().coerceAtLeast(240f)
    val height = configuration.screenHeightDp.toFloat().coerceAtLeast(480f)
    val previewHeight = previewSceneHeight(configuration.screenHeightDp, configuration.fontScale, 188f).dp
    val density = context.resources.displayMetrics.density
    val iconScale = if (drawer.matchDesktopSize) settings.home.iconScale else drawer.iconScale
    val minimumHeight = AppIconView.desiredHeight(context, AppIconView.Style.DRAWER, iconScale, drawer.labelScale,
        drawer.showLabels, drawer.labelSingleLine, drawer.labelFont, drawer.style == DrawerStyle.LIST) / density
    val layout = drawerPreviewLayout(drawer, width, height, max(48f, minimumHeight),
        context.resources.getDimension(R.dimen.home_drawer_column_width) / density, iconScale)
    val paged = drawer.style == DrawerStyle.HORIZONTAL_PAGED
    val pages = ((SAMPLE_TOTAL + layout.capacity - 1) / layout.capacity).coerceAtLeast(2)
    var page by remember(drawer.style, drawer.columns, drawer.rows) { mutableIntStateOf(0) }
    var fromPage by remember { mutableIntStateOf(0) }
    var searchFocused by remember(drawer.searchBarPosition, drawer.pullToSearch, drawer.openKeyboardOnOpen, searchInteraction) {
        mutableStateOf(searchInteraction && drawer.openKeyboardOnOpen && drawer.searchBarPosition != DrawerSearchBarPosition.HIDDEN)
    }
    var opened by remember(opening, drawer.transition, drawer.openMethod) { mutableStateOf(!opening) }
    var targetOpen by remember { mutableStateOf(true) }
    var everPlayed by remember(opening, drawer.transition, drawer.openMethod, settings.lookFeel.animationSpeed) { mutableStateOf(false) }
    val playback = rememberPreviewPlayback(settings.lookFeel.animationSpeed, drawer.transition to opening)
    val scrollPlayback = rememberPreviewPlayback(settings.lookFeel.animationSpeed, drawer.style)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val previous = stringResource(R.string.preview_df_previous)
    val next = stringResource(R.string.preview_df_next)
    val openLabel = stringResource(R.string.preview_df_open)
    val closeLabel = stringResource(R.string.preview_df_close)
    val replay = stringResource(R.string.preview_df_replay_opening)
    var launched by remember { mutableStateOf(false) }
    var previousOpening by remember { mutableStateOf(drawer) }
    LaunchedEffect(drawer.openMethod, drawer.transition) {
        if (opening && drawerOpeningPreviewChanged(previousOpening, drawer)) {
            launched = false
            opened = true
            targetOpen = true
            everPlayed = true
            playback.replay(320)
        }
        previousOpening = drawer
    }
    var previousAutoClose by remember { mutableStateOf(drawer.automaticallyClose) }
    var previousRemember by remember { mutableStateOf(drawer.rememberScrollPosition) }
    LaunchedEffect(drawer.automaticallyClose) {
        if (opening && previousAutoClose != drawer.automaticallyClose) {
            page = 1
            launched = false
            everPlayed = false
            opened = drawerAfterLaunchPreviewOpen(drawer.automaticallyClose)
        }
        previousAutoClose = drawer.automaticallyClose
    }
    LaunchedEffect(drawer.rememberScrollPosition) {
        if (opening && previousRemember != drawer.rememberScrollPosition) {
            page = drawerReopenedPreviewPage(1, drawer.rememberScrollPosition)
            launched = false
            opened = true
            everPlayed = false
        }
        previousRemember = drawer.rememberScrollPosition
    }
    val actionHeight = (64f * configuration.fontScale.coerceAtLeast(1f)).dp
    val launchLabel = stringResource(if (launched) R.string.preview_gap_return else R.string.preview_gap_launch)
    val scrollLabel = stringResource(R.string.preview_gap_scroll)
    val launchScreen = stringResource(R.string.preview_gap_sample_app)
    val openingState = stringResource(
        if (launched) R.string.preview_gap_app_showing else if (opened) R.string.preview_gap_drawer_showing else R.string.preview_gap_home_showing,
        page.coerceIn(0, pages - 1) + 1,
    )
    val content = if (opening) stringResource(R.string.preview_gap_drawer_state, openingState,
        stringResource(if (drawer.automaticallyClose) R.string.preview_gap_auto_close_on else R.string.preview_gap_auto_close_off),
        stringResource(if (drawer.rememberScrollPosition) R.string.preview_gap_remember_on else R.string.preview_gap_remember_off))
        else description ?: stringResource(R.string.preview_df_drawer_description, layout.columns, layout.visibleRows)
    val colors = drawerSampleColors(SurfaceColors.resolve(LauncherSurface.DRAWER, settings.night, context), drawer)
    val tabAccent = drawer.tabAccentColor?.let(::Color) ?: Color(colors.content).copy(alpha = DrawerChrome.SELECTED_TAB_ALPHA / 255f)
    val scrollerColor = drawer.fastScrollerColor?.let(::Color) ?: Color(colors.content)
    val label = drawer.labelColor?.let(::Color) ?: Color(colors.content)
    val sampleText = stringResource(R.string.preview_df_sample_name)
    val searchText = stringResource(R.string.search_hint)
    val sampleQuery = stringResource(R.string.preview_df_sample_query)
    val tabs = buildList {
        add(stringResource(if (drawer.showWorkTab) R.string.drawer_tab_personal else R.string.drawer_tab_all))
        if (drawer.showWorkTab) add(stringResource(R.string.drawer_tab_work))
        if (drawer.groupsEnabled) add(stringResource(R.string.preview_df_group_name))
    }
    fun move(delta: Int) {
        fromPage = page.coerceIn(0, pages - 1)
        page = previewNextPage(fromPage, delta, pages, paged && drawer.infiniteScroll)
        scrollPlayback.replay(320)
    }
    fun toggleOpen(force: Boolean? = null) {
        targetOpen = force ?: !opened
        if (targetOpen && !opened) page = drawerReopenedPreviewPage(page, drawer.rememberScrollPosition)
        launched = false
        opened = targetOpen
        everPlayed = true
        searchFocused = opened && drawer.openKeyboardOnOpen && drawer.searchBarPosition != DrawerSearchBarPosition.HIDDEN
        playback.replay(320)
    }
    fun tryLaunch() {
        if (launched) launched = false else {
            // Advance the sample so the subsequent reopen demonstrates remembered position.
            if (page == 0) page = 1
            launched = true
            opened = drawerAfterLaunchPreviewOpen(drawer.automaticallyClose)
            everPlayed = false
        }
    }
    Column(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(20.dp))) {
        Canvas(Modifier.fillMaxWidth().height(previewHeight).semantics {
            contentDescription = content
            customActions = listOf(
                CustomAccessibilityAction(previous) { move(-1); true },
                CustomAccessibilityAction(next) { move(1); true },
                CustomAccessibilityAction(if (opened) closeLabel else openLabel) { toggleOpen(); true },
            ) + if (opening) listOf(CustomAccessibilityAction(launchLabel) { tryLaunch(); true }) else emptyList()
        }.pointerInput(drawer.openMethod, opening, opened, launched) {
            detectTapGestures {
                if (opening && !launched && !opened && DrawerModel.showsOpenButton(drawer.openMethod)) toggleOpen(true)
            }
        }.pointerInput(drawer, opened, page, pages, layout, rtl, launched) {
            var drag = Offset.Zero
            detectDragGestures(onDragStart = { drag = Offset.Zero }, onDrag = { change, amount ->
                change.consume(); drag += amount
            }, onDragCancel = { drag = Offset.Zero }, onDragEnd = {
                when (drawerPreviewDragAction(drawer, opening, launched, opened, searchInteraction, searchFocused, paged, rtl, drag.x, drag.y)) {
                    DrawerPreviewDragAction.OPEN -> toggleOpen(true)
                    DrawerPreviewDragAction.CLOSE -> toggleOpen(false)
                    DrawerPreviewDragAction.FOCUS_SEARCH -> searchFocused = true
                    DrawerPreviewDragAction.UNFOCUS_SEARCH -> searchFocused = false
                    DrawerPreviewDragAction.PAGE_FORWARD -> move(1)
                    DrawerPreviewDragAction.PAGE_BACKWARD -> move(-1)
                    DrawerPreviewDragAction.NONE -> Unit
                }
            })
        }) {
            drawDrawerPreviewScene(DrawerPreviewScene(
                width = width, height = height, settings = settings, layout = layout, colors = colors,
                tabs = tabs, iconScale = iconScale, rtl = rtl, pages = pages, opening = opening,
                page = page, fromPage = fromPage, opened = opened, targetOpen = targetOpen,
                everPlayed = everPlayed, launched = launched, searchFocused = searchFocused,
                playback = playback, scrollPlayback = scrollPlayback,
                tabAccent = tabAccent, scrollerColor = scrollerColor, label = label,
                sampleText = sampleText, searchText = searchText, sampleQuery = sampleQuery, launchScreen = launchScreen,
            ))
        }
        // Pages that are not about scrolling hide the page buttons; drag and TalkBack actions remain.
        if (opening || pageControls) {
            DrawerPreviewButtonRow(
                opening = opening, opened = opened, page = page.coerceIn(0, pages - 1), pages = pages,
                previous = previous, next = next, openLabel = openLabel, closeLabel = closeLabel, replay = replay,
                onToggleOpen = { toggleOpen() }, onReplay = { toggleOpen(true) }, onMove = ::move,
            )
        }
        if (opening) {
            DrawerPreviewOpeningActions(
                scrollLabel = scrollLabel, launchLabel = launchLabel, openingState = openingState,
                actionHeight = actionHeight, stateHeight = (32f * configuration.fontScale.coerceAtLeast(1f)).dp,
                onScroll = { launched = false; opened = true; move(1) }, onLaunch = { tryLaunch() },
            )
        }
    }
}

/** Page/open buttons under the preview scene. Holds no state of its own. */
@Composable
private fun DrawerPreviewButtonRow(
    opening: Boolean, opened: Boolean, page: Int, pages: Int,
    previous: String, next: String, openLabel: String, closeLabel: String, replay: String,
    onToggleOpen: () -> Unit, onReplay: () -> Unit, onMove: (Int) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        if (opening) {
            TextButton(onClick = onReplay, Modifier.weight(1f).heightIn(min = 48.dp)) { Text(replay) }
            TextButton(onClick = onToggleOpen, Modifier.weight(1f).heightIn(min = 48.dp)) { Text(if (opened) closeLabel else openLabel) }
        } else {
            TextButton(onClick = { onMove(-1) }, Modifier.weight(1f).heightIn(min = 48.dp)) { Text(previous) }
            Text(stringResource(R.string.preview_df_page, page + 1, pages), Modifier.weight(.6f).padding(top = 14.dp), style = MaterialTheme.typography.labelMedium)
            TextButton(onClick = { onMove(1) }, Modifier.weight(1f).heightIn(min = 48.dp)) { Text(next) }
        }
    }
}

/** Scroll/launch actions and the visible state line of the opening preview. */
@Composable
private fun DrawerPreviewOpeningActions(
    scrollLabel: String, launchLabel: String, openingState: String,
    actionHeight: Dp, stateHeight: Dp, onScroll: () -> Unit, onLaunch: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        TextButton(onClick = onScroll, Modifier.weight(1f).height(actionHeight)) { Text(scrollLabel, maxLines = 2) }
        TextButton(onClick = onLaunch, Modifier.weight(1f).height(actionHeight)) { Text(launchLabel, maxLines = 2) }
    }
    Text(openingState, Modifier.fillMaxWidth().height(stateHeight).padding(horizontal = 16.dp).clearAndSetSemantics {},
        style = MaterialTheme.typography.labelMedium, maxLines = 1)
}

/** What releasing a drag on the drawer preview does; decided without touching any state. */
internal enum class DrawerPreviewDragAction { OPEN, CLOSE, FOCUS_SEARCH, UNFOCUS_SEARCH, PAGE_FORWARD, PAGE_BACKWARD, NONE }

/** The first matching rule wins, in the same order the preview has always applied them. */
internal fun drawerPreviewDragAction(
    drawer: DrawerSettings, opening: Boolean, launched: Boolean, opened: Boolean,
    searchInteraction: Boolean, searchFocused: Boolean, paged: Boolean, rtl: Boolean, dragX: Float, dragY: Float,
): DrawerPreviewDragAction {
    val searchAvailable = drawer.searchBarPosition != DrawerSearchBarPosition.HIDDEN
    return when {
        opening && !launched && !opened && dragY < -24f && DrawerModel.allowsSwipeOpen(drawer.openMethod) -> DrawerPreviewDragAction.OPEN
        opening && opened && dragY > 24f ->
            if (drawer.pullToSearch && searchAvailable) DrawerPreviewDragAction.FOCUS_SEARCH else DrawerPreviewDragAction.CLOSE
        searchInteraction && drawer.pullToSearch && dragY > 24f && searchAvailable -> DrawerPreviewDragAction.FOCUS_SEARCH
        searchInteraction && searchFocused && dragY < -24f -> DrawerPreviewDragAction.UNFOCUS_SEARCH
        paged && kotlin.math.abs(dragX) > 24f ->
            if ((dragX < 0) != rtl) DrawerPreviewDragAction.PAGE_FORWARD else DrawerPreviewDragAction.PAGE_BACKWARD
        !paged && kotlin.math.abs(dragY) > 24f ->
            if (dragY < 0) DrawerPreviewDragAction.PAGE_FORWARD else DrawerPreviewDragAction.PAGE_BACKWARD
        else -> DrawerPreviewDragAction.NONE
    }
}

/** Opening-animation progress (0 closed .. 1 open) for the scene. */
internal fun drawerPreviewProgress(everPlayed: Boolean, playbackRunning: Boolean, opened: Boolean, targetOpen: Boolean, playbackProgress: Float): Float =
    if (!everPlayed || !playbackRunning) (if (opened) 1f else 0f) else if (targetOpen) playbackProgress else 1f - playbackProgress

/** Everything the canvas needs for one frame, read from state inside the draw phase. */
private class DrawerPreviewScene(
    val width: Float, val height: Float, val settings: LauncherSettings, val layout: DrawerPreviewLayout,
    val colors: DrawerSampleColors, val tabs: List<String>, val iconScale: Float, val rtl: Boolean,
    val pages: Int, val opening: Boolean, val page: Int, val fromPage: Int, val opened: Boolean,
    val targetOpen: Boolean, val everPlayed: Boolean, val launched: Boolean, val searchFocused: Boolean,
    val playback: PreviewPlayback, val scrollPlayback: PreviewPlayback,
    val tabAccent: Color, val scrollerColor: Color, val label: Color,
    val sampleText: String, val searchText: String, val sampleQuery: String, val launchScreen: String,
) {
    val drawer: DrawerSettings get() = settings.drawer
    val paged: Boolean get() = drawer.style == DrawerStyle.HORIZONTAL_PAGED
}

private fun DrawScope.drawDrawerPreviewScene(s: DrawerPreviewScene) {
    val drawer = s.drawer
    val width = s.width
    val height = s.height
    val factor = min(size.height / height, size.width / width)
    val origin = Offset((size.width - width * factor) / 2f, 0f)
    translate(origin.x, origin.y) { scale(factor, factor, pivot = Offset.Zero) {
        drawPreviewHome(s)
        val progress = drawerPreviewProgress(s.everPlayed, s.playback.running, s.opened, s.targetOpen, s.playback.progress)
        val shift = if (drawer.transition == DrawerTransition.SLIDE) (1 - progress) * height else 0f
        val opacity = if (drawer.transition == DrawerTransition.SLIDE) 1f else progress
        val zoom = if (drawer.transition == DrawerTransition.SCALE) .92f + .08f * progress else 1f
        if (progress > 0f) clipRect(0f, 0f, width, height) { translate(top = shift) { scale(zoom, zoom, pivot = Offset(width / 2, height / 2)) {
            drawDrawerSurface(s, opacity)
        } } }
        if (s.opening && s.launched) drawLaunchedApp(s)
    } }
}

/** The wallpaper, the sample home icons and the open-drawer hint underneath the drawer. */
private fun DrawScope.drawPreviewHome(s: DrawerPreviewScene) {
    val width = s.width
    val height = s.height
    drawRoundRect(Brush.linearGradient(PREVIEW_WALLPAPER.map(::Color)), size = Size(width, height), cornerRadius = CornerRadius(18f))
    // The home underneath makes the opening method and animation readable.
    repeat(12) { i -> drawCircle(Color(PREVIEW_SAMPLE_COLORS[i % 4]), 20f,
        Offset(width * ((i % 4) + .5f) / 4, 110f + (i / 4) * 100f)) }
    if (DrawerModel.showsOpenButton(s.drawer.openMethod, s.settings.gestures.swipeUp == io.github.aj4abinjacob.chikku.core.model.GestureAction.OpenDrawer)) {
        val arrow = Path().apply { moveTo(width / 2 - 10, height - 54); lineTo(width / 2, height - 64); lineTo(width / 2 + 10, height - 54) }
        drawPath(arrow, Color.White, style = androidx.compose.ui.graphics.drawscope.Stroke(4f))
    }
}

/** The drawer itself: background, search field, tabs, grid, fast scroller, page counter, keyboard. */
private fun DrawScope.drawDrawerSurface(s: DrawerPreviewScene, opacity: Float) {
    val colors = s.colors
    drawRect(Color(colors.background).copy(alpha = Color(colors.background).alpha * opacity), size = Size(s.width, s.height))
    s.layout.searchTop?.let { y -> drawPreviewSearchField(s, y, opacity) }
    s.layout.tabsTop?.let { y -> drawPreviewTabs(s, y, opacity) }
    drawPreviewGrid(s, opacity)
    s.layout.scrollerLeft?.let { x -> drawPreviewFastScroller(s, x, opacity) }
    if (s.paged) previewText("${s.page + 1} / ${s.pages}", s.width / 2 - 14, s.height - 25, 13f, s.label.copy(alpha = opacity))
    if (s.searchFocused) drawPreviewKeyboard(s)
}

private fun DrawScope.drawPreviewSearchField(s: DrawerPreviewScene, y: Float, opacity: Float) {
    drawRoundRect(s.label.copy(alpha = .12f * opacity), Offset(16f, y + 6f), Size(s.width - 32, 48f), CornerRadius(24f))
    previewText(if (s.searchFocused) s.sampleQuery else s.searchText, 32f, y + 36f, 14f, s.label.copy(alpha = opacity))
}

private fun DrawScope.drawPreviewTabs(s: DrawerPreviewScene, y: Float, opacity: Float) {
    val drawer = s.drawer
    val colors = s.colors
    val w = (s.width - 24) / s.tabs.size
    s.tabs.forEachIndexed { i, text ->
        val segmented = drawer.tabStyle == DrawerTabStyle.SEGMENTED
        if (i == 0 || segmented) {
            val fill = if (i == 0) s.tabAccent else Color(colors.content).copy(alpha = DrawerChrome.UNSELECTED_TAB_ALPHA / 255f)
            drawRoundRect(fill.copy(alpha = fill.alpha * opacity), Offset(12 + i * w, y + 7), Size(w - 4, 34f), CornerRadius(if (segmented) 14f else 18f))
        }
        val tabText = if (i == 0 && segmented && drawer.tabAccentColor != null) {
            Color(DrawerChrome.accentTabTextColor(drawer.tabAccentColor!!))
        } else Color(colors.content)
        previewText(text, 20 + i * w, y + 30, 13f, tabText.copy(alpha = opacity))
    }
}

/** The sample app grid, or two grids sliding past each other while a page change plays. */
private fun DrawScope.drawPreviewGrid(s: DrawerPreviewScene, opacity: Float) {
    val drawer = s.drawer
    val layout = s.layout
    val paged = s.paged
    val rtl = s.rtl
    clipRect(layout.grid.left, layout.grid.top, layout.grid.right, layout.grid.bottom) {
        val fraction = s.scrollPlayback.progress
        val shown = s.page.coerceIn(0, s.pages - 1)
        if (s.scrollPlayback.running && s.fromPage != shown) {
            val direction = if (shown > s.fromPage || (drawer.infiniteScroll && s.fromPage == s.pages - 1 && shown == 0)) 1 else -1
            val travel = if (paged) layout.grid.width else layout.grid.height
            fun at(offset: Float, samplePage: Int) {
                translate(left = if (paged) offset * if (rtl) -1 else 1 else 0f, top = if (paged) 0f else offset) {
                    drawSampleGrid(layout.grid.translate(0f, -layout.firstRowTrim), layout.columns, layout.visibleRows, layout.rowHeight, samplePage * layout.capacity,
                        drawer.style == DrawerStyle.LIST, s.iconScale, drawer.showLabels, drawer.labelScale, s.label.copy(alpha = opacity), s.sampleText, rtl)
                }
            }
            at(-direction * travel * fraction, s.fromPage)
            at(direction * travel * (1 - fraction), shown)
        } else drawSampleGrid(layout.grid.translate(0f, -layout.firstRowTrim), layout.columns, layout.visibleRows, layout.rowHeight, shown * layout.capacity,
            drawer.style == DrawerStyle.LIST, s.iconScale, drawer.showLabels, drawer.labelScale, s.label.copy(alpha = opacity), s.sampleText, rtl)
    }
}

private fun DrawScope.drawPreviewFastScroller(s: DrawerPreviewScene, x: Float, opacity: Float) {
    val grid = s.layout.grid
    val color = s.scrollerColor.copy(alpha = opacity)
    if (s.drawer.fastScrollerStyle == DrawerFastScrollerStyle.ALPHABET) "ABCDEFGH".forEachIndexed { i, letter ->
        previewText(letter.toString(), x + 5, grid.top + grid.height * (i + .5f) / 8, 12f, color)
    } else drawRoundRect(color, Offset(x + 5, grid.top + s.page.toFloat() / s.pages * grid.height), Size(6f, 54f), CornerRadius(3f))
}

private fun DrawScope.drawPreviewKeyboard(s: DrawerPreviewScene) {
    val width = s.width
    val height = s.height
    drawRect(s.label.copy(alpha = .18f), Offset(0f, height - 160), Size(width, 160f))
    repeat(3) { r -> repeat(10) { c -> drawRoundRect(s.label.copy(alpha = .4f), Offset(8 + c * (width - 16) / 10, height - 150 + r * 42), Size((width - 32) / 10, 30f), CornerRadius(3f)) } }
}

/** Full-screen sample app shown after "launch" in the opening preview. */
private fun DrawScope.drawLaunchedApp(s: DrawerPreviewScene) {
    drawRoundRect(Color(s.colors.background).copy(alpha = 1f), size = Size(s.width, s.height), cornerRadius = CornerRadius(18f))
    drawCircle(Color(PREVIEW_SAMPLE_COLORS[0]), 42f, Offset(s.width / 2, s.height / 2 - 42))
    previewText(s.launchScreen, 24f, s.height / 2 + 38, 22f, Color(s.colors.content))
}

/** A separate synthetic drag rail: the active letter and popup never invoke drawer scrolling. */
@Composable
fun FastScrollerLivePreview(drawer: DrawerSettings, night: NightSettings = NightSettings(), modifier: Modifier = Modifier, speed: AnimationSpeed = AnimationSpeed.NORMAL) {
    var fraction by remember { mutableFloatStateOf(.35f) }
    var dragging by remember { mutableStateOf(false) }
    var showTest by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val fontScale = LocalConfiguration.current.fontScale
    val playback = rememberPreviewPlayback(speed, drawer.fastScrollerPopup)
    var shownPopup by remember { mutableStateOf(drawer.fastScrollerPopup) }
    LaunchedEffect(playback, drawer.fastScrollerPopup) {
        // Turning the popup on shows it once; turning it off simply hides it.
        if (shownPopup == drawer.fastScrollerPopup) return@LaunchedEffect
        shownPopup = drawer.fastScrollerPopup
        if (drawer.fastScrollerPopup && !dragging) { showTest = true; playback.replay(220) }
    }
    val colors = drawerSampleColors(SurfaceColors.resolve(LauncherSurface.DRAWER, night, context), drawer)
    val color = drawer.fastScrollerColor?.let(::Color) ?: Color(colors.content)
    val description = stringResource(R.string.preview_df_fast_description)
    val tryLabel = stringResource(R.string.preview_df_try_scroller)
    val letter = ('A'.code + (fraction * 7).toInt().coerceIn(0, 7)).toChar().toString()
    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(136.dp).semantics {
            contentDescription = description
            customActions = listOf(CustomAccessibilityAction(tryLabel) {
                fraction = if (fraction < .5f) .8f else .2f; showTest = true; playback.replay(220); true
            })
        }.pointerInput(drawer) {
            detectDragGestures(onDragStart = { position ->
                dragging = true; showTest = false; fraction = (position.y / size.height).coerceIn(0f, 1f); playback.replay(220)
            }, onDragEnd = { dragging = false }, onDragCancel = { dragging = false }, onDrag = { change, _ ->
                change.consume(); fraction = (change.position.y / size.height).coerceIn(0f, 1f)
            })
        }) {
            val unit = density
            val railWidth = if (drawer.fastScrollerStyle == DrawerFastScrollerStyle.ALPHABET) 28f * unit else 16f * unit
            val left = drawer.fastScrollerSide == DrawerFastScrollerSide.LEFT
            val x = if (left) 12f * unit else size.width - railWidth - 12f * unit
            val gap = drawer.fastScrollerGapDp * unit
            drawRoundRect(Brush.linearGradient(PREVIEW_WALLPAPER.map(::Color)), size = size, cornerRadius = CornerRadius(16f * unit))
            drawRoundRect(Color(colors.background), size = size, cornerRadius = CornerRadius(16f * unit))
            val gridLeft = if (left) x + railWidth + gap else 12f * unit
            val gridRight = if (left) size.width - 12f * unit else x - gap
            val grid = Rect(gridLeft, 8f * unit, gridRight, size.height - 8f * unit)
            drawSampleGrid(grid, 3, 2, grid.height / 2, (fraction * 24).toInt(), false, .7f, false, 1f,
                Color.White, "", false)
            if (drawer.fastScrollerStyle == DrawerFastScrollerStyle.ALPHABET) "ABCDEFGH".forEachIndexed { i, c ->
                if (fontScale <= 1.6f || i % 2 == 0 || c.toString() == letter) {
                    previewText(c.toString(), x + 8f * unit, size.height * (i + .5f) / 8, 11f * unit * fontScale,
                        if (c.toString() == letter) color else Color(colors.content).copy(alpha = .6f))
                }
            } else {
                drawRoundRect(color.copy(alpha = .25f), Offset(x + 5f * unit, 8f * unit), Size(6f * unit, size.height - 16f * unit), CornerRadius(3f * unit))
                drawRoundRect(color, Offset(x + 5f * unit, 8f * unit + fraction * (size.height - 48f * unit)), Size(6f * unit, 32f * unit), CornerRadius(3f * unit))
            }
            if (drawer.fastScrollerPopup && (dragging || showTest)) {
                val center = Offset((if (left) x + railWidth + gap + 44f * unit else x - gap - 44f * unit).coerceIn(32f * unit, size.width - 32f * unit),
                    (fraction * size.height).coerceIn(32f * unit, size.height - 32f * unit))
                val progress = if (playback.running) playback.progress else 1f
                scale(.4f + .6f * progress, .4f + .6f * progress, center) {
                    val radius = CornerRadius(32f * unit)
                    val bubble = Path().apply {
                        addRoundRect(androidx.compose.ui.geometry.RoundRect(
                            Rect(center - Offset(32f * unit, 32f * unit), Size(64f * unit, 64f * unit)),
                            radius, radius, if (left) radius else CornerRadius.Zero, if (left) CornerRadius.Zero else radius,
                        ))
                    }
                    drawPath(bubble, color.copy(alpha = color.alpha * progress))
                    val textSize = min(30f * fontScale, 44f / .6f) * unit
                    previewText(letter, center.x - textSize * .3f, center.y + textSize * .3f, textSize,
                        (if (androidx.core.graphics.ColorUtils.calculateLuminance(color.toArgb()) > .5) Color(0xDE000000) else Color.White).copy(alpha = progress))
                }
            }
        }
        TextButton(onClick = { fraction = if (fraction < .5f) .8f else .2f; showTest = true; playback.replay(220) }, Modifier.heightIn(min = 48.dp)) { Text(tryLabel) }
    }
}

private fun DrawScope.drawSampleGrid(grid: Rect, columns: Int, rows: Int, rowHeight: Float, first: Int,
    list: Boolean, iconScale: Float, showLabels: Boolean, labelScale: Float, labelColor: Color, sample: String,
    rtl: Boolean, order: List<Int>? = null, labels: List<String>? = null) {
    val cellWidth = grid.width / columns
    val radius = min(24f * iconScale, min(cellWidth * .32f, rowHeight * .28f))
    repeat(columns * rows) { i ->
        val position = first + i
        if (order != null && position >= order.size) return@repeat
        val index = order?.get(position) ?: position
        val column = if (rtl) columns - 1 - i % columns else i % columns
        val left = grid.left + column * cellWidth
        val top = grid.top + i / columns * rowHeight
        val center = Offset(if (list) if (rtl) grid.right - radius - 16 else grid.left + radius + 16 else left + cellWidth / 2,
            top + rowHeight / 2 - if (showLabels && !list) 10 else 0)
        drawCircle(Color(PREVIEW_SAMPLE_COLORS[index % PREVIEW_SAMPLE_COLORS.size]), radius, center)
        // Original, simple glyph inside the sample tile.
        drawRoundRect(Color.White.copy(alpha = .85f), center - Offset(radius * .4f, radius * .4f), Size(radius * .8f, radius * .8f), CornerRadius(radius * .18f))
        if (showLabels) {
            val text = labels?.get(index) ?: "$sample ${index + 1}"
            val size = 12f * labelScale
            val available = if (list) grid.width - radius * 2 - 48 else cellWidth - 6
            val display = text.take((available / (size * .56f)).toInt().coerceAtLeast(1))
            previewText(display, if (list) if (rtl) grid.left + 12 else center.x + radius + 12 else left + 4,
                if (list) center.y + size / 3 else center.y + radius + size + 5, size, labelColor)
        }
    }
}

private fun DrawScope.previewText(text: String, x: Float, baseline: Float, fontSize: Float, color: Color) {
    drawContext.canvas.nativeCanvas.drawText(text, x, baseline, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toArgb(); textSize = fontSize
    })
}

/** DrawerListGroups' leading letter column (drawer_list_section_gutter), in dp. */
internal const val LIST_GROUP_GUTTER_DP = 36f
