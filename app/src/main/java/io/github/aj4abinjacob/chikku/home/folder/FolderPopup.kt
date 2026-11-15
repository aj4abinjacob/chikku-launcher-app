package io.github.aj4abinjacob.chikku.home.folder

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.DragEvent
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.settings.FolderSettings
import io.github.aj4abinjacob.chikku.core.model.settings.FolderScrollStyle
import io.github.aj4abinjacob.chikku.core.model.settings.FolderSortOrder
import io.github.aj4abinjacob.chikku.core.model.settings.FolderWindowStyle
import java.text.Collator
import io.github.aj4abinjacob.chikku.home.AppIconView
import io.github.aj4abinjacob.chikku.home.DragPayload
import io.github.aj4abinjacob.chikku.home.FolderEntryUi
import io.github.aj4abinjacob.chikku.home.Haptics
import io.github.aj4abinjacob.chikku.home.HomeItemUi
import io.github.aj4abinjacob.chikku.home.IconBinder
import io.github.aj4abinjacob.chikku.home.IconLabelStyle
import io.github.aj4abinjacob.chikku.home.ItemContent
import io.github.aj4abinjacob.chikku.home.appmenu.AppMenuHandle
import io.github.aj4abinjacob.chikku.home.appmenu.LongPressMenuDrag
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import io.github.aj4abinjacob.chikku.theme.Motion
import io.github.aj4abinjacob.chikku.settings.components.CHIKKU_DEFAULT_ACCENT

/**
 * The open folder: a panel over home with the folder's apps and, below them, the inline name field
 * and an edit button, above a scrim that closes it. A floating panel opens next to the folder icon
 * it came from and grows out of it, so the folder reads as expanding in place. The scrim never takes part in drag and drop, so an entry
 * dragged off the panel reaches the workspace below; leaving the panel closes the folder.
 */
class FolderPopup(
    private val root: FrameLayout,
    private val iconBinder: IconBinder,
    private val listener: Listener,
) {
    interface Listener {
        fun onEntryClick(folder: HomeItemUi, entry: FolderEntryUi, view: View)

        /** Start a [DragPayload.FromFolder] drag from [view] (long-press, or moving out of the menu). */
        fun onEntryLongClick(folder: HomeItemUi, entry: FolderEntryUi, view: View): Boolean

        /** Long-press menu for [entry]; null means "no menu", and the long-press starts a drag at once. */
        fun onEntryMenu(folder: HomeItemUi, entry: FolderEntryUi, view: View): AppMenuHandle? = null
        fun onReorder(folderId: String, orderedEntryIds: List<String>)
        fun onRename(folderId: String, title: String)
        fun onMoveToHome(folderId: String, entryId: String)
        fun onRemoveEntry(folderId: String, entryId: String)
        fun onEdit(folder: HomeItemUi)

        /** Open state changed; the host hides the workspace from accessibility while open. */
        fun onOpenChanged(open: Boolean)
    }

    private val context: Context = root.context
    private val res = context.resources
    private var scrim: View? = null
    private var panel: LinearLayout? = null
    private var title: EditText? = null
    private var grid: FolderGridLayout? = null
    private var folder: HomeItemUi? = null
    private var settings: FolderSettings? = null
    private var currentPage = 0
    private var pageWindow = FolderPageWindow(0, 1, 0, 0)
    private var pageControls: LinearLayout? = null
    private var pageIndicator: TextView? = null
    private var previousPage: Button? = null
    private var nextPage: Button? = null
    private var pagedRows = 1
    private var pagedColumns = 1
    private var pagingCellHeight = 1
    private var stackedPager = false
    private var pageViewport: FolderSwipeViewport? = null
    private var entryScroller: ScrollView? = null
    private var safeInsets = Insets.NONE
    private var geometryWidth = -1
    private var geometryHeight = -1
    private val rootLayoutListener = View.OnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
        if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
            root.post { if (isOpen) refreshGeometry() }
        }
    }

    /** Night-mode panel color (style settings `SurfaceColors`); null keeps the theme resource. */
    var themePanelColor: Int? = null
        set(value) {
            if (field == value) return
            field = value
            restyleOpenPanel()
        }

    /** Wallpaper primary colour; tints the theme panel colour (not a user-chosen one) when set. */
    var wallpaperTint: Int? = null
        set(value) {
            if (field == value) return
            field = value
            restyleOpenPanel()
        }

    /** Folder icon bounds in [root] coordinates; null opens a centred panel (e.g. from search). */
    private var anchorRect: Rect? = null

    /** Animation speed (style settings); [Motion.duration] 0 opens without animating. */
    var motion: Motion = Motion.DEFAULT
        set(value) {
            field = value
            reorder.motion = value
            gridMotion.motion = value
        }

    /** Lift, make-room and drop motion while an entry is dragged inside the panel. */
    private val reorder = FolderReorderMotion()

    /** Page slide and entry add/remove/move motion when the grid's contents change. */
    private val gridMotion = FolderGridMotion()

    /** Views of the entries on the shown page, by entry id; kept across updates so only changes animate. */
    private class EntryHolder(val view: AppIconView, var entry: FolderEntryUi, var actionIds: List<Int> = emptyList())
    private var entryHolders = LinkedHashMap<String, EntryHolder>()

    /** Style accent for the name field's cursor and selection; null uses Chikku's default. */
    var accentColor: Int? = null
        set(value) {
            if (field == value) return
            field = value
            restyleOpenPanel()
        }

    /** Theme colours are read only when the panel is built, so an open panel is rebuilt when they change. */
    private fun restyleOpenPanel() {
        val item = folder ?: return
        val current = settings ?: return
        commitRename(item)
        hideKeyboard()
        removeViews()
        build(item, current)
    }

    val openFolderId: String? get() = folder?.id
    val isOpen: Boolean get() = folder != null

    fun open(item: HomeItemUi, folderSettings: FolderSettings, anchor: View? = null) {
        if (item.content !is ItemContent.Folder) return
        // The previous folder fades out beneath the new one rather than cutting away.
        if (isOpen) close(animate = true)
        anchorRect = anchor?.takeIf { it.isAttachedToWindow }?.let(::boundsInRoot)
        currentPage = 0
        folder = item
        settings = folderSettings
        build(item, folderSettings)
        listener.onOpenChanged(true)
    }

    /** New state for the open folder (entries, title or settings changed). */
    fun update(item: HomeItemUi, folderSettings: FolderSettings) {
        if (item.id != folder?.id || (item == folder && folderSettings == settings)) return
        val styleChanged = folderSettings != settings
        if (styleChanged) {
            folder?.let(::commitRename)
            hideKeyboard()
        }
        folder = item
        settings = folderSettings
        if (styleChanged) {
            removeViews()
            build(item, folderSettings)
            return
        }
        val content = item.content as? ItemContent.Folder ?: return
        title?.let { field -> if (!field.hasFocus() && field.text.toString() != content.title) field.setText(content.title) }
        ViewCompat.setAccessibilityPaneTitle(checkNotNull(panel), label(content))
        bindEntries(item, content, animate = true)
    }

    /**
     * Closes the folder, saving a changed name first. [animate] shrinks it back the way it opened
     * (Back, an outside tap or the close button); launches, drags and HOME close it at once.
     */
    fun close(animate: Boolean = false) {
        val item = folder ?: return
        commitRename(item)
        hideKeyboard()
        val closingPanel = panel
        val closingScrim = scrim
        val duration = if (animate) motion.duration(CLOSE_DURATION_MS) else 0L
        if (duration > 0L && closingPanel != null && closingScrim != null) {
            removeViews(keepOnScreen = true)
            animateOut(closingPanel, closingScrim, duration)
        } else {
            removeViews()
        }
        backProgress = 0f
        folder = null
        settings = null
        anchorRect = null
        currentPage = 0
        listener.onOpenChanged(false)
    }

    /**
     * Follows a predictive back swipe: the panel shrinks a little and leans toward the swipe's
     * edge, so it is clear what letting go will close. [progress] 0 restores it.
     */
    fun setBackProgress(progress: Float, fromLeftEdge: Boolean) {
        val panelView = panel ?: return
        backProgress = progress.coerceIn(0f, 1f)
        val scale = 1f - BACK_SCALE_DROP * backProgress
        panelView.animate().cancel()
        panelView.scaleX = scale
        panelView.scaleY = scale
        val lean = BACK_LEAN_DP * res.displayMetrics.density * backProgress
        panelView.translationX = if (fromLeftEdge) lean else -lean
    }

    /** The back swipe was abandoned; settle the panel back in place. */
    fun cancelBackProgress() {
        val panelView = panel ?: return
        backProgress = 0f
        panelView.animate().scaleX(1f).scaleY(1f).translationX(0f)
            .setDuration(motion.duration(CLOSE_DURATION_MS)).start()
    }

    private var backProgress = 0f

    /** Fades [panelView] and [scrimView] out, shrinking toward where the folder opened from, then removes them. */
    private fun animateOut(panelView: View, scrimView: View, duration: Long) {
        // The closing panel is no longer this folder: swallow touches so nothing in it launches.
        blockTouches(panelView)
        scrimView.setOnTouchListener { _, _ -> true }
        panelView.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        panelView.animate().cancel()
        panelView.animate().alpha(0f).scaleX(closeScale).scaleY(closeScale).setDuration(duration)
            .setInterpolator(AccelerateInterpolator(DECELERATE_FACTOR))
            .withEndAction { root.removeView(panelView) }.start()
        scrimView.animate().alpha(0f).setDuration(duration).withEndAction { root.removeView(scrimView) }.start()
    }

    private fun blockTouches(view: View) {
        view.setOnTouchListener { _, _ -> true }
        if (view is ViewGroup) for (i in 0 until view.childCount) blockTouches(view.getChildAt(i))
    }

    /** Scale a closing panel shrinks to: back into its icon, or slightly from the centre. */
    private var closeScale = OPEN_SCALE

    private fun removeViews(keepOnScreen: Boolean = false) {
        root.removeOnLayoutChangeListener(rootLayoutListener)
        reorder.clear()
        gridMotion.cancelAll()
        entryHolders = LinkedHashMap()
        grid?.let { g -> for (i in 0 until g.childCount) (g.getChildAt(i) as? AppIconView)?.let(iconBinder::cancel) }
        if (!keepOnScreen) {
            scrim?.let(root::removeView)
            panel?.let(root::removeView)
        }
        scrim = null
        panel = null
        title = null
        grid = null
        pageControls = null
        pageIndicator = null
        previousPage = null
        nextPage = null
        pageViewport = null
        entryScroller = null
    }

    private fun build(item: HomeItemUi, folderSettings: FolderSettings) {
        val content = item.content as ItemContent.Folder
        val padding = res.getDimensionPixelSize(R.dimen.home_panel_padding)
        val margin = res.getDimensionPixelSize(R.dimen.home_panel_margin)
        val immersive = folderSettings.windowStyle == FolderWindowStyle.IMMERSIVE
        ViewCompat.getRootWindowInsets(root)?.let { safeInsets = it.getInsets(SAFE_INSET_TYPES) }
        val panelInsets = panelInsets()

        // A tonal panel: the theme colour with a hint of the wallpaper keeps icon tiles (often the
        // same neutral as the theme surface) distinct from the panel. A chosen colour is kept as is.
        val base = folderSettings.backgroundColor ?: (themePanelColor ?: ContextCompat.getColor(context, R.color.folder_panel_background))
            .let { themed -> wallpaperTint?.let { tonal(themed, it) } ?: themed }
        val alpha = folderSettings.backgroundAlpha.coerceIn(0f, 1f)
        val panelColor = FolderPanelRules.panelColor(base, alpha)
        // Labels follow the panel: light text on dark or see-through panels, dark text on light ones.
        val dark = FolderPanelRules.isDark(base, alpha)
        val panelContext = nightContext(dark)
        val iconStyle = FolderPanelRules.iconStyle(alpha)
        val textColor = ContextCompat.getColor(panelContext, R.color.chikku_on_drawer)

        val touchTarget = res.getDimensionPixelSize(R.dimen.home_min_touch_target)
        val scrimView = View(context).apply {
            setBackgroundColor(ContextCompat.getColor(context, R.color.folder_scrim))
            contentDescription = context.getString(R.string.folder_close)
            isFocusable = true
            setOnClickListener { close(animate = true) }
        }

        val nameField = NoDragEditText(panelContext).apply {
            visibility = if (folderSettings.showName) View.VISIBLE else View.GONE
            setText(content.title)
            hint = context.getString(R.string.folder_name_hint)
            setHintTextColor(ColorUtils.setAlphaComponent(textColor, HINT_ALPHA))
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, res.getDimension(R.dimen.folder_title_text_size))
            gravity = if (immersive) Gravity.START or Gravity.CENTER_VERTICAL else Gravity.CENTER
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_DONE
            background = null
            applyAccent(accentColor ?: CHIKKU_DEFAULT_ACCENT)
            minHeight = res.getDimensionPixelSize(R.dimen.home_min_touch_target)
            setOnEditorActionListener { v, actionId, event ->
                val done = actionId == EditorInfo.IME_ACTION_DONE ||
                    (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
                if (done) {
                    folder?.let(::commitRename)
                    hideKeyboard()
                    v.clearFocus()
                }
                done
            }
        }
        // A quiet icon action, so it does not compete with the folder name.
        val editButton = ImageButton(panelContext, null, 0, R.style.Widget_Chikku_Button_Borderless).apply {
            setImageResource(R.drawable.ic_menu_more)
            scaleType = android.widget.ImageView.ScaleType.CENTER
            imageTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(textColor, SECONDARY_ALPHA))
            contentDescription = context.getString(R.string.folder_action_edit)
            minimumWidth = touchTarget
            minimumHeight = touchTarget
            setPadding(0, 0, 0, 0)
            setOnClickListener { folder?.let(listener::onEdit) }
        }
        val closeButton = Button(panelContext, null, 0, R.style.Widget_Chikku_Button_Borderless).apply {
            text = context.getString(R.string.folder_close_button)
            contentDescription = context.getString(R.string.folder_close)
            isAllCaps = false
            setTextColor(textColor)
            minHeight = res.getDimensionPixelSize(R.dimen.home_min_touch_target)
            setOnClickListener { close(animate = true) }
        }
        // Floating: a footer with the name centred on the panel (a leading spacer balances the edit
        // button). Immersive: a top bar with the name at the start and a close button.
        val header = LinearLayout(panelContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            if (!immersive) addView(View(panelContext), LinearLayout.LayoutParams(touchTarget, touchTarget))
            addView(nameField, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(editButton, LinearLayout.LayoutParams(touchTarget, touchTarget))
            if (immersive) addView(closeButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        val cellHeight = FolderPanelRules.cellHeight(panelContext, iconStyle, folderSettings)
        val cellWidth = FolderPanelRules.cellWidth(res, folderSettings, padding)
        val gridView = FolderGridLayout(panelContext, cellWidth, cellHeight)
        val availableHeight = safeHeight()
        val horizontal = folderSettings.scrollStyle == FolderScrollStyle.HORIZONTAL_PAGED
        val availableWidth = safeWidth() - 2 * margin - 2 * padding
        stackedPager = availableWidth < 3 * res.getDimensionPixelSize(R.dimen.home_min_touch_target)
        pagingCellHeight = cellHeight
        val viewport = if (horizontal) FolderSwipeViewport(panelContext, ::changePage).apply {
            addView(gridView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        } else null
        val scroller = MaxHeightScrollView(panelContext, maxHeightPx = (availableHeight * if (immersive) 1f else MAX_HEIGHT_FRACTION).toInt()).apply {
            isFillViewport = false
            addView(viewport ?: gridView)
        }
        val pager = if (horizontal) LinearLayout(panelContext).apply {
            orientation = if (stackedPager) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            fun navigationButton(textId: Int, descriptionId: Int, direction: Int) = Button(panelContext, null, 0, R.style.Widget_Chikku_Button_Borderless).apply {
                setText(textId)
                contentDescription = context.getString(descriptionId)
                isAllCaps = false
                setTextColor(textColor)
                minHeight = res.getDimensionPixelSize(R.dimen.home_min_touch_target)
                setOnClickListener { changePage(direction) }
            }
            val previous = navigationButton(R.string.folder_page_previous, R.string.folder_page_previous_description, -1)
            val indicator = TextView(panelContext).apply {
                setTextColor(textColor)
                gravity = Gravity.CENTER
                maxLines = 2
                minHeight = res.getDimensionPixelSize(R.dimen.home_min_touch_target)
                ViewCompat.setAccessibilityLiveRegion(this, ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE)
            }
            val next = navigationButton(R.string.folder_page_next, R.string.folder_page_next_description, 1)
            fun controlParams() = if (stackedPager) {
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            } else LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            addView(previous, controlParams())
            addView(indicator, controlParams())
            addView(next, controlParams())
            previousPage = previous
            pageIndicator = indicator
            nextPage = next
        } else null
        val emptyText = TextView(panelContext).apply {
            setText(R.string.folder_empty)
            setTextColor(textColor)
            gravity = Gravity.CENTER
            setPadding(padding, padding, padding, padding)
        }

        val panelView = LinearLayout(panelContext).apply {
            FolderPanelRules.styleFrame(this, res, folderSettings, immersive, padding, panelColor, alpha, textColor)
            isClickable = true // taps on the panel's own area do not close it
            if (immersive) addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(scroller, if (immersive) {
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            } else {
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            })
            pager?.let { addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)) }
            addView(emptyText, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            if (!immersive) addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            setOnDragListener(::onPanelDrag)
            if (!immersive) addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ -> placeNearAnchor(v) }
        }
        ViewCompat.setAccessibilityPaneTitle(panelView, label(content))
        ViewCompat.setOnApplyWindowInsetsListener(panelView) { _, insets ->
            refreshGeometry(insets)
            insets
        }
        emptyText.tag = EMPTY_TAG

        root.addView(scrimView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(
            panelView,
            FrameLayout.LayoutParams(
                if (immersive) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT,
                if (immersive) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT,
                if (immersive) Gravity.FILL else Gravity.CENTER,
            )
                .apply { setMargins(margin + panelInsets.left, margin + panelInsets.top,
                    margin + panelInsets.right, margin + panelInsets.bottom) },
        )
        scrim = scrimView
        panel = panelView
        title = nameField
        grid = gridView
        pageViewport = viewport
        entryScroller = scroller
        pageControls = pager
        iconStyleInUse = iconStyle
        refreshGeometry(forceBind = true)
        root.addOnLayoutChangeListener(rootLayoutListener)
        ViewCompat.requestApplyInsets(panelView)
        val duration = motion.duration(OPEN_DURATION_MS)
        if (duration > 0L) {
            // Grows out of the folder icon (pivot set in placeNearAnchor), or from the centre.
            val scale = if (anchorRect != null && !immersive) OPEN_SCALE_FROM_ICON else OPEN_SCALE
            closeScale = scale
            panelView.alpha = 0f
            panelView.scaleX = scale
            panelView.scaleY = scale
            panelView.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(duration)
                .setInterpolator(DecelerateInterpolator(DECELERATE_FACTOR)).start()
            scrimView.alpha = 0f
            scrimView.animate().alpha(1f).setDuration(duration).start()
        }
        panelView.post { panelView.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED) }
    }

    private var iconStyleInUse = AppIconView.Style.DRAWER

    /**
     * Shows the entries of the current page. Views of entries that are still there are kept and
     * updated in place (only a changed entry is rebound), so a title or icon update never restarts
     * running motion. With [animate], added and removed entries and moved cells play
     * [FolderGridMotion]; a non-zero [pageDirection] slides the old page out and the new one in.
     */
    private fun bindEntries(item: HomeItemUi, content: ItemContent.Folder, animate: Boolean = false, pageDirection: Int = 0) {
        val gridView = grid ?: return
        val panelView = panel ?: return
        panelView.findViewWithTag<View>(EMPTY_TAG)?.visibility = if (content.entries.isEmpty()) View.VISIBLE else View.GONE
        val appearance = settings ?: FolderSettings()
        val entries = displayFolderEntries(content.entries, appearance.sortOrder)
        val manualIds = content.entries.map { it.id }
        val horizontal = appearance.scrollStyle == FolderScrollStyle.HORIZONTAL_PAGED
        if (horizontal) {
            fitPagedGrid(appearance, entries.size)
            pageWindow = folderPageWindow(entries.size, pagedColumns, pagedRows, currentPage)
            currentPage = pageWindow.page
            gridView.columns = pagedColumns
            pageControls?.visibility = if (entries.isEmpty()) View.GONE else View.VISIBLE
            pageIndicator?.text = context.getString(R.string.folder_page_indicator, currentPage + 1, pageWindow.pages)
            previousPage?.isEnabled = currentPage > 0
            nextPage?.isEnabled = currentPage + 1 < pageWindow.pages
            previousPage?.alpha = if (currentPage > 0) 1f else 0.38f
            nextPage?.alpha = if (currentPage + 1 < pageWindow.pages) 1f else 0.38f
            pageViewport?.minimumHeight = pagedRows * pagingCellHeight
        } else {
            pageWindow = FolderPageWindow(0, 1, 0, entries.size)
            gridView.columns = if (appearance.columns == 0) columnsFor(entries.size) else appearance.columns.coerceIn(1, 6)
        }
        entryScroller?.visibility = if (entries.isEmpty()) View.GONE else View.VISIBLE
        val shown = entries.subList(pageWindow.start, pageWindow.end)
        val animated = animate && gridMotion.canAnimate(gridView)
        gridMotion.finishStale(gridView)
        val before = if (animated) gridMotion.capture(gridView) else emptyMap()

        // Diff by entry id: a page change replaces every view, an entry change only the ones that differ.
        val reusable = if (pageDirection != 0) emptyMap() else entryHolders
        val shownIds = shown.mapTo(HashSet()) { it.id }
        val gone = entryHolders.values.filter { pageDirection != 0 || it.entry.id !in shownIds }
        gone.forEach { iconBinder.cancel(it.view) }
        gridMotion.leave(gridView, gone.map { it.view }, pageDirection, animated)

        val next = LinkedHashMap<String, EntryHolder>()
        val entered = ArrayList<View>()
        shown.forEachIndexed { index, entry ->
            val holder = reusable[entry.id]?.also { if (it.entry != entry) { it.entry = entry; bindEntry(it.view, entry) } }
                ?: createEntryHolder(entry, appearance).also { created ->
                    entered += created.view
                    if (animated) gridMotion.prepareEnter(gridView, created.view, pageDirection)
                }
            next[entry.id] = holder
            if (gridView.getChildAt(index) !== holder.view) {
                if (holder.view.parent === gridView) gridView.removeView(holder.view)
                gridView.addView(holder.view, index)
            }
            refreshAccessibilityActions(holder, item.id, manualIds, pageWindow.start + index)
        }
        entryHolders = next
        val handled = reorder.bind(gridView, shown.map { it.id })
        gridMotion.play(gridView, before, entered, handled?.let { next[it]?.view }, animated)
    }

    private fun createEntryHolder(entry: FolderEntryUi, appearance: FolderSettings): EntryHolder {
        val view = AppIconView(checkNotNull(grid).context, iconStyleInUse).apply {
            IconLabelStyle.folder(appearance).applyTo(this)
        }
        val holder = EntryHolder(view, entry)
        bindEntry(view, entry)
        // The listeners read the holder, so a reused view always acts on its current entry.
        view.setOnClickListener { v -> folder?.let { listener.onEntryClick(it, holder.entry, v) } }
        // Long-press opens the app menu; moving the finger afterwards closes it and starts the drag.
        LongPressMenuDrag.attach(
            view,
            showMenu = { v -> folder?.let { listener.onEntryMenu(it, holder.entry, v) } },
            startDrag = { v -> folder?.let { listener.onEntryLongClick(it, holder.entry, v) } ?: false },
        )
        return holder
    }

    private fun refreshAccessibilityActions(holder: EntryHolder, folderId: String, manualIds: List<String>, index: Int) {
        holder.actionIds.forEach { ViewCompat.removeAccessibilityAction(holder.view, it) }
        val ids = ArrayList<Int>()
        ids += ViewCompat.addAccessibilityAction(holder.view, context.getString(R.string.app_menu_open)) { v, _ ->
            folder?.let { listener.onEntryMenu(it, holder.entry, v) } != null
        }
        ids += addEntryAccessibilityActions(holder.view, folderId, manualIds, holder.entry.id, index)
        holder.actionIds = ids
    }

    private fun fitPagedGrid(appearance: FolderSettings, count: Int) {
        val touch = res.getDimensionPixelSize(R.dimen.home_min_touch_target)
        val margin = res.getDimensionPixelSize(R.dimen.home_panel_margin)
        val padding = res.getDimensionPixelSize(R.dimen.home_panel_padding)
        val width = safeWidth() - 2 * margin - 2 * padding
        val requestedColumns = if (appearance.columns == 0) columnsFor(count) else appearance.columns.coerceIn(1, 6)
        pagedColumns = min(requestedColumns, (width / touch).coerceAtLeast(1))
        val height = safeHeight()
        val budget = (height * if (appearance.windowStyle == FolderWindowStyle.IMMERSIVE) 1f else MAX_HEIGHT_FRACTION).toInt()
        val controlsHeight = ((if (stackedPager) 4 else 2) * touch * res.configuration.fontScale.coerceAtLeast(1f)).toInt()
        val pageHeight = (budget - controlsHeight - 2 * margin - 2 * padding).coerceAtLeast(pagingCellHeight)
        pagedRows = min(appearance.rows.coerceIn(1, 6), (pageHeight / pagingCellHeight).coerceAtLeast(1))
    }

    /**
     * Moves the centred floating panel over its folder icon, kept inside the safe area, and pivots
     * the open animation on the icon. Re-run on every panel layout (rotation, insets, new entries).
     */
    private fun placeNearAnchor(panelView: View) {
        val anchor = anchorRect ?: return
        val margin = res.getDimensionPixelSize(R.dimen.home_panel_margin)
        val width = panelView.width
        val height = panelView.height
        val left = clampStart(anchor.centerX() - width / 2, margin + safeInsets.left, root.width - margin - safeInsets.right - width)
        val top = clampStart(anchor.centerY() - height / 2, margin + safeInsets.top, root.height - margin - safeInsets.bottom - height)
        panelView.translationX = (left - panelView.left).toFloat()
        panelView.translationY = (top - panelView.top).toFloat()
        panelView.pivotX = (anchor.centerX() - left).coerceIn(0, width).toFloat()
        panelView.pivotY = (anchor.centerY() - top).coerceIn(0, height).toFloat()
    }

    /**
     * [surface] recoloured with [tint]'s hue: lightness stays the theme's (so text contrast holds),
     * saturation is capped so the panel reads as a tinted surface, not a colour block. A grey
     * wallpaper leaves the surface neutral.
     */
    private fun tonal(surface: Int, tint: Int): Int {
        val base = FloatArray(3).also { ColorUtils.colorToHSL(surface, it) }
        val hue = FloatArray(3).also { ColorUtils.colorToHSL(tint, it) }
        val saturation = min(hue[1], MAX_TINT_SATURATION)
        // A dark surface is lifted slightly so tinted icon tiles of the same neutral stand apart.
        val lightness = if (base[2] < 0.5f) min(base[2] + DARK_LIFT, 1f) else base[2]
        return ColorUtils.setAlphaComponent(ColorUtils.HSLToColor(floatArrayOf(hue[0], saturation, lightness)), Color.alpha(surface))
    }

    /** [value] within [min]..[max]; [min] wins when the panel is larger than the space. */
    private fun clampStart(value: Int, min: Int, max: Int): Int = if (max < min) min else value.coerceIn(min, max)

    private fun boundsInRoot(view: View): Rect {
        val v = IntArray(2).also(view::getLocationInWindow)
        val r = IntArray(2).also(root::getLocationInWindow)
        val left = v[0] - r[0]
        val top = v[1] - r[1]
        return Rect(left, top, left + view.width, top + view.height)
    }

    private fun panelInsets(): Insets {
        if (settings?.windowStyle == FolderWindowStyle.IMMERSIVE) return safeInsets
        // FrameLayout.CENTER applies margin differences as offsets. Equal opposite margins keep
        // a floating panel centred while reserving both asymmetric bars/cutouts safely.
        val horizontal = max(safeInsets.left, safeInsets.right)
        val vertical = max(safeInsets.top, safeInsets.bottom)
        return Insets.of(horizontal, vertical, horizontal, vertical)
    }

    private fun safeWidth(): Int {
        val insets = panelInsets()
        return ((root.width.takeIf { it > 0 } ?: res.displayMetrics.widthPixels) - insets.left - insets.right).coerceAtLeast(1)
    }

    private fun safeHeight(): Int {
        val insets = panelInsets()
        return ((root.height.takeIf { it > 0 } ?: res.displayMetrics.heightPixels) - insets.top - insets.bottom).coerceAtLeast(1)
    }

    /** Own the panel's inset handling; HOME's root listener still owns workspace/drawer padding. */
    private fun refreshGeometry(insets: WindowInsetsCompat? = ViewCompat.getRootWindowInsets(root), forceBind: Boolean = false) {
        val nextInsets = insets?.getInsets(SAFE_INSET_TYPES) ?: safeInsets
        val changed = nextInsets != safeInsets || geometryWidth != root.width || geometryHeight != root.height
        safeInsets = nextInsets
        geometryWidth = root.width
        geometryHeight = root.height
        val panelView = panel ?: return
        val panelInsets = panelInsets()
        val margin = res.getDimensionPixelSize(R.dimen.home_panel_margin)
        val params = panelView.layoutParams as FrameLayout.LayoutParams
        val left = margin + panelInsets.left
        val top = margin + panelInsets.top
        val right = margin + panelInsets.right
        val bottom = margin + panelInsets.bottom
        if (params.leftMargin != left || params.topMargin != top || params.rightMargin != right || params.bottomMargin != bottom) {
            params.setMargins(left, top, right, bottom)
            panelView.layoutParams = params
        }
        val appearance = settings ?: return
        (entryScroller as? MaxHeightScrollView)?.maxHeightPx =
            (safeHeight() * if (appearance.windowStyle == FolderWindowStyle.IMMERSIVE) 1f else MAX_HEIGHT_FRACTION).toInt()
        pageControls?.let { controls ->
            val padding = res.getDimensionPixelSize(R.dimen.home_panel_padding)
            val stack = safeWidth() - 2 * margin - 2 * padding < 3 * res.getDimensionPixelSize(R.dimen.home_min_touch_target)
            if (stack != stackedPager) {
                stackedPager = stack
                controls.orientation = if (stack) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
                for (index in 0 until controls.childCount) controls.getChildAt(index).layoutParams = if (stack) {
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                } else LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
        }
        if (changed || forceBind) {
            val item = folder ?: return
            val content = item.content as? ItemContent.Folder ?: return
            bindEntries(item, content)
        }
    }

    private fun changePage(direction: Int) {
        if (settings?.scrollStyle != FolderScrollStyle.HORIZONTAL_PAGED) return
        val target = (currentPage + direction).coerceIn(0, pageWindow.pages - 1)
        if (target == currentPage) return
        val forward = if (target > currentPage) 1 else -1
        currentPage = target
        val item = folder ?: return
        val content = item.content as? ItemContent.Folder ?: return
        bindEntries(item, content, animate = true, pageDirection = forward)
    }

    private fun bindEntry(view: AppIconView, entry: FolderEntryUi) {
        when (val c = entry.content) {
            is ItemContent.App -> iconBinder.bindApp(view, c.key, c.label, c.isWork, c.availability)
            is ItemContent.Shortcut -> iconBinder.bindShortcut(view, c)
            is ItemContent.Folder, is ItemContent.Widget -> Unit // folders hold apps and shortcuts only
        }
    }

    private fun addEntryAccessibilityActions(view: View, folderId: String, ids: List<String>, entryId: String, index: Int): List<Int> {
        val canReorder = settings?.sortOrder != FolderSortOrder.ALPHABETICAL
        val added = ArrayList<Int>()
        if (canReorder && index > 0) {
            added += ViewCompat.addAccessibilityAction(view, context.getString(R.string.folder_action_move_earlier)) { _, _ ->
                listener.onReorder(folderId, ids.moved(index, index - 1))
                true
            }
        }
        if (canReorder && index < ids.lastIndex) {
            added += ViewCompat.addAccessibilityAction(view, context.getString(R.string.folder_action_move_later)) { _, _ ->
                listener.onReorder(folderId, ids.moved(index, index + 1))
                true
            }
        }
        added += ViewCompat.addAccessibilityAction(view, context.getString(R.string.folder_action_move_to_home)) { _, _ ->
            listener.onMoveToHome(folderId, entryId)
            true
        }
        added += ViewCompat.addAccessibilityAction(view, context.getString(R.string.folder_action_remove_entry)) { _, _ ->
            listener.onRemoveEntry(folderId, entryId)
            true
        }
        return added
    }

    // --- Drag within the panel -------------------------------------------------------------------

    private fun onPanelDrag(view: View, event: DragEvent): Boolean {
        val payload = event.localState as? DragPayload.FromFolder ?: return false
        if (payload.folderId != folder?.id) return false
        val manual = settings?.sortOrder != FolderSortOrder.ALPHABETICAL
        when (event.action) {
            DragEvent.ACTION_DRAG_STARTED -> reorder.start(payload.entryId, manual)
            DragEvent.ACTION_DRAG_LOCATION -> reorder.hover(cellAt(view, event.x, event.y))
            // Leaving the panel closes the folder so the workspace below takes the drop. Removing
            // views is posted: the hierarchy must not change while this event is dispatched.
            DragEvent.ACTION_DRAG_EXITED -> view.post { if (folder?.id == payload.folderId) close(animate = true) }
            DragEvent.ACTION_DROP -> {
                val content = folder?.content as? ItemContent.Folder
                val ids = content?.entries?.map { it.id }.orEmpty()
                val from = ids.indexOf(payload.entryId)
                // The entry lands in the cell the others made room for; letting go outside the grid keeps that cell.
                val cell = reorder.drop(cellAt(view, event.x, event.y))
                if (!manual || content == null || from < 0) return true
                val target = if (from in pageWindow.start until pageWindow.end) {
                    cell?.let { pageWindow.start + it } ?: return true
                } else {
                    dropIndex(view, event.x, event.y, ids.size) ?: return true
                }
                if (target != from) {
                    Haptics.drop(view)
                    listener.onReorder(payload.folderId, ids.moved(from, target))
                }
            }
            DragEvent.ACTION_DRAG_ENDED -> reorder.end()
        }
        return true
    }

    /** Cell of this page's grid under ([x], [y]) in [panelView] coordinates; null outside the entries. */
    private fun cellAt(panelView: View, x: Float, y: Float): Int? {
        val gridView = grid ?: return null
        val (gridX, gridY) = gridPoint(panelView, gridView, x, y)
        if (gridX < 0 || gridY < 0 || gridX >= gridView.width || gridY >= gridView.height || gridView.childCount == 0) return null
        return gridView.indexAt(gridX, gridY).coerceIn(0, gridView.childCount - 1)
    }

    private fun gridPoint(panelView: View, gridView: View, x: Float, y: Float): Pair<Float, Float> {
        val p = IntArray(2).also(panelView::getLocationInWindow)
        val g = IntArray(2).also(gridView::getLocationInWindow)
        return (x - (g[0] - p[0])) to (y - (g[1] - p[1]))
    }

    /** Index of the grid cell under ([x], [y]) in [panelView] coordinates, clamped to the entries. */
    private fun dropIndex(panelView: View, x: Float, y: Float, size: Int): Int? {
        val gridView = grid ?: return size - 1
        val (gridX, gridY) = gridPoint(panelView, gridView, x, y)
        if (settings?.scrollStyle == FolderScrollStyle.HORIZONTAL_PAGED) {
            // Header/footer drops are inert, and an incomplete page cannot target an invisible entry.
            if (gridX < 0 || gridY < 0 || gridX >= gridView.width || gridY >= gridView.height || gridView.childCount == 0) return null
            val local = gridView.indexAt(gridX, gridY).coerceIn(0, gridView.childCount - 1)
            return (pageWindow.start + local).coerceAtMost(size - 1)
        }
        return gridView.indexAt(gridX, gridY).coerceIn(0, max(0, size - 1))
    }

    // --- Helpers ---------------------------------------------------------------------------------

    private fun commitRename(item: HomeItemUi) {
        val field = title ?: return
        val content = item.content as? ItemContent.Folder ?: return
        val newTitle = field.text.toString().trim()
        if (newTitle != content.title) listener.onRename(item.id, newTitle)
    }

    private fun hideKeyboard() {
        val field = title ?: return
        context.getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(field.windowToken, 0)
        field.clearFocus()
    }

    private fun label(content: ItemContent.Folder) =
        content.title.ifBlank { context.getString(R.string.home_folder_unnamed) }

    private fun nightContext(night: Boolean): Context {
        val config = Configuration(res.configuration)
        config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
            (if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
        // A configuration context alone gets the platform default theme (DeviceDefault, i.e. the
        // device skin); keep HOME's Chikku theme on the folder's views.
        return ContextThemeWrapper(context, R.style.Theme_Chikku_Home).apply { applyOverrideConfiguration(config) }
    }

    companion object {
        private const val ALPHA_MAX = 255
        private const val HINT_ALPHA = 140
        private const val MAX_HEIGHT_FRACTION = 0.6f
        private const val SECONDARY_ALPHA = 200
        private const val MAX_TINT_SATURATION = 0.32f
        private const val DARK_LIFT = 0.04f
        private const val OPEN_SCALE = 0.92f
        private const val OPEN_SCALE_FROM_ICON = 0.5f
        private const val DECELERATE_FACTOR = 1.5f
        private const val OPEN_DURATION_MS = 200L
        private const val CLOSE_DURATION_MS = 160L
        /** How far a full back swipe shrinks the panel and moves it toward the swipe's edge. */
        private const val BACK_SCALE_DROP = 0.1f
        private const val BACK_LEAN_DP = 8f
        private const val EMPTY_TAG = "folder_empty"
        private const val MAX_COLUMNS = 4
        private val SAFE_INSET_TYPES = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()

        /** Roughly square grid, at most four columns. */
        fun columnsFor(count: Int): Int = ceil(sqrt(count.toDouble())).toInt().coerceIn(1, MAX_COLUMNS)

        /** [this] with the element at [from] moved to [to]. */
        fun <T> List<T>.moved(from: Int, to: Int): List<T> =
            toMutableList().apply { add(to.coerceIn(0, size - 1), removeAt(from)) }
    }
}

/** A name field that never accepts drag-and-drop, so a dragged entry cannot be dropped into it as text. */
@SuppressLint("ViewConstructor", "AppCompatCustomView")
private class NoDragEditText(context: Context) : EditText(context) {
    override fun onDragEvent(event: DragEvent?): Boolean = false
}

/**
 * Cursor, selection and select handles in [accent] (the effective Style accent), so the
 * folder name field never shows the device skin's or the theme's default accent.
 */
internal fun EditText.applyAccent(accent: Int) {
    val opaque = ColorUtils.setAlphaComponent(accent, 255)
    highlightColor = ColorUtils.setAlphaComponent(opaque, SELECTION_ALPHA)
    textCursorDrawable = GradientDrawable().apply {
        setColor(opaque)
        setSize((CURSOR_WIDTH_DP * resources.displayMetrics.density).toInt().coerceAtLeast(1), 0)
    }
    fun Drawable?.tinted(): Drawable? = this?.mutate()?.apply { setTint(opaque) }
    textSelectHandle?.tinted()?.let { setTextSelectHandle(it) }
    textSelectHandleLeft?.tinted()?.let { setTextSelectHandleLeft(it) }
    textSelectHandleRight?.tinted()?.let { setTextSelectHandleRight(it) }
}

private const val SELECTION_ALPHA = 0x66
private const val CURSOR_WIDTH_DP = 2f

/** Equal cells in [columns] columns, row by row; shrinks cells to fit a narrow parent. */
@SuppressLint("ViewConstructor")
internal class FolderGridLayout(context: Context, private val cellWidth: Int, private val cellHeight: Int) : ViewGroup(context) {
    var columns: Int = 1
        set(value) {
            field = value.coerceAtLeast(1)
            requestLayout()
        }

    private var actualCellWidth = cellWidth
    private var layoutColumns = columns
    private val minCellWidth = resources.getDimensionPixelSize(R.dimen.home_min_touch_target)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = resolveSize(columns * cellWidth, widthMeasureSpec)
        layoutColumns = min(columns, (width / minCellWidth).coerceAtLeast(1))
        actualCellWidth = width / layoutColumns
        val rows = (childCount + layoutColumns - 1) / layoutColumns
        val cw = MeasureSpec.makeMeasureSpec(actualCellWidth, MeasureSpec.EXACTLY)
        val ch = MeasureSpec.makeMeasureSpec(cellHeight, MeasureSpec.EXACTLY)
        for (i in 0 until childCount) getChildAt(i).measure(cw, ch)
        setMeasuredDimension(width, rows * cellHeight)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        for (i in 0 until childCount) {
            val col = i % layoutColumns
            val x = (if (rtl) layoutColumns - 1 - col else col) * actualCellWidth
            val y = (i / layoutColumns) * cellHeight
            getChildAt(i).layout(x, y, x + actualCellWidth, y + cellHeight)
        }
    }

    /** Child index for a point in this view's coordinates (may be past the last child). */
    fun indexAt(x: Float, y: Float): Int {
        if (actualCellWidth <= 0 || cellHeight <= 0) return 0
        val rawCol = (x / actualCellWidth).toInt().coerceIn(0, layoutColumns - 1)
        val col = if (layoutDirection == LAYOUT_DIRECTION_RTL) layoutColumns - 1 - rawCol else rawCol
        val row = (y / cellHeight).toInt().coerceAtLeast(0)
        return row * layoutColumns + col
    }
}

/** ScrollView capped at [maxHeightPx] so a large folder scrolls instead of covering the screen. */
@SuppressLint("ViewConstructor")
private class MaxHeightScrollView(context: Context, maxHeightPx: Int) : ScrollView(context) {
    var maxHeightPx: Int = maxHeightPx
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
        }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val capped = if (maxHeightPx > 0) {
            val available = MeasureSpec.getSize(heightMeasureSpec)
            val limit = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) maxHeightPx else min(available, maxHeightPx)
            MeasureSpec.makeMeasureSpec(limit, MeasureSpec.AT_MOST)
        } else {
            heightMeasureSpec
        }
        super.onMeasure(widthMeasureSpec, capped)
    }
}

/** Sorting is a presentation preference; the recoverable manual order stays in workspace storage. */
internal fun displayFolderEntries(entries: List<FolderEntryUi>, order: FolderSortOrder): List<FolderEntryUi> {
    if (order == FolderSortOrder.MANUAL) return entries
    val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
    fun label(entry: FolderEntryUi): String = when (val content = entry.content) {
        is ItemContent.App -> content.label.orEmpty()
        is ItemContent.Shortcut -> content.label.orEmpty()
        is ItemContent.Folder -> content.title
        is ItemContent.Widget -> ""
    }
    return entries.sortedWith { a, b -> collator.compare(label(a), label(b)) }
}
