package io.github.aj4abinjacob.chikku.core.model.search

import io.github.aj4abinjacob.chikku.core.model.settings.WebSearchEngine

/** Receives only user-typed text, never local search results or app/profile identity. */
fun interface WebSuggestionSource {
    suspend fun suggest(engine: WebSearchEngine, query: String): WebSuggestionResult
}

sealed interface WebSuggestionResult {
    data class Found(val suggestions: List<String>) : WebSuggestionResult
    data object Unavailable : WebSuggestionResult
}

object WebSuggestionProviders {
    fun supports(engine: WebSearchEngine): Boolean =
        engine == WebSearchEngine.GOOGLE || engine == WebSearchEngine.DUCKDUCKGO

    fun url(engine: WebSearchEngine, query: String): String? = when (engine) {
        WebSearchEngine.GOOGLE -> "https://www.google.com/complete/search?client=chrome&q=${WebSearchUrls.encode(query)}"
        WebSearchEngine.DUCKDUCKGO -> "https://duckduckgo.com/ac/?type=list&q=${WebSearchUrls.encode(query)}"
        else -> null
    }
}

data class WebSuggestions(
    val query: String = "",
    val engine: WebSearchEngine? = null,
    val values: List<String> = emptyList(),
    val unavailable: Boolean = false,
)
