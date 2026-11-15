package io.github.aj4abinjacob.chikku.home.folder

import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.CellPlacement
import io.github.aj4abinjacob.chikku.core.model.ComponentKey
import io.github.aj4abinjacob.chikku.core.model.ContainerType.DESKTOP
import io.github.aj4abinjacob.chikku.core.model.ContainerType.DOCK
import io.github.aj4abinjacob.chikku.core.model.FolderSource
import io.github.aj4abinjacob.chikku.core.model.GestureAction
import io.github.aj4abinjacob.chikku.core.model.GridSpec
import io.github.aj4abinjacob.chikku.core.model.ItemActions
import io.github.aj4abinjacob.chikku.core.model.ItemTarget
import io.github.aj4abinjacob.chikku.home.Availability
import io.github.aj4abinjacob.chikku.home.DragPayload
import io.github.aj4abinjacob.chikku.home.FolderEntryUi
import io.github.aj4abinjacob.chikku.home.HomeItemUi
import io.github.aj4abinjacob.chikku.home.ItemContent
import io.github.aj4abinjacob.chikku.home.WorkspaceUi
import io.github.aj4abinjacob.chikku.home.folder.FolderPopup.Companion.moved
import org.junit.Test

/** Pure folder logic: drop planning, gesture resolution, grid helpers. */
class FolderLogicTest {

    private fun key(n: Int) = AppKey("pkg$n", "pkg$n.Main", 0)
    private fun app(n: Int, availability: Availability = Availability.AVAILABLE) =
        ItemContent.App(key(n), "App $n", isWork = false, availability = availability)
    private fun at(x: Int, y: Int, page: Int = 0) = CellPlacement(DESKTOP, page, x, y)

    private val appA = HomeItemUi("a", at(0, 0), app(1))
    private val appB = HomeItemUi("b", at(1, 0), app(2))
    private val folder = HomeItemUi(
        "f", at(2, 0),
        ItemContent.Folder("Nav", listOf(FolderEntryUi("e1", app(3)), FolderEntryUi("e2", app(4)))),
    )
    private val widget = HomeItemUi(
        "w", at(0, 1).copy(spanX = 2, spanY = 2),
        ItemContent.Widget(5, ComponentKey("w", "w.P", 0), Availability.AVAILABLE),
    )
    private val dockApp = HomeItemUi("d", CellPlacement(DOCK, 0, 0, 0), app(9))
    private val dockFolder = HomeItemUi("df", CellPlacement(DOCK, 0, 1, 0), ItemContent.Folder("", emptyList()))
    private val ui = WorkspaceUi.Ready(
        desktopGrid = GridSpec(3, 3),
        dockGrid = GridSpec(3, 1),
        pages = listOf(listOf(appA, appB, folder, widget), emptyList()),
        dock = listOf(dockApp, dockFolder),
    )

    // --- Drop planning ---------------------------------------------------------------------------

    @Test
    fun appDroppedOnAppCreatesFolderOnTheTarget() {
        assertThat(FolderDropPlanner.plan(DragPayload.Existing("a", appA.placement), appB.placement, ui))
            .isEqualTo(DropPlan.CreateFolder("b", FolderSource.Item("a")))
    }

    @Test
    fun appDroppedOnFolderIsAdded() {
        assertThat(FolderDropPlanner.plan(DragPayload.Existing("a", appA.placement), folder.placement, ui))
            .isEqualTo(DropPlan.AddToFolder("f", FolderSource.Item("a")))
    }

    @Test
    fun dockWorksLikeTheDesktop() {
        assertThat(FolderDropPlanner.plan(DragPayload.Existing("a", appA.placement), dockApp.placement, ui))
            .isEqualTo(DropPlan.CreateFolder("d", FolderSource.Item("a")))
        assertThat(FolderDropPlanner.plan(DragPayload.Existing("a", appA.placement), dockFolder.placement, ui))
            .isEqualTo(DropPlan.AddToFolder("df", FolderSource.Item("a")))
    }

    @Test
    fun appFromDrawerOnAppOrFolderUsesTargetSource() {
        val payload = DragPayload.NewApp(key(7))
        assertThat(FolderDropPlanner.plan(payload, appA.placement, ui))
            .isEqualTo(DropPlan.CreateFolder("a", FolderSource.Target(ItemTarget.App(key(7)))))
        assertThat(FolderDropPlanner.plan(payload, folder.placement, ui))
            .isEqualTo(DropPlan.AddToFolder("f", FolderSource.Target(ItemTarget.App(key(7)))))
        assertThat(FolderDropPlanner.plan(payload, at(2, 2), ui)).isEqualTo(DropPlan.Default)
    }

    @Test
    fun plainMovesFoldersAndWidgetsStayDefault() {
        // Empty cell, own cell, folder onto app (no nesting), app onto a widget.
        assertThat(FolderDropPlanner.plan(DragPayload.Existing("a", appA.placement), at(2, 2), ui)).isEqualTo(DropPlan.Default)
        assertThat(FolderDropPlanner.plan(DragPayload.Existing("a", appA.placement), appA.placement, ui)).isEqualTo(DropPlan.Default)
        assertThat(FolderDropPlanner.plan(DragPayload.Existing("f", folder.placement), appA.placement, ui)).isEqualTo(DropPlan.Default)
        assertThat(FolderDropPlanner.plan(DragPayload.Existing("a", appA.placement), at(1, 2), ui)).isEqualTo(DropPlan.Default)
        assertThat(FolderDropPlanner.plan(DragPayload.Existing("a", appA.placement), appB.placement, null)).isEqualTo(DropPlan.Default)
    }

    @Test
    fun folderEntryDraggedOutLandsOnFreeOrNearestFreeCell() {
        val payload = DragPayload.FromFolder("f", "e1", ItemTarget.App(key(3)))
        assertThat(FolderDropPlanner.plan(payload, at(2, 2), ui)).isEqualTo(DropPlan.MoveOutOfFolder("f", "e1", at(2, 2)))
        assertThat(FolderDropPlanner.plan(payload, folder.placement, ui)).isEqualTo(DropPlan.Ignore)
        // Dropped on an app: nearest free cell on that page instead of nesting.
        assertThat(FolderDropPlanner.plan(payload, appB.placement, ui)).isEqualTo(DropPlan.MoveOutOfFolder("f", "e1", at(2, 1)))
        // Full dock: nowhere to go.
        val fullDock = ui.copy(dock = ui.dock + HomeItemUi("d3", CellPlacement(DOCK, 0, 2, 0), app(8)))
        assertThat(FolderDropPlanner.plan(payload, dockApp.placement, fullDock)).isEqualTo(DropPlan.Ignore)
    }

    @Test
    fun firstFreeDesktopCellSkipsFullPages() {
        assertThat(FolderDropPlanner.firstFreeDesktopCell(ui)).isEqualTo(at(2, 1))
        val full = ui.copy(pages = listOf(List(9) { HomeItemUi("x$it", at(it % 3, it / 3), app(20 + it)) }, emptyList()))
        assertThat(FolderDropPlanner.firstFreeDesktopCell(full)).isEqualTo(at(0, 0, page = 1))
        assertThat(FolderDropPlanner.firstFreeDesktopCell(full.copy(pages = full.pages.take(1)))).isNull()
    }

    @Test
    fun firstAppSkipsShortcuts() {
        val content = ItemContent.Folder(
            "",
            listOf(
                FolderEntryUi("s", ItemContent.Shortcut("p", "id", 0, "S", Availability.AVAILABLE)),
                FolderEntryUi("e", app(4)),
            ),
        )
        assertThat(FolderDropPlanner.firstApp(content)).isEqualTo(key(4))
        assertThat(FolderDropPlanner.firstApp(ItemContent.Folder("", emptyList()))).isNull()
    }

    // --- Gestures --------------------------------------------------------------------------------

    @Test
    fun defaultFolderOpensOnTapAndLeavesSwipesToHome() {
        val none = ItemActions()
        assertThat(FolderGestures.resolve(FolderGesture.TAP, none, true)).isEqualTo(FolderGestureOutcome.OpenFolder)
        assertThat(FolderGestures.resolve(FolderGesture.SWIPE_UP, none, true)).isEqualTo(FolderGestureOutcome.PassThrough)
        assertThat(FolderGestures.hasCustomGestures(none, true)).isFalse()
    }

    @Test
    fun swipeToOpenWithTapLaunchingFirstApp() {
        val actions = ItemActions(tap = GestureAction.LaunchApp(key(3)))
        assertThat(FolderGestures.resolve(FolderGesture.TAP, actions, true))
            .isEqualTo(FolderGestureOutcome.Perform(GestureAction.LaunchApp(key(3))))
        assertThat(FolderGestures.resolve(FolderGesture.SWIPE_UP, actions, true)).isEqualTo(FolderGestureOutcome.OpenFolder)
        // Only swipe up reveals the folder; swipe down stays with the home screen (notifications).
        assertThat(FolderGestures.resolve(FolderGesture.SWIPE_DOWN, actions, true)).isEqualTo(FolderGestureOutcome.PassThrough)
        assertThat(FolderGestures.claimsSwipe(FolderGesture.SWIPE_UP, actions, true)).isTrue()
        assertThat(FolderGestures.claimsSwipe(FolderGesture.SWIPE_DOWN, actions, true)).isFalse()
    }

    @Test
    fun swipeDownRunsItsOwnActionAndOtherSwipesPassThrough() {
        // Guide example: swipe down on a navigation folder launches a specific app.
        val actions = ItemActions(swipeDown = GestureAction.LaunchApp(key(4)))
        assertThat(FolderGestures.resolve(FolderGesture.SWIPE_DOWN, actions, true))
            .isEqualTo(FolderGestureOutcome.Perform(GestureAction.LaunchApp(key(4))))
        assertThat(FolderGestures.resolve(FolderGesture.SWIPE_UP, actions, true)).isEqualTo(FolderGestureOutcome.PassThrough)
        assertThat(FolderGestures.resolve(FolderGesture.TAP, actions, true)).isEqualTo(FolderGestureOutcome.OpenFolder)
    }

    @Test
    fun disabledSettingOrNoneMeansDefaults() {
        val actions = ItemActions(tap = GestureAction.OpenDrawer, swipeDown = GestureAction.LaunchApp(key(4)))
        assertThat(FolderGestures.resolve(FolderGesture.TAP, actions, false)).isEqualTo(FolderGestureOutcome.OpenFolder)
        assertThat(FolderGestures.resolve(FolderGesture.SWIPE_DOWN, actions, false)).isEqualTo(FolderGestureOutcome.PassThrough)
        assertThat(FolderGestures.hasCustomGestures(actions, false)).isFalse()
        val noneTap = ItemActions(tap = GestureAction.None)
        assertThat(FolderGestures.resolve(FolderGesture.TAP, noneTap, true)).isEqualTo(FolderGestureOutcome.OpenFolder)
        assertThat(FolderGestures.resolve(FolderGesture.SWIPE_UP, noneTap, true)).isEqualTo(FolderGestureOutcome.PassThrough)
    }

    // --- Popup helpers ---------------------------------------------------------------------------

    @Test
    fun gridColumnsAndReorderHelpers() {
        assertThat((0..10).map { FolderPopup.columnsFor(it) }).containsExactly(1, 1, 2, 2, 2, 3, 3, 3, 3, 3, 4).inOrder()
        assertThat(FolderPopup.columnsFor(40)).isEqualTo(4)
        assertThat(listOf("a", "b", "c").moved(0, 2)).containsExactly("b", "c", "a").inOrder()
        assertThat(listOf("a", "b", "c").moved(2, 0)).containsExactly("c", "a", "b").inOrder()
        assertThat(listOf("a", "b", "c").moved(1, 9)).containsExactly("a", "c", "b").inOrder()
    }
}
