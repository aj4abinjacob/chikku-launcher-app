package io.github.aj4abinjacob.chikku.settings

import android.content.res.Resources
import androidx.annotation.StringRes
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.settings.sections.AppearancePages
import io.github.aj4abinjacob.chikku.settings.sections.DockPages
import io.github.aj4abinjacob.chikku.settings.sections.SearchPages
import io.github.aj4abinjacob.chikku.settings.components.SettingsSubPageLink
import io.github.aj4abinjacob.chikku.settings.sections.HomeScreenPages
import io.github.aj4abinjacob.chikku.settings.sections.DrawerPages
import java.text.Normalizer
import java.util.Locale

/** Localized, static preference content only. App inventories, user values and queries stay out. */
internal data class SettingsSearchEntry(
    val section: SettingsSection,
    val title: String,
    val summary: String,
    val sectionTitle: String,
    val keywords: String,
    /** Route of the section's sub-page that holds this row, or null for the section's main page. */
    val subPage: String? = null,
    /** Localized title of [subPage], shown in the result's summary. */
    val subPageTitle: String? = null,
)

internal object SettingsSearchIndex {
    private data class Definition(
        val section: SettingsSection,
        @StringRes val title: Int,
        @StringRes val summary: Int? = null,
        @StringRes val keywords: Int? = null,
        /** Sub-page that holds the row; results open it directly. */
        val subPage: SettingsSubPageLink? = null,
    )

    fun entries(resources: Resources, labsUnlocked: Boolean): List<SettingsSearchEntry> =
        definitions.filter { it.section != SettingsSection.LABS || labsUnlocked }.map { definition ->
            SettingsSearchEntry(
                definition.section,
                resources.getString(definition.title),
                definition.summary?.let(resources::getString).orEmpty(),
                resources.getString(definition.section.title),
                (definition.keywords?.let(resources::getString).orEmpty() + " " + resources.getString(sectionKeywords(definition.section))),
                definition.subPage?.route,
                definition.subPage?.title?.let(resources::getString),
            )
        }

    private fun sectionKeywords(section: SettingsSection): Int = when (section) {
        SettingsSection.HOME_SCREEN -> R.string.settings_search_synonyms_section_home
        SettingsSection.APP_DRAWER -> R.string.settings_search_synonyms_section_drawer
        SettingsSection.DOCK -> R.string.settings_search_synonyms_section_dock
        SettingsSection.FOLDERS -> R.string.settings_search_synonyms_section_folders
        SettingsSection.CARDS -> R.string.settings_search_synonyms_cards
        SettingsSection.APP_LOCK -> R.string.settings_search_synonyms_app_lock
        else -> R.string.settings_search_synonyms_section_other
    }

    /** Every token must match; preference titles outrank description and synonym matches. */
    fun search(entries: List<SettingsSearchEntry>, query: String): List<SettingsSearchEntry> {
        val normalized = normalize(query.take(MAX_QUERY_LENGTH)).trim()
        val tokens = normalized.split(Regex("\\s+")).filter(String::isNotEmpty)
        if (tokens.isEmpty()) return emptyList()
        return entries.mapNotNull { entry ->
            val title = normalize(entry.title)
            val all = normalize("${entry.title} ${entry.summary} ${entry.sectionTitle} ${entry.keywords}")
            if (!tokens.all(all::contains)) null else {
                val rank = when {
                    title == normalized -> 0
                    title.startsWith(normalized) -> 1
                    title.contains(normalized) -> 2
                    tokens.all(title::contains) -> 3
                    else -> 4
                }
                Triple(rank, title, entry)
            }
        }.sortedWith(compareBy<Triple<Int, String, SettingsSearchEntry>> { it.first }
            .thenBy { it.third.section.ordinal }.thenBy { it.second })
            .map { it.third }
    }

    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)

    const val MAX_QUERY_LENGTH = 200

    // Each title is a real control in its owning section. Results open that section, or the sub-page
    // named by `subPage`; they never claim a row deep link.
    private val definitions = SettingsSection.entries.map { Definition(it, it.title, it.summary) } + listOf(
        Definition(SettingsSection.APP_LOCK, R.string.app_lock_relock_title),
        Definition(SettingsSection.HOME_SCREEN, R.string.settings_home_google_discover, R.string.settings_home_google_discover_summary, subPage = HomeScreenPages.Discover),
        Definition(SettingsSection.HOME_SCREEN, R.string.settings_home_lock_layout, R.string.settings_home_lock_layout_summary),
        Definition(SettingsSection.HOME_SCREEN, R.string.settings_home_columns, null, R.string.settings_search_synonyms_grid, HomeScreenPages.Grid),
        Definition(SettingsSection.HOME_SCREEN, R.string.settings_home_rows, null, R.string.settings_search_synonyms_grid, HomeScreenPages.Grid),
        Definition(SettingsSection.HOME_SCREEN, R.string.settings_home_icon_size, subPage = HomeScreenPages.Icons),
        Definition(SettingsSection.HOME_SCREEN, R.string.settings_home_show_labels, null, R.string.settings_search_synonyms_labels, HomeScreenPages.Icons),
        Definition(SettingsSection.HOME_SCREEN, R.string.settings_home_widget_overlap, R.string.settings_home_widget_overlap_summary, subPage = HomeScreenPages.Widgets),
        Definition(SettingsSection.HOME_SCREEN, R.string.settings_home_widget_corners, null, R.string.settings_search_synonyms_corners, HomeScreenPages.Widgets),
        Definition(SettingsSection.HOME_SCREEN, R.string.settings_home_transition, subPage = HomeScreenPages.Pages),
        Definition(SettingsSection.HOME_SCREEN, R.string.paging_settings_title, subPage = HomeScreenPages.Scrolling),
        Definition(SettingsSection.HOME_SCREEN, R.string.paging_sensitivity, subPage = HomeScreenPages.Scrolling),
        Definition(SettingsSection.HOME_SCREEN, R.string.paging_duration, subPage = HomeScreenPages.Scrolling),
        Definition(SettingsSection.HOME_SCREEN, R.string.paging_effect, subPage = HomeScreenPages.Scrolling),
        Definition(SettingsSection.HOME_SCREEN, R.string.paging_strength, subPage = HomeScreenPages.Scrolling),
        Definition(SettingsSection.HOME_SCREEN, R.string.paging_edge_feedback, subPage = HomeScreenPages.Scrolling),
        Definition(SettingsSection.APP_DRAWER, R.string.paging_settings_title, subPage = DrawerPages.Scrolling),
        Definition(SettingsSection.APP_DRAWER, R.string.paging_sensitivity, subPage = DrawerPages.Scrolling),
        Definition(SettingsSection.APP_DRAWER, R.string.paging_duration, subPage = DrawerPages.Scrolling),
        Definition(SettingsSection.APP_DRAWER, R.string.paging_effect, subPage = DrawerPages.Scrolling),
        Definition(SettingsSection.APP_DRAWER, R.string.paging_strength, subPage = DrawerPages.Scrolling),
        Definition(SettingsSection.APP_DRAWER, R.string.paging_edge_feedback, subPage = DrawerPages.Scrolling),
        Definition(SettingsSection.APP_DRAWER, R.string.vertical_scroll_settings_title, subPage = DrawerPages.VerticalScrolling),
        Definition(SettingsSection.APP_DRAWER, R.string.vertical_scroll_speed, subPage = DrawerPages.VerticalScrolling),
        Definition(SettingsSection.APP_DRAWER, R.string.vertical_scroll_edge, subPage = DrawerPages.VerticalScrolling),
        Definition(SettingsSection.HOME_SCREEN, R.string.settings_home_new_apps, R.string.settings_home_new_apps_summary),
        Definition(SettingsSection.HOME_SCREEN, R.string.settings_home_edge_shadow, R.string.settings_home_edge_shadow_summary),
        Definition(SettingsSection.HOME_SCREEN, R.string.settings_home_page_indicator, subPage = HomeScreenPages.Pages),
        Definition(SettingsSection.HOME_SCREEN, R.string.settings_home_page_indicator_auto_hide, R.string.settings_home_page_indicator_auto_hide_summary, subPage = HomeScreenPages.Pages),
        Definition(SettingsSection.HOME_SCREEN, R.string.settings_home_wallpaper_scrolling, R.string.settings_home_wallpaper_scrolling_summary, subPage = HomeScreenPages.Pages),
        Definition(SettingsSection.HOME_SCREEN, R.string.settings_home_loop_pages, R.string.settings_home_infinite_scroll_summary, R.string.settings_home_loop_pages_keywords, HomeScreenPages.Pages),
        Definition(SettingsSection.HOME_SCREEN, R.string.settings_home_default_page, subPage = HomeScreenPages.Pages),
        Definition(SettingsSection.HOME_SCREEN, R.string.settings_home_add_page, subPage = HomeScreenPages.Pages),
        Definition(SettingsSection.HOME_SCREEN, R.string.settings_home_move_hidden, null, R.string.settings_search_synonyms_hidden, HomeScreenPages.Grid),
        Definition(SettingsSection.HOME_SCREEN, R.string.settings_home_label_size, null, R.string.settings_search_synonyms_labels, HomeScreenPages.Icons),
        Definition(SettingsSection.HOME_SCREEN, R.string.settings_dock_label_font, null, R.string.settings_search_synonyms_labels, HomeScreenPages.Icons),
        Definition(SettingsSection.HOME_SCREEN, R.string.settings_dock_label_shadow, null, R.string.settings_search_synonyms_labels, HomeScreenPages.Icons),
        Definition(SettingsSection.HOME_SCREEN, R.string.settings_dock_single_line, subPage = HomeScreenPages.Icons),
        Definition(SettingsSection.HOME_SCREEN, R.string.settings_label_color, null, R.string.settings_search_synonyms_labels, HomeScreenPages.Icons),
        Definition(SettingsSection.HOME_SCREEN, R.string.settings_home_horizontal_padding, R.string.settings_home_grid_spacing_info, R.string.settings_spacing_keywords, HomeScreenPages.Grid),
        Definition(SettingsSection.HOME_SCREEN, R.string.settings_home_vertical_padding, R.string.settings_home_grid_spacing_info, R.string.settings_spacing_keywords, HomeScreenPages.Grid),
        Definition(SettingsSection.HOME_SCREEN, R.string.settings_home_widget_padding, R.string.settings_home_widget_padding_info, R.string.settings_spacing_keywords, HomeScreenPages.Widgets),
        Definition(SettingsSection.APP_DRAWER, R.string.drawer_settings_style),
        Definition(SettingsSection.APP_DRAWER, R.string.drawer_settings_auto_columns, null, R.string.settings_search_synonyms_grid, subPage = DrawerPages.Grid),
        Definition(SettingsSection.APP_DRAWER, R.string.drawer_settings_columns, null, R.string.settings_search_synonyms_grid, subPage = DrawerPages.Grid),
        Definition(SettingsSection.APP_DRAWER, R.string.drawer_settings_rows, null, R.string.settings_search_synonyms_grid, subPage = DrawerPages.Grid),
        Definition(SettingsSection.APP_DRAWER, R.string.drawer_settings_visible_rows, R.string.drawer_settings_rows_summary, R.string.settings_search_synonyms_grid, subPage = DrawerPages.Grid),
        Definition(SettingsSection.APP_DRAWER, R.string.drawer_settings_fit_rows, R.string.drawer_settings_fit_rows_summary, R.string.settings_search_synonyms_grid, subPage = DrawerPages.Grid),
        Definition(SettingsSection.APP_DRAWER, R.string.drawer_settings_match_desktop, subPage = DrawerPages.Grid),
        Definition(SettingsSection.APP_DRAWER, R.string.drawer_settings_icon_size, subPage = DrawerPages.Grid),
        Definition(SettingsSection.APP_DRAWER, R.string.drawer_settings_labels, null, R.string.settings_search_synonyms_labels, subPage = DrawerPages.Grid),
        Definition(SettingsSection.APP_DRAWER, R.string.drawer_settings_fast_scroller, R.string.drawer_settings_fast_scroller_summary, R.string.settings_search_synonyms_scroller, subPage = DrawerPages.FastScroller),
        Definition(SettingsSection.HOME_SCREEN, R.string.home_icon_label_gap, null, R.string.settings_search_synonyms_labels, HomeScreenPages.Icons),
        Definition(SettingsSection.HOME_SCREEN, R.string.home_icon_vertical_offset, subPage = HomeScreenPages.Icons),
        Definition(SettingsSection.APP_DRAWER, R.string.drawer_settings_scroller_style, null, R.string.settings_search_synonyms_scroller, subPage = DrawerPages.FastScroller),
        Definition(SettingsSection.APP_DRAWER, R.string.drawer_settings_scroller_side, null, R.string.settings_search_synonyms_scroller, subPage = DrawerPages.FastScroller),
        Definition(SettingsSection.APP_DRAWER, R.string.drawer_settings_scroller_gap, null, R.string.settings_search_synonyms_scroller, subPage = DrawerPages.FastScroller),
        Definition(SettingsSection.APP_DRAWER, R.string.drawer_settings_scroller_popup, R.string.drawer_settings_scroller_popup_summary, R.string.settings_search_synonyms_scroller, subPage = DrawerPages.FastScroller),
        Definition(SettingsSection.APP_DRAWER, R.string.drawer_settings_tab_style, subPage = DrawerPages.Tabs),
        Definition(SettingsSection.APP_DRAWER, R.string.drawer_settings_tab_accent, subPage = DrawerPages.Tabs),
        Definition(SettingsSection.APP_DRAWER, R.string.drawer_settings_fast_scroller_color, null, R.string.settings_search_synonyms_scroller, subPage = DrawerPages.FastScroller),
        Definition(SettingsSection.APP_DRAWER, R.string.drawer_settings_infinite_scroll, R.string.drawer_settings_infinite_scroll_summary),
        Definition(SettingsSection.APP_DRAWER, R.string.drawer_settings_remember_position, R.string.drawer_settings_remember_position_summary, subPage = DrawerPages.Opening),
        Definition(SettingsSection.APP_DRAWER, R.string.drawer_settings_show_arrow, subPage = DrawerPages.Opening),
        Definition(SettingsSection.APP_DRAWER, R.string.drawer_settings_open_method, subPage = DrawerPages.Opening),
        Definition(SettingsSection.APP_DRAWER, R.string.drawer_settings_automatically_close, subPage = DrawerPages.Opening),
        Definition(SettingsSection.APP_DRAWER, R.string.drawer_settings_transition, subPage = DrawerPages.Opening),
        Definition(SettingsSection.APP_DRAWER, R.string.drawer_settings_search_position, subPage = DrawerPages.Search),
        Definition(SettingsSection.APP_DRAWER, R.string.drawer_settings_search_style, subPage = DrawerPages.Search),
        Definition(SettingsSection.APP_DRAWER, R.string.settings_drawer_open_keyboard, null, R.string.settings_search_synonyms_keyboard, subPage = DrawerPages.Search),
        Definition(SettingsSection.APP_DRAWER, R.string.drawer_settings_pull_to_search, R.string.drawer_settings_pull_to_search_summary, subPage = DrawerPages.Search),
        Definition(SettingsSection.APP_DRAWER, R.string.drawer_settings_background_color, subPage = DrawerPages.Background),
        Definition(SettingsSection.APP_DRAWER, R.string.drawer_settings_background_alpha, null, R.string.settings_search_synonyms_opacity, subPage = DrawerPages.Background),
        Definition(SettingsSection.APP_DRAWER, R.string.drawer_settings_tab_position, subPage = DrawerPages.Tabs),
        Definition(SettingsSection.APP_DRAWER, R.string.drawer_settings_hidden_apps, R.string.drawer_settings_hidden_apps_summary, R.string.settings_search_synonyms_hidden, subPage = DrawerPages.HiddenApps),
        Definition(SettingsSection.APP_DRAWER, R.string.drawer_settings_groups_enabled, subPage = DrawerPages.Tabs),
        Definition(SettingsSection.APP_DRAWER, R.string.drawer_settings_groups, R.string.drawer_settings_groups_summary, subPage = DrawerPages.Groups),
        Definition(SettingsSection.APP_DRAWER, R.string.drawer_settings_work_tab, R.string.drawer_settings_work_tab_summary, subPage = DrawerPages.Tabs),
        Definition(SettingsSection.APP_DRAWER, R.string.drawer_page_grid, subPage = DrawerPages.Grid),
        Definition(SettingsSection.APP_DRAWER, R.string.drawer_page_tabs, subPage = DrawerPages.Tabs),
        Definition(SettingsSection.APP_DRAWER, R.string.drawer_page_search, subPage = DrawerPages.Search),
        Definition(SettingsSection.APP_DRAWER, R.string.drawer_page_opening, subPage = DrawerPages.Opening),
        Definition(SettingsSection.APP_DRAWER, R.string.settings_home_label_size, null, R.string.settings_search_synonyms_labels, subPage = DrawerPages.Grid),
        Definition(SettingsSection.APP_DRAWER, R.string.settings_dock_label_font, null, R.string.settings_search_synonyms_labels, subPage = DrawerPages.Grid),
        Definition(SettingsSection.APP_DRAWER, R.string.settings_dock_label_shadow, null, R.string.settings_search_synonyms_labels, subPage = DrawerPages.Grid),
        Definition(SettingsSection.APP_DRAWER, R.string.settings_dock_single_line, subPage = DrawerPages.Grid),
        Definition(SettingsSection.APP_DRAWER, R.string.settings_label_color, null, R.string.settings_search_synonyms_labels, subPage = DrawerPages.Grid),
        Definition(SettingsSection.DOCK, R.string.settings_dock_enabled),
        Definition(SettingsSection.DOCK, R.string.settings_dock_icon_size, subPage = DockPages.Icons),
        Definition(SettingsSection.DOCK, R.string.settings_dock_match_desktop, subPage = DockPages.Icons),
        Definition(SettingsSection.DOCK, R.string.settings_dock_show_labels, null, R.string.settings_search_synonyms_labels, subPage = DockPages.Icons),
        Definition(SettingsSection.DOCK, R.string.settings_dock_label_font, null, R.string.settings_search_synonyms_labels, subPage = DockPages.Icons),
        Definition(SettingsSection.DOCK, R.string.settings_dock_page_spacing, R.string.settings_dock_spacing_summary, R.string.settings_spacing_keywords, subPage = DockPages.Spacing),
        Definition(SettingsSection.DOCK, R.string.settings_dock_background, subPage = DockPages.Background),
        Definition(SettingsSection.DOCK, R.string.settings_dock_image, subPage = DockPages.Background),
        Definition(SettingsSection.DOCK, R.string.settings_dock_image_remove, subPage = DockPages.Background),
        Definition(SettingsSection.DOCK, R.string.settings_dock_background_shape, subPage = DockPages.Background),
        Definition(SettingsSection.DOCK, R.string.settings_dock_icon_count, subPage = DockPages.Apps),
        Definition(SettingsSection.DOCK, R.string.settings_dock_pages, subPage = DockPages.Apps),
        Definition(SettingsSection.DOCK, R.string.settings_dock_page_behavior, subPage = DockPages.Behavior),
        Definition(SettingsSection.DOCK, R.string.settings_dock_page_apps, subPage = DockPages.Apps),
        Definition(SettingsSection.DOCK, R.string.settings_dock_page_icons, null, R.string.settings_search_synonyms_labels, subPage = DockPages.Icons),
        Definition(SettingsSection.DOCK, R.string.settings_dock_search_bar, R.string.settings_dock_search_bar_summary, subPage = DockPages.Search),
        Definition(SettingsSection.DOCK, R.string.settings_home_label_size, null, R.string.settings_search_synonyms_labels, subPage = DockPages.Icons),
        Definition(SettingsSection.DOCK, R.string.settings_dock_label_shadow, null, R.string.settings_search_synonyms_labels, subPage = DockPages.Icons),
        Definition(SettingsSection.DOCK, R.string.settings_dock_single_line, subPage = DockPages.Icons),
        Definition(SettingsSection.FOLDERS, R.string.folders_custom_window),
        Definition(SettingsSection.FOLDERS, R.string.folders_custom_columns, null, R.string.settings_search_synonyms_grid),
        Definition(SettingsSection.FOLDERS, R.string.folders_settings_preview),
        Definition(SettingsSection.FOLDERS, R.string.folders_custom_preview_count),
        Definition(SettingsSection.FOLDERS, R.string.folders_settings_icon_background),
        Definition(SettingsSection.FOLDERS, R.string.folders_settings_background_color),
        Definition(SettingsSection.FOLDERS, R.string.folders_icon_background_color, R.string.folders_icon_color_summary),
        Definition(SettingsSection.FOLDERS, R.string.folders_settings_background_alpha, null, R.string.settings_search_synonyms_opacity),
        Definition(SettingsSection.FOLDERS, R.string.folders_custom_show_name),
        Definition(SettingsSection.FOLDERS, R.string.folders_custom_corner_radius, null, R.string.settings_search_synonyms_corners),
        Definition(SettingsSection.FOLDERS, R.string.folders_custom_shadow),
        Definition(SettingsSection.FOLDERS, R.string.folders_custom_match_desktop),
        Definition(SettingsSection.FOLDERS, R.string.folders_custom_icon_size),
        Definition(SettingsSection.FOLDERS, R.string.folders_custom_labels, null, R.string.settings_search_synonyms_labels),
        Definition(SettingsSection.FOLDERS, R.string.folders_custom_sort),
        Definition(SettingsSection.FOLDERS, R.string.folders_custom_close_on_launch, R.string.folders_custom_close_summary),
        Definition(SettingsSection.FOLDERS, R.string.folders_settings_gestures, R.string.folders_settings_gestures_summary),
        Definition(SettingsSection.FOLDERS, R.string.settings_home_label_size, null, R.string.settings_search_synonyms_labels),
        Definition(SettingsSection.FOLDERS, R.string.settings_dock_label_font, null, R.string.settings_search_synonyms_labels),
        Definition(SettingsSection.FOLDERS, R.string.settings_dock_label_shadow, null, R.string.settings_search_synonyms_labels),
        Definition(SettingsSection.FOLDERS, R.string.settings_dock_single_line),
        Definition(SettingsSection.FOLDERS, R.string.settings_label_color, null, R.string.settings_search_synonyms_labels),
        Definition(SettingsSection.LOOK_AND_FEEL, R.string.look_icon_pack, subPage = AppearancePages.Icons),
        Definition(SettingsSection.LOOK_AND_FEEL, R.string.look_icon_shape, subPage = AppearancePages.Icons),
        Definition(SettingsSection.LOOK_AND_FEEL, R.string.look_reshape_legacy, R.string.look_reshape_legacy_summary, subPage = AppearancePages.Icons),
        Definition(SettingsSection.LOOK_AND_FEEL, R.string.look_per_app_icons, R.string.look_per_app_icons_summary, subPage = AppearancePages.Icons),
        Definition(SettingsSection.LOOK_AND_FEEL, R.string.settings_look_animation_speed, subPage = AppearancePages.Motion),
        Definition(SettingsSection.LOOK_AND_FEEL, R.string.look_orientation, subPage = AppearancePages.Screen),
        Definition(SettingsSection.LOOK_AND_FEEL, R.string.look_status_bar, subPage = AppearancePages.Screen),
        Definition(SettingsSection.LOOK_AND_FEEL, R.string.look_app_menu_style, subPage = AppearancePages.Menus),
        Definition(SettingsSection.LOOK_AND_FEEL, R.string.look_accent_color, subPage = AppearancePages.Accent),
        Definition(SettingsSection.NIGHT_MODE, R.string.settings_night_mode),
        Definition(SettingsSection.NIGHT_MODE, R.string.night_dark_drawer),
        Definition(SettingsSection.NIGHT_MODE, R.string.night_dark_folders),
        Definition(SettingsSection.NIGHT_MODE, R.string.night_dark_search),
        Definition(SettingsSection.NIGHT_MODE, R.string.night_dark_menus),
        Definition(SettingsSection.NIGHT_MODE, R.string.night_pure_black, R.string.night_pure_black_summary),
        Definition(SettingsSection.GESTURES, R.string.gestures_home_button_only_default_page, R.string.gestures_home_button_only_default_page_summary),
        Definition(SettingsSection.GESTURES, R.string.gestures_home_icons, R.string.gestures_home_icons_summary),
        Definition(SettingsSection.GESTURES, R.string.gestures_app_defaults, R.string.gestures_app_defaults_summary),
        Definition(SettingsSection.BADGES, R.string.settings_badges_style, null, R.string.settings_search_synonyms_badges),
        Definition(SettingsSection.BADGES, R.string.badges_custom_position, null, R.string.settings_search_synonyms_badges),
        Definition(SettingsSection.BADGES, R.string.badges_custom_size, null, R.string.settings_search_synonyms_badges),
        Definition(SettingsSection.BADGES, R.string.settings_badges_color, null, R.string.settings_search_synonyms_badges),
        Definition(SettingsSection.BADGES, R.string.badges_custom_test, R.string.badges_custom_test_summary, R.string.settings_search_synonyms_badges),
        Definition(SettingsSection.BADGES, R.string.settings_badges_manage, null, R.string.settings_search_synonyms_badges),
        Definition(SettingsSection.BADGES, R.string.settings_badges_grant, null, R.string.settings_search_synonyms_badges),
        Definition(SettingsSection.SEARCH, R.string.search_settings_presentation),
        Definition(SettingsSection.SEARCH, R.string.search_settings_results_anchor, null, R.string.settings_search_synonyms_results_anchor),
        Definition(SettingsSection.SEARCH, R.string.search_settings_private_keyboard, R.string.search_settings_private_keyboard_summary, R.string.settings_search_synonyms_keyboard),
        Definition(SettingsSection.SEARCH, R.string.search_page_look, subPage = SearchPages.Look),
        Definition(SettingsSection.SEARCH, R.string.search_settings_background, subPage = SearchPages.Look),
        Definition(SettingsSection.SEARCH, R.string.search_settings_corner_radius, null, R.string.settings_search_synonyms_corners, subPage = SearchPages.Look),
        Definition(SettingsSection.SEARCH, R.string.settings_search_home_bar, subPage = SearchPages.HomeBar),
        Definition(SettingsSection.SEARCH, R.string.settings_search_home_bar_position, subPage = SearchPages.HomeBar),
        Definition(SettingsSection.SEARCH, R.string.search_settings_bar_color, subPage = SearchPages.HomeBar),
        Definition(SettingsSection.SEARCH, R.string.search_settings_bar_style, subPage = SearchPages.HomeBar),
        Definition(SettingsSection.SEARCH, R.string.search_bar_tap_action, null, R.string.search_bar_actions_keywords, subPage = SearchPages.HomeBar),
        Definition(SettingsSection.SEARCH, R.string.search_bar_chikku_button, null, R.string.search_bar_actions_keywords, subPage = SearchPages.HomeBar),
        Definition(SettingsSection.SEARCH, R.string.search_bar_chikku_icon, subPage = SearchPages.HomeBar),
        Definition(SettingsSection.SEARCH, R.string.search_settings_bar_corners, null, R.string.settings_search_synonyms_corners, subPage = SearchPages.HomeBar),
        Definition(SettingsSection.SEARCH, R.string.search_settings_bar_shadow, subPage = SearchPages.HomeBar),
        Definition(SettingsSection.SEARCH, R.string.search_page_home_bar_size, null, R.string.search_settings_bar_sizing_keywords, subPage = SearchPages.HomeBarSize),
        Definition(SettingsSection.SEARCH, R.string.search_settings_bar_width, null, R.string.search_settings_bar_sizing_keywords, subPage = SearchPages.HomeBarSize),
        Definition(SettingsSection.SEARCH, R.string.search_settings_bar_height, null, R.string.search_settings_bar_sizing_keywords, subPage = SearchPages.HomeBarSize),
        Definition(SettingsSection.SEARCH, R.string.search_settings_bar_horizontal_padding, null, R.string.search_settings_bar_sizing_keywords, subPage = SearchPages.HomeBarSize),
        Definition(SettingsSection.SEARCH, R.string.search_settings_bar_vertical_padding, null, R.string.search_settings_bar_sizing_keywords, subPage = SearchPages.HomeBarSize),
        Definition(SettingsSection.SEARCH, R.string.search_settings_bar_dock_gap, null, R.string.search_settings_bar_sizing_keywords, subPage = SearchPages.HomeBarSize),
        Definition(SettingsSection.SEARCH, R.string.search_settings_bar_reset_size, subPage = SearchPages.HomeBarSize),
        Definition(SettingsSection.SEARCH, R.string.search_settings_one_row, R.string.search_settings_one_row_summary, subPage = SearchPages.Apps),
        Definition(SettingsSection.SEARCH, R.string.search_app_priority_direction, R.string.search_app_priority_one_row_only,
            R.string.search_app_priority_keywords, subPage = SearchPages.Apps),
        Definition(SettingsSection.SEARCH, R.string.settings_search_history_show, R.string.settings_search_history_show_summary, subPage = SearchPages.Suggestions),
        Definition(SettingsSection.SEARCH, R.string.settings_search_history_learn, R.string.settings_search_history_learn_summary, subPage = SearchPages.Suggestions),
        Definition(SettingsSection.SEARCH, R.string.search_history_clear, subPage = SearchPages.Suggestions),
        Definition(SettingsSection.SEARCH, R.string.search_settings_folders, R.string.search_settings_folders_summary, subPage = SearchPages.Apps),
        Definition(SettingsSection.SEARCH, R.string.settings_search_shortcuts, R.string.settings_search_shortcuts_summary, subPage = SearchPages.Apps),
        Definition(SettingsSection.SEARCH, R.string.settings_search_play_store, subPage = SearchPages.Apps),
        Definition(SettingsSection.SEARCH, R.string.settings_search_contacts, R.string.settings_search_contacts_summary, subPage = SearchPages.Contacts),
        Definition(SettingsSection.SEARCH, R.string.settings_search_contacts_open_app_settings, subPage = SearchPages.Contacts),
        Definition(SettingsSection.SEARCH, R.string.search_page_answers, subPage = SearchPages.Answers),
        Definition(SettingsSection.SEARCH, R.string.settings_search_calculator, R.string.settings_search_calculator_summary, subPage = SearchPages.Answers),
        Definition(SettingsSection.SEARCH, R.string.settings_search_micro_results, R.string.settings_search_micro_results_summary, subPage = SearchPages.Answers),
        Definition(SettingsSection.CARDS, R.string.cards_add),
        Definition(SettingsSection.CARDS, R.string.card_content_weather, R.string.card_weather_online_summary),
        Definition(SettingsSection.CARDS, R.string.card_content_media, R.string.card_media_summary),
        Definition(SettingsSection.CARDS, R.string.card_content_widget),
        Definition(SettingsSection.CARDS, R.string.card_content_type),
        Definition(SettingsSection.CARDS, R.string.card_media_access),
        Definition(SettingsSection.CARDS, R.string.card_bluetooth_permission),
        Definition(SettingsSection.CARDS, R.string.card_bluetooth_audio, R.string.card_bluetooth_summary),
        Definition(SettingsSection.CARDS, R.string.cards_featured, R.string.cards_widget_reference_summary),
        Definition(SettingsSection.CARDS, R.string.cards_buttons, R.string.cards_buttons_summary),
        Definition(SettingsSection.CARDS, R.string.cards_days, R.string.cards_days_any_summary),
        Definition(SettingsSection.CARDS, R.string.cards_time_enabled, R.string.cards_time_summary),
        Definition(SettingsSection.CARDS, R.string.cards_charging_yes),
        Definition(SettingsSection.CARDS, R.string.cards_wifi_yes, R.string.cards_wifi_summary),
        Definition(SettingsSection.FOLDERS, R.string.folders_paging_direction, null, R.string.folders_paging_horizontal),
        Definition(SettingsSection.FOLDERS, R.string.folders_paging_rows),
        Definition(SettingsSection.BACKUP, R.string.backup_save, R.string.backup_save_summary, R.string.settings_search_synonyms_backup),
        Definition(SettingsSection.BACKUP, R.string.backup_share, R.string.backup_share_summary, R.string.settings_search_synonyms_backup),
        Definition(SettingsSection.BACKUP, R.string.backup_restore, R.string.backup_restore_summary, R.string.settings_search_synonyms_backup),
        Definition(SettingsSection.BACKUP, R.string.backup_quick_start, R.string.backup_quick_start_summary, R.string.settings_search_synonyms_backup),
        Definition(SettingsSection.BACKUP, R.string.backup_reset, R.string.backup_reset_summary, R.string.settings_search_synonyms_backup),
        Definition(SettingsSection.LABS, R.string.settings_labs_debug_overlay, R.string.labs_debug_overlay_summary),
        Definition(SettingsSection.LABS, R.string.labs_faster_icon_cache, R.string.labs_not_active_summary),
        Definition(SettingsSection.LABS, R.string.labs_extended_grid, R.string.settings_labs_extended_grid_summary, R.string.settings_search_synonyms_grid),
        Definition(SettingsSection.LABS, R.string.labs_hide, R.string.labs_hide_summary),
        Definition(SettingsSection.ABOUT, R.string.settings_set_default),
        Definition(SettingsSection.ABOUT, R.string.settings_choose_other, R.string.about_choose_other_summary),
        Definition(SettingsSection.ABOUT, R.string.about_privacy_row_title, R.string.settings_privacy_body),
        Definition(SettingsSection.LOOK_AND_FEEL, R.string.menu_settings_background_color, subPage = AppearancePages.Menus),
        Definition(SettingsSection.LOOK_AND_FEEL, R.string.menu_settings_text_color, subPage = AppearancePages.Menus),
        Definition(SettingsSection.LOOK_AND_FEEL, R.string.menu_settings_corner_radius, subPage = AppearancePages.Menus),
        Definition(SettingsSection.LOOK_AND_FEEL, R.string.menu_settings_uninstall, R.string.menu_settings_action_scope, subPage = AppearancePages.Menus),
        Definition(SettingsSection.LOOK_AND_FEEL, R.string.menu_settings_store, R.string.menu_settings_action_scope, subPage = AppearancePages.Menus),
        Definition(SettingsSection.LOOK_AND_FEEL, R.string.menu_settings_hide, R.string.menu_settings_action_scope, subPage = AppearancePages.Menus),
        Definition(SettingsSection.GESTURES, R.string.gestures_swipe_left),
        Definition(SettingsSection.GESTURES, R.string.gestures_swipe_right),
        Definition(SettingsSection.GESTURES, R.string.gestures_double_tap_swipe_up),
        Definition(SettingsSection.GESTURES, R.string.gestures_double_tap_swipe_down),
        Definition(SettingsSection.GESTURES, R.string.gestures_rotate_clockwise),
        Definition(SettingsSection.GESTURES, R.string.gestures_rotate_counterclockwise),
        Definition(SettingsSection.SEARCH, R.string.search_web_main_switch, R.string.settings_search_web_row_summary, subPage = SearchPages.Web),
        Definition(SettingsSection.SEARCH, R.string.settings_search_engine, subPage = SearchPages.Web),
        Definition(SettingsSection.SEARCH, R.string.settings_search_online_suggestions, subPage = SearchPages.Web),
        Definition(SettingsSection.DOCK, R.string.settings_search_web_row, subPage = DockPages.Search),
        Definition(SettingsSection.DOCK, R.string.settings_search_engine, subPage = DockPages.Search),
        Definition(SettingsSection.DOCK, R.string.settings_search_online_suggestions, subPage = DockPages.Search),
        Definition(SettingsSection.DOCK, R.string.settings_dock_behind_navigation, subPage = DockPages.Background),
        Definition(SettingsSection.DOCK, R.string.settings_dock_background_padding, R.string.settings_dock_inset_summary, R.string.settings_spacing_keywords, subPage = DockPages.Background),
        Definition(SettingsSection.DOCK, R.string.settings_dock_top_left, subPage = DockPages.Background),
        Definition(SettingsSection.DOCK, R.string.settings_dock_top_right, subPage = DockPages.Background),
        Definition(SettingsSection.DOCK, R.string.settings_dock_bottom_left, subPage = DockPages.Background),
        Definition(SettingsSection.DOCK, R.string.settings_dock_bottom_right, subPage = DockPages.Background),
        Definition(SettingsSection.DOCK, R.string.settings_dock_background_color, subPage = DockPages.Background),
        Definition(SettingsSection.DOCK, R.string.settings_dock_background_alpha, null, R.string.settings_search_synonyms_opacity, subPage = DockPages.Background),
        Definition(SettingsSection.DOCK, R.string.settings_home_infinite_scroll, subPage = DockPages.Apps),
        Definition(SettingsSection.DOCK, R.string.settings_dock_overlay, subPage = DockPages.Behavior),
        Definition(SettingsSection.DOCK, R.string.settings_dock_auto_close, subPage = DockPages.Behavior),
        Definition(SettingsSection.DOCK, R.string.settings_dock_remember_position, subPage = DockPages.Apps),
        Definition(SettingsSection.DOCK, R.string.settings_dock_side_space, R.string.settings_dock_spacing_summary, R.string.settings_spacing_keywords, subPage = DockPages.Spacing),
        Definition(SettingsSection.DOCK, R.string.settings_dock_vertical_space, R.string.settings_dock_spacing_summary, R.string.settings_spacing_keywords, subPage = DockPages.Spacing),
        Definition(SettingsSection.GESTURES, R.string.gestures_swipe_up),
        Definition(SettingsSection.GESTURES, R.string.gestures_swipe_down),
        Definition(SettingsSection.GESTURES, R.string.gestures_pinch_in),
        Definition(SettingsSection.GESTURES, R.string.gestures_pinch_out),
        Definition(SettingsSection.GESTURES, R.string.gestures_two_finger_swipe_up),
        Definition(SettingsSection.GESTURES, R.string.gestures_two_finger_swipe_down),
    )
}
