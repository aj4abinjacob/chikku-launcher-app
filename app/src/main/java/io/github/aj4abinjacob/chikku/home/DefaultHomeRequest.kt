package io.github.aj4abinjacob.chikku.home

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent

/**
 * The shared "make Chikku the default home" flow used by the home screen, settings and onboarding:
 * ask for the HOME role when the platform offers it, otherwise (or when that screen cannot open)
 * fall back to the system HOME settings, and report [onUnavailable] when even that cannot open.
 */
internal object DefaultHomeRequest {
    fun request(
        activity: Activity,
        deps: HomeDependencies,
        launchRoleRequest: (Intent) -> Unit,
        onUnavailable: () -> Unit,
    ) {
        val roleIntent = try {
            deps.homeRoleRequestIntent()
        } catch (_: Exception) {
            null
        }
        if (roleIntent != null) {
            try {
                launchRoleRequest(roleIntent)
                return
            } catch (_: ActivityNotFoundException) {
                // Fall through to the system HOME settings screen.
            }
        }
        openHomeSettings(activity, deps, onUnavailable)
    }

    fun openHomeSettings(activity: Activity, deps: HomeDependencies, onUnavailable: () -> Unit) {
        try {
            activity.startActivity(deps.homeSettingsIntent())
        } catch (_: ActivityNotFoundException) {
            onUnavailable()
        } catch (_: SecurityException) {
            onUnavailable()
        }
    }
}
