package io.github.aj4abinjacob.chikku.settings.cards

import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.GestureAction
import io.github.aj4abinjacob.chikku.core.model.cards.AutomationCard
import io.github.aj4abinjacob.chikku.core.model.cards.CardFeatured
import io.github.aj4abinjacob.chikku.core.model.cards.CardsSettings
import io.github.aj4abinjacob.chikku.settings.sections.cardsInShownOrder
import org.junit.Test

/**
 * Pure rules that were inline in CardEditorDialog, CardsSettingsSection and CardReorderList.
 * Expected values are worked out by hand from the original inline code, not from the new functions.
 */
class CardSettingsExtractedRulesTest {
    private val gap = 8f
    private val uniform: (Int) -> Int = { 100 } // every slot is 108 px; half is 54

    @Test
    fun reorderCrossesANeighbourOnlyPastHalfItsSlot() {
        assertThat(reorderTargetIndex(5, 0, 54f, gap, uniform)).isEqualTo(0)
        assertThat(reorderTargetIndex(5, 0, 55f, gap, uniform)).isEqualTo(1)
        assertThat(reorderTargetIndex(5, 0, 200f, gap, uniform)).isEqualTo(2) // 200-108=92, 92-108=-16
        assertThat(reorderTargetIndex(5, 2, -54f, gap, uniform)).isEqualTo(2)
        assertThat(reorderTargetIndex(5, 2, -55f, gap, uniform)).isEqualTo(1)
    }

    @Test
    fun reorderIsClampedToTheList() {
        assertThat(reorderTargetIndex(5, 0, -1000f, gap, uniform)).isEqualTo(0)
        assertThat(reorderTargetIndex(5, 4, 1000f, gap, uniform)).isEqualTo(4)
        assertThat(reorderTargetIndex(1, 0, 1000f, gap, uniform)).isEqualTo(0)
    }

    @Test
    fun unmeasuredNeighbourCountsAsGapOnly() {
        // height 0 -> slot 8, half 4: 5px crosses it.
        assertThat(reorderTargetIndex(3, 0, 5f, gap) { 0 }).isEqualTo(1)
        assertThat(reorderTargetIndex(3, 0, 4f, gap) { 0 }).isEqualTo(0)
    }

    @Test
    fun slotOffsetSumsTheCrossedSlotsWithTheirSign() {
        val heights = intArrayOf(10, 20, 30, 40)
        assertThat(reorderSlotOffset(1, 3, 5f) { heights[it] }).isEqualTo(80f) // (30+5)+(40+5)
        assertThat(reorderSlotOffset(3, 1, 5f) { heights[it] }).isEqualTo(-60f) // -((20+5)+(30+5))
        assertThat(reorderSlotOffset(2, 2, 5f) { heights[it] }).isEqualTo(0f)
    }

    @Test
    fun shiftOpensTheSlotAndLeavesOthersAlone() {
        // Dragged down from 1 to 3: items 2 and 3 move up.
        assertThat(reorderShift(2, 1, 3, false, 50f)).isEqualTo(-50f)
        assertThat(reorderShift(3, 1, 3, false, 50f)).isEqualTo(-50f)
        assertThat(reorderShift(0, 1, 3, false, 50f)).isEqualTo(0f)
        assertThat(reorderShift(4, 1, 3, false, 50f)).isEqualTo(0f)
        assertThat(reorderShift(1, 1, 3, true, 50f)).isEqualTo(0f)
        // Dragged up from 3 to 1: items 1 and 2 move down.
        assertThat(reorderShift(1, 3, 1, false, 50f)).isEqualTo(50f)
        assertThat(reorderShift(2, 3, 1, false, 50f)).isEqualTo(50f)
        assertThat(reorderShift(3, 3, 1, false, 50f)).isEqualTo(0f)
        // Nothing is dragged.
        assertThat(reorderShift(2, -1, -1, false, 0f)).isEqualTo(0f)
    }

    @Test
    fun edgeScrollRampsInsideEachZoneAndBottomWins() {
        assertThat(edgeScrollFraction(400f, 500f, 0f, 1000f, 100f)).isEqualTo(0f)
        assertThat(edgeScrollFraction(800f, 950f, 0f, 1000f, 100f)).isEqualTo(0.5f)
        assertThat(edgeScrollFraction(900f, 1200f, 0f, 1000f, 100f)).isEqualTo(1f)
        assertThat(edgeScrollFraction(50f, 150f, 0f, 1000f, 100f)).isEqualTo(-0.5f)
        assertThat(edgeScrollFraction(-500f, 150f, 0f, 1000f, 100f)).isEqualTo(-1f)
        assertThat(edgeScrollFraction(50f, 950f, 0f, 1000f, 100f)).isEqualTo(0.5f)
    }

    @Test
    fun titleMustBeTrimmedNonEmptyShortAndWithoutControlCharacters() {
        assertThat(cardTitleValid("Evening")).isTrue()
        assertThat(cardTitleValid("  Evening  ")).isTrue()
        assertThat(cardTitleValid("   ")).isFalse()
        assertThat(cardTitleValid("")).isFalse()
        assertThat(cardTitleValid("a".repeat(CardsSettings.MAX_TITLE_LENGTH))).isTrue()
        assertThat(cardTitleValid("a".repeat(CardsSettings.MAX_TITLE_LENGTH + 1))).isFalse()
        assertThat(cardTitleValid("bad\u0007name")).isFalse()
    }

    @Test
    fun actionsArePaddedToTheStoredSlotCount() {
        val padded = padCardActions(listOf(GestureAction.OpenSearch))
        assertThat(padded).hasSize(CardsSettings.ACTION_COUNT)
        assertThat(padded.first()).isEqualTo(GestureAction.OpenSearch)
        assertThat(padded.drop(1).toSet()).containsExactly(GestureAction.None)
    }

    @Test
    fun lastTileRowIsFilledToFourCells() {
        // The "add" tile counts while there is room: tiles = n + 1 below ACTION_COUNT (6).
        assertThat(cardActionRowPadding(0)).isEqualTo(3) // 1 tile
        assertThat(cardActionRowPadding(3)).isEqualTo(0) // 4 tiles
        assertThat(cardActionRowPadding(4)).isEqualTo(3) // 5 tiles
        assertThat(cardActionRowPadding(5)).isEqualTo(2) // 6 tiles
        assertThat(cardActionRowPadding(6)).isEqualTo(2) // full: 6 tiles, no add tile
    }

    @Test
    fun pickingAnActionReplacesAppendsOrRemoves() {
        val a = GestureAction.OpenSearch
        val b = GestureAction.LaunchApp(AppKey("x.y", "x.y.Main", 0))
        assertThat(cardActionsAfterPick(listOf(a), 0, b)).containsExactly(b).inOrder()
        assertThat(cardActionsAfterPick(listOf(a), 1, b)).containsExactly(a, b).inOrder()
        assertThat(cardActionsAfterPick(listOf(a, b), 0, GestureAction.None)).containsExactly(b)
        assertThat(cardActionsAfterPick(listOf(a, b), 5, a)).containsExactly(a, b, a).inOrder()
    }

    @Test
    fun pickerStartsOnTheFeaturedAppOrTheSlotsAction() {
        val key = AppKey("x.y", "x.y.Main", 0)
        val actions = listOf(GestureAction.OpenSearch)
        assertThat(cardPickerCurrent(true, CardFeatured.App(key), actions, 0)).isEqualTo(GestureAction.LaunchApp(key))
        assertThat(cardPickerCurrent(true, CardFeatured.None, actions, 0)).isEqualTo(GestureAction.None)
        assertThat(cardPickerCurrent(false, CardFeatured.None, actions, 0)).isEqualTo(GestureAction.OpenSearch)
        assertThat(cardPickerCurrent(false, CardFeatured.None, actions, 3)).isEqualTo(GestureAction.None)
    }

    @Test
    fun optimisticOrderIsShownOnlyWhileItIsAPermutationOfTheSavedCards() {
        val a = AutomationCard("a", "A")
        val b = AutomationCard("b", "B")
        val c = AutomationCard("c", "C")
        val saved = listOf(a, b, c)
        assertThat(cardsInShownOrder(saved, null)).isEqualTo(saved)
        assertThat(cardsInShownOrder(saved, listOf("c", "a", "b"))).containsExactly(c, a, b).inOrder()
        assertThat(cardsInShownOrder(saved, listOf("c", "a"))).isEqualTo(saved) // a card was added meanwhile
        assertThat(cardsInShownOrder(saved, listOf("c", "a", "z"))).isEqualTo(saved)
    }
}
