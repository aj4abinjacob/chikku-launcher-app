package io.github.aj4abinjacob.chikku.platform

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.ContactsContract
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.search.CommunicationProviders.SIGNAL
import io.github.aj4abinjacob.chikku.core.model.search.CommunicationProviders.TELEGRAM
import io.github.aj4abinjacob.chikku.core.model.search.CommunicationProviders.THREEMA
import io.github.aj4abinjacob.chikku.core.model.search.CommunicationProviders.WHATSAPP
import io.github.aj4abinjacob.chikku.core.model.search.ContactActionType
import io.github.aj4abinjacob.chikku.core.model.search.ContactChannelAction
import io.github.aj4abinjacob.chikku.core.model.search.ContactHit
import io.github.aj4abinjacob.chikku.core.model.search.ContactLookup
import io.github.aj4abinjacob.chikku.home.TestHomeApplication
import io.github.aj4abinjacob.chikku.search.ContactsSearchSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Capability discovery and validated launching of communication-service actions. Package manager,
 * contacts provider and app lock are Robolectric fakes; no real app is started, nothing is sent.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestHomeApplication::class)
class ContactActionPlatformTest {

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val message = WHATSAPP.entryPoints.first { it.type == ContactActionType.MESSAGE }
    private val voice = WHATSAPP.entryPoints.first { it.type == ContactActionType.VOICE_CALL }
    private val video = WHATSAPP.entryPoints.first { it.type == ContactActionType.VIDEO_CALL }
    private val contact = ContactHit(1, "lookup-1", "Ann")
    private val chat = ContactChannelAction("whatsapp", ContactActionType.MESSAGE, 11, message.mimeType)

    /** Data rows by id: contact id, mimetype, lookup key. Records every URI it was asked for. */
    class DataProvider : ContentProvider() {
        override fun onCreate() = true
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor {
            asked += uri
            val cursor = MatrixCursor(arrayOf(ContactsContract.Data.CONTACT_ID, ContactsContract.Data.MIMETYPE, ContactsContract.Data.LOOKUP_KEY))
            rows[uri.lastPathSegment?.toLongOrNull()]?.let { cursor.addRow(arrayOf<Any?>(it.first, it.second, it.third)) }
            return cursor
        }
        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0

        companion object {
            val rows = HashMap<Long, Triple<Long, String, String>>()
            val asked = ArrayList<Uri>()
        }
    }

    @Before fun setUp() {
        DataProvider.rows.clear()
        DataProvider.asked.clear()
        DataProvider.rows[11] = Triple(1L, message.mimeType, "lookup-1")
        Robolectric.setupContentProvider(DataProvider::class.java, ContactsContract.AUTHORITY)
        shadowOf(application).grantPermissions(Manifest.permission.READ_CONTACTS)
    }

    /** Installs [pkg] with one activity handling [mimeTypes], guarded by [permission] like the real apps' call entries. */
    private fun install(pkg: String, activity: String, mimeTypes: List<String>, permission: String? = null, exported: Boolean = true) {
        val pm = shadowOf(application.packageManager)
        val app = ApplicationInfo().apply { packageName = pkg; enabled = true }
        // Installing again would replace the activities added for the package so far.
        if (runCatching { application.packageManager.getPackageInfo(pkg, 0) }.isFailure) {
            pm.installPackage(PackageInfo().apply { packageName = pkg; applicationInfo = app })
        }
        val component = ComponentName(pkg, activity)
        pm.addOrUpdateActivity(ActivityInfo().apply {
            packageName = pkg; name = activity; applicationInfo = app; enabled = true
            this.exported = exported; this.permission = permission
        })
        pm.addIntentFilterForActivity(component, IntentFilter(Intent.ACTION_VIEW).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            mimeTypes.forEach(::addDataType)
        })
    }

    /** The shape verified on WhatsApp 2.26.39.79: chats open, calls require CALL_PHONE. */
    private fun installWhatsAppAsShipped() {
        install(WHATSAPP.packageName, "com.whatsapp.accountsync.ProfileActivity", listOf(message.mimeType))
        install(WHATSAPP.packageName, "com.whatsapp.accountsync.CallContactLandingActivity",
            listOf(voice.mimeType, video.mimeType), permission = Manifest.permission.CALL_PHONE)
    }

    @Test
    fun callEntriesGuardedByCallPhoneAreNotOfferedButChatsAre() {
        installWhatsAppAsShipped()
        val probe = AndroidContactEntryPointProbe(application)
        assertThat(probe.supports(WHATSAPP, message)).isTrue()
        assertThat(probe.supports(WHATSAPP, voice)).isFalse()
        assertThat(probe.supports(WHATSAPP, video)).isFalse()
    }

    /** The shape verified on Telegram 13.0.2: chats open in LaunchActivity, calls require CALL_PHONE. */
    @Test
    fun telegramAsShippedOffersChatsOnly() {
        val (chat, voiceCall, videoCall) = TELEGRAM.entryPoints
        install(TELEGRAM.packageName, "org.telegram.ui.LaunchActivity", listOf(chat.mimeType))
        install(TELEGRAM.packageName, "org.telegram.ui.CallsActivity",
            listOf(voiceCall.mimeType, videoCall.mimeType), permission = Manifest.permission.CALL_PHONE)
        val probe = AndroidContactEntryPointProbe(application)
        assertThat(probe.supports(TELEGRAM, chat)).isTrue()
        assertThat(probe.supports(TELEGRAM, voiceCall)).isFalse()
        assertThat(probe.supports(TELEGRAM, videoCall)).isFalse()
    }

    /** The shape in Threema's source (d44a2b5): neither entry is permission-guarded, so both are offered. */
    @Test
    fun threemaAsShippedOffersChatsAndVoiceCalls() {
        val (chat, voiceCall) = THREEMA.entryPoints
        install(THREEMA.packageName, "ch.threema.app.activities.ComposeMessageActivity", listOf(chat.mimeType))
        install(THREEMA.packageName, "ch.threema.app.voip.activities.CallActionIntentActivity", listOf(voiceCall.mimeType))
        val probe = AndroidContactEntryPointProbe(application)
        assertThat(probe.supports(THREEMA, chat)).isTrue()
        assertThat(probe.supports(THREEMA, voiceCall)).isTrue()
    }

    @Test
    fun unguardedCallEntriesWouldBeOfferedThroughTheSameModel() {
        install(WHATSAPP.packageName, "com.whatsapp.Calls", listOf(voice.mimeType, video.mimeType))
        val probe = AndroidContactEntryPointProbe(application)
        assertThat(probe.supports(WHATSAPP, voice)).isTrue()
        assertThat(probe.supports(WHATSAPP, video)).isTrue()
    }

    @Test
    fun missingUnexportedOrForeignHandlersAreNotSupported() {
        val probe = AndroidContactEntryPointProbe(application)
        assertThat(probe.supports(SIGNAL, SIGNAL.entryPoints.first())).isFalse() // not installed
        install(SIGNAL.packageName, "org.thoughtcrime.securesms.Hidden", listOf(SIGNAL.entryPoints.first().mimeType), exported = false)
        assertThat(probe.supports(SIGNAL, SIGNAL.entryPoints.first())).isFalse()
        // Another package declaring WhatsApp's mimetype is never used: intents are pinned to the provider package.
        install("org.example.impostor", "org.example.impostor.Main", listOf(message.mimeType))
        assertThat(probe.supports(WHATSAPP, message)).isFalse()
    }

    @Test
    fun searchSourceReadsOnlySupportedProviderRows() = runTest {
        installWhatsAppAsShipped()
        val probe = AndroidContactEntryPointProbe(application)
        // The real query path: probe answers are what limits the mimetypes read from the provider.
        val supported = WHATSAPP.entryPoints.filter { probe.supports(WHATSAPP, it) }.map { it.type }
        assertThat(supported).containsExactly(ContactActionType.MESSAGE)
        // Without READ_CONTACTS nothing is read at all.
        shadowOf(application).denyPermissions(Manifest.permission.READ_CONTACTS)
        assertThat(ContactsSearchSource(application, Dispatchers.Unconfined, probe = probe).contacts("ann", 5))
            .isEqualTo(ContactLookup.PermissionMissing)
    }

    @Test
    fun aValidatedChatOpensThePinnedProviderRowAfterTheTap() = runTest {
        installWhatsAppAsShipped()
        val launcher = ContactActionLauncher(application, AppLockGuard.NONE, Dispatchers.Unconfined)
        val prepared = launcher.prepare(contact, chat) as ContactActionLauncher.Prepared.Ready
        assertThat(launcher.start(prepared)).isEqualTo(ContactActionResult.Started)
        val started = shadowOf(application).nextStartedActivity
        assertThat(started.action).isEqualTo(Intent.ACTION_VIEW)
        assertThat(started.`package`).isEqualTo(WHATSAPP.packageName)
        assertThat(started.type).isEqualTo(message.mimeType)
        assertThat(started.data).isEqualTo(Uri.withAppendedPath(ContactsContract.Data.CONTENT_URI, "11"))
        assertThat(started.flags and Intent.FLAG_ACTIVITY_NEW_TASK).isNotEqualTo(0)
    }

    @Test
    fun joinedContactsKeepWorkingThroughTheirLookupKey() = runTest {
        installWhatsAppAsShipped()
        DataProvider.rows[11] = Triple(99L, message.mimeType, "lookup-1") // contact id changed after a join
        val prepared = ContactActionLauncher(application, AppLockGuard.NONE, Dispatchers.Unconfined).prepare(contact, chat)
        assertThat(prepared).isInstanceOf(ContactActionLauncher.Prepared.Ready::class.java)
    }

    @Test
    fun staleDeletedOrReassignedRowsReportAChangedContactAndStartNothing() = runTest {
        installWhatsAppAsShipped()
        val launcher = ContactActionLauncher(application, AppLockGuard.NONE, Dispatchers.Unconfined)
        DataProvider.rows.remove(11)
        assertThat(launcher.prepare(contact, chat)).isEqualTo(ContactActionLauncher.Prepared.Failed(ContactActionResult.ContactChanged))
        DataProvider.rows[11] = Triple(2L, message.mimeType, "someone-else")
        assertThat(launcher.prepare(contact, chat)).isEqualTo(ContactActionLauncher.Prepared.Failed(ContactActionResult.ContactChanged))
        DataProvider.rows[11] = Triple(1L, "vnd.android.cursor.item/other", "lookup-1")
        assertThat(launcher.prepare(contact, chat)).isEqualTo(ContactActionLauncher.Prepared.Failed(ContactActionResult.ContactChanged))
        assertThat(shadowOf(application).nextStartedActivity).isNull()
    }

    @Test
    fun revokedContactsPermissionIsReportedWithoutQuerying() = runTest {
        installWhatsAppAsShipped()
        shadowOf(application).denyPermissions(Manifest.permission.READ_CONTACTS)
        val prepared = ContactActionLauncher(application, AppLockGuard.NONE, Dispatchers.Unconfined).prepare(contact, chat)
        assertThat(prepared).isEqualTo(ContactActionLauncher.Prepared.Failed(ContactActionResult.PermissionMissing))
        assertThat(DataProvider.asked).isEmpty()
    }

    @Test
    fun removedAppsAndActionsThatLostSupportAreUnavailable() = runTest {
        val launcher = ContactActionLauncher(application, AppLockGuard.NONE, Dispatchers.Unconfined)
        assertThat(launcher.prepare(contact, chat)).isEqualTo(ContactActionLauncher.Prepared.Failed(ContactActionResult.Unavailable))
        installWhatsAppAsShipped()
        DataProvider.rows[12] = Triple(1L, voice.mimeType, "lookup-1")
        val call = ContactChannelAction("whatsapp", ContactActionType.VOICE_CALL, 12, voice.mimeType)
        assertThat(launcher.prepare(contact, call)).isEqualTo(ContactActionLauncher.Prepared.Failed(ContactActionResult.Unavailable))
        val unknown = chat.copy(providerId = "uninstalled-future-app")
        assertThat(launcher.prepare(contact, unknown)).isEqualTo(ContactActionLauncher.Prepared.Failed(ContactActionResult.Unavailable))
    }

    @Test
    fun appLockIsAskedLastAndALockedAppStartsNothing() = runTest {
        installWhatsAppAsShipped()
        val asked = ArrayList<String>()
        val launcher = ContactActionLauncher(application, { pkg, _ -> asked += pkg; false }, Dispatchers.Unconfined)
        val prepared = launcher.prepare(contact, chat) as ContactActionLauncher.Prepared.Ready
        assertThat(asked).isEmpty() // validation never consumes a one-time unlock
        val result = launcher.start(prepared)
        assertThat(result).isInstanceOf(ContactActionResult.Locked::class.java)
        assertThat((result as ContactActionResult.Locked).launch.packageName).isEqualTo(WHATSAPP.packageName)
        assertThat(shadowOf(application).nextStartedActivity).isNull()
    }

    @Test
    fun aRejectedLaunchIsReportedNotThrown() {
        shadowOf(application).checkActivities(true) // nothing handles the intent any more
        val launcher = ContactActionLauncher(application, AppLockGuard.NONE, Dispatchers.Unconfined)
        val ready = ContactActionLauncher.Prepared.Ready(ContactActionIntents.view(WHATSAPP.packageName, message.mimeType, 11), WHATSAPP.packageName)
        assertThat(launcher.start(ready)).isEqualTo(ContactActionResult.Unavailable)
    }
}
