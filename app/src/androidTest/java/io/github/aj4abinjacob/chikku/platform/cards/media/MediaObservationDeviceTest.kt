package io.github.aj4abinjacob.chikku.platform.cards.media

import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.DeviceTestSupport
import io.github.aj4abinjacob.chikku.core.model.AppCustomization
import io.github.aj4abinjacob.chikku.core.model.AppCustomizationRepository
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.AppsRepository
import io.github.aj4abinjacob.chikku.core.model.AppsState
import io.github.aj4abinjacob.chikku.core.model.LauncherApp
import io.github.aj4abinjacob.chikku.core.model.ProfileType
import io.github.aj4abinjacob.chikku.core.model.cards.AutomationCard
import io.github.aj4abinjacob.chikku.core.model.cards.CardContent
import io.github.aj4abinjacob.chikku.core.model.cards.CardsSettings
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettingsCodec
import io.github.aj4abinjacob.chikku.core.model.settings.SettingsImportResult
import io.github.aj4abinjacob.chikku.core.model.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Native session/permission APIs; fixtures never enable the listener or change saved preferences. */
@RunWith(AndroidJUnit4::class)
class MediaObservationDeviceTest {
    private val context get() = DeviceTestSupport.targetContext
    private val key get() = DeviceTestSupport.settingsKey()
    private class Settings : SettingsRepository {
        val state = MutableStateFlow(LauncherSettings())
        override val settings = state
        override suspend fun update(transform: (LauncherSettings) -> LauncherSettings) { state.value = transform(state.value) }
        override suspend fun reset() { state.value = LauncherSettings() }
        override suspend fun exportJson() = LauncherSettingsCodec.encode(state.value)
        override suspend fun importJson(json: String): SettingsImportResult { state.value = LauncherSettingsCodec.decode(json); return SettingsImportResult.Applied }
    }
    private class Custom : AppCustomizationRepository {
        val state = MutableStateFlow(emptyMap<AppKey, AppCustomization>())
        override val customizations = state
        override suspend fun get(key: AppKey) = state.value[key]
        override suspend fun update(key: AppKey, transform: (AppCustomization) -> AppCustomization): Boolean { state.value += key to transform(state.value[key] ?: AppCustomization(key)); return true }
        override suspend fun clear(key: AppKey): Boolean { state.value -= key; return true }
        override suspend fun all() = state.value.values.toList()
        override suspend fun replaceAll(customizations: List<AppCustomization>): Boolean { state.value = customizations.associateBy { it.key }; return true }
    }
    private fun source(settings: Settings, custom: Custom): AndroidCardMediaSource {
        val apps = object : AppsRepository {
            override val apps = MutableStateFlow<AppsState>(AppsState.Loaded(listOf(LauncherApp(key, "Synthetic audit player", ProfileType.PERSONAL))))
            override fun refresh() = Unit
        }
        return AndroidCardMediaSource(context, apps, custom, settings, Dispatchers.IO)
    }
    private suspend fun enable(settings: Settings) = settings.update {
        it.copy(cards = CardsSettings(listOf(AutomationCard("audit", "Audit", content = CardContent.Media))))
    }

    @Test fun defaultOffAndNativePermissionDenialEmitWithoutCrashing() = runBlocking {
        val settings = Settings()
        val source = source(settings, Custom())
        assertThat(withTimeout(5_000) { source.observe().first() }).isEqualTo(MediaCardState.Empty)
        if (!source.hasAccess()) {
            enable(settings)
            assertThat(withTimeout(5_000) { source.observe().first() }).isEqualTo(MediaCardState.NeedsAccess)
        }
    }

    @Test fun grantedNativeSessionMetadataIsWithheldAfterHidingItsOwner() = runBlocking {
        val settings = Settings(); val custom = Custom(); val source = source(settings, custom)
        assumeTrue("Real session check requires existing user-granted notification access", source.hasAccess())
        enable(settings)
        val session = MediaSession(context, "Chikku reliability fixture")
        try {
            session.setMetadata(MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, "Synthetic audit track").build())
            session.setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_PAUSED, 0, 1f).setActions(PlaybackState.ACTION_PLAY).build())
            session.isActive = true
            val active = withTimeout(10_000) { source.observe().first { it is MediaCardState.Active } } as MediaCardState.Active
            assertThat(active.item.title).isEqualTo("Synthetic audit track")
            custom.setHidden(key, true)
            assertThat(withTimeout(5_000) { source.observe().first() }).isEqualTo(MediaCardState.Empty)
            assertThat(source.command(active.item.sessionId, MediaCardCommand.PLAY)).isFalse()
        } finally { session.release() }
    }
}
