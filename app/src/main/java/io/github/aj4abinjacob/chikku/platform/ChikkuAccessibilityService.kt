package io.github.aj4abinjacob.chikku.platform

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import java.lang.ref.WeakReference

/**
 * Optional accessibility service used only for the "Lock screen" and "Recents" gesture actions (and
 * as a fallback to open the notification shade or quick settings). The user enables it in system
 * settings; Chikku never enables it on their behalf.
 *
 * It subscribes to no accessibility events, cannot retrieve window content
 * (`res/xml/chikku_accessibility_service.xml`) and does nothing but `performGlobalAction` when a
 * gesture asks for it. It reads, stores and logs nothing.
 */
class ChikkuAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        connected = WeakReference(this)
    }

    // No event types are requested, so nothing arrives here.
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        disconnect()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        disconnect()
        super.onDestroy()
    }

    private fun disconnect() {
        if (connected?.get() === this) connected = null
    }

    companion object {
        /**
         * The system owns the service's lifecycle; this weak reference to the bound instance is the
         * only way for HOME (same process) to reach it. Cleared on unbind.
         */
        @Volatile
        private var connected: WeakReference<ChikkuAccessibilityService>? = null

        /** Performs an `AccessibilityService.GLOBAL_ACTION_*`. False when the service is not running. */
        fun performGlobalAction(action: Int): Boolean = try {
            connected?.get()?.performGlobalAction(action) ?: false
        } catch (_: RuntimeException) {
            false
        }

        fun component(context: Context) = ComponentName(context, ChikkuAccessibilityService::class.java)

        /** Whether the user has enabled the service in system settings. */
        fun isEnabled(context: Context): Boolean = try {
            val manager = context.getSystemService(AccessibilityManager::class.java)
            val ours = component(context)
            manager?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).orEmpty().any { info ->
                val service = info.resolveInfo?.serviceInfo ?: return@any false
                service.packageName == ours.packageName && service.name == ours.className
            }
        } catch (_: RuntimeException) {
            false
        }

        /**
         * The system accessibility settings. The fragment-args extras highlight Chikku's entry on
         * devices that support them and are ignored elsewhere.
         */
        fun settingsIntent(context: Context): Intent {
            val key = component(context).flattenToString()
            return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .putExtra(EXTRA_FRAGMENT_ARG_KEY, key)
                .putExtra(EXTRA_SHOW_FRAGMENT_ARGUMENTS, Bundle().apply { putString(EXTRA_FRAGMENT_ARG_KEY, key) })
        }

        private const val EXTRA_FRAGMENT_ARG_KEY = ":settings:fragment_args_key"
        private const val EXTRA_SHOW_FRAGMENT_ARGUMENTS = ":settings:show_fragment_args"
    }
}
