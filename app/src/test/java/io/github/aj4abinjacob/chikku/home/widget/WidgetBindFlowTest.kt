package io.github.aj4abinjacob.chikku.home.widget

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.CellPlacement
import io.github.aj4abinjacob.chikku.core.model.ComponentKey
import io.github.aj4abinjacob.chikku.core.model.ContainerType.DESKTOP
import io.github.aj4abinjacob.chikku.core.model.ItemTarget
import io.github.aj4abinjacob.chikku.core.model.RejectReason
import io.github.aj4abinjacob.chikku.core.model.Workspace
import io.github.aj4abinjacob.chikku.core.model.WorkspaceContent
import io.github.aj4abinjacob.chikku.core.model.WorkspaceItem
import io.github.aj4abinjacob.chikku.core.model.WorkspaceState
import io.github.aj4abinjacob.chikku.core.model.WorkspaceRepository
import io.github.aj4abinjacob.chikku.core.model.EditResult
import kotlinx.coroutines.CancellationException
import io.github.aj4abinjacob.chikku.core.model.layout.WorkspaceLayoutEngine
import io.github.aj4abinjacob.chikku.data.ChikkuDatabase
import io.github.aj4abinjacob.chikku.data.RoomWorkspaceRepository
import io.github.aj4abinjacob.chikku.data.WorkspaceGrids
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The bind state machine against the real Room repository and a fake AppWidget service. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WidgetBindFlowTest {
    private lateinit var db: ChikkuDatabase
    private lateinit var repo: RoomWorkspaceRepository
    private val platform = FakeWidgetPlatform()
    private lateinit var flow: WidgetBindFlow
    private val clock = ComponentKey("com.clock", "com.clock.Widget", 0)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ChikkuDatabase::class.java).allowMainThreadQueries().build()
        repo = RoomWorkspaceRepository(db, Dispatchers.IO, MutableStateFlow(WorkspaceGrids.DEFAULT), clock = { 1L })
        flow = WidgetBindFlow(platform, repo)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun ready(): Workspace =
        (repo.workspace.first { it is WorkspaceState.Ready } as WorkspaceState.Ready).workspace

    @Test
    fun allowedBindWithoutConfigurationPlacesAtFreeSpot() = runTest {
        repo.addApp(AppKey("com.a", "com.a.Main", 0), CellPlacement(DESKTOP, 0, 0, 0))
        platform.allowBind = true

        val step = flow.begin(WidgetRequest.Add(clock, 2, 2, page = 0))

        val outcome = (step as WidgetStep.Done).outcome as WidgetOutcome.Added
        val widget = ready().items.single { it.id == outcome.itemId }
        assertThat(widget.target).isEqualTo(ItemTarget.Widget(1, clock))
        assertThat(widget.placement).isEqualTo(CellPlacement(DESKTOP, 0, 1, 0, 2, 2))
        assertThat(platform.deleted).isEmpty()
    }

    @Test
    fun consentThenConfigurationThenPlacement() = runTest {
        platform.configure = true
        val consent = flow.begin(WidgetRequest.Add(clock, 1, 1, 0)) as WidgetStep.NeedsConsent
        platform.bound[consent.pending.appWidgetId] = clock // the system dialog bound it

        val configure = flow.onConsentResult(consent.pending, granted = true) as WidgetStep.NeedsConfiguration
        val done = flow.onConfigurationResult(configure.pending, ok = true) as WidgetStep.Done

        assertThat(done.outcome).isInstanceOf(WidgetOutcome.Added::class.java)
        assertThat(ready().items.single().target).isEqualTo(ItemTarget.Widget(1, clock))
    }

    @Test
    fun declinedConsentOrCancelledConfigurationReleasesTheId() = runTest {
        val consent = flow.begin(WidgetRequest.Add(clock, 1, 1, 0)) as WidgetStep.NeedsConsent
        assertThat(flow.onConsentResult(consent.pending, granted = false))
            .isEqualTo(WidgetStep.Done(WidgetOutcome.Cancelled))
        assertThat(platform.deleted).containsExactly(1)

        platform.allowBind = true
        platform.configure = true
        val configure = flow.begin(WidgetRequest.Add(clock, 1, 1, 0)) as WidgetStep.NeedsConfiguration
        assertThat(flow.onConfigurationResult(configure.pending, ok = false))
            .isEqualTo(WidgetStep.Done(WidgetOutcome.Cancelled))
        assertThat(platform.deleted).containsExactly(1, 2)
        assertThat(ready().items).isEmpty()
    }

    @Test
    fun grantedConsentThatDidNotBindIsTreatedAsCancel() = runTest {
        val consent = flow.begin(WidgetRequest.Add(clock, 1, 1, 0)) as WidgetStep.NeedsConsent
        assertThat(flow.onConsentResult(consent.pending, granted = true))
            .isEqualTo(WidgetStep.Done(WidgetOutcome.Cancelled))
        assertThat(platform.deleted).containsExactly(1)
    }

    @Test
    fun droppedWidgetLandsAtTheDropCellOrTheNearestFreeOne() = runTest {
        repo.addApp(AppKey("com.a", "com.a.Main", 0), CellPlacement(DESKTOP, 0, 2, 3))
        platform.allowBind = true

        val free = CellPlacement(DESKTOP, 0, 1, 1, 2, 2)
        val first = (flow.begin(WidgetRequest.Add(clock, 2, 2, 0, at = free)) as WidgetStep.Done).outcome as WidgetOutcome.Added
        assertThat(ready().items.single { it.id == first.itemId }.placement).isEqualTo(free)

        // Dropped over the app at (2, 3): placed on a nearby free spot of the same page instead.
        val blocked = CellPlacement(DESKTOP, 0, 2, 3, 2, 2)
        val second = (flow.begin(WidgetRequest.Add(clock, 2, 2, 0, at = blocked)) as WidgetStep.Done).outcome as WidgetOutcome.Added
        val placed = ready().items.single { it.id == second.itemId }.placement
        assertThat(placed.page).isEqualTo(0)
        assertThat(placed).isNotEqualTo(blocked)
        assertThat(ready().items.filter { it.id != second.itemId }.none { WorkspaceLayoutEngine.overlaps(it.placement, placed) }).isTrue()
    }

    @Test
    fun fullWorkspaceAddsAPage() = runTest {
        repo.replaceAll(
            WorkspaceContent(
                1,
                (0 until 5).flatMap { y ->
                    (0 until 5).map { x ->
                        WorkspaceItem("w$x$y", CellPlacement(DESKTOP, 0, x, y), ItemTarget.Widget(0, clock))
                    }
                },
            ),
        )
        platform.allowBind = true
        val outcome = (flow.begin(WidgetRequest.Add(clock, 2, 1, 0)) as WidgetStep.Done).outcome as WidgetOutcome.Added
        val ws = ready()
        assertThat(ws.pageCount).isEqualTo(2)
        assertThat(ws.items.single { it.id == outcome.itemId }.placement).isEqualTo(CellPlacement(DESKTOP, 1, 0, 0, 2, 1))
    }

    @Test
    fun rebindUsesFreshIdKeepsPlacementAndReleasesOldId() = runTest {
        // A restored placement whose id belongs to another device (or 0).
        val itemId = (repo.addWidget(42, clock, CellPlacement(DESKTOP, 0, 1, 1, 2, 2)) as
            io.github.aj4abinjacob.chikku.core.model.EditResult.Applied).itemId
        platform.allowBind = true
        platform.nextId = 100

        val done = flow.begin(WidgetRequest.Rebind(itemId, 42, clock)) as WidgetStep.Done

        assertThat(done.outcome).isEqualTo(WidgetOutcome.Rebound(itemId))
        val item = ready().items.single()
        assertThat(item.id).isEqualTo(itemId)
        assertThat(item.placement).isEqualTo(CellPlacement(DESKTOP, 0, 1, 1, 2, 2))
        assertThat(item.target).isEqualTo(ItemTarget.Widget(100, clock))
        assertThat(platform.deleted).containsExactly(42)
    }

    @Test
    fun rebindOfIdZeroDeletesNothingButTheFailedNewId() = runTest {
        platform.allowBind = true
        val done = flow.begin(WidgetRequest.Rebind("missing-item", 0, clock)) as WidgetStep.Done
        assertThat(done.outcome).isEqualTo(WidgetOutcome.Rejected(RejectReason.UNKNOWN_ITEM))
        assertThat(platform.deleted).containsExactly(1) // only the newly allocated id
    }

    @Test
    fun orphanIdsExcludeReferencedAndKeptIds() = runTest {
        platform.hostIds = intArrayOf(3, 4, 5, 6)
        val referenced = WidgetBindFlow.referencedIds(
            sequenceOf(ItemTarget.Widget(3, clock), ItemTarget.Widget(0, clock), ItemTarget.App(AppKey("a", "a.B", 0))),
        )
        assertThat(referenced).containsExactly(3)
        assertThat(flow.orphanIds(referenced, keep = setOf(5))).containsExactly(4, 6)
    }

    @Test fun configurationCannotCommitAfterProviderLossOrChangedConsentBinding() = runTest {
        platform.allowBind = true
        platform.configure = true
        val pending = (flow.begin(WidgetRequest.Add(clock, 1, 1, 0)) as WidgetStep.NeedsConfiguration).pending
        platform.bound.remove(pending.appWidgetId)
        assertThat(flow.onConfigurationResult(pending, true)).isEqualTo(WidgetStep.Done(WidgetOutcome.Cancelled))
        assertThat(ready().items).isEmpty()
        platform.allowBind = false
        val consent = (flow.begin(WidgetRequest.Add(clock, 1, 1, 0)) as WidgetStep.NeedsConsent).pending
        platform.bound[consent.appWidgetId] = ComponentKey("other", "Other", 0)
        assertThat(flow.onConsentResult(consent, true)).isEqualTo(WidgetStep.Done(WidgetOutcome.Cancelled))
        assertThat(ready().items).isEmpty()
        assertThat(platform.deleted).containsExactly(pending.appWidgetId, consent.appWidgetId)
    }

    @Test fun cancellationAfterDatabaseCommitKeepsPersistedWidgetId() = runTest {
        platform.allowBind = true
        val uncertain = object : WorkspaceRepository by repo {
            override suspend fun addWidget(appWidgetId: Int, provider: ComponentKey, placement: CellPlacement, allowOverlap: Boolean): EditResult {
                repo.addWidget(appWidgetId, provider, placement, allowOverlap)
                throw CancellationException("Injected after commit")
            }
        }
        try { WidgetBindFlow(platform, uncertain).begin(WidgetRequest.Add(clock, 1, 1, 0)); error("Expected cancellation") }
        catch (_: CancellationException) { }
        assertThat((ready().items.single().target as ItemTarget.Widget).appWidgetId).isEqualTo(1)
        assertThat(platform.deleted).isEmpty()
    }

    @Test fun lateConfigurationCancellationCannotDestroyAnAlreadyPersistedBinding() = runTest {
        platform.allowBind = true
        platform.configure = true
        val pending = (flow.begin(WidgetRequest.Add(clock, 1, 1, 0)) as WidgetStep.NeedsConfiguration).pending
        assertThat(flow.onConfigurationResult(pending, true)).isInstanceOf(WidgetStep.Done::class.java)
        flow.cancel(pending)
        assertThat(platform.deleted).isEmpty()
        assertThat((ready().items.single().target as ItemTarget.Widget).appWidgetId).isEqualTo(pending.appWidgetId)
        val itemId = ready().items.single().id
        repo.updateWidgetId(itemId, 99)
        assertThat(repo.retainedWidgetIds()).contains(pending.appWidgetId)
        flow.cancel(pending)
        assertThat(platform.deleted).isEmpty()
        assertThat(repo.undoLastEdit()).isTrue()
        assertThat(ready().items.single().target).isEqualTo(ItemTarget.Widget(pending.appWidgetId, clock))
    }

    @Test fun deferredRebindRetirementKeepsNormalUndoBindingAndRejectsStaleRebind() = runTest {
        val itemId = (repo.addWidget(42, clock, CellPlacement(DESKTOP, 0, 0, 0)) as EditResult.Applied).itemId
        platform.allowBind = true
        val retired = mutableListOf<Int>()
        val deferred = WidgetBindFlow(platform, repo) { retired += it }
        assertThat(deferred.begin(WidgetRequest.Rebind(itemId, 42, clock))).isEqualTo(WidgetStep.Done(WidgetOutcome.Rebound(itemId)))
        assertThat(retired).containsExactly(42)
        assertThat(platform.deleted).isEmpty()
        assertThat(repo.undoLastEdit()).isTrue()
        assertThat(ready().items.single().target).isEqualTo(ItemTarget.Widget(42, clock))
        platform.configure = true
        val pending = (deferred.begin(WidgetRequest.Rebind(itemId, 42, clock)) as WidgetStep.NeedsConfiguration).pending
        repo.updateWidgetId(itemId, 99)
        assertThat(deferred.onConfigurationResult(pending, true)).isEqualTo(WidgetStep.Done(WidgetOutcome.Cancelled))
        assertThat(ready().items.single().target).isEqualTo(ItemTarget.Widget(99, clock))
    }

    @Test fun editBetweenRebindPreflightAndCommitCannotReplaceTheNewerBinding() = runTest {
        val itemId = (repo.addWidget(42, clock, CellPlacement(DESKTOP, 0, 0, 0)) as EditResult.Applied).itemId
        platform.allowBind = true
        val racing = object : WorkspaceRepository by repo {
            override suspend fun rebindWidget(itemId: String, expected: ItemTarget.Widget, appWidgetId: Int): EditResult {
                repo.updateWidgetId(itemId, 99)
                val snapshot = db.workspaceDao().latestSnapshot()
                val result = repo.rebindWidget(itemId, expected, appWidgetId)
                assertThat(db.workspaceDao().latestSnapshot()).isEqualTo(snapshot)
                return result
            }
        }
        val result = WidgetBindFlow(platform, racing).begin(WidgetRequest.Rebind(itemId, 42, clock))
        assertThat(result).isEqualTo(WidgetStep.Done(WidgetOutcome.Rejected(RejectReason.INVALID)))
        assertThat(ready().items.single().target).isEqualTo(ItemTarget.Widget(99, clock))
        assertThat(platform.deleted).containsExactly(1)
    }

    private class FakeWidgetPlatform : WidgetPlatform {
        var nextId = 1
        var allowBind = false
        var configure = false
        val bound = HashMap<Int, ComponentKey>()
        val deleted = mutableListOf<Int>()
        var hostIds = IntArray(0)

        override fun allocateId(): Int = nextId++
        override fun deleteId(appWidgetId: Int) {
            deleted += appWidgetId
            bound.remove(appWidgetId)
        }

        override fun bindIfAllowed(appWidgetId: Int, provider: ComponentKey): Boolean {
            if (allowBind) bound[appWidgetId] = provider
            return allowBind
        }

        override fun boundProvider(appWidgetId: Int): ComponentKey? = bound[appWidgetId]
        override fun needsConfiguration(appWidgetId: Int): Boolean = configure
        override fun hostIds(): IntArray = hostIds
    }
}
