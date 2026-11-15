package io.github.aj4abinjacob.chikku.settings

import android.app.KeyguardManager
import android.content.Intent
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
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
import io.github.aj4abinjacob.chikku.core.model.settings.BadgeStyle
import io.github.aj4abinjacob.chikku.home.homeDependencies
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises real settings UI and persistence; restores preferences without editing placements. */
@RunWith(AndroidJUnit4::class)
class SettingsValuesDeviceTest {
    private val context get() = DeviceTestSupport.targetContext
    private val device get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Before fun requireUnlockedScreen() {
        device.wakeUp()
        assumeFalse(context.getSystemService(KeyguardManager::class.java).isKeyguardLocked)
    }

    @Test fun radiiKeepFractionalDpAcrossSettingsAndRecreation() = runBlocking {
        val repository = context.homeDependencies().settingsRepository
        val original = repository.settings.first()
        try {
            repository.update { it.copy(
                home = it.home.copy(widgetCornerRadiusDp = 20.9f),
                folders = it.folders.copy(cornerRadiusDp = 20.9f),
                lookFeel = it.lookFeel.copy(menuCornerRadiusDp = 20.9f),
                search = it.search.copy(cornerRadiusDp = 20.9f),
            ) }
            for ((section, titleId) in listOf(
                SettingsSection.HOME_SCREEN to R.string.settings_home_widget_corners,
                SettingsSection.FOLDERS to R.string.folders_custom_corner_radius,
                SettingsSection.LOOK_AND_FEEL to R.string.menu_settings_corner_radius,
                SettingsSection.SEARCH to R.string.search_settings_corner_radius,
            )) {
                open(section).use { scenario ->
                    val title = context.getString(titleId)
                    if (section == SettingsSection.SEARCH) {
                        // Search keeps its corner control on the "Colors and corners" sub-page,
                        // which stays open across recreation.
                        val page = context.getString(R.string.search_page_look)
                        scrollTo(page)
                        device.findObject(By.text(page)).click()
                    }
                    if (section == SettingsSection.HOME_SCREEN) openHomeWidgets()
                    if (section == SettingsSection.LOOK_AND_FEEL) {
                        val page = context.getString(R.string.look_header_menus)
                        scrollTo(page)
                        device.findObject(By.text(page)).click()
                    }
                    scrollTo(title, slider = true)
                    assertThat(device.wait(Until.hasObject(By.text("20.9 dp")), DeviceTestSupport.UI_TIMEOUT_MS)).isTrue()
                    assertThat(device.hasObject(By.text("2090%"))).isFalse()
                    device.takeScreenshot(java.io.File(context.getExternalFilesDir(null), "settings-values-${section.name.lowercase()}.png"))
                    scenario.recreate()
                    scrollTo(title, slider = true)
                    assertThat(device.wait(Until.hasObject(By.text("20.9 dp")), DeviceTestSupport.UI_TIMEOUT_MS)).isTrue()
                }
            }
        } finally {
            DeviceTestSupport.finishAll(SettingsActivity::class.java)
            repository.update { it.copy(home = original.home, folders = original.folders,
                lookFeel = original.lookFeel, search = original.search) }
        }
    }

    @Test fun widgetRadiusAcceptsFineAdjustmentAndSavesAfterAccessibilityAction() = runBlocking {
        val repository = context.homeDependencies().settingsRepository
        val original = repository.settings.first().home.widgetCornerRadiusDp
        try {
            open(SettingsSection.HOME_SCREEN).use { scenario ->
                val title = context.getString(R.string.settings_home_widget_corners)
                openHomeWidgets()
                scrollTo(title, slider = true)
                var slider: AccessibilityNodeInfo? = null
                val sliderFound = DeviceTestSupport.waitUntil {
                    slider = findSlider(InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow, title)
                    slider != null
                }
                assertWithMessage("Slider accessibility node missing after scroll. Target=%s; hierarchy=%s",
                    title, accessibilitySnapshot()).that(sliderFound).isTrue()
                val arguments = Bundle().apply {
                    putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, 12.5f)
                }
                assertThat(checkNotNull(slider).performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id, arguments)).isTrue()
                assertThat(DeviceTestSupport.waitUntil {
                    runBlocking { repository.settings.first().home.widgetCornerRadiusDp == 12.5f }
                }).isTrue()
                scenario.recreate()
                scrollTo(title, slider = true)
                assertThat(device.wait(Until.hasObject(By.text("12.5 dp")), DeviceTestSupport.UI_TIMEOUT_MS)).isTrue()
            }
        } finally {
            DeviceTestSupport.finishAll(SettingsActivity::class.java)
            repository.update { it.copy(home = it.home.copy(widgetCornerRadiusDp = original)) }
        }
    }

    @Test fun customBadgeSizeIsShownAndRemainsSelectable() = runBlocking {
        val repository = context.homeDependencies().settingsRepository
        val original = repository.settings.first().badges
        try {
            repository.update { it.copy(badges = it.badges.copy(style = BadgeStyle.COUNT, badgeScale = .9f)) }
            open(SettingsSection.BADGES).use {
                val title = context.getString(R.string.badges_custom_size)
                scrollTo(title)
                // The size is a stepped slider; a saved custom scale keeps its own step and label.
                assertThat(device.wait(Until.hasObject(By.text("Custom (90%)")), DeviceTestSupport.UI_TIMEOUT_MS)).isTrue()
                assertThat(device.hasObject(By.desc(title))).isTrue()
                assertThat(repository.settings.first().badges.badgeScale).isEqualTo(.9f)
            }
        } finally {
            DeviceTestSupport.finishAll(SettingsActivity::class.java)
            repository.update { it.copy(badges = original) }
        }
    }

    private fun open(section: SettingsSection): ActivityScenario<SettingsActivity> =
        ActivityScenario.launch(Intent(context, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_SECTION, section.name))

    /** Home widget options live on the Widgets sub-page, which stays open across recreation. */
    private fun openHomeWidgets() {
        val row = context.getString(R.string.settings_home_header_widgets)
        scrollTo(row)
        device.findObject(By.text(row)).click()
        device.waitForIdle()
    }

    private fun scrollTo(title: String, slider: Boolean = false) {
        assertThat(device.wait(Until.hasObject(By.pkg(context.packageName)), DeviceTestSupport.UI_TIMEOUT_MS)).isTrue()
        fun visibleTarget() = device.findObject(if (slider) By.desc(title) else By.text(title))
        visibleTarget()?.let { return }
        val bounds = device.findObjects(By.scrollable(true))
            .map { it.visibleBounds }
            .filter { it.height() > it.width() }
            .maxByOrNull { it.width().toLong() * it.height() }
        val inset = (24 * context.resources.displayMetrics.density).toInt()
        val top = bounds?.top?.plus(inset) ?: device.displayHeight / 3
        val bottom = bounds?.bottom?.minus(inset) ?: device.displayHeight * 4 / 5
        val x = bounds?.centerX() ?: device.displayWidth / 2
        for (towardStart in listOf(true, false)) {
            repeat(24) {
                visibleTarget()?.let { return }
                if (towardStart) device.swipe(x, top, x, bottom, 25)
                else device.swipe(x, bottom, x, top, 25)
                device.waitForIdle()
            }
        }
        error("Settings control is not visible after scanning both directions: $title")
    }

    private fun findSlider(node: AccessibilityNodeInfo?, title: String): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.rangeInfo != null && containsDescription(node, title)) return node
        for (index in 0 until node.childCount) findSlider(node.getChild(index), title)?.let { return it }
        return null
    }

    private fun containsDescription(node: AccessibilityNodeInfo, title: String): Boolean {
        if (node.contentDescription?.toString() == title) return true
        return (0 until node.childCount).any { index -> node.getChild(index)?.let { containsDescription(it, title) } == true }
    }

    private fun accessibilitySnapshot(): String {
        val root = InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow
            ?: return "<no active accessibility root>"
        val lines = mutableListOf<String>()
        fun visit(node: AccessibilityNodeInfo, depth: Int) {
            if (lines.size >= 160) return
            val text = node.text?.toString().orEmpty()
            val description = node.contentDescription?.toString().orEmpty()
            lines += "${" ".repeat(depth)}${node.className} text=$text desc=$description range=${node.rangeInfo != null}"
            for (index in 0 until node.childCount) node.getChild(index)?.let { visit(it, depth + 1) }
        }
        visit(root, 0)
        return lines.joinToString(" | ")
    }
}
