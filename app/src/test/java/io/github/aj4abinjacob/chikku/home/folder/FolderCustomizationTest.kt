package io.github.aj4abinjacob.chikku.home.folder

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.CellPlacement
import io.github.aj4abinjacob.chikku.core.model.ContainerType
import io.github.aj4abinjacob.chikku.core.model.settings.DockLabelFont
import io.github.aj4abinjacob.chikku.core.model.settings.FolderIconBackground
import io.github.aj4abinjacob.chikku.core.model.settings.FolderPreviewStyle
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Real folder views with fake app icons; no device or launch behavior is implied. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FolderCustomizationTest {
    private val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext<Context>(), R.style.Theme_Chikku_Home)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val root = FrameLayout(context).apply { layout(0, 0, 480, 900) }
    private val listener = RecordingListener()
    private val popup = FolderPopup(root, IconBinder(context, scope, FakeIconProvider()), listener)
    private fun entry(id: String, label: String, profile: Long = 0) = FolderEntryUi(
        id, ItemContent.App(AppKey("pkg.$id", "pkg.$id.Main", profile), label, profile != 0L, Availability.AVAILABLE),
    )
    private val entries = listOf(entry("z", "Zulu"), entry("a", "Alpha"), entry("work", "alpha", 10))
    private val folder = HomeItemUi("folder", CellPlacement(ContainerType.DESKTOP, 0, 0, 0), ItemContent.Folder("Tools", entries))

    @After fun tearDown() { popup.close(); scope.cancel() }

    private inline fun <reified T : View> View.findAll(): List<T> = descendants(this).filterIsInstance<T>()
    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup) {
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) }
    } else emptyList()
    private fun grid() = root.findAll<FolderGridLayout>().single()

    @Test fun alphabeticalViewKeepsOriginalOrderAndProfileIdentity() {
        val sorted = displayFolderEntries(entries, FolderSortOrder.ALPHABETICAL)
        assertThat(sorted.map { it.id }).containsExactly("a", "work", "z").inOrder()
        assertThat(entries.map { it.id }).containsExactly("z", "a", "work").inOrder()
        assertThat((sorted[1].content as ItemContent.App).key.userSerial).isEqualTo(10)
        assertThat(displayFolderEntries(entries, FolderSortOrder.MANUAL)).isSameInstanceAs(entries)
    }

    @Test fun styleUpdateRebuildsCellsWithIndependentLabelsAndAccessibleNames() {
        popup.open(folder, FolderSettings())
        popup.update(folder, FolderSettings(
            columns = 5, iconScale = 1.3f, labelScale = 1.4f, showLabels = false,
            labelFont = DockLabelFont.CONDENSED, labelShadow = true, labelSingleLine = false,
            labelColor = android.graphics.Color.RED, sortOrder = FolderSortOrder.ALPHABETICAL,
        ))
        val cells = grid().findAll<AppIconView>()
        assertThat(grid().columns).isEqualTo(5)
        assertThat(cells.map { it.label }).containsExactly("Alpha", "alpha", "Zulu").inOrder()
        assertThat(cells.first().iconScale).isEqualTo(1.3f)
        assertThat(cells.first().labelScale).isEqualTo(1.4f)
        assertThat(cells.first().showLabel).isFalse()
        assertThat(cells.first().labelSingleLine).isFalse()
        assertThat(cells.first().labelFont).isEqualTo(DockLabelFont.CONDENSED)
        assertThat(cells.first().labelShadow).isTrue()
        assertThat(cells.first().contentDescription).isEqualTo("Alpha")
        cells.first().performClick()
        assertThat(listener.clicked).isEqualTo("a")
        val info = android.view.accessibility.AccessibilityNodeInfo.obtain()
        cells.first().onInitializeAccessibilityNodeInfo(info)
        assertThat(info.actionList.map { it.label?.toString() }).doesNotContain(context.getString(R.string.folder_action_move_later))
        val remove = info.actionList.first { it.label == context.getString(R.string.folder_action_remove_entry) }
        cells.first().performAccessibilityAction(remove.id, null)
        assertThat(listener.removed).isEqualTo("a")
    }

    @Test fun immersiveWindowHasCloseActionAndHiddenTitleRetainsAccessiblePaneName() {
        popup.open(folder, FolderSettings(windowStyle = FolderWindowStyle.IMMERSIVE, showName = false, windowShadow = false))
        val panel = root.getChildAt(root.childCount - 1)
        assertThat(panel.layoutParams.width).isEqualTo(ViewGroup.LayoutParams.MATCH_PARENT)
        assertThat(panel.layoutParams.height).isEqualTo(ViewGroup.LayoutParams.MATCH_PARENT)
        assertThat(panel.elevation).isEqualTo(0f)
        assertThat(root.findAll<EditText>().single().visibility).isEqualTo(View.GONE)
        assertThat(androidx.core.view.ViewCompat.getAccessibilityPaneTitle(panel)).isEqualTo("Tools")
        root.findAll<Button>().first { it.text == context.getString(R.string.folder_close_button) }.performClick()
        assertThat(popup.isOpen).isFalse()
        // The close button fades the folder out; nothing is left once that finishes.
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1))
        assertThat(root.childCount).isEqualTo(0)
    }

    @Test fun styleChangesCommitAnInProgressTitleEditBeforeRebuilding() {
        popup.open(folder, FolderSettings())
        root.findAll<EditText>().single().setText("  Travel tools  ")
        popup.update(folder, FolderSettings(windowStyle = FolderWindowStyle.IMMERSIVE))
        assertThat(listener.renamed).isEqualTo("Travel tools")
    }

    @Test fun openPanelFollowsAThemePanelColorChange() {
        popup.open(folder, FolderSettings(backgroundAlpha = 1f))
        fun panelColor() = root.findAll<android.widget.LinearLayout>()
            .mapNotNull { it.background as? android.graphics.drawable.GradientDrawable }
            .first().color!!.defaultColor
        popup.themePanelColor = 0xFF102030.toInt()
        assertThat(panelColor()).isEqualTo(0xFF102030.toInt())
        popup.themePanelColor = 0xFFE0D0C0.toInt()
        assertThat(popup.isOpen).isTrue()
        assertThat(panelColor()).isEqualTo(0xFFE0D0C0.toInt())
        assertThat(root.findAll<FolderGridLayout>()).hasSize(1)
    }

    @Test fun narrowGridReducesColumnsAndRtlDropMatchesRenderedCell() {
        val grid = FolderGridLayout(context, 100, 100).apply {
            columns = 6
            repeat(8) { addView(View(context)) }
        }
        val touchWidth = context.resources.getDimensionPixelSize(R.dimen.home_min_touch_target)
        val width = touchWidth * 2
        grid.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.AT_MOST))
        grid.layout(0, 0, width, grid.measuredHeight)
        assertThat(grid.getChildAt(0).width).isAtLeast(touchWidth)
        assertThat(grid.getChildAt(2).top).isEqualTo(100)
        grid.layoutDirection = View.LAYOUT_DIRECTION_RTL
        grid.layout(0, 0, width, grid.measuredHeight)
        assertThat(grid.indexAt((width - 1).toFloat(), 1f)).isEqualTo(0)
        assertThat(grid.indexAt(1f, 101f)).isEqualTo(3)
    }

    @Test fun ninePreviewGridDrawsEveryDistinctIconWithinBackground() {
        val previews = List(9) { RecordingDrawable() }
        val canvas = Canvas(android.graphics.Bitmap.createBitmap(96, 96, android.graphics.Bitmap.Config.ARGB_8888))
        FolderIconDrawable(previews, FolderPreviewStyle.GRID, FolderIconBackground.CIRCLE, android.graphics.Color.WHITE).apply {
            setBounds(0, 0, 96, 96)
            draw(canvas)
        }
        assertThat(previews.map { it.draws }).containsExactlyElementsIn(List(9) { 1 })
        assertThat(previews.map { Rect(it.bounds) }.distinct()).hasSize(9)
        previews.forEach { assertThat(it.bounds.left).isAtLeast(0); assertThat(it.bounds.right).isAtMost(96) }
    }

    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    @Test fun changingWindowColorLeavesPreviewBackdropAndItsCachedIconAlone() {
        val binder = FolderIconBinder(context, scope, FakeIconProvider())
        val view = AppIconView(context, AppIconView.Style.WORKSPACE)
        val empty = ItemContent.Folder("Tools", emptyList())
        val appearance = FolderSettings(iconBackgroundColor = android.graphics.Color.GREEN,
            backgroundColor = android.graphics.Color.RED)
        binder.bind(view, empty, appearance)
        val first = view.icon!!
        val bitmap = android.graphics.Bitmap.createBitmap(64, 64, android.graphics.Bitmap.Config.ARGB_8888)
        first.setBounds(0, 0, 64, 64)
        first.draw(Canvas(bitmap))
        assertThat(bitmap.getPixel(32, 4)).isEqualTo(android.graphics.Color.GREEN)
        val changed = appearance.copy(backgroundColor = android.graphics.Color.BLUE)
        binder.bind(view, empty, changed)
        assertThat(view.icon).isSameInstanceAs(first)
        popup.open(folder, changed)
        val panel = root.getChildAt(root.childCount - 1)
        assertThat((panel.background as android.graphics.drawable.GradientDrawable).color?.defaultColor)
            .isEqualTo(android.graphics.Color.BLUE)
    }

    private class RecordingDrawable : Drawable() {
        var draws = 0
        override fun draw(canvas: Canvas) { draws++ }
        override fun setAlpha(alpha: Int) = Unit
        override fun setColorFilter(colorFilter: ColorFilter?) = Unit
        @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
    }
    private class RecordingListener : FolderPopup.Listener {
        var clicked: String? = null
        var removed: String? = null
        var renamed: String? = null
        override fun onEntryClick(folder: HomeItemUi, entry: FolderEntryUi, view: View) { clicked = entry.id }
        override fun onEntryLongClick(folder: HomeItemUi, entry: FolderEntryUi, view: View) = false
        override fun onReorder(folderId: String, orderedEntryIds: List<String>) = Unit
        override fun onRename(folderId: String, title: String) { renamed = title }
        override fun onMoveToHome(folderId: String, entryId: String) = Unit
        override fun onRemoveEntry(folderId: String, entryId: String) { removed = entryId }
        override fun onEdit(folder: HomeItemUi) = Unit
        override fun onOpenChanged(open: Boolean) = Unit
    }
}

/** HOME integration ensures closing behavior and desktop-size matching affect real folder cells. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = io.github.aj4abinjacob.chikku.home.TestHomeApplication::class)
class FolderCustomizationHomeTest {
    @Test fun keepFolderOpenOnLaunchAndFollowDesktopSizeWhileOpen() {
        val application = ApplicationProvider.getApplicationContext<io.github.aj4abinjacob.chikku.home.TestHomeApplication>()
        application.getSharedPreferences("chikku_startup_guard", 0).edit().clear().commit()
        val app = io.github.aj4abinjacob.chikku.home.app("Tools")
        val workspace = io.github.aj4abinjacob.chikku.core.model.Workspace(
            desktopGrid = io.github.aj4abinjacob.chikku.core.model.GridSpec(4, 5),
            dockGrid = io.github.aj4abinjacob.chikku.core.model.GridSpec(5, 1),
            pageCount = 1,
            items = listOf(io.github.aj4abinjacob.chikku.core.model.WorkspaceItem(
                "folder", CellPlacement(ContainerType.DESKTOP, 0, 0, 0),
                io.github.aj4abinjacob.chikku.core.model.ItemTarget.Folder("Tools", listOf(
                    io.github.aj4abinjacob.chikku.core.model.FolderEntry("entry", io.github.aj4abinjacob.chikku.core.model.ItemTarget.App(app.key)),
                )),
            )),
        )
        val deps = io.github.aj4abinjacob.chikku.home.FakeHomeDependencies(
            appsRepository = io.github.aj4abinjacob.chikku.home.FakeAppsRepository(io.github.aj4abinjacob.chikku.core.model.AppsState.Loaded(listOf(app))),
            workspaceRepository = io.github.aj4abinjacob.chikku.home.FakeWorkspaceRepository(io.github.aj4abinjacob.chikku.core.model.WorkspaceState.Ready(workspace)),
        )
        deps.settingsRepository.state.value = deps.settingsRepository.state.value.let {
            it.copy(home = it.home.copy(iconScale = 1.2f), folders = it.folders.copy(matchDesktopSize = true, closeOnLaunch = false))
        }
        application.homeDependencies = deps
        androidx.test.core.app.ActivityScenario.launch(io.github.aj4abinjacob.chikku.home.HomeActivity::class.java).use { scenario ->
            lateinit var activity: io.github.aj4abinjacob.chikku.home.HomeActivity
            scenario.onActivity { activity = it }
            val pager = activity.findViewById<io.github.aj4abinjacob.chikku.home.WorkspacePager>(R.id.workspace_pager)
            waitUntil { pager.pageCount == 1 && pager.pageAt(0)!!.childCount == 1 }
            pager.pageAt(0)!!.getChildAt(0).performClick()
            val root = activity.findViewById<FrameLayout>(R.id.home_root)
            waitUntil { findGrid(root) != null }
            val grid = findGrid(root)!!
            assertThat((grid.getChildAt(0) as AppIconView).iconScale).isEqualTo(1.2f)
            grid.getChildAt(0).performClick()
            assertThat(deps.appLauncher.launched).containsExactly(app.key)
            assertThat(findGrid(root)).isNotNull()
            deps.settingsRepository.state.value = deps.settingsRepository.state.value.let { it.copy(home = it.home.copy(iconScale = 1.4f)) }
            waitUntil { (findGrid(root)!!.getChildAt(0) as AppIconView).iconScale == 1.4f }
            activity.onBackPressedDispatcher.onBackPressed()
            waitUntil { findGrid(root) == null }
        }
    }

    private fun findGrid(view: View): FolderGridLayout? {
        if (view is FolderGridLayout) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findGrid(view.getChildAt(i))?.let { return it }
        return null
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "Folder state did not arrive" }
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
    }
}
