package io.github.aj4abinjacob.chikku.search

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.settings.SearchAppPriorityDirection

internal fun SearchAppPriorityDirection.rowLayoutDirection(language: LayoutDirection): LayoutDirection = when (this) {
    SearchAppPriorityDirection.FOLLOW_LANGUAGE -> language
    SearchAppPriorityDirection.LEFT_TO_RIGHT -> LayoutDirection.Ltr
    SearchAppPriorityDirection.RIGHT_TO_LEFT -> LayoutDirection.Rtl
}

/** Moves ranked tiles without reversing ranking, text direction or keyboard actions. */
@Composable
internal fun SearchAppPriorityRow(
    direction: SearchAppPriorityDirection,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val language = LocalLayoutDirection.current
    CompositionLocalProvider(LocalLayoutDirection provides direction.rowLayoutDirection(language)) {
        Row(modifier.semantics { isTraversalGroup = true }) {
            CompositionLocalProvider(LocalLayoutDirection provides language) { content() }
        }
    }
}

@StringRes
internal fun SearchAppPriorityDirection.labelRes(): Int = when (this) {
    SearchAppPriorityDirection.FOLLOW_LANGUAGE -> R.string.search_app_priority_language
    SearchAppPriorityDirection.LEFT_TO_RIGHT -> R.string.search_app_priority_left_to_right
    SearchAppPriorityDirection.RIGHT_TO_LEFT -> R.string.search_app_priority_right_to_left
}

@StringRes
internal fun SearchAppPriorityDirection.summaryRes(): Int = when (this) {
    SearchAppPriorityDirection.FOLLOW_LANGUAGE -> R.string.search_app_priority_language_summary
    SearchAppPriorityDirection.LEFT_TO_RIGHT -> R.string.search_app_priority_left_to_right_summary
    SearchAppPriorityDirection.RIGHT_TO_LEFT -> R.string.search_app_priority_right_to_left_summary
}
