package io.github.aj4abinjacob.chikku.home

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Tactile confirmation for HOME interactions. Goes through [View.performHapticFeedback], so the
 * system's touch-feedback setting turns it off.
 */
internal object Haptics {
    /** A home gesture (swipe, double tap, two fingers) ran its action. */
    fun gesture(view: View) {
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE
            else HapticFeedbackConstants.CONTEXT_CLICK,
        )
    }

    /** An item was picked up from its menu and now follows the finger. */
    fun lift(view: View) {
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) HapticFeedbackConstants.DRAG_START
            else HapticFeedbackConstants.LONG_PRESS,
        )
    }

    /** A dragged item's drop target moved to another cell; lighter than [tick] so it never feels busy. */
    fun cell(view: View) {
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
            else HapticFeedbackConstants.CLOCK_TICK,
        )
    }

    /** A selection mark or switch was turned [on] or off. */
    fun toggle(view: View, on: Boolean) {
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                if (on) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.TOGGLE_OFF
            } else HapticFeedbackConstants.CONTEXT_CLICK,
        )
    }

    /** A dragged item was dropped into place or removed. */
    fun drop(view: View) {
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
            else HapticFeedbackConstants.VIRTUAL_KEY,
        )
    }

    /**
     * A drag held at the edge moved to the next page or reached the remove target, or a drag on the
     * alphabet rail reached a new letter.
     */
    fun tick(view: View) {
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) HapticFeedbackConstants.SEGMENT_TICK
            else HapticFeedbackConstants.CLOCK_TICK,
        )
    }
}
