package io.github.aj4abinjacob.chikku.settings.previews

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import android.widget.LinearLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.drawer.DrawerModel
import io.github.aj4abinjacob.chikku.core.model.settings.DockLabelFont
import io.github.aj4abinjacob.chikku.core.model.settings.IconShape
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.home.AppIconView
import io.github.aj4abinjacob.chikku.home.IconLabelStyle
import io.github.aj4abinjacob.chikku.platform.icons.IconShaper
import kotlin.math.roundToInt

/** Icon and label settings shown by [IconSamplePreview]; mirrors what the real surface applies. */
data class IconSampleLook(
    val iconScale: Float,
    val showLabels: Boolean,
    val labelScale: Float,
    val labelFont: DockLabelFont,
    val labelShadow: Boolean,
    val labelSingleLine: Boolean,
    val labelColor: Int?,
    val labelGapDp: Int? = null,
    val verticalOffsetDp: Int = 0,
    /** Drawer List style: a couple of rows with the icon at the leading edge instead of a grid row. */
    val listMode: Boolean = false,
    /** The launcher's icon shape and legacy reshaping, so the synthetic samples take the same mask. */
    val iconShape: IconShape = IconShape.SYSTEM,
    val reshapeLegacyIcons: Boolean = false,
) {
    /** The label and icon options the real surface applies to every cell. */
    val style: IconLabelStyle get() = IconLabelStyle(
        iconScale = iconScale, showLabels = showLabels, labelScale = labelScale, labelFont = labelFont,
        labelShadow = labelShadow, labelSingleLine = labelSingleLine, labelColor = labelColor,
        listMode = listMode, labelGapDp = labelGapDp, verticalOffsetDp = verticalOffsetDp,
    )
}

/**
 * A row of sample icons drawn by the real [AppIconView], so size, font, colour and spacing match the
 * launcher while a slider is still being dragged. [background] null draws a wallpaper-like gradient
 * with the home-screen style; otherwise cells use the drawer style on that colour.
 *
 * The height is fixed for the largest sizes, so the controls below never move during a drag.
 * Illustrative only: Chikku glyphs, never the user's app inventory or private-profile data.
 */
@Composable
fun IconSamplePreview(look: IconSampleLook, background: Int?, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.settings_icon_preview)
    val style = if (background == null) AppIconView.Style.WORKSPACE else AppIconView.Style.DRAWER
    AndroidView(
        modifier = modifier.semantics { contentDescription = description },
        factory = { context ->
            SampleRow(context).apply {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                orientation = LinearLayout.HORIZONTAL
                minimumHeight = maxCellHeight(context, style)
                PREVIEW_SAMPLES.forEachIndexed { index, (_, label) ->
                    addView(AppIconView(context, style).apply {
                        icon = previewSampleIcon(context, index)
                        this.label = context.getString(label)
                        isClickable = false
                        isLongClickable = false
                        isFocusable = false
                    }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
                }
            }
        },
        update = { row ->
            row.background = background?.let { GradientDrawable().apply { setColor(it) } } ?: wallpaperBackground()
            row.orientation = if (look.listMode) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            row.showGroupLetters(look.listMode, look.labelColor ?: ContextCompat.getColor(row.context, R.color.chikku_on_drawer))
            for (i in 0 until row.childCount) (row.getChildAt(i) as AppIconView).apply {
                layoutParams = if (look.listMode) {
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
                } else {
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                }
                visibility = if (look.listMode && i >= LIST_ROWS) View.GONE else View.VISIBLE
                icon = previewSampleIcon(context, i, look.iconShape, look.reshapeLegacyIcons)
                look.style.applyTo(this)
            }
        },
    )
}

/**
 * The sample row; in List style it indents the rows and draws each one's group letter in the
 * leading column, as DrawerListGroups does in the drawer (every sample starts a new letter).
 */
private class SampleRow(context: Context) : LinearLayout(context) {
    private val side = (16 * resources.displayMetrics.density).roundToInt()
    private val gutter = resources.getDimensionPixelSize(R.dimen.drawer_list_section_gutter)
    private val letterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = resources.getDimension(R.dimen.drawer_list_section_text_size)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }
    private var letters = false

    init { setPaddingRelative(side, 0, side, 0) }

    fun showGroupLetters(show: Boolean, color: Int) {
        letters = show
        letterPaint.color = ColorUtils.setAlphaComponent(color, GROUP_LETTER_ALPHA)
        setPaddingRelative(side + if (show) gutter else 0, 0, side, 0)
        invalidate()
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (!letters) return
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        val centreOffset = (letterPaint.descent() + letterPaint.ascent()) / 2f
        for (i in 0 until childCount) {
            val cell = getChildAt(i) as? AppIconView ?: continue
            if (cell.visibility != View.VISIBLE) continue
            val x = if (rtl) cell.right + gutter / 2f else cell.left - gutter / 2f
            canvas.drawText(DrawerModel.sectionLetter(cell.label), x, (cell.top + cell.bottom) / 2f - centreOffset, letterPaint)
        }
    }
}

private const val GROUP_LETTER_ALPHA = 0xB3 // as DrawerListGroups

/** Same gradient as the dock preview, standing in for a wallpaper. */
internal fun wallpaperBackground(): Drawable = GradientDrawable(GradientDrawable.Orientation.TL_BR, PREVIEW_WALLPAPER)

/** Wallpaper stand-in colours shared by every settings preview. */
internal val PREVIEW_WALLPAPER = intArrayOf(0xFF224A56.toInt(), 0xFF755982.toInt(), 0xFF23364F.toInt())

/**
 * Tallest cell the sliders can produce: largest icon, two lines of the largest label and the widest
 * gap, plus room to show the vertical offset (which, as on the home screen, is capped by free space).
 */
private fun maxCellHeight(context: Context, style: AppIconView.Style): Int {
    val density = context.resources.displayMetrics.density
    val content = AppIconView.desiredHeight(context, style, LauncherSettings.MAX_SCALE, LauncherSettings.MAX_SCALE,
        showLabel = true, singleLine = false, font = DockLabelFont.MEDIUM)
    val defaultGap = context.resources.getDimensionPixelSize(R.dimen.home_icon_label_gap)
    return content - defaultGap + ((MAX_GAP_DP + MAX_OFFSET_DP) * density).roundToInt()
}

/**
 * Sample app icon [index] (wrapping): a coloured disc with a Chikku glyph, never a real app's icon.
 * With a [shape] other than the system's, the sample is a synthetic adaptive icon run through
 * [IconShaper] exactly as a real app's icon is, so the preview shows the chosen mask.
 */
internal fun previewSampleIcon(
    context: Context,
    index: Int,
    shape: IconShape = IconShape.SYSTEM,
    reshapeLegacy: Boolean = false,
): Drawable {
    val glyph = PREVIEW_SAMPLES[index % PREVIEW_SAMPLES.size].first
    if (shape != IconShape.SYSTEM) {
        val plate = ColorDrawable(PREVIEW_SAMPLE_COLORS[index % PREVIEW_SAMPLE_COLORS.size])
        val symbol = ContextCompat.getDrawable(context, glyph)!!.mutate().apply { setTint(0xFFFFFFFF.toInt()) }
        return IconShaper.apply(AdaptiveIconDrawable(plate, InsetDrawable(symbol, 0.3f)), shape, reshapeLegacy)
    }
    val disc = GradientDrawable().apply {
        this.shape = GradientDrawable.OVAL
        setColor(PREVIEW_SAMPLE_COLORS[index % PREVIEW_SAMPLE_COLORS.size])
    }
    val symbol = ContextCompat.getDrawable(context, glyph)!!.mutate().apply { setTint(0xFFFFFFFF.toInt()) }
    return LayerDrawable(arrayOf(disc, InsetDrawable(symbol, 0.24f)))
}

private const val LIST_ROWS = 2
private const val MAX_GAP_DP = 24
private const val MAX_OFFSET_DP = 24

/** Chikku glyphs and generic labels used as sample apps by every settings preview. */
internal val PREVIEW_SAMPLES = listOf(
    R.drawable.ic_settings_home to R.string.settings_section_home,
    R.drawable.ic_settings_drawer to R.string.settings_section_drawer,
    R.drawable.ic_settings_folder to R.string.settings_section_folders,
    R.drawable.ic_settings_palette to R.string.settings_section_look_feel,
)

internal val PREVIEW_SAMPLE_COLORS = intArrayOf(0xFF3F6FD8.toInt(), 0xFF2E9D6A.toInt(), 0xFFD0812B.toInt(), 0xFFB04A8C.toInt())
