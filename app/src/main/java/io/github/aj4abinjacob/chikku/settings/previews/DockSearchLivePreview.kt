package io.github.aj4abinjacob.chikku.settings.previews

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.search.WebSearchUrls
import io.github.aj4abinjacob.chikku.core.model.settings.HomeSearchBarPosition
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.search.RowLayout
import io.github.aj4abinjacob.chikku.search.SearchTheme
import io.github.aj4abinjacob.chikku.settings.dock.HomeBarScenePreview

internal fun previewDockAutocomplete(settings: LauncherSettings): Boolean =
    settings.home.showSearchBar && settings.home.searchBarPosition == HomeSearchBarPosition.BELOW_DOCK &&
        previewOnlineSuggestions(settings.search)

/** Fixed offline examples above the native dock/bar scene. No search source is invoked. */
@Composable
fun DockSearchLivePreview(settings: LauncherSettings, modifier: Modifier = Modifier) {
    val below = settings.home.showSearchBar && settings.home.searchBarPosition == HomeSearchBarPosition.BELOW_DOCK
    val suggestions = previewDockAutocomplete(settings)
    val onlineDescription = stringResource(R.string.preview_dock_search_online_state, WebSearchUrls.engineName(settings.search.webEngine),
        stringResource(when {
            !below -> R.string.preview_dock_search_bar_disabled
            suggestions -> R.string.preview_search_online_on
            else -> R.string.preview_search_online_off
        }))
    val barShown = settings.home.showSearchBar && settings.home.searchBarPosition != HomeSearchBarPosition.NONE
    val dockDescription = stringResource(R.string.preview_dock_search_description,
        stringResource(when {
            !barShown -> R.string.settings_search_position_none
            settings.home.searchBarPosition == HomeSearchBarPosition.TOP -> R.string.settings_search_position_top
            else -> R.string.settings_search_position_below_dock
        }), settings.search.widgetDockGapDp)
    val description = "$dockDescription $onlineDescription"
    val envelope = rememberSearchPreviewEnvelope(284.dp)
    SearchTheme(settings) {
        Box(modifier.fillMaxWidth().height(envelope)
            .clearAndSetSemantics { contentDescription = description }) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(min = 108.dp)
                    .clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(8.dp)) {
                    if (suggestions) {
                        listOf(R.string.preview_search_suggestion_one, R.string.preview_search_suggestion_two).forEach { label ->
                            RowLayout(stringResource(label), leading = {
                                Icon(painterResource(R.drawable.ic_search), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                            }, onClick = null, compact = true)
                        }
                    } else Text(stringResource(if (below) R.string.preview_dock_search_online_disabled else R.string.preview_dock_search_bar_disabled),
                        Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodySmall, maxLines = 2)
                }
                // Actual size with the gap shaded, so each gap step is visible.
                HomeBarScenePreview(settings, 176.dp, markGap = true)
            }
        }
    }
}
