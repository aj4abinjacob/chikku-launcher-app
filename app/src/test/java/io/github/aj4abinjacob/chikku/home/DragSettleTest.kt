package io.github.aj4abinjacob.chikku.home

import android.app.Activity
import android.view.ContextThemeWrapper
import android.view.DragEvent
import android.view.View
import android.widget.FrameLayout
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.CellPlacement
import io.github.aj4abinjacob.chikku.core.model.ContainerType
import io.github.aj4abinjacob.chikku.core.model.GridSpec
import io.github.aj4abinjacob.chikku.core.model.settings.AnimationSpeed
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
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

/** A dropped item stays hidden until the stored layout shows it; resting alpha is never clobbered. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestHomeApplication::class)
class DragSettleTest {
    private val from = CellPlacement(ContainerType.DESKTOP, 0, 2, 2)
    private val moved = CellPlacement(ContainerType.DESKTOP, 0, 0, 0)

    private fun app(placement: CellPlacement, availability: Availability = Availability.AVAILABLE, label: String = "Synthetic") =
        HomeItemUi("item", placement, ItemContent.App(AppKey("pkg", "pkg.Main", 0), label, false, availability))

    private fun ui(item: HomeItemUi) =
        WorkspaceUi.Ready(GridSpec(5, 5), GridSpec(5, 1), listOf(listOf(item)), emptyList())

    private class Rig(val controller: WorkspaceController, val page: CellLayout, val scope: CoroutineScope, val drops: MutableList<CellPlacement>)

    private fun rig(initial: HomeItemUi, speed: AnimationSpeed = AnimationSpeed.NONE, block: Rig.(Activity) -> Unit) {
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
            val drops = mutableListOf<CellPlacement>()
            val actions = object : WorkspaceActions {
                override fun onItemClick(item: HomeItemUi, view: View) = Unit
                override fun onDrop(payload: DragPayload, target: CellPlacement) {
                    drops += target
                }
                override fun onRemoveItem(itemId: String) = Unit
                override fun onAppInfo(key: AppKey, view: View) = Unit
            }
            val controller = WorkspaceController(
                pager, dock, PageIndicatorView(context), View(context),
                IconBinder(context, scope, FakeIconProvider()), actions, dragStarter = { _, _ -> true },
            )
            val settings = LauncherSettings().let { it.copy(lookFeel = it.lookFeel.copy(animationSpeed = speed)) }
            controller.applySettings(settings)
            controller.render(ui(initial))
            val page = pager.pageAt(0)!!
            page.layout(0, 0, 500, 500)
            Rig(controller, page, scope, drops).block(host.get())
        } finally {
            scope.cancel()
            host.pause().stop().destroy()
        }
    }

    private fun iconSource(context: android.content.Context, label: String = "Dragged", alpha: Float = 1f) =
        AppIconView(context, AppIconView.Style.WORKSPACE).apply {
            this.label = label
            icon = ColorDrawable(Color.RED)
            this.alpha = alpha
            layout(0, 0, 100, 100)
        }

    private fun added(availability: Availability = Availability.AVAILABLE) =
        HomeItemUi("new", moved, ItemContent.App(AppKey("pkg2", "pkg2.Main", 0), "Other", false, availability))

    private fun withAdded(item: HomeItemUi) =
        WorkspaceUi.Ready(GridSpec(5, 5), GridSpec(5, 1), listOf(listOf(app(from), item)), emptyList())

    private fun drop(rig: Rig, payload: DragPayload, source: View) {
        assertThat(rig.controller.startDrag(source, payload)).isTrue()
        send(rig.page, DragEvent.ACTION_DRAG_STARTED, payload)
        send(rig.page, DragEvent.ACTION_DROP, payload)
        send(rig.page, DragEvent.ACTION_DRAG_ENDED, payload)
    }

    private fun idle(ms: Long) = shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    private fun newApp() = DragPayload.NewApp(AppKey("pkg2", "pkg2.Main", 0))

    private fun realView(page: CellLayout) = page.viewAt(Cell(0, 0))

    private fun send(view: View, action: Int, payload: DragPayload) {
        val event = ReflectionHelpers.callConstructor(DragEvent::class.java)
        ReflectionHelpers.setField(event, "mAction", action)
        ReflectionHelpers.setField(event, "mX", 10f)
        ReflectionHelpers.setField(event, "mY", 10f)
        ReflectionHelpers.setField(event, "mLocalState", payload)
        view.dispatchDragEvent(event)
    }

    @Test fun droppedItemStaysHiddenUntilTheStoredLayoutShowsIt() = rig(app(from)) {
        val view = page.getChildAt(0)
        val payload = DragPayload.Existing("item", from)
        assertThat(controller.startDrag(view, payload)).isTrue()
        assertThat(view.alpha).isWithin(0.001f).of(0.3f)
        send(page, DragEvent.ACTION_DRAG_STARTED, payload)
        send(page, DragEvent.ACTION_DROP, payload)
        send(page, DragEvent.ACTION_DRAG_ENDED, payload)
        assertThat(drops).hasSize(1)
        assertThat(view.alpha).isEqualTo(0f) // no flash back at the old cell while the move is stored

        controller.render(ui(app(moved)))
        assertThat(page.getChildAt(0).alpha).isEqualTo(1f)
        assertThat((page.getChildAt(0).layoutParams as CellLayout.LayoutParams).x).isEqualTo(0)
    }

    @Test fun aDropThatStoresNothingReturnsTheItemToItsOldCell() = rig(app(from, Availability.UNAVAILABLE)) {
        val view = page.getChildAt(0)
        val rest = view.alpha
        assertThat(rest).isWithin(0.001f).of(0.55f)
        val payload = DragPayload.Existing("item", from)
        controller.startDrag(view, payload)
        send(page, DragEvent.ACTION_DRAG_STARTED, payload)
        send(page, DragEvent.ACTION_DROP, payload)
        send(page, DragEvent.ACTION_DRAG_ENDED, payload)
        assertThat(view.alpha).isEqualTo(0f)

        shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(900))
        assertThat(view.alpha).isWithin(0.001f).of(rest) // the unavailable dimming, not a hard-coded 1
    }

    @Test fun aCancelledDragRestoresTheRestingAlphaNotOne() = rig(app(from, Availability.UNAVAILABLE)) {
        val view = page.getChildAt(0)
        val payload = DragPayload.Existing("item", from)
        controller.startDrag(view, payload)
        send(page, DragEvent.ACTION_DRAG_STARTED, payload)
        send(page, DragEvent.ACTION_DRAG_ENDED, payload)
        assertThat(view.alpha).isWithin(0.001f).of(0.55f)
    }

    @Test fun aRenderDuringTheDragDoesNotResetTheHeldSource() = rig(app(from)) {
        val view = page.getChildAt(0)
        val payload = DragPayload.Existing("item", from)
        controller.startDrag(view, payload)
        send(page, DragEvent.ACTION_DRAG_STARTED, payload)
        controller.render(ui(app(from, label = "Renamed")))
        assertThat(page.getChildAt(0).alpha).isWithin(0.001f).of(0.3f)
        send(page, DragEvent.ACTION_DRAG_ENDED, payload)
        assertThat(page.getChildAt(0).alpha).isEqualTo(1f)
    }

    @Test fun aDroppedWidgetIsOpaqueAgainAtItsNewCell() {
        fun widget(placement: CellPlacement) = HomeItemUi(
            "item", placement,
            ItemContent.Widget(5, io.github.aj4abinjacob.chikku.core.model.ComponentKey("w", "w.P", 0), Availability.AVAILABLE),
        )
        rig(widget(from)) {
            // The real binder never sets alpha for a widget, so a dimmed cell would be remembered as normal.
            controller.widgetBinder = object : io.github.aj4abinjacob.chikku.home.widget.WidgetViewBinder {
                override fun createWidgetView(): View = View(page.context)
                override fun isWidgetView(view: View) = view !is AppIconView
                override fun bindWidget(view: View, item: HomeItemUi, content: ItemContent.Widget) = Unit
                override fun recycleWidget(view: View) = Unit
                override fun onWorkspaceRendered(ui: WorkspaceUi.Ready) = Unit
            }
            controller.render(ui(widget(moved)))
            controller.render(ui(widget(from)))
            val view = page.getChildAt(0)
            val payload = DragPayload.Existing("item", from)
            assertThat(controller.startDrag(view, payload)).isTrue()
            send(page, DragEvent.ACTION_DRAG_STARTED, payload)
            controller.render(ui(widget(from).copy(zIndex = 1))) // a render mid-drag must not record the dim
            send(page, DragEvent.ACTION_DROP, payload)
            send(page, DragEvent.ACTION_DRAG_ENDED, payload)
            controller.render(ui(widget(moved)))
            idle(900)
            assertThat(page.getChildAt(0).alpha).isEqualTo(1f)
        }
    }

    @Test fun dropOutcomeCountsOnlyChangesThatReachTheStoredLayout() {
        val before = ui(app(from))
        val payload = DragPayload.Existing("item", from)
        assertThat(DropOutcome.applied(payload, before, before.copy(pages = listOf(listOf(app(from, label = "Other")))))).isFalse()
        assertThat(DropOutcome.applied(payload, before, ui(app(moved)))).isTrue()
        assertThat(DropOutcome.applied(payload, before, ui(app(from)).copy(pages = listOf(emptyList())))).isTrue() // removed or in a folder
        val newApp = DragPayload.NewApp(AppKey("pkg", "pkg.Main", 0))
        assertThat(DropOutcome.applied(newApp, before, before)).isFalse()
        assertThat(DropOutcome.applied(newApp, before, ui(app(moved)))).isTrue()
    }

    @Test fun aDrawerDropShowsTheIconAtItsTargetUntilTheStoredLayoutArrives() = rig(app(from)) {
        val newApp = DragPayload.NewApp(AppKey("pkg2", "pkg2.Main", 0))
        val dragged = iconSource(page.context)
        assertThat(controller.startDrag(dragged, newApp)).isTrue()
        send(page, DragEvent.ACTION_DRAG_STARTED, newApp)
        send(page, DragEvent.ACTION_DROP, newApp)
        send(page, DragEvent.ACTION_DRAG_ENDED, newApp)
        assertThat(drops).hasSize(1)
        // The drag shadow is gone and nothing is stored yet: the cell must not be empty.
        assertThat(controller.hasDropGhost).isTrue()
        assertThat(controller.holdsDragSource).isFalse() // the drag no longer points at the drawer view

        val added = HomeItemUi("new", moved, ItemContent.App(AppKey("pkg2", "pkg2.Main", 0), "Other", false, Availability.AVAILABLE))
        controller.render(WorkspaceUi.Ready(GridSpec(5, 5), GridSpec(5, 1), listOf(listOf(app(from), added)), emptyList()))
        assertThat(controller.hasDropGhost).isFalse() // animations off: handed over at once
    }

    @Test fun aDropThatNeverArrivesDropsTheGhostWithTheTimeout() = rig(app(from)) {
        val newApp = DragPayload.NewApp(AppKey("pkg2", "pkg2.Main", 0))
        controller.startDrag(iconSource(page.context), newApp)
        send(page, DragEvent.ACTION_DRAG_STARTED, newApp)
        send(page, DragEvent.ACTION_DROP, newApp)
        assertThat(controller.hasDropGhost).isTrue()
        shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(900))
        assertThat(controller.hasDropGhost).isFalse()
    }

    @Test fun noGhostWhenTheDropLandsOnAnotherItem() = rig(app(from)) {
        val newApp = DragPayload.NewApp(AppKey("pkg2", "pkg2.Main", 0))
        controller.startDrag(iconSource(page.context), newApp)
        send(page, DragEvent.ACTION_DRAG_STARTED, newApp)
        // The existing item sits at cell (2,2) of the 500px page: 5x5 cells of 100px.
        val event = ReflectionHelpers.callConstructor(DragEvent::class.java)
        ReflectionHelpers.setField(event, "mAction", DragEvent.ACTION_DROP)
        ReflectionHelpers.setField(event, "mX", 250f)
        ReflectionHelpers.setField(event, "mY", 250f)
        ReflectionHelpers.setField(event, "mLocalState", newApp)
        page.dispatchDragEvent(event)
        assertThat(controller.hasDropGhost).isFalse() // dropping onto an item has its own feedback
    }

    @Test fun aWidgetViewNeverGetsAGhostOrIsRetained() = rig(app(from)) {
        val moveWidget = DragPayload.Existing("item", from)
        val widgetView = View(page.context).apply { layout(0, 0, 100, 100) } // not an icon view: how widgets look here
        controller.startDrag(widgetView, moveWidget)
        assertThat(controller.holdsDragSource).isFalse()
        send(page, DragEvent.ACTION_DRAG_STARTED, moveWidget)
        send(page, DragEvent.ACTION_DROP, moveWidget)
        assertThat(controller.hasDropGhost).isFalse()
    }

    @Test fun nothingOfTheDragIsRetainedAfterItEndsOrIsCancelled() = rig(app(from)) {
        val newApp = DragPayload.NewApp(AppKey("pkg2", "pkg2.Main", 0))
        controller.startDrag(iconSource(page.context), newApp)
        assertThat(controller.holdsDragSource).isTrue()
        send(page, DragEvent.ACTION_DRAG_STARTED, newApp)
        send(page, DragEvent.ACTION_DRAG_ENDED, newApp) // ended without a drop
        assertThat(controller.holdsDragSource).isFalse()
        assertThat(controller.hasDropGhost).isFalse()

        assertThat(controller.startDrag(iconSource(page.context), newApp)).isTrue()
        assertThat(controller.holdsDragSource).isTrue()
        send(page, DragEvent.ACTION_DRAG_STARTED, newApp)
        send(page, DragEvent.ACTION_DROP, newApp)
        assertThat(controller.hasDropGhost).isTrue()
        controller.cancelDrag()
        assertThat(controller.holdsDragSource).isFalse()
        assertThat(controller.hasDropGhost).isFalse()
    }

    @Test fun aMovedItemKeepsItsRestingAlphaAfterTheGhostHandsOver() = rig(app(from, Availability.UNAVAILABLE)) {
        val view = page.getChildAt(0)
        val payload = DragPayload.Existing("item", from)
        controller.startDrag(view, payload)
        send(page, DragEvent.ACTION_DRAG_STARTED, payload)
        send(page, DragEvent.ACTION_DROP, payload)
        send(page, DragEvent.ACTION_DRAG_ENDED, payload)
        assertThat(controller.hasDropGhost).isTrue()
        controller.render(ui(app(moved, Availability.UNAVAILABLE)))
        assertThat(controller.hasDropGhost).isFalse()
        assertThat(page.getChildAt(0).alpha).isWithin(0.001f).of(0.55f)
    }

    @Test fun ghostLandsAtTheTargetCellWithTheDraggedLabelAndTheRealViewStaysHiddenUntilItEnds() =
        rig(app(from), AnimationSpeed.NORMAL) {
            drop(this, newApp(), iconSource(page.context, label = "Dragged"))
            val ghost = controller.dropGhostView as AppIconView
            assertThat(ghost.parent).isNotNull() // lives in the page's overlay
            assertThat(ghost.label).isEqualTo("Dragged")
            assertThat(ghost.alpha).isEqualTo(1f)
            val cell = android.graphics.Rect().also { page.cellBounds(Cell(0, 0), 1, 1, it) }
            assertThat(listOf(ghost.left, ghost.top, ghost.right, ghost.bottom)).containsExactly(cell.left, cell.top, cell.right, cell.bottom).inOrder()

            assertThat(ghost.scaleX).isEqualTo(0.6f) // lifted, about to grow
            controller.render(withAdded(added()))
            val real = realView(page)!!
            assertThat(controller.hasDropGhost).isTrue() // still landing
            assertThat(real.alpha).isEqualTo(0f) // never both visible
            idle(600)
            assertThat(controller.hasDropGhost).isFalse()
            assertThat(ghost.parent).isNull()
            assertThat(ghost.label).isEmpty() // nothing of the dragged app is kept
            assertThat(real.alpha).isEqualTo(1f)
        }

    @Test fun anUnavailableAppKeepsItsDimmedAlphaOnTheGhostAndTheRealView() = rig(app(from), AnimationSpeed.NORMAL) {
        drop(this, newApp(), iconSource(page.context, alpha = 0.55f))
        assertThat(controller.dropGhostView!!.alpha).isWithin(0.001f).of(0.55f)
        controller.render(withAdded(added(Availability.UNAVAILABLE)))
        idle(800)
        assertThat(controller.hasDropGhost).isFalse()
        assertThat(realView(page)!!.alpha).isWithin(0.001f).of(0.55f)
    }

    @Test fun stopDuringTheLandingRestoresTheRealView() = rig(app(from), AnimationSpeed.NORMAL) {
        drop(this, newApp(), iconSource(page.context))
        controller.render(withAdded(added()))
        val real = realView(page)!!
        assertThat(real.alpha).isEqualTo(0f)
        controller.cancelDrag()
        assertThat(controller.hasDropGhost).isFalse()
        assertThat(real.alpha).isEqualTo(1f) // never left invisible
    }

    @Test fun clearDuringTheLandingRestoresTheRealView() = rig(app(from), AnimationSpeed.NORMAL) {
        drop(this, newApp(), iconSource(page.context))
        controller.render(withAdded(added()))
        val real = realView(page)!!
        assertThat(real.alpha).isEqualTo(0f)
        controller.clear()
        assertThat(controller.hasDropGhost).isFalse()
        assertThat(real.alpha).isEqualTo(1f)
    }

    @Test fun aRenderDuringTheLandingKeepsTheRealViewHiddenAndRevealsItAtItsRestingAlpha() = rig(app(from), AnimationSpeed.NORMAL) {
        drop(this, newApp(), iconSource(page.context))
        controller.render(withAdded(added(Availability.UNAVAILABLE)))
        assertThat(controller.hasDropGhost).isTrue()
        assertThat(realView(page)!!.alpha).isEqualTo(0f)
        val renamed = added(Availability.UNAVAILABLE).copy(content = ItemContent.App(AppKey("pkg2", "pkg2.Main", 0), "Renamed", false, Availability.UNAVAILABLE))
        controller.render(withAdded(renamed))
        assertThat(realView(page)!!.alpha).isEqualTo(0f) // the re-bind did not show a second icon
        idle(800)
        assertThat(realView(page)!!.alpha).isWithin(0.001f).of(0.55f)
    }

    @Test fun anUnrelatedRenderDoesNotRemoveTheGhostBeforeTheItemExists() = rig(app(from), AnimationSpeed.NORMAL) {
        drop(this, newApp(), iconSource(page.context))
        controller.render(ui(app(from, label = "Renamed"))) // pages differ, but the dropped app is not there yet
        assertThat(controller.hasDropGhost).isTrue()
        controller.render(withAdded(added()))
        idle(800)
        assertThat(controller.hasDropGhost).isFalse()
        assertThat(realView(page)!!.alpha).isEqualTo(1f)
    }

    @Test fun aDropThatIsNeverStoredFadesTheGhostOutAndLeavesNothingBehind() = rig(app(from), AnimationSpeed.NORMAL) {
        drop(this, newApp(), iconSource(page.context))
        val ghost = controller.dropGhostView!!
        idle(900)
        assertThat(controller.hasDropGhost).isFalse()
        assertThat(ghost.parent).isNull()
        assertThat(page.childCount).isEqualTo(1)
    }

    @Test fun anEntryDraggedOutOfAFolderGetsAGhostToo() = rig(app(from), AnimationSpeed.NORMAL) {
        drop(this, DragPayload.FromFolder("folder", "entry", io.github.aj4abinjacob.chikku.core.model.ItemTarget.App(AppKey("pkg2", "pkg2.Main", 0))), iconSource(page.context, label = "Entry"))
        assertThat((controller.dropGhostView as AppIconView).label).isEqualTo("Entry")
    }

    @Test fun aLandingThatNeverReportsItsEndIsCutByTheSafetyTimeout() = rig(app(from), AnimationSpeed.NORMAL) {
        drop(this, newApp(), iconSource(page.context))
        controller.stallAnimationEndsForTest = true
        controller.render(withAdded(added(Availability.UNAVAILABLE)))
        val real = realView(page)!!
        assertThat(real.alpha).isEqualTo(0f)
        idle(450) // landing time plus the slack
        assertThat(controller.hasDropGhost).isFalse()
        assertThat(real.alpha).isWithin(0.001f).of(0.55f)
    }

    @Test fun theRealViewRevealsAtItsOwnRestingAlphaNotTheDraggedViewsOrZero() = rig(app(from), AnimationSpeed.NORMAL) {
        // The drawer row was undimmed, but the stored app is unavailable: the real icon rests at 0.55.
        drop(this, newApp(), iconSource(page.context, alpha = 1f))
        controller.render(withAdded(added(Availability.UNAVAILABLE)))
        val real = realView(page)!!
        assertThat(real.alpha).isEqualTo(0f)
        idle(800)
        assertThat(real.alpha).isWithin(0.001f).of(0.55f)
    }

    @Test fun aViewStillFadingInReportsItsTargetAlphaNotTheMidFadeValue() = rig(app(from), AnimationSpeed.NORMAL) {
        controller.render(withAdded(added()))
        val real = realView(page)!!
        assertThat(real.alpha).isEqualTo(0f) // fade-in just began
        assertThat(page.restingAlpha(real)).isEqualTo(1f)
    }

    @Test fun theGhostDrawsACopyOfTheIconAndNoGhostIsMadeWithoutOne() = rig(app(from), AnimationSpeed.NORMAL) {
        val source = iconSource(page.context)
        val bitmapIcon = android.graphics.drawable.BitmapDrawable(page.context.resources, android.graphics.Bitmap.createBitmap(4, 4, android.graphics.Bitmap.Config.ARGB_8888))
        source.icon = bitmapIcon
        drop(this, newApp(), source)
        val ghost = controller.dropGhostView as AppIconView
        assertThat(ghost.icon).isNotNull()
        assertThat(ghost.icon).isNotSameInstanceAs(bitmapIcon) // never shared with the source view
        controller.cancelDrag()

        val noCopy = object : android.graphics.drawable.Drawable() {
            override fun draw(canvas: android.graphics.Canvas) = Unit
            override fun setAlpha(alpha: Int) = Unit
            override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) = Unit
            @Deprecated("Deprecated in Java")
            override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
        }
        val folderLike = iconSource(page.context).apply { icon = noCopy }
        drop(this, newApp(), folderLike)
        assertThat(controller.hasDropGhost).isFalse() // the old behaviour: no ghost
    }

    @Test fun anUnrelatedItemAtTheTargetCellDoesNotSatisfyTheDrop() = rig(app(from), AnimationSpeed.NORMAL) {
        drop(this, newApp(), iconSource(page.context))
        controller.render(ui(app(moved))) // the existing item moved into the ghost's cell; nothing new exists
        assertThat(controller.hasDropGhost).isTrue()
        assertThat(realView(page)!!.alpha).isEqualTo(1f) // the unrelated item is not hidden under the ghost
        controller.render(withAdded(added().copy(placement = CellPlacement(ContainerType.DESKTOP, 0, 3, 3)))) // placed elsewhere
        idle(800)
        assertThat(controller.hasDropGhost).isFalse() // the ghost faded out, no stuck state
        val arrived = page.viewAt(Cell(3, 3))!!
        assertThat(arrived.alpha).isEqualTo(1f)
    }

    @Test fun aGhostThatIsStillFadingOutIsReleasedByStopAndClear() {
        for (release in 0..1) {
            rig(app(from), AnimationSpeed.NORMAL) {
                drop(this, newApp(), iconSource(page.context, label = "Secret"))
                val ghost = controller.dropGhostView as AppIconView
                controller.stallAnimationEndsForTest = true // the fade is still running when stop or clear arrives
                shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(600)) // timeout: the fade starts
                assertThat(controller.hasDropGhost).isFalse()
                assertThat(controller.fadingGhostView).isSameInstanceAs(ghost)
                if (release == 0) controller.cancelDrag() else controller.clear()
                assertThat(controller.fadingGhostView).isNull()
                assertThat(ghost.parent).isNull()
                assertThat(ghost.label).isEmpty()
                assertThat(ghost.icon).isNull()
            }
        }
    }

    @Test fun anItemPickedUpMidFadeInStaysDimmedWhenABindMovesItAndRestoresAfter() = rig(app(from), AnimationSpeed.NORMAL) {
        val exactly = View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY)
        controller.render(withAdded(added())) // "new" fades in
        page.measure(exactly, exactly)
        page.layout(0, 0, 500, 500)
        val view = page.viewAt(Cell(0, 0))!!
        assertThat(view.alpha).isLessThan(1f) // the fade-in is under way
        assertThat(page.restingAlpha(view)).isEqualTo(1f)
        val payload = DragPayload.Existing("new", moved)
        assertThat(controller.startDrag(view, payload)).isTrue()
        assertThat(view.alpha).isEqualTo(0.3f)
        send(page, DragEvent.ACTION_DRAG_STARTED, payload)

        controller.render(withAdded(added().copy(placement = CellPlacement(ContainerType.DESKTOP, 0, 3, 3)))) // a bind moves it
        page.measure(exactly, exactly)
        page.layout(0, 0, 500, 500)
        assertThat(view.translationX).isNotEqualTo(0f) // its glide to the new cell is under way
        idle(50)
        val moving = page.viewAt(Cell(3, 3))!!
        assertThat(moving).isSameInstanceAs(view)
        assertThat(moving.alpha).isEqualTo(0.3f)

        send(page, DragEvent.ACTION_DRAG_ENDED, payload)
        idle(1000)
        assertThat(moving.alpha).isEqualTo(1f)
    }
}
