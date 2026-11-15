package io.github.aj4abinjacob.chikku.platform.cards.weather

import io.github.aj4abinjacob.chikku.core.model.cards.WeatherLocation
import java.io.InputStream
import java.text.Normalizer
import java.util.Locale
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Streaming, bounded-memory search. No query or city list is transmitted or retained. */
class OfflineCitySearch(
    private val openIndex: () -> InputStream,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun search(query: String): WeatherLocationsResult = withContext(ioDispatcher) {
        val normalized = normalize(query)
        if (query.length !in 2..80 || query.any(Char::isISOControl) || normalized.length < 2) {
            return@withContext WeatherLocationsResult.Found(emptyList())
        }
        try {
            data class Match(val rank: Int, val order: Int, val location: WeatherLocation)
            val matches = mutableListOf<Match>()
            val tokens = normalized.split(' ')
            val parts = query.split(',', limit = 2)
            val cityTokens = if (parts.size == 2) normalize(parts[0]).split(' ').filter(String::isNotEmpty) else tokens
            val regionTokens = if (parts.size == 2) normalize(parts[1]).split(' ').filter(String::isNotEmpty) else emptyList()
            GZIPInputStream(openIndex()).bufferedReader().use { reader ->
                var order = 0
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val line = reader.readLine() ?: break
                    require(line.length <= 65_536 && order < 300_000)
                    val fields = line.split('\t')
                    require(fields.size == 5)
                    val names = fields[3]
                    val region = fields[4]
                    if (cityTokens.isNotEmpty() && (if (parts.size == 2) cityTokens.all(names::contains) && regionTokens.all {
                            if (it.length == 2) it in region.split(' ') else region.contains(it)
                        } else tokens.all { names.contains(it) || region.contains(it) })) {
                        val aliases = names.split('|')
                        val rank = when {
                            normalized in aliases -> 0
                            aliases.any { it == cityTokens.first() } -> 1
                            aliases.any { it.startsWith(cityTokens.first()) } -> 2
                            else -> 3
                        }
                        val location = WeatherLocation(fields[0], fields[1].toDouble(), fields[2].toDouble()).validated()
                        if (matches.none { it.location.latitude == location.latitude && it.location.longitude == location.longitude }) {
                            matches += Match(rank, order, location)
                            matches.sortWith(compareBy<Match> { it.rank }.thenBy { it.order })
                            if (matches.size > 5) matches.removeAt(5)
                        }
                    }
                    order++
                }
            }
            WeatherLocationsResult.Found(matches.map { it.location })
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            WeatherLocationsResult.Unavailable
        }
    }

    companion object {
        internal fun normalize(value: String): String = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFKD)
            .replace(Regex("\\p{M}+"), "").replace(',', ' ').trim().replace(Regex("[\\s\\p{Z}]+"), " ")
    }
}
