package io.github.aj4abinjacob.chikku.home.cards

import android.content.Intent
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.AppsState
import io.github.aj4abinjacob.chikku.core.model.GestureAction
import io.github.aj4abinjacob.chikku.core.model.GridSpec
import io.github.aj4abinjacob.chikku.core.model.Workspace
import io.github.aj4abinjacob.chikku.core.model.WorkspaceState
import io.github.aj4abinjacob.chikku.core.model.cards.AutomationCard
import io.github.aj4abinjacob.chikku.core.model.cards.CardsSettings
import io.github.aj4abinjacob.chikku.core.model.settings.AnimationSpeed
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.home.FakeAppsRepository
import io.github.aj4abinjacob.chikku.home.FakeHomeDependencies
import io.github.aj4abinjacob.chikku.home.FakeSettingsRepository
import io.github.aj4abinjacob.chikku.home.FakeWorkspaceRepository
import io.github.aj4abinjacob.chikku.home.HomeActivity
import io.github.aj4abinjacob.chikku.home.TestHomeApplication
import java.time.Duration
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Actual Home wiring: modal accessibility, drawer render races, Back and HOME dismissal. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestHomeApplication::class)
class HomeCardsIntegrationTest {
    private val application = ApplicationProvider.getApplicationContext<TestHomeApplication>()
    private lateinit var deps: FakeHomeDependencies

    @Before fun setup() {
        application.getSharedPreferences("chikku_startup_guard", 0).edit().clear().commit()
        val settings = LauncherSettings().let { defaults -> defaults.copy(
            cards = CardsSettings(listOf(AutomationCard("home-test", "Home integration card",
                actions = listOf(GestureAction.OpenDrawer)))),
            lookFeel = defaults.lookFeel.copy(animationSpeed = AnimationSpeed.NORMAL),
        ) }
        deps = FakeHomeDependencies(
            appsRepository = FakeAppsRepository(AppsState.Loaded(emptyList(), setOf(0L))),
            settingsRepository = FakeSettingsRepository(settings),
            workspaceRepository = FakeWorkspaceRepository(WorkspaceState.Ready(
                Workspace(GridSpec(4, 5), GridSpec(5, 1), 1, emptyList()))),
        )
        application.homeDependencies = deps
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "Home Cards workflow did not finish" }
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
            Thread.sleep(10)
        }
    }

    private fun HomeActivity.panel(): View? = findViewById<FrameLayout>(R.id.home_root).findViewWithTag("cards_panel")
    private fun HomeActivity.content(): View = findViewById(R.id.home_content)
    private fun HomeActivity.openCardsEntry() {
        val workspace = findViewById<View>(R.id.workspace_pager)
        val action = workspace.createAccessibilityNodeInfo().actionList.first {
            it.label?.toString() == getString(R.string.settings_section_cards)
        }
        assertThat(workspace.performAccessibilityAction(action.id, null)).isTrue()
    }

    @Test fun asynchronousDrawerCloseKeepsHomeHiddenWhileCardsAreOpenAndBackRestoresIt() {
        ActivityScenario.launch(HomeActivity::class.java).use { scenario ->
            lateinit var activity: HomeActivity
            scenario.onActivity { activity = it }
            waitUntil { activity.findViewById<View>(R.id.dock).visibility == View.VISIBLE }
            activity.findViewById<View>(R.id.all_apps_button).performClick()
            waitUntil { activity.findViewById<View>(R.id.drawer).visibility == View.VISIBLE &&
                activity.content().importantForAccessibility == View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS }
            // Invoke the workspace's real accessibility entry programmatically while drawer close
            // is still pending, exercising the asynchronous render that previously restored AUTO.
            activity.openCardsEntry()
            assertThat(activity.panel()).isNotNull()
            assertThat(activity.content().importantForAccessibility).isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS)
            waitUntil { activity.findViewById<View>(R.id.drawer).visibility != View.VISIBLE }
            deps.settingsRepository.state.value = deps.settingsRepository.state.value.let {
                it.copy(search = it.search.copy(widgetShadow = !it.search.widgetShadow))
            }
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
            assertThat(activity.panel()).isNotNull()
            assertThat(activity.content().importantForAccessibility).isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS)
            activity.onBackPressedDispatcher.onBackPressed()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500)) // exit animation
            assertThat(activity.panel()).isNull()
            assertThat(activity.content().importantForAccessibility).isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO)
        }
    }

    @Test fun homePressDismissesCardsAndRestoresHomeAccessibility() {
        ActivityScenario.launch(HomeActivity::class.java).use { scenario ->
            lateinit var activity: HomeActivity
            scenario.onActivity { activity = it }
            waitUntil { activity.findViewById<View>(R.id.dock).visibility == View.VISIBLE }
            activity.openCardsEntry()
            assertThat(activity.panel()).isNotNull()
            assertThat(activity.content().importantForAccessibility).isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS)
            activity.handleHomeIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
            assertThat(activity.panel()).isNull()
            assertThat(activity.content().importantForAccessibility).isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO)
            assertThat(deps.appLauncher.launched).isEmpty()
        }
    }
}
