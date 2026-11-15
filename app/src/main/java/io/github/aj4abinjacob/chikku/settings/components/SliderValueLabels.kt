package io.github.aj4abinjacob.chikku.settings.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import io.github.aj4abinjacob.chikku.R
import kotlin.math.roundToInt

/** Call sites must choose their unit; a dimension must never inherit percentage formatting. */
@Composable
internal fun percentageValueLabel(): (Float) -> String {
    val resources = LocalResources.current
    return { resources.getString(R.string.settings_slider_percentage_value, (it * 100).roundToInt()) }
}

/** Retain fractional radii from saved settings instead of truncating them to whole dp. */
@Composable
internal fun dpValueLabel(): (Float) -> String {
    val resources = LocalResources.current
    return { resources.getString(R.string.settings_slider_dp_value, it) }
}
