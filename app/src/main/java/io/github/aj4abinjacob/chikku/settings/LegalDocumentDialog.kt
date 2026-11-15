package io.github.aj4abinjacob.chikku.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.platform.copySourceDownloadLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Bundled, offline documents. No WebView, external activity or personal content. */
internal enum class LegalDocument(val title: Int, val asset: String) {
    PRIVACY(R.string.legal_privacy_title, "privacy-policy.txt"),
    PROJECT(R.string.legal_project_title, "gpl-3.0.txt"),
    APACHE(R.string.legal_apache_title, "apache-2.0.txt"),
    ASSETS(R.string.legal_assets_title, "assets.txt"),
    CREATIVE_COMMONS(R.string.legal_cc_by_title, "cc-by-4.0.txt"),
    DEPENDENCIES(R.string.legal_dependencies_title, "dependencies.txt"),
    NATIVE(R.string.legal_native_title, "native.txt"),
    SOURCE(R.string.legal_source_title, "source-access.txt"),
}

@Composable
internal fun LegalDocumentDialog(document: LegalDocument, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val loading = stringResource(R.string.legal_loading)
    val failed = stringResource(R.string.legal_load_failed)
    var sourceCopied by remember(document) { mutableStateOf<Boolean?>(null) }
    val copyLabel = stringResource(R.string.legal_copy_source)
    val paragraphs by produceState(listOf(loading), document, context) {
        value = withContext(Dispatchers.IO) {
            try {
                context.assets.open("legal/${document.asset}").bufferedReader().use { reader ->
                    reader.readText().split(Regex("\\n\\s*\\n")).filter(String::isNotBlank)
                }
            } catch (_: java.io.IOException) {
                listOf(failed)
            }
        }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)
                .windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp),
        ) {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.legal_close)) }
            Text(
                stringResource(document.title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(vertical = 12.dp).semantics { heading() },
            )
            if (document == LegalDocument.SOURCE) {
                TextButton(onClick = { sourceCopied = copySourceDownloadLink(context, copyLabel) }) {
                    Text(copyLabel)
                }
                sourceCopied?.let { copied ->
                    Text(
                        stringResource(if (copied) R.string.legal_source_copied else R.string.legal_source_copy_failed),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
            SelectionContainer {
                LazyColumn {
                    items(paragraphs) { paragraph ->
                        Text(
                            paragraph,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 16.dp),
                        )
                    }
                }
            }
        }
    }
}
