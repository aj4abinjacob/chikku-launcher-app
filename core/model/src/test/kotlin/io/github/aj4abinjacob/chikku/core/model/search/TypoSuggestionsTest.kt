package io.github.aj4abinjacob.chikku.core.model.search

import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.LauncherApp
import io.github.aj4abinjacob.chikku.core.model.ProfileType
import org.junit.Test

class TypoSuggestionsTest {

    private fun app(pkg: String, label: String) = LauncherApp(AppKey(pkg, "$pkg.Main", 0), label, ProfileType.PERSONAL)

    private val swiggy = app("in.swiggy", "Swiggy")
    private val spotify = app("com.spotify", "Spotify")
    private val maps = app("com.maps", "Google Maps")
    private val apps = listOf(spotify, swiggy, maps)

    @Test fun misspelledQueryFindsTheApp() {
        assertThat(TypoSuggestions.apps(apps, "seigg")).containsExactly(swiggy)
    }

    @Test fun toleratesWrongMissingExtraAndSwappedLetters() {
        assertThat(TypoSuggestions.distance("swiggy", "swoggy")).isEqualTo(1) // wrong
        assertThat(TypoSuggestions.distance("swiggy", "swigy")).isEqualTo(1) // missing
        assertThat(TypoSuggestions.distance("swiggy", "swiigg")).isEqualTo(1) // extra
        assertThat(TypoSuggestions.distance("swiggy", "wsiggy")).isEqualTo(1) // swapped
    }

    @Test fun matchesTheStartOfALaterWord() {
        assertThat(TypoSuggestions.apps(apps, "mpas")).containsExactly(maps)
    }

    @Test fun shortQueriesTolerateLessAndTinyOnesNothing() {
        assertThat(TypoSuggestions.maxTypos(2)).isEqualTo(0)
        assertThat(TypoSuggestions.distance("swiggy", "sw")).isNull()
        assertThat(TypoSuggestions.distance("swiggy", "sxi")).isEqualTo(1)
        assertThat(TypoSuggestions.distance("swiggy", "xxi")).isNull()
        assertThat(TypoSuggestions.distance("spotify", "zzzzzz")).isNull()
    }

    @Test fun closestFirstAndCapped() {
        val many = (1..10).map { app("com.s$it", "Swiggy $it") } + app("com.x", "Swiggx")
        val found = TypoSuggestions.apps(many, "swiggz")
        assertThat(found).hasSize(TypoSuggestions.MAX_APPS)
    }

    @Test fun customLabelsAreMatchedAndShown() {
        val found = TypoSuggestions.apps(apps, "fodo", mapOf(swiggy.key to "Food"))
        assertThat(found.map { it.label }).containsExactly("Food")
    }

    @Test fun neverThrowsForOddInput() {
        for (query in listOf("", "   ", "🍔🍔🍔", "a b c d e f", "x".repeat(200))) {
            TypoSuggestions.apps(apps, query)
        }
        assertThat(TypoSuggestions.distance("", "abc")).isNull()
    }
}
