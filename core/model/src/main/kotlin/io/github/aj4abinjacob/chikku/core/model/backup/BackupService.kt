package io.github.aj4abinjacob.chikku.core.model.backup

import io.github.aj4abinjacob.chikku.core.model.AppCustomizationRepository
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.DrawerGroupRepository
import io.github.aj4abinjacob.chikku.core.model.EditResult
import io.github.aj4abinjacob.chikku.core.model.ItemTarget
import io.github.aj4abinjacob.chikku.core.model.WorkspaceRepository
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettingsCodec
import io.github.aj4abinjacob.chikku.core.model.settings.OnboardingSettings
import io.github.aj4abinjacob.chikku.core.model.settings.SettingsImportResult
import io.github.aj4abinjacob.chikku.core.model.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** A failure either restores the previous state or explicitly requires recovery before access. */
sealed interface BackupOperationResult {
    data object Applied : BackupOperationResult

    /** A failed rollback is explicit; the durable journal remains available for retry. */
    data class Failed(val step: Step, val recoveryRequired: Boolean = false) : BackupOperationResult

    enum class Step { READ_CURRENT, SETTINGS, CUSTOMIZATIONS, DRAWER_GROUPS, WORKSPACE, JOURNAL, RECOVERY }
}

sealed interface BackupCreateResult {
    data class Success(val backup: ChikkuBackup) : BackupCreateResult
    data class Failed(val step: BackupOperationResult.Step) : BackupCreateResult
}

/**
 * Serializes restore/reset/undo with repository access. The previous state is persisted before
 * any write, and a durable commit marker is written last. An interrupted operation rolls back
 * before stores become available on the next start. Failed recovery keeps the journal intact.
 */
class BackupService(
    private val settings: SettingsRepository,
    private val workspace: WorkspaceRepository,
    private val customizations: AppCustomizationRepository,
    private val drawerGroups: DrawerGroupRepository,
    private val appVersion: String,
    private val clock: () -> Long = System::currentTimeMillis,
    private val journal: RestoreJournal = MemoryRestoreJournal(),
    private val access: RestoreAccess = RestoreAccess(),
) {
    @Volatile private var undoState: RestoreCheckpoint? = null

    /** Called by the shared access gate before any production repository reads or writes. */
    suspend fun recoverPending(): Boolean = try {
        val record = journal.read()
        if (record == null) {
            undoState = null
            true
        } else if (record.committed) {
            undoState = record.previous.takeIf { record.retainUndo }
            true
        } else {
            undoState = null
            val restored = restorePrevious(record.previous)
            restored && journal.clear()
        }
    } catch (e: CancellationException) { throw e }
    catch (_: Exception) { false }

    suspend fun prepare(): Boolean = access.run(false) {
        recoverPending().also { if (!it) access.requireRecovery() }
    }

    /** True after a restore or reset whose previous state can be brought back with [undo]. */
    val canUndo: Boolean get() = undoState != null

    /** Widget cleanup shares the restore lock and retains IDs needed by durable local undo. */
    suspend fun withWidgetReferences(block: suspend (Set<Int>) -> Unit): Boolean = access.run(false) {
        val current = workspace.exportContent() ?: return@run false
        val editUndoIds = workspace.retainedWidgetIds() ?: return@run false
        val record = try { journal.read() } catch (e: CancellationException) { throw e }
        catch (_: Exception) { return@run false }
        val previous = record?.takeIf { !it.committed || it.retainUndo }?.previous?.workspace
        val ids = (current.items + previous?.items.orEmpty()).mapNotNull {
            (it.target as? ItemTarget.Widget)?.appWidgetId?.takeIf { id -> id != 0 }
        }.toSet() + editUndoIds
        block(ids)
        true
    }

    suspend fun create(): BackupCreateResult = access.run<BackupCreateResult>(BackupCreateResult.Failed(BackupOperationResult.Step.RECOVERY)) {
        if (!recoverPending()) {
            access.requireRecovery()
            return@run BackupCreateResult.Failed(BackupOperationResult.Step.RECOVERY)
        }
        val current = readCurrent() ?: return@run BackupCreateResult.Failed(BackupOperationResult.Step.READ_CURRENT)
        val decodedSettings = LauncherSettingsCodec.decodeOrNull(current.settingsJson)
            ?: return@run BackupCreateResult.Failed(BackupOperationResult.Step.SETTINGS)
        BackupCreateResult.Success(
            ChikkuBackup(
                createdAtMillis = clock(),
                appVersion = appVersion,
                settings = decodedSettings,
                workspace = current.workspace,
                customizations = current.customizations,
                drawerGroups = current.groups,
            ).withoutWidgetIds(),
        )
    }

    /**
     * Replaces everything with [backup]. Widgets come back as placements with id 0 (they need to be
     * re-bound with fresh ids); apps that are not installed stay as placeholders. Onboarding is
     * marked complete so a restored device does not re-run first-run setup.
     */
    suspend fun restore(backup: ChikkuBackup): BackupOperationResult = access.restoring(BackupOperationResult.Failed(BackupOperationResult.Step.RECOVERY, true)) {
        val clean = backup.withoutWidgetIds()
        val restoredSettings = clean.settings.copy(onboarding = OnboardingSettings(completed = true))
        apply(
            RestoreCheckpoint(
                settingsJson = LauncherSettingsCodec.encode(restoredSettings),
                customizations = clean.customizations,
                groups = clean.drawerGroups,
                workspace = clean.workspace,
            ),
        )
    }

    /**
     * Reset to default: default settings (onboarding stays complete), no customizations or groups,
     * and an empty desktop with [dockApps] in the dock. Undoable with [undo].
     */
    suspend fun resetToDefault(dockApps: List<AppKey>, newId: () -> String): BackupOperationResult = access.restoring(BackupOperationResult.Failed(BackupOperationResult.Step.RECOVERY, true)) {
        val defaults = LauncherSettings(onboarding = OnboardingSettings(completed = true))
        apply(
            RestoreCheckpoint(
                settingsJson = LauncherSettingsCodec.encode(defaults),
                customizations = emptyList(),
                groups = emptyList(),
                workspace = DefaultLayout.content(dockApps, defaults.dock.iconCount, newId),
            ),
        )
    }

    /** Undo is itself journaled; cancellation/process death cannot leave half an undo. */
    suspend fun undo(): Boolean = access.restoring(false) {
        if (!recoverPending()) { access.requireRecovery(); return@restoring false }
        val previous = undoState ?: return@restoring false
        apply(previous, retainUndo = false, localWorkspace = true) == BackupOperationResult.Applied
    }

    private suspend fun apply(target: RestoreCheckpoint, retainUndo: Boolean = true, localWorkspace: Boolean = false): BackupOperationResult {
        if (!recoverPending()) {
            access.requireRecovery()
            return BackupOperationResult.Failed(BackupOperationResult.Step.RECOVERY, true)
        }
        val current = readCurrent() ?: return BackupOperationResult.Failed(BackupOperationResult.Step.READ_CURRENT)
        val pending = RestoreRecord(current, retainUndo = retainUndo)
        try {
            if (!persist(pending)) {
                val recovered = recoverPending()
                if (!recovered) access.requireRecovery()
                return BackupOperationResult.Failed(BackupOperationResult.Step.JOURNAL, !recovered)
            }
            undoState = null
            for (step in ORDER) {
                if (!write(step, target, recovering = localWorkspace)) return rollback(current, step)
            }
            if (!persist(pending.copy(committed = true))) {
                return rollback(current, BackupOperationResult.Step.JOURNAL)
            }
            undoState = current.takeIf { retainUndo }
            return BackupOperationResult.Applied
        } catch (cancelled: CancellationException) {
            // A cancellation can arrive after a file write reached disk, including the commit marker.
            withContext(NonCancellable) { if (!recoverPending()) access.requireRecovery() }
            throw cancelled
        } catch (fatal: Error) {
            access.requireRecovery()
            throw fatal
        }
    }

    /** A reported file-write error can arrive after the atomic rename; resolve that uncertainty. */
    private suspend fun persist(record: RestoreRecord): Boolean =
        safely(false) { journal.write(record) || journal.read() == record }

    private suspend fun rollback(previous: RestoreCheckpoint, failedStep: BackupOperationResult.Step): BackupOperationResult {
        // Retry every store: even the failing write may have reached disk before returning/throwing.
        val recovered = restorePrevious(previous) && safely(false) { journal.clear() }
        if (!recovered) access.requireRecovery()
        return BackupOperationResult.Failed(failedStep, recoveryRequired = !recovered)
    }

    private suspend fun restorePrevious(previous: RestoreCheckpoint): Boolean {
        var restored = true
        for (step in ORDER) if (!write(step, previous, recovering = true)) restored = false
        return restored
    }

    private suspend fun write(step: BackupOperationResult.Step, state: RestoreCheckpoint, recovering: Boolean = false): Boolean = safely(false) {
        when (step) {
            BackupOperationResult.Step.SETTINGS -> settings.importJson(state.settingsJson) == SettingsImportResult.Applied
            BackupOperationResult.Step.CUSTOMIZATIONS -> customizations.replaceAll(state.customizations)
            BackupOperationResult.Step.DRAWER_GROUPS -> drawerGroups.replaceAll(state.groups)
            BackupOperationResult.Step.WORKSPACE -> (if (recovering) workspace.recoverContent(state.workspace) else workspace.replaceAll(state.workspace)) is EditResult.Applied
            BackupOperationResult.Step.READ_CURRENT, BackupOperationResult.Step.JOURNAL, BackupOperationResult.Step.RECOVERY -> false
        }
    }

    private suspend fun readCurrent(): RestoreCheckpoint? = safely(null) {
        RestoreCheckpoint(
            settingsJson = settings.exportJson(),
            customizations = customizations.all() ?: return@safely null,
            groups = drawerGroups.all() ?: return@safely null,
            workspace = workspace.exportContent() ?: return@safely null,
        )
    }

    private inline fun <T> safely(fallback: T, block: () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        fallback
    }

    private companion object {
        /** Settings first so the restored grid is in place before the workspace that depends on it. */
        val ORDER = listOf(
            BackupOperationResult.Step.SETTINGS,
            BackupOperationResult.Step.CUSTOMIZATIONS,
            BackupOperationResult.Step.DRAWER_GROUPS,
            BackupOperationResult.Step.WORKSPACE,
        )
    }
}
