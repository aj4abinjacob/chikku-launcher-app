package io.github.aj4abinjacob.chikku.home

import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.message.ChikkuMessageBarView
import io.github.aj4abinjacob.chikku.platform.DiscoverOpenResult
import io.github.aj4abinjacob.chikku.platform.GoogleDiscoverClient
import io.github.aj4abinjacob.chikku.prompt.ChikkuPrompts
import io.github.aj4abinjacob.chikku.prompt.PromptAction
import io.github.aj4abinjacob.chikku.prompt.PromptSpec
import io.github.aj4abinjacob.chikku.prompt.PromptTone
import kotlinx.coroutines.launch

/** The Google Discover overlay entry points on HOME: enabling, the edge swipe, opening and explaining failures. */
internal class HomeDiscoverOverlay(
    private val activity: ComponentActivity,
    private val discover: GoogleDiscoverClient,
    private val workspacePager: WorkspacePager,
    private val deps: HomeDependencies,
    private val prompts: ChikkuPrompts,
    private val messageBar: ChikkuMessageBarView,
    private val closeCards: () -> Unit,
) {
    private var discoverAccessibilityAction = -1

    fun render(state: HomeUiState) {
        val enabled = state.settings.home.googleDiscoverEnabled && state.workspace is WorkspaceUi.Ready
        discover.setEnabled(enabled)
        workspacePager.onStartEdgeSwipe = if (enabled) ::open else null
        if (enabled && discoverAccessibilityAction == -1) {
            discoverAccessibilityAction = ViewCompat.addAccessibilityAction(
                workspacePager, activity.getString(R.string.home_open_google_discover),
            ) { _, _ -> open(); true }
        } else if (!enabled && discoverAccessibilityAction != -1) {
            ViewCompat.removeAccessibilityAction(workspacePager, discoverAccessibilityAction)
            discoverAccessibilityAction = -1
        }
    }

    private fun open() {
        closeCards()
        val message = when (discover.open()) {
            DiscoverOpenResult.OPENED, DiscoverOpenResult.OPENED_GOOGLE_APP -> return
            DiscoverOpenResult.BRIDGE_PROBLEM -> return explainProblem()
            DiscoverOpenResult.MISSING_GOOGLE -> R.string.home_google_discover_missing_google
            DiscoverOpenResult.CONNECTING -> R.string.home_google_discover_connecting
            DiscoverOpenResult.UNAVAILABLE -> R.string.home_google_discover_unavailable
        }
        messageBar.show(message, long = true)
    }

    /** Tells the user why the bridge failed (often battery settings) and offers the fix. */
    private fun explainProblem() {
        val checker = deps.discoverHealthChecker ?: return
        activity.lifecycleScope.launch {
            val health = checker.check()
            if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return@launch
            val bridge = health.bridgePackage
            prompts.show(
                PromptSpec(
                    icon = R.drawable.ic_settings_cards,
                    title = activity.getString(R.string.home_google_discover_problem_title),
                    message = activity.getString(health.messageRes(afterFailure = true)),
                    tone = PromptTone.WARNING,
                    primary = if (bridge != null) {
                        PromptAction(activity.getString(R.string.home_google_discover_bridge_settings)) { activity.openAppDetails(bridge) }
                    } else {
                        PromptAction(activity.getString(R.string.home_google_discover_retry)) { discover.retry() }
                    },
                    secondary = PromptAction(activity.getString(R.string.home_google_discover_open_google)) { discover.openGoogleApp() },
                    dismissLabel = activity.getString(R.string.home_cancel),
                ),
            )
        }
    }
}
