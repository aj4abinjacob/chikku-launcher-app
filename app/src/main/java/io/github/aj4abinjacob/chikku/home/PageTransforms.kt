package io.github.aj4abinjacob.chikku.home

import android.view.View
import io.github.aj4abinjacob.chikku.core.model.settings.PageMotion
import io.github.aj4abinjacob.chikku.core.model.settings.PageTransform
import io.github.aj4abinjacob.chikku.core.model.settings.PageTransitionEffect

/** What [PageMotion.transform] returns for SLIDE and for disabled effects; shared to avoid a per-page, per-frame allocation. */
private val IDENTITY_TRANSFORM = PageTransform()

// No hardware layer is requested for the alpha/scale effects: pages can hold AppWidgetHostViews
// (remote, possibly surface-backed content), and the view system already composites a view with
// alpha < 1 off-screen when it needs to.
internal fun applyPageTransform(view: View, position: Float, effect: PageTransitionEffect, strength: Float, enabled: Boolean) {
    val transform = if (!enabled || effect == PageTransitionEffect.SLIDE) IDENTITY_TRANSFORM
    else PageMotion.transform(position, effect, strength, enabled)
    view.alpha = transform.alpha
    view.scaleX = transform.scale
    view.scaleY = transform.scale
    view.rotationY = transform.rotationY
    view.pivotX = view.width * transform.pivot
    view.pivotY = view.height / 2f
    view.cameraDistance = view.width * 12f
}
