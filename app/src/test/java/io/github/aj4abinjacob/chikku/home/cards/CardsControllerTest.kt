package io.github.aj4abinjacob.chikku.home.cards

import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.AppCustomization
import io.github.aj4abinjacob.chikku.core.model.AppsState
import io.github.aj4abinjacob.chikku.core.model.CellPlacement
import io.github.aj4abinjacob.chikku.core.model.ComponentKey
import io.github.aj4abinjacob.chikku.core.model.ContainerType
import io.github.aj4abinjacob.chikku.core.model.GestureAction
import io.github.aj4abinjacob.chikku.core.model.GridSpec
import io.github.aj4abinjacob.chikku.core.model.ItemTarget
import io.github.aj4abinjacob.chikku.core.model.Workspace
import io.github.aj4abinjacob.chikku.core.model.WorkspaceItem
import io.github.aj4abinjacob.chikku.core.model.WorkspaceRepository
import io.github.aj4abinjacob.chikku.core.model.WorkspaceState
import io.github.aj4abinjacob.chikku.core.model.cards.AutomationCard
import io.github.aj4abinjacob.chikku.core.model.cards.CardConditions
import io.github.aj4abinjacob.chikku.core.model.cards.CardEnvironment
import io.github.aj4abinjacob.chikku.core.model.cards.CardFeatured
import io.github.aj4abinjacob.chikku.core.model.cards.CardTargetSnapshot
import io.github.aj4abinjacob.chikku.core.model.cards.CardsSettings
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.home.FakeHomeDependencies
import io.github.aj4abinjacob.chikku.home.HomeDependencies
import io.github.aj4abinjacob.chikku.home.TestHomeApplication
import io.github.aj4abinjacob.chikku.home.app
import io.github.aj4abinjacob.chikku.platform.GestureActionExecutor
import io.github.aj4abinjacob.chikku.platform.GestureHost
import io.github.aj4abinjacob.chikku.platform.LaunchResult
import io.github.aj4abinjacob.chikku.platform.cards.CardEnvironmentSource
import io.github.aj4abinjacob.chikku.platform.cards.CardValidation
import io.github.aj4abinjacob.chikku.platform.cards.CardsTargetValidator
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import java.time.Duration
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestHomeApplication::class)
class CardsControllerTest {
    private val target = app("Maps")
    private val second = app("Camera")
    private val card = AutomationCard("commute", "Commute", featured = CardFeatured.App(target.key),
        actions = listOf(GestureAction.LaunchApp(second.key)))
    private lateinit var activityController: ActivityController<ComponentActivity>
    private lateinit var activity: ComponentActivity
    private lateinit var root: FrameLayout
    private lateinit var home: View
    private lateinit var deps: FakeHomeDependencies
    private lateinit var controller: CardsController
    private lateinit var executor: GestureActionExecutor
    private lateinit var validator: CardsTargetValidator
    private val environment = FakeEnvironment()
    @Volatile private var nextValidation: CompletableDeferred<Unit>? = null
    private val validations = AtomicInteger()
    private val widgetProvider = ComponentKey("widget.only", "widget.only.Provider", 0)
    private val openedWidgets = mutableListOf<String>()
    private val messages = io.github.aj4abinjacob.chikku.message.RecordingMessages()

    @Before fun setup() {
        activityController = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        activity = activityController.get()
        root = FrameLayout(activity)
        home = View(activity).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES }
        root.addView(home)
        activity.setContentView(root)
        deps = FakeHomeDependencies()
        deps.appsRepository.state.value = AppsState.Loaded(listOf(target, second))
        deps.workspaceRepository.state.value = WorkspaceState.Ready(Workspace(GridSpec(4, 5), GridSpec(5, 1), 1, emptyList()))
        deps.settingsRepository.state.value = LauncherSettings(cards = CardsSettings(listOf(card)))
        executor = GestureActionExecutor(activity, deps.appLauncher, object : GestureHost {
            override fun openDrawer() = Unit
            override fun openSearch() = Unit
            override fun goToDefaultPage() = Unit
        }, shortcuts = { _, _, _, _ -> LaunchResult.Started })
        validator = CardsTargetValidator { _, apps, hidden, items ->
            validations.incrementAndGet()
            val pending = nextValidation
            nextValidation = null
            pending?.await()
            CardValidation(CardTargetSnapshot(apps, hidden, 0,
                widgets = items.mapNotNull { item -> (item.target as? ItemTarget.Widget)?.let { item.id to it.provider } }.toMap()),
                widgetLabels = items.associate { it.id to "Weather" })
        }
        controller = CardsController(activity, root, deps, executor, openedWidgets::add, environment, validator, messages = messages)
    }

    @After fun cleanup() { controller.onDestroy(); activityController.pause().stop().destroy() }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "Card workflow did not finish" }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
    }
    private fun button(): View? = root.findViewWithTag("card_action:commute:0")
    private fun shown(): Boolean = root.findViewWithTag<View>("card:commute") != null

    private fun useColdWorkspace(workspaceFlow: Flow<WorkspaceState>) {
        controller.onDestroy()
        val repository = object : WorkspaceRepository by deps.workspaceRepository {
            override val workspace = workspaceFlow
        }
        val delegated = object : HomeDependencies by deps {
            override val workspaceRepository: WorkspaceRepository = repository
        }
        controller = CardsController(activity, root, delegated, executor, openedWidgets::add, environment, validator, messages = messages)
    }

    @Test fun matchingCardNeverLaunchesUntilAnExplicitTap() {
        controller.open()
        waitUntil(::shown)
        assertThat(deps.appLauncher.launched).isEmpty()
        assertThat(home.importantForAccessibility).isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS)
        button()!!.performClick()
        waitUntil { deps.appLauncher.launched.isNotEmpty() }
        assertThat(deps.appLauncher.launched.single()).isEqualTo(second.key)
        assertThat(controller.isOpen).isFalse()
        assertThat(home.importantForAccessibility).isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_YES)
    }

    @Test fun actionWaitsThroughLoadingOnBothFreshColdWorkspaceSubscriptions() {
        val subscriptions = AtomicInteger()
        useColdWorkspace(flow {
            subscriptions.incrementAndGet()
            emit(WorkspaceState.Loading)
            delay(25)
            emitAll(deps.workspaceRepository.workspace)
        })
        controller.open(); waitUntil(::shown)
        button()!!.performClick()
        waitUntil { deps.appLauncher.launched.isNotEmpty() }
        assertThat(deps.appLauncher.launched).containsExactly(second.key)
        // The panel observes once; both pre-lookup and post-lookup checks subscribe afresh.
        assertThat(subscriptions.get()).isAtLeast(3)
    }

    @Test fun terminalColdWorkspaceFailureRejectsTheActionRatherThanWaitingForReadiness() {
        val subscriptions = AtomicInteger()
        useColdWorkspace(flow {
            val subscription = subscriptions.incrementAndGet()
            emit(WorkspaceState.Loading)
            delay(25)
            if (subscription == 1) emitAll(deps.workspaceRepository.workspace)
            else emit(WorkspaceState.Failed("storage unavailable"))
        })
        controller.open(); waitUntil(::shown)
        button()!!.performClick()
        waitUntil { messages.shown.isNotEmpty() }
        assertThat(deps.appLauncher.launched).isEmpty()
        assertThat(shown()).isFalse()
    }

    @Test fun hidingAnyActionRemovesTheCardTitleAndFeaturedTarget() {
        controller.open(); waitUntil(::shown)
        deps.appCustomizationRepository.state.value = mapOf(second.key to AppCustomization(second.key, hidden = true))
        waitUntil { !shown() }
        assertThat(root.findViewWithTag<View>("card_featured:commute")).isNull()
        assertThat(deps.appLauncher.launched).isEmpty()
    }

    @Test fun losingAProfileTargetWithholdsTheWholeCard() {
        controller.open(); waitUntil(::shown)
        deps.appsRepository.state.value = AppsState.Loaded(listOf(target))
        waitUntil { !shown() }
        assertThat(deps.appLauncher.launched).isEmpty()
    }

    @Test fun actionRevalidatesHidingThatHappensDuringPlatformLookup() {
        controller.open(); waitUntil(::shown)
        val barrier = CompletableDeferred<Unit>()
        val before = validations.get()
        nextValidation = barrier
        button()!!.performClick()
        waitUntil { validations.get() > before }
        deps.appCustomizationRepository.state.value = mapOf(second.key to AppCustomization(second.key, hidden = true))
        waitUntil { !shown() }
        barrier.complete(Unit)
        waitUntil { messages.shown.isNotEmpty() }
        assertThat(deps.appLauncher.launched).isEmpty()
    }

    @Test fun conditionsRemoveAndRestoreCardsWithoutRunningActions() {
        deps.settingsRepository.state.value = LauncherSettings(cards = CardsSettings(listOf(card.copy(conditions = CardConditions(chargingOnly = true)))))
        controller.open(); waitUntil { environment.collectors.get() == 1 }
        assertThat(shown()).isFalse()
        environment.state.value = environment.state.value.copy(isCharging = true)
        waitUntil(::shown)
        environment.state.value = environment.state.value.copy(isCharging = false)
        waitUntil { !shown() }
        assertThat(deps.appLauncher.launched).isEmpty()
    }

    @Test fun closingAndStoppingReleaseConditionObservations() {
        assertThat(environment.collectors.get()).isEqualTo(0)
        controller.open(); waitUntil { environment.collectors.get() == 1 }
        activityController.pause().stop()
        waitUntil { environment.collectors.get() == 0 }
        activityController.start().resume()
        waitUntil { environment.collectors.get() == 1 }
        controller.close(); waitUntil { environment.collectors.get() == 0 }
    }

    @Test fun bluetoothObservationStartsOnlyForEnabledConditionAndReleasesAfterDisable() {
        controller.open()
        waitUntil { environment.requirements.contains(false) }
        deps.settingsRepository.state.value = LauncherSettings(cards = CardsSettings(listOf(
            card.copy(conditions = CardConditions(bluetoothConnectedOnly = true)))))
        waitUntil { environment.requirements.contains(true) }
        assertThat(shown()).isFalse()
        environment.state.value = environment.state.value.copy(isBluetoothConnected = true)
        waitUntil(::shown)
        deps.settingsRepository.state.value = LauncherSettings(cards = CardsSettings(listOf(
            card.copy(enabled = false, conditions = CardConditions(bluetoothConnectedOnly = true)))))
        waitUntil { environment.requirements.lastOrNull() == false && !shown() }
        controller.close()
        waitUntil { environment.collectors.get() == 0 }
        assertThat(deps.appLauncher.launched).isEmpty()
    }

    @Test fun backClosesPanelAndRestoresHomeAccessibility() {
        controller.open(); waitUntil(::shown)
        activity.onBackPressedDispatcher.onBackPressed()
        assertThat(controller.isOpen).isFalse()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500)) // exit animation
        assertThat(home.importantForAccessibility).isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_YES)
    }

    @Test fun rapidCloseAndReopenDoesNotLetOldCleanupEmptyTheNewSurface() {
        controller.open(); waitUntil(::shown)
        controller.close()
        controller.open()
        waitUntil(::shown)
        shadowOf(Looper.getMainLooper()).idle()
        assertThat(controller.isOpen).isTrue()
        assertThat(shown()).isTrue()
        assertThat(environment.collectors.get()).isEqualTo(1)
    }

    @Test fun featuredWidgetNavigatesToExistingPlacementWithoutLaunchingApp() {
        deps.workspaceRepository.state.value = WorkspaceState.Ready(Workspace(GridSpec(4, 5), GridSpec(5, 1), 1,
            listOf(WorkspaceItem("weather", CellPlacement(ContainerType.DESKTOP, 0, 0, 0), ItemTarget.Widget(123, widgetProvider)))))
        deps.settingsRepository.state.value = LauncherSettings(cards = CardsSettings(listOf(card.copy(featured = CardFeatured.Widget("weather")))))
        controller.open(); waitUntil(::shown)
        root.findViewWithTag<View>("card_featured:commute")!!.performClick()
        waitUntil { openedWidgets.isNotEmpty() }
        assertThat(openedWidgets).containsExactly("weather")
        assertThat(deps.appLauncher.launched).isEmpty()
    }

    private class FakeEnvironment : CardEnvironmentSource {
        val state = MutableStateFlow(CardEnvironment(1, 8 * 60, isCharging = false, isWifi = false))
        val requirements = java.util.concurrent.ConcurrentLinkedQueue<Boolean>()
        val collectors = AtomicInteger()
        override fun observe(bluetoothRequired: Boolean): Flow<CardEnvironment> {
            requirements.add(bluetoothRequired)
            return observe()
        }
        override fun current() = state.value
        override fun observe(): Flow<CardEnvironment> = flow {
            collectors.incrementAndGet()
            try { emitAll(state) } finally { collectors.decrementAndGet() }
        }
    }
}
