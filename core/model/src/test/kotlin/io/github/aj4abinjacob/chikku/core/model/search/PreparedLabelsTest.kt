package io.github.aj4abinjacob.chikku.core.model.search

import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.LauncherApp
import io.github.aj4abinjacob.chikku.core.model.ProfileType
import io.github.aj4abinjacob.chikku.core.model.settings.SearchSettings
import kotlinx.coroutines.test.runTest
import org.junit.Test

class PreparedLabelsTest {
    private fun app(index: Int, label: String) =
        LauncherApp(AppKey("pkg.p$index", "pkg.p$index.Main", 0L), label, ProfileType.PERSONAL)

    private val apps = listOf(app(1, "Café"), app(2, "Camera"), app(3, "Calendar"), app(4, "Maps"), app(5, "Camera"))
    private val custom = mapOf(apps[3].key to "Navigation Café")

    @Test
    fun ranksLikeTheDefaultSearcher() {
        val prepared = PreparedLabels(apps, custom)
        for (query in listOf("ca", "cafe", "CAF", " map ", "navig", "calender", "zzz", "")) {
            assertThat(CustomLabelSearch.search(prepared.searcher, apps, query, custom))
                .isEqualTo(CustomLabelSearch.search(DefaultAppSearcher(), apps, query, custom))
        }
    }

    @Test
    fun searchingTheSameSnapshotNormalizesEachDistinctLabelOnce() {
        var calls = 0
        val prepared = PreparedLabels(apps, custom) { calls++; normalizeForSearch(it) }
        assertThat(calls).isEqualTo(0) // nothing is prepared before the first typed query
        prepared.searcher.search(apps, "ca")
        val afterFirst = calls
        // "Camera" appears twice; four distinct app labels plus the custom label.
        assertThat(afterFirst).isEqualTo(5)
        prepared.searcher.search(apps, "cam")
        prepared.searcher.search(apps, "cal")
        assertThat(calls).isEqualTo(afterFirst)
        // A label that is not in the snapshot still works, normalized on demand.
        assertThat(prepared.normalized("Écran")).isEqualTo("ecran")
        assertThat(calls).isEqualTo(afterFirst + 1)
    }

    @Test
    fun universalSearchReusesPreparedLabelsAndKeepsResults() = runTest {
        var calls = 0
        val prepared = PreparedLabels(apps, custom) { calls++; normalizeForSearch(it) }
        val search = UniversalSearch(DefaultAppSearcher(), { ShortcutLookup.NotDefaultHome }, { _, _ -> ContactLookup.Found(emptyList()) })
        val settings = SearchSettings()
        val plain = search.search("ca", apps, emptySet(), settings, custom)
        val first = search.search("ca", apps, emptySet(), settings, custom, labels = prepared)
        val afterFirst = calls
        val second = search.search("cam", apps, emptySet(), settings, custom, labels = prepared)
        assertThat(first).isEqualTo(plain)
        assertThat(second).isEqualTo(search.search("cam", apps, emptySet(), settings, custom))
        assertThat(calls).isEqualTo(afterFirst)
    }

    @Test
    fun providersOnlyReadsShortcutsAndContacts() = runTest {
        var contactQueries = 0
        val search = UniversalSearch(DefaultAppSearcher(), { ShortcutLookup.NotDefaultHome }) { _, _ ->
            contactQueries++
            ContactLookup.Found(emptyList())
        }
        val result = search.providers("nobody", apps, emptySet(), SearchSettings(contactsEnabled = true, shortcutsEnabled = false))
        assertThat(result.contacts).isEqualTo(ContactLookup.Found(emptyList()))
        assertThat(result.shortcuts).isNull()
        assertThat(result.apps).isEmpty()
        assertThat(contactQueries).isEqualTo(1)
    }
}
