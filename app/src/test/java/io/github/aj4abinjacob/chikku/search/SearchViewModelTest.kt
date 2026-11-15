package io.github.aj4abinjacob.chikku.search

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.AppsState
import io.github.aj4abinjacob.chikku.core.model.search.ContactHit
import io.github.aj4abinjacob.chikku.core.model.search.ContactLookup
import io.github.aj4abinjacob.chikku.core.model.search.ContactSource
import io.github.aj4abinjacob.chikku.core.model.search.DefaultAppSearcher
import io.github.aj4abinjacob.chikku.core.model.search.SearchRow
import io.github.aj4abinjacob.chikku.core.model.search.ShortcutLookup
import io.github.aj4abinjacob.chikku.core.model.search.UniversalSearch
import io.github.aj4abinjacob.chikku.core.model.search.WebSuggestionSource
import io.github.aj4abinjacob.chikku.core.model.search.WebSuggestionResult
import io.github.aj4abinjacob.chikku.core.model.settings.WebSearchEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.home.FakeAppCustomizationRepository
import io.github.aj4abinjacob.chikku.home.FakeAppsRepository
import io.github.aj4abinjacob.chikku.home.FakeSettingsRepository
import io.github.aj4abinjacob.chikku.home.app
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val maps = app("Maps")
    private val mail = app("Mail")
    private val music = app("Music")

    private val settings = FakeSettingsRepository()
    private val apps = FakeAppsRepository(AppsState.Loaded(listOf(maps, mail, music)))
    private val customizations = FakeAppCustomizationRepository()

    private class Contacts : ContactSource {
        val queries = mutableListOf<String>()
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun contacts(query: String, limit: Int): ContactLookup {
            queries += query
            gate?.await()
            return ContactLookup.Found(listOf(ContactHit(1, "k", "Contact for $query")))
        }
    }

    private val contacts = Contacts()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(
        source: WebSuggestionSource = WebSuggestionSource { _, _ -> WebSuggestionResult.Unavailable },
        collectSuggestions: Boolean = true,
    ): SearchViewModel {
        val vm = SearchViewModel(
            SavedStateHandle(),
            settings,
            apps,
            customizations,
            UniversalSearch(DefaultAppSearcher(), { ShortcutLookup.NotDefaultHome }, contacts),
            dispatcher,
            source,
        )
        backgroundScope.launch { vm.results.collect {} }
        if (collectSuggestions) backgroundScope.launch { vm.webSuggestions.collect {} }
        return vm
    }

    @Test
    fun searchesVisibleAppsAndExcludesHiddenOnes() = runTest(dispatcher) {
        customizations.setHidden(music.key, true)
        val vm = viewModel()
        vm.setQuery("m")
        advanceUntilIdle()
        val rows = vm.results.value.rows
        assertThat(rows.filterIsInstance<SearchRow.App>().map { it.app }).containsExactly(maps, mail).inOrder()
        assertThat(rows.filterIsInstance<SearchRow.WebSearch>()).hasSize(1)
    }

    @Test
    fun contactsAreOnlyQueriedWhileEnabled() = runTest(dispatcher) {
        val vm = viewModel()
        vm.setQuery("ann")
        advanceUntilIdle()
        assertThat(contacts.queries).isEmpty()
        assertThat(vm.results.value.rows.filter { it is SearchRow.Contact || it is SearchRow.Person }).isEmpty()

        settings.update { it.copy(search = it.search.copy(contactsEnabled = true)) }
        advanceUntilIdle()
        assertThat(contacts.queries).containsExactly("ann")
        assertThat(vm.results.value.rows.filter { it is SearchRow.Contact || it is SearchRow.Person }).hasSize(1)

        settings.update { it.copy(search = it.search.copy(contactsEnabled = false)) }
        vm.setQuery("bob")
        advanceUntilIdle()
        assertThat(contacts.queries).containsExactly("ann")
        assertThat(vm.results.value.rows.filter { it is SearchRow.Contact || it is SearchRow.Person }).isEmpty()
    }

    @Test
    fun aNewerQueryCancelsTheStaleSearch() = runTest(dispatcher) {
        settings.state.value = LauncherSettings().let { it.copy(search = it.search.copy(contactsEnabled = true)) }
        val vm = viewModel()
        val stuck = CompletableDeferred<Unit>()
        contacts.gate = stuck
        vm.setQuery("first")
        advanceUntilIdle() // "first" is now blocked inside the contact source
        contacts.gate = null
        vm.setQuery("second")
        advanceUntilIdle()
        assertThat(vm.results.value.query).isEqualTo("second")
        stuck.complete(Unit) // the stale search was cancelled; completing it changes nothing
        advanceUntilIdle()
        assertThat(vm.results.value.query).isEqualTo("second")
        assertThat(vm.results.value.rows).contains(SearchRow.Person(ContactHit(1, "k", "Contact for second"))) // no app matches: card leads
    }

    @Test fun anUncooperativeOldProviderCannotDelayNewLocalQueriesOrProfileFiltering() = runTest(dispatcher) {
        settings.update { it.copy(search = it.search.copy(contactsEnabled = true)) }
        val release = CompletableDeferred<Unit>()
        val entered = CompletableDeferred<Unit>()
        val provider = ContactSource { q, _ ->
            if (q == "ma") { entered.complete(Unit); withContext(NonCancellable) { release.await() } }
            ContactLookup.Found(listOf(ContactHit(1, "synthetic", "Synthetic contact")))
        }
        val vm = SearchViewModel(SavedStateHandle(), settings, apps, customizations,
            UniversalSearch(DefaultAppSearcher(), { ShortcutLookup.NotDefaultHome }, provider), dispatcher)
        backgroundScope.launch { vm.results.collect {} }
        try {
            vm.setQuery("ma"); advanceTimeBy(121); runCurrent()
            assertThat(entered.isCompleted).isTrue()
            assertThat(vm.results.value.apps).contains(mail)
            vm.setQuery("music"); advanceTimeBy(121); runCurrent()
            assertThat(vm.results.value.query).isEqualTo("music")
            assertThat(vm.results.value.apps).containsExactly(music)
            assertThat(vm.results.value.contacts).isNull()
            apps.state.value = AppsState.Loading; runCurrent()
            assertThat(vm.results.value.apps).isEmpty()
            assertThat(vm.results.value.shortcuts).isNull()
        } finally { release.complete(Unit); runCurrent() }
    }

    @Test
    fun blankQueryHasNoRows() = runTest(dispatcher) {
        val vm = viewModel()
        vm.setQuery("   ")
        advanceUntilIdle()
        assertThat(vm.results.value.rows).isEmpty()
    }
    @Test
    fun suggestionsRequireOptInAndOnlyReceiveTypedText() = runTest(dispatcher) {
        val queries = mutableListOf<Pair<WebSearchEngine, String>>()
        val vm = viewModel(WebSuggestionSource { engine, query ->
            queries += engine to query
            WebSuggestionResult.Found(listOf("$query tomorrow"))
        })
        vm.setQuery("weather")
        advanceTimeBy(500)
        runCurrent()
        assertThat(queries).isEmpty()
        settings.update { it.copy(search = it.search.copy(onlineSuggestions = true)) }
        runCurrent()
        advanceTimeBy(299)
        runCurrent()
        assertThat(queries).isEmpty()
        advanceTimeBy(1)
        runCurrent()
        assertThat(queries).containsExactly(WebSearchEngine.DUCKDUCKGO to "weather")
        assertThat(vm.webSuggestions.value.values).containsExactly("weather tomorrow")
        settings.update { it.copy(search = it.search.copy(webEngine = WebSearchEngine.GOOGLE)) }
        runCurrent()
        assertThat(vm.webSuggestions.value.values).isEmpty()
        advanceTimeBy(300)
        runCurrent()
        assertThat(queries.last()).isEqualTo(WebSearchEngine.GOOGLE to "weather")
        settings.update { it.copy(search = it.search.copy(webEngine = WebSearchEngine.BING)) }
        advanceTimeBy(500)
        runCurrent()
        assertThat(queries).hasSize(2)
        assertThat(vm.webSuggestions.value.values).isEmpty()
    }

    @Test
    fun rapidTypingAndDisableCancelRequestsWithoutDelayingLocalMatches() = runTest(dispatcher) {
        settings.update { it.copy(search = it.search.copy(onlineSuggestions = true)) }
        val gate = CompletableDeferred<Unit>()
        var cancelled = false
        val queries = mutableListOf<String>()
        val vm = viewModel(WebSuggestionSource { _, query ->
            queries += query
            try { gate.await() } catch (e: CancellationException) { cancelled = true; throw e }
            WebSuggestionResult.Found(listOf("$query suggestion"))
        })
        vm.setQuery("ma")
        runCurrent()
        advanceTimeBy(200)
        vm.setQuery("map")
        runCurrent()
        advanceTimeBy(120)
        runCurrent()
        assertThat(vm.results.value.apps).containsExactly(maps)
        assertThat(queries).isEmpty()
        advanceTimeBy(180)
        runCurrent()
        assertThat(queries).containsExactly("map")
        vm.setQuery("maps")
        runCurrent()
        assertThat(cancelled).isTrue()
        advanceTimeBy(300)
        runCurrent()
        cancelled = false
        settings.update { it.copy(search = it.search.copy(onlineSuggestions = false)) }
        runCurrent()
        assertThat(cancelled).isTrue()
        gate.complete(Unit)
        runCurrent()
        assertThat(vm.webSuggestions.value.values).isEmpty()
    }

    @Test
    fun offlineFailureStillLeavesBrowserSearchAndBlankClearsSuggestions() = runTest(dispatcher) {
        settings.update { it.copy(search = it.search.copy(onlineSuggestions = true)) }
        var calls = 0
        val vm = viewModel(WebSuggestionSource { _, _ -> calls++; throw java.io.IOException("offline") })
        vm.setQuery("weather")
        advanceTimeBy(400)
        runCurrent()
        assertThat(vm.webSuggestions.value.unavailable).isTrue()
        assertThat(vm.results.value.rows.filterIsInstance<SearchRow.WebSearch>()).hasSize(1)
        settings.update { it.copy(search = it.search.copy(showWebSuggestionRow = false)) }
        runCurrent()
        assertThat(vm.webSuggestions.value.unavailable).isFalse()
        vm.setQuery("   ")
        advanceTimeBy(400)
        runCurrent()
        assertThat(vm.webSuggestions.value.values).isEmpty()
        assertThat(calls).isEqualTo(1)
    }

    @Test
    fun closingSearchCancelsAnInFlightRequestAndClearsTheReplay() = runTest(dispatcher) {
        settings.update { it.copy(search = it.search.copy(onlineSuggestions = true)) }
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        var cancelled = false
        val vm = viewModel(WebSuggestionSource { _, _ ->
            calls++
            try { gate.await() } catch (e: CancellationException) { cancelled = true; throw e }
            WebSuggestionResult.Found(listOf("weather tomorrow"))
        }, collectSuggestions = false)
        val collector = backgroundScope.launch { vm.webSuggestions.collect {} }
        vm.setQuery("weather")
        advanceTimeBy(350)
        runCurrent()
        assertThat(calls).isEqualTo(1)
        collector.cancel()
        runCurrent()
        assertThat(cancelled).isTrue()
        assertThat(vm.webSuggestions.value.values).isEmpty()
        vm.setQuery("weather tomorrow")
        advanceTimeBy(500)
        runCurrent()
        assertThat(calls).isEqualTo(1)
    }

}
