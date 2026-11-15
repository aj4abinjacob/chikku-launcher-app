package io.github.aj4abinjacob.chikku.platform.cards

import android.app.Application
import android.content.Intent
import android.os.BatteryManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.home.TestHomeApplication
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestHomeApplication::class)
class CardEnvironmentSourceTest {
    @Test fun unknownBatteryStateFailsClosed() {
        assertThat(AndroidCardEnvironmentSource.batteryCharging(Intent(Intent.ACTION_BATTERY_CHANGED))).isNull()
        assertThat(AndroidCardEnvironmentSource.batteryCharging(Intent().putExtra(BatteryManager.EXTRA_STATUS,
            BatteryManager.BATTERY_STATUS_UNKNOWN))).isNull()
    }

    @Test fun chargingAndUnpluggedStatusAreDistinct() {
        fun status(value: Int) = AndroidCardEnvironmentSource.batteryCharging(Intent().putExtra(BatteryManager.EXTRA_STATUS, value))
        assertThat(status(BatteryManager.BATTERY_STATUS_CHARGING)).isTrue()
        assertThat(status(BatteryManager.BATTERY_STATUS_FULL)).isTrue()
        assertThat(status(BatteryManager.BATTERY_STATUS_DISCHARGING)).isFalse()
        assertThat(status(BatteryManager.BATTERY_STATUS_NOT_CHARGING)).isFalse()
    }

    @Test fun collectionRegistersAndReleasesReceiversAndCachedConditions() = runTest {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val before = shadowOf(context).registeredReceivers.size
        val source = AndroidCardEnvironmentSource(context)
        val began = CompletableDeferred<Unit>()
        val job = launch { source.observe().collect { began.complete(Unit) } }
        began.await()
        assertThat(shadowOf(context).registeredReceivers.size).isEqualTo(before + 1)
        job.cancelAndJoin()
        assertThat(shadowOf(context).registeredReceivers.size).isEqualTo(before)
        assertThat(source.current().isCharging).isNull()
        assertThat(source.current().isWifi).isNull()
    }
}
