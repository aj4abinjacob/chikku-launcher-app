package io.github.aj4abinjacob.chikku.core.model.search

import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.settings.WebSearchEngine
import org.junit.Test

class WebSuggestionsTest {
    @Test
    fun suggestionUrlsEncodeTheEntireQueryAndNeverFallBackToAnotherEngine() {
        assertThat(WebSuggestionProviders.url(WebSearchEngine.GOOGLE, "a &b=#c"))
            .isEqualTo("https://www.google.com/complete/search?client=chrome&q=a%20%26b%3D%23c")
        assertThat(WebSuggestionProviders.url(WebSearchEngine.DUCKDUCKGO, "café"))
            .isEqualTo("https://duckduckgo.com/ac/?type=list&q=caf%C3%A9")
        WebSearchEngine.entries.filterNot(WebSuggestionProviders::supports).forEach {
            assertThat(WebSuggestionProviders.url(it, "weather")).isNull()
        }
    }
}
