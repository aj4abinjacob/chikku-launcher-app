package io.github.aj4abinjacob.chikku.platform

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.os.Process
import android.os.UserManager
import android.view.WindowManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class DiscoverOpenResult {
    OPENED,
    OPENED_GOOGLE_APP,
    MISSING_GOOGLE,
    CONNECTING,
    UNAVAILABLE,

    /** The bridge app failed (blocked, killed or not accepted); the caller explains why. */
    BRIDGE_PROBLEM,
}

/** Activity-scoped, opt-in connection. Google owns and renders the cards in its overlay window. */
class GoogleDiscoverClient(
    private val activity: Activity,
    private val resolver: GoogleDiscoverResolver?,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val externalIntents: ExternalIntentStarter = ExternalIntentStarter.DENY,
    private val metadata: ShortcutMetadataPolicy = ShortcutMetadataPolicy.DENY,
    metadataChanges: Flow<*> = emptyFlow<Unit>(),
    private val onProgress: (Float) -> Unit,
) : DefaultLifecycleObserver {
    private var userSerial: Long? = null
    private fun mayEmbed(): Boolean = userSerial?.let { metadata.mayRead(GOOGLE_PACKAGE, it) } == true

    private var enabled = false
    private var started = false
    private var resumed = false
    private var generation = 0
    private var attachmentGeneration = 0
    private var resolution: Job? = null
    private var connectionTimeout: Job? = null
    private var connection: ServiceConnection? = null
    private var overlay: IBinder? = null
    private var provider: DiscoverProvider = DiscoverProvider.Unavailable
    private var appIntent: Intent? = null
    private var attached = false
    private var contentAvailable = false
    private var progress = 0f

    // Bridge failures survive disconnect() so the next swipe explains them instead of silently
    // opening the Google app; they clear once cards are available again.
    private var bridgeTrouble = false
    private var reregistered = false
    private var reconnects = 0

    init {
        scope.launch {
            metadataChanges.collect {
                // Embedded private content never uses/consumes a one-time app-launch grant.
                if (userSerial != null && !mayEmbed()) disconnect()
                else if (enabled && started) connect()
            }
        }
    }

    val isOpen: Boolean get() = progress > 0f

    fun setEnabled(value: Boolean) {
        if (enabled == value) return
        enabled = value
        if (value && started) connect() else {
            disconnect()
            appIntent = null
            bridgeTrouble = false
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        started = true
        reconnects = 0
        if (enabled) connect()
    }

    override fun onResume(owner: LifecycleOwner) {
        resumed = true
        updateActivityState()
    }

    override fun onPause(owner: LifecycleOwner) {
        resumed = false
        close()
        updateActivityState()
    }

    override fun onStop(owner: LifecycleOwner) {
        started = false
        disconnect()
    }

    override fun onDestroy(owner: LifecycleOwner) = disconnect()

    fun open(): DiscoverOpenResult {
        if (!enabled || !started) return DiscoverOpenResult.UNAVAILABLE
        if (!mayEmbed() && attached) disconnect()
        if (mayEmbed() && attached && contentAvailable && call(9) { it.writeInt(1) }) {
            // Back/Home must work even before the asynchronous first progress callback.
            progress = 1f
            return DiscoverOpenResult.OPENED
        }
        if (bridgeTrouble) {
            if (connection == null && resolution?.isActive != true) connect()
            return DiscoverOpenResult.BRIDGE_PROBLEM
        }
        // The bridge is still connecting; jumping into the Google app now would hide that.
        if (mayEmbed() && (provider as? DiscoverProvider.Available)?.viaBridge == true) return DiscoverOpenResult.CONNECTING
        // The user explicitly allows opening the installed Google app when embedding is unavailable.
        if (openGoogleApp()) return DiscoverOpenResult.OPENED_GOOGLE_APP
        val result = when (provider) {
            DiscoverProvider.MissingGoogle -> DiscoverOpenResult.MISSING_GOOGLE
            DiscoverProvider.Unavailable -> if (resolution?.isActive == true) DiscoverOpenResult.CONNECTING
                else DiscoverOpenResult.UNAVAILABLE
            is DiscoverProvider.Available -> DiscoverOpenResult.CONNECTING
            is DiscoverProvider.AppOnly -> DiscoverOpenResult.UNAVAILABLE
        }
        // Retry a missing/failed provider on the next user gesture, without a polling loop.
        if (connection == null && resolution?.isActive != true) connect()
        return result
    }

    /** Opens the installed Google app; also offered when a bridge problem is explained. */
    fun openGoogleApp(): Boolean {
        val intent = appIntent ?: return false
        return externalIntents.start(listOf(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)))
    }

    /** User-requested retry after a bridge problem, for example after changing battery settings. */
    fun retry() {
        if (!enabled || !started) return
        bridgeTrouble = false
        reconnects = 0
        disconnect()
        connect()
    }

    fun close() {
        if (attached) call(6) { it.writeInt(1) }
        progress = 0f
        onProgress(0f)
    }

    fun attachWindow() {
        if (!mayEmbed() || attached || overlay == null || !activity.window.decorView.isAttachedToWindow) return
        val currentGeneration = generation
        val currentAttachment = ++attachmentGeneration
        val callback = DiscoverOverlayProtocol.overlayCallback(
            scrolled = { value -> activity.runOnUiThread {
                if (currentGeneration == generation && currentAttachment == attachmentGeneration &&
                    overlay != null && value.isFinite()) {
                    progress = value.coerceIn(0f, 1f)
                    onProgress(progress)
                }
            } },
            status = { value -> activity.runOnUiThread {
                if (currentGeneration == generation && currentAttachment == attachmentGeneration && overlay != null) {
                    contentAvailable = value and 1 != 0
                    if (contentAvailable) {
                        connectionTimeout?.cancel()
                        bridgeTrouble = false
                        reconnects = 0
                    }
                    if (!contentAvailable) close()
                }
            } },
        )
        // Google adds an application window, so it needs the activity token from the window
        // attributes; decorView.windowToken is the view-root token and is rejected as unknown.
        val params = WindowManager.LayoutParams().apply {
            copyFrom(activity.window.attributes)
            token = activity.window.attributes.token
        }
        val version = (provider as? DiscoverProvider.Available)?.apiVersion ?: 1
        attached = if (version >= 3) {
            call(14) { parcel ->
                parcel.writeInt(1)
                Bundle().apply {
                    putParcelable("layout_params", params)
                    putParcelable("configuration", activity.resources.configuration)
                    putInt("client_options", 1)
                }.writeToParcel(parcel, 0)
                parcel.writeStrongBinder(callback)
            }
        } else {
            call(4) { parcel ->
                parcel.writeInt(1)
                params.writeToParcel(parcel, 0)
                parcel.writeStrongBinder(callback)
                parcel.writeInt(1)
            }
        }
        updateActivityState()
    }

    fun detachWindow() {
        attachmentGeneration++
        if (attached) call(5) { it.writeInt(if (activity.isChangingConfigurations) 1 else 0) }
        attached = false
        contentAvailable = false
        progress = 0f
        onProgress(0f)
    }

    private fun updateActivityState() {
        if (!attached) return
        val version = (provider as? DiscoverProvider.Available)?.apiVersion ?: 1
        if (version >= 4) call(16) { it.writeInt((if (started) 1 else 0) or (if (resumed) 2 else 0)) }
        else call(if (resumed) 8 else 7)
    }

    private fun connect() {
        if (connection != null || resolution?.isActive == true || resolver == null) return
        val currentGeneration = generation
        resolution = scope.launch {
            val found = try {
                userSerial = withContext(io) {
                    runCatching { activity.getSystemService(UserManager::class.java)
                        ?.getSerialNumberForUser(Process.myUserHandle())?.takeIf { it >= 0 } }.getOrNull()
                }
                resolver.resolve()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                DiscoverProvider.Unavailable
            }
            if (currentGeneration != generation || !enabled || !started) return@launch
            provider = found
            appIntent = when (found) {
                is DiscoverProvider.Available -> found.appIntent
                is DiscoverProvider.AppOnly -> found.appIntent
                else -> null
            }
            if (found !is DiscoverProvider.Available || !mayEmbed()) return@launch
            val service = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                    if (currentGeneration != generation) return
                    detachWindow()
                    overlay = binder
                    attachWindow()
                }

                // A killed or frozen-then-killed bridge lands here; reconnect without waiting for a swipe.
                override fun onServiceDisconnected(name: ComponentName) {
                    if (currentGeneration == generation) {
                        clearOverlay()
                        fail(found)
                    }
                }
                override fun onBindingDied(name: ComponentName) {
                    if (currentGeneration == generation) fail(found)
                }
                override fun onNullBinding(name: ComponentName) {
                    if (currentGeneration == generation) fail(found)
                }
            }
            connection = service
            reregistered = false
            val bound = try {
                val intent = if (found.viaBridge) Intent(AndroidGoogleDiscoverResolver.BRIDGE_ACTION)
                    else AndroidGoogleDiscoverResolver.overlayIntent(activity, found.component.packageName)
                // BIND_IMPORTANT keeps the bridge at HOME's priority so battery managers leave it running.
                val flags = if (found.viaBridge) Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT
                    else Context.BIND_AUTO_CREATE
                activity.bindService(intent.setComponent(found.component), service, flags)
            } catch (_: Exception) {
                false
            }
            if (!bound) fail(found) else awaitContent(currentGeneration, found)
        }
    }

    /** A provider that binds but never yields usable content must not stay attached forever. */
    private fun awaitContent(currentGeneration: Int, found: DiscoverProvider.Available) {
        connectionTimeout?.cancel()
        connectionTimeout = scope.launch {
            delay(CONTENT_TIMEOUT_MS)
            if (currentGeneration != generation || contentAvailable) return@launch
            val bridge = overlay
            if (!found.viaBridge || reregistered || bridge == null) return@launch fail(found)
            // Google may still be serving another client (such as another launcher's companion);
            // ask the bridge to register with Google again, then attach once more.
            reregistered = true
            val link = try {
                withContext(io) { DiscoverOverlayProtocol.bridgeReregister(bridge) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            if (currentGeneration != generation) return@launch
            if (link != BridgeGoogleLink.CONNECTED) return@launch fail(found)
            detachWindow()
            attachWindow()
            awaitContent(currentGeneration, found)
        }
    }

    private fun fail(found: DiscoverProvider.Available) {
        disconnect()
        if (!found.viaBridge) return
        bridgeTrouble = true
        if (!enabled || !started || reconnects >= MAX_RECONNECTS) return
        reconnects++
        val currentGeneration = generation
        scope.launch {
            delay(RECONNECT_DELAY_MS * reconnects)
            if (currentGeneration == generation && enabled && started) connect()
        }
    }

    private fun call(transaction: Int, write: (android.os.Parcel) -> Unit = {}): Boolean {
        val remote = overlay ?: return false
        return try {
            DiscoverOverlayProtocol.send(remote, DiscoverOverlayProtocol.OVERLAY, transaction, write)
            true
        } catch (_: Exception) {
            clearOverlay()
            disconnect()
            false
        }
    }

    private fun clearOverlay() {
        attachmentGeneration++
        overlay = null
        attached = false
        contentAvailable = false
        progress = 0f
        onProgress(0f)
    }

    private fun disconnect() {
        generation++
        resolution?.cancel()
        resolution = null
        connectionTimeout?.cancel()
        connectionTimeout = null
        detachWindow()
        connection?.let { service ->
            try { activity.unbindService(service) } catch (_: Exception) { /* Already disconnected. */ }
        }
        connection = null
        clearOverlay()
        provider = DiscoverProvider.Unavailable
    }

    private companion object {
        const val GOOGLE_PACKAGE = "com.google.android.googlequicksearchbox"
        const val CONTENT_TIMEOUT_MS = 5_000L
        const val RECONNECT_DELAY_MS = 1_000L
        const val MAX_RECONNECTS = 3
    }
}
