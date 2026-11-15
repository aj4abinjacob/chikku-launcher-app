package io.github.aj4abinjacob.chikku.core.model.cards

import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.ComponentKey
import io.github.aj4abinjacob.chikku.core.model.GestureAction
import io.github.aj4abinjacob.chikku.core.model.LauncherApp
import io.github.aj4abinjacob.chikku.core.model.ProfileType

/** Ephemeral validated targets. Never persisted as an inventory or included in diagnostics. */
data class CardTargetSnapshot(
    val apps: List<LauncherApp>,
    val hidden: Set<AppKey>,
    val personalUserSerial: Long?,
    val shortcuts: Set<CardFeatured.Shortcut> = emptySet(),
    val widgets: Map<String, ComponentKey> = emptyMap(),
)

/** Withhold the whole card, including its name, when any referenced target is inaccessible. */
object CardVisibility {
    fun appAllowed(key: AppKey, targets: CardTargetSnapshot): Boolean =
        key.userSerial == targets.personalUserSerial && key !in targets.hidden &&
            targets.apps.any { it.key == key && it.profileType == ProfileType.PERSONAL }

    fun packageAllowed(packageName: String, userSerial: Long, targets: CardTargetSnapshot): Boolean =
        userSerial == targets.personalUserSerial &&
            targets.hidden.none { it.packageName == packageName && it.userSerial == userSerial } &&
            targets.apps.any {
                it.key.packageName == packageName && it.key.userSerial == userSerial &&
                    it.profileType == ProfileType.PERSONAL
            }

    fun targetsAllowed(card: AutomationCard, targets: CardTargetSnapshot): Boolean {
        fun shortcutAllowed(shortcut: CardFeatured.Shortcut) =
            packageAllowed(shortcut.packageName, shortcut.userSerial, targets) && shortcut in targets.shortcuts
        val featuredAllowed = when (val featured = card.featured) {
            CardFeatured.None -> true
            is CardFeatured.App -> appAllowed(featured.key, targets)
            is CardFeatured.Shortcut -> shortcutAllowed(featured)
            is CardFeatured.Widget -> targets.widgets[featured.itemId]?.let { provider ->
                provider.userSerial == targets.personalUserSerial && targets.personalUserSerial != null &&
                    targets.hidden.none { it.packageName == provider.packageName && it.userSerial == provider.userSerial }
            } == true
        }
        val contentAllowed = (card.content as? CardContent.Widget)?.provider?.let { provider ->
            targets.personalUserSerial != null && provider.userSerial == targets.personalUserSerial &&
                targets.hidden.none { it.packageName == provider.packageName && it.userSerial == provider.userSerial }
        } ?: true
        return contentAllowed && featuredAllowed && card.actions.all { action ->
            when (action) {
                is GestureAction.LaunchApp -> appAllowed(action.key, targets)
                is GestureAction.LaunchShortcut -> shortcutAllowed(
                    CardFeatured.Shortcut(action.packageName, action.shortcutId, action.userSerial),
                )
                else -> true
            }
        }
    }
}
