package io.github.aj4abinjacob.chikku.search

import android.os.Looper
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import io.github.aj4abinjacob.chikku.core.model.settings.WebSearchEngine
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.AppsState
import io.github.aj4abinjacob.chikku.core.model.settings.HomeSearchBarPosition
import io.github.aj4abinjacob.chikku.home.FakeAppsRepository
import io.github.aj4abinjacob.chikku.home.FakeHomeDependencies
import io.github.aj4abinjacob.chikku.home.HomeActivity
import io.github.aj4abinjacob.chikku.home.TestHomeApplication
import io.github.aj4abinjacob.chikku.home.app
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** HOME's search entry points against fakes: search opens in HOME's window for continuous dragging. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestHomeApplication::class)
class HomeSearchEntryPointsTest {

    private val application = ApplicationProvider.getApplicationContext<TestHomeApplication>()
    private lateinit var deps: FakeHomeDependencies

    @Before
    fun setUp() {
        application.getSharedPreferences("chikku_startup_guard", 0).edit().clear().commit()
        deps = FakeHomeDependencies(appsRepository = FakeAppsRepository(AppsState.Loaded(listOf(app("Maps")))))
        deps.settingsRepository.state.value = deps.settingsRepository.state.value.let {
            it.copy(onboarding = it.onboarding.copy(completed = true))
        }
        application.homeDependencies = deps
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "condition not met in time" }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
    }

    private fun setBar(show: Boolean, position: HomeSearchBarPosition) {
        deps.settingsRepository.state.value = deps.settingsRepository.state.value.let {
            it.copy(home = it.home.copy(showSearchBar = show, searchBarPosition = position))
        }
    }

    @Test
    fun searchBarFollowsSettingsAndOpensSearch() {
        ActivityScenario.launch(HomeActivity::class.java).use { scenario ->
            lateinit var activity: HomeActivity
            scenario.onActivity { activity = it }
            val top = activity.findViewById<View>(R.id.home_search_bar_top)
            val bottom = activity.findViewById<View>(R.id.home_search_bar_bottom)
            shadowOf(Looper.getMainLooper()).idle()
            assertThat(top.visibility).isEqualTo(View.GONE) // off by default
            assertThat(bottom.visibility).isEqualTo(View.GONE)

            setBar(true, HomeSearchBarPosition.TOP)
            waitUntil { top.visibility == View.VISIBLE }
            assertThat(bottom.visibility).isEqualTo(View.GONE)

            setBar(true, HomeSearchBarPosition.BELOW_DOCK)
            waitUntil { bottom.visibility == View.VISIBLE }
            assertThat(top.visibility).isEqualTo(View.GONE)

            setBar(true, HomeSearchBarPosition.NONE)
            waitUntil { bottom.visibility == View.GONE }
            assertThat(top.visibility).isEqualTo(View.GONE)

            setBar(true, HomeSearchBarPosition.BELOW_DOCK)
            waitUntil { bottom.visibility == View.VISIBLE }
            deps.settingsRepository.state.value = deps.settingsRepository.state.value.let {
                it.copy(search = it.search.copy(webEngine = WebSearchEngine.GOOGLE))
            }
            waitUntil { (bottom as SearchWidgetView).engine == WebSearchEngine.GOOGLE }
            assertThat(bottom.contentDescription.toString()).contains("Google")
            bottom.performClick()
            val root = activity.findViewById<FrameLayout>(R.id.home_root)
            assertThat((0 until root.childCount).any { root.getChildAt(it) is ComposeView && root.getChildAt(it).visibility == View.VISIBLE }).isTrue()
            assertThat(shadowOf(activity).nextStartedActivity).isNull()
            activity.onBackPressedDispatcher.onBackPressed()
            // Back fades search out; it is gone once that finishes.
            shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1))
            assertThat((0 until root.childCount).any { root.getChildAt(it) is ComposeView && root.getChildAt(it).visibility == View.VISIBLE }).isFalse()
        }
    }

    @Test
    fun drawerQueryIsHandedToUniversalSearch() {
        ActivityScenario.launch(HomeActivity::class.java).use { scenario ->
            lateinit var activity: HomeActivity
            scenario.onActivity { activity = it }
            val more = activity.findViewById<View>(R.id.drawer_search_more)
            assertThat(more.visibility).isEqualTo(View.GONE)
            activity.findViewById<EditText>(R.id.drawer_search).setText("pizza near me")
            assertThat(more.visibility).isEqualTo(View.VISIBLE)
            more.performClick()
            val root = activity.findViewById<FrameLayout>(R.id.home_root)
            assertThat((0 until root.childCount).any { root.getChildAt(it) is ComposeView && root.getChildAt(it).visibility == View.VISIBLE }).isTrue()
            assertThat(shadowOf(activity).nextStartedActivity).isNull()
            assertThat(activity.findViewById<View>(R.id.home_content).importantForAccessibility)
                .isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS)
        }
    }

    @Test
    fun searchActivityStartsWithAPrefilledQuery() {
        val intent = SearchLauncher.intent(application, "2+2")
        ActivityScenario.launch<SearchActivity>(intent).use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity -> assertThat(activity.isFinishing).isFalse() }
        }
    }
}
