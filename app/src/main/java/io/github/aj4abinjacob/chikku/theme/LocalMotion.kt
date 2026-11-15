package io.github.aj4abinjacob.chikku.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween

// [LocalMotion] itself lives in ComposeMotion.kt.
/** A tween of [baseMs] scaled by the user's speed, or a snap when animations are off. */
fun <T> Motion.spec(baseMs: Int): FiniteAnimationSpec<T> {
    val ms = duration(baseMs.toLong()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    return if (ms <= 0) snap() else tween(ms, easing = FastOutSlowInEasing)
}
