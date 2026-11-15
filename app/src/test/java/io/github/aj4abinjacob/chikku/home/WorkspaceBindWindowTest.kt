package io.github.aj4abinjacob.chikku.home

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.CellPlacement
import io.github.aj4abinjacob.chikku.core.model.ComponentKey
import io.github.aj4abinjacob.chikku.core.model.ContainerType
import io.github.aj4abinjacob.chikku.core.model.GridSpec
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.home.widget.WidgetViewBinder
import io.github.aj4abinjacob.chikku.message.RecordingMessages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Pure bind-window math (memory): which desktop pages hold real views. */
class PagerBindWindowMathTest {

    @Test
    fun atRestBindsTheCurrentPageAndOneNeighbourOnEachSide() {
        assertThat(PagerMath.bindWindow(current = 0, position = 0f, pageCount = 5, wrap = false)).containsExactly(0, 1)
        assertThat(PagerMath.bindWindow(current = 2, position = 2f, pageCount = 5, wrap = false)).containsExactly(1, 2, 3)
        assertThat(PagerMath.bindWindow(current = 4, position = 4f, pageCount = 5, wrap = false)).containsExactly(3, 4)
        assertThat(PagerMath.bindWindow(current = 0, position = 0f, pageCount = 1, wrap = true)).containsExactly(0)
        assertThat(PagerMath.bindWindow(current = 0, position = 0f, pageCount = 0, wrap = false)).isEmpty()
    }

    @Test
    fun infiniteScrollWrapsTheNeighbours() {
        assertThat(PagerMath.bindWindow(current = 0, position = 0f, pageCount = 5, wrap = true)).containsExactly(4, 0, 1)
        assertThat(PagerMath.bindWindow(current = 4, position = 4f, pageCount = 5, wrap = true)).containsExactly(3, 4, 0)
        // Settled in the virtual slot past the end (before the pager normalizes the scroll).
        assertThat(PagerMath.bindWindow(current = 0, position = 5f, pageCount = 5, wrap = true)).containsExactly(4, 0, 1)
    }

    @Test
    fun movingBindsAheadAndReleasesOnlyOnceSettled() {
        val atRest = PagerMath.bindWindow(current = 2, position = 2f, pageCount = 6, wrap = false)
        // Swiping towards page 3: page 4 is bound before page 3 even arrives; nothing is released.
        val swiping = PagerMath.bindWindow(current = 2, position = 2.3f, pageCount = 6, wrap = false, previous = atRest)
        assertThat(swiping).containsExactly(1, 2, 3, 4)
        // The snap picked page 3 but the pager is still on its way: page 1 is still on screen.
        val snapping = PagerMath.bindWindow(current = 3, position = 2.6f, pageCount = 6, wrap = false, previous = swiping)
        assertThat(snapping).containsExactly(1, 2, 3, 4)
        // Settled: page 1 is released.
        assertThat(PagerMath.bindWindow(current = 3, position = 3f, pageCount = 6, wrap = false, previous = snapping))
            .containsExactly(2, 3, 4)
    }

    @Test
    fun aMultiPageJumpBindsEveryPageItPasses() {
        var bound = PagerMath.bindWindow(current = 0, position = 0f, pageCount = 8, wrap = false)
        for (step in 1..50) {
            val position = step * 6f / 50
            bound = PagerMath.bindWindow(current = 6, position = position, pageCount = 8, wrap = false, previous = bound)
            val low = kotlin.math.floor(position).toInt()
            val high = kotlin.math.ceil(position).toInt()
            assertThat(bound).containsAtLeastElementsIn(setOf(low, high)) // whatever is on screen is bound
        }
        assertThat(PagerMath.bindWindow(current = 6, position = 6f, pageCount = 8, wrap = false, previous = bound))
            .containsExactly(5, 6, 7)
    }

    @Test
    fun wrappingSwipeAcrossTheSeamBindsTheOtherEnd() {
        // Swiping right of page 0 into the last page through the wrap seam (position < 0).
        val bound = PagerMath.bindWindow(current = 0, position = -0.4f, pageCount = 5, wrap = true, previous = setOf(4, 0, 1))
        assertThat(bound).containsExactly(3, 4, 0, 1)
    }

    @Test
    fun pinnedPagesStayBound() {
        assertThat(PagerMath.bindWindow(current = 4, position = 4f, pageCount = 6, wrap = false, pinned = setOf(0, 9)))
            .containsExactly(0, 3, 4, 5)
    }
}

/** The workspace binds real views only for the pages in the bind window. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestHomeApplication::class)
class WorkspaceBindWindowTest {

    private val context: Context =
        ContextThemeWrapper(ApplicationProvider.getApplicationContext<Context>(), R.style.Theme_Chikku_Home)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val pager = WorkspacePager(context)
    private val dock = CellLayout(context)
    private val indicator = PageIndicatorView(context)
    private val removeTarget = View(context).apply { visibility = View.GONE }
    private val drops = mutableListOf<CellPlacement>()

    private val actions = object : WorkspaceActions {
        override fun onItemClick(item: HomeItemUi, view: View) = Unit
        override fun onDrop(payload: DragPayload, target: CellPlacement) {
            drops += target
        }
        override fun onRemoveItem(itemId: String) = Unit
        override fun onAppInfo(key: AppKey, view: View) = Unit
    }

    /** Records widget views created and recycled, like WidgetsController without a real host. */
    private inner class RecordingWidgets : WidgetViewBinder {
        val created = mutableListOf<View>()
        val recycled = mutableListOf<View>()
        override fun createWidgetView(): View = FrameLayout(context).also { created += it }
        override fun isWidgetView(view: View) = view in created
        override fun bindWidget(view: View, item: HomeItemUi, content: ItemContent.Widget) {
            view.setTag(R.id.home_item_model, item)
        }
        override fun recycleWidget(view: View) {
            recycled += view
        }
        override fun onWorkspaceRendered(ui: WorkspaceUi.Ready) = Unit
    }

    private val widgets = RecordingWidgets()

    private val controller = WorkspaceController(
        pager, dock, indicator, removeTarget, IconBinder(context, scope, FakeIconProvider()), actions, RecordingMessages(),
        dragStarter = { _, _ -> true },
    ).also { it.widgetBinder = widgets }

    init {
        FrameLayout(context).apply {
            addView(pager)
            addView(dock)
            addView(indicator)
        }
    }

    @After
    fun tearDown() = scope.cancel()

    private fun app(id: String, page: Int, x: Int = 0) = HomeItemUi(
        id,
        CellPlacement(ContainerType.DESKTOP, page, x, 0),
        ItemContent.App(AppKey("pkg.$id", "pkg.$id.Main", 0L), id, isWork = false, availability = Availability.AVAILABLE),
    )

    private fun widget(id: String, page: Int) = HomeItemUi(
        id,
        CellPlacement(ContainerType.DESKTOP, page, 1, 1, 2, 2),
        ItemContent.Widget(appWidgetId = 40 + page, provider = ComponentKey("pkg.w", "pkg.w.Provider", 0L), availability = Availability.AVAILABLE),
    )

    /** Five pages, each with an app and a widget. */
    private fun ready(pageCount: Int = 5) = WorkspaceUi.Ready(
        GridSpec(4, 5),
        GridSpec(4, 1),
        (0 until pageCount).map { listOf(app("a$it", it), widget("w$it", it)) },
        listOf(HomeItemUi("dock", CellPlacement(ContainerType.DOCK, 0, 0, 0), ItemContent.App(AppKey("pkg.d", "pkg.d.Main", 0L), "d", false, Availability.AVAILABLE))),
    )

    private fun children(page: Int): List<View> = pager.pageAt(page)!!.let { layout -> (0 until layout.childCount).map(layout::getChildAt) }

    private fun assertBound(page: Int) {
        val views = children(page)
        assertThat(views.map { it.getTag(R.id.home_item_id) }).containsExactly("a$page", "w$page")
        assertThat(views.filterIsInstance<AppIconView>()).hasSize(1)
        assertThat(views.count { it in widgets.created }).isEqualTo(1)
        assertThat(pager.pageAt(page)!!.contentBound).isTrue()
    }

    private fun assertReleased(page: Int) {
        val layout = pager.pageAt(page)!!
        val views = children(page)
        assertThat(layout.contentBound).isFalse()
        // Placeholders keep identity, model and cell so lookups, occupancy and drops still work.
        assertThat(views.map { it.getTag(R.id.home_item_id) }).containsExactly("a$page", "w$page")
        assertThat(views.all { it is CellLayout.Placeholder }).isTrue()
        assertThat(views.map { (it.getTag(R.id.home_item_model) as HomeItemUi).id }).containsExactly("a$page", "w$page")
        assertThat(layout.isOccupied(Cell(1, 1), 1, 1, excludeId = null)).isTrue()
        assertThat(views.all { it.importantForAccessibility == View.IMPORTANT_FOR_ACCESSIBILITY_NO }).isTrue()
    }

    @Test
    fun firstRenderBindsOnlyTheDefaultPageWindow() {
        controller.applySettings(LauncherSettings().let { it.copy(home = it.home.copy(defaultPage = 2)) })
        controller.render(ready())

        assertThat(pager.pageCount).isEqualTo(5)
        assertThat(controller.boundPages).containsExactly(1, 2, 3).inOrder()
        listOf(1, 2, 3).forEach(::assertBound)
        listOf(0, 4).forEach(::assertReleased)
        assertThat(widgets.created).hasSize(3)
        // The dock is always bound.
        assertThat(dock.getChildAt(0)).isInstanceOf(AppIconView::class.java)
    }

    @Test
    fun scrollingAwayReleasesAndComingBackRebinds() {
        controller.render(ready())
        assertThat(controller.boundPages).containsExactly(0, 1)
        val firstIcon = children(0).filterIsInstance<AppIconView>().single()
        val firstWidget = children(0).single { it in widgets.created }

        pager.snapToPage(3, animate = false) // synchronous: callers may look up the page's views next
        assertThat(controller.boundPages).containsExactly(2, 3, 4)
        listOf(2, 3, 4).forEach(::assertBound)
        assertReleased(0)
        assertReleased(1)
        assertThat(widgets.recycled).contains(firstWidget)
        assertThat(firstIcon.parent).isNull()

        pager.snapToPage(0, animate = false)
        assertThat(controller.boundPages).containsExactly(0, 1)
        assertBound(0)
        assertReleased(3)
        assertReleased(4)
    }

    @Test
    fun infiniteScrollKeepsTheLastPageBoundNextToTheFirst() {
        controller.applySettings(LauncherSettings().let { it.copy(home = it.home.copy(infiniteScroll = true)) })
        controller.render(ready())
        assertThat(controller.boundPages).containsExactly(0, 1, 4)
    }

    @Test
    fun rerenderKeepsTheWindowAndRemovedPagesAreRecycled() {
        controller.render(ready())
        pager.snapToPage(4, animate = false)
        assertThat(controller.boundPages).containsExactly(3, 4)
        val lastWidget = children(4).single { it in widgets.created }

        controller.render(ready(pageCount = 3))
        assertThat(pager.pageCount).isEqualTo(3)
        assertThat(pager.currentPage).isEqualTo(2)
        assertThat(widgets.recycled).contains(lastWidget)
        assertThat(controller.boundPages).containsExactly(1, 2)
        assertBound(2)
        assertReleased(0)
    }

    @Test
    fun dropsOnAReleasedPageStillResolveCells() {
        controller.render(ready())
        pager.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1500, View.MeasureSpec.EXACTLY))
        pager.layout(0, 0, 1000, 1500)
        val released = pager.pageAt(3)!!
        assertThat(released.contentBound).isFalse()
        assertThat(released.width).isEqualTo(1000)
        // Bottom-right corner of the 4x5 grid, as a drop would resolve it.
        assertThat(released.cellAt(released.width - released.paddingRight - 1f, released.height - released.paddingBottom - 1f))
            .isEqualTo(Cell(3, 4))
        assertThat(released.page).isEqualTo(3)
        assertThat(released.grid).isEqualTo(GridSpec(4, 5))
    }

    @Test
    fun theDragSourcePageStaysBoundUntilTheDragEnds() {
        controller.render(ready())
        val source = children(0).filterIsInstance<AppIconView>().single()
        assertThat(controller.startDrag(source, DragPayload.Existing("a0", CellPlacement(ContainerType.DESKTOP, 0, 0, 0)))).isTrue()

        pager.snapToPage(3, animate = false)
        assertThat(controller.boundPages).containsExactly(0, 2, 3, 4)
        assertThat(source.parent).isSameInstanceAs(pager.pageAt(0))

        controller.cancelDrag()
        assertThat(controller.boundPages).containsExactly(2, 3, 4)
    }

    @Test
    fun clearDropsEveryPage() {
        controller.render(ready())
        controller.clear()
        assertThat(pager.pageCount).isEqualTo(0)
        assertThat(controller.boundPages).isEmpty()
    }
}
