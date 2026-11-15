package io.github.aj4abinjacob.chikku.platform

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Process
import android.os.UserManager
import androidx.core.content.IntentCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A resolved, current-profile destination. The original action/data survives authentication. */
class IntentTarget internal constructor(
    internal val intent: Intent,
    val label: String,
    val userSerial: Long,
) {
    val packageName: String get() = checkNotNull(intent.component).packageName
}

sealed interface IntentHandoffResult {
    data object Started : IntentHandoffResult
    data object Unavailable : IntentHandoffResult
    data class Locked(val target: IntentTarget, val lock: LaunchResult.Locked) : IntentHandoffResult
    data class Choose(val targets: List<IntentTarget>) : IntentHandoffResult
}

/**
 * Search deep links use ordinary activities rather than LauncherApps. Resolve off main, then pin
 * the component and check its package/profile immediately before starting it. Never hand an
 * unchecked intent to Android's resolver/chooser: that would launch its selection past app lock.
 * This supports current-profile handlers only, not system resolver/profile-forwarding activities.
 */
class GuardedIntentLauncher(
    private val context: Context,
    private val lock: AppLockGuard,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val mayLaunch: () -> Boolean = { true },
) {
    suspend fun startFirst(intents: List<Intent>): IntentHandoffResult {
        for (intent in intents) {
            val targets = withContext(ioDispatcher) { resolve(intent) }
            if (targets.size > 1) return IntentHandoffResult.Choose(targets)
            val target = targets.singleOrNull() ?: continue
            val result = start(target)
            // A locked destination is not a launch failure; never fall through to another app.
            if (result != IntentHandoffResult.Unavailable) return result
        }
        return IntentHandoffResult.Unavailable
    }

    /** Retry this exact target after authentication, without re-resolving to a different app. */
    fun start(target: IntentTarget): IntentHandoffResult {
        if (!mayLaunch() || (context is Activity && (context.isFinishing || context.isDestroyed))) {
            return IntentHandoffResult.Unavailable
        }
        val lifecycle = (context as? LifecycleOwner)?.lifecycle
        if (lifecycle != null && !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            return IntentHandoffResult.Unavailable
        }
        if (!lock.admit(target.packageName, target.userSerial)) {
            return IntentHandoffResult.Locked(target, LaunchResult.Locked(target.packageName, target.userSerial))
        }
        return try {
            context.startActivity(Intent(target.intent))
            IntentHandoffResult.Started
        } catch (_: ActivityNotFoundException) {
            IntentHandoffResult.Unavailable
        } catch (_: SecurityException) {
            IntentHandoffResult.Unavailable
        }
    }

    private fun resolve(request: Intent): List<IntentTarget> = try {
        val forceChooser = request.action == Intent.ACTION_CHOOSER
        val intent = if (forceChooser) {
            IntentCompat.getParcelableExtra(request, Intent.EXTRA_INTENT, Intent::class.java)
        } else request
        if (intent == null) emptyList() else {
            val serial = context.getSystemService(UserManager::class.java).personalUserSerial(requireUnlocked = false)
            if (serial == null) emptyList() else {
                val pm = context.packageManager
                val candidates = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                    .mapNotNull { info ->
                        val activity = info.activityInfo ?: return@mapNotNull null
                        // "android" supplies the resolver and cross-profile forwarders, not a final
                        // app whose package/profile this boundary could safely authenticate.
                        if (!activity.exported || !activity.enabled || !activity.applicationInfo.enabled ||
                            activity.packageName == "android"
                        ) return@mapNotNull null
                        IntentTarget(
                            Intent(intent).setComponent(ComponentName(activity.packageName, activity.name)),
                            info.loadLabel(pm).toString(),
                            serial,
                        )
                    }.distinctBy { it.intent.component }
                val preferred = if (forceChooser) null else
                    pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.let {
                        ComponentName(it.packageName, it.name)
                    }
                candidates.firstOrNull { it.intent.component == preferred }?.let { listOf(it) } ?: candidates
            }
        }
    } catch (_: RuntimeException) {
        // Unknown destination/profile is never permission to launch unchecked.
        emptyList()
    }
}
