package io.github.aj4abinjacob.chikku.home

import android.content.Context
import android.graphics.Rect
import android.graphics.drawable.PaintDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import io.github.aj4abinjacob.chikku.settings.components.CHIKKU_DEFAULT_ACCENT
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings

/**
 * One entry of the long-press-home menu. [order] sorts entries within their [group] (lower first);
 * [isVisible] is evaluated each time the menu opens.
 */
data class HomeMenuEntry(
    val id: String,
    @param:StringRes val label: Int,
    val order: Int,
    val isVisible: () -> Boolean = { true },
    @param:DrawableRes val icon: Int = 0,
    val group: HomeMenuGroup = HomeMenuGroup.PERSONALIZE,
    val onClick: () -> Unit,
)

/** Sections of the home menu, in display order; a divider separates non-empty sections. */
enum class HomeMenuGroup { PERSONALIZE, PAGES, SETTINGS }

/**
 * Menu shown by a long press on an empty part of the desktop (Widgets, Wallpapers,
 * Settings, Cards and the page buttons), in the same card style as the app and widget menus ([LauncherMenuStyle]). Feature code
 * adds its own entries with [addEntry]; an entry with an existing id replaces it. Built-in orders:
 * Widgets 100, Wallpapers 200, Settings 300.
 */
class HomeMenu(
    private val context: Context,
    private val settings: () -> LauncherSettings = { LauncherSettings() },
) {
    private val entries = mutableListOf<HomeMenuEntry>()
    private var popup: PopupWindow? = null

    fun addEntry(entry: HomeMenuEntry) {
        entries.removeAll { it.id == entry.id }
        entries += entry
    }

    fun removeEntry(id: String) {
        entries.removeAll { it.id == id }
    }

    /** Visible entries in display order: by group, then by order. */
    fun visibleEntries(): List<HomeMenuEntry> =
        entries.filter { it.isVisible() }.sortedWith(compareBy({ it.group }, { it.order }))

    val isShowing: Boolean get() = popup?.isShowing == true

    /** Shows the menu near the screen point ([rawX], [rawY]) over [parent]'s window. */
    fun show(parent: View, rawX: Float, rawY: Float) {
        dismiss()
        val items = visibleEntries()
        if (items.isEmpty() || !parent.isAttachedToWindow) return
        val prefs = settings()
        val style = LauncherMenuStyle(context, prefs)
        val card = style.card()
        // A large corner radius (Style setting) would clip the rows' corners: keep them clear of the arc.
        val radius = prefs.lookFeel.menuCornerRadiusDp.takeIf { it.isFinite() }?.coerceIn(0f, LauncherMenuStyle.MAX_CORNER_RADIUS_DP) ?: 14f
        val clear = style.dp(radius * CORNER_CLEARANCE).toInt()
        // Buttons nest inside the card: their radius is the card's minus the inset, so the curves stay concentric.
        val inner = style.dp((radius - clear / style.dp(1f)).coerceIn(MIN_INNER_RADIUS_DP, MAX_INNER_RADIUS_DP))
        val vertical = (clear - style.dp(3f).toInt()).coerceAtLeast(style.dp(6f).toInt())
        card.setPadding(0, vertical, 0, vertical)
        val window = style.popup(card)
        val accent = accentColor(prefs, style)
        val run = { entry: HomeMenuEntry ->
            window.dismiss()
            entry.onClick()
        }
        // Personalize entries are large buttons; every other entry (pages, Settings) is a small
        // button, two to a row, so "Add page" and "Settings" share one row instead of taking two.
        // A button left alone on its row spans the full width.
        val (tiles, pills) = items.partition { it.group == HomeMenuGroup.PERSONALIZE }
        val rows = tiles.chunked(TILES_PER_ROW).map { true to it } + pills.chunked(2).map { false to it }
        rows.forEach { (isTile, chunk) ->
            card.addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                val side = clear.coerceAtLeast(style.dp(8f).toInt())
                setPadding(side, if (isTile) style.dp(4f).toInt() else style.dp(3f).toInt(), side, style.dp(3f).toInt())
                chunk.forEachIndexed { index, entry ->
                    val view = if (isTile) tile(style, entry, accent, inner) { run(entry) } else pill(style, entry, wide = chunk.size == 1, inner) { run(entry) }
                    addView(view, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        if (index > 0) marginStart = style.dp(6f).toInt()
                    })
                }
            })
        }
        window.setOnDismissListener { if (popup === window) popup = null }
        popup = window
        place(window, parent, rawX, rawY, style)
    }

    /** The user's accent, or Chikku's own (lightened on a dark card, as the settings theme does). */
    private fun accentColor(prefs: LauncherSettings, style: LauncherMenuStyle): Int {
        prefs.lookFeel.accentColor?.let { return it or ALPHA_OPAQUE }
        val dark = ColorUtils.calculateLuminance(style.colors.background) < 0.5
        return if (dark) DARK_DEFAULT_ACCENT else CHIKKU_DEFAULT_ACCENT
    }

    /** A large button: the entry's glyph on a tinted chip over its name. At least a 48dp touch target. */
    private fun tile(style: LauncherMenuStyle, entry: HomeMenuEntry, accent: Int, corner: Float, onClick: () -> Unit): View {
        val label = context.getString(entry.label)
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            minimumHeight = style.dp(72f).toInt()
            setPadding(style.dp(2f).toInt(), style.dp(4f).toInt(), style.dp(2f).toInt(), style.dp(4f).toInt())
            contentDescription = label
            isClickable = true
            isFocusable = true
            ripple(this)
            setOnClickListener { onClick() }
            addView(FrameLayout(context).apply {
                background = PaintDrawable(ColorUtils.setAlphaComponent(accent, TILE_CHIP_ALPHA)).apply { setCornerRadius(corner) }
                if (entry.icon != 0) addView(ImageView(context).apply {
                    setImageDrawable(context.getDrawable(entry.icon)?.mutate())
                    setColorFilter(accent)
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }, FrameLayout.LayoutParams(style.dp(20f).toInt(), style.dp(20f).toInt(), Gravity.CENTER))
            }, LinearLayout.LayoutParams(style.dp(44f).toInt(), style.dp(44f).toInt()))
            addView(TextView(context).apply {
                text = label
                setTextColor(style.colors.onSurface)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                gravity = Gravity.CENTER_HORIZONTAL
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = style.dp(5f).toInt()
            })
        }
    }

    /** A tinted pill with a small glyph and the entry's name; [wide] centres it across the card. */
    private fun pill(style: LauncherMenuStyle, entry: HomeMenuEntry, wide: Boolean, corner: Float, onClick: () -> Unit): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = if (wide) Gravity.CENTER else Gravity.CENTER_VERTICAL
            minimumHeight = style.dp(44f).toInt()
            setPadding(style.dp(10f).toInt(), 0, style.dp(10f).toInt(), 0)
            background = PaintDrawable(ColorUtils.setAlphaComponent(style.colors.onSurface, PILL_ALPHA)).apply { setCornerRadius(corner) }
            isClickable = true
            isFocusable = true
            ripple(this)
            setOnClickListener { onClick() }
            if (entry.icon != 0) addView(ImageView(context).apply {
                setImageDrawable(context.getDrawable(entry.icon)?.mutate())
                setColorFilter(style.colors.onSurfaceVariant)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(style.dp(18f).toInt(), style.dp(18f).toInt()).apply { marginEnd = style.dp(6f).toInt() })
            addView(TextView(context).apply {
                text = context.getString(entry.label)
                setTextColor(style.colors.onSurface)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                // A label that is a hair too wide for its half of the row shrinks instead of being cut.
                setAutoSizeTextTypeUniformWithConfiguration(10, 13, 1, TypedValue.COMPLEX_UNIT_SP)
            }, if (wide) LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            else LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }

    private fun ripple(view: View) {
        val out = TypedValue()
        if (context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, out, true)) {
            view.foreground = context.getDrawable(out.resourceId)
        }
    }

    fun dismiss() {
        popup?.dismiss()
        popup = null
    }

    /** Centred on the touch, above it when it fits (so the finger does not cover it), else below. */
    private fun place(window: PopupWindow, parent: View, rawX: Float, rawY: Float, style: LauncherMenuStyle) {
        val frame = Rect().also(parent::getWindowVisibleDisplayFrame)
        val shadow = style.dp(LauncherMenuStyle.CARD_ELEVATION_DP).toInt()
        val margin = style.dp(8f).toInt()
        val width = (style.dp(MENU_WIDTH_DP).toInt() + 2 * shadow).coerceAtMost(frame.width())
        val content = window.contentView
        content.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val height = content.measuredHeight
        val gap = style.dp(TOUCH_GAP_DP).toInt() - shadow
        val x = (rawX - width / 2f).toInt().coerceIn(frame.left + margin - shadow, frame.right - margin + shadow - width)
        val above = rawY.toInt() - gap - height
        val y = (if (above >= frame.top + margin - shadow) above else rawY.toInt() + gap)
            .coerceIn(frame.top, (frame.bottom - height).coerceAtLeast(frame.top))
        val inWindow = IntArray(2).also(parent::getLocationInWindow)
        val onScreen = IntArray(2).also(parent::getLocationOnScreen)
        window.width = width
        window.height = height
        window.showAtLocation(parent, Gravity.TOP or Gravity.START, x - (onScreen[0] - inWindow[0]), y - (onScreen[1] - inWindow[1]))
    }

    companion object {
        const val ENTRY_WIDGETS = "widgets"
        const val ENTRY_WALLPAPERS = "wallpapers"
        const val ENTRY_SETTINGS = "settings"
        private const val MENU_WIDTH_DP = 264f

        /** Inset from a card edge that clears its rounded corner: 1 - cos 45° of the radius, rounded up. */
        private const val CORNER_CLEARANCE = 0.3f
        private const val MIN_INNER_RADIUS_DP = 8f
        private const val MAX_INNER_RADIUS_DP = 22f
        private const val TILES_PER_ROW = 3
        private const val TILE_CHIP_ALPHA = 0x29
        private const val PILL_ALPHA = 0x14
        private const val ALPHA_OPAQUE = 0xFF000000.toInt()
        private const val DARK_DEFAULT_ACCENT = 0xFFFFAD89.toInt()
        private const val TOUCH_GAP_DP = 16f
    }
}
