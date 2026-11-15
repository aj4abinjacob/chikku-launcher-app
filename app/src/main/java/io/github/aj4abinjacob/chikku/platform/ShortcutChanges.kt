package io.github.aj4abinjacob.chikku.platform

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.os.UserManager

/**
 * Reports, on the main thread, the package and profile serial of each app that changed, removed or
 * disabled its shortcuts, so pinned shortcuts on HOME can be re-read. Package events are already
 * covered by the apps repository. Returns null when LauncherApps is unavailable.
 */
fun observeShortcutChanges(context: Context, onChanged: (packageName: String, userSerial: Long) -> Unit): AutoCloseable? {
    val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return null
    val users = context.getSystemService(UserManager::class.java) ?: return null
    val callback = object : LauncherApps.Callback() {
        override fun onShortcutsChanged(packageName: String, shortcuts: MutableList<ShortcutInfo>, user: UserHandle) {
            val serial = try { users.getSerialNumberForUser(user) } catch (_: RuntimeException) { -1L }
            if (serial >= 0) onChanged(packageName, serial)
        }

        override fun onPackageRemoved(packageName: String, user: UserHandle) = Unit
        override fun onPackageAdded(packageName: String, user: UserHandle) = Unit
        override fun onPackageChanged(packageName: String, user: UserHandle) = Unit
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = Unit
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = Unit
    }
    return try {
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        AutoCloseable { runCatching { launcherApps.unregisterCallback(callback) } }
    } catch (_: RuntimeException) {
        null
    }
}
