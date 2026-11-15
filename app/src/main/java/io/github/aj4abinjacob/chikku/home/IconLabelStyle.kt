package io.github.aj4abinjacob.chikku.home

import io.github.aj4abinjacob.chikku.core.model.settings.DockLabelFont
import io.github.aj4abinjacob.chikku.core.model.settings.DockSettings
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerSettings
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerStyle
import io.github.aj4abinjacob.chikku.core.model.settings.FolderSettings
import io.github.aj4abinjacob.chikku.core.model.settings.HomeSettings

/**
 * The icon and label options an [AppIconView] takes from settings, applied in one place. The
 * launcher and every Settings preview build one from the settings object that feeds their surface,
 * so a new label option is added here once. [labelColor] null keeps the style's own colour.
 */
data class IconLabelStyle(
    val iconScale: Float = 1f,
    val showLabels: Boolean = true,
    val labelScale: Float = 1f,
    val labelFont: DockLabelFont = DockLabelFont.NORMAL,
    val labelShadow: Boolean = false,
    val labelSingleLine: Boolean = true,
    val labelColor: Int? = null,
    val listMode: Boolean = false,
    /** Home screen only: gap between icon and label, null = the default. */
    val labelGapDp: Int? = null,
    /** Home screen only: shifts icon and label up. */
    val verticalOffsetDp: Int = 0,
) {
    fun applyTo(view: AppIconView) {
        view.iconScale = iconScale
        view.showLabel = showLabels
        view.labelScale = labelScale
        view.labelFont = labelFont
        view.labelShadow = labelShadow
        view.labelSingleLine = labelSingleLine
        view.listMode = listMode
        view.labelGapDp = labelGapDp
        view.verticalOffsetDp = verticalOffsetDp
        view.setLabelColor(labelColor)
    }

    companion object {
        /** Home-screen icons; [labelColor] is the home setting unless the caller has a derived tone. */
        fun home(home: HomeSettings, labelColor: Int? = home.labelColor) = IconLabelStyle(
            iconScale = home.iconScale,
            showLabels = home.showLabels,
            labelScale = home.labelScale,
            labelFont = home.labelFont,
            labelShadow = home.labelShadow,
            labelSingleLine = home.labelSingleLine,
            labelColor = labelColor,
            labelGapDp = home.iconLabelGapDp,
            verticalOffsetDp = home.iconVerticalOffsetDp,
        )

        /** Dock icons: their own sizes (or the home ones) and a label colour from the dock background. */
        fun dock(dock: DockSettings, home: HomeSettings, labelColor: Int?) = IconLabelStyle(
            iconScale = dock.effectiveIconScale(home),
            showLabels = dock.showLabels,
            labelScale = dock.effectiveLabelScale(home),
            labelFont = dock.labelFont,
            labelShadow = dock.labelShadow,
            labelSingleLine = dock.labelSingleLine,
            labelColor = labelColor,
        )

        fun drawer(drawer: DrawerSettings, iconScale: Float = drawer.iconScale, labelColor: Int? = drawer.labelColor) = IconLabelStyle(
            iconScale = iconScale,
            showLabels = drawer.showLabels,
            labelScale = drawer.labelScale,
            labelFont = drawer.labelFont,
            labelShadow = drawer.labelShadow,
            labelSingleLine = drawer.labelSingleLine,
            labelColor = labelColor,
            listMode = drawer.style == DrawerStyle.LIST,
        )

        fun folder(folder: FolderSettings, labelColor: Int? = folder.labelColor) = IconLabelStyle(
            iconScale = folder.iconScale,
            showLabels = folder.showLabels,
            labelScale = folder.labelScale,
            labelFont = folder.labelFont,
            labelShadow = folder.labelShadow,
            labelSingleLine = folder.labelSingleLine,
            labelColor = labelColor,
        )
    }
}
