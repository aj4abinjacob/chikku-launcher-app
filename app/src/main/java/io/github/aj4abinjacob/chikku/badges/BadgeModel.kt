package io.github.aj4abinjacob.chikku.badges

import android.graphics.Bitmap
import io.github.aj4abinjacob.chikku.core.model.AppKey

/**
 * Badges are per package and profile: every launcher activity of a package in one profile shows the
 * same badge, and the same package in the work profile has its own.
 */
data class BadgeKey(val packageName: String, val userSerial: Long) {
    companion object {
        fun of(key: AppKey) = BadgeKey(key.packageName, key.userSerial)
    }
}

/**
 * What one badge shows. [avatar] is the latest notification's sender/large icon, already scaled to
 * badge size, for the dynamic style only. It lives in memory only: never persisted, logged or
 * exported. No notification text is ever kept.
 */
data class BadgeInfo(val count: Int, val avatar: Bitmap? = null) {
    /**
     * Content equality: an avatar equals another with the same pixels, so a rebuild that produced an
     * identical badge does not redraw the views showing it.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BadgeInfo || count != other.count) return false
        val mine = avatar
        val theirs = other.avatar
        return mine === theirs || (mine != null && theirs != null && sameAvatar(mine, theirs))
    }

    override fun hashCode(): Int = 31 * count + (avatar?.let { 31 * it.width + it.height } ?: 0)

    private fun sameAvatar(a: Bitmap, b: Bitmap): Boolean = try {
        a.sameAs(b)
    } catch (_: RuntimeException) {
        false // recycled or unreadable: treat as changed
    }
}

/**
 * The few facts about one active notification that decide whether and how it badges. Built by the
 * listener from a StatusBarNotification; [ref] lets the caller find the notification that won
 * "latest" (to load its avatar) without the aggregator knowing about Android types.
 */
data class BadgeCandidate<T>(
    val key: BadgeKey,
    /** `Notification.number`; 0 or less counts as one. */
    val number: Int,
    val ongoing: Boolean,
    val groupSummary: Boolean,
    /** Channel / ranking `canShowBadge()`; true when the ranking is unknown. */
    val canShowBadge: Boolean,
    /** Has a title or text. Content-less notifications (e.g. foreground placeholders) do not badge. */
    val hasContent: Boolean,
    val postTime: Long,
    val ref: T,
)

/** Count and latest notification for one [BadgeKey]. */
data class BadgeAggregate<T>(val count: Int, val latest: T)

object BadgeAggregator {
    /** Same filtering idea as AOSP Launcher3: skip ongoing, group summaries, non-badgeable and empty ones. */
    fun isBadgeable(candidate: BadgeCandidate<*>): Boolean =
        candidate.canShowBadge && !candidate.ongoing && !candidate.groupSummary && candidate.hasContent

    /**
     * Sums counts per package+profile. [isProfileVisible] drops profiles that are locked, paused or
     * hidden (private space) so their notifications cannot leak onto the home screen.
     */
    fun <T> aggregate(
        candidates: Iterable<BadgeCandidate<T>>,
        isProfileVisible: (userSerial: Long) -> Boolean,
    ): Map<BadgeKey, BadgeAggregate<T>> {
        val result = LinkedHashMap<BadgeKey, BadgeAggregate<T>>()
        val latestTime = HashMap<BadgeKey, Long>()
        val visibility = HashMap<Long, Boolean>()
        for (c in candidates) {
            if (!isBadgeable(c)) continue
            if (!visibility.getOrPut(c.key.userSerial) { isProfileVisible(c.key.userSerial) }) continue
            val add = c.number.coerceAtLeast(1)
            val previous = result[c.key]
            if (previous == null) {
                result[c.key] = BadgeAggregate(add, c.ref)
                latestTime[c.key] = c.postTime
            } else {
                val newer = c.postTime >= (latestTime[c.key] ?: Long.MIN_VALUE)
                if (newer) latestTime[c.key] = c.postTime
                result[c.key] = BadgeAggregate(saturatingAdd(previous.count, add), if (newer) c.ref else previous.latest)
            }
        }
        return result
    }

    private fun saturatingAdd(a: Int, b: Int): Int = (a.toLong() + b).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}

/** Largest count shown as a number; anything above shows "99+". */
const val MAX_BADGE_COUNT = 99

/** Text for the COUNT style: "1".."99", then "99+". */
fun formatBadgeCount(count: Int): String = when {
    count <= 0 -> ""
    count > MAX_BADGE_COUNT -> "$MAX_BADGE_COUNT+"
    else -> count.toString()
}

/** Sum of the badges of a folder's entries, or null when none is badged (aggregate dot). */
fun folderBadge(badges: Map<BadgeKey, BadgeInfo>, keys: Iterable<AppKey>): BadgeInfo? {
    var total = 0L
    for (badgeKey in keys.mapTo(LinkedHashSet()) { BadgeKey.of(it) }) {
        total += badges[badgeKey]?.count ?: 0
    }
    return if (total > 0) BadgeInfo(total.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()) else null
}
