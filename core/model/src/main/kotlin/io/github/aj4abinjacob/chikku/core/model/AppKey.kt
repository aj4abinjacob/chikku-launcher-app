package io.github.aj4abinjacob.chikku.core.model

/**
 * Persistent identity of a launchable activity in one Android user profile.
 *
 * Package name alone is not an identity: the same package can exist in the personal, work and
 * private profiles, and one package can expose several launcher activities. [userSerial] is the
 * value of `UserManager.getSerialNumberForUser`, which is stable across reboots, unlike a raw
 * `UserHandle`.
 */
data class AppKey(
    val packageName: String,
    val className: String,
    val userSerial: Long,
) {
    init {
        require(packageName.isNotBlank()) { "packageName must not be blank" }
        require(className.isNotBlank()) { "className must not be blank" }
    }
}

/** Kind of Android user profile an app belongs to. Private Space is not surfaced in M0. */
enum class ProfileType { PERSONAL, WORK, OTHER }

/** A launchable app as currently reported by the platform. Never persisted as an inventory. */
data class LauncherApp(
    val key: AppKey,
    val label: String,
    val profileType: ProfileType,
)
