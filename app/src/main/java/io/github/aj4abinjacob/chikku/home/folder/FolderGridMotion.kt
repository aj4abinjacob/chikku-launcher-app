package io.github.aj4abinjacob.chikku.home.folder

import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.DecelerateInterpolator
import io.github.aj4abinjacob.chikku.home.ItemMotion
import io.github.aj4abinjacob.chikku.theme.Motion
import kotlin.math.abs

/**
 * Motion for the open folder's grid when its contents change underneath it: entries that stay
 * glide to their new cell (FLIP: remembered position, new layout, animate the difference), added
 * entries pop in, removed ones shrink away in the grid's overlay, and a page change slides the old
 * page out and the new one in.
 *
 * The caller keeps the views of entries that stay (diffing by id), so nothing here rebuilds the
 * grid and an animation in flight is never restarted by an unrelated update. Only translation,
 * scale and alpha change, and [cancelAll] leaves none of them behind. With [Motion.enabled] false,
 * or a grid that is not on screen, everything simply lands in place.
 */
internal class FolderGridMotion {
    var motion: Motion = Motion.DEFAULT

    /** Added or paging-in views, with the alpha each rests at. */
    private val entering = HashMap<View, Float>()
    private val leaving = ArrayList<View>()
    private val moving = HashSet<View>()
    private var host: FolderGridLayout? = null
    private var observer: ViewTreeObserver? = null
    private var preDraw: ViewTreeObserver.OnPreDrawListener? = null

    /** True when a change to [grid] can be animated now: shown, laid out and already holding entries. */
    fun canAnimate(grid: FolderGridLayout): Boolean =
        motion.enabled && grid.isAttachedToWindow && grid.width > 0 && grid.childCount > 0

    /** Where each of [grid]'s children is drawn now (layout position plus translation). */
    fun capture(grid: FolderGridLayout): Map<View, Pair<Float, Float>> {
        val visuals = HashMap<View, Pair<Float, Float>>()
        for (i in 0 until grid.childCount) {
            val view = grid.getChildAt(i)
            visuals[view] = (view.left + view.translationX) to (view.top + view.translationY)
        }
        return visuals
    }

    /** Ends what an earlier change left running, at its final state, before the next change is planned. */
    fun finishStale(grid: FolderGridLayout) {
        host = grid
        stopWatching()
        finishEntering()
        removeLeaving()
    }

    /** Forgets every animation and leaves all views at rest: the panel closed or was rebuilt. */
    fun cancelAll() {
        stopWatching()
        finishEntering()
        removeLeaving()
        for (view in moving) ItemMotion.settle(view, translationX = 0f, translationY = 0f)
        moving.clear()
        host = null
    }

    /**
     * Hides a view about to be added so its first frame is not at rest: it pops in ([pageDirection]
     * 0) or waits off to the side of the page it slides in from. Call before [play].
     */
    fun prepareEnter(grid: FolderGridLayout, view: View, pageDirection: Int) {
        entering[view] = view.alpha
        view.alpha = 0f
        if (pageDirection == 0) {
            view.scaleX = POP_SCALE
            view.scaleY = POP_SCALE
        } else {
            view.translationX = slide(grid, pageDirection)
        }
    }

    /**
     * Moves [views] (children of [grid] that are gone from the new contents) into its overlay and
     * animates them away; without [animate] they are just removed.
     */
    fun leave(grid: FolderGridLayout, views: List<View>, pageDirection: Int, animate: Boolean) {
        host = grid
        if (!animate) {
            views.forEach(grid::removeView)
            return
        }
        val ms = motion.duration(LEAVE_MS)
        for (view in views) {
            grid.overlay.add(view)
            leaving += view
            view.animate().cancel()
            val animator = view.animate().alpha(0f).setDuration(ms)
            if (pageDirection == 0) animator.scaleX(POP_SCALE).scaleY(POP_SCALE) else animator.translationX(-slide(grid, pageDirection))
            animator.setInterpolator(DECELERATE).withEndAction {
                grid.overlay.remove(view)
                leaving.remove(view)
            }.start()
        }
    }

    /**
     * The grid now holds its new children. Once it is laid out, starts [entered] views in and glides
     * the others from where they were drawn ([before]) to their cells; [skip] is a view the
     * reorder motion animates itself. Without [animate] translations are simply cleared.
     */
    fun play(grid: FolderGridLayout, before: Map<View, Pair<Float, Float>>, entered: List<View>, skip: View?, animate: Boolean) {
        host = grid
        if (!animate) {
            for (i in 0 until grid.childCount) {
                val view = grid.getChildAt(i)
                if (view === skip) continue
                ItemMotion.settle(view, translationX = 0f, translationY = 0f)
            }
            return
        }
        val listener = object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                stopWatching()
                start(grid, before, entered, skip)
                return true
            }
        }
        observer = grid.viewTreeObserver.also { it.addOnPreDrawListener(listener) }
        preDraw = listener
    }

    private fun start(grid: FolderGridLayout, before: Map<View, Pair<Float, Float>>, entered: List<View>, skip: View?) {
        val enterMs = motion.duration(ENTER_MS)
        val moveMs = motion.duration(MOVE_MS)
        for (i in 0 until grid.childCount) {
            val view = grid.getChildAt(i)
            if (view === skip) continue
            if (view in entered) {
                val rest = entering[view] ?: continue
                view.animate().cancel()
                view.animate().alpha(rest).scaleX(1f).scaleY(1f).translationX(0f).setDuration(enterMs)
                    .setInterpolator(DECELERATE).withEndAction { entering.remove(view) }.start()
                continue
            }
            val old = before[view]
            view.animate().cancel()
            val dx = if (old == null) 0f else old.first - view.left
            val dy = if (old == null) 0f else old.second - view.top
            if (abs(dx) < 1f && abs(dy) < 1f) {
                view.translationX = 0f
                view.translationY = 0f
                continue
            }
            view.translationX = dx
            view.translationY = dy
            moving += view
            view.animate().translationX(0f).translationY(0f).setDuration(moveMs).setInterpolator(DECELERATE)
                .withEndAction { moving.remove(view) }.start()
        }
    }

    private fun stopWatching() {
        val listener = preDraw ?: return
        observer?.takeIf { it.isAlive }?.removeOnPreDrawListener(listener)
        observer = null
        preDraw = null
    }

    private fun finishEntering() {
        for ((view, rest) in entering) {
            ItemMotion.settle(view, alpha = rest, scale = 1f, translationX = 0f)
        }
        entering.clear()
    }

    private fun removeLeaving() {
        val grid = host
        for (view in leaving) {
            view.animate().cancel()
            grid?.overlay?.remove(view)
        }
        leaving.clear()
    }

    /** How far a page's contents slide: toward the side [pageDirection] points to, mirrored in RTL. */
    private fun slide(grid: View, pageDirection: Int): Float {
        val sign = if (grid.layoutDirection == View.LAYOUT_DIRECTION_RTL) -1f else 1f
        return sign * pageDirection * grid.width * SLIDE_FRACTION
    }

    companion object {
        const val ENTER_MS = 220L
        const val LEAVE_MS = 160L
        const val MOVE_MS = 240L
        private const val POP_SCALE = 0.6f
        private const val SLIDE_FRACTION = 0.25f
        private val DECELERATE = DecelerateInterpolator()
    }
}
