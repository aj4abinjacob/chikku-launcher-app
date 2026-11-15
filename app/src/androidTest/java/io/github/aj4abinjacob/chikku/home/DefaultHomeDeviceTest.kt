package io.github.aj4abinjacob.chikku.home

import android.content.Context
import android.content.Intent
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.DeviceTestSupport
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.GestureAction
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerOpenMethod
import io.github.aj4abinjacob.chikku.data.SharedPreferencesSettingsRepository
import io.github.aj4abinjacob.chikku.settings.SettingsActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Host-side smoke script owns temporary HOME selection/reversion; the test never grants roles. */
@RunWith(AndroidJUnit4::class)
class DefaultHomeDeviceTest {
    @Test fun systemHomeReturnsFromSettingsAndClosesDrawerWithoutChangingLayout() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val deps = context.homeDependencies()
        assumeTrue("Run through the phone smoke script with temporary HOME selection", deps.homeRoleController.isDefaultHome())
        val device = UiDevice.getInstance(instrumentation)
        val prefs = context.getSharedPreferences(SharedPreferencesSettingsRepository.PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(SharedPreferencesSettingsRepository.KEY_SETTINGS, null)
        val original = deps.settingsRepository.settings.first()
        val content = deps.workspaceRepository.exportContent()
        try {
            DeviceTestSupport.resetStartupGuard()
            deps.settingsRepository.update { it.copy(
                onboarding = it.onboarding.copy(completed = true), dock = it.dock.copy(enabled = true),
                drawer = it.drawer.copy(openMethod = DrawerOpenMethod.BOTH),
                gestures = it.gestures.copy(homeGesture = GestureAction.None),
            ) }
            device.pressHome()
            val home = DeviceTestSupport.waitForResumed(HomeActivity::class.java) as? HomeActivity
            assertThat(home).isNotNull()
            assertThat(device.wait(Until.hasObject(By.desc(context.getString(R.string.home_open_all_apps))), DeviceTestSupport.UI_TIMEOUT_MS)).isTrue()
            instrumentation.runOnMainSync {
                assertThat(home!!.findViewById<View>(R.id.default_banner).visibility).isEqualTo(View.GONE)
                home.findViewById<View>(R.id.all_apps_button).performClick()
            }
            assertThat(DeviceTestSupport.waitUntil {
                var open = false
                instrumentation.runOnMainSync { open = home!!.findViewById<View>(R.id.drawer).visibility == View.VISIBLE }
                open
            }).isTrue()
            device.pressHome()
            assertThat(DeviceTestSupport.waitUntil {
                var closed = false
                instrumentation.runOnMainSync { closed = home!!.findViewById<View>(R.id.drawer).visibility != View.VISIBLE }
                closed
            }).isTrue()
            instrumentation.runOnMainSync { context.startActivity(Intent(context, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            assertThat(DeviceTestSupport.waitForResumed(SettingsActivity::class.java)).isNotNull()
            device.pressHome()
            assertThat(DeviceTestSupport.waitForResumed(HomeActivity::class.java)).isNotNull()
            // Boolean comparisons prevent failure output from revealing user layout contents.
            assertThat(deps.workspaceRepository.exportContent() == content).isTrue()
        } finally {
            DeviceTestSupport.finishAll(SettingsActivity::class.java)
            deps.settingsRepository.update { original }
            val editor = prefs.edit()
            if (raw == null) editor.remove(SharedPreferencesSettingsRepository.KEY_SETTINGS)
            else editor.putString(SharedPreferencesSettingsRepository.KEY_SETTINGS, raw)
            assertThat(editor.commit()).isTrue()
            DeviceTestSupport.resetStartupGuard()
        }
    }
}
