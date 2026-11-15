package io.github.aj4abinjacob.chikku.home.drawer

import android.graphics.Rect
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.recyclerview.widget.RecyclerView
import java.util.WeakHashMap

/**
 * Moves the drawer rows beside the fast scroller's letter bubble out of its way while it is shown,
 * and eases them back when the drag ends. Those rows shrink toward the side away from the rail just
 * enough to clear the bubble (the far column barely moves, the column next to the rail moves the
 * most), so no app is covered or pushed off screen at any column count. Only drawing moves: layout, touch targets and
 * accessibility positions are unchanged.
 */
internal class BubbleNudge(private val list: RecyclerView) {

    private var bubble: Rect? = null
    private val targets = WeakHashMap<View, Float>()
    private val scales = WeakHashMap<View, Float>()
    private val interpolator = DecelerateInterpolator()

    /** [io.github.aj4abinjacob.chikku.theme.Motion.scale]: 0 jumps, 1 is normal speed. */
    var motionScale: Float = 1f

    init {
        list.addOnChildAttachStateChangeListener(object : RecyclerView.OnChildAttachStateChangeListener {
            override fun onChildViewAttachedToWindow(view: View) {
                if (bubble != null) list.post(::apply)
            }

            // A recycled cell must not come back still shifted.
            override fun onChildViewDetachedFromWindow(view: View) = reset(view)
        })
        list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (bubble != null) apply()
            }
        })
    }

    /** [bounds] of the bubble in the list parent's coordinates, or null once it hides. */
    fun moveTo(bounds: Rect?) {
        if (bounds == bubble) return
        bubble = bounds?.let(::Rect)
        apply()
    }

    private fun apply() {
        val shown = bubble
        val gridLeft = list.left + list.paddingLeft
        val gridRight = list.left + list.width - list.paddingRight
        val gridWidth = (gridRight - gridLeft).coerceAtLeast(1)
        val railOnRight = shown != null && shown.centerX() > list.left + list.width / 2
        // The row narrows by exactly the bubble's overlap, whatever the column count, so cells
        // keep their relative spacing and labels never run into each other.
        val room = when {
            shown == null -> 0
            railOnRight -> gridRight - shown.left
            else -> shown.right - gridLeft
        }.coerceIn(0, (gridWidth * (1f - MIN_SCALE)).toInt())
        val scale = 1f - room.toFloat() / gridWidth
        for (i in 0 until list.childCount) {
            val child = list.getChildAt(i)
            if (shown == null || !besideBubble(child, shown)) {
                animate(child, 0f, 1f)
                continue
            }
            // Scale the row about the grid edge away from the rail.
            val center = list.left + (child.left + child.right) / 2f
            val target = if (railOnRight) (center - gridLeft) * (scale - 1f) else (gridRight - center) * (1f - scale)
            animate(child, target, scale)
        }
    }

    private fun besideBubble(child: View, bubble: Rect): Boolean =
        child.bottom + list.top > bubble.top && child.top + list.top < bubble.bottom

    private fun animate(view: View, target: Float, scale: Float) {
        if ((targets[view] ?: 0f) == target && (scales[view] ?: 1f) == scale) return
        targets[view] = target
        scales[view] = scale
        view.animate().cancel()
        if (motionScale <= 0f) {
            view.translationX = target
            view.scaleX = scale
            view.scaleY = scale
            return
        }
        view.animate()
            .translationX(target)
            .scaleX(scale)
            .scaleY(scale)
            .setDuration(((if (scale == 1f) RETURN_MS else MOVE_MS) * motionScale).toLong())
            .setInterpolator(interpolator)
            .start()
    }

    private fun reset(view: View) {
        targets.remove(view)
        scales.remove(view)
        view.animate().cancel()
        view.translationX = 0f
        view.scaleX = 1f
        view.scaleY = 1f
    }

    private companion object {
        /** Rows never shrink below this, even with a very wide bubble or a narrow grid. */
        const val MIN_SCALE = 0.6f
        const val MOVE_MS = 160L
        const val RETURN_MS = 220L
    }
}
