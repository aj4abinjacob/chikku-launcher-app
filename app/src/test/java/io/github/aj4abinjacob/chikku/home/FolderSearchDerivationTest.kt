package io.github.aj4abinjacob.chikku.home

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.AppCustomization
import io.github.aj4abinjacob.chikku.core.model.AppsState
import io.github.aj4abinjacob.chikku.core.model.CellPlacement
import io.github.aj4abinjacob.chikku.core.model.ContainerType
import io.github.aj4abinjacob.chikku.core.model.FolderEntry
import io.github.aj4abinjacob.chikku.core.model.GridSpec
import io.github.aj4abinjacob.chikku.core.model.ItemTarget
import io.github.aj4abinjacob.chikku.core.model.ProfileType
import io.github.aj4abinjacob.chikku.core.model.Workspace
import io.github.aj4abinjacob.chikku.core.model.WorkspaceItem
import io.github.aj4abinjacob.chikku.core.model.WorkspaceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

/** Folder-search validation derives from the ViewModel's resolved workspace (no second build). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestHomeApplication::class)
class FolderSearchDerivationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dispatcher = UnconfinedTestDispatcher()
    private val appScope = CoroutineScope(SupervisorJob() + dispatcher)

    private val maps = app("Maps")
    private val mail = app("Mail")
    private val work = app("Slack", profile = ProfileType.WORK)

    private fun folder(id: String, title: String, vararg members: io.github.aj4abinjacob.chikku.core.model.LauncherApp, dock: Boolean = false) =
        WorkspaceItem(
            id,
            CellPlacement(if (dock) ContainerType.DOCK else ContainerType.DESKTOP, 0, if (dock) 1 else 2, 0),
            ItemTarget.Folder(title, members.mapIndexed { i, m -> FolderEntry("$id-$i", ItemTarget.App(m.key)) }),
        )

    private val personal = folder("personal", "Travel", maps, mail)
    private val docked = folder("docked", "Dock", mail, dock = true)
    private val mixed = folder("mixed", "Work", maps, work)
    private val untitled = folder("untitled", " ", maps)
    private val plain = WorkspaceItem("plain", CellPlacement(ContainerType.DESKTOP, 0, 0, 0), ItemTarget.App(maps.key))
    private val workspace = Workspace(GridSpec(4, 5), GridSpec(5, 1), 1, listOf(personal, docked, mixed, untitled, plain))
    private val loaded = AppsState.Loaded(listOf(maps, mail, work))

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        context.getSharedPreferences("guard_test", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After
    fun tearDown() {
        appScope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun onlyFullyVisiblePersonalTitledFoldersAreSearchable() {
        val ui = buildWorkspaceUi(workspace, loaded)
        val items = searchableFolders(workspace, loaded, ui, emptySet())!!
        assertThat(items.keys).containsExactly("personal", "docked")
        // The same resolved items HOME renders, so the activity's rendered == verified check holds.
        assertThat(items["personal"]).isEqualTo(ui.pages[0].first { it.id == "personal" })
        assertThat(items["docked"]).isEqualTo(ui.dock.first { it.id == "docked" })

        assertThat(searchableFolders(workspace, loaded, ui, setOf(mail.key))!!.keys).isEmpty()
    }

    @Test
    fun nothingIsSearchableUntilWorkspaceAndAppsAreLoaded() {
        val ui = buildWorkspaceUi(workspace, loaded)
        assertThat(searchableFolders(null, loaded, ui, emptySet())).isNull()
        assertThat(searchableFolders(workspace, AppsState.Loading, buildWorkspaceUi(workspace, AppsState.Loading), emptySet())).isNull()
        assertThat(searchableFolders(workspace, loaded, WorkspaceUi.Loading, emptySet())).isNull()
    }

    @Test
    fun viewModelFlowFollowsHiddenAppsWithoutASecondWorkspaceSubscription() = runTest(dispatcher) {
        val deps = FakeHomeDependencies(
            appScope = appScope,
            appsRepository = FakeAppsRepository(loaded),
            workspaceRepository = FakeWorkspaceRepository(WorkspaceState.Ready(workspace)),
        )
        val guard = StartupGuard({ context.getSharedPreferences("guard_test", Context.MODE_PRIVATE) }, dispatcher)
        val vm = HomeViewModel(deps, SavedStateHandle(), guard, false, dispatcher, dispatcher)
        backgroundScope.launch(dispatcher) { vm.uiState.collect {} }
        val seen = mutableListOf<Map<String, HomeItemUi>?>()
        backgroundScope.launch(dispatcher) { vm.searchableFolderItems.collect { seen += it } }

        assertThat(seen.last()!!.keys).containsExactly("personal", "docked")
        assertThat(deps.workspaceRepository.totalSubscriptions).isEqualTo(1)

        deps.appCustomizationRepository.state.value = mapOf(maps.key to AppCustomization(maps.key, hidden = true))
        assertThat(seen.last()!!.keys).containsExactly("docked")
    }
}
