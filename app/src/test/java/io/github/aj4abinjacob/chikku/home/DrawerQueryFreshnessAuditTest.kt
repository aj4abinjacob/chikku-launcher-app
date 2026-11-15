package io.github.aj4abinjacob.chikku.home

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.AppsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestHomeApplication::class)
class DrawerQueryFreshnessAuditTest {
    @Test fun goNeverUsesPreviousResultsDuringDebounceAndNoMatchRemainsAvailable() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val chrome = app("Chrome")
            val maps = app("Maps")
            val deps = FakeHomeDependencies(
                appScope = backgroundScope,
                appsRepository = FakeAppsRepository(AppsState.Loaded(listOf(chrome, maps))),
            )
            val vm = HomeViewModel(
                deps,
                SavedStateHandle(),
                StartupGuard({ context.getSharedPreferences("audit_query_guard", 0) }, dispatcher),
                false,
                dispatcher,
                dispatcher,
            )
            backgroundScope.launch(dispatcher) { vm.uiState.collect {} }
            assertThat(vm.firstResult()).isEqualTo(chrome.key)
            vm.setQuery("maps")
            assertThat(vm.firstResult()).isNull()
            assertThat(vm.isDrawerResultCurrent()).isFalse()
            advanceTimeBy(HomeViewModel.SEARCH_DEBOUNCE_MS + 1)
            runCurrent()
            assertThat(vm.firstResult()).isEqualTo(maps.key)
            vm.setQuery("no-such-app")
            assertThat(vm.firstResult()).isNull()
            assertThat(vm.isDrawerResultCurrent()).isFalse()
            advanceTimeBy(HomeViewModel.SEARCH_DEBOUNCE_MS + 1)
            runCurrent()
            assertThat(vm.firstResult()).isNull()
            assertThat(vm.isDrawerResultCurrent()).isTrue()
            assertThat(vm.uiState.value.drawer).isEqualTo(DrawerUi.NoMatches)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
