package io.github.aj4abinjacob.chikku.settings

import androidx.activity.compose.setContent
import android.app.KeyguardManager
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.DeviceTestSupport
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.*
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.home.HomeDependencies
import io.github.aj4abinjacob.chikku.home.homeDependencies
import io.github.aj4abinjacob.chikku.settings.components.layoutChangeControl
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test
import org.junit.Assume.assumeFalse
import org.junit.runner.RunWith

/** Native Compose confirmation using an in-memory fixture; no saved preferences/layout are edited. */
@RunWith(AndroidJUnit4::class)
class LayoutChangeDeviceTest {
    @Test fun shrinkingRequiresExplicitConfirmationAndCancelPreservesTheLayout() {
        val context = DeviceTestSupport.targetContext
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.wakeUp()
        assumeFalse("Unlock the test phone for visible UI", context.getSystemService(KeyguardManager::class.java).isKeyguardLocked)
        val prefs = mutableStateOf(LauncherSettings())
        val item = WorkspaceItem("synthetic", CellPlacement(ContainerType.DESKTOP, 0, 4, 0), ItemTarget.App(DeviceTestSupport.settingsKey()))
        val source = MutableStateFlow<WorkspaceState>(WorkspaceState.Ready(Workspace(prefs.value.desktopGrid, prefs.value.dockStorageGrid, 1, listOf(item))))
        val repository = object : WorkspaceRepository by context.homeDependencies().workspaceRepository { override val workspace = source }
        val deps = object : HomeDependencies by context.homeDependencies() { override val workspaceRepository = repository }
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent {
                CompositionLocalProvider(LocalHomeDependencies provides deps) {
                    val control = layoutChangeControl(prefs.value) { prefs.value = it(prefs.value) }
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Button(enabled = control.ready, onClick = { control.request { it.copy(home = it.home.copy(desktopColumns = 3)) } }) { Text("Shrink test grid") }
                    }
                }
            } }
            fun open() {
                val button = device.wait(Until.findObject(By.text("Shrink test grid")), DeviceTestSupport.UI_TIMEOUT_MS)
                assertThat(button).isNotNull()
                button.click()
                assertThat(device.wait(Until.hasObject(By.text(context.getString(R.string.settings_grid_change_title))), DeviceTestSupport.UI_TIMEOUT_MS)).isTrue()
                assertThat(prefs.value.home.desktopColumns).isEqualTo(5)
            }
            open()
            device.findObject(By.text(context.getString(R.string.home_cancel))).click()
            assertThat(device.wait(Until.gone(By.text(context.getString(R.string.settings_grid_change_title))), DeviceTestSupport.UI_TIMEOUT_MS)).isTrue()
            assertThat(prefs.value.home.desktopColumns).isEqualTo(5)
            open()
            device.findObject(By.text(context.getString(R.string.settings_grid_change_apply))).click()
            assertThat(DeviceTestSupport.waitUntil { prefs.value.home.desktopColumns == 3 }).isTrue()
            assertThat((source.value as WorkspaceState.Ready).workspace.items).containsExactly(item)
        }
    }
}
