package io.github.aj4abinjacob.chikku.search

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.InsetDrawable
import androidx.core.graphics.ColorUtils
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.settings.WebSearchEngine
import io.github.aj4abinjacob.chikku.core.model.settings.SearchSettings
import io.github.aj4abinjacob.chikku.core.model.settings.HomeSearchBarStyle
import io.github.aj4abinjacob.chikku.core.model.settings.HomeSearchBarIcon
import io.github.aj4abinjacob.chikku.core.model.settings.HomeSearchBarButtonSlot
import io.github.aj4abinjacob.chikku.core.model.search.WebSearchUrls
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

/**
 * Home search bar. A tap on the bar itself does [SearchSettings.widgetTapAction]; up to two buttons
 * of its own sit at either end, each its own accessible action with a full-height touch target:
 * [chikkuButton] (always Chikku search, drawn with the chosen glyph) and [webButton] (the selected
 * engine). Buttons are clickable only once a listener is set, so previews stay inert.
 */
class SearchWidgetView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {
    private val providerMark = ProviderMark(context)
    private val searchGlyph = ImageView(context)
    /** Neutral mark on the web button in the Minimal style; [providerMark] shows in Provider. */
    internal val minimalMark = ImageView(context)
    private val spacer = View(context)
    /** Opens Chikku search; attached only when [SearchSettings.widgetChikkuButton] is not hidden. */
    val chikkuButton = barButton(searchGlyph, dp(28))
    /** Opens the selected web engine; attached only when [SearchSettings.widgetWebButton] is not hidden. */
    val webButton = barButton(FrameLayout(context).apply {
        addView(providerMark, FrameLayout.LayoutParams(dp(30), dp(30), Gravity.CENTER))
        addView(minimalMark, FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER))
    }, dp(30))
    private var barStyle = HomeSearchBarStyle.PROVIDER
    /** The glyph currently drawn; read by tests and settings previews. */
    var icon = HomeSearchBarIcon.NOSE
        private set
    private var renderedAppearance: Triple<Int, Float, Boolean>? = null
    private var horizontalPadding = dp(20)
    private var verticalPadding = dp(10)
    private var slots: Pair<HomeSearchBarButtonSlot, HomeSearchBarButtonSlot>? = null

    /** The engine on the web button, or null while that button is hidden. */
    var engine: WebSearchEngine? = null
        private set

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(56)
        setPaddingRelative(dp(20), dp(10), dp(20), dp(10))
        setBackgroundResource(R.drawable.bg_search_widget)
        elevation = dp(2).toFloat()
        isClickable = true
        isFocusable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        searchGlyph.setImageResource(icon.glyphRes())
        minimalMark.setImageResource(R.drawable.ic_search_bubbles)
        minimalMark.visibility = GONE
        arrange(HomeSearchBarButtonSlot.START, HomeSearchBarButtonSlot.END)
        engine = WebSearchEngine.DUCKDUCKGO
        providerMark.engine = engine
    }

    /** Shared by top and below-dock bars, independent of universal-search window styling. */
    fun applyAppearance(settings: SearchSettings) {
        if (icon != settings.widgetIcon) {
            icon = settings.widgetIcon
            searchGlyph.setImageResource(icon.glyphRes())
        }
        arrange(settings.widgetChikkuButton, settings.widgetWebButton)
        engine = settings.webEngine.takeIf { settings.widgetWebButton != HomeSearchBarButtonSlot.HIDDEN }
        providerMark.engine = settings.webEngine
        val engineName = WebSearchUrls.engineName(settings.webEngine)
        chikkuButton.contentDescription = resources.getString(R.string.search_home_bar_chikku_button)
        webButton.contentDescription = resources.getString(R.string.search_home_bar_web_button, engineName)
        if (barStyle != settings.widgetStyle) {
            barStyle = settings.widgetStyle
            renderedAppearance = null
            providerMark.visibility = if (barStyle == HomeSearchBarStyle.PROVIDER) VISIBLE else GONE
            minimalMark.visibility = if (barStyle == HomeSearchBarStyle.MINIMAL) VISIBLE else GONE
            requestLayout()
        }
        val height = dp(settings.widgetHeightDp.coerceIn(48, 96))
        val horizontal = dp(settings.widgetHorizontalPaddingDp.coerceIn(0, 48))
        val vertical = dp(settings.widgetVerticalPaddingDp.coerceIn(0, 24))
        if (minimumHeight != height || horizontalPadding != horizontal || verticalPadding != vertical) {
            minimumHeight = height
            horizontalPadding = horizontal
            verticalPadding = vertical
            requestLayout()
        }
        val color = settings.widgetColor ?: 0xFF22304F.toInt()
        val radius = settings.widgetCornerRadiusDp.takeIf(Float::isFinite)?.coerceIn(0f, 48f) ?: 32f
        val state = Triple(color, radius, settings.widgetShadow)
        if (state == renderedAppearance) return
        renderedAppearance = state
        val content = if (ColorUtils.calculateLuminance(color) < .18) Color.WHITE else 0xFF1B1B1F.toInt()
        val shape = GradientDrawable().apply {
            this.shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = radius * resources.displayMetrics.density
        }
        val mask = GradientDrawable().apply {
            this.shape = GradientDrawable.RECTANGLE
            setColor(Color.WHITE)
            cornerRadius = radius * resources.displayMetrics.density
        }
        val pressed = ColorStateList.valueOf(ColorUtils.setAlphaComponent(content, 0x20))
        val ripple = RippleDrawable(pressed, shape, mask)
        // Keep a 48dp touch target while the minimal capsule has a lighter, 44dp painted height.
        background = if (barStyle == HomeSearchBarStyle.MINIMAL) InsetDrawable(ripple, 0, dp(2), 0, dp(2)) else ripple
        // Borderless, so a button press reads as the button and not the whole bar.
        listOf(chikkuButton, webButton).forEach { button ->
            button.background = RippleDrawable(ColorStateList.valueOf(ColorUtils.setAlphaComponent(content, 0x30)), null, null)
                .also { buttonRipple -> buttonRipple.radius = dp(24) }
        }
        searchGlyph.imageTintList = ColorStateList.valueOf(content)
        minimalMark.imageTintList = ColorStateList.valueOf(content)
        elevation = if (settings.widgetShadow) dp(4).toFloat() else 0f
    }

    /** Chikku sits nearest the edge when both buttons share a side; hidden buttons are detached. */
    private fun arrange(chikku: HomeSearchBarButtonSlot, web: HomeSearchBarButtonSlot) {
        if (slots == chikku to web) return
        slots = chikku to web
        removeAllViews()
        if (chikku == HomeSearchBarButtonSlot.START) addView(chikkuButton)
        if (web == HomeSearchBarButtonSlot.START) addView(webButton)
        addView(spacer, LayoutParams(0, 0, 1f))
        if (web == HomeSearchBarButtonSlot.END) addView(webButton)
        if (chikku == HomeSearchBarButtonSlot.END) addView(chikkuButton)
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val availableWidth = MeasureSpec.getSize(widthMeasureSpec)
        val availableHeight = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) {
            minimumHeight
        } else {
            MeasureSpec.getSize(heightMeasureSpec).coerceAtMost(minimumHeight)
        }
        val (chikku, web) = slots ?: (HomeSearchBarButtonSlot.START to HomeSearchBarButtonSlot.END)
        val buttons = listOf(chikku to chikkuButton, web to webButton).filter { it.first != HomeSearchBarButtonSlot.HIDDEN }
        // Keep the icons inside a narrow capsule that has generous padding.
        val sides = buttons.map { it.first }.distinct()
        val contentWidth = buttons.sumOf { it.second.layoutParams.width } - sides.size * dp(BUTTON_INSET_DP)
        val horizontal = horizontalPadding.coerceAtMost(((availableWidth - contentWidth) / 2).coerceAtLeast(0))
        val vertical = verticalPadding.coerceAtMost(((availableHeight - dp(30)) / 2).coerceAtLeast(0))
        setPaddingRelative(horizontal, vertical, horizontal, vertical)
        // Padding places the icons; each button's touch area reaches past its icon into the padding
        // (and the bar's full height), so even a small capsule keeps 48dp targets.
        val edgeInset = -minOf(dp(BUTTON_INSET_DP), horizontal)
        val first = getChildAt(0).takeIf { it !== spacer }
        val last = getChildAt(childCount - 1).takeIf { it !== spacer }
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        buttons.forEach { (_, button) ->
            val params = button.layoutParams as LinearLayout.LayoutParams
            val start = if (button === first) edgeInset else 0
            val end = if (button === last) edgeInset else 0
            params.topMargin = -vertical
            params.bottomMargin = -vertical
            params.leftMargin = if (rtl) end else start
            params.rightMargin = if (rtl) start else end
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun barButton(content: View, iconSize: Int) = FrameLayout(context).apply {
        content.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        addView(content, FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER))
        isClickable = false
        isFocusable = false
        layoutParams = LinearLayout.LayoutParams(iconSize + 2 * dp(BUTTON_INSET_DP), LinearLayout.LayoutParams.MATCH_PARENT)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + .5f).toInt()

    private companion object {
        /** Touch area beyond each side of a button's icon: a 28dp glyph gets a 48dp target. */
        const val BUTTON_INSET_DP = 10
    }
}

/** Drawable for each home search capsule glyph; settings show the same drawables. */
@DrawableRes
fun HomeSearchBarIcon.glyphRes(): Int = when (this) {
    HomeSearchBarIcon.NOSE -> R.drawable.ic_search_bar_nose
    HomeSearchBarIcon.PAW -> R.drawable.ic_search_bar_paw
    HomeSearchBarIcon.EARS -> R.drawable.ic_search_bar_ears
    HomeSearchBarIcon.FACE -> R.drawable.ic_search_bar_face
    HomeSearchBarIcon.NOTCH -> R.drawable.ic_search_bar_notch
    HomeSearchBarIcon.MARK -> R.drawable.ic_search_bar_mark
    HomeSearchBarIcon.PLAIN -> R.drawable.ic_search_bar_plain
}

/** User-facing name of each glyph, in the settings choice and the preview's TalkBack description. */
@StringRes
fun HomeSearchBarIcon.labelRes(): Int = when (this) {
    HomeSearchBarIcon.NOSE -> R.string.search_settings_bar_icon_nose
    HomeSearchBarIcon.PAW -> R.string.search_settings_bar_icon_paw
    HomeSearchBarIcon.EARS -> R.string.search_settings_bar_icon_ears
    HomeSearchBarIcon.FACE -> R.string.search_settings_bar_icon_face
    HomeSearchBarIcon.NOTCH -> R.string.search_settings_bar_icon_notch
    HomeSearchBarIcon.MARK -> R.string.search_settings_bar_icon_mark
    HomeSearchBarIcon.PLAIN -> R.string.search_settings_bar_icon_plain
}

/** Original typographic provider marks; no third-party logos or assets are bundled. */
private class ProviderMark(context: Context) : View(context) {
    var engine: WebSearchEngine? = null
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val selected = engine ?: return
        val size = minOf(width, height).toFloat()
        val cx = width / 2f
        val cy = height / 2f
        paint.shader = null
        val (mark, color) = when (selected) {
            WebSearchEngine.GOOGLE -> "G" to Color.TRANSPARENT
            WebSearchEngine.DUCKDUCKGO -> "D" to 0xFFDE5833.toInt()
            WebSearchEngine.BING -> "b" to 0xFF1683A5.toInt()
            WebSearchEngine.BRAVE -> "Br" to 0xFFF15A24.toInt()
            WebSearchEngine.STARTPAGE -> "S" to 0xFF7465CF.toInt()
            WebSearchEngine.ECOSIA -> "E" to 0xFF368A5C.toInt()
        }
        if (selected != WebSearchEngine.GOOGLE) {
            paint.color = color
            canvas.drawCircle(cx, cy, size / 2f, paint)
            paint.color = Color.WHITE
        } else {
            paint.shader = SweepGradient(cx, cy,
                intArrayOf(0xFF4285F4.toInt(), 0xFF34A853.toInt(), 0xFFFBBC05.toInt(), 0xFFEA4335.toInt(), 0xFF4285F4.toInt()),
                floatArrayOf(0f, .28f, .53f, .73f, 1f),
            )
        }
        // These are decorative marks, not labels: size them like vector icons at every font scale.
        paint.textSize = size * if (mark.length > 1) .48f else if (selected == WebSearchEngine.GOOGLE) .95f else .65f
        val baseline = cy - (paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2f
        canvas.drawText(mark, cx, baseline, paint)
    }
}
