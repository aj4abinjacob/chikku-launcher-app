package io.github.aj4abinjacob.chikku.home

import android.content.Context
import io.github.aj4abinjacob.chikku.core.model.settings.DockSettings
import io.github.aj4abinjacob.chikku.core.model.settings.HomeSettings

/**
 * The dock container's padding and minimum cell height, as the home dock sets them. Shared with
 * the Settings dock previews so their sample dock is the same size as the real one.
 */
internal fun CellLayout.applyDockContainer(context: Context, dock: DockSettings, home: HomeSettings, density: Float) {
    val horizontal = HomeLayoutMetrics.paddingPx(dock.horizontalPadding ?: home.horizontalPadding, density)
    val vertical = HomeLayoutMetrics.paddingPx(dock.verticalPadding, density)
    setPaddingRelative(horizontal, vertical, horizontal, vertical)
    minCellHeight = AppIconView.desiredHeight(
        context, AppIconView.Style.WORKSPACE, dock.effectiveIconScale(home), dock.effectiveLabelScale(home), dock.showLabels,
        dock.labelSingleLine, dock.labelFont,
    )
}
