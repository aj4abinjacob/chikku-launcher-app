package io.github.aj4abinjacob.chikku.platform

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.UserHandle
import android.os.UserManager
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.settings.IconShape
import io.github.aj4abinjacob.chikku.platform.icons.IconShaper

/** One app shortcut offered in the long-press menu. [label] is shown only, never logged. */
class AppShortcut internal constructor(
    val id: String,
    val label: String,
    val icon: Drawable?,
    internal val info: ShortcutInfo,
)

/** What the long-press menu can offer for an app, loaded off the main thread. */
data class AppMenuDetails(
    val shortcuts: List<AppShortcut>,
    /** False for system apps that cannot be removed (they can only be disabled in App info). */
    val canUninstall: Boolean,
)

/**
 * LauncherApps calls behind the app long-press menu. [loadDetails] does binder I/O: call it off the
 * main thread. Starting activities happens on the main thread and never throws.
 */
class AppMenuPlatform(context: Context, private val lock: AppLockGuard) {
    private val appContext = context.applicationContext
    private val launcherApps = appContext.getSystemService(LauncherApps::class.java)
    private val userManager = appContext.getSystemService(UserManager::class.java)
    private val profiles = ProfileAvailability(appContext)

    private fun userOf(key: AppKey): UserHandle? = try {
        userManager?.getUserForSerialNumber(key.userSerial)
    } catch (_: RuntimeException) {
        null
    }

    /** [iconShape] is the user's icon shape, applied to shortcut icons so they match app icons. */
    fun loadDetails(key: AppKey, iconShape: IconShape = IconShape.SYSTEM, maxShortcuts: Int = MAX_SHORTCUTS): AppMenuDetails {
        val user = userOf(key) ?: return AppMenuDetails(emptyList(), canUninstall = false)
        return AppMenuDetails(shortcuts(key, user, iconShape, maxShortcuts), canUninstall(key, user))
    }

    /** Dynamic and manifest shortcuts; only available while Chikku is the default HOME app. */
    private fun shortcuts(key: AppKey, user: UserHandle, iconShape: IconShape, max: Int): List<AppShortcut> {
        val apps = launcherApps ?: return emptyList()
        return try {
            if (!apps.hasShortcutHostPermission()) return emptyList()
            val query = LauncherApps.ShortcutQuery()
                .setPackage(key.packageName)
                .setActivity(ComponentName(key.packageName, key.className))
                .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST)
            val density = appContext.resources.displayMetrics.densityDpi
            apps.getShortcuts(query, user).orEmpty()
                .filter { it.isEnabled }
                .sortedBy { it.rank }
                // Apps often publish a dynamic copy of a manifest shortcut under another id; show it once.
                .distinctBy { shortcutLabel(it).trim().lowercase() }
                .filter { shortcutLabel(it).isNotBlank() }
                .take(max)
                .map { info ->
                    val label = shortcutLabel(info)
                    val icon = try {
                        apps.getShortcutIconDrawable(info, density)?.let { IconShaper.normalizeShortcut(it, iconShape) }
                    } catch (_: RuntimeException) {
                        null
                    }
                    AppShortcut(info.id, label, icon, info)
                }
                .filter { it.label.isNotBlank() }
        } catch (_: RuntimeException) {
            emptyList() // SecurityException (not HOME) or locked/quiet profile
        }
    }

    private fun shortcutLabel(info: ShortcutInfo): String = (info.shortLabel ?: info.longLabel)?.toString().orEmpty()

    private fun canUninstall(key: AppKey, user: UserHandle): Boolean = try {
        val info = launcherApps?.getApplicationInfo(key.packageName, 0, user) ?: return false
        val system = info.flags and ApplicationInfo.FLAG_SYSTEM != 0
        val updated = info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
        !system || updated
    } catch (_: Exception) {
        false
    }

    fun startShortcut(shortcut: AppShortcut, sourceBounds: Rect?): LaunchResult {
        val info = shortcut.info
        // Unknown profile: refuse rather than skip the app lock check.
        val serial = try {
            userManager?.getSerialNumberForUser(info.userHandle)?.takeIf { it >= 0 }
        } catch (e: RuntimeException) {
            return LaunchResult.Unavailable(launchFailureReason(e))
        } ?: return LaunchResult.Unavailable(LaunchResult.Reason.PROFILE_UNAVAILABLE)
        return guardedLaunch(lock, profiles, info.`package`, serial, resolveUser = { info.userHandle }) {
            val apps = launcherApps ?: return LaunchResult.Unavailable(LaunchResult.Reason.UNKNOWN)
            apps.startShortcut(info, sourceBounds, launchAnimationOptions(appContext, sourceBounds))
        }
    }

    /**
     * System uninstall confirmation for [key]'s package in its own profile (a work app is removed
     * from the work profile only). Needs REQUEST_DELETE_PACKAGES. Null if the profile is gone.
     */
    fun uninstallIntent(key: AppKey): Intent? {
        val user = userOf(key) ?: return null
        return Intent(Intent.ACTION_DELETE, Uri.fromParts("package", key.packageName, null))
            .putExtra(Intent.EXTRA_USER, user)
    }

    companion object {
        const val MAX_SHORTCUTS = 4
    }
}
