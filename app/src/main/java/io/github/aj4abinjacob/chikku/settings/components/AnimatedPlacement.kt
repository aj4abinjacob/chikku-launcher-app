package io.github.aj4abinjacob.chikku.settings.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.IntOffset
import io.github.aj4abinjacob.chikku.theme.Motion
import io.github.aj4abinjacob.chikku.theme.tweenSpec
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Set while a layout change is already shown by other means (a dropped card), so it must not animate again. */
internal class PlacementGate {
    var suppress = false
}

/**
 * Glides an item to its new vertical position in a plain (non-lazy) column or layout when other
 * items are added, removed or reordered. Jumps when [motion] has animations off.
 */
internal fun Modifier.animatePlacement(motion: Motion, gate: PlacementGate? = null): Modifier = composed {
    val scope = rememberCoroutineScope()
    val shift = remember { Animatable(0f) }
    var previous by remember { mutableStateOf<Float?>(null) }
    this
        .onPlaced { coordinates ->
            val top = coordinates.positionInParent().y
            val old = previous
            previous = top
            if (old != null && old != top) {
                if (gate?.suppress != true && motion.enabled) {
                    scope.launch {
                        shift.snapTo(shift.value + old - top)
                        shift.animateTo(0f, motion.tweenSpec(PLACEMENT_MS))
                    }
                } else scope.launch { shift.snapTo(0f) }
            }
        }
        .offset { IntOffset(0, shift.value.roundToInt()) }
}

/** Fades the item in when it joins an already shown page ([animate] true); shows at once otherwise. */
internal fun Modifier.appearing(motion: Motion, animate: Boolean): Modifier = composed {
    val alpha = remember { Animatable(if (animate && motion.enabled) 0f else 1f) }
    LaunchedEffect(Unit) { if (alpha.value < 1f) alpha.animateTo(1f, motion.tweenSpec(PLACEMENT_MS)) }
    this.graphicsLayer { this.alpha = alpha.value }
}

/** [Modifier.animateItem] for lazy settings lists, with no motion when animations are off. */
internal fun LazyItemScope.animateItemWith(motion: Motion): Modifier =
    if (motion.enabled) {
        Modifier.animateItem(
            fadeInSpec = motion.tweenSpec(PLACEMENT_MS),
            placementSpec = motion.tweenSpec(PLACEMENT_MS),
            fadeOutSpec = motion.tweenSpec(PLACEMENT_MS / 2),
        )
    } else Modifier.animateItem(fadeInSpec = null, placementSpec = null, fadeOutSpec = null)

private const val PLACEMENT_MS = 200
