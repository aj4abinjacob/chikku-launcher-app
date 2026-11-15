package io.github.aj4abinjacob.chikku.home.appmenu

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import io.github.aj4abinjacob.chikku.home.Haptics
import kotlin.math.hypot

/** An open long-press menu that a drag can close. */
fun interface AppMenuHandle {
    fun dismiss()
}

/**
 * Long-press on an icon opens its menu; if the finger then moves past the touch slop, the menu
 * closes and a drag starts instead (the gesture is still the view's, so the drag follows the
 * finger). When [showMenu] returns null (no menu for this item) the long-press starts the drag at
 * once, as before menus existed. Click handling is untouched.
 *
 * Once the long-press fires, ancestors are asked not to intercept the rest of the gesture, so the
 * home surface's icon swipe actions and the pager do not steal the menu-to-drag movement. Before
 * the long-press they still can (a quick swipe on an icon runs its swipe action).
 *
 * The view keeps a single touch listener; another feature that needs touch events on the same view
 * (folder swipes) installs itself as [secondary], which sees events first and may consume them
 * while no menu is open.
 */
class LongPressMenuDrag private constructor(
    private val showMenu: (View) -> AppMenuHandle?,
    private val startDrag: (View) -> Boolean,
    private val canDragFromMenu: () -> Boolean,
    private val touchSlop: Int,
) : View.OnTouchListener, View.OnLongClickListener {

    private var downX = 0f
    private var downY = 0f
    private var openMenu: AppMenuHandle? = null

    /** Extra touch handling chained in front of this one (e.g. folder swipe detection). */
    var secondary: View.OnTouchListener? = null

    @SuppressLint("ClickableViewAccessibility") // Observes only; clicks and long-clicks still go to the view.
    override fun onTouch(view: View, event: MotionEvent): Boolean {
        val consumed = openMenu == null && secondary?.onTouch(view, event) == true
        observe(view, event)
        return consumed
    }

    private fun observe(view: View, event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                openMenu = null
            }
            MotionEvent.ACTION_MOVE -> {
                val menu = openMenu
                if (menu != null && canDragFromMenu() && shouldStartDrag(event.rawX - downX, event.rawY - downY, touchSlop)) {
                    openMenu = null
                    menu.dismiss()
                    // The long-press buzz was for the menu; this one says the item is now in hand.
                    if (startDrag(view)) Haptics.lift(view)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> openMenu = null
        }
    }

    override fun onLongClick(view: View): Boolean {
        // The rest of this gesture belongs to the icon (menu, then maybe a drag).
        view.parent?.requestDisallowInterceptTouchEvent(true)
        val menu = showMenu(view) ?: return startDrag(view)
        openMenu = menu
        return true
    }

    companion object {
        /** Installs the behavior on [view], replacing its long-click and touch listeners. */
        @SuppressLint("ClickableViewAccessibility")
        fun attach(
            view: View,
            showMenu: (View) -> AppMenuHandle?,
            startDrag: (View) -> Boolean,
            canDragFromMenu: () -> Boolean = { true },
        ): LongPressMenuDrag {
            val helper = LongPressMenuDrag(showMenu, startDrag, canDragFromMenu, ViewConfiguration.get(view.context).scaledTouchSlop)
            view.setOnTouchListener(helper)
            view.setOnLongClickListener(helper)
            return helper
        }

        internal fun shouldStartDrag(dx: Float, dy: Float, touchSlop: Int): Boolean = hypot(dx, dy) > touchSlop
    }
}
