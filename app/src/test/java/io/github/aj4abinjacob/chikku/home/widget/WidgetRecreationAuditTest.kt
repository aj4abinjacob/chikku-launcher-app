package io.github.aj4abinjacob.chikku.home.widget

import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.CellPlacement
import io.github.aj4abinjacob.chikku.core.model.ComponentKey
import io.github.aj4abinjacob.chikku.core.model.EditResult
import io.github.aj4abinjacob.chikku.core.model.GridSpec
import io.github.aj4abinjacob.chikku.core.model.Workspace
import io.github.aj4abinjacob.chikku.core.model.WorkspaceRepository
import io.github.aj4abinjacob.chikku.core.model.WorkspaceState
import io.github.aj4abinjacob.chikku.home.FakeWorkspaceRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WidgetRecreationAuditTest {
    @Test fun recreatedHomeSweepWaitsForOldControllersAllocationCommit() = runTest {
        val coordinator = WidgetOperationCoordinator()
        val commit = CompletableDeferred<Unit>()
        val referenced = mutableSetOf<Int>()
        val allocated = mutableSetOf<Int>()
        val provider = ComponentKey("example", "Widget", 0)
        val base =
            FakeWorkspaceRepository(WorkspaceState.Ready(Workspace(GridSpec(4, 4), GridSpec(4, 1), 1, emptyList())))
        val repository = object : WorkspaceRepository by base {
            override suspend fun addWidget(
                appWidgetId: Int,
                provider: ComponentKey,
                placement: CellPlacement,
                allowOverlap: Boolean,
            ): EditResult {
                commit.await()
                referenced += appWidgetId
                return EditResult.Applied("widget")
            }
        }
        val platform = object : WidgetPlatform {
            override fun allocateId(): Int = 42.also { allocated += it }
            override fun deleteId(appWidgetId: Int) {
                allocated -= appWidgetId
            }
            override fun bindIfAllowed(appWidgetId: Int, provider: ComponentKey) = true
            override fun boundProvider(appWidgetId: Int) = provider
            override fun needsConfiguration(appWidgetId: Int) = false
            override fun hostIds() = allocated.toIntArray()
        }
        val oldFlow = WidgetBindFlow(platform, repository)
        val recreatedFlow = WidgetBindFlow(platform, repository)
        launch {
            coordinator.step(null) { oldFlow.begin(WidgetRequest.Add(provider, 1, 1, 0)) }
        }
        runCurrent()
        var swept = false
        launch {
            coordinator.sweep { reserved ->
                recreatedFlow.orphanIds(referenced, reserved).forEach(platform::deleteId)
                swept =
                    true
            }
        }
        runCurrent()
        assertThat(swept).isFalse()
        commit.complete(Unit)
        runCurrent()
        assertThat(swept).isTrue()
        assertThat(allocated).containsExactly(42)
    }

    @Test fun pendingConsentAndRestoredConfigurationStayReservedUntilTerminalStep() = runTest {
        val coordinator = WidgetOperationCoordinator()
        val pending = PendingWidget(7, WidgetRequest.Add(ComponentKey("example", "Widget", 0), 1, 1, 0))
        coordinator.step(null) { WidgetStep.NeedsConsent(pending) }
        coordinator.reserveRestored(8)
        coordinator.sweep { assertThat(it).containsExactly(7, 8) }
        coordinator.step(pending) { WidgetStep.NeedsConfiguration(pending) }
        coordinator.sweep { assertThat(it).containsExactly(7, 8) }
        coordinator.step(pending) { WidgetStep.Done(WidgetOutcome.Cancelled) }
        coordinator.sweep { assertThat(it).containsExactly(8) }
    }
}
