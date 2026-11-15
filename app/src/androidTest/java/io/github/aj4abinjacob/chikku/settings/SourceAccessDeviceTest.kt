package io.github.aj4abinjacob.chikku.settings

import android.content.ClipboardManager
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.DeviceTestSupport
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.platform.SOURCE_DOWNLOAD_URL
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SourceAccessDeviceTest {
    @Test fun sourceLinkCopiesOnlyAfterTapAndKeepsTheReaderInTheLauncher() {
        val context = DeviceTestSupport.targetContext
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        try {
            ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
                scenario.onActivity { activity -> activity.setContent {
                    MaterialTheme { LegalDocumentDialog(LegalDocument.SOURCE) {} }
                } }
                val copy = device.wait(Until.findObject(By.text(context.getString(R.string.legal_copy_source))),
                    DeviceTestSupport.UI_TIMEOUT_MS)
                assertThat(copy).isNotNull()
                copy.click()
                assertThat(device.wait(Until.hasObject(By.text(context.getString(R.string.legal_source_copied))),
                    DeviceTestSupport.UI_TIMEOUT_MS)).isTrue()
                scenario.onActivity { activity ->
                    val clip = activity.getSystemService(ClipboardManager::class.java).primaryClip
                    assertThat(clip?.getItemAt(0)?.text?.toString()).isEqualTo(SOURCE_DOWNLOAD_URL)
                }
                assertThat(DeviceTestSupport.waitUntil { device.currentPackageName == context.packageName }).isTrue()
                scenario.onActivity { activity -> activity.setContent {
                    MaterialTheme { LegalDocumentDialog(LegalDocument.PRIVACY) {} }
                } }
                // setContent schedules composition; idle input alone can still expose the old tree.
                assertThat(device.wait(Until.gone(By.text(context.getString(R.string.legal_copy_source))),
                    DeviceTestSupport.UI_TIMEOUT_MS)).isTrue()
                assertThat(device.hasObject(By.text(context.getString(R.string.legal_copy_source)))).isFalse()
            }
        } finally { DeviceTestSupport.finishAll(SettingsActivity::class.java) }
    }
}
