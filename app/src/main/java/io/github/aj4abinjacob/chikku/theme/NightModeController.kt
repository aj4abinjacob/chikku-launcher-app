package io.github.aj4abinjacob.chikku.theme

import android.app.Activity
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.view.ContextThemeWrapper
import io.github.aj4abinjacob.chikku.core.model.settings.NightMode

/** Implemented by the Application so activities can reach the process's [NightModeController]. */
interface NightModeOwner {
    val nightModeController: NightModeController
}

/**
 * Applies the launcher's [NightMode] to Chikku's own activities without AppCompat.
 *
 * - API 31+: `UiModeManager.setApplicationNightMode`, which the system persists for the app and
 *   applies to every activity through a normal configuration change.
 * - API 29-30: each activity overrides its configuration's night bits in `attachBaseContext`
 *   ([applyOverride]) and recreates when the stored mode differs from what it was created with.
 *   Until settings have loaded after a cold start the mode is [NightMode.SYSTEM], so a non-system
 *   choice costs one recreate per cold start on these versions.
 */
class NightModeController(context: Context) {
    private val uiModeManager = context.applicationContext.getSystemService(UiModeManager::class.java)

    @Volatile
    var mode: NightMode = NightMode.SYSTEM
        private set

    /** Records [next]; on API 31+ also hands it to the system. Returns true if it changed. */
    fun update(next: NightMode): Boolean {
        val changed = next != mode
        mode = next
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                uiModeManager?.setApplicationNightMode(
                    when (next) {
                        NightMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
                        NightMode.ON -> UiModeManager.MODE_NIGHT_YES
                        NightMode.OFF -> UiModeManager.MODE_NIGHT_NO
                    },
                )
            } catch (_: RuntimeException) {
                // Not fatal: the activity keeps the system night state.
            }
        }
        return changed
    }

    companion object {
        /** The controller of [context]'s application, or null (e.g. a test Application). */
        fun of(context: Context): NightModeController? =
            (context.applicationContext as? NightModeOwner)?.nightModeController

        /**
         * Call from `attachBaseContext` right after `super`. Before API 31 applies the stored mode as
         * a configuration override and returns the mode the activity was created with.
         */
        fun applyOverride(activity: ContextThemeWrapper, base: Context): NightMode {
            val mode = of(base)?.mode ?: NightMode.SYSTEM
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return mode
            val bits = NightModeResolver.uiModeNightBits(mode) ?: return mode
            val baseUiMode = base.resources.configuration.uiMode
            val override = Configuration().apply {
                // Only uiMode is overridden; 0 leaves font scale to the base configuration.
                fontScale = 0f
                uiMode = (baseUiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or bits
            }
            try {
                activity.applyOverrideConfiguration(override)
            } catch (_: IllegalStateException) {
                // Resources already created; the activity simply follows the system.
            }
            return mode
        }

        /**
         * Call whenever settings emit. Updates the controller and, before API 31, recreates
         * [activity] if it was created with a different mode than [mode].
         */
        fun onModeChanged(activity: Activity, createdWith: NightMode, mode: NightMode) {
            // Without a controller the override could never be applied, so recreating would loop.
            val controller = of(activity) ?: return
            controller.update(mode)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && mode != createdWith && !activity.isFinishing) {
                activity.recreate()
            }
        }
    }
}
