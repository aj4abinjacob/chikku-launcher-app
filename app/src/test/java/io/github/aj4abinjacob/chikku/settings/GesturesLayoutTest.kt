package io.github.aj4abinjacob.chikku.settings

import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.GestureAction
import io.github.aj4abinjacob.chikku.core.model.settings.GestureSettings
import io.github.aj4abinjacob.chikku.settings.sections.HomeGesture
import io.github.aj4abinjacob.chikku.settings.sections.anyNeedsAccessibility
import io.github.aj4abinjacob.chikku.settings.sections.everydayGestures
import io.github.aj4abinjacob.chikku.settings.sections.moreGestures
import org.junit.Test

class GesturesLayoutTest {
    @Test
    fun everyGestureIsListedExactlyOnce() {
        val listed = everydayGestures + moreGestures
        assertThat(listed).containsExactlyElementsIn(HomeGesture.entries)
        assertThat(listed).containsNoDuplicates()
    }

    @Test
    fun everydayGroupHoldsTheCommonGesturesAndTheHomeGesture() {
        assertThat(everydayGestures).containsAtLeast(
            HomeGesture.SWIPE_UP, HomeGesture.SWIPE_DOWN, HomeGesture.DOUBLE_TAP, HomeGesture.HOME_BUTTON,
        )
        assertThat(moreGestures).doesNotContain(HomeGesture.HOME_BUTTON)
    }

    @Test
    fun accessibilityIsNeededOnlyWhenALockOrRecentsActionIsBound() {
        val none = GestureSettings()
        val cleared = HomeGesture.entries.fold(none) { g, gesture -> gesture.set(g, GestureAction.None) }
        assertThat(cleared.anyNeedsAccessibility()).isFalse()
        assertThat(HomeGesture.PINCH_IN.set(cleared, GestureAction.Recents).anyNeedsAccessibility()).isTrue()
        assertThat(HomeGesture.ROTATE_CLOCKWISE.set(cleared, GestureAction.LockScreen).anyNeedsAccessibility()).isTrue()
        assertThat(HomeGesture.SWIPE_UP.set(cleared, GestureAction.OpenSearch).anyNeedsAccessibility()).isFalse()
    }
}
