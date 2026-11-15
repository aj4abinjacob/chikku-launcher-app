package io.github.aj4abinjacob.chikku.settings.cards

import io.github.aj4abinjacob.chikku.core.model.AppCustomization
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.LauncherApp
import io.github.aj4abinjacob.chikku.core.model.WorkspaceItem
import io.github.aj4abinjacob.chikku.core.model.AppsState
import io.github.aj4abinjacob.chikku.core.model.WorkspaceState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

internal data class CardEditorSnapshot(
    val apps: List<LauncherApp>,
    val customizations: Map<AppKey, AppCustomization>,
    val items: List<WorkspaceItem>,
)

/** Repositories may be cold and emit Loading on every collection, including the second read. */
internal suspend fun readCardEditorSnapshot(
    apps: Flow<AppsState>,
    customizations: Flow<Map<AppKey, AppCustomization>>,
    workspace: Flow<WorkspaceState>,
    timeoutMs: Long = 10_000L,
): CardEditorSnapshot? = withTimeoutOrNull(timeoutMs) {
    try {
        val loaded = apps.first { it !is AppsState.Loading } as? AppsState.Loaded ?: return@withTimeoutOrNull null
        val ready = workspace.first { it !is WorkspaceState.Loading } as? WorkspaceState.Ready ?: return@withTimeoutOrNull null
        CardEditorSnapshot(loaded.apps, customizations.first(), ready.workspace.items)
    } catch (_: NoSuchElementException) {
        null // A stream that ended before its first usable value is unavailable too.
    }
}

/** Native validation may suspend; any visibility/placement change invalidates its result. */
internal suspend fun validateCardDraftSnapshot(
    readSnapshot: suspend () -> CardEditorSnapshot?,
    validate: suspend (CardEditorSnapshot) -> Boolean,
): Boolean {
    val before = readSnapshot() ?: return false
    if (!validate(before)) return false
    return readSnapshot() == before
}
