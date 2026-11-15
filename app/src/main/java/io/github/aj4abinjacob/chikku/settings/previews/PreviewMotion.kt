package io.github.aj4abinjacob.chikku.settings.previews

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.aj4abinjacob.chikku.core.model.settings.AnimationSpeed
import io.github.aj4abinjacob.chikku.theme.Motion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** A finite, explicitly requested preview; it never starts while a settings page is idle. */
@Stable
class PreviewPlayback internal constructor(private val scope: CoroutineScope, private val speed: AnimationSpeed) {
    private val position = Animatable(0f)
    private var job: Job? = null
    private var generation = 0
    private var requested by mutableStateOf(false)
    val progress: Float get() = position.value
    val running: Boolean get() = requested || position.isRunning

    fun replay(baseDurationMs: Long = 600L) {
        job?.cancel()
        val request = ++generation
        requested = true
        job = scope.launch {
            try {
                position.snapTo(0f)
                // Read on every request: returning from Android's Remove animations setting must
                // take effect even when the selected Chikku speed has not changed.
                val duration = Motion.current(speed).duration(baseDurationMs.coerceAtLeast(0L))
                if (duration == 0L) position.snapTo(1f)
                else position.animateTo(1f, tween(duration.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), easing = FastOutSlowInEasing))
            } finally {
                if (request == generation) requested = false
            }
        }
    }

    internal suspend fun reset() {
        job?.cancel()
        generation++
        requested = false
        position.snapTo(0f)
    }

    internal fun cancel() { job?.cancel(); generation++; requested = false }
}

/** Changing geometry/effect/speed clears the prior demonstration without auto-playing it. */
@Composable
fun rememberPreviewPlayback(speed: AnimationSpeed, resetKey: Any? = Unit): PreviewPlayback {
    val scope = rememberCoroutineScope()
    val playback = remember(scope, speed) { PreviewPlayback(scope, speed) }
    DisposableEffect(playback) { onDispose { playback.cancel() } }
    LaunchedEffect(playback, resetKey) { playback.reset() }
    return playback
}
