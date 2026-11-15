package io.github.aj4abinjacob.chikku.acceptance

import android.os.Bundle
import android.os.Process
import android.content.Intent
import android.view.WindowManager
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.ChikkuApplication
import io.github.aj4abinjacob.chikku.DeviceTestSupport
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.home.homeDependencies
import io.github.aj4abinjacob.chikku.backup.BackupMessage
import io.github.aj4abinjacob.chikku.backup.BackupViewModel
import io.github.aj4abinjacob.chikku.core.model.*
import io.github.aj4abinjacob.chikku.core.model.backup.*
import io.github.aj4abinjacob.chikku.core.model.settings.*
import io.github.aj4abinjacob.chikku.data.restore.FileRestoreJournal
import io.github.aj4abinjacob.chikku.data.restore.RecoveryWorkspace
import io.github.aj4abinjacob.chikku.platform.AndroidDiscoverHealthChecker
import io.github.aj4abinjacob.chikku.platform.AndroidRestoreEnvironment
import io.github.aj4abinjacob.chikku.settings.SettingsActivity
import io.github.aj4abinjacob.chikku.settings.SettingsSection
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Test
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith

/** Opt-in production-store fixtures for scripts/run-upgrade-restore.py only.
 * SAF picker and genuine widget consent/rebind are separate acceptance, not proved by these tests.
 */
@RunWith(AndroidJUnit4::class)
class UpgradeRestoreAcceptanceDeviceTest {
    private fun AcceptanceStores.journal() = FileRestoreJournal({ context.noBackupFilesDir }, Dispatchers.IO)
    private fun AcceptanceStores.service() = BackupService(settings, workspace, custom, groups,
        "cloud-acceptance", journal = journal())
    private fun expectedWidgetId() = InstrumentationRegistry.getArguments().getString("expectedWidgetId")!!.toInt()

    @Test fun verifyRetainedProductionState(): Unit = runBlocking {
        AcceptanceStores().use { stores ->
            assertThat(stores.service().prepare()).isTrue()
            stores.assertOriginalState(expectedWidgetId())
            assertThat(stores.settings.settings.first().search.historyLearningEnabled).isFalse()
            withContext(Dispatchers.IO) {
                stores.database.openHelper.writableDatabase.query("SELECT widgetPadding FROM workspace_items").use { cursor ->
                    while (cursor.moveToNext()) assertThat(cursor.isNull(0)).isTrue()
                }
            }
        }
    }

    @Test fun exportProductionBackup(): Unit = runBlocking {
        AcceptanceStores().use { stores ->
            stores.assertOriginalState(7301)
            val backup = (stores.service().create() as BackupCreateResult.Success).backup
            val encoded = BackupCodec.encode(backup)
            val decoded = (BackupCodec.decode(encoded) as BackupDecodeResult.Success).backup
            assertThat(decoded.workspace).isEqualTo(originalAcceptanceWorkspace(0))
            assertThat(decoded.summary(emptySet()).missingApps).isEqualTo(1)
            assertThat(decoded.summary(emptySet()).widgets).isEqualTo(1)
            assertThat(decoded.summary(emptySet()).shortcuts).isEqualTo(1)
            withContext(Dispatchers.IO) {
                val destination = File(checkNotNull(stores.context.getExternalFilesDir(null)), "cloud-acceptance-backup.json")
                FileOutputStream(destination).use { stream ->
                    stream.write(encoded.toByteArray(Charsets.UTF_8))
                    stream.fd.sync()
                }
            }
        }
    }

    @Test fun restoreFreshProductionBackup(): Unit = runBlocking {
        AcceptanceStores().use { stores ->
            assertThat(stores.workspace.exportContent()?.items).isEmpty()
            assertThat(stores.custom.all()).isEmpty()
            assertThat(stores.groups.all()).isEmpty()
            val path = "/data/local/tmp/${stores.context.packageName}-restore.json"
            val encoded = withContext(Dispatchers.IO) {
                // The ID is strictly guarded before constructing this shell command; only this
                // synthetic host-pushed file is read. No arbitrary path is accepted as an argument.
                InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("cat $path").use { descriptor ->
                    FileInputStream(descriptor.fileDescriptor).use { input ->
                        val bytes = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            require(bytes.size() + count <= BackupCodec.MAX_BYTES)
                            bytes.write(buffer, 0, count)
                        }
                        bytes.toByteArray().toString(Charsets.UTF_8)
                    }
                }
            }
            val backup = (BackupCodec.decode(encoded) as BackupDecodeResult.Success).backup
            assertThat(stores.service().restore(backup)).isEqualTo(BackupOperationResult.Applied)
            stores.workspace.seedDefaultsOnce(emptyList())
            stores.assertOriginalState(0)
            val environment = AndroidRestoreEnvironment(stores.context,
                AndroidDiscoverHealthChecker(stores.context, Dispatchers.IO), Dispatchers.IO)
            val checks = RestoreChecks.run(backup.settings, environment, listOf(RestoreChecks.iconPack, RestoreChecks.dockImage))
            assertThat(checks).containsExactly(RestoreAttention.ICON_PACK_MISSING, RestoreAttention.DOCK_IMAGE_UNREADABLE)
            // Dependency failures explain restored values; they must not silently reset them.
            stores.assertOriginalState(0)
        }
    }

    @Test fun applyReplacementForDurableUndo(): Unit = runBlocking {
        AcceptanceStores().use { stores ->
            stores.assertOriginalState(0)
            val service = stores.service()
            val saved = (service.create() as BackupCreateResult.Success).backup
            val replacement = saved.copy(settings = saved.settings.copy(home = saved.settings.home.copy(desktopColumns = 4)),
                workspace = WorkspaceContent(3, emptyList()), customizations = emptyList(), drawerGroups = emptyList())
            assertThat(service.restore(replacement)).isEqualTo(BackupOperationResult.Applied)
            assertThat(service.canUndo).isTrue()
            assertThat(stores.workspace.exportContent()).isEqualTo(WorkspaceContent(3, emptyList()))
        }
    }

    @Test fun verifyDurableUndoAfterRestart(): Unit = runBlocking {
        AcceptanceStores().use { stores ->
            val service = stores.service()
            assertThat(service.prepare()).isTrue()
            assertThat(service.canUndo).isTrue()
            assertThat(service.undo()).isTrue()
            stores.assertOriginalState(0)
            assertThat(service.canUndo).isFalse()
        }
    }

    @Test fun rejectMalformedAndFutureArchivesWithoutWrites(): Unit = runBlocking {
        AcceptanceStores().use { stores ->
            stores.assertOriginalState(0)
            val settingsBefore = stores.settings.exportJson()
            val workspaceBefore = stores.workspace.exportContent()
            val customBefore = stores.custom.all()
            val groupsBefore = stores.groups.all()
            val journalBefore = stores.journal().read()
            val encoded = BackupCodec.encode((stores.service().create() as BackupCreateResult.Success).backup)
            val future = encoded.replaceFirst("\"formatVersion\":1", "\"formatVersion\":999")
            require(future != encoded)
            for (invalid in listOf("{", "{}", future, encoded.take(encoded.length / 2))) {
                assertThat(BackupCodec.decode(invalid)).isInstanceOf(BackupDecodeResult.Failure::class.java)
            }
            assertThat(stores.settings.exportJson()).isEqualTo(settingsBefore)
            assertThat(stores.workspace.exportContent()).isEqualTo(workspaceBefore)
            assertThat(stores.custom.all()).isEqualTo(customBefore)
            assertThat(stores.groups.all()).isEqualTo(groupsBefore)
            assertThat(stores.journal().read()).isEqualTo(journalBefore)
        }
    }

    /** Actual Android CreateDocument/OpenDocument UI, deliberately independent of shell transport. */
    @Test fun safDocumentPickerRoundTrip(): Unit = runBlocking {
        val context = guardedAcceptanceContext()
        assumeTrue("System DocumentsUI fixture requires English labels", context.resources.configuration.locales[0].language == "en")
        val picker = context.packageManager.resolveActivity(Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE).setType("application/json"), 0)?.activityInfo?.packageName
        assumeTrue("No native SAF document picker is available", picker != null)
        val pickerPackage = checkNotNull(picker)
        AcceptanceStores().use { it.assertOriginalState(0) }
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val fileName = "${context.packageName.substringAfterLast('.')}-saf-${UUID.randomUUID()}.json"
        var stage = "initial"
        var expectedResource = 0
        var expectedSelector: BySelector? = null
        var backupViewModel: BackupViewModel? = null
        fun recordDiagnostics() {
            val state = backupViewModel?.state?.value
            instrumentation.addResults(Bundle().apply {
                // Fixed fixture stages/resource IDs and booleans only: no document names,
                // inventories, hierarchy dumps, labels or arbitrary package names.
                putString("chikkuSafStage", stage)
                putInt("chikkuSafExpectedResourceId", expectedResource)
                putInt("chikkuSafExpectedControlCount", expectedSelector?.let { device.findObjects(it).size } ?: 0)
                putBoolean("chikkuSafActivePackageIsOwned", device.currentPackageName == context.packageName)
                putBoolean("chikkuSafActivePackageIsPicker", device.currentPackageName == pickerPackage)
                putBoolean("chikkuSafPlatformAnrClosePresent", device.hasObject(By.pkg("android").res("android:id/aerr_close")))
                putBoolean("chikkuSafPlatformAnrWaitPresent", device.hasObject(By.pkg("android").res("android:id/aerr_wait")))
                putBoolean("chikkuSafProducerAvailable", state != null)
                putBoolean("chikkuSafProducerWorking", state?.working == true)
                putBoolean("chikkuSafProducerSaved", state?.message == BackupMessage.SAVED)
                putBoolean("chikkuSafProducerSaveFailed", state?.message == BackupMessage.SAVE_FAILED)
                putBoolean("chikkuSafProducerRestored", state?.message == BackupMessage.RESTORED)
                putBoolean("chikkuSafProducerRestoreFailed", state?.message == BackupMessage.RESTORE_FAILED)
            }) // Final-result extras never fabricate passing test events.
        }
        fun at(name: String, selector: BySelector? = null, resource: Int = 0) {
            stage = name
            expectedSelector = selector
            expectedResource = resource
        }
        fun passedStage() = instrumentation.addResults(Bundle().apply { putBoolean("chikkuSaf_${stage}_completed", true) })
        fun find(selector: BySelector, name: String, resource: Int = 0): UiObject2 {
            at(name, selector, resource)
            val node = device.wait(Until.findObject(selector), DeviceTestSupport.LOAD_TIMEOUT_MS)
            if (node == null) {
                recordDiagnostics()
                error("Required named document/settings control was not exposed at SAF stage $stage")
            }
            passedStage()
            return node
        }
        fun text(resource: Int, name: String) = find(By.text(context.getString(resource)).pkg(context.packageName), name, resource)
        fun settingsText(resource: Int, name: String, direction: Direction): UiObject2 {
            val selector = By.text(context.getString(resource)).pkg(context.packageName)
            at(name, selector, resource)
            check(DeviceTestSupport.waitUntil(DeviceTestSupport.LOAD_TIMEOUT_MS) {
                device.hasObject(selector) || device.hasObject(By.pkg(context.packageName).scrollable(true))
            }) { "Backup settings content did not appear at SAF stage $stage" }
            repeat(8) {
                val node = device.findObject(selector)
                val scroller = device.findObjects(By.pkg(context.packageName).scrollable(true)).lastOrNull {
                    it.visibleBounds.width() > 0 && it.visibleBounds.height() > 0
                }
                val bounds = node?.visibleBounds
                val viewport = scroller?.visibleBounds
                val margin = (8 * context.resources.displayMetrics.density).toInt()
                if (bounds != null && bounds.width() > 0 && bounds.height() > 0 &&
                    (viewport == null || (bounds.top >= viewport.top + margin && bounds.bottom <= viewport.bottom - margin))) {
                    passedStage()
                    return checkNotNull(node)
                }
                if (scroller == null) return find(selector, name, resource)
                scroller.scroll(direction, 0.7f) // Real accessible viewport; no coordinate fallback.
            }
            recordDiagnostics()
            error("Named backup settings control was not visible after semantic scrolling at SAF stage $stage")
        }
        fun clickable(node: UiObject2): UiObject2 {
            var action = node
            while (!action.isClickable && action.parent != null) action = checkNotNull(action.parent)
            check(action.isEnabled && action.isClickable) { "Named SAF control is not enabled and clickable at stage $stage" }
            return action
        }
        fun awaitProducer(message: BackupMessage, name: String) {
            at(name)
            val failures = if (message == BackupMessage.SAVED) setOf(BackupMessage.SAVE_FAILED)
                else setOf(BackupMessage.RESTORE_FAILED, BackupMessage.RECOVERY_REQUIRED)
            check(DeviceTestSupport.waitUntil(DeviceTestSupport.LOAD_TIMEOUT_MS) {
                backupViewModel?.state?.value?.let { !it.working && (it.message == message || it.message in failures) } == true
            }) { "Backup producer did not finish at SAF stage $stage" }
            check(backupViewModel?.state?.value?.message == message) { "Backup producer did not report success at SAF stage $stage" }
            passedStage()
        }
        fun selectDownloads() {
            at("picker_roots")
            fun tapFresh(selector: BySelector, name: String) {
                repeat(3) {
                    device.waitForIdle()
                    try {
                        clickable(find(selector, name)).click()
                        return
                    } catch (_: StaleObjectException) {
                        // DocumentsUI replaces drawer nodes during its transition. Re-query the
                        // same semantic control; never reuse a stale node or fall back to coordinates.
                    }
                }
                error("Document picker control stayed stale at $name")
            }
            val list = By.res(pickerPackage, "roots_list")
            if (!device.hasObject(list)) {
                val roots = By.desc("Show roots").pkg(pickerPackage)
                if (device.wait(Until.findObject(roots), 3_000) == null) {
                    // A permanently pinned Downloads view need not expose a drawer button.
                    assertThat(device.hasObject(By.text("Downloads").pkg(pickerPackage))).isTrue()
                    return
                }
                tapFresh(roots, "picker_roots")
            }
            // Toolbar and breadcrumb labels can also say Downloads. Select the actual root row.
            tapFresh(By.res("android:id/title").text("Downloads").pkg(pickerPackage), "picker_downloads")
        }
        val intent = Intent(context, SettingsActivity::class.java)
            .putExtra(SettingsActivity.EXTRA_SECTION, SettingsSection.BACKUP.name)
        ActivityScenario.launch<SettingsActivity>(intent).use { scenario ->
            fun settingsReady(name: String) {
                at(name)
                val frames = CountDownLatch(1)
                scenario.onActivity { activity ->
                    activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    val decor = activity.window.decorView
                    decor.postOnAnimation { decor.postOnAnimation { frames.countDown() } }
                }
                val drawn = frames.await(30, TimeUnit.SECONDS)
                var focused = false
                val ready = DeviceTestSupport.waitUntil(DeviceTestSupport.LOAD_TIMEOUT_MS) {
                    scenario.onActivity { activity ->
                        val decor = activity.window.decorView
                        focused = decor.hasWindowFocus()
                        focused = focused && decor.isAttachedToWindow && decor.isShown && decor.width > 0 && decor.height > 0 &&
                            !activity.isFinishing && !activity.isDestroyed
                    }
                    drawn && focused && device.currentPackageName == context.packageName
                }
                instrumentation.addResults(Bundle().apply {
                    putBoolean("chikkuSafTwoFramesDrawn", drawn)
                    putBoolean("chikkuSafSettingsDecorReady", focused)
                    putBoolean("chikkuSafSettingsReady", ready)
                })
                check(ready) { "Backup settings did not reach a drawn, focused Activity at SAF stage $stage" }
                passedStage()
            }
            try {
                settingsReady("initial_settings_ready")
                val save = settingsText(R.string.backup_save, "create_document_action", Direction.UP)
                scenario.onActivity { activity -> backupViewModel = ViewModelProvider(activity, BackupViewModel.factory(activity, activity.homeDependencies()))[BackupViewModel::class.java] }
                clickable(save).click()
                selectDownloads()
                find(By.clazz("android.widget.EditText").pkg(pickerPackage), "picker_filename").text = fileName
                find(By.text(Pattern.compile("(?i)save")).pkg(pickerPackage), "picker_save").click()
                settingsReady("save_return_ready")
                awaitProducer(BackupMessage.SAVED, "save_producer_completed")
                settingsText(R.string.backup_msg_saved, "save_visible_status", Direction.UP)

                // Use the live production service so its observed caches stay consistent with disk.
                val container = (context.applicationContext as ChikkuApplication).container
                val saved = (container.backupService.create() as BackupCreateResult.Success).backup
                val replacement = saved.copy(settings = saved.settings.copy(home = saved.settings.home.copy(desktopColumns = 4)),
                    workspace = WorkspaceContent(3, emptyList()), customizations = emptyList(), drawerGroups = emptyList())
                assertThat(container.backupService.restore(replacement)).isEqualTo(BackupOperationResult.Applied)

                fun openSavedDocument() {
                    clickable(settingsText(R.string.backup_restore, "open_document_action", Direction.DOWN)).click()
                    selectDownloads()
                    find(By.text(fileName).pkg(pickerPackage), "picker_saved_document").click()
                    text(R.string.backup_preview_title, "preview_visible_title")
                }
                openSavedDocument()
                clickable(text(R.string.backup_preview_cancel, "preview_cancel")).click()
                settingsReady("cancel_return_ready")
                assertThat(container.workspaceRepository.exportContent()).isEqualTo(WorkspaceContent(3, emptyList()))
                assertThat(container.settingsRepository.settings.first().home.desktopColumns).isEqualTo(4)
                openSavedDocument()
                clickable(text(R.string.backup_preview_confirm, "preview_confirm")).click()
                awaitProducer(BackupMessage.RESTORED, "restore_producer_completed")
                settingsReady("restore_return_ready")
                settingsText(R.string.backup_msg_restored, "restore_visible_status", Direction.UP)
                AcceptanceStores().use { stores -> stores.assertOriginalState(0) }
            } catch (failure: Throwable) {
                runCatching { recordDiagnostics() } // Diagnostics cannot replace the original failure.
                throw failure
            }
        }
    }

    @Test fun pauseProductionRestoreForExternalKill(): Unit = runBlocking {
        AcceptanceStores().use { stores ->
            stores.assertOriginalState(0)
            val stage = InstrumentationRegistry.getArguments().getString("cutAfterStore")!!.toInt()
            require(stage in 0..3)
            val saved = (stores.service().create() as BackupCreateResult.Success).backup
            val incoming = saved.copy(settings = saved.settings.copy(home = saved.settings.home.copy(desktopColumns = 4)),
                workspace = WorkspaceContent(3, emptyList()), customizations = emptyList(), drawerGroups = emptyList())
            suspend fun pause(): Nothing {
                withContext(Dispatchers.IO) {
                    FileOutputStream(File(stores.context.noBackupFilesDir, "cloud-acceptance-cut.marker")).use { stream ->
                        stream.write("${Process.myPid()}:$stage".toByteArray(Charsets.UTF_8))
                        stream.fd.sync()
                    }
                }
                // A progress status is readable from host instrumentation output even when the
                // optimized acceptance app is nondebuggable; no run-as or exported helper needed.
                InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply {
                    putString("chikkuCutMarker", "${Process.myPid()}:$stage")
                })
                awaitCancellation()
            }
            val settings = object : SettingsRepository by stores.settings {
                override suspend fun importJson(json: String): SettingsImportResult {
                    val result = stores.settings.importJson(json)
                    assertThat(result).isEqualTo(SettingsImportResult.Applied)
                    if (stage == 0) pause()
                    return result
                }
            }
            val custom = object : AppCustomizationRepository by stores.custom {
                override suspend fun replaceAll(customizations: List<AppCustomization>): Boolean {
                    val result = stores.custom.replaceAll(customizations)
                    assertThat(result).isTrue()
                    if (stage == 1) pause()
                    return result
                }
            }
            val groups = object : DrawerGroupRepository by stores.groups {
                override suspend fun replaceAll(groups: List<DrawerGroup>): Boolean {
                    val result = stores.groups.replaceAll(groups)
                    assertThat(result).isTrue()
                    if (stage == 2) pause()
                    return result
                }
            }
            val workspace = object : WorkspaceRepository by stores.workspace {
                override suspend fun replaceAll(content: WorkspaceContent): EditResult {
                    val result = stores.workspace.replaceAll(content)
                    assertThat(result).isInstanceOf(EditResult.Applied::class.java)
                    if (stage == 3) pause()
                    return result
                }
            }
            BackupService(settings, workspace, custom, groups, "cloud-acceptance", journal = stores.journal()).restore(incoming)
            error("External-kill preparation unexpectedly completed")
        }
    }

    @Test fun recoverProductionRestoreInNewProcess(): Unit = runBlocking {
        AcceptanceStores().use { stores ->
            val marker = withContext(Dispatchers.IO) {
                File(stores.context.noBackupFilesDir, "cloud-acceptance-cut.marker").readText().split(':')
            }
            val expectedStage = InstrumentationRegistry.getArguments().getString("cutAfterStore")!!.toInt()
            require(expectedStage in 0..3 && marker.size == 2)
            assertThat(marker[1].toInt()).isEqualTo(expectedStage)
            assertThat(Process.myPid()).isNotEqualTo(marker[0].toInt())
            assertThat(stores.journal().read()!!.committed).isFalse()
            lateinit var service: BackupService
            val access = RestoreAccess { service.recoverPending() }
            service = BackupService(stores.settings, stores.workspace, stores.custom, stores.groups,
                "cloud-acceptance", journal = stores.journal(), access = access)
            val first = RecoveryWorkspace(stores.workspace, access).workspace.first { it !is WorkspaceState.Loading }
            assertThat(first).isInstanceOf(WorkspaceState.Ready::class.java)
            stores.assertOriginalState(0)
            assertThat(stores.journal().read()).isNull()
            assertThat(service.canUndo).isFalse()
        }
    }
}
