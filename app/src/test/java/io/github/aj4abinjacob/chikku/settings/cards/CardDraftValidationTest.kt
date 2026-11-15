package io.github.aj4abinjacob.chikku.settings.cards

import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.AppCustomization
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.AppsState
import io.github.aj4abinjacob.chikku.core.model.CellPlacement
import io.github.aj4abinjacob.chikku.core.model.ContainerType
import io.github.aj4abinjacob.chikku.core.model.GridSpec
import io.github.aj4abinjacob.chikku.core.model.ItemTarget
import io.github.aj4abinjacob.chikku.core.model.LauncherApp
import io.github.aj4abinjacob.chikku.core.model.ProfileType
import io.github.aj4abinjacob.chikku.core.model.WorkspaceItem
import io.github.aj4abinjacob.chikku.core.model.Workspace
import io.github.aj4abinjacob.chikku.core.model.WorkspaceState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CardDraftValidationTest {
    private val key = AppKey("example.app", "example.app.Main", 0)
    private val initial = CardEditorSnapshot(listOf(LauncherApp(key, "App", ProfileType.PERSONAL)), emptyMap(), emptyList())

    @Test
    fun delayedNativeValidationRejectsHideAppRemovalPlacementChangeAndUnavailableState() = runTest {
        val moved = WorkspaceItem("item", CellPlacement(ContainerType.DESKTOP, 0, 1, 0), ItemTarget.App(key))
        for (changed in listOf(initial.copy(customizations = mapOf(key to AppCustomization(key, hidden = true))),
            initial.copy(apps = emptyList()), initial.copy(items = listOf(moved)), null)) {
            var current: CardEditorSnapshot? = initial
            val gate = CompletableDeferred<Unit>()
            val result = async { validateCardDraftSnapshot({ current }) { gate.await(); true } }
            runCurrent()
            current = changed
            gate.complete(Unit)
            assertThat(result.await()).isFalse()
        }
    }

    @Test
    fun unchangedSnapshotPassesAndUnavailableOrRejectedNativeResultFailsClosed() = runTest {
        assertThat(validateCardDraftSnapshot({ initial }) { true }).isTrue()
        assertThat(validateCardDraftSnapshot({ initial }) { false }).isFalse()
        var called = false
        assertThat(validateCardDraftSnapshot({ null }) { called = true; true }).isFalse()
        assertThat(called).isFalse()
    }

    private val emptyWorkspace = Workspace(GridSpec(4, 5), GridSpec(5, 1), 1, emptyList())

    @Test
    fun coldStreamsThatBeginLoadingYieldComparableSnapshotsBeforeAndAfterValidation() = runTest {
        var appCollections = 0
        var workspaceCollections = 0
        val apps = flow {
            appCollections++
            emit(AppsState.Loading)
            delay(10)
            emit(AppsState.Loaded(initial.apps))
            awaitCancellation()
        }
        val workspace = flow {
            workspaceCollections++
            emit(WorkspaceState.Loading)
            delay(10)
            emit(WorkspaceState.Ready(emptyWorkspace))
            awaitCancellation()
        }
        val accepted = validateCardDraftSnapshot({
            readCardEditorSnapshot(apps, flowOf(emptyMap()), workspace)
        }) { it == initial }
        assertThat(accepted).isTrue()
        assertThat(appCollections).isEqualTo(2)
        assertThat(workspaceCollections).isEqualTo(2)
    }

    @Test
    fun terminalFailureIsRejectedEvenIfFlowWouldLaterEmitReady() = runTest {
        val goodApps = flowOf(AppsState.Loading, AppsState.Loaded(initial.apps))
        val failedWorkspace = flowOf(WorkspaceState.Loading, WorkspaceState.Failed("unavailable"), WorkspaceState.Ready(emptyWorkspace))
        assertThat(readCardEditorSnapshot(goodApps, flowOf(emptyMap()), failedWorkspace)).isNull()
        val failedApps = flowOf(AppsState.Loading, AppsState.Failed("unavailable"), AppsState.Loaded(initial.apps))
        assertThat(readCardEditorSnapshot(failedApps, flowOf(emptyMap()), flowOf(WorkspaceState.Ready(emptyWorkspace)))).isNull()
    }

    @Test
    fun loadingForeverOrEndingBeforeReadyHasBoundedFailure() = runTest {
        val loadingForever = flow<WorkspaceState> { emit(WorkspaceState.Loading); awaitCancellation() }
        assertThat(readCardEditorSnapshot(flowOf(AppsState.Loaded(initial.apps)), flowOf(emptyMap()), loadingForever, timeoutMs = 100)).isNull()
        assertThat(testScheduler.currentTime).isEqualTo(100L)
        assertThat(readCardEditorSnapshot(flowOf(AppsState.Loading), flowOf(emptyMap()), flowOf(WorkspaceState.Ready(emptyWorkspace)))).isNull()
    }
}
