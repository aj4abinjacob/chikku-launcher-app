package io.github.aj4abinjacob.chikku.platform

import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.RemoteException

/**
 * Original Kotlin wire adapter for the Google launcher-overlay Binder contracts.
 * Transaction numbers are part of the interoperability protocol, not Android public APIs.
 * No upstream client implementation, generated AIDL source, assets or dependency is bundled.
 */
internal object DiscoverOverlayProtocol {
    const val OVERLAY = "com.google.android.libraries.launcherclient.ILauncherOverlay"
    const val OVERLAY_CALLBACK = "com.google.android.libraries.launcherclient.ILauncherOverlayCallback"

    // Bridge-only contract with the :discover-bridge app; must match DiscoverBridgeService.
    const val BRIDGE = "io.github.aj4abinjacob.chikku.discoverbridge.IDiscoverBridge"
    private const val BRIDGE_STATUS = 0x00B1D601
    private const val BRIDGE_REREGISTER = 0x00B1D602

    /** Blocking (waits up to a few seconds for the bridge's Google link); call off the main thread. */
    fun bridgeStatus(bridge: IBinder): BridgeStatus = call(bridge, BRIDGE_STATUS) { reply ->
        BridgeStatus(
            google = BridgeGoogleLink.from(reply.readInt()),
            backgroundRestricted = reply.readInt() != 0,
            standbyBucket = reply.readInt(),
        )
    }

    /** Blocking: asks the bridge to bind Google with a fresh intent so Google registers it again. */
    fun bridgeReregister(bridge: IBinder): BridgeGoogleLink =
        call(bridge, BRIDGE_REREGISTER) { BridgeGoogleLink.from(it.readInt()) }

    private fun <T> call(bridge: IBinder, code: Int, read: (Parcel) -> T): T {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(BRIDGE)
            if (!bridge.transact(code, data, reply, 0)) throw RemoteException("Bridge call unavailable")
            reply.readException()
            return read(reply)
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    fun send(remote: IBinder, descriptor: String, transaction: Int, write: (Parcel) -> Unit = {}) {
        val data = Parcel.obtain()
        try {
            data.writeInterfaceToken(descriptor)
            write(data)
            if (!remote.transact(transaction, data, null, IBinder.FLAG_ONEWAY)) {
                throw RemoteException("Overlay transaction unavailable")
            }
        } finally {
            data.recycle()
        }
    }

    fun overlayCallback(scrolled: (Float) -> Unit, status: (Int) -> Unit): Binder =
        object : Binder() {
            init { attachInterface(null, OVERLAY_CALLBACK) }

            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                if (code !in 1..2) return super.onTransact(code, data, reply, flags)
                data.enforceInterface(OVERLAY_CALLBACK)
                if (code == 1) scrolled(data.readFloat()) else status(data.readInt())
                return true
            }
        }
}

enum class BridgeGoogleLink {
    PENDING, CONNECTED, REJECTED, BIND_FAILED;

    companion object {
        fun from(value: Int) = entries.getOrElse(value) { PENDING }
    }
}

/** What the bridge app reports about itself; [standbyBucket] uses `UsageStatsManager` bucket values. */
data class BridgeStatus(
    val google: BridgeGoogleLink,
    val backgroundRestricted: Boolean,
    val standbyBucket: Int,
) {
    val batteryRestricted: Boolean
        get() = backgroundRestricted || standbyBucket >= RESTRICTED_BUCKET

    private companion object {
        /** UsageStatsManager.STANDBY_BUCKET_RESTRICTED; the constant is API 30, the value is stable. */
        const val RESTRICTED_BUCKET = 45
    }
}
