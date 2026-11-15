package io.github.aj4abinjacob.chikku.core.model.search

import io.github.aj4abinjacob.chikku.core.model.settings.WebSearchEngine
import java.net.URI
import java.net.URLEncoder

/**
 * URLs handed to other apps by intent when the user explicitly taps a web or Play Store row.
 * These handoffs only happen on a tap (optional online autocomplete is separate). Pure; the query is always fully percent-encoded (UTF-8) so it cannot inject
 * extra parameters, fragments or a different host.
 */
object WebSearchUrls {

    /** Display name of [engine]; brand names are not translated. */
    fun engineName(engine: WebSearchEngine): String = when (engine) {
        WebSearchEngine.GOOGLE -> "Google"
        WebSearchEngine.DUCKDUCKGO -> "DuckDuckGo"
        WebSearchEngine.BING -> "Bing"
        WebSearchEngine.BRAVE -> "Brave Search"
        WebSearchEngine.STARTPAGE -> "Startpage"
        WebSearchEngine.ECOSIA -> "Ecosia"
    }

    /** HTTPS results page of [engine] for [query] (trimmed). */
    fun webSearch(engine: WebSearchEngine, query: String): String = searchBase(engine) + encode(query)

    /**
     * The web address [engine]'s searches go to, for display (for example `duckduckgo.com`):
     * the host of [webSearch]'s URL without a leading `www.`. Derived, so it can never disagree
     * with where a search is actually sent.
     */
    fun engineHost(engine: WebSearchEngine): String =
        URI(searchBase(engine)).host.removePrefix("www.")

    /** HTTPS home page of [engine], opened by the home search bar's web button before anything is typed. */
    fun homePage(engine: WebSearchEngine): String = "https://${URI(searchBase(engine)).host}/"

    private fun searchBase(engine: WebSearchEngine): String = when (engine) {
        WebSearchEngine.GOOGLE -> "https://www.google.com/search?q="
        WebSearchEngine.DUCKDUCKGO -> "https://duckduckgo.com/?q="
        WebSearchEngine.BING -> "https://www.bing.com/search?q="
        WebSearchEngine.BRAVE -> "https://search.brave.com/search?q="
        WebSearchEngine.STARTPAGE -> "https://www.startpage.com/do/search?query="
        WebSearchEngine.ECOSIA -> "https://www.ecosia.org/search?q="
    }

    /** Play Store app search, opened in the store app when installed. */
    fun playStoreMarket(query: String): String = "market://search?q=${encode(query)}&c=apps"

    /** Web fallback for [playStoreMarket] when no app handles `market:`. */
    fun playStoreWeb(query: String): String = "https://play.google.com/store/search?q=${encode(query)}&c=apps"

    /** RFC 3986 query encoding: spaces as `%20`, everything outside unreserved characters escaped. */
    fun encode(query: String): String =
        URLEncoder.encode(query.trim(), "UTF-8").replace("+", "%20").replace("*", "%2A").replace("%7E", "~")
}
