package io.github.aj4abinjacob.chikku.home.appmenu

import android.graphics.Rect
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.PopupWindow
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.home.LauncherMenuStyle
import io.github.aj4abinjacob.chikku.home.MenuAnchorIcon

/** Keeps the menu off the edges of the visible frame. */
private const val FRAME_MARGIN_DP = 8f

/** Space between the arrow's tip and the item's footprint (above) or label (below). */
private const val TIP_GAP_DP = 2f

/**
 * Shows (or, with [showing], moves) a long-press menu [window] next to [anchor] inside the visible
 * frame: below the item when it fits, else above its icon, lined up with the footprint's nearer
 * side. The menu never covers the icon. [width] is the menu's width ([exactWidth]) or its
 * maximum; [shadowPad] is the transparent margin around the cards inside the window. Where the
 * frame allows, the card reaches far enough past the icon for its arrow to clear the card's
 * [cornerRadius] corners (px). A newly shown menu dims the screen behind it.
 *
 * Once the side is known, [arrange] gets it ([above]) and the icon's centre in the window's
 * coordinates, to stack the cards and aim their arrow ([arrangeMenu]) before the window shows.
 */
internal fun placeMenuWindow(
    window: PopupWindow,
    anchor: View,
    width: Int,
    exactWidth: Boolean,
    showing: Boolean,
    shadowPad: Int,
    cornerRadius: Float = 0f,
    arrange: (above: Boolean, arrowCenterX: Int) -> Unit = { _, _ -> },
) {
    if (!anchor.isAttachedToWindow) return
    val metrics = anchor.resources.displayMetrics
    fun dp(value: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, metrics).toInt()
    val frame = Rect().also(anchor::getWindowVisibleDisplayFrame) // screen coordinates
    val margin = dp(FRAME_MARGIN_DP)
    val menu = window.contentView
    val menuWidth = width.coerceAtMost((frame.width() - 2 * margin).coerceAtLeast(0))
    menu.measure(
        View.MeasureSpec.makeMeasureSpec(menuWidth, if (exactWidth) View.MeasureSpec.EXACTLY else View.MeasureSpec.AT_MOST),
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
    )
    val location = IntArray(2).also(anchor::getLocationOnScreen)
    val a = Rect(location[0], location[1], location[0] + anchor.width, location[1] + anchor.height)
    val icon = Rect()
    (anchor as? MenuAnchorIcon)?.iconBounds(icon)
    // Above the item, the menu stops at the icon's footprint rather than at the (taller) view.
    val footprintPad = anchor.resources.getDimensionPixelSize(R.dimen.home_footprint_icon_padding)
    val top = if (icon.isEmpty) a.top else a.top + icon.top - footprintPad
    // Below it, the menu starts under the label, not at the bottom of a tall drawer cell.
    val bottom = (anchor as? MenuAnchorIcon)?.takeUnless { icon.isEmpty }?.let { a.top + it.contentBottom() } ?: a.bottom
    // From the card's side to the icon's centre, so the arrow there clears the rounded corner.
    val arrowRoom = (arrowCornerInset(cornerRadius) + ARROW_WIDTH_DP * metrics.density / 2f).toInt()
    val pos = MenuPlacement.place(
        a.left, top, a.right, bottom,
        frame.left, frame.top, frame.right, frame.bottom,
        menu.measuredWidth, menu.measuredHeight,
        // The transparent shadow margin around the arrow may overlap; its tip stops just short.
        overlap = shadowPad - dp(TIP_GAP_DP),
        margin = margin,
        alignToEdge = true,
        alignLeft = if (icon.isEmpty) a.left else a.left + icon.left,
        alignRight = if (icon.isEmpty) a.right else a.left + icon.right,
        edgeOutset = (if (icon.isEmpty) 0 else maxOf(footprintPad, arrowRoom - icon.width() / 2)) + shadowPad,
    )
    val iconCenterX = if (icon.isEmpty) a.centerX() else a.left + icon.centerX()
    arrange(pos.above, iconCenterX - pos.x)
    // showAtLocation/update take coordinates in the anchor's window, not the screen.
    val inWindow = IntArray(2).also(anchor::getLocationInWindow)
    val dx = a.left - inWindow[0]
    val dy = a.top - inWindow[1]
    if (showing) {
        window.update(pos.x - dx, pos.y - dy, menu.measuredWidth, pos.height)
    } else {
        window.width = menu.measuredWidth
        window.height = pos.height
        window.showAtLocation(anchor, Gravity.TOP or Gravity.START, pos.x - dx, pos.y - dy)
        LauncherMenuStyle.dimBehind(window)
    }
}
