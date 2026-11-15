package io.github.aj4abinjacob.chikku.home.drawer

import android.content.res.Resources
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerSearchBarStyle
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerSettings
import io.github.aj4abinjacob.chikku.theme.SurfaceColors
import kotlin.math.roundToInt

/**
 * The drawer's search field, row-slot and tab look, in one place. [DrawerController] applies it to
 * the real drawer; the Settings previews call the same functions, so they cannot drift from the
 * launcher. Listeners, accessibility actions and the selection transition stay with the controller.
 */
internal object DrawerChrome {
    /** Space between the search bar and the first drawer row's icon padding. */
    const val FIRST_ROW_GAP_DP = 8
    const val HINT_ALPHA = 0xA0
    /** Search field fill over the drawer, matching SurfaceColors' field on light / dark surfaces. */
    const val FIELD_ALPHA_ON_LIGHT = 0x14
    const val FIELD_ALPHA_ON_DARK = 0x1F
    /** Search field outlines: a light edge, and one with at least 3:1 (WCAG non-text) on white and ~#1B1B1F drawers. */
    const val FIELD_OUTLINE_ALPHA_LIGHT = 0x29
    const val FIELD_OUTLINE_ALPHA_STRONG = 0x80
    const val UNSELECTED_TAB_ALPHA = 0x18
    const val SELECTED_TAB_ALPHA = 0x26
    const val UNSELECTED_SEGMENT_TEXT_ALPHA = 180
    const val LIGHT_CONTENT = 0xFFF2F0F4.toInt()
    const val DARK_CONTENT = 0xFF1B1B1F.toInt()

    /**
     * Label and icon colour over the drawer: the theme's own on an untouched opaque drawer, else light
     * or dark content by [lightContent] (whether the drawer's background prefers light content).
     */
    fun contentColor(themeContent: Int, drawer: DrawerSettings, lightContent: Boolean): Int = when {
        drawer.backgroundColor == null && drawer.backgroundAlpha >= 1f -> themeContent
        lightContent -> LIGHT_CONTENT
        else -> DARK_CONTENT
    }

    /** Search field colours; [outline] is null for the Subtle style. */
    data class SearchColors(val text: Int, val hint: Int, val fill: Int, val outline: Int?)

    /**
     * Search colours over a drawer whose content colour is [content]. The surface's search colours
     * are used only while their tone matches the drawer's: dark search on a light drawer (or the
     * reverse) would put light text on a light background.
     */
    fun searchColors(
        surface: SurfaceColors?,
        backgroundOverridden: Boolean,
        lightContent: Boolean,
        content: Int,
        style: DrawerSearchBarStyle,
    ): SearchColors {
        val theme = surface?.takeIf { !backgroundOverridden && it.isDark == lightContent }
        val text = theme?.onSurface ?: content
        val hint = theme?.onSurfaceVariant ?: ColorUtils.setAlphaComponent(content, HINT_ALPHA)
        // Fill follows the drawer background, not system night mode.
        val fill = theme?.field
            ?: ColorUtils.setAlphaComponent(content, if (lightContent) FIELD_ALPHA_ON_DARK else FIELD_ALPHA_ON_LIGHT)
        // Subtle is Material 3's search bar: tonal fill and the icon, no outline. The fill alone is
        // ~1.2:1 against the drawer, so high contrast adds an outline with a 3:1 edge.
        val outlineAlpha = when (style) {
            DrawerSearchBarStyle.SUBTLE -> 0
            DrawerSearchBarStyle.OUTLINED -> FIELD_OUTLINE_ALPHA_LIGHT
            DrawerSearchBarStyle.HIGH_CONTRAST -> FIELD_OUTLINE_ALPHA_STRONG
        }
        return SearchColors(text, hint, fill, ColorUtils.setAlphaComponent(text, outlineAlpha).takeIf { outlineAlpha > 0 })
    }

    fun searchFieldBackground(res: Resources, colors: SearchColors): GradientDrawable = GradientDrawable().apply {
        cornerRadius = res.getDimension(R.dimen.drawer_search_radius)
        setColor(colors.fill)
        colors.outline?.let { setStroke(res.getDimensionPixelSize(R.dimen.drawer_search_outline), it) }
    }

    // --- Rows --------------------------------------------------------------------------------

    /**
     * Height of one manual-row slot so that [rows] slots fill [viewport] once the first row is trimmed
     * (rows * slot - trim = viewport, trim = (slot - content) / 2 - gap). Unrounded, in the caller's unit.
     */
    fun manualRowSlot(viewport: Float, content: Float, gap: Float, rows: Int): Float =
        (viewport - content / 2f - gap) / (rows.coerceAtLeast(1) - 0.5f)

    /** The launcher's integer first-row trim in pixels. */
    fun firstRowTrim(rowHeight: Int, content: Int, gap: Int): Int = ((rowHeight - content) / 2 - gap).coerceAtLeast(0)

    /** The same trim for the preview's fractional dp units. */
    fun firstRowTrim(rowHeight: Float, content: Float, gap: Float): Float = ((rowHeight - content) / 2 - gap).coerceAtLeast(0f)

    // --- Tabs --------------------------------------------------------------------------------

    private fun dp(res: Resources, value: Int) = (value * res.displayMetrics.density).roundToInt()

    /** Start/end padding of the tab row. [panelMargin] is the unsegmented side padding. */
    fun tabRowSidePadding(res: Resources, segmented: Boolean, panelMargin: Int): Int =
        if (segmented) dp(res, 20) else panelMargin

    /** Size, padding and text size of a tab; the caller adds text, listeners and focusability. */
    fun configureTab(tab: TextView, res: Resources, segmented: Boolean) {
        tab.gravity = Gravity.CENTER
        tab.minHeight = res.getDimensionPixelSize(R.dimen.home_min_touch_target)
        val padding = res.getDimensionPixelSize(R.dimen.drawer_tab_padding)
        tab.setPaddingRelative(padding, 0, padding, if (segmented) dp(res, 8) else 0)
        tab.setTextSize(TypedValue.COMPLEX_UNIT_PX, res.getDimension(R.dimen.home_body_text_size))
    }

    fun tabLayoutParams(res: Resources, segmented: Boolean, index: Int, count: Int): LinearLayout.LayoutParams {
        val fill = segmented && count <= 3
        return LinearLayout.LayoutParams(if (fill) 0 else ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            if (fill) weight = 1f
            marginEnd = if (index == count - 1) 0 else if (segmented) dp(res, 8) else res.getDimensionPixelSize(R.dimen.drawer_tab_spacing)
        }
    }

    /**
     * Text on a segmented tab filled with [accent]: white when it reads at 4.5:1, else dark. A
     * translucent accent (a restored backup can carry any ARGB value) is judged as its opaque colour,
     * because [ColorUtils.calculateContrast] rejects alpha; the fill itself keeps the chosen alpha.
     */
    fun accentTabTextColor(accent: Int): Int =
        if (ColorUtils.calculateContrast(Color.WHITE, ColorUtils.setAlphaComponent(accent, 255)) >= 4.5) Color.WHITE else DARK_CONTENT

    /**
     * Text colour, weight and pill for a tab over content colour [content]; the new pill is assigned
     * to [tab] and returned.
     */
    fun styleTab(tab: TextView, res: Resources, segmented: Boolean, accent: Int?, content: Int, selected: Boolean): Drawable? {
        tab.isSelected = selected
        tab.setTextColor(content)
        tab.setTypeface(null, if (selected || segmented) Typeface.BOLD else Typeface.NORMAL)
        tab.background = if (selected || segmented) {
            GradientDrawable().apply {
                cornerRadius = if (segmented) 14 * res.displayMetrics.density else res.getDimension(R.dimen.drawer_tab_radius)
                val color = if (selected) accent ?: ColorUtils.setAlphaComponent(content, SELECTED_TAB_ALPHA)
                else ColorUtils.setAlphaComponent(content, UNSELECTED_TAB_ALPHA)
                setColor(color)
            }
        } else null
        if (segmented) {
            tab.background = InsetDrawable(tab.background, 0, 0, 0, dp(res, 8))
            if (selected && accent != null) {
                tab.setTextColor(accentTabTextColor(accent))
            } else if (!selected) tab.setTextColor(ColorUtils.setAlphaComponent(content, UNSELECTED_SEGMENT_TEXT_ALPHA))
        }
        return tab.background
    }
}
