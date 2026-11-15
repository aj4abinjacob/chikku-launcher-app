package io.github.aj4abinjacob.chikku.home.widget

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.UserManager
import io.github.aj4abinjacob.chikku.core.model.ComponentKey
import io.github.aj4abinjacob.chikku.platform.ProfileAvailability

/**
 * Chikku's AppWidgetHost. [HOST_ID] is persistent: widget ids allocated under it survive app
 * updates, so it must never change. Listening is tied to the HOME activity's started state.
 */
class ChikkuWidgetHost(
    context: Context,
    private val onProvidersChangedListener: () -> Unit,
) : AppWidgetHost(context.applicationContext, HOST_ID) {

    override fun onProvidersChanged() {
        onProvidersChangedListener()
    }

    companion object {
        const val HOST_ID = 0x0C41
    }
}

/** [WidgetPlatform] over the real host and AppWidgetManager. Never throws. */
class AndroidWidgetPlatform(
    context: Context,
    private val host: AppWidgetHost,
) : WidgetPlatform {
    private val manager: AppWidgetManager? = AppWidgetManager.getInstance(context.applicationContext)
    private val userManager: UserManager? = context.getSystemService(UserManager::class.java)
    private val profiles = ProfileAvailability(context.applicationContext)

    override fun allocateId(): Int = safe(0) { host.allocateAppWidgetId() }

    override fun deleteId(appWidgetId: Int) {
        if (appWidgetId == 0) return
        safe(Unit) { host.deleteAppWidgetId(appWidgetId) }
    }

    override fun bindIfAllowed(appWidgetId: Int, provider: ComponentKey): Boolean = safe(false) {
        val user = userManager?.getUserForSerialNumber(provider.userSerial) ?: return@safe false
        if (!profiles.isAvailable(user)) return@safe false
        manager?.bindAppWidgetIdIfAllowed(appWidgetId, user, ComponentName(provider.packageName, provider.className), null) == true
    }

    override fun boundProvider(appWidgetId: Int): ComponentKey? = info(appWidgetId)?.let(::keyOf)

    override fun needsConfiguration(appWidgetId: Int): Boolean {
        val info = info(appWidgetId) ?: return false
        if (info.configure == null) return false
        val optional = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            (info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL) != 0
        return !optional
    }

    override fun hostIds(): IntArray = safe(IntArray(0)) { host.appWidgetIds }

    fun info(appWidgetId: Int): AppWidgetProviderInfo? =
        if (appWidgetId == 0) null else safe(null) {
            manager?.getAppWidgetInfo(appWidgetId)?.takeIf { profiles.isAvailable(it.profile) }
        }

    fun keyOf(info: AppWidgetProviderInfo): ComponentKey? = safe(null) {
        val serial = userManager?.getSerialNumberForUser(info.profile) ?: return@safe null
        ComponentKey(info.provider.packageName, info.provider.className, serial)
    }

    /** True if the provider may be reconfigured after placement (API 28+ feature flag). */
    fun isReconfigurable(info: AppWidgetProviderInfo): Boolean =
        info.configure != null && (info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE) != 0

    private inline fun <T> safe(fallback: T, block: () -> T): T = try {
        block()
    } catch (_: RuntimeException) {
        // SecurityException, IllegalArgumentException, DeadSystemException wrapped as RuntimeException.
        fallback
    }
}

/** Copies the provider's size hints (already in pixels for this display) into a pure [WidgetSizeSpec]. */
fun AppWidgetProviderInfo.sizeSpec(): WidgetSizeSpec {
    val s = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    return WidgetSizeSpec(
        minWidth = minWidth,
        minHeight = minHeight,
        minResizeWidth = minResizeWidth,
        minResizeHeight = minResizeHeight,
        maxResizeWidth = if (s) maxResizeWidth else 0,
        maxResizeHeight = if (s) maxResizeHeight else 0,
        targetCellWidth = if (s) targetCellWidth else 0,
        targetCellHeight = if (s) targetCellHeight else 0,
        resizeHorizontal = (resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL) != 0,
        resizeVertical = (resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL) != 0,
    )
}
