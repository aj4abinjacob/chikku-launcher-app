package io.github.aj4abinjacob.chikku.home.widget

import io.github.aj4abinjacob.chikku.core.model.CellPlacement
import io.github.aj4abinjacob.chikku.core.model.ComponentKey
import io.github.aj4abinjacob.chikku.core.model.ContainerType
import io.github.aj4abinjacob.chikku.core.model.EditResult
import io.github.aj4abinjacob.chikku.core.model.ItemTarget
import io.github.aj4abinjacob.chikku.core.model.RejectReason
import io.github.aj4abinjacob.chikku.core.model.Workspace
import io.github.aj4abinjacob.chikku.core.model.WorkspaceRepository
import io.github.aj4abinjacob.chikku.core.model.WorkspaceState
import io.github.aj4abinjacob.chikku.core.model.layout.WorkspaceLayoutEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first

/**
 * The AppWidget operations the bind flow needs, behind an interface so the flow is testable without
 * a system AppWidgetService. Implementations must not throw; failures map to false/null.
 */
interface WidgetPlatform {
    /** Allocates a fresh id from Chikku's host. */
    fun allocateId(): Int

    /** Releases an id; unknown ids are ignored. */
    fun deleteId(appWidgetId: Int)

    /** Binds without UI if the user has already allowed Chikku to bind widgets. */
    fun bindIfAllowed(appWidgetId: Int, provider: ComponentKey): Boolean

    /** The provider currently bound to [appWidgetId], or null if unbound or unknown. */
    fun boundProvider(appWidgetId: Int): ComponentKey?

    /** True if the provider declares a configuration activity that must run before placement. */
    fun needsConfiguration(appWidgetId: Int): Boolean

    /** Every id currently allocated to Chikku's host. */
    fun hostIds(): IntArray
}

/** What the user asked for. */
sealed interface WidgetRequest {
    /**
     * Add [provider] with a default span of [spanX] x [spanY], preferably on [page]. [at] is the
     * desktop cell it was dropped on (from the picker by drag); null picks a free spot.
     */
    data class Add(
        val provider: ComponentKey,
        val spanX: Int,
        val spanY: Int,
        val page: Int,
        val at: CellPlacement? = null,
    ) : WidgetRequest

    /** Give the existing placement [itemId] a fresh id bound to its persisted [provider]. */
    data class Rebind(val itemId: String, val oldId: Int, val provider: ComponentKey) : WidgetRequest
}

/** A request with its allocated id, waiting on system UI (consent or configuration). */
data class PendingWidget(val appWidgetId: Int, val request: WidgetRequest) {
    val provider: ComponentKey
        get() = when (request) {
            is WidgetRequest.Add -> request.provider
            is WidgetRequest.Rebind -> request.provider
        }
}

/** The next thing the host activity must do. */
sealed interface WidgetStep {
    data class NeedsConsent(val pending: PendingWidget) : WidgetStep
    data class NeedsConfiguration(val pending: PendingWidget) : WidgetStep
    data class Done(val outcome: WidgetOutcome) : WidgetStep
}

sealed interface WidgetOutcome {
    data class Added(val itemId: String) : WidgetOutcome
    data class Rebound(val itemId: String) : WidgetOutcome

    /** The user declined consent or configuration; unreferenced IDs are released when storage is readable. */
    data object Cancelled : WidgetOutcome

    /** Storage rejected the placement; uncertain IDs are retained for a later orphan sweep. */
    data class Rejected(val reason: RejectReason) : WidgetOutcome
}

/**
 * Widget binding state machine (docs/ANDROID_APIS.md): allocate → bindIfAllowed or consent →
 * configure if required → persist. Failure/cancel paths release only IDs proven unreferenced;
 * unreadable storage defers cleanup. Production retains old rebind IDs for Undo until an orphan
 * sweep confirms they are no longer needed. Placements are never deleted.
 *
 * All functions do binder and database work: call them off the main thread.
 */
class WidgetBindFlow(
    private val platform: WidgetPlatform,
    private val repository: WorkspaceRepository,
    /** Production performs release under the restore gate, including ordinary/durable Undo. */
    private val releaseUnreferencedId: (suspend (Int) -> Unit)? = null,
    /** Production defers retirement so normal edit Undo and restore Undo can retain the old ID. */
    private val retireOldId: (Int) -> Unit = platform::deleteId,
) {

    suspend fun begin(request: WidgetRequest): WidgetStep {
        val id = platform.allocateId()
        if (id == 0) return WidgetStep.Done(WidgetOutcome.Rejected(RejectReason.STORAGE_ERROR))
        val pending = PendingWidget(id, request)
        return try {
            if (platform.bindIfAllowed(id, pending.provider)) afterBind(pending) else WidgetStep.NeedsConsent(pending)
        } catch (e: CancellationException) {
            releaseIfUnreferenced(id)
            throw e
        } catch (_: Exception) {
            releaseIfUnreferenced(id)
            WidgetStep.Done(WidgetOutcome.Rejected(RejectReason.STORAGE_ERROR))
        }
    }

    /** Result of the system ACTION_APPWIDGET_BIND consent screen. */
    suspend fun onConsentResult(pending: PendingWidget, granted: Boolean): WidgetStep =
        if (granted && platform.boundProvider(pending.appWidgetId) == pending.provider) afterBind(pending) else cancel(pending)

    /** Result of the provider's configuration activity. */
    suspend fun onConfigurationResult(pending: PendingWidget, ok: Boolean): WidgetStep =
        if (ok) commit(pending) else cancel(pending)

    /** Abandons [pending] (e.g. a launch failed) and releases its id. */
    suspend fun cancel(pending: PendingWidget): WidgetStep {
        releaseIfUnreferenced(pending.appWidgetId)
        return WidgetStep.Done(WidgetOutcome.Cancelled)
    }

    private suspend fun afterBind(pending: PendingWidget): WidgetStep =
        if (platform.needsConfiguration(pending.appWidgetId)) WidgetStep.NeedsConfiguration(pending) else commit(pending)

    private suspend fun commit(pending: PendingWidget): WidgetStep {
        // Configuration may outlive the provider or profile; never persist a different binding.
        if (platform.boundProvider(pending.appWidgetId) != pending.provider) return cancel(pending)
        if (pending.request is WidgetRequest.Rebind) {
            val existing = repository.exportContent()?.items?.firstOrNull { it.id == pending.request.itemId }
            if (existing == null) {
                releaseIfUnreferenced(pending.appWidgetId)
                return WidgetStep.Done(WidgetOutcome.Rejected(RejectReason.UNKNOWN_ITEM))
            }
            val widget = existing.target as? ItemTarget.Widget
            if (widget?.provider != pending.provider || widget.appWidgetId != pending.request.oldId) return cancel(pending)
        }
        val result = try {
            when (val request = pending.request) {
                is WidgetRequest.Add -> addAtFreeSpot(pending.appWidgetId, request)
                is WidgetRequest.Rebind -> repository.rebindWidget(request.itemId,
                    ItemTarget.Widget(request.oldId, request.provider), pending.appWidgetId)
            }
        } catch (e: CancellationException) {
            // Cancellation can arrive after the database commit. Unknown storage state retains the
            // ID until a later orphan sweep, rather than destroying a possibly persisted binding.
            releaseIfUnreferenced(pending.appWidgetId)
            throw e
        } catch (_: Exception) {
            releaseIfUnreferenced(pending.appWidgetId)
            return WidgetStep.Done(WidgetOutcome.Rejected(RejectReason.STORAGE_ERROR))
        }
        return when (result) {
            is EditResult.Rejected -> {
                releaseIfUnreferenced(pending.appWidgetId)
                WidgetStep.Done(WidgetOutcome.Rejected(result.reason))
            }
            is EditResult.Applied -> when (val request = pending.request) {
                is WidgetRequest.Add -> WidgetStep.Done(WidgetOutcome.Added(result.itemId))
                is WidgetRequest.Rebind -> {
                    if (request.oldId != 0 && request.oldId != pending.appWidgetId) retireOldId(request.oldId)
                    WidgetStep.Done(WidgetOutcome.Rebound(result.itemId))
                }
            }
        }
    }

    private suspend fun releaseIfUnreferenced(id: Int) = withContext(NonCancellable) {
        val release = releaseUnreferencedId
        if (release != null) {
            try { release(id) } catch (_: Exception) { /* Unknown state defers cleanup. */ }
            return@withContext
        }
        val content = try { repository.exportContent() } catch (_: Exception) { null }
        val retained = try { repository.retainedWidgetIds() } catch (_: Exception) { null }
        if (content != null && retained != null && id !in retained &&
            id !in referencedIds(content.items.asSequence().map { it.target })) platform.deleteId(id)
    }

    private suspend fun addAtFreeSpot(appWidgetId: Int, request: WidgetRequest.Add): EditResult {
        val state = repository.workspace.first { it !is WorkspaceState.Loading }
        val workspace = (state as? WorkspaceState.Ready)?.workspace
            ?: return EditResult.Rejected(RejectReason.STORAGE_ERROR)
        val placement = request.at?.let { nearestFreeTo(workspace, it, request.spanX, request.spanY) }
            ?: WidgetSizing.findSpot(workspace, request.page, request.spanX, request.spanY)
            ?: run {
                val grid = workspace.desktopGrid
                val page = repository.addPage()
                if (page < 0) return EditResult.Rejected(RejectReason.STORAGE_ERROR)
                CellPlacement(
                    ContainerType.DESKTOP, page, 0, 0,
                    request.spanX.coerceIn(1, grid.columns), request.spanY.coerceIn(1, grid.rows),
                )
            }
        return repository.addWidget(appWidgetId, request.provider, placement)
    }

    /** The free spot nearest the drop cell [at], on its page; null if that page has no room. */
    private fun nearestFreeTo(workspace: Workspace, at: CellPlacement, spanX: Int, spanY: Int): CellPlacement? {
        val grid = workspace.desktopGrid
        if (at.container != ContainerType.DESKTOP || at.page !in 0 until maxOf(workspace.pageCount, 1)) return null
        return WorkspaceLayoutEngine.findNearestFree(
            workspace.items, grid, ContainerType.DESKTOP, at.page, at.x, at.y,
            spanX.coerceIn(1, grid.columns), spanY.coerceIn(1, grid.rows),
        )
    }

    /**
     * Ids allocated to the host that no placement references and that are not [keep]. Callers
     * delete them to release ids leaked by an interrupted flow or a removal that was not undone.
     */
    fun orphanIds(referenced: Set<Int>, keep: Set<Int>): List<Int> =
        platform.hostIds().filter { it != 0 && it !in referenced && it !in keep }

    companion object {
        /** All widget ids referenced by workspace items. */
        fun referencedIds(targets: Sequence<ItemTarget>): Set<Int> =
            targets.filterIsInstance<ItemTarget.Widget>().map { it.appWidgetId }.filter { it != 0 }.toSet()
    }
}
