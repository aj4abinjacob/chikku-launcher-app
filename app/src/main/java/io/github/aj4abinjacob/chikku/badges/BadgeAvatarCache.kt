package io.github.aj4abinjacob.chikku.badges

import android.graphics.Bitmap

/**
 * Scaled badge avatars kept across badge rebuilds, so a rebuild for an unrelated notification event
 * reuses the same bitmap instead of decoding and scaling every avatar again. Keyed by notification
 * key plus a revision (post time and icon signature) that changes whenever the notification is
 * posted again. Entries for notifications that no longer produce a badge are dropped at the end of
 * each rebuild ([retainOnly]), and the cache never holds more than [maxEntries]. Memory only; never
 * persisted or logged. Thread-safe.
 */
internal class BadgeAvatarCache(private val maxEntries: Int = DEFAULT_MAX_ENTRIES) {
    data class Key(val notificationKey: String, val revision: Long)

    /** A cached result, including "no avatar", so failing icons are not loaded again either. */
    private class Entry(val avatar: Bitmap?)

    private val entries = object : LinkedHashMap<Key, Entry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, Entry>?): Boolean = size > maxEntries
    }

    @Synchronized
    fun getOrLoad(key: Key, load: () -> Bitmap?): Bitmap? {
        entries[key]?.let { return it.avatar }
        // A repost replaces the notification's previous avatar.
        entries.keys.removeAll { it.notificationKey == key.notificationKey }
        val loaded = load()
        entries[key] = Entry(loaded)
        return loaded
    }

    /** Drops every entry whose notification key is not in [notificationKeys]. */
    @Synchronized
    fun retainOnly(notificationKeys: Set<String>) {
        entries.keys.retainAll { it.notificationKey in notificationKeys }
    }

    @Synchronized
    fun clear() = entries.clear()

    @Synchronized
    fun size(): Int = entries.size

    companion object {
        const val DEFAULT_MAX_ENTRIES = 64
    }
}

/**
 * Whether a ranking update changes which notifications may badge: true when any notification seen
 * by the last rebuild now has a different `canShowBadge()` ([current] returns null when the
 * ranking no longer lists it, which onNotificationRemoved handles).
 */
internal fun rankingAffectsBadges(previous: Map<String, Boolean>, current: (String) -> Boolean?): Boolean =
    previous.any { (key, couldBadge) -> current(key)?.let { it != couldBadge } ?: false }
