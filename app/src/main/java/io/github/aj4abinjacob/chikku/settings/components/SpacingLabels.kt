package io.github.aj4abinjacob.chikku.settings.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.settings.PaddingSize
import io.github.aj4abinjacob.chikku.home.HomeLayoutMetrics

/** Use the same amount shown by settings and applied by the home/dock layout. */
@Composable
internal fun spacingLabel(size: PaddingSize): String = spacingLabel(LocalContext.current, size)

/** The same label for View-based UI, which has no composition to read resources from. */
internal fun spacingLabel(context: Context, size: PaddingSize): String = context.getString(
    R.string.settings_spacing_named_value,
    context.getString(when (size) {
        PaddingSize.NONE -> R.string.settings_padding_none
        PaddingSize.SMALL -> R.string.settings_padding_small
        PaddingSize.MEDIUM -> R.string.settings_padding_medium
        PaddingSize.LARGE -> R.string.settings_padding_large
    }),
    HomeLayoutMetrics.paddingDp(size),
)
