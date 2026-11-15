package io.github.aj4abinjacob.chikku.home

import android.content.Intent
import android.graphics.Rect
import android.os.Looper
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.AppsState
import io.github.aj4abinjacob.chikku.core.model.CellPlacement
import io.github.aj4abinjacob.chikku.core.model.ContainerType
import io.github.aj4abinjacob.chikku.core.model.GridSpec
import io.github.aj4abinjacob.chikku.core.model.ItemTarget
import io.github.aj4abinjacob.chikku.core.model.Workspace
import io.github.aj4abinjacob.chikku.core.model.WorkspaceItem
import io.github.aj4abinjacob.chikku.core.model.WorkspaceState
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Launches the real HomeActivity against fakes injected through [TestHomeApplication]. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestHomeApplication::class)
class HomeActivitySmokeTest {

    private val application = ApplicationProvider.getApplicationContext<TestHomeApplication>()
    private val chrome = app("Chrome")
    private val mail = app("Mail")
    private lateinit var deps: FakeHomeDependencies

    @Before
    fun setUp() {
        application.getSharedPreferences("chikku_startup_guard", 0).edit().clear().commit()
        val gone = AppKey("pkg.gone", "pkg.gone.Main", 0)
        val workspace = Workspace(
            desktopGrid = GridSpec(4, 5),
            dockGrid = GridSpec(5, 1),
            pageCount = 2,
            items = listOf(
                WorkspaceItem("1", CellPlacement(ContainerType.DESKTOP, 0, 0, 0), ItemTarget.App(chrome.key)),
                WorkspaceItem("2", CellPlacement(ContainerType.DESKTOP, 0, 1, 0), ItemTarget.App(gone)),
                WorkspaceItem("3", CellPlacement(ContainerType.DOCK, 0, 0, 0), ItemTarget.App(mail.key)),
            ),
        )
        deps = FakeHomeDependencies(
            appsRepository = FakeAppsRepository(AppsState.Loaded(listOf(chrome, mail))),
            workspaceRepository = FakeWorkspaceRepository(WorkspaceState.Ready(workspace)),
        )
        application.homeDependencies = deps
    }

    /** The startup guard reads preferences on Dispatchers.IO; pump the main looper until state lands. */
    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "condition not met in time" }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
    }

    @Test
    fun launchesAndRendersWorkspaceDockAndDrawer() {
        ActivityScenario.launch(HomeActivity::class.java).use { scenario ->
            lateinit var activity: HomeActivity
            scenario.onActivity { activity = it }
            val pager = activity.findViewById<WorkspacePager>(R.id.workspace_pager)
            val dock = activity.findViewById<CellLayout>(R.id.dock)
            waitUntil { pager.pageCount == 2 && dock.childCount == 1 }

            val firstPage = pager.pageAt(0)!!
            assertThat(firstPage.childCount).isEqualTo(2)
            val labels = (0 until firstPage.childCount).map { (firstPage.getChildAt(it) as AppIconView).contentDescription }
            assertThat(labels).containsExactly("Chrome", activity.getString(R.string.home_unavailable_app))
            assertThat(activity.findViewById<View>(R.id.recovery_panel).visibility).isEqualTo(View.GONE)

            // Open the drawer with the accessible button.
            activity.findViewById<View>(R.id.all_apps_button).performClick()
            waitUntil { activity.findViewById<View>(R.id.drawer).visibility == View.VISIBLE }
            val list = activity.findViewById<RecyclerView>(R.id.drawer_list)
            waitUntil { list.adapter?.itemCount == 2 }

            // Tapping a drawer item launches through the AppLauncher.
            list.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY))
            list.layout(0, 0, 1080, 1920)
            list.getChildAt(0).performClick()
            assertThat(deps.appLauncher.launched).containsExactly(chrome.key)

            // Back closes the drawer.
            activity.onBackPressedDispatcher.onBackPressed()
            waitUntil { activity.findViewById<View>(R.id.drawer).visibility != View.VISIBLE }
        }
    }

    @Test
    fun homeIntentClosesDrawer() {
        ActivityScenario.launch(HomeActivity::class.java).use { scenario ->
            lateinit var activity: HomeActivity
            scenario.onActivity { activity = it }
            activity.findViewById<View>(R.id.all_apps_button).performClick()
            waitUntil { activity.findViewById<View>(R.id.drawer).visibility == View.VISIBLE }

            scenario.onActivity {
                it.handleHomeIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
            }
            waitUntil { activity.findViewById<View>(R.id.drawer).visibility != View.VISIBLE }
        }
    }

    @Test
    fun failedWorkspaceShowsRecoveryPanelAndDrawerStillWorks() {
        deps.homeRoleController.isDefault = false
        deps.workspaceRepository.state.value = WorkspaceState.Failed("corrupt")
        ActivityScenario.launch(HomeActivity::class.java).use { scenario ->
            lateinit var activity: HomeActivity
            scenario.onActivity { activity = it }
            val panel = activity.findViewById<View>(R.id.recovery_panel)
            waitUntil { panel.visibility == View.VISIBLE }
            scenario.onActivity { assertRecoveryActionFits(it, R.id.recovery_retry) }
            activity.findViewById<View>(R.id.recovery_open_drawer).performClick()
            val list = activity.findViewById<RecyclerView>(R.id.drawer_list)
            waitUntil { activity.findViewById<View>(R.id.drawer).visibility == View.VISIBLE && list.adapter?.itemCount == 2 }
        }
    }

    @Test
    fun safeModeRecoveryActionFitsBeforeWorkspaceHasEverLoaded() {
        deps.homeRoleController.isDefault = false
        application.getSharedPreferences("chikku_startup_guard", 0).edit()
            .putInt("unfinished_startups", StartupGuard.DEFAULT_THRESHOLD).commit()
        ActivityScenario.launch(HomeActivity::class.java).use { scenario ->
            lateinit var activity: HomeActivity
            scenario.onActivity { activity = it }
            waitUntil { activity.findViewById<View>(R.id.recovery_panel).visibility == View.VISIBLE &&
                activity.findViewById<View>(R.id.recovery_leave_safe_mode).visibility == View.VISIBLE &&
                activity.findViewById<View>(R.id.default_banner).visibility == View.VISIBLE }
            scenario.onActivity { assertRecoveryActionFits(it, R.id.recovery_leave_safe_mode) }
        }
    }

    private fun assertRecoveryActionFits(activity: HomeActivity, actionId: Int) {
        // A phone-sized viewport with the non-default banner, before any Ready state can assign
        // the dock's one-row grid. An invisible default five-row CellLayout consumed this space.
        val root = activity.findViewById<View>(R.id.home_root)
        val density = activity.resources.displayMetrics.density
        val width = (360 * density).toInt()
        val height = (640 * density).toInt()
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, width, height)
        val action = activity.findViewById<View>(actionId)
        val visible = Rect()
        assertThat(action.getGlobalVisibleRect(visible)).isTrue()
        assertThat(visible.height()).isEqualTo(action.height)
        assertThat(visible.width()).isEqualTo(action.width)
        assertThat(action.height).isAtLeast(activity.resources.getDimensionPixelSize(R.dimen.home_min_touch_target))
    }
}
