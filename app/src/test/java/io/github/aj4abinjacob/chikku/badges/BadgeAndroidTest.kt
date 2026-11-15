package io.github.aj4abinjacob.chikku.badges

import android.app.Notification
import android.content.pm.PackageManager
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
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = TestHomeApplication::class)
class BadgeAndroidTest {
    private val app: TestHomeApplication = ApplicationProvider.getApplicationContext()
    private val settings = FakeSettingsRepository(LauncherSettings(badges = BadgeSettings(style = BadgeStyle.COUNT)))
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

    private fun notification(title: String? = "Hello", number: Int = 0, ongoing: Boolean = false): Notification =
        Notification.Builder(app, "channel")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .apply { if (title != null) setContentTitle(title) }
            .setNumber(number)
            .setOngoing(ongoing)
            .build()

    // The shadow's (pkg, id, notification) overload posts as UserHandle.ALL, which has no serial; post
    // as this user, as the platform does.
    @Suppress("DEPRECATION")
    private fun sbn(pkg: String, id: Int, notification: Notification) =
        StatusBarNotification(pkg, pkg, id, null, 0, 0, 0, notification, Process.myUserHandle(), id.toLong())

    private fun connectedService(): ChikkuNotificationListener {
        val controller = Robolectric.buildService(ChikkuNotificationListener::class.java).create()
        val service = controller.get()
        service.workDispatcher = Dispatchers.Unconfined
        service.rebuildDebounceMs = 0
        val shadow = shadowOf(service)
        shadow.addActiveNotification(sbn("com.example.mail", 1, notification(number = 3)))
        shadow.addActiveNotification(sbn("com.example.mail", 2, notification()))
        shadow.addActiveNotification(sbn("com.example.music", 3, notification(ongoing = true)))
        shadow.addActiveNotification(sbn("com.example.empty", 4, notification(title = null)))
        service.onListenerConnected()
        return service
    }

    @Test
    fun listenerPublishesCountsPerPackageAndProfile() {
        val service = connectedService()

        assertThat(repository.badges.value)
            .containsExactly(BadgeKey("com.example.mail", mySerial), BadgeInfo(4))

        shadowOf(service).addActiveNotification(sbn("com.example.chat", 5, notification()))
        service.onNotificationPosted(null, null)
        assertThat(repository.badges.value.keys)
            .containsExactly(BadgeKey("com.example.mail", mySerial), BadgeKey("com.example.chat", mySerial))
    }

    @Test
    fun turningBadgesOffClearsDataAndRequestsUnbind() {
        val service = connectedService()
        assertThat(repository.badges.value).isNotEmpty()

        settings.state.value = LauncherSettings(badges = BadgeSettings(style = BadgeStyle.OFF))

        assertThat(repository.badges.value).isEmpty()
        assertThat(shadowOf(service).unbindRequestCount).isEqualTo(1)
        // Later notification events while off publish nothing.
        service.onNotificationPosted(null, null)
        assertThat(repository.badges.value).isEmpty()
    }

    @Test
    fun connectingWhileOffHoldsNothing() {
        settings.state.value = LauncherSettings()
        val service = connectedService()
        assertThat(repository.badges.value).isEmpty()
        assertThat(shadowOf(service).unbindRequestCount).isEqualTo(1)
    }

    @Test
    fun dynamicStyleKeepsScaledAvatarOfLatestNotification() {
        settings.state.value = LauncherSettings(badges = BadgeSettings(style = BadgeStyle.DYNAMIC))
        val service = connectedService()
        val large = android.graphics.Bitmap.createBitmap(200, 100, android.graphics.Bitmap.Config.ARGB_8888)
        val withIcon = Notification.Builder(app, "channel")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Hi")
            .setLargeIcon(large)
            .build()
        shadowOf(service).addActiveNotification(
            @Suppress("DEPRECATION")
            StatusBarNotification("com.example.chat", "com.example.chat", 9, null, 0, 0, 0, withIcon, Process.myUserHandle(), 99L),
        )
        service.onNotificationPosted(null, null)

        val avatar = repository.badges.value.getValue(BadgeKey("com.example.chat", mySerial)).avatar
        val size = app.resources.getDimensionPixelSize(io.github.aj4abinjacob.chikku.R.dimen.badge_avatar_load_size)
        assertThat(avatar).isNotNull()
        assertThat(avatar!!.width).isEqualTo(size)
        assertThat(avatar.height).isEqualTo(size)
        // Packages without an icon fall back to a count (no avatar).
        assertThat(repository.badges.value.getValue(BadgeKey("com.example.mail", mySerial)).avatar).isNull()
    }

    @Test
    fun disconnectClearsBadges() {
        val service = connectedService()
        service.onListenerDisconnected()
        assertThat(repository.badges.value).isEmpty()
    }

    @Test
    fun accessApplyOffClearsAndDisablesListener() = runTest {
        val access = BadgeAccess(app)
        val pm = app.packageManager
        access.apply(BadgeStyle.DOT, repository, Dispatchers.Unconfined)
        assertThat(pm.getComponentEnabledSetting(access.component))
            .isEqualTo(PackageManager.COMPONENT_ENABLED_STATE_ENABLED)

        repository.publish(mapOf(BadgeKey("a", 0) to BadgeInfo(1)))
        access.apply(BadgeStyle.OFF, repository, Dispatchers.Unconfined)
        assertThat(repository.badges.value).isEmpty()
        assertThat(pm.getComponentEnabledSetting(access.component))
            .isEqualTo(PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
    }

    @Test
    fun binderBadgesIconsAndAnnouncesCount() {
        val binder = BadgeBinder(app)
        val view = AppIconView(app, AppIconView.Style.WORKSPACE)
        val key = AppKey("com.example.mail", "Main", mySerial)
        view.contentDescription = "Mail"
        binder.bindApp(view, key)
        assertThat(view.badge).isNull()

        val count = LauncherSettings(badges = BadgeSettings(style = BadgeStyle.COUNT, dotColor = 0xFF00FF00.toInt()))
        binder.update(mapOf(BadgeKey.of(key) to BadgeInfo(3)), count)
        assertThat(view.badge?.count).isEqualTo(3)
        assertThat(view.badge?.color).isEqualTo(0xFF00FF00.toInt())
        assertThat(view.contentDescription.toString()).isEqualTo("Mail, 3 notifications")

        binder.update(mapOf(BadgeKey.of(key) to BadgeInfo(3)), LauncherSettings())
        assertThat(view.badge).isNull()
        assertThat(view.contentDescription.toString()).isEqualTo("Mail")
    }

    @Test
    fun folderGetsAggregateDot() {
        val binder = BadgeBinder(app)
        val folder = AppIconView(app, AppIconView.Style.WORKSPACE)
        folder.contentDescription = "Folder Work"
        val entries = listOf(AppKey("a", "A", 0), AppKey("b", "B", 0))
        binder.bindFolder(folder, entries)
        binder.update(
            mapOf(BadgeKey("b", 0) to BadgeInfo(1)),
            LauncherSettings(badges = BadgeSettings(style = BadgeStyle.COUNT)),
        )
        assertThat(folder.badge?.style).isEqualTo(BadgeStyle.DOT)
        assertThat(folder.contentDescription.toString()).isEqualTo("Folder Work, 1 notification")

        binder.unbind(folder)
        assertThat(folder.badge).isNull()
        assertThat(folder.contentDescription.toString()).isEqualTo("Folder Work")
    }
}
