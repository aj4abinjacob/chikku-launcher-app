package io.github.aj4abinjacob.chikku.discoverbridge

import android.app.ActivityManager
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.Process
import android.os.SystemClock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Relays Chikku's launcher-overlay Binder calls to the Google app under this app's identity.
 *
 * Google only accepts overlay clients that are system or debuggable apps, and checks that every
 * call comes from the bound client. This debuggable helper binds and relays, so a normal Chikku
 * release build need not be debuggable. Google renders the cards in its own window attached to
 * Chikku's activity token and calls Chikku's callback Binder directly. Nothing is read, stored or
 * logged here, and only callers signed with this app's key are served.
 *
 * Two bridge-only calls let Chikku explain failures instead of silently opening the Google app:
 * [STATUS] reports the Google link and this app's own battery restrictions, and [REREGISTER]
 * binds Google again with a fresh intent so Google registers this client anew.
 */
class DiscoverBridgeService : Service() {
    private val lock = Any()
    @Volatile private var google: IBinder? = null
    @Volatile private var googleState = GOOGLE_PENDING
    @Volatile private var settled = CountDownLatch(1)
    private var connection: ServiceConnection? = null // guarded by lock
    private val trustedUids = ConcurrentHashMap<Int, Boolean>()

    private inner class GoogleConnection : ServiceConnection {
        private fun current() = synchronized(lock) { connection === this }

        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            if (!current()) return
            google = binder
            googleState = GOOGLE_CONNECTED
            settled.countDown()
        }

        // Google's window state is gone with its connection. Ending this stateless process makes
        // Chikku see the bridge die, tear down and reconnect.
        override fun onServiceDisconnected(name: ComponentName) {
            if (current()) Process.killProcess(Process.myPid())
        }

        override fun onBindingDied(name: ComponentName) {
            if (current()) Process.killProcess(Process.myPid())
        }

        // Google rejected this client; Chikku asks for STATUS and explains it to the user.
        override fun onNullBinding(name: ComponentName) {
            if (!current()) return
            googleState = GOOGLE_REJECTED
            settled.countDown()
        }
    }

    private val relay = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean = when (code) {
            INTERFACE_TRANSACTION -> super.onTransact(code, data, reply, flags)
            STATUS -> {
                data.enforceInterface(BRIDGE_DESCRIPTOR)
                requireTrusted()
                settled.await(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                reply?.writeNoException()
                reply?.writeInt(googleState)
                reply?.writeInt(if (backgroundRestricted()) 1 else 0)
                reply?.writeInt(standbyBucket())
                true
            }
            REREGISTER -> {
                data.enforceInterface(BRIDGE_DESCRIPTOR)
                requireTrusted()
                bindGoogle(fresh = true)
                settled.await(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                reply?.writeNoException()
                reply?.writeInt(googleState)
                true
            }
            else -> relayToGoogle(code, data, reply, flags)
        }
    }

    override fun onCreate() {
        super.onCreate()
        bindGoogle(fresh = false)
    }

    override fun onBind(intent: Intent): IBinder = relay

    override fun onDestroy() {
        synchronized(lock) {
            connection?.let { unbindService(it) }
            connection = null
        }
        super.onDestroy()
    }

    private fun relayToGoogle(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        if (!isTrusted(Binder.getCallingUid())) return false
        settled.await(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        val target = google ?: return false
        val forwarded = Parcel.obtain()
        return try {
            // Rewrite the interface header so it carries this app's identity, not Chikku's.
            data.enforceInterface(OVERLAY_DESCRIPTOR)
            forwarded.writeInterfaceToken(OVERLAY_DESCRIPTOR)
            forwarded.appendFrom(data, data.dataPosition(), data.dataAvail())
            target.transact(code, forwarded, reply, flags)
        } catch (_: Exception) {
            false
        } finally {
            forwarded.recycle()
        }
    }

    /**
     * Android reuses a cached Binder for an identical bind intent without asking Google again, so a
     * fresh query parameter is the only way to make Google register this client anew. Each fresh
     * intent leaves a small record in the system until Google's service restarts, so it is only
     * used when Chikku asks for it after a failure.
     */
    private fun bindGoogle(fresh: Boolean) = synchronized(lock) {
        connection?.let { unbindService(it) }
        google = null
        googleState = GOOGLE_PENDING
        settled = CountDownLatch(1)
        val query = if (fresh) "$QUERY&n=${SystemClock.elapsedRealtimeNanos()}" else QUERY
        val overlay = Intent(OVERLAY_ACTION)
            .setPackage(GOOGLE_PACKAGE)
            .setData(Uri.parse("app://$packageName:${Process.myUid()}?$query"))
        val next = GoogleConnection()
        connection = next
        val bound = try {
            bindService(overlay, next, BIND_AUTO_CREATE)
        } catch (_: SecurityException) {
            false
        }
        if (!bound) {
            connection = null
            googleState = GOOGLE_BIND_FAILED
            settled.countDown()
        }
    }

    private fun backgroundRestricted(): Boolean =
        getSystemService(ActivityManager::class.java)?.isBackgroundRestricted == true

    private fun standbyBucket(): Int =
        getSystemService(UsageStatsManager::class.java)?.appStandbyBucket ?: 0

    private fun requireTrusted() {
        if (!isTrusted(Binder.getCallingUid())) throw SecurityException("Caller is not signed with this app's key")
    }

    /** Window tokens and callbacks pass through here, so only same-signer callers are served. */
    private fun isTrusted(uid: Int): Boolean = trustedUids.getOrPut(uid) {
        packageManager.checkSignatures(uid, Process.myUid()) == PackageManager.SIGNATURE_MATCH
    }

    private companion object {
        const val GOOGLE_PACKAGE = "com.google.android.googlequicksearchbox"
        const val OVERLAY_ACTION = "com.android.launcher3.WINDOW_OVERLAY"
        const val OVERLAY_DESCRIPTOR = "com.google.android.libraries.launcherclient.ILauncherOverlay"
        const val QUERY = "v=7&cv=9"
        const val CONNECT_TIMEOUT_SECONDS = 3L

        // Bridge-only contract; must match DiscoverOverlayProtocol in the Chikku app. The codes sit
        // far above the overlay protocol's small transaction numbers, so they are never relayed.
        const val BRIDGE_DESCRIPTOR = "io.github.aj4abinjacob.chikku.discoverbridge.IDiscoverBridge"
        const val STATUS = 0x00B1D601
        const val REREGISTER = 0x00B1D602
        const val GOOGLE_PENDING = 0
        const val GOOGLE_CONNECTED = 1
        const val GOOGLE_REJECTED = 2
        const val GOOGLE_BIND_FAILED = 3
    }
}
