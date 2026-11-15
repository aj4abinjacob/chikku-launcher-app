package io.github.aj4abinjacob.chikku.platform.cards.bluetooth

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.home.TestHomeApplication
import io.github.aj4abinjacob.chikku.platform.cards.AndroidCardEnvironmentSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestHomeApplication::class)
class BluetoothEnvironmentOptInTest {
    private class Bluetooth : CardBluetoothSource {
        var starts = 0
        var active = 0
        override fun hasPermission() = true
        override fun observe() = flow<Boolean?> {
            starts++; active++
            try { emit(true); awaitCancellation() } finally { active-- }
        }
    }
    @Test fun environmentOnlyObservesBluetoothForEnabledConditionAndReleasesOnClose() = runTest {
        val bluetooth = Bluetooth()
        val source = AndroidCardEnvironmentSource(ApplicationProvider.getApplicationContext<Application>(), bluetooth)
        val started = CompletableDeferred<Unit>()
        val plain = launch { source.observe(false).collect { started.complete(Unit) } }
        started.await()
        assertThat(bluetooth.starts).isEqualTo(0)
        assertThat(source.current().isBluetoothConnected).isNull()
        plain.cancelAndJoin()
        val connected = CompletableDeferred<Unit>()
        val enabled = launch { source.observe(true).collect { if (it.isBluetoothConnected == true) connected.complete(Unit) } }
        connected.await()
        assertThat(bluetooth.starts).isEqualTo(1)
        assertThat(bluetooth.active).isEqualTo(1)
        enabled.cancelAndJoin()
        assertThat(bluetooth.active).isEqualTo(0)
        assertThat(source.current().isBluetoothConnected).isNull()
    }
}
