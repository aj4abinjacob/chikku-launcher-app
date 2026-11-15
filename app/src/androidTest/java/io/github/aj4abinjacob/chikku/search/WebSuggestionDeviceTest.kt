package io.github.aj4abinjacob.chikku.search

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.search.WebSuggestionResult
import io.github.aj4abinjacob.chikku.core.model.settings.WebSearchEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Live endpoint smoke; opt in with runner argument liveWebSuggestions=true. No settings are changed. */
@RunWith(AndroidJUnit4::class)
class WebSuggestionDeviceTest {
    @Test
    fun googleAndDuckDuckGoReturnSuggestions() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveWebSuggestions") == "true")
        val source = HttpsWebSuggestionSource(Dispatchers.IO)
        listOf(WebSearchEngine.GOOGLE, WebSearchEngine.DUCKDUCKGO).forEach { engine ->
            val result = source.suggest(engine, "weather")
            assertThat(result).isInstanceOf(WebSuggestionResult.Found::class.java)
            assertThat((result as WebSuggestionResult.Found).suggestions).isNotEmpty()
            assertThat(result.suggestions.size).isAtMost(HttpsWebSuggestionSource.MAX_SUGGESTIONS)
        }
    }
}
