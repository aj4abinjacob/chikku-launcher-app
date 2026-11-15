package io.github.aj4abinjacob.chikku.home

import android.os.Build
import android.os.StrictMode
import android.os.strictmode.Violation
import android.util.Log
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertWithMessage
import io.github.aj4abinjacob.chikku.DeviceTestSupport
import io.github.aj4abinjacob.chikku.R
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Main-thread disk I/O during HOME startup, observed with StrictMode on the real device.
 *
 * Partly informational: every violation is logged (tag [TAG]) with its innermost app frame. Framework
 * and OEM code (MIUI theming, font loading, inflation) can read disk inside HomeActivity.onCreate, so
 * "HomeActivity is on the stack" is not treated as a defect. The assertion is narrow and robust: no
 * violation's innermost Chikku frame may be in the data/ or platform/ layers (Room, LauncherApps,
 * icon loading), which must always run on a background dispatcher. StartupGuard's synchronous
 * commit() on the main thread is a documented, deliberate exception and is excluded.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.P) // penaltyListener is API 28+
class StrictModeStartupTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private var previousPolicy: StrictMode.ThreadPolicy? = null
    private val violations = CopyOnWriteArrayList<Violation>()

    @Before
    fun setUp() {
        DeviceTestSupport.resetStartupGuard()
        instrumentation.runOnMainSync {
            previousPolicy = StrictMode.getThreadPolicy()
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .penaltyLog()
                    .penaltyListener(Executor { it.run() }) { violations += it }
                    .build(),
            )
        }
    }

    @After
    fun tearDown() {
        instrumentation.runOnMainSync { previousPolicy?.let(StrictMode::setThreadPolicy) }
        DeviceTestSupport.finishAll(HomeActivity::class.java)
        DeviceTestSupport.resetStartupGuard()
    }

    @Test
    fun startupDoesNotDoDataOrPlatformDiskIoOnMainThread() {
        ActivityScenario.launch(HomeActivity::class.java).use { scenario ->
            val ready = DeviceTestSupport.waitUntil(DeviceTestSupport.LOAD_TIMEOUT_MS) {
                var visible = false
                scenario.onActivity { visible = it.findViewById<View>(R.id.dock).visibility == View.VISIBLE }
                visible
            }
            assertWithMessage("workspace did not become ready").that(ready).isTrue()
            // Let deferred work (icon binding, dock seeding, drawer list) settle.
            scenario.onActivity { it.findViewById<View>(R.id.all_apps_button).performClick() }
            DeviceTestSupport.waitUntil(3_000) { false }
            instrumentation.waitForIdleSync()
        }

        val summaries = violations.map { violation ->
            val appFrame = violation.stackTrace.firstOrNull { isAppFrame(it) }
            Triple(violation, appFrame, violation.stackTrace.any { it.className.startsWith(StartupGuard::class.java.name) })
        }
        Log.i(TAG, "StrictMode main-thread violations during startup: ${violations.size}")
        summaries.forEach { (violation, frame, guard) ->
            Log.i(TAG, "${violation.javaClass.simpleName} innermostAppFrame=$frame startupGuard=$guard")
        }

        val offending = summaries.filter { (_, frame, guard) ->
            !guard && frame != null && LAYERS_THAT_MUST_BE_OFF_MAIN.any { frame.className.startsWith(it) }
        }
        assertWithMessage(
            "data/platform disk I/O on main thread:\n" +
                offending.joinToString("\n") { (v, f, _) -> "${v.javaClass.simpleName} at $f" },
        ).that(offending).isEmpty()
    }

    /** A Chikku production frame (test classes share the package prefix and are excluded). */
    private fun isAppFrame(frame: StackTraceElement): Boolean =
        frame.className.startsWith(APP_PACKAGE) &&
            !frame.className.startsWith(StrictModeStartupTest::class.java.name) &&
            !frame.className.startsWith(DeviceTestSupport::class.java.name)

    private companion object {
        const val TAG = "ChikkuStrictModeQA"
        const val APP_PACKAGE = "io.github.aj4abinjacob.chikku."
        val LAYERS_THAT_MUST_BE_OFF_MAIN = listOf(
            "io.github.aj4abinjacob.chikku.data.",
            "io.github.aj4abinjacob.chikku.platform.",
        )
    }
}
