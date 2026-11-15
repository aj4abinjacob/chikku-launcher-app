package io.github.aj4abinjacob.chikku.platform.cards.weather

import io.github.aj4abinjacob.chikku.core.model.cards.CardContent
import io.github.aj4abinjacob.chikku.core.model.cards.WeatherLocation
import io.github.aj4abinjacob.chikku.core.model.cards.WeatherUnit

/** City search is explicit and offline. Only opted-in forecasts use the network; never GPS. */
interface CardWeatherSource {
    suspend fun searchLocations(query: String): WeatherLocationsResult
    suspend fun forecast(config: CardContent.Weather, forceRefresh: Boolean = false): WeatherState
}

sealed interface WeatherLocationsResult {
    data class Found(val locations: List<WeatherLocation>) : WeatherLocationsResult
    data object Unavailable : WeatherLocationsResult
}

sealed interface WeatherState {
    data object Disabled : WeatherState
    data object MissingLocation : WeatherState
    data object Loading : WeatherState
    data class Ready(val reading: WeatherReading) : WeatherState
    data object Unavailable : WeatherState
}

/** Provider values are model estimates. UI rounds temperatures and summarizes conditions. */
data class WeatherReading(
    val temperature: Double,
    val high: Double,
    val low: Double,
    val conditionCode: Int,
    val fetchedAtMillis: Long,
    val unit: WeatherUnit,
    val providerWarning: Boolean = false,
)

object NoopCardWeatherSource : CardWeatherSource {
    override suspend fun searchLocations(query: String) = WeatherLocationsResult.Unavailable
    override suspend fun forecast(config: CardContent.Weather, forceRefresh: Boolean): WeatherState = when {
        !config.onlineEnabled -> WeatherState.Disabled
        config.location == null -> WeatherState.MissingLocation
        else -> WeatherState.Unavailable
    }
}
