package io.github.aj4abinjacob.chikku.home

import android.app.Activity
import android.content.Context
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.message.RecordingMessages
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.CellPlacement
import io.github.aj4abinjacob.chikku.core.model.ContainerType
import io.github.aj4abinjacob.chikku.core.model.GridSpec
import io.github.aj4abinjacob.chikku.core.model.settings.DockBackground
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.core.model.settings.PageIndicatorStyle
import io.github.aj4abinjacob.chikku.core.model.settings.PaddingSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestHomeApplication::class)
class WorkspaceControllerSettingsTest {

    private val context: Context =
        ContextThemeWrapper(ApplicationProvider.getApplicationContext<Context>(), R.style.Theme_Chikku_Home)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val pager = WorkspacePager(context)
    private val dock = CellLayout(context)
    private val indicator = PageIndicatorView(context)
    private val removeTarget = View(context).apply { visibility = View.GONE } // as in activity_home.xml
    private val drops = mutableListOf<CellPlacement>()
    private val removals = mutableListOf<String>()

    private val actions = object : WorkspaceActions {
        override fun onItemClick(item: HomeItemUi, view: View) = Unit
        override fun onDrop(payload: DragPayload, target: CellPlacement) {
            drops += target
        }
        override fun onRemoveItem(itemId: String) {
            removals += itemId
        }
        override fun onAppInfo(key: AppKey, view: View) = Unit
    }

    private val messages = RecordingMessages()

    private val controller = WorkspaceController(
        pager, dock, indicator, removeTarget, IconBinder(context, scope, FakeIconProvider()), actions, messages,
    )

    init {
        FrameLayout(context).apply {
            addView(pager)
            addView(dock)
            addView(indicator)
        }
    }

    @After
    fun tearDown() = scope.cancel()

    private fun item(id: String, container: ContainerType, x: Int, y: Int = 0, page: Int = 0) = HomeItemUi(
        id,
        CellPlacement(container, page, x, y),
        ItemContent.App(AppKey("pkg.$id", "pkg.$id.Main", 0L), id, isWork = false, availability = Availability.AVAILABLE),
    )

    private fun ready(
        desktop: GridSpec = GridSpec(5, 5),
        dockGrid: GridSpec = GridSpec(5, 1),
        pages: List<List<HomeItemUi>> = listOf(emptyList()),
        dockItems: List<HomeItemUi> = emptyList(),
    ) = WorkspaceUi.Ready(desktop, dockGrid, pages, dockItems)

    private fun settings(transform: LauncherSettings.() -> LauncherSettings) = LauncherSettings().transform()

    private fun childFor(layout: CellLayout, id: String): View =
        (0 until layout.childCount).map(layout::getChildAt).single { it.getTag(R.id.home_item_id) == id }

    @Test
    fun lockedLayoutRefusesDragWithMessage() {
        controller.applySettings(settings { copy(home = home.copy(layoutLocked = true)) })
        controller.render(ready(pages = listOf(listOf(item("a", ContainerType.DESKTOP, 0)))))
        val view = childFor(pager.pageAt(0)!!, "a")

        val consumed = view.performLongClick()

        assertThat(consumed).isTrue()
        assertThat(removeTarget.visibility).isEqualTo(View.GONE) // no edit affordance appeared
        ShadowLooper.idleMainLooper()
        assertThat(messages.shown).containsExactly(R.string.home_layout_locked)
        assertThat(controller.startDrag(view, DragPayload.NewApp(AppKey("p", "p.A", 0L)))).isFalse()
    }

    @Test
    fun unlockedLayoutDoesNotRefuse() {
        controller.applySettings(LauncherSettings())
        assertThat(controller.refuseIfLocked()).isFalse()
        assertThat(messages.shown).isEmpty()
    }

    @Test
    fun indicatorStyleFollowsSettings() {
        controller.applySettings(settings { copy(home = home.copy(pageIndicatorStyle = PageIndicatorStyle.LINE)) })
        assertThat(indicator.style).isEqualTo(PageIndicatorStyle.LINE)
        controller.render(ready(pages = listOf(emptyList(), emptyList())))
        assertThat(indicator.isShowingPages).isTrue()

        controller.applySettings(settings { copy(home = home.copy(pageIndicatorStyle = PageIndicatorStyle.NONE)) })
        assertThat(indicator.isShowingPages).isFalse()
        assertThat(indicator.contentDescription).isNull()
    }

    @Test
    fun autoHideIndicatorAppearsOnlyWhilePagesScroll() {
        // Delayed fades only run on an attached view, as on the home screen.
        Robolectric.buildActivity(Activity::class.java).setup().get().setContentView(pager.parent as View)
        controller.applySettings(settings { copy(home = home.copy(pageIndicatorAutoHide = true)) })
        controller.render(ready(pages = listOf(emptyList(), emptyList())))
        pager.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
        )
        pager.layout(0, 0, 1080, 1920)
        assertThat(indicator.isShowingPages).isTrue()
        assertThat(indicator.alpha).isEqualTo(0f) // layout and instant jumps do not reveal it

        pager.snapToPage(1)
        ShadowLooper.idleMainLooper(50, TimeUnit.MILLISECONDS)
        pager.computeScroll()
        ShadowLooper.idleMainLooper(200, TimeUnit.MILLISECONDS)
        assertThat(indicator.alpha).isEqualTo(1f)

        ShadowLooper.idleMainLooper(2, TimeUnit.SECONDS)
        assertThat(indicator.alpha).isEqualTo(0f)

        controller.applySettings(settings { copy(home = home.copy(pageIndicatorAutoHide = false)) })
        assertThat(indicator.alpha).isEqualTo(1f)
    }

    @Test
    fun pagerFollowsScrollSettings() {
        controller.applySettings(settings { copy(home = home.copy(infiniteScroll = true, wallpaperScrolling = false)) })
        assertThat(pager.infiniteScroll).isTrue()
        assertThat(pager.wallpaperScrolling).isFalse()
    }

    @Test
    fun itemsOutsideShrunkGridAreHiddenButKept() {
        val inside = item("in", ContainerType.DESKTOP, 1, 1)
        val outside = item("out", ContainerType.DESKTOP, 4, 4)
        controller.render(ready(desktop = GridSpec(3, 3), pages = listOf(listOf(inside, outside))))
        val page = pager.pageAt(0)!!

        assertThat(page.childCount).isEqualTo(2)
        assertThat(childFor(page, "in").visibility).isEqualTo(View.VISIBLE)
        assertThat(childFor(page, "out").visibility).isEqualTo(View.GONE)
        assertThat(page.hiddenChildCount).isEqualTo(1)

        // Growing the grid again shows it without any edit.
        controller.render(ready(desktop = GridSpec(5, 5), pages = listOf(listOf(inside, outside))))
        assertThat(childFor(page, "out").visibility).isEqualTo(View.VISIBLE)
    }

    @Test
    fun iconScaleAndLabelsApplyToDesktopAndDockSeparately() {
        controller.applySettings(
            settings {
                copy(
                    home = home.copy(iconScale = 1.4f, showLabels = false, labelScale = 1.2f),
                    dock = dock.copy(iconScale = 0.8f, showLabels = true),
                )
            },
        )
        controller.render(
            ready(
                pages = listOf(listOf(item("d", ContainerType.DESKTOP, 0))),
                dockItems = listOf(item("k", ContainerType.DOCK, 0)),
            ),
        )
        val desktopIcon = childFor(pager.pageAt(0)!!, "d") as AppIconView
        val dockIcon = childFor(dock, "k") as AppIconView
        assertThat(desktopIcon.iconScale).isEqualTo(1.4f)
        assertThat(desktopIcon.showLabel).isFalse()
        assertThat(desktopIcon.labelScale).isEqualTo(1.2f)
        assertThat(dockIcon.iconScale).isEqualTo(0.8f)
        assertThat(dockIcon.showLabel).isTrue()
    }

    @Test
    fun paddingAppliesToPages() {
        val density = context.resources.displayMetrics.density
        controller.render(ready())
        controller.applySettings(
            settings { copy(home = home.copy(horizontalPadding = PaddingSize.LARGE, verticalPadding = PaddingSize.NONE)) },
        )
        val page = pager.pageAt(0)!!
        assertThat(page.paddingStart).isEqualTo(HomeLayoutMetrics.paddingPx(PaddingSize.LARGE, density))
        assertThat(page.paddingTop).isEqualTo(0)
    }

    @Test
    fun dockPagesShowOneWindowAndSwipe() {
        controller.applySettings(settings { copy(dock = dock.copy(iconCount = 4, pages = 2)) })
        val first = item("first", ContainerType.DOCK, 1)
        val second = item("second", ContainerType.DOCK, 5)
        controller.render(ready(dockGrid = GridSpec(8, 1), dockItems = listOf(first, second)))

        assertThat(dock.grid).isEqualTo(GridSpec(4, 1))
        assertThat(controller.dockPosition).isEqualTo(0 to 2)
        assertThat(childFor(dock, "first").visibility).isEqualTo(View.VISIBLE)
        assertThat(childFor(dock, "second").visibility).isEqualTo(View.GONE)

        dock.onHorizontalSwipe!!.invoke(true)

        assertThat(controller.dockPosition).isEqualTo(1 to 2)
        assertThat(dock.columnOffset).isEqualTo(4)
        assertThat(childFor(dock, "first").visibility).isEqualTo(View.GONE)
        assertThat(childFor(dock, "second").visibility).isEqualTo(View.VISIBLE)
    }

    @Test
    fun singleDockPageHasNoSwipe() {
        controller.render(ready())
        assertThat(dock.onHorizontalSwipe).isNull()
        assertThat(controller.dockPosition).isEqualTo(0 to 1)
    }

    @Test
    fun infiniteDockWrapsBothWaysAndHomeResetsUnlessRemembered() {
        controller.applySettings(settings { copy(dock = dock.copy(iconCount = 4, pages = 3, infiniteScroll = true)) })
        controller.render(ready(dockGrid = GridSpec(12, 1)))
        dock.onHorizontalSwipe!!.invoke(false)
        assertThat(controller.dockPosition).isEqualTo(2 to 3)
        dock.onHorizontalSwipe!!.invoke(true)
        assertThat(controller.dockPosition).isEqualTo(0 to 3)
        dock.onHorizontalSwipe!!.invoke(true)
        controller.resetDockPosition()
        assertThat(controller.dockPosition).isEqualTo(0 to 3)
        controller.clear()
        controller.applySettings(settings { copy(dock = dock.copy(iconCount = 4, pages = 3, rememberPosition = true, lastPage = 2)) })
        controller.render(ready(dockGrid = GridSpec(12, 1)))
        controller.resetDockPosition()
        assertThat(controller.dockPosition).isEqualTo(2 to 3)
        // An asynchronous settings emission must not overwrite a swipe just made locally.
        dock.onHorizontalSwipe!!.invoke(false)
        controller.applySettings(settings { copy(dock = dock.copy(iconCount = 4, pages = 3, rememberPosition = true, lastPage = 0)) })
        assertThat(controller.dockPosition).isEqualTo(1 to 3)
    }

    @Test
    fun independentDockPaddingAndLabelSizeIgnoreDesktopChanges() {
        val customized = settings { copy(
            home = home.copy(horizontalPadding = PaddingSize.LARGE, iconScale = 1.4f),
            dock = dock.copy(horizontalPadding = PaddingSize.SMALL, verticalPadding = PaddingSize.MEDIUM,
                matchDesktopSize = true, labelScale = 0.8f, labelSingleLine = false),
        ) }
        controller.applySettings(customized)
        controller.render(ready(dockItems = listOf(item("k", ContainerType.DOCK, 0))))
        val icon = childFor(dock, "k") as AppIconView
        assertThat(icon.iconScale).isEqualTo(1.4f)
        assertThat(icon.labelScale).isEqualTo(0.8f)
        assertThat(icon.labelSingleLine).isFalse()
        assertThat(dock.paddingStart).isEqualTo(HomeLayoutMetrics.paddingPx(PaddingSize.SMALL, context.resources.displayMetrics.density))
        assertThat(dock.paddingTop).isEqualTo(HomeLayoutMetrics.paddingPx(PaddingSize.MEDIUM, context.resources.displayMetrics.density))
    }

    @Test
    fun firstRenderOpensDefaultPageAndHomeReturnsThere() {
        controller.applySettings(settings { copy(home = home.copy(defaultPage = 1)) })
        controller.render(ready(pages = listOf(emptyList(), emptyList(), emptyList())))
        assertThat(pager.currentPage).isEqualTo(1)

        pager.snapToPage(2, animate = false)
        controller.goToDefaultPage()
        assertThat(pager.currentPage).isEqualTo(1)
    }

    @Test
    fun dockLastPageOnlyChangeSkipsTheFullSizingPass() {
        val solid = settings { copy(dock = dock.copy(background = DockBackground.RECTANGLE, rememberPosition = true)) }
        controller.applySettings(solid)
        dock.setPadding(7, 7, 7, 7) // a marker the sizing pass would overwrite

        controller.applySettings(solid.copy(dock = solid.dock.copy(lastPage = 2)))
        assertThat(dock.paddingTop).isEqualTo(7) // sizing pass skipped

        controller.applySettings(solid.copy(dock = solid.dock.copy(lastPage = 2, backgroundAlpha = 0.9f)))
        assertThat(dock.paddingTop).isNotEqualTo(7) // a real dock change still applies
    }

    @Test
    fun homeOnlySettingChangeKeepsTheBackgroundDockSurfaceInstalled() {
        val host = FrameLayout(context)
        val surface = DockSurfaceController(dock, FrameLayout(context), FrameLayout(context), root = host)
        val base = settings { copy(dock = dock.copy(background = DockBackground.RECTANGLE)) }
        fun apply(next: LauncherSettings) {
            controller.applySettings(next)
            surface.render(next.dock, true)
        }
        apply(base)
        val shape = dock.background
        assertThat(shape).isNotNull()

        apply(base.copy(home = base.home.copy(infiniteScroll = !base.home.infiniteScroll)))
        assertThat(dock.background).isSameInstanceAs(shape)

        // Drawn behind the navigation bar, the backdrop view owns the drawable and the dock has none.
        val behind = base.copy(dock = base.dock.copy(drawBehindNavigationBar = true))
        apply(behind)
        assertThat(dock.background).isNull()
        apply(behind.copy(home = behind.home.copy(infiniteScroll = !behind.home.infiniteScroll)))
        assertThat(dock.background).isNull()
    }
}
