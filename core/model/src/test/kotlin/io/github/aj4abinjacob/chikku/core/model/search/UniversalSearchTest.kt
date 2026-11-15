package io.github.aj4abinjacob.chikku.core.model.search

import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.LauncherApp
import io.github.aj4abinjacob.chikku.core.model.ProfileType
import io.github.aj4abinjacob.chikku.core.model.settings.SearchSettings
import io.github.aj4abinjacob.chikku.core.model.settings.WebSearchEngine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class UniversalSearchTest {

    private fun app(pkg: String, label: String, serial: Long = 0) =
        LauncherApp(AppKey(pkg, "$pkg.Main", serial), label, ProfileType.PERSONAL)

    private val camera = app("com.camera", "Camera")
    private val calendar = app("com.calendar", "Calendar")
    private val calc = app("com.calc", "Calculator")
    private val secret = app("com.secret", "Calc Secret")
    private val apps = listOf(camera, calendar, calc, secret)

    private val shortcuts = listOf(
        ShortcutHit("com.camera", "selfie", 0, "Take selfie", camera.key),
        ShortcutHit("com.calendar", "new", 0, "New calendar event", calendar.key),
        ShortcutHit("com.secret", "vault", 0, "Calc vault", secret.key),
        ShortcutHit("com.calendar", "work", 10, "Calendar work", null), // profile not listed
    )

    private class RecordingContacts(private val result: ContactLookup) : ContactSource {
        val queries = mutableListOf<String>()
        override suspend fun contacts(query: String, limit: Int): ContactLookup {
            queries += query
            return result
        }
    }

    private class RecordingShortcuts(private val result: ShortcutLookup) : ShortcutSource {
        var calls = 0
        var lastApps: List<LauncherApp> = emptyList()
        override suspend fun shortcuts(apps: List<LauncherApp>): ShortcutLookup {
            calls++
            lastApps = apps
            return result
        }
    }

    private fun search(
        contacts: ContactSource = RecordingContacts(ContactLookup.Found(emptyList())),
        shortcutSource: ShortcutSource = RecordingShortcuts(ShortcutLookup.Found(shortcuts)),
    ) = UniversalSearch(DefaultAppSearcher(), shortcutSource, contacts)

    @Test fun slowProvidersNeverDelayTheInitialAppMatchesAndExpireWithinTheDeadline() = runTest {
        val gate = CompletableDeferred<Unit>()
        val search = search(contacts = ContactSource { _, _ -> gate.await(); ContactLookup.Found(emptyList()) })
        val values = mutableListOf<SearchResults>()
        val job = launch { search.results("cal", apps, emptySet(), SearchSettings(contactsEnabled = true)).collect { values += it } }
        runCurrent()
        assertThat(values.first().apps).contains(calendar)
        assertThat(values.first().contacts).isNull()
        advanceTimeBy(UniversalSearch.PROVIDER_TIMEOUT_MILLIS + 1); runCurrent()
        assertThat(values.last().contacts).isEqualTo(ContactLookup.Unavailable)
        assertThat(values.last().apps).contains(calendar)
        job.join()
    }

    @Test fun providerExceptionsDoNotCancelOtherProvidersOrSubsequentSearches() = runTest {
        val search = search(contacts = ContactSource { _, _ -> throw IllegalArgumentException("synthetic contacts failure") },
            shortcutSource = ShortcutSource { throw NullPointerException("synthetic shortcut failure") })
        for (query in listOf("cal", "camera")) {
            val result = search.search(query, apps, emptySet(), SearchSettings(contactsEnabled = true))
            assertThat(result.apps).isNotEmpty()
            assertThat(result.contacts).isEqualTo(ContactLookup.Unavailable)
            assertThat(result.shortcuts).isEqualTo(ShortcutLookup.Unavailable)
        }
        val independent = search(contacts = ContactSource { _, _ -> throw IllegalArgumentException("synthetic failure") })
            .search("calendar", apps, emptySet(), SearchSettings(contactsEnabled = true))
        assertThat((independent.shortcuts as ShortcutLookup.Found).shortcuts).containsExactly(shortcuts[1])
    }

    @Test
    fun rowsAreMergedInSectionOrder() = runTest {
        val contacts = RecordingContacts(ContactLookup.Found(listOf(ContactHit(1, "k1", "Cal Smith"))))
        val results = search(contacts).search(
            "cal", apps, hidden = emptySet(), SearchSettings(contactsEnabled = true, webEngine = WebSearchEngine.ECOSIA),
        )
        assertThat(results.rows).containsExactly(
            SearchRow.Person(ContactHit(1, "k1", "Cal Smith")), // a whole name word beats an app prefix
            SearchRow.App(calendar),
            SearchRow.App(calc),
            SearchRow.App(secret),
            SearchRow.Shortcut(shortcuts[2]), // label prefix ranks before a later-word prefix
            SearchRow.Shortcut(shortcuts[1]),
            SearchRow.WebSearch(WebSearchEngine.ECOSIA, "cal"),
            SearchRow.PlayStore("cal"),
        ).inOrder()
    }

    @Test
    fun personCardLeadsOnlyWhenTheNameOutranksTheBestApp() = runTest {
        val ann = ContactHit(1, "k1", "Ann Lee", phoneNumber = "+15550100")
        val bob = ContactHit(2, "k2", "Annabel Bob")
        val noApp = search(RecordingContacts(ContactLookup.Found(listOf(ann, bob)))).search("ann", apps, emptySet(), SearchSettings(contactsEnabled = true))
        assertThat(noApp.rows.take(2)).containsExactly(SearchRow.Person(ann), SearchRow.Contact(bob)).inOrder()

        // "Cam" is a prefix of both the Camera app and "Camille": the app keeps first place, no card.
        val camille = ContactHit(3, "k3", "Camille Roy")
        val tie = search(RecordingContacts(ContactLookup.Found(listOf(camille)))).search("cam", apps, emptySet(), SearchSettings(contactsEnabled = true))
        assertThat(tie.rows.first()).isEqualTo(SearchRow.App(camera))
        assertThat(tie.rows).contains(SearchRow.Contact(camille))
        assertThat(tie.rows.filterIsInstance<SearchRow.Person>()).isEmpty()

        // A match the provider found outside the name (e.g. an email) never jumps above an app.
        val email = ContactHit(4, "k4", "Zed")
        val other = search(RecordingContacts(ContactLookup.Found(listOf(email)))).search("cam", apps, emptySet(), SearchSettings(contactsEnabled = true))
        assertThat(other.rows.filterIsInstance<SearchRow.Person>()).isEmpty()
    }

    @Test
    fun theClosestNameBecomesTheCardWhateverTheProviderOrder() = runTest {
        val anne = ContactHit(1, "k1", "Anne Xavier ( Amma's Friend)")
        val ann = ContactHit(2, "k2", "Ann")
        val arun = ContactHit(3, "k3", "Arun St Ann's")
        val results = search(RecordingContacts(ContactLookup.Found(listOf(anne, arun, ann))))
            .search("Ann", apps, emptySet(), SearchSettings(contactsEnabled = true))
        assertThat(results.rows.take(3)).containsExactly(
            SearchRow.Person(ann), // full name
            SearchRow.Contact(arun), // whole word "Ann" in "Ann's"
            SearchRow.Contact(anne), // prefix only
        ).inOrder()
    }

    @Test
    fun calculationComesFirstAndErrorsAreShown() = runTest {
        val results = search().search(" 2+2 ", apps, emptySet(), SearchSettings())
        assertThat(results.rows.first()).isEqualTo(SearchRow.Calculation("2+2", CalculationResult.Value(4.0, "4")))
        val error = search().search("1/0", apps, emptySet(), SearchSettings())
        assertThat(error.rows.first())
            .isEqualTo(SearchRow.Calculation("1/0", CalculationResult.Error(CalculationResult.Reason.DIVISION_BY_ZERO)))
        val off = search().search("2+2", apps, emptySet(), SearchSettings(calculatorEnabled = false))
        assertThat(off.rows.filterIsInstance<SearchRow.Calculation>()).isEmpty()
    }

    @Test
    fun hiddenAppsAndTheirShortcutsAreExcluded() = runTest {
        val source = RecordingShortcuts(ShortcutLookup.Found(shortcuts))
        val results = search(shortcutSource = source).search("calc", apps, hidden = setOf(secret.key), SearchSettings())
        assertThat(results.apps).containsExactly(calc, calendar).inOrder() // literal prefix before typo suggestion
        assertThat(source.lastApps).doesNotContain(secret)
        assertThat((results.shortcuts as ShortcutLookup.Found).shortcuts).isEmpty() // "Calc vault" belongs to the hidden app
    }

    @Test
    fun shortcutsFromUnlistedProfilesNeverSurface() = runTest {
        val results = search().search("calendar", apps, emptySet(), SearchSettings())
        assertThat((results.shortcuts as ShortcutLookup.Found).shortcuts).containsExactly(shortcuts[1])
    }

    @Test
    fun disabledContactsAreNeverQueried() = runTest {
        val contacts = RecordingContacts(ContactLookup.Found(listOf(ContactHit(1, "k", "Ann"))))
        val results = search(contacts).search("ann", apps, emptySet(), SearchSettings(contactsEnabled = false))
        assertThat(contacts.queries).isEmpty()
        assertThat(results.contacts).isNull()
        assertThat(results.rows.filterIsInstance<SearchRow.Contact>()).isEmpty()
    }

    @Test
    fun enabledContactsWithoutPermissionShowAGrantRow() = runTest {
        val contacts = RecordingContacts(ContactLookup.PermissionMissing)
        val results = search(contacts).search("ann", apps, emptySet(), SearchSettings(contactsEnabled = true))
        assertThat(contacts.queries).containsExactly("ann")
        assertThat(results.rows).contains(SearchRow.ContactsPermissionNeeded)
    }

    @Test
    fun disabledShortcutsAreNotRead_andTypedFailuresAddNoRows() = runTest {
        val source = RecordingShortcuts(ShortcutLookup.Found(shortcuts))
        val off = search(shortcutSource = source).search("cal", apps, emptySet(), SearchSettings(shortcutsEnabled = false))
        assertThat(source.calls).isEqualTo(0)
        assertThat(off.shortcuts).isNull()

        val notHome = search(shortcutSource = RecordingShortcuts(ShortcutLookup.NotDefaultHome))
            .search("cal", apps, emptySet(), SearchSettings())
        assertThat(notHome.shortcuts).isEqualTo(ShortcutLookup.NotDefaultHome)
        assertThat(notHome.rows.filterIsInstance<SearchRow.Shortcut>()).isEmpty()
    }

    @Test
    fun webAndPlayStoreRowsFollowSettings() = runTest {
        val results = search().search("zzz", apps, emptySet(), SearchSettings(showWebSuggestionRow = false, playStoreRow = false))
        assertThat(results.rows).isEmpty()
        val web = search().search("zzz", apps, emptySet(), SearchSettings(webEngine = WebSearchEngine.BRAVE))
        assertThat(web.rows).containsExactly(SearchRow.WebSearch(WebSearchEngine.BRAVE, "zzz"), SearchRow.PlayStore("zzz")).inOrder()
    }

    @Test
    fun blankQueryReadsNothing() = runTest {
        val contacts = RecordingContacts(ContactLookup.Found(emptyList()))
        val shortcutsSource = RecordingShortcuts(ShortcutLookup.Found(shortcuts))
        val results = search(contacts, shortcutsSource).search("   ", apps, emptySet(), SearchSettings(contactsEnabled = true))
        assertThat(results.rows).isEmpty()
        assertThat(contacts.queries).isEmpty()
        assertThat(shortcutsSource.calls).isEqualTo(0)
    }

    @Test
    fun appResultsAreCapped() = runTest {
        val many = (1..40).map { app("com.app$it", "App $it") }
        val results = search().search("app", many, emptySet(), SearchSettings())
        assertThat(results.apps).hasSize(UniversalSearch.MAX_APPS)
    }

    private val swiggy = app("in.swiggy", "Swiggy")

    private class TypoContacts(private val found: List<ContactHit>, private val near: List<ContactHit>) : ContactSource {
        var suggestionCalls = 0
        override suspend fun contacts(query: String, limit: Int) = ContactLookup.Found(found)
        override suspend fun suggestions(query: String, limit: Int): ContactLookup {
            suggestionCalls++
            return ContactLookup.Found(near)
        }
    }

    @Test
    fun typoWithNoMatchesSuggestsAppsAndContactsBeforeTheWebRows() = runTest {
        val sweety = ContactHit(1, "k1", "Sweety")
        val contacts = TypoContacts(found = emptyList(), near = listOf(sweety))
        // Typo-tolerant matching already finds Swiggy, so it is a real (suggested) match and the
        // "did you mean" fallback stays out of the way.
        val inline = search(contacts).search("seigg", apps + swiggy, emptySet(), SearchSettings(contactsEnabled = true))
        assertThat(inline.rows.first()).isEqualTo(SearchRow.App(swiggy, suggested = true))
        assertThat(inline.rows.none { it is SearchRow.SuggestedApp || it is SearchRow.SuggestedContact }).isTrue()
        // With no app match at all, near contacts are offered before the web rows.
        val results = search(contacts).search("seigg", apps, emptySet(), SearchSettings(contactsEnabled = true))
        assertThat(results.rows).containsExactly(
            SearchRow.SuggestedContact(sweety),
            SearchRow.WebSearch(SearchSettings().webEngine, "seigg"),
            SearchRow.PlayStore("seigg"),
        ).inOrder()
    }

    @Test
    fun anyRealMatchMeansNoSuggestions() = runTest {
        val ann = ContactHit(1, "k1", "Ann")
        val contactMatch = TypoContacts(found = listOf(ann), near = listOf(ContactHit(2, "k2", "Anne")))
        val byContact = search(contactMatch).search("swigg", apps + swiggy + app("x", "Swigg"), emptySet(), SearchSettings(contactsEnabled = true))
        assertThat(byContact.suggestedApps).isEmpty()
        val onlyContact = search(contactMatch).search("ann", apps + swiggy, emptySet(), SearchSettings(contactsEnabled = true))
        assertThat(onlyContact.suggestedApps).isEmpty()
        assertThat(onlyContact.suggestedContacts).isEmpty()
        assertThat(contactMatch.suggestionCalls).isEqualTo(0)

        val byApp = search(TypoContacts(emptyList(), listOf(ann))).search("cam", apps, emptySet(), SearchSettings(contactsEnabled = true))
        assertThat(byApp.rows.none { it is SearchRow.SuggestedApp || it is SearchRow.SuggestedContact }).isTrue()

        val sum = search().search("1+2", apps, emptySet(), SearchSettings())
        assertThat(sum.suggestedApps).isEmpty()
    }

    @Test
    fun contactsOffNeverAsksForContactSuggestionsAndHiddenAppsAreNeverSuggested() = runTest {
        val contacts = TypoContacts(emptyList(), listOf(ContactHit(1, "k1", "Sweety")))
        val results = search(contacts).search("seigg", apps + swiggy, setOf(swiggy.key), SearchSettings(contactsEnabled = false))
        assertThat(results.suggestedApps).isEmpty()
        assertThat(results.suggestedContacts).isEmpty()
        assertThat(contacts.suggestionCalls).isEqualTo(0)
    }

    @Test
    fun suggestionsWaitForTheContactPassInsteadOfFlashingFirst() = runTest {
        val gate = CompletableDeferred<Unit>()
        val contacts = ContactSource { _, _ -> gate.await(); ContactLookup.Found(listOf(ContactHit(1, "k1", "Seigg Kumar"))) }
        val values = mutableListOf<SearchResults>()
        val job = launch { search(contacts).results("seigg", apps + swiggy, emptySet(), SearchSettings(contactsEnabled = true)).collect { values += it } }
        runCurrent()
        assertThat(values.single().suggestedApps).isEmpty()
        gate.complete(Unit); runCurrent()
        assertThat(values.last().suggestedApps).isEmpty() // the contact matched, so no guess
        job.join()
    }

    @Test
    fun withoutProvidersTheSingleEmissionCarriesSuggestions() = runTest {
        val values = search().results("seigg", apps + swiggy, emptySet(), SearchSettings(shortcutsEnabled = false)).toList()
        assertThat(values.single().rows.first()).isEqualTo(SearchRow.App(swiggy, suggested = true))
    }
}
