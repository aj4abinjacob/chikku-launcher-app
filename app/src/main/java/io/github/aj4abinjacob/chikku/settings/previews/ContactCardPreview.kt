package io.github.aj4abinjacob.chikku.settings.previews

import android.graphics.drawable.Drawable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.LauncherApp
import io.github.aj4abinjacob.chikku.core.model.ProfileType
import io.github.aj4abinjacob.chikku.core.model.search.CommunicationProvider
import io.github.aj4abinjacob.chikku.core.model.search.CommunicationProviders
import io.github.aj4abinjacob.chikku.core.model.search.ContactCardButton
import io.github.aj4abinjacob.chikku.core.model.search.ContactChannelAction
import io.github.aj4abinjacob.chikku.core.model.search.ContactHit
import io.github.aj4abinjacob.chikku.core.model.search.ContactService
import io.github.aj4abinjacob.chikku.core.model.search.ContactServices
import io.github.aj4abinjacob.chikku.core.model.search.ResolvedCardButton
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.platform.IconProvider
import io.github.aj4abinjacob.chikku.search.PersonCard
import io.github.aj4abinjacob.chikku.search.SearchTheme

/**
 * The real search contact card, drawn with a sample contact who can use every chosen button, so
 * changes to Contact card buttons show immediately. Synthetic only: no contact is read and app
 * buttons show a generic icon and the brand name rather than an installed app's icon.
 */
@Composable
fun ContactCardLivePreview(settings: LauncherSettings, modifier: Modifier = Modifier) {
    val buttons = ContactServices.buttons(settings.search)
    val sampleName = stringResource(R.string.preview_contact_card_name)
    val sampleNumber = stringResource(R.string.preview_contact_card_number)
    val services = remember { CommunicationProviders.KNOWN.map(::sampleService) }
    val contact = remember(buttons, sampleName, sampleNumber) {
        ContactHit(
            id = -1, lookupKey = "preview", name = sampleName, phoneNumber = sampleNumber,
            channelActions = buttons.mapNotNull { button ->
                val provider = CommunicationProviders.KNOWN.firstOrNull { it.id == button.providerId }
                val entry = provider?.entryPoints?.firstOrNull { it.type == button.type } ?: return@mapNotNull null
                ContactChannelAction(provider.id, button.type, -1, entry.mimeType)
            },
        )
    }
    val resolved = ContactServices.resolve(buttons, contact, services)
    val names = resolved.map { button ->
        when (button) {
            ResolvedCardButton.Call -> stringResource(R.string.search_person_call)
            ResolvedCardButton.Text -> stringResource(R.string.search_person_text)
            is ResolvedCardButton.Service -> button.service.app.label
        }
    }
    val description = stringResource(R.string.preview_contact_card_description, names.joinToString(", "))
    val envelope = rememberSearchPreviewEnvelope(256.dp)
    SearchTheme(settings) {
        Box(
            modifier.fillMaxWidth().height(envelope).padding(horizontal = 4.dp, vertical = 4.dp)
                .focusProperties { canFocus = false }
                .clearAndSetSemantics { contentDescription = description },
        ) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(R.string.preview_contact_card_label),
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PersonCard(contact, resolved, NoAppIcons) { }
            }
        }
    }
}

private fun sampleService(provider: CommunicationProvider) = ContactService(
    provider,
    LauncherApp(AppKey(provider.packageName, "preview", 0), provider.displayName, ProfileType.PERSONAL),
)

/** Never loads an installed app's icon; the card falls back to a generic chat glyph. */
private object NoAppIcons : IconProvider {
    override suspend fun icon(key: AppKey): Drawable = throw IllegalStateException("preview")
    override fun invalidate(packageName: String) = Unit
}
