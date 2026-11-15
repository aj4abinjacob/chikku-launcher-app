package io.github.aj4abinjacob.chikku.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.CellPlacement
import io.github.aj4abinjacob.chikku.core.model.ContainerType
import io.github.aj4abinjacob.chikku.core.model.EditResult
import io.github.aj4abinjacob.chikku.core.model.GridSpec
import io.github.aj4abinjacob.chikku.core.model.ItemTarget
import io.github.aj4abinjacob.chikku.core.model.RejectReason
import io.github.aj4abinjacob.chikku.core.model.WorkspaceContent
import io.github.aj4abinjacob.chikku.core.model.WorkspaceItem
import io.github.aj4abinjacob.chikku.core.model.WorkspaceLimits
import io.github.aj4abinjacob.chikku.core.model.backup.BackupCodec
import io.github.aj4abinjacob.chikku.core.model.backup.BackupDecodeResult
import io.github.aj4abinjacob.chikku.core.model.backup.ChikkuBackup
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettingsCodec
import io.github.aj4abinjacob.chikku.core.model.settings.SettingsImportResult
import io.github.aj4abinjacob.chikku.core.model.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PageEditRecoveryAuditTest {
    private lateinit var db: ChikkuDatabase
    private lateinit var settings: TestSettings
    private lateinit var repo: RoomWorkspaceRepository
    private var blocked = false

    @Before fun setup() {
        db =
            Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext<Context>(),
                ChikkuDatabase::class.java,
            )
                .allowMainThreadQueries().build()
        settings = TestSettings()
        repo = repository()
    }

    @After fun close() = db.close()
    private fun repository() = RoomWorkspaceRepository(
        db,
        Dispatchers.IO,
        pageSettings = settings,
        onPageRecoveryRequired = { blocked = true },
    )
    private suspend fun threePages() {
        repo.addPage()
        repo.addPage()
        settings.select(2)
    }

    @Test fun removalAndUndoRestoreDefaultWithExactSnapshot() = runTest {
        threePages()
        assertThat(repo.removePage(0)).isInstanceOf(EditResult.Applied::class.java)
        assertThat(settings.selected).isEqualTo(1)
        assertThat(repo.undoLastEdit()).isTrue()
        assertThat(settings.selected).isEqualTo(2)
        assertThat(repo.exportContent()!!.pageCount).isEqualTo(3)
    }

    @Test fun removingSelectedLastPageClampsAndUndoRestoresSelection() = runTest {
        threePages()
        assertThat(repo.removePage(2)).isInstanceOf(EditResult.Applied::class.java)
        assertThat(settings.selected).isEqualTo(1)
        assertThat(repo.undoLastEdit()).isTrue()
        assertThat(settings.selected).isEqualTo(2)
    }

    @Test fun explicitLaterSelectionWinsOverCapturedDefault() = runTest {
        threePages()
        repo.removePage(0)
        settings.select(0)
        assertThat(repo.undoLastEdit()).isTrue()
        assertThat(settings.selected).isEqualTo(0)
    }

    @Test fun settingsFailureRestoresPagesAndUndoStack() = runTest {
        threePages()
        val snapshots = db.workspaceDao().retainedSnapshots()
        settings.failNext = true
        assertThat(repo.removePage(0)).isEqualTo(EditResult.Rejected(RejectReason.STORAGE_ERROR))
        assertThat(repo.exportContent()!!.pageCount).isEqualTo(3)
        assertThat(settings.selected).isEqualTo(2)
        assertThat(db.workspaceDao().retainedSnapshots()).isEqualTo(snapshots)
        assertThat(db.workspaceDao().meta("pendingPageEdit")).isNull()
    }

    @Test fun uncertainSettingsWriteRollsBackBothStores() = runTest {
        threePages()
        settings.writeThenFail = true
        assertThat(repo.removePage(0)).isEqualTo(EditResult.Rejected(RejectReason.STORAGE_ERROR))
        assertThat(repo.exportContent()!!.pageCount).isEqualTo(3)
        assertThat(settings.selected).isEqualTo(2)
        assertThat(db.workspaceDao().meta("pendingPageEdit")).isNull()
    }

    @Test fun failedUndoRetainsSnapshotAndCanBeRetried() = runTest {
        threePages()
        repo.removePage(0)
        val snapshots = db.workspaceDao().retainedSnapshots()
        settings.failNext = true
        assertThat(repo.undoLastEdit()).isFalse()
        assertThat(repo.exportContent()!!.pageCount).isEqualTo(2)
        assertThat(settings.selected).isEqualTo(1)
        assertThat(db.workspaceDao().retainedSnapshots()).isEqualTo(snapshots)
        assertThat(repo.undoLastEdit()).isTrue()
        assertThat(settings.selected).isEqualTo(2)
    }

    @Test fun failedRecoverySurvivesRecreationAndBlocksUntilRetried() = runTest {
        threePages()
        val snapshots = db.workspaceDao().retainedSnapshots()
        settings.failAlways = true
        assertThat(repo.removePage(0)).isEqualTo(EditResult.Rejected(RejectReason.STORAGE_ERROR))
        assertThat(blocked).isTrue()
        assertThat(db.workspaceDao().meta("pendingPageEdit")).isNotNull()
        settings.failAlways = false
        repo = repository()
        assertThat(repo.recoverPendingPageEdit()).isTrue()
        assertThat(repo.exportContent()!!.pageCount).isEqualTo(3)
        assertThat(settings.selected).isEqualTo(2)
        assertThat(db.workspaceDao().retainedSnapshots()).isEqualTo(snapshots)
    }

    @Test fun restartRollsBackCrashAfterBothStoresChangedBeforeCommitMarkerCleared() = runTest {
        threePages()
        val dao = db.workspaceDao()
        val before = SnapshotPayload(3, dao.items(), dao.folderItems())
        val snapshots = org.json.JSONArray()
        dao.retainedSnapshots().forEach {
            snapshots.put(
                org.json.JSONObject()
                    .put(
                        "id",
                        it.id,
                    ).put("createdAt", it.createdAt).put("reason", it.reason).put("payload", it.payloadJson),
            )
        }
        val record = org.json.JSONObject().put("version", 1)
            .put("workspace", WorkspaceSnapshotCodec.encode(before)).put("snapshots", snapshots)
            .put("defaultBefore", 2).put("defaultAfter", 1)
        repo.removePage(0)
        // Same persisted point as process death after settings commit, before deleting the marker.
        dao.putMeta(WorkspaceMetaEntity("pendingPageEdit", record.toString()))
        repo = repository()
        assertThat(repo.recoverPendingPageEdit()).isTrue()
        assertThat(repo.exportContent()!!.pageCount).isEqualTo(3)
        assertThat(settings.selected).isEqualTo(2)
        assertThat(dao.snapshotCount()).isEqualTo(2)
        assertThat(repo.recoverPendingPageEdit()).isTrue()
    }

    @Test fun sixtyFiveAcceptedPagesExportAndDecode() = runTest {
        repeat(64) { assertThat(repo.addPage()).isEqualTo(it + 1) }
        val content = repo.exportContent()!!
        val archive = ChikkuBackup(0, "audit", LauncherSettings(), content, emptyList(), emptyList())
        assertThat((BackupCodec.decode(BackupCodec.encode(archive)) as BackupDecodeResult.Success).backup.workspace)
            .isEqualTo(content)
    }

    @Test fun automaticPlacementGrowsPastSixtyFourAndRemainsPortable() = runTest {
        val grid = GridSpec(1, 1)
        repo =
            RoomWorkspaceRepository(
                db,
                Dispatchers.IO,
                MutableStateFlow(WorkspaceGrids(grid, grid)),
                pageSettings = settings,
            )
        val items = (0 until 64).map { page ->
            WorkspaceItem(
                "item$page",
                CellPlacement(ContainerType.DESKTOP, page, 0, 0),
                ItemTarget.App(AppKey("synthetic.p$page", "Main", 0)),
            )
        }
        assertThat(repo.replaceAll(WorkspaceContent(64, items)))
            .isInstanceOf(EditResult.Applied::class.java)
        assertThat(repo.addApp(AppKey("synthetic.extra", "Main", 0), null))
            .isInstanceOf(EditResult.Applied::class.java)
        val content = repo.exportContent()!!
        assertThat(content.pageCount).isEqualTo(65)
        val archive = ChikkuBackup(0, "audit", LauncherSettings(), content, emptyList(), emptyList())
        assertThat((BackupCodec.decode(BackupCodec.encode(archive)) as BackupDecodeResult.Success).backup.workspace)
            .isEqualTo(content)
    }

    @Test fun supportedLimitRejectsGrowthAndPublicImportButPreservesPrivateRecovery() = runTest {
        val grid = GridSpec(1, 1)
        repo =
            RoomWorkspaceRepository(
                db,
                Dispatchers.IO,
                MutableStateFlow(WorkspaceGrids(grid, grid)),
                pageSettings = settings,
            )
        val items = (0 until WorkspaceLimits.MAX_PAGES).map { page ->
            WorkspaceItem(
                "item$page",
                CellPlacement(ContainerType.DESKTOP, page, 0, 0),
                ItemTarget.App(AppKey("synthetic.p$page", "Main", 0)),
            )
        }
        val full = WorkspaceContent(WorkspaceLimits.MAX_PAGES, items)
        assertThat(repo.replaceAll(full)).isInstanceOf(EditResult.Applied::class.java)
        assertThat(repo.addPage()).isEqualTo(-1)
        assertThat(repo.addApp(AppKey("synthetic.extra", "Main", 0), null))
            .isEqualTo(EditResult.Rejected(RejectReason.NO_SPACE))
        assertThat(repo.exportContent()).isEqualTo(full)
        val historical = full.copy(pageCount = WorkspaceLimits.MAX_PAGES + 1)
        assertThat(repo.replaceAll(historical)).isEqualTo(EditResult.Rejected(RejectReason.OUT_OF_BOUNDS))
        assertThat(repo.recoverContent(historical)).isInstanceOf(EditResult.Applied::class.java)
        assertThat(repo.exportContent()).isEqualTo(historical)
    }

    @Test fun resetUndoUsesPrivateRecoveryForHistoricalOverLimitLayout() = runTest {
        val historical = WorkspaceContent(WorkspaceLimits.MAX_PAGES + 1, emptyList())
        assertThat(repo.recoverContent(historical)).isInstanceOf(EditResult.Applied::class.java)
        val service = io.github.aj4abinjacob.chikku.core.model.backup.BackupService(
            settings,
            repo,
            RoomAppCustomizationRepository(db, Dispatchers.IO),
            RoomDrawerGroupRepository(db, Dispatchers.IO),
            "audit",
        )
        assertThat(service.resetToDefault(emptyList()) { "synthetic-id" })
            .isEqualTo(io.github.aj4abinjacob.chikku.core.model.backup.BackupOperationResult.Applied)
        assertThat(repo.exportContent()!!.pageCount).isEqualTo(1)
        assertThat(service.undo()).isTrue()
        assertThat(repo.exportContent()).isEqualTo(historical)
    }

    @Test fun hiddenRecoveryDoesNotGrowBeyondSupportedLimitOrDiscardItems() = runTest {
        val grid = GridSpec(1, 1)
        val items = (0 until WorkspaceLimits.MAX_PAGES).map { page ->
            WorkspaceItem(
                "item$page",
                CellPlacement(ContainerType.DESKTOP, page, 0, 0),
                ItemTarget.App(AppKey("synthetic.p$page", "Main", 0)),
            )
        } + WorkspaceItem(
            "hidden",
            CellPlacement(ContainerType.DESKTOP, 0, 2, 0),
            ItemTarget.App(AppKey("synthetic.hidden", "Main", 0)),
        )
        val workspace = io.github.aj4abinjacob.chikku.core.model.Workspace(grid, grid, WorkspaceLimits.MAX_PAGES, items)
        val plan = io.github.aj4abinjacob.chikku.core.model.layout.HiddenLayoutRecovery.plan(workspace)
        assertThat(plan.content.items).isEqualTo(items)
        assertThat(plan.content.pageCount).isEqualTo(WorkspaceLimits.MAX_PAGES)
        assertThat(plan.result.moved).isEqualTo(0)
        assertThat(plan.result.remaining).isEqualTo(1)
    }

    private class TestSettings : SettingsRepository {
        override val settings = MutableStateFlow(LauncherSettings())
        var failNext = false
        var failAlways = false
        var writeThenFail = false
        val selected get() = settings.value.home.defaultPage
        suspend fun select(page: Int) = update { it.copy(home = it.home.copy(defaultPage = page)) }
        override suspend fun update(transform: (LauncherSettings) -> LauncherSettings) {
            if (failAlways || failNext) {
                failNext = false
                throw IOException("Synthetic failure")
            }
            settings.value = transform(settings.value)
            if (writeThenFail) {
                writeThenFail = false
                throw IOException("Synthetic uncertain write")
            }
        }
        override suspend fun reset() {
            settings.value = LauncherSettings()
        }
        override suspend fun exportJson() = LauncherSettingsCodec.encode(settings.value)
        override suspend fun importJson(json: String): SettingsImportResult {
            settings.value = LauncherSettingsCodec.decode(json)
            return SettingsImportResult.Applied
        }
    }
}
