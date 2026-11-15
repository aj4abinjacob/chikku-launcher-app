package io.github.aj4abinjacob.chikku.settings.cards

import io.github.aj4abinjacob.chikku.core.model.cards.AutomationCard
import io.github.aj4abinjacob.chikku.core.model.settings.SettingsRepository

/** Platform validation completes before the mutex-held transform; success requires a durable write. */
internal suspend fun persistCardMutation(
    repository: SettingsRepository,
    transform: (List<AutomationCard>) -> List<AutomationCard>?,
    validate: suspend () -> Boolean = { true },
): Boolean {
    if (!validate()) return false
    var accepted = false
    repository.update { current ->
        val next = transform(current.cards.cards)
        if (next == null) current else {
            accepted = true
            current.copy(cards = current.cards.copy(cards = next))
        }
    }
    return accepted
}
