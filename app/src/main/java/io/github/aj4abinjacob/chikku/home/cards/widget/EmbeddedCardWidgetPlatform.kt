package io.github.aj4abinjacob.chikku.home.cards.widget

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.UserManager
import io.github.aj4abinjacob.chikku.core.model.ComponentKey

interface EmbeddedCardWidgetPlatform : CardWidgetBindingPlatform {
    fun info(id: Int): AppWidgetProviderInfo?
    /** UI view creation is the only host operation that belongs on main. */
    fun createView(context: Context, id: Int, info: AppWidgetProviderInfo): AppWidgetHostView
    fun clearViews()
    fun startListening()
    fun stopListening()
    fun consentIntent(bindingId: Int, provider: ComponentKey): Intent
    fun configure(activity: Activity, id: Int, requestCode: Int)
    fun updateSize(id: Int, widthDp: Int, heightDp: Int)
}

class AndroidEmbeddedCardWidgetPlatform(context: Context, private val onProvidersChanged: () -> Unit = {}) : EmbeddedCardWidgetPlatform {
    private val context = context.applicationContext
    private val users = context.getSystemService(UserManager::class.java)
    private val manager = AppWidgetManager.getInstance(context)
    private val catalog = AndroidCardWidgetCatalogSource(context)
    private val host = object : AppWidgetHost(this.context, HOST_ID) {
        override fun onProvidersChanged() { this@AndroidEmbeddedCardWidgetPlatform.onProvidersChanged.invoke() }
        fun releaseViews() = clearViews()
    }
    override fun ownedIds(): Set<Int> = runCatching { host.appWidgetIds.toSet() }.getOrDefault(emptySet())
    override fun allocateId(): Int = runCatching { host.allocateAppWidgetId() }.getOrDefault(0)
    override fun deleteId(id: Int) { if (id in ownedIds()) runCatching { host.deleteAppWidgetId(id) } }
    override fun providerAvailable(provider: ComponentKey): Boolean = catalog.find(provider) != null
    override fun bindIfAllowed(id: Int, provider: ComponentKey): Boolean = runCatching {
        if (id !in ownedIds() || catalog.personalSerial() != provider.userSerial) return@runCatching false
        manager.bindAppWidgetIdIfAllowed(id, Process.myUserHandle(), ComponentName(provider.packageName, provider.className), null)
    }.getOrDefault(false)
    override fun boundProvider(id: Int): ComponentKey? = info(id)?.let { info -> runCatching {
        val serial = users?.getSerialNumberForUser(info.profile) ?: return@runCatching null
        ComponentKey(info.provider.packageName, info.provider.className, serial)
    }.getOrNull() }
    override fun needsConfiguration(id: Int): Boolean = info(id)?.let {
        it.configure != null && !(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            it.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL != 0)
    } ?: false
    override fun info(id: Int): AppWidgetProviderInfo? = runCatching {
        if (id !in ownedIds()) return@runCatching null
        manager.getAppWidgetInfo(id)?.takeIf { it.profile == Process.myUserHandle() && catalog.personalSerial() != null }
    }.getOrNull()
    override fun createView(context: Context, id: Int, info: AppWidgetProviderInfo): AppWidgetHostView = host.createView(context, id, info)
    override fun clearViews() = host.releaseViews()
    override fun startListening() = host.startListening()
    override fun stopListening() = host.stopListening()
    override fun consentIntent(bindingId: Int, provider: ComponentKey): Intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, bindingId)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, ComponentName(provider.packageName, provider.className))
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, Process.myUserHandle())
    override fun configure(activity: Activity, id: Int, requestCode: Int) {
        host.startAppWidgetConfigureActivityForResult(activity, id, 0, requestCode, null)
    }
    override fun updateSize(id: Int, widthDp: Int, heightDp: Int) {
        if (id !in ownedIds()) return
        manager.updateAppWidgetOptions(id, Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
        })
    }
    companion object { const val HOST_ID = 0x43415244 }
}
