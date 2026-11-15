package io.github.aj4abinjacob.chikku.settings.previews

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.settings.DockSettings
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.core.model.settings.PageIndicatorStyle
import io.github.aj4abinjacob.chikku.core.model.settings.PageTransitionEffect
import io.github.aj4abinjacob.chikku.home.WallpaperOffsets
import io.github.aj4abinjacob.chikku.settings.dock.DockPreview
import io.github.aj4abinjacob.chikku.theme.Motion
import kotlinx.coroutines.delay
import kotlin.math.abs

/** Preview navigation uses logical pages, just as the launcher does, independently of RTL drawing. */
internal fun previewPageTarget(current: Int, count: Int, step: Int, wrap: Boolean, allowStartWrap: Boolean = true): Int {
    val pages = count.coerceAtLeast(1)
    val page = current.coerceIn(0, pages - 1)
    val target = page.toLong() + step
    return when {
        target in 0L until pages.toLong() -> target.toInt()
        wrap && (target >= pages || allowStartWrap) -> Math.floorMod(target, pages.toLong()).toInt()
        else -> page
    }
}

internal fun previewDockReopenedPage(dock: DockSettings, page: Int): Int =
    if (dock.rememberPosition) page.coerceIn(0, dock.effectivePages() - 1) else 0

internal fun previewDockAfterLaunch(dock: DockSettings, shown: Boolean): Boolean =
    shown && !(dock.displayAsOverlay && dock.automaticallyClose)

internal fun homePreviewPageCount(stored: Int, demonstrate: Boolean): Int =
    stored.coerceIn(1, 100).let { if (demonstrate) maxOf(it, 3) else it }

/** A finite, isolated pager. It never changes the workspace or calls WallpaperManager. */
@Composable
fun HomePagesLivePreview(settings: LauncherSettings, storedPageCount: Int, modifier: Modifier = Modifier) {
    val home = settings.home
    var demonstrate by rememberSaveable { mutableStateOf(false) }
    val actualCount = storedPageCount.coerceIn(1, 100)
    val count = homePreviewPageCount(storedPageCount, demonstrate)
    val default = home.defaultPage.coerceIn(0, count - 1)
    var from by remember(count, default) { mutableIntStateOf(default) }
    var page by remember(count, default) { mutableIntStateOf(default) }
    var direction by remember { mutableIntStateOf(1) }
    var indicatorVisible by remember(home.pageIndicatorAutoHide) { mutableStateOf(!home.pageIndicatorAutoHide) }
    var reveal by remember { mutableIntStateOf(0) }
    var priorCount by remember { mutableIntStateOf(actualCount) }
    val playback = rememberPreviewPlayback(settings.lookFeel.animationSpeed)
    val motion = Motion.current(settings.lookFeel.animationSpeed)
    val indicatorAlpha by animateFloatAsState(if (indicatorVisible) 1f else 0f,
        tween(motion.duration(300).toInt()), label = "homePreviewIndicator")
    LaunchedEffect(settings.lookFeel.animationSpeed) { from = page }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val progress = if (from == page) 1f else playback.progress
    val navigate: (Int) -> Unit = { target ->
        if (!playback.running && target != page) {
            from = page
            direction = if (target > page) 1 else -1
            page = target
            indicatorVisible = true
            reveal++
            playback.replay(home.pageSnapDurationMs.toLong())
        }
    }
    LaunchedEffect(actualCount) {
        if (actualCount > priorCount && !demonstrate) navigate(actualCount - 1)
        priorCount = actualCount
    }
    val move: (Int) -> Unit = { step ->
        val target = previewPageTarget(page, count, step, home.infiniteScroll, !home.googleDiscoverEnabled)
        if (target != page) {
            navigate(target)
            direction = step
        }
    }
    LaunchedEffect(reveal, home.pageIndicatorAutoHide) {
        if (home.pageIndicatorAutoHide && reveal > 0) {
            delay(motion.duration(360) + 700)
            indicatorVisible = false
        } else if (!home.pageIndicatorAutoHide) indicatorVisible = true
    }
    val description = stringResource(R.string.preview_home_actual_description, actualCount, page + 1, count,
        stringResource(if (demonstrate) R.string.preview_home_demo_mode else R.string.preview_home_actual_mode))
    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier.fillMaxWidth().height(128.dp).padding(horizontal = 16.dp).clip(RoundedCornerShape(16.dp))
                .semantics { contentDescription = description }
                .pointerInput(page, count, home.infiniteScroll, home.googleDiscoverEnabled, rtl, playback.running) {
                    var dragged = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { dragged = 0f },
                        onHorizontalDrag = { change, amount -> change.consume(); dragged += amount },
                        onDragCancel = { dragged = 0f },
                        onDragEnd = {
                            if (abs(dragged) > 24.dp.toPx()) move(if ((dragged < 0) != rtl) 1 else -1)
                        },
                    )
                },
        ) {
            Canvas(Modifier.matchParentSize()) {
                val virtualPage = from + (page - from) * progress
                val offset = WallpaperOffsets.xOffset((virtualPage * 1_000).toInt(), 1_000, count, home.wallpaperScrolling)
                drawRect(Brush.linearGradient(PREVIEW_WALLPAPER.map { Color(it) }))
                // Original geometric wallpaper marks make wallpaper scrolling visible.
                val shift = (offset - .5f) * size.width * .45f * if (rtl) -1 else 1
                drawCircle(Color(0xFF6AADC0).copy(alpha = .55f), size.height * .9f,
                    Offset(size.width * .2f - shift, -size.height * .2f))
                drawCircle(Color(0xFFB38DCE).copy(alpha = .45f), size.height * .8f,
                    Offset(size.width * .9f - shift, size.height * 1.1f))
            }
            val visualDirection = direction * if (rtl) -1 else 1
            if (from != page && progress < 1f) HomeSamplePage(from, -progress * visualDirection, home.transitionEffect, home.pageEffectStrength)
            HomeSamplePage(page, if (from == page) 0f else (1 - progress) * visualDirection, home.transitionEffect, home.pageEffectStrength)
            if (home.pageIndicatorStyle != PageIndicatorStyle.NONE) {
                Canvas(Modifier.fillMaxWidth().height(18.dp).align(Alignment.BottomCenter).graphicsLayer { alpha = indicatorAlpha }) {
                    val total = minOf(count, 12) * 9.dp.toPx()
                    val start = (size.width - total) / 2f
                    val visualPage = if (rtl) count - 1 - page else page
                    when (home.pageIndicatorStyle) {
                        PageIndicatorStyle.DOTS -> repeat(minOf(count, 12)) { index ->
                            // Long sample workspaces keep the active page visible in a bounded strip.
                            val slot = (visualPage * minOf(count, 12) / count).coerceAtMost(11)
                            drawCircle(Color.White.copy(alpha = if (index == slot) 1f else .35f), 2.dp.toPx(),
                                Offset(start + (index + .5f) * 9.dp.toPx(), size.height / 2f))
                        }
                        PageIndicatorStyle.LINE -> {
                            drawRoundRect(Color.White.copy(alpha = .3f), Offset(start, size.height / 2f - 2.dp.toPx()),
                                Size(total, 4.dp.toPx()), CornerRadius(2.dp.toPx()))
                            val segment = total / count
                            drawRoundRect(Color.White, Offset(start + visualPage * segment, size.height / 2f - 2.dp.toPx()),
                                Size(segment, 4.dp.toPx()), CornerRadius(2.dp.toPx()))
                        }
                        PageIndicatorStyle.NONE -> Unit
                    }
                }
            }
        }
        Text(stringResource(R.string.preview_home_actual_caption, actualCount, page + 1, count, default + 1),
            Modifier.padding(horizontal = 24.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall,
            minLines = 2, maxLines = 2)
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            val previous = stringResource(R.string.preview_page_previous)
            IconButton(onClick = { move(-1) }, enabled = !playback.running &&
                previewPageTarget(page, count, -1, home.infiniteScroll, !home.googleDiscoverEnabled) != page,
                modifier = Modifier.semantics { contentDescription = previous }) {
                Icon(painterResource(R.drawable.ic_settings_back), null,
                    Modifier.graphicsLayer { scaleX = if (rtl) -1f else 1f })
            }
            Row(Modifier.weight(1f).height((32f + 32f * LocalDensity.current.fontScale.coerceIn(1f, 3f)).dp)) {
                TextButton(onClick = { navigate(default) }, enabled = !playback.running && page != default,
                    modifier = Modifier.weight(1f).height((32f + 32f * LocalDensity.current.fontScale.coerceIn(1f, 3f)).dp)) { Text(stringResource(R.string.preview_home_default), maxLines = 2) }
                TextButton(onClick = { demonstrate = !demonstrate }, enabled = !playback.running,
                    modifier = Modifier.weight(1f).height((32f + 32f * LocalDensity.current.fontScale.coerceIn(1f, 3f)).dp)) {
                    Text(stringResource(if (demonstrate) R.string.preview_home_actual_mode else R.string.preview_home_demo_mode), maxLines = 2)
                }
            }
            val next = stringResource(R.string.preview_page_next)
            IconButton(onClick = { move(1) }, enabled = !playback.running &&
                previewPageTarget(page, count, 1, home.infiniteScroll) != page,
                modifier = Modifier.semantics { contentDescription = next }) {
                Icon(painterResource(R.drawable.ic_settings_back), null,
                    Modifier.graphicsLayer { scaleX = if (rtl) 1f else -1f })
            }
        }
    }
}

@Composable
private fun HomeSamplePage(page: Int, position: Float, effect: PageTransitionEffect, strength: Float) {
    Canvas(Modifier.fillMaxWidth().height(110.dp).graphicsLayer {
        translationX = position * size.width
        val transform = io.github.aj4abinjacob.chikku.core.model.settings.PageMotion.transform(position, effect, strength, true)
        alpha = transform.alpha
        scaleX = transform.scale
        scaleY = transform.scale
        rotationY = transform.rotationY
        transformOrigin = TransformOrigin(transform.pivot, .5f)
        cameraDistance = size.width * 12f
    }) {
        val cellWidth = size.width / 5f
        val cellHeight = size.height / 2f
        repeat(8) { index ->
            val col = index % 4
            val row = index / 4
            val center = Offset(cellWidth * (col + 1f), cellHeight * (row + .5f))
            val radius = minOf(cellWidth, cellHeight) * .25f
            drawCircle(Color(PREVIEW_SAMPLE_COLORS[(index + page) % PREVIEW_SAMPLE_COLORS.size]), radius, center)
            drawRoundRect(Color.White.copy(alpha = .8f), Offset(center.x - radius, center.y + radius * 1.25f),
                Size(radius * 2, radius * .25f), CornerRadius(radius))
        }
    }
}

/** Native dock icons over synthetic HOME, plus deliberate controls that never launch an app. */
@Composable
fun DockInteractionLivePreview(
    settings: LauncherSettings,
    behavior: Boolean = false,
    includeSearch: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val dock = settings.dock
    val count = dock.effectivePages()
    var page by remember { mutableIntStateOf(if (dock.rememberPosition) dock.lastPage.coerceIn(0, count - 1) else 0) }
    var from by remember { mutableIntStateOf(page) }
    var previousCount by remember { mutableIntStateOf(count) }
    var direction by remember { mutableIntStateOf(1) }
    var shown by remember(dock.displayAsOverlay, dock.enabled) { mutableStateOf(true) }
    var launched by remember(dock.displayAsOverlay, dock.automaticallyClose) { mutableStateOf(false) }
    val playback = rememberPreviewPlayback(settings.lookFeel.animationSpeed, count)
    val visibilityPlayback = rememberPreviewPlayback(settings.lookFeel.animationSpeed, dock.displayAsOverlay)
    var visibilityAnimating by remember { mutableStateOf(false) }
    LaunchedEffect(settings.lookFeel.animationSpeed, count) {
        // A page-count edit gets one finite demonstration, including while the slider is held.
        // Other edits and opening the settings page leave the sample idle.
        from = page.coerceIn(0, count - 1)
        page = if (count > previousCount && !behavior && !includeSearch) count - 1 else from
        if (page != from) { direction = 1; playback.replay(280) }
        previousCount = count
    }
    LaunchedEffect(settings.lookFeel.animationSpeed, dock.displayAsOverlay) { visibilityAnimating = false }
    val navigate: (Int, Int) -> Unit = { target, step ->
        if (!playback.running && target != page) {
            from = page; page = target; direction = step; playback.replay(280)
        }
    }
    val move: (Int) -> Unit = { step -> navigate(previewPageTarget(page, count, step, dock.infiniteScroll), step) }
    var dragged by remember { mutableFloatStateOf(0f) }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Column(modifier.fillMaxWidth()) {
        DockPreview(
            settings,
            Modifier.fillMaxWidth().height(if (includeSearch) 184.dp else 136.dp)
                .pointerInput(page, count, dock.infiniteScroll, rtl, playback.running) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragged = 0f },
                        onHorizontalDrag = { change, amount -> change.consume(); dragged += amount },
                        onDragCancel = { dragged = 0f },
                        onDragEnd = { if (abs(dragged) > 24.dp.toPx()) move(if ((dragged < 0) != rtl) 1 else -1) },
                    )
                },
            samplePage = page,
            previousPage = from,
            pageProgress = if (from == page) 1f else playback.progress,
            pageDirection = direction,
            dockVisibility = if (!visibilityAnimating) { if (shown) 1f else 0f }
                else if (shown) visibilityPlayback.progress else 1 - visibilityPlayback.progress,
            includeSearch = includeSearch,
        )
        if (behavior) {
            Text(stringResource(when {
                !dock.displayAsOverlay -> R.string.preview_dock_reserved_space
                launched && !shown -> R.string.preview_dock_launch_closed
                launched -> R.string.preview_dock_launch_stays
                else -> R.string.preview_dock_over_home
            }), Modifier.padding(horizontal = 24.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                if (dock.displayAsOverlay) TextButton(onClick = {
                    shown = !shown; launched = false; visibilityAnimating = true; visibilityPlayback.replay(240)
                }, enabled = !visibilityPlayback.running, modifier = Modifier.weight(1f)) {
                    Text(stringResource(if (shown) R.string.preview_dock_hide else R.string.preview_dock_show))
                }
                TextButton(onClick = {
                    launched = true
                    val after = previewDockAfterLaunch(dock, shown)
                    if (after != shown) { shown = after; visibilityAnimating = true; visibilityPlayback.replay(240) }
                }, enabled = shown && !visibilityPlayback.running, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.preview_dock_launch)) }
            }
        } else if (!includeSearch) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(when {
                    count < dock.pages -> stringResource(R.string.preview_dock_limited_caption, page + 1, count, dock.pages)
                    count > 1 -> stringResource(R.string.preview_dock_page_caption, page + 1, count)
                    else -> stringResource(R.string.preview_dock_single_page_caption)
                },
                    Modifier.weight(1f).padding(vertical = 4.dp), style = MaterialTheme.typography.labelSmall,
                    minLines = 2, maxLines = 2)
                val reopen = stringResource(R.string.preview_dock_reopen)
                IconButton(onClick = {
                    page = previewDockReopenedPage(dock, page)
                    from = page
                }, enabled = count > 1 && !playback.running,
                    modifier = Modifier.semantics { contentDescription = reopen }) {
                    Icon(painterResource(R.drawable.ic_menu_refresh), null)
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                val previous = stringResource(R.string.preview_page_previous)
                IconButton(onClick = { move(-1) }, enabled = !playback.running &&
                    previewPageTarget(page, count, -1, dock.infiniteScroll) != page,
                    modifier = Modifier.semantics { contentDescription = previous }) {
                    Icon(painterResource(R.drawable.ic_settings_back), null,
                        Modifier.graphicsLayer { scaleX = if (rtl) -1f else 1f })
                }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center) {
                    repeat(count) { index ->
                        val description = stringResource(R.string.preview_dock_select_page, index + 1, count)
                        IconButton(onClick = { navigate(index, if (index > page) 1 else -1) },
                            enabled = !playback.running,
                            modifier = Modifier.semantics { contentDescription = description; selected = index == page }) {
                            Box(Modifier.size(28.dp).background(
                                if (index == page) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                CircleShape), contentAlignment = Alignment.Center) {
                                Text((index + 1).toString(), color = if (index == page) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
                val next = stringResource(R.string.preview_page_next)
                IconButton(onClick = { move(1) }, enabled = !playback.running &&
                    previewPageTarget(page, count, 1, dock.infiniteScroll) != page,
                    modifier = Modifier.semantics { contentDescription = next }) {
                    Icon(painterResource(R.drawable.ic_settings_back), null,
                        Modifier.graphicsLayer { scaleX = if (rtl) 1f else -1f })
                }
            }
        }
    }
}
