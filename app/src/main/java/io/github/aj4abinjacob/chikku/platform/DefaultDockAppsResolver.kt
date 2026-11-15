package io.github.aj4abinjacob.chikku.platform

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Process
import android.os.UserManager
import android.provider.MediaStore
import androidx.core.net.toUri
import io.github.aj4abinjacob.chikku.core.model.AppKey
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Suggests a first-run dock from the user's default dialer, SMS, browser and camera apps in the
 * personal profile. Requires the `<queries>` entries for these intents (package visibility).
 */
class DefaultDockAppsResolver internal constructor(
    private val ioDispatcher: CoroutineDispatcher,
    private val defaultHandlerFor: (Intent) -> String?,
    private val launcherActivityFor: (packageName: String) -> AppKey?,
) {

    constructor(context: Context, ioDispatcher: CoroutineDispatcher) : this(
        ioDispatcher,
        packageManagerDefaultHandler(context.applicationContext.packageManager),
        personalLauncherActivity(context.applicationContext),
    )

    /** At most [MAX_DOCK_APPS] distinct keys, in dialer, SMS, browser, camera order. Never throws. */
    suspend fun resolve(): List<AppKey> = withContext(ioDispatcher) {
        dockIntents()
            .mapNotNull { intent -> safely { defaultHandlerFor(intent)?.let(launcherActivityFor) } }
            .distinct()
            .take(MAX_DOCK_APPS)
    }

    private inline fun <T> safely(block: () -> T?): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    internal companion object {
        const val MAX_DOCK_APPS = 4

        fun dockIntents(): List<Intent> = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_SENDTO, "smsto:".toUri()),
            Intent(Intent.ACTION_VIEW, "https://example.com".toUri()).addCategory(Intent.CATEGORY_BROWSABLE),
            Intent(MediaStore.ACTION_IMAGE_CAPTURE),
        )
    }
}

/**
 * Returns the package of the user's default handler, or null if there is none. When no default is
 * set, `resolveActivity` returns the system resolver/chooser, which is not one of the activities
 * that handle the intent; requiring membership in the candidate set rejects it on every OEM
 * without hard-coding resolver class names.
 */
internal fun defaultHandlerPackage(resolvedPackage: String?, candidatePackages: Collection<String>): String? =
    resolvedPackage?.takeIf { it in candidatePackages }

private fun packageManagerDefaultHandler(pm: PackageManager): (Intent) -> String? = { intent ->
    val resolved = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
    val candidates = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        .mapNotNull { it.activityInfo?.packageName }
        .toSet()
    defaultHandlerPackage(resolved, candidates)
}

private fun personalLauncherActivity(context: Context): (String) -> AppKey? {
    val launcherApps = context.getSystemService(LauncherApps::class.java)
    val userManager = context.getSystemService(UserManager::class.java)
    return { packageName ->
        val user = Process.myUserHandle()
        val component = launcherApps?.getActivityList(packageName, user)?.firstOrNull()?.componentName
        val serial = userManager?.getSerialNumberForUser(user) ?: -1L
        if (component == null || serial < 0) null else AppKey(component.packageName, component.className, serial)
    }
}
