package io.github.aj4abinjacob.chikku.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.search.ContactActionType
import io.github.aj4abinjacob.chikku.core.model.search.ContactHit
import io.github.aj4abinjacob.chikku.core.model.search.ResolvedCardButton
import io.github.aj4abinjacob.chikku.platform.IconProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The best contact match: name and number, then one row of the user's card buttons (default Call,
 * Message, WhatsApp; see Search › Contacts). Buttons this contact can't use are left out, so
 * there is never an empty slot. Tapping the name opens the contact.
 */
@Composable
internal fun PersonCard(
    contact: ContactHit,
    buttons: List<ResolvedCardButton>,
    iconProvider: IconProvider,
    topResult: Boolean = false,
    onAction: (PersonAction) -> Unit,
) {
    val priorityDescription = stringResource(R.string.search_top_result_description)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .55f))
            .topSearchResult(topResult, cornerRadius = 24.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .semantics { if (topResult) stateDescription = priorityDescription }
                .clickable(onClickLabel = stringResource(R.string.search_person_open, contact.name)) { onAction(PersonAction.Open) }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ContactAvatar(contact, PersonAvatarSize)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                if (topResult) TopResultLabel()
                Text(contact.name, style = MaterialTheme.typography.titleMedium)
                contact.phoneNumber?.let {
                    // Digits stay left-to-right inside right-to-left layouts.
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (buttons.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 10.dp)) {
                buttons.forEach { button -> CardButton(contact, button, iconProvider, onAction, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun CardButton(
    contact: ContactHit,
    button: ResolvedCardButton,
    iconProvider: IconProvider,
    onAction: (PersonAction) -> Unit,
    modifier: Modifier,
) {
    val label: String
    val description: String
    val action: PersonAction
    when (button) {
        ResolvedCardButton.Call -> {
            label = stringResource(R.string.search_person_call)
            description = stringResource(R.string.search_person_call_description, contact.name)
            action = PersonAction.Call
        }
        ResolvedCardButton.Text -> {
            label = stringResource(R.string.search_person_text)
            description = stringResource(R.string.search_person_text_description, contact.name)
            action = PersonAction.TextMessage
        }
        is ResolvedCardButton.Service -> {
            val app = button.service.app.label
            label = when (button.action.type) {
                ContactActionType.MESSAGE -> app
                ContactActionType.VOICE_CALL -> stringResource(R.string.search_service_voice_short, app)
                ContactActionType.VIDEO_CALL -> stringResource(R.string.search_service_video_short, app)
            }
            description = stringResource(
                when (button.action.type) {
                    ContactActionType.MESSAGE -> R.string.search_service_message_description
                    ContactActionType.VOICE_CALL -> R.string.search_service_voice_call_description
                    ContactActionType.VIDEO_CALL -> R.string.search_service_video_call_description
                },
                contact.name, app,
            )
            action = PersonAction.Service(button.action)
        }
    }
    Column(
        modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(16.dp))
            .semantics { contentDescription = description }
            .clickable(role = Role.Button) { onAction(action) }
            .padding(horizontal = 2.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            when (button) {
                ResolvedCardButton.Call -> Icon(painterResource(R.drawable.ic_search_call), null, tint = MaterialTheme.colorScheme.primary)
                ResolvedCardButton.Text -> Icon(painterResource(R.drawable.ic_search_message), null, tint = MaterialTheme.colorScheme.primary)
                // The app's own icon tells WhatsApp from Signal; the label names it too.
                is ResolvedCardButton.Service -> AppBadge(button.service.app.key, iconProvider, button.action.type)
            }
        }
        Spacer(Modifier.size(4.dp))
        // Wraps rather than truncating at large font sizes; the button grows instead.
        Text(
            label,
            modifier = Modifier.clearAndSetSemantics { },
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The provider app's own monochrome glyph, tinted like the Call and Message glyphs; a generic chat
 * glyph when the app ships none (the label still names it). A small call/video mark sets call
 * actions apart from chats.
 */
@Composable
private fun AppBadge(key: AppKey, iconProvider: IconProvider, type: ContactActionType) {
    val sizePx = with(LocalDensity.current) { AppGlyphSize.roundToPx() }
    val revision by iconProvider.iconRevision.collectAsState()
    val glyph by produceState<ImageBitmap?>(initialValue = null, key, sizePx, revision) {
        value = try {
            iconProvider.monochromeIcon(key)?.let { icon ->
                withContext(Dispatchers.Default) { icon.toBitmap(sizePx, sizePx).asImageBitmap() }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: RuntimeException) {
            null
        }
    }
    Box(Modifier.size(AppGlyphSize), contentAlignment = Alignment.Center) {
        glyph?.let { Icon(it, contentDescription = null, modifier = Modifier.size(AppGlyphSize), tint = MaterialTheme.colorScheme.primary) }
            ?: Icon(painterResource(R.drawable.ic_search_chat), null, tint = MaterialTheme.colorScheme.primary)
        if (type != ContactActionType.MESSAGE) {
            Icon(
                painterResource(if (type == ContactActionType.VIDEO_CALL) R.drawable.ic_search_video else R.drawable.ic_search_call),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                // Inset so the mark stays inside the round button.
                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).size(14.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface),
            )
        }
    }
}

private val PersonAvatarSize: Dp = 52.dp
/**
 * A monochrome layer is a 108dp canvas whose glyph sits in the central ~48dp, so filling the 44dp
 * button leaves a glyph about the size of the 24dp Call and Message glyphs.
 */
private val AppGlyphSize: Dp = 44.dp
