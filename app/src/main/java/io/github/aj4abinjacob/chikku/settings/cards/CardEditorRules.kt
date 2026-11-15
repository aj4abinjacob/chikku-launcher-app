package io.github.aj4abinjacob.chikku.settings.cards

import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.GestureAction
import io.github.aj4abinjacob.chikku.core.model.cards.AutomationCard
import io.github.aj4abinjacob.chikku.core.model.cards.CardFeatured
import io.github.aj4abinjacob.chikku.core.model.cards.CardContent
import io.github.aj4abinjacob.chikku.core.model.cards.CardsSettings
import io.github.aj4abinjacob.chikku.core.model.cards.CardTargetSnapshot
import io.github.aj4abinjacob.chikku.core.model.cards.CardVisibility

/**
 * Launcher actions offered as card tiles. "Open cards" is left out because the cards panel is
 * already open; a stored card that has it still decodes and runs (no silent reset).
 */
internal val cardActionChoices: List<GestureAction> =
    GestureAction.simpleActions - GestureAction.None - GestureAction.OpenCards

/** Null means the UI must use a generic recovery row, including its accessibility labels. */
internal fun cardEditorTitle(card: AutomationCard, targets: CardTargetSnapshot?): String? {
    val allowed = if (targets != null) CardVisibility.targetsAllowed(card, targets)
    else (card.content as? CardContent.Widget)?.provider == null && card.featured == CardFeatured.None && card.actions.none { it is GestureAction.LaunchApp || it is GestureAction.LaunchShortcut }
    return card.title.takeIf { allowed }
}

/** Reject obsolete/invisible editor choices instead of substituting another profile's app. */
internal fun cardTargetsAvailable(card: AutomationCard, personalKeys: Set<AppKey>, widgetIds: Set<String>): Boolean {
    fun shortcutAvailable(packageName: String, serial: Long): Boolean =
        personalKeys.any { it.packageName == packageName && it.userSerial == serial }
    val featuredAvailable = when (val featured = card.featured) {
        CardFeatured.None -> true
        is CardFeatured.App -> featured.key in personalKeys
        is CardFeatured.Shortcut -> shortcutAvailable(featured.packageName, featured.userSerial)
        is CardFeatured.Widget -> featured.itemId in widgetIds
    }
    return featuredAvailable && card.actions.all { action ->
        when (action) {
            is GestureAction.LaunchApp -> action.key in personalKeys
            is GestureAction.LaunchShortcut -> shortcutAvailable(action.packageName, action.userSerial)
            else -> true
        }
    }
}

/** A draft is independent until save; replacements merge into the latest settings list. */
internal fun saveCardDraft(cards: List<AutomationCard>, draft: AutomationCard, requireExisting: Boolean = false): List<AutomationCard>? {
    val title = draft.title.trim()
    if (draft.id.isBlank() || draft.id.length > 80 || title.isEmpty() || title.length > CardsSettings.MAX_TITLE_LENGTH ||
        title.any { it.isISOControl() } ||
        draft.actions.size > CardsSettings.ACTION_COUNT
    ) return null
    val index = cards.indexOfFirst { it.id == draft.id }
    if (requireExisting && index == -1) return null
    if (cards.count { it.id == draft.id } > 1 || (index == -1 && cards.size >= CardsSettings.MAX_CARDS)) return null
    val normalized = draft.copy(title = title, actions = draft.actions +
        List(CardsSettings.ACTION_COUNT - draft.actions.size) { GestureAction.None })
    return if (index == -1) cards + normalized else cards.toMutableList().apply { set(index, normalized) }
}

internal fun moveCard(cards: List<AutomationCard>, id: String, delta: Int): List<AutomationCard> {
    if (delta != -1 && delta != 1) return cards
    val from = cards.indexOfFirst { it.id == id }
    val to = from + delta
    if (from < 0 || to !in cards.indices) return cards
    return cards.toMutableList().apply { add(to, removeAt(from)) }
}

internal data class RemovedCard(val card: AutomationCard, val index: Int)

/** Undo keeps unrelated intervening edits and never exceeds the card cap or duplicates identity. */
internal fun restoreRemovedCard(cards: List<AutomationCard>, removed: RemovedCard): List<AutomationCard> {
    if (cards.size >= CardsSettings.MAX_CARDS || cards.any { it.id == removed.card.id }) return cards
    return cards.toMutableList().apply { add(removed.index.coerceIn(0, size), removed.card) }
}

/** Moves [id] to [to] (clamped), as a drag-and-drop reorder would. */
internal fun moveCardTo(cards: List<AutomationCard>, id: String, to: Int): List<AutomationCard> {
    val from = cards.indexOfFirst { it.id == id }
    if (from < 0) return cards
    val target = to.coerceIn(0, cards.lastIndex)
    if (target == from) return cards
    return cards.toMutableList().apply { add(target, removeAt(from)) }
}
