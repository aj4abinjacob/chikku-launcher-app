package io.github.aj4abinjacob.chikku.platform

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.os.UserHandle
import android.os.UserManager
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.platform.LaunchResult.Reason

/**
 * [AppLauncher] backed by LauncherApps. Call from the main thread (it starts activities). Never
 * throws: every platform failure maps to [LaunchResult.Unavailable].
 */
class LauncherAppsLauncher(private val context: Context, private val lock: AppLockGuard) : AppLauncher {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val profiles = ProfileAvailability(context)

    override fun launch(key: AppKey, sourceBounds: Rect?): LaunchResult = start(key) { component, user ->
        launcherApps.startMainActivity(component, user, sourceBounds, launchAnimationOptions(context, sourceBounds))
    }

    override fun openAppDetails(key: AppKey, sourceBounds: Rect?): LaunchResult =
        start(key) { component, user ->
            launcherApps.startAppDetailsActivity(component, user, sourceBounds, launchAnimationOptions(context, sourceBounds))
        }

    private inline fun start(
        key: AppKey,
        action: (ComponentName, UserHandle) -> Unit,
    ): LaunchResult {
        if (launcherApps == null || userManager == null) return LaunchResult.Unavailable(Reason.UNKNOWN)
        // App info is gated too (guardedLaunch admits the lock): the system screen has an "Open" button.
        return guardedLaunch(lock, profiles, key.packageName, key.userSerial,
            resolveUser = { userManager.getUserForSerialNumber(key.userSerial) }) { user ->
            action(ComponentName(key.packageName, key.className), user)
        }
    }
}

/** Maps a platform launch exception to a user-facing reason. */
internal fun launchFailureReason(e: Throwable): Reason = when (e) {
    is ActivityNotFoundException -> Reason.NOT_FOUND
    is SecurityException -> Reason.SECURITY
    // Thrown for locked, stopped or quiet profiles.
    is IllegalStateException -> Reason.PROFILE_UNAVAILABLE
    else -> Reason.UNKNOWN
}
