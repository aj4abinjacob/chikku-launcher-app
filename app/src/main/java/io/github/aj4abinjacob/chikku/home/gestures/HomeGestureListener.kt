package io.github.aj4abinjacob.chikku.home.gestures

import io.github.aj4abinjacob.chikku.home.SwipeDirection

/**
 * Optional home-surface gestures reported by [io.github.aj4abinjacob.chikku.home.VerticalSwipeLayout]
 * in addition to its one-finger vertical swipes.
 */
interface HomeGestureListener {
    /** Whether a second finger may take the gesture away from the children (pager, icons) now. */
    fun canStartMultiTouch(): Boolean

    /** Called once per two-finger gesture, as soon as it is recognised. */
    fun onMultiTouchGesture(gesture: MultiTouchGesture)

    /** Two quick taps on an empty area (not on an icon or button). */
    fun onDoubleTap()

    /** Any single tap on an empty area, at screen position [screenX], [screenY] (live wallpapers). */
    fun onEmptyTap(screenX: Int, screenY: Int) = Unit

    /** Horizontal home actions only claim empty workspace touches with an assigned action. */
    fun canStartHorizontalSwipe(left: Boolean): Boolean = false
    fun onHorizontalSwipe(left: Boolean) = Unit

    /** A tap followed by a second touch dragged vertically, on empty workspace space. */
    fun canStartDoubleTapSwipe(direction: SwipeDirection): Boolean = false
    fun onDoubleTapSwipe(direction: SwipeDirection) = Unit
}
