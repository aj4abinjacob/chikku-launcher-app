package io.github.aj4abinjacob.chikku.core.model.search

import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.LauncherApp
import io.github.aj4abinjacob.chikku.core.model.ProfileType
import io.github.aj4abinjacob.chikku.core.model.settings.SearchSettings
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SearchHistoryTest {
    private val day = 24L * 60 * 60 * 1000
    private val t0 = 1_800_000_000_000L
    private fun app(name: String) = HistoryTarget.App(AppKey("pkg.$name", "pkg.$name.Main", 0))

    private fun recordTimes(target: HistoryTarget, times: Int, at: Long, hour: Int, into: List<HistoryEntry> = emptyList()) =
        (1..times).fold(into) { acc, _ -> SearchHistory.record(acc, target, at, hour) }

    @Test fun recordingIsNewestFirstWithoutDuplicates() {
        val history = recordTimes(app("a"), 2, t0, 9).let { SearchHistory.record(it, app("b"), t0, 9) }
        assertThat(history.map { it.target }).containsExactly(app("b"), app("a")).inOrder()
        assertThat(history.last().score).isEqualTo(2.0)
    }

    @Test fun frequencyHalvesEveryTwoWeeksSoOldHabitsFade() {
        assertThat(SearchHistory.decay(8.0, t0, t0 + 14 * day)).isWithin(1e-9).of(4.0)
        assertThat(SearchHistory.decay(8.0, t0, t0 - day)).isEqualTo(8.0) // clock moved back: no growth
        // Used a lot two months ago vs a few times this week: this week wins.
        val history = recordTimes(app("old"), 10, t0, 9) + recordTimes(app("new"), 3, t0 + 60 * day, 9)
        assertThat(SearchHistory.rank(history, t0 + 61 * day, 9).map { it.target }.first()).isEqualTo(app("new"))
    }

    @Test fun timeOfDayReordersButNeverInventsSuggestions() {
        // Morning app (used at 8) and evening app (used at 21), equally often.
        val history = recordTimes(app("morning"), 5, t0, 8) + recordTimes(app("evening"), 5, t0, 21)
        assertThat(SearchHistory.rank(history, t0, 8).first().target).isEqualTo(app("morning"))
        assertThat(SearchHistory.rank(history, t0, 21).first().target).isEqualTo(app("evening"))
        // Midnight wraps: 23 counts as next to 0.
        val late = recordTimes(app("late"), 5, t0, 23) + recordTimes(app("noon"), 5, t0, 12)
        assertThat(SearchHistory.rank(late, t0, 0).first().target).isEqualTo(app("late"))
        // Affinity is clamped: a much more frequent app still wins outside its usual hour.
        val clamped = recordTimes(app("often"), 20, t0, 12) + recordTimes(app("rare"), 2, t0, 8)
        assertThat(SearchHistory.rank(clamped, t0, 8).first().target).isEqualTo(app("often"))
    }

    @Test fun oldEntriesExpireAndCapsHold() {
        val old = recordTimes(app("old"), 1, t0, 9)
        assertThat(SearchHistory.prune(old, t0 + 91 * day)).isEmpty()
        assertThat(SearchHistory.rank(old, t0 + 91 * day, 9)).isEmpty()

        val many = (1..SearchHistory.MAX_ENTRIES + 20).fold(emptyList<HistoryEntry>()) { acc, i -> SearchHistory.record(acc, app("a$i"), t0, 9) }
        assertThat(many).hasSize(SearchHistory.MAX_ENTRIES)
        val queries = (1..50).fold(emptyList<HistoryEntry>()) { acc, i -> SearchHistory.record(acc, HistoryTarget.WebQuery("q$i"), t0, 9) }
        assertThat(queries).hasSize(SearchHistory.MAX_QUERIES)
    }

    @Test fun onlyRealQueriesAreRemembered() {
        assertThat(SearchHistory.rememberedQuery("  pizza   near me ")).isEqualTo("pizza near me")
        assertThat(SearchHistory.rememberedQuery("   ")).isNull()
        assertThat(SearchHistory.rememberedQuery("123456")).isNull()
        assertThat(SearchHistory.rememberedQuery("+91 98765 43210")).isNull()
        assertThat(SearchHistory.rememberedQuery("x".repeat(500))).hasLength(SearchHistory.MAX_QUERY_LENGTH)
    }

    @Test fun codecRoundTripsAndSurvivesCorruption() {
        val history = listOf(app("a"), HistoryTarget.Shortcut("pkg.a", "compose", 0), HistoryTarget.Contact("lk"),
            HistoryTarget.Folder("folder-1"), HistoryTarget.WebQuery("weather")).fold(emptyList<HistoryEntry>()) { acc, t ->
            SearchHistory.record(acc, t, t0, 7)
        }
        assertThat(HistoryCodec.decode(HistoryCodec.encode(history))).isEqualTo(history)
        assertThat(HistoryCodec.decode(null)).isEmpty()
        assertThat(HistoryCodec.decode("{not json")).isEmpty()
        assertThat(HistoryCodec.decode("x".repeat(HistoryCodec.MAX_TEXT_LENGTH + 1))).isEmpty()
        // Decayed values are stored to three decimals, which keeps the full history small.
        val decayed = SearchHistory.record(history, HistoryTarget.WebQuery("weather"), t0 + 5 * day, 7)
        HistoryCodec.decode(HistoryCodec.encode(decayed)).zip(decayed).forEach { (read, original) ->
            assertThat(read.score).isWithin(0.0005).of(original.score)
            read.hours.zip(original.hours).forEach { (a, b) -> assertThat(a).isWithin(0.0005).of(b) }
        }
        val full = (1..SearchHistory.MAX_ENTRIES).fold(emptyList<HistoryEntry>()) { acc, i ->
            SearchHistory.record(acc, HistoryTarget.App(AppKey("com.example.app$i", "com.example.app$i.MainActivity", 0)), t0 + i * 3_600_000L, i % 24)
        }.let { SearchHistory.prune(it, t0 + 300 * 3_600_000L).map { e -> e.copy(hours = e.hours.map { h -> SearchHistory.decay(h + 0.123456789, t0, t0 + 3 * day) }) } }
        assertThat(HistoryCodec.encode(full).length).isLessThan(64 * 1024)
        val oneBad = HistoryCodec.encode(history).replaceFirst("\"type\":\"app\"", "\"type\":\"spaceship\"")
        assertThat(HistoryCodec.decode(oneBad)).hasSize(history.size - 1)
    }

    @Test fun historyResolvesLiveAndLeavesOutWhatIsNoLongerVisible() = runTest {
        fun launcherApp(name: String, profile: ProfileType = ProfileType.PERSONAL) =
            LauncherApp(app(name).key, name.replaceFirstChar { it.uppercase() }, profile)
        val mail = launcherApp("mail")
        val notes = launcherApp("notes")
        val locked = launcherApp("bank")
        val ann = ContactHit(1, "lk-ann", "Ann")
        val compose = ShortcutHit(mail.key.packageName, "compose", 0, "Compose", mail.key)
        val search = UniversalSearch(
            DefaultAppSearcher(),
            { ShortcutLookup.Found(listOf(compose)) },
            object : ContactSource {
                override suspend fun contacts(query: String, limit: Int) = ContactLookup.Found(emptyList())
                override suspend fun byLookupKeys(lookupKeys: List<String>) = ContactLookup.Found(listOf(ann).filter { it.lookupKey in lookupKeys })
            },
        )
        val ranked = listOf(
            app("mail"), app("bank"), app("gone"), app("notes"),
            HistoryTarget.Contact("lk-ann"), HistoryTarget.Contact("lk-deleted"),
            HistoryTarget.Shortcut(mail.key.packageName, "compose", 0), HistoryTarget.Shortcut(mail.key.packageName, "removed", 0),
            HistoryTarget.Folder("missing-folder"), HistoryTarget.WebQuery("weather"),
        ).map { HistoryEntry(it, 1.0, t0, t0, List(24) { 0.0 }) }
        val all = SearchSettings(contactsEnabled = true, shortcutsEnabled = true)
        val result = search.history(ranked, listOf(mail, notes, locked), hidden = setOf(notes.key), locked = { it == locked.key }, settings = all,
            customLabels = mapOf(mail.key to "Post"))
        assertThat(result.apps.map { it.label }).containsExactly("Post")
        assertThat(result.recent).containsExactly(RecentItem.Contact(ann), RecentItem.Shortcut(compose), RecentItem.Query("weather")).inOrder()

        val offline = search.history(ranked, listOf(mail), emptySet(), { false }, all, readProviders = false)
        assertThat(offline.recent).containsExactly(RecentItem.Query("weather"))
        val contactsOff = search.history(ranked, listOf(mail), emptySet(), { false }, all.copy(contactsEnabled = false))
        assertThat(contactsOff.recent.none { it is RecentItem.Contact }).isTrue()
    }
}
