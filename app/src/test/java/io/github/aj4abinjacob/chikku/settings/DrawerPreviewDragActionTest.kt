package io.github.aj4abinjacob.chikku.settings

import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerOpenMethod
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerSearchBarPosition
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerSettings
import io.github.aj4abinjacob.chikku.settings.previews.DrawerPreviewDragAction
import io.github.aj4abinjacob.chikku.settings.previews.drawerPreviewDragAction
import io.github.aj4abinjacob.chikku.settings.previews.drawerPreviewProgress
import org.junit.Test

/** Expected outcomes are traced by hand through the original if/else-if chain in DrawerLayoutLivePreview. */
class DrawerPreviewDragActionTest {
    private val drawer = DrawerSettings().copy(
        openMethod = DrawerOpenMethod.BOTH, pullToSearch = true, searchBarPosition = DrawerSearchBarPosition.TOP,
    )

    private fun action(
        d: DrawerSettings = drawer, opening: Boolean = true, launched: Boolean = false, opened: Boolean = false,
        searchInteraction: Boolean = false, searchFocused: Boolean = false, paged: Boolean = false, rtl: Boolean = false,
        x: Float = 0f, y: Float = 0f,
    ) = drawerPreviewDragAction(d, opening, launched, opened, searchInteraction, searchFocused, paged, rtl, x, y)

    @Test
    fun swipeUpOpensAClosedDrawerOnlyWhenTheMethodAllowsIt() {
        assertThat(action(y = -25f)).isEqualTo(DrawerPreviewDragAction.OPEN)
        assertThat(action(y = -24f)).isEqualTo(DrawerPreviewDragAction.NONE)
        assertThat(action(d = drawer.copy(openMethod = DrawerOpenMethod.BUTTON), y = -25f))
            .isEqualTo(DrawerPreviewDragAction.PAGE_FORWARD) // falls through to the scrolling rule
        assertThat(action(launched = true, y = -25f)).isEqualTo(DrawerPreviewDragAction.PAGE_FORWARD)
    }

    @Test
    fun pullDownOnAnOpenDrawerFocusesSearchOrCloses() {
        assertThat(action(opened = true, y = 25f)).isEqualTo(DrawerPreviewDragAction.FOCUS_SEARCH)
        assertThat(action(d = drawer.copy(pullToSearch = false), opened = true, y = 25f))
            .isEqualTo(DrawerPreviewDragAction.CLOSE)
        assertThat(action(d = drawer.copy(searchBarPosition = DrawerSearchBarPosition.HIDDEN), opened = true, y = 25f))
            .isEqualTo(DrawerPreviewDragAction.CLOSE)
    }

    @Test
    fun searchPreviewPullsDownToFocusAndUpToUnfocus() {
        assertThat(action(opening = false, searchInteraction = true, y = 25f)).isEqualTo(DrawerPreviewDragAction.FOCUS_SEARCH)
        assertThat(action(opening = false, searchInteraction = true, searchFocused = true, y = -25f))
            .isEqualTo(DrawerPreviewDragAction.UNFOCUS_SEARCH)
        assertThat(action(opening = false, searchInteraction = true, searchFocused = false, y = -25f))
            .isEqualTo(DrawerPreviewDragAction.PAGE_FORWARD)
    }

    @Test
    fun pagingFollowsAxisAndLayoutDirection() {
        assertThat(action(opening = false, paged = true, x = -25f)).isEqualTo(DrawerPreviewDragAction.PAGE_FORWARD)
        assertThat(action(opening = false, paged = true, x = 25f)).isEqualTo(DrawerPreviewDragAction.PAGE_BACKWARD)
        assertThat(action(opening = false, paged = true, rtl = true, x = -25f)).isEqualTo(DrawerPreviewDragAction.PAGE_BACKWARD)
        assertThat(action(opening = false, paged = true, rtl = true, x = 25f)).isEqualTo(DrawerPreviewDragAction.PAGE_FORWARD)
        assertThat(action(opening = false, paged = true, x = 10f, y = 100f)).isEqualTo(DrawerPreviewDragAction.NONE)
        assertThat(action(opening = false, y = 25f)).isEqualTo(DrawerPreviewDragAction.PAGE_BACKWARD)
        assertThat(action(opening = false, y = -25f)).isEqualTo(DrawerPreviewDragAction.PAGE_FORWARD)
        assertThat(action(opening = false, x = 100f, y = 24f)).isEqualTo(DrawerPreviewDragAction.NONE)
    }

    @Test
    fun openingProgressIsStaticUnlessAnAnimationIsPlaying() {
        assertThat(drawerPreviewProgress(false, true, true, true, .3f)).isEqualTo(1f)
        assertThat(drawerPreviewProgress(true, false, false, true, .3f)).isEqualTo(0f)
        assertThat(drawerPreviewProgress(true, true, false, true, .3f)).isEqualTo(.3f)
        assertThat(drawerPreviewProgress(true, true, true, false, .25f)).isEqualTo(.75f)
    }
}
