package io.github.aj4abinjacob.chikku.core.model.search

/** Shared name ranking for candidate selection and UI; provider-only matches sort last. */
object ContactNameMatcher {
    const val PROVIDER_ONLY = 2_000
    private val separator = Regex("[^\\p{L}\\p{N}]+")

    fun rank(name: String, query: LabelMatcher.Query): Int? {
        val normalized = normalizeForSearch(name)
        if (normalized == query.normalized) return 0
        if (normalized.split(separator).any { it == query.normalized }) return 1
        return query.rankNormalized(normalized)?.plus(1)
    }
}
