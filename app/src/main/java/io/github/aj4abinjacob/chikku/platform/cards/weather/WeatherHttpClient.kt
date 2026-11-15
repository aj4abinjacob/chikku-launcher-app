package io.github.aj4abinjacob.chikku.platform.cards.weather

import io.github.aj4abinjacob.chikku.BuildConfig
import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.GZIPInputStream
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

data class WeatherHttpResponse(val status: Int, val body: String? = null, val headers: Map<String, String> = emptyMap()) {
    fun header(name: String): String? = headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
}

fun interface WeatherHttpClient {
    suspend fun get(url: URL, lastModified: String?): WeatherHttpResponse?
}

/** MET-only HTTPS, bounded decoded bytes/time/redirects; contact identifies the app, never the user. */
class BoundedWeatherHttpClient(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val connectionFactory: (URL) -> HttpsURLConnection = { it.openConnection() as HttpsURLConnection },
) : WeatherHttpClient {
    override suspend fun get(url: URL, lastModified: String?): WeatherHttpResponse? {
        if (!allowed(url)) return null
        return withTimeoutOrNull(10_000L) {
            coroutineScope {
                val connection = AtomicReference<HttpsURLConnection?>()
                suspendCancellableCoroutine { continuation ->
                    val worker = launch(ioDispatcher) {
                        val result = try {
                            var target = url
                            var response: WeatherHttpResponse? = null
                            for (redirect in 0..MAX_REDIRECTS) {
                                ensureActive()
                                val client = connectionFactory(target)
                                connection.set(client)
                                try {
                                    ensureActive()
                                    client.connectTimeout = 3_000
                                    client.readTimeout = 3_000
                                    client.instanceFollowRedirects = false
                                    client.useCaches = false
                                    client.setRequestProperty("Accept", "application/json")
                                    client.setRequestProperty("Accept-Encoding", "gzip")
                                    client.setRequestProperty("User-Agent", USER_AGENT)
                                    client.setRequestProperty("Cookie", "")
                                    lastModified?.takeIf { it.length <= 128 && it.none(Char::isISOControl) }?.let {
                                        client.setRequestProperty("If-Modified-Since", it)
                                    }
                                    val status = client.responseCode
                                    val headers = listOf("Expires", "Last-Modified", "Retry-After").mapNotNull { name ->
                                        client.getHeaderField(name)?.takeIf { it.length <= 128 && it.none(Char::isISOControl) }
                                            ?.let { name to it }
                                    }.toMap()
                                    if (status in setOf(301, 302, 303, 307, 308)) {
                                        val location = client.getHeaderField("Location") ?: break
                                        val next = URL(target, location)
                                        // Keep the chosen query intact; no redirect may select another city or origin.
                                        if (redirect == MAX_REDIRECTS || !allowed(next) || next.query != url.query) break
                                        target = next
                                        continue
                                    }
                                    if (status != 200 && status != 203) {
                                        response = WeatherHttpResponse(status, headers = headers)
                                        break
                                    }
                                    if (client.contentLengthLong > MAX_BYTES) break
                                    val encoding = client.getHeaderField("Content-Encoding")?.lowercase()
                                    if (encoding != null && encoding !in setOf("gzip", "identity")) break
                                    val bytes = client.inputStream.use { raw ->
                                        val bounded = TransferLimit(raw)
                                        val input = if (encoding == "gzip") GZIPInputStream(bounded) else bounded
                                        input.use {
                                            val output = ByteArrayOutputStream()
                                            val buffer = ByteArray(4096)
                                            while (output.size() <= MAX_BYTES) {
                                                ensureActive()
                                                val count = it.read(buffer, 0, minOf(buffer.size, MAX_BYTES + 1 - output.size()))
                                                if (count < 0) break
                                                output.write(buffer, 0, count)
                                            }
                                            output.toByteArray()
                                        }
                                    }
                                    ensureActive()
                                    if (bytes.size <= MAX_BYTES) response = WeatherHttpResponse(status, bytes.toString(Charsets.UTF_8), headers)
                                    break
                                } finally {
                                    connection.compareAndSet(client, null)
                                    client.disconnect()
                                }
                            }
                            response
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            null
                        } finally {
                            connection.getAndSet(null)?.disconnect()
                        }
                        if (continuation.isActive) continuation.resume(result)
                    }
                    continuation.invokeOnCancellation {
                        worker.cancel()
                        ioDispatcher.dispatch(EmptyCoroutineContext, Runnable { connection.getAndSet(null)?.disconnect() })
                    }
                }
            }
        }
    }

    /** Count wire bytes too, including gzip headers and chunked responses without Content-Length. */
    private class TransferLimit(input: InputStream) : FilterInputStream(input) {
        private var count = 0
        override fun read(): Int = `in`.read().also { if (it >= 0) record(1) }
        override fun read(bytes: ByteArray, offset: Int, length: Int): Int =
            `in`.read(bytes, offset, minOf(length, MAX_BYTES + 1 - count)).also { if (it > 0) record(it) }
        private fun record(size: Int) {
            count += size
            if (count > MAX_BYTES) throw IOException("Weather response exceeds transfer limit")
        }
    }

    companion object {
        internal const val MAX_BYTES = 512 * 1024
        internal const val MAX_REDIRECTS = 2
        internal val USER_AGENT = "ChikkuLauncher/${BuildConfig.VERSION_NAME} (https://www.abinjacob.in; hello@abinjacob.in)"
        internal fun allowed(url: URL): Boolean = url.protocol == "https" && url.userInfo == null &&
            url.port in listOf(-1, 443) && url.ref == null && url.host == "api.met.no" &&
            url.path == "/weatherapi/locationforecast/2.0/compact" &&
            url.query?.matches(Regex("lat=-?\\d{1,2}(?:\\.\\d{1,4})?&lon=-?\\d{1,3}(?:\\.\\d{1,4})?")) == true &&
            url.query.substringAfter("lat=").substringBefore('&').toDoubleOrNull()?.let { it in -90.0..90.0 } == true &&
            url.query.substringAfter("lon=").toDoubleOrNull()?.let { it in -180.0..180.0 } == true
    }
}
