package io.github.aj4abinjacob.chikku.platform

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.Parcel
import android.view.WindowManager
import androidx.activity.ComponentActivity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GoogleDiscoverClientTest {
    private val component = ComponentName("com.google.android.googlequicksearchbox", "GoogleOverlayService")

    @Test
    fun disabledOptionDoesNotInspectOrBindProviders() {
        Robolectric.buildActivity(DiscoverTestActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            var resolutions = 0
            val client = client(activity, GoogleDiscoverResolver { resolutions++; DiscoverProvider.MissingGoogle })
            client.onStart(activity)
            client.onResume(activity)
            assertThat(client.open()).isEqualTo(DiscoverOpenResult.UNAVAILABLE)
            assertThat(resolutions).isEqualTo(0)
            assertThat(activity.binds).isEqualTo(0)
        }
    }

    @Test
    fun missingProvidersAndResolverFailuresLeaveHomeUsable() {
        Robolectric.buildActivity(DiscoverTestActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            listOf(
                DiscoverProvider.MissingGoogle to DiscoverOpenResult.MISSING_GOOGLE,
                DiscoverProvider.Unavailable to DiscoverOpenResult.UNAVAILABLE,
            ).forEach { (provider, expected) ->
                val client = client(activity, GoogleDiscoverResolver { provider })
                client.onStart(activity)
                client.setEnabled(true)
                assertThat(client.open()).isEqualTo(expected)
                client.setEnabled(false)
            }
            val broken = client(activity, GoogleDiscoverResolver { error("provider failed") })
            broken.onStart(activity)
            broken.setEnabled(true)
            assertThat(broken.open()).isEqualTo(DiscoverOpenResult.UNAVAILABLE)
            assertThat(activity.binds).isEqualTo(0)
        }
    }

    @Test
    fun modernOverlayAttachesOpensClosesAndUnbindsOnDisable() {
        Robolectric.buildActivity(DiscoverTestActivity::class.java).setup().visible().use { controller ->
            val activity = controller.get()
            val overlay = FakeOverlay()
            val positions = mutableListOf<Float>()
            val client = client(activity, GoogleDiscoverResolver {
                DiscoverProvider.Available(component, 7, Intent(Intent.ACTION_MAIN).setComponent(component))
            }, positions::add)
            client.onStart(activity)
            client.onResume(activity)
            client.setEnabled(true)
            activity.deliverOverlay(overlay)
            client.attachWindow()
            assertThat(overlay.transactions).contains(14)
            assertThat(overlay.activityState).isEqualTo(3)
            assertThat(overlay.options).isEqualTo(1)
            assertThat(overlay.params?.token).isNotNull()
            overlay.status(1)
            assertThat(client.open()).isEqualTo(DiscoverOpenResult.OPENED)
            assertThat(activity.launched).isEmpty()
            assertThat(overlay.transactions).contains(9)
            overlay.progress(0.5f)
            assertThat(client.isOpen).isTrue()
            assertThat(positions.last()).isEqualTo(0.5f)
            client.close()
            assertThat(client.isOpen).isFalse()
            assertThat(overlay.transactions).contains(6)
            client.setEnabled(false)
            assertThat(overlay.transactions).contains(5)
            assertThat(activity.unbinds).isEqualTo(1)
            // A queued callback from a disconnected provider cannot move the home screen.
            overlay.progress(1f)
            assertThat(positions.last()).isEqualTo(0f)
            assertThat(client.open()).isEqualTo(DiscoverOpenResult.UNAVAILABLE)
        }
    }

    @Test
    fun legacyOverlayAndProviderDeathAreContainedAndCanReconnect() {
        Robolectric.buildActivity(DiscoverTestActivity::class.java).setup().visible().use { controller ->
            val activity = controller.get()
            val overlay = FakeOverlay()
            val client = client(activity, GoogleDiscoverResolver { DiscoverProvider.Available(component, 1) })
            client.onStart(activity)
            client.onResume(activity)
            client.setEnabled(true)
            activity.deliverOverlay(overlay)
            client.attachWindow()
            assertThat(overlay.transactions).containsAtLeast(4, 8)
            overlay.status(1)
            overlay.fail = true
            assertThat(client.open()).isNotEqualTo(DiscoverOpenResult.OPENED)
            assertThat(client.isOpen).isFalse()
            assertThat(activity.unbinds).isEqualTo(1)
            // open() retries the connection after a failed Binder call.
            assertThat(activity.binds).isEqualTo(2)
            val replacement = FakeOverlay()
            activity.deliverOverlay(replacement)
            client.attachWindow()
            replacement.status(1)
            assertThat(client.open()).isEqualTo(DiscoverOpenResult.OPENED)
            client.onPause(activity)
            client.onStop(activity)
            assertThat(client.isOpen).isFalse()
            assertThat(activity.unbinds).isEqualTo(2)
            overlay.fail = false
            overlay.progress(1f)
            assertThat(client.isOpen).isFalse()
        }
    }

    @Test
    fun disablingWhileResolvingCancelsBindingAndUnresponsiveProviderTimesOut() {
        Robolectric.buildActivity(DiscoverTestActivity::class.java).setup().visible().use { controller ->
            val activity = controller.get()
            val dispatcher = UnconfinedTestDispatcher()
            val pending = CompletableDeferred<DiscoverProvider>()
            val client = GoogleDiscoverClient(activity, GoogleDiscoverResolver { pending.await() }, CoroutineScope(dispatcher), dispatcher, metadata = ShortcutMetadataPolicy { _, _ -> true }) {}
            client.onStart(activity)
            client.setEnabled(true)
            assertThat(client.open()).isEqualTo(DiscoverOpenResult.CONNECTING)
            client.setEnabled(false)
            pending.complete(DiscoverProvider.Available(component, 7))
            dispatcher.scheduler.runCurrent()
            assertThat(activity.binds).isEqualTo(0)

            val waiting = GoogleDiscoverClient(
                activity, GoogleDiscoverResolver { DiscoverProvider.Available(component, 7) }, CoroutineScope(dispatcher), dispatcher, metadata = ShortcutMetadataPolicy { _, _ -> true },
            ) {}
            waiting.onStart(activity)
            waiting.setEnabled(true)
            assertThat(activity.binds).isEqualTo(1)
            dispatcher.scheduler.advanceTimeBy(5_001)
            dispatcher.scheduler.runCurrent()
            assertThat(activity.unbinds).isEqualTo(1)
            waiting.setEnabled(false)
            assertThat(activity.unbinds).isEqualTo(1)
        }
    }

    @Test
    fun bindRejectionIsContainedWithoutKeepingAConnection() {
        Robolectric.buildActivity(DiscoverTestActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            activity.rejectBind = true
            val client = client(activity, GoogleDiscoverResolver { DiscoverProvider.Available(component, 7) })
            client.onStart(activity)
            client.setEnabled(true)
            assertThat(activity.unbinds).isEqualTo(1)
            assertThat(client.open()).isEqualTo(DiscoverOpenResult.UNAVAILABLE)
            assertThat(activity.binds).isEqualTo(2)
            client.setEnabled(false)
            assertThat(client.isOpen).isFalse()
        }
    }

    @Test
    fun installedGoogleAppOpensWhenNoOverlayIsAvailable() {
        Robolectric.buildActivity(DiscoverTestActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            val appIntent = Intent(Intent.ACTION_MAIN).setComponent(component)
            val client = client(activity, GoogleDiscoverResolver { DiscoverProvider.AppOnly(appIntent) })
            client.onStart(activity)
            client.setEnabled(true)
            assertThat(client.open()).isEqualTo(DiscoverOpenResult.OPENED_GOOGLE_APP)
            assertThat(activity.launched.single().component).isEqualTo(component)
            assertThat(activity.binds).isEqualTo(0)
            client.setEnabled(false)
            assertThat(client.open()).isEqualTo(DiscoverOpenResult.UNAVAILABLE)
            assertThat(activity.launched).hasSize(1)
        }
    }

    @Test
    fun rejectedOverlayBindingStillAllowsOpeningGoogleWithoutACompanion() {
        Robolectric.buildActivity(DiscoverTestActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            activity.rejectBind = true
            val appIntent = Intent(Intent.ACTION_MAIN).setComponent(component)
            val client = client(activity, GoogleDiscoverResolver { DiscoverProvider.Available(component, 7, appIntent) })
            client.onStart(activity)
            client.setEnabled(true)
            assertThat(client.open()).isEqualTo(DiscoverOpenResult.OPENED_GOOGLE_APP)
            assertThat(activity.launched).hasSize(1)
            activity.rejectLaunch = true
            assertThat(client.open()).isEqualTo(DiscoverOpenResult.UNAVAILABLE)
            client.setEnabled(false)
        }
    }

    @Test
    fun directProviderBindsGoogleAndBridgeProviderBindsTheBridge() {
        Robolectric.buildActivity(DiscoverTestActivity::class.java).setup().visible().use { controller ->
            val activity = controller.get()
            val direct = client(activity, GoogleDiscoverResolver { DiscoverProvider.Available(component, 7) })
            direct.onStart(activity)
            direct.setEnabled(true)
            assertThat(activity.boundIntent?.action).isEqualTo("com.android.launcher3.WINDOW_OVERLAY")
            assertThat(activity.boundIntent?.component).isEqualTo(component)
            direct.setEnabled(false)

            val bridge = ComponentName("io.github.aj4abinjacob.chikku.discoverbridge", "Bridge")
            val relayed = client(activity, GoogleDiscoverResolver {
                DiscoverProvider.Available(bridge, 7, viaBridge = true)
            })
            relayed.onStart(activity)
            relayed.onResume(activity)
            relayed.setEnabled(true)
            assertThat(activity.boundIntent?.action).isEqualTo(AndroidGoogleDiscoverResolver.BRIDGE_ACTION)
            assertThat(activity.boundIntent?.component).isEqualTo(bridge)
            // The bridge relays the same overlay protocol, so the window attaches and opens as before.
            val overlay = FakeOverlay()
            activity.deliverOverlay(overlay)
            relayed.attachWindow()
            assertThat(overlay.transactions).contains(14)
            overlay.status(1)
            assertThat(relayed.open()).isEqualTo(DiscoverOpenResult.OPENED)
            relayed.setEnabled(false)
        }
    }

    private val bridge = ComponentName("io.github.aj4abinjacob.chikku.discoverbridge", "Bridge")
    private val googleApp = Intent(Intent.ACTION_MAIN).setComponent(component)

    @Test
    fun failingBridgeIsExplainedInsteadOfOpeningGoogleAndReconnectsBoundedly() {
        Robolectric.buildActivity(DiscoverTestActivity::class.java).setup().visible().use { controller ->
            val activity = controller.get()
            val dispatcher = UnconfinedTestDispatcher()
            val client = GoogleDiscoverClient(
                activity, GoogleDiscoverResolver { DiscoverProvider.Available(bridge, 7, googleApp, viaBridge = true) },
                CoroutineScope(dispatcher), dispatcher, externalIntents = fakeStarter(activity), metadata = ShortcutMetadataPolicy { _, _ -> true },
            ) {}
            activity.rejectBind = true
            client.onStart(activity)
            client.onResume(activity)
            client.setEnabled(true)
            assertThat(client.open()).isEqualTo(DiscoverOpenResult.BRIDGE_PROBLEM)
            assertThat(activity.launched).isEmpty()
            // Reconnects happen without a swipe, but stop after a few attempts.
            dispatcher.scheduler.advanceTimeBy(60_000)
            dispatcher.scheduler.runCurrent()
            val attempts = activity.binds
            assertThat(attempts).isIn(3..5)
            dispatcher.scheduler.advanceTimeBy(60_000)
            dispatcher.scheduler.runCurrent()
            assertThat(activity.binds).isEqualTo(attempts)

            // The explanation dialog can still open the Google app, and a retry recovers.
            assertThat(client.openGoogleApp()).isTrue()
            assertThat(activity.launched).hasSize(1)
            activity.rejectBind = false
            client.retry()
            assertThat(activity.boundFlags and Context.BIND_IMPORTANT).isNotEqualTo(0)
            val overlay = FakeOverlay()
            activity.deliverOverlay(overlay)
            client.attachWindow()
            overlay.status(1)
            assertThat(client.open()).isEqualTo(DiscoverOpenResult.OPENED)
            client.setEnabled(false)
        }
    }

    @Test
    fun connectingBridgeDoesNotJumpToTheGoogleApp() {
        Robolectric.buildActivity(DiscoverTestActivity::class.java).setup().visible().use { controller ->
            val activity = controller.get()
            val client = client(activity, GoogleDiscoverResolver {
                DiscoverProvider.Available(bridge, 7, googleApp, viaBridge = true)
            })
            client.onStart(activity)
            client.setEnabled(true)
            assertThat(client.open()).isEqualTo(DiscoverOpenResult.CONNECTING)
            assertThat(activity.launched).isEmpty()
            client.setEnabled(false)
        }
    }

    @Test
    fun silentBridgeReregistersOnceThenShowsCardsOrReportsTheProblem() {
        Robolectric.buildActivity(DiscoverTestActivity::class.java).setup().visible().use { controller ->
            val activity = controller.get()
            val dispatcher = UnconfinedTestDispatcher()
            val client = GoogleDiscoverClient(
                activity, GoogleDiscoverResolver { DiscoverProvider.Available(bridge, 7, googleApp, viaBridge = true) },
                CoroutineScope(dispatcher), dispatcher, externalIntents = fakeStarter(activity), metadata = ShortcutMetadataPolicy { _, _ -> true },
            ) {}
            client.onStart(activity)
            client.onResume(activity)
            client.setEnabled(true)
            val overlay = FakeOverlay()
            activity.deliverOverlay(overlay)
            client.attachWindow()
            dispatcher.scheduler.advanceTimeBy(5_001)
            dispatcher.scheduler.runCurrent()
            assertThat(overlay.reregisters).isEqualTo(1)
            assertThat(overlay.transactions.count { it == 14 }).isEqualTo(2)
            overlay.status(1)
            assertThat(client.open()).isEqualTo(DiscoverOpenResult.OPENED)
            client.close()

            // Still silent after re-registering: give up and explain on the next swipe.
            client.retry()
            val silent = FakeOverlay()
            activity.deliverOverlay(silent)
            client.attachWindow()
            dispatcher.scheduler.advanceTimeBy(5_001)
            dispatcher.scheduler.runCurrent()
            dispatcher.scheduler.advanceTimeBy(5_001)
            dispatcher.scheduler.runCurrent()
            assertThat(silent.reregisters).isEqualTo(1)
            assertThat(client.open()).isEqualTo(DiscoverOpenResult.BRIDGE_PROBLEM)
            assertThat(activity.launched).isEmpty()
            client.setEnabled(false)
        }
    }

    private fun client(
        activity: DiscoverTestActivity,
        resolver: GoogleDiscoverResolver,
        progress: (Float) -> Unit = {},
    ) = GoogleDiscoverClient(activity, resolver, CoroutineScope(UnconfinedTestDispatcher()),
        io = Dispatchers.Unconfined, externalIntents = fakeStarter(activity),
        metadata = ShortcutMetadataPolicy { _, _ -> true }, onProgress = progress)

    // This suite tests the Discover protocol. App-lock dispatch is covered by AuditExternalLaunchGuardTest.
    private fun fakeStarter(activity: DiscoverTestActivity) = ExternalIntentStarter { intents ->
        try { activity.startActivity(intents.first()); true } catch (_: ActivityNotFoundException) { false }
    }
}

class DiscoverTestActivity : ComponentActivity() {
    var binds = 0
    var unbinds = 0
    var rejectBind = false
    var rejectLaunch = false
    val launched = mutableListOf<Intent>()

    override fun startActivity(intent: Intent) {
        if (rejectLaunch) throw ActivityNotFoundException()
        launched += intent
    }
    var boundIntent: Intent? = null
    var boundFlags = 0
    private var connection: ServiceConnection? = null
    private var component: ComponentName? = null

    override fun bindService(service: Intent, connection: ServiceConnection, flags: Int): Boolean {
        binds++
        if (rejectBind) return false
        this.connection = connection
        component = service.component
        boundIntent = service
        boundFlags = flags
        return true
    }

    override fun unbindService(connection: ServiceConnection) { unbinds++ }

    fun deliverOverlay(overlay: IBinder) {
        connection!!.onServiceConnected(component!!, overlay)
    }
}

private class FakeOverlay : Binder() {
    val transactions = mutableListOf<Int>()
    var fail = false
    var activityState = 0
    var options = 0
    var params: WindowManager.LayoutParams? = null
    private var callback: IBinder? = null

    var reregisters = 0

    @Suppress("DEPRECATION")
    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        if (fail) return false
        if (code == BRIDGE_REREGISTER) {
            // Doubles as the bridge relay: answers the bridge-only re-register call.
            data.enforceInterface(DiscoverOverlayProtocol.BRIDGE)
            reregisters++
            reply!!.writeNoException()
            reply.writeInt(BridgeGoogleLink.CONNECTED.ordinal)
            return true
        }
        data.enforceInterface(DiscoverOverlayProtocol.OVERLAY)
        transactions += code
        when (code) {
            4 -> {
                check(data.readInt() == 1)
                params = WindowManager.LayoutParams.CREATOR.createFromParcel(data)
                callback = data.readStrongBinder()
                options = data.readInt()
            }
            14 -> {
                check(data.readInt() == 1)
                val bundle = Bundle.CREATOR.createFromParcel(data)
                params = bundle.getParcelable("layout_params")
                options = bundle.getInt("client_options")
                callback = data.readStrongBinder()
            }
            16 -> activityState = data.readInt()
        }
        return true
    }

    fun status(value: Int) = DiscoverOverlayProtocol.send(callback!!, DiscoverOverlayProtocol.OVERLAY_CALLBACK, 2) {
        it.writeInt(value)
    }

    fun progress(value: Float) = DiscoverOverlayProtocol.send(callback!!, DiscoverOverlayProtocol.OVERLAY_CALLBACK, 1) {
        it.writeFloat(value)
    }
}

private const val BRIDGE_REREGISTER = 0x00B1D602
