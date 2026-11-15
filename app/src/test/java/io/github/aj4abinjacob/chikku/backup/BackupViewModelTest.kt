package io.github.aj4abinjacob.chikku.backup

import android.net.Uri
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.AppsState
import io.github.aj4abinjacob.chikku.core.model.CellPlacement
import io.github.aj4abinjacob.chikku.core.model.ContainerType
import io.github.aj4abinjacob.chikku.core.model.GridSpec
import io.github.aj4abinjacob.chikku.core.model.ItemTarget
import io.github.aj4abinjacob.chikku.core.model.Workspace
import io.github.aj4abinjacob.chikku.core.model.WorkspaceContent
import io.github.aj4abinjacob.chikku.core.model.WorkspaceItem
import io.github.aj4abinjacob.chikku.core.model.WorkspaceState
import io.github.aj4abinjacob.chikku.core.model.backup.BackupCodec
import io.github.aj4abinjacob.chikku.core.model.backup.BackupService
import io.github.aj4abinjacob.chikku.core.model.backup.ChikkuBackup
import io.github.aj4abinjacob.chikku.core.model.backup.DiscoverReadiness
import io.github.aj4abinjacob.chikku.core.model.backup.RestoreAttention
import io.github.aj4abinjacob.chikku.core.model.backup.RestoreChecks
import io.github.aj4abinjacob.chikku.core.model.backup.RestoreEnvironment
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.home.FakeAppCustomizationRepository
import io.github.aj4abinjacob.chikku.home.FakeAppsRepository
import io.github.aj4abinjacob.chikku.home.FakeDrawerGroupRepository
import io.github.aj4abinjacob.chikku.home.FakeSettingsRepository
import io.github.aj4abinjacob.chikku.home.FakeWorkspaceRepository
import io.github.aj4abinjacob.chikku.home.app
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BackupViewModelTest {
    private val mail = app("Mail")
    private val gone = AppKey("pkg.gone", "pkg.gone.Main", 0)
    private val workspaceRepo = FakeWorkspaceRepository(
        WorkspaceState.Ready(Workspace(GridSpec(5, 5), GridSpec(5, 1), 1, emptyList())),
    )
    private val settings = FakeSettingsRepository()
    private val storage = FakeStorage()
    private val uri: Uri = Uri.parse("content://test/backup.json")

    private val vm = BackupViewModel(
        service = BackupService(settings, workspaceRepo, FakeAppCustomizationRepository(), FakeDrawerGroupRepository(), "t"),
        storage = storage,
        appsRepository = FakeAppsRepository(AppsState.Loaded(listOf(mail))),
        dockApps = { listOf(mail.key) },
        appScope = CoroutineScope(UnconfinedTestDispatcher()),
        fileName = { "chikku-backup-20261010.json" },
    )

    private fun backupWith(vararg keys: AppKey) = BackupCodec.encode(
        ChikkuBackup(
            1L, "t", LauncherSettings(),
            WorkspaceContent(1, keys.mapIndexed { i, k -> WorkspaceItem("i$i", CellPlacement(ContainerType.DESKTOP, 0, i, 0), ItemTarget.App(k)) }),
            emptyList(), emptyList(),
        ),
    )

    @Test
    fun saveWritesValidBackupToChosenDocument() {
        vm.saveTo(uri)
        assertThat(vm.state.value.message).isEqualTo(BackupMessage.SAVED)
        assertThat(BackupCodec.decode(storage.written.getValue(uri))).isInstanceOf(
            io.github.aj4abinjacob.chikku.core.model.backup.BackupDecodeResult.Success::class.java,
        )
    }

    @Test
    fun shareProducesUriForShareSheet() {
        vm.share()
        assertThat(vm.state.value.pendingShare).isEqualTo(Uri.parse("content://share/chikku-backup-20261010.json"))
        vm.shareHandled()
        assertThat(vm.state.value.pendingShare).isNull()
    }

    @Test
    fun damagedOrForeignFilesShowTypedErrorAndChangeNothing() {
        mapOf(
            "garbage" to BackupMessage.NOT_JSON,
            """{"a":1}""" to BackupMessage.NOT_A_BACKUP,
            backupWith(mail.key).replace("\"formatVersion\":1", "\"formatVersion\":7") to BackupMessage.NEWER_VERSION,
            backupWith(mail.key).replace("\"x\":0", "\"x\":-3") to BackupMessage.DAMAGED,
        ).forEach { (text, expected) ->
            storage.toRead = BackupReadResult.Text(text)
            vm.load(uri)
            assertThat(vm.state.value.message).isEqualTo(expected)
            assertThat(vm.state.value.preview).isNull()
        }
        storage.toRead = BackupReadResult.TooLarge
        vm.load(uri)
        assertThat(vm.state.value.message).isEqualTo(BackupMessage.TOO_LARGE)
        assertThat(workspaceRepo.calls).isEmpty()
    }

    @Test
    fun previewCountsMissingAppsAndConfirmRestores() {
        storage.toRead = BackupReadResult.Text(backupWith(mail.key, gone))
        vm.load(uri)
        val preview = vm.state.value.preview!!
        assertThat(preview.summary.items).isEqualTo(2)
        assertThat(preview.summary.missingApps).isEqualTo(1)
        assertThat(workspaceRepo.calls).isEmpty() // nothing applied before confirmation

        vm.confirmRestore()
        assertThat(vm.state.value.message).isEqualTo(BackupMessage.RESTORED)
        assertThat(vm.state.value.canUndo).isTrue()
        assertThat(workspaceRepo.calls).containsExactly("replaceAll")
    }

    @Test
    fun restoredDiscoverWithoutItsCompanionIsKeptAndExplainedOnce() {
        var deviceReadiness = DiscoverReadiness.NO_GOOGLE_APP
        val env = object : RestoreEnvironment {
            override suspend fun discoverReadiness() = deviceReadiness
            override suspend fun installedIconPacks(): Set<String>? = null
            override suspend fun canReadContent(uri: String): Boolean? = null
        }
        val checked = BackupViewModel(
            service = BackupService(settings, workspaceRepo, FakeAppCustomizationRepository(), FakeDrawerGroupRepository(), "t"),
            storage = storage,
            appsRepository = FakeAppsRepository(AppsState.Loaded(listOf(mail))),
            dockApps = { listOf(mail.key) },
            appScope = CoroutineScope(UnconfinedTestDispatcher()),
            restoreChecks = { RestoreChecks.run(it, env) },
        )
        val discoverOn = LauncherSettings().let { it.copy(home = it.home.copy(googleDiscoverEnabled = true)) }
        storage.toRead = BackupReadResult.Text(
            BackupCodec.encode(ChikkuBackup(1L, "t", discoverOn, WorkspaceContent(1, emptyList()), emptyList(), emptyList())),
        )
        checked.load(uri)
        assertThat(checked.state.value.preview!!.attention).containsExactly(RestoreAttention.DISCOVER_NEEDS_GOOGLE_APP)

        // The Google app appeared while the preview was open, but this normal build has no bridge.
        deviceReadiness = DiscoverReadiness.NO_BRIDGE
        checked.confirmRestore()
        assertThat(checked.state.value.restored).isTrue()
        assertThat(checked.state.value.restoredAttention).containsExactly(RestoreAttention.DISCOVER_NEEDS_BRIDGE)
        assertThat(settings.state.value.home.googleDiscoverEnabled).isTrue() // kept, never silently turned off

        checked.attentionShown()
        assertThat(checked.state.value.restoredAttention).isEmpty()
    }

    @Test
    fun withoutRestoreChecksNothingIsFlagged() {
        storage.toRead = BackupReadResult.Text(backupWith(mail.key))
        vm.load(uri)
        assertThat(vm.state.value.preview!!.attention).isEmpty()
        vm.confirmRestore()
        assertThat(vm.state.value.restoredAttention).isEmpty()
    }

    @Test
    fun cancelledPreviewChangesNothing() {
        storage.toRead = BackupReadResult.Text(backupWith(mail.key))
        vm.load(uri)
        vm.cancelPreview()
        assertThat(vm.state.value.preview).isNull()
        assertThat(workspaceRepo.calls).isEmpty()
    }

    @Test
    fun resetUsesDefaultDockAndCanBeUndone() {
        settings.state.value = LauncherSettings().let { it.copy(home = it.home.copy(desktopColumns = 3)) }
        vm.resetToDefault()
        assertThat(vm.state.value.message).isEqualTo(BackupMessage.RESET_DONE)
        assertThat(settings.state.value.home.desktopColumns).isEqualTo(5)
        assertThat(settings.state.value.onboarding.completed).isTrue()
        vm.undo()
        assertThat(vm.state.value.message).isEqualTo(BackupMessage.UNDONE)
        assertThat(settings.state.value.home.desktopColumns).isEqualTo(3)
        assertThat(vm.state.value.canUndo).isFalse()
    }

    private class FakeStorage : BackupStorage {
        val written = mutableMapOf<Uri, String>()
        var toRead: BackupReadResult = BackupReadResult.Failed
        override suspend fun write(uri: Uri, text: String): Boolean {
            written[uri] = text
            return true
        }

        override suspend fun read(uri: Uri): BackupReadResult = toRead
        override suspend fun shareableCopy(fileName: String, text: String): Uri = Uri.parse("content://share/$fileName")
    }
}
