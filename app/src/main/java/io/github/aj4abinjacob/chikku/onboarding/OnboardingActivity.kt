package io.github.aj4abinjacob.chikku.onboarding

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.luminance
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.backup.BackupFileNames
import io.github.aj4abinjacob.chikku.backup.BackupViewModel
import io.github.aj4abinjacob.chikku.core.model.settings.NightMode
import io.github.aj4abinjacob.chikku.core.model.settings.SettingsRepository
import io.github.aj4abinjacob.chikku.home.DefaultHomeRequest
import io.github.aj4abinjacob.chikku.home.HomeDependencies
import io.github.aj4abinjacob.chikku.home.homeDependencies
import io.github.aj4abinjacob.chikku.settings.ChikkuSettingsTheme
import io.github.aj4abinjacob.chikku.settings.LocalHomeDependencies
import io.github.aj4abinjacob.chikku.settings.SettingsViewModel
import io.github.aj4abinjacob.chikku.theme.NightModeController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * First-run setup (G20) and "Quick start" (G19): welcome, grid/icon size, drawer style, night mode,
 * default-launcher request, done. The welcome step can also restore a backup (G18) instead; the
 * restored settings then replace the remaining choices. Every choice is saved as soon as it is made; Skip, Done and Back
 * on the first step mark onboarding complete so it never comes back uninvited. It runs in its own
 * task and never blocks HOME: pressing Home simply leaves it.
 *
 * Styled like Chikku settings: the user's accent (present after a restore or on Quick start), the
 * launcher's night mode and in-screen snackbars instead of OEM-skinned toasts.
 */
class OnboardingActivity : ComponentActivity() {

    private lateinit var deps: HomeDependencies
    private val viewModel: SettingsViewModel by viewModels { SettingsViewModel.factory(deps) }
    private val backup: BackupViewModel by viewModels { BackupViewModel.factory(this, deps) }

    private val openBackup = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) backup.load(uri)
    }

    private val roleRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.refresh()
    }

    /** Short notices (no file app, no HOME settings) shown in the screen's snackbar host. */
    private val snackbar = SnackbarHostState()

    private var createdNight: NightMode = NightMode.SYSTEM

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        createdNight = NightModeController.applyOverride(this, newBase)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        deps = homeDependencies()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val isDefault by viewModel.isDefaultHome.collectAsStateWithLifecycle()
            val backupState by backup.state.collectAsStateWithLifecycle()
            // The night step applies its choice live, as Settings does.
            val nightMode = settings?.night?.mode
            LaunchedEffect(nightMode) {
                if (nightMode != null) NightModeController.onModeChanged(this@OnboardingActivity, createdNight, nightMode)
            }
            ChikkuSettingsTheme(accent = settings?.lookFeel?.accentColor, speed = settings?.lookFeel?.animationSpeed) {
                // The status bar sits on the accent-colored top bar; keep its icons readable on light accents.
                val lightStatusBar = MaterialTheme.colorScheme.primaryContainer.luminance() > 0.179f
                SideEffect {
                    WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = lightStatusBar
                }
                CompositionLocalProvider(LocalHomeDependencies provides deps) {
                    OnboardingScreen(
                        settings = settings,
                        isDefaultHome = isDefault,
                        update = viewModel::update,
                        onSetDefault = ::requestDefaultHome,
                        onFinish = ::finishOnboarding,
                        restore = OnboardingRestore(
                            state = backupState,
                            onPickFile = ::pickBackup,
                            onConfirm = backup::confirmRestore,
                            onCancel = backup::cancelPreview,
                            onMessageShown = backup::messageShown,
                        ),
                        snackbarHostState = snackbar,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    private fun finishOnboarding() {
        viewModel.update { it.copy(onboarding = it.onboarding.copy(completed = true)) }
        finish()
    }

    private fun pickBackup() {
        try {
            // Some file managers label JSON as text or binary; validation happens after reading.
            openBackup.launch(BackupFileNames.OPEN_MIME_TYPES)
        } catch (_: ActivityNotFoundException) {
            showMessage(R.string.backup_no_file_app)
        }
    }

    private fun requestDefaultHome() = DefaultHomeRequest.request(
        this, deps, roleRequest::launch, { showMessage(R.string.home_settings_unavailable) },
    )

    private fun showMessage(@StringRes message: Int) {
        val text = getString(message)
        lifecycleScope.launch { snackbar.showSnackbar(text) }
    }

    companion object {
        private const val SETTINGS_TIMEOUT_MS = 2_000L

        fun intent(context: Context): Intent = Intent(context, OnboardingActivity::class.java)

        /**
         * Called by HOME on a fresh start: opens onboarding when it has not been completed. Reads
         * settings off the main thread (the repository does its own I/O) and gives up quietly if
         * they are slow or unreadable, so HOME is never held up.
         */
        fun launchIfNeeded(activity: ComponentActivity, settingsRepository: SettingsRepository) {
            activity.lifecycleScope.launch {
                val settings = try {
                    withTimeoutOrNull(SETTINGS_TIMEOUT_MS) { settingsRepository.settings.first() }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
                if (settings == null || settings.onboarding.completed) return@launch
                try {
                    activity.startActivity(intent(activity))
                } catch (_: ActivityNotFoundException) {
                    // Never block HOME on setup.
                }
            }
        }
    }
}
