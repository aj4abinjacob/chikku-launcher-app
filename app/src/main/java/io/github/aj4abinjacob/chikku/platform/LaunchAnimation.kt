package io.github.aj4abinjacob.chikku.platform

import android.app.ActivityOptions
import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.view.View

/**
 * Activity options that open the target by revealing it from the tapped item's [sourceBounds]
 * (screen pixels), as Launcher3 does without a recents integration. Without options Android has no
 * origin to animate from, and on several devices the app appears with no transition at all.
 * Null when there is no source (the system default then applies).
 */
internal fun launchAnimationOptions(context: Context, sourceBounds: Rect?): Bundle? {
    if (sourceBounds == null || sourceBounds.isEmpty) return null
    // ActivityOptions offsets by the view's screen position; a detached view reports (0, 0), so
    // the bounds pass through unchanged as screen coordinates.
    return ActivityOptions.makeClipRevealAnimation(
        View(context),
        sourceBounds.left,
        sourceBounds.top,
        sourceBounds.width(),
        sourceBounds.height(),
    ).toBundle()
}
