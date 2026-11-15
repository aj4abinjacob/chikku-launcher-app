package io.github.aj4abinjacob.chikku.platform.cards.media

import android.content.Context
import io.github.aj4abinjacob.chikku.core.model.AppCustomization
import io.github.aj4abinjacob.chikku.core.model.AppCustomizationRepository
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.AppsRepository
import io.github.aj4abinjacob.chikku.core.model.AppsState
import io.github.aj4abinjacob.chikku.core.model.LauncherApp
import io.github.aj4abinjacob.chikku.core.model.ProfileType
import io.github.aj4abinjacob.chikku.core.model.cards.CardContent
import io.github.aj4abinjacob.chikku.core.model.settings.BadgeStyle
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.core.model.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicReference

/** Reads only the calling user's public sessions; filters owner identity before every metadata read. */
class AndroidCardMediaSource internal constructor(
    private val platform: MediaCardPlatform,
    private val apps: AppsRepository,
    private val customizations: AppCustomizationRepository,
    private val settings: SettingsRepository,
    private val io: CoroutineDispatcher,
) : CardMediaSource {
    constructor(context: Context, apps: AppsRepository, customizations: AppCustomizationRepository,
        settings: SettingsRepository, io: CoroutineDispatcher = Dispatchers.IO) :
        this(AndroidMediaCardPlatform(context), apps, customizations, settings, io)

    private data class Inputs(val apps: AppsState, val customizations: Map<AppKey, AppCustomization>, val settings: LauncherSettings) {
        val mediaEnabled: Boolean get() = settings.cards.cards.any { it.enabled && it.content == CardContent.Media }
        val loadedApps: List<LauncherApp> get() = (apps as? AppsState.Loaded)?.apps.orEmpty()
        fun owner(packageName: String, serial: Long): LauncherApp? {
            if (customizations.keys.any { it.packageName == packageName && it.userSerial == serial && customizations[it]?.hidden == true }) return null
            return loadedApps.firstOrNull { it.key.packageName == packageName && it.key.userSerial == serial && it.profileType == ProfileType.PERSONAL }
        }
    }

    private suspend fun readInputs(): Inputs? = withTimeoutOrNull(5_000) {
        val appState = apps.apps.first { it !is AppsState.Loading }
        if (appState !is AppsState.Loaded) null
        else Inputs(appState, customizations.customizations.first(), settings.settings.first())
    }

    override suspend fun hasAccess(): Boolean = withContext(io) { runCatching { platform.hasAccess() }.getOrDefault(false) }

    override suspend fun prepareAccess(): Boolean = withContext(io) {
        try {
            if (settings.settings.first().cards.cards.none { it.enabled && it.content == CardContent.Media }) return@withContext false
            // Only an explicit access tap after saving a media card can enable the component.
            currentCoroutineContext().ensureActive()
            if (!platform.prepareAccess()) return@withContext false
            val latest = settings.settings.first()
            val stillEnabled = latest.cards.cards.any { it.enabled && it.content == CardContent.Media }
            if (!stillEnabled) platform.restoreListenerPolicy(latest.badges.style != BadgeStyle.OFF)
            stillEnabled
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: RuntimeException) { false }
    }

    private fun signals(): Flow<Unit> = callbackFlow {
        var callback: AutoCloseable? = null
        if (settings.settings.first().cards.cards.any { it.enabled && it.content == CardContent.Media } && platform.hasAccess()) {
            callback = runCatching { platform.watchSessions { trySend(Unit) } }.getOrNull()
        }
        trySend(Unit)
        val poll = launch { while (true) { delay(2_000); trySend(Unit) } }
        awaitClose { poll.cancel(); runCatching { callback?.close() } }
    }

    override fun observe(): Flow<MediaCardState> = channelFlow {
        var selectedWatch: AutoCloseable? = null
        val refreshSink = AtomicReference<(() -> Unit)?>(null)
        // Session callbacks only signal a refresh. Their metadata payloads are never retained.
        val refreshes = callbackFlow<Unit> {
            val collector = launch { signals().collect { trySend(Unit) } }
            // Per-session changes feed the same conflated refresh stream.
            refreshSink.set { trySend(Unit) }
            awaitClose { collector.cancel(); refreshSink.set(null) }
        }
        try {
            combine(apps.apps, customizations.customizations, settings.settings, refreshes) { listed, custom, prefs, _ ->
                Inputs(listed, custom, prefs)
            }.collectLatest { input ->
                runCatching { selectedWatch?.close() }; selectedWatch = null
                if (!input.mediaEnabled) { send(MediaCardState.Empty); return@collectLatest }
                if (!platform.hasAccess()) { send(MediaCardState.NeedsAccess); return@collectLatest }
                if (input.apps !is AppsState.Loaded) { send(MediaCardState.Unavailable); return@collectLatest }
                val serial = platform.personalSerial() ?: return@collectLatest send(MediaCardState.Unavailable)
                val session = platform.sessions().firstOrNull { input.owner(it.packageName, serial) != null }
                    ?: return@collectLatest send(MediaCardState.Empty)
                val before = readInputs() ?: return@collectLatest send(MediaCardState.Empty)
                if (before != input || !platform.hasAccess()) { send(MediaCardState.Empty); return@collectLatest }
                val owner = before.owner(session.packageName, serial) ?: return@collectLatest send(MediaCardState.Empty)
                val details = session.details()
                if (readInputs() != before || !platform.hasAccess()) { send(MediaCardState.Empty); return@collectLatest }
                selectedWatch = session.watch { refreshSink.get()?.invoke() }
                val label = before.customizations[owner.key]?.customLabel?.takeIf { it.isNotBlank() } ?: owner.label
                send(MediaCardState.Active(MediaCardItem(session.id, label, details.title, details.artist, details.playing, details.commands)))
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { send(if (runCatching { platform.hasAccess() }.getOrDefault(false)) MediaCardState.Unavailable else MediaCardState.NeedsAccess) }
        finally { runCatching { selectedWatch?.close() } }
    }.buffer(Channel.RENDEZVOUS).flowOn(io)

    override suspend fun command(sessionId: String, command: MediaCardCommand): Boolean = withContext(io) {
        try {
            val before = readInputs() ?: return@withContext false
            if (!before.mediaEnabled || !platform.hasAccess()) return@withContext false
            val serial = platform.personalSerial() ?: return@withContext false
            val session = platform.sessions().firstOrNull { it.id == sessionId && before.owner(it.packageName, serial) != null }
                ?: return@withContext false
            if (readInputs() != before || !platform.hasAccess()) return@withContext false
            currentCoroutineContext().ensureActive()
            session.execute(command)
        } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { false }
    }
}
