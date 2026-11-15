package io.github.aj4abinjacob.chikku.settings.sections

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.draw.clip
import io.github.aj4abinjacob.chikku.settings.components.settingsSummaryColor
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.message.ChikkuMessages
import io.github.aj4abinjacob.chikku.message.rememberChikkuMessages
import io.github.aj4abinjacob.chikku.backup.BackupFileNames
import io.github.aj4abinjacob.chikku.backup.BackupMessage
import io.github.aj4abinjacob.chikku.backup.BackupViewModel
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.onboarding.OnboardingActivity
import io.github.aj4abinjacob.chikku.settings.LocalHomeDependencies
import io.github.aj4abinjacob.chikku.settings.backup.RestoreAttentionCard
import io.github.aj4abinjacob.chikku.settings.backup.RestorePreviewDialog
import io.github.aj4abinjacob.chikku.settings.backup.text
import io.github.aj4abinjacob.chikku.settings.components.SettingsInfoText
import io.github.aj4abinjacob.chikku.settings.components.SettingsNavigationRow
import io.github.aj4abinjacob.chikku.settings.components.SettingsPage
import io.github.aj4abinjacob.chikku.settings.components.SettingsSectionHeader

/**
 * Backup and restore (G18), quick start and reset to default (G19). Files go through the Storage
 * Access Framework or the share sheet; there is no network upload. Restore shows a preview with
 * counts first and is undoable; a damaged file never changes the current layout.
 */
@Composable
fun BackupSettingsSection(
    @Suppress("UNUSED_PARAMETER") settings: LauncherSettings,
    @Suppress("UNUSED_PARAMETER") update: ((LauncherSettings) -> LauncherSettings) -> Unit,
) {
    val context = LocalContext.current
    val messages = rememberChikkuMessages()
    val deps = LocalHomeDependencies.current
    val viewModel = remember(context, deps) {
        val activity = context as ComponentActivity
        ViewModelProvider(activity, BackupViewModel.factory(activity, deps))[BackupViewModel::class.java]
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmReset by rememberSaveable { mutableStateOf(false) }

    val createDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BackupFileNames.MIME_TYPE)) { uri ->
        if (uri != null) viewModel.saveTo(uri)
    }
    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.load(uri)
    }

    val chooserTitle = stringResource(R.string.backup_share_chooser)
    LaunchedEffect(state.pendingShare) {
        val uri = state.pendingShare ?: return@LaunchedEffect
        viewModel.shareHandled()
        val send = Intent(Intent.ACTION_SEND)
            .setType(BackupFileNames.MIME_TYPE)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(null, uri)
        context.startSafely(messages, Intent.createChooser(send, chooserTitle))
    }

    SettingsPage {
        if (state.working) {
            val workingLabel = stringResource(R.string.backup_working)
            LinearProgressIndicator(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).semantics { contentDescription = workingLabel },
            )
        }
        StatusCard(state.message, state.canUndo, state.working, onUndo = viewModel::undo, onDismiss = viewModel::messageShown)
        RestoreAttentionCard(
            state.restoredAttention,
            onDismiss = viewModel::attentionShown,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        // The two things people come here for lead the page as the primary actions.
        BackupActions(
            enabled = !state.working,
            onSave = {
                try {
                    createDocument.launch(viewModel.suggestedFileName())
                } catch (_: ActivityNotFoundException) {
                    messages.show(R.string.backup_no_file_app)
                }
            },
            onRestore = {
                try {
                    openDocument.launch(BackupFileNames.OPEN_MIME_TYPES)
                } catch (_: ActivityNotFoundException) {
                    messages.show(R.string.backup_no_file_app)
                }
            },
        )

        SettingsSectionHeader(stringResource(R.string.backup_header_backup))
        SettingsNavigationRow(
            title = stringResource(R.string.backup_share),
            summary = stringResource(R.string.backup_share_summary),
            enabled = !state.working,
            onClick = viewModel::share,
            icon = R.drawable.ic_settings_row_share,
        )

        SettingsInfoText(stringResource(R.string.backup_widgets_note))
        SettingsInfoText(stringResource(R.string.backup_shortcuts_note))

        SettingsSectionHeader(stringResource(R.string.backup_header_reset))
        SettingsNavigationRow(
            title = stringResource(R.string.backup_quick_start),
            summary = stringResource(R.string.backup_quick_start_summary),
            onClick = { context.startSafely(messages, OnboardingActivity.intent(context)) },
            icon = R.drawable.ic_settings_row_rocket_launch,
        )
        SettingsNavigationRow(
            title = stringResource(R.string.backup_reset),
            summary = stringResource(R.string.backup_reset_summary),
            enabled = !state.working,
            onClick = { confirmReset = true },
            icon = R.drawable.ic_settings_row_restart_alt,
        )
        // Page footer: the privacy promise, kept below the actions so they lead the page.
        SettingsInfoText(stringResource(R.string.backup_intro), details = stringResource(R.string.backup_intro_details))
    }

    state.preview?.let { preview ->
        RestorePreviewDialog(preview, onConfirm = viewModel::confirmRestore, onCancel = viewModel::cancelPreview)
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.backup_reset_confirm_title)) },
            text = { Text(stringResource(R.string.backup_reset_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    viewModel.resetToDefault()
                }) { Text(stringResource(R.string.backup_reset_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.settings_cancel)) }
            },
        )
    }
}

/** Save and restore as the page's primary actions; each keeps its one-line explanation beneath. */
@Composable
private fun BackupActions(enabled: Boolean, onSave: () -> Unit, onRestore: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(onClick = onSave, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.backup_save))
        }
        Text(
            stringResource(R.string.backup_save_summary),
            style = MaterialTheme.typography.bodySmall,
            color = settingsSummaryColor(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onRestore, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.backup_restore))
        }
        Text(
            stringResource(R.string.backup_restore_summary),
            style = MaterialTheme.typography.bodySmall,
            color = settingsSummaryColor(),
        )
    }
}

@Composable
private fun StatusCard(message: BackupMessage?, canUndo: Boolean, working: Boolean, onUndo: () -> Unit, onDismiss: () -> Unit) {
    if (message == null && !canUndo) return
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (message != null) {
                Text(
                    text = stringResource(message.text()),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                if (message != null) TextButton(onClick = onDismiss) { Text(stringResource(R.string.backup_dismiss)) }
                if (canUndo) TextButton(onClick = onUndo, enabled = !working) { Text(stringResource(R.string.backup_undo)) }
            }
        }
    }
}

private fun Context.startSafely(messages: ChikkuMessages, intent: Intent) {
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        messages.show(R.string.backup_no_file_app)
    }
}
