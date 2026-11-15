package io.github.aj4abinjacob.chikku.data

import android.content.Context
import io.github.aj4abinjacob.chikku.core.model.AppCustomizationRepository
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.AppsRepository
import io.github.aj4abinjacob.chikku.core.model.AppsState
import io.github.aj4abinjacob.chikku.core.model.ProfileType
import io.github.aj4abinjacob.chikku.core.model.search.HistoryCodec
import io.github.aj4abinjacob.chikku.core.model.search.HistoryEntry
import io.github.aj4abinjacob.chikku.core.model.search.HistoryTarget
import io.github.aj4abinjacob.chikku.core.model.search.SearchHistory
import io.github.aj4abinjacob.chikku.core.model.search.SearchHistoryRepository
import io.github.aj4abinjacob.chikku.core.model.settings.MiniJson
import io.github.aj4abinjacob.chikku.core.model.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

/**
 * Search history on this phone only (`search.historyLearningEnabled`): personal apps launched from
 * Chikku, and contacts, shortcuts, folders and sent web queries opened from search, with the first
 * few normalized characters typed before opening each one (learned top results). Stored by
 * identity, never by label; never logged; excluded from backups (see docs/DATA_MODEL.md).
 *
 * Learning off pauses recording and keeps history. Hidden, app-locked and other-profile apps (and
 * their shortcuts) are never recorded. On first load, counts from the earlier frequent-apps store
 * become app entries, and that store is deleted once the new one is written.
 */
class LocalSearchHistoryRepository(
    context: Context,
    settings: SettingsRepository,
    apps: AppsRepository,
    customizations: AppCustomizationRepository,
    private val isLocked: (packageName: String, userSerial: Long) -> Boolean,
    scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val clock: () -> Long = System::currentTimeMillis,
    private val hourOf: (Long) -> Int = { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).hour },
    private val prefsName: String = "chikku_search_history",
    private val legacyPrefsName: String = "chikku_frequent_apps",
) : SearchHistoryRepository {
    private val appContext = context.applicationContext
    private val mutex = Mutex()
    private val loaded = CompletableDeferred<Unit>()
    private val state = MutableStateFlow<List<HistoryEntry>>(emptyList())
    override val entries = state.asStateFlow()
    private var stored: List<HistoryEntry> = emptyList()
    private var learning = false
    private var visibleApps: Set<AppKey> = emptySet()

    init {
        scope.launch(io) {
            mutex.withLock {
                stored = SearchHistory.prune(load(), clock())
                state.value = stored
            }
            combine(
                settings.settings.map { it.search.historyLearningEnabled }.distinctUntilChanged(),
                apps.apps,
                customizations.customizations,
            ) { on, snapshot, custom ->
                on to (snapshot as? AppsState.Loaded)?.apps.orEmpty()
                    .filter { it.profileType == ProfileType.PERSONAL && custom[it.key]?.hidden != true }
                    .mapTo(HashSet()) { it.key }
            }.collect { (on, visible) ->
                mutex.withLock {
                    learning = on
                    visibleApps = visible
                }
                loaded.complete(Unit)
            }
        }
    }

    override suspend fun record(target: HistoryTarget) = withContext(io) {
        loaded.await()
        mutex.withLock {
            if (!learning || !allowed(target)) return@withLock
            val now = clock()
            update(SearchHistory.record(stored, target, now, hourOf(now)))
        }
    }

    override suspend fun learn(target: HistoryTarget, query: String) = withContext(io) {
        loaded.await()
        mutex.withLock {
            if (!learning || !allowed(target)) return@withLock
            val next = SearchHistory.learn(stored, target, query, clock())
            if (next != stored) update(next)
        }
    }

    override suspend fun forget(target: HistoryTarget) = withContext(io) {
        mutex.withLock { update(stored.filter { it.target != target }) }
    }

    override suspend fun clear() = withContext(io) {
        mutex.withLock { update(emptyList()) }
    }

    private fun allowed(target: HistoryTarget): Boolean = when (target) {
        is HistoryTarget.App -> target.key in visibleApps && !isLocked(target.key.packageName, target.key.userSerial)
        is HistoryTarget.Shortcut -> visibleApps.any { it.packageName == target.packageName && it.userSerial == target.userSerial } &&
            !isLocked(target.packageName, target.userSerial)
        is HistoryTarget.WebQuery -> SearchHistory.rememberedQuery(target.text) == target.text
        is HistoryTarget.Contact, is HistoryTarget.Folder -> true
    }

    private fun update(next: List<HistoryEntry>) {
        stored = next
        write(next)
        state.value = next
    }

    /** IO only. Unreadable history starts empty; it never touches settings or the workspace. */
    private fun load(): List<HistoryEntry> = try {
        val prefs = appContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY, null)
        if (raw != null) HistoryCodec.decode(raw) else migrateFrequentApps()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        emptyList()
    }

    /** Launch counts from the frequent-apps store become app entries (capped, no time-of-day data). */
    private fun migrateFrequentApps(): List<HistoryEntry> {
        val legacy = appContext.getSharedPreferences(legacyPrefsName, Context.MODE_PRIVATE)
        val raw = legacy.getString("counts", null) ?: return emptyList()
        val now = clock()
        val migrated = ((if (raw.length > HistoryCodec.MAX_TEXT_LENGTH) null else MiniJson.parse(raw)) as? List<*>).orEmpty()
            .mapNotNull { row ->
                val map = row as? Map<*, *> ?: return@mapNotNull null
                val pkg = (map["package"] as? String)?.takeIf { it.isNotBlank() && it.length <= 512 } ?: return@mapNotNull null
                val component = (map["component"] as? String)?.takeIf { it.isNotBlank() && it.length <= 512 } ?: return@mapNotNull null
                val serial = (map["serial"] as? Long)?.takeIf { it >= 0 } ?: return@mapNotNull null
                val count = (map["count"] as? Long)?.takeIf { it >= 1 } ?: return@mapNotNull null
                HistoryEntry(HistoryTarget.App(AppKey(pkg, component, serial)), count.coerceAtMost(MIGRATED_MAX_SCORE).toDouble(), now, now, List(24) { 0.0 })
            }
        val pruned = SearchHistory.prune(migrated, now)
        if (write(pruned)) {
            legacy.edit().clear().commit()
            appContext.deleteSharedPreferences(legacyPrefsName)
        }
        return pruned
    }

    private fun write(entries: List<HistoryEntry>): Boolean = try {
        appContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit().putString(KEY, HistoryCodec.encode(entries)).commit()
    } catch (_: RuntimeException) {
        false // Optional suggestions must never interrupt launching or HOME.
    }

    private companion object {
        const val KEY = "entries"
        const val MIGRATED_MAX_SCORE = 20L
    }
}
