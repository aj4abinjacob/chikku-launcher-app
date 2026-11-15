package io.github.aj4abinjacob.chikku.home.gestures

import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.activity.ComponentActivity
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.CellPlacement
import io.github.aj4abinjacob.chikku.core.model.ContainerType
import io.github.aj4abinjacob.chikku.core.model.GestureAction
import io.github.aj4abinjacob.chikku.core.model.ItemActions
import io.github.aj4abinjacob.chikku.core.model.settings.GestureSettings
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.home.Availability
import io.github.aj4abinjacob.chikku.home.DrawerMotion
import io.github.aj4abinjacob.chikku.home.FakeAppLauncher
import io.github.aj4abinjacob.chikku.home.HomeItemUi
import io.github.aj4abinjacob.chikku.home.ItemContent
import io.github.aj4abinjacob.chikku.home.VerticalSwipeLayout
import io.github.aj4abinjacob.chikku.home.app
import io.github.aj4abinjacob.chikku.platform.GestureActionExecutor
import io.github.aj4abinjacob.chikku.platform.GestureHost
import io.github.aj4abinjacob.chikku.platform.LaunchResult
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/** Real touch dispatch and routing with a fake SystemUI boundary; not an animation benchmark. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NotificationDragTest {
    private val activityController: ActivityController<ComponentActivity> =
        Robolectric.buildActivity(ComponentActivity::class.java).setup()
    private val activity = activityController.get()
    private val surface = VerticalSwipeLayout(activity)
    private val drawerMotion = DrawerMotion(View(activity)) { }
    private val launcher = FakeAppLauncher()
    private val shadeCalls = mutableListOf<Boolean>()
    private var onExpand: () -> Unit = { }
    private var searchCalls = 0
    private val shadeDistance = activity.resources.getDimension(R.dimen.gestures_shade_drag_distance)
    private val recognisedY = 100f + ViewConfiguration.get(activity).scaledTouchSlop + 1f
    private var time = 0L
    private var downTime = 0L
    private lateinit var gestures: HomeGestureController

    private fun start(action: GestureAction = GestureAction.ExpandNotifications, iconAction: GestureAction? = null) {
        activity.setContentView(surface)
        surface.layout(0, 0, 600, 1000)
        if (iconAction != null) {
            val app = app("Example")
            surface.addView(View(activity).apply {
                isClickable = true
                setTag(R.id.home_item_model, HomeItemUi(
                    "icon", CellPlacement(ContainerType.DESKTOP, 0, 0, 0),
                    ItemContent.App(app.key, app.label, false, Availability.AVAILABLE),
                    ItemActions(swipeDown = iconAction),
                ))
                layout(0, 0, 600, 300)
            })
        }
        gestures = HomeGestureController(
            activity, surface, drawerMotion,
            GestureActionExecutor(activity, launcher, object : GestureHost {
                override fun openDrawer() = Unit
                override fun openSearch() { searchCalls++ }
                override fun goToDefaultPage() = Unit
            }, statusBar = { quick ->
                shadeCalls += quick
                onExpand()
                true
            }, shortcuts = { _, _, _, _ -> LaunchResult.Started }),
            { LauncherSettings(gestures = GestureSettings(swipeDown = action)) },
            flowOf(emptyMap()),
            unlocker = { _, _ -> },
        )
        surface.listener = gestures
        surface.gestureListener = gestures
    }

    private fun touch(action: Int, y: Float, x: Float = 200f) {
        time += 100L // Slow deliberate drag: these tests cannot pass on release velocity alone.
        if (action == MotionEvent.ACTION_DOWN) downTime = time
        val event = MotionEvent.obtain(downTime, time, action, x, y, 0)
        try {
            surface.dispatchTouchEvent(event)
        } finally {
            event.recycle()
        }
    }

    private fun beginDrag() {
        touch(MotionEvent.ACTION_DOWN, 100f)
        touch(MotionEvent.ACTION_MOVE, recognisedY)
    }

    @After
    fun tearDown() {
        activityController.pause().stop().destroy()
    }

    @Test
    fun notificationsOpenBeforeFingerIsLiftedAndOnlyOnce() {
        start()
        beginDrag()
        touch(MotionEvent.ACTION_MOVE, recognisedY + shadeDistance - 1f)
        assertThat(shadeCalls).isEmpty()
        touch(MotionEvent.ACTION_MOVE, recognisedY + shadeDistance)
        assertThat(shadeCalls).containsExactly(false)
        touch(MotionEvent.ACTION_MOVE, recognisedY + shadeDistance * 3f)
        touch(MotionEvent.ACTION_UP, recognisedY + shadeDistance * 3f)
        assertThat(shadeCalls).containsExactly(false)
    }

    @Test
    fun quickSettingsAlsoOpenDuringTheDrag() {
        start(GestureAction.ExpandQuickSettings)
        beginDrag()
        touch(MotionEvent.ACTION_MOVE, recognisedY + shadeDistance)
        assertThat(shadeCalls).containsExactly(true)
        touch(MotionEvent.ACTION_CANCEL, recognisedY + shadeDistance)
        assertThat(shadeCalls).containsExactly(true)
    }

    @Test
    fun shortCancelledAndReversedDragsDoNotOpenTheShade() {
        start()
        beginDrag()
        touch(MotionEvent.ACTION_MOVE, recognisedY + shadeDistance - 1f)
        touch(MotionEvent.ACTION_CANCEL, recognisedY + shadeDistance - 1f)
        beginDrag()
        touch(MotionEvent.ACTION_MOVE, recognisedY + shadeDistance - 1f)
        touch(MotionEvent.ACTION_MOVE, 100f)
        touch(MotionEvent.ACTION_UP, 100f)
        assertThat(shadeCalls).isEmpty()
    }

    @Test
    fun horizontalSwipeDoesNotOpenTheShade() {
        start()
        touch(MotionEvent.ACTION_DOWN, 100f)
        touch(MotionEvent.ACTION_MOVE, 150f, x = 500f)
        touch(MotionEvent.ACTION_UP, 150f, x = 500f)
        assertThat(shadeCalls).isEmpty()
    }

    @Test
    fun systemCancellationAfterHandoffAllowsAnotherDrag() {
        start()
        beginDrag()
        touch(MotionEvent.ACTION_MOVE, recognisedY + shadeDistance)
        touch(MotionEvent.ACTION_CANCEL, recognisedY + shadeDistance)
        beginDrag()
        touch(MotionEvent.ACTION_MOVE, recognisedY + shadeDistance)
        assertThat(shadeCalls).containsExactly(false, false)
    }

    @Test
    fun cancellationDuringTheSystemCallDoesNotRetryTheHandoff() {
        start()
        onExpand = { gestures.onSwipeEnd(velocityY = 5000f, cancelled = true) }
        beginDrag()
        touch(MotionEvent.ACTION_MOVE, recognisedY + shadeDistance)
        touch(MotionEvent.ACTION_MOVE, recognisedY + shadeDistance * 3f)
        touch(MotionEvent.ACTION_UP, recognisedY + shadeDistance * 3f)
        assertThat(shadeCalls).containsExactly(false)
    }

    @Test
    fun iconOverrideStillWaitsForReleaseAndWinsOverHomeNotifications() {
        start(iconAction = GestureAction.OpenSearch)
        beginDrag()
        touch(MotionEvent.ACTION_MOVE, recognisedY + shadeDistance)
        assertThat(shadeCalls).isEmpty()
        assertThat(searchCalls).isEqualTo(0)
        val travel = activity.resources.getDimension(R.dimen.gestures_min_swipe_distance)
        touch(MotionEvent.ACTION_MOVE, recognisedY + travel + 1f)
        assertThat(searchCalls).isEqualTo(0)
        touch(MotionEvent.ACTION_UP, recognisedY + travel + 1f)
        assertThat(searchCalls).isEqualTo(1)
        assertThat(shadeCalls).isEmpty()
    }

    @Test
    fun iconNotificationActionAlsoHandsOffDuringDrag() {
        start(action = GestureAction.None, iconAction = GestureAction.ExpandNotifications)
        beginDrag()
        touch(MotionEvent.ACTION_MOVE, recognisedY + shadeDistance)
        assertThat(shadeCalls).containsExactly(false)
    }

    @Test
    fun shortFlingCanStillOpenNotificationsOnRelease() {
        start()
        beginDrag()
        touch(MotionEvent.ACTION_MOVE, recognisedY + shadeDistance / 2f)
        assertThat(shadeCalls).isEmpty()
        // Release fallback is also used when a fast flick has not reached the drag threshold.
        gestures.onSwipeEnd(velocityY = 5000f, cancelled = false)
        assertThat(shadeCalls).containsExactly(false)
    }
}
