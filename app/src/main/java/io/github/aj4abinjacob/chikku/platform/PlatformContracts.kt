package io.github.aj4abinjacob.chikku.platform

import android.graphics.Rect
import android.graphics.drawable.Drawable
import io.github.aj4abinjacob.chikku.core.model.AppKey
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** Outcome of asking the platform to open something. Never throws to the caller. */
sealed interface LaunchResult {
    data object Started : LaunchResult

    /** The app/activity is gone, disabled, or its profile is locked/quiet. */
    data class Unavailable(val reason: Reason) : LaunchResult

    enum class Reason { NOT_FOUND, SHORTCUT_UNAVAILABLE, PROFILE_UNAVAILABLE, SECURITY, UNKNOWN }

    /**
     * The app is locked in Chikku's app lock and nothing was started. The UI asks the system to
     * authenticate the user (see `AppUnlockPrompt`) and then retries the same action.
     */
    data class Locked(val packageName: String, val userSerial: Long) : LaunchResult
}

/**
 * Launch-time check for Chikku's app lock. Every platform class that starts an app, app shortcut
 * or App info screen asks it first, so each launcher surface (home, dock, folders, drawer, search,
 * gestures, cards, menus) is covered without per-surface checks; a missed UI path fails closed.
 */
fun interface AppLockGuard {
    /** True if [packageName] in profile [userSerial] may be opened now; consumes a one-time unlock. */
    fun admit(packageName: String, userSerial: Long): Boolean

    companion object {
        /** No app lock (tests and previews). */
        val NONE = AppLockGuard { _, _ -> true }
    }
}

/** Launches activities through LauncherApps with the correct UserHandle for the [AppKey]. */
interface AppLauncher {
    fun launch(key: AppKey, sourceBounds: Rect?): LaunchResult

    fun openAppDetails(key: AppKey, sourceBounds: Rect?): LaunchResult
}

/**
 * Loads app icons off the main thread with a bounded cache. Returns a fallback icon instead of
 * throwing when an icon is missing or malformed.
 *
 * Every call returns a new [Drawable] instance that the caller may give its own bounds, alpha and
 * colour filter; instances for the same icon may share (immutable) pixels, so callers need not
 * copy or [Drawable.mutate] them.
 */
interface IconProvider {
    /** Refresh time-dependent pack icons after date/time-zone changes or HOME resume. */
    fun refreshCalendarDate() = Unit

    suspend fun icon(key: AppKey): Drawable

    /**
     * The cached icon for pinned shortcut [key], or [load]'s result (cached when not null). [load]
     * does the expensive query and rendering; callers keep their own privacy checks around this
     * call, which runs them on every request, cached or not. Providers without a cache just load.
     */
    suspend fun shortcutIcon(key: ShortcutIconKey, load: suspend () -> Drawable?): Drawable? = load()

    /** Drops cached shortcut icons of [packageName] in profile [userSerial] after its shortcuts changed. */
    fun invalidateShortcuts(packageName: String, userSerial: Long) = Unit

    /**
     * The app's own single-colour glyph (its adaptive icon's monochrome layer, Android 13+) for the
     * caller to tint, or null when the app ships none. Never icon-pack or shape themed.
     */
    suspend fun monochromeIcon(key: AppKey): Drawable? = null

    /** Drops cached icons for a package (all profiles) after it is updated or removed. */
    fun invalidate(packageName: String)

    /**
     * Increments whenever every icon may look different (icon pack, shape or a per-app icon
     * changed). Views showing icons reload them on change. Providers without theming never change it.
     */
    val iconRevision: StateFlow<Long> get() = UNTHEMED_REVISION

    /**
     * Emits a package name after [invalidate] dropped only that package's icons (an app update may
     * have changed its launcher icon). Views bound to the package reload; others are left alone.
     * A full-cache drop bumps [iconRevision] instead.
     */
    val packageInvalidations: SharedFlow<String> get() = NO_INVALIDATIONS

    /** [icon], also reporting whether the result is the uncached fallback so a caller can retry later. */
    suspend fun resolvedIcon(key: AppKey): ResolvedIcon = ResolvedIcon(icon(key), isFallback = false)
}

/** Identity of a pinned shortcut's icon: the shortcut plus the icon-pack drawable chosen for it, if any. */
data class ShortcutIconKey(
    val packageName: String,
    val shortcutId: String,
    val userSerial: Long,
    val iconPack: String? = null,
    val iconName: String? = null,
)
/** An icon and whether it is the provider's fallback for a missing or failed load. */
data class ResolvedIcon(val drawable: Drawable, val isFallback: Boolean)

private val UNTHEMED_REVISION: StateFlow<Long> = MutableStateFlow(0L)
private val NO_INVALIDATIONS: SharedFlow<String> = MutableSharedFlow()

/** Reads and requests the HOME role without ever changing it on the user's behalf. */
interface HomeRoleController {
    /** True if Chikku currently is the default HOME app. */
    fun isDefaultHome(): Boolean
}
