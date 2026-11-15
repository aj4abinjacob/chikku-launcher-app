package io.github.aj4abinjacob.chikku.badges

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import io.github.aj4abinjacob.chikku.core.model.settings.BadgeStyle
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Shared notification access for optional badges and media cards; disabled until either is enabled. */
class BadgeAccess(context: Context) {
    private val context = context.applicationContext
    val component = ComponentName(this.context, ChikkuNotificationListener::class.java)

    /** True when the user granted notification access to Chikku's listener. Binder call: avoid in tight loops. */
    fun isGranted(): Boolean = try {
        context.getSystemService(NotificationManager::class.java)?.isNotificationListenerAccessGranted(component) == true
    } catch (_: RuntimeException) {
        false
    }

    /**
     * Opens the per-app notification-access page on API 30+ (falls back to the full list where the
     * device has no detail page). The caller should handle ActivityNotFoundException.
     */
    fun settingsIntents(): List<Intent> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            add(
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                    .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component.flattenToString()),
            )
        }
        add(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    /** Enables or disables the listener component. Package-manager call: run off the main thread. */
    fun setListenerEnabled(enabled: Boolean) {
        val pm = context.packageManager
        val wanted = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        if (pm.getComponentEnabledSetting(component) == wanted) return
        pm.setComponentEnabledSetting(component, wanted, PackageManager.DONT_KILL_APP)
    }

    /** Asks the system to bind the listener again (e.g. after badges were re-enabled). */
    fun requestRebind() {
        try {
            NotificationListenerService.requestRebind(component)
        } catch (_: RuntimeException) {
            // Access not granted or not supported; the grant flow binds it later.
        }
    }

    /**
     * Applies a badge style: OFF clears every badge. A saved enabled media card can keep the shared listener enabled;
     * otherwise it follows the badge style. Existing granted access is reused.
     */
    suspend fun apply(style: BadgeStyle, repository: BadgeRepository, ioDispatcher: CoroutineDispatcher,
        mediaEnabled: Boolean = false) {
        if (style == BadgeStyle.OFF) repository.clear()
        withContext(ioDispatcher) {
            try {
                val enabled = style != BadgeStyle.OFF || mediaEnabled
                setListenerEnabled(enabled)
                if (enabled && isGranted()) requestRebind() else repository.disconnect()
            } catch (_: RuntimeException) {
                repository.disconnect()
                // Package manager refused; the listener itself also unbinds when neither badges nor media need access.
            }
        }
    }
}
