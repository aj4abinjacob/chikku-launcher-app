package io.github.aj4abinjacob.chikku.settings.previews

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.search.RowLayout
import io.github.aj4abinjacob.chikku.search.SearchTheme
import io.github.aj4abinjacob.chikku.search.SearchAppPriorityRow
import io.github.aj4abinjacob.chikku.search.labelRes

/** Already learned examples stay visible when learning is paused, matching the history contract. */
@Composable
fun SearchHistoryLivePreview(settings: LauncherSettings, modifier: Modifier = Modifier) {
    val search = settings.search
    val shown = previewSearchSuggestions(search).isNotEmpty()
    val rowsDescription = stringResource(if (shown) R.string.preview_search_history_shown else R.string.preview_search_history_hidden)
    val learningDescription = stringResource(if (search.historyLearningEnabled) R.string.preview_search_history_learning else R.string.preview_search_history_paused)
    val description = stringResource(R.string.preview_search_history_state, rowsDescription, learningDescription) +
        if (shown) " " + stringResource(R.string.search_app_priority_preview_state, stringResource(search.appPriorityDirection.labelRes())) else ""
    val envelope = rememberSearchPreviewEnvelope(244.dp)
    SearchTheme(settings) {
        Box(modifier.fillMaxWidth().height(envelope).padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface)
            .clearAndSetSemantics { contentDescription = description }) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp)) {
                Text(learningDescription, style = MaterialTheme.typography.labelSmall)
                if (shown) {
                    Text(stringResource(R.string.search_section_suggested), Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    SearchAppPriorityRow(search.appPriorityDirection, Modifier.fillMaxWidth()) {
                        previewSearchSuggestions(search).forEach { index -> SearchPreviewAppTile(index, Modifier.weight(1f)) }
                    }
                    Text(stringResource(R.string.search_section_recent), Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    RowLayout(stringResource(R.string.preview_search_history_query), leading = {
                        Icon(painterResource(R.drawable.ic_search_history), null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }, subtitle = stringResource(R.string.search_recent_query), onClick = null, compact = true)
                } else Text(rowsDescription, Modifier.padding(top = 16.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
