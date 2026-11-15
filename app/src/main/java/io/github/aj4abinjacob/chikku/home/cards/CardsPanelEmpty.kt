package io.github.aj4abinjacob.chikku.home.cards

import androidx.annotation.StringRes
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.cards.AutomationCard

/** What the Cards panel says when no card is showing. Each state offers exactly one next step. */
internal enum class CardsPanelEmpty(@StringRes val title: Int, @StringRes val body: Int, @StringRes val action: Int) {
    /** Cards are still being read or checked: show nothing rather than a misleading message. */
    PENDING(0, 0, 0),
    NO_CARDS(R.string.cards_surface_none_title, R.string.cards_surface_none_body, R.string.cards_surface_add_first),
    ALL_OFF(R.string.cards_surface_all_off_title, R.string.cards_surface_all_off_body, R.string.cards_surface_edit),
    NONE_MATCH(R.string.cards_surface_empty_title, R.string.cards_surface_empty_body, R.string.cards_surface_edit),
}

/** [cards] is null when the saved cards are unknown (for example after a read failure). */
internal fun cardsPanelEmpty(cards: List<AutomationCard>?): CardsPanelEmpty = when {
    cards == null -> CardsPanelEmpty.NONE_MATCH
    cards.isEmpty() -> CardsPanelEmpty.NO_CARDS
    cards.none { it.enabled } -> CardsPanelEmpty.ALL_OFF
    else -> CardsPanelEmpty.NONE_MATCH
}
