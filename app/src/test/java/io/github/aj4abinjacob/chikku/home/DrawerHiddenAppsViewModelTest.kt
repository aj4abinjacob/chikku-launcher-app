package io.github.aj4abinjacob.chikku.home

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.AppCustomization
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.AppsState
import io.github.aj4abinjacob.chikku.core.model.CellPlacement
import io.github.aj4abinjacob.chikku.core.model.ContainerType
import io.github.aj4abinjacob.chikku.core.model.GridSpec
import io.github.aj4abinjacob.chikku.core.model.ItemTarget
import io.github.aj4abinjacob.chikku.core.model.LauncherApp
import io.github.aj4abinjacob.chikku.core.model.ProfileType
import io.github.aj4abinjacob.chikku.core.model.Workspace
import io.github.aj4abinjacob.chikku.core.model.WorkspaceItem
import io.github.aj4abinjacob.chikku.core.model.WorkspaceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Hidden apps (G14) leave the drawer and search but stay on the workspace. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestHomeApplication::class)
class DrawerHiddenAppsViewModelTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dispatcher = UnconfinedTestDispatcher()
    private val appScope = CoroutineScope(SupervisorJob() + dispatcher)

    private val chrome = app("Chrome")
    private val maps = app("Maps")
    private val workMaps = LauncherApp(AppKey(maps.key.packageName, maps.key.className, 10L), "Maps", ProfileType.WORK)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        context.getSharedPreferences("guard_hidden_test", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After
    fun tearDown() {
        appScope.cancel()
        Dispatchers.resetMain()
    }

    private fun TestScope.newViewModel(deps: FakeHomeDependencies): HomeViewModel {
        val guard = StartupGuard({ context.getSharedPreferences("guard_hidden_test", Context.MODE_PRIVATE) }, dispatcher)
        val vm = HomeViewModel(deps, SavedStateHandle(), guard, false, dispatcher, dispatcher)
        backgroundScope.launch(dispatcher) { vm.uiState.collect {} }
        return vm
    }

    @Test
    fun hiddenAppsAreLeftOutOfDrawerAndSearchButStayOnWorkspace() = runTest(dispatcher) {
        val workspace = Workspace(
            GridSpec(4, 5),
            GridSpec(5, 1),
            1,
            listOf(WorkspaceItem("1", CellPlacement(ContainerType.DESKTOP, 0, 0, 0), ItemTarget.App(maps.key))),
        )
        val deps = FakeHomeDependencies(
            appScope = appScope,
            appsRepository = FakeAppsRepository(AppsState.Loaded(listOf(chrome, maps, workMaps))),
            workspaceRepository = FakeWorkspaceRepository(WorkspaceState.Ready(workspace)),
        )
        deps.appCustomizationRepository.state.value = mapOf(maps.key to AppCustomization(maps.key, hidden = true))
        val vm = newViewModel(deps)

        // Same package in the work profile is a different app and stays visible.
        assertThat(vm.uiState.value.drawer).isEqualTo(DrawerUi.Apps(listOf(chrome, workMaps)))

        vm.setQuery("maps")
        advanceUntilIdle()
        assertThat(deps.appSearcher.queries).contains("maps")
        assertThat(vm.uiState.value.drawer).isEqualTo(DrawerUi.Apps(listOf(workMaps)))
        assertThat(vm.firstResult()).isEqualTo(workMaps.key)

        // The hidden app's workspace icon still resolves and is launchable.
        val ready = vm.uiState.value.workspace as WorkspaceUi.Ready
        val content = ready.pages[0].single().content as ItemContent.App
        assertThat(content.availability).isEqualTo(Availability.AVAILABLE)

        // Unhiding brings it back immediately.
        deps.appCustomizationRepository.setHidden(maps.key, false)
        vm.setQuery("")
        advanceUntilIdle()
        assertThat(vm.uiState.value.drawer).isEqualTo(DrawerUi.Apps(listOf(chrome, maps, workMaps)))
    }

    @Test
    fun hidingEverythingShowsNoApps() = runTest(dispatcher) {
        val deps = FakeHomeDependencies(
            appScope = appScope,
            appsRepository = FakeAppsRepository(AppsState.Loaded(listOf(chrome))),
        )
        deps.appCustomizationRepository.state.value = mapOf(chrome.key to AppCustomization(chrome.key, hidden = true))
        val vm = newViewModel(deps)
        assertThat(vm.uiState.value.drawer).isEqualTo(DrawerUi.NoApps)
    }
}
