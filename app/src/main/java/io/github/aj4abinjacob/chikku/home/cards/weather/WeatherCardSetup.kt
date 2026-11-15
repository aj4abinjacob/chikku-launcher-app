package io.github.aj4abinjacob.chikku.home.cards.weather

import androidx.annotation.StringRes
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.cards.CardContent

/** What an unconfigured weather card still needs before it can show a forecast. */
internal enum class WeatherSetupNeed(@StringRes val message: Int) {
    CITY(R.string.card_weather_needs_city),
    ONLINE(R.string.card_weather_needs_online),
    CITY_AND_ONLINE(R.string.card_weather_needs_city_and_online),
}

/**
 * Which controls a weather card shows. An unconfigured card offers only "Set up weather"; refresh
 * appears only when a forecast can actually be requested. MET/GeoNames attribution stays
 * whenever a selected city or an online forecast can be on screen.
 */
internal data class WeatherCardChrome(
    val setup: WeatherSetupNeed?,
    val showRefresh: Boolean,
    val showAttribution: Boolean,
)

internal fun weatherCardChrome(config: CardContent.Weather): WeatherCardChrome {
    val hasCity = config.location != null
    val setup = when {
        !hasCity && !config.onlineEnabled -> WeatherSetupNeed.CITY_AND_ONLINE
        !hasCity -> WeatherSetupNeed.CITY
        !config.onlineEnabled -> WeatherSetupNeed.ONLINE
        else -> null
    }
    return WeatherCardChrome(setup, showRefresh = setup == null, showAttribution = hasCity || config.onlineEnabled)
}
