package io.github.aj4abinjacob.chikku.home

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.OverScroller
import androidx.core.view.ViewCompat
import androidx.core.view.doOnLayout
import androidx.core.view.isEmpty
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.settings.PageTransitionEffect
import io.github.aj4abinjacob.chikku.core.model.settings.PageMotion
import io.github.aj4abinjacob.chikku.core.model.settings.PageSwipeSensitivity
import io.github.aj4abinjacob.chikku.core.model.settings.PageEdgeFeedback
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Minimal horizontal pager for desktop pages. Each child fills the pager; children are placed in
 * page order from the start edge, so in RTL page 0 is the rightmost. Vertical swipes are left to
 * the enclosing [VerticalSwipeLayout], which also covers the dock.
 */
class WorkspacePager @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : ViewGroup(context, attrs) {

    var onPageChanged: ((page: Int, pageCount: Int) -> Unit)? = null
    /** Called on each scroll step of a swipe or animated snap; not for instant jumps or layout. */
    var onUserScroll: (() -> Unit)? = null
    /** Optional feed at the start edge of desktop page 0; never becomes a stored layout page. */
    var onStartEdgeSwipe: (() -> Unit)? = null

    var currentPage: Int = 0
        private set

    val pageCount: Int get() = childCount

    /** Swiping past the last page wraps to the first and vice versa (infinite scroll). */
    var infiniteScroll: Boolean = false
        set(value) {
            if (field == value) return
            cancelIncomingPageSlide()
            scroller.abortAnimation()
            field = value
            scrollTo(scrollXForPage(currentPage), 0)
            updatePageTransforms()
        }

    var swipeSensitivity: PageSwipeSensitivity = PageSwipeSensitivity.NORMAL
    /** How much more sideways than vertical a movement must be to page; previews of the drawer use its looser rule. */
    var horizontalDominance: Float = PageMotion.WORKSPACE_HORIZONTAL_DOMINANCE
    var snapDurationMs: Int = 250
    var effectStrength: Float = 1f
        set(value) { field = value; updatePageTransforms() }
    var edgeFeedback: PageEdgeFeedback = PageEdgeFeedback.RESISTANCE
    /** Fractional logical position, including wrapping, for the interactive indicator. */
    var onPageScrolled: ((position: Float) -> Unit)? = null
    /** Settings samples must never send wallpaper Binder calls. */
    var wallpaperUpdatesEnabled: Boolean = true
    private var gestureStartSlot = 0
    private var gestureStartX = 0
    private var edgePull = 0f

    /** Wallpaper follows the page scroll; when off it stays centered. */
    var wallpaperScrolling: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            updateWallpaperOffsets(force = true)
        }

    private val wallpaperManager: WallpaperManager? = try {
        WallpaperManager.getInstance(context)
    } catch (_: Exception) {
        null
    }
    private var lastWallpaperX = Float.NaN
    private var lastWallpaperStep = Float.NaN

    private val scroller = OverScroller(context, DecelerateInterpolator(1.5f))
    private val config = ViewConfiguration.get(context)
    private val touchSlop = config.scaledTouchSlop
    private val minFlingVelocity = config.scaledMinimumFlingVelocity
    private var velocityTracker: VelocityTracker? = null

    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var mode = Mode.IDLE

    private enum class Mode { IDLE, PAGING, FEED, IGNORED }

    private val isRtl get() = layoutDirection == LAYOUT_DIRECTION_RTL

    init {
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    /** Visual slot (from the left) of a page index. */
    private fun visualIndex(page: Int): Int = if (isRtl) childCount - 1 - page else page

    private fun pageForVisual(visual: Int): Int = if (isRtl) childCount - 1 - visual else visual

    private fun scrollXForPage(page: Int): Int = visualIndex(page) * width

    fun pageAt(index: Int): CellLayout? = getChildAt(index) as? CellLayout

    /**
     * Where the pager is scrolled, in logical pages (RTL mirrored): 1.5 is halfway between pages 1
     * and 2. While wrapping (infinite scroll) it can lie just outside 0..pageCount-1. Before the
     * first layout it is [currentPage].
     */
    val scrollPosition: Float
        get() {
            if (width <= 0 || isEmpty()) return currentPage.toFloat()
            val visual = scrollX.toFloat() / width
            return if (isRtl) childCount - 1 - visual else visual
        }

    /** Page-snap animation speed (style settings animation speed); 0 jumps without animating. */
    var snapDurationScale: Float = 1f
        set(value) {
            field = value
            if (value <= 0f) cancelIncomingPageSlide()
            if (value <= 0f && !scroller.isFinished) {
                scroller.abortAnimation()
                scrollTo(scrollXForPage(currentPage), 0)
            }
            updatePageTransforms()
        }

    var transitionEffect: PageTransitionEffect = PageTransitionEffect.SLIDE
        set(value) {
            field = value
            updatePageTransforms()
        }

    private fun updatePageTransforms() {
        if (width <= 0) return
        val maximum = (childCount - 1) * width
        for (index in 0 until childCount) {
            val page = getChildAt(index)
            // Add/remove animations own these properties until completion or cancellation.
            if (page === incomingRemovalPage || page.getTag(R.id.page_removal_outline) != null) continue
            // Move the original edge page into the adjacent virtual slot. Widgets are never cloned.
            page.translationX = when {
                infiniteScroll && childCount > 1 && scrollX < 0 && visualIndex(index) == childCount - 1 -> -childCount * width.toFloat()
                infiniteScroll && childCount > 1 && scrollX > maximum && visualIndex(index) == 0 -> childCount * width.toFloat()
                else -> 0f
            }
            val position = (page.left + page.translationX - scrollX) / width
            applyPageTransform(page, position, transitionEffect, effectStrength, snapDurationScale > 0f)
        }
        val visual = scrollX.toFloat() / width
        onPageScrolled?.invoke(if (isRtl) childCount - 1 - visual else visual)
    }

    fun snapToPage(page: Int, animate: Boolean = true) {
        snapToSlot(page, animate)
    }

    private fun snapToSlot(page: Int, animate: Boolean, virtualSlot: Int? = null, velocity: Float = 0f) {
        if (isEmpty()) return
        cancelIncomingPageSlide()
        val target = page.coerceIn(0, childCount - 1)
        val changed = target != currentPage
        currentPage = target
        val slot = virtualSlot ?: when {
            infiniteScroll && childCount > 1 && visualIndex(target) == 0 && scrollX > (childCount - 1.5f) * width -> childCount
            infiniteScroll && childCount > 1 && visualIndex(target) == childCount - 1 && scrollX < width * .5f -> -1
            else -> visualIndex(target)
        }
        val dx = slot * width - scrollX
        if (!scroller.isFinished) scroller.abortAnimation()
        if (animate && width > 0 && dx != 0 && snapDurationScale > 0f) {
            scroller.startScroll(scrollX, 0, dx, 0, (PageMotion.duration(dx, width, snapDurationMs, velocity) * snapDurationScale).toInt().coerceAtLeast(1))
            postInvalidateOnAnimation()
        } else {
            scrollTo(scrollXForPage(target), 0)
        }
        onPageChanged?.invoke(currentPage, childCount)
        updatePageTransforms()
        if (changed) sendAccessibilityEventForPage()
    }

    /** Move one page towards the visual left or right edge (used by edge hover while dragging). */
    fun scrollTowardsEdge(left: Boolean): Boolean {
        val visual = visualIndex(currentPage) + if (left) -1 else 1
        if (visual !in 0 until childCount) return false
        snapToPage(pageForVisual(visual))
        return true
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        updateWallpaperOffsets(force = false)
        updatePageTransforms()
        if (l != oldl && (mode == Mode.PAGING || !scroller.isFinished)) onUserScroll?.invoke()
    }

    override fun onDetachedFromWindow() {
        mainHandler.removeCallbacks(removalTimeout)
        cancelIncomingPageSlide()
        scroller.abortAnimation()
        resetTouch()
        scrollTo(scrollXForPage(currentPage), 0)
        super.onDetachedFromWindow()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateWallpaperOffsets(force = true)
    }

    /** Tells the wallpaper where we are; a binder call, so it is skipped when nothing changed. */
    private fun updateWallpaperOffsets(force: Boolean) {
        if (!wallpaperUpdatesEnabled) return
        val manager = wallpaperManager ?: return
        val token = windowToken ?: return
        val x = WallpaperOffsets.xOffset(scrollX, width, childCount, wallpaperScrolling)
        val step = WallpaperOffsets.xStep(childCount, wallpaperScrolling)
        if (!force && x == lastWallpaperX && step == lastWallpaperStep) return
        lastWallpaperX = x
        lastWallpaperStep = step
        try {
            manager.setWallpaperOffsetSteps(step, 1f)
            manager.setWallpaperOffsets(token, x, WallpaperOffsets.CENTER)
        } catch (_: Exception) {
            // Best effort: a wallpaper service that rejects offsets must not break paging.
        }
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollTo(scroller.currX, 0)
            postInvalidateOnAnimation()
        }
        if (scroller.isFinished && mode != Mode.PAGING) normalizeWrap()
    }

    private fun normalizeWrap() {
        if (width <= 0 || childCount <= 1) return
        val maximum = (childCount - 1) * width
        if (scrollX < 0 || scrollX > maximum) scrollTo(scrollXForPage(currentPage), 0)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(w, h)
        val childW = MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY)
        val childH = MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY)
        for (i in 0 until childCount) getChildAt(i).measure(childW, childH)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val w = r - l
        for (i in 0 until childCount) {
            val left = visualIndex(i) * w
            getChildAt(i).layout(left, 0, left + w, b - t)
        }
        if (currentPage >= childCount) currentPage = (childCount - 1).coerceAtLeast(0)
        if (scroller.isFinished && mode != Mode.PAGING) scrollTo(scrollXForPage(currentPage), 0)
        updateWallpaperOffsets(force = changed)
        updatePageTransforms()
    }

    override fun onViewAdded(child: View?) {
        super.onViewAdded(child)
        onPageChanged?.invoke(currentPage.coerceAtMost(childCount - 1), childCount)
    }

    override fun onViewRemoved(child: View?) {
        if (child === incomingRemovalPage) cancelIncomingPageSlide()
        super.onViewRemoved(child)
        if (removingPage >= 0) finishPageRemoval()
        if (currentPage >= childCount) currentPage = (childCount - 1).coerceAtLeast(0)
        if (isEmpty()) {
            scroller.abortAnimation()
            resetTouch()
            scrollTo(0, 0)
        }
        onPageChanged?.invoke(currentPage, childCount)
    }

    // --- Page removal ---------------------------------------------------------------------------

    /** Page shrunk away by [animatePageRemoval] and waiting for the layout to drop it; -1 if none. */
    private var removingPage = -1
    private var incomingRemovalPage: View? = null
    private val removalTimeout = Runnable { cancelPageRemoval() }
    private val mainHandler = Handler(Looper.getMainLooper())

    private fun removalDuration(baseMs: Long): Long = (baseMs * snapDurationScale).toLong().coerceAtLeast(1L)

    /**
     * Shrinks and fades [page] out, then calls [commit] to remove it from the layout. When the layout
     * drops the page, the page that takes its place slides in, so the removal is visible even when
     * the neighbouring page is empty too. With animations off, [commit] runs at once.
     */
    fun animatePageRemoval(page: Int, commit: () -> Unit) {
        val view = getChildAt(page)
        if (view == null || snapDurationScale <= 0f || width <= 0) {
            commit()
            return
        }
        cancelPageRemoval()
        removingPage = page
        showRemovalOutline(view)
        view.pivotX = view.width / 2f
        view.pivotY = view.height / 2f
        // Shrink first, with the outline fully visible, then fade: the page is seen leaving.
        view.animate()
            .setStartDelay(0)
            .scaleX(REMOVED_SCALE).scaleY(REMOVED_SCALE)
            .setDuration(removalDuration(REMOVE_SHRINK_MS))
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                view.animate()
                    .setStartDelay(0)
                    .alpha(0f)
                    .setDuration(removalDuration(REMOVE_FADE_MS))
                    .setInterpolator(AccelerateInterpolator())
                    .withEndAction {
                        commit()
                        // The removal can be refused (e.g. an item landed on the page meanwhile).
                        mainHandler.postDelayed(removalTimeout, REMOVE_TIMEOUT_MS)
                    }
                    .start()
            }
            .start()
    }

    /** The removal was refused or never reached the layout: brings the page back. */
    fun cancelPageRemoval() {
        mainHandler.removeCallbacks(removalTimeout)
        cancelIncomingPageSlide()
        val view = getChildAt(removingPage)
        removingPage = -1
        view ?: return
        hideRemovalOutline(view)
        view.animate().cancel()
        view.animate().setStartDelay(0).scaleX(1f).scaleY(1f).alpha(1f)
            .setDuration(removalDuration(REMOVE_SLIDE_MS))
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    /**
     * Slides to a page that was just added and grows it in from the same outline used for removal,
     * so a new empty page is noticeable. With animations off it only jumps there.
     */
    fun showAddedPage(page: Int) {
        val view = getChildAt(page) ?: return
        // A page added in this frame has no size yet; its outline needs one.
        view.doOnLayout { if (getChildAt(page) === view) growInAddedPage(page, view) }
    }

    private fun growInAddedPage(page: Int, view: View) {
        val animate = snapDurationScale > 0f && width > 0
        snapToPage(page, animate)
        if (!animate) return
        showRemovalOutline(view)
        view.pivotX = view.width / 2f
        view.pivotY = view.height / 2f
        view.scaleX = REMOVED_SCALE
        view.scaleY = REMOVED_SCALE
        view.animate().cancel()
        view.animate()
            .scaleX(1f).scaleY(1f)
            .setStartDelay(removalDuration(SNAP_DURATION_MS.toLong()))
            .setDuration(removalDuration(REMOVE_SLIDE_MS))
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                view.animate().setStartDelay(0)
                // Leave the outline briefly on the full-size page, then fade it out.
                val outline = view.getTag(R.id.page_removal_outline) as? GradientDrawable ?: return@withEndAction
                android.animation.ValueAnimator.ofInt(255, 0).apply {
                    duration = removalDuration(REMOVE_FADE_MS * 2)
                    startDelay = removalDuration(REMOVE_FADE_MS)
                    addUpdateListener { outline.alpha = it.animatedValue as Int }
                    doOnEndCompat { hideRemovalOutline(view) }
                    start()
                }
            }
            .start()
    }

    private fun android.animation.Animator.doOnEndCompat(action: () -> Unit) {
        addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) = action()
        })
    }

    /**
     * A page-shaped card drawn over the page while it shrinks away. An empty page has nothing else to
     * shrink, so without it removing one empty page next to another would look like nothing happened.
     */
    private fun showRemovalOutline(view: View) {
        val density = resources.displayMetrics.density
        val inset = (REMOVE_OUTLINE_INSET_DP * density).roundToInt()
        val outline = GradientDrawable().apply {
            cornerRadius = REMOVE_OUTLINE_RADIUS_DP * density
            setColor(Color.argb(40, 255, 255, 255))
            setStroke((2 * density).roundToInt(), Color.argb(140, 255, 255, 255))
            setBounds(inset, inset, view.width - inset, view.height - inset)
        }
        hideRemovalOutline(view)
        view.setTag(R.id.page_removal_outline, outline)
        view.overlay.add(outline)
    }

    private fun hideRemovalOutline(view: View) {
        (view.getTag(R.id.page_removal_outline) as? GradientDrawable)?.let { view.overlay.remove(it) }
        view.setTag(R.id.page_removal_outline, null)
    }

    /** The layout dropped a page; the view that faded out now shows its successor's content. */
    private fun finishPageRemoval() {
        mainHandler.removeCallbacks(removalTimeout)
        cancelIncomingPageSlide()
        val removed = removingPage
        removingPage = -1
        for (i in 0 until childCount) getChildAt(i).apply {
            hideRemovalOutline(this)
            animate().cancel()
            scaleX = 1f
            scaleY = 1f
            alpha = 1f
            translationX = 0f
        }
        if (isEmpty()) return
        val fromEnd = removed < childCount
        currentPage = removed.coerceAtMost(childCount - 1)
        if (!fromEnd && !isRtl && width > 0) {
            // The last page went: glide back from where it was to the new last page.
            if (!scroller.isFinished) scroller.abortAnimation()
            scroller.startScroll(scrollX, 0, scrollXForPage(currentPage) - scrollX, 0, removalDuration(REMOVE_SLIDE_MS).toInt())
            postInvalidateOnAnimation()
            return
        }
        // The next page moves in from the end side to fill the gap (or, after the last page, the
        // previous one from the start side).
        val endSide = if (isRtl) -1f else 1f
        val incoming = getChildAt(currentPage)
        incomingRemovalPage = incoming
        incoming.translationX = (if (fromEnd) endSide else -endSide) * width * SLIDE_IN_FRACTION
        incoming.alpha = 0f
        incoming.animate().setStartDelay(0).translationX(0f).alpha(1f)
            .setDuration(removalDuration(REMOVE_SLIDE_MS))
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                if (incomingRemovalPage === incoming) {
                    incomingRemovalPage = null
                    updatePageTransforms()
                }
            }
            .start()
    }

    private fun cancelIncomingPageSlide() {
        val incoming = incomingRemovalPage ?: return
        incomingRemovalPage = null
        incoming.animate().cancel()
        incoming.translationX = 0f
        incoming.alpha = 1f
        updatePageTransforms()
    }

    // --- Touch ------------------------------------------------------------------------------

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        trackVelocity(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                lastX = ev.x
                beginGesture()
                mode = if (!scroller.isFinished) {
                    scroller.abortAnimation()
                    Mode.PAGING
                } else {
                    Mode.IDLE
                }
            }
            MotionEvent.ACTION_MOVE -> if (mode == Mode.IDLE) classify(ev)
            MotionEvent.ACTION_POINTER_DOWN -> {
                finishGesture(cancelled = true)
                mode = Mode.IGNORED
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (mode != Mode.PAGING && mode != Mode.FEED) resetTouch()
            }
        }
        return mode == Mode.PAGING || mode == Mode.FEED
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        trackVelocity(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                lastX = ev.x
                beginGesture()
                if (!scroller.isFinished) {
                    scroller.abortAnimation()
                    mode = Mode.PAGING
                }
                return true // receive the rest of the gesture on empty workspace areas
            }
            MotionEvent.ACTION_MOVE -> {
                if (mode == Mode.IDLE) classify(ev)
                if (mode == Mode.PAGING) {
                    val dx = (lastX - ev.x).roundToInt()
                    val max = ((childCount - 1).coerceAtLeast(0)) * width
                    if (infiniteScroll && childCount > 1) {
                        scrollTo((scrollX + dx).coerceIn(-width, max + width), 0)
                    } else {
                        edgePull += dx
                        val raw = gestureStartX + edgePull
                        val extra = if (snapDurationScale > 0f && edgeFeedback == PageEdgeFeedback.RESISTANCE) width * .12f else 0f
                        val resisted = when {
                            raw < 0 -> -extra * (1f - 1f / (1f + -raw / width.coerceAtLeast(1)))
                            raw > max -> max + extra * (1f - 1f / (1f + (raw - max) / width.coerceAtLeast(1)))
                            else -> raw
                        }
                        scrollTo(resisted.roundToInt(), 0)
                    }
                    lastX = ev.x
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                finishGesture(cancelled = true)
                mode = Mode.IGNORED
            }
            MotionEvent.ACTION_UP -> {
                finishGesture(cancelled = false)
                if (mode == Mode.IDLE) performClick()
                resetTouch()
            }
            MotionEvent.ACTION_CANCEL -> {
                finishGesture(cancelled = true)
                resetTouch()
            }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    private fun beginGesture() {
        cancelIncomingPageSlide()
        if (width > 0 && infiniteScroll && childCount > 1 && !scroller.isFinished) {
            if (scrollX < -width / 2) scrollTo(scrollX + childCount * width, 0)
            else if (scrollX > (childCount - .5f) * width) scrollTo(scrollX - childCount * width, 0)
        }
        gestureStartX = scrollX
        gestureStartSlot = (scrollX.toFloat() / width.coerceAtLeast(1)).roundToInt()
        edgePull = 0f
    }

    private fun classify(ev: MotionEvent) {
        val dx = ev.x - downX
        val dy = ev.y - downY
        if (PageMotion.isHorizontal(dx, dy, touchSlop.toFloat(), swipeSensitivity, horizontalDominance)) {
            mode = when {
                ev.pointerCount == 1 && currentPage == 0 && onStartEdgeSwipe != null &&
                    (if (isRtl) dx < 0 else dx > 0) -> Mode.FEED
                childCount > 1 -> Mode.PAGING
                else -> Mode.IGNORED
            }
            // Keep the first accepted movement, subtracting only slop. A coalesced one-MOVE
            // swipe must still move a page rather than losing its entire distance.
            lastX = if (mode == Mode.PAGING) downX + (if (dx > 0f) 1 else -1) * touchSlop * swipeSensitivity.slopMultiplier else ev.x
            parent?.requestDisallowInterceptTouchEvent(true)
        } else if (abs(dy) > touchSlop && abs(dy) > abs(dx)) {
            mode = Mode.IGNORED // vertical: handled by the enclosing VerticalSwipeLayout
        }
    }

    private fun finishGesture(cancelled: Boolean) {
        if (isEmpty()) return
        val tracker = velocityTracker
        when (mode) {
            Mode.FEED -> {
                tracker?.computeCurrentVelocity(1000)
                val direction = if (isRtl) -1 else 1
                val distance = (lastGestureX - downX) * direction
                val velocity = (tracker?.xVelocity ?: 0f) * direction
                if (!cancelled && (distance > width * WRAP_TRIGGER ||
                        (distance > touchSlop && velocity > minFlingVelocity))) {
                    onStartEdgeSwipe?.invoke()
                }
            }
            Mode.PAGING -> {
                tracker?.computeCurrentVelocity(1000)
                // A drift during an upward/downward swipe must not fling to the next page.
                val vx = PageMotion.pageFlingVelocity(tracker?.xVelocity ?: 0f, tracker?.yVelocity ?: 0f)
                val width = width.coerceAtLeast(1)
                val visual = PageMotion.settle(scrollX.toFloat() / width, gestureStartSlot,
                    (scrollX - gestureStartX).toFloat(), -vx, minFlingVelocity * 4f, cancelled)
                val wraps = infiniteScroll && childCount > 1
                val bounded = visual.coerceIn(if (wraps) -1 else 0, if (wraps) childCount else childCount - 1)
                val target = PagerMath.settleVisual(bounded, childCount, wraps)
                snapToSlot(pageForVisual(target), animate = true, virtualSlot = bounded, velocity = vx)
            }
            else -> Unit
        }
    }

    private fun trackVelocity(ev: MotionEvent) {
        lastGestureX = ev.x
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            velocityTracker?.clear()
        }
        val tracker = velocityTracker ?: VelocityTracker.obtain().also { velocityTracker = it }
        tracker.addMovement(ev)
    }

    private var lastGestureX = 0f

    private fun resetTouch() {
        mode = Mode.IDLE
        velocityTracker?.recycle()
        velocityTracker = null
    }

    // --- Accessibility: page scrolling without gestures ----------------------------------------

    override fun getAccessibilityClassName(): CharSequence = WorkspacePager::class.java.name

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.isScrollable = childCount > 1
        val wraps = infiniteScroll && childCount > 1
        if (currentPage > 0 || wraps) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
        if (currentPage < childCount - 1 || wraps) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean = when (action) {
        AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> {
            snapToPage(PagerMath.step(currentPage, +1, childCount, infiniteScroll))
            true
        }
        AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> {
            snapToPage(PagerMath.step(currentPage, -1, childCount, infiniteScroll))
            true
        }
        else -> super.performAccessibilityAction(action, arguments)
    }

    private fun sendAccessibilityEventForPage() {
        val text = context.getString(R.string.home_page_indicator_description, currentPage + 1, childCount)
        ViewCompat.setStateDescription(this, text)
    }

    private companion object {
        const val SNAP_DURATION_MS = 250
        const val REMOVE_SHRINK_MS = 220L
        const val REMOVE_FADE_MS = 160L
        const val REMOVE_SLIDE_MS = 280L
        const val REMOVE_TIMEOUT_MS = 2_000L
        const val REMOVED_SCALE = 0.85f
        const val REMOVE_OUTLINE_INSET_DP = 12f
        const val REMOVE_OUTLINE_RADIUS_DP = 28f
        const val SLIDE_IN_FRACTION = 0.4f
        const val WRAP_TRIGGER = 0.15f
    }
}

/** Page arithmetic for [WorkspacePager], kept pure for unit tests. */
object PagerMath {

    /**
     * Visual slot to settle on after a swipe. With [wrap], a slot past either end wraps around;
     * otherwise it clamps to the first/last page.
     */
    fun settleVisual(visual: Int, pageCount: Int, wrap: Boolean): Int {
        if (pageCount <= 0) return 0
        return if (wrap) Math.floorMod(visual, pageCount) else visual.coerceIn(0, pageCount - 1)
    }

    /**
     * Desktop pages whose icons, folder previews and widget views should be bound (memory window).
     *
     * At rest ([position] equals [current]) it is the current page and one neighbour on each side
     * (wrapping with [wrap]). While the pager moves, pages are only added: [previous] stays bound
     * and every page within one page of what is on screen is bound ahead of arrival, so a swipe or
     * a multi-page snap never shows an unbound page. Releasing waits until the pager settles,
     * which also keeps a swipe that hovers on a page boundary from rebinding over and over.
     * [pinned] pages (e.g. the page a drag started from) stay bound.
     */
    fun bindWindow(
        current: Int,
        position: Float,
        pageCount: Int,
        wrap: Boolean,
        previous: Set<Int> = emptySet(),
        pinned: Set<Int> = emptySet(),
        radius: Int = BIND_RADIUS,
    ): Set<Int> {
        if (pageCount <= 0) return emptySet()
        val wraps = wrap && pageCount > 1
        val result = HashSet<Int>()
        fun addAround(center: Int) {
            for (delta in -radius..radius) {
                val page = center + delta
                if (wraps) result += Math.floorMod(page, pageCount) else if (page in 0 until pageCount) result += page
            }
        }
        val anchor = current.coerceIn(0, pageCount - 1)
        addAround(anchor)
        if (!isSettled(anchor, position, pageCount, wraps)) {
            result += previous.filter { it in 0 until pageCount }
            val low = kotlin.math.floor(position).toInt()
            val high = kotlin.math.ceil(position).toInt()
            for (center in low..high) addAround(if (wraps) center else center.coerceIn(0, pageCount - 1))
        }
        result += pinned.filter { it in 0 until pageCount }
        return result
    }

    /** True when [position] shows exactly page [current] (also across the wrap seam). */
    fun isSettled(current: Int, position: Float, pageCount: Int, wrap: Boolean): Boolean {
        val distance = kotlin.math.abs(position - current)
        return distance < SETTLE_EPSILON || (wrap && kotlin.math.abs(distance - pageCount) < SETTLE_EPSILON)
    }

    /** Pages kept bound on each side of the current one. */
    const val BIND_RADIUS = 1
    private const val SETTLE_EPSILON = 0.001f

    /** Page index [delta] steps from [page], wrapping when [wrap] is set. */
    fun step(page: Int, delta: Int, pageCount: Int, wrap: Boolean): Int {
        if (pageCount <= 0) return 0
        val next = page + delta
        return if (wrap) Math.floorMod(next, pageCount) else next.coerceIn(0, pageCount - 1)
    }
}
