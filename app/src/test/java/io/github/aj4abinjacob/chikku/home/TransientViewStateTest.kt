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
import io.github.aj4abinjacob.chikku.core.model.ContainerType
import io.github.aj4abinjacob.chikku.core.model.GridSpec
import io.github.aj4abinjacob.chikku.core.model.settings.AnimationSpeed
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.home.widget.WidgetCellView
import io.github.aj4abinjacob.chikku.home.widget.WidgetViewBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.time.Duration

/**
 * Transient view state (alpha, scale, translation) set for a drag or an animation must always end at
 * the item's resting state, whatever interrupts it. Uses the view classes production renders: a real
 * [WidgetCellView] for widgets and [AppIconView] bound by the real [IconBinder] for icons/shortcuts.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestHomeApplication::class)
class TransientViewStateTest {
    private val from = CellPlacement(ContainerType.DESKTOP, 0, 2, 2)
    private val moved = CellPlacement(ContainerType.DESKTOP, 0, 0, 0)

    private fun app(placement: CellPlacement) =
        HomeItemUi("item", placement, ItemContent.App(AppKey("pkg", "pkg.Main", 0), "Synthetic", false, Availability.AVAILABLE))

    private fun shortcut(placement: CellPlacement) =
        HomeItemUi("item", placement, ItemContent.Shortcut("pkg", "sc1", 0, "Synthetic shortcut", Availability.AVAILABLE))

    private fun widget(placement: CellPlacement) =
        HomeItemUi("item", placement, ItemContent.Widget(5, ComponentKey("w", "w.P", 0), Availability.AVAILABLE))

    private fun ui(item: HomeItemUi) =
        WorkspaceUi.Ready(GridSpec(5, 5), GridSpec(5, 1), listOf(listOf(item)), emptyList())

    private class Rig(val controller: WorkspaceController, val page: CellLayout)

    /** Widget binder over real [WidgetCellView]s; like production it never sets alpha, scale or translation. */
    private class RealCellWidgets(private val context: android.content.Context) : WidgetViewBinder {
        override fun createWidgetView(): View = WidgetCellView(context)
        override fun isWidgetView(view: View) = view is WidgetCellView
        override fun bindWidget(view: View, item: HomeItemUi, content: ItemContent.Widget) {
            view.setTag(R.id.home_item_model, item)
            (view as WidgetCellView).showPlaceholder("Synthetic widget", "Synthetic widget")
        }
        override fun recycleWidget(view: View) = Unit
        override fun onWorkspaceRendered(ui: WorkspaceUi.Ready) = Unit
    }

    private fun rig(initial: HomeItemUi, speed: AnimationSpeed, block: Rig.() -> Unit) {
        val host = Robolectric.buildActivity(Activity::class.java).setup().visible()
        val context = ContextThemeWrapper(host.get(), R.style.Theme_Chikku_Home)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        try {
            val pager = WorkspacePager(context)
            val dock = CellLayout(context)
            host.get().setContentView(FrameLayout(context).apply {
                addView(pager, FrameLayout.LayoutParams(500, 500))
                addView(dock, FrameLayout.LayoutParams(500, 100))
            })
            val actions = object : WorkspaceActions {
                override fun onItemClick(item: HomeItemUi, view: View) = Unit
                override fun onDrop(payload: DragPayload, target: CellPlacement) = Unit
                override fun onRemoveItem(itemId: String) = Unit
                override fun onAppInfo(key: AppKey, view: View) = Unit
            }
            val controller = WorkspaceController(
                pager, dock, PageIndicatorView(context), View(context),
                IconBinder(context, scope, FakeIconProvider()), actions, dragStarter = { _, _ -> true },
            )
            controller.widgetBinder = RealCellWidgets(context)
            val settings = LauncherSettings().let { it.copy(lookFeel = it.lookFeel.copy(animationSpeed = speed)) }
            controller.applySettings(settings)
            controller.render(ui(initial))
            pager.measure(
                View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY),
            )
            pager.layout(0, 0, 500, 500)
            Rig(controller, pager.pageAt(0)!!).block()
        } finally {
            scope.cancel()
            host.pause().stop().destroy()
        }
    }

    private fun idle(ms: Long) = shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    private fun Rig.relayout() {
        page.measure(
            View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY),
        )
        page.layout(0, 0, 500, 500)
    }

    private fun send(view: View, action: Int, payload: DragPayload) {
        val event = ReflectionHelpers.callConstructor(DragEvent::class.java)
        ReflectionHelpers.setField(event, "mAction", action)
        ReflectionHelpers.setField(event, "mX", 10f)
        ReflectionHelpers.setField(event, "mY", 10f)
        ReflectionHelpers.setField(event, "mLocalState", payload)
        view.dispatchDragEvent(event)
    }

    private fun View.assertAtRest() {
        assertThat(alpha).isEqualTo(1f)
        assertThat(scaleX).isEqualTo(1f)
        assertThat(scaleY).isEqualTo(1f)
        assertThat(translationX).isEqualTo(0f)
        assertThat(translationY).isEqualTo(0f)
    }

    /** Drags the item from [from] and drops it at [moved] (the drop event's 10,10 is cell 0,0). */
    private fun Rig.dragAndDrop(item: (CellPlacement) -> HomeItemUi, renderMidDrag: Boolean) {
        val view = page.getChildAt(0)
        val payload = DragPayload.Existing("item", from)
        assertThat(controller.startDrag(view, payload)).isTrue()
        send(page, DragEvent.ACTION_DRAG_STARTED, payload)
        // e.g. a label, icon or badge update arrives while the item is held
        if (renderMidDrag) controller.render(ui(item(from).copy(zIndex = 1)))
        send(page, DragEvent.ACTION_DROP, payload)
        send(page, DragEvent.ACTION_DRAG_ENDED, payload)
        controller.render(ui(item(moved)))
        relayout()
    }

    // --- Bug: a shortcut re-bound during its drag remembered the drag dim as its resting alpha -------

    @Test fun aDroppedShortcutIsOpaqueAgainAfterARenderDuringTheDrag() = rig(shortcut(from), AnimationSpeed.NONE) {
        dragAndDrop(::shortcut, renderMidDrag = true)
        idle(900)
        page.getChildAt(0).assertAtRest()
    }

    @Test fun aCancelledShortcutDragIsOpaqueAgainAfterARenderDuringTheDrag() = rig(shortcut(from), AnimationSpeed.NONE) {
        val view = page.getChildAt(0)
        val payload = DragPayload.Existing("item", from)
        controller.startDrag(view, payload)
        send(page, DragEvent.ACTION_DRAG_STARTED, payload)
        controller.render(ui(shortcut(from).copy(zIndex = 1)))
        send(page, DragEvent.ACTION_DRAG_ENDED, payload)
        idle(900)
        page.getChildAt(0).assertAtRest()
    }

    // --- Bug: a render during a widget's pop-in or glide froze it part-way ---------------------------

    @Test fun aRenderDuringADroppedWidgetsPopInLeavesItFullSize() = rig(widget(from), AnimationSpeed.NORMAL) {
        dragAndDrop(::widget, renderMidDrag = false)
        val view = page.getChildAt(0)
        assertThat(view).isInstanceOf(WidgetCellView::class.java)
        assertThat(view.scaleX).isLessThan(1f) // pop-in under way
        controller.render(ui(widget(moved).copy(zIndex = 2))) // any later state update
        relayout()
        idle(900)
        view.assertAtRest()
    }

    @Test fun aRenderDuringAWidgetsGlideToANewCellLeavesNoOffset() = rig(widget(from), AnimationSpeed.NORMAL) {
        // e.g. Undo puts a moved widget back: it glides from its old cell.
        controller.render(ui(widget(moved)))
        relayout()
        val view = page.getChildAt(0)
        assertThat(view.translationX).isNotEqualTo(0f)
        controller.render(ui(widget(moved).copy(zIndex = 2))) // arrives while it glides
        relayout()
        idle(900)
        view.assertAtRest()
    }

    @Test fun anAppIconGlidingToANewCellIsNotFrozenByARender() = rig(app(from), AnimationSpeed.NORMAL) {
        controller.render(ui(app(moved)))
        relayout()
        val view = page.getChildAt(0)
        assertThat(view.translationX).isNotEqualTo(0f)
        controller.render(ui(app(moved).copy(zIndex = 2)))
        relayout()
        idle(900)
        view.assertAtRest()
    }

    // --- Bug: a drag started during a pop-in recorded its part-way alpha and scale as resting ----------

    @Test fun aWidgetDragStartedDuringThePopInEndsAtFullSize() = rig(widget(from), AnimationSpeed.RELAXED) {
        dragAndDrop(::widget, renderMidDrag = false)
        val view = page.getChildAt(0)
        assertThat(view.scaleX).isLessThan(1f) // a long press can finish inside a RELAXED pop-in
        val payload = DragPayload.Existing("item", moved)
        assertThat(controller.startDrag(view, payload)).isTrue()
        send(page, DragEvent.ACTION_DRAG_STARTED, payload)
        send(page, DragEvent.ACTION_DRAG_ENDED, payload)
        idle(1500)
        view.assertAtRest()
    }

    // --- Guards: a new item's fade-in cut short by a glide still ends opaque (both view types) -------

    @Test fun aNewWidgetThatMovesWhileFadingInEndsOpaque() = rig(app(from), AnimationSpeed.NORMAL) {
        val other = CellPlacement(ContainerType.DESKTOP, 0, 4, 4)
        fun two(w: CellPlacement) = WorkspaceUi.Ready(GridSpec(5, 5), GridSpec(5, 1), listOf(listOf(app(from), widget(w).copy(id = "w"))), emptyList())
        controller.render(two(moved))
        relayout()
        val view = page.itemView("w")!!
        assertThat(view.alpha).isLessThan(1f) // fading in
        controller.render(two(other)) // moved before the fade-in ended
        relayout()
        idle(900)
        view.assertAtRest()
    }

    @Test fun aNewAppThatMovesWhileFadingInEndsOpaque() = rig(widget(from).copy(id = "w"), AnimationSpeed.NORMAL) {
        val other = CellPlacement(ContainerType.DESKTOP, 0, 4, 4)
        fun two(a: CellPlacement) = WorkspaceUi.Ready(GridSpec(5, 5), GridSpec(5, 1), listOf(listOf(widget(from).copy(id = "w"), app(a))), emptyList())
        controller.render(two(moved))
        relayout()
        val view = page.itemView("item")!!
        assertThat(view.alpha).isLessThan(1f)
        controller.render(two(other))
        relayout()
        idle(900)
        view.assertAtRest()
    }

    // --- Regressions that already pass: every item type returns to rest after drop/cancel ------------

    @Test fun everyItemTypeIsAtRestAfterADropWithARenderDuringTheDrag() {
        for (make in listOf(::app, ::shortcut, ::widget)) {
            rig(make(from), AnimationSpeed.NORMAL) {
                dragAndDrop(make, renderMidDrag = true)
                idle(1500)
                page.getChildAt(0).assertAtRest()
            }
        }
    }
}
