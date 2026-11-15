package io.github.aj4abinjacob.chikku.home.cards.widget

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.AppsState
import io.github.aj4abinjacob.chikku.core.model.GridSpec
import io.github.aj4abinjacob.chikku.core.model.Workspace
import io.github.aj4abinjacob.chikku.core.model.WorkspaceState
import io.github.aj4abinjacob.chikku.core.model.cards.AutomationCard
import io.github.aj4abinjacob.chikku.core.model.cards.CardContent
import io.github.aj4abinjacob.chikku.core.model.cards.CardsSettings
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

/** Home's real Cards wiring renders an unconfigured widget without allocating or launching it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestHomeApplication::class)
class HomeEmbeddedWidgetIntegrationTest {
    @Before fun setup() {
        val application = ApplicationProvider.getApplicationContext<TestHomeApplication>()
        application.getSharedPreferences("chikku_startup_guard", 0).edit().clear().commit()
        application.homeDependencies = FakeHomeDependencies(
            appsRepository = FakeAppsRepository(AppsState.Loaded(emptyList(), setOf(0L))),
            settingsRepository = FakeSettingsRepository(LauncherSettings(onboarding = io.github.aj4abinjacob.chikku.core.model.settings.OnboardingSettings(completed = true), cards = CardsSettings(listOf(
                AutomationCard("embedded-home", "Embedded widget", content = CardContent.Widget()),
            )))),
            workspaceRepository = FakeWorkspaceRepository(WorkspaceState.Ready(
                Workspace(GridSpec(4, 5), GridSpec(5, 1), 1, emptyList()))),
        )
    }
    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "Home embedded widget workflow did not finish" }
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
            Thread.sleep(10)
        }
    }
    private fun View.buttons(): List<Button> = when (this) {
        is Button -> listOf(this)
        is ViewGroup -> (0 until childCount).flatMap { getChildAt(it).buttons() }
        else -> emptyList()
    }
    @Test fun accessibilityCardsEntryRendersChooseWidgetAndBackClosesItsHostSurface() {
        ActivityScenario.launch(HomeActivity::class.java).use { scenario ->
            lateinit var activity: HomeActivity
            scenario.onActivity { activity = it }
            waitUntil { activity.findViewById<View>(R.id.dock).visibility == View.VISIBLE }
            val workspace = activity.findViewById<View>(R.id.workspace_pager)
            val entry = workspace.createAccessibilityNodeInfo().actionList.first {
                it.label?.toString() == activity.getString(R.string.settings_section_cards)
            }
            assertThat(workspace.performAccessibilityAction(entry.id, null)).isTrue()
            val root = activity.findViewById<FrameLayout>(R.id.home_root)
            waitUntil { root.findViewWithTag<View>("card_widget:embedded-home")?.buttons()?.isNotEmpty() == true }
            val cell = root.findViewWithTag<View>("card_widget:embedded-home")!!
            assertThat(cell.buttons().single().text.toString()).isEqualTo(activity.getString(R.string.card_widget_choose))
            assertThat(shadowOf(activity).nextStartedActivity).isNull()
            activity.onBackPressedDispatcher.onBackPressed()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500)) // exit animation
            assertThat(root.findViewWithTag<View>("cards_panel")).isNull()
            assertThat((cell as ViewGroup).childCount).isEqualTo(0)
        }
    }
}
