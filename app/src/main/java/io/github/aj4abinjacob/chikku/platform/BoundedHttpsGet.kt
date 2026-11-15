package io.github.aj4abinjacob.chikku.platform

import java.io.ByteArrayOutputStream
import java.net.URL
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/** Response cap of [boundedHttpsGet]. */
internal const val BOUNDED_HTTPS_MAX_BYTES = 32 * 1024
private const val CONNECT_READ_TIMEOUT_MILLIS = 3_000

/**
 * The hardened HTTPS GET shared by the opt-in network features (weather, web suggestions): a
 * cancellable worker on [ioDispatcher], fixed headers, no redirects, cache or cookies, a capped read
 * of [BOUNDED_HTTPS_MAX_BYTES] and a disconnect on completion or cancellation. Callers decide
 * whether and where to connect; this never validates hosts. Returns [transform] of the body, or null
 * for a timeout ([totalTimeoutMillis]), a non-200 status, an oversized body (also by Content-Length
 * when [rejectOversizedContentLength]) or any I/O failure. [transform] runs on the worker.
 */
internal suspend fun <T : Any> boundedHttpsGet(
    url: URL,
    ioDispatcher: CoroutineDispatcher,
    connectionFactory: (URL) -> HttpsURLConnection,
    totalTimeoutMillis: Long,
    rejectOversizedContentLength: Boolean,
    transform: (ByteArray) -> T,
): T? = withTimeoutOrNull(totalTimeoutMillis) {
    coroutineScope {
        val connection = AtomicReference<HttpsURLConnection?>()
        suspendCancellableCoroutine<T?> { continuation ->
            val worker = launch(ioDispatcher) {
                val result = try {
                    val client = connectionFactory(url)
                    connection.set(client)
                    ensureActive()
                    client.connectTimeout = CONNECT_READ_TIMEOUT_MILLIS
                    client.readTimeout = CONNECT_READ_TIMEOUT_MILLIS
                    client.instanceFollowRedirects = false
                    client.useCaches = false
                    client.setRequestProperty("Accept", "application/json")
                    client.setRequestProperty("User-Agent", "ChikkuLauncher")
                    client.setRequestProperty("Cookie", "")
                    if (client.responseCode != 200 ||
                        (rejectOversizedContentLength && client.contentLengthLong > BOUNDED_HTTPS_MAX_BYTES)
                    ) null else client.inputStream.use { input ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(4_096)
                        while (output.size() <= BOUNDED_HTTPS_MAX_BYTES) {
                            ensureActive()
                            val count = input.read(buffer, 0, minOf(buffer.size, BOUNDED_HTTPS_MAX_BYTES + 1 - output.size()))
                            if (count < 0) break
                            output.write(buffer, 0, count)
                        }
                        ensureActive()
                        if (output.size() > BOUNDED_HTTPS_MAX_BYTES) null else transform(output.toByteArray())
                    }
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
                // Socket teardown can block; keep it off the UI thread even on cancellation.
                ioDispatcher.dispatch(EmptyCoroutineContext, Runnable { connection.getAndSet(null)?.disconnect() })
            }
        }
    }
}
