package io.github.aj4abinjacob.chikku.home.cards.media

import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.message.ChikkuMessages
import io.github.aj4abinjacob.chikku.badges.BadgeAccess
import io.github.aj4abinjacob.chikku.platform.cards.media.CardMediaSource
import io.github.aj4abinjacob.chikku.platform.cards.media.MediaCardCommand
import io.github.aj4abinjacob.chikku.platform.cards.media.MediaCardState
import io.github.aj4abinjacob.chikku.theme.SurfaceColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Each visible card has its own lifecycle collection; no session metadata survives detachment. */
class MediaCardView(
    private val activity: ComponentActivity,
    private val source: CardMediaSource,
    private val colors: SurfaceColors,
    /** Chikku's message bar for short notices; never a platform toast. */
    private val messages: ChikkuMessages = ChikkuMessages.of(activity),
) : LinearLayout(activity) {
    private var observing: Job? = null
    private var actions: CoroutineScope? = null
    init { orientation = VERTICAL; render(MediaCardState.Unavailable) }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        observing?.cancel()
        observing = activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                coroutineScope {
                    actions = this
                    try { source.observe().collect { render(it) } }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { render(MediaCardState.Unavailable) }
                    finally { actions = null; render(MediaCardState.Unavailable) }
                }
            }
        }
    }

    override fun onDetachedFromWindow() {
        actions = null
        observing?.cancel(); observing = null
        removeAllViews()
        super.onDetachedFromWindow()
    }

    private fun label(value: String, secondary: Boolean = false) = TextView(context).apply {
        text = value; textSize = if (secondary) 14f else 16f
        setTextColor(if (secondary) colors.onSurfaceVariant else colors.onSurface)
        setPadding(0, dp(4), 0, dp(4))
    }

    private fun button(resource: Int, action: () -> Unit) = Button(context, null, android.R.attr.borderlessButtonStyle).apply {
        text = context.getString(resource); contentDescription = text; isAllCaps = false
        setTextColor(colors.onSurface); minimumHeight = dp(48); minHeight = dp(48)
        setOnClickListener { action() }
    }

    private fun render(state: MediaCardState) {
        removeAllViews()
        when (state) {
            MediaCardState.Empty -> addView(label(context.getString(R.string.card_media_empty), true))
            MediaCardState.Unavailable -> addView(label(context.getString(R.string.card_media_unavailable), true))
            MediaCardState.NeedsAccess -> {
                addView(label(context.getString(R.string.card_media_summary), true))
                addView(button(R.string.card_media_access) {
                    actions?.launch {
                        if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) || !isAttachedToWindow || !safely { source.prepareAccess() } || !isAttachedToWindow) return@launch
                        val opened = BadgeAccess(context).settingsIntents().any { intent ->
                            runCatching { activity.startActivity(intent); true }.getOrDefault(false)
                        }
                        if (!opened) messages.show(R.string.card_media_access_failed)
                    }
                })
            }
            is MediaCardState.Active -> {
                val item = state.item
                addView(label(item.appLabel, true))
                item.title?.let { addView(label(it)) }
                item.artist?.let { addView(label(it, true)) }
                // Vertical controls retain full 48dp targets under large fonts and narrow cards.
                val playback = if (item.isPlaying) MediaCardCommand.PAUSE else MediaCardCommand.PLAY
                listOf(MediaCardCommand.PREVIOUS, playback, MediaCardCommand.NEXT).filter { it in item.commands }.forEach { command ->
                    val resource = when (command) {
                        MediaCardCommand.PLAY -> R.string.card_media_play
                        MediaCardCommand.PAUSE -> R.string.card_media_pause
                        MediaCardCommand.PREVIOUS -> R.string.card_media_previous
                        MediaCardCommand.NEXT -> R.string.card_media_next
                    }
                    addView(button(resource) {
                        actions?.launch {
                            if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) || !isAttachedToWindow) return@launch
                            if (!safely { source.command(item.sessionId, command) })
                                messages.show(R.string.card_media_command_failed)
                        }
                    }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
                }
            }
        }
    }
    private suspend fun safely(action: suspend () -> Boolean): Boolean = try { action() }
    catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { false }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
}
