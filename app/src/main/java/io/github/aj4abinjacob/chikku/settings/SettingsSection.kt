package io.github.aj4abinjacob.chikku.settings

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import io.github.aj4abinjacob.chikku.R

/** How the root list groups sections into cards, in display order. */
enum class SettingsGroup(@StringRes val title: Int) {
    LAYOUT(R.string.settings_group_layout),
    APPEARANCE(R.string.settings_group_appearance),
    BEHAVIOUR(R.string.settings_group_behaviour),
    SYSTEM(R.string.settings_group_system),
}

/**
 * Top-level settings sections, in display order. Each section's UI lives in its own
 * file `settings/sections/<Name>SettingsSection.kt`, owned by one feature; [SettingsRoot] routes to it.
 */
enum class SettingsSection(
    @StringRes val title: Int,
    @StringRes val summary: Int,
    @DrawableRes val icon: Int,
    val group: SettingsGroup,
) {
    HOME_SCREEN(R.string.settings_section_home, R.string.settings_section_home_summary, R.drawable.ic_settings_home, SettingsGroup.LAYOUT),
    APP_DRAWER(R.string.settings_section_drawer, R.string.settings_section_drawer_summary, R.drawable.ic_settings_drawer, SettingsGroup.LAYOUT),
    DOCK(R.string.settings_section_dock, R.string.settings_section_dock_summary, R.drawable.ic_settings_dock, SettingsGroup.LAYOUT),
    FOLDERS(R.string.settings_section_folders, R.string.settings_section_folders_summary, R.drawable.ic_settings_folder, SettingsGroup.LAYOUT),
    LOOK_AND_FEEL(R.string.settings_section_look_feel, R.string.settings_section_look_feel_summary, R.drawable.ic_settings_palette, SettingsGroup.APPEARANCE),
    NIGHT_MODE(R.string.settings_section_night, R.string.settings_section_night_summary, R.drawable.ic_settings_night, SettingsGroup.APPEARANCE),
    GESTURES(R.string.settings_section_gestures, R.string.settings_section_gestures_summary, R.drawable.ic_settings_gestures, SettingsGroup.BEHAVIOUR),
    BADGES(R.string.settings_section_badges, R.string.settings_section_badges_summary, R.drawable.ic_settings_badges, SettingsGroup.BEHAVIOUR),
    SEARCH(R.string.settings_section_search, R.string.settings_section_search_summary, R.drawable.ic_search, SettingsGroup.BEHAVIOUR),
    CARDS(R.string.settings_section_cards, R.string.settings_section_cards_summary, R.drawable.ic_settings_cards, SettingsGroup.BEHAVIOUR),
    APP_LOCK(R.string.settings_section_app_lock, R.string.settings_section_app_lock_summary, R.drawable.ic_settings_lock, SettingsGroup.SYSTEM),
    BACKUP(R.string.settings_section_backup, R.string.settings_section_backup_summary, R.drawable.ic_settings_backup, SettingsGroup.SYSTEM),

    /** Only listed while `labs.unlocked`. */
    LABS(R.string.settings_section_labs, R.string.settings_section_labs_summary, R.drawable.ic_settings_labs, SettingsGroup.SYSTEM),
    ABOUT(R.string.settings_section_about, R.string.settings_section_about_summary, R.drawable.ic_settings_about, SettingsGroup.SYSTEM),
}
