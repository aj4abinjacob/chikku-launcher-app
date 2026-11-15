package io.github.aj4abinjacob.chikku.settings.components

import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.AppSearcher
import io.github.aj4abinjacob.chikku.core.model.ComponentKey
import io.github.aj4abinjacob.chikku.core.model.LauncherApp
import io.github.aj4abinjacob.chikku.core.model.ProfileType
import io.github.aj4abinjacob.chikku.home.cards.widget.CardWidgetProviderOption
import io.github.aj4abinjacob.chikku.settings.cards.widget.filterWidgetOptions
import org.junit.Test

/** Search behind the long pickers: shared app list and the card widget chooser. Synthetic names only. */
class AppPickerFilterTest {

    private fun app(label: String) = LauncherApp(AppKey("com.example.$label", "$label.Main", 0L), label, ProfileType.PERSONAL)

    private val apps = listOf(app("Alpha"), app("Beta"), app("Gamma"))

    @Test
    fun blankQueryKeepsTheAlphabeticalListWithoutSearching() {
        val searcher = AppSearcher { _, _ -> error("blank queries must not search") }
        assertThat(filterApps(searcher, apps, "  ")).isEqualTo(apps)
    }

    @Test
    fun queryUsesTheSearcherTrimmedAndKeepsItsRelevanceOrder() {
        var asked: String? = null
        val searcher = AppSearcher { list, query ->
            asked = query
            list.reversed().filter { it.label.contains("a", ignoreCase = true) }
        }
        assertThat(filterApps(searcher, apps, " a ").map { it.label }).containsExactly("Gamma", "Beta", "Alpha").inOrder()
        assertThat(asked).isEqualTo("a")
    }

    @Test
    fun widgetSearchMatchesWidgetOrAppName() {
        fun widget(label: String, appLabel: String) =
            CardWidgetProviderOption(ComponentKey("com.example.${appLabel.lowercase()}", "$label.Provider", 0L), label, appLabel)
        val options = listOf(widget("Clock", "Sample Clock"), widget("Clock", "Other Time"), widget("Notes", "Pad"))
        assertThat(filterWidgetOptions(options, "")).isEqualTo(options)
        assertThat(filterWidgetOptions(options, "clock")).containsExactly(options[0], options[1]).inOrder()
        assertThat(filterWidgetOptions(options, "PAD")).containsExactly(options[2])
        assertThat(filterWidgetOptions(options, "nothing")).isEmpty()
    }
}
