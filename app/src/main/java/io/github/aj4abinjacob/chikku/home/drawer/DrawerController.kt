package io.github.aj4abinjacob.chikku.home.drawer

import io.github.aj4abinjacob.chikku.home.applyPageTransform
import io.github.aj4abinjacob.chikku.theme.Motion
import io.github.aj4abinjacob.chikku.core.model.settings.PageIndicatorStyle

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.doOnNextLayout
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.DrawerGroup
import io.github.aj4abinjacob.chikku.core.model.GestureAction
import io.github.aj4abinjacob.chikku.core.model.LauncherApp
import io.github.aj4abinjacob.chikku.core.model.drawer.DrawerModel
import io.github.aj4abinjacob.chikku.core.model.drawer.DrawerSection
import io.github.aj4abinjacob.chikku.core.model.drawer.DrawerTab
import io.github.aj4abinjacob.chikku.core.model.drawer.DrawerTabKind
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerSearchBarPosition
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerSearchBarStyle
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerSettings
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerFastScrollerSide
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerFastScrollerStyle
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerTabStyle
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerTabBarPosition
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerStyle
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.home.AppIconView
import io.github.aj4abinjacob.chikku.home.DrawerAdapter
import io.github.aj4abinjacob.chikku.home.DrawerAppearance
import io.github.aj4abinjacob.chikku.home.DrawerItemListener
import io.github.aj4abinjacob.chikku.home.DrawerUi
import io.github.aj4abinjacob.chikku.home.IconBinder
import io.github.aj4abinjacob.chikku.platform.LocaleSectionIndex
import io.github.aj4abinjacob.chikku.theme.SurfaceColors
import kotlin.math.roundToInt

/** The drawer's views, found by the activity. */
internal class DrawerViews(
    val drawer: LinearLayout,
    val search: EditText,
    val tabsScroll: HorizontalScrollView,
    val tabs: LinearLayout,
    val statusContainer: View,
    val status: TextView,
    val retry: Button,
    val list: RecyclerView,
    val pages: RecyclerView,
    val fastScroller: DrawerFastScroller,
    val dots: DrawerPageDots,
    /** Optional "search everything" button that travels with the search field (search feature). */
    val searchMore: View? = null,
)

/**
 * Renders the app drawer from [DrawerUi], the search query, `LauncherSettings.drawer` and the drawer
 * groups: vertical grid or horizontal pages, tabs (All / Work / groups), fast scroller, background,
 * search bar position and label/icon appearance. Owns no data; HomeActivity feeds it state.
 * Apps arrive already filtered of hidden apps (see `HomeViewModel.drawerApps`).
 */
internal class DrawerController(
    private val v: DrawerViews,
    iconBinder: IconBinder,
    listener: DrawerItemListener,
) {
    private val context = v.drawer.context
    private val rowHeightHandler = Handler(Looper.getMainLooper())
    private val automaticRowsAnimator = v.list.itemAnimator
    private val automaticPagesAnimator = v.pages.itemAnimator
    private val listBase = durationsOf(automaticRowsAnimator)
    private val pagesBase = durationsOf(automaticPagesAnimator)
    private val res = context.resources
    private val listAdapter = DrawerAdapter(iconBinder, listener)
    private val pagesAdapter = DrawerPagesAdapter(iconBinder, listener)
    private val grid = GridLayoutManager(context, DEFAULT_COLUMNS)
    private val pagesLayout = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
    private var animationScale = 1f
    private val snapHelper = DrawerPageSnapHelper(v.pages, { settings.pageSnapDurationMs }, { animationScale })
    private val sectionIndex = LocaleSectionIndex()
    private val listGroups = DrawerListGroups(context)
    private val bubbleNudge = BubbleNudge(v.list)
    private val cellWidth = res.getDimensionPixelSize(R.dimen.home_drawer_column_width)
    private val panelMargin = res.getDimensionPixelSize(R.dimen.home_panel_margin)
    private val listSidePadding = res.getDimensionPixelSize(R.dimen.home_page_padding)
    private val scrollerWidth = res.getDimensionPixelSize(R.dimen.drawer_fast_scroller_width)
    private val scrollerPadding = res.getDimensionPixelSize(R.dimen.drawer_fast_scroller_padding)
    private val thumbWidth = res.getDimensionPixelSize(R.dimen.drawer_fast_scroller_thumb_width)
    private var themeBackground = ContextCompat.getColor(context, R.color.chikku_drawer_background)
    private var themeContent = ContextCompat.getColor(context, R.color.chikku_on_drawer)

    /** Night-mode search tokens (style settings); null keeps the drawer content color for the field. */
    private var searchColors: SurfaceColors? = null

    private var ui: DrawerUi = DrawerUi.Loading
    private var query = ""
    private var resultCurrent = true
    private var settings = DrawerSettings()
    private var desktopIconScale = 1f
    private var privateKeyboard = true
    private var beforeSearch: ScrollPosition? = null
    private var pendingRestore: ScrollPosition? = null
    private var navigationEpoch = 0
    private data class ScrollPosition(val first: Int, val offset: Int, val page: Int)
    private var swipeUpAction: GestureAction = GestureAction.OpenDrawer
    private var groups: List<DrawerGroup> = emptyList()
    private var selectedTabId: String? = null
    private var tabs: List<DrawerTab> = emptyList()
    private var columns = DEFAULT_COLUMNS
    private var bottomInset = 0
    private var keyboardInset = 0
    /** Empty slot space removed above the first scrolling row so it sits under the search bar. */
    private var firstRowTrim = 0
    private var contentColor = themeContent
    private var renderedTabRow: List<String>? = null
    private var renderedSelected = -1

    /**
     * Memory: while the drawer is hidden ([onDrawerShown] false) both RecyclerViews are detached from
     * their adapters, which recycles every bound row/page (icon loads cancelled) and clears their
     * view pools. The adapters keep their data, so renders while closed only diff lists and bind
     * nothing. Where the user was is kept as each layout manager's pending scroll position, which
     * later navigation (closing without "remember position", tab switches, search) overrides.
     */
    private var contentShown = true
    private var contentAttached = true
    private var detachedList = ScrollPosition(0, 0, 0)
    private val releaseContent = Runnable { detachContent() }

    /** Whether the drawer's app rows are currently bound (false while it is closed). */
    val isContentAttached: Boolean get() = contentAttached

    /** Whether the system bars over the open drawer should use dark icons (light background). */
    var lightSystemBars: Boolean = !DrawerModel.prefersLightContent(themeBackground)
        private set

    init {
        // RecyclerView's scrollbar metrics otherwise count items, which makes a short last grid
        // row distort the thumb's range and its drag target.
        grid.setUsingSpansToEstimateScrollbarDimensions(true)
        v.list.layoutManager = grid
        v.list.adapter = listAdapter
        v.list.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(outRect: android.graphics.Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                val position = parent.getChildAdapterPosition(view)
                if (position != RecyclerView.NO_POSITION && position < grid.spanCount) outRect.top = -firstRowTrim
            }
        })
        v.list.addItemDecoration(listGroups)
        v.list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) = updateThumb()
        })
        v.pages.layoutManager = pagesLayout
        v.pages.adapter = pagesAdapter
        snapHelper.attachToRecyclerView(v.pages)
        v.dots.onPageSelected = ::goToPage
        v.pages.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updatePageMotion() }
        val tabSwipe = { DrawerTabSwipe(enabled = { tabs.size > 1 && query.isBlank() }, onSwipe = ::switchTab) }
        v.list.addOnItemTouchListener(tabSwipe())
        v.pages.addOnItemTouchListener(tabSwipe())
        v.pages.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                updatePageMotion()
                v.dots.revealWhileScrolling()
            }

            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    val snapped = snapHelper.findSnapView(pagesLayout)
                    val position = snapped?.let(pagesLayout::getPosition) ?: RecyclerView.NO_POSITION
                    if (position != RecyclerView.NO_POSITION && pagesAdapter.pageCount > 0) {
                        val normalized = pagesAdapter.normalizedPosition(position)
                        if (position != normalized) pagesLayout.scrollToPositionWithOffset(normalized, 0)
                    }
                    updateDots()
                }
            }
        })
        v.fastScroller.onSectionSelected = { position -> grid.scrollToPositionWithOffset(position, 0) }
        v.fastScroller.onThumbDragged = ::scrollToThumbFraction
        v.fastScroller.onThumbGestureStarted = v.list::stopScroll
        // List labels start at the far side from the rail, so the bubble covers no app there.
        v.fastScroller.onBubbleMoved = { bounds -> bubbleNudge.moveTo(bounds.takeIf { settings.style != DrawerStyle.LIST }) }
        v.fastScroller.letterBeside = ::letterBesideBubble
        (v.list.parent as View).addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateColumns() }
        v.list.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            // View.layout clears requestLayout flags after notifying its listeners. Defer the
            // adapter resize until that pass ends so it schedules the required second layout.
            rowHeightHandler.post { updateRowHeight(); updateThumb() }
        }
        applyPrivateKeyboard()
        applyAppearance()
        refresh()
    }

    // --- Inputs ----------------------------------------------------------------------------------

    /**
     * [query] is the query [ui] was computed for, not the text in the field, so the layout mode
     * (tabs, group letters, scroll) always matches the content shown. While the result for the
     * typed text is pending ([resultCurrent] false) the previous content stays in place, and a
     * "no matches" status of an older query is hidden rather than shown for the wrong query.
     */
    fun render(ui: DrawerUi, query: String, launcherSettings: LauncherSettings, resultCurrent: Boolean = true) {
        val drawerSettings = launcherSettings.drawer
        val swipeUp = launcherSettings.gestures.swipeUp
        val desktopScale = launcherSettings.home.iconScale
        val keyboardPrivate = launcherSettings.search.privateKeyboard
        val sameContent = ui == this.ui && query == this.query && drawerSettings == settings && swipeUp == swipeUpAction && desktopScale == desktopIconScale && keyboardPrivate == privateKeyboard && animationScale == Motion.current(launcherSettings.lookFeel.animationSpeed).scale
        if (sameContent && resultCurrent == this.resultCurrent) return
        if (sameContent) {
            // Only the pending/current flag moved (the first emission of a keystroke): the content
            // is unchanged, so only the status line can differ.
            this.resultCurrent = resultCurrent
            if (ui is DrawerUi.NoMatches) applyStatus(statusRes(shownEmpty = true, tabKind = null))
            return
        }
        if (keyboardPrivate != privateKeyboard) {
            privateKeyboard = keyboardPrivate
            applyPrivateKeyboard()
        }
        val settingsChanged = drawerSettings != settings || desktopScale != desktopIconScale
        if (this.query.isBlank() && query.isNotBlank()) beforeSearch = captureScrollPosition()
        if (this.query.isNotBlank() && query.isBlank()) {
            pendingRestore = beforeSearch
            beforeSearch = null
        }
        this.desktopIconScale = desktopScale
        this.ui = ui
        this.query = query
        this.resultCurrent = resultCurrent
        this.settings = drawerSettings
        animationScale = Motion.current(launcherSettings.lookFeel.animationSpeed).scale
        applyPageSettings()
        this.swipeUpAction = swipeUp
        if (settingsChanged) applyAppearance()
        refresh()
    }

    fun setGroups(groups: List<DrawerGroup>) {
        if (groups == this.groups) return
        this.groups = groups
        refresh()
    }

    /**
     * Theme colors from night mode (`SurfaceColors` for the drawer and search surfaces). They are the
     * base when the user has not chosen a drawer background color; a chosen color always wins.
     */
    fun setSurfaceColors(drawer: SurfaceColors, search: SurfaceColors) {
        if (drawer.background == themeBackground && drawer.onSurface == themeContent && search == searchColors) return
        themeBackground = drawer.background
        themeContent = drawer.onSurface
        searchColors = search
        applyAppearance()
        refresh() // relabel cells with the new content color
    }

    /**
     * Bottom system bar or keyboard height; the last drawer element is kept above it. [keyboard] is
     * the part of [bottom] taken by the keyboard, which must not squeeze the row spacing.
     */
    fun setBottomInset(bottom: Int, keyboard: Int = 0) {
        if (bottom == bottomInset && keyboard == keyboardInset) return
        bottomInset = bottom
        keyboardInset = keyboard
        updateBottom()
    }

    // --- Queries for the activity ----------------------------------------------------------------

    /** Whether a swipe down may close the drawer (the list is scrolled to its top). */
    fun isAtTop(): Boolean = !v.list.isVisible || !v.list.canScrollVertically(-1)

    /** Whether a swipe up on home may open the drawer (open method is not "button only"). */
    val allowsSwipeOpen: Boolean get() = DrawerModel.allowsSwipeOpen(settings.openMethod)

    /** Whether the home "All apps" button is shown. */
    val showsOpenButton: Boolean
        get() = DrawerModel.showsOpenButton(settings.openMethod, swipeUpAction == GestureAction.OpenDrawer)

    /** Called when the drawer has been asked to open: shows the keyboard if the user chose that. */
    fun onOpened() {
        if (!settings.openKeyboardOnOpen || v.search.visibility != View.VISIBLE) return
        v.search.post {
            if (v.drawer.visibility != View.VISIBLE || v.search.visibility != View.VISIBLE) return@post
            v.search.requestFocus()
            context.getSystemService(InputMethodManager::class.java)?.showSoftInput(v.search, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    /** Remembering applies to returning to this drawer; searches always start at the best match. */
    fun onClosed() {
        if (!settings.rememberScrollPosition) {
            navigationEpoch++
            beforeSearch = null
            pendingRestore = null
            scrollListTo(0, 0)
            if (pagesAdapter.pageCount > 0) scrollPagesTo(0)
        }
        updateDots()
    }

    /**
     * The drawer became visible (an open swipe or animation started, still below the screen) or
     * fully hidden; wire it to `DrawerMotion.onShownChanged`. Showing rebinds the rows at once, so
     * they are laid out before the drawer reaches the screen; hiding releases them after
     * [RELEASE_DELAY_MS], so a quick close-and-reopen, or a drag that starts from a drawer icon
     * (the drawer hides before the drag shadow is taken), keeps its views.
     */
    fun onDrawerShown(shown: Boolean) {
        if (shown == contentShown) return
        contentShown = shown
        rowHeightHandler.removeCallbacks(releaseContent)
        if (shown) attachContent() else rowHeightHandler.postDelayed(releaseContent, RELEASE_DELAY_MS)
    }

    /** System memory pressure while HOME is in the background: release now if the drawer is hidden. */
    fun onTrimMemory() {
        if (contentShown) return
        rowHeightHandler.removeCallbacks(releaseContent)
        detachContent()
    }

    private fun detachContent() {
        if (!contentAttached || contentShown) return
        // Keep the visible position as the pending one; the null adapter skips layout, so it survives.
        val position = captureScrollPosition()
        detachedList = position
        grid.scrollToPositionWithOffset(position.first, position.offset)
        if (pagesAdapter.pageCount > 0) pagesLayout.scrollToPositionWithOffset(pagesAdapter.adapterPosition(position.page), 0)
        v.fastScroller.cancelInteraction()
        bubbleNudge.moveTo(null)
        contentAttached = false
        v.list.adapter = null
        v.pages.adapter = null
        v.list.recycledViewPool.clear()
        v.pages.recycledViewPool.clear()
    }

    private fun attachContent() {
        if (contentAttached) return
        contentAttached = true
        v.list.adapter = listAdapter
        v.pages.adapter = pagesAdapter
        v.pages.post { updateDots(); updatePageMotion() }
        v.list.post { updateThumb() }
    }

    /** Scrolls the vertical list; while detached this only records where it will open. */
    private fun scrollListTo(first: Int, offset: Int) {
        if (!contentAttached) detachedList = detachedList.copy(first = first, offset = offset)
        grid.scrollToPositionWithOffset(first, offset)
    }

    /** Scrolls the pages; while detached this only records where they will open. */
    private fun scrollPagesTo(page: Int) {
        detachedList = detachedList.copy(page = page.coerceIn(0, (pagesAdapter.pageCount - 1).coerceAtLeast(0)))
        pagesLayout.scrollToPositionWithOffset(pagesAdapter.adapterPosition(page), 0)
    }

    /** A pull down at the top can focus the existing search field instead of dismissing the drawer. */
    val canPullToSearch: Boolean
        get() = settings.pullToSearch && settings.searchBarPosition != DrawerSearchBarPosition.HIDDEN && !v.search.hasFocus()

    fun onPullToSearch() {
        if (!canPullToSearch || !v.drawer.isVisible) return
        v.search.requestFocus()
        context.getSystemService(InputMethodManager::class.java)?.showSoftInput(v.search, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun captureScrollPosition(): ScrollPosition {
        if (!contentAttached) return detachedList
        val first = grid.findFirstVisibleItemPosition().coerceAtLeast(0)
        val offset = grid.findViewByPosition(first)?.let { grid.getDecoratedTop(it) - v.list.paddingTop } ?: 0
        return ScrollPosition(first, offset, currentPage())
    }

    // --- Rendering -------------------------------------------------------------------------------

    private fun applyPrivateKeyboard() {
        val options = v.search.imeOptions
        v.search.imeOptions = if (privateKeyboard) options or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            else options and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING.inv()
        if (v.search.hasFocus()) context.getSystemService(InputMethodManager::class.java)?.restartInput(v.search)
    }

    /**
     * The list animator follows the user's animation speed (durations scaled, none when off). The
     * paged drawer's animator drives the cross-fade of re-chunked pages. Manual row spacing in the
     * vertical drawer keeps no animator: outgoing holders would keep the old compact geometry.
     */
    private fun applyItemAnimators() {
        val motionOn = animationScale > 0f
        val listAnimator = if (motionOn && (settings.automaticRowSpacing || settings.style != DrawerStyle.VERTICAL)) {
            automaticRowsAnimator?.also { scaleDurations(it, listBase) }
        } else null
        if (v.list.itemAnimator !== listAnimator) {
            v.list.itemAnimator?.endAnimations()
            v.list.itemAnimator = listAnimator
        }
        val pagesAnimator = if (motionOn) automaticPagesAnimator?.also { scaleDurations(it, pagesBase) } else null
        if (v.pages.itemAnimator !== pagesAnimator) {
            v.pages.itemAnimator?.endAnimations()
            v.pages.itemAnimator = pagesAnimator
        }
    }

    private class Durations(val add: Long, val remove: Long, val move: Long, val change: Long)

    private fun durationsOf(animator: RecyclerView.ItemAnimator?) =
        Durations(animator?.addDuration ?: 0L, animator?.removeDuration ?: 0L, animator?.moveDuration ?: 0L, animator?.changeDuration ?: 0L)

    private fun scaleDurations(animator: RecyclerView.ItemAnimator, base: Durations) {
        animator.addDuration = (base.add * animationScale).toLong()
        animator.removeDuration = (base.remove * animationScale).toLong()
        animator.moveDuration = (base.move * animationScale).toLong()
        animator.changeDuration = (base.change * animationScale).toLong()
    }

    /** Fades a status block in or out instead of popping, honouring the animation speed. */
    private fun fadeVisibility(view: View, visible: Boolean) {
        view.animate().cancel()
        val scaled = (FADE_MS * animationScale).toLong()
        if (scaled <= 0L || !view.isAttachedToWindow) {
            view.alpha = 1f
            view.visibility = if (visible) View.VISIBLE else View.GONE
            return
        }
        if (visible) {
            if (view.visibility != View.VISIBLE) { view.alpha = 0f; view.visibility = View.VISIBLE }
            view.animate().alpha(1f).setDuration(scaled).start()
        } else if (view.visibility == View.VISIBLE) {
            view.animate().alpha(0f).setDuration(scaled).withEndAction { view.visibility = View.GONE; view.alpha = 1f }.start()
        }
    }

    private fun applyAppearance() {
        // Manual row slots must change immediately; outgoing animated holders can retain the
        // old compact geometry while the replacement is laid out. Keep normal list animations
        // for automatic spacing, and leave the drawer's own open/close transition independent.
        applyItemAnimators()
        val background = DrawerModel.backgroundColor(themeBackground, settings.backgroundColor, settings.backgroundAlpha)
        v.drawer.setBackgroundColor(background)
        val lightContent = DrawerModel.prefersLightContent(background)
        contentColor = DrawerChrome.contentColor(themeContent, settings, lightContent)
        lightSystemBars = !lightContent
        val search = DrawerChrome.searchColors(searchColors, settings.backgroundColor != null, lightContent, contentColor, settings.searchBarStyle)
        v.search.setTextColor(search.text)
        v.search.setHintTextColor(search.hint)
        v.search.compoundDrawableTintList = ColorStateList.valueOf(search.hint)
        v.search.background = DrawerChrome.searchFieldBackground(res, search)
        v.status.setTextColor(contentColor)
        // The link is an accent color, so pick the shade that stays readable on this background.
        val link = if (lightContent) LINK_ON_DARK else LINK_ON_LIGHT
        (v.searchMore as? TextView)?.setTextColor(link)
        v.retry.setTextColor(link)
        v.dots.setColor(contentColor)
        listGroups.color = contentColor
        v.list.invalidate()
        renderedTabRow = null // recolor tabs
        placeTabs()
        placeSearch()
        updateColumns()
    }

    private fun iconScale() = if (settings.matchDesktopSize) desktopIconScale else settings.iconScale

    private fun appearance() = DrawerAppearance(
        iconScale = iconScale(),
        showLabels = settings.showLabels,
        labelColor = settings.labelColor ?: contentColor,
        labelScale = settings.labelScale,
        labelFont = settings.labelFont,
        labelShadow = settings.labelShadow,
        labelSingleLine = settings.labelSingleLine,
        listMode = settings.style == DrawerStyle.LIST,
    )

    private fun placeTabs() {
        val parent = v.drawer
        val tabs = v.tabsScroll
        val body = v.list.parent as View
        parent.removeView(tabs)
        val tabIndex = if (settings.tabBarPosition == DrawerTabBarPosition.BOTTOM) {
            parent.indexOfChild(body) + 1
        } else {
            // An empty tab's status must not move the navigation row down.
            minOf(parent.indexOfChild(v.statusContainer), parent.indexOfChild(body))
        }
        parent.addView(tabs, tabIndex)
    }

    private fun placeSearch() {
        val parent = v.drawer
        val search = v.search
        when (settings.searchBarPosition) {
            DrawerSearchBarPosition.TOP -> if (parent.indexOfChild(search) != 0) {
                parent.removeView(search)
                parent.addView(search, 0)
            }
            DrawerSearchBarPosition.BOTTOM -> if (parent.indexOfChild(search) != parent.childCount - 1) {
                parent.removeView(search)
                parent.addView(search)
            }
            DrawerSearchBarPosition.HIDDEN -> Unit
        }
        // Keep the "search everything" button next to the field: below it at the top, above it at the bottom.
        v.searchMore?.takeIf { it.parent === parent }?.let { more ->
            val wanted = when (settings.searchBarPosition) {
                DrawerSearchBarPosition.BOTTOM -> parent.indexOfChild(search) - 1
                else -> parent.indexOfChild(search) + 1
            }
            if (parent.indexOfChild(more) != wanted) {
                parent.removeView(more)
                parent.addView(more, parent.indexOfChild(search) + if (settings.searchBarPosition == DrawerSearchBarPosition.BOTTOM) 0 else 1)
            }
        }
        search.visibility = if (settings.searchBarPosition == DrawerSearchBarPosition.HIDDEN) View.GONE else View.VISIBLE
        if (search.visibility == View.GONE) {
            if (search.hasFocus()) {
                context.getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(search.windowToken, 0)
                search.clearFocus()
            }
            // A hidden field must not leave the drawer filtered by an invisible query.
            if (search.text.isNotEmpty()) search.setText("")
            v.searchMore?.visibility = View.GONE
        }
    }

    /** What the drawer lists; a rail drag is only abandoned when this really changes. */
    private data class Dataset(val shown: List<LauncherApp>, val paged: Boolean, val style: DrawerStyle)
    private var renderedDataset: Dataset? = null

    /** Everything one refresh derives from the inputs; the steps below read only this. */
    private class Model(val tab: DrawerTab?, val shown: List<LauncherApp>, val searching: Boolean, val paged: Boolean)

    /** Inputs of the list/paged presentation; when equal (and nothing is pending) it is not redone. */
    private data class PresentationKey(
        val shown: List<LauncherApp>, val paged: Boolean, val searching: Boolean, val appearance: DrawerAppearance,
        val columns: Int, val rows: Int, val style: DrawerStyle, val infinite: Boolean, val query: String,
    )
    private var renderedPresentation: PresentationKey? = null

    /** Inputs of the fast-scroller configuration; sections are the costly part to derive. */
    private data class ScrollerKey(
        val shown: List<LauncherApp>, val showable: Boolean, val thumb: Boolean, val left: Boolean, val popup: Boolean,
        val letterColor: Int, val activeColor: Int, val gapDp: Int, val density: Float,
    )
    private var renderedScroller: ScrollerKey? = null
    private var scrollerSections: List<DrawerSection> = emptyList()

    private fun refresh() {
        val model = computeModel()
        renderTabRow(if (tabs.size > 1) tabs else emptyList(), DrawerModel.selectedIndex(tabs, selectedTabId))
        val dataset = Dataset(model.shown, model.paged, settings.style)
        if (dataset != renderedDataset) {
            // The rail's letters describe the old list; an unrelated emission must not end the drag.
            renderedDataset = dataset
            v.fastScroller.cancelInteraction()
        }
        applyStatus(statusRes(model.shown.isEmpty(), model.tab?.kind))
        v.retry.visibility = if (ui is DrawerUi.Failed) View.VISIBLE else View.GONE
        updateColumns()
        applyPresentation(model)
        applyFastScroller(model)
        updateDots()
        updateBottom()
        v.list.post { updateThumb() }
    }

    /** Step 1: the tabs and the apps shown for the current UI state, query and settings. */
    private fun computeModel(): Model {
        val searching = query.isNotBlank()
        val apps = (ui as? DrawerUi.Apps)?.apps
        tabs = if (apps != null && !searching) DrawerModel.tabs(apps, groups, settings) else emptyList()
        val tab = tabs.getOrNull(DrawerModel.selectedIndex(tabs, selectedTabId))
        val shown: List<LauncherApp> = when {
            apps == null -> emptyList()
            searching -> apps
            else -> tab?.apps.orEmpty()
        }
        return Model(tab, shown, searching, settings.style == DrawerStyle.HORIZONTAL_PAGED && !searching)
    }

    /** The status message for [ui], or null for none. [shownEmpty] and [tabKind] describe the shown tab. */
    private fun statusRes(shownEmpty: Boolean, tabKind: DrawerTabKind?): Int? = when (val current = ui) {
        DrawerUi.Loading -> R.string.home_drawer_loading
        is DrawerUi.Failed -> R.string.home_drawer_failed
        DrawerUi.NoApps -> R.string.home_drawer_no_apps
        DrawerUi.NoMatches -> if (resultCurrent) R.string.home_drawer_no_matches else null
        is DrawerUi.Apps -> when {
            current.apps.isNotEmpty() && shownEmpty ->
                if (tabKind == DrawerTabKind.GROUP) R.string.drawer_group_empty else R.string.drawer_tab_empty
            else -> null
        }
    }

    private fun applyStatus(status: Int?) {
        status?.let(v.status::setText)
        fadeVisibility(v.statusContainer, status != null)
    }

    /** Step 2: the list or paged presentation of [Model.shown]; skipped when its inputs are unchanged. */
    private fun applyPresentation(model: Model) {
        val shown = model.shown
        val searching = model.searching
        val paged = model.paged
        val restore = pendingRestore
        pendingRestore = null
        val epoch = navigationEpoch
        v.list.visibility = if (paged) View.GONE else View.VISIBLE
        v.pages.visibility = if (paged) View.VISIBLE else View.GONE
        val appearance = appearance()
        val key = PresentationKey(shown, paged, searching, appearance, columns, settings.rows, settings.style, settings.infiniteScroll, query)
        if (restore == null && key == renderedPresentation) return
        renderedPresentation = key
        listAdapter.appearance = appearance
        // Search results are ranked, not alphabetical, so they carry no group letters.
        val groups = if (settings.style == DrawerStyle.LIST && !searching) DrawerModel.listGroups(shown, sectionIndex::sectionOf) else emptyList()
        if (paged) {
            showListGroups(emptyList())
            listAdapter.submitList(emptyList())
            submitPages(shown)
            if (restore != null) scrollPagesTo(restore.page)
        } else {
            pagesAdapter.submit(emptyList(), columns, settings.rows, appearance)
            listAdapter.submitList(shown) {
                // Letters follow adapter positions, so they change with the list they describe.
                showListGroups(groups)
                // Keep the best match visible while typing.
                if (epoch != navigationEpoch) return@submitList
                if (searching) scrollListTo(0, 0)
                else if (restore != null) scrollListTo(restore.first.coerceAtMost((shown.size - 1).coerceAtLeast(0)), restore.offset)
                v.list.post { updateThumb() }
            }
        }
    }

    /**
     * Step 3: fast-scroller configuration and the list padding it implies. The scroller's own
     * configuration runs only when its inputs change; the padding check is cheap and always runs.
     */
    private fun applyFastScroller(model: Model) {
        val thumb = settings.fastScrollerStyle == DrawerFastScrollerStyle.THUMB
        val left = settings.fastScrollerSide == DrawerFastScrollerSide.LEFT
        val showable = !model.paged && !model.searching && settings.showFastScroller
        val density = res.displayMetrics.density
        val activeColor = settings.fastScrollerColor ?: contentColor
        val key = ScrollerKey(model.shown, showable, thumb, left, settings.fastScrollerPopup, contentColor, activeColor, settings.fastScrollerGapDp, density)
        val gap = (settings.fastScrollerGapDp * density).roundToInt()
        val strip = (if (thumb) thumbWidth else scrollerWidth) + gap
        val previous = renderedScroller
        if (key != previous) {
            renderedScroller = key
            // Sections are the costly part (AlphabeticIndex): recompute only for another list.
            if (previous == null || previous.shown != key.shown || previous.showable != showable) {
                scrollerSections = if (showable) DrawerModel.sections(model.shown, sectionIndex::sectionOf) else emptyList()
            }
            v.fastScroller.thumbMode = thumb
            v.fastScroller.thumbOnLeft = left
            v.fastScroller.popupEnabled = settings.fastScrollerPopup
            v.fastScroller.setSections(scrollerSections, contentColor, activeColor)
            // The visible thumb and its touch geometry are owned by the same view.
            v.list.isVerticalScrollBarEnabled = false
            v.fastScroller.visibility = if (!thumb && scrollerSections.size > 1) View.VISIBLE else View.GONE
            // The strip captures every touch on it, so it covers only the bar and the chosen gap,
            // never an app cell; otherwise a tap slightly off an edge app jumps the list instead.
            (v.fastScroller.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
                val gravity = if (left) Gravity.LEFT else Gravity.RIGHT
                if (params.width != strip || params.gravity != gravity) {
                    params.width = strip
                    params.gravity = gravity
                    v.fastScroller.layoutParams = params
                }
            }
            // Letters stay in the outer part of the strip; the gap side is empty but still draggable.
            v.fastScroller.setPadding(scrollerPadding + if (left) 0 else gap, 0, scrollerPadding + if (left) gap else 0, 0)
        }
        val scrollerSide = if (v.fastScroller.isVisible) strip else listSidePadding
        val leftPadding = if (left) scrollerSide else listSidePadding
        val rightPadding = if (left) listSidePadding else scrollerSide
        if (v.list.paddingLeft != leftPadding || v.list.paddingRight != rightPadding) {
            v.list.setPadding(leftPadding, v.list.paddingTop, rightPadding, v.list.paddingBottom)
            updateColumns() // the padding changed the usable width
        }
    }

    private fun scrollToThumbFraction(fraction: Float) {
        val range = v.list.computeVerticalScrollRange()
        val extent = v.list.computeVerticalScrollExtent()
        val offset = v.list.computeVerticalScrollOffset()
        val maxOffset = (range - extent).coerceAtLeast(0)
        if (maxOffset <= 0) return
        // Clamp against the real list at both ends; estimated grid metrics can round off by a
        // pixel when the final row is only partly filled.
        val delta = when {
            fraction <= 0f -> -range
            fraction >= 1f -> range
            else -> (fraction * maxOffset).roundToInt() - offset
        }
        v.list.scrollBy(0, delta)
        updateThumb()
    }

    private fun updateThumb() {
        // Detached rows measure as an empty list; keep the strip and padding until they return.
        if (!contentAttached) return
        if (v.fastScroller.thumbMode && v.list.height > 0) {
            val scrollable = settings.showFastScroller && query.isBlank() && v.list.visibility == View.VISIBLE &&
                listAdapter.currentList.isNotEmpty() &&
                v.list.computeVerticalScrollRange() > v.list.computeVerticalScrollExtent()
            val wanted = if (scrollable) View.VISIBLE else View.GONE
            if (v.fastScroller.visibility != wanted) {
                v.fastScroller.visibility = wanted
                val left = settings.fastScrollerSide == DrawerFastScrollerSide.LEFT
                val gap = (settings.fastScrollerGapDp * res.displayMetrics.density).roundToInt()
                val strip = thumbWidth + gap
                val scrollerSide = if (scrollable) strip else listSidePadding
                val leftPadding = if (left) scrollerSide else listSidePadding
                val rightPadding = if (left) listSidePadding else scrollerSide
                v.list.setPadding(leftPadding, v.list.paddingTop, rightPadding, v.list.paddingBottom)
                updateColumns()
            }
        }
        val first = grid.findFirstVisibleItemPosition()
        val apps = listAdapter.currentList
        // A mixed-letter grid row displays all its cells together. Use its leading app for the
        // bubble, including when a swipe lands halfway through that row.
        val rowStart = if (first >= 0) first - first % grid.spanCount.coerceAtLeast(1) else -1
        val letter = apps.getOrNull(rowStart)?.let { sectionIndex.sectionOf(it.label) }
        v.fastScroller.updateThumb(
            v.list.computeVerticalScrollOffset(),
            v.list.computeVerticalScrollExtent(),
            v.list.computeVerticalScrollRange(),
            letter,
        )
    }

    private fun submitPages(apps: List<LauncherApp>) {
        val pages = DrawerModel.pages(apps, columns, settings.rows)
        val previousPage = currentPage()
        pagesAdapter.submit(pages, columns, settings.rows, appearance(), settings.infiniteScroll)
        scrollPagesTo(previousPage)
        v.pages.post { updateDots(); updatePageMotion() }
    }

    private fun currentPage(): Int {
        if (!contentAttached) return detachedList.page
        // Right after attachContent, before the first layout, there is no snap view yet.
        val snapped = snapHelper.findSnapView(pagesLayout) ?: return detachedList.page
        return pagesAdapter.logicalPage(pagesLayout.getPosition(snapped).coerceAtLeast(0))
    }

    private fun updateDots() {
        val paged = v.pages.isVisible
        val count = pagesAdapter.pageCount
        v.dots.style = settings.pageIndicatorStyle
        v.dots.autoHide = settings.pageIndicatorAutoHide
        v.dots.setPages(currentPage().coerceAtMost((count - 1).coerceAtLeast(0)), count)
        // The drawer collapses the strip when unused; setPages applies the home visibility policy.
        v.dots.visibility = if (paged && count > 1 && settings.pageIndicatorStyle != PageIndicatorStyle.NONE) View.VISIBLE else View.GONE
    }

    private fun applyPageSettings() {
        (v.pages as? HorizontalDrawerRecyclerView)?.apply {
            swipeSensitivity = settings.pageSwipeSensitivity
            snapDurationMs = settings.pageSnapDurationMs
            animationScale = this@DrawerController.animationScale
            edgeFeedback = settings.pageEdgeFeedback
        }
        v.dots.motionScale = animationScale
        v.fastScroller.motionScale = animationScale
        bubbleNudge.motionScale = animationScale
        pagesAdapter.motionScale = animationScale
        applyItemAnimators()
        (v.list as? VerticalDrawerRecyclerView)?.apply {
            scrollSpeed = settings.listScrollSpeed
            edgeEffect = settings.listEdgeEffect
        }
        updatePageMotion()
    }

    private fun goToPage(page: Int) {
        if (pagesAdapter.pageCount <= 0) return
        val target = pagesAdapter.adapterPosition(page)
        v.pages.stopScroll()
        val existing = pagesLayout.findViewByPosition(target)
        if (animationScale <= 0f || existing == null) {
            // Direct jumps do not traverse every intervening app page.
            v.pages.doOnNextLayout { updateDots(); updatePageMotion() }
            pagesLayout.scrollToPositionWithOffset(target, 0)
            v.dots.setPages(page.coerceIn(0, pagesAdapter.pageCount - 1), pagesAdapter.pageCount)
        } else {
            val distance = snapHelper.calculateDistanceToFinalSnap(pagesLayout, existing) ?: return
            v.pages.smoothScrollBy(distance[0], distance[1])
        }
    }

    private fun updatePageMotion() {
        val width = v.pages.width
        if (width <= 0) return
        val rtl = v.pages.layoutDirection == View.LAYOUT_DIRECTION_RTL
        for (index in 0 until v.pages.childCount) {
            val child = v.pages.getChildAt(index)
            val position = child.left.toFloat() / width
            applyPageTransform(child, position, settings.pageTransitionEffect, settings.pageEffectStrength, animationScale > 0f)
        }
        val snapped = snapHelper.findSnapView(pagesLayout) ?: return
        val adapterPosition = pagesLayout.getPosition(snapped)
        if (adapterPosition == RecyclerView.NO_POSITION) return
        val fraction = snapped.left.toFloat() / width * if (rtl) 1 else -1
        v.dots.setScrollPosition(pagesAdapter.logicalPage(adapterPosition) + fraction)
    }

    private fun updateColumns() {
        val body = v.list.parent as View
        val usable = body.width - v.list.paddingLeft - v.list.paddingRight
        val cell = (cellWidth * iconScale().coerceAtLeast(MIN_CELL_SCALE)).roundToInt()
        val next = if (settings.style == DrawerStyle.LIST) 1 else DrawerModel.columns(settings.columns, usable, cell, MIN_AUTO_COLUMNS, MAX_AUTO_COLUMNS)
        if (body.width <= 0 && settings.columns == DrawerSettings.AUTO_COLUMNS && settings.style != DrawerStyle.LIST) return
        if (next == columns && grid.spanCount == next) return
        columns = next
        grid.spanCount = next
        if (v.pages.isVisible) {
            // Re-chunk after this layout pass.
            v.pages.post { refresh() }
        }
    }

    /**
     * Size scrolling grid rows from the actual viewport after search, tabs and system/IME insets.
     * List rows always keep their content height: stretching a one-app row to a fraction of the
     * screen leaves mostly empty rows.
     */
    private fun updateRowHeight() {
        if (settings.automaticRowSpacing || settings.style != DrawerStyle.VERTICAL || v.list.height <= 0) {
            setFirstRowTrim(0)
            listAdapter.rowHeightPx = ViewGroup.LayoutParams.WRAP_CONTENT
            return
        }
        val contentHeight = DrawerAppearance.cellContentHeight(context, settings, iconScale())
        // Measure as if the keyboard were closed: it covers the lower rows, it doesn't resize them.
        val viewport = (v.list.height - v.list.paddingTop - v.list.paddingBottom + keyboardInset).coerceAtLeast(0)
        // Icons are centred in their slot, so the first row would otherwise carry half a slot of
        // empty space under the search bar. Trim it to a small gap and give the space to the rows:
        // rows * slot - trim = viewport, where trim = (slot - content) / 2 - gap.
        val gap = (DrawerChrome.FIRST_ROW_GAP_DP * res.displayMetrics.density).roundToInt()
        val slot = DrawerChrome.manualRowSlot(viewport.toFloat(), contentHeight.toFloat(), gap.toFloat(), settings.rows).roundToInt()
        // Large icons/fonts can require fewer visible rows than requested; never clip the content.
        val rowHeight = slot.coerceAtLeast(contentHeight)
        setFirstRowTrim(DrawerChrome.firstRowTrim(rowHeight, contentHeight, gap))
        listAdapter.rowHeightPx = rowHeight
    }

    /**
     * Letter of the row beside the thumb's bubble at [y] (the scroller's coordinates), so the bubble
     * names the apps it sits next to rather than the top row. A grid row is named by its leading
     * app, as [updateThumb] names the top row.
     */
    private fun letterBesideBubble(y: Float): String? {
        val listY = y + v.fastScroller.top - v.list.top
        // Layout bounds, not drawn ones: the cells beside the bubble are scaled aside while it shows.
        val row = (0 until v.list.childCount).map(v.list::getChildAt)
            .firstOrNull { listY >= it.top && listY < it.bottom } ?: return null
        val position = v.list.getChildAdapterPosition(row).takeIf { it != RecyclerView.NO_POSITION } ?: return null
        val rowStart = position - position % grid.spanCount.coerceAtLeast(1)
        return listAdapter.currentList.getOrNull(rowStart)?.let { sectionIndex.sectionOf(it.label) }
    }

    private fun showListGroups(groups: List<DrawerSection>) {
        if (listGroups.update(groups)) v.list.invalidateItemDecorations() else v.list.invalidate()
    }

    private fun setFirstRowTrim(trim: Int) {
        if (trim == firstRowTrim) return
        firstRowTrim = trim
        v.list.invalidateItemDecorations()
    }

    private fun updateBottom() {
        val searchAtBottom = settings.searchBarPosition == DrawerSearchBarPosition.BOTTOM
        val dotsShown = v.dots.isVisible
        setBottomMargin(v.search, panelMargin + if (searchAtBottom) bottomInset else 0)
        setBottomMargin(v.dots, if (!searchAtBottom && dotsShown) bottomInset else 0)
        val listBottom = if (searchAtBottom || dotsShown) 0 else bottomInset
        val wideGrid = settings.tabStyle == DrawerTabStyle.SEGMENTED && !settings.automaticRowSpacing && settings.style == DrawerStyle.VERTICAL
        val gridTop = if (wideGrid) (16 * res.displayMetrics.density).roundToInt() else 0
        val gridBottom = listBottom + if (wideGrid) (24 * res.displayMetrics.density).roundToInt() else 0
        if (v.list.paddingTop != gridTop || v.list.paddingBottom != gridBottom)
            v.list.setPadding(v.list.paddingLeft, gridTop, v.list.paddingRight, gridBottom)
        if (v.pages.paddingBottom != listBottom) v.pages.setPadding(0, 0, 0, listBottom)
        setBottomMargin(v.fastScroller, listBottom)
        updateRowHeight()
    }

    private fun setBottomMargin(view: View, margin: Int) {
        val params = view.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        if (params.bottomMargin == margin) return
        params.bottomMargin = margin
        view.layoutParams = params
    }

    // --- Tabs ------------------------------------------------------------------------------------

    private fun tabTitle(tab: DrawerTab): String = when (tab.kind) {
        DrawerTabKind.ALL -> res.getString(R.string.drawer_tab_all)
        DrawerTabKind.PERSONAL -> res.getString(if (settings.tabStyle == DrawerTabStyle.SEGMENTED) R.string.drawer_tab_apps else R.string.drawer_tab_personal)
        DrawerTabKind.WORK -> res.getString(R.string.drawer_tab_work)
        DrawerTabKind.GROUP -> tab.name.orEmpty()
    }

    private fun renderTabRow(shownTabs: List<DrawerTab>, selected: Int) {
        v.tabsScroll.visibility = if (shownTabs.isEmpty()) View.GONE else View.VISIBLE
        val titles = shownTabs.map(::tabTitle)
        if (titles == renderedTabRow && selected == renderedSelected) return
        val rebuilt = titles != renderedTabRow
        if (rebuilt) {
            v.tabsScroll.isFillViewport = settings.tabStyle == DrawerTabStyle.SEGMENTED
            val side = DrawerChrome.tabRowSidePadding(res, settings.tabStyle == DrawerTabStyle.SEGMENTED, panelMargin)
            v.tabs.setPaddingRelative(side, 0, side, 0)
            if (renderedTabRow != null && v.tabs.childCount == titles.size) {
                // Same number of tabs (e.g. a renamed group): update them in place, no rebuild flash.
                titles.forEachIndexed { index, title -> bindTab(v.tabs.getChildAt(index) as TextView, index, title, shownTabs[index].id, titles.size) }
            } else {
                v.tabs.removeAllViews()
                titles.forEachIndexed { index, title -> v.tabs.addView(createTab(index, title, shownTabs[index].id, titles.size)) }
            }
            renderedTabRow = titles
        }
        // Only a selection change (not a rebuild) slides the pill from one tab to the next.
        val animateStyle = renderedSelected >= 0 && !rebuilt && animationScale > 0f && v.tabs.isAttachedToWindow
        renderedSelected = selected
        for (i in 0 until v.tabs.childCount) styleTab(v.tabs.getChildAt(i) as TextView, i == selected, animateStyle)
        v.tabs.getChildAt(selected)?.let { child ->
            v.tabsScroll.post {
                val x = (child.left - panelMargin).coerceAtLeast(0)
                if (animationScale > 0f) v.tabsScroll.smoothScrollTo(x, 0) else v.tabsScroll.scrollTo(x, 0)
            }
        }
    }

    private fun bindTab(tab: TextView, index: Int, title: String, id: String, count: Int) {
        tab.text = title
        tab.contentDescription = res.getString(R.string.drawer_tab_description, title, index + 1, count)
        tab.setOnClickListener { selectTab(id) }
    }

    private fun createTab(index: Int, title: String, id: String, count: Int): TextView = TextView(context).apply {
        isFocusable = true
        DrawerChrome.configureTab(this, res, settings.tabStyle == DrawerTabStyle.SEGMENTED)
        bindTab(this, index, title, id, count)
        ViewCompat.setAccessibilityDelegate(this, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.roleDescription = res.getString(R.string.drawer_tab_role)
                info.isSelected = host.isSelected
            }
        })
        layoutParams = DrawerChrome.tabLayoutParams(res, settings.tabStyle == DrawerTabStyle.SEGMENTED, index, count)
    }

    private fun styleTab(tab: TextView, selected: Boolean, animate: Boolean = false) {
        val previous = tab.background
        DrawerChrome.styleTab(tab, res, settings.tabStyle == DrawerTabStyle.SEGMENTED, settings.tabAccentColor, contentColor, selected)
        // The pill fades between states; a tab whose look does not change is left untouched.
        val next = tab.background
        if (animate && previous != null && next != null && previous !== next) {
            tab.background = android.graphics.drawable.TransitionDrawable(arrayOf(previous, next)).apply {
                isCrossFadeEnabled = true
                startTransition((TAB_FADE_MS * animationScale).toInt())
            }
        }
    }

    private fun selectTab(id: String) {
        if (id == selectedTabId && tabs.getOrNull(DrawerModel.selectedIndex(tabs, id))?.id == id) return
        selectedTabId = id
        refresh()
        crossFadeContent()
        scrollListTo(0, 0)
        scrollPagesTo(0)
        updateDots()
    }

    /** The new tab's apps fade in rather than replacing the old ones in a single frame. */
    private fun crossFadeContent() {
        val scaled = (FADE_MS * animationScale).toLong()
        if (scaled <= 0L) return
        listOf<View>(v.list, v.pages).filter { it.isVisible && it.isAttachedToWindow }.forEach { view ->
            view.animate().cancel()
            view.alpha = 0f
            view.animate().alpha(1f).setDuration(scaled).start()
        }
    }

    private fun switchTab(delta: Int) {
        if (tabs.size <= 1) return
        val current = DrawerModel.selectedIndex(tabs, selectedTabId)
        val next = (current + delta).coerceIn(0, tabs.lastIndex)
        if (next != current) {
            selectTab(tabs[next].id)
            v.tabs.getChildAt(next)?.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_SELECTED)
        }
    }

    private companion object {
        const val DEFAULT_COLUMNS = 4
        /** How long a hidden drawer keeps its rows before releasing them. */
        const val RELEASE_DELAY_MS = 1_000L
        const val FADE_MS = 140L
        const val TAB_FADE_MS = 180L
        const val MIN_AUTO_COLUMNS = 3
        const val MAX_AUTO_COLUMNS = 8
        const val MIN_CELL_SCALE = 0.75f

        /** Indigo accents with at least 4.5:1 contrast on dark (~#1C1B1F) and light (white) drawers. */
        const val LINK_ON_DARK = 0xFF8C9EFF.toInt()
        const val LINK_ON_LIGHT = 0xFF304FFE.toInt()
    }
}
