package io.github.aj4abinjacob.chikku.platform.cards.weather

import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URL
import java.security.cert.Certificate
import java.util.zip.GZIPOutputStream
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

class WeatherHttpClientTest {
    private val endpoint = URL("https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=1&lon=2")
    private class Connection(private val body: ByteArray, private val status: Int = 200,
        private val headers: Map<String, String> = emptyMap()) : HttpsURLConnection(URL("https://api.met.no")) {
        var disconnected = false
        var closed = false
        override fun connect() = Unit
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
        override fun getCipherSuite() = "test"
        override fun getLocalCertificates(): Array<Certificate>? = null
        override fun getServerCertificates(): Array<Certificate> = emptyArray()
        override fun getResponseCode() = status
        override fun getHeaderField(name: String) = headers[name]
        override fun getInputStream(): InputStream = object : ByteArrayInputStream(body) {
            override fun close() { closed = true; super.close() }
        }
    }

    @Test fun rejectsForeignOriginsOldProviderMalformedQueriesAndHighPrecisionWithoutConnecting() = runTest {
        var opened = 0
        val client = BoundedWeatherHttpClient(StandardTestDispatcher(testScheduler)) { opened++; error("Unexpected network") }
        listOf("http://api.met.no/weatherapi/locationforecast/2.0/compact?lat=1&lon=2",
            "https://api.open-meteo.com/v1/forecast", "https://evil.example/weatherapi/locationforecast/2.0/compact?lat=1&lon=2",
            "https://api.met.no:444/weatherapi/locationforecast/2.0/compact?lat=1&lon=2",
            "https://secret@api.met.no/weatherapi/locationforecast/2.0/compact?lat=1&lon=2",
            "$endpoint#fragment", "$endpoint&extra=1", endpoint.toString().replace("lat=1", "lat=1.12345"),
            endpoint.toString().replace("lat=1", "lat=91")).forEach { assertThat(client.get(URL(it), null)).isNull() }
        assertThat(opened).isEqualTo(0)
    }

    @Test fun sendsAppContactGzipAndExactConditionalHeaderClosesAndDisablesCookiesAutomaticRedirects() = runTest {
        val connection = Connection("{}".toByteArray(), headers = mapOf("Expires" to "expiry"))
        val response = BoundedWeatherHttpClient(StandardTestDispatcher(testScheduler)) { connection }
            .get(endpoint, "Wed, 14 Oct 2026 00:00:00 GMT")!!
        assertThat(response.body).isEqualTo("{}")
        assertThat(response.header("expires")).isEqualTo("expiry")
        assertThat(connection.disconnected).isTrue()
        assertThat(connection.closed).isTrue()
        assertThat(connection.instanceFollowRedirects).isFalse()
        assertThat(connection.useCaches).isFalse()
        assertThat(connection.getRequestProperty("Cookie")).isEmpty()
        assertThat(connection.getRequestProperty("User-Agent")).contains("hello@abinjacob.in")
        assertThat(connection.getRequestProperty("Accept-Encoding")).isEqualTo("gzip")
        assertThat(connection.getRequestProperty("If-Modified-Since")).isEqualTo("Wed, 14 Oct 2026 00:00:00 GMT")
        assertThat(connection.connectTimeout).isEqualTo(3_000)
    }

    @Test fun gzipDecodeAndDecodedByteLimitAreEnforced() = runTest {
        fun gzip(bytes: ByteArray) = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(bytes) } }.toByteArray()
        val valid = Connection(gzip("{}".toByteArray()), headers = mapOf("Content-Encoding" to "gzip"))
        assertThat(BoundedWeatherHttpClient(StandardTestDispatcher(testScheduler)) { valid }.get(endpoint, null)?.body).isEqualTo("{}")
        val oversized = Connection(gzip(ByteArray(BoundedWeatherHttpClient.MAX_BYTES + 1)), headers = mapOf("Content-Encoding" to "gzip"))
        assertThat(BoundedWeatherHttpClient(StandardTestDispatcher(testScheduler)) { oversized }.get(endpoint, null)).isNull()
        assertThat(oversized.closed).isTrue()
        assertThat(oversized.disconnected).isTrue()
        // Unknown/chunked length with a huge gzip FNAME header must not evade the wire-byte cap.
        val longHeader = byteArrayOf(0x1f, 0x8b.toByte(), 8, 8, 0, 0, 0, 0, 0, 0) +
            ByteArray(BoundedWeatherHttpClient.MAX_BYTES) { 65 } + byteArrayOf(0)
        val headerBomb = Connection(longHeader, headers = mapOf("Content-Encoding" to "gzip"))
        assertThat(BoundedWeatherHttpClient(StandardTestDispatcher(testScheduler)) { headerBomb }.get(endpoint, null)).isNull()
        assertThat(headerBomb.closed).isTrue()
        assertThat(headerBomb.disconnected).isTrue()
    }

    @Test fun redirectsCannotLeakCoordinatesAndAreBounded() = runTest {
        for (location in listOf("https://evil.example/", endpoint.toString().replace("lon=2", "lon=3"),
            endpoint.toString().replace("https:", "http:"))) {
            var calls = 0
            val redirect = Connection(byteArrayOf(), 302, mapOf("Location" to location))
            assertThat(BoundedWeatherHttpClient(StandardTestDispatcher(testScheduler)) { calls++; redirect }.get(endpoint, null)).isNull()
            assertThat(calls).isEqualTo(1)
            assertThat(redirect.disconnected).isTrue()
        }
        var calls = 0
        val loop = Connection(byteArrayOf(), 307, mapOf("Location" to endpoint.toString()))
        assertThat(BoundedWeatherHttpClient(StandardTestDispatcher(testScheduler)) { calls++; loop }.get(endpoint, null)).isNull()
        assertThat(calls).isEqualTo(3)
    }

    @Test fun statusHeadersSurviveWithoutConsumingErrorOrNotModifiedBodies() = runTest {
        for (status in listOf(304, 403, 429, 503)) {
            val connection = Connection(byteArrayOf(), status, mapOf("Retry-After" to "120"))
            val response = BoundedWeatherHttpClient(StandardTestDispatcher(testScheduler)) { connection }.get(endpoint, null)!!
            assertThat(response.status).isEqualTo(status)
            assertThat(response.body).isNull()
            assertThat(response.header("Retry-After")).isEqualTo("120")
            assertThat(connection.disconnected).isTrue()
        }
    }
}
