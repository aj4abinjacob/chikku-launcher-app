package io.github.aj4abinjacob.chikku.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.CellPlacement
import io.github.aj4abinjacob.chikku.core.model.ContainerType.DESKTOP
import io.github.aj4abinjacob.chikku.core.model.EditResult
import io.github.aj4abinjacob.chikku.core.model.ItemTarget
import io.github.aj4abinjacob.chikku.core.model.RejectReason
import io.github.aj4abinjacob.chikku.core.model.Workspace
import io.github.aj4abinjacob.chikku.core.model.WorkspaceState
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

/** Removing desktop pages: only empty ones, later pages shift down, undo restores. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RoomWorkspaceRepositoryPagesTest {
    private lateinit var db: ChikkuDatabase
    private lateinit var repo: RoomWorkspaceRepository
    private var now = 1_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ChikkuDatabase::class.java).allowMainThreadQueries().build()
        repo = RoomWorkspaceRepository(db, Dispatchers.IO, MutableStateFlow(WorkspaceGrids.DEFAULT), clock = { now++ })
    }

    @After
    fun tearDown() = db.close()

    private fun key(n: Int) = AppKey("com.example.app$n", "com.example.app$n.Main", 0)
    private fun on(page: Int) = CellPlacement(DESKTOP, page, 0, 0)

    private suspend fun ready(): Workspace =
        (repo.workspace.first { it is WorkspaceState.Ready } as WorkspaceState.Ready).workspace

    @Test
    fun removingAnEmptyMiddlePageShiftsLaterPagesDownAndUndoRestores() = runTest {
        repo.addApp(key(1), on(0))
        repo.addPage()
        repo.addPage()
        repo.addApp(key(2), on(2))
        assertThat(ready().pageCount).isEqualTo(3)

        assertThat(repo.removePage(1)).isInstanceOf(EditResult.Applied::class.java)
        val after = ready()
        assertThat(after.pageCount).isEqualTo(2)
        assertThat(after.items.single { it.target == ItemTarget.App(key(2)) }.placement.page).isEqualTo(1)

        assertThat(repo.undoLastEdit()).isTrue()
        val restored = ready()
        assertThat(restored.pageCount).isEqualTo(3)
        assertThat(restored.items.single { it.target == ItemTarget.App(key(2)) }.placement.page).isEqualTo(2)
    }

    @Test
    fun pagesWithItemsAndTheLastPageAreNeverRemoved() = runTest {
        repo.addApp(key(1), on(0))
        assertThat(repo.removePage(0)).isEqualTo(EditResult.Rejected(RejectReason.INVALID))
        repo.addPage()
        assertThat(repo.removePage(0)).isEqualTo(EditResult.Rejected(RejectReason.OCCUPIED))
        assertThat(repo.removePage(5)).isEqualTo(EditResult.Rejected(RejectReason.INVALID))
        assertThat(repo.removePage(1)).isInstanceOf(EditResult.Applied::class.java)
        assertThat(ready().pageCount).isEqualTo(1)
        assertThat(ready().items).hasSize(1)
    }

    @Test
    fun aConfirmedRemovalDeletesTheItemsOnThePageAndOneUndoRestoresEverything() = runTest {
        repo.addApp(key(1), on(0))
        repo.addPage()
        repo.addApp(key(2), on(1))
        repo.addPage()
        repo.addApp(key(3), on(2))

        assertThat(repo.removePage(1)).isEqualTo(EditResult.Rejected(RejectReason.OCCUPIED))
        assertThat(repo.removePage(1, deleteItems = true)).isInstanceOf(EditResult.Applied::class.java)
        val after = ready()
        assertThat(after.pageCount).isEqualTo(2)
        assertThat(after.items.map { it.target }).containsExactly(ItemTarget.App(key(1)), ItemTarget.App(key(3)))
        assertThat(after.items.single { it.target == ItemTarget.App(key(3)) }.placement.page).isEqualTo(1)

        assertThat(repo.undoLastEdit()).isTrue()
        val restored = ready()
        assertThat(restored.pageCount).isEqualTo(3)
        assertThat(restored.items).hasSize(3)
        assertThat(restored.items.single { it.target == ItemTarget.App(key(2)) }.placement.page).isEqualTo(1)
    }

    @Test
    fun theOnlyPageIsNeverRemovedEvenWhenConfirmed() = runTest {
        repo.addApp(key(1), on(0))
        assertThat(repo.removePage(0, deleteItems = true)).isEqualTo(EditResult.Rejected(RejectReason.INVALID))
        assertThat(ready().items).hasSize(1)
    }
}
