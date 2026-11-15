package io.github.aj4abinjacob.chikku.home.appmenu

import android.content.Context
import android.text.TextUtils
import android.graphics.drawable.PaintDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.graphics.ColorUtils
import kotlin.math.ceil

/** Most labelled action buttons in one row of a long-press menu. */
internal const val MAX_ACTION_COLUMNS = 4

/**
 * Width an action button asks for when its card wraps to the buttons (no shortcuts above it).
 * A full row of four keeps roomy cells; shorter rows get narrower ones so their chips don't drift
 * apart, but never so narrow that a two-word label like "Edit folder" wraps at normal font size.
 */
internal fun preferredCellDp(columns: Int): Float = when {
    columns >= MAX_ACTION_COLUMNS -> 76f
    columns == 3 -> 66f
    else -> 64f
}

/** Glyph size of a labelled action: smaller than a shortcut's app icon, so the two cards read apart. */
private const val ACTION_ICON_DP = 18f

/** The tinted chip behind a glyph, and how strongly it is tinted (of 255). */
private const val CHIP_DP = 34f
private const val CHIP_RADIUS_DP = 12f
private const val CHIP_ALPHA = 0x22

/** Stronger chip tint on a dark card, where a faint tint disappears into the fill. */
private const val CHIP_ALPHA_DARK = 0x40

/** Shortest an action button gets: a 48dp touch target. */
private const val MIN_BUTTON_DP = 52f

/** Narrowest an action button gets before the row wraps; still wider than a 48dp touch target. */
private const val MIN_CELL_DP = 56f

/**
 * Columns for [count] action buttons: rows as even as they can be, at most [max] per row
 * (5 → 3 + 2, 7 → 4 + 3), so a lone button never trails on a row of its own.
 */
internal fun actionColumns(count: Int, max: Int = MAX_ACTION_COLUMNS): Int {
    if (count <= 0) return 1
    val rows = ceil(count / max.toDouble()).toInt()
    return ceil(count / rows.toDouble()).toInt()
}

/** Light action colours sit on dark cards. */
private fun isLightColor(color: Int) = ColorUtils.calculateLuminance(color) > 0.5

/**
 * One labelled launcher action: a small glyph on a tinted chip over its name, both in [color]
 * (the menu's secondary tone, see LauncherMenuStyle.actionColor; the danger tone for Uninstall). The
 * whole button is the touch target, at least [MIN_BUTTON_DP] tall. The button announces its label
 * to TalkBack; with [onClick] null (settings previews) it is inert and not focusable.
 */
internal fun menuActionButton(context: Context, label: String, @DrawableRes icon: Int, color: Int, onClick: (() -> Unit)?): View {
    val density = context.resources.displayMetrics.density
    fun dp(value: Float) = (value * density).toInt()
    return LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        minimumHeight = dp(MIN_BUTTON_DP)
        setPadding(dp(2f), dp(6f), dp(2f), dp(6f))
        contentDescription = label
        if (onClick != null) {
            isClickable = true
            isFocusable = true
            val ripple = TypedValue()
            if (context.theme.resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)) setBackgroundResource(ripple.resourceId)
            setOnClickListener { onClick() }
        } else {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        addView(FrameLayout(context).apply {
            // Not a GradientDrawable: those mark the menu's cards (see AppMenuCustomizationTest).
            background = PaintDrawable(ColorUtils.setAlphaComponent(color, if (isLightColor(color)) CHIP_ALPHA_DARK else CHIP_ALPHA)).apply {
                setCornerRadius(dp(CHIP_RADIUS_DP).toFloat())
            }
            addView(ImageView(context).apply {
                setImageDrawable(context.getDrawable(icon)?.mutate())
                setColorFilter(color)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, FrameLayout.LayoutParams(dp(ACTION_ICON_DP), dp(ACTION_ICON_DP), Gravity.CENTER))
        }, LinearLayout.LayoutParams(dp(CHIP_DP), dp(CHIP_DP)))
        addView(TextView(context).apply {
            text = label
            setTextColor(color)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            gravity = Gravity.CENTER_HORIZONTAL
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(3f)
        })
    }
}

/** One launcher action to show in a [MenuActionGrid]; [onClick] null makes it inert (settings previews). */
internal class GridAction(val label: String, @DrawableRes val icon: Int, val color: Int, val onClick: (() -> Unit)?)

/**
 * The launcher actions of a long-press menu as equal-width labelled buttons ([menuActionButton]),
 * in rows from [actionColumns] (fewer when the card is too narrow). A short last row is spread so
 * its chips share the full width; rows run right to left in RTL. Each row is as tall as its
 * tallest label.
 */
internal class MenuActionGrid(context: Context) : ViewGroup(context) {
    private val density = resources.displayMetrics.density
    private var columns = 1
    private var rowHeights = IntArray(0)

    /** Adds [actions] as chips and returns them in order; a short last row is spread across the card. */
    fun addActions(actions: List<GridAction>): List<View> = actions.map { action ->
        menuActionButton(context, action.label, action.icon, action.color, action.onClick).also { addView(it) }
    }

    /** Buttons in [row]: a full row, or what is left for the last one. */
    private fun rowCount(row: Int) = (childCount - row * columns).coerceAtMost(columns)

    override fun onRtlPropertiesChanged(layoutDirection: Int) {
        super.onRtlPropertiesChanged(layoutDirection)
        // Direction can change without a size change. Re-place buttons after inherited RTL resolves.
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val wanted = actionColumns(childCount)
        val horizontalPadding = paddingLeft + paddingRight
        val width = resolveSize(horizontalPadding + (wanted * preferredCellDp(wanted) * density).toInt(), widthMeasureSpec)
        val fit = ((width - horizontalPadding) / (MIN_CELL_DP * density)).toInt()
        columns = wanted.coerceAtMost(fit).coerceAtLeast(1)
        val rows = (childCount + columns - 1) / columns
        rowHeights = IntArray(rows)
        val inner = width - horizontalPadding
        fun cellWidth(index: Int) = inner / rowCount(index / columns)
        fun spec(index: Int, exactHeight: Int?): Pair<Int, Int> {
            val w = MeasureSpec.makeMeasureSpec(cellWidth(index), MeasureSpec.EXACTLY)
            val h = if (exactHeight == null) MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            else MeasureSpec.makeMeasureSpec(exactHeight, MeasureSpec.EXACTLY)
            return w to h
        }
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val (w, h) = spec(i, null)
            child.measure(w, h)
            val outer = child.measuredHeight
            rowHeights[i / columns] = maxOf(rowHeights[i / columns], outer)
        }
        // Every button in a row gets the row's height, so their ripples line up.
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val outer = child.measuredHeight
            if (outer != rowHeights[i / columns]) {
                val (w, h) = spec(i, rowHeights[i / columns])
                child.measure(w, h)
            }
        }
        setMeasuredDimension(width, resolveSize(paddingTop + paddingBottom + rowHeights.sum(), heightMeasureSpec))
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        val inner = width - paddingLeft - paddingRight
        var y = paddingTop
        for (row in rowHeights.indices) {
            val first = row * columns
            val count = rowCount(row)
            val cell = inner / count
            val start = (inner - count * cell) / 2
            for (column in 0 until count) {
                val x = if (rtl) width - paddingRight - start - (column + 1) * cell else paddingLeft + start + column * cell
                getChildAt(first + column).let { it.layout(x, y, x + cell, y + it.measuredHeight) }
            }
            y += rowHeights[row]
        }
    }
}
