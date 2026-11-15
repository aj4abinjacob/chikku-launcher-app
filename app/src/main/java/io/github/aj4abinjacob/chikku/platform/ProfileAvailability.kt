package io.github.aj4abinjacob.chikku.platform

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.os.UserManager

internal fun profileMayLaunch(personal: Boolean, quiet: Boolean, unlocked: Boolean, type: UserTypeInfo): Boolean =
    !quiet && unlocked && classifyProfile(personal, type) != null

/** Fresh public-API checks at action time; unknown, private, locked and paused profiles fail closed. */
internal class ProfileAvailability(context: Context) {
    private val users = context.getSystemService(UserManager::class.java)
    private val launcher = context.getSystemService(LauncherApps::class.java)
    fun isAvailable(serial: Long): Boolean = try {
        users?.getUserForSerialNumber(serial)?.let(::isAvailable) == true
    } catch (_: RuntimeException) { false }

    fun isAvailable(user: UserHandle): Boolean = try {
        val manager = users
        if (manager == null) false else {
            val type = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) UserTypeInfo.NotAvailable
            else launcher?.getLauncherUserInfo(user)?.userType?.let(UserTypeInfo::Known) ?: UserTypeInfo.Inaccessible
            profileMayLaunch(user == Process.myUserHandle(), manager.isQuietModeEnabled(user), manager.isUserUnlocked(user), type)
        }
    } catch (_: RuntimeException) { false }
}
