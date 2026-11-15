package io.github.aj4abinjacob.chikku.settings.components

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ChoiceEntriesTest {

    @Test
    fun optionsWithoutSectionsAreListedPlainly() {
        assertThat(choiceEntries(listOf("a", "b")) { null })
            .containsExactly(ChoiceEntry.Option(0, "a"), ChoiceEntry.Option(1, "b")).inOrder()
    }

    @Test
    fun aHeadingStartsEachRunOfASection() {
        val section = mapOf("up" to "One finger", "down" to "One finger", "pinch" to "Two fingers", "home" to null)
        assertThat(choiceEntries(listOf("up", "down", "pinch", "home")) { section[it] }).containsExactly(
            ChoiceEntry.Heading("One finger"),
            ChoiceEntry.Option(0, "up"),
            ChoiceEntry.Option(1, "down"),
            ChoiceEntry.Heading("Two fingers"),
            ChoiceEntry.Option(2, "pinch"),
            ChoiceEntry.Option(3, "home"),
        ).inOrder()
    }
}
