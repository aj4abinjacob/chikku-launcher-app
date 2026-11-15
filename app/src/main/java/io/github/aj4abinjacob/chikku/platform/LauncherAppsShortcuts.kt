package io.github.aj4abinjacob.chikku.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Rect
import android.os.Process
import android.os.UserManager
import io.github.aj4abinjacob.chikku.core.model.AppKey

/** One app shortcut offered by the action picker. Labels are shown, never logged or stored. */
data class ShortcutOption(val packageName: String, val shortcutId: String, val userSerial: Long, val label: String)

/** Result of listing an app's shortcuts. */
sealed interface ShortcutListResult {
    data class Listed(val shortcuts: List<ShortcutOption>) : ShortcutListResult

    /** Only the default HOME app may read or start other apps' shortcuts. */
    data object NotDefaultLauncher : ShortcutListResult

    data object Failed : ShortcutListResult
}

/**
 * App shortcuts through LauncherApps with the profile's UserHandle. [shortcutsFor] does binder I/O:
 * call it off the main thread. Never throws.
 */
class LauncherAppsShortcuts(
    private val context: Context,
    private val lock: AppLockGuard,
    private val metadata: ShortcutMetadataPolicy = lock as? ShortcutMetadataPolicy ?: ShortcutMetadataPolicy.DENY,
) : ShortcutStarter {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val profiles = ProfileAvailability(context)

    override fun start(packageName: String, shortcutId: String, userSerial: Long, sourceBounds: Rect?): LaunchResult {
        if (launcherApps == null || userManager == null) return LaunchResult.Unavailable(LaunchResult.Reason.UNKNOWN)
        return guardedLaunch(lock, profiles, packageName, userSerial,
            resolveUser = { userManager.getUserForSerialNumber(userSerial) }) { user ->
            try {
                launcherApps.startShortcut(packageName, shortcutId, sourceBounds, launchAnimationOptions(context, sourceBounds), user)
            } catch (_: ActivityNotFoundException) {
                // A portable backup cannot recreate Android's publisher-owned pinned intent.
                // Firefox's URL IDs can still open the exact website, scoped to Firefox only.
                val website = firefoxWebsiteIntent(packageName, shortcutId)
                    ?: return LaunchResult.Unavailable(LaunchResult.Reason.SHORTCUT_UNAVAILABLE)
                // startActivity is current-profile only. Never open a work shortcut personally.
                if (user != Process.myUserHandle() || !profiles.isAvailable(user)) {
                    return LaunchResult.Unavailable(LaunchResult.Reason.PROFILE_UNAVAILABLE)
                }
                website.sourceBounds = sourceBounds
                context.startActivity(website, launchAnimationOptions(context, sourceBounds))
            }
        }
    }

    /**
     * Which of [apps] have at least one enabled shortcut, with package-scoped queries for readable owners. Null when that
     * cannot be read (not the default home app, a profile locked or unavailable), so callers fall
     * back to listing every app instead of hiding ones that may have shortcuts.
     */
    fun appsWithShortcuts(apps: Collection<AppKey>): Set<AppKey>? {
        if (launcherApps == null || userManager == null) return null
        return try {
            if (!launcherApps.hasShortcutHostPermission()) return null
            val found = HashSet<AppKey>()
            // Package-scoped queries avoid fetching locked owners' private metadata at all.
            for ((owner, keys) in apps.filter { metadata.mayRead(it.packageName, it.userSerial) }
                .groupBy { it.packageName to it.userSerial }) {
                val (packageName, serial) = owner
                if (!metadata.mayRead(packageName, serial)) continue
                val user = userManager.getUserForSerialNumber(serial) ?: return null
                if (!profiles.isAvailable(user)) return null
                val shortcuts = launcherApps.getShortcuts(shortcutQuery(packageName), user).orEmpty()
                if (!metadata.mayRead(packageName, serial) || !profiles.isAvailable(user)) continue
                for (info in shortcuts) {
                    if (!info.isEnabledFor(packageName, user)) continue
                    keys.filterTo(found) { info.belongsToActivity(it.className) }
                }
            }
            found.removeAll { !metadata.mayRead(it.packageName, it.userSerial) }
            found
        } catch (_: SecurityException) {
            null
        } catch (_: RuntimeException) {
            null
        }
    }

    fun shortcutsFor(app: AppKey): ShortcutListResult {
        if (!metadata.mayRead(app.packageName, app.userSerial)) return ShortcutListResult.Failed
        if (launcherApps == null || userManager == null) return ShortcutListResult.Failed
        return try {
            if (!launcherApps.hasShortcutHostPermission()) return ShortcutListResult.NotDefaultLauncher
            val user = userManager.getUserForSerialNumber(app.userSerial) ?: return ShortcutListResult.Failed
            if (!profiles.isAvailable(user)) return ShortcutListResult.Failed
            val native = launcherApps.getShortcuts(shortcutQuery(app.packageName), user).orEmpty()
            if (!metadata.mayRead(app.packageName, app.userSerial)) return ShortcutListResult.Failed
            val shortcuts = native
                .filter { it.isEnabledFor(app.packageName, user) && it.belongsToActivity(app.className) }
                .sortedWith(compareBy<ShortcutInfo> { it.rank }.thenBy { it.id })
                .mapNotNull { info ->
                    if (!metadata.mayRead(app.packageName, app.userSerial)) return@mapNotNull null
                    ShortcutOption(
                        packageName = info.`package`,
                        shortcutId = info.id,
                        userSerial = app.userSerial,
                        label = (info.shortLabel ?: info.longLabel ?: info.id).toString(),
                    )
                }
            if (!profiles.isAvailable(user)) return ShortcutListResult.Failed
            if (!launcherApps.hasShortcutHostPermission()) return ShortcutListResult.NotDefaultLauncher
            if (!metadata.mayRead(app.packageName, app.userSerial)) return ShortcutListResult.Failed
            ShortcutListResult.Listed(shortcuts.distinctBy { it.shortcutId })
        } catch (_: SecurityException) {
            ShortcutListResult.NotDefaultLauncher
        } catch (_: RuntimeException) {
            ShortcutListResult.Failed
        }
    }
}
