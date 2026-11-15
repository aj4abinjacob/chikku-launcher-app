package io.github.aj4abinjacob.chikku.home.folder

import android.animation.TimeInterpolator
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import io.github.aj4abinjacob.chikku.home.Haptics
import io.github.aj4abinjacob.chikku.home.ItemMotion
import io.github.aj4abinjacob.chikku.theme.Motion

/**
 * Feedback for reordering inside an open folder: the dragged entry lifts out of its cell, the others
 * slide aside to show where it will land, and it drops into place with a small bounce.
 *
 * Only translations, scale and alpha change. The grid's children keep their order and layout, so
 * nothing is added or removed while the platform dispatches the drag; the caller stores the new
 * order on drop, and the update that follows shows it without a jump ([bind]).
 */
internal class FolderReorderMotion {
    var motion: Motion = Motion.DEFAULT

    private var grid: FolderGridLayout? = null

    /** Main-thread timers; not the grid's own queue, which holds posts until it is attached. */
    private val handler = Handler(Looper.getMainLooper())
    private var entryIds: List<String> = emptyList()

    /** The dragged entry, until it is dropped or the drag ends. */
    private var dragged: String? = null
    private var reorderable = false

    /** Cell the dragged entry came from on this page, or -1 when it is not on it. */
    private var from = -1

    /** Cell the dragged entry would land in now. */
    private var slot = -1
    private var pendingSlot = -1
    private var restAlpha = 1f

    /** A dropped entry waiting for the rebuild that shows it in its new cell. */
    private var settling: String? = null

    /** The settling entry already shows and grows at its new cell, so the rebuild must not restart it. */
    private var landed = false

    private val shift = Runnable {
        if (pendingSlot >= 0 && pendingSlot != slot) {
            slot = pendingSlot
            grid?.let(Haptics::tick) // the others made room: a new landing cell
            layoutPreview(animate = true)
        }
    }
    private val settleTimeout = Runnable {
        // Nothing was stored (or nothing moved): everything returns to its cell.
        if (settling == null) return@Runnable
        settling = null
        glideBack(settleSource = true)
        landed = false
    }

    val isDragging: Boolean get() = dragged != null

    /**
     * [grid]'s children were updated, one per id in [ids] (views of entries that stayed may be
     * reused, still carrying preview offsets). Plays the drop bounce on a settling entry, or keeps
     * an ongoing drag's lifted entry out of its cell. Returns the id of the entry whose view is
     * animated here, so the caller leaves it alone, or null.
     */
    fun bind(grid: FolderGridLayout, ids: List<String>): String? {
        this.grid = grid
        entryIds = ids
        settling?.let { id ->
            // Shown in its new cell (or on another page): its preview offset no longer applies.
            settling = null
            handler.removeCallbacks(settleTimeout)
            val wasLanded = landed
            landed = false
            from = -1
            slot = -1
            grid.getChildAt(ids.indexOf(id))?.let { view ->
                if (wasLanded) {
                    // Already visible and growing where the rebuild puts it: only the offset is dropped,
                    // which is the same place on screen, and the running grow carries on.
                    view.translationX = 0f
                    view.translationY = 0f
                } else {
                    view.animate().cancel()
                    view.translationX = 0f
                    view.translationY = 0f
                    bounceIn(view, restAlpha)
                }
            }
            return id
        }
        val id = dragged ?: return null
        handler.removeCallbacks(shift)
        from = ids.indexOf(id)
        slot = from
        pendingSlot = -1
        grid.getChildAt(from)?.let { source ->
            // A reused view may already be the hidden, lifted one: keep the alpha it rests at.
            if (source.alpha != 0f) restAlpha = source.alpha
            source.translationX = 0f
            source.translationY = 0f
            source.alpha = 0f
        }
        return id
    }

    /** A drag of [entryId] started in this folder; [canReorder] is false for alphabetical folders. */
    fun start(entryId: String, canReorder: Boolean) {
        val g = grid ?: return
        if (settling != null) {
            // A new drag begins before the last drop was stored: settle that entry back first.
            settling = null
            handler.removeCallbacks(settleTimeout)
            glideBack(settleSource = true)
        }
        landed = false
        handler.removeCallbacks(settleTimeout)
        dragged = entryId
        reorderable = canReorder
        from = entryIds.indexOf(entryId)
        slot = from
        pendingSlot = -1
        val source = g.getChildAt(from) ?: return
        source.animate().cancel()
        restAlpha = source.alpha
        animate(source, source.translationX, source.translationY, LIFT_SCALE, 0f, LIFT_MS, DECELERATE)
    }

    /** The finger is over cell [index] of this page (null: over no cell); shifts entries after a short hover. */
    fun hover(index: Int?) {
        val g = grid ?: return
        if (dragged == null || !reorderable || from < 0 || index == null) return
        val target = index.coerceIn(0, g.childCount - 1)
        if (target == pendingSlot) return
        handler.removeCallbacks(shift)
        pendingSlot = target
        if (target != slot) handler.postDelayed(shift, REORDER_DELAY_MS) else pendingSlot = -1
    }

    /**
     * Dropped over cell [index] of this page. Returns the cell to store the entry in, or null when
     * the order stays as it is (the entry then settles back at once).
     */
    fun drop(index: Int?): Int? {
        val g = grid ?: return null
        val id = dragged ?: return null
        handler.removeCallbacks(shift)
        dragged = null
        if (reorderable && from >= 0 && index != null) {
            val target = index.coerceIn(0, g.childCount - 1)
            if (target != slot) {
                slot = target
                layoutPreview(animate = true)
            }
        }
        if (!reorderable || from < 0 || slot == from) {
            glideBack(settleSource = true)
            return null
        }
        settling = id
        // The shadow ends now but the stored order arrives later: land the entry at its new cell at
        // once, so the cell is never empty in between.
        g.getChildAt(from)?.let { view ->
            land(view, restAlpha)
            landed = true
        }
        handler.postDelayed(settleTimeout, SETTLE_WAIT_MS)
        return slot
    }

    /** The drag ended without a drop here: everything returns to its cell. */
    fun end() {
        if (grid == null || dragged == null) return
        handler.removeCallbacks(shift)
        dragged = null
        glideBack(settleSource = true)
    }

    /** The panel is gone; forget its views. */
    fun clear() {
        handler.removeCallbacksAndMessages(null)
        if (landed) grid?.getChildAt(from)?.let { view ->
            view.animate().cancel()
            view.translationX = 0f
            view.translationY = 0f
            view.scaleX = 1f
            view.scaleY = 1f
            view.alpha = restAlpha
        }
        landed = false
        grid = null
        entryIds = emptyList()
        dragged = null
        settling = null
        from = -1
        slot = -1
        pendingSlot = -1
    }

    /** Moves every entry to the cell it takes with the dragged entry in [slot]. */
    private fun layoutPreview(animate: Boolean) {
        val g = grid ?: return
        val cells = previewCells(g.childCount, from, slot)
        for (child in 0 until g.childCount) {
            val view = g.getChildAt(child)
            val cell = g.getChildAt(cells[child]) ?: continue
            val tx = (cell.left - view.left).toFloat()
            val ty = (cell.top - view.top).toFloat()
            if (child == from || !animate) {
                view.translationX = tx
                view.translationY = ty
            } else {
                animate(view, tx, ty, 1f, view.alpha, SHIFT_MS, SHIFT)
            }
        }
    }

    private fun glideBack(settleSource: Boolean) {
        val g = grid ?: return
        for (child in 0 until g.childCount) {
            val view = g.getChildAt(child)
            if (child == from && settleSource && landed) {
                // It was shown at the new cell while waiting for the stored order: glide back, never jump.
                animate(view, 0f, 0f, 1f, restAlpha, SHIFT_MS, SHIFT)
            } else if (child == from && settleSource) {
                view.translationX = 0f
                view.translationY = 0f
                bounceIn(view, restAlpha)
            } else if (view.translationX != 0f || view.translationY != 0f) {
                animate(view, 0f, 0f, 1f, view.alpha, SHIFT_MS, SHIFT)
            }
        }
        from = -1
        slot = -1
        pendingSlot = -1
    }

    /**
     * The dropped [view] appears at its current offset (the new cell) at its resting alpha and grows
     * from the lifted size. Offsets are left to the caller: the rebuild resets them to the same spot.
     */
    private fun land(view: View, alpha: Float) {
        view.animate().cancel()
        view.alpha = alpha
        val ms = motion.duration(SETTLE_MS)
        if (ms <= 0L || !view.isAttachedToWindow) {
            view.scaleX = 1f
            view.scaleY = 1f
            return
        }
        ItemMotion.bounceIn(view, ms)
    }

    /** The dropped entry pops into its cell. */
    private fun bounceIn(view: View, alpha: Float = view.alpha) {
        view.animate().cancel()
        val ms = motion.duration(SETTLE_MS)
        if (ms <= 0L) {
            view.scaleX = 1f
            view.scaleY = 1f
            view.alpha = alpha
            return
        }
        // Visible from the first frame: the drag shadow has just ended, so nothing may be blank.
        view.alpha = alpha
        if (!view.isAttachedToWindow) {
            view.scaleX = 1f
            view.scaleY = 1f
            return
        }
        ItemMotion.bounceIn(view, ms)
    }

    private fun animate(view: View, tx: Float, ty: Float, scale: Float, alpha: Float, baseMs: Long, interpolator: TimeInterpolator) {
        val ms = motion.duration(baseMs)
        view.animate().cancel()
        if (ms <= 0L || !view.isAttachedToWindow) {
            view.translationX = tx
            view.translationY = ty
            view.scaleX = scale
            view.scaleY = scale
            view.alpha = alpha
            return
        }
        view.animate().translationX(tx).translationY(ty).scaleX(scale).scaleY(scale).alpha(alpha)
            .setInterpolator(interpolator).setDuration(ms).start()
    }

    companion object {
        /** How long the finger rests over a cell before the others make room, so passing over does not shuffle. */
        const val REORDER_DELAY_MS = 90L
        const val LIFT_MS = 120L
        const val SHIFT_MS = 220L
        const val SETTLE_MS = 320L

        /** Longest wait for the stored order before the entries settle back where they were. */
        const val SETTLE_WAIT_MS = 600L
        const val LIFT_SCALE = 0.6f
        private val DECELERATE = DecelerateInterpolator()
        private val SHIFT = OvershootInterpolator(0.8f)

        /**
         * For each of [count] cells, the cell its entry shows in while the entry from [from] is held
         * over [slot]: entries between the two shift one cell toward [from].
         */
        fun previewCells(count: Int, from: Int, slot: Int): IntArray {
            val cells = IntArray(count) { it }
            if (from !in 0 until count || slot !in 0 until count) return cells
            val order = (0 until count).toMutableList().apply { add(slot, removeAt(from)) }
            order.forEachIndexed { cell, child -> cells[child] = cell }
            return cells
        }
    }
}
