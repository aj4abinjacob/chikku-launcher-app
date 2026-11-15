package io.github.aj4abinjacob.chikku.home.folder

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs

/**
 * Detects a vertical swipe on a folder icon without breaking its tap and long-press. While the
 * folder claims a direction ([claims]), the home screen's own swipe handling (drawer) and the
 * pager are kept away with `requestDisallowInterceptTouchEvent`; a horizontal movement or an
 * unclaimed direction hands the gesture back to them.
 */
class FolderSwipeDetector(
    private val claims: (FolderGesture) -> Boolean,
    private val onSwipe: (View, FolderGesture) -> Unit,
) : View.OnTouchListener {

    private var downX = 0f
    private var downY = 0f
    private var tracking = false
    private var swiped: FolderGesture? = null

    // Taps still reach View.onTouchEvent (performClick); a recognised swipe is consumed whole.
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(view: View, event: MotionEvent): Boolean {
        val slop = ViewConfiguration.get(view.context).scaledTouchSlop
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                swiped = null
                tracking = claims(FolderGesture.SWIPE_UP) || claims(FolderGesture.SWIPE_DOWN)
                if (tracking) view.parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> if (tracking && swiped == null) {
                val dx = event.rawX - downX
                val dy = event.rawY - downY
                when {
                    abs(dx) > slop && abs(dx) > abs(dy) -> release(view) // a page swipe
                    abs(dy) > slop * SWIPE_SLOP_FACTOR -> {
                        val gesture = if (dy < 0) FolderGesture.SWIPE_UP else FolderGesture.SWIPE_DOWN
                        if (claims(gesture)) {
                            swiped = gesture
                            view.cancelLongPress()
                            // End the pressed state the view started on ACTION_DOWN.
                            val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
                            view.onTouchEvent(cancel)
                            cancel.recycle()
                        } else {
                            release(view) // e.g. swipe up opens the drawer as usual
                        }
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                val gesture = swiped
                reset()
                if (gesture != null) {
                    onSwipe(view, gesture)
                    return true
                }
            }
            MotionEvent.ACTION_CANCEL -> reset()
        }
        return swiped != null
    }

    private fun release(view: View) {
        tracking = false
        view.parent?.requestDisallowInterceptTouchEvent(false)
    }

    private fun reset() {
        tracking = false
        swiped = null
    }

    private companion object {
        const val SWIPE_SLOP_FACTOR = 2
    }
}
