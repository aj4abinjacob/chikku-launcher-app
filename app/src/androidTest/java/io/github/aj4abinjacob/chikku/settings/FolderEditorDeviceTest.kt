package io.github.aj4abinjacob.chikku.settings

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
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Folder settings page; does not mutate user settings or workspace placements. */
@RunWith(AndroidJUnit4::class)
class FolderEditorDeviceTest {
    @Test
    fun folderSettingsShowLivePreviewAndEditGuidance() {
        val context = DeviceTestSupport.targetContext
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val intent = Intent(context, SettingsActivity::class.java)
            .putExtra(SettingsActivity.EXTRA_SECTION, SettingsSection.FOLDERS.name)
        try {
            ActivityScenario.launch<SettingsActivity>(intent).use {
                // The pinned preview labels its closed and opened folder tiles.
                assertThat(device.wait(Until.hasObject(By.descContains(context.getString(R.string.folder_scene_description, 3))), DeviceTestSupport.UI_TIMEOUT_MS)).isTrue()
                // Editing guidance is the page footer, below the settings.
                val footer = By.text(context.getString(R.string.folders_settings_info))
                repeat(20) {
                    if (device.hasObject(footer)) return@repeat
                    val bounds = device.findObjects(By.scrollable(true)).last().visibleBounds
                    device.swipe(bounds.centerX(), bounds.bottom - bounds.height() / 4, bounds.centerX(), bounds.top + bounds.height() / 4, 24)
                    device.waitForIdle()
                }
                assertThat(device.hasObject(footer)).isTrue()
                assertThat(device.takeScreenshot(File(context.cacheDir, "folder-settings.png"))).isTrue()
            }
        } finally {
            DeviceTestSupport.finishAll(SettingsActivity::class.java)
        }
    }
}
