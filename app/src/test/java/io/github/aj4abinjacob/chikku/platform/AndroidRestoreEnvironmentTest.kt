package io.github.aj4abinjacob.chikku.platform

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.backup.DiscoverReadiness
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AndroidRestoreEnvironmentTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    private fun env(health: DiscoverHealthChecker, packs: () -> Set<String> = { emptySet() }) =
        AndroidRestoreEnvironment(context, health, Dispatchers.IO, packs)

    @Test
    fun discoverHealthMapsToWhatTheSwipeWillDo() {
        val ok = BridgeStatus(BridgeGoogleLink.CONNECTED, backgroundRestricted = false, standbyBucket = 10)
        mapOf(
            DiscoverHealth.MissingGoogle to DiscoverReadiness.NO_GOOGLE_APP,
            DiscoverHealth.Direct to DiscoverReadiness.READY,
            DiscoverHealth.BridgeMissing to DiscoverReadiness.NO_BRIDGE,
            DiscoverHealth.BridgeWrongKey("b") to DiscoverReadiness.BRIDGE_NOT_WORKING,
            DiscoverHealth.BridgeDisabled("b") to DiscoverReadiness.BRIDGE_NOT_WORKING,
            DiscoverHealth.BridgeUnresponsive("b") to DiscoverReadiness.BRIDGE_NOT_WORKING,
            DiscoverHealth.Bridge("b", ok) to DiscoverReadiness.READY,
            DiscoverHealth.Bridge("b", ok.copy(backgroundRestricted = true)) to DiscoverReadiness.BRIDGE_NOT_WORKING,
            DiscoverHealth.Bridge("b", ok.copy(google = BridgeGoogleLink.REJECTED)) to DiscoverReadiness.BRIDGE_NOT_WORKING,
        ).forEach { (health, readiness) -> assertThat(health.readiness()).isEqualTo(readiness) }
    }

    @Test
    fun aFailingOrHangingCheckIsUnknownNotAWarning() = runBlocking {
        assertThat(env({ error("boom") }).discoverReadiness()).isEqualTo(DiscoverReadiness.UNKNOWN)
        val hanging = AndroidRestoreEnvironment(context, { awaitCancellation() }, Dispatchers.IO)
        assertThat(hanging.discoverReadiness()).isEqualTo(DiscoverReadiness.UNKNOWN)
        assertThat(env({ DiscoverHealth.Direct }, packs = { error("pm died") }).installedIconPacks()).isNull()
    }

    @Test
    fun unreadableContentIsReportedAsFalse() = runBlocking {
        assertThat(env({ DiscoverHealth.Direct }).canReadContent("content://nobody.home/image/1")).isFalse()
    }
}
