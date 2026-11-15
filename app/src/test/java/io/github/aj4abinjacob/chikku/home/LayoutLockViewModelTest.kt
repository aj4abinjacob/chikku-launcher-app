package io.github.aj4abinjacob.chikku.home

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.CellPlacement
import io.github.aj4abinjacob.chikku.core.model.ContainerType
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** G17: every workspace edit is refused while the layout is locked, with a message. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestHomeApplication::class)
class LayoutLockViewModelTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dispatcher = UnconfinedTestDispatcher()
    private val appScope = CoroutineScope(SupervisorJob() + dispatcher)
    private val key = AppKey("pkg.a", "pkg.a.Main", 0L)
    private val target = CellPlacement(ContainerType.DESKTOP, 0, 1, 1)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() {
        appScope.cancel()
        Dispatchers.resetMain()
    }

    private fun guard() = StartupGuard({ context.getSharedPreferences("lock_guard", Context.MODE_PRIVATE) }, dispatcher)

    @Test
    fun lockedLayoutRejectsEditsWithEvent() = runTest(dispatcher) {
        val deps = FakeHomeDependencies(appScope = appScope)
        deps.settingsRepository.state.value = LauncherSettings().let { it.copy(home = it.home.copy(layoutLocked = true)) }
        val vm = HomeViewModel(deps, SavedStateHandle(), guard(), false, dispatcher, dispatcher)
        backgroundScope.launch(dispatcher) { vm.uiState.collect {} }
        val events = mutableListOf<HomeEvent>()
        backgroundScope.launch(dispatcher) { vm.homeEvents.collect { events += it } }

        vm.moveItem("a", target)
        vm.removeItem("a")
        vm.addApp(key, null)

        val repo = deps.workspaceRepository
        assertThat(repo.moves).isEmpty()
        assertThat(repo.removals).isEmpty()
        assertThat(repo.adds).isEmpty()
        assertThat(events).containsExactly(HomeEvent.LayoutLocked, HomeEvent.LayoutLocked, HomeEvent.LayoutLocked)
    }

    @Test
    fun unlockingAllowsEditsAgain() = runTest(dispatcher) {
        val deps = FakeHomeDependencies(appScope = appScope)
        deps.settingsRepository.state.value = LauncherSettings().let { it.copy(home = it.home.copy(layoutLocked = true)) }
        val vm = HomeViewModel(deps, SavedStateHandle(), guard(), false, dispatcher, dispatcher)
        backgroundScope.launch(dispatcher) { vm.uiState.collect {} }

        assertThat(vm.rejectIfLayoutLocked()).isTrue()
        deps.settingsRepository.state.value = LauncherSettings()
        assertThat(vm.rejectIfLayoutLocked()).isFalse()

        vm.moveItem("a", target)
        assertThat(deps.workspaceRepository.moves).containsExactly("a" to target)
    }
}
