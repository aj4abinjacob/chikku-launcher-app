package io.github.aj4abinjacob.chikku.home

import android.content.Intent
import android.os.Looper
import android.view.ViewGroup
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.message.shownChikkuMessage
import io.github.aj4abinjacob.chikku.core.model.AppCustomization
import io.github.aj4abinjacob.chikku.core.model.AppsState
import io.github.aj4abinjacob.chikku.core.model.CellPlacement
import io.github.aj4abinjacob.chikku.core.model.ContainerType
import io.github.aj4abinjacob.chikku.core.model.FolderEntry
import io.github.aj4abinjacob.chikku.core.model.GestureAction
import io.github.aj4abinjacob.chikku.core.model.GridSpec
import io.github.aj4abinjacob.chikku.core.model.ItemActions
import io.github.aj4abinjacob.chikku.core.model.ItemTarget
import io.github.aj4abinjacob.chikku.core.model.Workspace
import io.github.aj4abinjacob.chikku.core.model.WorkspaceItem
import io.github.aj4abinjacob.chikku.core.model.WorkspaceState
import io.github.aj4abinjacob.chikku.home.folder.FolderGridLayout
import io.github.aj4abinjacob.chikku.search.SearchActivity
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestHomeApplication::class)
class SearchFolderIntentTest {
    private val application = ApplicationProvider.getApplicationContext<TestHomeApplication>()
    private val maps = app("Maps")
    private lateinit var deps: FakeHomeDependencies
    private val folder = WorkspaceItem("tools", CellPlacement(ContainerType.DESKTOP, 1, 0, 0),
        ItemTarget.Folder("Tools", listOf(FolderEntry("member", ItemTarget.App(maps.key)))),
        ItemActions(tap = GestureAction.LaunchApp(maps.key)))
    private val stored = WorkspaceState.Ready(Workspace(GridSpec(4, 5), GridSpec(5, 1), 2, listOf(folder)))

    @Before
    fun setUp() {
        application.getSharedPreferences("chikku_startup_guard", 0).edit().clear().commit()
        deps = FakeHomeDependencies(appsRepository = FakeAppsRepository(AppsState.Loaded(listOf(maps))),
            workspaceRepository = FakeWorkspaceRepository(WorkspaceState.Loading))
        application.homeDependencies = deps
    }

    private fun request(id: String = "tools") = Intent(application, HomeActivity::class.java)
        .setAction(SearchActivity.ACTION_OPEN_FOLDER).putExtra(SearchActivity.EXTRA_FOLDER_ID, id)

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline)
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
    }

    private fun ViewGroup.folderGrid(): FolderGridLayout? {
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child is FolderGridLayout) return child
            if (child is ViewGroup) child.folderGrid()?.let { return it }
        }
        return null
    }

    @Test
    fun createRequestWaitsForWorkspaceAndOpensPanelInsteadOfFolderCoverAction() {
        ActivityScenario.launch<HomeActivity>(request()).use { scenario ->
            lateinit var home: HomeActivity
            scenario.onActivity { home = it }
            assertThat(home.findViewById<ViewGroup>(R.id.home_root).folderGrid()).isNull()
            deps.workspaceRepository.state.value = stored
            waitUntil { home.findViewById<ViewGroup>(R.id.home_root).folderGrid() != null }
            assertThat(home.findViewById<WorkspacePager>(R.id.workspace_pager).currentPage).isEqualTo(1)
            assertThat(deps.appLauncher.launched).isEmpty()
            assertThat(home.intent.hasExtra(SearchActivity.EXTRA_FOLDER_ID)).isFalse()
        }
    }

    @Test
    fun hiddenFolderRequestIsRejectedAndUnknownIdCannotOpenAnything() {
        deps.workspaceRepository.state.value = stored
        deps.appCustomizationRepository.state.value = mapOf(maps.key to AppCustomization(maps.key, hidden = true))
        ActivityScenario.launch<HomeActivity>(request()).use { scenario ->
            lateinit var home: HomeActivity
            scenario.onActivity { home = it }
            waitUntil { home.shownChikkuMessage()?.toString() == home.getString(R.string.search_folder_unavailable) }
            assertThat(home.findViewById<ViewGroup>(R.id.home_root).folderGrid()).isNull()
            home.handleHomeIntent(request("unknown"))
            shadowOf(Looper.getMainLooper()).idle()
            assertThat(home.findViewById<ViewGroup>(R.id.home_root).folderGrid()).isNull()
            assertThat(deps.appLauncher.launched).isEmpty()
        }
    }

    @Test
    fun successiveSearchRequestsOpenDifferentFoldersWithoutRepositoryChanges() {
        val other = folder.copy(id = "other", target = ItemTarget.Folder("Other", listOf(FolderEntry("other-member", ItemTarget.App(maps.key)))))
        deps.workspaceRepository.state.value = stored.copy(workspace = stored.workspace.copy(items = listOf(folder, other)))
        ActivityScenario.launch<HomeActivity>(request()).use { scenario ->
            lateinit var home: HomeActivity
            scenario.onActivity { home = it }
            val root = home.findViewById<ViewGroup>(R.id.home_root)
            waitUntil { root.folderGrid() != null }
            home.handleHomeIntent(request("other"))
            fun ViewGroup.findTitle(): EditText? {
                for (index in 0 until childCount) {
                    val child = getChildAt(index)
                    if (child is EditText) return child
                    if (child is ViewGroup) child.findTitle()?.let { return it }
                }
                return null
            }
            waitUntil { (root.getChildAt(root.childCount - 1) as? ViewGroup)?.findTitle()?.text?.toString() == "Other" }
            assertThat(deps.appLauncher.launched).isEmpty()
        }
    }

    @Test
    fun newIntentOpensKnownFolderAndProfileRemovalClosesItsPanel() {
        deps.workspaceRepository.state.value = stored
        ActivityScenario.launch(HomeActivity::class.java).use { scenario ->
            lateinit var home: HomeActivity
            scenario.onActivity { home = it }
            waitUntil { home.findViewById<WorkspacePager>(R.id.workspace_pager).pageCount == 2 }
            home.handleHomeIntent(request())
            waitUntil { home.findViewById<ViewGroup>(R.id.home_root).folderGrid() != null }
            deps.appsRepository.state.value = AppsState.Loaded(emptyList())
            waitUntil { home.findViewById<ViewGroup>(R.id.home_root).folderGrid() == null }
        }
    }
}
