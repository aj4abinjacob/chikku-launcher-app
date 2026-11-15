package io.github.aj4abinjacob.chikku.core.model.backup

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Serializes store operations with restore and withholds observations until recovery completes. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RestoreAccess(private val recover: suspend () -> Boolean = { true }) {
    private enum class Status { WAITING, READY, RESTORING, FAILED }
    private val mutex = Mutex()
    private val status = MutableStateFlow(Status.WAITING)
    private var recovered = false

    private class Owner(val access: RestoreAccess) : AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<Owner>
    }

    /** Nested repository calls in a restore use the same ownership across dispatcher changes. */
    suspend fun <T> run(onFailure: T, block: suspend () -> T): T {
        if (currentCoroutineContext()[Owner]?.access === this) return block()
        return mutex.withLock {
            withContext(Owner(this)) {
                if (!recovered) {
                    recovered = try { recover() } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { false }
                    status.value = if (recovered) Status.READY else Status.FAILED
                }
                if (recovered) block() else onFailure
            }
        }
    }

    /** Called only while owning run's lock. A failed rollback blocks further store access. */
    fun requireRecovery() { recovered = false; status.value = Status.FAILED }

    suspend fun <T> restoring(onFailure: T, block: suspend () -> T): T = run(onFailure) {
        status.value = Status.RESTORING
        try { block() } finally { status.value = if (recovered) Status.READY else Status.FAILED }
    }

    fun <T> observe(source: Flow<T>, unavailable: T): Flow<T> = flow {
        run(false) { true }
        emitAll(status.flatMapLatest { state ->
            when (state) {
                Status.READY -> source
                Status.FAILED -> flowOf(unavailable)
                else -> kotlinx.coroutines.flow.emptyFlow()
            }
        })
    }
}
