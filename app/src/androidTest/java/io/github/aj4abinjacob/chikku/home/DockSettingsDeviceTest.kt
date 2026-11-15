package io.github.aj4abinjacob.chikku.home

import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.DeviceTestSupport
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.GestureAction
import io.github.aj4abinjacob.chikku.core.model.settings.DockBackground
import io.github.aj4abinjacob.chikku.core.model.settings.DockCorner
import io.github.aj4abinjacob.chikku.core.model.settings.DockCornerShape
import io.github.aj4abinjacob.chikku.core.model.settings.PaddingSize
import io.github.aj4abinjacob.chikku.settings.SettingsActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/** Physical dock swipe, persistence, overlay and launch/return workflow; restores edited preferences. */
@RunWith(AndroidJUnit4::class)
class DockSettingsDeviceTest {
    @Test
    fun dockSwipesWrapAndRememberAcrossRecreationWithOverlayAndAutoClose() = runBlocking {
        val context = DeviceTestSupport.targetContext
        val deps = context.homeDependencies()
        val original = deps.settingsRepository.settings.first()
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        DeviceTestSupport.resetStartupGuard()
        try {
            deps.settingsRepository.update { current -> current.copy(
                dock = current.dock.copy(enabled = true, iconCount = 4, pages = 3, matchDesktopSize = true,
                    horizontalPadding = PaddingSize.SMALL, verticalPadding = PaddingSize.MEDIUM,
                    showLabels = true, labelScale = 1.2f, labelSingleLine = false, infiniteScroll = true,
                    rememberPosition = true, lastPage = 1, displayAsOverlay = true, automaticallyClose = true,
                    background = DockBackground.CUSTOM, backgroundEnabled = true, drawBehindNavigationBar = true,
                    topLeftCorner = DockCorner(0.6f, DockCornerShape.CUT)),
                gestures = current.gestures.copy(doubleTap = GestureAction.ToggleDock, swipeUp = GestureAction.OpenDrawer),
            ) }
            ActivityScenario.launch(HomeActivity::class.java).use { scenario ->
                fun read(check: (HomeActivity) -> Boolean): Boolean {
                    var result = false
                    scenario.onActivity { result = check(it) }
                    return result
                }
                fun await(check: (HomeActivity) -> Boolean) {
                    assertThat(DeviceTestSupport.waitUntil { read(check) }).isTrue()
                }
                await { it.findViewById<CellLayout>(R.id.dock).isShown && it.findViewById<CellLayout>(R.id.dock).columnOffset == 4 }
                scenario.onActivity { activity ->
                    val dock = activity.findViewById<CellLayout>(R.id.dock)
                    assertThat(dock.parent).isSameInstanceAs(activity.findViewById<View>(R.id.workspace_area))
                    assertThat(dock.grid.columns).isEqualTo(4)
                    assertThat(dock.paddingTop).isGreaterThan(0)
                }
                fun swipeBack() {
                    val rect = android.graphics.Rect()
                    scenario.onActivity { it.findViewById<View>(R.id.dock).getGlobalVisibleRect(rect) }
                    device.swipe(rect.left + rect.width() / 5, rect.centerY(), rect.right - rect.width() / 5, rect.centerY(), 20)
                }
                swipeBack()
                await { it.findViewById<CellLayout>(R.id.dock).columnOffset == 0 }
                swipeBack()
                await { it.findViewById<CellLayout>(R.id.dock).columnOffset == 8 }
                assertThat(DeviceTestSupport.waitUntil { runBlocking { deps.settingsRepository.settings.first().dock.lastPage == 2 } }).isTrue()
                scenario.recreate()
                await { it.findViewById<CellLayout>(R.id.dock).columnOffset == 8 && it.findViewById<View>(R.id.dock).isShown }
                // Launch only Chikku's own settings through its drawer, then return to this HOME.
                val workspaceRect = android.graphics.Rect()
                scenario.onActivity { it.findViewById<View>(R.id.workspace_pager).getGlobalVisibleRect(workspaceRect) }
                device.swipe(workspaceRect.centerX(), workspaceRect.bottom - 30,
                    workspaceRect.centerX(), workspaceRect.top + 100, 30)
                val search = device.wait(Until.findObject(By.res(context.packageName, "drawer_search")), DeviceTestSupport.UI_TIMEOUT_MS)
                search.text = context.getString(R.string.settings_title)
                val settingsIcon = device.wait(Until.findObject(By.desc(context.getString(R.string.settings_title))), DeviceTestSupport.UI_TIMEOUT_MS)
                assertThat(settingsIcon).isNotNull()
                settingsIcon.click()
                assertThat(DeviceTestSupport.waitForResumed(SettingsActivity::class.java)).isNotNull()
                device.pressBack()
                await { it.findViewById<View>(R.id.dock).visibility == View.GONE }
                repeat(4) {
                    if (read { it.findViewById<View>(R.id.drawer).visibility == View.VISIBLE }) {
                        device.pressBack()
                        device.waitForIdle()
                    }
                }
                await { it.findViewById<View>(R.id.drawer).visibility != View.VISIBLE }
                // A mapped double tap is the user-facing way back to an automatically closed dock.
                val rect = android.graphics.Rect()
                scenario.onActivity { it.findViewById<View>(R.id.workspace_pager).getGlobalVisibleRect(rect) }
                device.click(rect.centerX(), rect.centerY())
                device.click(rect.centerX(), rect.centerY())
                await { it.findViewById<View>(R.id.dock).isShown }
            }
        } finally {
            DeviceTestSupport.finishAll(SettingsActivity::class.java)
            DeviceTestSupport.finishAll(HomeActivity::class.java)
            deps.settingsRepository.update { it.copy(dock = original.dock, gestures = it.gestures.copy(doubleTap = original.gestures.doubleTap, swipeUp = original.gestures.swipeUp)) }
            DeviceTestSupport.resetStartupGuard()
        }
    }
}
