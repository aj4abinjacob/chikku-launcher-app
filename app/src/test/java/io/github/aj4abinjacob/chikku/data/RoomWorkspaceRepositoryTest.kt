package io.github.aj4abinjacob.chikku.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.CellPlacement
import io.github.aj4abinjacob.chikku.core.model.ContainerType.DESKTOP
import io.github.aj4abinjacob.chikku.core.model.ContainerType.DOCK
import io.github.aj4abinjacob.chikku.core.model.EditResult
import io.github.aj4abinjacob.chikku.core.model.RejectReason
import io.github.aj4abinjacob.chikku.core.model.Workspace
import io.github.aj4abinjacob.chikku.core.model.WorkspaceState
import io.github.aj4abinjacob.chikku.core.model.layout.WorkspaceLayoutEngine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RoomWorkspaceRepositoryTest {
    private lateinit var db: ChikkuDatabase
    private lateinit var repo: RoomWorkspaceRepository
    private var now = 1_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ChikkuDatabase::class.java).allowMainThreadQueries().build()
        repo = RoomWorkspaceRepository(db, Dispatchers.IO, clock = { now++ })
    }

    @After
    fun tearDown() = db.close()

    private fun key(n: Int) = AppKey("com.example.app$n", "com.example.app$n.Main", 0)

    private suspend fun ready(): Workspace =
        (repo.workspace.first { it is WorkspaceState.Ready } as WorkspaceState.Ready).workspace

    private suspend fun placementOf(id: String) = ready().items.single { it.id == id }.placement

    private fun EditResult.id() = (this as EditResult.Applied).itemId

    @Test
    fun workspaceEmitsLoadingThenEmptyReady() = runTest {
        assertThat(repo.workspace.first()).isEqualTo(WorkspaceState.Loading)
        val workspace = ready()
        assertThat(workspace.items).isEmpty()
        assertThat(workspace.pageCount).isEqualTo(1)
    }

    @Test
    fun addWithoutPreferenceUsesFirstFreeDesktopCell() = runTest {
        val first = repo.addApp(key(1), null).id()
        val second = repo.addApp(key(2), null).id()
        assertThat(placementOf(first)).isEqualTo(CellPlacement(DESKTOP, 0, 0, 0))
        assertThat(placementOf(second)).isEqualTo(CellPlacement(DESKTOP, 0, 1, 0))
    }

    @Test
    fun addAtOccupiedPreferenceFallsBackToNearestFreeCell() = runTest {
        repo.addApp(key(1), CellPlacement(DESKTOP, 0, 2, 2))
        val id = repo.addApp(key(2), CellPlacement(DESKTOP, 0, 2, 2)).id()
        assertThat(placementOf(id)).isEqualTo(CellPlacement(DESKTOP, 0, 2, 1))
    }

    @Test
    fun addAppendsPageWhenAllPagesAreFull() = runTest {
        repeat(25) { repo.addApp(key(it), null) }
        assertThat(ready().pageCount).isEqualTo(1)
        val id = repo.addApp(key(25), null).id()
        val workspace = ready()
        assertThat(workspace.pageCount).isEqualTo(2)
        assertThat(placementOf(id)).isEqualTo(CellPlacement(DESKTOP, 1, 0, 0))
        assertThat(WorkspaceLayoutEngine.findOverlaps(workspace.items)).isEmpty()
    }

    @Test
    fun duplicateAppIsRejectedWithoutChanges() = runTest {
        repo.addApp(key(1), null)
        val before = ready()
        val snapshots = db.workspaceDao().snapshotCount()
        assertThat(repo.addApp(key(1), CellPlacement(DOCK, 0, 0, 0))).isEqualTo(EditResult.Rejected(RejectReason.DUPLICATE))
        assertThat(ready()).isEqualTo(before)
        assertThat(db.workspaceDao().snapshotCount()).isEqualTo(snapshots)
    }

    @Test
    fun sameComponentInAnotherProfileIsNotADuplicate() = runTest {
        repo.addApp(key(1), null)
        assertThat(repo.addApp(key(1).copy(userSerial = 10), null)).isInstanceOf(EditResult.Applied::class.java)
    }

    @Test
    fun addOutOfBoundsIsRejected() = runTest {
        assertThat(repo.addApp(key(1), CellPlacement(DESKTOP, 0, 5, 0)))
            .isEqualTo(EditResult.Rejected(RejectReason.OUT_OF_BOUNDS))
        assertThat(repo.addApp(key(1), CellPlacement(DESKTOP, 1, 0, 0)))
            .isEqualTo(EditResult.Rejected(RejectReason.OUT_OF_BOUNDS))
        assertThat(repo.addApp(key(1), CellPlacement(DOCK, 0, 0, 1)))
            .isEqualTo(EditResult.Rejected(RejectReason.OUT_OF_BOUNDS))
        assertThat(ready().items).isEmpty()
    }

    @Test
    fun addToFullDockIsRejectedWithNoSpace() = runTest {
        repeat(5) { repo.addApp(key(it), CellPlacement(DOCK, 0, it, 0)) }
        assertThat(repo.addApp(key(9), CellPlacement(DOCK, 0, 0, 0)))
            .isEqualTo(EditResult.Rejected(RejectReason.NO_SPACE))
    }

    @Test
    fun moveAppliesAndKeepsStableId() = runTest {
        val id = repo.addApp(key(1), null).id()
        assertThat(repo.moveItem(id, CellPlacement(DOCK, 0, 3, 0))).isEqualTo(EditResult.Applied(id))
        assertThat(placementOf(id)).isEqualTo(CellPlacement(DOCK, 0, 3, 0))
    }

    @Test
    fun moveIntoOccupiedOrInvalidTargetIsRejectedWithoutChanges() = runTest {
        val a = repo.addApp(key(1), null).id()
        repo.addApp(key(2), CellPlacement(DESKTOP, 0, 3, 3))
        val before = ready()
        assertThat(repo.moveItem(a, CellPlacement(DESKTOP, 0, 3, 3))).isEqualTo(EditResult.Rejected(RejectReason.OCCUPIED))
        assertThat(repo.moveItem(a, CellPlacement(DESKTOP, 0, 2, 2, spanX = 2, spanY = 2)))
            .isEqualTo(EditResult.Rejected(RejectReason.OCCUPIED))
        assertThat(repo.moveItem(a, CellPlacement(DESKTOP, 0, 4, 0, spanX = 2)))
            .isEqualTo(EditResult.Rejected(RejectReason.OUT_OF_BOUNDS))
        assertThat(repo.moveItem(a, CellPlacement(DESKTOP, 3, 0, 0))).isEqualTo(EditResult.Rejected(RejectReason.OUT_OF_BOUNDS))
        assertThat(repo.moveItem("missing", CellPlacement(DESKTOP, 0, 1, 1)))
            .isEqualTo(EditResult.Rejected(RejectReason.UNKNOWN_ITEM))
        assertThat(ready()).isEqualTo(before)
    }

    @Test
    fun moveOntoItsOwnCellsIsAllowed() = runTest {
        val id = repo.addApp(key(1), CellPlacement(DESKTOP, 0, 0, 0, spanX = 2)).id()
        assertThat(repo.moveItem(id, CellPlacement(DESKTOP, 0, 1, 0, spanX = 2))).isEqualTo(EditResult.Applied(id))
    }

    @Test
    fun removeThenUndoRestoresItemAndConsumesSnapshot() = runTest {
        val id = repo.addApp(key(1), CellPlacement(DESKTOP, 0, 2, 2)).id()
        val before = ready()
        assertThat(repo.removeItem(id)).isEqualTo(EditResult.Applied(id))
        assertThat(ready().items).isEmpty()

        assertThat(repo.undoLastEdit()).isTrue()
        assertThat(ready()).isEqualTo(before)
        // The next undo reverts the add, then there is nothing left.
        assertThat(repo.undoLastEdit()).isTrue()
        assertThat(ready().items).isEmpty()
        assertThat(repo.undoLastEdit()).isFalse()
        assertThat(repo.removeItem(id)).isEqualTo(EditResult.Rejected(RejectReason.UNKNOWN_ITEM))
    }

    @Test
    fun undoRevertsMoveAndPageAdditions() = runTest {
        val id = repo.addApp(key(1), null).id()
        repo.moveItem(id, CellPlacement(DESKTOP, 0, 4, 4))
        assertThat(repo.addPage()).isEqualTo(1)
        assertThat(repo.addPage()).isEqualTo(2)
        assertThat(ready().pageCount).isEqualTo(3)

        repo.undoLastEdit()
        repo.undoLastEdit()
        assertThat(ready().pageCount).isEqualTo(1)
        repo.undoLastEdit()
        assertThat(placementOf(id)).isEqualTo(CellPlacement(DESKTOP, 0, 0, 0))
    }

    @Test
    fun malformedSnapshotIsKeptAndUndoChangesNothing() = runTest {
        repo.addApp(key(1), null)
        db.workspaceDao().insertSnapshot(WorkspaceSnapshotEntity(createdAt = 0, reason = "test", payloadJson = "{bad"))
        val before = ready()
        assertThat(repo.undoLastEdit()).isFalse()
        assertThat(ready()).isEqualTo(before)
        assertThat(db.workspaceDao().latestSnapshot()!!.payloadJson).isEqualTo("{bad")
    }

    @Test
    fun snapshotsAreBounded() = runTest {
        repeat(15) { repo.addApp(key(it), null) }
        assertThat(db.workspaceDao().snapshotCount()).isEqualTo(RoomWorkspaceRepository.MAX_SNAPSHOTS)
        repeat(RoomWorkspaceRepository.MAX_SNAPSHOTS) { assertThat(repo.undoLastEdit()).isTrue() }
        assertThat(repo.undoLastEdit()).isFalse()
        assertThat(ready().items).hasSize(5)
    }

    @Test
    fun seedDefaultsOnceFillsDockLeftToRightExactlyOnce() = runTest {
        val apps = (0 until 7).map(::key)
        assertThat(repo.seedDefaultsOnce(apps)).isTrue()
        val dock = ready().items.filter { it.placement.container == DOCK }.sortedBy { it.placement.x }
        assertThat(dock.map { it.placement.x }).containsExactly(0, 1, 2, 3, 4).inOrder()
        assertThat(dock.map { (it.target as io.github.aj4abinjacob.chikku.core.model.ItemTarget.App).key })
            .containsExactlyElementsIn(apps.take(5)).inOrder()

        dock.forEach { repo.removeItem(it.id) }
        assertThat(repo.seedDefaultsOnce(apps)).isFalse()
        assertThat(ready().items).isEmpty()
    }

    @Test
    fun seedingDoesNotCreateUndoSnapshots() = runTest {
        repo.seedDefaultsOnce(listOf(key(1)))
        assertThat(repo.undoLastEdit()).isFalse()
        assertThat(ready().items).hasSize(1)
    }

    @Test
    fun placementsAreStoredInHalfCellUnits() = runTest {
        val id = repo.addApp(key(1), CellPlacement(DESKTOP, 0, 1, 2, spanX = 2, spanY = 1)).id()
        val row = db.workspaceDao().item(id)!!
        assertThat(listOf(row.x2, row.y2, row.width2, row.height2)).containsExactly(2, 4, 4, 2).inOrder()
        assertThat(row.container).isEqualTo("desktop")
        assertThat(row.kind).isEqualTo("app")
        assertThat(placementOf(id)).isEqualTo(CellPlacement(DESKTOP, 0, 1, 2, spanX = 2, spanY = 1))
    }

    @Test
    fun flowEmitsReadyAfterEdits() = runTest {
        val sawEmpty = CompletableDeferred<Unit>()
        val updated = async(Dispatchers.IO) {
            repo.workspace
                .onEach { if (it is WorkspaceState.Ready && it.workspace.items.isEmpty()) sawEmpty.complete(Unit) }
                .first { it is WorkspaceState.Ready && it.workspace.items.size == 1 }
        }
        sawEmpty.await()
        repo.addApp(key(1), null)
        assertThat((updated.await() as WorkspaceState.Ready).workspace.items.single().placement)
            .isEqualTo(CellPlacement(DESKTOP, 0, 0, 0))
    }

    @Test
    fun unreadableRowsSurfaceAsFailedWithoutLeakingDataAndAreKept() = runTest {
        val bad = newEntity("bad", key(1), CellPlacement(DESKTOP, 0, 0, 0), 0).copy(container = "bogus")
        db.workspaceDao().insertItem(bad)

        val state = repo.workspace.first { it !is WorkspaceState.Loading }
        assertThat(state).isInstanceOf(WorkspaceState.Failed::class.java)
        assertThat((state as WorkspaceState.Failed).reason).doesNotContain("com.example")
        assertThat(repo.addApp(key(2), null)).isEqualTo(EditResult.Rejected(RejectReason.STORAGE_ERROR))
        assertThat(db.workspaceDao().items()).containsExactly(bad)
    }

    @Test
    fun randomEditSequencesNeverProduceOverlaps() = runTest {
        val random = kotlin.random.Random(42)
        val ids = mutableListOf<String>()
        repeat(150) { step ->
            when (random.nextInt(5)) {
                0, 1 -> {
                    val preferred = if (random.nextBoolean()) null else
                        CellPlacement(DESKTOP, 0, random.nextInt(5), random.nextInt(5), random.nextInt(1, 3), random.nextInt(1, 3))
                    (repo.addApp(key(step), preferred) as? EditResult.Applied)?.let { ids += it.itemId }
                }
                2 -> if (ids.isNotEmpty()) repo.moveItem(
                    ids.random(random),
                    CellPlacement(if (random.nextBoolean()) DESKTOP else DOCK, 0, random.nextInt(5), random.nextInt(5)),
                )
                3 -> if (ids.isNotEmpty()) repo.removeItem(ids.random(random))
                else -> repo.undoLastEdit()
            }
            assertThat(WorkspaceLayoutEngine.findOverlaps(ready().items)).isEmpty()
        }
    }
}
