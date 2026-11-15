package io.github.aj4abinjacob.chikku.home

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.message.shownChikkuMessage
import io.github.aj4abinjacob.chikku.core.model.*
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestHomeApplication::class)
class FolderLongPressIntegrationTest {
    private val application = ApplicationProvider.getApplicationContext<TestHomeApplication>()
    private lateinit var deps: FakeHomeDependencies

    @Before
    fun setUp() {
        application.getSharedPreferences("chikku_startup_guard", 0).edit().clear().commit()
        deps = FakeHomeDependencies(
            appsRepository = FakeAppsRepository(AppsState.Loaded(emptyList())),
            workspaceRepository = FakeWorkspaceRepository(WorkspaceState.Ready(Workspace(
                GridSpec(4, 5), GridSpec(5, 1), 1,
                listOf(WorkspaceItem("folder", CellPlacement(ContainerType.DESKTOP, 0, 0, 0), ItemTarget.Folder("Tools"))),
            ))),
        )
        application.homeDependencies = deps
    }

    private fun await(check: () -> Boolean) {
        val until = System.currentTimeMillis() + 5_000
        while (!check()) {
            check(System.currentTimeMillis() < until) { "HOME did not become ready" }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
    }

    private fun View.descendants(): List<View> = listOf(this) +
        if (this is ViewGroup) (0 until childCount).flatMap { getChildAt(it).descendants() } else emptyList()

    private fun popup(home: HomeActivity): PopupWindow {
        val controller = HomeActivity::class.java.getDeclaredField("folders").apply { isAccessible = true }.get(home)
        val menu = controller.javaClass.getDeclaredField("menu").apply { isAccessible = true }.get(controller)
        return menu.javaClass.getDeclaredField("popup").apply { isAccessible = true }.get(menu) as PopupWindow
    }

    private fun composeDialogs(home: HomeActivity): ComposeDialogHost =
        HomeActivity::class.java.getDeclaredField("composeDialogs").apply { isAccessible = true }.get(home) as ComposeDialogHost

    @Test
    fun actualWorkspaceLongPressOffersEditAndCancelLeavesRepositoryUntouched() {
        ActivityScenario.launch(HomeActivity::class.java).use { scenario ->
            lateinit var home: HomeActivity
            scenario.onActivity { home = it }
            val pager = home.findViewById<WorkspacePager>(R.id.workspace_pager)
            await { pager.pageAt(0)?.childCount == 1 }
            assertThat(pager.pageAt(0)!!.getChildAt(0).performLongClick()).isTrue()
            val menu = popup(home)
            assertThat(menu.isShowing).isTrue()
            val labels = menu.contentView.descendants().filterIsInstance<TextView>()
            assertThat(labels.map { it.text.toString() }).containsExactly(
                home.getString(R.string.folder_edit_button), home.getString(R.string.folder_menu_open),
                home.getString(R.string.app_menu_remove),
            )
            (labels.first().parent as View).performClick() // the row is the touch target
            // Edit opens the settings-styled Compose sheet over HOME; dismissing it saves nothing.
            val sheets = composeDialogs(home)
            assertThat(sheets.isShowing).isTrue()
            sheets.dismiss()
            shadowOf(Looper.getMainLooper()).idle()
            assertThat(sheets.isShowing).isFalse()
            assertThat(deps.workspaceRepository.calls).isEmpty()
        }
    }

    @Test
    fun lockedFolderStillOffersMenuButRefusesEditing() {
        val defaults = LauncherSettings()
        deps.settingsRepository.state.value = defaults.copy(home = defaults.home.copy(layoutLocked = true))
        ActivityScenario.launch(HomeActivity::class.java).use { scenario ->
            lateinit var home: HomeActivity
            scenario.onActivity { home = it }
            val pager = home.findViewById<WorkspacePager>(R.id.workspace_pager)
            await { pager.pageAt(0)?.childCount == 1 }
            pager.pageAt(0)!!.getChildAt(0).performLongClick()
            (popup(home).contentView.descendants().filterIsInstance<TextView>().first().parent as View).performClick()
            assertThat(home.shownChikkuMessage()).isEqualTo(home.getString(R.string.home_layout_locked))
            assertThat(deps.workspaceRepository.calls).isEmpty()
        }
    }

    @Test
    fun openStaysAvailableWhenTheLayoutIsLocked() {
        val defaults = LauncherSettings()
        deps.settingsRepository.state.value = defaults.copy(home = defaults.home.copy(layoutLocked = true))
        ActivityScenario.launch(HomeActivity::class.java).use { scenario ->
            lateinit var home: HomeActivity
            scenario.onActivity { home = it }
            val pager = home.findViewById<WorkspacePager>(R.id.workspace_pager)
            await { pager.pageAt(0)?.childCount == 1 }
            pager.pageAt(0)!!.getChildAt(0).performLongClick()
            val open = popup(home).contentView.descendants().filterIsInstance<TextView>()
                .first { it.text.toString() == home.getString(R.string.folder_menu_open) }
            (open.parent as View).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            assertThat(home.shownChikkuMessage()).isNotEqualTo(home.getString(R.string.home_layout_locked))
            assertThat(deps.workspaceRepository.calls).isEmpty()
        }
    }
}
