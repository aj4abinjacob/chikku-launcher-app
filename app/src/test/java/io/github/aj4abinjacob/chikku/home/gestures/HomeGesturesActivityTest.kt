package io.github.aj4abinjacob.chikku.home.gestures

import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.AppsState
import io.github.aj4abinjacob.chikku.core.model.CellPlacement
import io.github.aj4abinjacob.chikku.core.model.ContainerType
import io.github.aj4abinjacob.chikku.core.model.GestureAction
import io.github.aj4abinjacob.chikku.core.model.GridSpec
import io.github.aj4abinjacob.chikku.core.model.ItemActions
import io.github.aj4abinjacob.chikku.core.model.ItemTarget
import io.github.aj4abinjacob.chikku.core.model.Workspace
import io.github.aj4abinjacob.chikku.core.model.WorkspaceItem
import io.github.aj4abinjacob.chikku.core.model.WorkspaceState
import io.github.aj4abinjacob.chikku.core.model.settings.GestureSettings
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.home.AppIconView
import io.github.aj4abinjacob.chikku.home.FakeAppsRepository
import io.github.aj4abinjacob.chikku.home.FakeHomeDependencies
import io.github.aj4abinjacob.chikku.home.FakeSettingsRepository
import io.github.aj4abinjacob.chikku.home.FakeWorkspaceRepository
import io.github.aj4abinjacob.chikku.home.HomeActivity
import io.github.aj4abinjacob.chikku.home.HomeItemUi
import io.github.aj4abinjacob.chikku.home.TestHomeApplication
import io.github.aj4abinjacob.chikku.home.VerticalSwipeLayout
import io.github.aj4abinjacob.chikku.home.app
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Feeds synthetic touches into the real HomeActivity (Robolectric) and checks which action runs.
 * This is JVM evidence of the routing only; it is not a device test of gesture feel.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestHomeApplication::class)
class HomeGesturesActivityTest {

    private val application = ApplicationProvider.getApplicationContext<TestHomeApplication>()
    private val chrome = app("Chrome")
    private val mail = app("Mail")
    private lateinit var deps: FakeHomeDependencies

    private fun start(gestures: GestureSettings, chromeActions: ItemActions = ItemActions()) {
        application.getSharedPreferences("chikku_startup_guard", 0).edit().clear().commit()
        val workspace = Workspace(
            desktopGrid = GridSpec(4, 5),
            dockGrid = GridSpec(5, 1),
            pageCount = 1,
            items = listOf(
                WorkspaceItem("1", CellPlacement(ContainerType.DESKTOP, 0, 0, 0), ItemTarget.App(chrome.key), chromeActions),
                WorkspaceItem("3", CellPlacement(ContainerType.DOCK, 0, 0, 0), ItemTarget.App(mail.key)),
            ),
        )
        deps = FakeHomeDependencies(
            appsRepository = FakeAppsRepository(AppsState.Loaded(listOf(chrome, mail))),
            workspaceRepository = FakeWorkspaceRepository(WorkspaceState.Ready(workspace)),
            settingsRepository = FakeSettingsRepository(LauncherSettings(gestures = gestures)),
        )
        application.homeDependencies = deps
    }

    @Before
    fun clearGuard() {
        application.getSharedPreferences("chikku_startup_guard", 0).edit().clear().commit()
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "condition not met in time" }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
    }

    private fun withHome(block: (HomeActivity, VerticalSwipeLayout) -> Unit) {
        ActivityScenario.launch(HomeActivity::class.java).use { scenario ->
            lateinit var activity: HomeActivity
            scenario.onActivity { activity = it }
            val content = activity.findViewById<VerticalSwipeLayout>(R.id.home_content)
            waitUntil { chromeIcon(activity) != null && content.height > 0 }
            block(activity, content)
        }
    }

    private fun chromeIcon(activity: HomeActivity): AppIconView? {
        val found = mutableListOf<AppIconView>()
        fun walk(v: View) {
            if (v is AppIconView && (v.getTag(R.id.home_item_model) as? HomeItemUi)?.id == "1") found += v
            if (v is android.view.ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(activity.findViewById(R.id.home_content))
        return found.firstOrNull()?.takeIf { it.width > 0 }
    }

    private fun drawerVisible(activity: HomeActivity) = activity.findViewById<View>(R.id.drawer).visibility == View.VISIBLE

    // --- Event helpers (coordinates relative to the home content view) ----------------------------

    private val t0 = SystemClock.uptimeMillis()

    private fun event(action: Int, time: Long, vararg points: Pair<Float, Float>): MotionEvent {
        val props = Array(points.size) { i -> MotionEvent.PointerProperties().apply { id = i; toolType = MotionEvent.TOOL_TYPE_FINGER } }
        val coords = Array(points.size) { i -> MotionEvent.PointerCoords().apply { x = points[i].first; y = points[i].second; pressure = 1f; size = 1f } }
        return MotionEvent.obtain(t0, t0 + time, action, points.size, props, coords, 0, 0, 1f, 1f, 0, 0, 0, 0)
    }

    private fun swipe(view: View, x: Float, fromY: Float, toY: Float) {
        view.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, 0, x to fromY))
        for (step in 1..10) view.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE, step * 16L, x to fromY + (toY - fromY) * step / 10))
        view.dispatchTouchEvent(event(MotionEvent.ACTION_UP, 176, x to toY))
    }

    private fun pinchIn(view: View) {
        val cx = view.width / 2f
        val cy = view.height / 2f
        view.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, 0, cx to cy - 150f))
        val pointerDown = MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        view.dispatchTouchEvent(event(pointerDown, 10, cx to cy - 150f, cx to cy + 150f))
        for (step in 1..10) {
            val d = 150f - step * 12f
            view.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE, 10 + step * 16L, cx to cy - d, cx to cy + d))
        }
        val pointerUp = MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        view.dispatchTouchEvent(event(pointerUp, 200, cx to cy - 30f, cx to cy + 30f))
        view.dispatchTouchEvent(event(MotionEvent.ACTION_UP, 210, cx to cy - 30f))
    }

    private fun emptySpot(content: View): Pair<Float, Float> = content.width / 2f to content.height / 2f

    // --- Tests ---------------------------------------------------------------------------------

    @Test
    fun pinchInRunsItsAction() {
        start(GestureSettings(pinchIn = GestureAction.OpenDrawer))
        withHome { activity, content ->
            assertThat(drawerVisible(activity)).isFalse()
            pinchIn(content)
            waitUntil { drawerVisible(activity) }
        }
    }

    @Test
    fun doubleTapOnEmptySpaceRunsItsAction() {
        start(GestureSettings(doubleTap = GestureAction.LaunchApp(mail.key)))
        withHome { _, content ->
            val (x, y) = emptySpot(content)
            content.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, 0, x to y))
            content.dispatchTouchEvent(event(MotionEvent.ACTION_UP, 50, x to y))
            assertThat(deps.appLauncher.launched).isEmpty()
            content.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, 120, x to y))
            content.dispatchTouchEvent(event(MotionEvent.ACTION_UP, 170, x to y))
            assertThat(deps.appLauncher.launched).containsExactly(mail.key)
        }
    }

    @Test
    fun homeSwipeDownRunsItsActionOnRelease() {
        start(GestureSettings(swipeDown = GestureAction.LaunchApp(mail.key)))
        withHome { _, content ->
            val (x, y) = emptySpot(content)
            swipe(content, x, y - 100f, y + 150f)
            assertThat(deps.appLauncher.launched).containsExactly(mail.key)
        }
    }

    @Test
    fun swipeOnAnIconRunsTheIconActionInsteadOfLaunchingIt() {
        start(GestureSettings(), chromeActions = ItemActions(swipeDown = GestureAction.LaunchApp(mail.key)))
        withHome { activity, content ->
            val icon = chromeIcon(activity)!!
            val iconLoc = IntArray(2).also(icon::getLocationInWindow)
            val contentLoc = IntArray(2).also(content::getLocationInWindow)
            val x = (iconLoc[0] - contentLoc[0]) + icon.width / 2f
            val y = (iconLoc[1] - contentLoc[1]) + icon.height / 4f
            swipe(content, x, y, y + 150f)
            shadowOf(Looper.getMainLooper()).idle()
            // The icon's swipe-down action ran; the tap (launch Chrome) did not.
            assertThat(deps.appLauncher.launched).containsExactly(mail.key)
            assertThat(drawerVisible(activity)).isFalse()
        }
    }

    private fun iconPoint(activity: HomeActivity, content: View): Pair<Float, Float> {
        val icon = chromeIcon(activity)!!
        val iconLoc = IntArray(2).also(icon::getLocationInWindow)
        val contentLoc = IntArray(2).also(content::getLocationInWindow)
        return (iconLoc[0] - contentLoc[0]) + icon.width / 2f to (iconLoc[1] - contentLoc[1]) + icon.height / 2f
    }

    private fun dragAndRelease(content: View, x: Float, fromY: Float, toY: Float, whileHeld: () -> Unit) {
        content.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, 0, x to fromY))
        for (step in 1..10) content.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE, step * 16L, x to fromY + (toY - fromY) * step / 10))
        whileHeld()
        content.dispatchTouchEvent(event(MotionEvent.ACTION_UP, 176, x to toY))
    }

    @Test
    fun iconWithASwipeActionFollowsTheFingerAndSpringsBack() {
        start(GestureSettings(), chromeActions = ItemActions(swipeUp = GestureAction.LaunchApp(mail.key), swipeDown = GestureAction.LaunchApp(mail.key)))
        withHome { activity, content ->
            val maxNudge = activity.resources.getDimension(R.dimen.gestures_icon_swipe_nudge)
            for (dy in listOf(-150f, 150f)) {
                val (x, y) = iconPoint(activity, content)
                var held = 0f
                dragAndRelease(content, x, y, y + dy) { held = chromeIcon(activity)!!.translationY }
                assertThat(held).isNonZero()
                assertThat(Math.signum(held)).isEqualTo(Math.signum(dy))
                assertThat(Math.abs(held)).isAtMost(maxNudge)
                shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(500))
                assertThat(chromeIcon(activity)!!.translationY).isEqualTo(0f)
            }
            assertThat(deps.appLauncher.launched).containsExactly(mail.key, mail.key)
        }
    }

    @Test
    fun iconWithoutASwipeActionStaysPut() {
        start(GestureSettings(swipeDown = GestureAction.LaunchApp(mail.key)))
        withHome { activity, content ->
            val (x, y) = iconPoint(activity, content)
            var held = 1f
            dragAndRelease(content, x, y, y + 150f) { held = chromeIcon(activity)!!.translationY }
            assertThat(held).isEqualTo(0f)
        }
    }

    @Test
    fun swipeUpStillOpensTheDrawerByDefault() {
        start(GestureSettings())
        withHome { activity, content ->
            val (x, y) = emptySpot(content)
            swipe(content, x, y + 100f, y - 250f)
            waitUntil { drawerVisible(activity) }
            assertThat(deps.appLauncher.launched).isEmpty()
        }
    }
}
