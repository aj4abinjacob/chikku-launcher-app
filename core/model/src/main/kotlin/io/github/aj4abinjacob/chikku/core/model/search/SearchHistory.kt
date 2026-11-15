package io.github.aj4abinjacob.chikku.core.model.search

import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.settings.MiniJson
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.pow

/**
 * Something the user opened, kept by identity only: names, numbers and labels are looked up live
 * when suggestions are shown, so a renamed contact or app never shows a stale name.
 */
sealed interface HistoryTarget {
    data class App(val key: AppKey) : HistoryTarget
    data class Shortcut(val packageName: String, val shortcutId: String, val userSerial: Long) : HistoryTarget
    data class Contact(val lookupKey: String) : HistoryTarget
    data class Folder(val itemId: String) : HistoryTarget

    /** A query the user sent to the web or Play Store. Text typed but never sent is not kept. */
    data class WebQuery(val text: String) : HistoryTarget
}

/**
 * One remembered target. [score] is a frequency that halves every [SearchHistory.HALF_LIFE_MILLIS]
 * (as of [scoredAtMillis]); [hours] is the same decayed count per hour of the day (24 values).
 * [prefixes] is the same decayed count per typed prefix this target was opened from search with
 * (normalized, at most [SearchHistory.MAX_PREFIX_LENGTH] characters; see [QueryLearning]).
 */
data class HistoryEntry(
    val target: HistoryTarget,
    val score: Double,
    val scoredAtMillis: Long,
    val lastUsedMillis: Long,
    val hours: List<Double>,
    val prefixes: Map<String, Double> = emptyMap(),
)

/**
 * On-device search history. [entries] is everything stored for the personal profile, whether or
 * not suggestions are shown; [record] does nothing while learning is off or for targets that must
 * not be learned (hidden, app-locked, other profiles). Never logs and is never written to backups.
 */
interface SearchHistoryRepository {
    val entries: StateFlow<List<HistoryEntry>>
    suspend fun record(target: HistoryTarget)

    /**
     * [target] was opened from search after typing [query]: learn its prefixes ([SearchHistory.learn])
     * under the same rules as [record]. The launch itself is still recorded by [record].
     */
    suspend fun learn(target: HistoryTarget, query: String) {}
    suspend fun forget(target: HistoryTarget)
    suspend fun clear()
}

/** Pure history rules: recording, decay, pruning and ranking. Times are wall-clock milliseconds. */
object SearchHistory {
    const val HALF_LIFE_MILLIS = 14L * 24 * 60 * 60 * 1000
    const val MAX_AGE_MILLIS = 90L * 24 * 60 * 60 * 1000
    const val MAX_ENTRIES = 200
    const val MAX_QUERIES = 30
    const val MAX_QUERY_LENGTH = 200

    /** Entries ranked below this have faded out (about 9 weeks unused after a single use). */
    const val MIN_RANK = 0.05

    /** Typed prefixes are learned up to this many characters: enough to settle ties, never the whole query. */
    const val MAX_PREFIX_LENGTH = 4

    /** Different prefixes kept per target; the weakest go first. */
    const val MAX_PREFIXES_PER_ENTRY = 32

    /** Learned prefix counts below this have faded out and are dropped when the entry is next written. */
    private const val MIN_PREFIX_SCORE = 0.05

    private val DIGITS_ONLY = Regex("[\\d\\s+()-]{6,}")
    private val WHITESPACE = Regex("\\s+")

    /**
     * The query as it would be remembered, or null when it must not be: blank, or only digits
     * (a phone number, PIN or OTP is not search history).
     */
    fun rememberedQuery(text: String): String? {
        val collapsed = WHITESPACE.replace(text.trim(), " ").take(MAX_QUERY_LENGTH)
        if (collapsed.isEmpty() || DIGITS_ONLY.matches(collapsed)) return null
        return collapsed
    }

    /**
     * Shortcut use as a tie-break weight for typed search: the best-ranked remembered shortcut gets
     * the highest value. Only order matters; empty while nothing is remembered.
     */
    fun shortcutWeights(ranked: List<HistoryEntry>): Map<ShortcutKey, Int> {
        val shortcuts = ranked.mapNotNull { it.target as? HistoryTarget.Shortcut }
        return shortcuts.mapIndexed { index, target ->
            ShortcutKey(target.packageName, target.shortcutId, target.userSerial) to shortcuts.size - index
        }.toMap()
    }

    /**
     * The prefixes of [query] that opening something from search teaches: each normalized prefix
     * of 1..[MAX_PREFIX_LENGTH] characters. Empty when the query has no letter (a number, PIN or
     * code is not learned).
     */
    fun learnedPrefixes(query: String): List<String> {
        val key = prefixKey(normalizeForSearch(query))
        if (key.none(Char::isLetter)) return emptyList()
        return (1..key.codePointCount(0, key.length)).map { key.takeCodePoints(it) }.filter { it.isNotBlank() && it == it.trimEnd() }
    }

    /**
     * [entries] with [target] opened from search after typing [query]: each of its
     * [learnedPrefixes] counts one more pick. Frequency and time of day are left to [record] (the
     * launch itself is recorded separately); a target seen first here starts with no frequency.
     * Sent web queries are not learned (their row is always the typed text itself).
     */
    fun learn(entries: List<HistoryEntry>, target: HistoryTarget, query: String, nowMillis: Long): List<HistoryEntry> {
        val prefixes = learnedPrefixes(query)
        if (prefixes.isEmpty() || target is HistoryTarget.WebQuery) return entries
        val previous = entries.firstOrNull { it.target == target }
        val base = previous?.atTime(nowMillis) ?: HistoryEntry(target, 0.0, nowMillis, nowMillis, List(24) { 0.0 })
        val learned = base.prefixes.toMutableMap()
        prefixes.forEach { learned[it] = (learned[it] ?: 0.0) + 1 }
        val kept = if (learned.size <= MAX_PREFIXES_PER_ENTRY) learned
        else learned.entries.sortedByDescending { it.value }.take(MAX_PREFIXES_PER_ENTRY).associate { it.key to it.value }
        val entry = base.copy(prefixes = kept, lastUsedMillis = nowMillis)
        return capped(listOf(entry) + entries.filter { it.target != target }, nowMillis)
    }

    /** The learned part of a normalized query: its first [MAX_PREFIX_LENGTH] characters (code points, so an emoji is never split). */
    internal fun prefixKey(normalized: String): String = normalized.takeCodePoints(MAX_PREFIX_LENGTH)

    private fun String.takeCodePoints(count: Int): String =
        if (codePointCount(0, length) <= count) this else substring(0, offsetByCodePoints(0, count))

    /** [this] with every count decayed to [nowMillis] (same values, newer reference time). */
    internal fun HistoryEntry.atTime(nowMillis: Long): HistoryEntry {
        if (nowMillis <= scoredAtMillis) return this
        return copy(
            score = decay(score, scoredAtMillis, nowMillis),
            scoredAtMillis = nowMillis,
            hours = hours.map { decay(it, scoredAtMillis, nowMillis) },
            prefixes = prefixes.mapValues { decay(it.value, scoredAtMillis, nowMillis) }.filterValues { it >= MIN_PREFIX_SCORE },
        )
    }

    fun decay(value: Double, fromMillis: Long, nowMillis: Long): Double =
        value * 0.5.pow((nowMillis - fromMillis).coerceAtLeast(0).toDouble() / HALF_LIFE_MILLIS)

    /** [entries] with one more use of [target] at [hour] (0..23), most recent first and capped. */
    fun record(entries: List<HistoryEntry>, target: HistoryTarget, nowMillis: Long, hour: Int): List<HistoryEntry> {
        val previous = entries.firstOrNull { it.target == target }?.atTime(nowMillis)
        val hours = (previous?.hours ?: List(24) { 0.0 })
            .mapIndexed { index, value -> if (index == hour.mod(24)) value + 1 else value }
        val entry = HistoryEntry(
            target = target,
            score = (previous?.score ?: 0.0) + 1,
            scoredAtMillis = nowMillis,
            lastUsedMillis = nowMillis,
            hours = hours,
            prefixes = previous?.prefixes.orEmpty(),
        )
        return capped(listOf(entry) + entries.filter { it.target != target }, nowMillis)
    }

    /** Drops entries unused for [MAX_AGE_MILLIS] and evicts the weakest beyond the size caps. */
    fun prune(entries: List<HistoryEntry>, nowMillis: Long): List<HistoryEntry> = capped(entries, nowMillis)

    private fun capped(entries: List<HistoryEntry>, nowMillis: Long): List<HistoryEntry> {
        val fresh = entries.filter { nowMillis - it.lastUsedMillis <= MAX_AGE_MILLIS }
        val queries = fresh.filter { it.target is HistoryTarget.WebQuery }
        val keep = (fresh.filterNot { it.target is HistoryTarget.WebQuery } + queries.strongest(MAX_QUERIES, nowMillis))
            .strongest(MAX_ENTRIES, nowMillis).toSet()
        return fresh.filter { it in keep }
    }

    private fun List<HistoryEntry>.strongest(limit: Int, nowMillis: Long): List<HistoryEntry> =
        if (size <= limit) this else sortedByDescending { decay(it.score, it.scoredAtMillis, nowMillis) }.take(limit)

    /**
     * Suggestion order at [nowMillis] and [hour]: decayed frequency (recency and frequency in one
     * number) times how much more than usual this target is used around this hour, clamped to
     * 0.5..2 so time of day reorders suggestions but never invents them. Ties: newest use, then kind.
     */
    fun rank(entries: List<HistoryEntry>, nowMillis: Long, hour: Int): List<HistoryEntry> =
        entries
            .filter { nowMillis - it.lastUsedMillis <= MAX_AGE_MILLIS }
            .map { it to rankOf(it, nowMillis, hour) }
            .filter { it.second >= MIN_RANK }
            .sortedWith(
                compareByDescending<Pair<HistoryEntry, Double>> { it.second }
                    .thenByDescending { it.first.lastUsedMillis }
                    .thenBy { kindOrder(it.first.target) },
            )
            .map { it.first }

    internal fun rankOf(entry: HistoryEntry, nowMillis: Long, hour: Int): Double {
        val frequency = decay(entry.score, entry.scoredAtMillis, nowMillis)
        val h = hour.mod(24)
        val hours = entry.hours
        val window = hours[h] + 0.5 * (hours[(h + 23) % 24] + hours[(h + 1) % 24])
        val expected = 2 * hours.sum() / 24
        val affinity = ((window + 1) / (expected + 1)).coerceIn(0.5, 2.0)
        return frequency * affinity
    }

    private fun kindOrder(target: HistoryTarget): Int = when (target) {
        is HistoryTarget.App -> 0
        is HistoryTarget.Shortcut -> 1
        is HistoryTarget.Contact -> 2
        is HistoryTarget.Folder -> 3
        is HistoryTarget.WebQuery -> 4
    }
}

/**
 * Stored form of the history. Decoding never throws: oversized or unreadable text gives an empty
 * history, and each malformed row is dropped on its own.
 */
object HistoryCodec {
    const val MAX_TEXT_LENGTH = 256 * 1024
    private const val MAX_ID_LENGTH = 512

    fun encode(entries: List<HistoryEntry>): String = MiniJson.stringify(
        entries.map { entry ->
            target(entry.target) + mapOf(
                "score" to stored(entry.score),
                "scoredAt" to entry.scoredAtMillis,
                "lastUsed" to entry.lastUsedMillis,
                "hours" to entry.hours.map(::stored),
            ) + (if (entry.prefixes.isEmpty()) emptyMap() else mapOf("prefixes" to entry.prefixes.mapValues { stored(it.value) }))
        },
    )

    fun decode(text: String?): List<HistoryEntry> {
        if (text == null || text.length > MAX_TEXT_LENGTH) return emptyList()
        val rows = try { MiniJson.parse(text) as? List<*> } catch (_: RuntimeException) { null } ?: return emptyList()
        return rows.take(SearchHistory.MAX_ENTRIES).mapNotNull { row -> (row as? Map<*, *>)?.let(::entry) }
            .distinctBy { it.target }
    }

    /**
     * Three decimals are far finer than ranking needs (one use is 1.0) and keep the file about a
     * third of its full-precision size, since every launch rewrites it.
     */
    private fun stored(value: Double): Double = Math.round(value * 1000) / 1000.0

    private fun target(target: HistoryTarget): Map<String, Any?> = when (target) {
        is HistoryTarget.App -> mapOf("type" to "app", "package" to target.key.packageName, "component" to target.key.className, "serial" to target.key.userSerial)
        is HistoryTarget.Shortcut -> mapOf("type" to "shortcut", "package" to target.packageName, "id" to target.shortcutId, "serial" to target.userSerial)
        is HistoryTarget.Contact -> mapOf("type" to "contact", "lookup" to target.lookupKey)
        is HistoryTarget.Folder -> mapOf("type" to "folder", "id" to target.itemId)
        is HistoryTarget.WebQuery -> mapOf("type" to "web", "text" to target.text)
    }

    private fun entry(row: Map<*, *>): HistoryEntry? {
        fun text(key: String) = (row[key] as? String)?.takeIf { it.isNotBlank() && it.length <= MAX_ID_LENGTH }
        fun number(key: String) = (row[key] as? Number)?.toDouble()?.takeIf { it.isFinite() && it >= 0 }
        val serial = (row["serial"] as? Long)?.takeIf { it >= 0 }
        val target = when (row["type"]) {
            "app" -> AppKey(text("package") ?: return null, text("component") ?: return null, serial ?: return null).let(HistoryTarget::App)
            "shortcut" -> HistoryTarget.Shortcut(text("package") ?: return null, text("id") ?: return null, serial ?: return null)
            "contact" -> HistoryTarget.Contact(text("lookup") ?: return null)
            "folder" -> HistoryTarget.Folder(text("id") ?: return null)
            "web" -> HistoryTarget.WebQuery(SearchHistory.rememberedQuery(text("text") ?: return null) ?: return null)
            else -> return null
        }
        val hours = (row["hours"] as? List<*>)?.map { (it as? Number)?.toDouble()?.takeIf { v -> v.isFinite() && v >= 0 } ?: return null }
            ?.takeIf { it.size == 24 } ?: return null
        return HistoryEntry(
            target = target,
            score = number("score") ?: return null,
            scoredAtMillis = (row["scoredAt"] as? Long)?.takeIf { it >= 0 } ?: return null,
            lastUsedMillis = (row["lastUsed"] as? Long)?.takeIf { it >= 0 } ?: return null,
            hours = hours,
            prefixes = prefixes(row["prefixes"]),
        )
    }

    /** Optional (older history has none). Keys must look learned; bad keys are dropped one by one. */
    private fun prefixes(value: Any?): Map<String, Double> {
        val map = value as? Map<*, *> ?: return emptyMap()
        return map.entries.mapNotNull { (key, count) ->
            val prefix = (key as? String)?.takeIf { it.isNotBlank() && it.codePointCount(0, it.length) <= SearchHistory.MAX_PREFIX_LENGTH && it == normalizeForSearch(it) }
            val score = (count as? Number)?.toDouble()?.takeIf { it.isFinite() && it > 0 }
            if (prefix != null && score != null) prefix to score else null
        }.take(SearchHistory.MAX_PREFIXES_PER_ENTRY).toMap()
    }
}
