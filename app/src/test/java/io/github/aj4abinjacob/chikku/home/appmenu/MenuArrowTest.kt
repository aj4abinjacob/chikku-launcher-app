package io.github.aj4abinjacob.chikku.home.appmenu

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A long-press menu stacks its cards nearest-the-item first and aims one arrow at the icon. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MenuArrowTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun actionRowsAreEvenAndAtMostFourWide() {
        assertThat((1..8).map { actionColumns(it) }).containsExactly(1, 2, 3, 4, 3, 3, 4, 4).inOrder()
    }

    @Test
    fun shorterActionRowsAskForNarrowerButtons() {
        assertThat((1..4).map { preferredCellDp(it) }).containsExactly(64f, 64f, 66f, 76f).inOrder()
    }

    @Test
    fun arrowAimsAtTheIconButStaysOffTheCorners() {
        assertThat(arrowLeft(target = 300f, width = 600f, inset = 40f, arrowWidth = 60f)).isEqualTo(270f)
        assertThat(arrowLeft(target = 10f, width = 600f, inset = 40f, arrowWidth = 60f)).isEqualTo(40f)
        assertThat(arrowLeft(target = 590f, width = 600f, inset = 40f, arrowWidth = 60f)).isEqualTo(500f)
        // Too narrow for both insets: centred.
        assertThat(arrowLeft(target = 0f, width = 100f, inset = 40f, arrowWidth = 60f)).isEqualTo(20f)
    }

    @Test
    fun theArrowStaysOnTheFlatPartOfTheCardEdge() {
        assertThat(arrowCornerInset(0f)).isEqualTo(0f)
        assertThat(arrowCornerInset(60f)).isEqualTo(60f)
        assertThat(arrowCornerInset(-1f)).isEqualTo(0f)
    }

    private fun card(name: String) = LinearLayout(context).apply {
        tag = name
        background = GradientDrawable().apply { cornerRadius = 30f }
    }

    @Test
    fun cardsRunFromTheItemOutwardWithTheArrowOnTheNearestOne() {
        val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val shortcuts = card("shortcuts")
        val actions = card("actions")
        val arrow = menuArrow(context, 0xFF303030.toInt(), null, 1)

        arrangeMenu(content, listOf(shortcuts, actions), arrow, above = true, arrowCenterX = 120)
        assertThat(children(content)).containsExactly(actions, shortcuts, arrow).inOrder()
        assertThat(arrow.pointsDown).isTrue()
        assertThat(arrow.cornerRadius).isEqualTo(30f)
        assertThat(arrow.targetX).isEqualTo(120f)
        assertThat(arrow.elevation).isGreaterThan(shortcuts.elevation)

        arrangeMenu(content, listOf(shortcuts, actions), arrow, above = false, arrowCenterX = null)
        assertThat(children(content)).containsExactly(arrow, shortcuts, actions).inOrder()
        assertThat(arrow.pointsDown).isFalse()
        assertThat(arrow.targetX).isNull()
        // Full width, so a column that wraps its content still sizes to the cards.
        assertThat(arrow.layoutParams.width).isEqualTo(LinearLayout.LayoutParams.MATCH_PARENT)
    }

    @Test
    fun aWrappingColumnSizesToItsCardsNotToTheArrow() {
        val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val wide = card("wide").apply { minimumWidth = 500 }
        arrangeMenu(content, listOf(wide), menuArrow(context, 0, null, 1), above = true, arrowCenterX = 0)
        content.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        assertThat(content.measuredWidth).isAtLeast(500)
    }

    private fun children(group: LinearLayout) = (0 until group.childCount).map(group::getChildAt)
}
