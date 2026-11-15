package io.github.aj4abinjacob.chikku.core.model.search

import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.LauncherApp
import io.github.aj4abinjacob.chikku.core.model.ProfileType
import io.github.aj4abinjacob.chikku.core.model.settings.SearchSettings
import kotlinx.coroutines.test.runTest
import org.junit.Test

class TypoSearchTest {
    private fun app(name: String, id: Int = name.hashCode(), profile: Long = 0) =
        LauncherApp(AppKey("p$id", "p$id.Main", profile), name, ProfileType.PERSONAL)
    private val searcher = DefaultAppSearcher()

    @Test fun acceptsBoundedSubstitutionTranspositionInsertionDeletionAndPartialTyping() {
        for (query in listOf("blonkit", "bilnkit", "bliinkit", "blnkit", "blonk")) {
            assertThat(searcher.search(listOf(app("Blinkit")), query).map { it.label }).containsExactly("Blinkit")
        }
        assertThat(searcher.search(listOf(app("Askhay Albert")), "Akshiy Albort").map { it.label })
            .containsExactly("Askhay Albert")
        assertThat(searcher.search(listOf(app("Éléphant Café")), "elephnat cafe")).hasSize(1)
    }

    @Test fun shortUnrelatedAndRepeatedWordsDoNotLooselyMatch() {
        for (query in listOf("blo", "bxxxkit", "akshiy zzzz", "albert albert")) {
            assertThat(searcher.search(listOf(app("Blinkit"), app("Askhay Albert")), query)).isEmpty()
        }
        assertThat(searcher.search(listOf(app("Maps Mail")), "maps maps")).isEmpty()
        assertThat(searcher.search(listOf(app("Albert Albert")), "albort albort")).hasSize(1)
    }

    @Test fun literalMatchesComeBeforeFuzzyAndTiesAreStable() {
        val apps = listOf(app("Blinkit", 1), app("Blonkit Maps", 2), app("Blonkit", 3), app("Blinkit", 4))
        assertThat(searcher.search(apps, "blonkit").map { it.key })
            .containsExactly(apps[2].key, apps[1].key, apps[0].key, apps[3].key).inOrder()
    }

    @Test fun customAndOriginalLabelsUseTheirBestRankTogether() {
        val exact = app("Blinkit", 1)
        val weak = app("Elsewhere", 2)
        val result = CustomLabelSearch.search(searcher, listOf(weak, exact), "blinkit",
            mapOf(weak.key to "Blonkit", exact.key to "Shopping"))
        assertThat(result.map { it.key }).containsExactly(exact.key, weak.key).inOrder()
        assertThat(result.first().label).isEqualTo("Shopping")
    }

    @Test fun codePointsAndWorkBoundsPreserveLiteralMatching() {
        val supplementary = "\uD801\uDC00\uD801\uDC01\uD801\uDC02\uD801\uDC03"
        val swapped = "\uD801\uDC00\uD801\uDC02\uD801\uDC01\uD801\uDC03"
        assertThat(LabelMatcher.prepare(swapped).rank(supplementary)).isAtLeast(LabelMatcher.FUZZY)
        assertThat(LabelMatcher.prepare("ab").rank("ac")).isNull()
        assertThat(LabelMatcher.prepare("x".repeat(100_000)).rank("x".repeat(100_000))).isEqualTo(LabelMatcher.EXACT)
        assertThat(LabelMatcher.prepare("albert ".repeat(100)).rank("albort ".repeat(100))).isNull()
    }

    @Test fun fuzzyRowsAreSuggestionsAndCannotPromoteAPersonOverAnExactOriginalApp() = runTest {
        val original = app("Albert", 1)
        val hidden = app("Albort", 2)
        val profileCopy = app("Albert", 3, 20)
        val contact = ContactHit(1, "key", "Albert")
        val universal = UniversalSearch(searcher, ShortcutSource { ShortcutLookup.Found(emptyList()) },
            ContactSource { _, _ -> ContactLookup.Found(listOf(contact)) })
        val typo = universal.search("albort", listOf(original, hidden, profileCopy), setOf(hidden.key, profileCopy.key),
            SearchSettings(contactsEnabled = true))
        assertThat(typo.rows.filter { it is SearchRow.App || it is SearchRow.Contact }).containsExactly(SearchRow.App(original, true), SearchRow.Contact(contact, true)).inOrder()
        assertThat(typo.appMatchRanks.keys).containsExactly(original.key)
        val exact = universal.search("albert", listOf(original), emptySet(), SearchSettings(contactsEnabled = true),
            mapOf(original.key to "Shopping"))
        assertThat(exact.rows.first()).isEqualTo(SearchRow.App(original.copy(label = "Shopping")))
        assertThat(exact.rows.filterIsInstance<SearchRow.Person>()).isEmpty()
    }
}
