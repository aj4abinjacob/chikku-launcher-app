package io.github.aj4abinjacob.chikku.home.folder

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.AppKey
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
import io.github.aj4abinjacob.chikku.core.model.settings.FolderIconBackground
import io.github.aj4abinjacob.chikku.core.model.settings.FolderPreviewStyle
import io.github.aj4abinjacob.chikku.home.AppIconView
import io.github.aj4abinjacob.chikku.home.CellLayout
import io.github.aj4abinjacob.chikku.home.FakeAppsRepository
import io.github.aj4abinjacob.chikku.home.FakeHomeDependencies
import io.github.aj4abinjacob.chikku.home.FakeWorkspaceRepository
import io.github.aj4abinjacob.chikku.home.HomeActivity
import io.github.aj4abinjacob.chikku.home.TestHomeApplication
import io.github.aj4abinjacob.chikku.home.WorkspacePager
import io.github.aj4abinjacob.chikku.home.app
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Folders in the real HomeActivity against fakes: icon, open/close, launch, rename, gestures. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestHomeApplication::class)
class FolderHomeTest {

    private val application = ApplicationProvider.getApplicationContext<TestHomeApplication>()
    private val maps = app("Maps")
    private val transit = app("Transit")
    private val gone = AppKey("pkg.gone", "pkg.gone.Main", 0)
    private lateinit var deps: FakeHomeDependencies

    private fun workspace(actions: ItemActions = ItemActions()) = Workspace(
        desktopGrid = GridSpec(4, 5),
        dockGrid = GridSpec(5, 1),
        pageCount = 1,
        items = listOf(
            WorkspaceItem(
                "folder", CellPlacement(ContainerType.DESKTOP, 0, 0, 0),
                ItemTarget.Folder(
                    "Navigation",
                    listOf(
                        FolderEntry("e1", ItemTarget.App(maps.key)),
                        FolderEntry("e2", ItemTarget.App(transit.key)),
                        FolderEntry("e3", ItemTarget.App(gone)),
                    ),
                ),
                actions,
            ),
            WorkspaceItem("dockFolder", CellPlacement(ContainerType.DOCK, 0, 0, 0), ItemTarget.Folder("", emptyList())),
        ),
    )

    @Before
    fun setUp() {
        application.getSharedPreferences("chikku_startup_guard", 0).edit().clear().commit()
        deps = FakeHomeDependencies(
            appsRepository = FakeAppsRepository(AppsState.Loaded(listOf(maps, transit))),
            workspaceRepository = FakeWorkspaceRepository(WorkspaceState.Ready(workspace())),
        )
        application.homeDependencies = deps
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "condition not met in time" }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
    }

    private fun withHome(block: (HomeActivity) -> Unit) {
        ActivityScenario.launch(HomeActivity::class.java).use { scenario ->
            lateinit var activity: HomeActivity
            scenario.onActivity { activity = it }
            val pager = activity.findViewById<WorkspacePager>(R.id.workspace_pager)
            val dock = activity.findViewById<CellLayout>(R.id.dock)
            waitUntil { pager.pageCount == 1 && pager.pageAt(0)!!.childCount == 1 && dock.childCount == 1 }
            block(activity)
        }
    }

    private fun HomeActivity.folderIcon(): AppIconView =
        findViewById<WorkspacePager>(R.id.workspace_pager).pageAt(0)!!.getChildAt(0) as AppIconView

    /** The open-folder panel is the last child of the root while a folder is open. */
    private fun HomeActivity.panel(): ViewGroup? {
        val root = findViewById<FrameLayout>(R.id.home_root)
        return (root.getChildAt(root.childCount - 1) as? ViewGroup)?.takeIf { it.findGrid() != null }
    }

    private fun ViewGroup.findGrid(): FolderGridLayout? {
        for (i in 0 until childCount) {
            when (val child = getChildAt(i)) {
                is FolderGridLayout -> return child
                is ViewGroup -> child.findGrid()?.let { return it }
            }
        }
        return null
    }

    private fun ViewGroup.findEditText(): EditText? {
        for (i in 0 until childCount) {
            when (val child = getChildAt(i)) {
                is EditText -> return child
                is ViewGroup -> child.findEditText()?.let { return it }
            }
        }
        return null
    }

    @Test
    fun folderIconShowsTitleAndDescription() = withHome { activity ->
        val icon = activity.folderIcon()
        assertThat(icon.label).isEqualTo("Navigation")
        assertThat(icon.contentDescription.toString()).isEqualTo("Folder Navigation, 3 items")
        assertThat(icon.icon).isInstanceOf(FolderIconDrawable::class.java)
        val dockIcon = activity.findViewById<CellLayout>(R.id.dock).getChildAt(0) as AppIconView
        assertThat(dockIcon.label).isEqualTo(activity.getString(R.string.home_folder_unnamed))
    }

    @Test
    fun tapOpensFolderEntryLaunchesAndBackCloses() = withHome { activity ->
        activity.folderIcon().performClick()
        waitUntil { activity.panel() != null }
        val grid = activity.panel()!!.findGrid()!!
        assertThat(grid.childCount).isEqualTo(3)
        assertThat((grid.getChildAt(2) as AppIconView).label).isEqualTo(activity.getString(R.string.home_unavailable_app))

        grid.getChildAt(1).performClick()
        assertThat(deps.appLauncher.launched).containsExactly(transit.key)
        waitUntil { activity.panel() == null } // launching closes the folder

        activity.folderIcon().performClick()
        waitUntil { activity.panel() != null }
        activity.onBackPressedDispatcher.onBackPressed()
        waitUntil { activity.panel() == null }
    }

    @Test
    fun renamingInlineIsSavedOnClose() = withHome { activity ->
        activity.folderIcon().performClick()
        waitUntil { activity.panel() != null }
        activity.panel()!!.findEditText()!!.setText("  Maps & transit ")
        activity.onBackPressedDispatcher.onBackPressed()
        waitUntil { deps.workspaceRepository.calls.contains("renameFolder") }
    }

    @Test
    fun customTapActionLaunchesFirstAppInsteadOfOpening() {
        deps.workspaceRepository.state.value =
            WorkspaceState.Ready(workspace(ItemActions(tap = GestureAction.LaunchApp(maps.key))))
        withHome { activity ->
            activity.folderIcon().performClick()
            assertThat(deps.appLauncher.launched).containsExactly(maps.key)
            shadowOf(Looper.getMainLooper()).idle()
            assertThat(activity.panel()).isNull()

            // Swipe to open, also reachable through TalkBack.
            val icon = activity.folderIcon()
            val info = android.view.accessibility.AccessibilityNodeInfo.obtain()
            icon.onInitializeAccessibilityNodeInfo(info)
            val swipeUp = info.actionList.first { it.label == activity.getString(R.string.folder_action_swipe_up) }
            assertThat(icon.performAccessibilityAction(swipeUp.id, null)).isTrue()
            waitUntil { activity.panel() != null }
        }
    }

    @Test
    fun disabledFolderGesturesIgnoreTheCustomTap() {
        deps.settingsRepository.state.value = deps.settingsRepository.state.value.let {
            it.copy(folders = it.folders.copy(swipeActionsEnabled = false))
        }
        deps.workspaceRepository.state.value =
            WorkspaceState.Ready(workspace(ItemActions(tap = GestureAction.LaunchApp(maps.key))))
        withHome { activity ->
            shadowOf(Looper.getMainLooper()).idle()
            activity.folderIcon().performClick()
            waitUntil { activity.panel() != null }
            assertThat(deps.appLauncher.launched).isEmpty()
        }
    }

    @Test
    fun everyPreviewStyleAndBackgroundDraws() {
        val bitmap = android.graphics.Bitmap.createBitmap(96, 96, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        for (count in 0..5) {
            for (style in FolderPreviewStyle.entries) {
                for (background in FolderIconBackground.entries) {
                    val previews = List(count) { android.graphics.drawable.ColorDrawable(android.graphics.Color.RED) }
                    FolderIconDrawable(previews, style, background, android.graphics.Color.WHITE).apply {
                        setBounds(0, 0, 96, 96)
                        draw(canvas)
                    }
                }
            }
        }
    }

    @Test
    fun accessibilityActionsMoveWithinFolderAndToHome() = withHome { activity ->
        activity.folderIcon().performClick()
        waitUntil { activity.panel() != null }
        val first = activity.panel()!!.findGrid()!!.getChildAt(0)
        val info = android.view.accessibility.AccessibilityNodeInfo.obtain()
        first.onInitializeAccessibilityNodeInfo(info)
        val labels = info.actionList.mapNotNull { it.label?.toString() }
        assertThat(labels).containsAtLeast(
            activity.getString(R.string.folder_action_move_later),
            activity.getString(R.string.folder_action_move_to_home),
            activity.getString(R.string.folder_action_remove_entry),
        )
        val moveLater = info.actionList.first { it.label == activity.getString(R.string.folder_action_move_later) }
        assertThat(first.performAccessibilityAction(moveLater.id, null)).isTrue()
        val toHome = info.actionList.first { it.label == activity.getString(R.string.folder_action_move_to_home) }
        assertThat(first.performAccessibilityAction(toHome.id, null)).isTrue()
        waitUntil { deps.workspaceRepository.calls.containsAll(listOf("reorderFolder", "removeFromFolder")) }
        assertThat(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS)
            .isEqualTo(activity.findViewById<View>(R.id.home_content).importantForAccessibility)
    }

    @Test
    fun folderSwipeActionRunsThroughTheGestureExecutor() {
        deps.workspaceRepository.state.value =
            WorkspaceState.Ready(workspace(ItemActions(swipeDown = GestureAction.LaunchApp(transit.key))))
        withHome { activity ->
            val icon = activity.folderIcon()
            val info = android.view.accessibility.AccessibilityNodeInfo.obtain()
            icon.onInitializeAccessibilityNodeInfo(info)
            val swipeDown = info.actionList.first { it.label == activity.getString(R.string.folder_action_swipe_down) }
            assertThat(icon.performAccessibilityAction(swipeDown.id, null)).isTrue()
            assertThat(deps.appLauncher.launched).containsExactly(transit.key)
        }
    }

    @Test
    fun lockedLayoutRefusesFolderEdits() {
        deps.settingsRepository.state.value = deps.settingsRepository.state.value.let {
            it.copy(home = it.home.copy(layoutLocked = true))
        }
        withHome { activity ->
            shadowOf(Looper.getMainLooper()).idle()
            activity.folderIcon().performClick()
            waitUntil { activity.panel() != null }
            val first = activity.panel()!!.findGrid()!!.getChildAt(0)
            val info = android.view.accessibility.AccessibilityNodeInfo.obtain()
            first.onInitializeAccessibilityNodeInfo(info)
            val toHome = info.actionList.first { it.label == activity.getString(R.string.folder_action_move_to_home) }
            first.performAccessibilityAction(toHome.id, null)
            activity.panel()?.findEditText()?.setText("Renamed")
            activity.onBackPressedDispatcher.onBackPressed()
            waitUntil { activity.panel() == null }
            shadowOf(Looper.getMainLooper()).idle()
            assertThat(deps.workspaceRepository.calls).containsNoneOf("removeFromFolder", "renameFolder")
        }
    }
}
