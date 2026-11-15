package io.github.aj4abinjacob.chikku

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.os.Process
import android.os.SystemClock
import android.os.UserManager
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.settings.SettingsActivity

/**
 * Shared helpers for on-device tests. Everything here is safe on a personal phone: it never
 * changes the default HOME role, never touches other apps' data and never presses system HOME.
 */
object DeviceTestSupport {
    /** Generous: the reference device is a mid-range MIUI phone on API 30. */
    const val UI_TIMEOUT_MS = 15_000L
    const val LOAD_TIMEOUT_MS = 30_000L

    /** Mirrors StartupGuard's private prefs file name; tests reset it so repeated short launches never trip safe mode. */
    private const val STARTUP_GUARD_PREFS = "chikku_startup_guard"

    val targetContext: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    /** The app under test's package, e.g. `io.github.aj4abinjacob.chikku.debug`. */
    val targetPackage: String get() = targetContext.packageName

    fun personalSerial(context: Context = targetContext): Long =
        checkNotNull(context.getSystemService(UserManager::class.java)).getSerialNumberForUser(Process.myUserHandle())

    /** Chikku's own MAIN/LAUNCHER entry: the only app every device running these tests is guaranteed to have. */
    fun settingsKey(context: Context = targetContext): AppKey =
        AppKey(context.packageName, SettingsActivity::class.java.name, personalSerial(context))

    fun settingsComponent(context: Context = targetContext) =
        ComponentName(context.packageName, SettingsActivity::class.java.name)

    /**
     * HomeActivity counts every start as "unfinished" until it has been stable for 5 s; three quick
     * test launches in a row would otherwise put the debug app into sticky safe mode. Call before and
     * after any test that launches HomeActivity.
     */
    fun resetStartupGuard(context: Context = targetContext) {
        context.getSharedPreferences(STARTUP_GUARD_PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    }

    /** Polls [condition] (any thread) until true or [timeoutMs] elapses; returns the final result. */
    fun waitUntil(timeoutMs: Long = UI_TIMEOUT_MS, intervalMs: Long = 100, condition: () -> Boolean): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return true
            SystemClock.sleep(intervalMs)
        }
        return condition()
    }

    /** Resumed activities of this process, read on the main thread. */
    fun resumedActivities(): List<Activity> {
        var result: List<Activity> = emptyList()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            result = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).toList()
        }
        return result
    }

    fun waitForResumed(activityClass: Class<out Activity>, timeoutMs: Long = UI_TIMEOUT_MS): Activity? {
        var found: Activity? = null
        waitUntil(timeoutMs) {
            found = resumedActivities().firstOrNull { activityClass.isInstance(it) }
            found != null
        }
        return found
    }

    /** Finishes every live (not yet destroyed) instance of [activityClass] in this process. */
    fun finishAll(activityClass: Class<out Activity>) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val monitor = ActivityLifecycleMonitorRegistry.getInstance()
            listOf(Stage.PRE_ON_CREATE, Stage.CREATED, Stage.STARTED, Stage.RESUMED, Stage.PAUSED, Stage.STOPPED)
                .flatMap { monitor.getActivitiesInStage(it) }
                .filter { activityClass.isInstance(it) && !it.isFinishing }
                .forEach { it.finish() }
        }
    }
}
