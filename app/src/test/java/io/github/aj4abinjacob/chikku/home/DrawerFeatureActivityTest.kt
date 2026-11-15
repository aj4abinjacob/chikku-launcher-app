package io.github.aj4abinjacob.chikku.home

import android.graphics.drawable.ColorDrawable
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.AppCustomization
import io.github.aj4abinjacob.chikku.core.model.AppsState
import io.github.aj4abinjacob.chikku.core.model.DrawerGroup
import io.github.aj4abinjacob.chikku.core.model.GestureAction
import io.github.aj4abinjacob.chikku.core.model.WorkspaceState
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerOpenMethod
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerSearchBarPosition
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerSettings
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerStyle
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.home.drawer.DrawerFastScroller
import io.github.aj4abinjacob.chikku.home.drawer.DrawerPageView
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Drawer settings (G13) and hidden apps / groups (G14) rendered by the real HomeActivity. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestHomeApplication::class)
class DrawerFeatureActivityTest {

    private val application = ApplicationProvider.getApplicationContext<TestHomeApplication>()
    private val apps = listOf("Alpha", "Bravo", "Camera", "Clock", "Delta", "Echo").map { app(it) }
    private lateinit var deps: FakeHomeDependencies

    @Before
    fun setUp() {
        application.getSharedPreferences("chikku_startup_guard", 0).edit().clear().commit()
        deps = FakeHomeDependencies(
            appsRepository = FakeAppsRepository(AppsState.Loaded(apps)),
            workspaceRepository = FakeWorkspaceRepository(WorkspaceState.Failed("not needed")),
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

    private fun withDrawer(settings: DrawerSettings, block: (HomeActivity) -> Unit) {
        deps.settingsRepository.state.value = LauncherSettings(drawer = settings)
        ActivityScenario.launch(HomeActivity::class.java).use { scenario ->
            lateinit var activity: HomeActivity
            scenario.onActivity { activity = it }
            activity.findViewById<View>(R.id.recovery_open_drawer).performClick()
            waitUntil { activity.findViewById<View>(R.id.drawer).visibility == View.VISIBLE }
            block(activity)
        }
    }

    private fun HomeActivity.list() = findViewById<RecyclerView>(R.id.drawer_list)

    private fun layOut(view: View) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, 1080, 1920)
    }

    @Test
    fun hiddenAppsAreNotInTheDrawer() {
        val hidden = apps[1]
        deps.appCustomizationRepository.state.value = mapOf(hidden.key to AppCustomization(hidden.key, hidden = true))
        withDrawer(DrawerSettings()) { activity ->
            waitUntil { activity.list().adapter?.itemCount == apps.size - 1 }
            layOut(activity.list())
            val shown = (0 until activity.list().childCount).map { activity.list().getChildAt(it).contentDescription }
            assertThat(shown).doesNotContain("Bravo")
        }
    }

    @Test
    fun groupsShowAsTabsAndTabSelectionFiltersApps() {
        deps.drawerGroupRepository.state.value = listOf(DrawerGroup("g", "Tools", 0, members = listOf(apps[3].key, apps[2].key)))
        withDrawer(DrawerSettings(groupsEnabled = true)) { activity ->
            val tabs = activity.findViewById<LinearLayout>(R.id.drawer_tabs)
            waitUntil { tabs.childCount == 2 }
            assertThat(activity.findViewById<View>(R.id.drawer_tabs_scroll).visibility).isEqualTo(View.VISIBLE)
            assertThat((tabs.getChildAt(0) as TextView).text.toString()).isEqualTo(activity.getString(R.string.drawer_tab_all))
            assertThat((tabs.getChildAt(1) as TextView).text.toString()).isEqualTo("Tools")
            assertThat(activity.list().adapter?.itemCount).isEqualTo(apps.size)

            tabs.getChildAt(1).performClick()
            waitUntil { activity.list().adapter?.itemCount == 2 }
            assertThat(tabs.getChildAt(1).isSelected).isTrue()

            // Searching ignores tabs and searches every visible app.
            activity.findViewById<TextView>(R.id.drawer_search).text = "e"
            // The tabs stay until the search result is current (search debounce), then give way to it.
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(500))
            waitUntil { activity.findViewById<View>(R.id.drawer_tabs_scroll).visibility == View.GONE }
        }
    }

    @Test
    fun groupsAreNotShownWhenDisabled() {
        deps.drawerGroupRepository.state.value = listOf(DrawerGroup("g", "Tools", 0, members = listOf(apps[0].key)))
        withDrawer(DrawerSettings(groupsEnabled = false)) { activity ->
            waitUntil { activity.list().adapter?.itemCount == apps.size }
            assertThat(activity.findViewById<View>(R.id.drawer_tabs_scroll).visibility).isEqualTo(View.GONE)
        }
    }

    @Test
    fun pagedStyleChunksAppsIntoPagesWithDots() {
        withDrawer(DrawerSettings(style = DrawerStyle.HORIZONTAL_PAGED, columns = 2, rows = 2)) { activity ->
            val pages = activity.findViewById<RecyclerView>(R.id.drawer_pages)
            waitUntil { pages.adapter?.itemCount == 2 }
            assertThat(pages.visibility).isEqualTo(View.VISIBLE)
            assertThat(activity.list().visibility).isEqualTo(View.GONE)
            assertThat(activity.findViewById<View>(R.id.drawer_page_dots).visibility).isEqualTo(View.VISIBLE)
            layOut(pages)
            val first = pages.getChildAt(0) as DrawerPageView
            assertThat(first.childCount).isEqualTo(4)
            first.getChildAt(0).performClick()
            assertThat(deps.appLauncher.launched).containsExactly(apps[0].key)
        }
    }

    @Test
    fun fastScrollerListsLettersAndJumps() {
        withDrawer(DrawerSettings(showFastScroller = true)) { activity ->
            val scroller = activity.findViewById<DrawerFastScroller>(R.id.drawer_fast_scroller)
            waitUntil { scroller.childCount == 5 }
            assertThat(scroller.visibility).isEqualTo(View.VISIBLE)
            val letters = (0 until scroller.childCount).map { (scroller.getChildAt(it) as TextView).text.toString() }
            assertThat(letters).containsExactly("A", "B", "C", "D", "E").inOrder()
            assertThat(scroller.getChildAt(2).contentDescription).isEqualTo(activity.getString(R.string.drawer_fast_scroller_jump, "C"))
            scroller.getChildAt(4).performClick() // accessible path does not crash
        }
    }

    @Test
    fun searchBarPositionAndBackground() {
        withDrawer(
            DrawerSettings(searchBarPosition = DrawerSearchBarPosition.BOTTOM, backgroundColor = 0xFF000000.toInt(), backgroundAlpha = 0.5f),
        ) { activity ->
            val drawer = activity.findViewById<ViewGroup>(R.id.drawer)
            val search = activity.findViewById<View>(R.id.drawer_search)
            assertThat(drawer.indexOfChild(search)).isEqualTo(drawer.childCount - 1)
            assertThat((drawer.background as ColorDrawable).color).isEqualTo(0x80000000.toInt())
        }
        withDrawer(DrawerSettings(searchBarPosition = DrawerSearchBarPosition.HIDDEN)) { activity ->
            assertThat(activity.findViewById<View>(R.id.drawer_search).visibility).isEqualTo(View.GONE)
        }
    }

    @Test
    fun changingSearchPlacementClearsHiddenQueryAndRestoresAllApps() {
        withDrawer(DrawerSettings()) { activity ->
            val drawer = activity.findViewById<ViewGroup>(R.id.drawer)
            val search = activity.findViewById<EditText>(R.id.drawer_search)
            val more = activity.findViewById<View>(R.id.drawer_search_more)
            waitUntil { activity.list().adapter?.itemCount == apps.size }
            assertThat(drawer.indexOfChild(search)).isEqualTo(0)
            search.setText("Camera")
            waitUntil { activity.list().adapter?.itemCount == 1 }
            assertThat(more.visibility).isEqualTo(View.VISIBLE)

            deps.settingsRepository.state.value = LauncherSettings(
                drawer = DrawerSettings(searchBarPosition = DrawerSearchBarPosition.BOTTOM),
            )
            waitUntil { drawer.indexOfChild(search) == drawer.childCount - 1 }
            assertThat(search.text.toString()).isEqualTo("Camera")
            assertThat(drawer.indexOfChild(more)).isEqualTo(drawer.indexOfChild(search) - 1)

            deps.settingsRepository.state.value = LauncherSettings(
                drawer = DrawerSettings(searchBarPosition = DrawerSearchBarPosition.HIDDEN, openKeyboardOnOpen = true),
            )
            waitUntil { search.visibility == View.GONE && activity.list().adapter?.itemCount == apps.size }
            assertThat(search.text.toString()).isEmpty()
            assertThat(search.hasFocus()).isFalse()
            assertThat(more.visibility).isEqualTo(View.GONE)

            deps.settingsRepository.state.value = LauncherSettings()
            waitUntil { search.visibility == View.VISIBLE && drawer.indexOfChild(search) == 0 }
            assertThat(drawer.indexOfChild(more)).isEqualTo(1)
            assertThat(more.visibility).isEqualTo(View.GONE)
        }
    }

    @Test
    fun allAppsButtonFollowsOpenMethod() {
        deps.settingsRepository.state.value = LauncherSettings(drawer = DrawerSettings(openMethod = DrawerOpenMethod.SWIPE_UP))
        deps.workspaceRepository.state.value = WorkspaceState.Loading
        ActivityScenario.launch(HomeActivity::class.java).use { scenario ->
            lateinit var activity: HomeActivity
            scenario.onActivity { activity = it }
            val button = activity.findViewById<View>(R.id.all_apps_button)
            assertThat(button).isInstanceOf(ImageButton::class.java)
            assertThat(button.contentDescription).isEqualTo(activity.getString(R.string.home_open_all_apps))
            waitUntil { button.visibility == View.GONE }

            // If swipe up is bound to something else, the button comes back so the drawer stays reachable.
            deps.settingsRepository.state.value = LauncherSettings(
                drawer = DrawerSettings(openMethod = DrawerOpenMethod.SWIPE_UP),
                gestures = LauncherSettings().gestures.copy(swipeUp = GestureAction.None),
            )
            waitUntil { button.visibility == View.VISIBLE }
        }
    }
}
