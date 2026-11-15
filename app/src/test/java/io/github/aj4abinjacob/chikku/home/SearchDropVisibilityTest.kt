package io.github.aj4abinjacob.chikku.home

import android.os.Looper
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The real HOME drop handler checks visibility again after a search/drawer drag began. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestHomeApplication::class)
class SearchDropVisibilityTest {
    private val application = ApplicationProvider.getApplicationContext<TestHomeApplication>()
    private val listed = app("Maps")
    private lateinit var deps: FakeHomeDependencies
    private val destination = CellPlacement(ContainerType.DESKTOP, 0, 1, 1)

    @Before
    fun setUp() {
        application.getSharedPreferences("chikku_startup_guard", 0).edit().clear().commit()
        deps = FakeHomeDependencies(
            appsRepository = FakeAppsRepository(AppsState.Loaded(listOf(listed))),
            workspaceRepository = FakeWorkspaceRepository(WorkspaceState.Ready(Workspace(GridSpec(4, 5), GridSpec(5, 1), 1, emptyList()))),
        )
        deps.settingsRepository.state.value = deps.settingsRepository.state.value.let { it.copy(onboarding = it.onboarding.copy(completed = true)) }
        application.homeDependencies = deps
    }

    private fun await(check: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!check()) {
            check(System.currentTimeMillis() < deadline)
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
    }

    private fun withHome(block: (HomeActivity, WorkspaceActions) -> Unit) {
        ActivityScenario.launch(HomeActivity::class.java).use { scenario ->
            lateinit var home: HomeActivity
            scenario.onActivity { home = it }
            await { home.findViewById<WorkspacePager>(R.id.workspace_pager).pageCount == 1 }
            val actions = HomeActivity::class.java.getDeclaredField("workspaceActions").apply { isAccessible = true }.get(home) as WorkspaceActions
            block(home, actions)
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    @Test
    fun hiddenOrNoLongerListedProfileCannotBecomeAPlacementAtDropTime() {
        withHome { _, actions ->
            deps.appCustomizationRepository.state.value = mapOf(listed.key to AppCustomization(listed.key, hidden = true))
            actions.onDrop(DragPayload.NewApp(listed.key), destination)
            shadowOf(Looper.getMainLooper()).idle()
            assertThat(deps.workspaceRepository.adds).isEmpty()
            deps.appCustomizationRepository.state.value = emptyMap()
            deps.appsRepository.state.value = AppsState.Loaded(listOf(listed), emptySet())
            actions.onDrop(DragPayload.NewApp(listed.key), destination)
            shadowOf(Looper.getMainLooper()).idle()
            assertThat(deps.workspaceRepository.adds).isEmpty()
        }
    }

    @Test
    fun visibleComponentAndProfileReachExistingPlacementPath() {
        withHome { _, actions ->
            actions.onDrop(DragPayload.NewApp(listed.key), destination)
            await { deps.workspaceRepository.adds.isNotEmpty() }
            assertThat(deps.workspaceRepository.adds).containsExactly(listed.key to destination)
        }
    }
}
