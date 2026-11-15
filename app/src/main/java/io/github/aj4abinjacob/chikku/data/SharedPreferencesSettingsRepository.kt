package io.github.aj4abinjacob.chikku.data

import android.annotation.SuppressLint
import java.io.IOException
import android.content.Context
import android.content.SharedPreferences
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettingsCodec
import io.github.aj4abinjacob.chikku.core.model.settings.SettingsFormatException
import io.github.aj4abinjacob.chikku.core.model.settings.SettingsImportResult
import io.github.aj4abinjacob.chikku.core.model.settings.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * [SettingsRepository] storing [LauncherSettingsCodec] JSON in one SharedPreferences key.
 *
 * Nothing is read until the first collector or edit, and all reads and writes run on [io]. Writes
 * use `commit()` so an update has reached disk before [update] returns. A stored value that cannot
 * be decoded is not overwritten silently: it is copied to [KEY_DAMAGED] before defaults are used,
 * so it can be inspected or recovered.
 */
class SharedPreferencesSettingsRepository(
    context: Context,
    private val io: CoroutineDispatcher,
    private val prefsName: String = PREFS_NAME,
) : SettingsRepository {
    private val appContext = context.applicationContext
    private val state = MutableStateFlow<LauncherSettings?>(null)
    private val mutex = Mutex()
    /** A failed recovery-copy write must never allow a later edit to overwrite the damaged input. */
    private var pendingDamagedSettings: String? = null

    override val settings: Flow<LauncherSettings> = flow {
        if (state.value == null) mutex.withLock { load() }
        emitAll(state.filterNotNull())
    }.distinctUntilChanged()

    override suspend fun update(transform: (LauncherSettings) -> LauncherSettings) {
        mutex.withLock {
            val current = load()
            val next = transform(current).coerced()
            if (next != current && !write(next)) throw IOException("Settings could not be saved")
        }
    }

    override suspend fun reset() {
        mutex.withLock { if (!write(LauncherSettings())) throw IOException("Settings could not be saved") }
    }

    override suspend fun exportJson(): String = mutex.withLock { LauncherSettingsCodec.encode(load()) }

    override suspend fun importJson(json: String): SettingsImportResult {
        val decoded = try {
            LauncherSettingsCodec.decode(json)
        } catch (e: SettingsFormatException) {
            return SettingsImportResult.Rejected(e.message ?: "invalid settings")
        }
        return mutex.withLock {
            if (write(decoded)) SettingsImportResult.Applied else SettingsImportResult.Rejected("Settings could not be saved")
        }
    }

    /** Must hold [mutex]. */
    private suspend fun load(): LauncherSettings {
        state.value?.let { return it }
        val loaded = withContext(io) {
            val prefs = prefs()
            val raw = prefs.getString(KEY_SETTINGS, null)
            when {
                raw == null -> LauncherSettings()
                else -> LauncherSettingsCodec.decodeOrNull(raw) ?: run {
                    pendingDamagedSettings = raw
                    if (preserveDamage(prefs, raw)) pendingDamagedSettings = null
                    LauncherSettings()
                }
            }
        }
        state.value = loaded
        return loaded
    }

    /** Must hold [mutex]. Publishes only after the value is on disk (the KTX edit {} hides commit()'s result). */
    @SuppressLint("UseKtx")
    private suspend fun write(settings: LauncherSettings): Boolean {
        // Restore/reset can be the first operation on a fresh instance, before any collector loads it.
        load()
        // commit(), not apply(): already on the IO dispatcher, and the caller must know the value is on disk.
        val committed = withContext(io) {
            val preferences = prefs()
            pendingDamagedSettings?.let { raw ->
                if (!preserveDamage(preferences, raw)) return@withContext false
                pendingDamagedSettings = null
            }
            preferences.edit().putString(KEY_SETTINGS, LauncherSettingsCodec.encode(settings)).commit()
        }
        if (committed) state.value = settings
        return committed
    }

    private fun prefs(): SharedPreferences = appContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    @SuppressLint("UseKtx") // The recovery copy must reach disk before damaged input can be replaced.
    private fun preserveDamage(preferences: SharedPreferences, raw: String): Boolean =
        preferences.edit().putString(KEY_DAMAGED, raw).commit()

    companion object {
        const val PREFS_NAME = "chikku_settings"
        const val KEY_SETTINGS = "settings_json"
        const val KEY_DAMAGED = "settings_json_damaged"
    }
}
