package io.github.aj4abinjacob.chikku.home

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.view.animation.DecelerateInterpolator
import androidx.core.view.drawToBitmap
import io.github.aj4abinjacob.chikku.theme.Motion
import android.util.AttributeSet
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.ContainerType
import io.github.aj4abinjacob.chikku.core.model.GridSpec
import kotlin.math.abs

/**
 * Lays out children on a fixed [GridSpec]. Measurement is deterministic: every cell has the same
 * size, children are measured exactly to their span, and model x mirrors in RTL. When the height is
 * not fixed by the parent (the dock), each row is [minCellHeight] tall.
 *
 * The visible grid is a window of model columns starting at [columnOffset] (used for dock pages).
 * Children that do not fit entirely inside the window, e.g. after the user shrank the grid, are
 * hidden but kept: their placement is untouched and they reappear when the grid grows again.
 */
class CellLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : ViewGroup(context, attrs) {

    var grid: GridSpec = GridSpec(4, 5)
        set(value) {
            if (field == value) return
            field = value
            refreshViewport()
            requestLayout()
        }

    /** First model column shown in visual column 0 (before RTL mirroring). */
    var columnOffset: Int = 0
        set(value) {
            if (field == value) return
            field = value
            refreshViewport()
            requestLayout()
            invalidate()
        }

    var container: ContainerType = ContainerType.DESKTOP
    var page: Int = 0

    /**
     * False while this page is outside the workspace's bind window: its children are then
     * [Placeholder]s that keep each item's id, model tag and cell but hold no icon, folder preview
     * or widget view. Set by `WorkspaceController`.
     */
    var contentBound: Boolean = true
        internal set

    /** Row height used when the parent does not dictate one. */
    var minCellHeight: Int = 0
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
        }

    /**
     * When set, a horizontal swipe over this layout (including over its children) is consumed and
     * reported once with `forward = true` for the next page in reading order (mirrors in RTL).
     */
    var onHorizontalSwipe: ((forward: Boolean) -> Unit)? = null

    /** Children currently hidden because they lie outside the visible grid window. */
    var hiddenChildCount: Int = 0
        private set

    /** How a drop highlight reads: where the item lands, or an item that will take it into a folder. */
    enum class DropHint { CELL, FOLDER }

    /** Animation speed for the layout's own motion (set by the workspace controller). */
    var motion: Motion = Motion.DEFAULT

    private var highlight: Cell? = null // last shown cell; kept while the highlight fades out
    private var highlightWanted = false
    private var highlightHint = DropHint.CELL
    private var highlightEmphasisId: String? = null
    private var highlightSpanX = 1
    private var highlightSpanY = 1
    private var highlightAlpha = 0f
    private var highlightAnimator: ValueAnimator? = null
    private var emphasized: View? = null
    private val highlightBaseAlpha = Color.alpha(ContextCompat.getColor(context, R.color.home_drop_highlight))
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.home_drop_highlight)
    }
    private val highlightRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.home_drop_highlight)
        style = Paint.Style.STROKE
        strokeWidth = 2f * context.resources.displayMetrics.density
    }
    private val highlightRect = RectF()
    private val highlightRadius = context.resources.getDimension(R.dimen.home_drop_highlight_radius)

    // The drawn highlight glides between cells and fades in and out instead of jumping.
    private val highlightFrom = RectF()
    private val highlightTo = RectF()
    private var highlightProgress = 1f
    private var highlightAlphaFrom = 0f
    private var highlightAlphaTo = 0f

    /** How long the drop highlight takes to follow the finger to a new cell; 0 jumps. */
    var highlightMoveMs: Long = 0L

    // Footprint: the cells an item occupies, shown while its long-press menu is open.
    private var footprintChild: View? = null
    private var footprintAlpha = 0f
    private var footprintAnimator: ValueAnimator? = null
    private val footprintRect = RectF()
    private var footprintShape: FootprintShape? = null
    private val footprintEdge = RectF()
    private val footprintFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val footprintStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.resources.getDimension(R.dimen.home_footprint_stroke)
    }
    private var footprintColors = intArrayOf(0, 0) // fill, stroke at full strength

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var swipeDownX = 0f
    private var swipeDownY = 0f
    private var swiping = false
    private var swipeDecided = false
    /** Screen position of the latest touch-down, e.g. to place the long-press-home menu. */
    var lastDownRawX: Float = 0f
        private set
    var lastDownRawY: Float = 0f
        private set

    init {
        setWillNotDraw(false)
        clipToPadding = false
        // Draw (and dispatch touches) in LayoutParams.zIndex order so overlapping widgets stack.
        isChildrenDrawingOrderEnabled = true
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            lastDownRawX = ev.rawX
            lastDownRawY = ev.rawY
        }
        return super.dispatchTouchEvent(ev)
    }

    private var drawingOrder = IntArray(0)

    override fun getChildDrawingOrder(childCount: Int, drawingPosition: Int): Int {
        if (drawingOrder.size != childCount) {
            drawingOrder = (0 until childCount)
                .sortedBy { (getChildAt(it).layoutParams as? LayoutParams)?.zIndex ?: 0 } // stable: index breaks ties
                .toIntArray()
        }
        return drawingOrder.getOrElse(drawingPosition) { drawingPosition }
    }

    override fun onViewAdded(child: View?) {
        super.onViewAdded(child)
        drawingOrder = IntArray(0)
    }

    override fun onViewRemoved(child: View?) {
        super.onViewRemoved(child)
        drawingOrder = IntArray(0)
    }

    val isRtl: Boolean get() = layoutDirection == LAYOUT_DIRECTION_RTL

    private val contentWidth get() = width - paddingLeft - paddingRight
    private val contentHeight get() = height - paddingTop - paddingBottom

    /** Model cell under the point, including [columnOffset]. */
    fun cellAt(px: Float, py: Float, spanX: Int = 1, spanY: Int = 1): Cell {
        val local = CellMath.cellAt(px, py, paddingLeft, paddingTop, contentWidth, contentHeight, grid, isRtl, spanX, spanY)
        return Cell(local.x + columnOffset, local.y)
    }

    /**
     * Pixel bounds of the model area at [cell] spanning [spanX] by [spanY], in this layout's own
     * coordinates; false when it is not inside the visible window or the layout has no size yet.
     */
    fun cellBounds(cell: Cell, spanX: Int, spanY: Int, out: Rect): Boolean {
        if (width <= 0 || height <= 0 || !CellMath.isInWindow(cell.x, cell.y, spanX, spanY, columnOffset, grid)) return false
        val cellW = contentWidth / grid.columns
        val cellH = contentHeight / grid.rows
        val col = CellMath.visualColumn(cell.x - columnOffset, spanX, grid, isRtl)
        val left = paddingLeft + col * cellW
        val top = paddingTop + cell.y * cellH
        out.set(left, top, left + cellW * spanX, top + cellH * spanY)
        return true
    }

    /** The alpha [view] rests at: its own, or the one it is fading in to. */
    fun restingAlpha(view: View): Float? = fadingIn[view] ?: view.alpha

    /**
     * Drops [view]'s pending fade-in and returns the alpha it was fading to. Called by whoever cancels the
     * view's animator and takes over its alpha, because a cancelled fade-in never runs its end action and
     * the recorded alpha would later be written back by [playFlip].
     */
    internal fun forgetFadeIn(view: View): Float? = fadingIn.remove(view)

    /** The item whose placement starts at [cell], if any (the one that was just bound there). */
    fun viewAt(cell: Cell): View? {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val lp = child.layoutParams as? LayoutParams ?: continue
            if (lp.x == cell.x && lp.y == cell.y) return child
        }
        return null
    }

    /** True if [lp] lies entirely inside the visible window of the grid. */
    fun isInViewport(lp: LayoutParams): Boolean =
        CellMath.isInWindow(lp.x, lp.y, lp.spanX, lp.spanY, columnOffset, grid)

    /** True if any child other than the one tagged [excludeId] overlaps the given model area. */
    fun isOccupied(cell: Cell, spanX: Int, spanY: Int, excludeId: String?): Boolean {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (excludeId != null && child.getTag(R.id.home_item_id) == excludeId) continue
            val lp = child.layoutParams as LayoutParams
            val overlapX = cell.x < lp.x + lp.spanX && lp.x < cell.x + spanX
            val overlapY = cell.y < lp.y + lp.spanY && lp.y < cell.y + spanY
            if (overlapX && overlapY) return true
        }
        return false
    }

    /**
     * [cell] is in model coordinates, as returned by [cellAt]; null fades the highlight out.
     * [hint] FOLDER rings the cell and scales up the item [emphasisId] to show it will take the drop in.
     * Returns true when a shown highlight moved to a different cell (not when it first appears or goes away).
     */
    fun setDropHighlight(
        cell: Cell?,
        spanX: Int = 1,
        spanY: Int = 1,
        hint: DropHint = DropHint.CELL,
        emphasisId: String? = null,
    ): Boolean {
        val wanted = cell != null
        if (highlightWanted == wanted && (!wanted || (highlight == cell && highlightSpanX == spanX &&
                highlightSpanY == spanY && highlightHint == hint && highlightEmphasisId == emphasisId))
        ) return false
        val moved = highlightWanted && cell != null &&
            (highlight != cell || highlightSpanX != spanX || highlightSpanY != spanY)
        val wasShown = highlightAlpha > 0f
        currentHighlightRect(highlightFrom)
        if (cell != null) {
            highlight = cell
            highlightSpanX = spanX
            highlightSpanY = spanY
            highlightHint = hint
            cellRect(cell, spanX, spanY, highlightTo)
            // A highlight appearing starts in its cell; only a move glides from the old one.
            if (!wasShown) highlightFrom.set(highlightTo)
        }
        highlightWanted = wanted
        highlightEmphasisId = if (wanted) emphasisId else null
        updateEmphasis()
        animateHighlight(if (wanted) 1f else 0f)
        invalidate()
        return moved
    }

    private fun animateHighlight(alphaTarget: Float) {
        highlightAnimator?.cancel()
        highlightAnimator = null
        highlightAlphaFrom = highlightAlpha
        highlightAlphaTo = alphaTarget
        val ms = if (highlightMoveMs > 0L) highlightMoveMs else motion.duration(HIGHLIGHT_FADE_MS)
        if (ms <= 0L || !isAttachedToWindow) {
            setHighlightProgress(1f)
            return
        }
        highlightProgress = 0f
        highlightAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ms
            interpolator = DecelerateInterpolator()
            addUpdateListener { setHighlightProgress(it.animatedValue as Float) }
            doOnEndOnce { if (highlightAnimator === this) highlightAnimator = null }
            start()
        }
    }

    private fun setHighlightProgress(progress: Float) {
        highlightProgress = progress
        highlightAlpha = highlightAlphaFrom + (highlightAlphaTo - highlightAlphaFrom) * progress
        invalidate()
    }

    /** The highlight as drawn right now, mid-animation included. */
    private fun currentHighlightRect(out: RectF) {
        val p = highlightProgress
        out.set(
            highlightFrom.left + (highlightTo.left - highlightFrom.left) * p,
            highlightFrom.top + (highlightTo.top - highlightFrom.top) * p,
            highlightFrom.right + (highlightTo.right - highlightFrom.right) * p,
            highlightFrom.bottom + (highlightTo.bottom - highlightFrom.bottom) * p,
        )
    }

    /** [cell]'s full span on screen; drawing applies the hint's inset. */
    private fun cellRect(cell: Cell, spanX: Int, spanY: Int, out: RectF) {
        val cellW = contentWidth.toFloat() / grid.columns
        val cellH = contentHeight.toFloat() / grid.rows
        val col = CellMath.visualColumn(cell.x - columnOffset, spanX, grid, isRtl)
        val left = paddingLeft + col * cellW
        val top = paddingTop + cell.y * cellH
        out.set(left, top, left + cellW * spanX, top + cellH * spanY)
    }

    private fun updateEmphasis() {
        val next = highlightEmphasisId?.let(::findItemView)
        if (next === emphasized) return
        emphasized?.let { scaleTo(it, 1f) }
        emphasized = next
        next?.let { scaleTo(it, EMPHASIS_SCALE) }
    }

    private fun scaleTo(view: View, scale: Float) {
        view.animate().cancel()
        // The cancel also stops a fade-in under way, whose end action would never run: land its alpha.
        forgetFadeIn(view)?.let { view.alpha = it }
        val ms = motion.duration(HIGHLIGHT_FADE_MS)
        if (ms <= 0L || !view.isAttachedToWindow) {
            view.scaleX = scale
            view.scaleY = scale
        } else {
            view.animate().scaleX(scale).scaleY(scale).setDuration(ms).start()
        }
    }

    /** The child showing item [id], if any. */
    fun itemView(id: String): View? = findItemView(id)

    private fun findItemView(id: String): View? {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.getTag(R.id.home_item_id) == id) return child
        }
        return null
    }

    /**
     * Shows [child]'s footprint ([shape], see showItemFootprint): a tile in the menu's colors
     * behind an icon, or an outline and tint over the whole cell area of a widget, so the
     * empty-looking parts that still take taps are visible.
     */
    internal fun showFootprint(child: View, shape: FootprintShape, colors: FootprintColors, fadeMs: Long) {
        if (child.parent !== this) return
        footprintColors = intArrayOf(colors.fill, colors.stroke)
        footprintStroke.strokeWidth = colors.strokeWidth
        footprintShape = shape
        footprintChild = child
        animateFootprint(1f, fadeMs)
    }

    /** Fades out the footprint if it still belongs to [child]. */
    fun clearFootprint(child: View, fadeMs: Long) {
        if (footprintChild !== child) return
        animateFootprint(0f, fadeMs) { if (footprintAlpha == 0f) footprintChild = null }
    }

    private fun animateFootprint(target: Float, durationMs: Long, end: () -> Unit = {}) {
        footprintAnimator?.cancel()
        footprintAnimator = null
        if (durationMs <= 0L || !isAttachedToWindow) {
            footprintAlpha = target
            end()
            invalidate()
            return
        }
        footprintAnimator = ValueAnimator.ofFloat(footprintAlpha, target).apply {
            duration = durationMs
            addUpdateListener {
                footprintAlpha = it.animatedValue as Float
                invalidate()
            }
            doOnEndOnce { if (footprintAnimator === this) { footprintAnimator = null; end() } }
            start()
        }
    }

    private inline fun ValueAnimator.doOnEndOnce(crossinline block: () -> Unit) {
        addListener(object : android.animation.AnimatorListenerAdapter() {
            private var cancelled = false
            override fun onAnimationCancel(animation: android.animation.Animator) { cancelled = true }
            override fun onAnimationEnd(animation: android.animation.Animator) { if (!cancelled) block() }
        })
    }

    /** Updates [footprintRect] from the footprint child; false when nothing should be drawn. */
    private fun footprintBounds(): Boolean {
        val child = footprintChild ?: return false
        if (footprintAlpha <= 0f) return false
        // A removed child keeps its last rect while the footprint fades out.
        if (child.parent === this && !child.isGone) {
            val shape = footprintShape ?: return false
            footprintRect.set(shape.rect)
            footprintRect.offset(child.left.toFloat(), child.top.toFloat())
        }
        return !footprintRect.isEmpty
    }

    private fun alphaOf(color: Int): Int = (Color.alpha(color) * footprintAlpha).toInt()

    override fun onDetachedFromWindow() {
        highlightAnimator?.cancel()
        highlightAnimator = null
        highlight = null
        highlightAlpha = 0f
        highlightProgress = 1f
        highlightWanted = false
        emphasized?.let {
            it.animate().cancel()
            it.scaleX = 1f
            it.scaleY = 1f
        }
        emphasized = null
        finishPageShift()
        finishItemMotion()
        footprintAnimator?.cancel()
        footprintAnimator = null
        footprintChild = null
        footprintAlpha = 0f
        super.onDetachedFromWindow()
    }

    /** Shows children inside the visible window and hides the rest (without touching their placement). */
    fun refreshViewport() {
        var hidden = 0
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val lp = child.layoutParams as? LayoutParams ?: continue
            val inside = isInViewport(lp)
            if (!inside) hidden++
            val wanted = if (inside) View.VISIBLE else View.GONE
            if (child.visibility != wanted) child.visibility = wanted
        }
        hiddenChildCount = hidden
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) {
            MeasureSpec.getSize(heightMeasureSpec)
        } else {
            val wanted = paddingTop + paddingBottom + grid.rows * minCellHeight
            resolveSize(wanted, heightMeasureSpec)
        }
        setMeasuredDimension(width, height)
        val cellW = (width - paddingLeft - paddingRight) / grid.columns
        val cellH = (height - paddingTop - paddingBottom) / grid.rows
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.isGone) continue
            val lp = child.layoutParams as LayoutParams
            child.measure(
                MeasureSpec.makeMeasureSpec((cellW * lp.spanX).coerceAtLeast(0), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec((cellH * lp.spanY).coerceAtLeast(0), MeasureSpec.EXACTLY),
            )
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val cellW = contentWidth / grid.columns
        val cellH = contentHeight / grid.rows
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.isGone) continue
            val lp = child.layoutParams as LayoutParams
            val col = CellMath.visualColumn(lp.x - columnOffset, lp.spanX, grid, isRtl)
            val left = paddingLeft + col * cellW
            val top = paddingTop + lp.y * cellH
            child.layout(left, top, left + child.measuredWidth, top + child.measuredHeight)
            flipFrom.remove(child)?.let { from -> playFlip(child, from.first - left, from.second - top) }
        }
        flipFrom.clear()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Behind the children: tints transparent parts of a widget and the space around an icon.
        if (footprintBounds()) {
            footprintFill.color = footprintColors[0]
            footprintFill.alpha = alphaOf(footprintColors[0])
            val radius = footprintShape?.radius ?: 0f
            canvas.drawRoundRect(footprintRect, radius, radius, footprintFill)
        }
        if (highlightAlpha <= 0f) return
        // A settled highlight follows the grid (resized or dock page changed); a moving one its animation.
        highlight?.takeIf { highlightAnimator == null }?.let { cellRect(it, highlightSpanX, highlightSpanY, highlightTo) }
        currentHighlightRect(highlightRect)
        if (highlightHint == DropHint.FOLDER) {
            highlightRect.inset(highlightRadius / 4f, highlightRadius / 4f)
            highlightPaint.alpha = (highlightBaseAlpha * highlightAlpha / 2f).toInt()
            canvas.drawRoundRect(highlightRect, highlightRadius, highlightRadius, highlightPaint)
            highlightRing.alpha = (255 * highlightAlpha).toInt()
            canvas.drawRoundRect(highlightRect, highlightRadius, highlightRadius, highlightRing)
            return
        }
        val inset = highlightRadius / 2f
        highlightRect.inset(inset, inset)
        highlightPaint.alpha = (highlightBaseAlpha * highlightAlpha).toInt()
        canvas.drawRoundRect(highlightRect, highlightRadius, highlightRadius, highlightPaint)
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        // Above the children, so an opaque widget filling its cells still shows its edges.
        if (footprintBounds()) {
            footprintStroke.color = footprintColors[1]
            footprintStroke.alpha = alphaOf(footprintColors[1])
            // Inside the footprint, the way the menu card's outline lies inside the card.
            val inset = footprintStroke.strokeWidth / 2f
            footprintEdge.set(footprintRect)
            footprintEdge.inset(inset, inset)
            val radius = ((footprintShape?.radius ?: 0f) - inset).coerceAtLeast(0f)
            canvas.drawRoundRect(footprintEdge, radius, radius, footprintStroke)
        }
    }

    // --- Horizontal swipe (dock pages) ---------------------------------------------------------

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (onHorizontalSwipe == null) return false
        trackSwipe(ev)
        return swiping
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (onHorizontalSwipe == null) return super.onTouchEvent(ev)
        val wasSwiping = swiping
        trackSwipe(ev)
        if (ev.actionMasked == MotionEvent.ACTION_UP && !wasSwiping) performClick()
        return true // keep receiving the gesture over empty cells
    }

    override fun performClick(): Boolean = super.performClick()

    private fun trackSwipe(ev: MotionEvent) {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                swipeDownX = ev.x
                swipeDownY = ev.y
                swiping = false
                swipeDecided = false
            }
            MotionEvent.ACTION_MOVE -> if (!swipeDecided) {
                val dx = ev.x - swipeDownX
                val dy = ev.y - swipeDownY
                if (abs(dx) > touchSlop && abs(dx) > abs(dy)) {
                    swipeDecided = true
                    swiping = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    val towardsLeft = dx < 0
                    onHorizontalSwipe?.invoke(if (isRtl) !towardsLeft else towardsLeft)
                } else if (abs(dy) > touchSlop) {
                    swipeDecided = true // vertical: the enclosing layout handles it
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                swiping = false
                swipeDecided = false
            }
        }
    }

    /**
     * Re-binds children to [items], reusing existing views by item id so focus and icons survive.
     * A view is replaced when [canReuse] says it cannot show the item (e.g. an icon for a widget).
     */
    fun <T> bind(
        items: List<T>,
        id: (T) -> String,
        cellOf: (T) -> LayoutParams,
        create: (T) -> View,
        update: (View, T) -> Unit,
        recycle: (View) -> Unit,
        canReuse: (View, T) -> Boolean = { _, _ -> true },
    ) {
        finishPageShift()
        val animate = hasBound && isAttachedToWindow && width > 0 && motion.enabled
        val existing = HashMap<String, View>(childCount)
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            (child.getTag(R.id.home_item_id) as? String)?.let { existing[it] = child }
        }
        val wanted = items.mapTo(HashSet(items.size)) { id(it) }
        existing.filterKeys { it !in wanted }.forEach { (key, view) ->
            recycle(view)
            if (animate && view is AppIconView && key !in flipExemptIds) removeWithFade(view) else removeView(view)
        }
        for (item in items) {
            val itemId = id(item)
            existing[itemId]?.let { old ->
                if (!canReuse(old, item)) {
                    recycle(old)
                    removeView(old)
                    existing.remove(itemId)
                }
            }
            val created = existing[itemId] == null
            val view = existing[itemId] ?: create(item).also {
                it.setTag(R.id.home_item_id, itemId)
                addView(it, cellOf(item))
            }
            val newLp = cellOf(item)
            val lp = view.layoutParams as LayoutParams
            if (lp.x != newLp.x || lp.y != newLp.y || lp.spanX != newLp.spanX || lp.spanY != newLp.spanY) {
                if (animate && !created && itemId !in flipExemptIds && view.width > 0 && view.visibility == View.VISIBLE) {
                    flipFrom[view] = (view.left + view.translationX).toInt() to (view.top + view.translationY).toInt()
                }
                view.layoutParams = newLp
            }
            if (lp.zIndex != newLp.zIndex) {
                (view.layoutParams as LayoutParams).zIndex = newLp.zIndex
                invalidate()
            }
            update(view, item)
            if (created && animate && itemId !in flipExemptIds) fadeIn(view)
        }
        refreshViewport()
        drawingOrder = IntArray(0)
        hasBound = true
    }

    /** Forget that this layout was bound, so the next bind places items without motion (views were cleared). */
    fun forgetBinding() {
        finishItemMotion()
        hasBound = false
    }

    // --- Item motion: items glide to a new cell, fade in when added and out when removed ---------

    private var hasBound = false

    /** Items whose arrival is animated by someone else (e.g. a dropped item that bounces in). */
    var flipExemptIds: Set<String> = emptySet()

    private val flipFrom = HashMap<View, Pair<Int, Int>>()
    private val fadingIn = HashMap<View, Float>()
    private val fadingOut = HashSet<View>()

    private fun playFlip(child: View, dx: Int, dy: Int) {
        if (dx == 0 && dy == 0) return
        val ms = motion.duration(MOVE_MS)
        if (ms <= 0L || !isAttachedToWindow) return
        // Cancelling a fade-in under way would freeze the item part-way transparent.
        val fadeRest = fadingIn.remove(child)
        ItemMotion.settle(child, alpha = fadeRest)
        child.translationX = dx.toFloat()
        child.translationY = dy.toFloat()
        child.animate().translationX(0f).translationY(0f).setDuration(ms)
            .setInterpolator(DecelerateInterpolator()).start()
    }

    private fun fadeIn(view: View) {
        val ms = motion.duration(FADE_MS)
        if (ms <= 0L) return
        val rest = view.alpha
        view.alpha = 0f
        fadingIn[view] = rest
        view.animate().alpha(rest).setDuration(ms).withEndAction { fadingIn.remove(view) }.start()
    }

    private fun removeWithFade(view: View) {
        // A removed view must not stay in fadingIn; its alpha rests at the fade-in's target, not part-way.
        val fadeRest = forgetFadeIn(view)
        val ms = motion.duration(FADE_MS)
        if (ms <= 0L || view.width == 0 || view.visibility != View.VISIBLE) {
            if (fadeRest != null) ItemMotion.settle(view, alpha = fadeRest)
            removeView(view)
            return
        }
        val rest = fadeRest ?: view.alpha
        view.animate().cancel()
        removeView(view)
        overlay.add(view)
        fadingOut += view
        view.animate().alpha(0f).setDuration(ms).withEndAction {
            fadingOut.remove(view)
            overlay.remove(view)
            view.alpha = rest
        }.start()
    }

    /** Ends every running item animation at its final state; nothing keeps a stray offset or alpha. */
    private fun finishItemMotion() {
        flipFrom.clear()
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            ItemMotion.settle(child, translationX = 0f, translationY = 0f)
        }
        fadingIn.forEach { (view, rest) -> ItemMotion.settle(view, alpha = rest) }
        fadingIn.clear()
        fadingOut.toList().forEach { view ->
            view.animate().cancel()
            overlay.remove(view)
        }
        fadingOut.clear()
    }

    // --- Dock pages: the outgoing columns slide and fade out while the incoming ones slide in ------

    private var pageShift: ValueAnimator? = null
    private var shiftIncoming: List<Pair<View, Float>> = emptyList()
    private var shiftOutgoing: List<Pair<BitmapDrawable, Rect>> = emptyList()
    private var shiftVector = 0f

    /** Shows the window starting at [offset]; [forward] is the reading-order direction of the change. */
    fun shiftColumnOffset(offset: Int, forward: Boolean) {
        if (offset == columnOffset) return
        finishPageShift()
        if (!motion.enabled || !isAttachedToWindow || width == 0 || motion.duration(PAGE_SHIFT_MS) <= 0L) {
            columnOffset = offset
            return
        }
        val before = (0 until childCount).map(::getChildAt).filter { it.visibility == View.VISIBLE && it.width > 0 }
        val snapshots = before.mapNotNull { view ->
            try {
                BitmapDrawable(resources, view.drawToBitmap()) to view
            } catch (_: RuntimeException) {
                null // cannot be drawn into a bitmap: that item just disappears
            } catch (_: OutOfMemoryError) {
                null
            }
        }
        columnOffset = offset
        val incoming = (0 until childCount).map(::getChildAt).filter { it.visibility == View.VISIBLE && it !in before }
        val outgoing = snapshots.filter { (_, view) -> view.visibility != View.VISIBLE }
        if (incoming.isEmpty() && outgoing.isEmpty()) return
        val sign = (if (forward) -1f else 1f) * (if (isRtl) -1f else 1f)
        shiftVector = sign * width * PAGE_SHIFT_FRACTION
        shiftIncoming = incoming.map { it to it.alpha }
        shiftOutgoing = outgoing.map { (drawable, view) ->
            val bounds = Rect(view.left, view.top, view.right, view.bottom)
            drawable.bounds = bounds
            overlay.add(drawable)
            drawable to bounds
        }
        applyPageShift(0f)
        pageShift = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = motion.duration(PAGE_SHIFT_MS)
            interpolator = DecelerateInterpolator()
            addUpdateListener { applyPageShift(it.animatedValue as Float) }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (pageShift === animation) finishPageShift()
                }
            })
            start()
        }
    }

    private fun applyPageShift(progress: Float) {
        for ((view, rest) in shiftIncoming) {
            view.translationX = -shiftVector * (1f - progress)
            view.alpha = rest * progress
        }
        for ((drawable, bounds) in shiftOutgoing) {
            val dx = (shiftVector * progress).toInt()
            drawable.setBounds(bounds.left + dx, bounds.top, bounds.right + dx, bounds.bottom)
            drawable.alpha = (255 * (1f - progress)).toInt()
        }
    }

    private fun finishPageShift() {
        val running = pageShift
        pageShift = null
        running?.cancel()
        for ((view, rest) in shiftIncoming) {
            view.translationX = 0f
            view.alpha = rest
        }
        for ((drawable, _) in shiftOutgoing) overlay.remove(drawable)
        shiftIncoming = emptyList()
        shiftOutgoing = emptyList()
    }

    private companion object {
        const val HIGHLIGHT_FADE_MS = 120L
        const val EMPHASIS_SCALE = 1.12f
        const val MOVE_MS = 220L
        const val FADE_MS = 160L
        const val PAGE_SHIFT_MS = 240L
        const val PAGE_SHIFT_FRACTION = 0.25f
    }

    override fun checkLayoutParams(p: ViewGroup.LayoutParams?): Boolean = p is LayoutParams
    override fun generateDefaultLayoutParams(): ViewGroup.LayoutParams = LayoutParams(0, 0)
    override fun generateLayoutParams(p: ViewGroup.LayoutParams?): ViewGroup.LayoutParams = LayoutParams(0, 0)
    override fun shouldDelayChildPressedState(): Boolean = false

    // The page is long-clickable (empty-area menu) and has no pressed visual of its own; forwarding
    // its pressed state would light up non-clickable children such as widgets.
    override fun dispatchSetPressed(pressed: Boolean) = Unit

    /**
     * Stand-in for an item on a page outside the bind window: same id tag, model tag and
     * [LayoutParams] as the real view, so cell occupancy and lookups by id keep working, but it draws
     * nothing, takes no touches and is hidden from accessibility (the page is off screen anyway).
     */
    class Placeholder(context: Context) : View(context) {
        init {
            setWillNotDraw(true)
            isFocusable = false
            isClickable = false
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }
    }

    class LayoutParams(val x: Int, val y: Int, val spanX: Int = 1, val spanY: Int = 1) :
        ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT) {
        /** Stacking order when items overlap (home.allowWidgetOverlap); higher draws on top. */
        var zIndex: Int = 0
    }
}
