package io.github.aj4abinjacob.chikku.home

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import io.github.aj4abinjacob.chikku.applock.AppUnlocker
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.platform.AppLockGuard
import io.github.aj4abinjacob.chikku.platform.ExternalIntentStarter
import io.github.aj4abinjacob.chikku.platform.GuardedIntentLauncher
import io.github.aj4abinjacob.chikku.platform.IntentHandoffResult
import io.github.aj4abinjacob.chikku.platform.IntentTarget
import io.github.aj4abinjacob.chikku.search.SearchIntentChooser
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** Activity-owned external handoffs shared by gestures, Discover and public Store listings. */
class GuardedExternalActions(
    private val activity: ComponentActivity,
    lock: AppLockGuard,
    settings: Flow<LauncherSettings>,
    private val unlocker: AppUnlocker,
    private val onUnavailable: () -> Unit,
) : ExternalIntentStarter {
    private val launcher = GuardedIntentLauncher(activity, lock)
    private val chooser = SearchIntentChooser(activity, settings)
    private var request = 0L
    private var resolving: Job? = null
    private var authenticatedRetry: Pair<IntentTarget, Long>? = null

    init {
        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = resumeAuthenticatedRetry()
            override fun onStop(owner: LifecycleOwner) {
                // Cancel an unfinished lookup, but keep a completed lookup's exact auth retry:
                // device-credential confirmation itself can stop HOME on Android 10.
                if (resolving?.isActive == true) {
                    request++
                    resolving?.cancel()
                }
            }
            override fun onDestroy(owner: LifecycleOwner) {
                request++
                authenticatedRetry = null
                resolving?.cancel()
            }
        })
    }

    override fun start(intents: List<Intent>): Boolean {
        if (!active() || intents.isEmpty() || resolving?.isActive == true || chooser.isShowing) return false
        val token = ++request
        authenticatedRetry = null
        val copied = intents.map { Intent(it).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        resolving = activity.lifecycleScope.launch {
            val result = launcher.startFirst(copied)
            resolving = null
            handle(result, token)
        }
        return true
    }

    private fun active(): Boolean = !activity.isFinishing && !activity.isDestroyed &&
        activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

    private fun resumeAuthenticatedRetry() {
        if (!active()) return
        val (target, token) = authenticatedRetry ?: return
        authenticatedRetry = null
        if (token == request) handle(launcher.start(target), token)
    }

    private fun retryAfterAuthentication(target: IntentTarget, token: Long) {
        if (token != request || activity.isFinishing || activity.isDestroyed) return
        // BiometricPrompt's executor can report success before credential UI restarts HOME.
        authenticatedRetry = target to token
        resumeAuthenticatedRetry()
    }

    private fun handle(result: IntentHandoffResult, token: Long) {
        if (token != request || !active()) return
        when (result) {
            IntentHandoffResult.Started -> Unit

            IntentHandoffResult.Unavailable -> onUnavailable()

            is IntentHandoffResult.Locked -> unlocker.unlock(result.lock) {
                // Preserve the chosen component, profile, extras and data. No fallback after denial.
                retryAfterAuthentication(result.target, token)
            }

            is IntentHandoffResult.Choose -> chooser.show(result.targets) { chosen ->
                if (token == request && active()) handle(launcher.start(chosen), token)
            }
        }
    }
}
