package io.github.aj4abinjacob.chikku.home

import android.app.Activity
import android.view.ContextThemeWrapper
import android.view.DragEvent
import android.view.View
import android.widget.FrameLayout
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.CellPlacement
import io.github.aj4abinjacob.chikku.core.model.ContainerType
import io.github.aj4abinjacob.chikku.core.model.GridSpec
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import org.robolectric.util.ReflectionHelpers
import java.util.concurrent.TimeUnit

/**
 * A dropped item must not flash back in its old cell while the move is stored: it stays hidden
 * until the render that moves it, then glides from under the finger. The native drag shadow itself
 * needs the device suite.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestHomeApplication::class)
class WorkspaceDropSettleTest {
    private lateinit var host: ActivityController<Activity>
    private lateinit var scope: CoroutineScope
    private lateinit var root: FrameLayout
    private lateinit var pager: WorkspacePager
    private lateinit var controller: WorkspaceController
    private val drops = mutableListOf<CellPlacement>()

    private val key = AppKey("synthetic", "synthetic.Main", 0)
    private val grid = GridSpec(5, 5)

    @Before
    fun setUp() {
        host = Robolectric.buildActivity(Activity::class.java).setup().visible()
        val context = ContextThemeWrapper(host.get(), R.style.Theme_Chikku_Home)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        pager = WorkspacePager(context)
        val dock = CellLayout(context)
        root = FrameLayout(context).apply {
            addView(pager, FrameLayout.LayoutParams(500, 500))
            addView(dock, FrameLayout.LayoutParams(500, 100))
        }
        host.get().setContentView(root)
        val actions = object : WorkspaceActions {
            override fun onItemClick(item: HomeItemUi, view: View) = Unit
            override fun onDrop(payload: DragPayload, target: CellPlacement) {
                drops += target
            }
            override fun onRemoveItem(itemId: String) = Unit
            override fun onAppInfo(key: AppKey, view: View) = Unit
        }
        controller = WorkspaceController(
            pager, dock, PageIndicatorView(context), View(context), IconBinder(context, scope, FakeIconProvider()), actions,
            dragStarter = { _, _ -> true },
        )
        controller.applySettings(LauncherSettings())
    }

    @After
    fun tearDown() {
        scope.cancel()
        host.pause().stop().destroy()
    }

    private fun at(x: Int, y: Int) = CellPlacement(ContainerType.DESKTOP, 0, x, y)

    private fun render(placement: CellPlacement) {
        val item = HomeItemUi("item", placement, ItemContent.App(key, "Sample", isWork = false, availability = Availability.AVAILABLE))
        controller.render(WorkspaceUi.Ready(grid, GridSpec(5, 1), listOf(listOf(item)), emptyList()))
        layout()
    }

    private fun layout() {
        root.measure(View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 500, 600)
        root.viewTreeObserver.dispatchOnPreDraw()
    }

    private fun page() = pager.pageAt(0)!!
    private fun itemView() = page().getChildAt(0)

    private fun send(view: View, action: Int, payload: DragPayload, x: Float, y: Float) {
        val event = ReflectionHelpers.callConstructor(DragEvent::class.java)
        ReflectionHelpers.setField(event, "mAction", action)
        ReflectionHelpers.setField(event, "mX", x)
        ReflectionHelpers.setField(event, "mY", y)
        ReflectionHelpers.setField(event, "mLocalState", payload)
        view.dispatchDragEvent(event)
    }

    private fun dragTo(x: Float, y: Float) {
        val payload = DragPayload.Existing("item", at(0, 0))
        assertThat(controller.startDrag(itemView(), payload)).isTrue()
        send(page(), DragEvent.ACTION_DRAG_STARTED, payload, x, y)
        send(page(), DragEvent.ACTION_DROP, payload, x, y)
        send(page(), DragEvent.ACTION_DRAG_ENDED, payload, x, y)
    }

    // Drop settle itself (hidden until stored, ghost at the target, timeout) is covered by DragSettleTest.

    @Test
    fun dropHighlightReportsOnlyMovesBetweenCells() {
        render(at(0, 0))
        val page = page()
        assertThat(page.setDropHighlight(Cell(1, 1))).isFalse() // appears
        assertThat(page.setDropHighlight(Cell(1, 1))).isFalse() // same cell
        assertThat(page.setDropHighlight(Cell(2, 1))).isTrue() // moved: one haptic tick
        assertThat(page.setDropHighlight(null)).isFalse() // gone
    }
}
