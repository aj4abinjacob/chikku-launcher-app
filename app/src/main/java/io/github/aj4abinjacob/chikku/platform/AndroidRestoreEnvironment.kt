package io.github.aj4abinjacob.chikku.platform

import android.content.Context
import android.content.pm.LauncherApps
import android.os.CancellationSignal
import android.os.Process
import androidx.core.net.toUri
import io.github.aj4abinjacob.chikku.core.model.backup.DiscoverReadiness
import io.github.aj4abinjacob.chikku.core.model.backup.RestoreEnvironment
import io.github.aj4abinjacob.chikku.platform.icons.IconPackRepository
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Answers [RestoreEnvironment] questions for restore checks. Every answer is bounded in time and
 * falls back to "unknown", so a slow companion app can delay a restore result by seconds at most
 * and never produces a warning without evidence. Nothing is logged.
 */
class AndroidRestoreEnvironment(
    context: Context,
    private val discoverHealth: DiscoverHealthChecker,
    @Suppress("UNUSED_PARAMETER") io: CoroutineDispatcher,
    private val iconPacks: () -> Set<String> = { IconPackRepository(context).installedPacks().mapTo(HashSet()) { it.packageName } },
    private val checks: BoundedRestoreChecks = BoundedRestoreChecks(),
) : RestoreEnvironment {
    private val context = context.applicationContext

    override suspend fun discoverReadiness(): DiscoverReadiness =
        bounded(DISCOVER_TIMEOUT_MS) { discoverHealth.check().readiness() } ?: DiscoverReadiness.UNKNOWN

    override suspend fun installedIconPacks(): Set<String>? = bounded(LOOKUP_TIMEOUT_MS) { iconPacks() }

    override suspend fun canReadContent(uri: String): Boolean? {
        val cancellation = CancellationSignal()
        return checks.await(LOOKUP_TIMEOUT_MS, cancellation::cancel) {
            try {
                context.contentResolver.openFileDescriptor(uri.toUri(), "r", cancellation)?.use { true } ?: false
            } catch (_: SecurityException) {
                false
            } catch (_: java.io.FileNotFoundException) {
                false
            }
        }
    }

    override suspend fun installedPackages(packages: Set<String>): Set<String>? = bounded(LOOKUP_TIMEOUT_MS) {
        val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return@bounded null
        val user = Process.myUserHandle()
        packages.filterTo(HashSet()) { launcherApps.getActivityList(it, user).isNotEmpty() }
    }

    private suspend fun <T> bounded(timeoutMs: Long, block: suspend () -> T): T? =
        checks.await(timeoutMs, block = block)

    private companion object {
        /** The health check can wait for an unresponsive bridge; Settings shows that case live. */
        const val DISCOVER_TIMEOUT_MS = 3_500L
        const val LOOKUP_TIMEOUT_MS = 2_000L
    }
}

/** Whether a restored "Discover on" works here; matches what the swipe will actually do. */
internal fun DiscoverHealth.readiness(): DiscoverReadiness = when (this) {
    DiscoverHealth.MissingGoogle -> DiscoverReadiness.NO_GOOGLE_APP
    DiscoverHealth.Direct, DiscoverHealth.AppOnly -> DiscoverReadiness.READY
    DiscoverHealth.BridgeMissing -> DiscoverReadiness.NO_BRIDGE
    is DiscoverHealth.BridgeWrongKey, is DiscoverHealth.BridgeDisabled, is DiscoverHealth.BridgeUnresponsive ->
        DiscoverReadiness.BRIDGE_NOT_WORKING
    is DiscoverHealth.Bridge -> when {
        status.batteryRestricted -> DiscoverReadiness.BRIDGE_NOT_WORKING
        status.google == BridgeGoogleLink.REJECTED || status.google == BridgeGoogleLink.BIND_FAILED ->
            DiscoverReadiness.BRIDGE_NOT_WORKING
        else -> DiscoverReadiness.READY
    }
}
