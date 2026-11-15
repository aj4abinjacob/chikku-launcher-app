package io.github.aj4abinjacob.chikku.search

import io.github.aj4abinjacob.chikku.core.model.search.WebSuggestionProviders
import io.github.aj4abinjacob.chikku.core.model.search.WebSuggestionResult
import io.github.aj4abinjacob.chikku.core.model.search.WebSuggestionSource
import io.github.aj4abinjacob.chikku.core.model.settings.WebSearchEngine
import io.github.aj4abinjacob.chikku.platform.boundedHttpsGet
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.CoroutineDispatcher
import org.json.JSONArray

/** Bounded, cookie-free autocomplete. No cache, logs, identifiers, redirects or retry loop. */
class HttpsWebSuggestionSource(
    private val ioDispatcher: CoroutineDispatcher,
    private val connectionFactory: (URL) -> HttpsURLConnection = { it.openConnection() as HttpsURLConnection },
) : WebSuggestionSource {
    override suspend fun suggest(engine: WebSearchEngine, query: String): WebSuggestionResult {
        val trimmed = query.trim()
        if (trimmed.length !in MIN_QUERY_LENGTH..MAX_QUERY_LENGTH) return WebSuggestionResult.Found(emptyList())
        val url = WebSuggestionProviders.url(engine, trimmed) ?: return WebSuggestionResult.Found(emptyList())
        val target = try { URL(url) } catch (_: java.net.MalformedURLException) {
            return WebSuggestionResult.Unavailable
        }
        return boundedHttpsGet(
            target, ioDispatcher, connectionFactory,
            totalTimeoutMillis = TOTAL_TIMEOUT_MILLIS,
            rejectOversizedContentLength = false,
        ) { parse(it.toString(Charsets.UTF_8), trimmed) } ?: WebSuggestionResult.Unavailable
    }

    companion object {
        const val MIN_QUERY_LENGTH = 2
        const val MAX_QUERY_LENGTH = 200
        const val MAX_SUGGESTIONS = 6
        private const val TOTAL_TIMEOUT_MILLIS = 4_000L

        /** Both providers' browser suggestion format is [query, [plain text suggestions], ...]. */
        internal fun parse(body: String, query: String): WebSuggestionResult = try {
            val suggestions = JSONArray(body).getJSONArray(1)
            WebSuggestionResult.Found(buildList {
                for (index in 0 until suggestions.length()) {
                    val value = (suggestions.opt(index) as? String)?.trim() ?: continue
                    if (value.isEmpty() || value.length > MAX_QUERY_LENGTH || value.any { it.isISOControl() }) continue
                    if (value.equals(query, ignoreCase = true) || any { it.equals(value, ignoreCase = true) }) continue
                    add(value)
                    if (size == MAX_SUGGESTIONS) break
                }
            })
        } catch (_: Exception) {
            WebSuggestionResult.Unavailable
        }
    }
}
