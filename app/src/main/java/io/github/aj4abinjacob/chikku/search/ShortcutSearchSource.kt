package io.github.aj4abinjacob.chikku.search

import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Rect
import io.github.aj4abinjacob.chikku.platform.ProfileAvailability
import android.os.UserManager
import io.github.aj4abinjacob.chikku.core.model.LauncherApp
import io.github.aj4abinjacob.chikku.core.model.search.ShortcutHit
import io.github.aj4abinjacob.chikku.core.model.search.ShortcutLookup
import io.github.aj4abinjacob.chikku.core.model.search.ShortcutSource
import io.github.aj4abinjacob.chikku.platform.AppLockGuard
import io.github.aj4abinjacob.chikku.platform.LaunchResult
import io.github.aj4abinjacob.chikku.platform.LauncherAppsShortcuts
import io.github.aj4abinjacob.chikku.platform.shortcutQuery
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Narrow platform boundary so role/profile changes can be exercised independently of Android. */
internal interface ShortcutPlatform {
    fun hasHostPermission(): Boolean
    fun isProfileAvailable(serial: Long): Boolean
    fun read(serial: Long, owners: List<LauncherApp>): List<ShortcutHit>
}

private class AndroidShortcutPlatform(context: Context) : ShortcutPlatform {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val users = context.getSystemService(UserManager::class.java)
    override fun hasHostPermission() = launcherApps?.hasShortcutHostPermission() == true
    private val profiles = ProfileAvailability(context)
    override fun isProfileAvailable(serial: Long) = profiles.isAvailable(serial)
    override fun read(serial: Long, owners: List<LauncherApp>): List<ShortcutHit> {
        val user = users?.getUserForSerialNumber(serial) ?: return emptyList()
        val byPackage = owners.groupBy { it.key.packageName }
        // Pinned: shortcuts the user added to Chikku's home screen, e.g. a browser site shortcut.
        return launcherApps?.getShortcuts(shortcutQuery(), user).orEmpty().mapNotNull { info ->
            if (!info.isEnabled) return@mapNotNull null
            val label = (info.shortLabel ?: info.longLabel)?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val candidates = byPackage[info.`package`] ?: return@mapNotNull null
            // A shortcut explicitly owned by a hidden activity must not be reassigned to another one.
            val owner = info.activity?.let { activity -> candidates.firstOrNull { it.key.className == activity.className } }
                ?: (if (info.activity == null) candidates.firstOrNull() else null)
                ?: return@mapNotNull null
            ShortcutHit(info.`package`, info.id, serial, label, owner.key)
        }
    }
}

/** Cached metadata is returned only after fresh HOME-role and profile checks, including after reads. */
class LauncherAppsShortcutSource internal constructor(
    private val platform: ShortcutPlatform,
    private val ioDispatcher: CoroutineDispatcher,
    private val clock: () -> Long = System::currentTimeMillis,
) : ShortcutSource {
    constructor(context: Context, ioDispatcher: CoroutineDispatcher, clock: () -> Long = System::currentTimeMillis) :
        this(AndroidShortcutPlatform(context.applicationContext), ioDispatcher, clock)

    @Volatile private var cache: Cached? = null
    private data class Cached(val apps: List<LauncherApp>, val at: Long, val result: ShortcutLookup.Found)

    override suspend fun shortcuts(apps: List<LauncherApp>): ShortcutLookup = withContext(ioDispatcher) {
        try {
            if (!platform.hasHostPermission()) { cache = null; return@withContext ShortcutLookup.NotDefaultHome }
            val eligible = apps.map { it.key.userSerial }.distinct().filter { platform.isProfileAvailable(it) }.toSet()
            val visible = apps.filter { it.key.userSerial in eligible }
            val now = clock()
            val cached = cache?.takeIf { it.apps == visible && now >= it.at && now - it.at < CACHE_MILLIS }
            val result = cached?.result ?: ShortcutLookup.Found(visible.groupBy { it.key.userSerial }.flatMap { (serial, owners) ->
                ensureActive()
                platform.read(serial, owners)
            })
            ensureActive()
            if (!platform.hasHostPermission()) { cache = null; return@withContext ShortcutLookup.NotDefaultHome }
            val stillEligible = eligible.filter { platform.isProfileAvailable(it) }.toSet()
            val keys = visible.filter { it.key.userSerial in stillEligible }.mapTo(HashSet()) { it.key }
            val safe = ShortcutLookup.Found(result.shortcuts.filter { it.appKey in keys && it.userSerial in stillEligible })
            cache = if (stillEligible == eligible) Cached(visible, now, safe) else null
            safe
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: SecurityException) { cache = null; ShortcutLookup.NotDefaultHome }
        catch (_: RuntimeException) { cache = null; ShortcutLookup.Unavailable }
    }

    /** Drops cached shortcuts so the next lookup reads them again (apps changed their shortcuts). */
    fun invalidate() { cache = null }

    private companion object { const val CACHE_MILLIS = 30_000L }
}

/**
 * Apps change their shortcuts without a package event. While open, this empties [source]'s 30 s cache
 * on each change and bumps [revision], which search combines into its inputs so shown results re-read.
 */
internal class ShortcutChangeSignal(context: Context, source: LauncherAppsShortcutSource) : AutoCloseable {
    val revision = kotlinx.coroutines.flow.MutableStateFlow(0L)
    private val watch = io.github.aj4abinjacob.chikku.platform.observeShortcutChanges(context) { _, _ ->
        source.invalidate()
        revision.value += 1
    }

    override fun close() { watch?.close() }
}

/** Starts a shortcut found by [LauncherAppsShortcutSource]. Main thread; never throws. */
class ShortcutStarter(context: Context, lock: AppLockGuard) {
    private val delegate = LauncherAppsShortcuts(context, lock)

    fun start(hit: ShortcutHit, sourceBounds: Rect?): LaunchResult =
        start(hit.packageName, hit.shortcutId, hit.userSerial, sourceBounds)

    fun start(packageName: String, shortcutId: String, userSerial: Long, sourceBounds: Rect?): LaunchResult =
        delegate.start(packageName, shortcutId, userSerial, sourceBounds)
}
