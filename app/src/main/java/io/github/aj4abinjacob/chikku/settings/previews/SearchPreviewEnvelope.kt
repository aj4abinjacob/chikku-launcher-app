package io.github.aj4abinjacob.chikku.settings.previews

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Reserve fixed chrome space before capping a pinned scene to 40% of the screen viewport. */
internal fun searchPreviewEnvelopeDp(preferredHeight: Float, screenHeight: Int, fontScale: Float): Float {
    val scale = fontScale.takeIf { it.isFinite() }?.coerceIn(1f, 3f) ?: 1f
    val chrome = 120f + (scale - 1f) * 48f
    return minOf(preferredHeight, (screenHeight - chrome).coerceAtLeast(0f) * .4f)
}

/**
 * Source changes can alter a Column child's remaining constraints. Use the screen/font bucket
 * instead so switching results never changes the preview or the position of controls beneath it.
 * Rotation, multiwindow resizing and deliberate font configuration changes recalculate the bucket.
 */
@Composable
internal fun rememberSearchPreviewEnvelope(preferred: Dp): Dp {
    val screenHeight = LocalConfiguration.current.screenHeightDp
    val fontScale = LocalDensity.current.fontScale
    return remember(preferred, screenHeight, fontScale) {
        searchPreviewEnvelopeDp(preferred.value, screenHeight, fontScale).dp
    }
}
