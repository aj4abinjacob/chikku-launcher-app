package io.github.aj4abinjacob.chikku.home.cards

import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.badges.BadgeAccess
import io.github.aj4abinjacob.chikku.badges.BadgeInfo
import io.github.aj4abinjacob.chikku.badges.BadgeKey
import io.github.aj4abinjacob.chikku.badges.BadgeRepository
import io.github.aj4abinjacob.chikku.badges.ChikkuNotificationListener
import io.github.aj4abinjacob.chikku.core.model.cards.AutomationCard
import io.github.aj4abinjacob.chikku.core.model.cards.CardContent
import io.github.aj4abinjacob.chikku.core.model.cards.CardsSettings
import io.github.aj4abinjacob.chikku.core.model.settings.BadgeStyle
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.home.FakeHomeDependencies
import io.github.aj4abinjacob.chikku.home.FakeSettingsRepository
import io.github.aj4abinjacob.chikku.home.HomeDependencies
import io.github.aj4abinjacob.chikku.home.TestHomeApplication
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
class CardMediaAccessIntegrationTest {
    private val app get() = ApplicationProvider.getApplicationContext<TestHomeApplication>()

    @Test fun mediaKeepsListenerEnabledWithoutKeepingBadges() = runTest {
        val access = BadgeAccess(app)
        val badges = BadgeRepository()
        badges.publish(mapOf(BadgeKey("sample", 0) to BadgeInfo(2)))
        access.apply(BadgeStyle.OFF, badges, Dispatchers.Unconfined, mediaEnabled = true)
        assertThat(badges.badges.value).isEmpty()
        assertThat(app.packageManager.getComponentEnabledSetting(access.component))
            .isEqualTo(PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
        access.apply(BadgeStyle.OFF, badges, Dispatchers.Unconfined, mediaEnabled = false)
        assertThat(app.packageManager.getComponentEnabledSetting(access.component))
            .isEqualTo(PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
    }

    @Test fun listenerStaysBoundForMediaThenUnbindsWhenLastFeatureIsDisabled() {
        val settings = FakeSettingsRepository(LauncherSettings(cards = CardsSettings(listOf(
            AutomationCard("media", "Media", content = CardContent.Media)))))
        val badges = BadgeRepository()
        app.homeDependencies = object : HomeDependencies by FakeHomeDependencies(settingsRepository = settings) {
            override val badgeRepository = badges
        }
        val controller = Robolectric.buildService(ChikkuNotificationListener::class.java).create()
        val service = controller.get()
        service.workDispatcher = Dispatchers.Unconfined
        try {
            service.onListenerConnected()
            assertThat(shadowOf(service).unbindRequestCount).isEqualTo(0)
            service.onNotificationPosted(null, null)
            assertThat(badges.badges.value).isEmpty()
            settings.state.value = LauncherSettings()
            assertThat(shadowOf(service).unbindRequestCount).isEqualTo(1)
        } finally { controller.destroy() }
    }
}
