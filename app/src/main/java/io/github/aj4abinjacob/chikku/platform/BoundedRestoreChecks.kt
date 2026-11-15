package io.github.aj4abinjacob.chikku.platform

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.FutureTask
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * App-owned, bounded isolation for optional checks. A provider ignoring interruption occupies one
 * of two slots, never the caller's structured job. Saturated checks immediately return unknown.
 * There is no queue, retry, or per-request thread. Late results cannot resume a cancelled await.
 */
class BoundedRestoreChecks : AutoCloseable {
    private fun pool(size: Int, name: String) = ThreadPoolExecutor(
        0,
        size,
        30,
        TimeUnit.SECONDS,
        SynchronousQueue(),
        { task -> Thread(task, name).apply { isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy(),
    )
    private val workers = pool(2, "chikku-restore-check")

    // CancellationSignal.cancel can itself call a remote provider. Never run it on the caller.
    private val cancellations = pool(1, "chikku-restore-cancel")

    suspend fun <T> await(timeoutMs: Long, cancel: () -> Unit = {}, block: suspend () -> T): T? =
        withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { continuation ->
                val task = FutureTask<Unit> {
                    val result = try {
                        runBlocking { block() }
                    } catch (_: Exception) {
                        null
                    }
                    if (continuation.isActive) continuation.resume(result)
                }
                continuation.invokeOnCancellation {
                    task.cancel(true)
                    try {
                        cancellations.execute {
                            try {
                                cancel()
                            } catch (
                                _: Exception,
                            ) { }
                        }
                    } catch (_: RejectedExecutionException) { /* Already saturated: deadline still wins. */ }
                }
                try {
                    workers.execute(task)
                } catch (
                    _: RejectedExecutionException,
                ) {
                    if (continuation.isActive) continuation.resume(null)
                }
            }
        }

    override fun close() {
        workers.shutdownNow()
        cancellations.shutdownNow()
    }
}
