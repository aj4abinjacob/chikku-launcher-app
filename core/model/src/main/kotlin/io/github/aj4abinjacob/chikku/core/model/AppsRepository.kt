package io.github.aj4abinjacob.chikku.core.model

import kotlinx.coroutines.flow.Flow

/** Snapshot of the launchable apps visible to the launcher. */
sealed interface AppsState {
    data object Loading : AppsState

    /**
     * [apps] is sorted for display (locale-aware label order, then profile, then key).
     * [listedProfiles] holds the user serials whose app lists were read successfully. A workspace
     * item may only be called "uninstalled" when its profile is in this set; otherwise the profile
     * is paused, locked or failed to list, and the item must not be offered for removal.
     */
    data class Loaded(
        val apps: List<LauncherApp>,
        val listedProfiles: Set<Long> = apps.mapTo(mutableSetOf()) { it.key.userSerial },
    ) : AppsState

    /** Enumeration failed; HOME must stay usable and offer a retry. */
    data class Failed(val reason: String) : AppsState
}

interface AppsRepository {
    /** Emits a fresh snapshot on start and after every coalesced package/profile change. */
    val apps: Flow<AppsState>

    /** Forces a re-enumeration, e.g. from a recovery screen's retry button. */
    fun refresh()
}

/** Pure, offline app search. Implementations must be deterministic and never throw on any input. */
fun interface AppSearcher {
    fun search(apps: List<LauncherApp>, query: String): List<LauncherApp>
}
