package io.github.aj4abinjacob.chikku.home

import android.content.Intent
import android.os.SystemClock
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.DeviceTestSupport
import io.github.aj4abinjacob.chikku.DeviceTestSupport.UI_TIMEOUT_MS
import io.github.aj4abinjacob.chikku.DeviceTestSupport.targetContext
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerOpenMethod
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerSearchBarPosition
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerSettings
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerStyle
import io.github.aj4abinjacob.chikku.settings.SettingsActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.regex.Pattern
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end HOME workflow on a real device: start, open the drawer, search, launch, recreate.
 *
 * HomeActivity is started explicitly through ActivityScenario; system HOME is never pressed and the
 * default HOME role is never changed. The workspace is only read (the app's normal first-run dock
 * seeding may run against the debug app's own database). Startup-guard prefs are reset before and
 * after so repeated short launches never leave the debug app in safe mode. Drawer preferences are
 * temporarily configured for the button/search workflow and restored after every test.
 */
@RunWith(AndroidJUnit4::class)
class HomeActivityDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val device: UiDevice get() = UiDevice.getInstance(instrumentation)

    private val settingsLabel: String get() = targetContext.getString(R.string.settings_title)
    private var originalDrawer: DrawerSettings? = null
    private var originalOnboarding: io.github.aj4abinjacob.chikku.core.model.settings.OnboardingSettings? = null

    @Before
    fun setUp() {
        DeviceTestSupport.resetStartupGuard()
        runBlocking {
            val repository = targetContext.homeDependencies().settingsRepository
            val original = repository.settings.first()
            originalDrawer = original.drawer
            originalOnboarding = original.onboarding
            repository.update { settings -> settings.copy(onboarding = settings.onboarding.copy(completed = true), drawer = settings.drawer.copy(
                openMethod = DrawerOpenMethod.BOTH,
                style = DrawerStyle.VERTICAL,
                searchBarPosition = DrawerSearchBarPosition.TOP,
            )) }
        }
    }

    @After
    fun tearDown() {
        try {
            DeviceTestSupport.finishAll(SettingsActivity::class.java)
            DeviceTestSupport.finishAll(HomeActivity::class.java)
        } finally {
            try {
                originalDrawer?.let { drawer -> runBlocking {
                    targetContext.homeDependencies().settingsRepository.update {
                        it.copy(drawer = drawer, onboarding = originalOnboarding ?: it.onboarding)
                    }
                } }
            } finally {
                originalDrawer = null
                originalOnboarding = null
                DeviceTestSupport.resetStartupGuard()
            }
        }
    }

    private fun <T> ActivityScenario<HomeActivity>.read(block: (HomeActivity) -> T): T {
        var value: Any? = null
        onActivity { value = block(it) }
        @Suppress("UNCHECKED_CAST")
        return value as T
    }

    private fun ActivityScenario<HomeActivity>.waitFor(timeoutMs: Long = UI_TIMEOUT_MS, check: (HomeActivity) -> Boolean) =
        DeviceTestSupport.waitUntil(timeoutMs) { read(check) }

    private fun HomeActivity.view(id: Int): View = findViewById(id)

    private fun res(id: String) = By.res(Pattern.compile(".*:id/$id"))

    private fun ActivityScenario<HomeActivity>.awaitWorkspaceReady() {
        // Dock and pager become VISIBLE only when the workspace state is Ready.
        val ready = waitFor(DeviceTestSupport.LOAD_TIMEOUT_MS) { it.view(R.id.dock).visibility == View.VISIBLE }
        assertThat(ready).isTrue()
    }

    private fun drawerApps(scenario: ActivityScenario<HomeActivity>): List<String> = scenario.read { activity ->
        val list = activity.findViewById<RecyclerView>(R.id.drawer_list)
        (list.adapter as DrawerAdapter).currentList.map { it.label }
    }

    @Test
    fun startsAndRendersWorkspaceAndDock() {
        ActivityScenario.launch(HomeActivity::class.java).use { scenario ->
            scenario.awaitWorkspaceReady()
            scenario.onActivity { activity ->
                assertThat(activity.view(R.id.dock).isShown).isTrue()
                assertThat(activity.view(R.id.workspace_pager).isShown).isTrue()
                assertThat(activity.view(R.id.all_apps_button).isShown).isTrue()
                // Not in safe mode / recovery on a healthy start.
                assertThat(activity.view(R.id.recovery_panel).visibility).isEqualTo(View.GONE)
                assertThat(activity.view(R.id.drawer).visibility).isNotEqualTo(View.VISIBLE)
            }
            // Visible to accessibility / UiAutomator too, by its content description.
            val dockDesc = targetContext.getString(R.string.home_dock_description)
            assertThat(device.wait(Until.hasObject(By.desc(dockDesc)), UI_TIMEOUT_MS)).isTrue()
        }
    }

    @Test
    fun returningFromLongStopDoesNotReplayWorkspaceOrAppLoading() {
        val settings = targetContext.homeDependencies().settingsRepository
        val originalOnboarding = runBlocking { settings.settings.first().onboarding }
        runBlocking { settings.update { it.copy(onboarding = it.onboarding.copy(completed = true)) } }
        try {
            ActivityScenario.launch(HomeActivity::class.java).use { scenario ->
                scenario.awaitWorkspaceReady()
                assertThat(scenario.waitFor(DeviceTestSupport.LOAD_TIMEOUT_MS) {
                    ViewModelProvider(it)[HomeViewModel::class.java].uiState.value.drawer is DrawerUi.Apps
                }).isTrue()
                lateinit var before: HomeUiState
                val returned = mutableListOf<HomeUiState>() // Read and written only on the main thread.
                scenario.onActivity { activity ->
                    val vm = ViewModelProvider(activity)[HomeViewModel::class.java]
                    before = vm.uiState.value
                    activity.lifecycleScope.launch {
                        activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                            vm.uiState.collect { returned += it }
                        }
                    }
                }
                scenario.onActivity { it.startActivity(Intent(it, SettingsActivity::class.java)) }
                assertThat(DeviceTestSupport.waitForResumed(SettingsActivity::class.java) != null).isTrue()
                assertThat(scenario.waitFor { it.lifecycle.currentState == Lifecycle.State.CREATED }).isTrue()
                // The UI collectors have stopped. Leave enough time for both original sharing
                // timeouts to expire; an observer spanning this wait would conceal the regression.
                SystemClock.sleep(16_000)
                instrumentation.runOnMainSync { returned.clear() }
                DeviceTestSupport.finishAll(SettingsActivity::class.java)
                assertThat(DeviceTestSupport.waitForResumed(HomeActivity::class.java) != null).isTrue()
                SystemClock.sleep(1_000) // Include the old debounced app reload, not just its first frame.
                scenario.onActivity {
                    assertThat(returned.isNotEmpty()).isTrue()
                    // Report booleans only: assertion failures must not print the device's app list.
                    assertThat(returned.all { state -> state.workspace == before.workspace }).isTrue()
                    assertThat(returned.all { state -> state.drawer == before.drawer }).isTrue()
                }
            }
        } finally {
            runBlocking { settings.update { it.copy(onboarding = originalOnboarding) } }
        }
    }

    @Test
    fun drawerSearchAndLaunchWorkflowSurvivesRecreate() {
        ActivityScenario.launch(HomeActivity::class.java).use { scenario ->
            scenario.awaitWorkspaceReady()

            // 1. Open the drawer through the accessible up arrow.
            val allApps = device.wait(Until.findObject(By.desc(targetContext.getString(R.string.home_open_all_apps))), UI_TIMEOUT_MS)
            assertThat(allApps).isNotNull()
            allApps.click()
            assertThat(device.wait(Until.hasObject(res("drawer_search")), UI_TIMEOUT_MS)).isTrue()
            assertThat(scenario.waitFor { it.view(R.id.drawer).visibility == View.VISIBLE }).isTrue()

            // 2. The drawer lists Chikku settings (checked in the adapter: it may be scrolled off-screen).
            assertThat(DeviceTestSupport.waitUntil(DeviceTestSupport.LOAD_TIMEOUT_MS) { settingsLabel in drawerApps(scenario) })
                .isTrue()

            // 3. A configuration-change style recreate keeps HOME alive and the drawer open.
            scenario.recreate()
            scenario.awaitWorkspaceReady()
            assertThat(scenario.waitFor { it.view(R.id.drawer).visibility == View.VISIBLE }).isTrue()

            // 4. Typing part of the label filters the list.
            val query = settingsLabel.take(SEARCH_PREFIX_LENGTH).trim()
            val search: UiObject2 = device.wait(Until.findObject(res("drawer_search")), UI_TIMEOUT_MS)
            search.text = query
            val filtered = DeviceTestSupport.waitUntil {
                val labels = drawerApps(scenario)
                labels.isNotEmpty() && labels.first() == settingsLabel
            }
            assertThat(filtered).isTrue()
            val labels = drawerApps(scenario)
            assertThat(labels.all { it.lowercase().contains(query.lowercase()) }).isTrue()

            // 5. Tapping the result launches the settings activity.
            val result = device.wait(Until.findObject(By.desc(settingsLabel)), UI_TIMEOUT_MS)
            assertThat(result).isNotNull()
            result.click()
            assertThat(DeviceTestSupport.waitForResumed(SettingsActivity::class.java)).isNotNull()
            DeviceTestSupport.finishAll(SettingsActivity::class.java)
        }
    }

    @Test
    fun noMatchQueryShowsEmptyStateAndBackClearsQuery() {
        ActivityScenario.launch(HomeActivity::class.java).use { scenario ->
            scenario.awaitWorkspaceReady()
            scenario.onActivity { it.view(R.id.all_apps_button).performClick() }
            assertThat(scenario.waitFor { it.view(R.id.drawer).visibility == View.VISIBLE }).isTrue()

            val search: UiObject2 = device.wait(Until.findObject(res("drawer_search")), UI_TIMEOUT_MS)
            search.text = "zzqx-no-app-matches-this-0192"
            val noMatches = targetContext.getString(R.string.home_drawer_no_matches)
            assertThat(device.wait(Until.hasObject(By.text(noMatches)), UI_TIMEOUT_MS)).isTrue()

            // Back in the drawer clears the query first, then closes the drawer; HOME itself stays.
            device.pressBack()
            if (!scenario.waitFor(5_000) { it.findViewById<android.widget.EditText>(R.id.drawer_search).text.isEmpty() }) {
                device.pressBack() // first Back may have only dismissed the IME
            }
            assertThat(scenario.waitFor { it.findViewById<android.widget.EditText>(R.id.drawer_search).text.isEmpty() }).isTrue()
            device.pressBack()
            if (!scenario.waitFor(5_000) { it.view(R.id.drawer).visibility != View.VISIBLE }) device.pressBack()
            assertThat(scenario.waitFor { it.view(R.id.drawer).visibility != View.VISIBLE }).isTrue()

            // Back on the workspace does not finish HOME.
            device.pressBack()
            instrumentation.waitForIdleSync()
            assertThat(scenario.read { it.isFinishing }).isFalse()
        }
    }

    private companion object {
        const val SEARCH_PREFIX_LENGTH = 7
    }
}
