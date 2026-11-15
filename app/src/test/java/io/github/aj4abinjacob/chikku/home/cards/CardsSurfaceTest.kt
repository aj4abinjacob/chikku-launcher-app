package io.github.aj4abinjacob.chikku.home.cards

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.cards.AutomationCard
import io.github.aj4abinjacob.chikku.home.TestHomeApplication
import io.github.aj4abinjacob.chikku.theme.Motion
import io.github.aj4abinjacob.chikku.theme.SurfaceColors
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestHomeApplication::class)
class CardsSurfaceTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun layout(root: FrameLayout, width: Int, height: Int) {
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, width, height)
    }

    @Test fun panelLeavesRoomForSystemBarsAndCutouts() {
        val root = FrameLayout(context)
        layout(root, 400, 700)
        val surface = CardsSurface(root, {}, {}, { _, _, _ -> })
        surface.render(emptyList(), SurfaceColors.LIGHT, Motion.DEFAULT)
        val overlay = root.findViewWithTag<FrameLayout>("cards_panel")
        ViewCompat.dispatchApplyWindowInsets(overlay, WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, 28, 0, 36))
            .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.of(12, 0, 0, 0)).build())
        layout(root, 400, 700)
        val panel = overlay.getChildAt(0)
        assertThat(panel.top).isAtLeast(28)
        assertThat(panel.left).isAtLeast(12)
        assertThat(panel.bottom).isAtMost(700 - 36)
        surface.close()
    }

    @Test fun unequalTopAndBottomInsetsKeepTheFullHeightPanelInsideTheSafeArea() {
        assertSafePanelBounds(widthDp = 400, leftDp = 36, rightDp = 0)
    }

    @Test fun boundedTabletPanelRemainsInsideALargeAsymmetricSideCutout() {
        assertSafePanelBounds(widthDp = 1_000, leftDp = 350, rightDp = 80)
    }

    private fun assertSafePanelBounds(widthDp: Int, leftDp: Int, rightDp: Int) {
        fun px(dp: Int) = (dp * context.resources.displayMetrics.density).toInt()
        val width = px(widthDp)
        val height = px(700)
        val left = px(leftDp)
        val right = px(rightDp)
        val top = px(80)
        val bottom = px(20)
        val root = FrameLayout(context)
        layout(root, width, height)
        val surface = CardsSurface(root, {}, {}, { _, _, _ -> })
        surface.render(emptyList(), SurfaceColors.LIGHT, Motion.DEFAULT)
        val overlay = root.findViewWithTag<FrameLayout>("cards_panel")
        ViewCompat.dispatchApplyWindowInsets(overlay, WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, top, right, bottom))
            .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.of(left, 0, 0, 0)).build())
        // The width listener may constrain the bounded panel once the overlay is measured.
        layout(root, width, height)
        layout(root, width, height)
        val panel = overlay.getChildAt(0)
        assertThat(panel.top).isAtLeast(top)
        assertThat(panel.bottom).isAtMost(height - bottom)
        assertThat(panel.left).isAtLeast(left)
        assertThat(panel.right).isAtMost(width - right)
        assertThat(panel.width).isGreaterThan(0)
        surface.close()
    }

    @Test fun largeTextLandscapeKeepsButtonsTouchableAndContentScrollable() {
        RuntimeEnvironment.setFontScale(2f)
        try {
            val root = FrameLayout(context)
            layout(root, 700, 320)
            val surface = CardsSurface(root, {}, {}, { _, _, _ -> })
            val card = AutomationCard("large", "A longer card title with large text")
            surface.render(listOf(CardSurfaceItem(card, "Open featured application", (0..5).map { it to "Action button ${it + 1}" })),
                SurfaceColors.DARK, Motion.DEFAULT)
            layout(root, 700, 320)
            val minimum = (48 * context.resources.displayMetrics.density).toInt()
            (0..5).forEach { index ->
                assertThat(root.findViewWithTag<View>("card_action:large:$index").height).isAtLeast(minimum)
            }
            val overlay = root.findViewWithTag<FrameLayout>("cards_panel")
            assertThat(overlay.getChildAt(0).height).isAtMost(320)
            surface.close()
        } finally { RuntimeEnvironment.setFontScale(1f) }
    }

    @Test fun cardsShowWhyTheyAreShowingAndEmptyPanelOffersEditing() {
        val root = FrameLayout(context)
        layout(root, 400, 700)
        var edited = false
        val surface = CardsSurface(root, {}, { edited = true }, { _, _, _ -> })
        surface.render(listOf(CardSurfaceItem(AutomationCard("work", "Work"), null, listOf(0 to "Open search"), "Weekdays")),
            SurfaceColors.LIGHT, Motion.DEFAULT)
        val card = root.findViewWithTag<android.view.ViewGroup>("card:work")
        val texts = (0 until card.childCount).mapNotNull { (card.getChildAt(it) as? android.widget.TextView)?.text?.toString() }
        assertThat(texts).containsAtLeast("Work", "Weekdays").inOrder()
        assertThat(root.findViewWithTag<View>("card_action:work:0").contentDescription.toString()).isEqualTo("Open search")

        surface.render(emptyList(), SurfaceColors.DARK, Motion.DEFAULT)
        val editLabel = context.getString(io.github.aj4abinjacob.chikku.R.string.cards_surface_edit)
        val found = ArrayList<View>()
        root.findViewsWithText(found, editLabel, View.FIND_VIEWS_WITH_TEXT)
        found.single().performClick()
        assertThat(edited).isTrue()
        surface.close()
    }

    @Test fun unchangedCardsKeepTheirViewsAndOnlyChangedOnesAreDiscarded() {
        val root = FrameLayout(context)
        layout(root, 400, 700)
        val discarded = mutableListOf<String>()
        val surface = CardsSurface(root, {}, {}, { _, _, _ -> }, onCardDiscarded = { discarded += it })
        val a = CardSurfaceItem(AutomationCard("a", "A"), null, listOf(0 to "One"))
        val b = CardSurfaceItem(AutomationCard("b", "B"), null, listOf(0 to "Two"))
        surface.render(listOf(a, b), SurfaceColors.LIGHT, Motion.DEFAULT)
        val firstA = root.findViewWithTag<View>("card:a")
        val firstB = root.findViewWithTag<View>("card:b")

        val changedB = CardSurfaceItem(AutomationCard("b", "B renamed"), null, listOf(0 to "Two"))
        surface.render(listOf(a, changedB), SurfaceColors.LIGHT, Motion.DEFAULT)
        assertThat(root.findViewWithTag<View>("card:a")).isSameInstanceAs(firstA)
        assertThat(root.findViewWithTag<View>("card:b")).isNotSameInstanceAs(firstB)
        assertThat(discarded).containsExactly("b")

        surface.render(listOf(changedB), SurfaceColors.LIGHT, Motion.DEFAULT)
        assertThat(root.findViewWithTag<View>("card:a")).isNull()
        assertThat(discarded).containsExactly("b", "a").inOrder()
        surface.close()
    }

    @Test fun dismissWithoutAnimationRemovesThePanelAndReportsOnce() {
        val root = FrameLayout(context)
        layout(root, 400, 700)
        val surface = CardsSurface(root, {}, {}, { _, _, _ -> })
        surface.render(emptyList(), SurfaceColors.LIGHT, Motion(io.github.aj4abinjacob.chikku.core.model.settings.AnimationSpeed.NONE))
        var closed = 0
        surface.close(animate = true) { closed++ }
        surface.close()
        assertThat(root.findViewWithTag<View>("cards_panel")).isNull()
        assertThat(closed).isEqualTo(1)
    }
}
