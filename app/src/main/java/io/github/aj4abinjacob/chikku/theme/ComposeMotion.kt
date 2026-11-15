package io.github.aj4abinjacob.chikku.theme

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import io.github.aj4abinjacob.chikku.core.model.settings.AnimationSpeed

/**
 * The user's animation speed for Compose surfaces (settings, icon picker, onboarding). Defaults to
 * normal speed where no activity provides it. Compose animators already follow the system animator
 * scale; [Motion.enabled] additionally honours [AnimationSpeed.NONE].
 */
val LocalMotion = compositionLocalOf { Motion.DEFAULT }

/** Provides [LocalMotion] for [speed] (null while settings load: normal speed). */
@Composable
fun ProvideMotion(speed: AnimationSpeed?, content: @Composable () -> Unit) {
    val motion = remember(speed) { Motion.current(speed ?: AnimationSpeed.NORMAL) }
    CompositionLocalProvider(LocalMotion provides motion, content = content)
}

/** A tween of [baseMs] scaled for the user's speed; zero length (jump to the end) when animations are off. */
fun <T> Motion.tweenSpec(baseMs: Int, easing: Easing = FastOutSlowInEasing): TweenSpec<T> =
    tween(durationMillis = duration(baseMs.toLong()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), easing = easing)
