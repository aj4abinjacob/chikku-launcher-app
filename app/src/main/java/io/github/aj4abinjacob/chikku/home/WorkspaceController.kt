package io.github.aj4abinjacob.chikku.home

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Point
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.DragEvent
import android.view.View
import androidx.core.view.ViewCompat
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.CellPlacement
import io.github.aj4abinjacob.chikku.core.model.ComponentKey
import io.github.aj4abinjacob.chikku.core.model.ContainerType
import io.github.aj4abinjacob.chikku.core.model.GridSpec
import io.github.aj4abinjacob.chikku.core.model.ItemTarget
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.home.appmenu.AppMenuHandle
import io.github.aj4abinjacob.chikku.home.appmenu.LongPressMenuDrag
import io.github.aj4abinjacob.chikku.home.folder.DropPlan
import io.github.aj4abinjacob.chikku.home.folder.FolderDropPlanner
import io.github.aj4abinjacob.chikku.home.folder.FolderReorderMotion
import io.github.aj4abinjacob.chikku.home.widget.WidgetViewBinder
import io.github.aj4abinjacob.chikku.message.ChikkuMessages
import io.github.aj4abinjacob.chikku.theme.Motion

/** Local state carried by a drag. Never leaves the process (no ClipData is attached). */
sealed interface DragPayload {
    data class Existing(val itemId: String, val from: CellPlacement) : DragPayload
    data class NewApp(val key: AppKey) : DragPayload

    /** A widget dragged from the widget picker; it is bound and placed on drop (owner: widgets). */
    data class NewWidget(val provider: ComponentKey, val spanX: Int, val spanY: Int) : DragPayload

    /** An entry dragged out of an open folder (owner: folders). */
    data class FromFolder(val folderId: String, val entryId: String, val target: ItemTarget) : DragPayload
}

/** What the workspace asks of its host; the host forwards to the ViewModel. */
interface WorkspaceActions {
    fun onItemClick(item: HomeItemUi, view: View)
    fun onDrop(payload: DragPayload, target: CellPlacement)
    fun onRemoveItem(itemId: String)
    fun onAppInfo(key: AppKey, view: View)

    /** A folder entry was dropped on the remove target (owner: folders). */
    fun onRemoveFolderEntry(folderId: String, entryId: String) = Unit

    /** Long-press menu for [item]; null means "no menu", and the long-press starts a drag at once. */
    fun onShowItemMenu(item: HomeItemUi, view: View): AppMenuHandle? = null
}

/** Folder rendering hooks (owner: folders); without them folders draw as placeholders. */
interface FolderViewBinder {
    fun bindFolder(view: AppIconView, item: HomeItemUi)
}

/**
 * Renders [WorkspaceUi.Ready] into the pager and dock and runs drag and drop. Views never own
 * positions: a drop only *requests* a move, and items move when the repository emits new state.
 *
 * Memory: every page keeps its [CellLayout] (grid, page index, drop target), but only the pages
 * in the bind window ([PagerMath.bindWindow]: the current page ±1, plus pages coming on screen
 * while the pager moves) hold real icon, folder and widget views. The other pages hold
 * [CellLayout.Placeholder]s with the same ids, model tags and cells, and are bound again as the
 * pager scrolls towards them. The dock is always bound.
 */
class WorkspaceController(
    private val pager: WorkspacePager,
    private val dock: CellLayout,
    private val indicator: PageIndicatorView,
    private val removeTarget: View,
    private val iconBinder: IconBinder,
    private val actions: WorkspaceActions,
    /** Chikku's message bar (the "layout locked" notice); never a platform toast. */
    private val messages: ChikkuMessages = ChikkuMessages.of(pager.context),
    private val dragStarter: (View, DragPayload) -> Boolean = { source, payload ->
        source.startDragAndDrop(null, LiftedDragShadow(source), payload, 0)
    },
    private val onDockPageChanged: (Int) -> Unit = {},
) {
    private val context = pager.context
    private val density = context.resources.displayMetrics.density

    /** System animator duration scale (ValueAnimator.getDurationScale needs API 33); 1 when unreadable. */
    private fun systemAnimatorScale(): Float =
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    private val edgeZone = context.resources.getDimensionPixelSize(R.dimen.home_drag_edge_zone)

    private var dragSource: View? = null
    private var dragging = false
    /** Desktop page the current drag started from; kept bound until the drag ends. */
    private var dragSourcePage: Int? = null
    /** True while [render] adds, removes and binds pages; window updates wait for it. */
    private var rendering = false
    private var bindUpdatePosted = false
    private val bindUpdate = Runnable {
        bindUpdatePosted = false
        updateBindWindow()
    }
    private var edgeLeft: Boolean? = null
    private var edgeLayout: CellLayout? = null
    private val edgeRunnable = Runnable {
        val left = edgeLeft
        if (left != null) {
            val moved = if (edgeLayout === dock) stepDockVisual(left) else pager.scrollTowardsEdge(left)
            if (moved) Haptics.tick(pager) // no cue when the page or dock cannot move
        }
        edgeLeft = null // re-armed by the next location event while still at the edge
    }

    /** Animation speed in force (user setting plus the system "Remove animations" switch). */
    private var motion = Motion.DEFAULT
    private val handler = Handler(Looper.getMainLooper())

    /** Settings currently applied; [applySettings] updates them. */
    private var settings = LauncherSettings()

    /** Dock pages (G03): the stored dock row is split into pages of `dock.iconCount` columns. */
    private var dockPage = 0
    private var dockPageCount = 1
    private var dockPositionInitialized = false
    private val dockAccessibilityActions = mutableListOf<Int>()

    /** Layout lock (G17): edits are refused with a short message. */
    val isLayoutLocked: Boolean get() = settings.home.layoutLocked
    /** Renders widget items; null falls back to placeholder icons. Set by the widgets feature. */
    var widgetBinder: WidgetViewBinder? = null

    /**
     * Long press on an empty part of a desktop page, with the touch's screen position. Returns true
     * if handled (the widgets feature shows the home menu).
     */
    var onEmptyLongPress: ((page: CellLayout, rawX: Float, rawY: Float) -> Boolean)? = null

    /** Draws folder icons and their gestures; set by the host (owner: folders). */
    var folderBinder: FolderViewBinder? = null

    /** Re-binds every folder icon, e.g. after folder settings changed. */
    fun rebindFolders() {
        val layouts = (0 until pager.childCount).mapNotNull { pager.pageAt(it) } + dock
        for (layout in layouts) {
            for (i in 0 until layout.childCount) {
                val view = layout.getChildAt(i) as? AppIconView ?: continue
                val item = itemOf(view) ?: continue
                if (item.content is ItemContent.Folder) updateItemView(view, item)
            }
        }
    }

    /** True once a workspace has been rendered, so drops have somewhere to land. */
    var isReady: Boolean = false
        private set

    init {
        pager.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) = Unit
            override fun onViewDetachedFromWindow(view: View) = cancelDrag()
        })
        dock.container = ContainerType.DOCK
        dock.setOnDragListener(::onCellDrag)
        removeTarget.setOnDragListener(::onRemoveDrag)
        pager.onPageChanged = { page, count ->
            indicator.setPages(page, count)
            // Synchronous, so a caller that snaps to a page can find that page's views right after.
            requestBindWindowUpdate(immediate = true)
        }
        pager.onUserScroll = indicator::revealWhileScrolling
        pager.onPageScrolled = { position ->
            indicator.setScrollPosition(position)
            // Scroll steps arrive from computeScroll/layout: bind on the next frame, not mid-draw.
            requestBindWindowUpdate(immediate = false)
        }
        indicator.onPageSelected = { pager.snapToPage(it) }
        applySettings(settings, force = true)
    }

    private var rendered: WorkspaceUi.Ready? = null

    /**
     * Applies home and dock preferences (G02, G03, G12, G17). Cheap when nothing relevant changed;
     * call it before [render] on every state update.
     */
    fun applySettings(new: LauncherSettings) = applySettings(new, force = false)

    private fun applySettings(new: LauncherSettings, force: Boolean) {
        applyMotion(Motion.current(new.lookFeel.animationSpeed))
        val old = settings
        if (!force && new.home == old.home && new.dock == old.dock && new.lookFeel.animationSpeed == old.lookFeel.animationSpeed) return
        settings = new
        // Remembering the dock page rewrites only dock.lastPage. The page is read once, when the
        // dock is first laid out, so a change that touches nothing else needs no sizing pass
        // (which would otherwise run in the middle of the dock's page animation).
        if (!force && new.home == old.home && new.dock.copy(lastPage = old.dock.lastPage) == old.dock) return
        val home = new.home
        indicator.style = home.pageIndicatorStyle
        indicator.autoHide = home.pageIndicatorAutoHide
        pager.infiniteScroll = home.infiniteScroll
        pager.wallpaperScrolling = home.wallpaperScrolling
        pager.swipeSensitivity = home.pageSwipeSensitivity
        pager.snapDurationMs = home.pageSnapDurationMs
        pager.effectStrength = home.pageEffectStrength
        pager.edgeFeedback = home.pageEdgeFeedback
        for (i in 0 until pager.childCount) pager.pageAt(i)?.let(::applyPagePadding)
        dock.applyDockContainer(context, new.dock, home, density)
        // The dock (and its backdrop) background is owned by DockSurfaceController.render.
        for (i in 0 until pager.childCount) pager.pageAt(i)?.let { applySizing(it, dockItems = false) }
        applySizing(dock, dockItems = true)
        rendered?.let(::layoutDock)
        // Follow a changed default page if the user is still on the old one (e.g. settings just loaded).
        if (!force && home.defaultPage != old.home.defaultPage && pager.currentPage == old.home.defaultPage) {
            pager.snapToPage(home.defaultPage, animate = false)
        }
    }

    /** Re-reads the system animation switch, which can change while HOME is running. */
    fun refreshMotion() = applyMotion(Motion.current(settings.lookFeel.animationSpeed))

    private fun applyMotion(new: Motion) {
        if (new == motion && initialMotionApplied) return
        initialMotionApplied = true
        motion = new
        indicator.motionScale = new.scale
        dock.motion = new
        for (i in 0 until pager.childCount) pager.pageAt(i)?.motion = new
    }

    private var initialMotionApplied = false

    private fun applyPagePadding(page: CellLayout) {
        val h = HomeLayoutMetrics.paddingPx(settings.home.horizontalPadding, density)
        val v = HomeLayoutMetrics.paddingPx(settings.home.verticalPadding, density)
        page.setPaddingRelative(h, v, h, v)
    }

    private fun applySizing(layout: CellLayout, dockItems: Boolean) {
        for (i in 0 until layout.childCount) {
            val view = layout.getChildAt(i)
            (view as? AppIconView)?.let { applySizing(it, dockItems) }
            (view as? io.github.aj4abinjacob.chikku.home.widget.WidgetCellView)?.let { widget ->
                val override = itemOf(widget)?.widgetPadding
                val padding = HomeLayoutMetrics.widgetPaddingPx(settings.home, density, override)
                widget.setPadding(padding, padding, padding, padding)
                widget.cornerRadiusPx = settings.home.widgetCornerRadiusDp * density
            }
        }
    }

    private fun applySizing(view: AppIconView, dockItem: Boolean) {
        val style = if (dockItem) IconLabelStyle.dock(settings.dock, settings.home, DockBackgroundStyle.labelColor(context, settings.dock))
            else IconLabelStyle.home(settings.home)
        style.applyTo(view)
    }

    fun render(ui: WorkspaceUi.Ready) {
        if (ui == rendered) return // e.g. only the search query changed
        val firstRender = rendered == null
        val previous = rendered
        rendered = ui
        val settle = dropSettle
        val exempt = settle?.itemId?.let(::setOf).orEmpty()
        dock.flipExemptIds = exempt
        for (i in 0 until pager.childCount) pager.pageAt(i)?.flipExemptIds = exempt
        isReady = true
        rendering = true
        try {
            while (pager.childCount < ui.pages.size) {
                val page = CellLayout(context).apply {
                    contentBound = false
                    motion = this@WorkspaceController.motion
                    applyPagePadding(this)
                    setOnDragListener(::onCellDrag)
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    setOnLongClickListener { v ->
                        val layout = v as CellLayout
                        onEmptyLongPress?.invoke(layout, layout.lastDownRawX, layout.lastDownRawY) ?: false
                    }
                }
                pager.addView(page)
            }
            while (pager.childCount > ui.pages.size) {
                val last = pager.childCount - 1
                pager.pageAt(last)?.let { recycleAll(it) }
                pager.removeViewAt(last)
            }
            // Snap before binding so the first render binds the default page's window only.
            if (firstRender) pager.snapToPage(settings.home.defaultPage, animate = false)
            val wanted = bindWindow(ui)
            ui.pages.forEachIndexed { index, items ->
                val page = pager.pageAt(index) ?: return@forEachIndexed
                bindPage(page, index, items, live = index in wanted)
            }
        } finally {
            rendering = false // a failed bind must not freeze the bind window
        }
        layoutDock(ui)
        bindCells(dock, ui.dock, live = true)
        indicator.setPages(pager.currentPage, pager.pageCount)
        widgetBinder?.onWorkspaceRendered(ui)
        if (settle != null && previous != null && DropOutcome.applied(settle.payload, previous, ui)) {
            val created = newItemIds(previous, ui)
            if (ghostTargetShown(settle, created)) resolveSettle(settle, created)
        }
    }

    // --- Bind window ---------------------------------------------------------------------------

    /** Pages that should hold real views now (see [PagerMath.bindWindow]). */
    private fun bindWindow(ui: WorkspaceUi.Ready): Set<Int> {
        val count = minOf(pager.childCount, ui.pages.size)
        val previous = (0 until count).filterTo(HashSet()) { pager.pageAt(it)?.contentBound == true }
        return PagerMath.bindWindow(
            current = pager.currentPage,
            position = pager.scrollPosition,
            pageCount = count,
            wrap = pager.infiniteScroll,
            previous = previous,
            pinned = setOfNotNull(dragSourcePage),
        )
    }

    private fun requestBindWindowUpdate(immediate: Boolean) {
        if (rendered == null || rendering) return
        if (immediate && !pager.isInLayout) {
            updateBindWindow()
        } else if (!bindUpdatePosted) {
            bindUpdatePosted = true
            pager.postOnAnimation(bindUpdate)
        }
    }

    /** Binds pages entering the window and releases pages that left it; cheap when nothing changed. */
    private fun updateBindWindow() {
        val ui = rendered ?: return
        if (rendering) return
        val wanted = bindWindow(ui)
        for (index in 0 until minOf(pager.childCount, ui.pages.size)) {
            val page = pager.pageAt(index) ?: continue
            val live = index in wanted
            if (page.contentBound != live) bindPage(page, index, ui.pages[index], live)
        }
    }

    /** Pages currently holding real views, in page order (for tests and diagnostics). */
    val boundPages: List<Int>
        get() = (0 until pager.childCount).filter { pager.pageAt(it)?.contentBound == true }

    private fun bindPage(page: CellLayout, index: Int, items: List<HomeItemUi>, live: Boolean) {
        page.grid = rendered?.desktopGrid ?: page.grid
        page.page = index
        page.container = ContainerType.DESKTOP
        page.contentBound = live
        bindCells(page, items, live)
    }

    private fun newItemIds(before: WorkspaceUi.Ready, after: WorkspaceUi.Ready): Set<String> {
        val known = (before.pages.flatten() + before.dock).mapTo(HashSet()) { it.id }
        return (after.pages.flatten() + after.dock).mapNotNullTo(HashSet()) { it.id.takeIf { id -> id !in known } }
    }

    /**
     * With a drop ghost showing, the stored change counts only once the dropped item exists: the moved
     * item itself, or for a drop that creates an item (drawer, search, folder) an item that was not in the
     * previous layout. A bare matching cell is not enough, since an unrelated item can start there.
     */
    private fun ghostTargetShown(settle: DropSettle, created: Set<String>): Boolean {
        if (dropGhost == null) return true
        return if (settle.itemId != null) findItemView(settle.itemId) != null else created.isNotEmpty()
    }

    /** Shows one dock page: a window of `dock.iconCount` columns over the stored dock row. */
    private fun layoutDock(ui: WorkspaceUi.Ready) {
        val pageColumns = settings.dock.iconCount.coerceIn(1, ui.dockGrid.columns)
        dockPageCount = DockPaging.pageCount(ui.dockGrid.columns, pageColumns)
        dockPage = (if (!dockPositionInitialized && settings.dock.rememberPosition) settings.dock.lastPage else dockPage).coerceIn(0, dockPageCount - 1)
        dockPositionInitialized = true
        dock.grid = GridSpec(pageColumns, ui.dockGrid.rows)
        dock.columnOffset = dockPage * pageColumns
        dock.onHorizontalSwipe = if (dockPageCount > 1) { forward -> stepDock(forward); Unit } else null
        updateDockAccessibility()
    }

    private fun stepDock(forward: Boolean): Boolean {
        val candidate = dockPage + if (forward) 1 else -1
        val next = if (settings.dock.infiniteScroll) Math.floorMod(candidate, dockPageCount)
            else candidate.coerceIn(0, dockPageCount - 1)
        if (next == dockPage) return false
        dockPage = next
        dock.shiftColumnOffset(dockPage * dock.grid.columns, forward)
        onDockPageChanged(dockPage)
        updateDockAccessibility()
        return true
    }

    /** HOME returns to the first dock page unless remembering the page is enabled. */
    fun resetDockPosition() {
        if (settings.dock.rememberPosition || dockPage == 0) return
        dockPage = 0
        dock.columnOffset = 0
        updateDockAccessibility()
    }

    /** Moves the dock one page towards the visual left or right (drag edge hover). */
    private fun stepDockVisual(left: Boolean): Boolean = stepDock(forward = if (dock.isRtl) left else !left)

    private fun updateDockAccessibility() {
        dockAccessibilityActions.forEach { ViewCompat.removeAccessibilityAction(dock, it) }
        dockAccessibilityActions.clear()
        if (dockPageCount <= 1) {
            ViewCompat.setStateDescription(dock, null)
            return
        }
        ViewCompat.setStateDescription(dock, context.getString(R.string.home_dock_page_description, dockPage + 1, dockPageCount))
        if (settings.dock.infiniteScroll || dockPage < dockPageCount - 1) {
            dockAccessibilityActions += ViewCompat.addAccessibilityAction(dock, context.getString(R.string.home_dock_next_page)) { _, _ ->
                stepDock(forward = true)
                true
            }
        }
        if (settings.dock.infiniteScroll || dockPage > 0) {
            dockAccessibilityActions += ViewCompat.addAccessibilityAction(dock, context.getString(R.string.home_dock_previous_page)) { _, _ ->
                stepDock(forward = false)
                true
            }
        }
    }

    /** Workspace unavailable (failed or safe mode): drop all views so nothing renders or loads. */
    fun clear() {
        rendered = null
        rendering = false
        dragSourcePage = null
        pager.removeCallbacks(bindUpdate)
        bindUpdatePosted = false
        dockPositionInitialized = false
        isReady = false
        for (i in 0 until pager.childCount) pager.pageAt(i)?.let { recycleAll(it) }
        pager.removeAllViews()
        cancelSettle(restore = false)
        finishDropGhost()
        poppingIn = null
        recycleAll(dock)
        dock.forgetBinding()
        dock.removeAllViews()
        indicator.setPages(0, 0)
    }

    /** Home button / "go to default page" gesture (G12). */
    fun goToDefaultPage() = pager.snapToPage(settings.home.defaultPage.coerceIn(0, (pager.pageCount - 1).coerceAtLeast(0)))

    /** Current dock page (0-based) and count, for tests and accessibility. */
    val dockPosition: Pair<Int, Int> get() = dockPage to dockPageCount

    /** Shows the "layout locked" message; returns true if the layout is locked and the edit must stop. */
    fun refuseIfLocked(): Boolean {
        if (!isLayoutLocked) return false
        messages.show(R.string.home_layout_locked) // replaces any message showing, so repeats never queue up
        return true
    }

    private fun recycleAll(layout: CellLayout) {
        for (i in 0 until layout.childCount) recycleView(layout.getChildAt(i))
    }

    private fun recycleView(view: View) {
        when (view) {
            is CellLayout.Placeholder -> Unit
            is AppIconView -> iconBinder.cancel(view)
            else -> widgetBinder?.recycleWidget(view)
        }
    }

    private fun isWidget(item: HomeItemUi) = item.content is ItemContent.Widget && widgetBinder != null

    /**
     * Binds [items] into [layout]. A [live] layout gets real icon/folder/widget views; otherwise each
     * item gets a [CellLayout.Placeholder] and any real view it had is recycled (icon load cancelled,
     * widget host view released).
     */
    private fun bindCells(layout: CellLayout, items: List<HomeItemUi>, live: Boolean) {
        layout.bind(
            items = items,
            id = { it.id },
            cellOf = {
                CellLayout.LayoutParams(it.placement.x, it.placement.y, it.placement.spanX, it.placement.spanY)
                    .apply { zIndex = it.zIndex }
            },
            create = { item ->
                when {
                    !live -> CellLayout.Placeholder(context)
                    isWidget(item) -> widgetBinder?.createWidgetView() ?: createItemView()
                    else -> createItemView()
                }
            },
            update = { view, item ->
                val content = item.content
                if (view is CellLayout.Placeholder) {
                    view.setTag(R.id.home_item_model, item)
                    return@bind
                }
                if (view is AppIconView) {
                    updateItemView(view, item)
                    applySizing(view, dockItem = layout === dock)
                } else if (content is ItemContent.Widget) {
                    widgetBinder?.bindWidget(view, item, content)
                    // Widgets have no binder that sets alpha: a cell dimmed for a drag would otherwise
                    // be remembered as the "normal" alpha and stay faded after the drop. A running
                    // pop-in or glide is not cancelled: that would freeze it part-way (scaled or offset).
                    view.alpha = 1f
                }
                if (item.id == heldItemId) holdDragSource(view)
                keepHiddenWhileLanding(view)
            },
            recycle = ::recycleView,
            canReuse = { view, item ->
                if (!live) {
                    view is CellLayout.Placeholder
                } else {
                    view !is CellLayout.Placeholder && isWidget(item) == (widgetBinder?.isWidgetView(view) == true)
                }
            },
        )
    }

    private fun createItemView(): View {
        val view = AppIconView(context, AppIconView.Style.WORKSPACE)
        view.setOnClickListener { v -> itemOf(v)?.let { actions.onItemClick(it, v) } }
        // Long-press opens the item menu; moving the finger afterwards closes it and drags the item.
        // While the layout is locked the menu still opens (app info, uninstall, ...) but never turns
        // into a drag; without a menu the long-press explains the lock instead.
        val touch = LongPressMenuDrag.attach(
            view,
            showMenu = { v -> itemOf(v)?.let { actions.onShowItemMenu(it, v) } },
            startDrag = { v ->
                val item = itemOf(v)
                when {
                    item == null -> false
                    refuseIfLocked() -> true // consumed: the lock message replaces the drag
                    else -> startDrag(v, DragPayload.Existing(item.id, item.placement))
                }
            },
            canDragFromMenu = { !isLayoutLocked },
        )
        view.setTag(R.id.home_icon_touch, touch)
        ViewCompat.addAccessibilityAction(view, context.getString(R.string.app_menu_open)) { v, _ ->
            itemOf(v)?.let { actions.onShowItemMenu(it, v) } != null
        }
        ViewCompat.addAccessibilityAction(view, context.getString(R.string.home_action_remove_from_home)) { v, _ ->
            if (!refuseIfLocked()) itemOf(v)?.let { actions.onRemoveItem(it.id) }
            true
        }
        ViewCompat.addAccessibilityAction(view, context.getString(R.string.home_action_app_info)) { v, _ ->
            when (val content = itemOf(v)?.content) {
                is ItemContent.App -> actions.onAppInfo(content.key, v)
                // Folder/widget/shortcut menus are added by their feature owners.
                is ItemContent.Folder, is ItemContent.Widget, is ItemContent.Shortcut, null -> Unit
            }
            true
        }
        return view
    }

    private fun updateItemView(view: AppIconView, item: HomeItemUi) {
        view.setTag(R.id.home_item_model, item)
        when (val content = item.content) {
            is ItemContent.App -> iconBinder.bindApp(view, content.key, content.label, content.isWork, content.availability)
            is ItemContent.Folder -> folderBinder?.bindFolder(view, item) ?: iconBinder.bindPlaceholder(
                view,
                content.title.ifBlank { context.getString(R.string.home_folder_unnamed) },
                context.resources.getQuantityString(
                    R.plurals.home_folder_description, content.entries.size, content.title, content.entries.size,
                ),
                R.drawable.ic_app_placeholder,
            )
            is ItemContent.Widget -> iconBinder.bindPlaceholder(
                view,
                context.getString(R.string.home_widget_placeholder),
                context.getString(R.string.home_widget_placeholder),
                R.drawable.ic_app_placeholder,
            )
            is ItemContent.Shortcut -> iconBinder.bindShortcut(view, content)
        }
    }

    private fun itemOf(view: View): HomeItemUi? = view.getTag(R.id.home_item_model) as? HomeItemUi

    // --- Drag and drop -------------------------------------------------------------------------

    /** Called whenever a drag session ends, dropped or not. */
    var onDragEnded: (() -> Unit)? = null

    /** A drop that was handed to the host and is waiting for the stored layout to show it. */
    private class DropSettle(val payload: DragPayload, val itemId: String?)

    private var dropSettle: DropSettle? = null
    private var dragSourceId: String? = null

    /** Alpha the dragged item shows when it is not held (0.55 for an unavailable app). */
    private var dragRestAlpha = 1f

    /** The item whose views stay dimmed or hidden, even when a render rebinds them. */
    private val heldItemId: String? get() = dropSettle?.itemId ?: dragSourceId

    private val settleTimeout = Runnable { cancelSettle(restore = true) }

    private var previewLayout: CellLayout? = null
    private var previewCell: Cell? = null

    /** Cancels [view]'s animator and forgets its pending fade-in; returns the alpha that fade was heading to. */
    private fun takeOverMotion(view: View): Float? {
        val rest = (view.parent as? CellLayout)?.forgetFadeIn(view)
        view.animate().cancel()
        return rest
    }

    /** Dims [view] the way the drag state wants, remembering the alpha the binder just gave it. */
    private fun holdDragSource(view: View) {
        dragRestAlpha = view.alpha
        view.alpha = if (dropSettle != null) 0f else dragSourceAlpha(view)
    }

    /**
     * Widgets (the clock is white text on a transparent background) vanish at the icon dim of 0.3 on
     * a light wallpaper, so a dragged widget's cell is dimmed only slightly.
     */
    private fun dragSourceAlpha(view: View): Float =
        if (view is AppIconView) DRAG_SOURCE_ALPHA else DRAG_WIDGET_SOURCE_ALPHA

    private fun allLayouts(): List<CellLayout> = (0 until pager.childCount).mapNotNull { pager.pageAt(it) } + dock

    private fun findItemView(itemId: String): View? =
        allLayouts().firstNotNullOfOrNull { it.itemView(itemId) }

    /**
     * Starts a drag from a workspace item or, with [DragPayload.NewApp] / [DragPayload.NewWidget],
     * from the drawer or the widget picker.
     */
    fun startDrag(source: View, payload: DragPayload): Boolean {
        if (!isReady || dragging) return false
        if (refuseIfLocked()) return false
        cancelSettle(restore = true)
        dragging = true
        finishDropGhost()
        dragGhostSource = (source as? AppIconView)?.takeIf { payload !is DragPayload.NewWidget && itemOf(it)?.content !is ItemContent.Widget }
        if (payload is DragPayload.Existing) {
            val fadeRest = takeOverMotion(source)
            finishPopIn(source) // mid pop-in its alpha and scale are not the ones it rests at
            dragRestAlpha = fadeRest ?: source.alpha
            dragSourceId = payload.itemId
            source.alpha = dragSourceAlpha(source)
            dragSource = source
            dragSourcePage = (source.parent as? CellLayout)?.takeIf { it !== dock }?.page
        }
        val highlightMs = motion.duration(HIGHLIGHT_MOVE_MS)
        dock.highlightMoveMs = highlightMs
        for (i in 0 until pager.childCount) pager.pageAt(i)?.highlightMoveMs = highlightMs
        if (payload is DragPayload.Existing || payload is DragPayload.FromFolder) {
            // Must be visible before DRAG_STARTED is dispatched, so it fades by alpha, not visibility.
            removeTarget.animate().cancel()
            val ms = motion.duration(TARGET_FADE_MS)
            removeTarget.alpha = if (ms > 0L) 0f else 1f
            removeTarget.visibility = View.VISIBLE
            if (ms > 0L) removeTarget.animate().alpha(1f).setDuration(ms).start()
        }
        val started = try {
            dragStarter(source, payload)
        } catch (_: RuntimeException) {
            false // A window disappearing during long press must not strand this session.
        }
        if (!started) endDrag()
        return started
    }

    /** Stop/detach must release retained drag sources even if Android sends no ENDED event. */
    fun cancelDrag() {
        cancelSettle(restore = false)
        finishDropGhost()
        if (!dragging) return
        try {
            pager.cancelDragAndDrop()
        } catch (_: RuntimeException) {
            // The window may already be detached; local cleanup still has to happen.
        } finally {
            endDrag()
        }
    }

    private fun endDrag() {
        if (!dragging) return
        dragging = false
        cancelEdgeHover()
        val source = dragSource
        dragSource = null
        dragGhostSource = null
        // A dropped item stays hidden until the stored layout shows it; any other end restores it.
        if (source != null && dropSettle?.itemId != dragSourceId) restoreSource(source)
        if (dropSettle == null) dragSourceId = null
        if (dragSourcePage != null) {
            dragSourcePage = null
            requestBindWindowUpdate(immediate = true)
        }
        hideRemoveTarget()
        removeTarget.isActivated = false
        clearPreview()
        onDragEnded?.invoke()
    }

    private fun restoreSource(view: View) {
        takeOverMotion(view)
        val ms = motion.duration(TARGET_FADE_MS)
        if (ms <= 0L || !view.isAttachedToWindow) view.alpha = dragRestAlpha
        else view.animate().alpha(dragRestAlpha).setDuration(ms).start()
    }

    private fun hideRemoveTarget() {
        removeTarget.animate().cancel()
        val ms = motion.duration(TARGET_FADE_MS)
        if (ms <= 0L || !removeTarget.isAttachedToWindow) {
            removeTarget.visibility = View.GONE
            removeTarget.alpha = 1f
            return
        }
        removeTarget.animate().alpha(0f).setDuration(ms).withEndAction {
            if (!dragging) {
                removeTarget.visibility = View.GONE
                removeTarget.alpha = 1f
            }
        }.start()
    }

    private fun clearPreview() {
        previewLayout = null
        previewCell = null
        dock.setDropHighlight(null)
        for (i in 0 until pager.childCount) pager.pageAt(i)?.setDropHighlight(null)
    }

    /** The host was handed a drop: hide the item until the next render, then bounce it into place. */
    private fun beginSettle(payload: DragPayload, landing: Pair<CellLayout, CellPlacement>? = null) {
        cancelSettle(restore = true)
        val itemId = (payload as? DragPayload.Existing)?.itemId
        dropSettle = DropSettle(payload, itemId)
        dragSourceId = itemId
        dragSource?.let { view ->
            takeOverMotion(view)
            val ms = motion.duration(TARGET_FADE_MS)
            if (ms > 0L && view.isAttachedToWindow) view.animate().alpha(0f).setDuration(ms).start() else view.alpha = 0f
        }
        if (landing != null) showDropGhost(landing.first, landing.second, payload)
        handler.postDelayed(settleTimeout, SETTLE_WAIT_MS)
    }

    // --- Drop ghost: the dropped icon stays visible at its target while the stored layout is awaited ---

    /**
     * A copy of the dropped icon, built from the same view class and settings as a workspace icon, so
     * handing over to the real view is invisible. It holds the icon and label only while a drop is
     * pending; there is no bitmap of the dragged view, and widgets never get one.
     */
    private class DropGhost(val layout: CellLayout, val cell: Cell, val view: AppIconView, var restAlpha: Float) {
        var animating = false

        /** The real view, kept hidden until the ghost's landing ends. */
        var reveal: View? = null
    }

    private var dropGhost: DropGhost? = null

    /** The dragged icon view while a drag runs: read for its icon and label at the drop, never stored beyond it. */
    private var dragGhostSource: AppIconView? = null

    /** For tests: a dropped icon is drawn at its target while the stored layout is awaited. */
    internal val hasDropGhost: Boolean get() = dropGhost != null

    /** For tests: the drag still points at its source view. */
    internal val holdsDragSource: Boolean get() = dragGhostSource != null

    /** For tests: the ghost view, which lives in its layout's overlay. */
    internal val dropGhostView: View? get() = dropGhost?.view

    /** Safety net: reveals the real view even if the landing animation never reports its end. */
    private val ghostRevealTimeout = Runnable { finishDropGhost() }

    /**
     * The drag shadow ends at the drop but the stored layout arrives later: draw the icon at its
     * target cell meanwhile, landing with the same lift-and-settle the folders use, so the cell is
     * never empty. Only for free cells inside the visible page; a drop onto an item (into a folder)
     * has its own feedback.
     */
    private fun showDropGhost(layout: CellLayout, target: CellPlacement, payload: DragPayload) {
        finishDropGhost()
        val source = dragGhostSource ?: return
        val cell = Cell(target.x, target.y)
        val excluded = (payload as? DragPayload.Existing)?.itemId
        if (layout.isOccupied(cell, target.spanX, target.spanY, excluded)) return
        val bounds = Rect()
        if (!layout.cellBounds(cell, target.spanX, target.spanY, bounds)) return
        // A drawable is never shared with the source view; without a copy there is no ghost.
        val icon = source.icon?.constantState?.newDrawable()?.mutate() ?: return
        // The resting alpha of the dragged item (0.55 for an unavailable app), not a fixed 1.
        val rest = if (payload is DragPayload.Existing) dragRestAlpha else source.alpha.takeIf { it > 0f } ?: 1f
        val view = AppIconView(context, AppIconView.Style.WORKSPACE).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            applySizing(this, dockItem = layout === dock)
            label = source.label
            showWorkBadge = source.showWorkBadge
            badge = source.badge
            this.icon = icon
            alpha = rest
            measure(
                View.MeasureSpec.makeMeasureSpec(bounds.width(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(bounds.height(), View.MeasureSpec.EXACTLY),
            )
        }
        layout.overlay.add(view)
        view.layout(bounds.left, bounds.top, bounds.right, bounds.bottom)
        val ghost = DropGhost(layout, cell, view, rest)
        dropGhost = ghost
        val ms = motion.duration(FolderReorderMotion.SETTLE_MS)
        if (ms > 0L && layout.isAttachedToWindow) {
            ghost.animating = true
            ItemMotion.bounceIn(view, ms) { landed(ghost) }
        }
    }

    /** For tests: pretend the landing and fade animations never report their end. */
    internal var stallAnimationEndsForTest = false

    private fun landed(ghost: DropGhost) {
        if (stallAnimationEndsForTest) return
        ghost.animating = false
        if (dropGhost === ghost && ghost.reveal != null) finishDropGhost()
    }

    private fun releaseGhostView(view: AppIconView) {
        view.icon = null
        view.badge = null
        view.label = ""
    }

    /** Shows the real view (if it was waiting) and removes the ghost at once; the two look the same. */
    private fun finishDropGhost() {
        releaseFadingGhost()
        val ghost = dropGhost ?: return
        dropGhost = null
        handler.removeCallbacks(ghostRevealTimeout)
        ghost.view.animate().cancel()
        ghost.reveal?.let { it.alpha = ghost.restAlpha }
        ghost.layout.overlay.remove(ghost.view)
        releaseGhostView(ghost.view)
    }

    /** Nothing was stored: the ghost fades out as the source returns to its old cell. */
    private fun fadeOutDropGhost() {
        val ghost = dropGhost ?: return
        val ms = motion.duration(TARGET_FADE_MS)
        if (ms <= 0L || !ghost.layout.isAttachedToWindow) {
            finishDropGhost()
            return
        }
        dropGhost = null
        handler.removeCallbacks(ghostRevealTimeout)
        releaseFadingGhost()
        fadingGhost = ghost
        val view = ghost.view
        view.animate().cancel()
        view.animate().alpha(0f).setDuration(ms).withEndAction { if (!stallAnimationEndsForTest) releaseFadingGhost() }.start()
    }

    /** A ghost that is fading out; tracked so a new drag, stop or clear can release it at once. */
    private var fadingGhost: DropGhost? = null

    /** For tests: a ghost is still fading out in its layout's overlay. */
    internal val fadingGhostView: View? get() = fadingGhost?.view

    private fun releaseFadingGhost() {
        val ghost = fadingGhost ?: return
        fadingGhost = null
        ghost.view.animate().cancel()
        ghost.layout.overlay.remove(ghost.view)
        releaseGhostView(ghost.view)
    }

    /** The stored layout arrived: the real view waits under the ghost until it has landed. */
    private fun handOverToGhost(real: View?, created: Set<String>) {
        val ghost = dropGhost ?: return
        // A created item is the new one at the ghost's cell. If the stored layout placed it elsewhere (the
        // cell was taken meanwhile), the ghost just fades out and the item's own arrival shows.
        val view = real ?: ghost.layout.viewAt(ghost.cell)?.takeIf { it.getTag(R.id.home_item_id) in created }
        if (view == null) {
            fadeOutDropGhost()
            return
        }
        ghost.restAlpha = ghost.layout.restingAlpha(view)?.takeIf { it > 0f } ?: ghost.restAlpha
        takeOverMotion(view)
        ghost.reveal = view
        if (ghost.animating) {
            view.alpha = 0f
            // Animators run at the system duration scale, so the safety net waits for that long.
            val landingMs = (motion.duration(FolderReorderMotion.SETTLE_MS) * systemAnimatorScale()).toLong()
            handler.postDelayed(ghostRevealTimeout, landingMs + GHOST_REVEAL_SLACK_MS)
        } else {
            finishDropGhost()
        }
    }

    /** A render during the landing re-binds the real view; it stays hidden until the ghost reveals it. */
    private fun keepHiddenWhileLanding(view: View) {
        val ghost = dropGhost ?: return
        if (ghost.reveal !== view) return
        if (view.alpha > 0f) ghost.restAlpha = view.alpha
        view.alpha = 0f
    }

    /** The stored layout shows the drop: confirm it and let the item pop into its new cell. */
    private fun resolveSettle(settle: DropSettle, created: Set<String> = emptySet()) {
        handler.removeCallbacks(settleTimeout)
        dropSettle = null
        if (!dragging) dragSourceId = null
        clearFlipExemptions()
        Haptics.drop(pager)
        val view = settle.itemId?.let(::findItemView)
        if (dropGhost != null) {
            handOverToGhost(view, created)
            return
        }
        if (view != null) popIn(view)
    }

    private fun clearFlipExemptions() {
        dock.flipExemptIds = emptySet()
        for (i in 0 until pager.childCount) pager.pageAt(i)?.flipExemptIds = emptySet()
    }

    /** Nothing was stored (rejected, or no change): everything returns to where it was. */
    private fun cancelSettle(restore: Boolean) {
        handler.removeCallbacks(settleTimeout)
        val settle = dropSettle ?: return
        dropSettle = null
        if (!dragging) dragSourceId = null
        clearFlipExemptions()
        if (restore) fadeOutDropGhost() else finishDropGhost()
        val view = settle.itemId?.let(::findItemView) ?: return
        takeOverMotion(view)
        if (restore) restoreSource(view) else view.alpha = dragRestAlpha
    }

    private fun popIn(view: View) {
        takeOverMotion(view)
        val rest = dragRestAlpha
        val ms = motion.duration(FolderReorderMotion.SETTLE_MS)
        if (ms <= 0L || !view.isAttachedToWindow) {
            view.alpha = rest
            return
        }
        view.alpha = 0f
        poppingIn = view to rest
        ItemMotion.bounceIn(view, ms, alphaTo = rest) { if (poppingIn?.first === view) poppingIn = null }
    }

    /** The view popping into its cell after a drop, with the alpha it rests at. */
    private var poppingIn: Pair<View, Float>? = null

    /** Ends [view]'s pop-in at its resting state; its animation must already be cancelled. */
    private fun finishPopIn(view: View) {
        val (popping, rest) = poppingIn ?: return
        if (popping !== view) return
        poppingIn = null
        if (view.scaleX == 1f && view.scaleY == 1f) return // already ended; a bind has set its alpha since
        view.scaleX = 1f
        view.scaleY = 1f
        view.alpha = rest
    }

    private fun onCellDrag(view: View, event: DragEvent): Boolean {
        val layout = view as CellLayout
        val payload = event.localState as? DragPayload ?: return false
        val (spanX, spanY) = when (payload) {
            is DragPayload.Existing -> payload.from.spanX to payload.from.spanY
            is DragPayload.NewWidget -> payload.spanX to payload.spanY
            is DragPayload.NewApp, is DragPayload.FromFolder -> 1 to 1
        }
        when (event.action) {
            DragEvent.ACTION_DRAG_STARTED -> return true
            DragEvent.ACTION_DRAG_LOCATION -> {
                // New widgets go on the desktop; the dock only reports the end of the drag.
                if (payload is DragPayload.NewWidget && layout === dock) return true
                showLandingPreview(layout, payload, layout.cellAt(event.x, event.y, spanX, spanY), spanX, spanY)
                if (layout.container == ContainerType.DESKTOP || dockPageCount > 1) updateEdgeHover(layout, event.x)
            }
            DragEvent.ACTION_DRAG_EXITED -> {
                layout.setDropHighlight(null)
                if (previewLayout === layout) {
                    previewLayout = null
                    previewCell = null
                }
                cancelEdgeHover()
            }
            DragEvent.ACTION_DROP -> {
                clearPreview()
                cancelEdgeHover()
                val cell = layout.cellAt(event.x, event.y, spanX, spanY)
                val target = CellPlacement(layout.container, layout.page, cell.x, cell.y, spanX, spanY)
                if (payload is DragPayload.Existing && payload.from == target) return true
                if (refuseIfLocked()) return true
                WallpaperCommands.drop(layout, event.x, event.y)
                if (payload is DragPayload.NewWidget) {
                    Haptics.drop(layout) // starts the add flow, which validates and may refuse later
                } else {
                    // the buzz waits for the stored layout to show the drop
                    beginSettle(payload, layout to target)
                }
                actions.onDrop(payload, target)
            }
            DragEvent.ACTION_DRAG_ENDED -> {
                layout.setDropHighlight(null)
                // Any accepted surface can finish: the dock may be hidden or disabled.
                endDrag()
            }
        }
        return true
    }

    /** Shows where the drop would end up: a folder ring on an occupant, the real free cell, or the cell itself. */
    private fun showLandingPreview(layout: CellLayout, payload: DragPayload, cell: Cell, spanX: Int, spanY: Int) {
        if (previewLayout === layout && previewCell == cell) return
        previewLayout = layout
        previewCell = cell
        val target = CellPlacement(layout.container, layout.page, cell.x, cell.y, spanX, spanY)
        val ui = rendered
        val moved = when (val plan = FolderDropPlanner.plan(payload, target, ui)) {
            is DropPlan.CreateFolder, is DropPlan.AddToFolder -> {
                val occupant = ui?.let { FolderDropPlanner.occupantAt(it, target) }
                val id = when (plan) {
                    is DropPlan.CreateFolder -> plan.anchorItemId
                    is DropPlan.AddToFolder -> plan.folderId
                    else -> null
                }
                if (occupant == null) layout.setDropHighlight(cell, spanX, spanY)
                else layout.setDropHighlight(Cell(occupant.placement.x, occupant.placement.y), 1, 1, CellLayout.DropHint.FOLDER, id)
            }
            is DropPlan.MoveOutOfFolder -> layout.setDropHighlight(Cell(plan.to.x, plan.to.y), 1, 1)
            DropPlan.Ignore -> layout.setDropHighlight(null)
            DropPlan.Default -> layout.setDropHighlight(cell, spanX, spanY)
        }
        // A highlight gliding to another cell gets a light tick; appearing or clearing does not.
        if (moved) Haptics.cell(layout)
    }

    private fun onRemoveDrag(view: View, event: DragEvent): Boolean {
        val payload = event.localState as? DragPayload ?: return false
        if (payload is DragPayload.NewApp || payload is DragPayload.NewWidget) return false
        when (event.action) {
            DragEvent.ACTION_DRAG_ENTERED -> {
                view.isActivated = true
                Haptics.tick(view)
            }
            DragEvent.ACTION_DRAG_EXITED -> view.isActivated = false
            DragEvent.ACTION_DROP -> if (!refuseIfLocked()) when (payload) {
                is DragPayload.Existing -> {
                    beginSettle(payload)
                    actions.onRemoveItem(payload.itemId)
                }
                is DragPayload.FromFolder -> {
                    beginSettle(payload)
                    actions.onRemoveFolderEntry(payload.folderId, payload.entryId)
                }
                is DragPayload.NewApp, is DragPayload.NewWidget -> Unit
            }
            DragEvent.ACTION_DRAG_ENDED -> if (dragging) endDrag()
        }
        return true
    }

    private fun updateEdgeHover(layout: CellLayout, x: Float) {
        val left = when {
            x < edgeZone -> true
            x > layout.width - edgeZone -> false
            else -> null
        }
        if (left == null) {
            cancelEdgeHover()
            return
        }
        if (edgeLeft == left && edgeLayout === layout) return
        cancelEdgeHover()
        edgeLeft = left
        edgeLayout = layout
        pager.postDelayed(edgeRunnable, EDGE_HOVER_DELAY_MS)
    }

    private fun cancelEdgeHover() {
        pager.removeCallbacks(edgeRunnable)
        edgeLeft = null
        edgeLayout = null
    }

    /** The platform drag image: the item, slightly enlarged so it reads as lifted off the page. */
    private class LiftedDragShadow(view: View) : View.DragShadowBuilder(view) {
        override fun onProvideShadowMetrics(size: Point, touch: Point) {
            val v = view
            size.set((v.width * SHADOW_SCALE).toInt().coerceAtLeast(1), (v.height * SHADOW_SCALE).toInt().coerceAtLeast(1))
            touch.set(size.x / 2, size.y / 2)
        }

        override fun onDrawShadow(canvas: Canvas) {
            canvas.scale(SHADOW_SCALE, SHADOW_SCALE)
            view.draw(canvas)
        }
    }

    private companion object {
        const val EDGE_HOVER_DELAY_MS = 500L
        const val DRAG_SOURCE_ALPHA = 0.3f
        const val HIGHLIGHT_MOVE_MS = 110L
        const val DRAG_WIDGET_SOURCE_ALPHA = 0.75f
        const val TARGET_FADE_MS = 150L
        const val GHOST_REVEAL_SLACK_MS = 120L

        /** Longest wait for the stored layout before a dropped item returns to its old cell. */
        const val SETTLE_WAIT_MS = FolderReorderMotion.SETTLE_WAIT_MS

        /**
         * Enlarged rather than FolderReorderMotion.LIFT_SCALE (0.6, which shrinks the folder cell's
         * own copy): the floating shadow reads as lifted when it is a little bigger than the icon.
         */
        const val SHADOW_SCALE = 1.1f
    }
}

/** Decides from two rendered layouts whether a dropped payload changed anything. Pure, for tests. */
internal object DropOutcome {
    fun applied(payload: DragPayload, before: WorkspaceUi.Ready, after: WorkspaceUi.Ready): Boolean = when (payload) {
        is DragPayload.Existing -> {
            val now = (after.pages.flatten() + after.dock).firstOrNull { it.id == payload.itemId }
            now == null || now.placement != payload.from
        }
        is DragPayload.NewWidget -> false
        is DragPayload.NewApp, is DragPayload.FromFolder -> before.pages != after.pages || before.dock != after.dock
    }
}
