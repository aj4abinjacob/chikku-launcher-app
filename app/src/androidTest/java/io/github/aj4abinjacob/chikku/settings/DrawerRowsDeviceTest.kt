package io.github.aj4abinjacob.chikku.settings

import android.content.Intent
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.aj4abinjacob.chikku.DeviceTestSupport
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerOpenMethod
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerStyle
import io.github.aj4abinjacob.chikku.home.DrawerAdapter
import io.github.aj4abinjacob.chikku.home.HomeActivity
import io.github.aj4abinjacob.chikku.home.homeDependencies
import io.github.aj4abinjacob.chikku.onboarding.OnboardingActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/** Real preference controls and drawer measurement; restores settings and never edits placements. */
@RunWith(AndroidJUnit4::class)
class DrawerRowsDeviceTest {
    @Test
    fun chooseRowsInVerticalDrawerAndKeepSpacingAfterRecreation() = runBlocking {
        val context = DeviceTestSupport.targetContext
        val deps = context.homeDependencies()
        val originalSettings = deps.settingsRepository.settings.first()
        val original = originalSettings.drawer
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.wakeUp()
        DeviceTestSupport.resetStartupGuard()
        DeviceTestSupport.finishAll(OnboardingActivity::class.java)
        try {
            deps.settingsRepository.update { it.copy(
                onboarding = it.onboarding.copy(completed = true),
                drawer = it.drawer.copy(
                style = DrawerStyle.VERTICAL, automaticRowSpacing = true, rows = 5,
                columns = 2, openMethod = DrawerOpenMethod.BOTH, openKeyboardOnOpen = false,
            )) }
            val intent = Intent(context, SettingsActivity::class.java)
                .putExtra(SettingsActivity.EXTRA_SECTION, SettingsSection.APP_DRAWER.name)
            ActivityScenario.launch<SettingsActivity>(intent).use { scenario ->
                device.wait(Until.findObject(By.text(context.getString(R.string.drawer_page_grid))), DeviceTestSupport.UI_TIMEOUT_MS).click()
                val fitRows = device.wait(Until.findObject(By.text(context.getString(R.string.drawer_settings_fit_rows))), DeviceTestSupport.UI_TIMEOUT_MS)
                assertThat(fitRows).isNotNull()
                fitRows.click()
                assertThat(DeviceTestSupport.waitUntil {
                    runBlocking { !deps.settingsRepository.settings.first().drawer.automaticRowSpacing }
                }).isTrue()
                val slider = device.wait(Until.findObject(By.desc(context.getString(R.string.drawer_settings_visible_rows))), DeviceTestSupport.UI_TIMEOUT_MS)
                assertThat(slider).isNotNull()
                assertThat(slider.isEnabled).isTrue()
                val bounds = slider.visibleBounds
                device.click(bounds.left + bounds.width() / 3, bounds.centerY())
                assertThat(DeviceTestSupport.waitUntil {
                    runBlocking { deps.settingsRepository.settings.first().drawer.rows != 5 }
                }).isTrue()
                val chosenRows = deps.settingsRepository.settings.first().drawer.rows
                scenario.recreate()
                assertThat(device.wait(Until.hasObject(By.desc(context.getString(R.string.drawer_settings_visible_rows))), DeviceTestSupport.UI_TIMEOUT_MS)).isTrue()
                assertThat(deps.settingsRepository.settings.first().drawer.rows).isEqualTo(chosenRows)
            }
            deps.settingsRepository.update { it.copy(drawer = it.drawer.copy(rows = 3)) }
            ActivityScenario.launch(HomeActivity::class.java).use { scenario ->
                fun await(check: (HomeActivity) -> Boolean) {
                    var geometry = "Layout pending"
                    val reached = DeviceTestSupport.waitUntil {
                        var result = false
                        scenario.onActivity { activity ->
                            val list = activity.findViewById<RecyclerView>(R.id.drawer_list)
                            geometry = "shown=${list.isShown}, height=${list.height}, bottom=${list.paddingBottom}, " +
                                "cell=${list.getChildAt(0)?.height}, slot=${(list.adapter as? DrawerAdapter)?.rowHeightPx}"
                            result = check(activity)
                        }
                        result
                    }
                    assertWithMessage(geometry).that(reached).isTrue()
                }
                fun open() {
                    scenario.onActivity { activity ->
                        val button = activity.findViewById<View>(R.id.all_apps_button)
                        if (button.isShown) button.performClick()
                        else activity.findViewById<View>(R.id.recovery_open_drawer).performClick()
                    }
                }
                open()
                await { activity ->
                    val list = activity.findViewById<RecyclerView>(R.id.drawer_list)
                    list.isShown && visibleRowCenters(list) == 3
                }
                var threeRowHeight = 0
                scenario.onActivity { threeRowHeight = it.findViewById<RecyclerView>(R.id.drawer_list).getChildAt(0).height }
                deps.settingsRepository.update { it.copy(drawer = it.drawer.copy(rows = 4)) }
                await { activity ->
                    val list = activity.findViewById<RecyclerView>(R.id.drawer_list)
                    list.isShown && visibleRowCenters(list) == 4 &&
                        list.getChildAt(0).height in 1 until threeRowHeight
                }
                var fourRowHeight = 0
                scenario.onActivity { fourRowHeight = it.findViewById<RecyclerView>(R.id.drawer_list).getChildAt(0).height }
                scenario.recreate()
                open()
                await { activity ->
                    val list = activity.findViewById<RecyclerView>(R.id.drawer_list)
                    list.isShown && visibleRowCenters(list) == 4 &&
                        list.getChildAt(0).height == fourRowHeight
                }
            }
        } finally {
            DeviceTestSupport.finishAll(SettingsActivity::class.java)
            DeviceTestSupport.finishAll(HomeActivity::class.java)
            DeviceTestSupport.finishAll(OnboardingActivity::class.java)
            deps.settingsRepository.update { it.copy(drawer = original, onboarding = originalSettings.onboarding) }
            DeviceTestSupport.resetStartupGuard()
        }
    }

    /** Count rows whose item centers are in the usable viewport, independent of row-sizing math. */
    private fun visibleRowCenters(list: RecyclerView): Int {
        val layout = list.layoutManager as? GridLayoutManager ?: return 0
        val top = list.paddingTop
        val bottom = list.height - list.paddingBottom
        return (0 until list.childCount).mapNotNull { index ->
            val child = list.getChildAt(index)
            val centerY = child.top + child.height / 2
            if (centerY in top until bottom) layout.getPosition(child) / layout.spanCount else null
        }.distinct().size
    }
}
