package io.github.aj4abinjacob.chikku.home.gestures

import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.GestureAction
import io.github.aj4abinjacob.chikku.core.model.settings.GestureSettings
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.home.SwipeDirection
import org.junit.Test

class ExtraGestureClassifiersTest {
    @Test
    fun rotationsRequireClearTurnAndStableSpanAndCentroid() {
        assertThat(classifyMultiTouch(200f, 200f, 0f, 0f, 24f, 40f)).isEqualTo(MultiTouchGesture.ROTATE_CLOCKWISE)
        assertThat(classifyMultiTouch(200f, 200f, 0f, 0f, 24f, -40f)).isEqualTo(MultiTouchGesture.ROTATE_COUNTERCLOCKWISE)
        assertThat(classifyMultiTouch(200f, 200f, 0f, 0f, 24f, 15f)).isNull()
        assertThat(classifyMultiTouch(20f, 20f, 0f, 0f, 24f, 80f)).isNull()
        assertThat(classifyMultiTouch(200f, 80f, 0f, 0f, 24f, 45f)).isEqualTo(MultiTouchGesture.PINCH_IN)
        assertThat(classifyMultiTouch(200f, 200f, 0f, -150f, 24f, 45f)).isEqualTo(MultiTouchGesture.TWO_FINGER_SWIPE_UP)
        assertThat(classifyMultiTouch(200f, 200f, 0f, 0f, 24f, Float.NaN)).isNull()
    }

    @Test
    fun angleBoundaryDoesNotReverseRotationOrCreateAFullTurn() {
        assertThat(rotationDeltaDegrees(179f, -179f)).isEqualTo(2f)
        assertThat(rotationDeltaDegrees(-179f, 179f)).isEqualTo(-2f)
        assertThat(rotationDeltaDegrees(20f, 45f)).isEqualTo(25f)
        assertThat(rotationDeltaDegrees(Float.NaN, 20f)).isEqualTo(0f)
    }

    @Test
    fun homeButtonScopeReturnsToMainPageBeforeItsAssignedAction() {
        val initial = LauncherSettings(gestures = GestureSettings(homeGesture = GestureAction.OpenSearch))
        assertThat(homeButtonAction(initial, false)).isEqualTo(GestureAction.OpenSearch)
        val scoped = initial.copy(gestures = initial.gestures.copy(homeButtonOnlyOnDefaultPage = true))
        assertThat(homeButtonAction(scoped, false)).isEqualTo(GestureAction.GoToDefaultPage)
        assertThat(homeButtonAction(scoped, true)).isEqualTo(GestureAction.OpenSearch)
    }

    @Test
    fun extraActionsAreIndependentAndDefaultOff() {
        val defaults = LauncherSettings()
        assertThat(horizontalSwipeAction(defaults, true)).isEqualTo(GestureAction.None)
        assertThat(doubleTapSwipeAction(defaults, SwipeDirection.UP)).isEqualTo(GestureAction.None)
        assertThat(hasMultiTouchActions(defaults)).isFalse()
        val custom = defaults.copy(gestures = defaults.gestures.copy(
            swipeLeft = GestureAction.OpenSearch, swipeRight = GestureAction.OpenDrawer,
            doubleTapSwipeUp = GestureAction.OpenLauncherSettings, doubleTapSwipeDown = GestureAction.ToggleDock,
            rotateClockwise = GestureAction.Recents, rotateCounterclockwise = GestureAction.LockScreen,
        ))
        assertThat(horizontalSwipeAction(custom, true)).isEqualTo(GestureAction.OpenSearch)
        assertThat(horizontalSwipeAction(custom, false)).isEqualTo(GestureAction.OpenDrawer)
        assertThat(doubleTapSwipeAction(custom, SwipeDirection.UP)).isEqualTo(GestureAction.OpenLauncherSettings)
        assertThat(doubleTapSwipeAction(custom, SwipeDirection.DOWN)).isEqualTo(GestureAction.ToggleDock)
        assertThat(multiTouchAction(custom, MultiTouchGesture.ROTATE_CLOCKWISE)).isEqualTo(GestureAction.Recents)
        assertThat(multiTouchAction(custom, MultiTouchGesture.ROTATE_COUNTERCLOCKWISE)).isEqualTo(GestureAction.LockScreen)
        assertThat(hasMultiTouchActions(custom)).isTrue()
    }
}
