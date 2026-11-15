package io.github.aj4abinjacob.chikku.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.settings.sections.DockPages
import io.github.aj4abinjacob.chikku.settings.sections.AppearancePages
import io.github.aj4abinjacob.chikku.settings.sections.DrawerPages
import io.github.aj4abinjacob.chikku.settings.sections.HomeScreenPages
import io.github.aj4abinjacob.chikku.settings.sections.SearchPages
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SettingsSearchIndexTest {
    private val resources = ApplicationProvider.getApplicationContext<Context>().resources

    @Test fun indexesActualLocalizedControlsAndKeepsLabsHiddenUntilUnlocked() {
        val entries = SettingsSearchIndex.entries(resources, false)
        assertThat(entries.size).isGreaterThan(140)
        assertThat(entries.map { it.section }).doesNotContain(SettingsSection.LABS)
        assertThat(SettingsSearchIndex.entries(resources, true).map { it.section }).contains(SettingsSection.LABS)
        val folderName = resources.getString(R.string.folders_custom_show_name)
        assertThat(entries.first { it.title == folderName }.section).isEqualTo(SettingsSection.FOLDERS)
    }

    @Test fun aliasesFindTheCorrectSectionsAndAllWordsMustMatch() {
        val entries = SettingsSearchIndex.entries(resources, false)
        assertThat(SettingsSearchIndex.search(entries, "desktop font").map { it.section }).contains(SettingsSection.HOME_SCREEN)
        assertThat(SettingsSearchIndex.search(entries, "transparency").map { it.section }).containsAtLeast(SettingsSection.DOCK, SettingsSection.FOLDERS)
        assertThat(SettingsSearchIndex.search(entries, "all apps alphabet").map { it.section }).contains(SettingsSection.APP_DRAWER)
        assertThat(SettingsSearchIndex.search(entries, "unfindable-word-not-a-setting")).isEmpty()
        assertThat(SettingsSearchIndex.search(entries, "  ")).isEmpty()
    }

    @Test fun exactLocalizedTitleRanksBeforeDescriptionAndSynonymMatches() {
        val title = resources.getString(R.string.folders_custom_corner_radius)
        val entries = SettingsSearchIndex.entries(resources, false)
        val matches = SettingsSearchIndex.search(entries, title)
        assertThat(matches.first().title).isEqualTo(title)
        assertThat(matches.first().section).isEqualTo(SettingsSection.FOLDERS)
    }

    @Test fun accentAndCaseNormalizationPreservesLiteralResults() {
        val entry = SettingsSearchEntry(SettingsSection.HOME_SCREEN, "Écran font", "", "Desktop", "typeface")
        assertThat(SettingsSearchIndex.search(listOf(entry), "ÉCRAN")).containsExactly(entry)
        assertThat(SettingsSearchIndex.search(listOf(entry), "ecran desktop")).containsExactly(entry)
        assertThat(SettingsSearchIndex.search(listOf(entry), "typeface")).containsExactly(entry)
        assertThat(entry.title).isEqualTo("Écran font")
    }
    @Test fun automationAndHorizontalFolderSearchFindImplementedControlsWithoutDuplicateSections() {
        val entries = SettingsSearchIndex.entries(resources, false)
        assertThat(SettingsSearchIndex.search(entries, "automation schedule").map { it.section }).contains(SettingsSection.CARDS)
        assertThat(SettingsSearchIndex.search(entries, "horizontal folder").map { it.section }).contains(SettingsSection.FOLDERS)
        assertThat(entries.filter { it.section == SettingsSection.CARDS && it.title == resources.getString(R.string.settings_section_cards) }).hasSize(1)
    }

    @Test fun drawerResultsOpenTheSubPageThatHoldsTheRow() {
        val entries = SettingsSearchIndex.entries(resources, false)
        fun entry(id: Int) = entries.first { it.section == SettingsSection.APP_DRAWER && it.title == resources.getString(id) }
        val scrollerStyle = entry(R.string.drawer_settings_scroller_style)
        assertThat(scrollerStyle.subPage).isEqualTo(DrawerPages.FastScroller.route)
        assertThat(scrollerStyle.subPageTitle).isEqualTo(resources.getString(R.string.drawer_settings_fast_scroller))
        assertThat(entry(R.string.drawer_settings_tab_style).subPage).isEqualTo(DrawerPages.Tabs.route)
        assertThat(entry(R.string.drawer_settings_fit_rows).subPage).isEqualTo(DrawerPages.Grid.route)
        // The scrolling style stays on the main page.
        assertThat(entry(R.string.drawer_settings_style).subPage).isNull()
        // Every drawer route a result names is one the section can open.
        val routes = DrawerPages.all.map { it.route }
        assertThat(routes).containsNoDuplicates()
        assertThat(routes).containsAtLeastElementsIn(entries.filter { it.section == SettingsSection.APP_DRAWER }.mapNotNull { it.subPage }.toSet())
    }

    @Test fun appearanceResultsOpenTheirFocusedPageAndOverviewStaysSeparate() {
        val entries = SettingsSearchIndex.entries(resources, false).filter { it.section == SettingsSection.LOOK_AND_FEEL }
        fun entry(id: Int) = entries.first { it.title == resources.getString(id) }
        assertThat(entry(R.string.look_accent_color).subPage).isEqualTo(AppearancePages.Accent.route)
        assertThat(entry(R.string.look_icon_shape).subPage).isEqualTo(AppearancePages.Icons.route)
        assertThat(entry(R.string.settings_look_animation_speed).subPage).isEqualTo(AppearancePages.Motion.route)
        assertThat(entry(R.string.look_orientation).subPage).isEqualTo(AppearancePages.Screen.route)
        assertThat(entry(R.string.menu_settings_corner_radius).subPage).isEqualTo(AppearancePages.Menus.route)
        assertThat(entry(R.string.menu_settings_hide).subPageTitle).isEqualTo(resources.getString(R.string.look_header_menus))
        assertThat(entry(R.string.settings_section_look_feel).subPage).isNull()
        assertThat(AppearancePages.all.map { it.route }).containsNoDuplicates()
        assertThat(AppearancePages.all.map { it.route }).containsAtLeastElementsIn(entries.mapNotNull { it.subPage }.distinct())
    }

    @Test fun dockResultsOpenTheSubPageThatHoldsTheRow() {
        val entries = SettingsSearchIndex.entries(resources, false)
        fun entry(id: Int) = entries.first { it.section == SettingsSection.DOCK && it.title == resources.getString(id) }
        assertThat(entry(R.string.settings_dock_background_alpha).subPage).isEqualTo(DockPages.Background.route)
        assertThat(entry(R.string.settings_dock_background_alpha).subPageTitle)
            .isEqualTo(resources.getString(R.string.settings_dock_page_background))
        assertThat(entry(R.string.settings_dock_icon_count).subPage).isEqualTo(DockPages.Apps.route)
        assertThat(entry(R.string.settings_dock_label_shadow).subPage).isEqualTo(DockPages.Icons.route)
        assertThat(entry(R.string.settings_dock_auto_close).subPage).isEqualTo(DockPages.Behavior.route)
        assertThat(entry(R.string.settings_search_engine).subPage).isEqualTo(DockPages.Search.route)
        // Turning the dock on or off stays on the main page.
        assertThat(entry(R.string.settings_dock_enabled).subPage).isNull()
        val routes = DockPages.all.map { it.route }
        assertThat(routes).containsNoDuplicates()
        assertThat(routes).containsAtLeastElementsIn(entries.filter { it.section == SettingsSection.DOCK }.mapNotNull { it.subPage }.toSet())
    }

    @Test fun searchResultsOpenTheSubPageThatHoldsTheRow() {
        val entries = SettingsSearchIndex.entries(resources, false)
        fun entry(id: Int) = entries.first { it.section == SettingsSection.SEARCH && it.title == resources.getString(id) }
        assertThat(entry(R.string.search_settings_corner_radius).subPage).isEqualTo(SearchPages.Look.route)
        assertThat(entry(R.string.search_settings_bar_width).subPage).isEqualTo(SearchPages.HomeBarSize.route)
        assertThat(entry(R.string.search_settings_bar_width).subPageTitle)
            .isEqualTo(resources.getString(R.string.search_page_home_bar_size))
        assertThat(entry(R.string.settings_search_home_bar).subPage).isEqualTo(SearchPages.HomeBar.route)
        assertThat(entry(R.string.search_settings_one_row).subPage).isEqualTo(SearchPages.Apps.route)
        assertThat(entry(R.string.search_app_priority_direction).subPage).isEqualTo(SearchPages.Apps.route)
        assertThat(SettingsSearchIndex.search(entries, "one hand priority").map { it.title })
            .contains(resources.getString(R.string.search_app_priority_direction))
        assertThat(entry(R.string.settings_search_contacts).subPage).isEqualTo(SearchPages.Contacts.route)
        assertThat(entry(R.string.settings_search_engine).subPage).isEqualTo(SearchPages.Web.route)
        assertThat(entry(R.string.settings_search_calculator).subPage).isEqualTo(SearchPages.Answers.route)
        // The display choice and the keyboard privacy switch stay on the main page.
        assertThat(entry(R.string.search_settings_presentation).subPage).isNull()
        assertThat(entry(R.string.search_settings_private_keyboard).subPage).isNull()
        val routes = SearchPages.all.map { it.route }
        assertThat(routes).containsNoDuplicates()
        assertThat(routes).containsAtLeastElementsIn(entries.filter { it.section == SettingsSection.SEARCH }.mapNotNull { it.subPage }.distinct())
    }

    @Test fun homeResultsOpenTheSubPageThatHoldsTheRow() {
        val entries = SettingsSearchIndex.entries(resources, false)
        fun entry(id: Int) = entries.first { it.section == SettingsSection.HOME_SCREEN && it.title == resources.getString(id) }
        assertThat(entry(R.string.settings_home_columns).subPage).isEqualTo(HomeScreenPages.Grid.route)
        assertThat(entry(R.string.settings_home_columns).subPageTitle).isEqualTo(resources.getString(R.string.settings_home_page_grid))
        assertThat(entry(R.string.settings_home_move_hidden).subPage).isEqualTo(HomeScreenPages.Grid.route)
        assertThat(entry(R.string.settings_label_color).subPage).isEqualTo(HomeScreenPages.Icons.route)
        assertThat(entry(R.string.settings_home_widget_corners).subPage).isEqualTo(HomeScreenPages.Widgets.route)
        assertThat(entry(R.string.settings_home_widget_padding).subPage).isEqualTo(HomeScreenPages.Widgets.route)
        assertThat(entry(R.string.settings_home_loop_pages).subPage).isEqualTo(HomeScreenPages.Pages.route)
        assertThat(entry(R.string.settings_home_google_discover).subPage).isEqualTo(HomeScreenPages.Discover.route)
        // The layout lock, new-app placement and edge shade stay on the main page.
        assertThat(entry(R.string.settings_home_lock_layout).subPage).isNull()
        assertThat(entry(R.string.settings_home_new_apps).subPage).isNull()
        assertThat(entry(R.string.settings_home_edge_shadow).subPage).isNull()
        // "Infinite scroll" still finds the renamed row.
        assertThat(SettingsSearchIndex.search(entries, "infinite scroll").map { it.title })
            .contains(resources.getString(R.string.settings_home_loop_pages))
        val routes = HomeScreenPages.all.map { it.route }
        assertThat(routes).containsNoDuplicates()
        assertThat(routes).containsAtLeastElementsIn(entries.filter { it.section == SettingsSection.HOME_SCREEN }.mapNotNull { it.subPage }.toSet())
    }
}
