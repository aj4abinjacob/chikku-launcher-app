package io.github.aj4abinjacob.chikku.search

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.AppsState
import io.github.aj4abinjacob.chikku.core.model.ProfileType
import io.github.aj4abinjacob.chikku.core.model.search.ContactHit
import io.github.aj4abinjacob.chikku.core.model.search.ContactLookup
import io.github.aj4abinjacob.chikku.core.model.search.ContactSource
import io.github.aj4abinjacob.chikku.core.model.search.DefaultAppSearcher
import io.github.aj4abinjacob.chikku.core.model.search.HistoryEntry
import io.github.aj4abinjacob.chikku.core.model.search.HistoryTarget
import io.github.aj4abinjacob.chikku.core.model.search.RecentItem
import io.github.aj4abinjacob.chikku.core.model.search.SearchHistoryRepository
import io.github.aj4abinjacob.chikku.core.model.search.ShortcutLookup
import io.github.aj4abinjacob.chikku.core.model.search.UniversalSearch
import io.github.aj4abinjacob.chikku.home.FakeAppCustomizationRepository
import io.github.aj4abinjacob.chikku.home.FakeAppsRepository
import io.github.aj4abinjacob.chikku.home.FakeSettingsRepository
import io.github.aj4abinjacob.chikku.home.app
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchHistoryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val now = 1_800_000_000_000L
    private fun entry(target: HistoryTarget, score: Double) = HistoryEntry(target, score, now, now, List(24) { 0.0 })

    private class History(initial: List<HistoryEntry>) : SearchHistoryRepository {
        override val entries = MutableStateFlow(initial)
        override suspend fun record(target: HistoryTarget) = Unit
        override suspend fun forget(target: HistoryTarget) { entries.value = entries.value.filter { it.target != target } }
        override suspend fun clear() { entries.value = emptyList() }
    }

    private val amma = ContactHit(1, "lookup-amma", "Amma")
    private val contacts = object : ContactSource {
        override suspend fun contacts(query: String, limit: Int) = ContactLookup.Found(emptyList())
        override suspend fun byLookupKeys(lookupKeys: List<String>) = ContactLookup.Found(listOf(amma).filter { it.lookupKey in lookupKeys })
    }

    @Test
    fun emptySearchShowsRankedHistoryAndWithdrawsHiddenLockedOrTurnedOffItemsAtOnce() = runTest(dispatcher) {
        val camera = app("Camera")
        val music = app("Music")
        val work = app("Work", profile = ProfileType.WORK)
        val settings = FakeSettingsRepository()
        val apps = FakeAppsRepository(AppsState.Loaded(listOf(camera, music, work)))
        val custom = FakeAppCustomizationRepository()
        val history = History(
            listOf(
                entry(HistoryTarget.App(camera.key), 1.0),
                entry(HistoryTarget.App(music.key), 4.0),
                entry(HistoryTarget.App(work.key), 99.0),
                entry(HistoryTarget.WebQuery("pizza near me"), 3.0),
                entry(HistoryTarget.Contact(amma.lookupKey), 2.0),
            ),
        )
        val locked = MutableStateFlow<Set<AppKey>>(emptySet())
        val vm = SearchViewModel(
            SavedStateHandle(), settings, apps, custom,
            UniversalSearch(DefaultAppSearcher(), { ShortcutLookup.NotDefaultHome }, contacts),
            dispatcher, searchHistory = history,
            isLocked = { it in locked.value }, lockChanges = locked, clock = { now }, hourOf = { 12 },
        )
        backgroundScope.launch { vm.results.collect {} }
        settings.update { it.copy(search = it.search.copy(historyLearningEnabled = true, contactsEnabled = true)) }
        advanceUntilIdle()
        assertThat(vm.results.value.apps).containsExactly(music, camera).inOrder() // work profile never suggested
        assertThat(vm.results.value.recent).containsExactly(RecentItem.Query("pizza near me"), RecentItem.Contact(amma)).inOrder()

        custom.setHidden(music.key, true)
        locked.value = setOf(camera.key)
        advanceUntilIdle()
        assertThat(vm.results.value.apps).isEmpty()

        settings.update { it.copy(search = it.search.copy(contactsEnabled = false)) }
        advanceUntilIdle()
        assertThat(vm.results.value.recent).containsExactly(RecentItem.Query("pizza near me"))

        settings.update { it.copy(search = it.search.copy(historySuggestionsEnabled = false)) }
        advanceUntilIdle()
        assertThat(vm.results.value.rows).isEmpty()

        settings.update { it.copy(search = it.search.copy(historySuggestionsEnabled = true)) }
        history.clear()
        advanceUntilIdle()
        assertThat(vm.results.value.rows).isEmpty()
    }

    @Test
    fun typingReplacesSuggestions() = runTest(dispatcher) {
        val camera = app("Camera")
        val settings = FakeSettingsRepository()
        val vm = SearchViewModel(
            SavedStateHandle(), settings, FakeAppsRepository(AppsState.Loaded(listOf(camera))), FakeAppCustomizationRepository(),
            UniversalSearch(DefaultAppSearcher(), { ShortcutLookup.NotDefaultHome }, contacts),
            dispatcher, searchHistory = History(listOf(entry(HistoryTarget.WebQuery("weather"), 1.0))), clock = { now }, hourOf = { 0 },
        )
        backgroundScope.launch { vm.results.collect {} }
        advanceUntilIdle()
        assertThat(vm.results.value.recent).containsExactly(RecentItem.Query("weather"))
        vm.setQuery("cam")
        advanceUntilIdle()
        assertThat(vm.results.value.recent).isEmpty()
        assertThat(vm.results.value.apps).containsExactly(camera)
    }

    @Test
    fun aHabitLearnedForTypedLettersBecomesTheTopResultOfVisibleMatchesOnly() = runTest(dispatcher) {
        val phone = app("Phone")
        val photos = app("Photos")
        val custom = FakeAppCustomizationRepository()
        val learned = HistoryEntry(HistoryTarget.App(photos.key), 3.0, now, now, List(24) { 0.0 }, prefixes = mapOf("p" to 3.0, "ph" to 3.0))
        val vm = SearchViewModel(
            SavedStateHandle(), FakeSettingsRepository(), FakeAppsRepository(AppsState.Loaded(listOf(phone, photos))), custom,
            UniversalSearch(DefaultAppSearcher(), { ShortcutLookup.NotDefaultHome }, contacts),
            dispatcher, searchHistory = History(listOf(learned)), clock = { now }, hourOf = { 0 },
        )
        backgroundScope.launch { vm.results.collect {} }
        vm.setQuery("ph")
        advanceUntilIdle()
        assertThat(vm.results.value.apps).containsExactly(photos, phone).inOrder()
        assertThat(vm.results.value.goRow()).isEqualTo(io.github.aj4abinjacob.chikku.core.model.search.SearchRow.App(photos))

        custom.setHidden(photos.key, true)
        advanceUntilIdle()
        assertThat(vm.results.value.apps).containsExactly(phone)
        assertThat(vm.results.value.learnedTop).isNull()
    }

    @Test
    fun aHabitLearnedBeforeAnAppWasLockedNoLongerPromotesIt() = runTest(dispatcher) {
        val phone = app("Phone")
        val photos = app("Photos")
        val learned = HistoryEntry(HistoryTarget.App(photos.key), 3.0, now, now, List(24) { 0.0 }, prefixes = mapOf("p" to 3.0, "ph" to 3.0))
        val locked = MutableStateFlow<Set<AppKey>>(emptySet())
        val vm = SearchViewModel(
            SavedStateHandle(), FakeSettingsRepository(), FakeAppsRepository(AppsState.Loaded(listOf(phone, photos))), FakeAppCustomizationRepository(),
            UniversalSearch(DefaultAppSearcher(), { ShortcutLookup.NotDefaultHome }, contacts),
            dispatcher, searchHistory = History(listOf(learned)),
            isLocked = { it in locked.value }, lockChanges = locked, clock = { now }, hourOf = { 0 },
        )
        backgroundScope.launch { vm.results.collect {} }
        vm.setQuery("ph")
        advanceUntilIdle()
        assertThat(vm.results.value.learnedTop).isEqualTo(HistoryTarget.App(photos.key))

        locked.value = setOf(photos.key)
        advanceUntilIdle()
        assertThat(vm.results.value.learnedTop).isNull()
        assertThat(vm.results.value.apps).containsExactly(phone, photos).inOrder() // still found by name, not promoted
    }

    @Test
    fun aShortcutPickedMostForTypedLettersBecomesTheTopResult() = runTest(dispatcher) {
        val camera = app("Camera")
        val scanner = app("Scanner")
        val scan = io.github.aj4abinjacob.chikku.core.model.search.ShortcutHit(camera.key.packageName, "scan", camera.key.userSerial, "Scan document", camera.key)
        val target = HistoryTarget.Shortcut(scan.packageName, scan.shortcutId, scan.userSerial)
        val learned = HistoryEntry(target, 3.0, now, now, List(24) { 0.0 }, prefixes = mapOf("s" to 3.0, "sc" to 3.0))
        val vm = SearchViewModel(
            SavedStateHandle(), FakeSettingsRepository(), FakeAppsRepository(AppsState.Loaded(listOf(camera, scanner))), FakeAppCustomizationRepository(),
            UniversalSearch(DefaultAppSearcher(), { ShortcutLookup.Found(listOf(scan)) }, contacts),
            dispatcher, searchHistory = History(listOf(learned)), clock = { now }, hourOf = { 0 },
        )
        backgroundScope.launch { vm.results.collect {} }
        vm.setQuery("sc")
        advanceUntilIdle()
        assertThat(vm.results.value.learnedTop).isEqualTo(target)
        assertThat(vm.results.value.goRow()).isEqualTo(io.github.aj4abinjacob.chikku.core.model.search.SearchRow.Shortcut(scan))
    }
}
