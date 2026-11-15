package io.github.aj4abinjacob.chikku.search

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.CancellationSignal
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import android.provider.ContactsContract
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.search.ContactActionType
import io.github.aj4abinjacob.chikku.core.model.search.ContactChannelAction
import io.github.aj4abinjacob.chikku.core.model.search.ContactHit
import io.github.aj4abinjacob.chikku.platform.ContactEntryPointProbe
import io.github.aj4abinjacob.chikku.core.model.search.ContactLookup
import io.github.aj4abinjacob.chikku.core.model.settings.WebSearchEngine
import io.github.aj4abinjacob.chikku.home.TestHomeApplication
import io.github.aj4abinjacob.chikku.platform.AppLockGuard
import io.github.aj4abinjacob.chikku.platform.GuardedIntentLauncher
import io.github.aj4abinjacob.chikku.platform.IntentHandoffResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestHomeApplication::class)
class SearchPlatformTest {

    private val application = ApplicationProvider.getApplicationContext<Application>()

    /** Stands in for the contacts provider and records what it was asked. */
    open class FakeContactsProvider : ContentProvider() {
        override fun onCreate() = true
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor {
            if (uri.toString().startsWith(ContactsContract.Data.CONTENT_URI.toString())) {
                lastDataArgs = args?.toList()
                // Already in the requested order: primary number first.
                return MatrixCursor(arrayOf(ContactsContract.Data._ID, ContactsContract.Data.CONTACT_ID, ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.NUMBER)).apply {
                    addRow(arrayOf<Any?>(10L, 1L, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE, "+1 555 0100"))
                    addRow(arrayOf<Any?>(12L, 1L, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE, "+1 555 0199"))
                    addRow(arrayOf<Any?>(11L, 1L, "vnd.android.cursor.item/vnd.com.whatsapp.profile", "15550100@s.whatsapp.net"))
                    addRow(arrayOf<Any?>(13L, 1L, "vnd.android.cursor.item/vnd.com.whatsapp.profile", "15550199@s.whatsapp.net")) // second number: first row wins
                }
            }
            lastUri = uri
            if (uri.toString().startsWith(ContactsContract.Contacts.CONTENT_FILTER_URI.toString())) lastFilterUri = uri
            return MatrixCursor(arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.LOOKUP_KEY, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY, ContactsContract.Contacts.PHOTO_THUMBNAIL_URI)).apply {
                addRow(arrayOf<Any?>(1L, "k1", "Ann Lee", null))
                addRow(arrayOf<Any?>(2L, "k2", " ", null)) // blank names are skipped
                addRow(arrayOf<Any?>(3L, "k3", "Anna Bell", null))
                addRow(arrayOf<Any?>(4L, "k4", "Annie Hall", null))
            }
        }
        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0

        companion object {
            var lastUri: Uri? = null
            var lastFilterUri: Uri? = null
            var lastDataArgs: List<String>? = null
        }
    }

    class BlockingContactsProvider : FakeContactsProvider() {
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?, signal: CancellationSignal?): Cursor {
            signal?.setOnCancelListener { cancelled.countDown() }
            started.countDown()
            check(cancelled.await(5, TimeUnit.SECONDS)) { "Provider did not receive cancellation" }
            signal?.throwIfCanceled()
            return super.query(uri, projection, selection, args, sort)
        }
        companion object {
            var started = CountDownLatch(1)
            var cancelled = CountDownLatch(1)
        }
    }

    @Test fun queryCancellationReachesTheBlockedProviderBeforeItsCoroutineCompletes() = runTest {
        BlockingContactsProvider.started = CountDownLatch(1)
        BlockingContactsProvider.cancelled = CountDownLatch(1)
        Robolectric.setupContentProvider(BlockingContactsProvider::class.java, ContactsContract.AUTHORITY)
        shadowOf(application).grantPermissions(Manifest.permission.READ_CONTACTS)
        val query = async(Dispatchers.IO) { ContactsSearchSource(application, Dispatchers.IO).contacts("synthetic", 5) }
        try {
            assertThat(withContext(Dispatchers.IO) { BlockingContactsProvider.started.await(5, TimeUnit.SECONDS) }).isTrue()
            query.cancel()
            assertThat(withContext(Dispatchers.IO) { BlockingContactsProvider.cancelled.await(2, TimeUnit.SECONDS) }).isTrue()
            query.join()
            assertThat(query.isCancelled).isTrue()
        } finally { query.cancel(); BlockingContactsProvider.cancelled.countDown() }
    }

    @Test
    fun contactsWithoutPermissionReturnPermissionMissingAndQueryNothing() = runTest {
        FakeContactsProvider.lastUri = null
        Robolectric.setupContentProvider(FakeContactsProvider::class.java, ContactsContract.AUTHORITY)
        shadowOf(application).denyPermissions(Manifest.permission.READ_CONTACTS)
        val result = ContactsSearchSource(application, Dispatchers.Unconfined).contacts("ann", 5)
        assertThat(result).isEqualTo(ContactLookup.PermissionMissing)
        assertThat(FakeContactsProvider.lastUri).isNull()
    }

    @Test
    fun contactsWithPermissionUseTheFilterUriAndRespectTheLimit() = runTest {
        Robolectric.setupContentProvider(FakeContactsProvider::class.java, ContactsContract.AUTHORITY)
        shadowOf(application).grantPermissions(Manifest.permission.READ_CONTACTS)
        // Only WhatsApp's chat entry is startable, as verified on the shipping app (calls need CALL_PHONE).
        val probe = ContactEntryPointProbe { _, entry -> entry.type == ContactActionType.MESSAGE }
        val result = ContactsSearchSource(application, Dispatchers.Unconfined, probe = probe).contacts(" ann b/ ", 2)
        val chat = ContactChannelAction("whatsapp", ContactActionType.MESSAGE, 11, "vnd.android.cursor.item/vnd.com.whatsapp.profile")
        assertThat(result).isEqualTo(ContactLookup.Found(listOf(
            ContactHit(3, "k3", "Anna Bell"), // distinct name-token prefixes beat a provider-only match
            ContactHit(1, "k1", "Ann Lee", phoneNumber = "+1 555 0100", channelActions = listOf(chat)),
        )))
        // One batched data query, only for the returned hits.
        assertThat(FakeContactsProvider.lastDataArgs?.take(2)).containsExactly("3", "1").inOrder()
        // Unsupported entry points (the CALL_PHONE-guarded calls) are not even read.
        assertThat(FakeContactsProvider.lastDataArgs).doesNotContain("vnd.android.cursor.item/vnd.com.whatsapp.voip.call")
        assertThat(FakeContactsProvider.lastDataArgs).contains("vnd.android.cursor.item/vnd.com.whatsapp.profile")
        assertThat(FakeContactsProvider.lastFilterUri?.lastPathSegment).isEqualTo("ann b/")
        assertThat(FakeContactsProvider.lastFilterUri.toString()).startsWith(ContactsContract.Contacts.CONTENT_FILTER_URI.toString())
    }

    @Test
    fun webIntentIsABrowsableHttpsViewInANewTask() {
        val intent = SearchHandoff.webSearchIntent(WebSearchEngine.DUCKDUCKGO, "a b&c")
        assertThat(intent.action).isEqualTo(Intent.ACTION_VIEW)
        assertThat(intent.data.toString()).isEqualTo("https://duckduckgo.com/?q=a%20b%26c")
        assertThat(intent.categories).contains(Intent.CATEGORY_BROWSABLE)
        assertThat(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK).isNotEqualTo(0)
    }

    @Test
    fun playStoreFallsBackToTheWebsiteWhenNoStoreAppIsInstalled() = runTest {
        val shadowApp = shadowOf(application)
        shadowApp.checkActivities(true)
        val web = ComponentName("org.example.browser", "org.example.browser.Main")
        shadowOf(application.packageManager).apply {
            addActivityIfNotPresent(web).apply {
                exported = true
                enabled = true
                applicationInfo.enabled = true
            }
            addIntentFilterForActivity(
                web,
                IntentFilter(Intent.ACTION_VIEW).apply {
                    addCategory(Intent.CATEGORY_DEFAULT)
                    addCategory(Intent.CATEGORY_BROWSABLE)
                    addDataScheme("https")
                },
            )
        }
        val result = GuardedIntentLauncher(application, AppLockGuard.NONE, Dispatchers.Unconfined)
            .startFirst(SearchHandoff.playStoreIntents("maps"))
        assertThat(result).isEqualTo(IntentHandoffResult.Started)
        assertThat(shadowApp.nextStartedActivity.data.toString())
            .isEqualTo("https://play.google.com/store/search?q=maps&c=apps")
    }

    @Test
    fun handoffWithoutAnyHandlerReportsNoHandler() = runTest {
        shadowOf(application).checkActivities(true)
        val result = GuardedIntentLauncher(application, AppLockGuard.NONE, Dispatchers.Unconfined)
            .startFirst(SearchHandoff.playStoreIntents("maps"))
        assertThat(result).isEqualTo(IntentHandoffResult.Unavailable)
    }

    @Test
    fun contactIntentViewsTheLookupUri() {
        val intent = SearchHandoff.contactIntent(ContactHit(7, "lk", "Ann"))
        assertThat(intent.action).isEqualTo(Intent.ACTION_VIEW)
        assertThat(intent.data).isEqualTo(ContactsContract.Contacts.getLookupUri(7, "lk"))
    }
}
