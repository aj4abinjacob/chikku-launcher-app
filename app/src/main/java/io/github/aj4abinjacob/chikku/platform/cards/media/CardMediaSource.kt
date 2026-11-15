package io.github.aj4abinjacob.chikku.platform.cards.media

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

enum class MediaCardCommand { PLAY, PAUSE, PREVIOUS, NEXT }

/** Ephemeral, filtered metadata; opaque session IDs are neither backed up nor logged. */
data class MediaCardItem(
    val sessionId: String,
    val appLabel: String,
    val title: String?,
    val artist: String?,
    val isPlaying: Boolean,
    val commands: Set<MediaCardCommand>,
)

sealed interface MediaCardState {
    data object NeedsAccess : MediaCardState
    data object Empty : MediaCardState
    data object Unavailable : MediaCardState
    data class Active(val item: MediaCardItem) : MediaCardState
}

interface CardMediaSource {
    /** Cold observation; native callbacks and ephemeral metadata are released on cancellation. */
    fun observe(): Flow<MediaCardState>
    /** Re-resolves the same session and visible personal owner before an explicit transport tap. */
    suspend fun command(sessionId: String, command: MediaCardCommand): Boolean
    suspend fun hasAccess(): Boolean
    /** Called only by an access button; requires an enabled, saved media card. */
    suspend fun prepareAccess(): Boolean
}

object NoopCardMediaSource : CardMediaSource {
    override fun observe(): Flow<MediaCardState> = flowOf(MediaCardState.Unavailable)
    override suspend fun command(sessionId: String, command: MediaCardCommand) = false
    override suspend fun hasAccess() = false
    override suspend fun prepareAccess() = false
}
