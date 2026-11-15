package io.github.aj4abinjacob.chikku.home.folder

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.CellPlacement
import io.github.aj4abinjacob.chikku.core.model.ContainerType
import io.github.aj4abinjacob.chikku.core.model.settings.AnimationSpeed
import io.github.aj4abinjacob.chikku.core.model.settings.FolderScrollStyle
import io.github.aj4abinjacob.chikku.core.model.settings.FolderSettings
import io.github.aj4abinjacob.chikku.core.model.settings.FolderSortOrder
import io.github.aj4abinjacob.chikku.core.model.settings.FolderWindowStyle
import io.github.aj4abinjacob.chikku.home.AppIconView
import io.github.aj4abinjacob.chikku.home.Availability
import io.github.aj4abinjacob.chikku.home.FakeIconProvider
import io.github.aj4abinjacob.chikku.home.FolderEntryUi
import io.github.aj4abinjacob.chikku.home.HomeItemUi
import io.github.aj4abinjacob.chikku.home.IconBinder
import io.github.aj4abinjacob.chikku.home.ItemContent
import io.github.aj4abinjacob.chikku.home.appmenu.AppMenuHandle
import io.github.aj4abinjacob.chikku.theme.Motion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FolderPagingTest {
    private val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext<Context>(), R.style.Theme_Chikku_Home)
    private val root = FrameLayout(context).apply { layout(0, 0, 1000, 1600) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val listener = RecordingListener()
    private val popup = FolderPopup(root, IconBinder(context, scope, FakeIconProvider()), listener).apply {
        motion = Motion(AnimationSpeed.NONE)
    }
    private val appearance = FolderSettings(scrollStyle = FolderScrollStyle.HORIZONTAL_PAGED, columns = 2, rows = 1)
    private val entries = (1..7).map { number -> FolderEntryUi("entry-$number", ItemContent.App(
        AppKey("sample", "sample.Activity$number", 0), "Sample $number", false, Availability.AVAILABLE,
    )) }
    private fun item(content: List<FolderEntryUi> = entries) = HomeItemUi(
        "folder", CellPlacement(ContainerType.DESKTOP, 0, 0, 0), ItemContent.Folder("Samples", content),
    )
    @After fun cleanup() { popup.close(); scope.cancel() }
    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup) {
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) }
    } else emptyList()
    private fun grid() = descendants(root).filterIsInstance<FolderGridLayout>().single()
    private fun labels() = descendants(grid()).filterIsInstance<AppIconView>().map { it.label }
    private fun button(id: Int) = descendants(root).filterIsInstance<Button>().single { it.text == context.getString(id) }
    private fun indicator() = descendants(root).filterIsInstance<TextView>().single { it.text.startsWith("Page ") }.text.toString()
    private fun layout() {
        root.measure(View.MeasureSpec.makeMeasureSpec(root.width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(root.height, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
    }

    @Test fun animatedCloseIsClosedAtOnceIgnoresTapsAndThenLeavesNothingBehind() {
        val before = root.childCount
        popup.motion = Motion(AnimationSpeed.NORMAL)
        popup.open(item(), appearance)
        layout()
        val icon = descendants(root).filterIsInstance<AppIconView>().first()
        popup.close(animate = true)
        assertThat(popup.isOpen).isFalse()
        assertThat(listener.openChanges.last()).isFalse()
        // Still fading out, but a tap on it must not open anything.
        assertThat(root.childCount).isGreaterThan(before)
        icon.dispatchTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 5f, 5f, 0))
        icon.dispatchTouchEvent(MotionEvent.obtain(0, 50, MotionEvent.ACTION_UP, 5f, 5f, 0))
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1))
        assertThat(listener.clicked).isEmpty()
        assertThat(root.childCount).isEqualTo(before)
    }

    @Test fun backSwipeShrinksTheOpenFolderAndCancellingRestoresIt() {
        popup.open(item(), appearance)
        layout()
        val panel = root.getChildAt(root.childCount - 1)
        popup.setBackProgress(1f, fromLeftEdge = true)
        assertThat(panel.scaleX).isLessThan(1f)
        assertThat(panel.translationX).isGreaterThan(0f)
        popup.cancelBackProgress()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1))
        assertThat(panel.scaleX).isEqualTo(1f)
        assertThat(panel.translationX).isEqualTo(0f)
    }

    @Test fun windowsCoverEveryEntryOnceAndClampEmptyAndInvalidRequests() {
        val pages = (0..3).map { folderPageWindow(7, 2, 1, it) }
        assertThat(pages.map { it.start until it.end }.flatMap { it.toList() }).containsExactly(0, 1, 2, 3, 4, 5, 6).inOrder()
        assertThat(folderPageWindow(7, 2, 1, 99)).isEqualTo(FolderPageWindow(3, 4, 6, 7))
        assertThat(folderPageWindow(7, 2, 1, -1).page).isEqualTo(0)
        assertThat(folderPageWindow(0, 0, 0, 99)).isEqualTo(FolderPageWindow(0, 1, 0, 0))
    }

    @Test fun buttonsNavigateBoundedPagesAndRetainOrClampPageAfterUpdates() {
        popup.open(item(), appearance)
        assertThat(labels()).containsExactly("Sample 1", "Sample 2").inOrder()
        assertThat(button(R.string.folder_page_previous).isEnabled).isFalse()
        button(R.string.folder_page_next).performClick()
        assertThat(labels()).containsExactly("Sample 3", "Sample 4").inOrder()
        assertThat(indicator()).isEqualTo(context.getString(R.string.folder_page_indicator, 2, 4))
        popup.update(item(entries + entries.last().copy(id = "entry-8")), appearance)
        assertThat(labels()).containsExactly("Sample 3", "Sample 4").inOrder()
        popup.update(item(), appearance.copy(labelScale = 1.2f))
        assertThat(indicator()).isEqualTo(context.getString(R.string.folder_page_indicator, 2, 4))
        repeat(10) { button(R.string.folder_page_next).performClick() }
        assertThat(labels()).containsExactly("Sample 7")
        assertThat(button(R.string.folder_page_next).isEnabled).isFalse()
        popup.update(item(entries.take(3)), appearance.copy(labelScale = 1.2f))
        assertThat(indicator()).isEqualTo(context.getString(R.string.folder_page_indicator, 2, 2))
        assertThat(labels()).containsExactly("Sample 3")
        popup.close()
        popup.open(item(), appearance)
        assertThat(labels()).containsExactly("Sample 1", "Sample 2").inOrder()
    }

    @Test fun swipeChangesPagesInBothDirectionsAndRtlMirrorsPhysicalDirection() {
        popup.open(item(), appearance)
        layout()
        val viewport = descendants(root).filterIsInstance<FolderSwipeViewport>().single()
        swipe(viewport, forward = true)
        assertThat(labels()).containsExactly("Sample 3", "Sample 4").inOrder()
        swipe(viewport, forward = false)
        assertThat(labels()).containsExactly("Sample 1", "Sample 2").inOrder()
        viewport.layoutDirection = View.LAYOUT_DIRECTION_RTL
        swipe(viewport, forward = true)
        assertThat(labels()).containsExactly("Sample 3", "Sample 4").inOrder()
        repeat(8) { swipe(viewport, forward = true) }
        assertThat(labels()).containsExactly("Sample 7")
        assertThat(listener.clicked).isEmpty()
    }

    @Test fun longPressOpensEntryMenuAndMovingOnClosesItAndStartsTheDrag() {
        listener.hasMenu = true
        popup.open(item(), appearance)
        layout()
        val entry = grid().getChildAt(0)
        fun send(action: Int, x: Float, y: Float) {
            val event = MotionEvent.obtain(0, 10, action, x, y, 0)
            try { entry.dispatchTouchEvent(event) } finally { event.recycle() }
        }
        send(MotionEvent.ACTION_DOWN, 20f, 20f)
        assertThat(entry.performLongClick()).isTrue()
        assertThat(listener.menus).containsExactly("entry-1")
        assertThat(listener.longPressed).isEmpty()
        send(MotionEvent.ACTION_MOVE, 20f, 200f)
        assertThat(listener.menusDismissed).isEqualTo(1)
        assertThat(listener.longPressed).containsExactly("entry-1")
        send(MotionEvent.ACTION_CANCEL, 20f, 200f)
        assertThat(listener.clicked).isEmpty()
    }

    @Test fun tapAndVerticalMotionDoNotTurnPagesAndLongPressStillReachesEntry() {
        popup.open(item(), appearance)
        layout()
        grid().getChildAt(0).performClick()
        grid().getChildAt(0).performLongClick()
        assertThat(listener.clicked).containsExactly("entry-1")
        assertThat(listener.longPressed).containsExactly("entry-1")
        val viewport = descendants(root).filterIsInstance<FolderSwipeViewport>().single()
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 20f, 20f, 0)
        val vertical = MotionEvent.obtain(0, 50, MotionEvent.ACTION_MOVE, 22f, 180f, 0)
        try {
            assertThat(viewport.onInterceptTouchEvent(down)).isFalse()
            assertThat(viewport.onInterceptTouchEvent(vertical)).isFalse()
        } finally { down.recycle(); vertical.recycle() }
        assertThat(labels()).containsExactly("Sample 1", "Sample 2").inOrder()
    }

    @Test fun swipesStartingInEmptyPageSpaceAreRecognizedWithoutAChildTouchTarget() {
        val changes = mutableListOf<Int>()
        val viewport = FolderSwipeViewport(context, changes::add).apply { layout(0, 0, 400, 200) }
        swipe(viewport, forward = true)
        swipe(viewport, forward = false)
        viewport.layoutDirection = View.LAYOUT_DIRECTION_RTL
        swipe(viewport, forward = true)
        assertThat(changes).containsExactly(1, -1, 1).inOrder()
    }

    @Test fun earlierActionCrossesPageBoundaryUsingGlobalManualOrder() {
        popup.open(item(), appearance)
        button(R.string.folder_page_next).performClick()
        val icon = grid().getChildAt(0)
        val info = AccessibilityNodeInfo.obtain()
        icon.onInitializeAccessibilityNodeInfo(info)
        val action = info.actionList.single { it.label == context.getString(R.string.folder_action_move_earlier) }
        assertThat(icon.performAccessibilityAction(action.id, null)).isTrue()
        assertThat(listener.reorders.single()).containsExactly("entry-1", "entry-3", "entry-2", "entry-4", "entry-5", "entry-6", "entry-7").inOrder()
    }

    @Test fun alphabeticalPagesNeverOfferOrInvokePersistentReordering() {
        val reversed = entries.reversed()
        popup.open(item(reversed), appearance.copy(sortOrder = FolderSortOrder.ALPHABETICAL))
        button(R.string.folder_page_next).performClick()
        assertThat(labels()).containsExactly("Sample 3", "Sample 4").inOrder()
        val info = AccessibilityNodeInfo.obtain()
        grid().getChildAt(0).onInitializeAccessibilityNodeInfo(info)
        assertThat(info.actionList.map { it.label?.toString() }).doesNotContain(context.getString(R.string.folder_action_move_earlier))
        assertThat(info.actionList.map { it.label?.toString() }).doesNotContain(context.getString(R.string.folder_action_move_later))
        assertThat(listener.reorders).isEmpty()
        assertThat(reversed.map { it.id }).containsExactly("entry-7", "entry-6", "entry-5", "entry-4", "entry-3", "entry-2", "entry-1").inOrder()
    }

    @Test fun pagedDropsUseCurrentPageOffsetAndIgnoreHeaderAndFooter() {
        popup.open(item(), appearance)
        button(R.string.folder_page_next).performClick()
        layout()
        val panel = root.getChildAt(root.childCount - 1)
        val p = IntArray(2).also(panel::getLocationInWindow)
        val g = IntArray(2).also(grid()::getLocationInWindow)
        val method = FolderPopup::class.java.getDeclaredMethod("dropIndex", View::class.java, Float::class.javaPrimitiveType, Float::class.javaPrimitiveType, Int::class.javaPrimitiveType).apply { isAccessible = true }
        fun drop(x: Float, y: Float) = method.invoke(popup, panel, x, y, entries.size)
        assertThat(drop((g[0] - p[0] + 1).toFloat(), (g[1] - p[1] + 1).toFloat())).isEqualTo(2)
        assertThat(drop(1f, -1f)).isNull()
        assertThat(drop(1f, panel.height.toFloat() + 1)).isNull()
    }

    @Test fun verticalDefaultStillRendersAllEntriesWithoutPageControlsOrPageAnimations() {
        popup.open(item(), FolderSettings())
        assertThat(labels()).hasSize(7)
        assertThat(descendants(root).filterIsInstance<FolderSwipeViewport>()).isEmpty()
        assertThat(root.getChildAt(root.childCount - 1).alpha).isEqualTo(1f)
        popup.update(item(), appearance)
        button(R.string.folder_page_next).performClick()
        assertThat(root.getChildAt(root.childCount - 1).alpha).isEqualTo(1f)
        assertThat(listener.reorders).isEmpty()
    }

    @Test fun narrowWindowFitsCellsAndStacksNavigationToPreserveTouchTargets() {
        val touch = context.resources.getDimensionPixelSize(R.dimen.home_min_touch_target)
        val padding = context.resources.getDimensionPixelSize(R.dimen.home_panel_padding)
        val margin = context.resources.getDimensionPixelSize(R.dimen.home_panel_margin)
        root.layout(0, 0, 2 * touch + 2 * padding + 2 * margin, 800)
        popup.open(item(), appearance.copy(columns = 6, rows = 6))
        layout()
        assertThat(grid().columns).isEqualTo(2)
        assertThat(grid().getChildAt(0).width).isAtLeast(touch)
        val previous = button(R.string.folder_page_previous)
        val next = button(R.string.folder_page_next)
        assertThat(previous.width).isAtLeast(touch)
        assertThat(next.width).isAtLeast(touch)
        assertThat(next.top).isGreaterThan(previous.bottom)
        assertThat(labels().size).isAtMost(6)
    }

    @Test fun immersivePanelAndPageCapacityRefreshInsideSystemBarsAndCutouts() {
        root.layout(0, 0, 400, 700)
        var rootInsetCalls = 0
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets -> rootInsetCalls++; insets }
        val many = (1..41).map { entries.first().copy(id = "safe-sample-$it") }
        val settings = appearance.copy(columns = 6, rows = 6, windowStyle = FolderWindowStyle.IMMERSIVE)
        popup.open(item(many), settings)
        layout()
        val initialCapacity = grid().childCount
        ViewCompat.dispatchApplyWindowInsets(root, WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, 80, 60, 100))
            .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.of(40, 0, 0, 0)).build())
        layout()
        fun assertSafeBounds() {
            val panel = root.getChildAt(root.childCount - 1)
            val margin = context.resources.getDimensionPixelSize(R.dimen.home_panel_margin)
            assertThat(panel.left).isAtLeast(40 + margin)
            assertThat(panel.top).isAtLeast(80 + margin)
            assertThat(panel.right).isAtMost(400 - 60 - margin)
            assertThat(panel.bottom).isAtMost(700 - 100 - margin)
        }
        assertSafeBounds()
        assertThat(grid().childCount).isLessThan(initialCapacity)
        assertThat(rootInsetCalls).isEqualTo(1)
        // A style rebuild retains the safe region even before the new panel receives its insets.
        popup.update(item(many), settings.copy(cornerRadiusDp = 28f))
        layout()
        assertSafeBounds()
        // Floating centering also has to reserve the asymmetric edges, rather than shifting past one.
        popup.update(item(many), settings.copy(windowStyle = FolderWindowStyle.FLOATING))
        layout()
        assertSafeBounds()
        val floating = root.getChildAt(root.childCount - 1)
        assertThat(floating.left + floating.right).isWithin(1).of(root.width)
    }

    private fun swipe(viewport: FolderSwipeViewport, forward: Boolean) {
        val rtl = viewport.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val increasingX = forward == rtl
        val start = viewport.width * if (increasingX) 0.15f else 0.85f
        val end = viewport.width * if (increasingX) 0.85f else 0.15f
        listOf(MotionEvent.ACTION_DOWN to start, MotionEvent.ACTION_MOVE to (start + end) / 2, MotionEvent.ACTION_UP to end).forEachIndexed { i, (action, x) ->
            val event = MotionEvent.obtain(0, (i * 100).toLong(), action, x, 20f, 0)
            try { viewport.dispatchTouchEvent(event) } finally { event.recycle() }
        }
    }

    private class RecordingListener : FolderPopup.Listener {
        val clicked = mutableListOf<String>()
        val longPressed = mutableListOf<String>()
        val reorders = mutableListOf<List<String>>()
        override fun onEntryClick(folder: HomeItemUi, entry: FolderEntryUi, view: View) { clicked += entry.id }
        override fun onEntryLongClick(folder: HomeItemUi, entry: FolderEntryUi, view: View): Boolean { longPressed += entry.id; return true }
        var hasMenu = false
        val menus = mutableListOf<String>()
        var menusDismissed = 0
        override fun onEntryMenu(folder: HomeItemUi, entry: FolderEntryUi, view: View): AppMenuHandle? =
            if (!hasMenu) null else AppMenuHandle { menusDismissed++ }.also { menus += entry.id }
        override fun onReorder(folderId: String, orderedEntryIds: List<String>) { reorders += orderedEntryIds }
        override fun onRename(folderId: String, title: String) = Unit
        override fun onMoveToHome(folderId: String, entryId: String) = Unit
        override fun onRemoveEntry(folderId: String, entryId: String) = Unit
        override fun onEdit(folder: HomeItemUi) = Unit
        val openChanges = mutableListOf<Boolean>()
        override fun onOpenChanged(open: Boolean) { openChanges += open }
    }
}
