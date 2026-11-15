package io.github.aj4abinjacob.chikku.search

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.settings.HomeSearchBarButtonSlot
import io.github.aj4abinjacob.chikku.core.model.settings.HomeSearchBarPosition
import io.github.aj4abinjacob.chikku.core.model.settings.HomeSettings
import io.github.aj4abinjacob.chikku.core.model.settings.SearchSettings
import io.github.aj4abinjacob.chikku.core.model.settings.WebSearchEngine
import io.github.aj4abinjacob.chikku.home.TestHomeApplication
import io.github.aj4abinjacob.chikku.settings.SettingsSearchIndex
import io.github.aj4abinjacob.chikku.settings.SettingsSection
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestHomeApplication::class)
class SearchBarSizingTest {
    @Test
    fun minimalStyleKeepsTouchTargetAndLocalSearchThenRestoresOriginalMarginsAndProvider() {
        withBars { fixture ->
            val home = HomeSettings(showSearchBar = true, searchBarPosition = HomeSearchBarPosition.BELOW_DOCK)
            val minimal = SearchSettings(widgetStyle = io.github.aj4abinjacob.chikku.core.model.settings.HomeSearchBarStyle.MINIMAL,
                widgetHeightDp = 48, widgetHorizontalPaddingDp = 16, showWebSuggestionRow = false)
            fixture.entryPoints.render(home, minimal)
            fixture.layout(400)
            assertThat(fixture.bottom.height).isEqualTo(fixture.dp(48))
            val mark = fixture.bottom.minimalMark
            assertThat(mark.visibility).isEqualTo(View.VISIBLE)
            assertThat(mark.boundsIn(fixture.bottom).right).isAtMost(fixture.bottom.width - fixture.bottom.paddingRight)
            assertThat(fixture.bottom.contentDescription.toString()).isEqualTo(fixture.activity.getString(R.string.search_home_bar_description))
            assertThat((fixture.bottom.layoutParams as LinearLayout.LayoutParams).bottomMargin).isEqualTo(fixture.dp(21))
            fixture.entryPoints.render(home, SearchSettings())
            fixture.layout(400)
            assertThat(fixture.bottom.minimalMark.visibility).isEqualTo(View.GONE)
            assertThat(fixture.bottom.engine).isEqualTo(WebSearchEngine.DUCKDUCKGO)
            assertThat((fixture.bottom.layoutParams as LinearLayout.LayoutParams).marginStart).isEqualTo(fixture.dp(12))
        }
    }

    @Test
    fun bothPositionsUseIndependentSizeAndInsetsAndRecenterAfterWindowResize() {
        withBars { fixture ->
            val settings = SearchSettings(
                widgetWidthPercent = 65,
                widgetHeightDp = 80,
                widgetHorizontalPaddingDp = 24,
                widgetVerticalPaddingDp = 16,
            )
            for (direction in listOf(View.LAYOUT_DIRECTION_LTR, View.LAYOUT_DIRECTION_RTL)) {
                fixture.root.layoutDirection = direction
                for (position in listOf(HomeSearchBarPosition.TOP, HomeSearchBarPosition.BELOW_DOCK)) {
                    fixture.entryPoints.render(HomeSettings(showSearchBar = true, searchBarPosition = position), settings)
                    val bar = if (position == HomeSearchBarPosition.TOP) fixture.top else fixture.bottom
                    for (widthDp in listOf(400, 640, 320)) {
                        fixture.layout(widthDp)
                        val availableWidth = fixture.dp(widthDp) - fixture.root.paddingLeft -
                            fixture.root.paddingRight - 2 * fixture.dp(12)
                        assertThat(bar.width).isEqualTo(availableWidth * 65 / 100)
                        assertThat(bar.height).isEqualTo(fixture.dp(80))
                        assertThat(bar.paddingStart).isEqualTo(fixture.dp(24))
                        assertThat(bar.paddingEnd).isEqualTo(fixture.dp(24))
                        assertThat(bar.paddingTop).isEqualTo(fixture.dp(16))
                        assertThat(bar.paddingBottom).isEqualTo(fixture.dp(16))
                        assertThat(kotlin.math.abs(bar.left + bar.width / 2 - fixture.root.width / 2)).isAtMost(1)
                    }
                }
            }
        }
    }

    @Test
    fun changingSizeWithoutChangingPositionUpdatesVisibleBarAndResetRestoresDefaults() {
        withBars { fixture ->
            val home = HomeSettings(showSearchBar = true, searchBarPosition = HomeSearchBarPosition.TOP)
            fixture.entryPoints.render(home, SearchSettings(widgetWidthPercent = 40, widgetHeightDp = 48))
            fixture.layout(400)
            val narrowWidth = fixture.top.width
            fixture.entryPoints.render(home, SearchSettings(widgetWidthPercent = 90, widgetHeightDp = 96,
                widgetHorizontalPaddingDp = 0, widgetVerticalPaddingDp = 0))
            fixture.layout(400)
            assertThat(fixture.top.width).isGreaterThan(narrowWidth)
            assertThat(fixture.top.height).isEqualTo(fixture.dp(96))
            assertThat(fixture.top.paddingStart).isEqualTo(0)
            assertThat(fixture.top.paddingTop).isEqualTo(0)
            fixture.entryPoints.render(home, SearchSettings())
            fixture.layout(400)
            assertThat(fixture.top.width).isEqualTo(fixture.dp(344))
            assertThat(fixture.top.height).isEqualTo(fixture.dp(56))
            assertThat(fixture.top.paddingStart).isEqualTo(fixture.dp(20))
            assertThat(fixture.top.paddingTop).isEqualTo(fixture.dp(10))
        }
    }

    @Test
    fun largestPaddingStillKeepsBothMarksInsideSmallestCapsuleAndWebToggleReflows() {
        withBars { fixture ->
            val home = HomeSettings(showSearchBar = true, searchBarPosition = HomeSearchBarPosition.TOP)
            val search = SearchSettings(widgetWidthPercent = 40, widgetHeightDp = 48,
                widgetHorizontalPaddingDp = 48, widgetVerticalPaddingDp = 24)
            fixture.entryPoints.render(home, search)
            fixture.layout(320)
            val bar = fixture.top
            assertThat(bar.engine).isEqualTo(WebSearchEngine.DUCKDUCKGO)
            val glyph = bar.chikkuButton.getChildAt(0).boundsIn(bar)
            val provider = (bar.webButton.getChildAt(0) as ViewGroup).getChildAt(0).boundsIn(bar)
            for (icon in listOf(glyph, provider)) {
                assertThat(icon.left).isAtLeast(bar.paddingLeft)
                assertThat(icon.right).isAtMost(bar.width - bar.paddingRight)
                assertThat(icon.top).isAtLeast(bar.paddingTop)
                assertThat(icon.bottom).isAtMost(bar.height - bar.paddingBottom)
            }
            assertThat(glyph.right).isAtMost(provider.left)
            // Both buttons keep full-height targets of at least 48dp inside the bar.
            for (button in listOf(bar.chikkuButton, bar.webButton)) {
                assertThat(button.width).isAtLeast(fixture.dp(48))
                assertThat(button.height).isEqualTo(bar.height)
                assertThat(button.left).isAtLeast(0)
                assertThat(button.right).isAtMost(bar.width)
            }
            assertThat(bar.chikkuButton.right).isAtMost(bar.webButton.left)
            val paddingWithProvider = bar.paddingStart
            fixture.entryPoints.render(home, search.copy(showWebSuggestionRow = false, widgetWebButton = HomeSearchBarButtonSlot.HIDDEN))
            fixture.layout(320)
            assertThat(bar.webButton.parent).isNull()
            assertThat(bar.engine).isNull()
            assertThat(bar.paddingStart).isAtLeast(paddingWithProvider)
            assertThat(bar.contentDescription.toString()).isEqualTo(fixture.activity.getString(R.string.search_home_bar_description))
        }
    }

    @Test
    fun hiddenAndNonePositionsStayHiddenWhenSizingChanges() {
        withBars { fixture ->
            val sized = SearchSettings(widgetHeightDp = 96, widgetWidthPercent = 50)
            fixture.entryPoints.render(HomeSettings(showSearchBar = false, searchBarPosition = HomeSearchBarPosition.TOP), sized)
            fixture.layout(400)
            assertThat(fixture.top.visibility).isEqualTo(View.GONE)
            assertThat(fixture.bottom.visibility).isEqualTo(View.GONE)
            fixture.entryPoints.render(HomeSettings(showSearchBar = true, searchBarPosition = HomeSearchBarPosition.NONE), sized)
            fixture.layout(400)
            assertThat(fixture.top.visibility).isEqualTo(View.GONE)
            assertThat(fixture.bottom.visibility).isEqualTo(View.GONE)
        }
    }

    @Test
    fun dockGapChangesOutsideSpacingWithoutChangingCapsuleAndTopMargin() {
        withBars { fixture ->
            val home = HomeSettings(showSearchBar = true, searchBarPosition = HomeSearchBarPosition.BELOW_DOCK)
            for (gap in listOf(12, 64, 0, 24)) {
                fixture.entryPoints.render(home, SearchSettings(widgetDockGapDp = gap,
                    widgetVerticalPaddingDp = 24))
                fixture.layout(400)
                assertThat(fixture.bottom.top - fixture.dock.bottom).isEqualTo(fixture.dp(gap))
                assertThat(fixture.bottom.height).isEqualTo(fixture.dp(56))
                assertThat((fixture.top.layoutParams as LinearLayout.LayoutParams).topMargin).isEqualTo(fixture.dp(8))
            }
            fixture.entryPoints.render(home, SearchSettings())
            fixture.layout(400)
            assertThat(fixture.bottom.top - fixture.dock.bottom).isEqualTo(fixture.dp(12))
            fixture.entryPoints.render(home.copy(searchBarPosition = HomeSearchBarPosition.TOP),
                SearchSettings(widgetDockGapDp = 64))
            fixture.layout(400)
            assertThat(fixture.top.top).isEqualTo(fixture.dp(8))
            assertThat(fixture.bottom.visibility).isEqualTo(View.GONE)
        }
    }

    @Test
    fun sizingControlsAreDiscoverableThroughSettingsSearch() {
        withBars { fixture ->
            val entries = SettingsSearchIndex.entries(fixture.activity.resources, labsUnlocked = false)
            for (query in listOf("search bar width", "search bar height", "search horizontal padding", "search vertical padding", "space above search bar")) {
                val results = SettingsSearchIndex.search(entries, query)
                assertThat(results).isNotEmpty()
                assertThat(results.first().section).isEqualTo(SettingsSection.SEARCH)
            }
        }
    }

    private fun View.boundsIn(ancestor: View): android.graphics.Rect {
        var x = left
        var y = top
        var parent = parent as View
        while (parent !== ancestor) {
            x += parent.left
            y += parent.top
            parent = parent.parent as View
        }
        return android.graphics.Rect(x, y, x + width, y + height)
    }

    private fun withBars(test: (Fixture) -> Unit) {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            test(Fixture(controller.get()))
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private class Fixture(val activity: Activity) {
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), 0)
        }
        val top = bar(R.id.home_search_bar_top)
        val dock = View(activity).also {
            root.addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(90)))
        }
        val bottom = bar(R.id.home_search_bar_bottom)
        val entryPoints: HomeSearchEntryPoints

        init {
            root.addView(View(activity).apply { id = R.id.drawer_search_more })
            val input = EditText(activity)
            root.addView(input)
            activity.setContentView(root)
            entryPoints = HomeSearchEntryPoints(activity, input).also { it.bind() }
        }

        fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density + .5f).toInt()

        private fun bar(viewId: Int) = SearchWidgetView(activity).apply {
            id = viewId
            root.addView(this, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = if (viewId == R.id.home_search_bar_top) dp(8) else 0
                leftMargin = dp(12)
                rightMargin = dp(12)
            })
        }

        fun layout(widthDp: Int) {
            // Simulate ViewRoot's follow-up traversal after a child requests layout during layout.
            repeat(2) {
                root.forceLayout()
                root.measure(View.MeasureSpec.makeMeasureSpec(dp(widthDp), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(dp(600), View.MeasureSpec.EXACTLY))
                root.layout(0, 0, root.measuredWidth, root.measuredHeight)
            }
        }
    }
}
