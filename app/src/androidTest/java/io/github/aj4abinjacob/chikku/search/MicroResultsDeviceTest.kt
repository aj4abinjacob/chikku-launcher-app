package io.github.aj4abinjacob.chikku.search

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.DeviceTestSupport
import io.github.aj4abinjacob.chikku.home.homeDependencies
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.settings.SettingsActivity
import io.github.aj4abinjacob.chikku.settings.SettingsSection
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Before
import org.junit.Assume.assumeFalse
import android.app.KeyguardManager
import org.junit.runner.RunWith

/** Real Compose display, navigation and recreation; no external app is launched and layout stays intact. */
@RunWith(AndroidJUnit4::class)
class MicroResultsDeviceTest {
    @Before
    fun requireUnlockedScreen() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.wakeUp()
        assumeFalse("Unlock the test device to exercise visible UI", DeviceTestSupport.targetContext
            .getSystemService(KeyguardManager::class.java).isKeyguardLocked)
    }

    @Test
    fun conversionPhoneAndFlightActionsAppearInSearchAndSurviveRecreation() = runBlocking {
        val context = DeviceTestSupport.targetContext
        val deps = context.homeDependencies()
        val original = deps.settingsRepository.settings.first().search
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        try {
            deps.settingsRepository.update { it.copy(search = it.search.copy(microResultsEnabled = true, onlineSuggestions = false)) }
            ActivityScenario.launch<SearchActivity>(SearchLauncher.intent(context, "30 teaspoons to tablespoons")).use { scenario ->
                assertThat(device.wait(Until.hasObject(By.text("10 tbsp")), DeviceTestSupport.UI_TIMEOUT_MS)).isTrue()
                device.takeScreenshot(java.io.File(context.getExternalFilesDir(null), "chikku-micro-results.png"))
                scenario.recreate()
                assertThat(device.wait(Until.hasObject(By.text("10 tbsp")), DeviceTestSupport.UI_TIMEOUT_MS)).isTrue()
                val field = device.findObject(By.clazz("android.widget.EditText"))
                assertThat(field).isNotNull()
                field.text = "+919876543210"
                assertThat(device.wait(Until.hasObject(By.text("Open dialer")), DeviceTestSupport.UI_TIMEOUT_MS)).isTrue()
                assertThat(device.wait(Until.hasObject(By.text("Write a text message")), DeviceTestSupport.UI_TIMEOUT_MS)).isTrue()
                field.text = "flight AI101"
                assertThat(device.wait(Until.hasObject(By.text("Track flight · Flightradar24")), DeviceTestSupport.UI_TIMEOUT_MS)).isTrue()
                field.text = "32F"
                assertThat(device.wait(Until.hasObject(By.text("0 °C")), DeviceTestSupport.UI_TIMEOUT_MS)).isTrue()
            }
        } finally {
            DeviceTestSupport.finishAll(SearchActivity::class.java)
            deps.settingsRepository.update { it.copy(search = original) }
        }
    }

    @Test
    fun settingsNavigateFromRootToHomeWidgetControls() {
        val context = DeviceTestSupport.targetContext
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        ActivityScenario.launch<SettingsActivity>(Intent(context, SettingsActivity::class.java)).use {
            val home = device.wait(Until.findObject(By.text("Home screen")), DeviceTestSupport.UI_TIMEOUT_MS)
            assertThat(home).isNotNull()
            device.takeScreenshot(java.io.File(context.getExternalFilesDir(null), "chikku-settings-root.png"))
            home.click()
            assertThat(device.wait(Until.hasObject(By.text(context.getString(R.string.settings_home_lock_layout))),
                DeviceTestSupport.UI_TIMEOUT_MS)).isTrue()
        }
        ActivityScenario.launch<SettingsActivity>(Intent(context, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_SECTION, SettingsSection.HOME_SCREEN.name)).use {
            // Widget controls live on the home screen's Widgets sub-page.
            val widgets = device.wait(Until.findObject(By.text(context.getString(R.string.settings_home_header_widgets))),
                DeviceTestSupport.UI_TIMEOUT_MS)
            assertThat(widgets).isNotNull()
            widgets.click()
            val corners = context.getString(R.string.settings_home_widget_corners)
            assertThat(device.wait(Until.hasObject(By.text(corners)), DeviceTestSupport.UI_TIMEOUT_MS)).isTrue()
            // Overlap is offered only while the layout is unlocked; otherwise one line explains why.
            val overlap = context.getString(R.string.settings_home_widget_overlap)
            val lockedInfo = context.getString(R.string.settings_home_widget_overlap_locked_info)
            repeat(4) {
                if (!device.hasObject(By.text(overlap)) && !device.hasObject(By.textContains(lockedInfo))) {
                    device.swipe(device.displayWidth / 2, device.displayHeight * 4 / 5,
                        device.displayWidth / 2, device.displayHeight / 3, 25)
                    device.waitForIdle()
                }
            }
            assertThat(device.hasObject(By.text(overlap)) || device.hasObject(By.textContains(lockedInfo))).isTrue()
            device.takeScreenshot(java.io.File(context.getExternalFilesDir(null), "chikku-widget-settings.png"))
        }
    }
}
