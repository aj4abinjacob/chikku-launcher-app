package io.github.aj4abinjacob.chikku.core.model.settings

import kotlin.math.abs
import kotlin.math.roundToInt

enum class PageSwipeSensitivity(val slopMultiplier: Float) { LOW(2f), NORMAL(1.5f), HIGH(1f) }
enum class PageEdgeFeedback { NONE, RESISTANCE }

data class PageTransform(val alpha: Float = 1f, val scale: Float = 1f, val rotationY: Float = 0f, val pivot: Float = .5f)

/** Original, toolkit-independent paging rules shared by native surfaces and settings samples. */
object PageMotion {
    const val HORIZONTAL_DOMINANCE = 1.35f

    /**
     * The home workspace shares its surface with swipe up/down actions, so it only pages on a
     * clearly sideways movement (within about 27 degrees of horizontal). Steeper movements stay
     * unclaimed until the vertical swipe layout recognises them.
     */
    const val WORKSPACE_HORIZONTAL_DOMINANCE = 2f

    fun isHorizontal(
        dx: Float,
        dy: Float,
        slop: Float,
        sensitivity: PageSwipeSensitivity,
        dominance: Float = HORIZONTAL_DOMINANCE,
    ): Boolean = abs(dx) > slop * sensitivity.slopMultiplier && abs(dx) > abs(dy) * dominance

    /** A release that moves mostly vertically is not a page fling, whatever its sideways speed. */
    fun pageFlingVelocity(vx: Float, vy: Float): Float = if (abs(vx) > abs(vy)) vx else 0f

    fun transform(position: Float, effect: PageTransitionEffect, strength: Float, enabled: Boolean): PageTransform {
        if (!enabled) return PageTransform()
        val amount = abs(position).coerceIn(0f, 1f) * strength.coerceIn(0f, 1f)
        return when (effect) {
            PageTransitionEffect.SLIDE -> PageTransform()
            PageTransitionEffect.FADE -> PageTransform(alpha = 1f - amount)
            PageTransitionEffect.ZOOM -> PageTransform(alpha = 1f - amount * .4f, scale = 1f - amount * .2f)
            PageTransitionEffect.CUBE -> PageTransform(alpha = 1f - amount * .3f,
                rotationY = position.coerceIn(-1f, 1f) * -60f * strength, pivot = if (position < 0f) 1f else 0f)
        }
    }

    /** A cancelled gesture returns to its starting slot, including a virtual wrap slot. */
    fun settle(position: Float, start: Int, delta: Float, velocity: Float, minVelocity: Float, cancelled: Boolean): Int {
        if (cancelled) return start
        return if (abs(velocity) >= minVelocity && abs(delta) > 0f && (velocity < 0) == (delta < 0)) {
            start + if (velocity > 0) 1 else -1
        } else position.roundToInt()
    }

    /** Short remaining distances finish sooner; a fast fling shortens, rather than extends, settling. */
    fun duration(distance: Int, width: Int, baseMs: Int, velocity: Float = 0f): Int {
        val fraction = (abs(distance).toFloat() / width.coerceAtLeast(1)).coerceIn(0f, 1f)
        val distanceMs = (baseMs * (.4f + .6f * fraction)).roundToInt()
        val velocityMs = if (abs(velocity) > 0f) (abs(distance) / abs(velocity) * 1000f).roundToInt() else baseMs
        return minOf(distanceMs, velocityMs).coerceIn(70, baseMs.coerceAtLeast(70))
    }
}
