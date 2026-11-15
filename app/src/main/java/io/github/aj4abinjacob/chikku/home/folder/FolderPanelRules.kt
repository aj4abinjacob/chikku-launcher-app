package io.github.aj4abinjacob.chikku.home.folder

import android.content.Context
import android.content.res.Resources
import android.graphics.drawable.GradientDrawable
import android.widget.LinearLayout
import androidx.core.graphics.ColorUtils
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.settings.FolderSettings
import io.github.aj4abinjacob.chikku.home.AppIconView
import kotlin.math.max

/**
 * How the open folder panel looks and sizes its cells. [FolderPopup] builds the real panel with
 * these; the Settings folder preview calls the same functions, so the two cannot drift. The popup
 * adds its scroller, pager buttons and listeners; the preview deliberately has none.
 */
internal object FolderPanelRules {
    /** A panel less opaque than this is "see-through": light text, wallpaper-style icons, no outline. */
    const val SEE_THROUGH_ALPHA = 0.5f
    const val DARK_LUMINANCE = 0.5
    const val OUTLINE_ALPHA = 28
    private const val ALPHA_MAX = 255

    /** Light text goes on dark or see-through panels, dark text on light ones. */
    fun isDark(base: Int, alpha: Float): Boolean =
        alpha < SEE_THROUGH_ALPHA || ColorUtils.calculateLuminance(ColorUtils.setAlphaComponent(base, ALPHA_MAX)) < DARK_LUMINANCE

    fun iconStyle(alpha: Float): AppIconView.Style =
        if (alpha < SEE_THROUGH_ALPHA) AppIconView.Style.WORKSPACE else AppIconView.Style.DRAWER

    fun panelColor(base: Int, alpha: Float): Int = ColorUtils.setAlphaComponent(base, (alpha * ALPHA_MAX).toInt())

    /** One cell's height: the icon and label, never below a touch target. */
    fun cellHeight(context: Context, iconStyle: AppIconView.Style, folders: FolderSettings): Int =
        AppIconView.desiredHeight(
            context, iconStyle, folders.iconScale, folders.labelScale,
            folders.showLabels, folders.labelSingleLine, folders.labelFont,
        ).coerceAtLeast(context.resources.getDimensionPixelSize(R.dimen.home_min_touch_target))

    fun cellWidth(res: Resources, folders: FolderSettings, padding: Int): Int = max(
        res.getDimensionPixelSize(R.dimen.home_drawer_column_width),
        (res.getDimensionPixelSize(R.dimen.home_icon_size) * folders.iconScale).toInt() + padding,
    )

    /** The panel's padding, tonal background with hairline edge, and elevation. */
    fun styleFrame(panel: LinearLayout, res: Resources, folders: FolderSettings, immersive: Boolean, padding: Int, panelColor: Int, alpha: Float, textColor: Int) {
        panel.orientation = LinearLayout.VERTICAL
        if (immersive) panel.setPadding(padding, padding / 2, padding, padding) else panel.setPadding(padding, padding, padding, padding / 2)
        panel.background = GradientDrawable().apply {
            setColor(panelColor)
            cornerRadius = folders.cornerRadiusDp * res.displayMetrics.density
            // Hairline edge: keeps the panel's shape legible on busy or same-toned wallpapers.
            if (alpha >= SEE_THROUGH_ALPHA) {
                setStroke(res.displayMetrics.density.toInt().coerceAtLeast(1), ColorUtils.setAlphaComponent(textColor, OUTLINE_ALPHA))
            }
        }
        panel.elevation = if (folders.windowShadow) 12f * res.displayMetrics.density else 0f
    }
}
