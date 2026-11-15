package io.github.aj4abinjacob.chikku.settings

import android.app.KeyguardManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.foundation.layout.statusBarsPadding
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

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import io.github.aj4abinjacob.chikku.core.model.DrawerGroup
import io.github.aj4abinjacob.chikku.core.model.DrawerSortMode
import io.github.aj4abinjacob.chikku.core.model.cards.CardConditions
import io.github.aj4abinjacob.chikku.core.model.cards.CardTimeRange
import io.github.aj4abinjacob.chikku.core.model.settings.WebSearchEngine
import io.github.aj4abinjacob.chikku.settings.previews.ContactCardLivePreview
import io.github.aj4abinjacob.chikku.settings.previews.SearchResultsLivePreview
import io.github.aj4abinjacob.chikku.settings.previews.SearchPreviewMode

/** Actual controls and synthetic scenes; real preferences/layouts/providers are never changed. */
@RunWith(AndroidJUnit4::class)
class PreviewGapsDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = DeviceTestSupport.targetContext
    private val device = UiDevice.getInstance(instrumentation)


    @Test fun searchSourcesAndFrequencyChangeThePinnedResultScene() {
        val base = LauncherSettings()
        val initial = base.copy(search = base.search.copy(foldersEnabled = false, shortcutsEnabled = false,
            playStoreRow = false, historyLearningEnabled = false, historySuggestionsEnabled = false))
        fixture(SettingsSection.SEARCH, initial) { prefs, _, _ ->
            scrollTo(By.text(context.getString(R.string.search_page_apps))).click()
            val before = image(find(By.descContains("Visible result rows:")), "gaps-search-before")
            val pinnedTop = find(By.descContains("Visible result rows:")).visibleBounds.top
            val firstControlBounds = find(By.text(context.getString(R.string.search_settings_one_row))).visibleBounds
            for (label in listOf(R.string.search_settings_folders, R.string.settings_search_shortcuts, R.string.settings_search_play_store)) {
                scrollTo(By.text(context.getString(label))).click()
            }
            assertThat(DeviceTestSupport.waitUntil { prefs.value.search.foldersEnabled && prefs.value.search.shortcutsEnabled && prefs.value.search.playStoreRow }).isTrue()
            val sources = find(By.descContains("Visible result rows:"))
            assertThat(sources.contentDescription).contains(context.getString(R.string.preview_search_folder_sample))
            assertThat(sources.contentDescription).contains(context.getString(R.string.preview_search_shortcut_sample))
            assertThat(sources.visibleBounds.top).isEqualTo(pinnedTop)
            // This shorter page fits without scrolling after history moved to Suggestions.
            // Measure the real first control rather than accessibility clipping of sample rows.
            assertThat(find(By.text(context.getString(R.string.search_settings_one_row))).visibleBounds)
                .isEqualTo(firstControlBounds)
            find(By.desc(context.getString(R.string.settings_back))).click()
            clickableParent(scrollTo(By.text(context.getString(R.string.settings_search_history_header)))).click()
            find(By.descContains(context.getString(R.string.preview_search_history_hidden)))
            scrollTo(By.text(context.getString(R.string.settings_search_history_show))).click()
            val ranked = find(By.descContains(context.getString(R.string.preview_search_history_shown)))
            image(ranked, "gaps-search-ranked").recycle()
            find(By.descContains(context.getString(R.string.preview_search_history_paused)))
            scrollTo(By.text(context.getString(R.string.settings_search_history_learn))).click()
            find(By.descContains(context.getString(R.string.preview_search_history_learning)))
            scrollTo(By.text(context.getString(R.string.settings_search_history_learn))).click()
            find(By.descContains(context.getString(R.string.preview_search_history_paused)))
            find(By.descContains(context.getString(R.string.preview_search_history_shown)))
            assertThat(prefs.value.search.historyLearningEnabled).isFalse()
            assertThat(prefs.value.search.historySuggestionsEnabled).isTrue()
            before.recycle()
        }
    }

    @Test fun contactCardUsesTheActualRendererWithSafeFictionalActionsAtLargeFont() {
        fixture(SettingsSection.SEARCH, LauncherSettings(), fontScale = 2f, rtl = true) { prefs, deps, scenario ->
            render(scenario, deps, fontScale = 2f, rtl = true) {
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.statusBarsPadding()) {
                    ContactCardLivePreview(prefs.value.copy(search = prefs.value.search.copy(contactsEnabled = true)))
                }
            }
            val descriptionPrefix = context.getString(R.string.preview_contact_card_description).substringBefore('%')
            val card = find(By.descStartsWith(descriptionPrefix))
            image(card, "gaps-contact-large-rtl").recycle()
            val bounds = card.visibleBounds
            device.click(bounds.centerX(), bounds.centerY())
            assertThat(device.currentPackageName).isEqualTo(context.packageName)
            scenario.onActivity { prefs.value = prefs.value.copy(search = prefs.value.search.copy(
                contactCardButtons = listOf("sms:message", "phone:voice_call"))) }
            val reordered = context.getString(R.string.preview_contact_card_description,
                context.getString(R.string.search_person_text) + ", " + context.getString(R.string.search_person_call))
            val changed = find(By.desc(reordered))
            image(changed, "gaps-contact-reordered-rtl").recycle()
            assertThat(changed.contentDescription).doesNotContain("WhatsApp")
        }
    }

    @Test fun dockAutocompleteTracksOnlineToggleAndUnsupportedEngine() {
        val base = LauncherSettings()
        val initial = base.copy(home = base.home.copy(showSearchBar = true, searchBarPosition = HomeSearchBarPosition.BELOW_DOCK),
            search = base.search.copy(showWebSuggestionRow = true, onlineSuggestions = false, webEngine = WebSearchEngine.GOOGLE))
        fixture(SettingsSection.DOCK, initial) { prefs, _, _ ->
            scrollTo(By.text(context.getString(R.string.settings_dock_page_search))).click()
            val off = find(By.descContains(context.getString(R.string.preview_search_online_off)))
            val before = image(off, "gaps-autocomplete-off")
            scrollTo(By.text(context.getString(R.string.settings_search_online_suggestions))).click()
            val on = find(By.descContains(context.getString(R.string.preview_search_online_on)))
            val after = image(on, "gaps-autocomplete-on")
            assertThat(before.sameAs(after)).isFalse()
            before.recycle(); after.recycle()
            scrollTo(By.text(context.getString(R.string.settings_search_engine))).click()
            find(By.text("Bing")).click()
            find(By.descContains(context.getString(R.string.preview_search_online_off)))
            assertThat(prefs.value.search.onlineSuggestions).isTrue()
            assertThat(prefs.value.search.webEngine).isEqualTo(WebSearchEngine.BING)
        }
    }

    @Test fun drawerOpeningDemonstratesAutoCloseAndRememberedPosition() {
        val base = LauncherSettings()
        val initial = base.copy(drawer = base.drawer.copy(automaticallyClose = false, rememberScrollPosition = false))
        fixture(SettingsSection.APP_DRAWER, initial) { prefs, _, _ ->
            clickableParent(scrollTo(By.text(context.getString(R.string.drawer_page_opening)))).click()
            clickPreviewButton(R.string.preview_gap_scroll)
            find(By.descContains(context.getString(R.string.preview_gap_drawer_showing, 2)))
            clickPreviewButton(R.string.preview_gap_launch)
            find(By.descContains(context.getString(R.string.preview_gap_app_showing, 2)))
            clickPreviewButton(R.string.preview_gap_return)
            find(By.descContains(context.getString(R.string.preview_gap_drawer_showing, 2)))
            scrollTo(By.text(context.getString(R.string.drawer_settings_automatically_close))).click()
            find(By.descContains(context.getString(R.string.preview_gap_home_showing, 2)))
            scrollTo(By.text(context.getString(R.string.drawer_settings_remember_position))).click()
            val remembered = find(By.descContains(context.getString(R.string.preview_gap_drawer_showing, 2)))
            image(remembered, "gaps-drawer-behaviour").recycle()
            scrollTo(By.text(context.getString(R.string.drawer_settings_remember_position))).click()
            find(By.descContains(context.getString(R.string.preview_gap_drawer_showing, 1)))
            assertThat(prefs.value.drawer.rememberScrollPosition).isFalse()
        }
    }

    @Test fun groupReorderingAndMemberSortingChangeSyntheticTabsAndOrder() {
        val keys = (0..3).map { AppKey("sample.$it", "sample.$it.Main", 0) }
        val groups = listOf(DrawerGroup("a", "Fixture A", 0, DrawerSortMode.MANUAL, keys),
            DrawerGroup("b", "Fixture B", 1, DrawerSortMode.MANUAL, keys))
        val base = LauncherSettings()
        fixture(SettingsSection.APP_DRAWER, base.copy(drawer = base.drawer.copy(groupsEnabled = true)), testGroups = groups) { _, _, _ ->
            scrollTo(By.text(context.getString(R.string.drawer_settings_groups))).click()
            val before = find(By.descStartsWith("Synthetic drawer. Tabs:"))
            val previousDescription = before.contentDescription
            scrollTo(By.desc(context.getString(R.string.drawer_groups_move_up, "Fixture B"))).click()
            assertThat(DeviceTestSupport.waitUntil { find(By.descStartsWith("Synthetic drawer. Tabs:")).contentDescription != previousDescription }).isTrue()
            scrollTo(By.text("Fixture A")).click()
            val manual = find(By.descStartsWith("Synthetic drawer. Tabs:"))
            val manualDescription = manual.contentDescription
            scrollTo(By.text(context.getString(R.string.drawer_groups_sort))).click()
            find(By.text(context.getString(R.string.drawer_groups_sort_alphabetical))).click()
            assertThat(DeviceTestSupport.waitUntil { find(By.descStartsWith("Synthetic drawer. Tabs:")).contentDescription != manualDescription }).isTrue()
            image(find(By.descStartsWith("Synthetic drawer. Tabs:")), "gaps-groups-sorted").recycle()
        }
    }

    @Test fun changingDrawerMenuActionsAutomaticallySelectsDrawerPreview() {
        fixture(SettingsSection.LOOK_AND_FEEL, LauncherSettings()) { prefs, _, _ ->
            clickableParent(find(By.text(context.getString(R.string.look_header_menus)))).click()
            scrollTo(By.desc(context.getString(R.string.preview_menu_scoped_control,
                context.getString(R.string.settings_section_drawer), context.getString(R.string.menu_settings_store)))).click()
            val drawerLabel = context.getString(R.string.settings_section_drawer)
            val scene = find(By.descStartsWith("$drawerLabel menu sample:"))
            assertThat(DeviceTestSupport.waitUntil { prefs.value.lookFeel.menuShowStoreDrawer }).isTrue()
            assertThat(scene.contentDescription).contains(context.getString(R.string.app_menu_store))
            image(scene, "gaps-menu-drawer").recycle()
        }
    }

    @Test fun appearanceSubpagesKeepEachPreviewBesideItsControls() {
        checkAppearancePreviewLayout(fontScale = 1f, rtl = false)
    }

    @Test fun appearancePreviewKeepsControlsStableWithLargeTextAndRtl() {
        checkAppearancePreviewLayout(fontScale = 2f, rtl = true)
    }

    private fun checkAppearancePreviewLayout(fontScale: Float, rtl: Boolean) {
        val initial = LauncherSettings()
        fixture(SettingsSection.LOOK_AND_FEEL, initial, fontScale = fontScale, rtl = rtl) { prefs, _, _ ->
            assertThat(device.hasObject(By.text(context.getString(R.string.preview_appearance_choose)))).isFalse()
            for ((title, description) in listOf(
                R.string.look_header_accent to By.descStartsWith("Theme sample for"),
                R.string.look_header_icons to By.desc(context.getString(R.string.preview_appearance_icons_description)),
                R.string.look_header_motion to By.desc(context.getString(R.string.preview_appearance_motion_description)),
                R.string.look_header_window to By.desc(context.getString(R.string.preview_appearance_screen_description)),
                R.string.look_header_menus to By.descStartsWith(context.getString(R.string.settings_section_home) + " menu sample:"),
            )) {
                scrollTo(By.text(context.getString(title))).click()
                val preview = find(description)
                val viewport = find(By.res("appearance-settings-controls")).visibleBounds
                assertThat(viewport.height()).isGreaterThan((device.displayHeight * .55f).toInt())
                image(preview, "appearance-page-$title-$fontScale").recycle()
                if (title == R.string.look_header_icons) {
                    val top = preview.visibleBounds.top
                    scrollTo(By.text(context.getString(R.string.look_reshape_legacy))).click()
                    find(description)
                    assertThat(find(By.res("appearance-settings-controls")).visibleBounds).isEqualTo(viewport)
                    assertThat(find(description).visibleBounds.top).isEqualTo(top)
                    assertThat(prefs.value.lookFeel.reshapeLegacyIcons).isNotEqualTo(initial.lookFeel.reshapeLegacyIcons)
                    // Restore only the synthetic fixture value before testing the next page.
                    instrumentation.runOnMainSync { prefs.value = initial }
                }
                device.pressBack()
                find(By.text(context.getString(R.string.look_page_accent_summary)))
                assertThat(device.hasObject(By.text(context.getString(R.string.preview_appearance_choose)))).isFalse()
            }
            assertThat(prefs.value).isEqualTo(initial)
        }
    }

    @Test fun customAccentShowsThemedControlsBeforeSavingAndCancelKeepsPreferences() {
        fixture(SettingsSection.LOOK_AND_FEEL, LauncherSettings()) { prefs, _, _ ->
            clickableParent(find(By.text(context.getString(R.string.look_header_accent)))).click()
            scrollTo(By.desc(context.getString(R.string.accent_custom))).click()
            val preview = find(By.descStartsWith("Theme sample for"))
            val description = preview.contentDescription
            val before = image(preview, "gaps-accent-before")
            val slider = find(By.desc(context.getString(R.string.accent_hue)))
            dragHeld(slider) {
                assertThat(DeviceTestSupport.waitUntil { find(By.descStartsWith("Theme sample for")).contentDescription != description }).isTrue()
                assertThat(prefs.value.lookFeel.accentColor).isNull()
                val after = image(find(By.descStartsWith("Theme sample for")), "gaps-accent-drag")
                assertThat(before.sameAs(after)).isFalse()
                before.recycle(); after.recycle()
            }
            find(By.text(context.getString(R.string.settings_cancel))).click()
            assertThat(prefs.value.lookFeel.accentColor).isNull()
        }
    }

    @Test fun menuCornerDraftChangesPreviewBeforeReleaseWithoutMovingControls() {
        fixture(SettingsSection.LOOK_AND_FEEL, LauncherSettings()) { prefs, _, _ ->
            clickableParent(find(By.text(context.getString(R.string.look_header_menus)))).click()
            val selector = By.descStartsWith(context.getString(R.string.settings_section_home) + " menu sample:")
            val scene = find(selector)
            val before = image(scene, "appearance-menu-before")
            val top = scene.visibleBounds.top
            val savedRadius = prefs.value.lookFeel.menuCornerRadiusDp
            val slider = scrollTo(By.desc(context.getString(R.string.menu_settings_corner_radius)))
            val viewport = device.findObjects(By.scrollable(true)).last().visibleBounds
            dragHeld(slider) {
                val changed = find(selector)
                val after = image(changed, "appearance-menu-held-drag")
                assertThat(before.sameAs(after)).isFalse()
                before.recycle(); after.recycle()
                assertThat(prefs.value.lookFeel.menuCornerRadiusDp).isEqualTo(savedRadius)
                assertThat(changed.visibleBounds.top).isEqualTo(top)
                assertThat(device.findObjects(By.scrollable(true)).last().visibleBounds).isEqualTo(viewport)
            }
            assertThat(prefs.value.lookFeel.menuCornerRadiusDp).isNotEqualTo(savedRadius)
        }
    }

    @Test fun homePagePreviewTracksActualPageAdditionSeparatelyFromDemoPages() {
        fixture(SettingsSection.HOME_SCREEN, LauncherSettings(), pages = 1) { prefs, _, _ ->
            scrollTo(By.text(context.getString(R.string.settings_home_header_pages))).click()
            find(By.descStartsWith("Home layout: 1 saved pages."))
            scrollTo(By.text(context.getString(R.string.settings_home_add_page))).click()
            val two = find(By.descStartsWith("Home layout: 2 saved pages."))
            assertThat(two.contentDescription).contains("of 2")
            assertThat(DeviceTestSupport.waitUntil {
                device.findObject(By.text(context.getString(R.string.preview_home_demo_mode)))?.isEnabled == true
            }).isTrue()
            clickPreviewButton(R.string.preview_home_demo_mode)
            val demo = find(By.descStartsWith("Home layout: 2 saved pages."))
            assertThat(DeviceTestSupport.waitUntil { find(By.descStartsWith("Home layout: 2 saved pages.")).contentDescription.contains("of 3") }).isTrue()
            image(demo, "gaps-home-pages").recycle()
            assertThat(prefs.value.home.defaultPage).isEqualTo(0)
        }
    }

    @Test fun discoverIllustratesEnabledSwipeAndFallbackWithoutExternalActions() {
        fixture(SettingsSection.HOME_SCREEN, LauncherSettings(), rtl = true) { prefs, _, _ ->
            scrollTo(By.text(context.getString(R.string.settings_home_google_discover))).click()
            find(By.desc(context.getString(R.string.preview_discover_description,
                context.getString(R.string.settings_state_off), context.getString(R.string.preview_discover_home))))
            scrollTo(By.text(context.getString(R.string.settings_home_google_discover))).click()
            find(By.descContains(context.getString(R.string.preview_discover_feed)))
            clickPreviewButton(R.string.preview_discover_try_fallback)
            find(By.descContains(context.getString(R.string.preview_discover_fallback)))
            clickPreviewButton(R.string.preview_discover_home)
            val home = find(By.descStartsWith("Offline Discover sample:"))
            val bounds = home.visibleBounds
            device.swipe(bounds.right - bounds.width()/4, bounds.centerY(), bounds.left + bounds.width()/4, bounds.centerY(), 20)
            val feed = find(By.descContains(context.getString(R.string.preview_discover_feed)))
            image(feed, "gaps-discover-rtl").recycle()
            assertThat(prefs.value.home.googleDiscoverEnabled).isTrue()
        }
    }

    @Test fun debugOverlaySampleFollowsToggleWithoutShowingDeviceMeasurements() {
        val base = LauncherSettings()
        fixture(SettingsSection.LABS, base.copy(labs = base.labs.copy(unlocked = true)), fontScale = 2f) { prefs, _, _ ->
            val off = find(By.descStartsWith("Debug overlay sample:"))
            val before = image(off, "gaps-debug-off")
            scrollTo(By.text(context.getString(R.string.settings_labs_debug_overlay))).click()
            val expected = context.getString(R.string.preview_debug_description, context.getString(R.string.settings_state_on))
            val on = find(By.desc(expected))
            val after = image(on, "gaps-debug-on")
            assertThat(before.sameAs(after)).isFalse()
            before.recycle(); after.recycle()
            assertThat(prefs.value.labs.debugOverlay).isTrue()
        }
    }

    @Test fun cardConditionSimulationChangesVisibilityWithoutSavingOrPermissions() {
        fixture(SettingsSection.CARDS, LauncherSettings()) { _, deps, scenario ->
            val original = AutomationCard("sample-card", "Private title must not appear in preview",
                conditions = CardConditions(chargingOnly = true, wifiOnly = true,
                    bluetoothConnectedOnly = true, weekdays = setOf(1), timeRange = CardTimeRange(540, 600)))
            var saved: AutomationCard? = null
            render(scenario, deps) {
                CardEditorDialog(original, emptyList(), emptyList(), false, false,
                    onSave = { saved = it }, onDismiss = {})
            }
            find(By.descContains(context.getString(R.string.preview_card_hidden_charging)))
            scrollTo(By.text(context.getString(R.string.preview_card_sample_charging))).click()
            find(By.descContains(context.getString(R.string.preview_card_hidden_bluetooth)))
            scrollTo(By.text(context.getString(R.string.preview_card_sample_bluetooth))).click()
            val visible = find(By.descContains(context.getString(R.string.preview_card_visible)))
            assertThat(visible.contentDescription).doesNotContain(original.title)
            image(visible, "gaps-card-visible").recycle()
            scrollToAbove(By.text(context.getString(R.string.preview_card_sample_wifi))).click()
            find(By.descContains(context.getString(R.string.preview_card_hidden_wifi)))
            scrollToAbove(By.text(context.getString(R.string.preview_card_sample_wifi))).click()
            find(By.descContains(context.getString(R.string.preview_card_visible)))
            scrollToAbove(By.text(context.getString(R.string.preview_card_sample_day))).click()
            find(By.text("Tuesday")).click()
            find(By.descContains(context.getString(R.string.preview_card_hidden_day)))
            scrollToAbove(By.text(context.getString(R.string.preview_card_sample_day))).click()
            find(By.text("Monday")).click()
            find(By.descContains(context.getString(R.string.preview_card_visible)))
            scrollToAbove(By.text(context.getString(R.string.preview_card_sample_time))).click()
            find(By.desc("10 o'clock")).click()
            find(By.text(context.getString(R.string.cards_ok))).click()
            find(By.descContains(context.getString(R.string.preview_card_hidden_time)))
            find(By.text(context.getString(R.string.cards_save))).click()
            assertThat(DeviceTestSupport.waitUntil { saved != null }).isTrue()
            assertThat(saved).isEqualTo(original)
        }
    }

    private fun render(scenario: ActivityScenario<SettingsActivity>, deps: HomeDependencies,
        fontScale: Float = 1f, rtl: Boolean = false, content: @Composable () -> Unit) {
        scenario.onActivity { activity ->
            // Scoped to this fixture Activity; does not change the phone's timeout setting.
            activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            // This is an intentional scoped configuration fixture, not a live system setting.
            // Keep its screen bucket steady across preference recompositions.
            val configuration = Configuration(activity.resources.configuration).apply { this.fontScale = fontScale }
            val previewContext = activity.createConfigurationContext(configuration)
            activity.setContent { ChikkuSettingsTheme {
                CompositionLocalProvider(LocalHomeDependencies provides deps, LocalActivityResultRegistryOwner provides activity,
                    LocalContext provides previewContext, LocalConfiguration provides configuration,
                    LocalResources provides previewContext.resources,
                    LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                    LocalDensity provides Density(activity.resources.displayMetrics.density, fontScale)) { content() }
            }
        } }
        awaitDraw()
        device.waitForIdle()
    }

    private fun fixture(section: SettingsSection, initial: LauncherSettings, fontScale: Float = 1f,
        pages: Int = 3, testGroups: List<DrawerGroup> = emptyList(), rtl: Boolean = false,
        block: (androidx.compose.runtime.MutableState<LauncherSettings>, HomeDependencies, ActivityScenario<SettingsActivity>) -> Unit) {
        device.wakeUp()
        assumeFalse("Unlock the test phone for visible UI", context.getSystemService(KeyguardManager::class.java).isKeyguardLocked)
        val real = context.homeDependencies()
        val before = runBlocking { real.settingsRepository.exportJson() }
        val prefs = mutableStateOf(initial)
        val workspaceFlow = MutableStateFlow<WorkspaceState>(WorkspaceState.Ready(Workspace(initial.desktopGrid, initial.dockStorageGrid, pages, emptyList())))
        val groupFlow = MutableStateFlow(testGroups)
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
            override val searchHistoryRepository = frozen(
                io.github.aj4abinjacob.chikku.core.model.search.SearchHistoryRepository::class.java,
                "getEntries", MutableStateFlow(emptyList<io.github.aj4abinjacob.chikku.core.model.search.HistoryEntry>()))
            override val workspaceRepository = Proxy.newProxyInstance(WorkspaceRepository::class.java.classLoader,
                arrayOf(WorkspaceRepository::class.java)) { _, method, _ ->
                when (method.name) {
                    "getWorkspace" -> workspaceFlow
                    "addPage" -> {
                        val current = (workspaceFlow.value as WorkspaceState.Ready).workspace
                        workspaceFlow.value = WorkspaceState.Ready(current.copy(pageCount = current.pageCount + 1))
                        current.pageCount
                    }
                    else -> { prohibitedCalls++; error("Unexpected workspace operation") }
                }
            } as WorkspaceRepository
            override val appsRepository = frozen(AppsRepository::class.java, "getApps", MutableStateFlow<AppsState>(AppsState.Loaded(emptyList())))
            override val appCustomizationRepository = frozen(AppCustomizationRepository::class.java, "getCustomizations",
                MutableStateFlow(emptyMap<AppKey, io.github.aj4abinjacob.chikku.core.model.AppCustomization>()))
            override val drawerGroupRepository = Proxy.newProxyInstance(DrawerGroupRepository::class.java.classLoader,
                arrayOf(DrawerGroupRepository::class.java)) { _, method, arguments ->
                when (method.name) {
                    "getGroups" -> groupFlow
                    "reorderGroups" -> {
                        @Suppress("UNCHECKED_CAST") val ids = arguments!![0] as List<String>
                        groupFlow.value = ids.mapIndexed { index, id -> groupFlow.value.first { it.id == id }.copy(rank = index) }
                        true
                    }
                    "setSortMode" -> {
                        val id = arguments!![0] as String
                        val mode = arguments[1] as DrawerSortMode
                        groupFlow.value = groupFlow.value.map { if (it.id == id) it.copy(sortMode = mode) else it }
                        true
                    }
                    else -> { prohibitedCalls++; error("Unexpected group operation") }
                }
            } as DrawerGroupRepository
            override val discoverHealthChecker: io.github.aj4abinjacob.chikku.platform.DiscoverHealthChecker? = null
            override val webSuggestionSource = io.github.aj4abinjacob.chikku.core.model.search.WebSuggestionSource { _, _ ->
                prohibitedCalls++; error("A preview requested web suggestions")
            }
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
                render(scenario, deps, fontScale, rtl) {
                    SettingsRoot(prefs.value, { prefs.value = it(prefs.value) },
                        SettingsHost(false, "Synthetic preview", {}, {}), initialSection = section)
                }
                try { block(prefs, deps, scenario) } catch (failure: Throwable) {
                    val directory = File(context.cacheDir, "live-preview-evidence").apply { mkdirs() }
                    device.dumpWindowHierarchy(File(directory, "gaps-" + failure.stackTrace.firstOrNull { it.className.contains("PreviewGapsDeviceTest") && it.methodName.contains('$') }?.methodName?.substringBefore('$') + "-failure.xml"))
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

    private fun clickableParent(node: UiObject2): UiObject2 {
        var current = node
        while (!current.isClickable && current.parent != null) current = current.parent
        return current
    }

    private fun clickPreviewButton(resource: Int) {
        val selector = By.text(context.getString(resource))
        assertThat(DeviceTestSupport.waitUntil {
            device.findObject(selector)?.let { clickableParent(it).isEnabled } == true
        }).isTrue()
        clickableParent(find(selector)).click()
        awaitDraw()
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
        repeat(14) {
            device.waitForIdle()
            awaitDraw()
            val node = device.findObject(selector)
            val scroller = device.findObjects(By.scrollable(true)).lastOrNull()
            if (node != null && node.visibleBounds.height() > 0) {
                val bounds = node.visibleBounds
                val viewport = scroller?.visibleBounds
                // Avoid partial rows and the bottom gesture area on Samsung/MIUI phones.
                if (viewport == null || bounds.centerY() < viewport.bottom - viewport.height() / 5) return settledNode(selector)
            }
            scrollControls()
        }
        return settledNode(selector)
    }

    /** Samsung can report UI idle during a Compose fling; tapping then only stops the fling. */
    private fun settledNode(selector: BySelector): UiObject2 {
        var previous: android.graphics.Rect? = null
        var settled = 0
        assertThat(DeviceTestSupport.waitUntil {
            val bounds = device.findObject(selector)?.visibleBounds
            settled = if (bounds != null && bounds == previous) settled + 1 else 0
            previous = bounds
            settled >= 3
        }).isTrue()
        return find(selector)
    }

    private fun scrollToAbove(selector: BySelector): UiObject2 {
        repeat(12) {
            device.waitForIdle()
            awaitDraw()
            device.findObject(selector)?.takeIf { it.visibleBounds.height() > 0 }?.let { return it }
            val bounds = checkNotNull(device.findObjects(By.scrollable(true)).lastOrNull()).visibleBounds
            device.swipe(bounds.centerX(), bounds.top + bounds.height()/4,
                bounds.centerX(), bounds.bottom - bounds.height()/4, 24)
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
