package io.github.aj4abinjacob.chikku.badges

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Current notification badges, in memory only. Written by [ChikkuNotificationListener] while it is
 * bound, read by HOME. Nothing here is persisted, backed up or logged, and it is emptied whenever
 * badges are turned off or the listener disconnects.
 */
class BadgeRepository {
    private val state = MutableStateFlow<Map<BadgeKey, BadgeInfo>>(emptyMap())
    private val connection = MutableStateFlow(false)
    private var generation = 0L

    val badges: StateFlow<Map<BadgeKey, BadgeInfo>> = state.asStateFlow()
    val connected: StateFlow<Boolean> = connection.asStateFlow()

    @Synchronized fun publish(badges: Map<BadgeKey, BadgeInfo>) {
        state.value = badges.toMap()
    }

    @Synchronized fun clear() {
        generation++
        state.value = emptyMap()
    }

    @Synchronized internal fun revision(): Long = generation

    /** A slow Binder/icon read started before revoke/profile change must not republish old data. */
    @Synchronized internal fun publishIfCurrent(revision: Long, badges: Map<BadgeKey, BadgeInfo>) {
        if (revision == generation && connection.value) state.value = badges.toMap()
    }

    @Synchronized internal fun connect() {
        clear()
        connection.value = true
    }

    @Synchronized fun disconnect() {
        connection.value = false
        clear()
    }
}
