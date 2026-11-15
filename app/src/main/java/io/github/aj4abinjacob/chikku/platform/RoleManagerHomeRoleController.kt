package io.github.aj4abinjacob.chikku.platform

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * [HomeRoleController] backed by RoleManager (API 29+). It only reads the role and builds intents
 * the user can act on; it never changes the default HOME app itself.
 */
class RoleManagerHomeRoleController(context: Context) : HomeRoleController {
    private val roleManager = context.getSystemService(RoleManager::class.java)

    override fun isDefaultHome(): Boolean = runCatching {
        roleManager != null &&
            roleManager.isRoleAvailable(RoleManager.ROLE_HOME) &&
            roleManager.isRoleHeld(RoleManager.ROLE_HOME)
    }.getOrDefault(false)

    /**
     * Intent for `startActivityForResult` that asks the user to make Chikku the HOME app, or null
     * when the role is unavailable or already held (then use [homeSettingsIntent]).
     */
    fun createRequestIntent(): Intent? = runCatching {
        val manager = roleManager ?: return null
        if (manager.isRoleAvailable(RoleManager.ROLE_HOME) && !manager.isRoleHeld(RoleManager.ROLE_HOME)) {
            manager.createRequestRoleIntent(RoleManager.ROLE_HOME)
        } else {
            null
        }
    }.getOrNull()

    /** System default-HOME settings screen; a fallback when the role request is not offered. */
    fun homeSettingsIntent(): Intent = Intent(Settings.ACTION_HOME_SETTINGS)
}
