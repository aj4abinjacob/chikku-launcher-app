package io.github.aj4abinjacob.chikku.home.widget

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Application-owned allocation/commit/sweep boundary, shared by successive HOME instances. */
class WidgetOperationCoordinator {
    private val gate = Mutex()
    private val pendingIds = java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()

    /** Saved-state restoration reserves synchronously, before the first workspace render. */
    fun reserveRestored(id: Int) {
        if (id > 0) pendingIds.add(id)
    }

    suspend fun step(previous: PendingWidget?, operation: suspend () -> WidgetStep): WidgetStep = gate.withLock {
        val result = operation()
        when (result) {
            is WidgetStep.NeedsConsent -> pendingIds.add(result.pending.appWidgetId)
            is WidgetStep.NeedsConfiguration -> pendingIds.add(result.pending.appWidgetId)
            is WidgetStep.Done -> previous?.let { pendingIds.remove(it.appWidgetId) }
        }
        result
    }

    /** References must be read inside this boundary, after an older controller's commit finishes. */
    suspend fun sweep(operation: suspend (Set<Int>) -> Unit) = gate.withLock {
        operation(pendingIds.toSet())
    }
}
