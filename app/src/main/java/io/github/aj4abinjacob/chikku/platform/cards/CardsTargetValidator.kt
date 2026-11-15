package io.github.aj4abinjacob.chikku.platform.cards

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.os.Process
import android.os.UserManager
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.GestureAction
import io.github.aj4abinjacob.chikku.core.model.ItemTarget
import io.github.aj4abinjacob.chikku.core.model.LauncherApp
import io.github.aj4abinjacob.chikku.core.model.WorkspaceItem
import io.github.aj4abinjacob.chikku.core.model.cards.AutomationCard
import io.github.aj4abinjacob.chikku.core.model.cards.CardFeatured
import io.github.aj4abinjacob.chikku.core.model.cards.CardTargetSnapshot
import io.github.aj4abinjacob.chikku.core.model.cards.CardVisibility
import io.github.aj4abinjacob.chikku.platform.ShortcutMetadataPolicy
import io.github.aj4abinjacob.chikku.platform.isEnabledFor
import io.github.aj4abinjacob.chikku.platform.personalUserSerial
import io.github.aj4abinjacob.chikku.platform.shortcutQuery

data class CardValidation(
    val targets: CardTargetSnapshot,
    val shortcutLabels: Map<CardFeatured.Shortcut, String> = emptyMap(),
    val widgetLabels: Map<String, String> = emptyMap(),
)

fun interface CardsTargetValidator {
    /** Resolves only referenced targets. Call off main; unavailable identities fail closed. */
    suspend fun validate(
        cards: List<AutomationCard>, apps: List<LauncherApp>, hidden: Set<AppKey>, items: List<WorkspaceItem>,
    ): CardValidation
}

class AndroidCardsTargetValidator(context: Context, private val metadata: ShortcutMetadataPolicy = ShortcutMetadataPolicy.DENY) : CardsTargetValidator {
    private val context = context.applicationContext
    private val users = context.getSystemService(UserManager::class.java)
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val widgets = AppWidgetManager.getInstance(context)

    override suspend fun validate(
        cards: List<AutomationCard>, apps: List<LauncherApp>, hidden: Set<AppKey>, items: List<WorkspaceItem>,
    ): CardValidation {
        val personal = Process.myUserHandle()
        val serial = users.personalUserSerial(requireUnlocked = true)
        val base = CardTargetSnapshot(apps, hidden, serial)
        if (serial == null) return CardValidation(base.copy(apps = emptyList()))
        val requestedApps = mutableSetOf<AppKey>()
        val requestedShortcuts = mutableSetOf<CardFeatured.Shortcut>()
        val requestedWidgets = mutableSetOf<String>()
        cards.forEach { card ->
            when (val featured = card.featured) {
                CardFeatured.None -> Unit
                is CardFeatured.App -> requestedApps += featured.key
                is CardFeatured.Shortcut -> requestedShortcuts += featured
                is CardFeatured.Widget -> requestedWidgets += featured.itemId
            }
            card.actions.forEach { action ->
                when (action) {
                    is GestureAction.LaunchApp -> requestedApps += action.key
                    is GestureAction.LaunchShortcut -> requestedShortcuts +=
                        CardFeatured.Shortcut(action.packageName, action.shortcutId, action.userSerial)
                    else -> Unit
                }
            }
        }
        val verifiedApps = requestedApps.filterTo(mutableSetOf()) { key ->
            CardVisibility.appAllowed(key, base) && runCatching {
                launcherApps?.isActivityEnabled(ComponentName(key.packageName, key.className), personal) == true
            }.getOrDefault(false)
        }
        val shortcutLabels = mutableMapOf<CardFeatured.Shortcut, String>()
        requestedShortcuts.forEach { shortcut ->
            if (!metadata.mayRead(shortcut.packageName, shortcut.userSerial)) return@forEach
            if (!CardVisibility.packageAllowed(shortcut.packageName, shortcut.userSerial, base)) return@forEach
            runCatching {
                if (launcherApps?.hasShortcutHostPermission() != true) return@runCatching
                val query = shortcutQuery(shortcut.packageName, listOf(shortcut.shortcutId))
                launcherApps.getShortcuts(query, personal)?.firstOrNull {
                    it.id == shortcut.shortcutId && it.isEnabledFor(shortcut.packageName, personal)
                }?.let { info ->
                    if (!metadata.mayRead(shortcut.packageName, shortcut.userSerial)) return@let
                    shortcutLabels[shortcut] = (info.shortLabel ?: info.longLabel ?: shortcut.shortcutId).toString()
                }
            }
        }
        val widgetProviders = mutableMapOf<String, io.github.aj4abinjacob.chikku.core.model.ComponentKey>()
        val widgetLabels = mutableMapOf<String, String>()
        requestedWidgets.forEach { id ->
            val target = items.firstOrNull { it.id == id }?.target as? ItemTarget.Widget ?: return@forEach
            val provider = target.provider
            if (provider.userSerial != serial || hidden.any {
                    it.packageName == provider.packageName && it.userSerial == provider.userSerial
                } || target.appWidgetId <= 0) return@forEach
            runCatching {
                val info = widgets.getAppWidgetInfo(target.appWidgetId) ?: return@runCatching
                if (info.profile != personal || info.provider != ComponentName(provider.packageName, provider.className)) return@runCatching
                widgetProviders[id] = provider
                widgetLabels[id] = info.loadLabel(context.packageManager).toString()
            }
        }
        shortcutLabels.keys.removeAll { !metadata.mayRead(it.packageName, it.userSerial) }
        // Shortcut owners remain solely for package/profile visibility checks. App targets require
        // the native activity check above as well as the repository's visible personal identity.
        val safeApps = apps.filter { app ->
            app.key in verifiedApps || (app.key !in requestedApps && requestedShortcuts.any {
                it in shortcutLabels && it.packageName == app.key.packageName && it.userSerial == app.key.userSerial
            })
        }
        return CardValidation(base.copy(apps = safeApps, shortcuts = shortcutLabels.keys.toSet(), widgets = widgetProviders),
            shortcutLabels, widgetLabels)
    }
}
