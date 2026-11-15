package io.github.aj4abinjacob.chikku.settings.backup

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.backup.BackupMessage
import io.github.aj4abinjacob.chikku.backup.RestorePreview
import io.github.aj4abinjacob.chikku.core.model.backup.RestoreAttention
import io.github.aj4abinjacob.chikku.settings.SettingsActivity
import io.github.aj4abinjacob.chikku.settings.SettingsSection
import java.util.Date

/**
 * Restore UI shared by Settings > Backup and first-run onboarding, so both show the same preview,
 * messages and "check these restored settings" card.
 */
@Composable
fun RestorePreviewDialog(preview: RestorePreview, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val s = preview.summary
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.backup_preview_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val created = preview.backup.createdAtMillis
                Text(
                    if (created > 0) {
                        stringResource(
                            R.string.backup_preview_created,
                            DateFormat.getMediumDateFormat(context).format(Date(created)),
                            preview.backup.appVersion,
                        )
                    } else {
                        stringResource(R.string.backup_preview_created_unknown)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(stringResource(R.string.backup_preview_intro), style = MaterialTheme.typography.bodyMedium)
                Bullet(pluralStringResource(R.plurals.backup_preview_pages, s.pages, s.pages))
                Bullet(pluralStringResource(R.plurals.backup_preview_items, s.items, s.items))
                if (s.folders > 0) Bullet(pluralStringResource(R.plurals.backup_preview_folders, s.folders, s.folders))
                if (s.widgets > 0) Bullet(pluralStringResource(R.plurals.backup_preview_widgets, s.widgets, s.widgets))
                if (s.shortcuts > 0) {
                    Bullet(pluralStringResource(R.plurals.backup_preview_shortcuts, s.shortcuts, s.shortcuts))
                    Text(stringResource(R.string.backup_shortcuts_note), style = MaterialTheme.typography.bodyMedium)
                }
                if (s.customizations > 0) {
                    Bullet(pluralStringResource(R.plurals.backup_preview_customizations, s.customizations, s.customizations))
                }
                if (s.drawerGroups > 0) Bullet(pluralStringResource(R.plurals.backup_preview_groups, s.drawerGroups, s.drawerGroups))
                val missing = s.missingApps
                when {
                    missing == null -> Text(stringResource(R.string.backup_preview_missing_unknown), style = MaterialTheme.typography.bodyMedium)
                    missing > 0 -> Text(
                        pluralStringResource(R.plurals.backup_preview_missing, missing, missing),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (preview.attention.isNotEmpty()) {
                    Text(stringResource(R.string.backup_preview_attention), style = MaterialTheme.typography.bodyMedium)
                    preview.attention.forEach { Bullet(stringResource(it.textRes())) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.backup_preview_confirm)) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.backup_preview_cancel)) } },
    )
}

/**
 * Restored settings this device cannot fully honour yet, each with a link to the setting that
 * explains or fixes it. The settings themselves are kept as restored.
 */
@Composable
fun RestoreAttentionCard(attention: List<RestoreAttention>, onDismiss: (() -> Unit)?, modifier: Modifier = Modifier) {
    if (attention.isEmpty()) return
    val context = LocalContext.current
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.restore_attention_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics {
                    heading()
                    liveRegion = LiveRegionMode.Polite
                },
            )
            attention.forEach { item ->
                Text(stringResource(item.textRes()), style = MaterialTheme.typography.bodyMedium)
                TextButton(
                    onClick = { context.openSettingsSection(item.section()) },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.restore_attention_open)) }
            }
            if (onDismiss != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.backup_dismiss)) }
                }
            }
        }
    }
}

@Composable
private fun Bullet(text: String) {
    Text("• $text", style = MaterialTheme.typography.bodyLarge)
}

@StringRes
fun BackupMessage.text(): Int = when (this) {
    BackupMessage.SAVED -> R.string.backup_msg_saved
    BackupMessage.SAVE_FAILED -> R.string.backup_msg_save_failed
    BackupMessage.SHARE_FAILED -> R.string.backup_msg_share_failed
    BackupMessage.READ_FAILED -> R.string.backup_msg_read_failed
    BackupMessage.TOO_LARGE -> R.string.backup_msg_too_large
    BackupMessage.NOT_JSON -> R.string.backup_msg_not_json
    BackupMessage.NOT_A_BACKUP -> R.string.backup_msg_not_a_backup
    BackupMessage.NEWER_VERSION -> R.string.backup_msg_newer_version
    BackupMessage.DAMAGED -> R.string.backup_msg_damaged
    BackupMessage.RESTORED -> R.string.backup_msg_restored
    BackupMessage.RESTORE_FAILED -> R.string.backup_msg_restore_failed
    BackupMessage.RECOVERY_REQUIRED -> R.string.backup_msg_recovery_required
    BackupMessage.RESET_DONE -> R.string.backup_msg_reset_done
    BackupMessage.RESET_FAILED -> R.string.backup_msg_reset_failed
    BackupMessage.UNDONE -> R.string.backup_msg_undone
    BackupMessage.UNDO_FAILED -> R.string.backup_msg_undo_failed
}

@StringRes
fun RestoreAttention.textRes(): Int = when (this) {
    RestoreAttention.DISCOVER_NEEDS_GOOGLE_APP -> R.string.restore_attention_discover_google
    RestoreAttention.DISCOVER_NEEDS_BRIDGE -> R.string.restore_attention_discover_bridge
    RestoreAttention.DISCOVER_BRIDGE_NOT_WORKING -> R.string.restore_attention_discover_bridge_broken
    RestoreAttention.ICON_PACK_MISSING -> R.string.restore_attention_icon_pack
    RestoreAttention.DOCK_IMAGE_UNREADABLE -> R.string.restore_attention_dock_image
    RestoreAttention.CONTACT_SERVICE_APP_MISSING -> R.string.restore_attention_contact_services
}

/** The settings section that shows the live status of, or fixes, [this]. */
fun RestoreAttention.section(): SettingsSection = when (this) {
    RestoreAttention.DISCOVER_NEEDS_GOOGLE_APP,
    RestoreAttention.DISCOVER_NEEDS_BRIDGE,
    RestoreAttention.DISCOVER_BRIDGE_NOT_WORKING -> SettingsSection.HOME_SCREEN
    RestoreAttention.ICON_PACK_MISSING -> SettingsSection.LOOK_AND_FEEL
    RestoreAttention.DOCK_IMAGE_UNREADABLE -> SettingsSection.DOCK
    RestoreAttention.CONTACT_SERVICE_APP_MISSING -> SettingsSection.SEARCH
}

private fun Context.openSettingsSection(section: SettingsSection) {
    try {
        startActivity(Intent(this, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_SECTION, section.name))
    } catch (_: ActivityNotFoundException) {
        // Settings is part of this app; nothing else to fall back to.
    }
}
