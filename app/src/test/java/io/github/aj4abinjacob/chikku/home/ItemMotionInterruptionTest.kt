package io.github.aj4abinjacob.chikku.home

import android.app.Activity
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.FrameLayout
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.GridSpec
import io.github.aj4abinjacob.chikku.home.folder.FolderGridLayout
import io.github.aj4abinjacob.chikku.home.folder.FolderGridMotion
import io.github.aj4abinjacob.chikku.theme.Motion
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/**
 * An item animation interrupted by the next bind, layout, cancel or detach must never leave a view
 * faded, offset, scaled or hidden: both the desktop/dock grid and the open folder's grid.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestHomeApplication::class)
class ItemMotionInterruptionTest {
    private class Item(val id: String, val x: Int, val y: Int)

    private fun View.assertAtRest() {
        assertThat(alpha).isEqualTo(1f)
        assertThat(scaleX).isEqualTo(1f)
        assertThat(scaleY).isEqualTo(1f)
        assertThat(translationX).isEqualTo(0f)
        assertThat(translationY).isEqualTo(0f)
        assertThat(visibility).isEqualTo(View.VISIBLE)
    }

    private fun idle(ms: Long) = shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    private fun measureAndLayout(view: View, w: Int, h: Int) {
        view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, w, h)
    }

    // --- CellLayout ---

    private fun cells(icons: Boolean = false, block: (CellLayout, (List<Item>) -> Unit, () -> Unit) -> Unit) {
        val host = Robolectric.buildActivity(Activity::class.java).setup().visible()
        val context = ContextThemeWrapper(host.get(), R.style.Theme_Chikku_Home)
        val layout = CellLayout(context).apply { grid = GridSpec(4, 2); motion = Motion.DEFAULT }
        host.get().setContentView(FrameLayout(context).apply { addView(layout, FrameLayout.LayoutParams(400, 200)) })
        measureAndLayout(layout, 400, 200)
        val bind = { items: List<Item> ->
            layout.bind(
                items = items,
                id = { it.id },
                cellOf = { CellLayout.LayoutParams(it.x, it.y) },
                create = { if (icons) AppIconView(context, AppIconView.Style.WORKSPACE) else View(context) },
                update = { _, _ -> },
                recycle = {},
            )
        }
        try {
            block(layout, bind) { measureAndLayout(layout, 400, 200) }
        } finally {
            host.pause().stop().destroy()
        }
    }

    @Test fun aSecondBindMidGlideAndFadeInEndsEveryChildAtRest() = cells { layout, bind, relayout ->
        bind(listOf(Item("a", 0, 0)))
        relayout()
        bind(listOf(Item("a", 2, 1), Item("b", 1, 0)))
        relayout()
        val a = layout.itemView("a")!!
        val b = layout.itemView("b")!!
        assertThat(a.translationX).isNotEqualTo(0f)
        assertThat(b.alpha).isLessThan(1f)

        bind(listOf(Item("a", 3, 0), Item("b", 0, 1), Item("c", 1, 1)))
        relayout()
        idle(2000)
        for (id in listOf("a", "b", "c")) layout.itemView(id)!!.assertAtRest()
    }

    @Test fun forgettingTheBindingMidMotionLeavesChildrenAtRestAtOnce() = cells { layout, bind, relayout ->
        bind(listOf(Item("a", 0, 0)))
        relayout()
        bind(listOf(Item("a", 2, 1), Item("b", 1, 0)))
        relayout()
        layout.forgetBinding()
        // Nothing may wait for a frame: the views are about to be reused.
        for (i in 0 until layout.childCount) layout.getChildAt(i).assertAtRest()
        idle(2000)
        for (i in 0 until layout.childCount) layout.getChildAt(i).assertAtRest()
    }

    @Test fun anItemRemovedMidFadeLeavesNoStrayViewInTheOverlay() = cells(icons = true) { layout, bind, relayout ->
        bind(listOf(Item("a", 0, 0), Item("b", 1, 0)))
        relayout()
        val removed = layout.itemView("b")!!
        bind(listOf(Item("a", 0, 0)))
        // The fade-out ghost lives in the overlay, which childCount does not count; while it fades its
        // parent is the overlay's internal group, so a leak shows as a non-null parent after cleanup.
        assertThat(removed.parent).isNotNull()
        layout.forgetBinding()
        idle(2000)
        assertThat(removed.parent).isNull()
        assertThat(layout.childCount).isEqualTo(1)
        layout.getChildAt(0).assertAtRest()
    }

    @Test fun aFadeOutCleansUpItsOwnOverlayViewWithoutAnyForgetBinding() = cells(icons = true) { layout, bind, relayout ->
        bind(listOf(Item("a", 0, 0), Item("b", 1, 0)))
        relayout()
        val removed = layout.itemView("b")!!
        bind(listOf(Item("a", 0, 0)))
        assertThat(removed.parent).isNotNull() // the ghost is fading in the overlay
        // Only the fade-out's own end action may remove it: no forgetBinding(), no further bind.
        idle(2000)
        assertThat(removed.parent).isNull()
        assertThat(removed.alpha).isEqualTo(1f) // restored for reuse
    }

    @Test fun emphasisDuringAFadeInLandsTheFadeAtFullAlphaAndTheItemScaled() = cells { layout, bind, relayout ->
        bind(listOf(Item("a", 0, 0)))
        relayout()
        bind(listOf(Item("a", 0, 0), Item("b", 1, 0)))
        relayout()
        val b = layout.itemView("b")!!
        assertThat(b.alpha).isLessThan(1f) // still fading in
        layout.setDropHighlight(Cell(1, 0), hint = CellLayout.DropHint.FOLDER, emphasisId = "b")
        idle(2000)
        assertThat(b.alpha).isEqualTo(1f)
        assertThat(b.scaleX).isWithin(0.001f).of(1.12f)
        // Dropping the emphasis returns the item to rest.
        layout.setDropHighlight(null)
        idle(2000)
        b.assertAtRest()
    }

    @Test fun anItemRemovedDuringItsFadeInLeavesNoPendingFadeBehind() = cells(icons = true) { layout, bind, relayout ->
        bind(listOf(Item("a", 0, 0)))
        relayout()
        bind(listOf(Item("a", 0, 0), Item("b", 1, 0)))
        relayout()
        val b = layout.itemView("b")!!
        assertThat(b.alpha).isLessThan(1f) // mid fade-in
        bind(listOf(Item("a", 0, 0)))
        // The fade-in is no longer pending for the removed view.
        assertThat(layout.forgetFadeIn(b)).isNull()
        // A later bind that moves the remaining item must not resurrect or touch the removed view.
        bind(listOf(Item("a", 2, 1)))
        relayout()
        idle(2000)
        assertThat(b.parent).isNull()
        assertThat(b.alpha).isEqualTo(1f) // its resting alpha, not the part-way value it was removed at
        layout.itemView("a")!!.assertAtRest()
        assertThat(layout.childCount).isEqualTo(1)
    }

    // --- FolderGridMotion ---

    private fun folder(block: (FolderGridLayout, FolderGridMotion) -> Unit) {
        val host = Robolectric.buildActivity(Activity::class.java).setup().visible()
        val grid = FolderGridLayout(host.get(), 100, 100).apply {
            columns = 2
            repeat(4) { addView(View(context)) }
        }
        host.get().setContentView(FrameLayout(host.get()).apply { addView(grid, FrameLayout.LayoutParams(200, 200)) })
        measureAndLayout(grid, 200, 200)
        try {
            block(grid, FolderGridMotion())
        } finally {
            host.pause().stop().destroy()
        }
    }

    /** Swaps the first two children, adds one and plays the change, so a glide and a pop-in run. */
    private fun FolderGridLayout.startChange(motion: FolderGridMotion): View {
        val before = motion.capture(this)
        val first = getChildAt(0)
        removeView(first)
        addView(first, 1)
        val added = View(context)
        motion.prepareEnter(this, added, pageDirection = 0)
        addView(added)
        measureAndLayout(this, 200, 200)
        motion.play(this, before, listOf(added), skip = null, animate = true)
        viewTreeObserver.dispatchOnPreDraw()
        return added
    }

    @Test fun cancelAllMidGlideAndPopInLeavesEveryChildAtRest() = folder { grid, motion ->
        assertThat(motion.canAnimate(grid)).isTrue()
        val added = grid.startChange(motion)
        assertThat(added.alpha).isLessThan(1f)
        motion.cancelAll()
        for (i in 0 until grid.childCount) grid.getChildAt(i).assertAtRest()
        idle(2000)
        for (i in 0 until grid.childCount) grid.getChildAt(i).assertAtRest()
    }

    @Test fun aSecondChangeMidAnimationEndsEveryChildAtRest() = folder { grid, motion ->
        val added = grid.startChange(motion)
        motion.finishStale(grid) // what the next change does before it is planned
        // Entering views are ended at rest; glides keep going (the next change continues from them).
        assertThat(added.alpha).isEqualTo(1f)
        assertThat(added.scaleX).isEqualTo(1f)
        grid.startChange(motion)
        idle(2000)
        for (i in 0 until grid.childCount) grid.getChildAt(i).assertAtRest()
    }
}
