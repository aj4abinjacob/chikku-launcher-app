package io.github.aj4abinjacob.chikku.home

import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.CellPlacement
import io.github.aj4abinjacob.chikku.core.model.ComponentKey
import io.github.aj4abinjacob.chikku.core.model.ContainerType
import io.github.aj4abinjacob.chikku.core.model.GridSpec
import io.github.aj4abinjacob.chikku.core.model.ItemTarget
import io.github.aj4abinjacob.chikku.core.model.Workspace
import io.github.aj4abinjacob.chikku.core.model.WorkspaceItem
import io.github.aj4abinjacob.chikku.core.model.WorkspaceState
import io.github.aj4abinjacob.chikku.core.model.layout.HiddenLayoutRecovery
import kotlinx.coroutines.test.runTest
import org.junit.Test

class HiddenItemsTest {

    private fun app(id: String, container: ContainerType, x: Int, y: Int = 0, page: Int = 0) =
        WorkspaceItem(id, CellPlacement(container, page, x, y), ItemTarget.App(AppKey("p.$id", "p.$id.A", 0L)))

    private fun workspace(vararg items: WorkspaceItem, desktop: GridSpec = GridSpec(3, 3), pages: Int = 1) =
        Workspace(desktop, GridSpec(4, 1), pages, items.toList())

    @Test
    fun findsDesktopAndDockItemsOutsideTheGrid() {
        val ws = workspace(
            app("ok", ContainerType.DESKTOP, 2, 2),
            app("right", ContainerType.DESKTOP, 3, 0),
            app("below", ContainerType.DESKTOP, 0, 3),
            app("dockOk", ContainerType.DOCK, 3),
            app("dockOut", ContainerType.DOCK, 5),
        )
        assertThat(HiddenItems.find(ws).map { it.id }).containsExactly("right", "below", "dockOut")
    }

    @Test
    fun relocateMovesHiddenItemsToFirstFreeCells() = runTest {
        val original = workspace(
                    app("a", ContainerType.DESKTOP, 0, 0),
                    app("hidden1", ContainerType.DESKTOP, 4, 4),
                    app("hidden2", ContainerType.DOCK, 7),
        )
        val plan = HiddenLayoutRecovery.plan(original)
        assertThat(plan.result.moved).isEqualTo(2)
        assertThat(plan.result.remaining).isEqualTo(0)
        assertThat(plan.content.items.filter { it.id.startsWith("hidden") }.map { it.id to it.placement }).containsExactly(
            "hidden1" to CellPlacement(ContainerType.DESKTOP, 0, 1, 0),
            "hidden2" to CellPlacement(ContainerType.DESKTOP, 0, 2, 0),
        ).inOrder()
        assertThat(plan.content.items.map { it.target }).containsExactlyElementsIn(original.items.map { it.target })
    }

    @Test
    fun relocateAddsPageWhenFullAndSkipsTooLargeItems() = runTest {
        val full = (0 until 2).flatMap { y -> (0 until 2).map { x -> app("f$x$y", ContainerType.DESKTOP, x, y) } }
        val widget = WorkspaceItem(
            "w",
            CellPlacement(ContainerType.DESKTOP, 0, 0, 2, spanX = 3, spanY = 1),
            ItemTarget.Widget(7, ComponentKey("p.w", "p.w.Provider", 0L)),
        )
        val plan = HiddenLayoutRecovery.plan(workspace(*(full + app("h", ContainerType.DESKTOP, 5, 5) + widget).toTypedArray(), desktop = GridSpec(2, 2)))
        assertThat(plan.content.items.single { it.id == "h" }.placement).isEqualTo(CellPlacement(ContainerType.DESKTOP, 1, 0, 0))
        assertThat(plan.content.items.single { it.id == "w" }).isEqualTo(widget)
        assertThat(plan.result.moved).isEqualTo(1)
        assertThat(plan.result.remaining).isEqualTo(1)
        assertThat(plan.content.pageCount).isEqualTo(2)
    }
}
