package io.github.aj4abinjacob.chikku.badges

import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.AppKey
import org.junit.Test

class BadgeAggregatorTest {
    private fun candidate(
        pkg: String,
        serial: Long = 0,
        number: Int = 0,
        ongoing: Boolean = false,
        summary: Boolean = false,
        canShowBadge: Boolean = true,
        hasContent: Boolean = true,
        time: Long = 0,
        ref: String = "$pkg@$serial#$time",
    ) = BadgeCandidate(BadgeKey(pkg, serial), number, ongoing, summary, canShowBadge, hasContent, time, ref)

    @Test
    fun countsArePerPackageAndProfile() {
        val result = BadgeAggregator.aggregate(
            listOf(
                candidate("mail", serial = 0),
                candidate("mail", serial = 0, number = 4),
                candidate("mail", serial = 10),
                candidate("chat", serial = 0, number = 2),
            ),
        ) { true }

        assertThat(result.mapValues { it.value.count }).containsExactly(
            BadgeKey("mail", 0), 5,
            BadgeKey("mail", 10), 1,
            BadgeKey("chat", 0), 2,
        )
    }

    @Test
    fun ongoingSummariesNonBadgeableAndEmptyAreIgnored() {
        val result = BadgeAggregator.aggregate(
            listOf(
                candidate("music", ongoing = true),
                candidate("chat", summary = true, number = 9),
                candidate("chat", number = 1),
                candidate("silent", canShowBadge = false),
                candidate("fgs", hasContent = false),
            ),
        ) { true }

        assertThat(result.keys).containsExactly(BadgeKey("chat", 0))
        assertThat(result.getValue(BadgeKey("chat", 0)).count).isEqualTo(1)
    }

    @Test
    fun hiddenProfilesAreDropped() {
        val asked = mutableListOf<Long>()
        val result = BadgeAggregator.aggregate(
            listOf(candidate("a", serial = 0), candidate("a", serial = 10), candidate("b", serial = 10)),
        ) { serial -> asked += serial; serial == 0L }

        assertThat(result.keys).containsExactly(BadgeKey("a", 0))
        assertThat(asked).containsExactly(0L, 10L) // visibility checked once per profile
    }

    @Test
    fun latestNotificationWinsForAvatar() {
        val result = BadgeAggregator.aggregate(
            listOf(
                candidate("chat", time = 5, ref = "middle"),
                candidate("chat", time = 9, ref = "newest"),
                candidate("chat", time = 1, ref = "oldest"),
            ),
        ) { true }

        assertThat(result.getValue(BadgeKey("chat", 0)).latest).isEqualTo("newest")
    }

    @Test
    fun countsDoNotOverflow() {
        val result = BadgeAggregator.aggregate(
            listOf(candidate("x", number = Int.MAX_VALUE), candidate("x", number = 5)),
        ) { true }
        assertThat(result.getValue(BadgeKey("x", 0)).count).isEqualTo(Int.MAX_VALUE)
    }

    @Test
    fun countFormatting() {
        assertThat(formatBadgeCount(0)).isEmpty()
        assertThat(formatBadgeCount(1)).isEqualTo("1")
        assertThat(formatBadgeCount(99)).isEqualTo("99")
        assertThat(formatBadgeCount(100)).isEqualTo("99+")
        assertThat(formatBadgeCount(Int.MAX_VALUE)).isEqualTo("99+")
    }

    @Test
    fun folderBadgeSumsDistinctPackagesOfEntries() {
        val badges = mapOf(BadgeKey("mail", 0) to BadgeInfo(3), BadgeKey("chat", 10) to BadgeInfo(2))
        val mailA = AppKey("mail", "A", 0)
        val mailB = AppKey("mail", "B", 0) // second activity of the same package: counted once
        val chatWork = AppKey("chat", "Main", 10)
        val chatPersonal = AppKey("chat", "Main", 0)

        assertThat(folderBadge(badges, listOf(mailA, mailB, chatWork))?.count).isEqualTo(5)
        assertThat(folderBadge(badges, listOf(chatPersonal))).isNull()
        assertThat(folderBadge(badges, emptyList())).isNull()
    }

    @Test
    fun repositoryClearDropsEverything() {
        val repository = BadgeRepository()
        repository.publish(mapOf(BadgeKey("a", 0) to BadgeInfo(1)))
        assertThat(repository.badges.value).hasSize(1)
        repository.clear()
        assertThat(repository.badges.value).isEmpty()
    }
}
