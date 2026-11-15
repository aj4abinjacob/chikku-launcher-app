package io.github.aj4abinjacob.chikku.settings

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.settings.HomeSearchBarButtonSlot
import io.github.aj4abinjacob.chikku.core.model.settings.HomeSearchBarIcon
import io.github.aj4abinjacob.chikku.core.model.settings.HomeSearchBarTapAction
import io.github.aj4abinjacob.chikku.core.model.settings.SearchSettings
import io.github.aj4abinjacob.chikku.core.model.settings.WebSearchEngine
import io.github.aj4abinjacob.chikku.home.TestHomeApplication
import io.github.aj4abinjacob.chikku.settings.dock.homeBarActionsDescription
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/** The home bar preview's TalkBack description follows the tap action and both buttons. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestHomeApplication::class)
class HomeBarActionsPreviewTest {
    private fun describe(search: SearchSettings, direction: LayoutDirection = LayoutDirection.Ltr): String {
        var text = ""
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction) { text = homeBarActionsDescription(search) }
        }
        ShadowLooper.idleMainLooper()
        return text
    }

    @Test
    fun defaultDescribesChikkuTapAndBothButtons() {
        assertThat(describe(SearchSettings())).isEqualTo(
            "Tapping it opens Chikku search. Dog nose Chikku button on the left. DuckDuckGo button on the right.",
        )
    }

    @Test
    fun followsTapActionPlacementIconAndDirection() {
        val swapped = SearchSettings(
            webEngine = WebSearchEngine.GOOGLE, widgetTapAction = HomeSearchBarTapAction.WEB_SEARCH, widgetIcon = HomeSearchBarIcon.PAW,
            widgetChikkuButton = HomeSearchBarButtonSlot.END, widgetWebButton = HomeSearchBarButtonSlot.HIDDEN,
        )
        assertThat(describe(swapped)).isEqualTo("Tapping it opens Google. Paw Chikku button on the right.")
        assertThat(describe(swapped, LayoutDirection.Rtl)).isEqualTo("Tapping it opens Google. Paw Chikku button on the left.")
        assertThat(describe(swapped.copy(widgetChikkuButton = HomeSearchBarButtonSlot.HIDDEN)))
            .isEqualTo("Tapping it opens Google. No buttons.")
    }
}
