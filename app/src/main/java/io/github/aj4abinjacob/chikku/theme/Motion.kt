package io.github.aj4abinjacob.chikku.theme

import android.animation.ValueAnimator
import io.github.aj4abinjacob.chikku.core.model.settings.AnimationSpeed
import kotlin.math.roundToLong

/**
 * The one place that turns the user's [AnimationSpeed] into animation durations. Every launcher
 * animation (drawer, folders, home transitions, menus) asks [duration] for its length and skips
 * the animation entirely when [enabled] is false: [AnimationSpeed.NONE], or the system "Animator
 * duration scale" developer/accessibility setting at 0 ("Remove animations").
 *
 * The system scale itself is not multiplied in here: the platform animators already apply it.
 */
data class Motion(
    val speed: AnimationSpeed,
    /** False when the system animator duration scale is 0. */
    val systemAnimationsEnabled: Boolean = true,
) {
    val enabled: Boolean get() = speed != AnimationSpeed.NONE && systemAnimationsEnabled

    /** Multiplier for base durations; 0 when animations are off. */
    val scale: Float get() = if (enabled) speed.durationScale else 0f

    /** [baseMs] scaled for the user's speed; 0 means "jump to the end state". */
    fun duration(baseMs: Long): Long = if (enabled) (baseMs * scale).roundToLong().coerceAtLeast(0L) else 0L

    companion object {
        /** Normal speed with animations allowed; for code that has no settings yet. */
        val DEFAULT = Motion(AnimationSpeed.NORMAL)

        /** [speed] combined with the current system animator setting. Call on any thread. */
        fun current(speed: AnimationSpeed): Motion = Motion(speed, ValueAnimator.areAnimatorsEnabled())
    }
}
