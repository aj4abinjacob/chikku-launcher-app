package io.github.aj4abinjacob.chikku.home.gestures

import io.github.aj4abinjacob.chikku.core.model.AppCustomization
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.GestureAction
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerOpenMethod
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.home.HomeItemUi
import io.github.aj4abinjacob.chikku.home.ItemContent
import io.github.aj4abinjacob.chikku.home.SwipeDirection
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot

/*
 * Pure gesture decisions, kept free of Android types so they are unit-tested on the JVM. The
 * views (VerticalSwipeLayout, HomeGestureController) only feed coordinates and times in.
 */

/** Two-finger gestures on the home surface. */
enum class MultiTouchGesture {
    PINCH_IN, PINCH_OUT, TWO_FINGER_SWIPE_UP, TWO_FINGER_SWIPE_DOWN,
    ROTATE_CLOCKWISE, ROTATE_COUNTERCLOCKWISE,
}

/**
 * Classifies a two-finger movement once it is clearly a pinch or a swipe.
 *
 * [startSpan]/[span] are the distances between the two fingers at the start and now;
 * [centroidDx]/[centroidDy] how far their midpoint moved (up is negative). Returns null while the
 * movement is still below [minDistance] or ambiguous (as much spreading as travelling).
 */
fun classifyMultiTouch(
    startSpan: Float,
    span: Float,
    centroidDx: Float,
    centroidDy: Float,
    minDistance: Float,
    rotationDegrees: Float = 0f,
): MultiTouchGesture? {
    val spanDelta = span - startSpan
    val travel = hypot(centroidDx, centroidDy)
    return when {
        rotationDegrees.isFinite() && abs(rotationDegrees) >= 35f &&
            startSpan >= minDistance * 2 && span >= minDistance * 2 &&
            abs(spanDelta) <= (startSpan * 0.35f).coerceAtLeast(minDistance) &&
            travel <= startSpan * 0.4f ->
                if (rotationDegrees > 0f) MultiTouchGesture.ROTATE_CLOCKWISE else MultiTouchGesture.ROTATE_COUNTERCLOCKWISE
        abs(spanDelta) >= minDistance && abs(spanDelta) > travel * PINCH_DOMINANCE ->
            if (spanDelta < 0) MultiTouchGesture.PINCH_IN else MultiTouchGesture.PINCH_OUT
        abs(centroidDy) >= minDistance && abs(centroidDy) > abs(centroidDx) && abs(centroidDy) > abs(spanDelta) * PINCH_DOMINANCE ->
            if (centroidDy < 0) MultiTouchGesture.TWO_FINGER_SWIPE_UP else MultiTouchGesture.TWO_FINGER_SWIPE_DOWN
        else -> null
    }
}

/** Spreading must beat travel (and vice versa) by this factor, so a sloppy swipe is not a pinch. */
private const val PINCH_DOMINANCE = 1.2f

/** Signed incremental rotation, including crossing the -180/180-degree boundary. */
internal fun rotationDeltaDegrees(previous: Float, next: Float): Float {
    if (!previous.isFinite() || !next.isFinite()) return 0f
    var delta = (next - previous) % 360f
    if (delta > 180f) delta -= 360f
    if (delta < -180f) delta += 360f
    return delta
}

/**
 * Whether a released one-finger swipe in [direction] should run its action: it travelled at least
 * [minDistance] or was flung in its own direction faster than [minFlingVelocity] (px/s, up negative).
 */
fun isSwipeCommitted(
    direction: SwipeDirection,
    offsetY: Float,
    velocityY: Float,
    minDistance: Float,
    minFlingVelocity: Float,
): Boolean = when (direction) {
    SwipeDirection.UP -> offsetY <= -minDistance || (velocityY < -minFlingVelocity && offsetY < 0f)
    SwipeDirection.DOWN -> offsetY >= minDistance || (velocityY > minFlingVelocity && offsetY > 0f)
    SwipeDirection.NONE -> false
}

/**
 * How far an icon follows a one-finger swipe in [direction] while the finger is down: it eases
 * towards [maxNudge] (rubber band, about two thirds of the way at [travel]) and stays put while the
 * finger is back on the other side of where it started. Signed like [offsetY] (up is negative).
 */
fun iconSwipeNudge(direction: SwipeDirection, offsetY: Float, maxNudge: Float, travel: Float): Float {
    val along = when (direction) {
        SwipeDirection.UP -> -offsetY
        SwipeDirection.DOWN -> offsetY
        SwipeDirection.NONE -> return 0f
    }
    if (along <= 0f || maxNudge <= 0f || travel <= 0f) return 0f
    val pulled = maxNudge * (1f - exp(-along / travel))
    return if (direction == SwipeDirection.UP) -pulled else pulled
}

/**
 * Detects a double tap from raw touch input, ignoring taps that land on an interactive child (an
 * icon, a button) or that move, last too long (long press) or come too far apart.
 */
class DoubleTapTracker(
    private val maxTapDurationMs: Long,
    private val doubleTapTimeoutMs: Long,
    private val touchSlop: Float,
    private val doubleTapSlop: Float,
) {
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var tracking = false

    /** The previous completed tap, if it can still start a double tap. */
    private var lastTapX = 0f
    private var lastTapY = 0f
    private var lastTapUpTime = Long.MIN_VALUE
    private var hasLastTap = false

    /** Available after DOWN, before MOVE invalidates tap recognition. */
    val isSecondTap: Boolean get() = tracking && hasLastTap

    fun onDown(x: Float, y: Float, timeMs: Long, overInteractiveChild: Boolean) {
        tracking = !overInteractiveChild
        if (!tracking) hasLastTap = false
        downX = x
        downY = y
        downTime = timeMs
        if (hasLastTap && (timeMs - lastTapUpTime > doubleTapTimeoutMs || !near(x, y, lastTapX, lastTapY, doubleTapSlop))) {
            hasLastTap = false
        }
    }

    fun onMove(x: Float, y: Float) {
        if (tracking && !near(x, y, downX, downY, touchSlop)) cancel()
    }

    /** Another finger, a cancel, or a gesture taken by someone else: no tap. */
    fun cancel() {
        tracking = false
        hasLastTap = false
    }

    /** Whether the last release ended a tap (short, still, on empty space), single or double. */
    var lastUpWasTap = false
        private set

    /** Returns true when this release completes a double tap. */
    fun onUp(timeMs: Long): Boolean {
        lastUpWasTap = false
        if (!tracking) return false
        tracking = false
        if (timeMs - downTime > maxTapDurationMs) {
            hasLastTap = false
            return false
        }
        lastUpWasTap = true
        if (hasLastTap) {
            hasLastTap = false
            return true
        }
        hasLastTap = true
        lastTapX = downX
        lastTapY = downY
        lastTapUpTime = timeMs
        return false
    }

    private fun near(x: Float, y: Float, x0: Float, y0: Float, slop: Float) = hypot(x - x0, y - y0) <= slop
}

/**
 * The action a one-finger home swipe in [direction] runs. Swipe up only opens the drawer when the
 * drawer's open method allows swiping (the drawer settings own that choice).
 */
fun homeSwipeAction(settings: LauncherSettings, direction: SwipeDirection): GestureAction {
    val gestures = settings.gestures
    return when (direction) {
        SwipeDirection.UP ->
            if (gestures.swipeUp == GestureAction.OpenDrawer && settings.drawer.openMethod == DrawerOpenMethod.BUTTON) {
                GestureAction.None
            } else {
                gestures.swipeUp
            }
        SwipeDirection.DOWN -> gestures.swipeDown
        SwipeDirection.NONE -> GestureAction.None
    }
}

/** Home action bound to a two-finger gesture. */
fun multiTouchAction(settings: LauncherSettings, gesture: MultiTouchGesture): GestureAction = when (gesture) {
    MultiTouchGesture.PINCH_IN -> settings.gestures.pinchIn
    MultiTouchGesture.PINCH_OUT -> settings.gestures.pinchOut
    MultiTouchGesture.TWO_FINGER_SWIPE_UP -> settings.gestures.twoFingerSwipeUp
    MultiTouchGesture.TWO_FINGER_SWIPE_DOWN -> settings.gestures.twoFingerSwipeDown
    MultiTouchGesture.ROTATE_CLOCKWISE -> settings.gestures.rotateClockwise
    MultiTouchGesture.ROTATE_COUNTERCLOCKWISE -> settings.gestures.rotateCounterclockwise
}

fun horizontalSwipeAction(settings: LauncherSettings, left: Boolean): GestureAction =
    if (left) settings.gestures.swipeLeft else settings.gestures.swipeRight

fun doubleTapSwipeAction(settings: LauncherSettings, direction: SwipeDirection): GestureAction = when (direction) {
    SwipeDirection.UP -> settings.gestures.doubleTapSwipeUp
    SwipeDirection.DOWN -> settings.gestures.doubleTapSwipeDown
    SwipeDirection.NONE -> GestureAction.None
}

/** Off the main page, HOME first returns there when the user has enabled that scope. */
fun homeButtonAction(settings: LauncherSettings, isOnDefaultPage: Boolean): GestureAction =
    if (settings.gestures.homeButtonOnlyOnDefaultPage && !isOnDefaultPage) GestureAction.GoToDefaultPage
    else settings.gestures.homeGesture

/** True when any two-finger gesture has an action, so the home surface should take two-finger touches. */
fun hasMultiTouchActions(settings: LauncherSettings): Boolean =
    MultiTouchGesture.entries.any { multiTouchAction(settings, it) != GestureAction.None }

/**
 * The swipe action of a workspace or dock item, or null when the swipe should fall through to the
 * home gesture. The item's own [io.github.aj4abinjacob.chikku.core.model.ItemActions] win; an app
 * icon without one uses the app's [AppCustomization] default. Folder swipes honour
 * `folders.swipeActionsEnabled`. An explicit [GestureAction.None] blocks the app default.
 */
fun resolveItemSwipeAction(
    item: HomeItemUi,
    direction: SwipeDirection,
    customizations: Map<AppKey, AppCustomization>,
    folderSwipesEnabled: Boolean,
): GestureAction? {
    val explicit = when (direction) {
        SwipeDirection.UP -> item.actions.swipeUp
        SwipeDirection.DOWN -> item.actions.swipeDown
        SwipeDirection.NONE -> return null
    }
    val content = item.content
    if (content is ItemContent.Folder && !folderSwipesEnabled) return null
    val resolved = explicit ?: (content as? ItemContent.App)?.let { app ->
        customizations[app.key]?.let { if (direction == SwipeDirection.UP) it.swipeUpAction else it.swipeDownAction }
    }
    return resolved?.takeUnless { it == GestureAction.None }
}
