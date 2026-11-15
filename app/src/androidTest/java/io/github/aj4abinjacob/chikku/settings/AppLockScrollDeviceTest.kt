package io.github.aj4abinjacob.chikku.settings

import android.app.KeyguardManager
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.DeviceTestSupport
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.home.homeDependencies
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * App lock page on a real device: its only search is the app filter, which stays on screen, and
 * the drawer's scroll thumb offers letter jumps. Reads the installed app list; changes nothing.
 * Labels are matched only through Chikku's own strings, never logged.
 */
@RunWith(AndroidJUnit4::class)
class AppLockScrollDeviceTest {
    private val context get() = DeviceTestSupport.targetContext
    private val device get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Test fun appLockHasOnlyItsAppSearchAndAFastScroller() = runBlocking {
        device.wakeUp()
        assumeFalse("Unlock the device", context.getSystemService(KeyguardManager::class.java).isKeyguardLocked)
        val lock = context.homeDependencies().appLock.state.first { it.loaded }
        // With any app locked the page first asks for the screen lock, which a test cannot answer.
        assumeTrue("No app may be locked", lock.locked.isEmpty() && !lock.readFailed)
        val intent = Intent(context, SettingsActivity::class.java)
            .putExtra(SettingsActivity.EXTRA_SECTION, SettingsSection.APP_LOCK.name)
        ActivityScenario.launch<SettingsActivity>(intent).use {
            val searchApps = context.getString(R.string.app_lock_search_label)
            assertThat(device.wait(Until.hasObject(By.text(searchApps)), DeviceTestSupport.UI_TIMEOUT_MS)).isTrue()
            // The settings-wide magnifier would read as searching these apps.
            assertThat(device.hasObject(By.desc(context.getString(R.string.settings_search_title)))).isFalse()

            val jumpPrefix = context.getString(R.string.drawer_fast_scroller_jump, "")
            val jumps = device.wait(Until.findObjects(By.descStartsWith(jumpPrefix)), DeviceTestSupport.UI_TIMEOUT_MS)
            assumeTrue("Needs apps under at least two letters", jumps != null && jumps.size > 1)
            jumps!!.last().click()
            device.waitForIdle()
            // The filter stays pinned after jumping to the end of a long list.
            assertThat(device.wait(Until.hasObject(By.text(searchApps)), DeviceTestSupport.UI_TIMEOUT_MS)).isTrue()
        }
    }
}
