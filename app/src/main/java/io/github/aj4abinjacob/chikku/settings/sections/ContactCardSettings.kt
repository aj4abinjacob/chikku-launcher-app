package io.github.aj4abinjacob.chikku.settings.sections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import io.github.aj4abinjacob.chikku.home.Haptics
import io.github.aj4abinjacob.chikku.settings.components.animatePlacement
import io.github.aj4abinjacob.chikku.settings.components.appearing
import io.github.aj4abinjacob.chikku.theme.LocalMotion
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.AppsState
import io.github.aj4abinjacob.chikku.core.model.ProfileType
import io.github.aj4abinjacob.chikku.core.model.search.CommunicationProviders
import io.github.aj4abinjacob.chikku.core.model.search.ContactActionType
import io.github.aj4abinjacob.chikku.core.model.search.ContactCardButton
import io.github.aj4abinjacob.chikku.core.model.search.ContactServices
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.platform.AndroidContactEntryPointProbe
import io.github.aj4abinjacob.chikku.settings.LocalHomeDependencies
import io.github.aj4abinjacob.chikku.settings.components.SettingsInfoText
import io.github.aj4abinjacob.chikku.settings.components.SettingsSectionHeader
import io.github.aj4abinjacob.chikku.settings.components.settingsSegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Which buttons the search contact card shows, in order (default Call, Message, WhatsApp). New
 * buttons can be added only from actions this phone supports; a chosen button whose app is missing
 * (for example after a restore) stays in the list and simply isn't shown on cards.
 */
@Composable
internal fun ContactCardSettings(settings: LauncherSettings, update: (((LauncherSettings) -> LauncherSettings) -> Unit)) {
    val context = LocalContext.current
    val deps = LocalHomeDependencies.current
    // Personal-profile launcher label per package, for names and the "not installed" state.
    val installedFlow = remember(deps) {
        deps.appsRepository.apps.map { state ->
            (state as? AppsState.Loaded)?.apps.orEmpty().filter { it.profileType == ProfileType.PERSONAL }
                .groupBy { it.key.packageName }.mapValues { (_, apps) -> apps.minBy { it.key.className }.label }
        }
    }
    val installed by installedFlow.collectAsStateWithLifecycle(emptyMap())
    // Actions each installed app lets Chikku open (PackageManager, off the main thread).
    val supported by produceState(emptySet<ContactCardButton>(), installed.keys) {
        value = withContext(Dispatchers.IO) {
            val probe = AndroidContactEntryPointProbe(context)
            CommunicationProviders.KNOWN.filter { it.packageName in installed }.flatMap { p ->
                p.entryPoints.filter { probe.supports(p, it) }.map { ContactCardButton(p.id, it.type) }
            }.toSet()
        }
    }
    val chosen = ContactServices.buttons(settings.search)
    fun save(buttons: List<ContactCardButton>) = update { s -> s.copy(search = s.search.copy(contactCardButtons = buttons.map { it.id })) }
    fun name(button: ContactCardButton): String {
        val provider = CommunicationProviders.KNOWN.firstOrNull { it.id == button.providerId }
        return provider?.let { installed[it.packageName] ?: it.displayName } ?: button.providerId
    }

    SettingsSectionHeader(stringResource(R.string.settings_contact_card_header))
    val motion = LocalMotion.current
    val view = LocalView.current
    // Rows added after the page first showed fade in; the first composition does not animate.
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { ready = true }
    chosen.forEachIndexed { index, button ->
      key(button.id) {
        val provider = CommunicationProviders.KNOWN.firstOrNull { it.id == button.providerId }
        val status = when {
            button == ContactCardButton.CALL || button == ContactCardButton.TEXT -> null
            provider == null || provider.packageName !in installed -> stringResource(R.string.settings_contact_card_not_installed)
            button !in supported -> stringResource(R.string.settings_contact_card_unsupported)
            else -> null
        }
        ButtonRow(
            modifier = Modifier.animatePlacement(motion).appearing(motion, ready),
            title = label(button, name(button)),
            summary = status,
            index = index,
            count = chosen.size,
            onMove = { delta ->
                val to = index + delta
                if (to in chosen.indices) {
                    Haptics.tick(view)
                    save(chosen.toMutableList().apply { add(to, removeAt(index)) })
                }
            },
            // At least one button stays, so the card never loses all actions by accident.
            onRemove = if (chosen.size > 1) ({ save(chosen - button) }) else null,
        )
      }
    }
    val addable = (listOf(ContactCardButton.CALL, ContactCardButton.TEXT) + supported.sortedWith(
        compareBy({ b -> CommunicationProviders.KNOWN.indexOfFirst { it.id == b.providerId } }, { it.type.ordinal }),
    )).filterNot { it in chosen }
    if (chosen.size < ContactCardButton.MAX && addable.isNotEmpty()) {
        SettingsSectionHeader(stringResource(R.string.settings_contact_card_add_header))
        addable.forEachIndexed { index, button ->
          key(button.id) {
            val title = label(button, name(button))
            Row(
                Modifier.animatePlacement(motion).appearing(motion, ready).settingsSegment(index, addable.size).fillMaxWidth().heightIn(min = 56.dp)
                    .clickable(onClickLabel = stringResource(R.string.settings_contact_card_add, title)) { save(chosen + button) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_action_add), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp))
            }
          }
        }
    }
    if (chosen.map { it.id } != ContactCardButton.DEFAULT_IDS) {
        Row(
            Modifier.appearing(motion, ready).settingsSegment(0, 1).fillMaxWidth().heightIn(min = 56.dp)
                .clickable { save(ContactCardButton.DEFAULT) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.settings_contact_card_reset), style = MaterialTheme.typography.bodyLarge)
        }
    }
    SettingsInfoText(stringResource(R.string.settings_contact_card_info))
}

@Composable
private fun label(button: ContactCardButton, name: String): String = when {
    button == ContactCardButton.CALL -> stringResource(R.string.search_person_call)
    button == ContactCardButton.TEXT -> stringResource(R.string.settings_contact_card_sms)
    button.type == ContactActionType.MESSAGE -> stringResource(R.string.settings_contact_card_app_message, name)
    button.type == ContactActionType.VOICE_CALL -> stringResource(R.string.settings_contact_card_app_voice, name)
    else -> stringResource(R.string.settings_contact_card_app_video, name)
}

@Composable
private fun ButtonRow(
    modifier: Modifier,
    title: String,
    summary: String?,
    index: Int,
    count: Int,
    onMove: (Int) -> Unit,
    onRemove: (() -> Unit)?,
) {
    Row(
        modifier.settingsSegment(index, count).fillMaxWidth().heightIn(min = 56.dp).padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            summary?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        // Disabled at the ends rather than removed, so the buttons do not jump under the finger.
        IconButton(onClick = { onMove(-1) }, enabled = index > 0) {
            Icon(painterResource(R.drawable.ic_swipe_up), contentDescription = stringResource(R.string.settings_contact_card_move_up, title))
        }
        IconButton(onClick = { onMove(1) }, enabled = index < count - 1) {
            Icon(painterResource(R.drawable.ic_swipe_down), contentDescription = stringResource(R.string.settings_contact_card_move_down, title))
        }
        IconButton(onClick = { onRemove?.invoke() }, enabled = onRemove != null) {
            Icon(painterResource(R.drawable.ic_menu_remove), contentDescription = stringResource(R.string.settings_contact_card_remove, title))
        }
    }
}
