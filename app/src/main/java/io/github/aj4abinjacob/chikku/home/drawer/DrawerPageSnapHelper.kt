package io.github.aj4abinjacob.chikku.home.drawer

import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import io.github.aj4abinjacob.chikku.core.model.settings.PageMotion

/** Keeps AndroidX's one-page fling policy but applies Chikku's duration and reduced-motion controls. */
internal class DrawerPageSnapHelper(
    private val pages: RecyclerView,
    private val durationMs: () -> Int,
    private val scale: () -> Float,
) : PagerSnapHelper() {
    private var velocity = 0f

    override fun onFling(velocityX: Int, velocityY: Int): Boolean {
        val manager = pages.layoutManager ?: return false
        if (scale() <= 0f) {
            val target = findTargetSnapPosition(manager, velocityX, velocityY)
            if (target == RecyclerView.NO_POSITION) return false
            manager.scrollToPosition(target)
            return true
        }
        velocity = velocityX.toFloat()
        return super.onFling(velocityX, velocityY)
    }

    override fun createScroller(layoutManager: RecyclerView.LayoutManager): RecyclerView.SmoothScroller? {
        if (layoutManager !is RecyclerView.SmoothScroller.ScrollVectorProvider) return null
        return object : LinearSmoothScroller(pages.context) {
            override fun onTargetFound(targetView: View, state: RecyclerView.State, action: RecyclerView.SmoothScroller.Action) {
                val distance = calculateDistanceToFinalSnap(layoutManager, targetView) ?: return
                val duration = (PageMotion.duration(distance[0], pages.width, durationMs(), velocity) * scale()).toInt().coerceAtLeast(1)
                action.update(distance[0], distance[1], duration, DecelerateInterpolator(1.5f))
            }
        }
    }
}
