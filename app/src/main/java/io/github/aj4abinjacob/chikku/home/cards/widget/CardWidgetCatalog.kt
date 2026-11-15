package io.github.aj4abinjacob.chikku.home.cards.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Process
import android.os.UserManager
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.ComponentKey
import io.github.aj4abinjacob.chikku.platform.personalUserSerial

/** A widget a card can host. [appLabel] names the app that provides it, so same-named widgets differ. */
data class CardWidgetProviderOption(val key: ComponentKey, val label: String, val appLabel: String = "")

interface CardWidgetCatalogSource {
    fun personalSerial(): Long?
    fun load(hidden: Set<AppKey>): List<CardWidgetProviderOption>
}

class AndroidCardWidgetCatalogSource(context: Context) : CardWidgetCatalogSource {
    private val context = context.applicationContext
    private val users = context.getSystemService(UserManager::class.java)
    private val manager = AppWidgetManager.getInstance(context)

    override fun personalSerial(): Long? = users.personalUserSerial(requireUnlocked = true)

    override fun load(hidden: Set<AppKey>): List<CardWidgetProviderOption> {
        val serial = personalSerial() ?: return emptyList()
        val pm = context.packageManager
        val appLabels = HashMap<String, String>()
        return runCatching {
            manager.getInstalledProvidersForProfile(Process.myUserHandle()).orEmpty().filter { info ->
                info.profile == Process.myUserHandle() && hidden.none {
                    it.packageName == info.provider.packageName && it.userSerial == serial
                }
            }.map { info ->
                val pkg = info.provider.packageName
                val app = appLabels.getOrPut(pkg) {
                    runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
                }
                CardWidgetProviderOption(keyOf(info, serial), info.loadLabel(pm).toString(), app)
            }
                // Grouped by app, as in the home widget picker, then by widget name.
                .distinctBy { it.key }.sortedWith(compareBy({ it.appLabel.lowercase() }, { it.label.lowercase() }))
        }.getOrDefault(emptyList())
    }

    internal fun find(provider: ComponentKey): AppWidgetProviderInfo? {
        val serial = personalSerial() ?: return null
        if (provider.userSerial != serial) return null
        return runCatching { manager.getInstalledProvidersForProfile(Process.myUserHandle()).firstOrNull {
            it.profile == Process.myUserHandle() && keyOf(it, serial) == provider
        } }.getOrNull()
    }

    private fun keyOf(info: AppWidgetProviderInfo, serial: Long) =
        ComponentKey(info.provider.packageName, info.provider.className, serial)
}
