package io.github.aj4abinjacob.chikku.settings

import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.settings.HomeSearchBarPosition
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.core.model.settings.SearchSettings
import io.github.aj4abinjacob.chikku.core.model.settings.WebSearchEngine
import io.github.aj4abinjacob.chikku.settings.previews.PreviewSearchSource
import io.github.aj4abinjacob.chikku.settings.previews.previewDockAutocomplete
import io.github.aj4abinjacob.chikku.settings.previews.previewOnlineSuggestions
import io.github.aj4abinjacob.chikku.settings.previews.previewSearchSources
import io.github.aj4abinjacob.chikku.settings.previews.previewSearchSuggestions
import io.github.aj4abinjacob.chikku.settings.previews.searchPreviewEnvelopeDp
import org.junit.Test

class SearchPreviewGapsTest {
    @Test
    fun previewEnvelopeUsesOnlyViewportAndFontConfigurationAndLeavesRoomForControls() {
        assertThat(searchPreviewEnvelopeDp(292f, 800, 1f)).isEqualTo(272f)
        assertThat(searchPreviewEnvelopeDp(292f, 800, 2f)).isWithin(.01f).of(252.8f)
        assertThat(searchPreviewEnvelopeDp(180f, 800, 2f)).isEqualTo(180f)
        assertThat(searchPreviewEnvelopeDp(292f, 1200, 1f)).isEqualTo(292f)
        assertThat(searchPreviewEnvelopeDp(292f, 480, 2f)).isWithin(.01f).of(124.8f)
    }

    @Test
    fun eachOptionalResultSourceDisappearsIndependentlyAndReturnsInRuntimeOrder() {
        val all = SearchSettings(foldersEnabled = true, shortcutsEnabled = true, playStoreRow = true)
        assertThat(previewSearchSources(all)).containsExactly(PreviewSearchSource.FOLDER, PreviewSearchSource.SHORTCUT, PreviewSearchSource.STORE).inOrder()
        assertThat(previewSearchSources(all.copy(foldersEnabled = false))).containsExactly(PreviewSearchSource.SHORTCUT, PreviewSearchSource.STORE).inOrder()
        assertThat(previewSearchSources(all.copy(shortcutsEnabled = false))).containsExactly(PreviewSearchSource.FOLDER, PreviewSearchSource.STORE).inOrder()
        assertThat(previewSearchSources(all.copy(playStoreRow = false))).containsExactly(PreviewSearchSource.FOLDER, PreviewSearchSource.SHORTCUT).inOrder()
        assertThat(previewSearchSources(all.copy(foldersEnabled = false, shortcutsEnabled = false, playStoreRow = false))).isEmpty()
    }

    @Test
    fun showingSuggestionsControlsRankedEmptyQuerySamplesIndependentlyOfLearning() {
        val off = SearchSettings(historySuggestionsEnabled = false)
        assertThat(previewSearchSuggestions(off)).isEmpty()
        val ranked = previewSearchSuggestions(off.copy(historySuggestionsEnabled = true))
        assertThat(ranked).containsExactly(3, 1, 4, 0, 2).inOrder()
        assertThat(ranked).isNotEqualTo(ranked.sorted())
        assertThat(previewSearchSuggestions(off)).isEmpty()
        assertThat(off.historySuggestionsEnabled).isFalse()
    }

    @Test
    fun dockAutocompleteRequiresVisibleBelowDockBarWebRowAndSupportedOptIn() {
        val defaults = LauncherSettings()
        val on = defaults.copy(home = defaults.home.copy(showSearchBar = true, searchBarPosition = HomeSearchBarPosition.BELOW_DOCK),
            search = defaults.search.copy(showWebSuggestionRow = true, onlineSuggestions = true, webEngine = WebSearchEngine.GOOGLE))
        assertThat(previewDockAutocomplete(on)).isTrue()
        assertThat(previewDockAutocomplete(on.copy(search = on.search.copy(webEngine = WebSearchEngine.DUCKDUCKGO)))).isTrue()
        assertThat(previewDockAutocomplete(on.copy(search = on.search.copy(webEngine = WebSearchEngine.BING)))).isFalse()
        assertThat(previewDockAutocomplete(on.copy(search = on.search.copy(showWebSuggestionRow = false)))).isFalse()
        assertThat(previewDockAutocomplete(on.copy(search = on.search.copy(onlineSuggestions = false)))).isFalse()
        assertThat(previewDockAutocomplete(on.copy(home = on.home.copy(showSearchBar = false)))).isFalse()
        assertThat(previewDockAutocomplete(on.copy(home = on.home.copy(searchBarPosition = HomeSearchBarPosition.TOP)))).isFalse()
        assertThat(previewOnlineSuggestions(on.search)).isTrue()
    }

    @Test
    fun pausingLearningKeepsPreviouslyLearnedSuggestionsUntilShowingIsDisabled() {
        val learning = SearchSettings(historyLearningEnabled = true, historySuggestionsEnabled = true)
        val before = previewSearchSuggestions(learning)
        assertThat(before).isNotEmpty()
        assertThat(previewSearchSuggestions(learning.copy(historyLearningEnabled = false))).isEqualTo(before)
        assertThat(previewSearchSuggestions(learning.copy(historySuggestionsEnabled = false))).isEmpty()
        assertThat(learning.historyLearningEnabled).isTrue()
    }
}
