package io.github.aj4abinjacob.chikku.home

import android.app.Activity
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.FrameLayout
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.GridSpec
import io.github.aj4abinjacob.chikku.core.model.settings.AnimationSpeed
import io.github.aj4abinjacob.chikku.theme.Motion
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/** Cell moves, adds, removes and dock page changes animate and always end with clean views. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestHomeApplication::class)
class CellLayoutMotionTest {
    private class Item(val id: String, val x: Int, val y: Int)

    private fun CellLayout.bindItems(items: List<Item>) = bind(
        items = items,
        id = { it.id },
        cellOf = { CellLayout.LayoutParams(it.x, it.y) },
        create = { View(context) },
        update = { _, _ -> },
        recycle = {},
    )

    private fun layout(motion: Motion, block: (CellLayout) -> Unit) {
        val host = Robolectric.buildActivity(Activity::class.java).setup().visible()
        val context = ContextThemeWrapper(host.get(), R.style.Theme_Chikku_Home)
        val layout = CellLayout(context).apply { grid = GridSpec(4, 2); this.motion = motion }
        host.get().setContentView(FrameLayout(context).apply { addView(layout, FrameLayout.LayoutParams(400, 200)) })
        layout.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY))
        layout.layout(0, 0, 400, 200)
        try {
            block(layout)
        } finally {
            host.pause().stop().destroy()
        }
    }

    private fun relayout(layout: CellLayout) {
        layout.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY))
        layout.layout(0, 0, 400, 200)
    }

    private fun settle() = shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))

    @Test fun aMovedItemStartsAtItsOldCellAndGlidesWithoutLeavingAnOffset() = layout(Motion.DEFAULT) { layout ->
        layout.bindItems(listOf(Item("a", 0, 0)))
        relayout(layout)
        val view = layout.getChildAt(0)
        assertThat(view.left).isEqualTo(0)

        layout.bindItems(listOf(Item("a", 2, 1)))
        relayout(layout)
        assertThat(view.left).isEqualTo(200)
        assertThat(view.translationX).isEqualTo(-200f) // visually still at the old cell
        assertThat(view.translationY).isEqualTo(-100f)

        settle()
        assertThat(view.translationX).isEqualTo(0f)
        assertThat(view.translationY).isEqualTo(0f)
    }

    @Test fun withAnimationsOffAMovedItemJumpsWithNoOffset() = layout(Motion(AnimationSpeed.NONE)) { layout ->
        layout.bindItems(listOf(Item("a", 0, 0)))
        relayout(layout)
        layout.bindItems(listOf(Item("a", 2, 1)))
        relayout(layout)
        assertThat(layout.getChildAt(0).translationX).isEqualTo(0f)
    }

    @Test fun addedItemsFadeInAndEndFullyOpaque() = layout(Motion.DEFAULT) { layout ->
        layout.bindItems(listOf(Item("a", 0, 0)))
        relayout(layout)
        layout.bindItems(listOf(Item("a", 0, 0), Item("b", 1, 0)))
        val added = layout.itemView("b")!!
        assertThat(added.alpha).isEqualTo(0f)
        settle()
        assertThat(added.alpha).isEqualTo(1f)
    }

    @Test fun detachingMidMotionLeavesNoStrayAlphaOrTranslation() = layout(Motion.DEFAULT) { layout ->
        layout.bindItems(listOf(Item("a", 0, 0)))
        relayout(layout)
        layout.bindItems(listOf(Item("a", 2, 1), Item("b", 1, 0)))
        relayout(layout)
        (layout.parent as FrameLayout).removeView(layout)
        for (i in 0 until layout.childCount) {
            assertThat(layout.getChildAt(i).alpha).isEqualTo(1f)
            assertThat(layout.getChildAt(i).translationX).isEqualTo(0f)
        }
    }

    @Test fun dockPageShiftEndsWithTheNewColumnsShownCleanly() = layout(Motion.DEFAULT) { layout ->
        layout.grid = GridSpec(2, 1)
        layout.bindItems(listOf(Item("a", 0, 0), Item("b", 1, 0), Item("c", 2, 0), Item("d", 3, 0)))
        relayout(layout)
        layout.shiftColumnOffset(2, forward = true)
        assertThat(layout.columnOffset).isEqualTo(2)
        relayout(layout)
        settle()
        val c = layout.itemView("c")!!
        assertThat(c.visibility).isEqualTo(View.VISIBLE)
        assertThat(c.alpha).isEqualTo(1f)
        assertThat(c.translationX).isEqualTo(0f)
        assertThat(layout.itemView("a")!!.visibility).isEqualTo(View.GONE)
    }
}
