package io.github.aj4abinjacob.chikku.search

import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.search.WebSuggestionResult
import io.github.aj4abinjacob.chikku.core.model.settings.WebSearchEngine
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.URL
import java.security.cert.Certificate
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HttpsWebSuggestionSourceTest {
    @Test
    fun parsesBrowserSuggestionsAndFiltersUnsafeDuplicateAndExactQueryValues() {
        val result = HttpsWebSuggestionSource.parse(
            """["weather",[" weather ","weather tomorrow","WEATHER TOMORROW",null,12,{"phrase":"ignore"},"bad\ntext","","weather near me"],{}]""",
            "weather",
        )
        assertThat(result).isEqualTo(WebSuggestionResult.Found(listOf("weather tomorrow", "weather near me")))
    }

    @Test
    fun malformedResponseIsUnavailableAndSuggestionsAreBounded() {
        listOf("not json", "{}", "[]", "[\"q\",null]", "[\"q\",{}]").forEach {
            assertThat(HttpsWebSuggestionSource.parse(it, "q")).isEqualTo(WebSuggestionResult.Unavailable)
        }
        val values = (1..10).joinToString(",") { "\"result $it\"" }
        val result = HttpsWebSuggestionSource.parse("[\"q\",[$values]]", "q") as WebSuggestionResult.Found
        assertThat(result.suggestions).hasSize(6)
        assertThat(HttpsWebSuggestionSource.parse("[\"q\",[\"${"x".repeat(201)}\"]]", "q"))
            .isEqualTo(WebSuggestionResult.Found(emptyList()))
    }

    private class Connection(private val body: ByteArray, private val status: Int = 200) : HttpsURLConnection(URL("https://www.google.com")) {
        var disconnected = false
        var closed = false
        override fun connect() = Unit
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
        override fun getCipherSuite() = "test"
        override fun getLocalCertificates(): Array<Certificate>? = null
        override fun getServerCertificates(): Array<Certificate> = emptyArray()
        override fun getResponseCode() = status
        override fun getInputStream(): InputStream = object : ByteArrayInputStream(body) {
            override fun close() { closed = true; super.close() }
        }
    }

    @Test
    fun httpsResponseIsBoundedAndConnectionsAreClosedWithoutRedirectsOrCookies() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val success = Connection("[\"weather\",[\"weather tomorrow\"]]".toByteArray())
        val source = HttpsWebSuggestionSource(dispatcher) { success }
        assertThat(source.suggest(WebSearchEngine.GOOGLE, "weather"))
            .isEqualTo(WebSuggestionResult.Found(listOf("weather tomorrow")))
        assertThat(success.disconnected).isTrue()
        assertThat(success.closed).isTrue()
        assertThat(success.instanceFollowRedirects).isFalse()
        assertThat(success.useCaches).isFalse()
        assertThat(success.getRequestProperty("Cookie")).isEmpty()
        assertThat(success.connectTimeout).isEqualTo(3000)
        assertThat(success.readTimeout).isEqualTo(3000)
        listOf(Connection(ByteArray(32769) { 32 }), Connection(byteArrayOf(), 302), Connection(byteArrayOf(), 503)).forEach { connection ->
            assertThat(HttpsWebSuggestionSource(dispatcher) { connection }.suggest(WebSearchEngine.DUCKDUCKGO, "weather"))
                .isEqualTo(WebSuggestionResult.Unavailable)
            assertThat(connection.disconnected).isTrue()
        }
        assertThat(HttpsWebSuggestionSource(dispatcher) { throw java.io.IOException("offline") }.suggest(WebSearchEngine.GOOGLE, "weather"))
            .isEqualTo(WebSuggestionResult.Unavailable)
    }

    @Test
    fun unsupportedProvidersAndInvalidQueriesNeverOpenAConnection() = runTest {
        val source = HttpsWebSuggestionSource(StandardTestDispatcher(testScheduler))
        listOf("", "a", " ", "x".repeat(201)).forEach {
            assertThat(source.suggest(WebSearchEngine.GOOGLE, it)).isEqualTo(WebSuggestionResult.Found(emptyList()))
        }
        assertThat(source.suggest(WebSearchEngine.BRAVE, "weather")).isEqualTo(WebSuggestionResult.Found(emptyList()))
    }
}
