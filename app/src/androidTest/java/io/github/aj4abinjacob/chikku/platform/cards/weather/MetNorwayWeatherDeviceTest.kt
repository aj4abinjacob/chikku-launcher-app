package io.github.aj4abinjacob.chikku.platform.cards.weather

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.DeviceTestSupport
import io.github.aj4abinjacob.chikku.core.model.cards.CardContent
import io.github.aj4abinjacob.chikku.core.model.cards.WeatherLocation
import io.github.aj4abinjacob.chikku.core.model.cards.WeatherUnit
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/** Public Greenwich fixture only; no real location/query, preferences or workspace is read/changed. */
@RunWith(AndroidJUnit4::class)
class MetNorwayWeatherDeviceTest {
    @Test fun realOfflineGazetteerAndOptInHttpsForecastUseOnlyChosenCoordinates() = runBlocking {
        val context = DeviceTestSupport.targetContext
        val folder = File(context.cacheDir, "met-test-${UUID.randomUUID()}")
        val requests = mutableListOf<String>()
        val network = BoundedWeatherHttpClient()
        val source = MetNorwayWeatherSource(
            OfflineCitySearch({ context.assets.open("weather/cities.index") }),
            WeatherHttpClient { url, modified -> requests += url.toString(); network.get(url, modified) },
            FileForecastCache { folder },
        )
        try {
            val offline = CardContent.Weather(WeatherLocation("Greenwich public example", 51.5, 0.0))
            assertThat(source.forecast(offline)).isEqualTo(WeatherState.Disabled)
            val found = source.searchLocations("Paris, France") as WeatherLocationsResult.Found
            assertThat(found.locations.first().label).contains("France")
            assertThat(found.locations.first().latitude).isWithin(0.2).of(48.85)
            val accent = source.searchLocations("São Paulo, Brazil") as WeatherLocationsResult.Found
            assertThat(accent.locations.first().label).contains("Paulo")
            assertThat(requests).isEmpty()
            val ready = source.forecast(offline.copy(onlineEnabled = true))
            assertThat(ready).isInstanceOf(WeatherState.Ready::class.java)
            assertThat(requests).containsExactly("https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=51.5&lon=0")
            val converted = source.forecast(offline.copy(onlineEnabled = true, unit = WeatherUnit.FAHRENHEIT), true)
            assertThat(converted).isInstanceOf(WeatherState.Ready::class.java)
            assertThat(requests).hasSize(1)
            assertThat((converted as WeatherState.Ready).reading.temperature).isWithin(0.001)
                .of((ready as WeatherState.Ready).reading.temperature * 9 / 5 + 32)
        } finally {
            // Only this test's UUID-scoped re-fetchable cache, never user settings/data.
            folder.deleteRecursively()
        }
    }
}
