package io.github.aj4abinjacob.chikku.core.model.search

import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.LauncherApp
import io.github.aj4abinjacob.chikku.core.model.ProfileType
import io.github.aj4abinjacob.chikku.core.model.settings.SearchSettings
import kotlinx.coroutines.runBlocking
import org.junit.Test

/** Synthetic JVM coverage only; these fixtures are not installed apps or device benchmarks. */
class ReliabilitySearchStressTest {
    @Test
    fun tenThousandSameLabelCrossProfileAppsKeepIdentityAndExcludeHiddenKeys() = runBlocking {
        val apps = List(10_000) { index ->
            LauncherApp(
                key = AppKey("org.example.app${index / 3}", "org.example.Activity${index % 3}", (index % 5).toLong()),
                label = when (index % 5) {
                    0 -> "Stress Café 📷"
                    1 -> "STRESS Café 👨‍👩‍👧‍👦"
                    2 -> "اختبار Stress"
                    3 -> "പരീക്ഷണം stress"
                    else -> "压力 Stress"
                },
                profileType = if (index % 5 == 0) ProfileType.WORK else ProfileType.PERSONAL,
            )
        }
        val hidden = apps.filterIndexed { index, _ -> index % 7 == 0 }.map { it.key }.toSet()
        val search = UniversalSearch(
            appSearcher = DefaultAppSearcher(),
            shortcutSource = ShortcutSource { ShortcutLookup.Unavailable },
            contactSource = ContactSource { _, _ -> ContactLookup.Unavailable },
        )

        val first = search.search("stress", apps, hidden, SearchSettings())
        val second = search.search("stress", apps, hidden, SearchSettings())

        assertThat(first.apps).isEqualTo(second.apps)
        assertThat(first.apps).isNotEmpty()
        assertThat(first.apps.map { it.key }.toSet()).hasSize(first.apps.size)
        assertThat(first.apps.map { it.key }).containsNoneIn(hidden)
        assertThat(first.apps.all { app -> apps.any { it.key == app.key } }).isTrue()
    }
}
