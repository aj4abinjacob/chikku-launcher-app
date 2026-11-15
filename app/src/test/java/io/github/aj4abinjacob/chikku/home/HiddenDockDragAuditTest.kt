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
import io.github.aj4abinjacob.chikku.core.model.ComponentKey
import io.github.aj4abinjacob.chikku.core.model.GridSpec
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

/** Controller dispatch regression; native drag/shadow behavior requires the separate device suite. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestHomeApplication::class)
class HiddenDockDragAuditTest {
    @Test fun hiddenDockNewAppAndWidgetDropOrCancelCleanExactlyOnceAndAllowNextDrag() {
        val host = Robolectric.buildActivity(Activity::class.java).setup().visible()
        val context = ContextThemeWrapper(host.get(), R.style.Theme_Chikku_Home)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        try {
            for (dockVisibility in listOf(View.GONE, View.INVISIBLE)) {
                val pager = WorkspacePager(context)
                val dock = CellLayout(context).apply { visibility = dockVisibility }
                val root = FrameLayout(context).apply {
                    addView(pager, FrameLayout.LayoutParams(500, 500))
                    addView(dock, FrameLayout.LayoutParams(500, 100))
                }
                host.get().setContentView(root)
                // View.dispatchDragEvent updates ViewRootImpl's drag focus on DROP/ENDED.
                // Keep a real window attachment rather than bypassing framework dispatch.
                assertThat(pager.isAttachedToWindow).isTrue()
                var ended = 0
                var drops = 0
                val actions = object : WorkspaceActions {
                    override fun onItemClick(item: HomeItemUi, view: View) = Unit
                    override fun onDrop(payload: DragPayload, target: CellPlacement) {
                        drops++
                    }
                    override fun onRemoveItem(itemId: String) = Unit
                    override fun onAppInfo(key: AppKey, view: View) = Unit
                }
                val controller = WorkspaceController(
                    pager,
                    dock,
                    PageIndicatorView(context),
                    View(context),
                    IconBinder(context, scope, FakeIconProvider()),
                    actions,
                    dragStarter = { _, _ -> true },
                )
                controller.applySettings(LauncherSettings().let { it.copy(dock = it.dock.copy(enabled = false)) })
                controller.render(WorkspaceUi.Ready(GridSpec(5, 5), GridSpec(5, 1), listOf(emptyList()), emptyList()))
                val page = pager.pageAt(0)!!
                page.layout(0, 0, 500, 500)
                controller.onDragEnded = { ended++ }
                val payloads = listOf(
                    DragPayload.NewApp(AppKey("synthetic", "synthetic.Main", 0)),
                    DragPayload.NewWidget(ComponentKey("synthetic", "synthetic.Widget", 0), 1, 1),
                )
                for (payload in payloads) {
                    for (drop in listOf(false, true)) {
                        val before = ended
                        assertThat(controller.startDrag(View(context), payload)).isTrue()
                        send(page, DragEvent.ACTION_DRAG_STARTED, payload)
                        if (drop) send(page, DragEvent.ACTION_DROP, payload)
                        send(page, DragEvent.ACTION_DRAG_ENDED, payload)
                        // Another interested surface and stop cleanup can both run after the first ENDED.
                        send(dock, DragEvent.ACTION_DRAG_ENDED, payload)
                        controller.cancelDrag()
                        assertThat(ended).isEqualTo(before + 1)
                        assertThat(controller.startDrag(View(context), payload)).isTrue()
                        controller.cancelDrag()
                        assertThat(ended).isEqualTo(before + 2)
                    }
                }
                assertThat(drops).isEqualTo(2)
            }
        } finally {
            scope.cancel()
            host.pause().stop().destroy()
        }
    }

    private fun send(view: View, action: Int, payload: DragPayload) {
        val event = ReflectionHelpers.callConstructor(DragEvent::class.java)
        ReflectionHelpers.setField(event, "mAction", action)
        ReflectionHelpers.setField(event, "mX", 10f)
        ReflectionHelpers.setField(event, "mY", 10f)
        ReflectionHelpers.setField(event, "mLocalState", payload)
        view.dispatchDragEvent(event)
    }
}
