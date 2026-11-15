package io.github.aj4abinjacob.chikku.core.model.search

import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.LauncherApp
import io.github.aj4abinjacob.chikku.core.model.ProfileType
import io.github.aj4abinjacob.chikku.core.model.settings.SearchSettings

/** What a contact action does. The UI label states exactly this and nothing more. */
enum class ContactActionType { MESSAGE, VOICE_CALL, VIDEO_CALL }

/**
 * A documented way an app lets other apps start [type] for one contact: Android's Contacts app
 * pattern, `ACTION_VIEW` on a `ContactsContract.Data` row of [mimeType] that the app's own sync
 * adapter wrote. A listed entry point is a candidate only; the platform layer offers it after
 * checking that the installed app still handles it without a permission Chikku does not hold.
 */
data class ProviderEntryPoint(val mimeType: String, val type: ContactActionType)

/**
 * A communication app that syncs per-contact rows into the contacts provider. [id] is stable and
 * persisted in [SearchSettings]; never rename one. [displayName] is the brand name used only where
 * the app is not installed (Settings); results show the installed app's own label and icon.
 */
data class CommunicationProvider(
    val id: String,
    val packageName: String,
    val displayName: String,
    val entryPoints: List<ProviderEntryPoint>,
)

/**
 * Providers whose entry points were checked against the shipping app (see docs/CONTACT_SERVICES.md
 * for revisions and evidence). Adding a provider is a data change here plus a manifest `<queries>`
 * entry only if its package would otherwise be invisible; the UI, settings and backup need nothing new.
 */
object CommunicationProviders {
    private const val ITEM = "vnd.android.cursor.item/"

    val WHATSAPP = CommunicationProvider(
        id = "whatsapp",
        packageName = "com.whatsapp",
        displayName = "WhatsApp",
        entryPoints = listOf(
            ProviderEntryPoint("${ITEM}vnd.com.whatsapp.profile", ContactActionType.MESSAGE),
            ProviderEntryPoint("${ITEM}vnd.com.whatsapp.voip.call", ContactActionType.VOICE_CALL),
            ProviderEntryPoint("${ITEM}vnd.com.whatsapp.video.call", ContactActionType.VIDEO_CALL),
        ),
    )

    val SIGNAL = CommunicationProvider(
        id = "signal",
        packageName = "org.thoughtcrime.securesms",
        displayName = "Signal",
        entryPoints = listOf(
            ProviderEntryPoint("${ITEM}vnd.org.thoughtcrime.securesms.contact", ContactActionType.MESSAGE),
            ProviderEntryPoint("${ITEM}vnd.org.thoughtcrime.securesms.call", ContactActionType.VOICE_CALL),
            ProviderEntryPoint("${ITEM}vnd.org.thoughtcrime.securesms.videocall", ContactActionType.VIDEO_CALL),
        ),
    )

    val TELEGRAM = CommunicationProvider(
        id = "telegram",
        packageName = "org.telegram.messenger",
        displayName = "Telegram",
        entryPoints = listOf(
            ProviderEntryPoint("${ITEM}vnd.org.telegram.messenger.android.profile", ContactActionType.MESSAGE),
            ProviderEntryPoint("${ITEM}vnd.org.telegram.messenger.android.call", ContactActionType.VOICE_CALL),
            ProviderEntryPoint("${ITEM}vnd.org.telegram.messenger.android.call.video", ContactActionType.VIDEO_CALL),
        ),
    )

    /** The Google Play / Threema Shop build. Libre and HMS builds reuse these mimetypes under other packages. */
    val THREEMA = CommunicationProvider(
        id = "threema",
        packageName = "ch.threema.app",
        displayName = "Threema",
        entryPoints = listOf(
            ProviderEntryPoint("${ITEM}vnd.ch.threema.app.profile", ContactActionType.MESSAGE),
            ProviderEntryPoint("${ITEM}vnd.ch.threema.app.call", ContactActionType.VOICE_CALL),
        ),
    )

    /** Built-in order, used for providers the user has not ordered. */
    val KNOWN: List<CommunicationProvider> = listOf(WHATSAPP, SIGNAL, TELEGRAM, THREEMA)
}

/**
 * One synced contact data row offering [type] through provider [providerId]. [dataId] is the
 * `ContactsContract.Data._ID`; it is re-validated against the contact before every launch.
 */
data class ContactChannelAction(
    val providerId: String,
    val type: ContactActionType,
    val dataId: Long,
    val mimeType: String,
)

/** A provider that may appear on contact cards right now: installed in the personal profile and not hidden. */
data class ContactService(val provider: CommunicationProvider, val app: LauncherApp)

/**
 * One configurable button on the person card. [providerId] is [PHONE], [SMS] or a
 * [CommunicationProvider.id]; [id] (`"whatsapp:message"`) is what settings store.
 */
data class ContactCardButton(val providerId: String, val type: ContactActionType) {
    val id: String get() = "$providerId:${type.name.lowercase()}"

    companion object {
        const val PHONE = "phone"
        const val SMS = "sms"
        val CALL = ContactCardButton(PHONE, ContactActionType.VOICE_CALL)
        val TEXT = ContactCardButton(SMS, ContactActionType.MESSAGE)

        /** Most buttons that fit the card's single row. */
        const val MAX = 4

        /** Call, Message (SMS), WhatsApp. */
        val DEFAULT: List<ContactCardButton> = listOf(CALL, TEXT, ContactCardButton(CommunicationProviders.WHATSAPP.id, ContactActionType.MESSAGE))
        val DEFAULT_IDS: List<String> = DEFAULT.map { it.id }

        /** Parses a stored id; null for anything malformed. Unknown provider ids are kept (a newer build may know them). */
        fun parse(id: String): ContactCardButton? {
            val provider = id.substringBefore(':', "").takeIf { it.isNotBlank() && it.length <= 48 } ?: return null
            val type = ContactActionType.entries.firstOrNull { it.name.lowercase() == id.substringAfter(':') } ?: return null
            return when (provider) {
                PHONE -> CALL.takeIf { type == it.type }
                SMS -> TEXT.takeIf { type == it.type }
                else -> ContactCardButton(provider, type)
            }
        }
    }
}

/** A person-card button resolved for one contact. */
sealed interface ResolvedCardButton {
    data object Call : ResolvedCardButton
    data object Text : ResolvedCardButton
    data class Service(val service: ContactService, val action: ContactChannelAction) : ResolvedCardButton
}

/**
 * Pure provider selection and person-card resolution. Contacts are personal-profile data, so only
 * personal-profile apps are providers; a provider app hidden from the launcher never appears.
 */
object ContactServices {

    /**
     * Providers usable now: a visible launcher activity in the personal profile ([apps] minus
     * [hidden]). The lowest [AppKey] is chosen for a stable icon and label.
     */
    fun available(
        apps: List<LauncherApp>,
        hidden: Set<AppKey>,
        providers: List<CommunicationProvider> = CommunicationProviders.KNOWN,
    ): List<ContactService> {
        val personal = apps.filter { it.profileType == ProfileType.PERSONAL && it.key !in hidden }
            .groupBy { it.key.packageName }
        return providers.mapNotNull { provider ->
            personal[provider.packageName]?.minWithOrNull(compareBy({ it.key.className }, { it.key.userSerial }))
                ?.let { ContactService(provider, it) }
        }
    }

    /** The user's card buttons from settings, parsed; malformed or duplicate ids are skipped. */
    fun buttons(settings: SearchSettings): List<ContactCardButton> =
        settings.contactCardButtons.mapNotNull(ContactCardButton::parse).distinct().take(ContactCardButton.MAX)

    /**
     * The card's buttons for [hit], in the user's order. A button the contact cannot use (no number,
     * not on that app, app missing or the action unsupported) is left out, never shown disabled.
     */
    fun resolve(
        buttons: List<ContactCardButton>,
        hit: ContactHit,
        services: List<ContactService>,
    ): List<ResolvedCardButton> {
        val actions = visibleActions(hit.channelActions, services)
        return buttons.mapNotNull { button ->
            when (button) {
                ContactCardButton.CALL -> ResolvedCardButton.Call.takeIf { hit.phoneNumber != null }
                ContactCardButton.TEXT -> ResolvedCardButton.Text.takeIf { hit.phoneNumber != null }
                else -> {
                    val service = services.firstOrNull { it.provider.id == button.providerId } ?: return@mapNotNull null
                    actions.firstOrNull { it.providerId == button.providerId && it.type == button.type }
                        ?.let { ResolvedCardButton.Service(service, it) }
                }
            }
        }
    }

    /** [actions] reduced to those of [services] (and their declared entry points), one per type. */
    fun visibleActions(actions: List<ContactChannelAction>, services: List<ContactService>): List<ContactChannelAction> {
        val allowed = services.associate { service ->
            service.provider.id to service.provider.entryPoints.associate { it.mimeType to it.type }
        }
        return actions.filter { allowed[it.providerId]?.get(it.mimeType) == it.type }
            .distinctBy { it.providerId to it.type }
    }

    /** Applies [visibleActions] to every hit so no hidden or removed service reaches the UI. */
    fun filter(lookup: ContactLookup?, services: List<ContactService>): ContactLookup? =
        (lookup as? ContactLookup.Found)?.let { found ->
            ContactLookup.Found(found.contacts.map { it.copy(channelActions = visibleActions(it.channelActions, services)) })
        } ?: lookup
}
