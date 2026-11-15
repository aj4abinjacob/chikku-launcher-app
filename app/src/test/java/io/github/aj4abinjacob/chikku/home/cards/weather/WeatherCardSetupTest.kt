package io.github.aj4abinjacob.chikku.home.cards.weather

import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.cards.CardContent
import io.github.aj4abinjacob.chikku.core.model.cards.WeatherLocation
import org.junit.Test

class WeatherCardSetupTest {
    private val city = WeatherLocation("Synthetic city", 1.0, 2.0)

    @Test fun configuredCardRefreshesAndKeepsAttribution() {
        assertThat(weatherCardChrome(CardContent.Weather(city, onlineEnabled = true)))
            .isEqualTo(WeatherCardChrome(setup = null, showRefresh = true, showAttribution = true))
    }

    @Test fun missingCityOnlineOnAsksForCityAndKeepsAttributionForCitySearch() {
        assertThat(weatherCardChrome(CardContent.Weather(null, onlineEnabled = true)))
            .isEqualTo(WeatherCardChrome(WeatherSetupNeed.CITY, showRefresh = false, showAttribution = true))
    }

    @Test fun cityChosenButOfflineAsksForOnlineAndKeepsAttributionForTheCityName() {
        assertThat(weatherCardChrome(CardContent.Weather(city, onlineEnabled = false)))
            .isEqualTo(WeatherCardChrome(WeatherSetupNeed.ONLINE, showRefresh = false, showAttribution = true))
    }

    @Test fun noCityAndOfflineAsksForBothAndShowsNoProviderData() {
        assertThat(weatherCardChrome(CardContent.Weather(null, onlineEnabled = false)))
            .isEqualTo(WeatherCardChrome(WeatherSetupNeed.CITY_AND_ONLINE, showRefresh = false, showAttribution = false))
    }
}
