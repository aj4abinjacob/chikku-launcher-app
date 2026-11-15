package io.github.aj4abinjacob.chikku.platform.cards.media

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserManager
import io.github.aj4abinjacob.chikku.badges.BadgeAccess
import io.github.aj4abinjacob.chikku.badges.ChikkuNotificationListener
import io.github.aj4abinjacob.chikku.platform.personalUserSerial
import java.util.UUID

internal data class MediaDetails(val title: String?, val artist: String?, val playing: Boolean, val commands: Set<MediaCardCommand>)

internal interface MediaSessionHandle {
    val id: String
    val packageName: String
    fun details(): MediaDetails
    fun watch(changed: () -> Unit): AutoCloseable
    fun execute(command: MediaCardCommand): Boolean
}

internal interface MediaCardPlatform {
    fun personalSerial(): Long?
    fun hasAccess(): Boolean
    fun restoreListenerPolicy(enabled: Boolean)
    fun prepareAccess(): Boolean
    /** Public getActiveSessions defaults to the calling user's sessions, never an all-user API. */
    fun sessions(): List<MediaSessionHandle>
    fun watchSessions(changed: () -> Unit): AutoCloseable
}

/** Every method is called on IO. Callback payloads are ignored until current visibility is checked. */
internal class AndroidMediaCardPlatform(context: Context) : MediaCardPlatform {
    private val context = context.applicationContext
    private val manager = context.getSystemService(MediaSessionManager::class.java)
    private val listener = ComponentName(context, ChikkuNotificationListener::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val tokens = mutableMapOf<MediaSession.Token, String>()

    override fun personalSerial(): Long? =
        context.getSystemService(UserManager::class.java).personalUserSerial(requireUnlocked = true)

    override fun hasAccess(): Boolean = runCatching {
        context.getSystemService(NotificationManager::class.java)?.isNotificationListenerAccessGranted(listener) == true
    }.getOrDefault(false)

    override fun prepareAccess(): Boolean = runCatching {
        BadgeAccess(context).setListenerEnabled(true)
        true
    }.getOrDefault(false)

    override fun restoreListenerPolicy(enabled: Boolean) { BadgeAccess(context).setListenerEnabled(enabled) }

    override fun sessions(): List<MediaSessionHandle> {
        val controllers = manager?.getActiveSessions(listener).orEmpty().take(32)
        return synchronized(tokens) {
            tokens.keys.retainAll(controllers.map { it.sessionToken }.toSet())
            controllers.map { controller ->
                val id = tokens.getOrPut(controller.sessionToken) { UUID.randomUUID().toString() }
                AndroidSession(id, controller, handler)
            }
        }
    }

    override fun watchSessions(changed: () -> Unit): AutoCloseable {
        val callback = MediaSessionManager.OnActiveSessionsChangedListener { changed() }
        manager?.addOnActiveSessionsChangedListener(callback, listener, handler)
        return AutoCloseable { runCatching { manager?.removeOnActiveSessionsChangedListener(callback) } }
    }

    private class AndroidSession(override val id: String, private val controller: MediaController, private val handler: Handler) : MediaSessionHandle {
        override val packageName: String get() = controller.packageName

        override fun details(): MediaDetails {
            val metadata = controller.metadata
            val state = controller.playbackState
            fun text(key: String): String? = metadata?.getText(key)?.toString()
                ?.filterNot { it.isISOControl() }?.take(200)?.takeIf { it.isNotBlank() }
            return MediaDetails(
                text(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: text(MediaMetadata.METADATA_KEY_TITLE),
                text(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) ?: text(MediaMetadata.METADATA_KEY_ARTIST),
                state?.state in setOf(PlaybackState.STATE_PLAYING, PlaybackState.STATE_BUFFERING, PlaybackState.STATE_CONNECTING),
                supportedMediaCommands(state?.actions ?: 0L),
            )
        }

        override fun watch(changed: () -> Unit): AutoCloseable {
            val callback = object : MediaController.Callback() {
                override fun onMetadataChanged(metadata: MediaMetadata?) = changed()
                override fun onPlaybackStateChanged(state: PlaybackState?) = changed()
                override fun onSessionDestroyed() = changed()
            }
            controller.registerCallback(callback, handler)
            return AutoCloseable { runCatching { controller.unregisterCallback(callback) } }
        }

        override fun execute(command: MediaCardCommand): Boolean {
            val supported = supportedMediaCommands(controller.playbackState?.actions ?: 0L)
            if (command !in supported) return false
            when (command) {
                MediaCardCommand.PLAY -> controller.transportControls.play()
                MediaCardCommand.PAUSE -> controller.transportControls.pause()
                MediaCardCommand.PREVIOUS -> controller.transportControls.skipToPrevious()
                MediaCardCommand.NEXT -> controller.transportControls.skipToNext()
            }
            return true
        }
    }
}

internal fun supportedMediaCommands(actions: Long): Set<MediaCardCommand> = buildSet {
    if (actions and (PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PLAY_PAUSE) != 0L) add(MediaCardCommand.PLAY)
    if (actions and (PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE) != 0L) add(MediaCardCommand.PAUSE)
    if (actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L) add(MediaCardCommand.PREVIOUS)
    if (actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L) add(MediaCardCommand.NEXT)
}
