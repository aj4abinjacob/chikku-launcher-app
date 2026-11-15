package io.github.aj4abinjacob.chikku.platform.cards.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

interface CardBluetoothSource {
    fun hasPermission(): Boolean
    /** Supported public audio profiles only; null means permission/device state is unavailable. */
    fun observe(): Flow<Boolean?>
}

/** No discovery, device list, address, name, bonding or location access. */
class AndroidCardBluetoothSource(context: Context) : CardBluetoothSource {
    private val context = context.applicationContext

    override fun hasPermission(): Boolean = Build.VERSION.SDK_INT < 31 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    // hasPermission checks BLUETOOTH_CONNECT before every protected call; revocation is caught.
    @SuppressLint("MissingPermission")
    private fun read(): Boolean? {
        if (!hasPermission()) return null
        return try {
            val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return false
            if (!adapter.isEnabled) return false
            connectedAudioState(adapter.getProfileConnectionState(BluetoothProfile.HEADSET),
                adapter.getProfileConnectionState(BluetoothProfile.A2DP))
        } catch (_: RuntimeException) { null }
    }

    override fun observe(): Flow<Boolean?> = callbackFlow {
        val active = AtomicBoolean(true)
        val refreshes = Channel<Unit>(Channel.CONFLATED)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (active.get()) refreshes.trySend(Unit)
            }
        }
        var registered = false
        try {
            ContextCompat.registerReceiver(context, receiver, IntentFilter().apply {
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
            }, ContextCompat.RECEIVER_EXPORTED)
            registered = true
        } catch (_: RuntimeException) { }
        val reads = launch {
            serializedBluetoothReads(refreshes.receiveAsFlow(), { read() }, { hasPermission() }).collect { connected ->
                if (active.get()) trySend(connected)
            }
        }
        refreshes.trySend(Unit)
        // While the visible card panel is collecting, notice grant revocation even without a
        // Bluetooth broadcast. Permission loss prevents every subsequent adapter query.
        val poll = launch { while (true) { delay(5_000); refreshes.trySend(Unit) } }
        awaitClose {
            active.set(false)
            poll.cancel()
            reads.cancel()
            refreshes.close()
            if (registered) runCatching { context.unregisterReceiver(receiver) }
        }
    }.flowOn(Dispatchers.IO)
}

internal fun connectedAudioState(headset: Int, a2dp: Int): Boolean? = when {
    headset == BluetoothProfile.STATE_CONNECTED || a2dp == BluetoothProfile.STATE_CONNECTED -> true
    headset in 0..3 && a2dp in 0..3 -> false
    else -> null
}

/** One native read at a time; a permission change during the read withholds its result. */
internal fun serializedBluetoothReads(
    triggers: Flow<Unit>, read: suspend () -> Boolean?, hasPermission: () -> Boolean,
): Flow<Boolean?> = triggers.map {
    if (!hasPermission()) null else read().takeIf { hasPermission() }
}
