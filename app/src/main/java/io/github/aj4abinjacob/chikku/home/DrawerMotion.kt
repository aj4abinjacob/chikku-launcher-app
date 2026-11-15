package io.github.aj4abinjacob.chikku.home

import android.animation.ValueAnimator
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerTransition
import android.view.View
import androidx.core.view.isVisible
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import kotlin.math.abs

/**
 * Moves the app drawer between closed (below the screen) and open, following the finger during a
 * swipe and settling with the release velocity. Progress is 0 when closed and 1 when open.
 *
 * Closed means INVISIBLE rather than GONE, so the drawer's own views stay laid out. Invisible views
 * neither receive touches nor register as drop targets. [onShownChanged] reports every change so
 * the drawer content can release its app rows while closed and bind them again as soon as an open
 * gesture or animation starts (while the drawer is still below the screen).
 */
internal class DrawerMotion(
    private val drawer: View,
    /** Called when a swipe is released; the activity records the new state in the ViewModel. */
    private val onSwipeSettled: (open: Boolean) -> Unit,
) {

    private val minFlingVelocity = ViewConfiguration.get(drawer.context).scaledMinimumFlingVelocity * FLING_MULTIPLIER
    private val interpolator = DecelerateInterpolator(1.5f)

    /**
     * Duration multiplier from the user's animation speed (`theme.Motion.scale`); 0 makes every
     * open/close jump straight to its end state.
     */
    var animationScale: Float = 1f

    var transition: DrawerTransition = DrawerTransition.SLIDE
        set(value) {
            if (field == value) return
            field = value
            jumpTo(targetOpen)
        }

    /**
     * Called with true when the drawer becomes visible (an open swipe or animation starts) and with
     * false once it is fully hidden again. HomeActivity forwards it to `DrawerController.onDrawerShown`.
     */
    var onShownChanged: (shown: Boolean) -> Unit = {}

    private fun setShown(shown: Boolean) {
        drawer.visibility = if (shown) View.VISIBLE else View.INVISIBLE
        onShownChanged(shown)
    }

    var canPullToSearch: () -> Boolean = { false }
    var onPullToSearch: () -> Unit = {}
    private var pullingToSearch = false
    private var animator: ValueAnimator? = null
    private var displayedProgress = 0f

    /** Where the drawer is or is animating to. */
    var targetOpen = false
        private set

    private var dragging = false
    private var dragStartProgress = 0f

    private val travel: Float get() = (drawer.parent as? View)?.height?.toFloat()?.takeIf { it > 0f } ?: drawer.height.toFloat()

    private val progress: Float
        get() {
            return displayedProgress
        }

    private fun setProgress(value: Float) {
        displayedProgress = value.coerceIn(0f, 1f)
        val remaining = 1f - displayedProgress
        drawer.translationY = if (transition == DrawerTransition.SLIDE) remaining * travel else 0f
        drawer.alpha = if (transition == DrawerTransition.SLIDE) 1f else displayedProgress
        val scale = if (transition == DrawerTransition.SCALE) 0.92f + displayedProgress * 0.08f else 1f
        drawer.scaleX = scale
        drawer.scaleY = scale
    }

    /** Show the final state without animation (first render, or when the drawer must vanish now). */
    fun jumpTo(open: Boolean) {
        dragging = false
        animator?.cancel()
        animator = null
        drawer.animate().cancel()
        targetOpen = open
        setProgress(if (open) 1f else 0f)
        setShown(open)
    }

    /** Animate to [open] unless already there or on the way (e.g. after a swipe settled). */
    fun animateTo(open: Boolean, velocityY: Float = 0f) {
        if (open == targetOpen && !dragging) return
        dragging = false
        targetOpen = open
        val travel = travel
        if (travel <= 0f || animationScale <= 0f || (!open && drawer.visibility != View.VISIBLE)) {
            jumpTo(open)
            return
        }
        if (drawer.visibility != View.VISIBLE) setShown(true)
        settle(if (open) 1f else 0f, velocityY)
    }

    /**
     * Predictive back on the open drawer: it gives way by up to [BACK_PEEK] of its travel while
     * the swipe lasts, so letting go visibly continues the close. Back then closes it from there.
     */
    fun backProgress(progress: Float) {
        if (!targetOpen || dragging || animationScale <= 0f || drawer.visibility != View.VISIBLE) return
        animator?.cancel()
        animator = null
        setProgress(1f - BACK_PEEK * progress.coerceIn(0f, 1f))
    }

    /** The back swipe was abandoned: settle the drawer fully open again. */
    fun cancelBack() {
        if (targetOpen && !dragging && displayedProgress < 1f) settle(1f, 0f)
    }

    private fun settle(target: Float, velocityY: Float) {
        val travel = travel
        val from = progress
        val distance = abs(from - target) * travel
        animator?.cancel()
        animator = ValueAnimator.ofFloat(from, target).apply {
            duration = settleDurationMs(distance, travel, velocityY)
            interpolator = this@DrawerMotion.interpolator
            addUpdateListener { animation ->
                setProgress(animation.animatedValue as Float)
                if (displayedProgress == 0f && !targetOpen && !dragging && drawer.isVisible) setShown(false)
            }
            start()
        }
    }

    private fun startDrag() {
        animator?.cancel()
        animator = null
        drawer.animate().cancel()
        if (drawer.visibility != View.VISIBLE) {
            setProgress(0f)
            setShown(true)
        }
        dragging = true
        dragStartProgress = progress
    }

    private fun drag(offsetY: Float) {
        if (!dragging || pullingToSearch) return
        val travel = travel
        if (travel > 0f) setProgress(dragStartProgress - offsetY / travel)
    }

    private fun release(velocityY: Float, cancelled: Boolean) {
        if (!dragging) return
        if (pullingToSearch) {
            pullingToSearch = false
            dragging = false
            jumpTo(open = true)
            if (!cancelled) onPullToSearch()
            return
        }
        val wasOpen = targetOpen
        val open = if (cancelled) wasOpen else shouldSettleOpen(progress, velocityY, minFlingVelocity.toFloat(), wasOpen)
        animateTo(open, velocityY)
        if (open != wasOpen) onSwipeSettled(open)
    }

    /** Whether a home swipe up may open the drawer (false when the user chose "button only"). */
    var swipeOpenAllowed: () -> Boolean = { true }

    /** Listener for the home surface: a swipe up opens the drawer while it is closed. */
    val homeSwipes: VerticalSwipeLayout.Listener =
        swipes { it == SwipeDirection.UP && !targetOpen && !dragging && swipeOpenAllowed() }

    /** Listener for the drawer: a swipe down closes it once [listAtTop] allows. */
    fun drawerSwipes(listAtTop: () -> Boolean): VerticalSwipeLayout.Listener =
        swipes { it == SwipeDirection.DOWN && targetOpen && !dragging && listAtTop() }

    private fun swipes(canStart: (SwipeDirection) -> Boolean) = object : VerticalSwipeLayout.Listener {
        override fun canStartSwipe(direction: SwipeDirection) = canStart(direction)
        override fun onSwipeStart(direction: SwipeDirection) {
            pullingToSearch = direction == SwipeDirection.DOWN && targetOpen && canPullToSearch()
            startDrag()
        }
        override fun onSwipeMove(offsetY: Float) = drag(offsetY)
        override fun onSwipeEnd(velocityY: Float, cancelled: Boolean) = release(velocityY, cancelled)
    }

    private fun settleDurationMs(distance: Float, travel: Float, velocityY: Float): Long {
        val byDistance = MAX_SETTLE_MS * distance / travel
        val speed = abs(velocityY)
        // A fast release finishes about as fast as the finger was moving, decelerating to rest.
        val byVelocity = if (speed > 0f) 1000f * distance / speed * 1.5f else byDistance
        val base = minOf(byDistance, byVelocity).toLong().coerceIn(MIN_SETTLE_MS, MAX_SETTLE_MS)
        return (base * animationScale).toLong().coerceAtLeast(1L)
    }

    private companion object {
        const val FLING_MULTIPLIER = 4
        const val MIN_SETTLE_MS = 120L
        const val MAX_SETTLE_MS = 320L
        const val BACK_PEEK = 0.15f
    }
}

/**
 * Whether a released drawer should settle open. A fling decides by its direction; otherwise the
 * drawer only needs to be pulled a quarter of the way from where it started.
 */
internal fun shouldSettleOpen(progress: Float, velocityY: Float, minFlingVelocity: Float, startedOpen: Boolean): Boolean = when {
    velocityY < -minFlingVelocity -> true
    velocityY > minFlingVelocity -> false
    startedOpen -> progress > 1f - SETTLE_THRESHOLD
    else -> progress > SETTLE_THRESHOLD
}

private const val SETTLE_THRESHOLD = 0.25f
