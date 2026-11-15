package io.github.aj4abinjacob.chikku.platform

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import io.github.aj4abinjacob.chikku.core.model.AppCustomizationRepository
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.WorkspaceRepository
import io.github.aj4abinjacob.chikku.core.model.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

/** Small platform boundary for real personal-profile install events, never the existing inventory. */
internal interface InstalledAppEventSource {
    /** Called off the main thread. The source must filter other profiles before invoking [onAdded]. */
    fun register(onAdded: (String) -> Unit)
    fun unregister()
    suspend fun personalKeys(packageName: String): List<AppKey>
}

internal class AndroidInstalledAppEventSource(context: Context) : InstalledAppEventSource {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val personal = Process.myUserHandle()
    private var callback: LauncherApps.Callback? = null

    override fun register(onAdded: (String) -> Unit) {
        // Every subscription captures its own generation callback. A late old Binder callback must
        // never read a new subscription's listener and make an old install appear current.
        val subscription = object : LauncherApps.Callback() {
            override fun onPackageAdded(packageName: String, user: UserHandle) {
                if (user == personal) onAdded(packageName)
            }
            override fun onPackageRemoved(packageName: String, user: UserHandle) = Unit
            override fun onPackageChanged(packageName: String, user: UserHandle) = Unit
            override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = Unit
            override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = Unit
        }
        callback = subscription
        // Registration runs on IO; use an explicit main handler for callbacks, independent of its Looper.
        launcherApps.registerCallback(subscription, Handler(Looper.getMainLooper()))
    }

    override fun unregister() {
        val subscription = callback ?: return
        callback = null
        launcherApps.unregisterCallback(subscription)
    }

    override suspend fun personalKeys(packageName: String): List<AppKey> {
        val serial = userManager.getSerialNumberForUser(personal)
        if (serial < 0) return emptyList()
        return launcherApps.getActivityList(packageName, personal).map {
            AppKey(it.componentName.packageName, it.componentName.className, serial)
        }
    }
}

/** Watches install events only. Preference changes invalidate queued events and in-flight queries. */
internal class NewAppsController(
    context: Context,
    scope: CoroutineScope,
    private val settings: SettingsRepository,
    private val workspace: () -> WorkspaceRepository,
    private val customizations: () -> AppCustomizationRepository,
    private val io: CoroutineDispatcher,
    private val canEdit: suspend () -> Boolean = { true },
    private val installSource: InstalledAppEventSource = AndroidInstalledAppEventSource(context),
) {
    private data class InstallEvent(val packageName: String, val generation: Long)
    private val generation = AtomicLong()
    private val installs = Channel<InstallEvent>(Channel.BUFFERED)

    init {
        scope.launch {
            try {
                settings.settings.map { it.home.addNewApps && !it.home.layoutLocked }.distinctUntilChanged().collectLatest { enabled ->
                    val currentGeneration = generation.incrementAndGet()
                    if (!enabled) return@collectLatest
                    try {
                        withContext(io) {
                            installSource.register { packageName -> installs.trySend(InstallEvent(packageName, currentGeneration)) }
                        }
                        awaitCancellation()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // Registration failure does not prevent HOME or surface installed package names.
                    } finally {
                        generation.incrementAndGet()
                        withContext(NonCancellable + io) { runCatching { installSource.unregister() } }
                    }
                }
            } finally {
                generation.incrementAndGet()
                installs.close()
            }
        }
        scope.launch {
            for (event in installs) {
                try {
                    if (!enabledFor(event) || !canEdit()) continue
                    val keys = withContext(io) { installSource.personalKeys(event.packageName).distinct().sortedBy { it.className } }
                    if (!enabledFor(event) || !canEdit()) continue
                    for (key in keys) {
                        if (!enabledFor(event) || !canEdit()) break
                        // Fresh customization state for every component, after package resolution completes.
                        val customizationRepository = withContext(io) { customizations() }
                        val workspaceRepository = withContext(io) { workspace() }
                        // Lazy repository construction may yield or discover recovery state; admit only afterward.
                        // The recovery gate can suspend for disk reads. Check live preferences and
                        // hidden state after it returns, without another platform factory/gate yield.
                        if (!canEdit() || !enabledFor(event)) break
                        if (customizationRepository.customizations.first()[key]?.hidden == true) continue
                        if (!enabledFor(event)) break
                        // This separate API atomically rejects top-level and folder duplicates; manual adds keep their semantics.
                        workspaceRepository.addInstalledApp(key)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Profile, package or storage failure must not affect HOME or expose inventory.
                }
            }
        }
    }

    private suspend fun enabledFor(event: InstallEvent): Boolean {
        if (event.generation != generation.get()) return false
        val home = settings.settings.first().home
        return home.addNewApps && !home.layoutLocked && event.generation == generation.get()
    }
}
