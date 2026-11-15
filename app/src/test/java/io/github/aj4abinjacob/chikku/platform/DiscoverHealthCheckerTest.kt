package io.github.aj4abinjacob.chikku.platform

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import android.content.pm.Signature
import android.os.Binder
import android.os.Looper
import android.os.Parcel
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.home.bridgePackage
import io.github.aj4abinjacob.chikku.home.messageRes
import kotlinx.coroutines.async
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DiscoverHealthCheckerTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val google = AndroidGoogleDiscoverResolver.GOOGLE_PACKAGE
    private val bridge = ComponentName("io.github.aj4abinjacob.chikku.discoverbridge", "Bridge")
    private val key = Signature("c0ffee")

    @Test
    fun appOnlyDistributionDoesNotRequireABridgeAfterRestore() = runTest {
        installGoogle()
        installBridge(key)
        shadowOf(context).declareComponentUnbindable(bridge)
        val health = AndroidDiscoverHealthChecker(
            context, UnconfinedTestDispatcher(testScheduler), experimentalOverlay = false,
        ).check()
        assertThat(health).isEqualTo(DiscoverHealth.AppOnly)
        assertThat(health.readiness()).isEqualTo(io.github.aj4abinjacob.chikku.core.model.backup.DiscoverReadiness.READY)
        assertThat(health.messageRes(false)).isEqualTo(R.string.discover_health_app_only)
        assertThat(health.bridgePackage).isNull()
    }

    @Before
    fun signChikku() {
        shadowOf(context.packageManager).getInternalMutablePackageInfo(context.packageName).signatures = arrayOf(key)
        context.applicationInfo.flags = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE.inv()
    }

    @Test
    fun missingGoogleIsReportedFirst() = runTest {
        assertThat(checker().check()).isEqualTo(DiscoverHealth.MissingGoogle)
    }

    @Test
    fun withoutABridgeReleaseBuildsOpenTheAppAndDebugBuildsConnectDirectly() = runTest {
        installGoogle()
        val missing = checker().check()
        assertThat(missing).isEqualTo(DiscoverHealth.BridgeMissing)
        assertThat(missing.messageRes(afterFailure = false)).isEqualTo(R.string.discover_health_bridge_missing)
        context.applicationInfo.flags = context.applicationInfo.flags or ApplicationInfo.FLAG_DEBUGGABLE
        assertThat(checker().check()).isEqualTo(DiscoverHealth.Direct)
    }

    @Test
    fun bridgeWithAnotherKeyOrDisabledIsNamed() = runTest {
        installGoogle()
        installBridge(Signature("bad0"))
        assertThat(checker().check()).isEqualTo(DiscoverHealth.BridgeWrongKey(bridge.packageName))

        installBridge(key, enabled = false)
        val disabled = checker().check()
        assertThat(disabled).isEqualTo(DiscoverHealth.BridgeDisabled(bridge.packageName))
        assertThat(disabled.bridgePackage).isEqualTo(bridge.packageName)
    }

    @Test
    fun bridgeThatCannotStartPointsAtBatterySettings() = runTest {
        installGoogle()
        installBridge(key)
        shadowOf(context).declareComponentUnbindable(bridge)
        val health = checkIdlingMainLooper()
        assertThat(health).isEqualTo(DiscoverHealth.BridgeUnresponsive(bridge.packageName))
        assertThat(health.messageRes(afterFailure = true)).isEqualTo(R.string.discover_health_unresponsive)
        assertThat(health.bridgePackage).isEqualTo(bridge.packageName)
    }

    @Test
    fun bridgeReportsBatteryRestrictionAndGoogleLink() = runTest {
        installGoogle()
        installBridge(key)
        shadowOf(context).setComponentNameAndServiceForBindService(bridge, FakeBridge(BridgeGoogleLink.CONNECTED, restricted = true))
        val restricted = checkIdlingMainLooper() as DiscoverHealth.Bridge
        assertThat(restricted.status.batteryRestricted).isTrue()
        assertThat(restricted.messageRes(afterFailure = true)).isEqualTo(R.string.discover_health_restricted)
        assertThat(restricted.bridgePackage).isEqualTo(bridge.packageName)

        shadowOf(context).setComponentNameAndServiceForBindService(bridge, FakeBridge(BridgeGoogleLink.CONNECTED, restricted = false))
        val healthy = checkIdlingMainLooper()
        assertThat(healthy.messageRes(afterFailure = false)).isEqualTo(R.string.discover_health_ready)
        // Healthy bridge but the swipe failed: Google is probably serving another launcher's companion.
        assertThat(healthy.messageRes(afterFailure = true)).isEqualTo(R.string.discover_health_not_shown)
        assertThat(healthy.bridgePackage).isNull()

        shadowOf(context).setComponentNameAndServiceForBindService(bridge, FakeBridge(BridgeGoogleLink.REJECTED, restricted = false))
        assertThat(checkIdlingMainLooper().messageRes(afterFailure = true)).isEqualTo(R.string.discover_health_rejected)
    }

    private fun checker() = AndroidDiscoverHealthChecker(context, UnconfinedTestDispatcher())

    /** Service connections are delivered on the main looper, which the check waits for. */
    private suspend fun kotlinx.coroutines.test.TestScope.checkIdlingMainLooper(): DiscoverHealth {
        val result = async { AndroidDiscoverHealthChecker(context, UnconfinedTestDispatcher(testScheduler)).check() }
        repeat(5) {
            shadowOf(Looper.getMainLooper()).idle()
            testScheduler.runCurrent()
        }
        return result.await()
    }

    private fun installGoogle() {
        shadowOf(context.packageManager).installPackage(PackageInfo().apply {
            packageName = google
            applicationInfo = ApplicationInfo().apply { packageName = google }
        })
    }

    private fun installBridge(signature: Signature, enabled: Boolean = true) {
        val pm = shadowOf(context.packageManager)
        pm.installPackage(PackageInfo().apply {
            packageName = bridge.packageName
            signatures = arrayOf(signature)
            applicationInfo = ApplicationInfo().apply { packageName = bridge.packageName }
        })
        val service = ServiceInfo().apply {
            packageName = bridge.packageName
            name = bridge.className
            this.enabled = enabled
            exported = true
            applicationInfo = context.packageManager.getApplicationInfo(bridge.packageName, 0)
        }
        pm.removeResolveInfosForIntent(Intent(AndroidGoogleDiscoverResolver.BRIDGE_ACTION), bridge.packageName)
        pm.addResolveInfoForIntent(
            Intent(AndroidGoogleDiscoverResolver.BRIDGE_ACTION),
            ResolveInfo().apply { serviceInfo = service },
        )
    }

    private class FakeBridge(private val link: BridgeGoogleLink, private val restricted: Boolean) : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            data.enforceInterface(DiscoverOverlayProtocol.BRIDGE)
            reply!!.writeNoException()
            reply.writeInt(link.ordinal)
            reply.writeInt(if (restricted) 1 else 0)
            reply.writeInt(10)
            return true
        }
    }
}
