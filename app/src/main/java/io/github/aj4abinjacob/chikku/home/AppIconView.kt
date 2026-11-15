package io.github.aj4abinjacob.chikku.home

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.view.View
import androidx.core.content.ContextCompat
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.badges.BadgeBinder
import io.github.aj4abinjacob.chikku.badges.BadgeOverlay
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.settings.DockLabelFont
import kotlinx.coroutines.Job
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Icon above a one- or two-line label, or beside it in drawer list mode. Draws itself so measuring is deterministic and
 * the icon shrinks to fit when the font scale makes the label taller. The label is sized in sp and
 * ellipsized; on the wallpaper it gets a text shadow for contrast.
 */
// Created in code only (style is required), so no XML constructor.
@SuppressLint("ViewConstructor")
class AppIconView(context: Context, val style: Style) : View(context), MenuAnchorIcon {

    enum class Style { WORKSPACE, DRAWER }

    private val res = context.resources
    private val gridIconSize = res.getDimensionPixelSize(R.dimen.home_icon_size)
    // List rows are read by name, so the icon is smaller and the label larger than in a grid cell.
    private val listIconSize = res.getDimensionPixelSize(R.dimen.drawer_list_icon_size)
    private val maxIconSize get() = ((if (listMode) listIconSize else gridIconSize) * iconScale).roundToInt()
    private val minIconSize = res.getDimensionPixelSize(R.dimen.home_icon_min_size)
    private val defaultGap = res.getDimensionPixelSize(R.dimen.home_icon_label_gap)
    private val gap get() = labelGapDp?.let { (it * res.displayMetrics.density).roundToInt() } ?: defaultGap
    var labelGapDp: Int? = null
        set(value) { if (field != value) { field = value?.coerceIn(0, 24); relayoutContent() } }
    var verticalOffsetDp: Int = 0
        set(value) { if (field != value) { field = value.coerceIn(0, 24); relayoutContent() } }
    private val badgeSize = res.getDimensionPixelSize(R.dimen.home_work_badge_size)

    private val textPaint = labelPaint(context, style)
    private val gridTextSize = textPaint.textSize
    private val listTextSize = res.getDimension(R.dimen.drawer_list_label_text_size)
    private val baseTextSize get() = if (listMode) listTextSize else gridTextSize

    /** Icon size multiplier (home/dock settings); 1 = default. */
    var iconScale: Float = 1f
        set(value) {
            val coerced = if (value.isNaN() || value <= 0f) 1f else value
            if (field == coerced) return
            field = coerced
            relayoutContent()
        }

    /** Label text size multiplier on top of the sp size, so the system font scale still applies. */
    var labelScale: Float = 1f
        set(value) {
            val scale = if (value.isFinite() && value > 0f) value else 1f
            if (field == scale) return
            field = scale
            textPaint.textSize = baseTextSize * scale
            relayoutContent()
        }

    /** Hides the drawn label; the content description keeps the name for accessibility. */
    var showLabel: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            relayoutContent()
        }

    var labelFont: DockLabelFont = DockLabelFont.NORMAL
        set(value) {
            if (field == value) return
            field = value
            textPaint.typeface = typeface(value)
            relayoutContent()
        }

    var labelShadow: Boolean = style == Style.WORKSPACE
        set(value) {
            if (field == value) return
            field = value
            if (value) {
                val radius = res.getDimension(R.dimen.home_label_shadow_radius)
                textPaint.setShadowLayer(radius, 0f, radius / 2f, ContextCompat.getColor(context, R.color.home_label_shadow))
            } else textPaint.clearShadowLayer()
            invalidate()
        }

    var labelSingleLine: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            relayoutContent()
        }

    /** Drawer list rows place the icon at the leading edge instead of above the label. */
    var listMode: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            if (style == Style.DRAWER) {
                val side = if (value) res.getDimensionPixelSize(R.dimen.home_icon_padding) else 0
                setPaddingRelative(side, paddingTop, side, paddingBottom)
            }
            textPaint.textSize = baseTextSize * labelScale
            relayoutContent()
        }

    private var multilineLabel: StaticLayout? = null
    private var ellipsized: CharSequence = ""
    private var iconSize = maxIconSize
    private var iconLeft = 0
    private var iconTop = 0
    private var baseline = 0f
    private var labelLeft = 0
    private var labelRight = 0

    /** The key whose icon is shown or loading; used to drop stale icon loads after rebinding. */
    var boundKey: AppKey? = null
        internal set

    /** In-flight icon load, cancelled on rebind/detach by [IconBinder]. */
    internal var iconJob: Job? = null

    var icon: Drawable? = null
        set(value) {
            field = value
            updateIconBounds()
            invalidate()
        }

    var label: String = ""
        set(value) {
            if (field == value) return
            field = value
            updateEllipsized()
            invalidate()
        }

    var showWorkBadge: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private val workBadge: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_work_badge)

    /** Notification badge over the icon's top-end corner; set by [BadgeBinder]. */
    var badge: BadgeOverlay? = null
        set(value) {
            if (field === value) return
            field = value
            invalidate()
        }

    /** Where the icon is drawn, in this view's coordinates; e.g. to put a selection mark on its corner. */
    override fun iconBounds(out: Rect) {
        out.set(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize)
    }

    /** Long-press footprint, drawn under the icon and label (see showItemFootprint). */
    private var footprint: Drawable? = null

    override fun setFootprint(footprint: Drawable?) {
        this.footprint = footprint
        invalidate()
    }

    override fun contentBottom(): Int {
        val iconBottom = iconTop + iconSize
        if (listMode) return maxOf(iconBottom, height - paddingBottom) // label sits beside the icon
        if (!showLabel || ellipsized.isEmpty()) return iconBottom
        val labelBottom = multilineLabel?.let { baseline + textPaint.fontMetrics.ascent + it.height }
            ?: (baseline + textPaint.fontMetrics.descent)
        return maxOf(iconBottom, kotlin.math.ceil(labelBottom).toInt()).coerceAtMost(height)
    }

    /** Overrides the style's label color, e.g. for a custom drawer background or a night-mode surface. */
    fun setLabelColor(color: Int?) {
        val resolved = color ?: ContextCompat.getColor(context, when (style) {
            Style.WORKSPACE -> R.color.chikku_on_wallpaper
            Style.DRAWER -> R.color.chikku_on_drawer
        })
        if (textPaint.color == resolved) return
        textPaint.color = resolved
        invalidate()
    }

    init {
        isFocusable = true
        isClickable = true
        isLongClickable = true
        val padding = res.getDimensionPixelSize(R.dimen.home_icon_padding)
        setPaddingRelative(if (style == Style.DRAWER) 0 else padding, padding,
            if (style == Style.DRAWER) 0 else padding, padding)
        minimumHeight = res.getDimensionPixelSize(R.dimen.home_min_touch_target)
        minimumWidth = res.getDimensionPixelSize(R.dimen.home_min_touch_target)
        // No ripple background: a bounded ripple paints the whole cell as a grey rectangle on a
        // short press. Touch feedback is a dim of the icon itself; keyboard focus gets a rounded
        // highlight behind the icon (see onDraw).
    }

    private val focusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.chikku_on_wallpaper)
        alpha = FOCUS_ALPHA
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        invalidate()
    }

    // Announced as a button so TalkBack users know a double-tap opens the app.
    override fun getAccessibilityClassName(): CharSequence = android.widget.Button::class.java.name

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = paddingLeft + paddingRight + max(maxIconSize, minimumWidth) +
            if (listMode && showLabel) gap * 2 + minimumWidth else 0
        val desiredHeight = cachedDesiredHeight() +
            if (showLabel && !listMode) gap - defaultGap else 0
        setMeasuredDimension(
            resolveSize(desiredWidth, widthMeasureSpec),
            resolveSize(max(desiredHeight, minimumHeight), heightMeasureSpec),
        )
    }

    // desiredHeight() builds a paint and font metrics, and the grid measures every cell each pass, so
    // keep the last result and recompute only when an input (or the font scale / density) changes.
    private var heightCacheValid = false
    private var cachedHeight = 0
    private var keyIconScale = 0f
    private var keyLabelScale = 0f
    private var keyShowLabel = false
    private var keySingleLine = false
    private var keyFont: DockLabelFont? = null
    private var keyListMode = false
    private var keyFontScale = 0f
    private var keyDensity = 0f

    private fun cachedDesiredHeight(): Int {
        val fontScale = res.configuration.fontScale
        val density = res.displayMetrics.density
        if (!heightCacheValid || keyIconScale != iconScale || keyLabelScale != labelScale ||
            keyShowLabel != showLabel || keySingleLine != labelSingleLine || keyFont != labelFont ||
            keyListMode != listMode || keyFontScale != fontScale || keyDensity != density
        ) {
            cachedHeight = desiredHeight(context, style, iconScale, labelScale, showLabel, labelSingleLine, labelFont, listMode)
            keyIconScale = iconScale; keyLabelScale = labelScale; keyShowLabel = showLabel
            keySingleLine = labelSingleLine; keyFont = labelFont; keyListMode = listMode
            keyFontScale = fontScale; keyDensity = density
            heightCacheValid = true
        }
        return cachedHeight
    }

    private val drawFontMetrics = Paint.FontMetrics()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateGeometry(w, h)
    }

    override fun onRtlPropertiesChanged(layoutDirection: Int) {
        super.onRtlPropertiesChanged(layoutDirection)
        // Relative padding is still being resolved in this callback. Reading it here re-enters
        // direction resolution; recompute after layout, even when the size stayed the same.
        requestLayout()
        invalidate()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        updateGeometry(width, height)
    }

    private fun relayoutContent() {
        requestLayout()
        if (width > 0 && height > 0) updateGeometry(width, height)
        invalidate()
    }

    private fun updateGeometry(w: Int, h: Int) {
        val textHeight = if (showLabel) textPaint.fontMetrics.let { (it.descent - it.ascent) * if (labelSingleLine) 1 else 2 } else 0f
        val labelGap = if (!showLabel) 0 else if (listMode) gap * 2 else gap
        if (listMode) {
            val contentHeight = (h - paddingTop - paddingBottom).coerceAtLeast(0)
            iconSize = min(maxIconSize, min(contentHeight, w - paddingLeft - paddingRight)).coerceAtLeast(minIconSize)
            val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
            iconLeft = if (rtl) w - paddingRight - iconSize else paddingLeft
            iconTop = paddingTop + ((contentHeight - iconSize) / 2f).roundToInt()
            labelLeft = if (rtl) paddingLeft else iconLeft + iconSize + labelGap
            labelRight = if (rtl) iconLeft - labelGap else w - paddingRight
            baseline = paddingTop + ((contentHeight - textHeight) / 2f).coerceAtLeast(0f) - textPaint.fontMetrics.ascent
            textPaint.textAlign = if (!labelSingleLine || !rtl) Paint.Align.LEFT else Paint.Align.RIGHT
            updateIconBounds()
            updateEllipsized()
            return
        }
        textPaint.textAlign = if (labelSingleLine) Paint.Align.CENTER else Paint.Align.LEFT
        val availableForIcon = (h - paddingTop - paddingBottom - labelGap - textHeight).toInt()
        iconSize = min(maxIconSize, min(availableForIcon, w - paddingLeft - paddingRight)).coerceAtLeast(minIconSize)
        val blockHeight = iconSize + labelGap + textHeight
        val freeHalf = ((h - paddingTop - paddingBottom - blockHeight) / 2f).coerceAtLeast(0f)
        val offset = (verticalOffsetDp * res.displayMetrics.density).coerceAtMost(freeHalf)
        val blockTop = paddingTop + freeHalf + offset
        iconLeft = (w - iconSize) / 2
        iconTop = blockTop.roundToInt()
        baseline = blockTop + iconSize + labelGap - textPaint.fontMetrics.ascent
        labelLeft = paddingLeft
        labelRight = w - paddingRight
        updateIconBounds()
        updateEllipsized()
    }

    private fun updateIconBounds() {
        icon?.setBounds(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize)
    }

    private fun updateEllipsized() {
        val available = (labelRight - labelLeft).toFloat()
        multilineLabel = if (!labelSingleLine && available > 0f) {
            StaticLayout.Builder.obtain(label, 0, label.length, textPaint, available.toInt())
                .setAlignment(if (!listMode) Layout.Alignment.ALIGN_CENTER
                    else if (layoutDirection == LAYOUT_DIRECTION_RTL) Layout.Alignment.ALIGN_OPPOSITE
                    else Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false)
                .setMaxLines(2)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build()
        } else null
        ellipsized = if (available <= 0f) "" else TextUtils.ellipsize(label, textPaint, available, TextUtils.TruncateAt.END)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        footprint?.draw(canvas)
        if (isFocused && isInTouchMode.not()) {
            val r = iconSize * FOCUS_RADIUS_FRACTION
            val inset = iconSize * FOCUS_INSET_FRACTION
            canvas.drawRoundRect(iconLeft - inset, iconTop - inset, iconLeft + iconSize + inset,
                iconTop + iconSize + inset, r, r, focusPaint)
        }
        icon?.let { drawable ->
            if (isPressed) {
                val restore = canvas.saveLayerAlpha(iconLeft.toFloat(), iconTop.toFloat(),
                    (iconLeft + iconSize).toFloat(), (iconTop + iconSize).toFloat(), PRESSED_ICON_ALPHA)
                drawable.draw(canvas)
                canvas.restoreToCount(restore)
            } else {
                drawable.draw(canvas)
            }
        }
        if (showWorkBadge) {
            workBadge?.let { badge ->
                val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
                val right = iconLeft + iconSize
                val left = if (rtl) iconLeft else right - badgeSize
                val bottom = iconTop + iconSize
                badge.setBounds(left, bottom - badgeSize, left + badgeSize, bottom)
                badge.draw(canvas)
            }
        }
        badge?.draw(canvas, iconLeft, iconTop, iconSize, layoutDirection == LAYOUT_DIRECTION_RTL)
        if (showLabel && ellipsized.isNotEmpty()) {
            val multiline = multilineLabel
            if (multiline != null) {
                canvas.save()
                textPaint.getFontMetrics(drawFontMetrics)
                canvas.translate(labelLeft.toFloat(), baseline + drawFontMetrics.ascent)
                multiline.draw(canvas)
                canvas.restore()
            } else {
                val x = if (!listMode) width / 2f else
                    if (layoutDirection == LAYOUT_DIRECTION_RTL) labelRight.toFloat() else labelLeft.toFloat()
                canvas.drawText(ellipsized, 0, ellipsized.length, x, baseline, textPaint)
            }
        }
    }

    companion object {
        private const val PRESSED_ICON_ALPHA = 179 // ~70%: a quiet press cue on the icon only
        private const val FOCUS_ALPHA = 46
        private const val FOCUS_RADIUS_FRACTION = 0.28f
        private const val FOCUS_INSET_FRACTION = 0.12f

        private fun typeface(font: DockLabelFont): Typeface = font.labelTypeface()

        private fun labelPaint(context: Context, style: Style): TextPaint =
            TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = context.resources.getDimension(R.dimen.home_icon_label_text_size) // sp: follows font scale
                textAlign = Paint.Align.CENTER
                when (style) {
                    Style.WORKSPACE -> {
                        color = ContextCompat.getColor(context, R.color.chikku_on_wallpaper)
                        val radius = context.resources.getDimension(R.dimen.home_label_shadow_radius)
                        setShadowLayer(radius, 0f, radius / 2f, ContextCompat.getColor(context, R.color.home_label_shadow))
                    }
                    Style.DRAWER -> color = ContextCompat.getColor(context, R.color.chikku_on_drawer)
                }
            }

        /**
         * Preferred height for one icon cell, including the label at the current font scale.
         * [iconScale] and [labelScale] multiply the default icon and label sizes.
         */
        fun desiredHeight(
            context: Context,
            style: Style,
            iconScale: Float = 1f,
            labelScale: Float = 1f,
            showLabel: Boolean = true,
            singleLine: Boolean = true,
            font: DockLabelFont = DockLabelFont.NORMAL,
            listMode: Boolean = false,
        ): Int {
            val res = context.resources
            val padding = res.getDimensionPixelSize(if (listMode) R.dimen.drawer_list_row_padding else R.dimen.home_icon_padding)
            val icon = res.getDimensionPixelSize(if (listMode) R.dimen.drawer_list_icon_size else R.dimen.home_icon_size) * iconScale
            val label = if (showLabel) {
                val paint = labelPaint(context, style).apply {
                    if (listMode) textSize = res.getDimension(R.dimen.drawer_list_label_text_size)
                    textSize *= labelScale
                    typeface = typeface(font)
                }
                val fm = paint.fontMetrics
                res.getDimensionPixelSize(R.dimen.home_icon_label_gap) + (fm.descent - fm.ascent) * if (singleLine) 1 else 2
            } else {
                0f
            }
            return (padding * 2 + if (listMode) max(icon, label) else icon + label).roundToInt()
        }
    }
}
