package io.github.aj4abinjacob.chikku.home.appmenu

import android.content.Context
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Every launcher action is a chip; a short last row is spread across the card, never turned into wide bars. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MenuActionGridChipsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun grid(count: Int): Pair<MenuActionGrid, List<View>> {
        val grid = MenuActionGrid(context)
        val views = grid.addActions((1..count).map { GridAction("Action $it", R.drawable.ic_menu_edit, 0xFF888888.toInt(), null) })
        val width = (320 * context.resources.displayMetrics.density).toInt()
        grid.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        grid.layout(0, 0, grid.measuredWidth, grid.measuredHeight)
        return grid to views
    }

    @Test
    fun fiveActionsAreThreeThenTwoChipsAndTheLastRowSpansTheCard() {
        val (grid, views) = grid(5)
        assertThat(views.none { it.tag == "action-bar" }).isTrue()
        assertThat(views.map { it.top }.distinct()).hasSize(2)
        assertThat(views[3].left).isEqualTo(0)
        assertThat(views[4].right).isEqualTo(grid.measuredWidth)
    }
}
