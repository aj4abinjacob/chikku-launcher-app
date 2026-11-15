package io.github.aj4abinjacob.chikku.core.model.search

import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.LauncherApp
import io.github.aj4abinjacob.chikku.core.model.ProfileType
import io.github.aj4abinjacob.chikku.core.model.settings.SearchSettings
import kotlinx.coroutines.test.runTest
import org.junit.Test

class FrequentShortcutsTest {
    private fun app(pkg: String, label: String, profile: ProfileType = ProfileType.PERSONAL) =
        LauncherApp(AppKey(pkg, "$pkg.Main", if (profile == ProfileType.WORK) 10L else 0L), label, profile)

    private val pay = app("com.pay", "Pay")
    private val browser = app("com.browser", "Browser")
    private val workMail = app("com.mail", "Mail", ProfileType.WORK)
    private val apps = listOf(pay, browser, workMail)

    private val scan = ShortcutHit("com.pay", "scan", 0, "Scan QR code", pay.key)
    private val send = ShortcutHit("com.pay", "send", 0, "Send money", pay.key)
    private val site = ShortcutHit("com.browser", "pinned-site", 0, "Bank", null) // pinned, no owning activity
    private val workCompose = ShortcutHit("com.mail", "compose", 10, "Compose", workMail.key)
    private val all = listOf(send, scan, site, workCompose)

    private fun search(lookup: ShortcutLookup = ShortcutLookup.Found(all)) =
        UniversalSearch(DefaultAppSearcher(), { lookup }, { _, _ -> ContactLookup.Found(emptyList()) })

    private fun used(vararg hits: ShortcutHit): List<HistoryEntry> =
        hits.mapIndexed { index, hit ->
            HistoryEntry(HistoryTarget.Shortcut(hit.packageName, hit.shortcutId, hit.userSerial), score = 10.0 - index,
                scoredAtMillis = 0, lastUsedMillis = 0, hours = List(24) { 0.0 })
        }

    @Test fun emptySearchShowsRecentPersonalShortcutsIncludingPinnedOnes() = runTest {
        val gone = ShortcutHit("com.gone", "x", 0, "Gone", null)
        val result = search().history(used(scan, workCompose, gone, site), apps, emptySet(), { false }, SearchSettings())
        assertThat(result.recent).containsExactly(RecentItem.Shortcut(scan), RecentItem.Shortcut(site)).inOrder()
    }

    @Test fun hiddenOwnersOrUnavailableSourcesShowNoShortcuts() = runTest {
        val history = used(scan, site)
        assertThat(search().history(history, apps, setOf(pay.key), { false }, SearchSettings()).recent).containsExactly(RecentItem.Shortcut(site))
        assertThat(search(ShortcutLookup.NotDefaultHome).history(history, apps, emptySet(), { false }, SearchSettings()).recent).isEmpty()
    }

    @Test fun historyOrderBecomesTypedSearchWeights() {
        val weights = SearchHistory.shortcutWeights(used(send, scan))
        assertThat(weights.getValue(send.key)).isGreaterThan(weights.getValue(scan.key))
        assertThat(SearchHistory.shortcutWeights(emptyList())).isEmpty()
    }

    @Test fun moreUsedShortcutsRankFirstAmongEqualMatches() = runTest {
        val both = listOf(ShortcutHit("com.pay", "a", 0, "Pay bills", pay.key), ShortcutHit("com.pay", "b", 0, "Pay contact", pay.key))
        val plain = UniversalSearch.filterShortcuts(ShortcutLookup.Found(both), "pay", apps)
        assertThat((plain as ShortcutLookup.Found).shortcuts).containsExactlyElementsIn(both).inOrder()
        val learned = UniversalSearch.filterShortcuts(ShortcutLookup.Found(both), "pay", apps, mapOf(both[1].key to 4))
        assertThat((learned as ShortcutLookup.Found).shortcuts).containsExactly(both[1], both[0]).inOrder()
        // Frequency never lifts a weaker label match over a better one.
        val scanFirst = UniversalSearch.filterShortcuts(ShortcutLookup.Found(all), "scan", apps, mapOf(send.key to 100))
        assertThat((scanFirst as ShortcutLookup.Found).shortcuts).containsExactly(scan)
    }

    @Test fun typedSearchPassesCountsThroughToShortcutOrder() = runTest {
        val bills = ShortcutHit("com.pay", "bills", 0, "Pay bills", pay.key)
        val contact = ShortcutHit("com.pay", "contact", 0, "Pay contact", pay.key)
        val result = search(ShortcutLookup.Found(listOf(bills, contact)))
            .search("pay", apps, emptySet(), SearchSettings(), shortcutCounts = mapOf(contact.key to 2))
        assertThat((result.shortcuts as ShortcutLookup.Found).shortcuts).containsExactly(contact, bills).inOrder()
    }
}
