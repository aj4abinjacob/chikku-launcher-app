package io.github.aj4abinjacob.chikku.core.model.search

import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.LauncherApp
import io.github.aj4abinjacob.chikku.core.model.ProfileType
import io.github.aj4abinjacob.chikku.core.model.search.CommunicationProviders.SIGNAL
import io.github.aj4abinjacob.chikku.core.model.search.CommunicationProviders.WHATSAPP
import io.github.aj4abinjacob.chikku.core.model.search.ContactActionType.MESSAGE
import io.github.aj4abinjacob.chikku.core.model.search.ContactActionType.VIDEO_CALL
import io.github.aj4abinjacob.chikku.core.model.search.ContactActionType.VOICE_CALL
import io.github.aj4abinjacob.chikku.core.model.settings.SearchSettings
import org.junit.Test

class ContactServicesTest {

    /** A future provider that only lets other apps open a chat. */
    private val future = CommunicationProvider(
        "future", "org.example.chat", "Example Chat",
        listOf(ProviderEntryPoint("vnd.android.cursor.item/vnd.org.example.chat", MESSAGE)),
    )
    private val providers = listOf(WHATSAPP, SIGNAL, future)

    private fun app(pkg: String, profile: ProfileType = ProfileType.PERSONAL, serial: Long = 0, cls: String = "$pkg.Main") =
        LauncherApp(AppKey(pkg, cls, serial), pkg.substringAfterLast('.'), profile)

    private val whatsApp = app(WHATSAPP.packageName)
    private val signal = app(SIGNAL.packageName)
    private val futureApp = app(future.packageName)

    private fun action(provider: CommunicationProvider, type: ContactActionType, id: Long) =
        ContactChannelAction(provider.id, type, id, provider.entryPoints.first { it.type == type }.mimeType)

    private fun button(provider: CommunicationProvider, type: ContactActionType = MESSAGE) = ContactCardButton(provider.id, type)

    private val ann = ContactHit(1, "k", "Ann", "+1 555", channelActions = listOf(action(WHATSAPP, MESSAGE, 1), action(SIGNAL, MESSAGE, 2)))

    @Test
    fun defaultCardIsCallMessageWhatsAppAndMatchesTheSettingsDefault() {
        assertThat(ContactCardButton.DEFAULT_IDS).containsExactly("phone:voice_call", "sms:message", "whatsapp:message").inOrder()
        assertThat(SearchSettings().contactCardButtons).isEqualTo(ContactCardButton.DEFAULT_IDS)
        assertThat(ContactServices.buttons(SearchSettings())).isEqualTo(ContactCardButton.DEFAULT)
    }

    @Test
    fun defaultCardResolvesToThreeButtonsInOrder() {
        val services = ContactServices.available(listOf(whatsApp, signal), emptySet(), providers)
        val resolved = ContactServices.resolve(ContactCardButton.DEFAULT, ann, services)
        assertThat(resolved.map { it::class.simpleName }).containsExactly("Call", "Text", "Service").inOrder()
        assertThat((resolved[2] as ResolvedCardButton.Service).action).isEqualTo(action(WHATSAPP, MESSAGE, 1))
    }

    @Test
    fun buttonsCanBeSwappedAndReordered() {
        val services = ContactServices.available(listOf(whatsApp, signal), emptySet(), providers)
        val custom = listOf(button(WHATSAPP), button(SIGNAL), ContactCardButton.CALL)
        val resolved = ContactServices.resolve(custom, ann, services)
        assertThat(resolved.map { (it as? ResolvedCardButton.Service)?.service?.provider?.id ?: "call" })
            .containsExactly("whatsapp", "signal", "call").inOrder()
    }

    @Test
    fun buttonsTheContactCannotUseAreLeftOutWithoutEmptySlots() {
        val services = ContactServices.available(listOf(whatsApp), emptySet(), providers)
        val noNumberNoApps = ContactHit(2, "k2", "Bo")
        assertThat(ContactServices.resolve(ContactCardButton.DEFAULT, noNumberNoApps, services)).isEmpty()
        // Phone-only contact: Call and Message, no WhatsApp.
        val phoneOnly = ContactHit(3, "k3", "Cy", "+1 555")
        assertThat(ContactServices.resolve(ContactCardButton.DEFAULT, phoneOnly, services))
            .containsExactly(ResolvedCardButton.Call, ResolvedCardButton.Text).inOrder()
        // Signal chosen but uninstalled: left out; WhatsApp video chosen but not supported/synced: left out.
        val custom = listOf(button(SIGNAL), button(WHATSAPP, VIDEO_CALL), button(WHATSAPP))
        assertThat(ContactServices.resolve(custom, ann, services).single()).isInstanceOf(ResolvedCardButton.Service::class.java)
    }

    @Test
    fun anAdditionalProviderWorksThroughTheSameButtons() {
        val services = ContactServices.available(listOf(futureApp), emptySet(), providers)
        val hit = ann.copy(channelActions = listOf(action(future, MESSAGE, 9)))
        val resolved = ContactServices.resolve(listOf(button(future), button(future, VOICE_CALL)), hit, services)
        assertThat(resolved).containsExactly(ResolvedCardButton.Service(services.single(), action(future, MESSAGE, 9)))
    }

    @Test
    fun onlyInstalledVisiblePersonalProvidersAreAvailable() {
        val apps = listOf(signal, whatsApp, app(future.packageName, ProfileType.WORK, serial = 10))
        assertThat(ContactServices.available(apps, emptySet(), providers).map { it.provider.id }).containsExactly("whatsapp", "signal").inOrder()
        assertThat(ContactServices.available(apps, setOf(signal.key), providers).map { it.provider.id }).containsExactly("whatsapp")
    }

    @Test
    fun aProviderWithSeveralLauncherActivitiesUsesAStableOne() {
        val b = app(WHATSAPP.packageName, cls = "com.whatsapp.B")
        val a = app(WHATSAPP.packageName, cls = "com.whatsapp.A")
        assertThat(ContactServices.available(listOf(b, a), emptySet(), providers).single().app).isEqualTo(a)
    }

    @Test
    fun removedProvidersAndForgedRowsAreFiltered() {
        val services = ContactServices.available(listOf(whatsApp), emptySet(), providers)
        val actions = listOf(
            action(SIGNAL, MESSAGE, 4),
            ContactChannelAction("whatsapp", VIDEO_CALL, 3, WHATSAPP.entryPoints.first { it.type == MESSAGE }.mimeType),
            ContactChannelAction("whatsapp", MESSAGE, 7, "vnd.android.cursor.item/other"),
            action(WHATSAPP, MESSAGE, 1), action(WHATSAPP, MESSAGE, 2),
        )
        assertThat(ContactServices.visibleActions(actions, services)).containsExactly(action(WHATSAPP, MESSAGE, 1))
        val filtered = ContactServices.filter(ContactLookup.Found(listOf(ann)), services) as ContactLookup.Found
        assertThat(filtered.contacts.single().channelActions).containsExactly(action(WHATSAPP, MESSAGE, 1))
        assertThat(ContactServices.filter(ContactLookup.PermissionMissing, services)).isEqualTo(ContactLookup.PermissionMissing)
    }

    @Test
    fun storedIdsParseStrictlyAndKeepUnknownProviders() {
        assertThat(ContactCardButton.parse("phone:voice_call")).isEqualTo(ContactCardButton.CALL)
        assertThat(ContactCardButton.parse("phone:message")).isNull()
        assertThat(ContactCardButton.parse("sms:video_call")).isNull()
        assertThat(ContactCardButton.parse("newer_app:video_call")).isEqualTo(ContactCardButton("newer_app", VIDEO_CALL))
        assertThat(ContactCardButton.parse("whatsapp")).isNull()
        assertThat(ContactCardButton.parse(":message")).isNull()
        val settings = SearchSettings(contactCardButtons = listOf("signal:message", "bad", "signal:message", "phone:voice_call"))
        assertThat(ContactServices.buttons(settings)).containsExactly(button(SIGNAL), ContactCardButton.CALL).inOrder()
    }

    @Test
    fun builtInProvidersHaveStableDistinctIdsAndUnambiguousEntryPoints() {
        val known = CommunicationProviders.KNOWN
        assertThat(known.map { it.id }).containsExactly("whatsapp", "signal", "telegram", "threema").inOrder()
        assertThat(known.flatMap { p -> p.entryPoints.map { it.mimeType } }).containsNoDuplicates()
        assertThat(known.map { it.id }).containsNoneOf(ContactCardButton.PHONE, ContactCardButton.SMS)
    }
}
