package io.github.aj4abinjacob.chikku.settings.cards

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.cards.CardContent
import io.github.aj4abinjacob.chikku.core.model.cards.CardFeatured
import io.github.aj4abinjacob.chikku.core.model.cards.WeatherUnit

@StringRes
internal fun cardPreviewWeatherMessage(content: CardContent.Weather): Int = when {
    !content.onlineEnabled -> R.string.card_weather_disabled
    content.location == null -> R.string.card_weather_choose_city
    else -> R.string.preview_card_weather_ready
}

/** Announce target kinds, never identity or labels, including the empty featured slot. */
@StringRes
internal fun cardPreviewFeaturedMessage(featured: CardFeatured): Int = when (featured) {
    CardFeatured.None -> R.string.preview_card_featured_empty
    is CardFeatured.App -> R.string.preview_card_sample_app
    is CardFeatured.Shortcut -> R.string.preview_card_sample_shortcut
    is CardFeatured.Widget -> R.string.preview_card_sample_widget
}

@StringRes
internal fun cardPreviewWidgetMessage(content: CardContent.Widget): Int =
    if (content.provider == null) R.string.preview_card_widget_empty else R.string.preview_card_sample_widget

/** Illustrative content only: no personal labels, provider artwork, requests or allocations. */
@Composable
internal fun CardContentLivePreview(content: CardContent) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(18.dp))
        .background(colors.surfaceContainerHighest).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.preview_cards_sample_content), style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant)
        when (content) {
            CardContent.Actions -> Unit
            is CardContent.Weather -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(painterResource(R.drawable.ic_action_weather), null, Modifier.size(40.dp), tint = colors.primary)
                Column(Modifier.weight(1f)) {
                    if (content.onlineEnabled && content.location != null) {
                        Text(stringResource(R.string.preview_cards_sample_city), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(if (content.unit == WeatherUnit.CELSIUS) R.string.preview_cards_weather_celsius
                            else R.string.preview_cards_weather_fahrenheit), style = MaterialTheme.typography.bodyMedium)
                    } else Text(stringResource(cardPreviewWeatherMessage(content)), style = MaterialTheme.typography.bodyMedium)
                }
            }
            CardContent.Media -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(painterResource(R.drawable.ic_action_music), null, Modifier.size(40.dp), tint = colors.primary)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.preview_cards_sample_track), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.preview_cards_sample_artist), style = MaterialTheme.typography.bodySmall)
                }
                Text(stringResource(R.string.preview_cards_media_controls), style = MaterialTheme.typography.titleMedium)
            }
            is CardContent.Widget -> WidgetContentPreview(content)
        }
    }
}

/** Fixed frame; the synthetic widget resizes with the draft height and never loads a provider. */
@Composable
private fun WidgetContentPreview(content: CardContent.Widget) {
    Text(stringResource(R.string.preview_cards_widget_height, content.heightDp), style = MaterialTheme.typography.labelMedium)
    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.fillMaxWidth().height((content.heightDp.coerceIn(96, 600) / 5f).dp)
            .clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center) {
            if (content.provider == null) Text(stringResource(cardPreviewWidgetMessage(content)),
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(8.dp))
            else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(painterResource(R.drawable.ic_menu_widgets), null, Modifier.size(24.dp))
                Text(stringResource(cardPreviewWidgetMessage(content)), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
