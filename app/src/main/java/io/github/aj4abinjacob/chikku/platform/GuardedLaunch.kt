package io.github.aj4abinjacob.chikku.platform

import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.Process
import android.os.UserHandle
import android.os.UserManager

/**
 * The one place that orders the checks in front of every LauncherApps launch: app lock, then the
 * profile's [UserHandle], then [ProfileAvailability] (locked, quiet, private or unknown profiles fail
 * closed), then [action]; platform exceptions map to [LaunchResult.Unavailable] via
 * [launchFailureReason]. [action] may `return` from the caller to report a more specific result.
 */
internal inline fun guardedLaunch(
    lock: AppLockGuard,
    profiles: ProfileAvailability,
    packageName: String,
    userSerial: Long,
    resolveUser: () -> UserHandle?,
    action: (UserHandle) -> Unit,
): LaunchResult {
    if (!lock.admit(packageName, userSerial)) return LaunchResult.Locked(packageName, userSerial)
    return try {
        val user = resolveUser() ?: return LaunchResult.Unavailable(LaunchResult.Reason.PROFILE_UNAVAILABLE)
        // Revalidate persisted or recently displayed targets before handing them to Android.
        if (!profiles.isAvailable(user)) return LaunchResult.Unavailable(LaunchResult.Reason.PROFILE_UNAVAILABLE)
        action(user)
        LaunchResult.Started
    } catch (e: RuntimeException) {
        LaunchResult.Unavailable(launchFailureReason(e))
    }
}

/** Dynamic, manifest and pinned shortcuts, optionally limited to [packageName] and [shortcutIds]. */
internal fun shortcutQuery(packageName: String? = null, shortcutIds: List<String>? = null): LauncherApps.ShortcutQuery {
    val query = LauncherApps.ShortcutQuery().setQueryFlags(
        LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
            LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
            LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
    )
    packageName?.let(query::setPackage)
    shortcutIds?.let(query::setShortcutIds)
    return query
}

/** Enabled, and really owned by [packageName] in [user] (the query is advisory, not trusted). */
internal fun ShortcutInfo.isEnabledFor(packageName: String, user: UserHandle): Boolean =
    isEnabled && `package` == packageName && userHandle == user

/** True when the shortcut has no owning activity or its activity is [className]. */
internal fun ShortcutInfo.belongsToActivity(className: String): Boolean =
    activity == null || activity?.className == className

/**
 * The calling user's serial, or null when unknown. With [requireUnlocked] a locked profile also
 * yields null, so credential-protected storage is never touched before first unlock.
 */
internal fun UserManager?.personalUserSerial(requireUnlocked: Boolean): Long? = try {
    val user = Process.myUserHandle()
    this?.takeIf { !requireUnlocked || it.isUserUnlocked(user) }?.getSerialNumberForUser(user)?.takeIf { it >= 0 }
} catch (_: RuntimeException) {
    null
}
