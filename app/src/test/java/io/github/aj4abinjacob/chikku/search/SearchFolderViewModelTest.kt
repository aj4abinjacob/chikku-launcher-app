package io.github.aj4abinjacob.chikku.search

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.AppCustomization
import io.github.aj4abinjacob.chikku.core.model.AppsState
import io.github.aj4abinjacob.chikku.core.model.CellPlacement
import io.github.aj4abinjacob.chikku.core.model.ContainerType
import io.github.aj4abinjacob.chikku.core.model.FolderEntry
import io.github.aj4abinjacob.chikku.core.model.GridSpec
import io.github.aj4abinjacob.chikku.core.model.ItemTarget
import io.github.aj4abinjacob.chikku.core.model.Workspace
import io.github.aj4abinjacob.chikku.core.model.WorkspaceItem
import io.github.aj4abinjacob.chikku.core.model.WorkspaceState
import io.github.aj4abinjacob.chikku.core.model.search.ContactLookup
import io.github.aj4abinjacob.chikku.core.model.search.DefaultAppSearcher
import io.github.aj4abinjacob.chikku.core.model.search.ShortcutLookup
import io.github.aj4abinjacob.chikku.core.model.search.UniversalSearch
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.core.model.settings.SearchSettings
import io.github.aj4abinjacob.chikku.home.FakeAppCustomizationRepository
import io.github.aj4abinjacob.chikku.home.FakeAppsRepository
import io.github.aj4abinjacob.chikku.home.FakeSettingsRepository
import io.github.aj4abinjacob.chikku.home.FakeWorkspaceRepository
import io.github.aj4abinjacob.chikku.home.app
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchFolderViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val app = app("App")
    private val apps = FakeAppsRepository(AppsState.Loaded(listOf(app)))
    private val custom = FakeAppCustomizationRepository()
    private val settings = FakeSettingsRepository(LauncherSettings(search = SearchSettings(foldersEnabled = true)))
    private val folder = WorkspaceItem("tools", CellPlacement(ContainerType.DESKTOP, 0, 0, 0),
        ItemTarget.Folder("Tools", listOf(FolderEntry("member", ItemTarget.App(app.key)))))
    private val workspace = FakeWorkspaceRepository(WorkspaceState.Ready(Workspace(GridSpec(5, 5), GridSpec(5, 1), 1, listOf(folder))))

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun hiddenFolderTitleDisappearsImmediatelyEvenWhileReplacementContactsReadWaits() = runTest(dispatcher) {
        var gate: CompletableDeferred<Unit>? = null
        val search = UniversalSearch(DefaultAppSearcher(), { ShortcutLookup.NotDefaultHome }, { _, _ ->
            gate?.await()
            ContactLookup.Unavailable
        })
        val vm = SearchViewModel(SavedStateHandle(), settings, apps, custom, search, dispatcher, workspaceRepository = workspace)
        backgroundScope.launch { vm.results.collect {} }
        vm.setQuery("tools")
        advanceTimeBy(SearchViewModel.DEBOUNCE_MILLIS + 1)
        runCurrent()
        assertThat(vm.results.value.folders.map { it.itemId }).containsExactly("tools")
        gate = CompletableDeferred()
        settings.state.value = settings.state.value.copy(search = settings.state.value.search.copy(contactsEnabled = true))
        runCurrent()
        custom.state.value = mapOf(app.key to AppCustomization(app.key, hidden = true))
        runCurrent()
        assertThat(vm.results.value.folders).isEmpty()
        gate!!.complete(Unit)
        runCurrent()
        assertThat(vm.results.value.folders).isEmpty()
    }

    @Test
    fun workspaceLossAndSwitchDisableRemoveResultsAndRecoveryRestoresKnownFolder() = runTest(dispatcher) {
        val search = UniversalSearch(DefaultAppSearcher(), { ShortcutLookup.NotDefaultHome }, { _, _ -> ContactLookup.Unavailable })
        val vm = SearchViewModel(SavedStateHandle(), settings, apps, custom, search, dispatcher, workspaceRepository = workspace)
        backgroundScope.launch { vm.results.collect {} }
        vm.setQuery("tools")
        advanceTimeBy(SearchViewModel.DEBOUNCE_MILLIS + 1)
        runCurrent()
        assertThat(vm.results.value.folders).hasSize(1)
        val saved = workspace.state.value
        workspace.state.value = WorkspaceState.Failed("unavailable")
        runCurrent()
        assertThat(vm.results.value.folders).isEmpty()
        workspace.state.value = saved
        runCurrent()
        assertThat(vm.results.value.folders).hasSize(1)
        settings.state.value = settings.state.value.copy(search = settings.state.value.search.copy(foldersEnabled = false))
        runCurrent()
        assertThat(vm.results.value.folders).isEmpty()
    }
}
