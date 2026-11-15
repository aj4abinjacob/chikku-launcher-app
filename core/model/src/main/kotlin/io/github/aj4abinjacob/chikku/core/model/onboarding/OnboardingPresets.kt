package io.github.aj4abinjacob.chikku.core.model.onboarding

import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings

/**
 * Desktop grid and icon size presets offered during onboarding. [LARGE] and [EXTRA_LARGE] trade
 * cells for bigger icons and labels (easier to see and hit, e.g. for older users).
 */
enum class LayoutPreset(val columns: Int, val rows: Int, val iconScale: Float, val labelScale: Float) {
    COMPACT(columns = 6, rows = 6, iconScale = 0.9f, labelScale = 0.9f),
    STANDARD(columns = 5, rows = 5, iconScale = 1f, labelScale = 1f),
    LARGE(columns = 4, rows = 5, iconScale = 1.25f, labelScale = 1.2f),
    EXTRA_LARGE(columns = 3, rows = 4, iconScale = 1.5f, labelScale = 1.4f),
    ;

    /** Applies the preset to the desktop, and scales dock and drawer icons to match. */
    fun applyTo(settings: LauncherSettings): LauncherSettings = settings.copy(
        home = settings.home.copy(desktopColumns = columns, desktopRows = rows, iconScale = iconScale, labelScale = labelScale),
        dock = settings.dock.copy(iconCount = minOf(settings.dock.iconCount, maxOf(columns, DOCK_MIN)), iconScale = iconScale),
        drawer = settings.drawer.copy(iconScale = iconScale),
    ).coerced()

    companion object {
        private const val DOCK_MIN = 4

        /** The preset matching [settings], or null for a custom grid. */
        fun of(settings: LauncherSettings): LayoutPreset? = entries.firstOrNull {
            it.columns == settings.home.desktopColumns && it.rows == settings.home.desktopRows &&
                it.iconScale == settings.home.iconScale
        }
    }
}
