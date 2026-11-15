package io.github.aj4abinjacob.chikku.platform

import android.icu.text.AlphabeticIndex
import android.os.LocaleList
import io.github.aj4abinjacob.chikku.core.model.drawer.DrawerModel
import java.util.Locale

/**
 * Fast-scroller buckets that follow the phone's languages, matching the collated drawer order:
 * accents fold into their letter where the language does (É under E in French, Å its own letter in
 * Swedish), Chinese uses pinyin letters, Japanese kana rows, Korean initial consonants. Digits and
 * symbols share [DrawerModel.OTHER_SECTION]; scripts outside the phone's languages share one
 * overflow bucket after the letters instead of one entry per character.
 *
 * Not thread-safe; rebuilt when the system languages change.
 */
class LocaleSectionIndex(private val locales: () -> LocaleList = LocaleList::getDefault) {

    private var builtFor: LocaleList? = null
    private var index: AlphabeticIndex.ImmutableIndex<Any>? = null

    fun sectionOf(label: String): String {
        val text = label.trimStart { it.isWhitespace() || Character.getType(it) == Character.FORMAT.toInt() }
        val first = text.codePointAtOrNull(0) ?: return DrawerModel.OTHER_SECTION
        if (!Character.isLetter(first)) return DrawerModel.OTHER_SECTION
        val bucket = index().let { it.getBucket(it.getBucketIndex(text)) }
        return when (bucket.labelType) {
            AlphabeticIndex.Bucket.LabelType.NORMAL -> bucket.label
            // A letter the collator sorts before the index's first letter.
            AlphabeticIndex.Bucket.LabelType.UNDERFLOW -> DrawerModel.OTHER_SECTION
            else -> OVERFLOW_SECTION
        }
    }

    private fun index(): AlphabeticIndex.ImmutableIndex<Any> {
        val current = locales()
        index?.takeIf { current == builtFor }?.let { return it }
        val primary = current.get(0) ?: Locale.getDefault()
        val built = AlphabeticIndex<Any>(primary).setMaxLabelCount(MAX_LABELS).apply {
            for (i in 1 until current.size()) addLabels(current.get(i))
            // Most app names are Latin whatever the phone language.
            addLabels(Locale.ENGLISH)
        }.buildImmutableIndex()
        builtFor = current
        index = built
        return built
    }

    private fun String.codePointAtOrNull(i: Int): Int? = if (i < length) codePointAt(i) else null

    companion object {
        /** Apps in a script outside the phone's languages. */
        const val OVERFLOW_SECTION = "…"
        private const val MAX_LABELS = 300
    }
}
