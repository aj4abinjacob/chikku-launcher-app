package io.github.aj4abinjacob.chikku.platform.cards

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import io.github.aj4abinjacob.chikku.core.model.cards.CardEnvironment
import io.github.aj4abinjacob.chikku.platform.cards.bluetooth.CardBluetoothSource
import io.github.aj4abinjacob.chikku.platform.cards.bluetooth.AndroidCardBluetoothSource
import java.time.ZonedDateTime
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

interface CardEnvironmentSource {
    /** Cold observation: closing the collector releases every callback and the minute tick. */
    fun observe(): Flow<CardEnvironment>
    /** Existing implementations need not observe Bluetooth; consumers opt in explicitly. */
    fun observe(bluetoothRequired: Boolean): Flow<CardEnvironment> = observe()
    /** Current local time and the most recently observed device state; no binder or disk calls. */
    fun current(): CardEnvironment
}

class AndroidCardEnvironmentSource(
    context: Context,
    private val bluetooth: CardBluetoothSource = AndroidCardBluetoothSource(context),
) : CardEnvironmentSource {
    private val context = context.applicationContext
    private class DeviceState {
        @Volatile var charging: Boolean? = null
        @Volatile var wifi: Boolean? = null
        @Volatile var bluetooth: Boolean? = null
    }
    @Volatile private var observedState: DeviceState? = null

    override fun current(): CardEnvironment {
        val now = ZonedDateTime.now()
        val state = observedState
        return CardEnvironment(now.dayOfWeek.value, now.hour * 60 + now.minute, state?.charging, state?.wifi, state?.bluetooth)
    }

    override fun observe(): Flow<CardEnvironment> = observe(bluetoothRequired = false)

    override fun observe(bluetoothRequired: Boolean): Flow<CardEnvironment> = callbackFlow {
        val active = AtomicBoolean(true)
        val state = DeviceState()
        observedState = state
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (!active.get()) return
                if (intent?.action == Intent.ACTION_BATTERY_CHANGED) state.charging = batteryCharging(intent)
                trySend(current())
            }
        }
        var receiverRegistered = false
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        var defaultNetwork: Network? = null
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                if (!active.get()) return
                defaultNetwork = network
                state.wifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                trySend(current())
            }
            override fun onLost(network: Network) {
                if (!active.get() || network != defaultNetwork) return
                defaultNetwork = null
                state.wifi = false
                trySend(current())
            }
        }
        var networkRegistered = false
        try {
            val sticky = ContextCompat.registerReceiver(context, receiver, IntentFilter().apply {
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            }, ContextCompat.RECEIVER_NOT_EXPORTED)
            receiverRegistered = true
            sticky?.let { state.charging = batteryCharging(it) }
        } catch (_: RuntimeException) {
            // Unknown state fails closed for a condition that requires charging.
        }
        try {
            defaultNetwork = connectivity?.activeNetwork
            state.wifi = defaultNetwork?.let { connectivity?.getNetworkCapabilities(it) }
                ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ?: false
            connectivity?.registerDefaultNetworkCallback(callback)
            networkRegistered = connectivity != null
        } catch (_: RuntimeException) {
            state.wifi = null
        }
        trySend(current())
        val bluetoothObservation = if (bluetoothRequired) launch {
            bluetooth.observe().collect { connected ->
                if (active.get()) { state.bluetooth = connected; trySend(current()) }
            }
        } else null
        val ticker = launch {
            while (true) {
                delay(60_000L - System.currentTimeMillis().mod(60_000L))
                trySend(current())
            }
        }
        awaitClose {
            active.set(false)
            ticker.cancel()
            bluetoothObservation?.cancel()
            if (receiverRegistered) runCatching { context.unregisterReceiver(receiver) }
            if (networkRegistered) runCatching { connectivity?.unregisterNetworkCallback(callback) }
            if (observedState === state) observedState = null
        }
    }.flowOn(Dispatchers.IO)

    companion object {
        internal fun batteryCharging(intent: Intent): Boolean? {
            if (!intent.hasExtra(BatteryManager.EXTRA_STATUS)) return null
            return when (intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
                BatteryManager.BATTERY_STATUS_CHARGING, BatteryManager.BATTERY_STATUS_FULL -> true
                BatteryManager.BATTERY_STATUS_DISCHARGING, BatteryManager.BATTERY_STATUS_NOT_CHARGING -> false
                else -> null
            }
        }
    }
}
