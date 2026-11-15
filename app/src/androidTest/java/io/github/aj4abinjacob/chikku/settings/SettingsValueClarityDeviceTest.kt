package io.github.aj4abinjacob.chikku.settings

import android.app.KeyguardManager
import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.aj4abinjacob.chikku.DeviceTestSupport
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.home.homeDependencies
import io.github.aj4abinjacob.chikku.settings.sections.AppDrawerSettingsSection
import io.github.aj4abinjacob.chikku.settings.sections.FoldersSettingsSection
import io.github.aj4abinjacob.chikku.settings.sections.HomeScreenSettingsSection
import io.github.aj4abinjacob.chikku.settings.sections.LabsSettingsSection
import io.github.aj4abinjacob.chikku.settings.sections.LookFeelSettingsSection
import io.github.aj4abinjacob.chikku.settings.sections.SearchSettingsSection
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real Compose settings with synthetic state: never writes settings, layouts or permissions. */
@RunWith(AndroidJUnit4::class)
class SettingsValueClarityDeviceTest {
    private val context get() = DeviceTestSupport.targetContext
    private val device get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private var activeSection: SettingsSection? = null
    private var activeTarget: String? = null

    @Before fun requireUnlockedScreen() {
        device.wakeUp()
        assumeFalse("Unlock the device to exercise settings", context.getSystemService(KeyguardManager::class.java).isKeyguardLocked)
    }

    @Test fun cornerControlsShowDpAndOpacityStillShowsPercentage() {
        val settings = LauncherSettings().let { it.copy(
            home = it.home.copy(widgetCornerRadiusDp = 16.7f),
            folders = it.folders.copy(cornerRadiusDp = 31.5f, backgroundAlpha = .7f),
            lookFeel = it.lookFeel.copy(menuCornerRadiusDp = 48f),
            search = it.search.copy(cornerRadiusDp = 12.5f, maxAppResults = 3),
        ) }
        for ((section, title, radius) in listOf(
            Triple(SettingsSection.HOME_SCREEN, R.string.settings_home_widget_corners, 16.7f),
            Triple(SettingsSection.FOLDERS, R.string.folders_custom_corner_radius, 31.5f),
            Triple(SettingsSection.LOOK_AND_FEEL, R.string.menu_settings_corner_radius, 48f),
            Triple(SettingsSection.SEARCH, R.string.search_settings_corner_radius, 12.5f),
        )) {
            activeTarget = "${context.getString(title)}: expected ${context.getString(R.string.settings_slider_dp_value, radius)}"
            show(section, settings) {
                // Search keeps its corner control on the "Colors and corners" sub-page.
                if (section == SettingsSection.SEARCH) scrollTo(R.string.search_page_look).click()
                if (section == SettingsSection.LOOK_AND_FEEL) scrollTo(R.string.look_header_menus).click()
                // Home widget options live on the Widgets sub-page.
                if (section == SettingsSection.HOME_SCREEN) scrollTo(R.string.settings_home_header_widgets).click()
                scrollTo(title)
                val expectedDp = context.getString(R.string.settings_slider_dp_value, radius)
                scrollToFullyVisible(By.text(expectedDp))
                assertObjectVisible(
                    By.text(expectedDp),
                    expected = expectedDp,
                )
                device.takeScreenshot(java.io.File(context.getExternalFilesDir(null),
                    "settings-clarity-${section.name.lowercase()}.png"))
                if (section == SettingsSection.FOLDERS) {
                    // The opacity row precedes the corner row and may have scrolled off screen.
                    scrollTo(R.string.folders_settings_background_alpha, down = false)
                    assertThat(device.hasObject(By.text(context.getString(R.string.settings_slider_percentage_value, 70)))).isTrue()
                }
                if (section == SettingsSection.SEARCH) {
                    // The main page summarises the app-results limit; the switch is on the Apps sub-page.
                    device.pressBack()
                    assertThat(device.wait(Until.hasObject(By.textContains(context.resources.getQuantityString(R.plurals.search_summary_one_row, 3, 3))),
                        DeviceTestSupport.UI_TIMEOUT_MS)).isTrue()
                    scrollTo(R.string.search_page_apps).click()
                    scrollTo(R.string.search_settings_one_row)
                    assertThat(device.hasObject(By.textContains(context.getString(R.string.settings_search_compact_count_summary, 3)))).isTrue()
                }
            }
        }
    }

    @Test fun savedLargeGridRemainsVisibleAndAutomaticDrawerHidesManualCounts() {
        val settings = LauncherSettings().let { it.copy(
            home = it.home.copy(desktopColumns = 10, desktopRows = 9),
            drawer = it.drawer.copy(columns = 0, rows = 11, automaticRowSpacing = true),
        ) }
        show(SettingsSection.HOME_SCREEN, settings) {
            scrollTo(R.string.settings_home_page_grid).click()
            scrollTo(R.string.settings_home_columns)
            assertThat(device.hasObject(By.text("10"))).isTrue()
            assertThat(device.hasObject(By.text("9"))).isTrue()
        }
        show(SettingsSection.APP_DRAWER, settings) {
            // Grid controls live on the drawer's Grid & icons sub-page.
            device.wait(Until.findObject(By.text(context.getString(R.string.drawer_page_grid))), DeviceTestSupport.UI_TIMEOUT_MS).click()
            assertThat(device.wait(Until.hasObject(By.textContains(context.getString(R.string.drawer_settings_auto_columns))),
                DeviceTestSupport.UI_TIMEOUT_MS)).isTrue()
            assertThat(device.hasObject(By.desc(context.getString(R.string.drawer_settings_columns)))).isFalse()
            assertThat(device.hasObject(By.desc(context.getString(R.string.drawer_settings_visible_rows)))).isFalse()
            device.findObject(By.textContains(context.getString(R.string.drawer_settings_fit_rows))).click()
            assertThat(device.wait(Until.hasObject(By.desc(context.getString(R.string.drawer_settings_visible_rows))),
                DeviceTestSupport.UI_TIMEOUT_MS)).isTrue()
            assertThat(device.hasObject(By.text("11"))).isTrue()
        }
    }

    @Test fun unfinishedCacheCannotBeToggledAndExtendedGridDescriptionIsActive() {
        show(SettingsSection.LABS, LauncherSettings()) { current ->
            val cache = device.wait(Until.findObject(By.textContains(context.getString(R.string.labs_faster_icon_cache))),
                DeviceTestSupport.UI_TIMEOUT_MS)
            assertThat(cache).isNotNull()
            // Text descendants can report enabled even when their containing toggle is disabled.
            // Exercise the real touch path instead of relying on that descendant's flag.
            cache.click()
            device.waitForIdle()
            assertThat(current().labs.fasterIconCache).isFalse()
            assertThat(device.hasObject(By.textContains(context.getString(R.string.settings_labs_extended_grid_summary)))).isTrue()
            device.findObject(By.textContains(context.getString(R.string.labs_extended_grid))).click()
            assertThat(DeviceTestSupport.waitUntil { current().labs.extendedGrid }).isTrue()
        }
    }

    private fun show(section: SettingsSection, settings: LauncherSettings, check: (() -> LauncherSettings) -> Unit) {
        activeSection = section
        activeTarget = activeTarget ?: "section content"
        val deps = context.homeDependencies()
        val state = mutableStateOf(settings)
        ActivityScenario.launch<SettingsActivity>(Intent(context, SettingsActivity::class.java)).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    CompositionLocalProvider(LocalHomeDependencies provides deps) {
                        ChikkuSettingsTheme {
                            Box(Modifier.systemBarsPadding()) {
                                val update: ((LauncherSettings) -> LauncherSettings) -> Unit = { state.value = it(state.value) }
                                when (section) {
                                    SettingsSection.HOME_SCREEN -> HomeScreenSettingsSection(state.value, update)
                                    SettingsSection.APP_DRAWER -> AppDrawerSettingsSection(state.value, update)
                                    SettingsSection.FOLDERS -> FoldersSettingsSection(state.value, update)
                                    SettingsSection.LOOK_AND_FEEL -> LookFeelSettingsSection(state.value, update)
                                    SettingsSection.SEARCH -> SearchSettingsSection(state.value, update)
                                    SettingsSection.LABS -> LabsSettingsSection(state.value, update)
                                    else -> error("Unsupported test section")
                                }
                            }
                        }
                    }
                }
            }
            device.waitForIdle()
            try {
                check { state.value }
            } catch (failure: Throwable) {
                val dump = captureFailureUi(section, activeTarget ?: "section content")
                throw AssertionError(
                    "Settings value clarity failure: section=$section, target=${activeTarget ?: "section content"}, " +
                        "syntheticFolderRadiusDp=${state.value.folders.cornerRadiusDp}; UI dump: $dump",
                    failure,
                )
            }
        }
        activeSection = null
        activeTarget = null
    }

    private fun scrollTo(title: Int, down: Boolean = true): UiObject2 {
        val label = context.getString(title)
        repeat(12) {
            device.findObject(By.desc(label))?.let { return it }
            device.findObject(By.text(label))?.let { return it }
            val viewport = largestSettingsViewport()
            swipeSettingsPane(viewport, moveDownContent = down)
            device.waitForIdle()
        }
        device.findObject(By.desc(label))?.let { return it }
        device.findObject(By.text(label))?.let { return it }

        val section = activeSection?.name ?: "UNKNOWN"
        val dump = captureFailureUi(activeSection ?: SettingsSection.HOME_SCREEN, label)
        throw AssertionError(
            "Settings value clarity target was not found after 12 scroll attempts: " +
                "section=$section, target='$label' (resourceId=$title); UI dump: $dump",
        )
    }

    private fun assertObjectVisible(selector: androidx.test.uiautomator.BySelector, expected: String) {
        val visible = device.hasObject(selector)
        assertWithMessage(
            "section=%s, target=%s, expected visible value=%s",
            activeSection?.name ?: "UNKNOWN",
            activeTarget ?: "unspecified",
            expected,
        ).that(visible).isTrue()
    }

    /** Places the formatted value itself fully inside the largest settings scroll viewport. */
    private fun scrollToFullyVisible(selector: androidx.test.uiautomator.BySelector): UiObject2 {
        val paneSelector = By.scrollable(true)
        val inset = (12 * context.resources.displayMetrics.density).toInt()
        val minimumTextHeight = (16 * context.resources.displayMetrics.density).toInt()
        repeat(24) { attempt ->
            device.waitForIdle()
            val candidate = device.findObject(selector)
            val pane = device.findObjects(paneSelector).maxByOrNull { it.visibleBounds.width() * it.visibleBounds.height() }
            if (pane == null) {
                // Some settings sub-pages fit their content without a scroll container.
                // In that case the exact expected value only needs to be fully on screen.
                val bounds = candidate?.visibleBounds
                if (bounds != null && bounds.height() >= minimumTextHeight &&
                    bounds.top >= inset && bounds.bottom <= device.displayHeight - inset
                ) return checkNotNull(candidate)
                val dump = captureFailureUi(activeSection ?: SettingsSection.HOME_SCREEN, activeTarget ?: selector.toString())
                throw AssertionError(
                    "No scroll viewport and expected value is not fully visible: $selector; UI dump: $dump",
                )
            }
            val viewport = pane.visibleBounds
            val usableTop = viewport.top + inset
            val usableBottom = viewport.bottom - inset
            if (candidate != null) {
                val bounds = candidate.visibleBounds
                if (bounds.height() >= minimumTextHeight && bounds.top >= usableTop && bounds.bottom <= usableBottom) {
                    return candidate
                }
                // If clipping tells us which side the value is on, move content toward it.
                // Otherwise alternate direction so a partially visible row cannot strand the test.
                val moveDownContent = when {
                    bounds.height() == 0 -> attempt % 2 == 1
                    bounds.top < usableTop -> true
                    bounds.bottom > usableBottom -> false
                    else -> attempt % 2 == 1
                }
                swipeSettingsPane(viewport, moveDownContent)
            } else {
                swipeSettingsPane(viewport, moveDownContent = attempt < 12)
            }
            device.waitForIdle()
            // Wait for accessibility to publish the newly exposed text before the next gesture.
            device.wait(Until.findObject(selector), 350)
        }
        val label = selector.toString()
        val dump = captureFailureUi(activeSection ?: SettingsSection.HOME_SCREEN, activeTarget ?: label)
        throw AssertionError("Expected slider value never became fully visible: $label; UI dump: $dump")
    }

    /** `moveDownContent` maps to an upward finger swipe within the viewport. */
    private fun swipeSettingsPane(viewport: android.graphics.Rect, moveDownContent: Boolean) {
        // The left gutter is inside the scroll container but outside the slider tracks.
        val x = viewport.left + (8 * context.resources.displayMetrics.density).toInt()
        val top = viewport.top + viewport.height() / 4
        val bottom = viewport.bottom - viewport.height() / 4
        val start = if (moveDownContent) bottom else top
        val end = if (moveDownContent) top else bottom
        device.swipe(x, start, x, end, 55)
    }

    private fun largestSettingsViewport(selector: androidx.test.uiautomator.BySelector = By.scrollable(true)): android.graphics.Rect {
        val pane = device.findObjects(selector).maxByOrNull { it.visibleBounds.width() * it.visibleBounds.height() }
            ?: throw AssertionError("Settings page has no scrollable viewport")
        return pane.visibleBounds
    }

    private fun captureFailureUi(section: SettingsSection, target: String): String {
        val safeSection = section.name.lowercase().replace(Regex("[^a-z0-9_-]"), "-")
        val safeLabel = target.lowercase().replace(Regex("[^a-z0-9_-]"), "-").take(80)
        val directory = context.getExternalFilesDir(null) ?: context.cacheDir
        val screenshot = java.io.File(directory, "settings-clarity-$safeSection-$safeLabel.png")
        val hierarchy = java.io.File(directory, "settings-clarity-$safeSection-$safeLabel.xml")
        runCatching { device.takeScreenshot(screenshot) }
        runCatching { device.dumpWindowHierarchy(hierarchy) }
        return hierarchy.absolutePath
    }
}
