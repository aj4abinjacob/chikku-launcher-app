package io.github.aj4abinjacob.chikku.settings.previews

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.search.WebSearchUrls
import io.github.aj4abinjacob.chikku.core.model.search.WebSuggestionProviders
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.core.model.settings.SearchSettings
import io.github.aj4abinjacob.chikku.search.SearchTheme
import io.github.aj4abinjacob.chikku.search.RowLayout
import io.github.aj4abinjacob.chikku.search.TopResultLabel
import io.github.aj4abinjacob.chikku.search.topSearchResult
import io.github.aj4abinjacob.chikku.search.SearchAppPriorityRow
import io.github.aj4abinjacob.chikku.search.labelRes

/** Compact app results use one strip; otherwise each match is its own list row, as in search. */
internal fun previewSearchAppRows(search: SearchSettings): List<List<Int>> =
    if (search.maxAppResults > 0) listOf((0 until minOf(search.maxAppResults, SEARCH_PREVIEW_APPS.size)).toList())
    else (0 until EXPANDED_APP_ROWS).map { listOf(it) }

/** Illustrates already learned samples. Showing is independent of whether learning is paused. */
internal fun previewSearchSuggestions(search: SearchSettings): List<Int> =
    if (search.historySuggestionsEnabled) listOf(3, 1, 4, 0, 2) else emptyList()

/** Fictional apps for search previews: original glyphs and generic names, never the user's apps. */
internal val SEARCH_PREVIEW_APPS = listOf(
    Triple(R.drawable.ic_settings_cards, R.string.preview_search_app_notes, 0xFF3F6FD8.toInt()),
    Triple(R.drawable.ic_action_music, R.string.preview_search_app_music, 0xFFB04A8C.toInt()),
    Triple(R.drawable.ic_action_schedule, R.string.preview_search_app_clock, 0xFF2E9D6A.toInt()),
    Triple(R.drawable.ic_action_weather, R.string.preview_search_app_weather, 0xFFD0812B.toInt()),
    Triple(R.drawable.ic_search_chat, R.string.preview_search_app_chat, 0xFF5B5BD6.toInt()),
)

private const val EXPANDED_APP_ROWS = 3

internal enum class PreviewSearchSource { FOLDER, SHORTCUT, STORE }

internal fun previewSearchSources(search: SearchSettings): List<PreviewSearchSource> = buildList {
    if (search.foldersEnabled) add(PreviewSearchSource.FOLDER)
    if (search.shortcutsEnabled) add(PreviewSearchSource.SHORTCUT)
    if (search.playStoreRow) add(PreviewSearchSource.STORE)
}

internal fun previewOnlineSuggestions(search: SearchSettings): Boolean =
    search.showWebSuggestionRow && search.onlineSuggestions && WebSuggestionProviders.supports(search.webEngine)

@Composable
fun SearchResultsLivePreview(settings: LauncherSettings, mode: SearchPreviewMode, modifier: Modifier = Modifier) {
    val search = settings.search
    if (mode == SearchPreviewMode.CONTACTS && search.contactsEnabled) {
        ContactCardLivePreview(settings, modifier)
        return
    }
    val sources = previewSearchSources(search)
    val sourceNames = buildList {
        sources.forEach { source -> add(stringResource(when (source) {
            PreviewSearchSource.FOLDER -> R.string.preview_search_folder_sample
            PreviewSearchSource.SHORTCUT -> R.string.preview_search_shortcut_sample
            PreviewSearchSource.STORE -> R.string.preview_search_store_sample
        })) }
    }
    val description = when (mode) {
        SearchPreviewMode.RESULTS -> stringResource(R.string.preview_search_apps_state,
            stringResource(if (search.maxAppResults > 0) R.string.preview_search_apps_compact else R.string.preview_search_apps_expanded),
            stringResource(R.string.preview_search_typed_sample),
            if (sourceNames.isEmpty()) stringResource(R.string.preview_search_extra_sources_none) else sourceNames.joinToString(", "))
        SearchPreviewMode.CONTACTS -> stringResource(if (search.contactsEnabled) R.string.preview_search_contact_card_state else R.string.preview_search_contacts_off_state)
        SearchPreviewMode.WEB -> stringResource(R.string.preview_search_web_state,
            WebSearchUrls.engineName(search.webEngine),
            stringResource(if (previewOnlineSuggestions(search)) R.string.preview_search_online_on else R.string.preview_search_online_off))
        else -> stringResource(R.string.preview_search_answers_description)
    }.let { base ->
        buildList {
            add(base)
            if (mode == SearchPreviewMode.RESULTS) {
                add(stringResource(R.string.search_top_result_preview_description, stringResource(SEARCH_PREVIEW_APPS.first().second)))
                if (search.maxAppResults > 0) add(stringResource(R.string.search_app_priority_preview_state,
                    stringResource(search.appPriorityDirection.labelRes())))
            }
        }.joinToString(" ")
    }
    val preferredHeight = when (mode) {
        SearchPreviewMode.RESULTS -> 292.dp
        SearchPreviewMode.CONTACTS -> 256.dp
        else -> 180.dp
    }
    val envelope = rememberSearchPreviewEnvelope(preferredHeight)
    SearchTheme(settings) {
        val colors = MaterialTheme.colorScheme
        // Bound the pinned scene on small screens. Only its contents scroll at large font sizes;
        // changes in source availability never resize the scene or move settings controls.
        // Keep accessibility on a fixed outer box. A Scroll layout's semantic bounds can change
        // when its content becomes scrollable, even if its requested height stays the same.
        Box(
            modifier.fillMaxWidth().height(envelope)
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(16.dp)).background(colors.surface)
                .clearAndSetSemantics { contentDescription = description },
        ) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp)) {
                when (mode) {
                    SearchPreviewMode.RESULTS -> {
                        PreviewHeader(stringResource(R.string.search_section_apps))
                        val rows = previewSearchAppRows(search)
                        if (search.maxAppResults > 0) {
                            SearchAppPriorityRow(search.appPriorityDirection, Modifier.fillMaxWidth()) {
                                rows.single().forEach { index -> SearchPreviewAppTile(index, Modifier.weight(1f), topResult = index == 0) }
                            }
                        } else rows.forEach { (index) ->
                            RowLayout(title = stringResource(SEARCH_PREVIEW_APPS[index].second),
                                leading = { SearchPreviewAppIcon(index, 32.dp) }, onClick = null, compact = true, topResult = index == 0)
                        }
                        sources.forEach { source ->
                            when (source) {
                                PreviewSearchSource.FOLDER -> {
                                    PreviewHeader(stringResource(R.string.search_section_folders))
                                    RowLayout(title = stringResource(R.string.preview_search_folder_sample),
                                        subtitle = stringResource(R.string.search_folder_item_count, 3),
                                        leading = { PreviewGlyph(R.drawable.ic_settings_folder) }, onClick = null, compact = true)
                                }
                                PreviewSearchSource.SHORTCUT -> {
                                    PreviewHeader(stringResource(R.string.search_section_shortcuts))
                                    RowLayout(title = stringResource(R.string.preview_search_shortcut_sample),
                                        subtitle = stringResource(R.string.search_shortcut_of),
                                        leading = { SearchPreviewAppIcon(0, 24.dp) }, onClick = null, compact = true)
                                }
                                PreviewSearchSource.STORE -> {
                                    PreviewHeader(stringResource(R.string.search_section_web))
                                    RowLayout(title = stringResource(R.string.preview_search_store_result_sample),
                                        subtitle = stringResource(R.string.search_play_store_summary),
                                        leading = { PreviewGlyph(R.drawable.ic_search) }, onClick = null, compact = true)
                                }
                            }
                        }
                    }
                    SearchPreviewMode.CONTACTS -> if (search.contactsEnabled) {
                        ContactCardLivePreview(settings)
                    } else Text(stringResource(R.string.preview_search_source_off), Modifier.padding(top = 16.dp))
                    SearchPreviewMode.WEB -> if (search.showWebSuggestionRow) {
                        SampleResult(R.drawable.ic_settings_row_public,
                            stringResource(R.string.preview_search_web_engine_sample, WebSearchUrls.engineName(search.webEngine)))
                        if (previewOnlineSuggestions(search)) {
                            SampleResult(R.drawable.ic_search, stringResource(R.string.preview_search_web_suggestion_sample))
                        }
                    } else Text(stringResource(R.string.preview_search_source_off), Modifier.padding(top = 16.dp))
                    else -> {
                        if (search.calculatorEnabled) SampleResult(R.drawable.ic_settings_row_calculate, stringResource(R.string.preview_search_calculation_sample))
                        if (search.microResultsEnabled) SampleResult(R.drawable.ic_settings_row_calculate, stringResource(R.string.preview_search_conversion_sample))
                        if (!search.calculatorEnabled && !search.microResultsEnabled) Text(stringResource(R.string.preview_search_source_off), Modifier.padding(top = 16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewHeader(text: String) {
    Text(text, Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 2.dp),
        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
        maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun PreviewGlyph(glyph: Int) {
    Icon(painterResource(glyph), null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun SearchPreviewAppIcon(index: Int, size: Dp) {
    val (glyph, _, color) = SEARCH_PREVIEW_APPS[index % SEARCH_PREVIEW_APPS.size]
    Box(Modifier.size(size).clip(CircleShape).background(Color(color)), contentAlignment = Alignment.Center) {
        Icon(painterResource(glyph), null, Modifier.size(size * .55f), tint = Color.White)
    }
}

/** One cell of the compact app strip: icon above a single-line label, like the search screen. */
@Composable
internal fun SearchPreviewAppTile(index: Int, modifier: Modifier = Modifier, iconSize: Dp = 36.dp, topResult: Boolean = false) {
    Column(modifier.topSearchResult(topResult).padding(horizontal = 2.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        SearchPreviewAppIcon(index, iconSize)
        if (topResult) TopResultLabel()
        Text(stringResource(SEARCH_PREVIEW_APPS[index % SEARCH_PREVIEW_APPS.size].second), Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SampleResult(glyph: Int, label: String) {
    Row(Modifier.fillMaxWidth().height(52.dp).padding(vertical = 4.dp)
        .clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
        .padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(glyph), contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Text(label, Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodySmall,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
