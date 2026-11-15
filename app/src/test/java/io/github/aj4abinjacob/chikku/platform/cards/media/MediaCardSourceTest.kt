package io.github.aj4abinjacob.chikku.platform.cards.media

import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.AppCustomization
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.AppsState
import io.github.aj4abinjacob.chikku.core.model.LauncherApp
import io.github.aj4abinjacob.chikku.core.model.ProfileType
import io.github.aj4abinjacob.chikku.core.model.cards.AutomationCard
import io.github.aj4abinjacob.chikku.core.model.cards.CardContent
import io.github.aj4abinjacob.chikku.core.model.cards.CardsSettings
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.home.FakeAppsRepository
import io.github.aj4abinjacob.chikku.home.FakeAppCustomizationRepository
import io.github.aj4abinjacob.chikku.home.FakeSettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MediaCardSourceTest {
    private val key = AppKey("synthetic.player", "Player", 0)
    private val personal = LauncherApp(key, "Player", ProfileType.PERSONAL)
    private val preferences = LauncherSettings(cards = CardsSettings(listOf(AutomationCard("media", "Media", content = CardContent.Media))))
    private class Session(override val id: String = "session", override val packageName: String = "synthetic.player") : MediaSessionHandle {
        var reads = 0
        var commands = 0
        var title = "Synthetic song"
        var duringRead: () -> Unit = {}
        val callbacks = mutableListOf<() -> Unit>()
        override fun details(): MediaDetails { reads++; duringRead(); return MediaDetails(title, null, false, setOf(MediaCardCommand.PLAY)) }
        override fun watch(changed: () -> Unit): AutoCloseable { callbacks += changed; return AutoCloseable { callbacks -= changed } }
        override fun execute(command: MediaCardCommand): Boolean { commands++; return true }
    }
    private class Platform(val session: Session = Session()) : MediaCardPlatform {
        var access = true
        var prepared = 0
        var restored: Boolean? = null
        var failRollback = false
        var afterPrepare: () -> Unit = {}
        var onAccess: () -> Unit = {}
        val callbacks = mutableListOf<() -> Unit>()
        override fun personalSerial() = 0L
        override fun hasAccess(): Boolean { onAccess(); return access }
        override fun prepareAccess(): Boolean { prepared++; afterPrepare(); return true }
        override fun restoreListenerPolicy(enabled: Boolean) { if (failRollback) throw IllegalStateException("synthetic failure"); restored = enabled }
        override fun sessions() = listOf(session)
        override fun watchSessions(changed: () -> Unit): AutoCloseable { callbacks += changed; return AutoCloseable { callbacks -= changed } }
    }

    @Test fun workAndHiddenOwnersNeverReadMetadataOrReceiveCommands() = runTest {
        for (blocked in listOf("work", "hidden")) {
            val apps = FakeAppsRepository(AppsState.Loaded(listOf(if (blocked == "work") personal.copy(profileType = ProfileType.WORK) else personal)))
            val custom = FakeAppCustomizationRepository()
            if (blocked == "hidden") custom.state.value = mapOf(key to AppCustomization(key, hidden = true))
            val platform = Platform()
            val source = AndroidCardMediaSource(platform, apps, custom, FakeSettingsRepository(preferences), StandardTestDispatcher(testScheduler))
            assertThat(source.observe().first()).isEqualTo(MediaCardState.Empty)
            assertThat(source.command("session", MediaCardCommand.PLAY)).isFalse()
            assertThat(platform.session.reads).isEqualTo(0)
            assertThat(platform.session.commands).isEqualTo(0)
            assertThat(platform.session.callbacks).isEmpty()
            assertThat(platform.callbacks).isEmpty()
        }
    }

    @Test fun hideDuringMetadataReadWithholdsEveryDetail() = runTest {
        val custom = FakeAppCustomizationRepository()
        val platform = Platform()
        platform.session.duringRead = { custom.state.value = mapOf(key to AppCustomization(key, hidden = true)) }
        val source = AndroidCardMediaSource(platform, FakeAppsRepository(AppsState.Loaded(listOf(personal))), custom,
            FakeSettingsRepository(preferences), StandardTestDispatcher(testScheduler))
        assertThat(source.observe().first()).isEqualTo(MediaCardState.Empty)
        assertThat(platform.session.callbacks).isEmpty()
    }

    @Test fun multipleVisibleCardsHaveIndependentCallbacksAndCancelCleanly() = runTest {
        val platform = Platform()
        val source = AndroidCardMediaSource(platform, FakeAppsRepository(AppsState.Loaded(listOf(personal))),
            FakeAppCustomizationRepository(), FakeSettingsRepository(preferences), StandardTestDispatcher(testScheduler))
        val first = mutableListOf<MediaCardState>()
        val second = mutableListOf<MediaCardState>()
        val one = backgroundScope.launch { source.observe().collect { first += it } }
        val two = backgroundScope.launch { source.observe().collect { second += it } }
        runCurrent()
        assertThat(platform.session.callbacks).hasSize(2)
        platform.session.title = "Changed song"
        platform.session.callbacks.toList().forEach { it() }
        runCurrent()
        assertThat((first.last() as MediaCardState.Active).item.title).isEqualTo("Changed song")
        assertThat((second.last() as MediaCardState.Active).item.title).isEqualTo("Changed song")
        one.cancelAndJoin()
        assertThat(platform.session.callbacks).hasSize(1)
        two.cancelAndJoin()
        assertThat(platform.callbacks).isEmpty()
        assertThat(platform.session.callbacks).isEmpty()
    }

    @Test fun unsavedOrDisabledMediaCannotEnableListenerAndDeletionRaceRestoresPolicy() = runTest {
        val settings = FakeSettingsRepository()
        val platform = Platform()
        val source = AndroidCardMediaSource(platform, FakeAppsRepository(AppsState.Loaded(listOf(personal))),
            FakeAppCustomizationRepository(), settings, StandardTestDispatcher(testScheduler))
        assertThat(source.prepareAccess()).isFalse()
        assertThat(platform.prepared).isEqualTo(0)
        settings.state.value = preferences.copy(cards = CardsSettings(preferences.cards.cards.map { it.copy(enabled = false) }))
        assertThat(source.prepareAccess()).isFalse()
        settings.state.value = preferences
        platform.afterPrepare = { settings.state.value = LauncherSettings() }
        assertThat(source.prepareAccess()).isFalse()
        assertThat(platform.restored).isFalse()
        settings.state.value = preferences; platform.failRollback = true
        assertThat(source.prepareAccess()).isFalse()
    }

    @Test fun commandResolvesFreshSessionAndRejectsHideAndRevokedAccess() = runTest {
        val custom = FakeAppCustomizationRepository()
        val platform = Platform()
        val source = AndroidCardMediaSource(platform, FakeAppsRepository(AppsState.Loaded(listOf(personal))), custom,
            FakeSettingsRepository(preferences), StandardTestDispatcher(testScheduler))
        assertThat(source.command("stale", MediaCardCommand.PLAY)).isFalse()
        assertThat(source.command("session", MediaCardCommand.PLAY)).isTrue()
        custom.state.value = mapOf(key to AppCustomization(key, hidden = true))
        assertThat(source.command("session", MediaCardCommand.PLAY)).isFalse()
        custom.state.value = emptyMap(); platform.access = false
        assertThat(source.command("session", MediaCardCommand.PLAY)).isFalse()
        assertThat(platform.session.commands).isEqualTo(1)
    }

    @Test fun cancellationDuringFinalNativeAccessCheckPreventsTransport() = runTest {
        val platform = Platform()
        val source = AndroidCardMediaSource(platform, FakeAppsRepository(AppsState.Loaded(listOf(personal))),
            FakeAppCustomizationRepository(), FakeSettingsRepository(preferences), StandardTestDispatcher(testScheduler))
        var accesses = 0
        var command: Job? = null
        platform.onAccess = { accesses++; if (accesses == 2) command?.cancel() }
        command = async { source.command("session", MediaCardCommand.PLAY) }
        runCurrent()
        assertThat(command?.isCancelled).isTrue()
        assertThat(platform.session.commands).isEqualTo(0)
    }
}
