package io.github.aj4abinjacob.chikku.settings.cards

import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.cards.AutomationCard
import io.github.aj4abinjacob.chikku.core.model.cards.CardsSettings
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.core.model.settings.SettingsImportResult
import io.github.aj4abinjacob.chikku.core.model.settings.SettingsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Real editor mutation boundary with delayed and failed storage, not cosmetic UI assertions. */
@OptIn(ExperimentalCoroutinesApi::class)
class CardMutationTest {
    private class Repository : SettingsRepository {
        override val settings = MutableStateFlow(LauncherSettings())
        var updates = 0
        var fail = false
        var beforeWrite: suspend () -> Unit = {}
        override suspend fun update(transform: (LauncherSettings) -> LauncherSettings) {
            updates++
            val next = transform(settings.value)
            beforeWrite()
            if (fail) throw IllegalStateException("write failed")
            settings.value = next
        }
        override suspend fun reset() = Unit
        override suspend fun exportJson() = ""
        override suspend fun importJson(json: String) = SettingsImportResult.Rejected("unused")
    }

    @Test
    fun successWaitsForWriteBeforeCallerMayCloseDraftOrShowUndo() = runTest {
        val repository = Repository()
        val gate = CompletableDeferred<Unit>()
        repository.beforeWrite = { gate.await() }
        val card = AutomationCard("card", "Card")
        val result = async { persistCardMutation(repository, { saveCardDraft(it, card) }) }
        runCurrent()
        assertThat(result.isCompleted).isFalse()
        assertThat(repository.settings.value.cards.cards).isEmpty()
        gate.complete(Unit)
        assertThat(result.await()).isTrue()
        assertThat(repository.settings.value.cards.cards).containsExactly(card)
    }

    @Test
    fun unavailableTargetsSkipWritingAndFailedStorageCannotReportSuccess() = runTest {
        val repository = Repository()
        val card = AutomationCard("card", "Card")
        assertThat(persistCardMutation(repository, { it + card }, validate = { false })).isFalse()
        assertThat(repository.updates).isEqualTo(0)
        repository.fail = true
        val outcome = runCatching { persistCardMutation(repository, { it + card }) }
        assertThat(outcome.isFailure).isTrue()
        assertThat(repository.settings.value.cards.cards).isEmpty()
    }

    @Test
    fun validationRaceUsesLatestCardsAndDoesNotOverwriteOtherSettings() = runTest {
        val repository = Repository()
        val other = AutomationCard("other", "Other")
        val draft = AutomationCard("draft", "Draft")
        val saved = persistCardMutation(repository, { saveCardDraft(it, draft) }, validate = {
            repository.settings.value = repository.settings.value.copy(
                cards = CardsSettings(listOf(other)), home = repository.settings.value.home.copy(labelScale = 1.5f))
            true
        })
        assertThat(saved).isTrue()
        assertThat(repository.settings.value.cards.cards).containsExactly(other, draft).inOrder()
        assertThat(repository.settings.value.home.labelScale).isEqualTo(1.5f)
    }
}
