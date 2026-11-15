package io.github.aj4abinjacob.chikku.search

import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.LauncherApp
import io.github.aj4abinjacob.chikku.core.model.ProfileType
import io.github.aj4abinjacob.chikku.core.model.search.ContactHit
import io.github.aj4abinjacob.chikku.core.model.search.SearchRow
import org.junit.Test

class SearchRowIdentityTest {
    private fun app(name: String, serial: Long = 0) = LauncherApp(AppKey("sample.$name", "Main", serial), name, ProfileType.PERSONAL)

    @Test
    fun appIdentityIgnoresRankLabelAndSuggestion() {
        val a = SearchRow.App(app("alpha"))
        val relabelled = SearchRow.App(app("alpha").copy(label = "Renamed"), suggested = true)
        assertThat(a.identityKey()).isEqualTo(relabelled.identityKey())
    }

    @Test
    fun sameComponentInAnotherProfileIsADifferentRow() {
        assertThat(SearchRow.App(app("alpha", 0)).identityKey()).isNotEqualTo(SearchRow.App(app("alpha", 10)).identityKey())
    }

    @Test
    fun differentKindsOfRowForOneTargetDoNotCollide() {
        val contact = ContactHit(1, "lookup", "Sample")
        val keys = listOf(SearchRow.Contact(contact), SearchRow.Person(contact), SearchRow.SuggestedContact(contact)).map { it.identityKey() }
        assertThat(keys.toSet()).hasSize(3)
    }
}
