package io.github.aj4abinjacob.chikku.settings

import android.app.KeyguardManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.activity.compose.setContent
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.DeviceTestSupport
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.AppCustomizationRepository
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.GestureAction
import io.github.aj4abinjacob.chikku.core.model.cards.AutomationCard
import io.github.aj4abinjacob.chikku.core.model.cards.CardContent
import io.github.aj4abinjacob.chikku.core.model.settings.NightMode
import io.github.aj4abinjacob.chikku.core.model.AppsRepository
import io.github.aj4abinjacob.chikku.core.model.AppsState
import io.github.aj4abinjacob.chikku.core.model.DrawerGroupRepository
import io.github.aj4abinjacob.chikku.core.model.Workspace
import io.github.aj4abinjacob.chikku.core.model.WorkspaceRepository
import io.github.aj4abinjacob.chikku.core.model.WorkspaceState
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerStyle
import io.github.aj4abinjacob.chikku.core.model.settings.HomeSearchBarPosition
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.core.model.settings.SettingsRepository
import io.github.aj4abinjacob.chikku.home.HomeDependencies
import io.github.aj4abinjacob.chikku.home.homeDependencies
import io.github.aj4abinjacob.chikku.platform.AppLauncher
import io.github.aj4abinjacob.chikku.platform.LaunchResult
import io.github.aj4abinjacob.chikku.settings.cards.CardEditorDialog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeFalse
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.lang.reflect.Proxy

/** Native settings controls with in-memory preferences and repositories that reject every write. */
@RunWith(AndroidJUnit4::class)
class LivePreviewsDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = DeviceTestSupport.targetContext
    private val device = UiDevice.getInstance(instrumentation)

    @Test fun dockPagesPreviewShowsThreePagesBeforeReleaseAndSupportsNavigation() {
        val defaults = LauncherSettings()
        val initial = defaults.copy(dock = defaults.dock.copy(iconCount = 4, pages = 1,
            infiniteScroll = false, rememberPosition = false))
        fixture(SettingsSection.DOCK, initial) { prefs, _, _ ->
            scrollTo(By.text(context.getString(R.string.settings_dock_page_apps))).click()
            val onePage = dockPreview(1, 1)
            val previewBounds = onePage.visibleBounds
            val before = image(onePage, "dock-pages-one")
            val slider = find(By.desc(context.getString(R.string.settings_dock_pages)))
            val sliderBounds = slider.visibleBounds
            dragHeld(slider) {
                val threePages = dockPreview(3, 3)
                assertThat(threePages.visibleBounds).isEqualTo(previewBounds)
                assertThat(find(By.desc(context.getString(R.string.settings_dock_pages))).visibleBounds).isEqualTo(sliderBounds)
                assertThat(prefs.value.dock.pages).isEqualTo(1)
                find(By.desc(context.getString(R.string.preview_dock_select_page, 1, 3)))
                find(By.desc(context.getString(R.string.preview_dock_select_page, 2, 3)))
                find(By.desc(context.getString(R.string.preview_dock_select_page, 3, 3)))
                val after = image(threePages, "dock-pages-three-held")
                assertThat(before.sameAs(after)).isFalse()
                before.recycle(); after.recycle()
            }
            assertThat(DeviceTestSupport.waitUntil { prefs.value.dock.pages == 3 }).isTrue()
            dockPreview(3, 3)
            find(By.desc(context.getString(R.string.preview_dock_select_page, 1, 3))).click()
            dockPreview(1, 3)
            find(By.desc(context.getString(R.string.preview_page_next))).click()
            val secondPage = dockPreview(2, 3).visibleBounds
            // Keep synthetic paging away from Android's edge Back gesture.
            device.swipe(secondPage.right - secondPage.width() / 4, secondPage.centerY(),
                secondPage.left + secondPage.width() / 4, secondPage.centerY(), 20)
            dockPreview(3, 3)
            find(By.desc(context.getString(R.string.preview_page_next))).click()
            dockPreview(3, 3)
            find(By.desc(context.getString(R.string.preview_dock_reopen))).click()
            dockPreview(1, 3)
            assertThat(prefs.value.dock.lastPage).isEqualTo(initial.dock.lastPage)
            image(dockPreview(1, 3), "dock-pages-selector", includeDockControls = true).recycle()
        }
    }

    @Test fun shrinkingDockPreviewToOnePageRemovesExtraSamplePages() {
        val defaults = LauncherSettings()
        val initial = defaults.copy(dock = defaults.dock.copy(iconCount = 4, pages = 3, rememberPosition = true, lastPage = 2))
        fixture(SettingsSection.DOCK, initial, fontScale = 2f) { prefs, _, _ ->
            scrollTo(By.text(context.getString(R.string.settings_dock_page_apps))).click()
            dockPreview(3, 3)
            val slider = scrollTo(By.desc(context.getString(R.string.settings_dock_pages)))
            dragHeld(slider, endFraction = .12f) {
                dockPreview(1, 1)
                assertThat(prefs.value.dock.pages).isEqualTo(3)
                assertThat(DeviceTestSupport.waitUntil {
                    !device.hasObject(By.desc(context.getString(R.string.preview_dock_select_page, 3, 3)))
                }).isTrue()
            }
            assertThat(DeviceTestSupport.waitUntil { prefs.value.dock.pages == 1 }).isTrue()
            dockPreview(1, 1)
            assertThat(prefs.value.dock.lastPage).isEqualTo(2)
            image(dockPreview(1, 1), "dock-pages-large-font", includeDockControls = true).recycle()
        }
    }

    @Test fun dockPreviewExplainsWhenOnlyTwoOfThreeRequestedPagesFit() {
        val defaults = LauncherSettings()
        val initial = defaults.copy(dock = defaults.dock.copy(iconCount = 5, pages = 1))
        fixture(SettingsSection.DOCK, initial) { prefs, _, _ ->
            scrollTo(By.text(context.getString(R.string.settings_dock_page_apps))).click()
            val slider = find(By.desc(context.getString(R.string.settings_dock_pages)))
            dragHeld(slider) {
                dockPreview(2, 2)
                find(By.text(context.getString(R.string.preview_dock_limited_caption, 2, 2, 3)))
                assertThat(device.hasObject(By.desc(context.getString(R.string.preview_dock_select_page, 3, 3)))).isFalse()
                assertThat(prefs.value.dock.pages).isEqualTo(1)
            }
            assertThat(DeviceTestSupport.waitUntil { prefs.value.dock.pages == 3 }).isTrue()
            dockPreview(2, 2)
        }
    }

    @Test fun drawerColumnsPreviewUpdatesBeforeReleaseAndRemainsPinned() {
        val defaults = LauncherSettings()
        val initial = defaults.copy(drawer = defaults.drawer.copy(style = DrawerStyle.HORIZONTAL_PAGED,
            columns = 3, rows = 4, automaticRowSpacing = false))
        fixture(SettingsSection.APP_DRAWER, initial) { prefs, _, _ ->
            find(By.text(context.getString(R.string.drawer_page_grid))).click()
            val preview = preview(R.string.preview_df_drawer_description)
            val originalDescription = preview.contentDescription
            val originalBounds = preview.visibleBounds
            val before = image(preview, "drawer-before")
            val slider = find(By.desc(context.getString(R.string.drawer_settings_columns)))
            dragHeld(slider) {
                assertThat(DeviceTestSupport.waitUntil { preview(R.string.preview_df_drawer_description).contentDescription != originalDescription }).isTrue()
                assertThat(prefs.value.drawer.columns).isEqualTo(3)
                val after = image(preview(R.string.preview_df_drawer_description), "drawer-drag")
                assertThat(before.sameAs(after)).isFalse()
                before.recycle(); after.recycle()
            }
            assertThat(DeviceTestSupport.waitUntil { prefs.value.drawer.columns > 3 }).isTrue()
            scrollControls()
            assertThat(preview(R.string.preview_df_drawer_description).visibleBounds).isEqualTo(originalBounds)
        }
    }

    @Test fun belowDockGapPreviewChangesDuringDragOnDockRoute() {
        val defaults = LauncherSettings()
        val initial = defaults.copy(home = defaults.home.copy(showSearchBar = true,
            searchBarPosition = HomeSearchBarPosition.BELOW_DOCK), search = defaults.search.copy(widgetDockGapDp = 12))
        fixture(SettingsSection.DOCK, initial) { prefs, _, _ ->
            scrollTo(By.text(context.getString(R.string.settings_dock_page_search))).click()
            val before = image(preview(R.string.preview_dock_search_description), "dock-gap-before")
            val slider = find(By.desc(context.getString(R.string.search_settings_bar_dock_gap)))
            dragHeld(slider) {
                assertThat(DeviceTestSupport.waitUntil {
                    !preview(R.string.preview_dock_search_description).contentDescription.contains("12 dp")
                }).isTrue()
                assertThat(prefs.value.search.widgetDockGapDp).isEqualTo(12)
                val after = image(preview(R.string.preview_dock_search_description), "dock-gap-drag")
                assertThat(before.sameAs(after)).isFalse()
                before.recycle(); after.recycle()
            }
            assertThat(DeviceTestSupport.waitUntil { prefs.value.search.widgetDockGapDp > 12 }).isTrue()
        }
    }

    @Test fun nightSurfaceToggleUpdatesTheVisibleSample() {
        val defaults = LauncherSettings()
        fixture(SettingsSection.NIGHT_MODE, defaults.copy(night = defaults.night.copy(mode = NightMode.ON, darkDrawer = true))) { prefs, _, _ ->
            val dark = context.getString(R.string.preview_appearance_night_surface_description,
                context.getString(R.string.settings_section_drawer), context.getString(R.string.preview_appearance_dark))
            val light = context.getString(R.string.preview_appearance_night_surface_description,
                context.getString(R.string.settings_section_drawer), context.getString(R.string.preview_appearance_light))
            val before = image(find(By.desc(dark)), "night-dark")
            scrollTo(By.text(context.getString(R.string.night_dark_drawer))).click()
            val after = image(find(By.desc(light)), "night-light")
            assertThat(prefs.value.night.darkDrawer).isFalse()
            assertThat(before.sameAs(after)).isFalse()
            before.recycle(); after.recycle()
        }
    }

    @Test fun largeFontGesturePracticeRecognizesInputWithoutExecutingTheAction() {
        val defaults = LauncherSettings()
        val initial = defaults.copy(gestures = defaults.gestures.copy(swipeDown = GestureAction.LaunchApp(AppKey("sample.preview", "sample.preview.Main", 0))))
        fixture(SettingsSection.GESTURES, initial, fontScale = 2f) { prefs, _, _ ->
            val pad = find(By.desc(context.getString(R.string.preview_gesture_touch_description)))
            val bounds = pad.visibleBounds
            device.swipe(bounds.centerX(), bounds.top + 8, bounds.centerX(), bounds.bottom - 8, 20)
            val expected = context.getString(R.string.preview_gesture_would_run,
                context.getString(R.string.gestures_swipe_down), context.getString(R.string.preview_gesture_launch_app))
            scrollTo(By.text(expected))
            assertThat(prefs.value).isEqualTo(initial)
            assertThat(device.findObjects(By.scrollable(true)).last().visibleBounds.height()).isGreaterThan(80)
            image(pad, "gesture-large-font").recycle()
        }
    }

    @Test fun cardWidgetHeightDraftUpdatesThePinnedPreviewWithoutSaving() {
        fixture(SettingsSection.CARDS, LauncherSettings()) { _, deps, scenario ->
            val card = AutomationCard("synthetic-preview-card", "Synthetic widget", content = CardContent.Widget(heightDp = 180))
            var saved = false
            val shown = mutableStateOf(true)
            scenario.onActivity { activity -> activity.setContent {
                ChikkuSettingsTheme {
                    CompositionLocalProvider(LocalHomeDependencies provides deps) {
                        if (shown.value) CardEditorDialog(card, emptyList(), emptyList(), false, false,
                            onSave = { saved = true }, onDismiss = { shown.value = false })
                    }
                }
            } }
            val initialDescription = context.getString(R.string.preview_cards_widget_height, 180)
            find(By.descContains(initialDescription))
            val slider = scrollTo(By.desc(context.getString(R.string.card_widget_height)))
            dragHeld(slider) {
                assertThat(DeviceTestSupport.waitUntil { !device.hasObject(By.descContains(initialDescription)) }).isTrue()
                assertThat(saved).isFalse()
                assertThat(device.hasObject(By.text(context.getString(R.string.preview_cards_hide)))).isTrue()
            }
            find(By.desc(context.getString(R.string.settings_cancel))).click()
            find(By.text(context.getString(R.string.cards_discard))).click()
            assertThat(saved).isFalse()
            assertThat(card.content).isEqualTo(CardContent.Widget(instanceId = (card.content as CardContent.Widget).instanceId, heightDp = 180))
        }
    }

    private fun fixture(section: SettingsSection, initial: LauncherSettings, fontScale: Float = 1f,
        block: (androidx.compose.runtime.MutableState<LauncherSettings>, HomeDependencies, ActivityScenario<SettingsActivity>) -> Unit) {
        device.wakeUp()
        assumeFalse("Unlock the test phone for visible UI", context.getSystemService(KeyguardManager::class.java).isKeyguardLocked)
        val real = context.homeDependencies()
        val before = runBlocking { real.settingsRepository.exportJson() }
        val prefs = mutableStateOf(initial)
        var prohibitedCalls = 0
        fun <T : Any> frozen(type: Class<T>, property: String, value: Any): T {
            @Suppress("UNCHECKED_CAST")
            return Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { proxy, method, arguments ->
                when (method.name) {
                    property -> value
                    "toString" -> "Synthetic preview repository"
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === arguments?.firstOrNull()
                    else -> { prohibitedCalls++; error("A preview called a repository operation") }
                }
            } as T
        }
        val deps = object : HomeDependencies by real {
            override val settingsRepository = frozen(SettingsRepository::class.java, "getSettings", MutableStateFlow(initial))
            override val workspaceRepository = frozen(WorkspaceRepository::class.java, "getWorkspace",
                MutableStateFlow<WorkspaceState>(WorkspaceState.Ready(Workspace(initial.desktopGrid, initial.dockStorageGrid, 3, emptyList()))))
            override val appsRepository = frozen(AppsRepository::class.java, "getApps", MutableStateFlow<AppsState>(AppsState.Loaded(emptyList())))
            override val appCustomizationRepository = frozen(AppCustomizationRepository::class.java, "getCustomizations",
                MutableStateFlow(emptyMap<AppKey, io.github.aj4abinjacob.chikku.core.model.AppCustomization>()))
            override val drawerGroupRepository = frozen(DrawerGroupRepository::class.java, "getGroups",
                MutableStateFlow(emptyList<io.github.aj4abinjacob.chikku.core.model.DrawerGroup>()))
            override val appLauncher = object : AppLauncher {
                override fun launch(key: AppKey, sourceBounds: android.graphics.Rect?): LaunchResult {
                    prohibitedCalls++; return LaunchResult.Unavailable(LaunchResult.Reason.UNKNOWN)
                }
                override fun openAppDetails(key: AppKey, sourceBounds: android.graphics.Rect?): LaunchResult {
                    prohibitedCalls++; return LaunchResult.Unavailable(LaunchResult.Reason.UNKNOWN)
                }
            }
        }
        try {
            ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val configuration = Configuration(activity.resources.configuration).apply { this.fontScale = fontScale }
                    val previewContext = activity.createConfigurationContext(configuration)
                    activity.setContent { ChikkuSettingsTheme {
                        CompositionLocalProvider(LocalHomeDependencies provides deps, LocalActivityResultRegistryOwner provides activity,
                            LocalContext provides previewContext,
                            LocalConfiguration provides configuration, LocalResources provides previewContext.resources,
                            LocalDensity provides Density(activity.resources.displayMetrics.density, fontScale)) {
                            SettingsRoot(prefs.value, { prefs.value = it(prefs.value) },
                                SettingsHost(false, "Synthetic preview", {}, {}), initialSection = section)
                        }
                    }
                } }
                try { block(prefs, deps, scenario) } catch (failure: Throwable) {
                    val directory = File(context.cacheDir, "live-preview-evidence").apply { mkdirs() }
                    device.dumpWindowHierarchy(File(directory, "synthetic-failure.xml"))
                    throw failure
                }
                assertThat(prohibitedCalls).isEqualTo(0)
            }
        } finally {
            // Boolean assertion prevents printing real serialized settings if preservation fails.
            assertThat(runBlocking { real.settingsRepository.exportJson() } == before).isTrue()
        }
    }

    private fun preview(resource: Int): UiObject2 = find(By.descContains(context.getString(resource).substringBefore('%')))
    private fun find(selector: BySelector): UiObject2 {
        val node = checkNotNull(device.wait(Until.findObject(selector), DeviceTestSupport.UI_TIMEOUT_MS))
        awaitDraw()
        return device.findObject(selector) ?: node
    }

    private fun dockPreview(page: Int, count: Int): UiObject2 =
        find(By.desc(context.getString(R.string.preview_dock_description, page, count)))

    private fun dragHeld(slider: UiObject2, endFraction: Float = .88f, inspect: () -> Unit) {
        val bounds = slider.visibleBounds
        val down = SystemClock.uptimeMillis()
        fun send(action: Int, x: Float) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, bounds.centerY().toFloat(), 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try { assertThat(instrumentation.uiAutomation.injectInputEvent(event, true)).isTrue() }
            finally { event.recycle() }
        }
        send(MotionEvent.ACTION_DOWN, bounds.centerX().toFloat())
        try {
            send(MotionEvent.ACTION_MOVE, bounds.left + bounds.width() * endFraction)
            inspect()
        } finally { send(MotionEvent.ACTION_UP, bounds.left + bounds.width() * endFraction) }
        device.waitForIdle()
    }

    private fun scrollControls() {
        val scroller = checkNotNull(device.findObjects(By.scrollable(true)).lastOrNull())
        val bounds = scroller.visibleBounds
        device.swipe(bounds.centerX(), bounds.bottom - bounds.height() / 4,
            bounds.centerX(), bounds.top + bounds.height() / 4, 24)
        device.waitForIdle()
    }

    private fun scrollTo(selector: BySelector): UiObject2 {
        repeat(12) {
            device.findObject(selector)?.takeIf { it.visibleBounds.height() > 0 }?.let { return it }
            scrollControls()
        }
        return find(selector)
    }

    private fun awaitDraw() {
        val frames = CountDownLatch(1)
        instrumentation.runOnMainSync {
            val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .first { it is SettingsActivity }
            val decor = activity.window.decorView
            decor.postOnAnimation { decor.postOnAnimation { frames.countDown() } }
        }
        assertThat(frames.await(5, TimeUnit.SECONDS)).isTrue()
    }

    private fun image(node: UiObject2, name: String, includeDockControls: Boolean = false): Bitmap {
        awaitDraw()
        val screen = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val bounds = node.visibleBounds
        if (includeDockControls) bounds.bottom = find(By.desc(context.getString(R.string.preview_page_next))).visibleBounds.bottom
        val cropped = Bitmap.createBitmap(screen, bounds.left, bounds.top, bounds.width(), bounds.height())
        if (screen !== cropped) screen.recycle()
        val directory = File(context.cacheDir, "live-preview-evidence").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { cropped.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return cropped
    }
}
