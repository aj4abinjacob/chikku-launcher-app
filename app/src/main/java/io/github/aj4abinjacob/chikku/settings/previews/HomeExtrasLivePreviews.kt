package io.github.aj4abinjacob.chikku.settings.previews

import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.labs.createDebugOverlayText
import kotlin.math.abs

internal enum class DiscoverSample { HOME, FEED, FALLBACK }

internal fun discoverSampleAfterSwipe(enabled: Boolean, fromStart: Boolean): DiscoverSample =
    if (enabled && fromStart) DiscoverSample.FEED else DiscoverSample.HOME

/** Offline spatial demonstration: it never binds Google or opens another application. */
@Composable
internal fun DiscoverLivePreview(enabled: Boolean) {
    var sample by remember(enabled) { mutableStateOf(if (enabled) DiscoverSample.FEED else DiscoverSample.HOME) }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val state = stringResource(when (sample) {
        DiscoverSample.HOME -> R.string.preview_discover_home
        DiscoverSample.FEED -> R.string.preview_discover_feed
        DiscoverSample.FALLBACK -> R.string.preview_discover_fallback
    })
    val description = stringResource(R.string.preview_discover_description,
        stringResource(if (enabled) R.string.settings_state_on else R.string.settings_state_off), state)
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(132.dp).padding(horizontal = 16.dp)
            .background(Brush.linearGradient(PREVIEW_WALLPAPER.map(::Color)), RoundedCornerShape(16.dp))
            .clearAndSetSemantics { contentDescription = description }
            .pointerInput(enabled, rtl) {
                var dragged = 0f
                detectHorizontalDragGestures(onDragStart = { dragged = 0f },
                    onHorizontalDrag = { change, amount -> change.consume(); dragged += amount },
                    onDragCancel = { dragged = 0f }, onDragEnd = {
                        if (abs(dragged) > 24.dp.toPx()) sample = discoverSampleAfterSwipe(enabled, (dragged > 0) != rtl)
                    })
            }, contentAlignment = Alignment.Center) {
            when (sample) {
                DiscoverSample.HOME -> {
                    Canvas(Modifier.matchParentSize()) {
                        repeat(8) { index -> drawCircle(Color(PREVIEW_SAMPLE_COLORS[index % 4]), 13.dp.toPx(),
                            Offset(size.width * (index % 4 + .5f) / 4, size.height * (index / 4 + .6f) / 3)) }
                    }
                    Text(stringResource(if (enabled) R.string.preview_discover_swipe_hint else R.string.preview_discover_disabled),
                        Modifier.align(Alignment.BottomCenter).padding(8.dp), color = Color.White,
                        style = MaterialTheme.typography.labelSmall, maxLines = 2)
                }
                DiscoverSample.FEED -> Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(state, color = Color.White, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    repeat(2) { index -> Text(stringResource(if (index == 0) R.string.preview_discover_card_one else R.string.preview_discover_card_two),
                        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)).padding(8.dp),
                        color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodySmall, maxLines = 1) }
                }
                DiscoverSample.FALLBACK -> Text(stringResource(R.string.preview_discover_fallback_hint),
                    Modifier.padding(16.dp), color = Color.White, style = MaterialTheme.typography.bodySmall, maxLines = 3)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            TextButton(onClick = { sample = DiscoverSample.HOME }, Modifier.weight(1f)) {
                Text(stringResource(R.string.preview_discover_home), maxLines = 2)
            }
            TextButton(onClick = { sample = discoverSampleAfterSwipe(enabled, true) }, enabled = enabled,
                modifier = Modifier.weight(1f)) { Text(stringResource(R.string.preview_discover_try), maxLines = 2) }
            TextButton(onClick = { sample = DiscoverSample.FALLBACK }, enabled = enabled,
                modifier = Modifier.weight(1f)) { Text(stringResource(R.string.preview_discover_try_fallback), maxLines = 2) }
        }
        Text(stringResource(R.string.preview_discover_synthetic), Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.labelSmall, maxLines = 2)
    }
}

/** Same native text styling as HOME; measurements are fixed fictional examples, never a benchmark. */
@Composable
internal fun DebugOverlayLivePreview(enabled: Boolean) {
    val description = stringResource(R.string.preview_debug_description,
        stringResource(if (enabled) R.string.settings_state_on else R.string.settings_state_off))
    Box(Modifier.fillMaxWidth().height(maxOf(144f, 84f + 52f * LocalDensity.current.fontScale.coerceIn(1f, 3f)).dp).padding(horizontal = 16.dp)
        .background(Brush.linearGradient(PREVIEW_WALLPAPER.map(::Color)), RoundedCornerShape(16.dp))
        .clearAndSetSemantics { contentDescription = description }) {
        Canvas(Modifier.matchParentSize()) {
            repeat(12) { index -> drawCircle(Color(PREVIEW_SAMPLE_COLORS[index % 4]), 12.dp.toPx(),
                Offset(size.width * (index % 4 + .5f) / 4, size.height * (index / 4 + .5f) / 3)) }
        }
        AndroidView(factory = { createDebugOverlayText(it) }, modifier = Modifier.align(Alignment.TopCenter).wrapContentWidth(),
            update = { view ->
                view.text = view.context.getString(R.string.labs_overlay_text, 4, 6, 4, 3, 24, 360, 800, 2.0, 16.7, 0)
                view.visibility = if (enabled) View.VISIBLE else View.GONE
                view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            })
        Text(stringResource(R.string.preview_debug_synthetic), Modifier.align(Alignment.BottomCenter).padding(8.dp),
            color = Color.White, style = MaterialTheme.typography.labelSmall, maxLines = 2)
    }
}
