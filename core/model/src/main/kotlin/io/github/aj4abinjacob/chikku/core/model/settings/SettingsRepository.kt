package io.github.aj4abinjacob.chikku.core.model.settings

import kotlinx.coroutines.flow.Flow

/** Outcome of [SettingsRepository.importJson]. A rejected import changes nothing. */
sealed interface SettingsImportResult {
    data object Applied : SettingsImportResult
    data class Rejected(val reason: String) : SettingsImportResult
}

/**
 * Source of truth for [LauncherSettings]. Implementations do all I/O off the main thread, emit the
 * current value to every new collector, and never throw from [settings] (a damaged store yields
 * defaults and keeps the damaged data for inspection; it is never silently overwritten).
 */
interface SettingsRepository {
    val settings: Flow<LauncherSettings>

    /** Atomically applies [transform] to the latest value; the result is [LauncherSettings.coerced]. */
    suspend fun update(transform: (LauncherSettings) -> LauncherSettings)

    /** Restores defaults ("Reset to default"). */
    suspend fun reset()

    /** Current settings encoded with [LauncherSettingsCodec]. */
    suspend fun exportJson(): String

    /** Replaces all settings with [json] decoded by [LauncherSettingsCodec]. */
    suspend fun importJson(json: String): SettingsImportResult
}
