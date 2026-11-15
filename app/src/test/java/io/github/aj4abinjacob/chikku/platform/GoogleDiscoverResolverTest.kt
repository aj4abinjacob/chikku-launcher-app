package io.github.aj4abinjacob.chikku.platform

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import android.content.pm.Signature
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GoogleDiscoverResolverTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val google = AndroidGoogleDiscoverResolver.GOOGLE_PACKAGE
    private val launcher = ComponentName(google, "$google.Main")

    @Test
    fun playDistributionUsesOnlyTheAppHandoffEvenWithATrustedBridge() = runTest {
        installGoogle()
        installGoogleOverlay()
        val key = Signature("c0ffee")
        shadowOf(context.packageManager).getInternalMutablePackageInfo(context.packageName).signatures = arrayOf(key)
        installBridge(ComponentName("io.github.aj4abinjacob.chikku.discoverbridge", "Bridge"), key)
        val resolver = AndroidGoogleDiscoverResolver(
            context, UnconfinedTestDispatcher(testScheduler), experimentalOverlay = false,
        )
        val provider = resolver.resolve() as DiscoverProvider.AppOnly
        assertThat(provider.appIntent.component).isEqualTo(launcher)
    }

    @Test
    fun absentOrDisabledGoogleDoesNotOfferAProvider() = runTest {
        val resolver = AndroidGoogleDiscoverResolver(context, UnconfinedTestDispatcher(testScheduler))
        assertThat(resolver.resolve()).isEqualTo(DiscoverProvider.MissingGoogle)
        installGoogle(enabled = false)
        assertThat(resolver.resolve()).isEqualTo(DiscoverProvider.MissingGoogle)
    }

    @Test
    fun installedGoogleWithoutOverlayOffersAppOpeningWithoutABridge() = runTest {
        installGoogle()
        val resolver = AndroidGoogleDiscoverResolver(context, UnconfinedTestDispatcher(testScheduler))
        val provider = resolver.resolve() as DiscoverProvider.AppOnly
        assertThat(provider.appIntent.component).isEqualTo(launcher)
    }

    @Test
    fun exportedGoogleServiceIsResolvedDirectlyWithAppFallback() = runTest {
        installGoogle()
        val service = ServiceInfo().apply {
            packageName = google
            name = "$google.Overlay"
            enabled = true
            exported = true
            applicationInfo = context.packageManager.getApplicationInfo(google, 0)
            metaData = Bundle().apply { putInt("service.api.version", 7) }
        }
        shadowOf(context.packageManager).addResolveInfoForIntent(
            AndroidGoogleDiscoverResolver.overlayIntent(context, google),
            ResolveInfo().apply { serviceInfo = service },
        )
        val resolver = AndroidGoogleDiscoverResolver(context, UnconfinedTestDispatcher(testScheduler))
        val provider = resolver.resolve() as DiscoverProvider.Available
        assertThat(provider.component).isEqualTo(ComponentName(google, service.name))
        assertThat(provider.apiVersion).isEqualTo(7)
        assertThat(provider.appIntent?.component).isEqualTo(launcher)
        service.exported = false
        assertThat(resolver.resolve()).isInstanceOf(DiscoverProvider.AppOnly::class.java)
    }

    @Test
    fun sameSignerBridgeIsPreferredAndOtherSignersAreIgnored() = runTest {
        installGoogle()
        installGoogleOverlay()
        val key = Signature("c0ffee")
        shadowOf(context.packageManager).getInternalMutablePackageInfo(context.packageName).signatures = arrayOf(key)
        val bridge = ComponentName("io.github.aj4abinjacob.chikku.discoverbridge", "Bridge")
        installBridge(bridge, Signature("bad0"))
        val resolver = AndroidGoogleDiscoverResolver(context, UnconfinedTestDispatcher(testScheduler))
        val foreign = resolver.resolve() as DiscoverProvider.Available
        assertThat(foreign.viaBridge).isFalse()
        assertThat(foreign.component.packageName).isEqualTo(google)

        shadowOf(context.packageManager).getInternalMutablePackageInfo(bridge.packageName).signatures = arrayOf(key)
        val trusted = resolver.resolve() as DiscoverProvider.Available
        assertThat(trusted.viaBridge).isTrue()
        assertThat(trusted.component).isEqualTo(bridge)
        assertThat(trusted.apiVersion).isEqualTo(7)
        assertThat(trusted.appIntent?.component).isEqualTo(launcher)
    }

    private fun installGoogleOverlay() {
        val service = ServiceInfo().apply {
            packageName = google
            name = "$google.Overlay"
            enabled = true
            exported = true
            applicationInfo = context.packageManager.getApplicationInfo(google, 0)
            metaData = Bundle().apply { putInt("service.api.version", 7) }
        }
        shadowOf(context.packageManager).addResolveInfoForIntent(
            AndroidGoogleDiscoverResolver.overlayIntent(context, google),
            ResolveInfo().apply { serviceInfo = service },
        )
    }

    private fun installBridge(component: ComponentName, signature: Signature) {
        val pm = shadowOf(context.packageManager)
        pm.installPackage(PackageInfo().apply {
            packageName = component.packageName
            signatures = arrayOf(signature)
            applicationInfo = ApplicationInfo().apply { packageName = component.packageName }
        })
        val service = ServiceInfo().apply {
            packageName = component.packageName
            name = component.className
            enabled = true
            exported = true
            applicationInfo = context.packageManager.getApplicationInfo(component.packageName, 0)
        }
        pm.addResolveInfoForIntent(
            Intent(AndroidGoogleDiscoverResolver.BRIDGE_ACTION),
            ResolveInfo().apply { serviceInfo = service },
        )
    }

    private fun installGoogle(enabled: Boolean = true) {
        val pm = shadowOf(context.packageManager)
        pm.installPackage(PackageInfo().apply {
            packageName = google
            applicationInfo = ApplicationInfo().apply {
                packageName = google
                this.enabled = enabled
            }
        })
        pm.addActivityIfNotPresent(launcher)
        pm.addIntentFilterForActivity(launcher, IntentFilter(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        })
    }
}
