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
import io.github.aj4abinjacob.chikku.core.model.ProfileType
import io.github.aj4abinjacob.chikku.core.model.RejectReason
import io.github.aj4abinjacob.chikku.core.model.Workspace
import io.github.aj4abinjacob.chikku.core.model.WorkspaceItem
import io.github.aj4abinjacob.chikku.core.model.WorkspaceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestHomeApplication::class)
class HomeViewModelTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dispatcher = UnconfinedTestDispatcher()
    private val appScope = CoroutineScope(SupervisorJob() + dispatcher)

    private val chrome = app("Chrome")
    private val mail = app("Mail", profile = ProfileType.WORK)
    private val maps = app("Maps")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        context.getSharedPreferences("guard_test", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("ui_test", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After
    fun tearDown() {
        appScope.cancel()
        Dispatchers.resetMain()
    }

    private fun guard() = StartupGuard({ context.getSharedPreferences("guard_test", Context.MODE_PRIVATE) }, dispatcher)

    private fun TestScope.newViewModel(deps: FakeHomeDependencies, initialSafeMode: Boolean? = false): HomeViewModel {
        val vm = HomeViewModel(deps, SavedStateHandle(), guard(), initialSafeMode, dispatcher, dispatcher)
        backgroundScope.launch(dispatcher) { vm.uiState.collect {} }
        return vm
    }

    private fun deps(
        apps: AppsState = AppsState.Loading,
        workspace: WorkspaceState = WorkspaceState.Loading,
    ) = FakeHomeDependencies(
        appScope = appScope,
        appsRepository = FakeAppsRepository(apps),
        workspaceRepository = FakeWorkspaceRepository(workspace),
    )

    private fun workspaceWith(vararg items: Pair<String, AppKey>, dock: Pair<String, AppKey>? = null): WorkspaceState.Ready {
        val desktop = items.mapIndexed { i, (id, key) ->
            WorkspaceItem(id, CellPlacement(ContainerType.DESKTOP, 0, i, 0), ItemTarget.App(key))
        }
        val dockItems = listOfNotNull(dock).map { (id, key) ->
            WorkspaceItem(id, CellPlacement(ContainerType.DOCK, 0, 0, 0), ItemTarget.App(key))
        }
        return WorkspaceState.Ready(Workspace(GridSpec(4, 5), GridSpec(5, 1), 1, desktop + dockItems))
    }

    @Test
    fun initialStateIsLoading() = runTest(dispatcher) {
        val vm = newViewModel(deps())
        val state = vm.uiState.value
        assertThat(state.workspace).isEqualTo(WorkspaceUi.Loading)
        assertThat(state.drawer).isEqualTo(DrawerUi.Loading)
        assertThat(state.safeMode).isFalse()
        assertThat(state.drawerOpen).isFalse()
    }

    @Test
    fun failedAppsKeepWorkspaceUsable() = runTest(dispatcher) {
        val deps = deps(AppsState.Failed("boom"), workspaceWith("a" to chrome.key))
        val vm = newViewModel(deps)
        val state = vm.uiState.value
        assertThat(state.drawer).isInstanceOf(DrawerUi.Failed::class.java)
        val ready = state.workspace as WorkspaceUi.Ready
        val content = ready.pages[0].single().content as ItemContent.App
        assertThat(content.availability).isEqualTo(Availability.UNKNOWN)

        vm.retryApps()
        assertThat(deps.appsRepository.refreshCount).isEqualTo(1)
    }

    @Test
    fun failedWorkspaceKeepsDrawerUsableAndRetryResubscribes() = runTest(dispatcher) {
        val deps = deps(AppsState.Loaded(listOf(chrome, maps)), WorkspaceState.Failed("db"))
        val vm = newViewModel(deps)
        assertThat(vm.uiState.value.workspace).isEqualTo(WorkspaceUi.Failed("db"))
        assertThat(vm.uiState.value.drawer).isEqualTo(DrawerUi.Apps(listOf(chrome, maps)))

        val before = deps.workspaceRepository.totalSubscriptions
        deps.workspaceRepository.state.value = workspaceWith("a" to chrome.key)
        vm.retryWorkspace()
        assertThat(deps.workspaceRepository.totalSubscriptions).isEqualTo(before + 1)
        assertThat(vm.uiState.value.workspace).isInstanceOf(WorkspaceUi.Ready::class.java)
    }

    @Test
    fun missingAppRendersAsPlaceholderAndIsNotRemoved() = runTest(dispatcher) {
        val gone = AppKey("pkg.gone", "pkg.gone.Main", 0)
        val deps = deps(
            AppsState.Loaded(listOf(chrome, mail)),
            workspaceWith("a" to chrome.key, "b" to gone, dock = "d" to mail.key),
        )
        val vm = newViewModel(deps)
        val ready = vm.uiState.value.workspace as WorkspaceUi.Ready

        val (present, missing) = ready.pages[0].map { it.content as ItemContent.App }
        assertThat(present).isEqualTo(ItemContent.App(chrome.key, "Chrome", false, Availability.AVAILABLE))
        assertThat(missing).isEqualTo(ItemContent.App(gone, null, false, Availability.UNAVAILABLE))
        val docked = ready.dock.single().content as ItemContent.App
        assertThat(docked.isWork).isTrue()
        assertThat(deps.workspaceRepository.removals).isEmpty()
    }

    @Test
    fun itemsOnPagesBeyondPageCountAreStillShown() {
        val ws = Workspace(
            GridSpec(4, 5), GridSpec(5, 1), pageCount = 1,
            items = listOf(WorkspaceItem("x", CellPlacement(ContainerType.DESKTOP, 2, 0, 0), ItemTarget.App(chrome.key))),
        )
        val ui = buildWorkspaceUi(ws, AppsState.Loaded(listOf(chrome)))
        assertThat(ui.pages).hasSize(3)
        assertThat(ui.pages[2].single().id).isEqualTo("x")
    }

    @Test
    fun searchIsDebouncedAndNewestQueryWins() = runTest(dispatcher) {
        val deps = deps(AppsState.Loaded(listOf(chrome, mail, maps)))
        val vm = newViewModel(deps)

        vm.setQuery("m")
        advanceTimeBy(50)
        vm.setQuery("ma")
        advanceTimeBy(50)
        vm.setQuery("map")
        assertThat(vm.uiState.value.query).isEqualTo("map") // field echo is immediate
        advanceTimeBy(HomeViewModel.SEARCH_DEBOUNCE_MS + 1)

        assertThat(deps.appSearcher.queries).containsExactly("map")
        assertThat(vm.uiState.value.drawer).isEqualTo(DrawerUi.Apps(listOf(maps)))

        vm.setQuery("zzz")
        advanceUntilIdle()
        assertThat(vm.uiState.value.drawer).isEqualTo(DrawerUi.NoMatches)

        vm.setQuery("")
        assertThat(vm.uiState.value.drawer).isEqualTo(DrawerUi.Apps(listOf(chrome, mail, maps)))
    }

    @Test
    fun firstKeystrokeKeepsTheGridInsteadOfShowingLoading() = runTest(dispatcher) {
        val vm = newViewModel(deps(AppsState.Loaded(listOf(chrome, mail, maps))))
        val states = mutableListOf<DrawerUi>()
        backgroundScope.launch(dispatcher) { vm.uiState.collect { states += it.drawer } }
        assertThat(vm.uiState.value.drawer).isEqualTo(DrawerUi.Apps(listOf(chrome, mail, maps)))

        vm.setQuery("m")
        // Debounce still pending: the previous content stays, marked as not yet current.
        assertThat(vm.uiState.value.drawer).isEqualTo(DrawerUi.Apps(listOf(chrome, mail, maps)))
        assertThat(vm.uiState.value.drawerQuery).isEqualTo("")
        assertThat(vm.isDrawerResultCurrent()).isFalse()
        advanceTimeBy(HomeViewModel.SEARCH_DEBOUNCE_MS + 1)
        assertThat(vm.uiState.value.drawerQuery).isEqualTo("m")
        assertThat(vm.isDrawerResultCurrent()).isTrue()
        vm.setQuery("ma")
        vm.setQuery("")
        advanceUntilIdle()
        assertThat(states).doesNotContain(DrawerUi.Loading)
    }

    /** Guard: true cold-start loading (no app list yet) must still surface as Loading, query or not. */
    @Test
    fun loadingIsStillShownUntilTheFirstAppListArrives() = runTest(dispatcher) {
        val deps = deps(AppsState.Loading)
        val vm = newViewModel(deps)
        vm.setQuery("c")
        advanceTimeBy(HomeViewModel.SEARCH_DEBOUNCE_MS + 1)
        assertThat(vm.uiState.value.drawer).isEqualTo(DrawerUi.Loading)
        assertThat(vm.isDrawerResultCurrent()).isFalse()
        assertThat(vm.firstResult()).isNull()

        (deps.appsRepository as FakeAppsRepository).state.value = AppsState.Loaded(listOf(chrome, mail, maps))
        advanceUntilIdle()
        assertThat(vm.uiState.value.drawer).isEqualTo(DrawerUi.Apps(listOf(chrome)))
        assertThat(vm.isDrawerResultCurrent()).isTrue()
    }

    @Test
    fun hiddenAndRemovedProfileAppsStayOutWhileAResultIsPending() = runTest(dispatcher) {
        val deps = deps(AppsState.Loaded(listOf(chrome, mail, maps)))
        val vm = newViewModel(deps)
        vm.setQuery("m") // debounce pending: the old (unfiltered) list is still on screen
        assertThat(vm.isDrawerResultCurrent()).isFalse()

        deps.appCustomizationRepository.state.value = mapOf(maps.key to AppCustomization(maps.key, hidden = true))
        assertThat((vm.uiState.value.drawer as DrawerUi.Apps).apps).doesNotContain(maps)

        // Work profile locked or paused: its apps leave the list before the result is current.
        (deps.appsRepository as FakeAppsRepository).state.value = AppsState.Loaded(listOf(chrome, maps))
        assertThat(vm.isDrawerResultCurrent()).isFalse()
        assertThat((vm.uiState.value.drawer as DrawerUi.Apps).apps).containsExactly(chrome)
        advanceUntilIdle()
        assertThat(vm.uiState.value.drawer).isEqualTo(DrawerUi.Apps(listOf(chrome)))
    }

    @Test
    fun staleResultCannotBeLaunchedFromTheImeAction() = runTest(dispatcher) {
        val vm = newViewModel(deps(AppsState.Loaded(listOf(chrome, mail, maps))))
        assertThat(vm.firstResult()).isEqualTo(chrome.key)
        vm.setQuery("map")
        assertThat(vm.isDrawerResultCurrent()).isFalse()
        assertThat(vm.firstResult()).isNull() // the stale grid's first app must not launch
        advanceUntilIdle()
        assertThat(vm.firstResult()).isEqualTo(maps.key)
    }

    @Test
    fun noMatchesOfAnOlderQueryIsMarkedStaleForTheNewQuery() = runTest(dispatcher) {
        val vm = newViewModel(deps(AppsState.Loaded(listOf(chrome, mail, maps))))
        vm.setQuery("xyz")
        advanceUntilIdle()
        assertThat(vm.uiState.value.drawer).isEqualTo(DrawerUi.NoMatches)
        assertThat(vm.uiState.value.drawerQuery).isEqualTo("xyz")

        vm.setQuery("m")
        // The renderer receives NoMatches for "xyz" while the field says "m": it must hide it.
        assertThat(vm.uiState.value.drawerQuery).isNotEqualTo(vm.uiState.value.query)
        assertThat(vm.isDrawerResultCurrent()).isFalse()
        advanceUntilIdle()
        assertThat(vm.uiState.value.drawerQuery).isEqualTo("m")
        assertThat(vm.uiState.value.drawer).isEqualTo(DrawerUi.Apps(listOf(chrome, mail, maps)))
    }

    @Test
    fun emptyAppListShowsNoApps() = runTest(dispatcher) {
        val vm = newViewModel(deps(AppsState.Loaded(emptyList())))
        assertThat(vm.uiState.value.drawer).isEqualTo(DrawerUi.NoApps)
    }

    @Test
    fun closingDrawerClearsQuery() = runTest(dispatcher) {
        val vm = newViewModel(deps(AppsState.Loaded(listOf(chrome))))
        vm.openDrawer()
        vm.setQuery("chr")
        assertThat(vm.uiState.value.drawerOpen).isTrue()
        vm.closeDrawer()
        assertThat(vm.uiState.value.drawerOpen).isFalse()
        assertThat(vm.uiState.value.query).isEmpty()
    }

    @Test
    fun safeModeAfterRepeatedUnfinishedStartupsSkipsWorkspace() = runTest(dispatcher) {
        val deps = deps(AppsState.Loaded(listOf(chrome)), workspaceWith("a" to chrome.key))
        val vm = newViewModel(deps, initialSafeMode = true)

        assertThat(vm.uiState.value.safeMode).isTrue()
        assertThat(vm.uiState.value.workspace).isEqualTo(WorkspaceUi.Disabled)
        assertThat(deps.workspaceRepository.totalSubscriptions).isEqualTo(0)
        assertThat(vm.uiState.value.drawer).isEqualTo(DrawerUi.Apps(listOf(chrome)))

        vm.leaveSafeMode()
        assertThat(vm.uiState.value.safeMode).isFalse()
        assertThat(vm.uiState.value.workspace).isInstanceOf(WorkspaceUi.Ready::class.java)
    }

    @Test
    fun corruptLayoutNoticeFollowsTheLiveMarkerAndDismissAcknowledges() = runTest(dispatcher) {
        val deps = deps()
        val vm = newViewModel(deps)
        assertThat(vm.uiState.value.showCorruptLayoutNotice).isFalse()

        // Corruption detected after HOME started (first database open) must still be announced.
        deps.corruptLayoutNotice.value = true
        assertThat(vm.uiState.value.showCorruptLayoutNotice).isTrue()
        vm.dismissCorruptNotice()
        assertThat(vm.uiState.value.showCorruptLayoutNotice).isFalse()
        assertThat(deps.acknowledgements).isEqualTo(1)

        // A later corruption is announced again.
        deps.corruptLayoutNotice.value = true
        assertThat(vm.uiState.value.showCorruptLayoutNotice).isTrue()
    }

    @Test
    fun recreatedViewModelReadsPersistedSafeModeWithoutCounting() = runTest(dispatcher) {
        repeat(StartupGuard.DEFAULT_THRESHOLD) { guard().recordStartup() }
        val vm = newViewModel(deps(), initialSafeMode = null)
        assertThat(vm.uiState.value.safeMode).isTrue()
    }

    @Test
    fun itemsOfAProfileThatWasNotListedAreNotCalledUninstalled() {
        val workApp = AppKey("com.example.mail", "com.example.mail.Main", userSerial = 10)
        val ws = Workspace(
            GridSpec(4, 5), GridSpec(5, 1), pageCount = 1,
            items = listOf(WorkspaceItem("w", CellPlacement(ContainerType.DESKTOP, 0, 0, 0), ItemTarget.App(workApp))),
        )
        val unlisted = buildWorkspaceUi(ws, AppsState.Loaded(listOf(chrome), listedProfiles = setOf(chrome.key.userSerial)))
        assertThat((unlisted.pages[0].single().content as ItemContent.App).availability).isEqualTo(Availability.UNKNOWN)

        val listed = buildWorkspaceUi(ws, AppsState.Loaded(listOf(chrome), listedProfiles = setOf(chrome.key.userSerial, 10L)))
        assertThat((listed.pages[0].single().content as ItemContent.App).availability).isEqualTo(Availability.UNAVAILABLE)
    }

    @Test
    fun bannerShowsWhenNotDefaultAndCanBeDismissed() = runTest(dispatcher) {
        val deps = deps().also { it.homeRoleController.isDefault = false }
        val vm = newViewModel(deps)
        assertThat(vm.uiState.value.showDefaultHomeBanner).isFalse() // unknown until checked
        vm.refreshDefaultHomeStatus()
        assertThat(vm.uiState.value.showDefaultHomeBanner).isTrue()
        vm.dismissDefaultHomeBanner()
        assertThat(vm.uiState.value.showDefaultHomeBanner).isFalse()
    }

    @Test
    fun editsReportUndoAndRejections() = runTest(dispatcher) {
        val deps = deps(AppsState.Loaded(listOf(chrome)), workspaceWith("a" to chrome.key))
        val vm = newViewModel(deps)
        val events = mutableListOf<HomeEvent>()
        backgroundScope.launch(dispatcher) { vm.homeEvents.toList(events) }

        vm.removeItem("a")
        assertThat(deps.workspaceRepository.removals).containsExactly("a")
        vm.undoLastEdit()
        assertThat(deps.workspaceRepository.undoCount).isEqualTo(1)

        deps.workspaceRepository.nextResult = FakeWorkspaceRepository.Rejected
        val target = CellPlacement(ContainerType.DESKTOP, 0, 1, 1)
        vm.moveItem("a", target)
        assertThat(deps.workspaceRepository.moves).containsExactly("a" to target)

        assertThat(events).containsExactly(HomeEvent.ItemRemoved, HomeEvent.EditRejected(RejectReason.OCCUPIED)).inOrder()
    }

    @Test
    fun unavailableLaunchProducesEventNotCrash() = runTest(dispatcher) {
        val deps = deps(AppsState.Loaded(listOf(chrome)))
        deps.appLauncher.result = io.github.aj4abinjacob.chikku.platform.LaunchResult.Unavailable(
            io.github.aj4abinjacob.chikku.platform.LaunchResult.Reason.NOT_FOUND,
        )
        val vm = newViewModel(deps)
        val events = mutableListOf<HomeEvent>()
        backgroundScope.launch(dispatcher) { vm.homeEvents.toList(events) }
        vm.launchApp(chrome.key, null)
        assertThat(events).containsExactly(HomeEvent.LaunchUnavailable)
    }

    @Test
    fun lockedLaunchAsksToUnlockAndRetriesTheSameLaunch() = runTest(dispatcher) {
        val deps = deps(AppsState.Loaded(listOf(chrome)))
        deps.appLauncher.result = io.github.aj4abinjacob.chikku.platform.LaunchResult.Locked(chrome.key.packageName, chrome.key.userSerial)
        val vm = newViewModel(deps)
        val events = mutableListOf<HomeEvent>()
        backgroundScope.launch(dispatcher) { vm.homeEvents.toList(events) }
        vm.launchApp(chrome.key, null)

        val locked = events.single() as HomeEvent.AppLocked
        assertThat(locked.locked.packageName).isEqualTo(chrome.key.packageName)
        // After the system confirms the owner, the retry launches the same app.
        deps.appLauncher.result = io.github.aj4abinjacob.chikku.platform.LaunchResult.Started
        locked.retry()
        assertThat(deps.appLauncher.launched).containsExactly(chrome.key, chrome.key).inOrder()
        assertThat(events.last()).isEqualTo(HomeEvent.AppLaunched)
    }
}
