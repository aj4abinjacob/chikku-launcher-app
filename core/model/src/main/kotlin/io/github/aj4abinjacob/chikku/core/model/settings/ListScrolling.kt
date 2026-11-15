package io.github.aj4abinjacob.chikku.core.model.settings

import kotlin.math.roundToInt

/** How far a flick carries a vertical list: the platform fling velocity is scaled by [flingMultiplier]. */
enum class ListScrollSpeed(val flingMultiplier: Float) { SLOW(0.6f), NORMAL(1f), FAST(1.6f) }

/** What reaching the top or bottom of a vertical list shows. SYSTEM is Android's own: stretch on 12+, glow before. */
enum class ListEdgeEffect { SYSTEM, NONE }

/** Toolkit-independent vertical list rules shared by the drawer and its settings sample. */
object ListScrolling {
    /** Scales a fling but never past the platform's maximum, so Fast can't exceed what a hard flick allows. */
    fun flingVelocity(velocity: Int, speed: ListScrollSpeed, maxVelocity: Int): Int {
        val limit = maxVelocity.coerceAtLeast(0)
        return (velocity * speed.flingMultiplier).roundToInt().coerceIn(-limit, limit)
    }
}
