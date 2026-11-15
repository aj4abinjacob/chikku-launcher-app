package io.github.aj4abinjacob.chikku.home.widget

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.CellPlacement
import io.github.aj4abinjacob.chikku.core.model.GridSpec
import io.github.aj4abinjacob.chikku.home.CellMath
import io.github.aj4abinjacob.chikku.home.Haptics
import io.github.aj4abinjacob.chikku.theme.Motion
import kotlin.math.hypot

/**
 * Full-screen widget resize overlay. Each edge and corner keeps its opposite edge fixed, follows
 * the finger within provider/grid limits, and snaps to whole cells on release. [onCommit] persists
 * position and spans atomically, then reports success on the main thread. Failed or cancelled
 * drags return to the last accepted placement. Handles stay reachable at screen boundaries.
 * [widgetRect] is in overlay coordinates; model columns mirror in RTL.
 */
@SuppressLint("ViewConstructor")
class WidgetResizeFrame(
    context: Context,
    widgetRect: RectF,
    private val cellWidth: Int,
    private val cellHeight: Int,
    private val range: SpanRange,
    startPlacement: CellPlacement,
    private val grid: GridSpec,
    private val rtl: Boolean,
    private val onCommit: (CellPlacement, complete: (Boolean) -> Unit) -> Unit,
    private val onClose: () -> Unit,
) : View(context) {

    private val gridLeft = widgetRect.left - visualColumn(startPlacement) * cellWidth
    private val gridTop = widgetRect.top - startPlacement.y * cellHeight
    private val gridRight = gridLeft + grid.columns * cellWidth
    private val gridBottom = gridTop + grid.rows * cellHeight
    /** Animation speed for the fades and the glide to the snapped size; set by the host. */
    var motion: Motion = Motion.DEFAULT
    private var rectAnimator: ValueAnimator? = null
    private var closing = false
    private var accepted = startPlacement
    private var placement = startPlacement
    private var committing = false
    private val rect = RectF(widgetRect)
    private val snapRect = RectF()
    private val startRect = RectF()
    private val drawRect = RectF()

    private val density = resources.displayMetrics.density
    private val handleRadius = 5f * density
    private val frameRadius = 12f * density
    private val touchRadius = resources.getDimensionPixelSize(R.dimen.home_min_touch_target) / 2f + 4f * density
    private val accent = ContextCompat.getColor(context, R.color.chikku_accent)
    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        color = ContextCompat.getColor(context, R.color.chikku_on_wallpaper)
        alpha = FRAME_ALPHA
    }
    private val snapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.home_drop_highlight)
        alpha = SNAP_ALPHA
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.chikku_on_wallpaper)
    }
    private val handleRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
        color = accent
    }
    private val handleInset = handleRadius + 2f * density

    private enum class Handle(val left: Boolean, val right: Boolean, val top: Boolean, val bottom: Boolean) {
        LEFT(true, false, false, false),
        RIGHT(false, true, false, false),
        TOP(false, false, true, false),
        BOTTOM(false, false, false, true),
        TOP_LEFT(true, false, true, false),
        TOP_RIGHT(false, true, true, false),
        BOTTOM_LEFT(true, false, false, true),
        BOTTOM_RIGHT(false, true, false, true),
    }

    private var active: Handle? = null
    private var pointerId = MotionEvent.INVALID_POINTER_ID
    private var downX = 0f
    private var downY = 0f
    private val canResizeX get() = range.minX != range.maxX
    private val canResizeY get() = range.minY != range.maxY

    init {
        isClickable = true
        isFocusable = true
        contentDescription = context.getString(R.string.widgets_resize_frame_description)
        accessibilityLiveRegion = ACCESSIBILITY_LIVE_REGION_POLITE
        updateStateDescription()
        addAccessibilityActions()
    }

    private fun addAccessibilityActions() {
        fun action(label: Int, dx: Int, dy: Int) {
            ViewCompat.addAccessibilityAction(this, context.getString(label)) { _, _ ->
                if (!committing && active == null) {
                    // Accessible size actions keep the model origin and respect the remaining room.
                    val x = (accepted.spanX + dx).coerceIn(
                        range.minX.coerceAtMost(grid.columns - accepted.x),
                        range.maxX.coerceAtMost(grid.columns - accepted.x),
                    )
                    val y = (accepted.spanY + dy).coerceIn(
                        range.minY.coerceAtMost(grid.rows - accepted.y),
                        range.maxY.coerceAtMost(grid.rows - accepted.y),
                    )
                    commit(accepted.copy(spanX = x, spanY = y))
                }
                true
            }
        }
        if (canResizeX) {
            action(R.string.widgets_resize_wider, 1, 0)
            action(R.string.widgets_resize_narrower, -1, 0)
        }
        if (canResizeY) {
            action(R.string.widgets_resize_taller, 0, 1)
            action(R.string.widgets_resize_shorter, 0, -1)
        }
        ViewCompat.addAccessibilityAction(this, context.getString(R.string.widgets_resize_done)) { _, _ ->
            onClose()
            true
        }
    }

    private fun visualColumn(p: CellPlacement): Int = CellMath.visualColumn(p.x, p.spanX, grid, rtl)

    private fun frameRect(p: CellPlacement, out: RectF) {
        val left = gridLeft + visualColumn(p) * cellWidth
        val top = gridTop + p.y * cellHeight
        out.set(left, top, left + p.spanX * cellWidth, top + p.spanY * cellHeight)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        frameRect(placement, snapRect)
        if (active != null) canvas.drawRoundRect(inView(snapRect), frameRadius, frameRadius, snapPaint)
        canvas.drawRoundRect(inView(rect), frameRadius, frameRadius, framePaint)
        handlePoints().forEach { (_, p) ->
            canvas.drawCircle(p.x, p.y, handleRadius, handlePaint)
            canvas.drawCircle(p.x, p.y, handleRadius, handleRing)
        }
    }

    /** [r] pulled onto the handle line so edges and corners of full-width widgets stay visible. */
    private fun inView(r: RectF): RectF {
        val maxX = (width - handleInset).coerceAtLeast(handleInset)
        val maxY = (height - handleInset).coerceAtLeast(handleInset)
        drawRect.set(
            r.left.coerceIn(handleInset, maxX),
            r.top.coerceIn(handleInset, maxY),
            r.right.coerceIn(handleInset, maxX),
            r.bottom.coerceIn(handleInset, maxY),
        )
        return drawRect
    }

    /** Handle centers pulled inside the overlay so full-width/full-height widgets remain usable. */
    private fun handlePoints(): List<Pair<Handle, PointF>> {
        fun point(x: Float, y: Float) = PointF(
            x.coerceIn(handleInset, (width - handleInset).coerceAtLeast(handleInset)),
            y.coerceIn(handleInset, (height - handleInset).coerceAtLeast(handleInset)),
        )
        return buildList {
            if (canResizeX) {
                add(Handle.LEFT to point(rect.left, rect.centerY()))
                add(Handle.RIGHT to point(rect.right, rect.centerY()))
            }
            if (canResizeY) {
                add(Handle.TOP to point(rect.centerX(), rect.top))
                add(Handle.BOTTOM to point(rect.centerX(), rect.bottom))
            }
            if (canResizeX && canResizeY) {
                add(Handle.TOP_LEFT to point(rect.left, rect.top))
                add(Handle.TOP_RIGHT to point(rect.right, rect.top))
                add(Handle.BOTTOM_LEFT to point(rect.left, rect.bottom))
                add(Handle.BOTTOM_RIGHT to point(rect.right, rect.bottom))
            }
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        updateGestureExclusion()
    }

    private fun updateGestureExclusion() {
        systemGestureExclusionRects = handlePoints().map { (_, p) ->
            Rect(
                (p.x - touchRadius).toInt().coerceAtLeast(0),
                (p.y - touchRadius).toInt().coerceAtLeast(0),
                (p.x + touchRadius).toInt().coerceAtMost(width),
                (p.y + touchRadius).toInt().coerceAtMost(height),
            )
        }
    }

    private fun handleAt(x: Float, y: Float): Handle? = handlePoints()
        .minByOrNull { (_, p) -> hypot(x - p.x, y - p.y) }
        ?.takeIf { (_, p) -> hypot(x - p.x, y - p.y) <= touchRadius }?.first

    @SuppressLint("ClickableViewAccessibility") // resizing is also offered as accessibility actions
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (closing) return false
        if (committing) return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                rectAnimator?.cancel()
                active = handleAt(event.x, event.y)
                if (active == null) {
                    if (!rect.contains(event.x, event.y)) onClose()
                    return true
                }
                pointerId = event.getPointerId(0)
                downX = event.x
                downY = event.y
                startRect.set(rect)
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(pointerId)
                if (active != null && index >= 0) dragTo(event.getX(index), event.getY(index))
            }
            MotionEvent.ACTION_UP -> if (active != null) {
                val index = event.findPointerIndex(pointerId)
                if (index >= 0) dragTo(event.getX(index), event.getY(index))
                finishDrag()
                commit(placement)
            }
            MotionEvent.ACTION_POINTER_UP -> if (event.getPointerId(event.actionIndex) == pointerId) cancelDrag()
            MotionEvent.ACTION_CANCEL -> cancelDrag()
        }
        return true
    }

    private fun dragTo(x: Float, y: Float) {
        val handle = active ?: return
        val dx = x - downX
        val dy = y - downY
        // Clamp each moving edge against its fixed opposite edge, provider limits and grid bounds.
        if (handle.left) {
            val max = minOf(range.maxX * cellWidth.toFloat(), startRect.right - gridLeft)
            val min = minOf(range.minX * cellWidth.toFloat(), max)
            rect.left = (startRect.left + dx).coerceIn(startRect.right - max, startRect.right - min)
        } else if (handle.right) {
            val max = minOf(range.maxX * cellWidth.toFloat(), gridRight - startRect.left)
            val min = minOf(range.minX * cellWidth.toFloat(), max)
            rect.right = (startRect.right + dx).coerceIn(startRect.left + min, startRect.left + max)
        }
        if (handle.top) {
            val max = minOf(range.maxY * cellHeight.toFloat(), startRect.bottom - gridTop)
            val min = minOf(range.minY * cellHeight.toFloat(), max)
            rect.top = (startRect.top + dy).coerceIn(startRect.bottom - max, startRect.bottom - min)
        } else if (handle.bottom) {
            val max = minOf(range.maxY * cellHeight.toFloat(), gridBottom - startRect.top)
            val min = minOf(range.minY * cellHeight.toFloat(), max)
            rect.bottom = (startRect.bottom + dy).coerceIn(startRect.top + min, startRect.top + max)
        }
        val sx = WidgetSizing.snap(rect.width(), cellWidth, 1, grid.columns)
        val sy = WidgetSizing.snap(rect.height(), cellHeight, 1, grid.rows)
        val modelStartEdge = if (rtl) handle.right else handle.left
        val previous = placement
        placement = accepted.copy(
            x = if (modelStartEdge) accepted.x + accepted.spanX - sx else accepted.x,
            y = if (handle.top) accepted.y + accepted.spanY - sy else accepted.y,
            spanX = sx,
            spanY = sy,
        )
        if (placement.spanX != previous.spanX || placement.spanY != previous.spanY) Haptics.tick(this)
        invalidate()
    }

    private fun finishDrag() {
        active = null
        pointerId = MotionEvent.INVALID_POINTER_ID
        parent?.requestDisallowInterceptTouchEvent(false)
    }

    private fun cancelDrag() {
        finishDrag()
        resetToAccepted()
    }

    private fun resetToAccepted() {
        placement = accepted
        glideRectTo(accepted)
        updateStateDescription()
    }

    /** Moves the frame to the cells of [p] (a short glide, or at once when animations are off). */
    private fun glideRectTo(p: CellPlacement) {
        rectAnimator?.cancel()
        rectAnimator = null
        val target = RectF()
        frameRect(p, target)
        val ms = motion.duration(SNAP_MS)
        if (ms <= 0L || !isAttachedToWindow || rect == target) {
            rect.set(target)
            invalidate()
            updateGestureExclusion()
            return
        }
        val from = RectF(rect)
        rectAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ms
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val f = it.animatedFraction
                rect.set(
                    from.left + (target.left - from.left) * f, from.top + (target.top - from.top) * f,
                    from.right + (target.right - from.right) * f, from.bottom + (target.bottom - from.bottom) * f,
                )
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    rect.set(target)
                    invalidate()
                    updateGestureExclusion()
                }
            })
            start()
        }
    }

    private fun updateStateDescription() {
        ViewCompat.setStateDescription(this, context.getString(R.string.widgets_picker_size, placement.spanX, placement.spanY))
    }

    private fun commit(requested: CellPlacement) {
        if (requested == accepted) {
            resetToAccepted()
            return
        }
        placement = requested
        glideRectTo(requested)
        committing = true
        onCommit(requested) { success ->
            if (success) {
                accepted = requested
                Haptics.drop(this) // confirmed by the stored result, not by the release
            }
            committing = false
            resetToAccepted()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // A short fade instead of popping the frame in.
        val ms = motion.duration(FADE_MS)
        if (ms > 0L) {
            alpha = 0f
            animate().alpha(1f).setDuration(ms).start()
        }
    }

    override fun onDetachedFromWindow() {
        rectAnimator?.cancel()
        rectAnimator = null
        animate().cancel()
        super.onDetachedFromWindow()
    }

    /** Fades the frame out and then calls [remove]; at once when animations are off. */
    fun dismiss(remove: () -> Unit) {
        closing = true
        rectAnimator?.cancel()
        val ms = motion.duration(FADE_MS)
        if (ms <= 0L || !isAttachedToWindow) {
            remove()
            return
        }
        animate().cancel()
        animate().alpha(0f).setDuration(ms).withEndAction(remove).start()
    }

    private companion object {
        const val FRAME_ALPHA = 204 // ~80%
        const val SNAP_ALPHA = 64
        const val FADE_MS = 150L
        const val SNAP_MS = 140L
    }
}
