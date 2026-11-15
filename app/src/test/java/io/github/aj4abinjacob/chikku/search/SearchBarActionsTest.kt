package io.github.aj4abinjacob.chikku.search

import android.app.Activity
import android.app.SearchManager
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.settings.HomeSearchBarButtonSlot
import io.github.aj4abinjacob.chikku.core.model.settings.HomeSearchBarPosition
import io.github.aj4abinjacob.chikku.core.model.settings.HomeSearchBarTapAction
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The home bar's own tap and its Chikku and web buttons, as placed and wired by HOME. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestHomeApplication::class)
class SearchBarActionsTest {
    private val home = HomeSettings(showSearchBar = true, searchBarPosition = HomeSearchBarPosition.TOP)

    @Test
    fun barOpensChikkuSearchByDefaultAndEachButtonKeepsItsOwnAction() {
        withBar { fixture ->
            fixture.render(SearchSettings(webEngine = WebSearchEngine.GOOGLE))
            fixture.bar.performClick()
            assertThat(fixture.nextStarted()?.component?.className).isEqualTo(SearchActivity::class.java.name)
            assertThat(fixture.handedOff).isEmpty()

            fixture.bar.webButton.performClick()
            assertThat(fixture.nextStarted()).isNull()
            val (googleApp, browser) = fixture.handedOff.single()
            assertThat(googleApp.action).isEqualTo(SearchManager.INTENT_ACTION_GLOBAL_SEARCH)
            assertThat(googleApp.`package`).isEqualTo("com.google.android.googlequicksearchbox")
            assertThat(browser.action).isEqualTo(Intent.ACTION_VIEW)
            assertThat(browser.dataString).isEqualTo("https://www.google.com/")
            assertThat(browser.hasCategory(Intent.CATEGORY_BROWSABLE)).isTrue()
        }
    }

    @Test
    fun webTapActionSendsTheBarToTheEngineWhileTheChikkuButtonStillOpensChikku() {
        withBar { fixture ->
            fixture.render(SearchSettings(widgetTapAction = HomeSearchBarTapAction.WEB_SEARCH, webEngine = WebSearchEngine.ECOSIA))
            fixture.bar.performClick()
            assertThat(fixture.nextStarted()).isNull()
            // Only Google has an app search screen to try first; other engines open their home page.
            assertThat(fixture.handedOff.single().map { it.dataString }).containsExactly("https://www.ecosia.org/")
            assertThat(fixture.bar.contentDescription.toString()).isEqualTo("Search the web with Ecosia")

            fixture.bar.chikkuButton.performClick()
            assertThat(fixture.nextStarted()?.component?.className).isEqualTo(SearchActivity::class.java.name)
            assertThat(fixture.handedOff).hasSize(1)
        }
    }

    @Test
    fun buttonsFollowTheirPlacementWithChikkuOutermostAndHiddenButtonsDetached() {
        withBar { fixture ->
            val bar = fixture.bar
            fun order() = (0 until bar.childCount).map {
                when (bar.getChildAt(it)) {
                    bar.chikkuButton -> "chikku"
                    bar.webButton -> "web"
                    else -> "space"
                }
            }
            fixture.render(SearchSettings())
            assertThat(order()).containsExactly("chikku", "space", "web").inOrder()
            fixture.render(SearchSettings(widgetChikkuButton = HomeSearchBarButtonSlot.END, widgetWebButton = HomeSearchBarButtonSlot.START))
            assertThat(order()).containsExactly("web", "space", "chikku").inOrder()
            fixture.render(SearchSettings(widgetWebButton = HomeSearchBarButtonSlot.START))
            assertThat(order()).containsExactly("chikku", "web", "space").inOrder()
            fixture.render(SearchSettings(widgetChikkuButton = HomeSearchBarButtonSlot.END, widgetWebButton = HomeSearchBarButtonSlot.END))
            assertThat(order()).containsExactly("space", "web", "chikku").inOrder()
            assertThat(bar.chikkuButton.right).isAtMost(bar.width)
            assertThat(bar.webButton.right).isAtMost(bar.chikkuButton.left)
            fixture.render(SearchSettings(widgetChikkuButton = HomeSearchBarButtonSlot.HIDDEN, widgetWebButton = HomeSearchBarButtonSlot.HIDDEN))
            assertThat(order()).containsExactly("space")
            assertThat(bar.engine).isNull()
            // With no buttons the whole bar is still one action.
            bar.performClick()
            assertThat(fixture.nextStarted()?.component?.className).isEqualTo(SearchActivity::class.java.name)
        }
    }

    @Test
    fun startIsTheRightSideInRightToLeftLayouts() {
        withBar { fixture ->
            fixture.root.layoutDirection = View.LAYOUT_DIRECTION_RTL
            fixture.render(SearchSettings())
            val bar = fixture.bar
            assertThat(bar.chikkuButton.left).isGreaterThan(bar.width / 2)
            assertThat(bar.webButton.right).isLessThan(bar.width / 2)
            assertThat(bar.chikkuButton.right).isAtMost(bar.width)
            assertThat(bar.webButton.left).isAtLeast(0)
        }
    }

    @Test
    fun buttonsAreSeparateNamedTargetsOnHomeOnly() {
        withBar { fixture ->
            fixture.render(SearchSettings(webEngine = WebSearchEngine.BRAVE))
            for (button in listOf(fixture.bar.chikkuButton, fixture.bar.webButton)) {
                assertThat(button.isClickable).isTrue()
                assertThat(button.isFocusable).isTrue()
                assertThat(button.height).isAtLeast(fixture.dp(48))
                assertThat(button.width).isAtLeast(fixture.dp(48))
            }
            assertThat(fixture.bar.chikkuButton.contentDescription.toString()).isEqualTo("Chikku search")
            assertThat(fixture.bar.webButton.contentDescription.toString()).isEqualTo("Search the web with Brave Search")
        }
        // Previews and other unwired bars draw buttons that cannot be pressed or focused.
        val preview = SearchWidgetView(Robolectric.buildActivity(Activity::class.java).setup().get())
        preview.applyAppearance(SearchSettings())
        assertThat(preview.chikkuButton.isClickable || preview.chikkuButton.isFocusable).isFalse()
        assertThat(preview.webButton.isClickable || preview.webButton.isFocusable).isFalse()
    }

    @Test
    fun barActionsAreDiscoverableThroughSettingsSearch() {
        withBar { fixture ->
            val entries = SettingsSearchIndex.entries(fixture.activity.resources, labsUnlocked = false)
            for (query in listOf("tapping the bar opens", "chikku button", "google button")) {
                val results = SettingsSearchIndex.search(entries, query)
                assertThat(results).isNotEmpty()
                assertThat(results.first().section).isEqualTo(SettingsSection.SEARCH)
            }
        }
    }

    private fun withBar(test: (Fixture) -> Unit) {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            test(Fixture(controller.get()))
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private inner class Fixture(val activity: Activity) {
        val handedOff = mutableListOf<List<Intent>>()
        val root = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val bar = SearchWidgetView(activity).apply { id = R.id.home_search_bar_top }
        private val entryPoints: HomeSearchEntryPoints

        init {
            root.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            root.addView(SearchWidgetView(activity).apply { id = R.id.home_search_bar_bottom },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            root.addView(View(activity).apply { id = R.id.drawer_search_more })
            val input = EditText(activity)
            root.addView(input)
            activity.setContentView(root)
            entryPoints = HomeSearchEntryPoints(activity, input) { handedOff += it; true }.also { it.bind() }
        }

        fun render(search: SearchSettings) {
            entryPoints.render(home, search)
            repeat(2) {
                root.forceLayout()
                root.measure(View.MeasureSpec.makeMeasureSpec(dp(400), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(dp(600), View.MeasureSpec.EXACTLY))
                root.layout(0, 0, root.measuredWidth, root.measuredHeight)
            }
        }

        fun nextStarted(): Intent? = shadowOf(activity).nextStartedActivity

        fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density + .5f).toInt()
    }
}
