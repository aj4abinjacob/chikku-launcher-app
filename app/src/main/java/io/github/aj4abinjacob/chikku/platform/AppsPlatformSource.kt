package io.github.aj4abinjacob.chikku.platform

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import androidx.core.content.ContextCompat
import io.github.aj4abinjacob.chikku.core.model.ProfileType

/** A profile the launcher may show, already classified. [user] is null only in tests. */
internal data class SourceProfile(val user: UserHandle?, val serial: Long, val type: ProfileType)

/** One launcher activity as reported by the platform, before validation. */
internal data class RawActivity(val packageName: String, val className: String, val label: CharSequence?)

/**
 * The narrow slice of LauncherApps/UserManager that [LauncherAppsRepository] needs. Separated so
 * the repository's flow, sorting and failure handling can be tested without platform shadows.
 * All query methods may block on binder calls and must be called off the main thread.
 */
internal interface AppsPlatformSource {
    fun profiles(): List<SourceProfile>

    fun activities(profile: SourceProfile): List<RawActivity>

    /**
     * Starts observing package and profile changes. [onEvent] receives a package name for
     * package-level events and null for profile/locale events. Close the result to stop.
     */
    fun observe(onEvent: (packageName: String?) -> Unit): AutoCloseable
}

/** What the platform told us about a profile's user type. */
internal sealed interface UserTypeInfo {
    /** API < 35: there is no public way to read another profile's user type. */
    data object NotAvailable : UserTypeInfo

    /** API 35+: `getLauncherUserInfo` returned null, i.e. the profile is inaccessible to us. */
    data object Inaccessible : UserTypeInfo

    data class Known(val userType: String) : UserTypeInfo
}

// Values of UserManager.USER_TYPE_PROFILE_MANAGED / USER_TYPE_PROFILE_PRIVATE (public in API 35).
// Mirrored here so this pure function has no API-level dependency.
internal const val USER_TYPE_MANAGED = "android.os.usertype.profile.MANAGED"
internal const val USER_TYPE_PRIVATE = "android.os.usertype.profile.PRIVATE"

/**
 * Classifies a profile, or returns null when it must not be surfaced at all.
 *
 * - Private Space is always excluded in M0 (Chikku does not declare ACCESS_HIDDEN_PROFILES, so the
 *   platform should not return it; this is a defensive second check).
 * - On API 35+, a profile whose LauncherUserInfo is null is inaccessible and is excluded, because
 *   we cannot prove it is not a hidden profile.
 * - Below API 35 a non-calling profile cannot be classified with public APIs, so it is OTHER.
 */
internal fun classifyProfile(isCallingUser: Boolean, info: UserTypeInfo): ProfileType? {
    if (info is UserTypeInfo.Known && info.userType == USER_TYPE_PRIVATE) return null
    if (info == UserTypeInfo.Inaccessible) return null
    if (isCallingUser) return ProfileType.PERSONAL
    return when (info) {
        UserTypeInfo.NotAvailable -> ProfileType.OTHER
        UserTypeInfo.Inaccessible -> null
        is UserTypeInfo.Known -> if (info.userType == USER_TYPE_MANAGED) ProfileType.WORK else ProfileType.OTHER
    }
}

/** A saved UserHandle is insufficient: Android can reuse its ID after removing a profile. */
internal fun profileSnapshotIsCurrent(
    expected: SourceProfile,
    currentSerial: Long,
    currentType: ProfileType?,
    quiet: Boolean,
    unlocked: Boolean,
): Boolean = currentSerial >= 0 && currentSerial == expected.serial &&
    currentType != null && currentType == expected.type && !quiet && unlocked

/**
 * Broadcasts after which the app list must be re-enumerated. All are protected system broadcasts.
 * API levels follow the platform api-versions data used by lint.
 */
@SuppressLint("InlinedApi") // Each constant is gated on [sdkInt] below.
internal fun profileChangeActions(sdkInt: Int): List<String> = buildList {
    add(Intent.ACTION_MANAGED_PROFILE_ADDED)
    add(Intent.ACTION_MANAGED_PROFILE_REMOVED)
    add(Intent.ACTION_MANAGED_PROFILE_AVAILABLE)
    add(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE)
    add(Intent.ACTION_MANAGED_PROFILE_UNLOCKED)
    // Labels and collation order depend on the device locale.
    add(Intent.ACTION_LOCALE_CHANGED)
    if (sdkInt >= Build.VERSION_CODES.S) {
        add(Intent.ACTION_PROFILE_ACCESSIBLE)
        add(Intent.ACTION_PROFILE_INACCESSIBLE)
    }
    if (sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        add(Intent.ACTION_PROFILE_ADDED)
        add(Intent.ACTION_PROFILE_REMOVED)
    }
    if (sdkInt >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        add(Intent.ACTION_PROFILE_AVAILABLE)
        add(Intent.ACTION_PROFILE_UNAVAILABLE)
    }
}

/** Production [AppsPlatformSource] backed by LauncherApps and UserManager. */
internal class AndroidAppsPlatformSource(context: Context) : AppsPlatformSource {
    private val context = context.applicationContext
    private val launcherApps = checkNotNull(context.getSystemService(LauncherApps::class.java))
    private val userManager = checkNotNull(context.getSystemService(UserManager::class.java))

    override fun profiles(): List<SourceProfile> {
        val me = Process.myUserHandle()
        return launcherApps.profiles.mapNotNull { user ->
            try {
                if (userManager.isQuietModeEnabled(user) || !userManager.isUserUnlocked(user)) return@mapNotNull null
            } catch (_: RuntimeException) { return@mapNotNull null }
            val type = classifyProfile(user == me, userTypeOf(user)) ?: return@mapNotNull null
            val serial = userManager.getSerialNumberForUser(user)
            if (serial < 0) null else SourceProfile(user, serial, type)
        }
    }

    private fun userTypeOf(user: UserHandle): UserTypeInfo {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return UserTypeInfo.NotAvailable
        val userType = try {
            launcherApps.getLauncherUserInfo(user)?.userType
        } catch (_: SecurityException) {
            null
        }
        return if (userType == null) UserTypeInfo.Inaccessible else UserTypeInfo.Known(userType)
    }

    override fun activities(profile: SourceProfile): List<RawActivity> {
        val user = profile.user ?: return emptyList()
        requireCurrentProfile(profile)
        val result = launcherApps.getActivityList(null, user).map { info ->
            val component = info.componentName
            RawActivity(component.packageName, component.className, info.label)
        }
        requireCurrentProfile(profile)
        return result
    }

    /** Fail closed before reading labels and before publishing a cross-profile result. */
    private fun requireCurrentProfile(profile: SourceProfile) {
        val user = profile.user ?: throw IllegalStateException("Profile unavailable")
        val current = try {
            profileSnapshotIsCurrent(
                profile,
                userManager.getSerialNumberForUser(user),
                classifyProfile(user == Process.myUserHandle(), userTypeOf(user)),
                userManager.isQuietModeEnabled(user),
                userManager.isUserUnlocked(user),
            )
        } catch (_: RuntimeException) {
            false
        }
        if (!current) throw IllegalStateException("Profile unavailable")
    }

    override fun observe(onEvent: (packageName: String?) -> Unit): AutoCloseable {
        val callback = PackageCallback(onEvent)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = onEvent(null)
        }
        val filter = IntentFilter().apply { profileChangeActions(Build.VERSION.SDK_INT).forEach(::addAction) }
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        // Every action in the filter is a protected broadcast that only the system can send, so
        // exporting cannot be used for spoofing. The Android broadcasts guide says receivers for
        // system broadcasts should use RECEIVER_EXPORTED; NOT_EXPORTED may miss broadcasts sent
        // from privileged non-system UIDs.
        try {
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        } catch (e: RuntimeException) {
            launcherApps.unregisterCallback(callback)
            throw e
        }
        return AutoCloseable {
            runCatching { launcherApps.unregisterCallback(callback) }
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    private class PackageCallback(private val onEvent: (String?) -> Unit) : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = onEvent(packageName)

        override fun onPackageAdded(packageName: String, user: UserHandle) = onEvent(packageName)

        override fun onPackageChanged(packageName: String, user: UserHandle) = onEvent(packageName)

        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =
            notifyAll(packageNames)

        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =
            notifyAll(packageNames)

        override fun onPackagesSuspended(packageNames: Array<out String>, user: UserHandle) = notifyAll(packageNames)

        override fun onPackagesUnsuspended(packageNames: Array<out String>, user: UserHandle) = notifyAll(packageNames)

        private fun notifyAll(packageNames: Array<out String>?) {
            // Names let icon caches invalidate; the trailing null marks an availability change
            // that must drop the app list to Loading until it is re-read.
            packageNames?.forEach(onEvent)
            onEvent(null)
        }
    }
}
