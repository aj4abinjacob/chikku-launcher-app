package io.github.aj4abinjacob.chikku.platform.cards.weather

import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.cards.CardContent
import io.github.aj4abinjacob.chikku.core.model.cards.WeatherLocation
import io.github.aj4abinjacob.chikku.core.model.cards.WeatherUnit
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URL
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MetNorwayWeatherSourceTest {
    @get:Rule val temporary = TemporaryFolder()
    private val start = Instant.parse("2026-10-16T00:00:00Z").toEpochMilli()
    private val config = CardContent.Weather(WeatherLocation("Restored small place", 12.123456, -34.987654), onlineEnabled = true)
    private val cities = OfflineCitySearch({ ByteArrayInputStream(gzip("Sample, Example\t1\t2\tsample\texample ex\n")) })
    private fun gzip(text: String) = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(text.toByteArray()) } }.toByteArray()
    private fun date(time: Long) = DateTimeFormatter.RFC_1123_DATE_TIME.format(Instant.ofEpochMilli(time).atZone(ZoneOffset.UTC))
    private fun body(symbol: String = "partlycloudy_day", count: Int = 30): String {
        val series = JSONArray()
        repeat(count) { index ->
            series.put(JSONObject().put("time", Instant.ofEpochMilli(start + index * 3_600_000L).toString())
                .put("data", JSONObject().put("instant", JSONObject().put("details", JSONObject().put("air_temperature", index.toDouble())))
                    .put("next_1_hours", JSONObject().put("summary", JSONObject().put("symbol_code", symbol)))))
        }
        return JSONObject().put("properties", JSONObject().put("meta", JSONObject().put("units", JSONObject().put("air_temperature", "celsius")))
            .put("timeseries", series)).toString()
    }
    private fun response(expires: Long = start + 300_000, status: Int = 200) = WeatherHttpResponse(status, body(),
        mapOf("Expires" to date(expires), "Last-Modified" to date(start - 3600_000)))

    @Test fun disabledMissingInvalidForecastsNeverConnectAndCitySearchIsOffline() = runTest {
        var calls = 0
        val source = MetNorwayWeatherSource(cities, WeatherHttpClient { _, _ -> calls++; response() }, ioDispatcher = StandardTestDispatcher(testScheduler)) { start }
        assertThat(source.forecast(config.copy(onlineEnabled = false))).isEqualTo(WeatherState.Disabled)
        assertThat(source.forecast(config.copy(location = null))).isEqualTo(WeatherState.MissingLocation)
        assertThat(source.forecast(config.copy(location = config.location!!.copy(latitude = Double.NaN)))).isEqualTo(WeatherState.Unavailable)
        assertThat(source.searchLocations("Sample")).isInstanceOf(WeatherLocationsResult.Found::class.java)
        assertThat(calls).isEqualTo(0)
    }

    @Test fun reducedPrecisionNeverChangesSavedCoordinatesAndCelsiusFahrenheitShareProviderCache() = runTest {
        val urls = mutableListOf<URL>()
        val source = MetNorwayWeatherSource(cities, WeatherHttpClient { url, _ -> urls += url; response() }, ioDispatcher = StandardTestDispatcher(testScheduler)) { start }
        val first = source.forecast(config) as WeatherState.Ready
        assertThat(first.reading.temperature).isEqualTo(0.0)
        assertThat(first.reading.high).isEqualTo(24.0)
        assertThat(first.reading.low).isEqualTo(0.0)
        val fahrenheit = source.forecast(config.copy(unit = WeatherUnit.FAHRENHEIT), true) as WeatherState.Ready
        assertThat(fahrenheit.reading.temperature).isEqualTo(32.0)
        assertThat(fahrenheit.reading.high).isWithin(0.001).of(75.2)
        assertThat(urls).hasSize(1)
        assertThat(urls.single().query).isEqualTo("lat=12.1234&lon=-34.9876")
        assertThat(config.location!!.latitude).isEqualTo(12.123456)
        assertThat(config.location!!.label).isEqualTo("Restored small place")
    }

    @Test fun expiryAndConditional304SurviveSourceAndFileCacheRestart() = runTest {
        var now = start
        val folder = temporary.newFolder()
        val calls = mutableListOf<String?>()
        val client = WeatherHttpClient { _, modified ->
            calls += modified
            if (calls.size == 1) response() else WeatherHttpResponse(304, headers = mapOf("Expires" to date(start + 900_000)))
        }
        fun source() = MetNorwayWeatherSource(cities, client, FileForecastCache { folder }, StandardTestDispatcher(testScheduler)) { now }
        assertThat(source().forecast(config)).isInstanceOf(WeatherState.Ready::class.java)
        assertThat(source().forecast(config, true)).isInstanceOf(WeatherState.Ready::class.java)
        assertThat(calls).hasSize(1)
        now += 300_000
        assertThat(source().forecast(config)).isInstanceOf(WeatherState.Ready::class.java)
        assertThat(calls).containsExactly(null, date(start - 3600_000)).inOrder()
        source().forecast(config, true)
        assertThat(calls).hasSize(2)
    }

    @Test fun concurrentRequestsCoalesceAndChangingCityCannotBypassPersistedThrottle() = runTest {
        var calls = 0
        val cache = MemoryForecastCache()
        val client = WeatherHttpClient { _, _ -> calls++; response() }
        val source = MetNorwayWeatherSource(cities, client, cache, StandardTestDispatcher(testScheduler)) { start }
        val results = (1..5).map { async { source.forecast(config, true) } }.map { it.await() }
        assertThat(results.all { it is WeatherState.Ready }).isTrue()
        assertThat(calls).isEqualTo(1)
        val restarted = MetNorwayWeatherSource(cities, client, cache, StandardTestDispatcher(testScheduler)) { start }
        assertThat(restarted.forecast(config.copy(location = WeatherLocation("Other", 1.0, 2.0)))).isEqualTo(WeatherState.Unavailable)
        assertThat(calls).isEqualTo(1)
    }

    @Test fun retryAfterIsRespectedAcrossRestartAndRefresh() = runTest {
        var now = start
        var calls = 0
        val cache = MemoryForecastCache()
        val client = WeatherHttpClient { _, _ -> calls++; if (calls == 1) WeatherHttpResponse(429, headers = mapOf("Retry-After" to "180")) else response(start + 900_000) }
        fun source() = MetNorwayWeatherSource(cities, client, cache, StandardTestDispatcher(testScheduler)) { now }
        assertThat(source().forecast(config)).isEqualTo(WeatherState.Unavailable)
        now += 120_000
        assertThat(source().forecast(config, true)).isEqualTo(WeatherState.Unavailable)
        assertThat(calls).isEqualTo(1)
        now += 60_000
        assertThat(source().forecast(config)).isInstanceOf(WeatherState.Ready::class.java)
        assertThat(calls).isEqualTo(2)
        assertThat(MetNorwayWeatherSource.retryDelay(date(start + 7200_000), start)).isEqualTo(7200_000)
        assertThat(MetNorwayWeatherSource.retryDelay("172800", start)).isEqualTo(172800_000)
    }

    @Test fun provider203WarningIsVisibleAndCached() = runTest {
        val source = MetNorwayWeatherSource(cities, WeatherHttpClient { _, _ -> response(status = 203) }, ioDispatcher = StandardTestDispatcher(testScheduler)) { start }
        assertThat((source.forecast(config) as WeatherState.Ready).reading.providerWarning).isTrue()
        assertThat((source.forecast(config, true) as WeatherState.Ready).reading.providerWarning).isTrue()
    }

    @Test fun cancellationReleasesMutexAndCannotBypassRateLimit() = runTest {
        var now = start
        var calls = 0
        var cancelled = false
        val source = MetNorwayWeatherSource(cities, WeatherHttpClient { _, _ ->
            calls++
            if (calls == 1) try { awaitCancellation() } finally { cancelled = true } else response(start + 900_000)
        }, ioDispatcher = StandardTestDispatcher(testScheduler)) { now }
        val job = launch { source.forecast(config) }
        runCurrent(); job.cancel(); runCurrent()
        assertThat(cancelled).isTrue()
        assertThat(source.forecast(config, true)).isEqualTo(WeatherState.Unavailable)
        now += MetNorwayWeatherSource.MIN_REQUEST_MILLIS
        assertThat(source.forecast(config)).isInstanceOf(WeatherState.Ready::class.java)
    }

    @Test fun malformedShortStaleUnitsAndNonNumericForecastsAreNotDisplayed() {
        val valid = body()
        listOf("{}", "not json", body(count = 4), valid.replace("celsius", "fahrenheit"),
            valid.replace("\"air_temperature\":0", "\"air_temperature\":\"0\""),
            valid.replace("\"air_temperature\":0", "\"air_temperature\":1e999"),
            "x".repeat(BoundedWeatherHttpClient.MAX_BYTES + 1)).forEach {
            assertThat(MetNorwayWeatherSource.parseForecast(it, WeatherUnit.CELSIUS, start, start)).isEqualTo(WeatherState.Unavailable)
        }
        assertThat(MetNorwayWeatherSource.parseForecast(valid, WeatherUnit.CELSIUS, start + 10 * 86400_000L, start)).isEqualTo(WeatherState.Unavailable)
    }

    @Test fun conditionMappingHandlesVariantsSleetThunderAndUnknownWithoutImportingIcons() {
        assertThat(MetNorwayWeatherSource.condition("fair_night")).isEqualTo(1)
        assertThat(MetNorwayWeatherSource.condition("sleetshowers_day")).isEqualTo(68)
        assertThat(MetNorwayWeatherSource.condition("lightssnowshowersandthunder_polartwilight")).isEqualTo(95)
        assertThat(MetNorwayWeatherSource.condition("future_rainbow")).isEqualTo(-1)
        assertThat(MetNorwayWeatherSource.condition(null)).isEqualTo(-1)
    }

    @Test fun corruptForecastCacheIsRefetchableAndBoundedWithoutTouchingUserSettings() {
        val folder = temporary.newFolder()
        val store = FileForecastCache { folder }
        val entry = CachedForecast(body(), start + 300_000, date(start), start, false)
        repeat(26) { store.write(it.toString(16).padStart(64, '0'), entry) }
        assertThat(folder.listFiles()!!.count { it.name != "limits.json" }).isEqualTo(24)
        val key = "f".repeat(64)
        java.io.File(folder, "$key.json").writeText("bad cache")
        assertThat(store.read(key)).isNull()
        store.writeNotBefore(start + 60_000)
        assertThat(FileForecastCache { folder }.readNotBefore()).isEqualTo(start + 60_000)
    }
}
