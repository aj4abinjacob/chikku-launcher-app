package io.github.aj4abinjacob.chikku.badges

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Process
import android.os.UserManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.settings.BadgeStyle
import io.github.aj4abinjacob.chikku.home.HomeDependenciesProvider
import io.github.aj4abinjacob.chikku.platform.UserTypeInfo
import io.github.aj4abinjacob.chikku.platform.classifyProfile
import io.github.aj4abinjacob.chikku.platform.profileChangeActions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlin.math.max

/**
 * Shared optional notification listener. Badge styles read only counts/small avatars into memory;
 * media-only access leaves badge data empty and lets MediaSessionManager use this component.
 * Disabled/unbound when neither saved media cards nor badges need it. Never stores, logs, forwards,
 * cancels or changes notifications.
 */
class ChikkuNotificationListener : NotificationListenerService() {

    /** Where rebuilds run; tests swap in an immediate dispatcher. */
    @VisibleForTesting
    internal var workDispatcher: CoroutineDispatcher = Dispatchers.Default

    /**
     * Quiet period after the first notification event before badges are rebuilt (trailing edge: the
     * rebuild reads the state after the window, so the final state is always published). A burst of
     * posts/removals/ranking updates therefore costs one rebuild. 0 disables it (tests).
     */
    @VisibleForTesting
    internal var rebuildDebounceMs: Long = REBUILD_DEBOUNCE_MS

    private var scope: CoroutineScope? = null
    private var rebuildRequests: Channel<Unit>? = null
    private var profileReceiver: BroadcastReceiver? = null

    @Volatile
    private var style: BadgeStyle = BadgeStyle.OFF

    /** Avatars reused across rebuilds (dynamic style only). */
    @VisibleForTesting
    internal val avatarCache = BadgeAvatarCache()

    /** `canShowBadge()` per notification key as of the last rebuild, to filter ranking updates. */
    @Volatile
    private var lastBadgeRanking: Map<String, Boolean> = emptyMap()

    /** Rebuilds run so far; for tests. */
    @VisibleForTesting
    @Volatile
    internal var rebuildCount = 0
        private set

    override fun onListenerConnected() {
        super.onListenerConnected()
        val deps = (applicationContext as? HomeDependenciesProvider)?.homeDependencies
        val repository = deps?.badgeRepository
        if (deps == null || repository == null) {
            requestUnbindSafely()
            return
        }
        scope?.cancel()
        repository.connect()
        val newScope = CoroutineScope(SupervisorJob() + workDispatcher)
        val requests = Channel<Unit>(Channel.CONFLATED)
        scope = newScope
        rebuildRequests = requests

        newScope.launch {
            deps.settingsRepository.settings.map { settings -> settings.badges.style to
                settings.cards.cards.any { it.enabled && it.content == io.github.aj4abinjacob.chikku.core.model.cards.CardContent.Media }
            }.distinctUntilChanged().collect { (newStyle, mediaEnabled) ->
                repository.clear()
                style = newStyle
                if (newStyle != BadgeStyle.DYNAMIC) avatarCache.clear()
                if (newStyle == BadgeStyle.OFF) {
                    repository.clear()
                    if (!mediaEnabled) requestUnbindSafely()
                } else {
                    requests.trySend(Unit)
                }
            }
        }
        newScope.launch {
            for (ignored in requests) {
                if (style == BadgeStyle.OFF) continue
                if (rebuildDebounceMs > 0) {
                    delay(rebuildDebounceMs)
                    // Events that arrived during the window are covered by the read below.
                    requests.tryReceive()
                    if (style == BadgeStyle.OFF) continue
                }
                // Taken after the window: a clear() (disconnect, style or profile change) during it
                // still invalidates this read through the revision check on publish.
                val revision = repository.revision()
                rebuildCount++
                val badges = try { readBadges() }
                    catch (e: CancellationException) { throw e }
                    catch (_: RuntimeException) { emptyMap() }
                coroutineContext.ensureActive()
                if (style != BadgeStyle.OFF) repository.publishIfCurrent(revision, badges)
            }
        }
        registerProfileReceiver()
    }

    override fun onListenerDisconnected() {
        stop()
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        stop()
        super.onDestroy()
    }

    // The system calls the two-argument variants; the one-argument defaults are never reached.
    override fun onNotificationPosted(sbn: StatusBarNotification?, rankingMap: RankingMap?) = requestRebuild()

    override fun onNotificationRemoved(sbn: StatusBarNotification?, rankingMap: RankingMap?) = requestRebuild()

    /** Only a change to whether a known notification may badge needs a rebuild. */
    override fun onNotificationRankingUpdate(rankingMap: RankingMap?) {
        val previous = lastBadgeRanking
        if (rankingMap == null || previous.isEmpty()) return
        val launched = scope?.launch {
            val ranking = Ranking()
            val changed = try {
                rankingAffectsBadges(previous) { key -> if (rankingMap.getRanking(key, ranking)) ranking.canShowBadge() else null }
            } catch (_: RuntimeException) {
                true // unreadable ranking: rebuild rather than risk a stale badge
            }
            if (changed) requestRebuild()
        }
        if (launched == null) requestRebuild()
    }

    private fun requestRebuild() {
        rebuildRequests?.trySend(Unit)
    }

    private companion object {
        /** Trailing-edge coalescing window for notification events. */
        const val REBUILD_DEBOUNCE_MS = 150L
    }

    private fun stop() {
        style = BadgeStyle.OFF
        avatarCache.clear()
        lastBadgeRanking = emptyMap()
        scope?.cancel()
        scope = null
        rebuildRequests?.close()
        rebuildRequests = null
        profileReceiver?.let { runCatching { unregisterReceiver(it) } }
        profileReceiver = null
        // Hold nothing once we are not listening: stale badges would be wrong and are private data.
        (applicationContext as? HomeDependenciesProvider)?.homeDependencies?.badgeRepository?.disconnect()
    }

    private fun requestUnbindSafely() {
        try {
            requestUnbind()
        } catch (_: RuntimeException) {
            // Not connected (anymore); nothing to unbind.
        }
    }

    /** Paused, locked or private profiles change visibility; rebuild so their badges appear or vanish. */
    private fun registerProfileReceiver() {
        if (profileReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                (applicationContext as? HomeDependenciesProvider)?.homeDependencies?.badgeRepository?.clear()
                avatarCache.clear()
                requestRebuild()
            }
        }
        val filter = IntentFilter().apply { profileChangeActions(Build.VERSION.SDK_INT).forEach(::addAction) }
        // Protected system broadcasts only (see AndroidAppsPlatformSource.observe).
        try {
            ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
            profileReceiver = receiver
        } catch (_: RuntimeException) {
            // Without profile events badges still refresh on the next notification change.
        }
    }

    private fun readBadges(): Map<BadgeKey, BadgeInfo> {
        val active: Array<StatusBarNotification> = try {
            activeNotifications ?: emptyArray()
        } catch (e: CancellationException) {
            throw e
        } catch (_: RuntimeException) {
            return emptyMap() // listener not connected (SecurityException) or system hiccup
        }
        val ranking = try {
            currentRanking
        } catch (_: RuntimeException) {
            null
        }
        val userManager = getSystemService(UserManager::class.java) ?: return emptyMap()
        val visibility = ProfileVisibility(this, userManager)
        val candidates = active.mapNotNull { sbn -> candidateOf(sbn, ranking, userManager) }
        lastBadgeRanking = candidates.associate { it.ref.key to it.canShowBadge }
        val aggregates = BadgeAggregator.aggregate(candidates, visibility::isVisible)
        val dynamic = style == BadgeStyle.DYNAMIC
        val avatarSize = resources.getDimensionPixelSize(R.dimen.badge_avatar_load_size)
        val result = aggregates.mapValues { (_, aggregate) ->
            BadgeInfo(aggregate.count, if (dynamic) cachedAvatarOf(aggregate.latest, avatarSize) else null)
        }.filterKeys { visibility.isVisible(it.userSerial) }
        // Keep only avatars still shown, so removed or now-hidden notifications release theirs.
        avatarCache.retainOnly(if (dynamic) aggregates.filterKeys { it in result }.values.mapTo(HashSet()) { it.latest.key } else emptySet())
        return result
    }

    private fun cachedAvatarOf(sbn: StatusBarNotification, size: Int): Bitmap? {
        val key = sbn.key ?: return avatarOf(sbn, size)
        return avatarCache.getOrLoad(BadgeAvatarCache.Key(key, avatarRevision(sbn, size))) { avatarOf(sbn, size) }
    }

    /**
     * Changes whenever the notification is posted again (the platform stamps a new post time on
     * every post or update), or its large icon or badge preference changes.
     */
    private fun avatarRevision(sbn: StatusBarNotification, size: Int): Long {
        val notification = sbn.notification
        var hash = sbn.postTime
        hash = 31 * hash + size
        hash = 31 * hash + (notification?.badgeIconType ?: 0)
        hash = 31 * hash + iconSignature(notification?.getLargeIcon())
        return hash
    }

    private fun candidateOf(
        sbn: StatusBarNotification,
        ranking: RankingMap?,
        userManager: UserManager,
    ): BadgeCandidate<StatusBarNotification>? {
        val notification = sbn.notification ?: return null
        val serial = userManager.getSerialNumberForUser(sbn.user)
        if (serial < 0) return null
        val extras = notification.extras
        // Only emptiness is checked; the text itself is never kept.
        val hasContent = extras != null && (
            !extras.getCharSequence(Notification.EXTRA_TITLE).isNullOrEmpty() ||
                !extras.getCharSequence(Notification.EXTRA_TEXT).isNullOrEmpty()
            )
        val canShowBadge = ranking?.let { map ->
            val r = Ranking()
            // No ranking for this key (e.g. shadowed in tests): do not suppress the badge.
            if (map.getRanking(sbn.key, r)) r.canShowBadge() else true
        } ?: true
        return BadgeCandidate(
            key = BadgeKey(sbn.packageName, serial),
            number = notification.number,
            ongoing = sbn.isOngoing,
            groupSummary = (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0,
            canShowBadge = canShowBadge,
            hasContent = hasContent,
            postTime = sbn.postTime,
            ref = sbn,
        )
    }

    /**
     * Sender avatar of the newest message, else the notification's large icon, scaled to [size].
     * Apps that ask for the small icon as their badge ([Notification.BADGE_ICON_SMALL]) get a count.
     * `BADGE_ICON_NONE` is the platform default that almost no app changes, so it is not treated as
     * an opt-out.
     */
    private fun avatarOf(sbn: StatusBarNotification, size: Int): Bitmap? {
        val notification = sbn.notification ?: return null
        if (notification.badgeIconType == Notification.BADGE_ICON_SMALL) return null
        val drawable: Drawable? = try {
            val sender = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
                ?.messages?.lastOrNull()?.person?.icon?.loadDrawable(this)
            sender ?: notification.getLargeIcon()?.loadDrawable(this)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null // icons from other apps can fail to load (resources, URIs); fall back to a count
        }
        return drawable?.let { scaledSquare(it, size) }
    }

    /** Cheap identity of an icon source without loading it: resource, URI or bitmap size. */
    private fun iconSignature(icon: Icon?): Int {
        if (icon == null) return 0
        return try {
            when (icon.type) {
                Icon.TYPE_RESOURCE -> 31 * icon.resPackage.hashCode() + icon.resId
                Icon.TYPE_URI, Icon.TYPE_URI_ADAPTIVE_BITMAP -> icon.uri.hashCode()
                else -> 31 * icon.type // bitmap or data: the post time decides
            }
        } catch (_: RuntimeException) {
            0
        }
    }

    private fun scaledSquare(drawable: Drawable, size: Int): Bitmap? {
        if (size <= 0) return null
        val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: size
        val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: size
        // Centre-crop into a square of the badge size.
        val scale = size.toFloat() / minOf(w, h)
        val dw = max(1, (w * scale).toInt())
        val dh = max(1, (h * scale).toInt())
        val bitmap = createBitmap(size, size)
        val left = (size - dw) / 2
        val top = (size - dh) / 2
        drawable.setBounds(left, top, left + dw, top + dh)
        drawable.draw(Canvas(bitmap))
        return bitmap
    }
}

/**
 * Whether badges of a profile may be shown: the personal profile always; another profile only when
 * it is unlocked, not paused and not a private/inaccessible profile (same rules as the app list).
 */
internal class ProfileVisibility(context: Context, private val userManager: UserManager) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val me = Process.myUserHandle()

    fun isVisible(serial: Long): Boolean = try {
        val user = userManager.getUserForSerialNumber(serial)
        when {
            user == null -> false
            user == me -> true
            userManager.isQuietModeEnabled(user) -> false
            !userManager.isUserUnlocked(user) -> false
            else -> classifyProfile(isCallingUser = false, info = userType(user)) != null
        }
    } catch (_: RuntimeException) {
        false // fail closed: never leak a profile we cannot classify
    }

    private fun userType(user: android.os.UserHandle): UserTypeInfo {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return UserTypeInfo.NotAvailable
        val type = try {
            launcherApps?.getLauncherUserInfo(user)?.userType
        } catch (_: SecurityException) {
            null
        }
        return if (type == null) UserTypeInfo.Inaccessible else UserTypeInfo.Known(type)
    }
}
