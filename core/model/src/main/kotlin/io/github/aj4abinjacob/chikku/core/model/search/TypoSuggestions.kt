package io.github.aj4abinjacob.chikku.core.model.search

import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.LauncherApp

/**
 * "Did you mean" matching for a query that found nothing ("seigg" → "Swiggy").
 *
 * A label is close when the query is within a few typos (a wrong, missing, extra or swapped letter)
 * of the start of the label or of one of its later words. Short queries tolerate fewer typos so
 * three letters do not match half the drawer. Pure and stateless; never logs.
 */
object TypoSuggestions {

    const val MAX_APPS = 3
    const val MAX_CONTACTS = 3

    /** Typos tolerated for a normalized query of [length] characters; 0 turns suggestions off. */
    fun maxTypos(length: Int): Int = when {
        length < 3 -> 0
        length <= 4 -> 1
        else -> 2
    }

    /**
     * Fewest typos between [needle] and the start of [label] or of a later word in it, or null when
     * that is more than [maxTypos]. Both arguments must already be normalized ([normalizeForSearch]).
     */
    fun distance(label: String, needle: String): Int? {
        val allowed = maxTypos(needle.length)
        if (allowed == 0 || label.isEmpty()) return null
        var best = Int.MAX_VALUE
        for (start in label.indices) {
            if (start > 0 && label[start - 1].isLetterOrDigit()) continue
            if (!label[start].isLetterOrDigit()) continue
            best = minOf(best, prefixDistance(label, start, needle, allowed))
            if (best == 0) break
        }
        return best.takeIf { it <= allowed }
    }

    /** Ranks [apps] by [distance] to [query] (on the custom label, else the app's own), best first. */
    fun apps(apps: List<LauncherApp>, query: String, customLabels: Map<AppKey, String> = emptyMap()): List<LauncherApp> {
        val needle = normalizeForSearch(query)
        if (maxTypos(needle.length) == 0) return emptyList()
        return apps
            .mapIndexedNotNull { index, app ->
                val custom = customLabels[app.key]
                val score = listOfNotNull(custom, app.label)
                    .mapNotNull { distance(normalizeForSearch(it), needle) }
                    .minOrNull() ?: return@mapIndexedNotNull null
                Triple(score, index, if (custom != null) app.copy(label = custom) else app)
            }
            .sortedWith(compareBy({ it.first }, { it.second }))
            .take(MAX_APPS)
            .map { it.third }
    }

    /**
     * Optimal-string-alignment distance between [needle] and the closest prefix of
     * `label.substring(start)` whose length is within [allowed] of the needle's.
     */
    private fun prefixDistance(label: String, start: Int, needle: String, allowed: Int): Int {
        val n = needle.length
        val m = minOf(label.length - start, n + allowed)
        if (m < n - allowed) return Int.MAX_VALUE
        // d[i][j]: distance between needle[0, i) and label[start, start + j).
        val d = Array(n + 1) { IntArray(m + 1) }
        for (i in 0..n) d[i][0] = i
        for (j in 0..m) d[0][j] = j
        for (i in 1..n) {
            for (j in 1..m) {
                val a = needle[i - 1]
                val b = label[start + j - 1]
                var cost = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + if (a == b) 0 else 1)
                if (i > 1 && j > 1 && a == label[start + j - 2] && needle[i - 2] == b) cost = minOf(cost, d[i - 2][j - 2] + 1)
                d[i][j] = cost
            }
        }
        var best = Int.MAX_VALUE
        for (j in maxOf(0, n - allowed)..m) best = minOf(best, d[n][j])
        return best
    }
}
