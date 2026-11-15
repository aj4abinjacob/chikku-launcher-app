package io.github.aj4abinjacob.chikku.settings

import android.app.KeyguardManager
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.DeviceTestSupport
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.CellPlacement
import io.github.aj4abinjacob.chikku.core.model.ContainerType
import io.github.aj4abinjacob.chikku.core.model.LauncherApp
import io.github.aj4abinjacob.chikku.core.model.ProfileType
import io.github.aj4abinjacob.chikku.core.model.search.SearchResults
import io.github.aj4abinjacob.chikku.core.model.settings.DockLabelFont
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerOpenMethod
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerStyle
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerTabBarPosition
import io.github.aj4abinjacob.chikku.core.model.settings.FolderSettings
import io.github.aj4abinjacob.chikku.core.model.settings.FolderWindowStyle
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.core.model.settings.SearchPresentation
import io.github.aj4abinjacob.chikku.home.AppIconView
import io.github.aj4abinjacob.chikku.home.Availability
import io.github.aj4abinjacob.chikku.home.FolderEntryUi
import io.github.aj4abinjacob.chikku.home.HomeActivity
import io.github.aj4abinjacob.chikku.home.HomeItemUi
import io.github.aj4abinjacob.chikku.home.IconBinder
import io.github.aj4abinjacob.chikku.home.ItemContent
import io.github.aj4abinjacob.chikku.home.folder.FolderPopup
import io.github.aj4abinjacob.chikku.home.homeDependencies
import io.github.aj4abinjacob.chikku.onboarding.OnboardingActivity
import io.github.aj4abinjacob.chikku.search.SearchScreen
import io.github.aj4abinjacob.chikku.search.SearchTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Physical UI and synthetic previews; restores preferences and never edits the user's workspace. */
@RunWith(AndroidJUnit4::class)
class ExpandedSettingsDeviceTest {
    private val context get() = DeviceTestSupport.targetContext
    private val device get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Before fun requireUnlockedScreen() {
        device.wakeUp()
        assumeFalse("Unlock the device to exercise visible UI", context.getSystemService(KeyguardManager::class.java).isKeyguardLocked)
    }

    @Test fun settingsSearchFindsSearchAppearanceAndWritesWindowAndOneRowControls() = runBlocking {
        val deps = context.homeDependencies()
        val original = deps.settingsRepository.settings.first().search
        try {
            deps.settingsRepository.update { it.copy(search = it.search.copy(presentation = SearchPresentation.FULLSCREEN, maxAppResults = 0)) }
            ActivityScenario.launch<SettingsActivity>(Intent(context, SettingsActivity::class.java)).use { scenario ->
                try {
                    val search = device.wait(Until.findObject(By.desc(context.getString(R.string.settings_search_title))), DeviceTestSupport.UI_TIMEOUT_MS)
                    assertThat(search).isNotNull()
                    search.click()
                    val field = device.wait(Until.findObject(By.clazz("android.widget.EditText")), DeviceTestSupport.UI_TIMEOUT_MS)
                    assertThat(field).isNotNull()
                    field.text = context.getString(R.string.search_settings_presentation)
                    openSearchResult(R.string.settings_section_search)
                    assertThat(device.wait(Until.hasObject(By.text(context.getString(R.string.search_settings_window_header))), DeviceTestSupport.UI_TIMEOUT_MS)).isTrue()
                    device.findObject(By.text(context.getString(R.string.search_settings_presentation))).click()
                    val window = device.wait(Until.findObject(By.text(context.getString(R.string.search_settings_window))), DeviceTestSupport.UI_TIMEOUT_MS)
                    assertThat(window).isNotNull()
                    window.click()
                    assertThat(DeviceTestSupport.waitUntil { runBlocking { deps.settingsRepository.settings.first().search.presentation == SearchPresentation.WINDOW } }).isTrue()
                    // The one-row limit lives on the "Apps and shortcuts" sub-page.
                    scrollTo(context.getString(R.string.search_page_apps)).click()
                    scrollTo(context.getString(R.string.search_settings_one_row)).click()
                    assertThat(DeviceTestSupport.waitUntil { runBlocking { deps.settingsRepository.settings.first().search.maxAppResults == 5 } }).isTrue()
                    scenario.recreate()
                    // The sub-page survives recreation; Back returns to the Search main page.
                    assertThat(device.wait(Until.hasObject(By.text(context.getString(R.string.search_settings_one_row))), DeviceTestSupport.UI_TIMEOUT_MS)).isTrue()
                    device.pressBack()
                    assertThat(device.wait(Until.hasObject(By.text(context.getString(R.string.search_settings_window))), DeviceTestSupport.UI_TIMEOUT_MS)).isTrue()
                    // Pages carry no settings search: there, a magnifier reads as searching the page.
                    assertThat(device.hasObject(By.desc(context.getString(R.string.settings_search_title)))).isFalse()
                    // Search also routes new folder, badge and drawer controls to their owning pages.
                    for ((titleId, pageTitleId) in listOf(
                        R.string.folders_custom_window to R.string.settings_section_folders,
                        R.string.badges_custom_size to R.string.settings_section_badges,
                        R.string.drawer_settings_style to R.string.settings_section_drawer,
                    )) {
                        device.pressBack()
                        device.wait(Until.findObject(By.desc(context.getString(R.string.settings_search_title))), DeviceTestSupport.UI_TIMEOUT_MS).click()
                        val queryField = device.wait(Until.findObject(By.clazz("android.widget.EditText")), DeviceTestSupport.UI_TIMEOUT_MS)
                        queryField.text = context.getString(titleId)
                        openSearchResult(pageTitleId)
                        assertThat(device.wait(Until.hasObject(By.text(context.getString(pageTitleId))), DeviceTestSupport.UI_TIMEOUT_MS)).isTrue()
                        assertThat(scrollTo(context.getString(titleId))).isNotNull()
                    }
                } catch (failure: Throwable) {
                    // This workflow displays settings labels only; keep its failed UI available for diagnosis.
                    val directory = context.getExternalFilesDir(null)
                    runCatching { device.takeScreenshot(java.io.File(directory, "chikku-phone-settings-search-failure.png")) }
                    runCatching { device.dumpWindowHierarchy(java.io.File(directory, "chikku-phone-settings-search-failure.xml")) }
                    throw failure
                }
            }
        } finally {
            DeviceTestSupport.finishAll(SettingsActivity::class.java)
            deps.settingsRepository.update { it.copy(search = original) }
        }
    }

    @Test fun fullScreenAndWindowSearchRenderThreeSyntheticAppResults() {
        val deps = context.homeDependencies()
        val key = DeviceTestSupport.settingsKey()
        val samples = (1..8).map { LauncherApp(key.copy(className = "sample.Activity$it"), "Synthetic sample $it", ProfileType.PERSONAL) }
        for (mode in SearchPresentation.entries) {
            val settings = LauncherSettings(search = io.github.aj4abinjacob.chikku.core.model.settings.SearchSettings(
                presentation = mode, maxAppResults = 3, onlineSuggestions = false, privateKeyboard = true,
            ))
            ActivityScenario.launch<SettingsActivity>(Intent(context, SettingsActivity::class.java)).use { scenario ->
                scenario.onActivity { activity -> activity.setContent {
                    SearchTheme(settings) {
                        SearchScreen("synthetic", SearchResults("synthetic", apps = samples), deps.iconProvider, {}, {}, {}, settings = settings.search)
                    }
                } }
                assertThat(device.wait(Until.hasObject(By.text("Synthetic sample 3")), DeviceTestSupport.UI_TIMEOUT_MS)).isTrue()
                assertThat(device.findObjects(By.textStartsWith("Synthetic sample ")).size).isEqualTo(3)
                assertThat(device.hasObject(By.text("Synthetic sample 4"))).isFalse()
                // Captures only synthetic app names, never the device's app inventory.
                device.takeScreenshot(java.io.File(context.getExternalFilesDir(null), "chikku-phone-search-${mode.name.lowercase()}.png"))
            }
        }
    }

    @Test fun listDrawerBottomTabsLiveLabelsAndSyntheticImmersiveFolderUseNewPreferences() = runBlocking {
        val deps = context.homeDependencies()
        val originalSettings = deps.settingsRepository.settings.first()
        val original = originalSettings.drawer
        DeviceTestSupport.resetStartupGuard()
        var popup: FolderPopup? = null
        try {
            deps.settingsRepository.update { it.copy(onboarding = it.onboarding.copy(completed = true), drawer = it.drawer.copy(
                style = DrawerStyle.LIST, tabBarPosition = DrawerTabBarPosition.BOTTOM, openMethod = DrawerOpenMethod.BUTTON,
                showLabels = true, labelScale = 1.15f, labelFont = DockLabelFont.CONDENSED, labelShadow = true, labelSingleLine = false,
            )) }
            DeviceTestSupport.finishAll(OnboardingActivity::class.java)
            ActivityScenario.launch(HomeActivity::class.java).use { scenario ->
                val open = device.wait(Until.findObject(By.res(context.packageName, "all_apps_button")), DeviceTestSupport.UI_TIMEOUT_MS)
                assertThat(open).isNotNull()
                open.click()
                val field = device.wait(Until.findObject(By.res(context.packageName, "drawer_search")), DeviceTestSupport.UI_TIMEOUT_MS)
                assertThat(field).isNotNull()
                field.text = context.getString(R.string.settings_title)
                fun read(check: (HomeActivity) -> Boolean): Boolean {
                    var result = false
                    scenario.onActivity { result = check(it) }
                    return result
                }
                assertThat(DeviceTestSupport.waitUntil { read { activity ->
                    val list = activity.findViewById<RecyclerView>(R.id.drawer_list)
                    val icons = descendants(list).filterIsInstance<AppIconView>()
                    icons.any { it.listMode && it.labelScale == 1.15f && it.labelFont == DockLabelFont.CONDENSED }
                } }).isTrue()
                scenario.onActivity { activity ->
                    val list = activity.findViewById<RecyclerView>(R.id.drawer_list)
                    assertThat((list.layoutManager as GridLayoutManager).spanCount).isEqualTo(1)
                    val drawer = activity.findViewById<LinearLayout>(R.id.drawer)
                    assertThat(drawer.indexOfChild(activity.findViewById(R.id.drawer_tabs_scroll)))
                        .isGreaterThan(drawer.indexOfChild(activity.findViewById(R.id.drawer_body)))
                }
                deps.settingsRepository.update { it.copy(drawer = it.drawer.copy(labelScale = 1.35f, showLabels = false)) }
                assertThat(DeviceTestSupport.waitUntil { read { activity ->
                    descendants(activity.findViewById<RecyclerView>(R.id.drawer_list)).filterIsInstance<AppIconView>()
                        .any { it.labelScale == 1.35f && !it.showLabel }
                } }).isTrue()
                device.pressBack()
                scenario.onActivity { activity ->
                    val root = activity.findViewById<FrameLayout>(R.id.home_root)
                    val binder = IconBinder(activity, activity.lifecycleScope, deps.iconProvider)
                    popup = FolderPopup(root, binder, NoEditsListener())
                    val key = DeviceTestSupport.settingsKey()
                    val item = HomeItemUi("test-folder-only", CellPlacement(ContainerType.DESKTOP, 0, 0, 0), ItemContent.Folder(
                        "Synthetic preview", (1..3).map { FolderEntryUi("sample-$it", ItemContent.App(key, "Folder sample $it", false, Availability.AVAILABLE)) },
                    ))
                    popup!!.open(item, FolderSettings(windowStyle = FolderWindowStyle.IMMERSIVE, columns = 3, iconScale = 1.2f,
                        labelScale = 1.3f, showName = false, showLabels = false, labelSingleLine = false, windowShadow = true, cornerRadiusDp = 28f))
                    val panel = root.getChildAt(root.childCount - 1)
                    assertThat(panel.layoutParams.width).isEqualTo(ViewGroup.LayoutParams.MATCH_PARENT)
                    assertThat(panel.elevation).isGreaterThan(0f)
                    val icons = descendants(panel).filterIsInstance<AppIconView>()
                    assertThat(icons.size).isEqualTo(3)
                    assertThat(icons.all { !it.showLabel && it.iconScale == 1.2f && it.labelScale == 1.3f }).isTrue()
                }
                assertThat(device.wait(Until.hasObject(By.desc("Folder sample 1")), DeviceTestSupport.UI_TIMEOUT_MS)).isTrue()
                device.wait(Until.findObject(By.text(context.getString(R.string.folder_close_button))), DeviceTestSupport.UI_TIMEOUT_MS).click()
                assertThat(device.wait(Until.gone(By.desc("Folder sample 1")), DeviceTestSupport.UI_TIMEOUT_MS)).isTrue()
            }
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { popup?.close() }
            DeviceTestSupport.finishAll(HomeActivity::class.java)
            DeviceTestSupport.finishAll(OnboardingActivity::class.java)
            deps.settingsRepository.update { it.copy(drawer = original, onboarding = originalSettings.onboarding) }
            DeviceTestSupport.resetStartupGuard()
        }
    }

    private fun openSearchResult(sectionTitleId: Int) {
        // The query field contains the preference title too. Only the result has the navigation summary.
        val summary = context.getString(R.string.settings_search_open_section, context.getString(sectionTitleId))
        val result = device.wait(Until.findObject(By.textContains(summary)), DeviceTestSupport.UI_TIMEOUT_MS)
        assertThat(result).isNotNull()
        result.click()
    }

    private fun scrollTo(label: String): androidx.test.uiautomator.UiObject2 {
        fun visibleTarget() = device.findObject(By.text(label))
        visibleTarget()?.let { return it }
        // A section keeps its scroll offset when returning to it from Settings Search. Scan toward
        // the start first, then toward the end, so a prior visit at either extreme cannot hide a
        // real control. Choice previews can also expose horizontal scrollables, so select the
        // largest vertical page container.
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
                visibleTarget()?.let { return it }
                if (towardStart) device.swipe(x, top, x, bottom, 25)
                else device.swipe(x, bottom, x, top, 25)
                device.waitForIdle()
            }
        }
        return checkNotNull(visibleTarget()) {
            "Preference '$label' was not visible after scrolling its settings page"
        }
    }

    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup) {
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) }
    } else emptyList()

    private class NoEditsListener : FolderPopup.Listener {
        override fun onEntryClick(folder: HomeItemUi, entry: FolderEntryUi, view: View) = Unit
        override fun onEntryLongClick(folder: HomeItemUi, entry: FolderEntryUi, view: View) = false
        override fun onReorder(folderId: String, orderedEntryIds: List<String>) = Unit
        override fun onRename(folderId: String, title: String) = Unit
        override fun onMoveToHome(folderId: String, entryId: String) = Unit
        override fun onRemoveEntry(folderId: String, entryId: String) = Unit
        override fun onEdit(folder: HomeItemUi) = Unit
        override fun onOpenChanged(open: Boolean) = Unit
    }
}
