package io.github.aj4abinjacob.chikku.badges

import android.app.Notification
import android.graphics.Bitmap
import android.graphics.Color
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Badge rebuilds reuse avatars and leave unchanged views alone. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = TestHomeApplication::class)
class BadgeMemoryTest {
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

    private fun bitmap(color: Int) = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

    @Suppress("DEPRECATION")
    private fun sbn(pkg: String, id: Int, postTime: Long, large: Bitmap? = null): StatusBarNotification {
        val notification = Notification.Builder(app, "channel")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Hi")
            .apply { if (large != null) setLargeIcon(large) }
            .build()
        return StatusBarNotification(pkg, pkg, id, null, 0, 0, 0, notification, Process.myUserHandle(), postTime)
    }

    @Test
    fun badgeInfoComparesAvatarPixelsNotIdentity() {
        assertThat(BadgeInfo(2, bitmap(Color.RED))).isEqualTo(BadgeInfo(2, bitmap(Color.RED)))
        assertThat(BadgeInfo(2, bitmap(Color.RED)).hashCode()).isEqualTo(BadgeInfo(2, bitmap(Color.RED)).hashCode())
        assertThat(BadgeInfo(2, bitmap(Color.RED))).isNotEqualTo(BadgeInfo(2, bitmap(Color.BLUE)))
        assertThat(BadgeInfo(2, bitmap(Color.RED))).isNotEqualTo(BadgeInfo(3, bitmap(Color.RED)))
        assertThat(BadgeInfo(2, bitmap(Color.RED))).isNotEqualTo(BadgeInfo(2))
        assertThat(BadgeInfo(2)).isEqualTo(BadgeInfo(2))
    }

    @Test
    fun avatarCacheIsBoundedAndRetainsOnlyShownNotifications() {
        val cache = BadgeAvatarCache(maxEntries = 2)
        var loads = 0
        val load = { loads++; bitmap(Color.RED) }
        val first = cache.getOrLoad(BadgeAvatarCache.Key("a", 1), load)
        assertThat(cache.getOrLoad(BadgeAvatarCache.Key("a", 1), load)).isSameInstanceAs(first)
        assertThat(loads).isEqualTo(1)
        // A repost (new revision) loads again; failures are cached too.
        cache.getOrLoad(BadgeAvatarCache.Key("a", 2), load)
        cache.getOrLoad(BadgeAvatarCache.Key("b", 1)) { loads++; null }
        assertThat(cache.getOrLoad(BadgeAvatarCache.Key("b", 1)) { loads++; null }).isNull()
        assertThat(loads).isEqualTo(3)
        assertThat(cache.size()).isEqualTo(2) // "a" revision 2 replaced revision 1
        cache.getOrLoad(BadgeAvatarCache.Key("c", 1), load)
        assertThat(cache.size()).isEqualTo(2) // bounded: the eldest entry went
        cache.retainOnly(setOf("c"))
        assertThat(cache.size()).isEqualTo(1)
        cache.clear()
        assertThat(cache.size()).isEqualTo(0)
    }

    @Test
    fun rankingUpdateRebuildsOnlyWhenBadgeEligibilityChanges() {
        val previous = mapOf("k1" to true, "k2" to false)
        assertThat(rankingAffectsBadges(previous) { previous[it] }).isFalse()
        assertThat(rankingAffectsBadges(previous) { null }).isFalse()
        assertThat(rankingAffectsBadges(previous) { if (it == "k2") true else previous[it] }).isTrue()
        assertThat(rankingAffectsBadges(emptyMap()) { true }).isFalse()
    }

    @Test
    fun unrelatedNotificationEventReusesAvatarBitmapAndRepostReloadsIt() {
        val controller = Robolectric.buildService(ChikkuNotificationListener::class.java).create()
        val service = controller.get()
        service.workDispatcher = Dispatchers.Unconfined
        service.rebuildDebounceMs = 0
        val shadow = shadowOf(service)
        val chat = sbn("com.example.chat", 1, 10L, Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888))
        shadow.addActiveNotification(chat)
        service.onListenerConnected()
        val key = BadgeKey("com.example.chat", mySerial)
        val avatar = repository.badges.value.getValue(key).avatar
        assertThat(avatar).isNotNull()

        shadow.addActiveNotification(sbn("com.example.mail", 2, 20L))
        service.onNotificationPosted(null, null)
        assertThat(repository.badges.value.getValue(key).avatar).isSameInstanceAs(avatar)

        service.cancelAllNotifications() // the shadow clears its active list
        shadow.addActiveNotification(sbn("com.example.chat", 1, 30L, Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888)))
        service.onNotificationPosted(null, null)
        assertThat(repository.badges.value.getValue(key).avatar).isNotSameInstanceAs(avatar)
        assertThat(service.avatarCache.size()).isEqualTo(1)

        // A ranking update with no ranking data does not rebuild.
        val rebuilds = service.rebuildCount
        service.onNotificationRankingUpdate(null)
        assertThat(service.rebuildCount).isEqualTo(rebuilds)

        service.onListenerDisconnected()
        assertThat(service.avatarCache.size()).isEqualTo(0)
    }

    @Test
    fun binderLeavesViewsWithUnchangedBadgesAlone() {
        val binder = BadgeBinder(app)
        val mail = AppKey("com.example.mail", "Main", 0)
        val chat = AppKey("com.example.chat", "Main", 0)
        val mailView = AppIconView(app, AppIconView.Style.WORKSPACE).apply { contentDescription = "Mail" }
        val chatView = AppIconView(app, AppIconView.Style.WORKSPACE).apply { contentDescription = "Chat" }
        binder.bindApp(mailView, mail)
        binder.bindApp(chatView, chat)
        val count = LauncherSettings(badges = BadgeSettings(style = BadgeStyle.COUNT))
        binder.update(mapOf(BadgeKey.of(mail) to BadgeInfo(1), BadgeKey.of(chat) to BadgeInfo(2, bitmap(Color.RED))), count)
        val chatOverlay = chatView.badge
        val mailOverlay = mailView.badge

        // New map, new (equal) avatar bitmap: only Mail changed.
        binder.update(mapOf(BadgeKey.of(mail) to BadgeInfo(5), BadgeKey.of(chat) to BadgeInfo(2, bitmap(Color.RED))), count)
        assertThat(chatView.badge).isSameInstanceAs(chatOverlay)
        assertThat(mailView.badge).isNotSameInstanceAs(mailOverlay)
        assertThat(mailView.badge?.count).isEqualTo(5)
        assertThat(mailView.contentDescription.toString()).isEqualTo("Mail, 5 notifications")

        // A settings change still redraws every view.
        binder.update(mapOf(BadgeKey.of(mail) to BadgeInfo(5), BadgeKey.of(chat) to BadgeInfo(2)), count.copy(badges = count.badges.copy(dotColor = Color.GREEN)))
        assertThat(chatView.badge).isNotSameInstanceAs(chatOverlay)
        assertThat(chatView.badge?.color).isEqualTo(Color.GREEN)
    }
}
