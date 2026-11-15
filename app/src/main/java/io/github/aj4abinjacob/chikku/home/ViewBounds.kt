package io.github.aj4abinjacob.chikku.home

import android.graphics.Rect
import android.view.View

/** The view's rectangle in screen coordinates, used as the source bounds for launches. */
internal fun View.screenBounds(): Rect {
    val location = IntArray(2)
    getLocationOnScreen(location)
    return Rect(location[0], location[1], location[0] + width, location[1] + height)
}
