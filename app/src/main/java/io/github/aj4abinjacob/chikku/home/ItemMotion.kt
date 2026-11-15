package io.github.aj4abinjacob.chikku.home

import android.view.View
import android.view.animation.OvershootInterpolator
import io.github.aj4abinjacob.chikku.home.folder.FolderReorderMotion

/** Motion shared by the desktop/dock grid and the open folder's grid. */
internal object ItemMotion {
    /** Scale a dropped item is lifted to and lands from. */
    const val LIFT_SCALE = FolderReorderMotion.LIFT_SCALE

    /** Base duration of a drop landing, before the animation speed is applied. */
    const val SETTLE_MS = FolderReorderMotion.SETTLE_MS

    /** The overshoot of the landing. */
    private val BOUNCE = OvershootInterpolator(2.2f)

    /**
     * The drop "bounce": [view] starts at [LIFT_SCALE] and grows to 1 with an overshoot over [ms]
     * (already scaled by the animation speed). With [alphaTo] its alpha animates to that value as
     * well (the caller sets the start alpha); [onEnd] runs when the animation completes (not when
     * it is cancelled). Does not cancel a running animator: callers do that first.
     */
    fun bounceIn(view: View, ms: Long, alphaTo: Float? = null, onEnd: (() -> Unit)? = null) {
        view.scaleX = LIFT_SCALE
        view.scaleY = LIFT_SCALE
        val animator = view.animate().scaleX(1f).scaleY(1f).setInterpolator(BOUNCE).setDuration(ms)
        if (alphaTo != null) animator.alpha(alphaTo)
        if (onEnd != null) animator.withEndAction(onEnd)
        animator.start()
    }

    /**
     * Cancels [view]'s animator and puts the listed properties at rest; a null property is left
     * as it is. The one routine every cancel/re-bind path uses, so no view keeps a half-way
     * translation, scale or alpha.
     */
    fun settle(
        view: View,
        alpha: Float? = null,
        scale: Float? = null,
        translationX: Float? = null,
        translationY: Float? = null,
    ) {
        view.animate().cancel()
        if (alpha != null) view.alpha = alpha
        if (scale != null) {
            view.scaleX = scale
            view.scaleY = scale
        }
        if (translationX != null) view.translationX = translationX
        if (translationY != null) view.translationY = translationY
    }
}
