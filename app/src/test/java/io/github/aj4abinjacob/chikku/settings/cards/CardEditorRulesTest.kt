package io.github.aj4abinjacob.chikku.settings.cards

import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.GestureAction
import io.github.aj4abinjacob.chikku.core.model.LauncherApp
import io.github.aj4abinjacob.chikku.core.model.ProfileType
import io.github.aj4abinjacob.chikku.core.model.cards.AutomationCard
import io.github.aj4abinjacob.chikku.core.model.cards.CardConditions
import io.github.aj4abinjacob.chikku.core.model.cards.CardFeatured
import io.github.aj4abinjacob.chikku.core.model.cards.CardsSettings
import io.github.aj4abinjacob.chikku.core.model.cards.CardTargetSnapshot
import org.junit.Test

class CardEditorRulesTest {
    private val app = AppKey("example.app", "example.app.Main", 0)

    @Test
    fun saveMergesIntoLatestOrderWithoutTouchingOtherCardsAndTrimsName() {
        val original = AutomationCard("editing", "Original")
        val addedMeanwhile = AutomationCard("new", "Added meanwhile")
        val draft = original.copy(title = "  Evening  ", actions = listOf(GestureAction.OpenSearch))
        val saved = requireNotNull(saveCardDraft(listOf(addedMeanwhile, original), draft, requireExisting = true))
        assertThat(saved.first()).isEqualTo(addedMeanwhile)
        assertThat(saved.last().title).isEqualTo("Evening")
        assertThat(saved.last().actions).hasSize(6)
        assertThat(saved.last().actions.first()).isEqualTo(GestureAction.OpenSearch)
        assertThat(original.title).isEqualTo("Original") // draft/cancel never alters stored data
    }

    @Test
    fun capRejectsNewCardsButAllowsEditingAndStaleEditsCannotRecreateDeletedCards() {
        val full = (0 until CardsSettings.MAX_CARDS).map { AutomationCard("$it", "Card $it") }
        assertThat(saveCardDraft(full, AutomationCard("extra", "Extra"))).isNull()
        assertThat(saveCardDraft(full, full[0].copy(title = "Changed"), requireExisting = true)).isNotNull()
        assertThat(saveCardDraft(emptyList(), full[0], requireExisting = true)).isNull()
    }

    @Test
    fun invalidNamesIdsOrTooManyActionsAreRejectedWithoutChangingList() {
        val card = AutomationCard("valid", "Valid")
        listOf(card.copy(title = " "), card.copy(title = "x".repeat(81)), card.copy(title = "Line\nbreak"),
            card.copy(id = ""), card.copy(id = "x".repeat(81)), card.copy(actions = List(7) { GestureAction.OpenDrawer }))
            .forEach { assertThat(saveCardDraft(emptyList(), it)).isNull() }
        assertThat(saveCardDraft(listOf(card, card), card)).isNull()
    }

    @Test
    fun actionAndFeaturedChoicesUseExactComponentAndProfileAndExistingWidgetIds() {
        val samePackageOtherProfile = app.copy(userSerial = 10)
        val samePackageOtherActivity = app.copy(className = "example.app.Other")
        val base = AutomationCard("card", "Card", featured = CardFeatured.App(app))
        assertThat(cardTargetsAvailable(base, setOf(app), emptySet())).isTrue()
        assertThat(cardTargetsAvailable(base.copy(featured = CardFeatured.App(samePackageOtherActivity)), setOf(app), emptySet())).isFalse()
        assertThat(cardTargetsAvailable(base.copy(actions = listOf(GestureAction.LaunchApp(samePackageOtherProfile))), setOf(app), emptySet())).isFalse()
        assertThat(cardTargetsAvailable(base.copy(featured = CardFeatured.Shortcut(app.packageName, "shortcut", 10)), setOf(app), emptySet())).isFalse()
        val widget = base.copy(featured = CardFeatured.Widget("workspace-widget"))
        assertThat(cardTargetsAvailable(widget, emptySet(), setOf("workspace-widget"))).isTrue()
        assertThat(cardTargetsAvailable(widget, emptySet(), emptySet())).isFalse()
    }

    @Test
    fun reorderMovesOneCardAndPreservesContentAndBoundaryMoves() {
        val cards = listOf(AutomationCard("a", "A", conditions = CardConditions(chargingOnly = true)),
            AutomationCard("b", "B"), AutomationCard("c", "C"))
        assertThat(moveCard(cards, "b", -1)).containsExactly(cards[1], cards[0], cards[2]).inOrder()
        assertThat(moveCard(cards, "b", 1)).containsExactly(cards[0], cards[2], cards[1]).inOrder()
        assertThat(moveCard(cards, "a", -1)).isEqualTo(cards)
        assertThat(moveCard(cards, "missing", 1)).isEqualTo(cards)
        assertThat(moveCard(cards, "b", 2)).isEqualTo(cards)
    }

    @Test
    fun deleteUndoKeepsInterveningEditsAndRejectsDuplicateOrFullRestore() {
        val deleted = RemovedCard(AutomationCard("b", "B"), 1)
        val remaining = listOf(AutomationCard("a", "Edited A"), AutomationCard("c", "New C"))
        val restored = restoreRemovedCard(remaining, deleted)
        assertThat(restored).containsExactly(remaining[0], deleted.card, remaining[1]).inOrder()
        assertThat(restoreRemovedCard(restored, deleted)).isEqualTo(restored)
        val full = (0 until 24).map { AutomationCard("other-$it", "Other") }
        assertThat(restoreRemovedCard(full, deleted)).isEqualTo(full)
    }

    @Test
    fun settingsTitlesAreWithheldForHiddenUnavailableAndOtherProfileReferences() {
        val card = AutomationCard("private", "Sensitive context", featured = CardFeatured.App(app))
        val visible = CardTargetSnapshot(listOf(LauncherApp(app, "App", ProfileType.PERSONAL)), emptySet(), 0)
        assertThat(cardEditorTitle(card, visible)).isEqualTo("Sensitive context")
        assertThat(cardEditorTitle(card, null)).isNull()
        assertThat(cardEditorTitle(card, visible.copy(hidden = setOf(app)))).isNull()
        assertThat(cardEditorTitle(card, visible.copy(apps = emptyList()))).isNull()
        val otherProfile = app.copy(userSerial = 10)
        assertThat(cardEditorTitle(card.copy(featured = CardFeatured.App(otherProfile)),
            visible.copy(apps = visible.apps + LauncherApp(otherProfile, "Private", ProfileType.OTHER)))).isNull()
        assertThat(cardEditorTitle(card.copy(featured = CardFeatured.None, actions = listOf(GestureAction.LaunchApp(otherProfile))), visible)).isNull()
        val simple = AutomationCard("simple", "Everyday", actions = listOf(GestureAction.OpenDrawer))
        assertThat(cardEditorTitle(simple, null)).isEqualTo("Everyday")
    }

    @Test
    fun dragAndDropMoveKeepsEveryCardAndClampsTheDropPosition() {
        val cards = listOf("a", "b", "c", "d").map { AutomationCard(it, it.uppercase()) }
        assertThat(moveCardTo(cards, "a", 2).map { it.id }).containsExactly("b", "c", "a", "d").inOrder()
        assertThat(moveCardTo(cards, "d", 0).map { it.id }).containsExactly("d", "a", "b", "c").inOrder()
        assertThat(moveCardTo(cards, "b", 99).map { it.id }).containsExactly("a", "c", "d", "b").inOrder()
        assertThat(moveCardTo(cards, "missing", 0)).isSameInstanceAs(cards)
        assertThat(moveCardTo(cards, "c", 2)).isSameInstanceAs(cards)
    }

    @Test
    fun cardActionChoicesLeaveOutOpenCardsAndNoneButKeepOtherLauncherActions() {
        assertThat(cardActionChoices).doesNotContain(GestureAction.OpenCards)
        assertThat(cardActionChoices).doesNotContain(GestureAction.None)
        assertThat(cardActionChoices).contains(GestureAction.OpenSearch)
        assertThat(cardActionChoices).containsExactlyElementsIn(
            GestureAction.simpleActions - GestureAction.None - GestureAction.OpenCards)
        // A stored card that already has "Open cards" still validates; nothing is reset.
        assertThat(AutomationCard("stored", "Stored", actions = listOf(GestureAction.OpenCards)).coerced().actions.first())
            .isEqualTo(GestureAction.OpenCards)
    }
}
