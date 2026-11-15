package io.github.aj4abinjacob.chikku.core.model.search

import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.AppCustomization
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.LauncherApp
import io.github.aj4abinjacob.chikku.core.model.ProfileType
import org.junit.Test

class CustomLabelSearchTest {
    private val searcher = DefaultAppSearcher()
    private val mail = LauncherApp(AppKey("p.mail", "p.mail.Main", 0L), "Gmail", ProfileType.PERSONAL)
    private val maps = LauncherApp(AppKey("p.maps", "p.maps.Main", 0L), "Maps", ProfileType.PERSONAL)
    private val apps = listOf(mail, maps)

    @Test
    fun findsAppsByCustomLabelAndShowsIt() {
        val result = CustomLabelSearch.search(searcher, apps, "post", mapOf(mail.key to "Post"))
        assertThat(result.map { it.key }).containsExactly(mail.key)
        assertThat(result.single().label).isEqualTo("Post")
    }

    @Test
    fun stillFindsRenamedAppsByTheirOwnLabelOnce() {
        val result = CustomLabelSearch.search(searcher, apps, "ma", mapOf(mail.key to "Mail"))
        // "Mail" (custom, prefix) and "Maps" (own label, prefix) match; Gmail's own label also contains "ma" but it is listed once.
        assertThat(result.map { it.key }).containsExactly(mail.key, maps.key).inOrder()
    }

    @Test
    fun withoutCustomLabelsBehavesLikeTheSearcher() {
        assertThat(CustomLabelSearch.search(searcher, apps, "ma", emptyMap())).isEqualTo(searcher.search(apps, "ma"))
    }

    @Test
    fun customLabelsIgnoreBlankAndMissingNames() {
        val labels = CustomLabelSearch.customLabels(
            mapOf(
                mail.key to AppCustomization(mail.key, customLabel = "Post"),
                maps.key to AppCustomization(maps.key, customLabel = " "),
            ),
        )
        assertThat(labels).containsExactly(mail.key, "Post")
    }
}
