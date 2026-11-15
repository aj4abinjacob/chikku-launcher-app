package io.github.aj4abinjacob.chikku.settings

import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.luminance
import androidx.core.view.WindowCompat
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aj4abinjacob.chikku.BuildConfig
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.prompt.ChikkuPrompts
import io.github.aj4abinjacob.chikku.applock.AppUnlockPrompt
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerSettings
import io.github.aj4abinjacob.chikku.core.model.settings.NightMode
import io.github.aj4abinjacob.chikku.theme.NightModeController
import io.github.aj4abinjacob.chikku.home.DefaultHomeRequest
import io.github.aj4abinjacob.chikku.home.HomeDependencies
import io.github.aj4abinjacob.chikku.home.homeDependencies
import io.github.aj4abinjacob.chikku.labs.LabsTapCounter
import io.github.aj4abinjacob.chikku.message.ChikkuMessages
import io.github.aj4abinjacob.chikku.message.LocalChikkuMessages
import io.github.aj4abinjacob.chikku.message.SnackbarChikkuMessages
import io.github.aj4abinjacob.chikku.settings.components.LocalDrawerScrollSettings

/**
 * The visible app entry point (MAIN/LAUNCHER, "Chikku Settings"): the section list of all launcher
 * settings, plus default-launcher status with links to the system role request and HOME settings.
 * Never changes HOME itself.
 */
class SettingsActivity : ComponentActivity(), ChikkuMessages.Owner {

    /**
     * Chikku's messages for every settings page (a snackbar in the settings theme, never a
     * platform toast); composables read it through [LocalChikkuMessages].
     */
    private val messages by lazy { SnackbarChikkuMessages(this, lifecycleScope, SnackbarHostState()) }
    override val chikkuMessages: ChikkuMessages get() = messages

    private lateinit var deps: HomeDependencies
    private lateinit var unlockPrompt: AppUnlockPrompt
    private val viewModel: SettingsViewModel by viewModels { SettingsViewModel.factory(deps) }

    private val roleRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.refresh()
    }

    private var createdNight: NightMode = NightMode.SYSTEM

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        createdNight = NightModeController.applyOverride(this, newBase)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        deps = homeDependencies()
        val prompts = ChikkuPrompts(this) { viewModel.settings.value?.lookFeel?.accentColor }
        unlockPrompt = AppUnlockPrompt(this, deps.appLock, prompts, messages)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        holdFirstFrameUntilSettingsLoad()
        setContent {
            val isDefault by viewModel.isDefaultHome.collectAsStateWithLifecycle()
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val nightMode = settings?.night?.mode
            LaunchedEffect(nightMode) {
                if (nightMode != null) NightModeController.onModeChanged(this@SettingsActivity, createdNight, nightMode)
            }
            CompositionLocalProvider(
                LocalHomeDependencies provides deps,
                LocalAppUnlockPrompt provides unlockPrompt,
                LocalChikkuMessages provides messages,
                LocalDrawerScrollSettings provides (settings?.drawer ?: DrawerSettings()),
            ) {
                ChikkuSettingsTheme(accent = settings?.lookFeel?.accentColor, speed = settings?.lookFeel?.animationSpeed) {
                    // The status bar sits on the page-colored top bar; keep its icons readable in light mode.
                    val lightStatusBar = MaterialTheme.colorScheme.surface.luminance() > 0.179f
                    SideEffect {
                        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = lightStatusBar
                    }
                    Box(Modifier.fillMaxSize()) {
                        SettingsRoot(
                            settings = settings,
                            update = viewModel::update,
                            host = SettingsHost(
                                isDefaultHome = isDefault,
                                versionName = BuildConfig.VERSION_NAME,
                                onSetDefault = ::requestDefaultHome,
                                onChooseOtherHome = ::openHomeSettings,
                                onVersionTap = labsTaps::onTap,
                            ),
                            initialSection = sectionFromIntent(),
                        )
                        // Above every page, the navigation bar and the keyboard.
                        SnackbarHost(
                            messages.state,
                            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding(),
                        )
                    }
                }
            }
        }
    }

    /**
     * Settings are null for the first frame(s) of a new activity, which would draw the default accent
     * and then jump to the stored one. Skip drawing until they load, or [FIRST_FRAME_HOLD_MS] passes.
     */
    private fun holdFirstFrameUntilSettingsLoad() {
        val content = findViewById<View>(android.R.id.content)
        val deadline = SystemClock.uptimeMillis() + FIRST_FRAME_HOLD_MS
        content.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (viewModel.settings.value == null && SystemClock.uptimeMillis() < deadline) {
                    content.postInvalidateOnAnimation()
                    return false
                }
                content.viewTreeObserver.removeOnPreDrawListener(this)
                return true
            }
        })
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    // --- Labs (G11): tapping the version in About toggles the hidden Labs section -----------------

    @VisibleForTesting
    internal val labsTaps by lazy { LabsTapCounter(::toggleLabs) }

    private fun toggleLabs() {
        val current = viewModel.settings.value ?: return // not loaded yet: ignore rather than guess
        viewModel.update(LabsTapCounter::toggled)
        val message = if (current.labs.unlocked) R.string.labs_locked_toast else R.string.labs_unlocked_toast
        messages.show(message)
    }

    /** Deep link: `EXTRA_SECTION` = a [SettingsSection] name opens that section directly. */
    private fun sectionFromIntent(): SettingsSection? =
        intent?.getStringExtra(EXTRA_SECTION)?.let { name -> SettingsSection.entries.firstOrNull { it.name == name } }

    private fun requestDefaultHome() = DefaultHomeRequest.request(
        this, deps, roleRequest::launch, { messages.show(R.string.home_settings_unavailable) },
    )

    private fun openHomeSettings() = DefaultHomeRequest.openHomeSettings(
        this, deps, { messages.show(R.string.home_settings_unavailable) },
    )

    companion object {
        const val EXTRA_SECTION = "io.github.aj4abinjacob.chikku.settings.SECTION"
        private const val FIRST_FRAME_HOLD_MS = 500L
    }
}
