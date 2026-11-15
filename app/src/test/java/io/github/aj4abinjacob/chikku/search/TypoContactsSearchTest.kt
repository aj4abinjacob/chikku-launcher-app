package io.github.aj4abinjacob.chikku.search

import android.Manifest
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.CancellationSignal
import android.provider.ContactsContract.Contacts
import android.provider.ContactsContract.Data
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.search.ContactLookup
import io.github.aj4abinjacob.chikku.home.TestHomeApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestHomeApplication::class)
class TypoContactsSearchTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()

    class Provider : ContentProvider() {
        override fun onCreate() = true
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?, signal: CancellationSignal?): Cursor? {
            calls++
            if (uri == Data.CONTENT_URI) {
                // Contact ids, then one mimetype per supported entry point (phone always; providers vary).
                actionIds = args?.filter { arg -> arg.all(Char::isDigit) }.orEmpty()
                if (revokeInDetails) {
                    shadowOf(ApplicationProvider.getApplicationContext<Application>()).denyPermissions(Manifest.permission.READ_CONTACTS)
                    throw SecurityException()
                }
                return MatrixCursor(projection)
            }
            if (uri.toString().startsWith(Contacts.CONTENT_LOOKUP_URI.toString())) {
                return MatrixCursor(projection).apply {
                    // The provider reports the contact's current key, which may differ after a merge.
                    lookups[uri.lastPathSegment]?.let { (id, name) -> addRow(arrayOf<Any?>(id, "current-$id", name, null)) }
                }
            }
            val names = if (uri == Contacts.CONTENT_URI) {
                scans++
                if (blockScan) {
                    signal?.setOnCancelListener { cancelled.countDown() }
                    started.countDown()
                    check(cancelled.await(5, TimeUnit.SECONDS))
                    signal?.throwIfCanceled()
                }
                if (failScan) throw IllegalStateException("synthetic scan failure")
                snapshot
            } else filter
            return MatrixCursor(projection).apply {
                names.forEach { (id, name) -> addRow(arrayOf<Any?>(id, "k$id", name, null)) }
            }
        }
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor? =
            query(uri, projection, selection, args, sort, null)
        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0
        companion object {
            var snapshot = emptyList<Pair<Long, String>>()
            var filter = emptyList<Pair<Long, String>>()
            var lookups = emptyMap<String, Pair<Long, String>>()
            var actionIds = emptyList<String>()
            var calls = 0
            var scans = 0
            var failScan = false
            var revokeInDetails = false
            var blockScan = false
            var started = CountDownLatch(1)
            var cancelled = CountDownLatch(1)
        }
    }

    @Before fun prepare() {
        Provider.snapshot = emptyList(); Provider.filter = emptyList(); Provider.lookups = emptyMap(); Provider.actionIds = emptyList()
        Provider.calls = 0; Provider.scans = 0; Provider.failScan = false; Provider.revokeInDetails = false
        Provider.blockScan = false; Provider.started = CountDownLatch(1); Provider.cancelled = CountDownLatch(1)
        Robolectric.setupContentProvider(Provider::class.java, "com.android.contacts")
        shadowOf(application).grantPermissions(Manifest.permission.READ_CONTACTS)
    }

    @Test fun emptyProviderFilterStillFindsMisspelledNameBeyondTwentyAndReadsOnlyFinalActions() = runTest {
        Provider.snapshot = List(10_000) { it.toLong() to "Unrelated $it" } + listOf(10_001L to "Askhay Albert")
        val result = ContactsSearchSource(application, Dispatchers.Unconfined).contacts("Akshiy Albort", 20)
        assertThat((result as ContactLookup.Found).contacts.map { it.name }).containsExactly("Askhay Albert")
        assertThat(Provider.actionIds).containsExactly("10001")
    }

    @Test fun rankBeforeTrimFindsLiteralNamesAndBoundsDetailsToFive() = runTest {
        Provider.filter = List(20) { it.toLong() to "Provider Only $it" }
        Provider.snapshot = List(30) { (100 + it).toLong() to "Albert $it" } + listOf(200L to "Albert")
        val result = ContactsSearchSource(application, Dispatchers.Unconfined).contacts("Albert", 20) as ContactLookup.Found
        assertThat(result.contacts).hasSize(5)
        assertThat(result.contacts.first().id).isEqualTo(200)
        assertThat(Provider.actionIds).hasSize(5)
        assertThat(Provider.actionIds.first()).isEqualTo("200")
    }

    @Test fun enterpriseIdentitiesAreExcludedFromFilterAndNameScan() = runTest {
        assertThat(Contacts.isEnterpriseContactId(1_000_000_001L)).isTrue()
        Provider.filter = listOf(1_000_000_001L to "Albert")
        Provider.snapshot = listOf(1_000_000_001L to "Albert", 1L to "Albert")
        val result = ContactsSearchSource(application, Dispatchers.Unconfined).contacts("albert", 5) as ContactLookup.Found
        assertThat(result.contacts.map { it.id }).containsExactly(1L)
        assertThat(Provider.actionIds).containsExactly("1")
    }

    @Test fun invalidFilterRowsDoNotConsumeTheValidProviderMatchLimit() = runTest {
        Provider.filter = List(20) { index ->
            if (index % 2 == 0) index.toLong() to " "
            else (1_000_000_001L + index) to "Enterprise Person"
        } + listOf(50L to "Other Person") // matched only by phone/email, beyond invalid rows
        Provider.snapshot = emptyList()
        val result = ContactsSearchSource(application, Dispatchers.Unconfined).contacts("555", 20) as ContactLookup.Found
        assertThat(result.contacts.map { it.id }).containsExactly(50L)
        assertThat(result.contacts.single().name).isEqualTo("Other Person")
        assertThat(Provider.actionIds).containsExactly("50")
    }

    @Test fun freshQueriesObserveRenamesAndDeletedContactsWithoutRetainedIndex() = runTest {
        val source = ContactsSearchSource(application, Dispatchers.Unconfined)
        Provider.snapshot = listOf(1L to "Askhay Albert")
        assertThat((source.contacts("akshiy", 5) as ContactLookup.Found).contacts).hasSize(1)
        Provider.snapshot = listOf(1L to "Other Person")
        assertThat((source.contacts("akshiy", 5) as ContactLookup.Found).contacts).isEmpty()
        Provider.snapshot = emptyList()
        assertThat((source.contacts("other", 5) as ContactLookup.Found).contacts).isEmpty()
        assertThat(Provider.scans).isEqualTo(3)
    }

    @Test fun nameScanFailureKeepsNonNameProviderMatches() = runTest {
        Provider.filter = listOf(1L to "Other Person") // e.g. phone/email/nickname match
        Provider.failScan = true
        val result = ContactsSearchSource(application, Dispatchers.Unconfined).contacts("555", 5) as ContactLookup.Found
        assertThat(result.contacts.map { it.id }).containsExactly(1L)
    }

    @Test fun revokedPermissionDuringDetailsWithholdsAllNamesAndNextQueryTouchesNothing() = runTest {
        Provider.snapshot = listOf(1L to "Albert")
        Provider.revokeInDetails = true
        val source = ContactsSearchSource(application, Dispatchers.Unconfined)
        assertThat(source.contacts("albort", 5)).isEqualTo(ContactLookup.PermissionMissing)
        val before = Provider.calls
        assertThat(source.contacts("albort", 5)).isEqualTo(ContactLookup.PermissionMissing)
        assertThat(Provider.calls).isEqualTo(before)
    }

    @Test fun cancellationInterruptsFullNameScanAndNewQueriesWork() = runTest {
        Provider.blockScan = true
        val source = ContactsSearchSource(application, Dispatchers.IO)
        val read = async(Dispatchers.IO) { source.contacts("albort", 5) }
        try {
            assertThat(withContext(Dispatchers.IO) { Provider.started.await(5, TimeUnit.SECONDS) }).isTrue()
            read.cancel()
            assertThat(withContext(Dispatchers.IO) { Provider.cancelled.await(2, TimeUnit.SECONDS) }).isTrue()
            read.join()
            assertThat(read.isCancelled).isTrue()
            Provider.blockScan = false
            Provider.snapshot = listOf(2L to "Blinkit")
            assertThat((source.contacts("blonkit", 5) as ContactLookup.Found).contacts.map { it.id }).containsExactly(2L)
        } finally { read.cancel(); Provider.cancelled.countDown() }
    }

    @Test fun historyKeysResolveThroughTheLookupUriInOrderAndSkipDeletedContacts() = runTest {
        // A name search for the key text would find these decoys instead of the remembered people.
        Provider.filter = listOf(90L to "key-b")
        Provider.snapshot = listOf(91L to "key-a")
        Provider.lookups = mapOf("key-a" to (1L to "Amma"), "key-b" to (2L to "Binu"))
        val result = ContactsSearchSource(application, Dispatchers.Unconfined)
            .byLookupKeys(listOf("key-b", "deleted", "key-a", "key-b")) as ContactLookup.Found
        assertThat(result.contacts.map { it.name }).containsExactly("Binu", "Amma").inOrder()
        // History keeps the key it remembered, so the same entry resolves again next time.
        assertThat(result.contacts.map { it.lookupKey }).containsExactly("key-b", "key-a").inOrder()
        assertThat(Provider.scans).isEqualTo(0)
    }

    @Test fun historyLookupWithoutPermissionReturnsNoPartialList() = runTest {
        Provider.lookups = mapOf("key-a" to (1L to "Amma"))
        shadowOf(application).denyPermissions(Manifest.permission.READ_CONTACTS)
        assertThat(ContactsSearchSource(application, Dispatchers.Unconfined).byLookupKeys(listOf("key-a")))
            .isEqualTo(ContactLookup.PermissionMissing)
        assertThat(Provider.calls).isEqualTo(0)
    }

    @Test fun suggestionsKeepOnlyNamesWithinTypoDistanceAndIgnoreTheProviderFilter() = runTest {
        Provider.filter = listOf(9L to "Unrelated Provider Row")
        Provider.snapshot = listOf(1L to "Zebra", 2L to "Albert", 3L to "Alberta")
        val result = ContactsSearchSource(application, Dispatchers.Unconfined).suggestions("albort", 5) as ContactLookup.Found
        assertThat(result.contacts.map { it.name }).containsExactly("Albert", "Alberta")
        assertThat(Provider.scans).isEqualTo(1)
    }
}
