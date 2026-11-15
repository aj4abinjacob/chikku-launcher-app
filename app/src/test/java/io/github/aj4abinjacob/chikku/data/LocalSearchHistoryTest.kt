package io.github.aj4abinjacob.chikku.data

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.AppsState
import io.github.aj4abinjacob.chikku.core.model.ProfileType
import io.github.aj4abinjacob.chikku.core.model.search.HistoryTarget
import io.github.aj4abinjacob.chikku.home.FakeAppCustomizationRepository
import io.github.aj4abinjacob.chikku.home.FakeAppsRepository
import io.github.aj4abinjacob.chikku.home.FakeSettingsRepository
import io.github.aj4abinjacob.chikku.home.TestHomeApplication
import io.github.aj4abinjacob.chikku.home.app
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestHomeApplication::class)
@OptIn(ExperimentalCoroutinesApi::class)
class LocalSearchHistoryTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val camera = app("Camera")
    private val music = app("Music")
    private val bank = app("Bank")
    private val work = app("Work", profile = ProfileType.WORK)
    private val settings = FakeSettingsRepository()
    private val apps = FakeAppsRepository(AppsState.Loaded(listOf(camera, music, bank, work)))
    private val custom = FakeAppCustomizationRepository()
    private val now = 1_800_000_000_000L

    private fun TestScope.store(scope: CoroutineScope, prefs: String) = LocalSearchHistoryRepository(
        context, settings, apps, custom, isLocked = { pkg, _ -> pkg == bank.key.packageName },
        scope = scope, io = StandardTestDispatcher(testScheduler), clock = { now }, hourOf = { 9 },
        prefsName = prefs, legacyPrefsName = "$prefs-legacy",
    )

    @Test
    fun learningIsOptInAndOnlyVisiblePersonalUnlockedTargetsAreRecorded() = runTest {
        val history = store(backgroundScope, "history-privacy")
        runCurrent()
        history.record(HistoryTarget.App(camera.key))
        assertThat(history.entries.value).isEmpty() // default off

        settings.update { it.copy(search = it.search.copy(historyLearningEnabled = true)) }
        custom.setHidden(music.key, true)
        runCurrent()
        for (target in listOf(
            HistoryTarget.App(camera.key),
            HistoryTarget.App(music.key), // hidden
            HistoryTarget.App(bank.key), // app-locked
            HistoryTarget.App(work.key), // other profile
            HistoryTarget.Shortcut(bank.key.packageName, "pay", 0), // shortcut of a locked app
            HistoryTarget.WebQuery("123456"), // looks like a number or code
            HistoryTarget.WebQuery("pizza"),
            HistoryTarget.Contact("lookup-1"),
        )) history.record(target)
        assertThat(history.entries.value.map { it.target }).containsExactly(
            HistoryTarget.Contact("lookup-1"), HistoryTarget.WebQuery("pizza"), HistoryTarget.App(camera.key),
        )
        assertThat(history.entries.value.first { it.target == HistoryTarget.App(camera.key) }.hours[9]).isEqualTo(1.0)
    }

    @Test
    fun typedPrefixesAreLearnedUnderTheSameRulesAndSurviveRestarts() = runTest {
        val history = store(backgroundScope, "history-prefixes")
        runCurrent()
        history.learn(HistoryTarget.App(camera.key), "cam")
        assertThat(history.entries.value).isEmpty() // learning is opt-in

        settings.update { it.copy(search = it.search.copy(historyLearningEnabled = true)) }
        runCurrent()
        history.learn(HistoryTarget.App(camera.key), "Camera")
        history.learn(HistoryTarget.App(bank.key), "ba") // app-locked: never learned
        history.learn(HistoryTarget.App(work.key), "wo") // other profile: never learned
        history.learn(HistoryTarget.Contact("lookup-1"), "4821") // digits only: never learned
        assertThat(history.entries.value.map { it.target }).containsExactly(HistoryTarget.App(camera.key))
        assertThat(history.entries.value.single().prefixes.keys).containsExactly("c", "ca", "cam", "came")

        val restarted = store(backgroundScope, "history-prefixes")
        runCurrent()
        assertThat(restarted.entries.value.single().prefixes).isEqualTo(history.entries.value.single().prefixes)
        restarted.forget(HistoryTarget.App(camera.key))
        assertThat(restarted.entries.value).isEmpty()
    }

    @Test
    fun pausingKeepsHistoryAcrossRestartsAndForgetAndClearRemoveIt() = runTest {
        settings.update { it.copy(search = it.search.copy(historyLearningEnabled = true)) }
        val first = store(backgroundScope, "history-pause")
        runCurrent()
        first.record(HistoryTarget.App(camera.key))
        first.record(HistoryTarget.WebQuery("weather"))
        settings.update { it.copy(search = it.search.copy(historyLearningEnabled = false)) }
        runCurrent()
        first.record(HistoryTarget.App(music.key))
        assertThat(first.entries.value).hasSize(2) // paused: kept, not growing

        val restarted = store(backgroundScope, "history-pause")
        runCurrent()
        assertThat(restarted.entries.value.map { it.target }).containsExactly(HistoryTarget.WebQuery("weather"), HistoryTarget.App(camera.key))
        restarted.forget(HistoryTarget.WebQuery("weather"))
        assertThat(restarted.entries.value.map { it.target }).containsExactly(HistoryTarget.App(camera.key))
        restarted.clear()
        val cleared = store(backgroundScope, "history-pause")
        runCurrent()
        assertThat(cleared.entries.value).isEmpty()
    }

    @Test
    fun frequentAppCountsMigrateOnceAndCorruptHistoryStartsEmpty() = runTest {
        context.getSharedPreferences("history-migrate-legacy", Context.MODE_PRIVATE).edit()
            .putString("counts", """[{"package":"${camera.key.packageName}","component":"${camera.key.className}","serial":0,"count":500},{"bad":1}]""")
            .commit()
        val migrated = store(backgroundScope, "history-migrate")
        runCurrent()
        assertThat(migrated.entries.value.map { it.target }).containsExactly(HistoryTarget.App(camera.key))
        assertThat(migrated.entries.value.single().score).isEqualTo(20.0)
        assertThat(context.getSharedPreferences("history-migrate-legacy", Context.MODE_PRIVATE).getString("counts", null)).isNull()

        context.getSharedPreferences("history-corrupt", Context.MODE_PRIVATE).edit().putString("entries", "{not json").commit()
        val corrupt = store(backgroundScope, "history-corrupt")
        runCurrent()
        assertThat(corrupt.entries.value).isEmpty()
    }
}
