package io.github.aj4abinjacob.chikku.platform

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import io.github.aj4abinjacob.chikku.platform.AndroidGoogleDiscoverResolver.Companion.BRIDGE_ACTION
import io.github.aj4abinjacob.chikku.platform.AndroidGoogleDiscoverResolver.Companion.GOOGLE_PACKAGE
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Why Google Discover cards can or cannot appear on the home screen, so the user is told the
 * actual cause (for example a battery-restricted bridge app) instead of having to search forums.
 */
sealed interface DiscoverHealth {
    data object MissingGoogle : DiscoverHealth

    /** This distribution deliberately opens Google; the external bridge is not a dependency. */
    data object AppOnly : DiscoverHealth

    /** This build is debuggable and connects to Google directly; no bridge is needed. */
    data object Direct : DiscoverHealth

    /** A normal build without the bridge app: the swipe opens the Google app. */
    data object BridgeMissing : DiscoverHealth

    /** A bridge is installed but signed with another key, so Chikku will not use it. */
    data class BridgeWrongKey(val packageName: String) : DiscoverHealth

    data class BridgeDisabled(val packageName: String) : DiscoverHealth

    /** Trusted and enabled, but it did not start or answer; OEM battery management is the usual cause. */
    data class BridgeUnresponsive(val packageName: String) : DiscoverHealth

    data class Bridge(val packageName: String, val status: BridgeStatus) : DiscoverHealth
}

fun interface DiscoverHealthChecker {
    suspend fun check(): DiscoverHealth
}

class AndroidDiscoverHealthChecker(
    context: Context,
    private val io: CoroutineDispatcher,
    private val experimentalOverlay: Boolean = true,
) : DiscoverHealthChecker {
    private val context = context.applicationContext

    override suspend fun check(): DiscoverHealth = withContext(io) {
        val pm = context.packageManager
        val googleEnabled = try {
            pm.getApplicationInfo(GOOGLE_PACKAGE, 0).enabled
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
        if (!googleEnabled) return@withContext DiscoverHealth.MissingGoogle
        if (!experimentalOverlay) return@withContext DiscoverHealth.AppOnly
        val candidates = pm.queryIntentServices(Intent(BRIDGE_ACTION), PackageManager.MATCH_DISABLED_COMPONENTS)
            .mapNotNull { it.serviceInfo }
        val trusted = candidates.firstOrNull { isSameSigner(pm, context, it.packageName) }
        when {
            trusted == null && candidates.isNotEmpty() -> DiscoverHealth.BridgeWrongKey(candidates.first().packageName)
            trusted == null -> if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                DiscoverHealth.Direct
            } else {
                DiscoverHealth.BridgeMissing
            }
            !trusted.enabled || trusted.applicationInfo?.enabled == false ->
                DiscoverHealth.BridgeDisabled(trusted.packageName)
            else -> withTimeoutOrNull(STATUS_TIMEOUT_MS) { queryStatus(trusted) }
                ?.let { DiscoverHealth.Bridge(trusted.packageName, it) }
                ?: DiscoverHealth.BridgeUnresponsive(trusted.packageName)
        }
    }

    /** Binds the bridge just long enough to ask for its status; null if it cannot be reached. */
    private suspend fun queryStatus(info: ServiceInfo): BridgeStatus? {
        var connection: ServiceConnection? = null
        try {
            val binder = suspendCancellableCoroutine<IBinder?> { continuation ->
                val service = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                        if (continuation.isActive) continuation.resume(binder)
                    }
                    override fun onServiceDisconnected(name: ComponentName?) = Unit
                    override fun onBindingDied(name: ComponentName) {
                        if (continuation.isActive) continuation.resume(null)
                    }
                    override fun onNullBinding(name: ComponentName) {
                        if (continuation.isActive) continuation.resume(null)
                    }
                }
                connection = service
                val bound = try {
                    context.bindService(
                        Intent(BRIDGE_ACTION).setComponent(ComponentName(info.packageName, info.name)),
                        service,
                        Context.BIND_AUTO_CREATE,
                    )
                } catch (_: Exception) {
                    false
                }
                if (!bound && continuation.isActive) continuation.resume(null)
            } ?: return null
            return withContext(io) { DiscoverOverlayProtocol.bridgeStatus(binder) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            return null
        } finally {
            connection?.let { try { context.unbindService(it) } catch (_: Exception) { /* Never bound. */ } }
        }
    }

    private companion object {
        const val STATUS_TIMEOUT_MS = 6_000L
    }
}

/** The bridge receives window tokens, so only an app signed with Chikku's own key is trusted. */
internal fun isSameSigner(pm: PackageManager, context: Context, packageName: String): Boolean =
    pm.checkSignatures(context.packageName, packageName) == PackageManager.SIGNATURE_MATCH
