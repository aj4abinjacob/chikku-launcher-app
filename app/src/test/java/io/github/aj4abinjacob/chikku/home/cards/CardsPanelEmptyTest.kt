package io.github.aj4abinjacob.chikku.home.cards

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.cards.AutomationCard
import io.github.aj4abinjacob.chikku.home.TestHomeApplication
import io.github.aj4abinjacob.chikku.theme.Motion
import io.github.aj4abinjacob.chikku.theme.SurfaceColors
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestHomeApplication::class)
class CardsPanelEmptyTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test fun emptyKindFollowsSavedCards() {
        assertThat(cardsPanelEmpty(emptyList())).isEqualTo(CardsPanelEmpty.NO_CARDS)
        assertThat(cardsPanelEmpty(listOf(AutomationCard("a", "A", enabled = false)))).isEqualTo(CardsPanelEmpty.ALL_OFF)
        assertThat(cardsPanelEmpty(listOf(AutomationCard("a", "A", enabled = false), AutomationCard("b", "B"))))
            .isEqualTo(CardsPanelEmpty.NONE_MATCH)
        assertThat(cardsPanelEmpty(null)).isEqualTo(CardsPanelEmpty.NONE_MATCH)
    }

    @Test fun eachEmptyStateSaysWhyWithOneActionAndPendingShowsNothing() {
        val root = FrameLayout(context)
        var opened = 0
        val surface = CardsSurface(root, {}, { opened++ }, { _, _, _ -> })
        listOf(
            CardsPanelEmpty.NO_CARDS to R.string.cards_surface_add_first,
            CardsPanelEmpty.ALL_OFF to R.string.cards_surface_edit,
            CardsPanelEmpty.NONE_MATCH to R.string.cards_surface_edit,
        ).forEach { (kind, action) ->
            surface.render(emptyList(), SurfaceColors.LIGHT, Motion.DEFAULT, empty = kind)
            val state = root.findViewWithTag<View>("cards_empty:${kind.name}")
            assertThat(state).isNotNull()
            val title = ArrayList<View>()
            root.findViewsWithText(title, context.getString(kind.title), View.FIND_VIEWS_WITH_TEXT)
            assertThat(title).hasSize(1)
            val buttons = ArrayList<View>()
            root.findViewsWithText(buttons, context.getString(action), View.FIND_VIEWS_WITH_TEXT)
            buttons.single().performClick()
        }
        assertThat(opened).isEqualTo(3)
        surface.render(emptyList(), SurfaceColors.LIGHT, Motion.DEFAULT, empty = CardsPanelEmpty.PENDING)
        CardsPanelEmpty.entries.forEach { assertThat(root.findViewWithTag<View>("cards_empty:${it.name}")).isNull() }
        surface.close()
    }
}
