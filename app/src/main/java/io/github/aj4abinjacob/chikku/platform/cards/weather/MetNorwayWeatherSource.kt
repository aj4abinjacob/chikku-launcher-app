package io.github.aj4abinjacob.chikku.platform.cards.weather

import io.github.aj4abinjacob.chikku.core.model.cards.CardContent
import io.github.aj4abinjacob.chikku.core.model.cards.WeatherUnit
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URL
import java.security.MessageDigest
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Original MET client. Freshness and cooldown cannot be bypassed by Refresh or unit changes. */
class MetNorwayWeatherSource(
    private val cities: OfflineCitySearch,
    private val http: WeatherHttpClient = BoundedWeatherHttpClient(),
    private val cache: ForecastCache = MemoryForecastCache(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
) : CardWeatherSource {
    private val mutex = Mutex()
    private var nextRequestAt = 0L

    override suspend fun searchLocations(query: String) = cities.search(query)

    override suspend fun forecast(config: CardContent.Weather, forceRefresh: Boolean): WeatherState {
        if (!config.onlineEnabled) return WeatherState.Disabled
        val location = config.location ?: return WeatherState.MissingLocation
        try { location.validated() } catch (_: IllegalArgumentException) { return WeatherState.Unavailable }
        return withContext(ioDispatcher) {
            try {
                mutex.withLock {
                    val url = URL("https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=${coordinate(location.latitude)}&lon=${coordinate(location.longitude)}")
                    val key = MessageDigest.getInstance("SHA-256").digest(url.toString().toByteArray()).joinToString("") { "%02x".format(it) }
                    val now = clock()
                    val saved = cache.read(key)?.takeIf { it.receivedAtMillis <= now }
                    val previous = saved?.let { parseForecast(it.body, config.unit, now, it.receivedAtMillis, it.providerWarning) }
                    if (saved != null && saved.expiresAtMillis > now) return@withLock previous ?: WeatherState.Unavailable
                    nextRequestAt = maxOf(nextRequestAt, cache.readNotBefore())
                    if (now < nextRequestAt) return@withLock WeatherState.Unavailable
                    // Persist before connecting, so stop/restart and multiple cards cannot hammer the service.
                    nextRequestAt = now + MIN_REQUEST_MILLIS
                    cache.writeNotBefore(nextRequestAt)
                    currentCoroutineContext().ensureActive()
                    val response = http.get(url, saved?.lastModified?.takeIf { (httpDate(it) ?: Long.MAX_VALUE) <= now })
                    currentCoroutineContext().ensureActive()
                    if (response == null) return@withLock WeatherState.Unavailable
                    if (response.status == 429 || response.status == 403 || response.status >= 500) {
                        val delay = if (response.status == 403) MAX_BACKOFF_MILLIS else retryDelay(response.header("Retry-After"), now)
                        nextRequestAt = maxOf(nextRequestAt, now + delay)
                        cache.writeNotBefore(nextRequestAt)
                        return@withLock WeatherState.Unavailable
                    }
                    val body = when (response.status) {
                        200, 203 -> response.body
                        304 -> saved?.body
                        else -> null
                    } ?: return@withLock WeatherState.Unavailable
                    val warning = response.status == 203 || (response.status == 304 && saved?.providerWarning == true)
                    val received = if (response.status == 304) saved!!.receivedAtMillis else clock()
                    val parsed = parseForecast(body, config.unit, clock(), received, warning)
                    if (parsed !is WeatherState.Ready) return@withLock WeatherState.Unavailable
                    val expires = httpDate(response.header("Expires"))?.coerceAtLeast(now)
                        ?: (now + MIN_REQUEST_MILLIS)
                    val modified = response.header("Last-Modified")?.takeIf { (httpDate(it) ?: Long.MAX_VALUE) <= clock() }
                        ?: saved?.lastModified?.takeIf { response.status == 304 }
                    currentCoroutineContext().ensureActive()
                    cache.write(key, CachedForecast(body, expires, modified, received, warning))
                    parsed
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                WeatherState.Unavailable
            }
        }
    }

    companion object {
        internal const val MIN_REQUEST_MILLIS = 60_000L
        internal const val MAX_BACKOFF_MILLIS = 24 * 60 * 60_000L
        internal fun coordinate(value: Double): String = BigDecimal.valueOf(value).setScale(4, RoundingMode.DOWN)
            .stripTrailingZeros().toPlainString()
        internal fun httpDate(value: String?): Long? = runCatching {
            ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
        }.getOrNull()
        internal fun retryDelay(value: String?, now: Long): Long =
            (value?.toLongOrNull()?.takeIf { it >= 0 }?.coerceAtMost((Long.MAX_VALUE - now) / 1000)?.times(1000)
                ?: httpDate(value)?.minus(now) ?: 15 * 60_000L).coerceAtLeast(MIN_REQUEST_MILLIS)

        internal fun parseForecast(body: String, unit: WeatherUnit, now: Long, fetched: Long, warning: Boolean = false): WeatherState = try {
            require(body.toByteArray().size <= BoundedWeatherHttpClient.MAX_BYTES)
            val properties = JSONObject(body).getJSONObject("properties")
            require(properties.getJSONObject("meta").getJSONObject("units").getString("air_temperature") == "celsius")
            val series = properties.getJSONArray("timeseries")
            require(series.length() in 2..1024)
            data class Point(val time: Long, val temperature: Double, val symbol: String?)
            val points = (0 until series.length()).map { index ->
                val item = series.getJSONObject(index)
                val data = item.getJSONObject("data")
                val temperature = (data.getJSONObject("instant").getJSONObject("details").get("air_temperature") as? Number)?.toDouble()
                require(temperature != null && temperature.isFinite() && temperature in -150.0..100.0)
                val symbol = listOf("next_1_hours", "next_6_hours", "next_12_hours").firstNotNullOfOrNull {
                    data.optJSONObject(it)?.optJSONObject("summary")?.optString("symbol_code")?.takeIf(String::isNotBlank)
                }
                Point(Instant.parse(item.getString("time")).toEpochMilli(), temperature, symbol)
            }
            require(points.all { it.time in (now - 86400_000L)..(now + 14 * 86400_000L) })
            require(points.zipWithNext().all { (a, b) -> a.time < b.time })
            val first = points.minBy { kotlin.math.abs(it.time - now) }
            require(kotlin.math.abs(first.time - now) <= 3 * 60 * 60_000L)
            val end = first.time + 24 * 60 * 60_000L
            require(points.last().time >= end)
            val day = points.filter { it.time in first.time..end }
            require(day.size >= 5 && day.zipWithNext().all { (a, b) -> b.time - a.time <= 6 * 60 * 60_000L })
            fun convert(value: Double) = if (unit == WeatherUnit.CELSIUS) value else value * 9 / 5 + 32
            WeatherState.Ready(WeatherReading(convert(first.temperature), convert(day.maxOf { it.temperature }),
                convert(day.minOf { it.temperature }), condition(first.symbol), fetched, unit, warning))
        } catch (_: Exception) { WeatherState.Unavailable }

        /** Chikku's own condition summaries; no provider icon artwork is imported. */
        internal fun condition(symbol: String?): Int {
            val base = symbol?.substringBeforeLast('_')?.takeIf { symbol.substringAfterLast('_') in setOf("day", "night", "polartwilight") } ?: symbol
            return when {
                base == "clearsky" -> 0
                base == "fair" -> 1
                base == "partlycloudy" -> 2
                base == "cloudy" -> 3
                base == "fog" -> 45
                base != null && base.matches(Regex("(?:light|heavy|lights)?(?:rain|sleet|snow)(?:showers)?(?:andthunder)?")) -> when {
                    base.endsWith("andthunder") -> 95
                    base.contains("sleet") -> 68
                    base.contains("snow") -> 71
                    else -> 61
                }
                else -> -1
            }
        }
    }
}
