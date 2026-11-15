package io.github.aj4abinjacob.chikku.settings.previews

import androidx.compose.foundation.background
import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.core.model.settings.SearchPresentation
import io.github.aj4abinjacob.chikku.core.model.settings.SearchResultsAnchor
import io.github.aj4abinjacob.chikku.settings.sections.labelRes
import io.github.aj4abinjacob.chikku.search.SearchTheme
import io.github.aj4abinjacob.chikku.search.SearchAppPriorityRow
import io.github.aj4abinjacob.chikku.settings.dock.HomeBarScenePreview

/** Opened search, the combined home bar scene, and offline synthetic result compositions. */
enum class SearchPreviewMode { WINDOW, HOME_BAR, HOME_BAR_SIZE, RESULTS, CONTACTS, WEB, ANSWERS }

/** Field, a results row and a sample keyboard, so moving results between field and keyboard reads at a glance. */
val SearchWindowPreviewHeight = 196.dp
/** Room for the tallest bar and widest dock gap at actual size, with part of the dock above. */
val SearchBarPreviewHeight = 232.dp

/** Every sample uses only original local glyphs and fixed examples; no query or provider is started. */
@Composable
fun SearchLivePreview(settings: LauncherSettings, mode: SearchPreviewMode, modifier: Modifier = Modifier) {
    when (mode) {
        SearchPreviewMode.WINDOW -> SearchWindowPreview(settings, modifier.height(SearchWindowPreviewHeight))
        // Actual size: a whole phone scaled into the preview made bar sizes and the gap unreadable.
        SearchPreviewMode.HOME_BAR, SearchPreviewMode.HOME_BAR_SIZE -> HomeBarScenePreview(settings,
            rememberSearchPreviewEnvelope(SearchBarPreviewHeight), markGap = mode == SearchPreviewMode.HOME_BAR_SIZE, modifier)
        else -> SearchResultsLivePreview(settings, mode, modifier)
    }
}

@Composable
private fun SearchWindowPreview(settings: LauncherSettings, modifier: Modifier) {
    val search = settings.search
    val radius = search.cornerRadiusDp.takeIf { it.isFinite() }?.coerceIn(0f, 48f) ?: 20f
    val windowed = search.presentation == SearchPresentation.WINDOW
    val nearKeyboard = search.resultsAnchor == SearchResultsAnchor.KEYBOARD
    val description = stringResource(R.string.preview_search_window_description,
        stringResource(if (windowed) R.string.search_settings_window else R.string.search_settings_fullscreen),
        stringResource(search.resultsAnchor.labelRes()))
    Box(
        modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description }
            .background(Brush.linearGradient(PREVIEW_WALLPAPER.map { Color(it) })),
    ) {
        SearchTheme(settings) {
            val colors = MaterialTheme.colorScheme
            val shape = RoundedCornerShape((if (windowed) radius.coerceAtMost(28f) else radius).dp)
            val outline = colors.outlineVariant.copy(alpha = .5f)
            Column(Modifier.fillMaxSize()) {
                Box(
                    Modifier.weight(1f).fillMaxWidth()
                        .then(if (windowed) Modifier.background(Color.Black.copy(alpha = if (colors.surface.luminance() < .5f) .6f else .45f))
                            .padding(horizontal = 16.dp, vertical = 8.dp) else Modifier),
                    contentAlignment = if (windowed && nearKeyboard) Alignment.BottomCenter else Alignment.TopCenter,
                ) {
                    val field: @Composable () -> Unit = {
                        Row(
                            Modifier.fillMaxWidth().height(if (windowed) 44.dp else 54.dp)
                                .then(if (windowed) Modifier.padding(horizontal = 14.dp) else Modifier.padding(10.dp).clip(shape)
                                    .background(colors.surfaceContainerHighest).padding(horizontal = 14.dp)),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(painterResource(if (windowed) R.drawable.ic_search else R.drawable.ic_settings_back), contentDescription = null,
                                tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(R.string.search_hint), color = colors.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    val results: @Composable () -> Unit = {
                        SearchAppPriorityRow(search.appPriorityDirection, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                            SEARCH_PREVIEW_APPS.indices.take(4).forEach { index ->
                                SearchPreviewAppTile(index, Modifier.weight(1f), iconSize = 30.dp)
                            }
                        }
                    }
                    Column(
                        (if (windowed) Modifier.fillMaxWidth().shadow(8.dp, shape).clip(shape).border(1.dp, outline, shape)
                            .background(colors.surfaceContainerHigh) else Modifier.fillMaxSize().background(colors.surface)),
                    ) {
                        // Window: wraps its content, docks to the keyboard when results stay near it. Full screen: field on top.
                        if (windowed && nearKeyboard) { results(); HorizontalDivider(color = outline); field() }
                        else {
                            field()
                            if (windowed) HorizontalDivider(color = outline)
                            if (nearKeyboard) Spacer(Modifier.weight(1f, fill = !windowed))
                            results()
                        }
                    }
                }
                PreviewKeyboard()
            }
        }
    }
}

/** A plain stand-in for the on-screen keyboard: three rows of keys, no letters. */
@Composable
private fun PreviewKeyboard() {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().height(40.dp).background(colors.surfaceContainerHigh).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        listOf(10, 9, 7).forEach { keys ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                repeat(keys) {
                    Box(Modifier.padding(horizontal = 2.dp).width(18.dp).height(8.dp).clip(RoundedCornerShape(2.dp)).background(colors.surfaceContainerHighest))
                }
            }
        }
    }
}
