package io.github.aj4abinjacob.chikku.core.model.search

import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.LauncherApp
import io.github.aj4abinjacob.chikku.core.model.ProfileType
import kotlin.random.Random
import org.junit.Test

class DefaultAppSearcherTest {
    private val searcher = DefaultAppSearcher()

    private fun app(label: String, index: Int = label.hashCode()) =
        LauncherApp(AppKey("pkg.p$index", "pkg.p$index.Main", 0L), label, ProfileType.PERSONAL)

    private fun labels(apps: List<LauncherApp>) = apps.map { it.label }

    @Test
    fun blankQueryReturnsInputUnchanged() {
        val apps = listOf(app("Zeta"), app("Alpha"))
        assertThat(searcher.search(apps, "")).isSameInstanceAs(apps)
        assertThat(searcher.search(apps, "   \t")).isSameInstanceAs(apps)
        // A query made only of combining marks normalizes to empty as well.
        assertThat(searcher.search(apps, "́̈")).isSameInstanceAs(apps)
    }

    @Test
    fun ranksExactThenPrefixThenWordPrefixThenSubstring() {
        val apps = listOf(
            app("Tamap", 1), // substring
            app("Google Maps", 2), // word prefix
            app("Mapster", 3), // prefix
            app("Map", 4), // exact
            app("Calculator", 5), // no match
        )
        assertThat(labels(searcher.search(apps, "map"))).containsExactly("Map", "Mapster", "Google Maps", "Tamap").inOrder()
    }

    @Test
    fun tiesKeepInputOrder() {
        val apps = listOf(app("Notes B", 1), app("Notes A", 2), app("Notes C", 3))
        assertThat(labels(searcher.search(apps, "notes"))).containsExactly("Notes B", "Notes A", "Notes C").inOrder()
    }

    @Test
    fun ignoresCaseAndDiacritics() {
        val apps = listOf(app("Café", 1), app("CAFETERIA", 2), app("Ñandú", 3))
        assertThat(labels(searcher.search(apps, "cafe"))).containsExactly("Café", "CAFETERIA").inOrder()
        assertThat(labels(searcher.search(apps, "  ÇAFÉ "))).containsExactly("Café", "CAFETERIA").inOrder()
        assertThat(labels(searcher.search(apps, "nandu"))).containsExactly("Ñandú")
    }

    @Test
    fun wordPrefixUsesNonAlphanumericBoundaries() {
        val apps = listOf(app("my-files", 1), app("profiles", 2))
        assertThat(labels(searcher.search(apps, "fil"))).containsExactly("my-files", "profiles").inOrder()
        assertThat(DefaultAppSearcher.rank("my-files", "fil")).isEqualTo(DefaultAppSearcher.WORD_PREFIX)
        assertThat(DefaultAppSearcher.rank("profiles", "fil")).isEqualTo(DefaultAppSearcher.SUBSTRING)
    }

    @Test
    fun wordPrefixFoundAfterEarlierSubstringOccurrence() {
        assertThat(DefaultAppSearcher.rank("amap map", "map")).isEqualTo(DefaultAppSearcher.WORD_PREFIX)
    }

    @Test
    fun handlesBlankLabelsEmojiAndRtl() {
        val apps = listOf(app("", 1), app("   ", 2), app("📷 Camera", 3), app("واتساب", 4), app("עברית", 5))
        assertThat(labels(searcher.search(apps, "camera"))).containsExactly("📷 Camera")
        assertThat(labels(searcher.search(apps, "📷"))).containsExactly("📷 Camera")
        assertThat(labels(searcher.search(apps, "واتس"))).containsExactly("واتساب")
        assertThat(searcher.search(apps, "x")).isEmpty()
    }

    @Test
    fun turkishDottedCapitalIMatchesPlainI() {
        assertThat(normalizeForSearch("İstanbul")).isEqualTo("istanbul")
    }

    @Test
    fun fuzzRandomUnicodeAcrossTenThousandAppsNeverThrows() {
        val random = Random(20261010)
        val apps = List(10_000) { index -> app(randomUnicode(random, random.nextInt(0, 40)), index) }
        repeat(300) {
            val query = randomUnicode(random, random.nextInt(0, 12))
            val results = searcher.search(apps, query)
            assertThat(results.size).isAtMost(apps.size)
            // Deterministic: same input, same output.
            assertThat(searcher.search(apps, query)).isEqualTo(results)
        }
        // Prefixes of real labels must find that label.
        repeat(100) {
            val target = apps[random.nextInt(apps.size)]
            val prefix = target.label.take(3)
            if (normalizeForSearch(prefix).isNotEmpty()) {
                assertThat(searcher.search(apps, prefix)).contains(target)
            }
        }
    }

    @Test
    fun veryLongStringsDoNotThrow() {
        val long = "ä".repeat(100_000) + "end"
        val apps = listOf(app(long, 1), app("end", 2))
        assertThat(labels(searcher.search(apps, "END"))).containsExactly("end", long).inOrder()
        assertThat(searcher.search(apps, long)).containsExactly(apps[0])
    }

    @Test
    fun tenThousandAppsWithManyQueriesCompletes() {
        val random = Random(7)
        val words = listOf("mail", "maps", "camera", "clock", "café", "notes", "files", "photos", "music", "chat")
        val apps = List(10_000) { index -> app("${words[index % words.size]} ${random.nextInt()} ${words[random.nextInt(words.size)]}", index) }
        // Sanity check only: completes without hanging; no strict timing so it stays non-flaky.
        var matched = 0
        for (query in listOf("m", "ma", "map", "c", "ca", "caf", "cafe", "x", "notes 1", "o")) {
            matched += searcher.search(apps, query).size
        }
        assertThat(matched).isGreaterThan(0)
    }

    private fun randomUnicode(random: Random, length: Int): String = buildString {
        repeat(length) {
            when (random.nextInt(6)) {
                // Unpaired surrogates and control characters are deliberately included.
                0 -> append(random.nextInt(0, 0x10000).toChar())
                1 -> appendCodePoint(random.nextInt(0x1F300, 0x1FAFF)) // emoji
                2 -> append(random.nextInt(0x0590, 0x06FF).toChar()) // Hebrew/Arabic
                3 -> append(random.nextInt(0x0300, 0x036F).toChar()) // combining marks
                4 -> append(" \t\n‏‎"[random.nextInt(5)])
                else -> append(random.nextInt('a'.code, 'z'.code + 1).toChar())
            }
        }
    }
}
