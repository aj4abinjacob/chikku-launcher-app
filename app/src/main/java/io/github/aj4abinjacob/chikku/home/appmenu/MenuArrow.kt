package io.github.aj4abinjacob.chikku.home.appmenu

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout

/** The pointer's base width and its height beyond the card. */
internal const val ARROW_WIDTH_DP = 20f
internal const val ARROW_HEIGHT_DP = 9f

/**
 * How far the arrow's base reaches into its card: a sliver of overlap so no outline crosses the
 * join. The base stays on the card's flat edge (see [arrowCornerInset]), so this band never
 * shows beside a rounded corner.
 */
private const val ARROW_OVERLAP_DP = 3f

/** Space between stacked menu cards. */
private const val CARD_GAP_DP = 8f

/**
 * Closest the arrow's base may come to the side of a card with [radius] corners (px): where the
 * card's bottom (or top) edge is straight. Closer in, the curve rises under the base and the
 * overlap band would show as a step beside the arrow. 0 for square corners.
 */
internal fun arrowCornerInset(radius: Float): Float = radius.coerceAtLeast(0f)

/**
 * Left edge for an arrow of [arrowWidth] centred on [target], kept [inset] from either side of a
 * [width]-wide card; centred when the card is too narrow for that.
 */
internal fun arrowLeft(target: Float, width: Float, inset: Float, arrowWidth: Float): Float {
    val max = width - inset - arrowWidth
    return if (max < inset) (width - arrowWidth) / 2f else (target - arrowWidth / 2f).coerceIn(inset, max)
}

/**
 * The small pointer on a long-press menu's card nearest the item, aiming at the icon: the card's
 * [fill], and its hairline [edge] (if it has one) along the two slanted sides. As wide as the
 * card, it draws the triangle at [targetX] (null: centred), kept clear of the card's corners
 * ([cornerRadius]). Its base reaches [overlap] into the card and it sits a step above it, so no
 * outline crosses the join.
 */
internal class MenuArrowView(context: Context, fill: Int, private val edge: Int?, private val edgeWidth: Float) : View(context) {
    private val density = resources.displayMetrics.density
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill }
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = edgeWidth
        strokeJoin = Paint.Join.ROUND
        color = edge ?: 0
    }
    private val shape = Path()
    private val sides = Path()

    /** How far the arrow reaches into its card. */
    val overlap: Int = (ARROW_OVERLAP_DP * density).toInt()

    /** The view's height: the visible arrow plus [overlap]. */
    val viewHeight: Int = (ARROW_HEIGHT_DP * density).toInt() + overlap

    /** Down when the menu is above the item, up when it is below. */
    var pointsDown = true
        set(value) {
            field = value
            invalidate()
        }

    /** Where the tip aims, in this view's coordinates; null centres it. */
    var targetX: Float? = null
        set(value) {
            field = value
            invalidate()
        }

    /** The nearest card's corner radius, in px. */
    var cornerRadius = 0f
        set(value) {
            field = value
            invalidate()
        }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        outlineProvider = null // no shadow of its own; the card's carries it
    }

    override fun onDraw(canvas: Canvas) {
        val w = ARROW_WIDTH_DP * density
        val left = arrowLeft(targetX ?: (width / 2f), width.toFloat(), arrowCornerInset(cornerRadius), w)
        val right = left + w
        val mid = left + w / 2f
        // Card side first: the overlap band, then the base on the card's edge, then the tip.
        val inner = if (pointsDown) 0f else height.toFloat()
        val base = if (pointsDown) overlap.toFloat() else height - overlap.toFloat()
        val tip = if (pointsDown) height.toFloat() else 0f
        shape.reset()
        shape.moveTo(left, inner)
        shape.lineTo(right, inner)
        shape.lineTo(right, base)
        shape.lineTo(mid, tip)
        shape.lineTo(left, base)
        shape.close()
        canvas.drawPath(shape, fillPaint)
        if (edge == null) return
        // Only the slanted sides: the base lies on the card, whose outline stops under the arrow.
        val inset = if (pointsDown) -edgeWidth / 2f else edgeWidth / 2f
        sides.reset()
        sides.moveTo(left, base)
        sides.lineTo(mid, tip + inset)
        sides.lineTo(right, base)
        canvas.drawPath(sides, edgePaint)
    }
}

/**
 * Stacks a long-press menu's [cards] in [content] (a vertical column with the shadow padding),
 * [cards] listed nearest-the-item first: above the item ([above]) they run bottom-up with
 * [arrow] under the last, pointing down; below it they run top-down under the arrow. The arrow
 * aims at [arrowCenterX] (content coordinates; null centres it). The height does not depend on
 * [above], so this may run after the content was measured.
 */
internal fun arrangeMenu(content: LinearLayout, cards: List<View>, arrow: MenuArrowView, above: Boolean, arrowCenterX: Int?) {
    val gap = (CARD_GAP_DP * content.resources.displayMetrics.density).toInt()
    content.removeAllViews()
    val ordered = if (above) cards.reversed() else cards
    if (!above) content.addView(arrow)
    ordered.forEachIndexed { i, card ->
        content.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            if (i > 0) topMargin = gap
        })
    }
    if (above) content.addView(arrow)
    // Full width like the cards: a narrower child would make a wrapping column ignore them.
    arrow.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, arrow.viewHeight).apply {
        if (above) topMargin = -arrow.overlap else bottomMargin = -arrow.overlap
    }
    arrow.pointsDown = above
    arrow.targetX = arrowCenterX?.let { (it - content.paddingLeft).toFloat() }
    val near = cards.firstOrNull()
    arrow.cornerRadius = (near?.background as? GradientDrawable)?.cornerRadius ?: 0f
    near?.let { arrow.elevation = it.elevation + content.resources.displayMetrics.density }
}

/** A [MenuArrowView] for cards drawn with [fill] and an optional hairline [edge] of [edgeWidth] px. */
internal fun menuArrow(context: Context, fill: Int, edge: Int?, edgeWidth: Int): MenuArrowView =
    MenuArrowView(context, fill, edge, edgeWidth.toFloat())
