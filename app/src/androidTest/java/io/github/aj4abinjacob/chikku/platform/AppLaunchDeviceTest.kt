package io.github.aj4abinjacob.chikku.platform

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.DeviceTestSupport
import io.github.aj4abinjacob.chikku.DeviceTestSupport.targetContext
import io.github.aj4abinjacob.chikku.DeviceTestSupport.targetPackage
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.settings.SettingsActivity
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Launches through the real LauncherApps service. The only app launched is Chikku's own settings
 * activity, which the test finishes afterwards; failure cases never start anything.
 */
@RunWith(AndroidJUnit4::class)
class AppLaunchDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val device: UiDevice get() = UiDevice.getInstance(instrumentation)

    @After
    fun tearDown() {
        DeviceTestSupport.finishAll(SettingsActivity::class.java)
        // Only reached if the platform wrongly honoured the non-launcher HomeActivity launch.
        DeviceTestSupport.finishAll(io.github.aj4abinjacob.chikku.home.HomeActivity::class.java)
        DeviceTestSupport.resetStartupGuard()
    }

    /** LauncherAppsLauncher documents main-thread use; honour it. */
    private fun launchOnMain(key: AppKey): LaunchResult {
        lateinit var result: LaunchResult
        instrumentation.runOnMainSync { result = LauncherAppsLauncher(targetContext, AppLockGuard.NONE).launch(key, null) }
        return result
    }

    @Test
    fun launchingChikkuSettingsStartsIt() {
        val result = launchOnMain(DeviceTestSupport.settingsKey())

        assertThat(result).isEqualTo(LaunchResult.Started)
        assertThat(DeviceTestSupport.waitForResumed(SettingsActivity::class.java)).isNotNull()
        // Also visible to the window system, not just resumed in-process.
        assertThat(device.wait(Until.hasObject(By.pkg(targetPackage)), DeviceTestSupport.UI_TIMEOUT_MS)).isTrue()
    }

    @Test
    fun unknownProfileSerialIsUnavailableWithoutThrowing() {
        val key = DeviceTestSupport.settingsKey().copy(userSerial = UNKNOWN_SERIAL)

        val result = launchOnMain(key)

        assertThat(result).isEqualTo(LaunchResult.Unavailable(LaunchResult.Reason.PROFILE_UNAVAILABLE))
        assertThat(DeviceTestSupport.resumedActivities().none { it is SettingsActivity }).isTrue()
    }

    @Test
    fun unknownComponentIsUnavailableWithoutThrowing() {
        val key = AppKey(targetPackage, "$targetPackage.DoesNotExistActivity", DeviceTestSupport.personalSerial())

        val result = launchOnMain(key)

        assertThat(result).isInstanceOf(LaunchResult.Unavailable::class.java)
        assertThat((result as LaunchResult.Unavailable).reason)
            .isAnyOf(LaunchResult.Reason.NOT_FOUND, LaunchResult.Reason.SECURITY)
    }

    @Test
    fun unknownPackageIsUnavailableWithoutThrowing() {
        val key = AppKey("io.github.aj4abinjacob.chikku.qa.not.installed", "Missing", DeviceTestSupport.personalSerial())

        val result = launchOnMain(key)

        assertThat(result).isInstanceOf(LaunchResult.Unavailable::class.java)
    }

    @Test
    fun nonLauncherActivityOfOwnPackageIsRefused() {
        // HomeActivity is exported but has no LAUNCHER category; LauncherApps must refuse it.
        val key = AppKey(
            targetPackage,
            io.github.aj4abinjacob.chikku.home.HomeActivity::class.java.name,
            DeviceTestSupport.personalSerial(),
        )

        val result = launchOnMain(key)

        assertThat(result).isInstanceOf(LaunchResult.Unavailable::class.java)
    }

    private companion object {
        /** No real profile has this serial. */
        const val UNKNOWN_SERIAL = 987_654_321L
    }
}
