package io.github.aj4abinjacob.chikku.badges

import android.app.Notification
import android.graphics.Bitmap
import android.os.Process
import android.os.UserManager
import android.service.notification.StatusBarNotification
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.settings.BadgeSettings
import io.github.aj4abinjacob.chikku.core.model.settings.BadgeStyle
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.home.AppIconView
import io.github.aj4abinjacob.chikku.home.FakeHomeDependencies
import io.github.aj4abinjacob.chikku.home.FakeSettingsRepository
import io.github.aj4abinjacob.chikku.home.HomeDependencies
import io.github.aj4abinjacob.chikku.home.TestHomeApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = TestHomeApplication::class)
/**
 * Rebuild coalescing and avatar reuse on the notification listener. Badge equality and the per-view
 * skip are covered by [BadgeMemoryTest].
 */
class BadgeDedupTest {
    private val app: TestHomeApplication = ApplicationProvider.getApplicationContext()
    private val settings = FakeSettingsRepository(LauncherSettings(badges = BadgeSettings(style = BadgeStyle.DYNAMIC)))
    private val repository = BadgeRepository()
    private val mySerial: Long
        get() = app.getSystemService(UserManager::class.java).getSerialNumberForUser(Process.myUserHandle())

    @Before
    fun setUp() {
        val fake = FakeHomeDependencies(settingsRepository = settings)
        app.homeDependencies = object : HomeDependencies by fake {
            override val badgeRepository: BadgeRepository = repository
        }
    }

    private fun bitmap() = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)

    @Suppress("DEPRECATION")
    private fun sbn(pkg: String, id: Int, postTime: Long, notification: Notification) =
        StatusBarNotification(pkg, pkg, id, null, 0, 0, 0, notification, Process.myUserHandle(), postTime)

    private fun plain(title: String) = Notification.Builder(app, "channel")
        .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).build()

    private fun withIcon() = Notification.Builder(app, "channel")
        .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Hi").setLargeIcon(bitmap()).build()

    @Test
    fun rebuildReusesDecodedAvatarWhileLatestNotificationIsUnchanged() {
        val service = Robolectric.buildService(ChikkuNotificationListener::class.java).create().get()
        service.workDispatcher = Dispatchers.Unconfined
        service.rebuildDebounceMs = 0
        val shadow = shadowOf(service)
        shadow.addActiveNotification(sbn("com.example.chat", 1, 100L, withIcon()))
        service.onListenerConnected()
        val key = BadgeKey("com.example.chat", mySerial)
        val first = repository.badges.value.getValue(key)
        assertThat(first.avatar).isNotNull()

        // An unrelated event: nothing changed, so the same avatar instance is published again.
        service.onNotificationRankingUpdate(null)
        val second = repository.badges.value.getValue(key)
        assertThat(second.avatar).isSameInstanceAs(first.avatar)
        assertThat(second).isEqualTo(first)

        // A newer notification from the same app is decoded afresh.
        shadow.addActiveNotification(sbn("com.example.chat", 2, 200L, withIcon()))
        service.onNotificationPosted(null, null)
        val third = repository.badges.value.getValue(key)
        assertThat(third.count).isEqualTo(2)
        assertThat(third.avatar).isNotSameInstanceAs(first.avatar)
    }

    @Test
    fun eventBurstIsCoalescedIntoOneTrailingRebuild() {
        settings.state.value = LauncherSettings(badges = BadgeSettings(style = BadgeStyle.COUNT))
        val scheduler = TestCoroutineScheduler()
        val service = Robolectric.buildService(ChikkuNotificationListener::class.java).create().get()
        service.workDispatcher = StandardTestDispatcher(scheduler)
        service.rebuildDebounceMs = 150
        val shadow = shadowOf(service)
        service.onListenerConnected()
        scheduler.advanceTimeBy(200) // the connect-time rebuild finishes
        scheduler.runCurrent()
        assertThat(repository.badges.value).isEmpty()

        shadow.addActiveNotification(sbn("com.example.mail", 1, 1L, plain("a")))
        service.onNotificationPosted(null, null)
        scheduler.advanceTimeBy(100)
        scheduler.runCurrent()
        // Still inside the window: nothing published yet.
        assertThat(repository.badges.value).isEmpty()

        shadow.addActiveNotification(sbn("com.example.chat", 2, 2L, plain("b")))
        service.onNotificationPosted(null, null)
        scheduler.advanceTimeBy(100) // window (150 ms from the first event) is over
        scheduler.runCurrent()
        assertThat(repository.badges.value.keys).containsExactly(
            BadgeKey("com.example.mail", mySerial), BadgeKey("com.example.chat", mySerial),
        )
    }

}
