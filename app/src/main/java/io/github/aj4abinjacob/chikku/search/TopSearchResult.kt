package io.github.aj4abinjacob.chikku.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.getValue
import io.github.aj4abinjacob.chikku.theme.LocalMotion
import io.github.aj4abinjacob.chikku.theme.spec
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import io.github.aj4abinjacob.chikku.R

/** Shared by list rows, compact app tiles and the native drag source. */
@Composable
internal fun Modifier.topSearchResult(highlighted: Boolean, cornerRadius: Dp = 16.dp): Modifier {
    // The highlight fades in and out (speed-aware) instead of popping when the top result changes.
    val progress by animateFloatAsState(if (highlighted) 1f else 0f, LocalMotion.current.spec<Float>(HIGHLIGHT_MS), label = "topResult")
    if (progress <= 0f) return this
    val shape = RoundedCornerShape(cornerRadius)
    val colors = MaterialTheme.colorScheme
    return clip(shape)
        .background(colors.primary.copy(alpha = .10f * progress).compositeOver(colors.surface))
        .border(1.dp, colors.primary.copy(alpha = progress), shape)
}

private const val HIGHLIGHT_MS = 160

/** [TopResultLabel] that grows in and fades rather than shifting the row's content in one frame. */
@Composable
internal fun AnimatedTopResultLabel(visible: Boolean) {
    val motion = LocalMotion.current
    AnimatedVisibility(
        visible,
        enter = fadeIn(motion.spec(HIGHLIGHT_MS)) + expandVertically(motion.spec(HIGHLIGHT_MS)),
        exit = fadeOut(motion.spec(HIGHLIGHT_MS)) + shrinkVertically(motion.spec(HIGHLIGHT_MS)),
    ) { TopResultLabel() }
}

/** The actionable result owns the spoken state; this visible label must not repeat it. */
@Composable
internal fun TopResultLabel() {
    Text(
        stringResource(R.string.search_top_result),
        modifier = Modifier.clearAndSetSemantics { },
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.SemiBold,
    )
}
