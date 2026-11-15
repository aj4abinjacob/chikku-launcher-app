package io.github.aj4abinjacob.chikku.core.model.search

/** Offline literal-first matching. A prepared query is reusable across one result snapshot. */
object LabelMatcher {
    const val EXACT = 0
    const val PREFIX = 1
    const val WORD_PREFIX = 2
    const val SUBSTRING = 3
    const val TOKEN_PREFIX = 4
    const val FUZZY = 1_000
    private val words = Regex("[\\p{L}\\p{N}]+")

    fun prepare(query: String): Query = Query(normalizeForSearch(query))

    class Query internal constructor(val normalized: String) {
        // Literal matching remains unrestricted; only approximate matching has work bounds.
        private val tokens: List<IntArray> = if (normalized.length <= 128) {
            words.findAll(normalized).map { it.value.codePoints().toArray() }.toList()
                .takeIf { it.size in 1..6 && it.all { token -> token.size <= 48 } }.orEmpty()
        } else emptyList()

        fun rank(label: String): Int? = rankNormalized(normalizeForSearch(label))

        fun rankNormalized(label: String): Int? {
            literalRank(label, normalized)?.let { return it }
            if (tokens.isEmpty() || label.length > 256 || (tokens.size == 1 && tokens[0].size <= 3)) return null
            val candidates = words.findAll(label).map { it.value.codePoints().toArray() }.toList()
            if (candidates.size !in tokens.size..24 || candidates.any { it.size > 64 }) return null
            // Assign each query token to a distinct label token. DP over the small query mask
            // is O(labelWords * queryWords * 2^queryWords), independent of word permutations.
            val costs = IntArray(1 shl tokens.size) { Int.MAX_VALUE }
            costs[0] = 0
            for (candidate in candidates) {
                val next = costs.copyOf()
                for (i in tokens.indices) {
                    val token = tokens[i]
                    val budget = when {
                        token.any { !Character.isLetter(it) } || token.size <= 3 -> 0
                        token.size <= 5 -> 1
                        else -> 2
                    }
                    val cost = prefixDistance(token, candidate, budget) ?: continue
                    for (mask in costs.indices) {
                        if (mask and (1 shl i) != 0 || costs[mask] == Int.MAX_VALUE) continue
                        val assigned = mask or (1 shl i)
                        next[assigned] = minOf(next[assigned], costs[mask] + cost)
                    }
                }
                next.copyInto(costs)
            }
            val cost = costs.last()
            if (cost == Int.MAX_VALUE || cost / 64 > minOf(3, tokens.size + 1)) return null
            return if (cost < 64) TOKEN_PREFIX else FUZZY + cost
        }
    }

    /** Rank normalized strings, including the original literal tiers for punctuation/emoji. */
    fun literalRank(label: String, needle: String): Int? {
        if (label == needle) return EXACT
        if (label.startsWith(needle)) return PREFIX
        var index = label.indexOf(needle, startIndex = 1)
        if (index < 0) return null
        while (index >= 0) {
            if (!label[index - 1].isLetterOrDigit()) return WORD_PREFIX
            index = label.indexOf(needle, startIndex = index + 1)
        }
        return SUBSTRING
    }

    /** Bounded optimal-string-alignment distance to a word prefix, over Unicode code points. */
    private fun prefixDistance(query: IntArray, word: IntArray, budget: Int): Int? {
        if (word.size >= query.size && query.indices.all { query[it] == word[it] }) {
            return if (word.size == query.size) 0 else 1
        }
        if (budget == 0 || word.size < query.size - budget) return null
        val columns = minOf(word.size, query.size + budget)
        var previousPrevious = IntArray(columns + 1)
        var previous = IntArray(columns + 1) { it }
        for (i in 1..query.size) {
            val current = IntArray(columns + 1) { budget + 1 }
            current[0] = i
            for (j in maxOf(1, i - budget)..minOf(columns, i + budget)) {
                current[j] = minOf(previous[j] + 1, current[j - 1] + 1,
                    previous[j - 1] + if (query[i - 1] == word[j - 1]) 0 else 1)
                if (i > 1 && j > 1 && query[i - 1] == word[j - 2] && query[i - 2] == word[j - 1]) {
                    current[j] = minOf(current[j], previousPrevious[j - 2] + 1)
                }
            }
            previousPrevious = previous
            previous = current
        }
        var best: Int? = null
        for (j in maxOf(1, query.size - budget)..columns) {
            if (previous[j] > budget) continue
            val cost = previous[j] * 64 + if (j < word.size) 1 else 0
            best = best?.let { minOf(it, cost) } ?: cost
        }
        return best
    }
}
