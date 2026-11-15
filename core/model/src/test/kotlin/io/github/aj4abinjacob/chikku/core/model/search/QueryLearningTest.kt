package io.github.aj4abinjacob.chikku.core.model.search

import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.LauncherApp
import io.github.aj4abinjacob.chikku.core.model.ProfileType
import org.junit.Test

/** Synthetic apps, contacts and queries only. */
class QueryLearningTest {
    private val day = 24L * 60 * 60 * 1000
    private val t0 = 1_800_000_000_000L

    private fun app(label: String) = LauncherApp(AppKey("pkg.${label.lowercase().replace(" ", "")}", "Main", 0), label, ProfileType.PERSONAL)
    private val phone = app("Phone")
    private val photos = app("Photos")
    private val maps = app("Maps")
    private val mapsGo = app("Maps Go")
    private val amma = ContactHit(1, "lookup-amma", "Amma")
    private val amazon = app("Amazon")

    private fun target(app: LauncherApp) = HistoryTarget.App(app.key)

    private fun learnTimes(target: HistoryTarget, query: String, times: Int, at: Long = t0, into: List<HistoryEntry> = emptyList()) =
        (1..times).fold(into) { acc, _ -> SearchHistory.learn(acc, target, query, at) }

    private fun results(query: String, vararg apps: LauncherApp, contacts: List<ContactHit>? = null, calculation: CalculationResult? = null) =
        SearchResults(
            query = query,
            apps = apps.toList(),
            appMatchRanks = apps.associate { it.key to LabelMatcher.prepare(query).rank(it.label)!! },
            contacts = contacts?.let(ContactLookup::Found),
            calculation = calculation,
        )

    private fun learned(history: List<HistoryEntry>, results: SearchResults, now: Long = t0) =
        QueryLearning.of(history, results.query, now).apply(results)

    @Test fun onlyTheFirstFewNormalizedCharactersWithALetterAreLearned() {
        assertThat(SearchHistory.learnedPrefixes("WhatsApp")).containsExactly("w", "wh", "wha", "what").inOrder()
        assertThat(SearchHistory.learnedPrefixes("  Éc")).containsExactly("e", "ec").inOrder()
        assertThat(SearchHistory.learnedPrefixes("a b")).containsExactly("a", "a b").inOrder() // no trailing-space key
        assertThat(SearchHistory.learnedPrefixes("1234")).isEmpty() // PINs, codes and numbers are never learned
        assertThat(SearchHistory.learnedPrefixes("   ")).isEmpty()
        // Characters, not UTF-16 units: an emoji is never cut in half.
        assertThat(SearchHistory.learnedPrefixes("ab\uD83D\uDE00x!")).containsExactly("a", "ab", "ab\uD83D\uDE00", "ab\uD83D\uDE00x").inOrder()
    }

    @Test fun learningKeepsPrefixCountsWithoutCountingALaunch() {
        val history = learnTimes(target(photos), "pho", 2)
        val entry = history.single()
        assertThat(entry.prefixes).containsExactly("p", 2.0, "ph", 2.0, "pho", 2.0)
        assertThat(entry.score).isEqualTo(0.0) // the launcher records the launch itself
        assertThat(SearchHistory.rank(history, t0, 9)).isEmpty() // never invents a blank-query suggestion

        // A launch keeps (and decays) what was learned; web queries are not learned.
        val launched = SearchHistory.record(history, target(photos), t0 + 14 * day, 9).single()
        assertThat(launched.score).isEqualTo(1.0)
        assertThat(launched.prefixes["p"]).isWithin(1e-9).of(1.0)
        assertThat(SearchHistory.learn(emptyList(), HistoryTarget.WebQuery("photos"), "pho", t0)).isEmpty()
    }

    @Test fun learnedPrefixesSurviveStorageAndOlderHistoryStillDecodes() {
        val history = learnTimes(target(photos), "photos", 3)
        assertThat(HistoryCodec.decode(HistoryCodec.encode(history))).isEqualTo(history)

        val old = """[{"type":"app","package":"pkg.a","component":"Main","serial":0,"score":1.0,"scoredAt":1,"lastUsed":1,"hours":[${List(24) { "0" }.joinToString(",")}]}]"""
        assertThat(HistoryCodec.decode(old).single().prefixes).isEmpty()

        val bad = old.replace("\"hours\"", "\"prefixes\":{\"ph\":2,\"TOO LONG\":1,\"x\":-1,\"Ab\":1},\"hours\"")
        assertThat(HistoryCodec.decode(bad).single().prefixes).containsExactly("ph", 2.0)
    }

    @Test fun oneLetterNeedsThreeClearPicksToBecomeTheTopResult() {
        // Same match quality: two picks already break the tie, without being a habit yet.
        val tie = learned(learnTimes(target(photos), "p", 2), results("p", phone, photos))
        assertThat(tie.learnedTop).isNull()
        assertThat(tie.apps).containsExactly(photos, phone).inOrder()

        // A weaker letter match ("c" starts a later word) needs a habit to go above "Camera".
        val camera = app("Camera")
        val openCamera = app("Open Camera")
        val query = results("c", camera, openCamera)
        assertThat(learned(learnTimes(target(openCamera), "c", 2), query).rows.first()).isEqualTo(SearchRow.App(camera))
        val habit = learned(learnTimes(target(openCamera), "c", 3), query)
        assertThat(habit.learnedTop).isEqualTo(target(openCamera))
        assertThat(habit.rows.first()).isEqualTo(SearchRow.App(openCamera))
    }

    @Test fun twoLettersNeedTwoPicksAndLongerQueriesFallBackToTheLearnedPrefix() {
        val history = learnTimes(target(photos), "ph", 2)
        assertThat(learned(history, results("ph", phone, photos)).learnedTop).isEqualTo(target(photos))
        // Learned at "ph", typed "pho": still the habit.
        assertThat(learned(history, results("pho", photos)).learnedTop).isEqualTo(target(photos))
    }

    @Test fun anEvenSplitOrAFadedHabitDoesNotTakeTheTop() {
        val split = learnTimes(target(photos), "ph", 3).let { learnTimes(target(phone), "ph", 3, into = it) }
        assertThat(learned(split, results("ph", phone, photos)).learnedTop).isNull()
        assertThat(learned(split, results("ph", phone, photos)).rows.first()).isEqualTo(SearchRow.App(phone))

        // Three picks a month ago count as fewer than one now.
        val faded = learnTimes(target(photos), "p", 3)
        assertThat(learned(faded, results("p", phone, photos), now = t0 + 30 * day).learnedTop).isNull()
    }

    @Test fun picksOfResultsNoLongerShownDoNotCount() {
        // "ph": Phone 3, Photos 2. At "pho" only Photos matches, so Phone's picks are not competition.
        val history = learnTimes(target(phone), "ph", 3).let { learnTimes(target(photos), "ph", 2, into = it) }
        assertThat(learned(history, results("ph", phone, photos)).learnedTop).isNull()
        assertThat(learned(history, results("pho", photos)).learnedTop).isEqualTo(target(photos))
    }

    @Test fun anAppNamedExactlyTheQueryKeepsTheTopUnlessTheHabitIsOverwhelming() {
        val query = results("maps", maps, mapsGo)
        assertThat(learned(learnTimes(target(mapsGo), "maps", 3), query).learnedTop).isNull()
        assertThat(learned(learnTimes(target(mapsGo), "maps", 5), query).learnedTop).isEqualTo(target(mapsGo))
    }

    @Test fun aLearnedContactBecomesThePersonCardOverAnApp() {
        val history = learnTimes(HistoryTarget.Contact(amma.lookupKey), "a", 3)
        val habit = learned(history, results("a", amazon, contacts = listOf(amma)))
        assertThat(habit.rows.first()).isEqualTo(SearchRow.Person(amma))
        assertThat(habit.rows).contains(SearchRow.App(amazon))
    }

    @Test fun aLearnedShortcutBringsItsSectionToTheTopUnderOneHeading() {
        val rotate = ShortcutHit("pkg.camera", "rotate", 0, "Screen capture", null)
        val scan = ShortcutHit("pkg.camera", "scan", 0, "Scan document", null)
        val scanner = app("Scanner")
        val history = learnTimes(HistoryTarget.Shortcut("pkg.camera", "scan", 0), "sc", 2)
        val base = results("sc", scanner).copy(shortcuts = ShortcutLookup.Found(listOf(rotate, scan)))
        val rows = learned(history, base).rows
        assertThat(rows.take(3)).containsExactly(SearchRow.Shortcut(scan), SearchRow.Shortcut(rotate), SearchRow.App(scanner)).inOrder()
        assertThat(rows.count { it is SearchRow.Shortcut }).isEqualTo(2)
    }

    @Test fun aStrongerHabitStillLoadingIsNotPreEmptedByAWeakerOne() {
        // Contact habit 10, app habit 2; the contact lookup has not answered yet.
        val marta = HistoryTarget.Contact("lookup-marta")
        val history = learnTimes(marta, "ma", 10).let { learnTimes(target(maps), "ma", 2, into = it) }
        val offline = results("ma", maps)
        assertThat(QueryLearning.of(history, "ma", t0).apply(offline) { it is HistoryTarget.Contact }.learnedTop).isNull()
        // Once contacts answered without Marta, the app habit stands on its own.
        assertThat(QueryLearning.of(history, "ma", t0).apply(offline) { false }.learnedTop).isEqualTo(target(maps))
    }

    @Test fun aSumKeepsItsCalculationFirst() {
        val history = learnTimes(target(photos), "p", 5)
        val sum = results("p", phone, photos, calculation = CalculationResult.Value(1.0, "1"))
        assertThat(learned(history, sum).learnedTop).isNull()
    }

    @Test fun nothingLearnedLeavesResultsUnchanged() {
        val query = results("p", phone, photos)
        assertThat(learned(emptyList(), query)).isEqualTo(query)
        assertThat(QueryLearning.of(learnTimes(target(photos), "p", 3), "123", t0)).isEqualTo(QueryLearning.NONE)
    }

    @Test fun aShortcutNamedExactlyTheQueryLeadsOverAnAppThatOnlyContainsIt() {
        val enhance = app("Galaxy Enhance-X")
        val site = ShortcutHit("org.mozilla.firefox", "site-x", 0, "X", null)
        val base = results("x", enhance).copy(shortcuts = ShortcutLookup.Found(listOf(site)))
        assertThat(base.rows.first()).isEqualTo(SearchRow.Shortcut(site))

        // An app named exactly the query still comes first.
        val xApp = app("X")
        assertThat(results("x", xApp, enhance).copy(shortcuts = ShortcutLookup.Found(listOf(site))).rows.first()).isEqualTo(SearchRow.App(xApp))

        // A modest app habit does not take the top from the exact shortcut; an overwhelming one does.
        assertThat(learned(learnTimes(target(enhance), "x", 3), base).rows.first()).isEqualTo(SearchRow.Shortcut(site))
        assertThat(learned(learnTimes(target(enhance), "x", 5), base).learnedTop).isEqualTo(target(enhance))
    }
}
