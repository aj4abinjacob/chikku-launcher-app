package io.github.aj4abinjacob.chikku.platform.cards.bluetooth

import android.bluetooth.BluetoothProfile
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class CardBluetoothSourceTest {
    @Test fun audioProfilesIgnoreConnectingStatesAndUnknownValuesFailClosed() {
        assertThat(connectedAudioState(BluetoothProfile.STATE_CONNECTED, BluetoothProfile.STATE_DISCONNECTED)).isTrue()
        assertThat(connectedAudioState(BluetoothProfile.STATE_DISCONNECTED, BluetoothProfile.STATE_CONNECTED)).isTrue()
        assertThat(connectedAudioState(BluetoothProfile.STATE_CONNECTING, BluetoothProfile.STATE_DISCONNECTING)).isFalse()
        assertThat(connectedAudioState(-1, BluetoothProfile.STATE_DISCONNECTED)).isNull()
        assertThat(connectedAudioState(BluetoothProfile.STATE_DISCONNECTED, 99)).isNull()
    }

    @Test fun burstRefreshesNeverRunNativeReadsConcurrentlyOrPublishRevokedResult() = runTest {
        val triggers = Channel<Unit>(Channel.CONFLATED)
        val gate = CompletableDeferred<Unit>()
        val observed = mutableListOf<Boolean?>()
        var granted = true
        var reads = 0
        var active = 0
        var maximum = 0
        val job = backgroundScope.launch {
            serializedBluetoothReads(triggers.receiveAsFlow(), {
                reads++; active++; maximum = maxOf(maximum, active)
                try { if (reads == 1) { gate.await(); true } else false } finally { active-- }
            }, { granted }).collect { observed += it }
        }
        triggers.send(Unit); runCurrent()
        repeat(8) { triggers.trySend(Unit) }; runCurrent()
        assertThat(reads).isEqualTo(1)
        granted = false; gate.complete(Unit); runCurrent()
        assertThat(observed).containsExactly(null, null)
        assertThat(maximum).isEqualTo(1)
        assertThat(reads).isEqualTo(1)
        granted = true; triggers.send(Unit); runCurrent()
        assertThat(observed.last()).isFalse()
        assertThat(reads).isEqualTo(2)
        job.cancel()
    }
}
