package io.github.aj4abinjacob.chikku.settings.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.settings.LegalDocument
import io.github.aj4abinjacob.chikku.settings.LegalDocumentDialog
import io.github.aj4abinjacob.chikku.settings.components.ChikkuAppIcon
import io.github.aj4abinjacob.chikku.settings.components.SettingsInfoText
import io.github.aj4abinjacob.chikku.settings.components.SettingsNavigationRow
import io.github.aj4abinjacob.chikku.settings.components.SettingsPage
import io.github.aj4abinjacob.chikku.settings.components.SettingsSectionHeader
import io.github.aj4abinjacob.chikku.settings.components.SettingsTextRow

/**
 * What a home app is, default-launcher status and privacy statement (owner: backup-onboarding).
 * Reads but never changes the HOME role; the callbacks open the system role request/picker.
 */
@Composable
fun AboutSettingsSection(
    isDefaultHome: Boolean?,
    versionName: String,
    onSetDefault: () -> Unit,
    onChooseOtherHome: () -> Unit,
    onVersionTap: () -> Unit = {},
) {
    var document by remember { mutableStateOf<LegalDocument?>(null) }
    document?.let { LegalDocumentDialog(it) { document = null } }
    SettingsPage {
        AppHeader(versionName, onVersionTap)

        SettingsSectionHeader(stringResource(R.string.about_header_default))
        val status = when (isDefaultHome) {
            null -> R.string.settings_status_checking
            true -> R.string.settings_status_default
            false -> R.string.settings_status_not_default
        }
        SettingsTextRow(
            title = stringResource(status),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            icon = if (isDefaultHome == true) R.drawable.ic_settings_row_check_circle else R.drawable.ic_settings_row_error_outline,
        )
        if (isDefaultHome != true) {
            SettingsNavigationRow(
                title = stringResource(R.string.settings_set_default),
                onClick = onSetDefault,
                icon = R.drawable.ic_settings_row_home,
            )
        }
        SettingsNavigationRow(
            title = stringResource(R.string.settings_choose_other),
            summary = stringResource(R.string.about_choose_other_summary),
            onClick = onChooseOtherHome,
            icon = R.drawable.ic_settings_row_swap_horiz,
        )
        SettingsInfoText(
            stringResource(R.string.about_what_is_home_short),
            details = stringResource(R.string.settings_what_is_home_body) + "\n\n" +
                stringResource(R.string.about_default_instructions),
        )

        SettingsSectionHeader(stringResource(R.string.settings_privacy_title))
        SettingsTextRow(
            title = stringResource(R.string.about_privacy_row_title),
            summary = stringResource(R.string.settings_privacy_body),
            icon = R.drawable.ic_settings_row_privacy_tip,
        )
        for (entry in LegalDocument.entries) {
            SettingsNavigationRow(
                title = stringResource(entry.title),
                onClick = { document = entry },
            )
        }
    }
}

/**
 * App icon, name, version and the product promises at the top of About. Tapping the version is the
 * hidden Labs switch.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppHeader(versionName: String, onVersionTap: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ChikkuAppIcon(size = 64.dp)
            Column {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = stringResource(R.string.settings_version, versionName),
                    modifier = Modifier.clickable(onClick = onVersionTap),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(R.string.about_tag_no_ads, R.string.about_tag_no_account, R.string.about_tag_offline).forEach { tag ->
                Text(
                    text = stringResource(tag),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}
