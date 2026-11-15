package io.github.aj4abinjacob.chikku.core.model.search

import io.github.aj4abinjacob.chikku.core.model.AppSearcher
import io.github.aj4abinjacob.chikku.core.model.LauncherApp
import java.text.Normalizer

/**
 * Offline label search, insensitive to case and diacritics.
 *
 * Ranking: literal exact/prefix/word-prefix/substring, then token prefixes and bounded typos. Ties keep the input
 * order, so results are deterministic. A blank query returns the input unchanged. Pure and
 * stateless: safe to call from any thread and never throws for any string.
 */
class DefaultAppSearcher : AppSearcher {

    override fun search(apps: List<LauncherApp>, query: String): List<LauncherApp> =
        search(apps, query, ::normalizeForSearch)

    internal companion object {
        const val EXACT = 0
        const val PREFIX = 1
        const val WORD_PREFIX = 2
        const val SUBSTRING = 3

        /** [DefaultAppSearcher.search] with label normalization supplied by the caller (see [PreparedLabels]). */
        fun search(apps: List<LauncherApp>, query: String, normalizeLabel: (String) -> String): List<LauncherApp> {
            val prepared = LabelMatcher.prepare(query)
            if (prepared.normalized.isEmpty()) return apps
            return apps
                .mapIndexedNotNull { index, app ->
                    prepared.rankNormalized(normalizeLabel(app.label))?.let { Triple(it, index, app) }
                }
                .sortedWith(compareBy({ it.first }, { it.second }))
                .map { it.third }
        }

        /** Match tier of [label] for [needle] (both normalized), or null when it does not match. */
        fun rank(label: String, needle: String): Int? {
            return LabelMatcher.literalRank(label, needle)
        }
    }
}

private val COMBINING_MARKS = Regex("\\p{Mn}+")
private val WHITESPACE = Regex("\\s+")

/**
 * Lower-cases each code point independently (locale- and context-free, so a prefix of a label
 * always normalizes to a prefix of the normalized label), decomposes (NFD), strips combining
 * marks, collapses ASCII whitespace and trims.
 */
fun normalizeForSearch(text: String): String {
    val lower = buildString(text.length) {
        text.codePoints().forEach { appendCodePoint(Character.toLowerCase(it)) }
    }
    val stripped = COMBINING_MARKS.replace(Normalizer.normalize(lower, Normalizer.Form.NFD), "")
    return WHITESPACE.replace(stripped, " ").trim()
}
