package io.github.aj4abinjacob.chikku.platform

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Process
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

sealed interface DiscoverProvider {
    data class Available(
        val component: ComponentName,
        val apiVersion: Int,
        val appIntent: Intent? = null,
        /** True when [component] is the optional same-signer bridge app rather than Google itself. */
        val viaBridge: Boolean = false,
    ) : DiscoverProvider
    data class AppOnly(val appIntent: Intent) : DiscoverProvider
    data object MissingGoogle : DiscoverProvider
    data object Unavailable : DiscoverProvider
}

fun interface GoogleDiscoverResolver {
    suspend fun resolve(): DiscoverProvider
}

/**
 * Only the current user's Google app is inspected. Google accepts overlay clients that are system
 * or debuggable apps, so a release build also looks for the optional Chikku Discover bridge, a
 * separate debuggable app signed with the same key that relays the connection.
 */
class AndroidGoogleDiscoverResolver(
    context: Context,
    private val io: CoroutineDispatcher,
    private val experimentalOverlay: Boolean = true,
) : GoogleDiscoverResolver {
    private val context = context.applicationContext

    override suspend fun resolve(): DiscoverProvider = withContext(io) {
        try {
            val pm = context.packageManager
            if (!pm.getApplicationInfo(GOOGLE_PACKAGE, 0).enabled) return@withContext DiscoverProvider.MissingGoogle
            val appIntent = pm.getLaunchIntentForPackage(GOOGLE_PACKAGE)
            // The first Play release uses only the explicit Google-app handoff. Do not inspect
            // or bind either unofficial overlay service, even when a same-signer bridge exists.
            if (!experimentalOverlay) {
                return@withContext appIntent?.let(DiscoverProvider::AppOnly) ?: DiscoverProvider.Unavailable
            }
            val google = pm.resolveService(overlayIntent(context, GOOGLE_PACKAGE), PackageManager.GET_META_DATA)
                ?: return@withContext appIntent?.let(DiscoverProvider::AppOnly) ?: DiscoverProvider.Unavailable
            val service = google.serviceInfo
            if (!service.enabled || !service.exported || service.packageName != GOOGLE_PACKAGE) {
                return@withContext appIntent?.let(DiscoverProvider::AppOnly) ?: DiscoverProvider.Unavailable
            }
            val apiVersion = google.serviceInfo.metaData?.getInt("service.api.version", 1) ?: 1
            val bridge = findBridge(pm)
            if (bridge != null) {
                DiscoverProvider.Available(bridge, apiVersion, appIntent, viaBridge = true)
            } else {
                DiscoverProvider.Available(ComponentName(service.packageName, service.name), apiVersion, appIntent)
            }
        } catch (_: PackageManager.NameNotFoundException) {
            DiscoverProvider.MissingGoogle
        } catch (_: Exception) {
            DiscoverProvider.Unavailable
        }
    }

    private fun findBridge(pm: PackageManager): ComponentName? =
        pm.queryIntentServices(Intent(BRIDGE_ACTION), 0)
            .mapNotNull { it.serviceInfo }
            .firstOrNull { info -> info.enabled && info.exported && isSameSigner(pm, context, info.packageName) }
            ?.let { ComponentName(it.packageName, it.name) }

    companion object {
        const val GOOGLE_PACKAGE = "com.google.android.googlequicksearchbox"

        /** Must match the action declared by the :discover-bridge module's service. */
        const val BRIDGE_ACTION = "io.github.aj4abinjacob.chikku.action.DISCOVER_BRIDGE"

        internal fun overlayIntent(context: Context, targetPackage: String): Intent =
            Intent("com.android.launcher3.WINDOW_OVERLAY")
                .setPackage(targetPackage)
                .setData("app://${context.packageName}:${Process.myUid()}?v=7&cv=9".toUri())
    }
}
