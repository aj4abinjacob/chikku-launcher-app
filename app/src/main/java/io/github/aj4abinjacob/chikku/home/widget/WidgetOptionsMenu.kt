package io.github.aj4abinjacob.chikku.home.widget

import android.app.Activity
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.core.model.settings.PaddingSize
import io.github.aj4abinjacob.chikku.home.HomeLayoutMetrics
import io.github.aj4abinjacob.chikku.home.LauncherMenuStyle
import io.github.aj4abinjacob.chikku.home.showItemFootprint
import io.github.aj4abinjacob.chikku.settings.components.spacingLabel
import io.github.aj4abinjacob.chikku.home.appmenu.MenuPlacement

/**
 * Long-press widget menu, styled like the app menu: one rounded card in the menu surface colors,
 * icon rows, and an inline padding choice that applies live without closing the menu. It sits
 * beside the widget (below when it fits, else above) instead of covering it.
 */
class WidgetOptionsMenu(
    private val activity: Activity,
    private val settings: () -> LauncherSettings,
) {
    class Action(val title: Int, val icon: Int, val onClick: () -> Unit)

    /** [current] null means the widget follows [global]. */
    class Padding(val current: PaddingSize?, val global: PaddingSize, val onSelect: (PaddingSize?) -> Unit)

    private var popup: PopupWindow? = null

    fun dismiss() {
        popup?.dismiss()
        popup = null
    }

    fun show(anchor: View, actions: List<Action>, padding: Padding?, destructive: Action?) {
        if (!anchor.isAttachedToWindow) return
        dismiss()
        val prefs = settings()
        val style = LauncherMenuStyle(activity, prefs)
        val card = style.card()
        actions.forEach { card.addView(row(it, style)) }
        if (padding != null) {
            if (card.childCount > 0) card.addView(style.divider())
            card.addView(paddingSection(padding, style))
        }
        if (destructive != null) {
            if (card.childCount > 0) card.addView(style.divider())
            card.addView(row(destructive, style))
        }
        val window = style.popup(card)
        val clearFootprint = showItemFootprint(anchor, prefs)
        window.setOnDismissListener {
            clearFootprint()
            if (popup === window) popup = null
        }
        popup = window
        place(window, anchor)
    }

    private fun row(action: Action, style: LauncherMenuStyle): View =
        style.row(activity.getString(action.title), action.icon) {
            dismiss()
            action.onClick()
        }

    /** "Padding" title with the effective amount, then one chip per choice. Taps apply live. */
    private fun paddingSection(padding: Padding, style: LauncherMenuStyle): View {
        val colors = style.colors
        val section = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val h = dp(16f).toInt()
            setPadding(h, dp(8f).toInt(), h, dp(10f).toInt())
        }
        val value = TextView(activity).apply {
            setTextColor(colors.onSurfaceVariant)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        }
        section.addView(LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(32f).toInt()
            addView(style.icon(R.drawable.ic_menu_padding), LinearLayout.LayoutParams(dp(22f).toInt(), dp(22f).toInt()).apply {
                marginEnd = dp(16f).toInt()
            })
            addView(TextView(activity).apply {
                setText(R.string.widgets_padding)
                setTextColor(colors.onSurface)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(value)
        })

        val choices: List<PaddingSize?> = listOf(null) + PaddingSize.entries
        var selected = padding.current
        val chips = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            background = pill(colors.field)
            val inset = dp(3f).toInt()
            setPadding(inset, inset, inset, inset)
        }
        fun refresh() {
            value.text = activity.getString(R.string.widgets_padding_value, HomeLayoutMetrics.paddingDp(selected ?: padding.global))
            for (i in 0 until chips.childCount) {
                val chip = chips.getChildAt(i) as TextView
                val on = choices[i] == selected
                chip.isSelected = on
                chip.background = if (on) pill(colors.onSurface) else null
                chip.setTextColor(if (on) colors.background else colors.onSurface)
            }
        }
        choices.forEach { choice ->
            chips.addView(TextView(activity).apply {
                text = if (choice == null) activity.getString(R.string.widgets_padding_auto) else shortLabel(choice)
                contentDescription = if (choice == null) {
                    activity.getString(R.string.widgets_padding_default, fullLabel(padding.global))
                } else {
                    fullLabel(choice)
                }
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                minHeight = dp(36f).toInt()
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    if (selected == choice) return@setOnClickListener
                    selected = choice
                    refresh()
                    padding.onSelect(choice)
                }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, if (choice == null) 1.4f else 1f))
        }
        refresh()
        section.addView(chips, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(8f).toInt()
        })
        return section
    }

    private fun shortLabel(size: PaddingSize): String = HomeLayoutMetrics.paddingDp(size).toString()

    private fun fullLabel(size: PaddingSize): String = spacingLabel(activity, size)

    private fun pill(color: Int) = GradientDrawable().apply {
        cornerRadius = dp(18f)
        setColor(color)
    }

    /** Below the widget when it fits, else above, else on the roomier side (scroll-free, capped). */
    private fun place(window: PopupWindow, anchor: View) {
        val frame = Rect().also(anchor::getWindowVisibleDisplayFrame)
        val margin = dp(8f).toInt()
        val width = dp(MENU_WIDTH_DP).toInt().coerceAtMost((frame.width() - 2 * margin).coerceAtLeast(0))
        val content = window.contentView
        content.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val loc = IntArray(2).also(anchor::getLocationOnScreen)
        val inset = (anchor as? WidgetCellView)?.paddingTop ?: 0
        val pos = MenuPlacement.place(
            loc[0], loc[1] + inset, loc[0] + anchor.width, loc[1] + anchor.height - inset,
            frame.left, frame.top, frame.right, frame.bottom,
            content.measuredWidth, content.measuredHeight,
            // The frame's transparent shadow padding may overlap the widget; the card does not.
            overlap = dp(LauncherMenuStyle.CARD_ELEVATION_DP).toInt() - dp(4f).toInt(),
            margin = margin,
        )
        val inWindow = IntArray(2).also(anchor::getLocationInWindow)
        window.width = content.measuredWidth
        window.height = pos.height
        window.showAtLocation(anchor, Gravity.TOP or Gravity.START, pos.x - (loc[0] - inWindow[0]), pos.y - (loc[1] - inWindow[1]))
    }

    private fun dp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, activity.resources.displayMetrics)

    private companion object {
        const val MENU_WIDTH_DP = 280f
    }
}
