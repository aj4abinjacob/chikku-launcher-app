package io.github.aj4abinjacob.chikku.home.appmenu

/** Where a popup menu goes, in the same coordinates as the inputs. [height] may be less than asked. */
data class MenuPosition(val x: Int, val y: Int, val height: Int, val above: Boolean)

/**
 * Places a [menuWidth] x [menuHeight] menu next to an anchor (left/top/right/bottom) inside the
 * visible [frame] (window minus system bars): below the anchor when it fits, otherwise above it,
 * otherwise on the side with more room with its height capped so it scrolls. [overlap] lets the
 * menu cover the edge of the anchor; [margin] keeps it off the frame edges.
 *
 * Horizontally the menu is centred on the anchor, or with [alignToEdge] lines up with the nearer
 * edge of [alignLeft]..[alignRight] (the icon): an icon in the left half of the frame starts the
 * menu [edgeOutset] before its left edge, one in the right half ends it [edgeOutset] past its right
 * edge. The rule depends only on screen position, so it is the same in RTL.
 */
object MenuPlacement {
    fun place(
        anchorLeft: Int,
        anchorTop: Int,
        anchorRight: Int,
        anchorBottom: Int,
        frameLeft: Int,
        frameTop: Int,
        frameRight: Int,
        frameBottom: Int,
        menuWidth: Int,
        menuHeight: Int,
        overlap: Int,
        margin: Int,
        alignToEdge: Boolean = false,
        alignLeft: Int = anchorLeft,
        alignRight: Int = anchorRight,
        edgeOutset: Int = 0,
    ): MenuPosition {
        val minX = frameLeft + margin
        val maxX = (frameRight - margin - menuWidth).coerceAtLeast(minX)
        val wanted = when {
            !alignToEdge -> (anchorLeft + anchorRight) / 2 - menuWidth / 2
            alignLeft + alignRight < frameLeft + frameRight -> alignLeft - edgeOutset
            else -> alignRight + edgeOutset - menuWidth
        }
        val x = wanted.coerceIn(minX, maxX)

        val belowTop = anchorBottom - overlap
        val roomBelow = frameBottom - margin - belowTop
        val aboveBottom = anchorTop + overlap
        val roomAbove = aboveBottom - (frameTop + margin)
        return when {
            menuHeight <= roomBelow -> MenuPosition(x, belowTop, menuHeight, above = false)
            menuHeight <= roomAbove -> MenuPosition(x, aboveBottom - menuHeight, menuHeight, above = true)
            roomAbove > roomBelow -> MenuPosition(x, frameTop + margin, roomAbove.coerceAtLeast(0), above = true)
            else -> MenuPosition(x, belowTop, roomBelow.coerceAtLeast(0), above = false)
        }
    }
}
