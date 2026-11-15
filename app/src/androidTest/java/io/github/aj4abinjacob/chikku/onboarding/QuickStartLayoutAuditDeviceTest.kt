package io.github.aj4abinjacob.chikku.onboarding

import android.app.KeyguardManager
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.DeviceTestSupport
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.CellPlacement
import io.github.aj4abinjacob.chikku.core.model.ContainerType
import io.github.aj4abinjacob.chikku.core.model.ItemTarget
import io.github.aj4abinjacob.chikku.core.model.Workspace
import io.github.aj4abinjacob.chikku.core.model.WorkspaceItem
import io.github.aj4abinjacob.chikku.core.model.WorkspaceRepository
import io.github.aj4abinjacob.chikku.core.model.WorkspaceState
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.home.HomeDependencies
import io.github.aj4abinjacob.chikku.home.homeDependencies
import io.github.aj4abinjacob.chikku.settings.ChikkuSettingsTheme
import io.github.aj4abinjacob.chikku.settings.LocalHomeDependencies
import io.github.aj4abinjacob.chikku.settings.SettingsActivity
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assume.assumeFalse
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic in-memory Quick start: no changes to the user's stored workspace or preferences. */
@RunWith(AndroidJUnit4::class)
class QuickStartLayoutAuditDeviceTest {
    @Test fun populatedQuickStartRequiresConfirmationAndLockedQuickStartCannotApply() {
        val context = DeviceTestSupport.targetContext
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.wakeUp()
        assumeFalse(
            "Unlock the test phone for visible UI",
            context.getSystemService(KeyguardManager::class.java).isKeyguardLocked,
        )
        val prefs = mutableStateOf(LauncherSettings())
        val items = listOf(
            WorkspaceItem(
                "desktop",
                CellPlacement(ContainerType.DESKTOP, 0, 4, 4),
                ItemTarget.App(DeviceTestSupport.settingsKey()),
            ),
            WorkspaceItem(
                "dock",
                CellPlacement(ContainerType.DOCK, 0, 4, 0),
                ItemTarget.App(DeviceTestSupport.settingsKey()),
            ),
        )
        val workspace = Workspace(prefs.value.desktopGrid, prefs.value.dockStorageGrid, 1, items)
        val source = MutableStateFlow<WorkspaceState>(WorkspaceState.Ready(workspace))
        val repository = object : WorkspaceRepository by context.homeDependencies().workspaceRepository {
            override val workspace = source
        }
        val deps = object : HomeDependencies by context.homeDependencies() {
            override val workspaceRepository = repository
        }
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    CompositionLocalProvider(LocalHomeDependencies provides deps) {
                        ChikkuSettingsTheme {
                            OnboardingScreen(prefs.value, true, { prefs.value = it(prefs.value) }, {}, {})
                        }
                    }
                }
            }
            fun click(resource: Int) {
                if (resource == R.string.onboarding_preset_extra_large) {
                    UiScrollable(UiSelector().scrollable(true)).scrollTextIntoView(context.getString(resource))
                }
                val button = device.wait(
                    Until.findObject(By.text(context.getString(resource))),
                    DeviceTestSupport.UI_TIMEOUT_MS,
                )
                assertThat(button).isNotNull()
                button.click()
                device.waitForIdle()
            }
            click(R.string.onboarding_next)
            click(R.string.onboarding_preset_extra_large)
            assertThat(
                device.wait(
                    Until.hasObject(By.text(context.getString(R.string.settings_grid_change_title))),
                    DeviceTestSupport.UI_TIMEOUT_MS,
                ),
            ).isTrue()
            assertThat(prefs.value.home.desktopColumns).isEqualTo(5)
            click(R.string.home_cancel)
            assertThat(prefs.value.home.desktopColumns).isEqualTo(5)
            assertThat((source.value as WorkspaceState.Ready).workspace).isEqualTo(workspace)
            click(R.string.onboarding_preset_extra_large)
            click(R.string.settings_grid_change_apply)
            assertThat(DeviceTestSupport.waitUntil { prefs.value.home.desktopColumns == 3 }).isTrue()
            assertThat(prefs.value.dock.iconCount).isEqualTo(4)
            assertThat((source.value as WorkspaceState.Ready).workspace.items).containsExactlyElementsIn(items)
            val detail = context.getString(R.string.onboarding_layout_preview_details, 4, 150, 140)
            assertThat(device.wait(Until.hasObject(By.descContains(detail)), DeviceTestSupport.UI_TIMEOUT_MS)).isTrue()

            scenario.onActivity {
                prefs.value =
                    LauncherSettings().let { it.copy(home = it.home.copy(layoutLocked = true)) }
            }
            assertThat(
                device.wait(
                    Until.hasObject(By.text(context.getString(R.string.home_layout_locked))),
                    DeviceTestSupport.UI_TIMEOUT_MS,
                ),
            ).isTrue()
            // A disabled row must not mutate preferences or open the confirmation dialog.
            click(R.string.onboarding_preset_extra_large)
            assertThat(prefs.value.home.desktopColumns).isEqualTo(5)
            assertThat(prefs.value.home.layoutLocked).isTrue()
            assertThat(device.hasObject(By.text(context.getString(R.string.settings_grid_change_title)))).isFalse()
        }
    }
}
