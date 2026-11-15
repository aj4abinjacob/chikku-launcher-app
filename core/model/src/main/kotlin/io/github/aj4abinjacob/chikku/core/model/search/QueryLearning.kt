package io.github.aj4abinjacob.chikku.core.model.search

/**
 * What search history learned about one typed query: how often each target was opened from search
 * after typing (a prefix of) it, decayed like the rest of the history.
 *
 * [prefix] is the longest learned prefix of the query (at most [SearchHistory.MAX_PREFIX_LENGTH]
 * characters); a longer query falls back to it, so a habit learned at "ph" still helps at "phot".
 * Learning only reorders results that already match the query; it never adds one.
 */
data class QueryLearning(
    val prefix: String = "",
    val weights: Map<HistoryTarget, Double> = emptyMap(),
    /** Normalized length of the typed query; shorter queries need more evidence (see [MIN_PICKS]). */
    val queryLength: Int = 0,
) {
    fun isEmpty(): Boolean = weights.isEmpty()

    /**
     * [results] reordered by this learning:
     * - Among equally good app and shortcut matches, the more often picked comes first.
     * - A clear habit becomes the top result ([SearchResults.learnedTop]), even over a better
     *   letter match: enough picks for the query length, a large share of all picks among the
     *   results shown, and well ahead of the runner-up. An app or shortcut whose name is exactly
     *   the query keeps the top unless the habit is overwhelming.
     * A calculation keeps its place: the learned top is not applied while the query is a sum.
     *
     * [loading] says which targets' source has not answered yet (contacts or shortcuts still being
     * read). While a stronger habit may still arrive from one, nothing is promoted, so the top
     * result does not briefly go to a weaker habit and then jump.
     */
    fun apply(results: SearchResults, loading: (HistoryTarget) -> Boolean = { false }): SearchResults {
        if (isEmpty() || results.query.isBlank()) return results
        val needle = normalizeForSearch(results.query)
        val shortcuts = (results.shortcuts as? ShortcutLookup.Found)?.shortcuts.orEmpty()
        val contacts = (results.contacts as? ContactLookup.Found)?.contacts.orEmpty()
            .sortedBy { SearchResults.contactTier(it.name, needle) }.take(UniversalSearch.MAX_CONTACTS)
        val present = buildSet {
            results.apps.forEach { add(HistoryTarget.App(it.key)) }
            shortcuts.forEach { add(HistoryTarget.Shortcut(it.packageName, it.shortcutId, it.userSerial)) }
            contacts.forEach { add(HistoryTarget.Contact(it.lookupKey)) }
            results.folders.forEach { add(HistoryTarget.Folder(it.itemId)) }
        }
        val shown = weights.filterKeys { it in present }
        if (shown.isEmpty()) return results

        val top = top(shown, results)?.takeUnless { best ->
            val bestWeight = shown.getValue(best)
            weights.any { (target, weight) -> target !in present && weight >= bestWeight && loading(target) }
        }
        val apps = results.apps
            .reorderWithinTiers({ results.appMatchRanks[it.key] }) { shown[HistoryTarget.App(it.key)] ?: 0.0 }
            .let { list -> if (top is HistoryTarget.App) list.sortedByDescending { it.key == top.key } else list }
        val reorderedShortcuts = (results.shortcuts as? ShortcutLookup.Found)?.let { found ->
            ShortcutLookup.Found(
                found.shortcuts.reorderWithinTiers({ DefaultAppSearcher.rank(normalizeForSearch(it.label), needle) }) {
                    shown[HistoryTarget.Shortcut(it.packageName, it.shortcutId, it.userSerial)] ?: 0.0
                },
            )
        } ?: results.shortcuts
        return results.copy(apps = apps, shortcuts = reorderedShortcuts, learnedTop = top)
    }

    private fun top(shown: Map<HistoryTarget, Double>, results: SearchResults): HistoryTarget? {
        if (results.calculation is CalculationResult.Value) return null
        val ranked = shown.entries.sortedByDescending { it.value }
        val best = ranked.first()
        val runnerUp = ranked.getOrNull(1)?.value ?: 0.0
        val total = shown.values.sum()
        // +1 in the denominator: a single pick is never certainty.
        val share = best.value / (total + 1)
        val level = queryLength.coerceIn(1, MIN_PICKS.size) - 1
        if (best.value < MIN_PICKS[level] || share < MIN_SHARE[level] || best.value < MARGIN * runnerUp) return null
        // An app or shortcut named exactly the query keeps the top unless the habit is overwhelming.
        val needle = normalizeForSearch(results.query)
        val exact = results.apps.filter { results.appMatchRanks[it.key] == DefaultAppSearcher.EXACT }.map { HistoryTarget.App(it.key) } +
            (results.shortcuts as? ShortcutLookup.Found)?.shortcuts.orEmpty()
                .filter { DefaultAppSearcher.rank(normalizeForSearch(it.label), needle) == DefaultAppSearcher.EXACT }
                .map { HistoryTarget.Shortcut(it.packageName, it.shortcutId, it.userSerial) }
        if (exact.isNotEmpty() && best.key !in exact && share < EXACT_OVERRIDE_SHARE) return null
        return best.key
    }

    companion object {
        val NONE = QueryLearning()

        /** Picks (decayed) needed to become the top result at 1, 2 and 3+ typed characters. */
        internal val MIN_PICKS = doubleArrayOf(3.0, 2.0, 2.0)

        /** Share of all picks among the shown results needed at 1, 2 and 3+ characters. */
        internal val MIN_SHARE = doubleArrayOf(0.7, 0.6, 0.5)

        /** The habit must be this many times the runner-up, so the top result does not flip-flop. */
        internal const val MARGIN = 1.5

        /** Share needed to put something above an app whose name is exactly the query. */
        internal const val EXACT_OVERRIDE_SHARE = 0.8

        private const val MIN_WEIGHT = 0.05

        /** Learning for [query] from [entries] at [nowMillis]; [NONE] when nothing was learned. */
        fun of(entries: List<HistoryEntry>, query: String, nowMillis: Long): QueryLearning {
            val normalized = normalizeForSearch(query)
            val key = SearchHistory.prefixKey(normalized)
            if (entries.isEmpty() || key.none(Char::isLetter)) return NONE
            for (length in key.codePointCount(0, key.length) downTo 1) {
                val prefix = key.substring(0, key.offsetByCodePoints(0, length))
                val weights = entries.mapNotNull { entry ->
                    val count = entry.prefixes[prefix] ?: return@mapNotNull null
                    val weight = SearchHistory.decay(count, entry.scoredAtMillis, nowMillis)
                    if (weight >= MIN_WEIGHT && nowMillis - entry.lastUsedMillis <= SearchHistory.MAX_AGE_MILLIS) entry.target to weight else null
                }.toMap()
                if (weights.isNotEmpty()) return QueryLearning(prefix, weights, normalized.length)
            }
            return NONE
        }
    }
}

/**
 * Within each run of consecutive items of the same [tier], more [weight] first; the order of tiers
 * and of unweighted items is kept exactly.
 */
private fun <T> List<T>.reorderWithinTiers(tier: (T) -> Int?, weight: (T) -> Double): List<T> {
    if (size < 2) return this
    val out = ArrayList<T>(size)
    var start = 0
    while (start < size) {
        val current = tier(this[start])
        var end = start + 1
        while (end < size && tier(this[end]) == current) end++
        out += subList(start, end).sortedByDescending(weight) // stable
        start = end
    }
    return out
}
