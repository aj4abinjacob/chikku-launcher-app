package io.github.aj4abinjacob.chikku.platform

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.AppCustomization
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.EditResult
import io.github.aj4abinjacob.chikku.core.model.WorkspaceRepository
import io.github.aj4abinjacob.chikku.core.model.settings.HomeSettings
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.home.FakeAppCustomizationRepository
import io.github.aj4abinjacob.chikku.home.FakeSettingsRepository
import io.github.aj4abinjacob.chikku.home.FakeWorkspaceRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Install events and platform IO are injected. No real apps are installed or enumerated. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NewAppsControllerTest {
    private val a = AppKey("sample", "sample.A", 0)
    private val b = AppKey("sample", "sample.B", 0)

    private class EventSource : InstalledAppEventSource {
        var active: ((String) -> Unit)? = null
        val callbacks = mutableListOf<(String) -> Unit>()
        var registrations = 0
        var unregistrations = 0
        val queries = mutableListOf<String>()
        var resolve: suspend (String) -> List<AppKey> = { emptyList() }
        override fun register(onAdded: (String) -> Unit) { registrations++; active = onAdded; callbacks += onAdded }
        override fun unregister() { unregistrations++; active = null }
        override suspend fun personalKeys(packageName: String): List<AppKey> { queries += packageName; return resolve(packageName) }
        fun emit(packageName: String = "sample") { active?.invoke(packageName) }
    }

    private class RecordingWorkspace(val manual: FakeWorkspaceRepository = FakeWorkspaceRepository()) : WorkspaceRepository by manual {
        val installed = mutableListOf<AppKey>()
        var afterAdd: (AppKey) -> Unit = {}
        override suspend fun addInstalledApp(key: AppKey): EditResult {
            installed += key
            afterAdd(key)
            return EditResult.Applied("sample")
        }
    }

    private class Fixture {
        val settings = FakeSettingsRepository(LauncherSettings(home = HomeSettings(addNewApps = true)))
        val customizations = FakeAppCustomizationRepository()
        val workspace = RecordingWorkspace()
        val source = EventSource()
        var workspaceFactory: () -> WorkspaceRepository = { workspace }
        var safeToEdit = true
        var beforeGate: () -> Unit = {}
    }

    private fun TestScope.start(f: Fixture) {
        NewAppsController(
            ApplicationProvider.getApplicationContext<Context>(), backgroundScope, f.settings,
            { f.workspaceFactory() }, { f.customizations }, UnconfinedTestDispatcher(testScheduler),
            canEdit = { f.beforeGate(); f.safeToEdit }, installSource = f.source,
        )
        runCurrent()
    }

    @Test fun preferenceEnableDoesNotEnumerateExistingInventoryAndLockUnregisters() = runTest {
        val f = Fixture()
        f.settings.state.value = LauncherSettings()
        start(f)
        assertThat(f.source.registrations).isEqualTo(0)
        f.settings.state.value = LauncherSettings(home = HomeSettings(addNewApps = true))
        runCurrent()
        assertThat(f.source.registrations).isEqualTo(1)
        assertThat(f.source.queries).isEmpty()
        val stale = f.source.callbacks.last()
        f.settings.state.value = LauncherSettings(home = HomeSettings(addNewApps = true, layoutLocked = true))
        runCurrent()
        assertThat(f.source.unregistrations).isEqualTo(1)
        stale("sample")
        runCurrent()
        assertThat(f.source.queries).isEmpty()
        assertThat(f.workspace.installed).isEmpty()
    }

    @Test fun appsHiddenWhilePackageQueryIsSuspendedAreNotAdded() = runTest {
        val f = Fixture()
        val resolved = CompletableDeferred<List<AppKey>>()
        f.source.resolve = { resolved.await() }
        start(f)
        f.source.emit()
        runCurrent()
        f.customizations.state.value = mapOf(a to AppCustomization(a, hidden = true))
        resolved.complete(listOf(a))
        runCurrent()
        assertThat(f.workspace.installed).isEmpty()
    }

    @Test fun reenableRejectsOldInFlightAndQueuedEventsButProcessesNewEvents() = runTest {
        val f = Fixture()
        val resolved = CompletableDeferred<List<AppKey>>()
        var first = true
        f.source.resolve = { if (first) { first = false; resolved.await() } else listOf(a) }
        start(f)
        val oldCallback = f.source.callbacks.single()
        f.source.emit()
        runCurrent()
        f.settings.state.value = LauncherSettings()
        runCurrent()
        f.settings.state.value = LauncherSettings(home = HomeSettings(addNewApps = true))
        runCurrent()
        oldCallback("stale-event")
        f.source.emit("current-event")
        resolved.complete(listOf(a))
        runCurrent()
        assertThat(f.source.queries).containsExactly("sample", "current-event").inOrder()
        assertThat(f.workspace.installed).containsExactly(a)
        assertThat(f.workspace.manual.adds).isEmpty()
    }

    @Test fun hiddenStateIsReadAgainForEachComponentAndDuplicateQueryKeysAreCollapsed() = runTest {
        val f = Fixture()
        f.source.resolve = { listOf(b, a, a) }
        f.workspace.afterAdd = { f.customizations.state.value = mapOf(b to AppCustomization(b, hidden = true)) }
        start(f)
        f.source.emit()
        runCurrent()
        assertThat(f.workspace.installed).containsExactly(a)
    }

    @Test fun layoutLockDuringBatchStopsLaterAdds() = runTest {
        val f = Fixture()
        f.source.resolve = { listOf(a, b) }
        f.workspace.afterAdd = { f.settings.state.value = f.settings.state.value.let { it.copy(home = it.home.copy(layoutLocked = true)) } }
        start(f)
        f.source.emit()
        runCurrent()
        assertThat(f.workspace.installed).containsExactly(a)
    }

    @Test fun recoveryAndSafeModeGateAvoidQueriesAndSuppressInFlightAdds() = runTest {
        val f = Fixture()
        f.safeToEdit = false
        start(f)
        f.source.emit()
        runCurrent()
        assertThat(f.source.queries).isEmpty()
        f.safeToEdit = true
        val resolved = CompletableDeferred<List<AppKey>>()
        f.source.resolve = { resolved.await() }
        f.source.emit()
        runCurrent()
        f.safeToEdit = false
        resolved.complete(listOf(a))
        runCurrent()
        assertThat(f.workspace.installed).isEmpty()
    }

    @Test fun repositoryConstructionCannotBypassNewRecoveryOrHiddenState() = runTest {
        val recovery = Fixture()
        recovery.source.resolve = { listOf(a) }
        recovery.workspaceFactory = { recovery.safeToEdit = false; recovery.workspace }
        start(recovery)
        recovery.source.emit()
        runCurrent()
        assertThat(recovery.workspace.installed).isEmpty()

        val hidden = Fixture()
        hidden.source.resolve = { listOf(a) }
        hidden.workspaceFactory = {
            hidden.customizations.state.value = mapOf(a to AppCustomization(a, hidden = true))
            hidden.workspace
        }
        start(hidden)
        hidden.source.emit()
        runCurrent()
        assertThat(hidden.workspace.installed).isEmpty()
    }

    @Test fun lockChangingWhileFinalRecoveryCheckReturnsSuppressesPlacement() = runTest {
        val f = Fixture()
        f.source.resolve = { listOf(a) }
        var gates = 0
        f.beforeGate = {
            gates++
            if (gates == 4) f.settings.state.value = f.settings.state.value.let {
                it.copy(home = it.home.copy(layoutLocked = true))
            }
        }
        start(f)
        f.source.emit()
        runCurrent()
        assertThat(gates).isEqualTo(4)
        assertThat(f.workspace.installed).isEmpty()
    }

    @Test fun providerFailureDoesNotStopSubsequentInstallEvents() = runTest {
        val f = Fixture()
        var fail = true
        f.source.resolve = { if (fail) { fail = false; throw SecurityException("Synthetic provider failure") } else listOf(a) }
        start(f)
        f.source.emit()
        runCurrent()
        f.source.emit()
        runCurrent()
        assertThat(f.workspace.installed).containsExactly(a)
    }

    @Test fun cancellationUnregistersAndIgnoresLateCallbacks() = runTest {
        val f = Fixture()
        f.source.resolve = { listOf(a) }
        start(f)
        val late = f.source.callbacks.single()
        backgroundScope.cancel()
        runCurrent()
        assertThat(f.source.unregistrations).isEqualTo(1)
        late("sample")
        runCurrent()
        assertThat(f.workspace.installed).isEmpty()
    }
}
