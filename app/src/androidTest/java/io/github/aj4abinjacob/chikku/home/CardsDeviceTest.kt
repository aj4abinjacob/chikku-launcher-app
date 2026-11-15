package io.github.aj4abinjacob.chikku.home

import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.DeviceTestSupport
import io.github.aj4abinjacob.chikku.DeviceTestSupport.targetContext
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.GestureAction
import io.github.aj4abinjacob.chikku.core.model.cards.AutomationCard
import io.github.aj4abinjacob.chikku.core.model.cards.CardConditions
import io.github.aj4abinjacob.chikku.core.model.cards.CardsSettings
import io.github.aj4abinjacob.chikku.core.model.settings.OnboardingSettings
import io.github.aj4abinjacob.chikku.onboarding.OnboardingActivity
import io.github.aj4abinjacob.chikku.settings.SettingsActivity
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real Home entry, conditions, Back and explicit action; cards and onboarding are restored. */
@RunWith(AndroidJUnit4::class)
class CardsDeviceTest {
    private var original: CardsSettings? = null
    private var originalOnboarding: OnboardingSettings? = null
    private val card = AutomationCard("device-card", "Device workflow card", actions = listOf(GestureAction.OpenDrawer))

    @Before fun setup() {
        DeviceTestSupport.resetStartupGuard()
        runBlocking {
            val repository = targetContext.homeDependencies().settingsRepository
            val before = repository.settings.first()
            original = before.cards
            originalOnboarding = before.onboarding
            repository.update { it.copy(cards = CardsSettings(listOf(card)),
                onboarding = it.onboarding.copy(completed = true)) }
        }
    }

    @After fun cleanup() {
        try {
            DeviceTestSupport.finishAll(SettingsActivity::class.java)
            DeviceTestSupport.finishAll(OnboardingActivity::class.java)
            DeviceTestSupport.finishAll(HomeActivity::class.java)
        } finally {
            try {
                original?.let { settings -> runBlocking {
                    targetContext.homeDependencies().settingsRepository.update {
                        it.copy(cards = settings, onboarding = originalOnboarding ?: it.onboarding)
                    }
                } }
            } finally { original = null; originalOnboarding = null; DeviceTestSupport.resetStartupGuard() }
        }
    }

    private fun <T> ActivityScenario<HomeActivity>.read(block: (HomeActivity) -> T): T {
        var result: Any? = null
        onActivity { result = block(it) }
        @Suppress("UNCHECKED_CAST") return result as T
    }

    private fun ActivityScenario<HomeActivity>.waitFor(check: (HomeActivity) -> Boolean) =
        DeviceTestSupport.waitUntil(DeviceTestSupport.LOAD_TIMEOUT_MS) { read(check) }

    private fun HomeActivity.root() = findViewById<FrameLayout>(R.id.home_root)
    private fun HomeActivity.cardVisible() = root().findViewWithTag<View>("card:device-card") != null

    private fun ActivityScenario<HomeActivity>.openCards() {
        onActivity { activity ->
            val workspace = activity.findViewById<View>(R.id.workspace_pager)
            val node = workspace.createAccessibilityNodeInfo()
            val action = node.actionList.first { it.label?.toString() == activity.getString(R.string.settings_section_cards) }
            assertThat(workspace.performAccessibilityAction(action.id, null)).isTrue()
        }
    }

    @Test fun homeEntryConditionsBackAndTappedActionWorkTogether() {
        ActivityScenario.launch(HomeActivity::class.java).use { scenario ->
            assertThat(scenario.waitFor { it.findViewById<View>(R.id.dock).visibility == View.VISIBLE }).isTrue()
            scenario.openCards()
            assertThat(scenario.waitFor { it.cardVisible() }).isTrue()
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).waitForIdle()
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(
                java.io.File(targetContext.getExternalFilesDir(null), "cards-panel-synthetic.png"))
            // A local weekday condition changes visibility immediately, with no background action.
            val nextDay = ZonedDateTime.now().dayOfWeek.value % 7 + 1
            runBlocking { targetContext.homeDependencies().settingsRepository.update {
                it.copy(cards = CardsSettings(listOf(card.copy(conditions = CardConditions(weekdays = setOf(nextDay))))))
            } }
            assertThat(scenario.waitFor { !it.cardVisible() }).isTrue()
            assertThat(scenario.read { it.root().findViewWithTag<View>("cards_panel") != null }).isTrue()
            runBlocking { targetContext.homeDependencies().settingsRepository.update { it.copy(cards = CardsSettings(listOf(card))) } }
            assertThat(scenario.waitFor { it.cardVisible() }).isTrue()
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
            assertThat(scenario.waitFor { it.root().findViewWithTag<View>("cards_panel") == null }).isTrue()
            scenario.openCards()
            assertThat(scenario.waitFor { it.cardVisible() }).isTrue()
            scenario.onActivity { it.root().findViewWithTag<View>("card_action:device-card:0").performClick() }
            assertThat(scenario.waitFor {
                it.root().findViewWithTag<View>("cards_panel") == null && it.findViewById<View>(R.id.drawer).visibility == View.VISIBLE
            }).isTrue()
        }
    }
}
