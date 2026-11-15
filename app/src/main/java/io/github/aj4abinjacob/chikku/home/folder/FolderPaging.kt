package io.github.aj4abinjacob.chikku.home.folder

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

/** A page range in presentation order. Manual workspace order is never changed by navigation. */
internal data class FolderPageWindow(val page: Int, val pages: Int, val start: Int, val end: Int)

internal fun folderPageWindow(count: Int, columns: Int, rows: Int, requestedPage: Int): FolderPageWindow {
    val size = count.coerceAtLeast(0)
    val capacity = columns.coerceIn(1, 6) * rows.coerceIn(1, 6)
    val pages = ((size + capacity - 1) / capacity).coerceAtLeast(1)
    val page = requestedPage.coerceIn(0, pages - 1)
    val start = page * capacity
    return FolderPageWindow(page, pages, start, (start + capacity).coerceAtMost(size))
}

/** Horizontal swipes leave taps and long presses with the icon; vertical drags reach the scroller. */
@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
internal class FolderSwipeViewport(context: Context, private val onPageSwipe: (Int) -> Unit) : FrameLayout(context) {
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var swiping = false
    private var blockedGesture = false

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                swiping = false
                blockedGesture = false
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                blockedGesture = true
                swiping = false
                parent?.requestDisallowInterceptTouchEvent(false)
            }
            MotionEvent.ACTION_MOVE -> {
                trackMovement(event)
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                if (!swiping) parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return swiping
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                swiping = false
                blockedGesture = false
                return true
            }
            // Empty space can make this viewport the touch target itself, bypassing interception.
            MotionEvent.ACTION_MOVE -> trackMovement(event)
            MotionEvent.ACTION_POINTER_DOWN -> {
                blockedGesture = true
                swiping = false
            }
            MotionEvent.ACTION_UP -> {
                val distance = event.x - downX
                if (!blockedGesture && swiping && abs(distance) >= (touchSlop * 2).coerceAtLeast((width * 0.12f).toInt())) {
                    val forward = if (layoutDirection == LAYOUT_DIRECTION_RTL) distance > 0 else distance < 0
                    onPageSwipe(if (forward) 1 else -1)
                } else if (!blockedGesture && !swiping) performClick()
                swiping = false
                parent?.requestDisallowInterceptTouchEvent(false)
            }
            MotionEvent.ACTION_CANCEL -> {
                swiping = false
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }

    private fun trackMovement(event: MotionEvent) {
        if (blockedGesture || event.pointerCount != 1) return
        val dx = abs(event.x - downX)
        val dy = abs(event.y - downY)
        if (dx > touchSlop && dx > dy) swiping = true
        else if (dy > touchSlop) parent?.requestDisallowInterceptTouchEvent(false)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
