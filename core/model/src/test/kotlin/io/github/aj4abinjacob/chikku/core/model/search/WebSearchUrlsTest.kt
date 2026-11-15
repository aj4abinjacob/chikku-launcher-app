package io.github.aj4abinjacob.chikku.core.model.search

import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.settings.WebSearchEngine
import org.junit.Test
import java.net.URI

class WebSearchUrlsTest {

    @Test
    fun everyEngineBuildsAnHttpsUrlOnItsOwnHost() {
        val hosts = mapOf(
            WebSearchEngine.GOOGLE to "www.google.com",
            WebSearchEngine.DUCKDUCKGO to "duckduckgo.com",
            WebSearchEngine.BING to "www.bing.com",
            WebSearchEngine.BRAVE to "search.brave.com",
            WebSearchEngine.STARTPAGE to "www.startpage.com",
            WebSearchEngine.ECOSIA to "www.ecosia.org",
        )
        assertThat(hosts.keys).containsExactlyElementsIn(WebSearchEngine.entries)
        hosts.forEach { (engine, host) ->
            val uri = URI(WebSearchUrls.webSearch(engine, "kotlin coroutines"))
            assertThat(uri.scheme).isEqualTo("https")
            assertThat(uri.host).isEqualTo(host)
            assertThat(uri.rawQuery).endsWith("=kotlin%20coroutines")
            assertThat(WebSearchUrls.engineName(engine)).isNotEmpty()
        }
    }

    @Test
    fun everyEngineHasAHostSummaryMatchingItsSearchUrl() {
        WebSearchEngine.entries.forEach { engine ->
            val host = WebSearchUrls.engineHost(engine)
            assertThat(host).isNotEmpty()
            assertThat(host.isBlank()).isFalse()
            assertThat(host).doesNotContain("/")
            val urlHost = URI(WebSearchUrls.webSearch(engine, "x")).host
            assertThat(urlHost == host || urlHost == "www.$host").isTrue()
        }
        assertThat(WebSearchUrls.engineHost(WebSearchEngine.DUCKDUCKGO)).isEqualTo("duckduckgo.com")
        assertThat(WebSearchUrls.engineHost(WebSearchEngine.GOOGLE)).isEqualTo("google.com")
        assertThat(WebSearchUrls.engineHost(WebSearchEngine.BRAVE)).isEqualTo("search.brave.com")
    }

    @Test
    fun homePageIsTheSearchHostOverHttpsWithNoQuery() {
        WebSearchEngine.entries.forEach { engine ->
            val home = URI(WebSearchUrls.homePage(engine))
            assertThat(home.scheme).isEqualTo("https")
            assertThat(home.host).isEqualTo(URI(WebSearchUrls.webSearch(engine, "x")).host)
            assertThat(home.path).isEqualTo("/")
            assertThat(home.query).isNull()
        }
        assertThat(WebSearchUrls.homePage(WebSearchEngine.GOOGLE)).isEqualTo("https://www.google.com/")
    }

    @Test
    fun queryIsFullyEncodedAndCannotInjectParametersOrHosts() {
        val url = WebSearchUrls.webSearch(WebSearchEngine.GOOGLE, "a&b=c#frag/@evil.com?x 100%+1*~")
        assertThat(url).isEqualTo("https://www.google.com/search?q=a%26b%3Dc%23frag%2F%40evil.com%3Fx%20100%25%2B1%2A~")
        val uri = URI(url)
        assertThat(uri.host).isEqualTo("www.google.com")
        assertThat(uri.fragment).isNull()
        assertThat(uri.query).isEqualTo("q=a&b=c#frag/@evil.com?x 100%+1*~")
    }

    @Test
    fun unicodeIsUtf8PercentEncodedAndQueryIsTrimmed() {
        assertThat(WebSearchUrls.webSearch(WebSearchEngine.DUCKDUCKGO, "  café ☕ "))
            .isEqualTo("https://duckduckgo.com/?q=caf%C3%A9%20%E2%98%95")
        assertThat(WebSearchUrls.webSearch(WebSearchEngine.STARTPAGE, "x"))
            .isEqualTo("https://www.startpage.com/do/search?query=x")
    }

    @Test
    fun playStoreUrls() {
        assertThat(WebSearchUrls.playStoreMarket("open camera")).isEqualTo("market://search?q=open%20camera&c=apps")
        assertThat(WebSearchUrls.playStoreWeb("a&c=books"))
            .isEqualTo("https://play.google.com/store/search?q=a%26c%3Dbooks&c=apps")
    }
}
