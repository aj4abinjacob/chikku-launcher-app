package io.github.aj4abinjacob.chikku.home

import android.os.Looper
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.AppsState
import io.github.aj4abinjacob.chikku.core.model.settings.AnimationSpeed
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.platform.LaunchResult
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestHomeApplication::class)
class DrawerLaunchPolicyTest {
    @Test fun successfulLaunchClosesDrawerWhenEnabled() = checkLaunch(true, LaunchResult.Started, false)
    @Test fun successfulLaunchRetainsDrawerWhenDisabled() = checkLaunch(false, LaunchResult.Started, true)
    @Test fun failedLaunchRetainsDrawerEvenWhenAutoCloseEnabled() =
        checkLaunch(true, LaunchResult.Unavailable(LaunchResult.Reason.UNKNOWN), true)

    private fun checkLaunch(autoClose: Boolean, result: LaunchResult, expectedOpen: Boolean) {
        val application = ApplicationProvider.getApplicationContext<TestHomeApplication>()
        application.getSharedPreferences("chikku_startup_guard", 0).edit().clear().commit()
        val sample = app("Sample")
        val prefs = LauncherSettings().let { it.copy(
            drawer = it.drawer.copy(automaticallyClose = autoClose),
            lookFeel = it.lookFeel.copy(animationSpeed = AnimationSpeed.NONE),
        ) }
        val deps = FakeHomeDependencies(appsRepository = FakeAppsRepository(AppsState.Loaded(listOf(sample))),
            settingsRepository = FakeSettingsRepository(prefs))
        deps.appLauncher.result = result
        application.homeDependencies = deps
        ActivityScenario.launch(HomeActivity::class.java).use { scenario ->
            lateinit var activity: HomeActivity
            scenario.onActivity { activity = it }
            val list = activity.findViewById<RecyclerView>(R.id.drawer_list)
            waitUntil { list.adapter?.itemCount == 1 }
            activity.findViewById<View>(R.id.all_apps_button).performClick()
            waitUntil { activity.findViewById<View>(R.id.drawer).visibility == View.VISIBLE }
            list.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY))
            list.layout(0, 0, 1080, 1920)
            list.getChildAt(0).performClick()
            assertThat(deps.appLauncher.launched).containsExactly(sample.key)
            shadowOf(Looper.getMainLooper()).idle()
            if (!expectedOpen) waitUntil { activity.findViewById<View>(R.id.drawer).visibility != View.VISIBLE }
            assertThat(activity.findViewById<View>(R.id.drawer).visibility == View.VISIBLE).isEqualTo(expectedOpen)
        }
    }

    private fun waitUntil(condition: () -> Boolean) {
        val end = System.currentTimeMillis() + 5_000
        while (!condition()) {
            check(System.currentTimeMillis() < end)
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
    }
}
