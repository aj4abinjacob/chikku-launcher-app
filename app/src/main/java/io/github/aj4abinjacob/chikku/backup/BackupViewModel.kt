package io.github.aj4abinjacob.chikku.backup

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.aj4abinjacob.chikku.BuildConfig
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.AppsRepository
import io.github.aj4abinjacob.chikku.core.model.AppsState
import io.github.aj4abinjacob.chikku.core.model.backup.BackupCodec
import io.github.aj4abinjacob.chikku.core.model.backup.BackupCreateResult
import io.github.aj4abinjacob.chikku.core.model.backup.BackupDecodeResult
import io.github.aj4abinjacob.chikku.core.model.backup.BackupError
import io.github.aj4abinjacob.chikku.core.model.backup.BackupOperationResult
import io.github.aj4abinjacob.chikku.core.model.backup.BackupService
import io.github.aj4abinjacob.chikku.core.model.backup.BackupSummary
import io.github.aj4abinjacob.chikku.core.model.backup.ChikkuBackup
import io.github.aj4abinjacob.chikku.core.model.backup.RestoreAttention
import io.github.aj4abinjacob.chikku.core.model.backup.RestoreChecks
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.home.HomeDependencies
import io.github.aj4abinjacob.chikku.platform.DefaultDockAppsResolver
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Result shown to the user after an operation; counts only, never app names. */
enum class BackupMessage {
    SAVED, SAVE_FAILED, SHARE_FAILED,
    READ_FAILED, TOO_LARGE, NOT_JSON, NOT_A_BACKUP, NEWER_VERSION, DAMAGED,
    RESTORED, RESTORE_FAILED, RECOVERY_REQUIRED, RESET_DONE, RESET_FAILED, UNDONE, UNDO_FAILED,
}

/**
 * A parsed backup awaiting the user's confirmation. [attention] lists restored settings this device
 * cannot fully honour (for example Discover without its companion app); restore keeps them anyway.
 */
data class RestorePreview(
    val backup: ChikkuBackup,
    val summary: BackupSummary,
    val attention: List<RestoreAttention> = emptyList(),
)

data class BackupUiState(
    val working: Boolean = false,
    val preview: RestorePreview? = null,
    val message: BackupMessage? = null,
    /** A restore or reset can be undone. */
    val canUndo: Boolean = false,
    /** A content URI ready to hand to the share sheet; cleared by [BackupViewModel.shareHandled]. */
    val pendingShare: Uri? = null,
    /** True once a restore in this screen has been applied (onboarding moves on when it sees this). */
    val restored: Boolean = false,
    /** What the just-restored settings need from this device; shown once, cleared by [BackupViewModel.attentionShown]. */
    val restoredAttention: List<RestoreAttention> = emptyList(),
)

/**
 * Backup, restore, reset to default and undo for the settings screen. Writes run in [appScope] so
 * leaving the screen cannot cancel a commit half-way. Production uses the shared, journaled service;
 * its undo state is recovered after process restart.
 */
class BackupViewModel(
    private val service: BackupService,
    private val storage: BackupStorage,
    private val appsRepository: AppsRepository,
    private val dockApps: suspend () -> List<AppKey>,
    private val appScope: CoroutineScope,
    private val fileName: () -> String = { BackupFileNames.defaultName() },
    /** Device checks for restored settings ([RestoreChecks]); no checks by default. */
    private val restoreChecks: suspend (LauncherSettings) -> List<RestoreAttention> = { emptyList() },
) : ViewModel() {

    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    init { appScope.launch { service.prepare(); _state.update { it.copy(canUndo = service.canUndo) } } }

    fun suggestedFileName(): String = fileName()

    fun saveTo(uri: Uri) = launchOp(BackupMessage.SAVE_FAILED) {
        val text = createText() ?: return@launchOp BackupMessage.SAVE_FAILED
        if (storage.write(uri, text)) BackupMessage.SAVED else BackupMessage.SAVE_FAILED
    }

    fun share() = launchOp(BackupMessage.SHARE_FAILED) {
        val text = createText() ?: return@launchOp BackupMessage.SHARE_FAILED
        val uri = storage.shareableCopy(fileName(), text) ?: return@launchOp BackupMessage.SHARE_FAILED
        _state.update { it.copy(pendingShare = uri) }
        null
    }

    fun shareHandled() = _state.update { it.copy(pendingShare = null) }

    /** Reads and validates [uri]; on success shows a preview. Nothing is changed yet. */
    fun load(uri: Uri) = launchOp(BackupMessage.READ_FAILED) {
        val text = when (val read = storage.read(uri)) {
            is BackupReadResult.Text -> read.text
            BackupReadResult.TooLarge -> return@launchOp BackupMessage.TOO_LARGE
            BackupReadResult.Failed -> return@launchOp BackupMessage.READ_FAILED
        }
        when (val decoded = BackupCodec.decode(text)) {
            is BackupDecodeResult.Failure -> decoded.error.toMessage()
            is BackupDecodeResult.Success -> {
                val backup = decoded.backup
                val preview = RestorePreview(backup, backup.summary(installedApps()), checks(backup.settings))
                _state.update { it.copy(preview = preview) }
                null
            }
        }
    }

    fun cancelPreview() = _state.update { it.copy(preview = null) }

    fun confirmRestore() {
        val preview = _state.value.preview ?: return
        _state.update { it.copy(preview = null) }
        launchOp(BackupMessage.RESTORE_FAILED) {
            when (val result = service.restore(preview.backup)) {
                BackupOperationResult.Applied -> {
                    // Re-checked after the commit: the device may have changed while the preview was open.
                    val attention = checks(preview.backup.settings)
                    _state.update { it.copy(restored = true, restoredAttention = attention) }
                    BackupMessage.RESTORED
                }
                is BackupOperationResult.Failed -> if (result.recoveryRequired) BackupMessage.RECOVERY_REQUIRED else BackupMessage.RESTORE_FAILED
            }
        }
    }

    fun resetToDefault() = launchOp(BackupMessage.RESET_FAILED) {
        val apps = try {
            dockApps()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
        val result = service.resetToDefault(apps) { UUID.randomUUID().toString() }
        when (result) {
            BackupOperationResult.Applied -> BackupMessage.RESET_DONE
            is BackupOperationResult.Failed -> if (result.recoveryRequired) BackupMessage.RECOVERY_REQUIRED else BackupMessage.RESET_FAILED
        }
    }

    fun undo() = launchOp(BackupMessage.UNDO_FAILED) {
        if (service.undo()) BackupMessage.UNDONE else if (!service.prepare()) BackupMessage.RECOVERY_REQUIRED else BackupMessage.UNDO_FAILED
    }

    fun messageShown() = _state.update { it.copy(message = null) }

    fun attentionShown() = _state.update { it.copy(restoredAttention = emptyList()) }

    private suspend fun checks(settings: LauncherSettings): List<RestoreAttention> = try {
        restoreChecks(settings)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        emptyList()
    }

    /** Runs [block] once at a time in [appScope]; its non-null result becomes the message. */
    private fun launchOp(onError: BackupMessage, block: suspend () -> BackupMessage?) {
        if (_state.value.working) return
        _state.update { it.copy(working = true, message = null) }
        appScope.launch {
            val message = try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                onError
            }
            _state.update { it.copy(working = false, message = message ?: it.message, canUndo = service.canUndo) }
        }
    }

    private suspend fun createText(): String? = when (val created = service.create()) {
        is BackupCreateResult.Success -> BackupCodec.encode(created.backup)
        is BackupCreateResult.Failed -> null
    }

    /** Installed apps for the missing-app count; null when the list is not available soon. */
    private suspend fun installedApps(): Set<AppKey>? = withTimeoutOrNull(APPS_TIMEOUT_MS) {
        when (val s = appsRepository.apps.first { it !is AppsState.Loading }) {
            is AppsState.Loaded -> s.apps.mapTo(HashSet()) { it.key }
            else -> null
        }
    }

    companion object {
        private const val APPS_TIMEOUT_MS = 3_000L

        fun factory(context: Context, deps: HomeDependencies): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            return viewModelFactory {
                initializer {
                    BackupViewModel(
                        service = deps.backupService ?: BackupService(
                            deps.settingsRepository,
                            deps.workspaceRepository,
                            deps.appCustomizationRepository,
                            deps.drawerGroupRepository,
                            appVersion = BuildConfig.VERSION_NAME,
                        ),
                        storage = ContentResolverBackupStorage(appContext, Dispatchers.IO),
                        appsRepository = deps.appsRepository,
                        dockApps = { DefaultDockAppsResolver(appContext, Dispatchers.IO).resolve() },
                        appScope = deps.appScope,
                        restoreChecks = deps.restoreEnvironment
                            ?.let { env -> { settings -> RestoreChecks.run(settings, env) } }
                            ?: { emptyList() },
                    )
                }
            }
        }
    }
}

private fun BackupError.toMessage(): BackupMessage = when (this) {
    BackupError.TooLarge -> BackupMessage.TOO_LARGE
    BackupError.NotJson -> BackupMessage.NOT_JSON
    BackupError.NotChikkuBackup -> BackupMessage.NOT_A_BACKUP
    is BackupError.UnsupportedVersion -> BackupMessage.NEWER_VERSION
    is BackupError.Invalid -> BackupMessage.DAMAGED
}
