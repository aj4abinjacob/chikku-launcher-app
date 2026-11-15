package io.github.aj4abinjacob.chikku.home.gestures

import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.AppCustomization
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.CellPlacement
import io.github.aj4abinjacob.chikku.core.model.ContainerType
import io.github.aj4abinjacob.chikku.core.model.GestureAction
import io.github.aj4abinjacob.chikku.core.model.ItemActions
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerOpenMethod
import io.github.aj4abinjacob.chikku.core.model.settings.GestureSettings
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.home.Availability
import io.github.aj4abinjacob.chikku.home.HomeItemUi
import io.github.aj4abinjacob.chikku.home.ItemContent
import io.github.aj4abinjacob.chikku.home.SwipeDirection
import org.junit.Test

class GestureClassifiersTest {

    // --- Two-finger gestures -----------------------------------------------------------------

    @Test
    fun smallTwoFingerMovementIsNotYetAGesture() {
        assertThat(classifyMultiTouch(startSpan = 300f, span = 290f, centroidDx = 5f, centroidDy = -10f, minDistance = 48f)).isNull()
    }

    @Test
    fun fingersMovingTogetherOrApartIsAPinch() {
        assertThat(classifyMultiTouch(300f, 200f, 0f, 5f, 48f)).isEqualTo(MultiTouchGesture.PINCH_IN)
        assertThat(classifyMultiTouch(300f, 420f, 4f, -6f, 48f)).isEqualTo(MultiTouchGesture.PINCH_OUT)
    }

    @Test
    fun fingersTravellingTogetherIsATwoFingerSwipe() {
        assertThat(classifyMultiTouch(300f, 305f, 3f, -90f, 48f)).isEqualTo(MultiTouchGesture.TWO_FINGER_SWIPE_UP)
        assertThat(classifyMultiTouch(300f, 295f, -3f, 90f, 48f)).isEqualTo(MultiTouchGesture.TWO_FINGER_SWIPE_DOWN)
    }

    @Test
    fun ambiguousOrHorizontalTwoFingerMovementIsIgnored() {
        // As much spreading as travelling: neither wins clearly.
        assertThat(classifyMultiTouch(300f, 360f, 0f, -60f, 48f)).isNull()
        // Mostly sideways travel is left alone (not a vertical swipe).
        assertThat(classifyMultiTouch(300f, 300f, 120f, 60f, 48f)).isNull()
    }

    // --- Released one-finger swipes ----------------------------------------------------------

    @Test
    fun swipeRunsItsActionAfterEnoughTravelOrAFlingInItsDirection() {
        assertThat(isSwipeCommitted(SwipeDirection.UP, offsetY = -60f, velocityY = 0f, minDistance = 48f, minFlingVelocity = 1000f)).isTrue()
        assertThat(isSwipeCommitted(SwipeDirection.UP, offsetY = -10f, velocityY = -2000f, minDistance = 48f, minFlingVelocity = 1000f)).isTrue()
        assertThat(isSwipeCommitted(SwipeDirection.DOWN, offsetY = 60f, velocityY = 0f, minDistance = 48f, minFlingVelocity = 1000f)).isTrue()
    }

    @Test
    fun shortSlowOrReversedSwipeDoesNothing() {
        assertThat(isSwipeCommitted(SwipeDirection.UP, -20f, -200f, 48f, 1000f)).isFalse()
        // Pulled up, then flung back down.
        assertThat(isSwipeCommitted(SwipeDirection.UP, -20f, 3000f, 48f, 1000f)).isFalse()
        assertThat(isSwipeCommitted(SwipeDirection.DOWN, 10f, -3000f, 48f, 1000f)).isFalse()
        assertThat(isSwipeCommitted(SwipeDirection.NONE, -500f, -5000f, 48f, 1000f)).isFalse()
    }

    @Test
    fun iconFollowsTheSwipeALittleInEitherDirection() {
        val up = iconSwipeNudge(SwipeDirection.UP, offsetY = -48f, maxNudge = 30f, travel = 48f)
        val down = iconSwipeNudge(SwipeDirection.DOWN, offsetY = 48f, maxNudge = 30f, travel = 48f)
        assertThat(up).isLessThan(0f)
        assertThat(down).isEqualTo(-up)
        // About two thirds of the way at the run distance.
        assertThat(down).isWithin(0.5f).of(30f * 0.632f)
    }

    @Test
    fun iconNudgeEasesAndNeverPassesItsLimit() {
        val near = iconSwipeNudge(SwipeDirection.DOWN, 10f, 30f, 48f)
        val far = iconSwipeNudge(SwipeDirection.DOWN, 100f, 30f, 48f)
        val veryFar = iconSwipeNudge(SwipeDirection.DOWN, 5_000f, 30f, 48f)
        assertThat(near).isLessThan(far)
        assertThat(far - near).isLessThan(100f - 10f)
        assertThat(veryFar).isAtMost(30f)
        assertThat(iconSwipeNudge(SwipeDirection.UP, -5_000f, 30f, 48f)).isAtLeast(-30f)
    }

    @Test
    fun iconStaysPutWhenTheFingerIsBackPastWhereItStarted() {
        assertThat(iconSwipeNudge(SwipeDirection.UP, 20f, 30f, 48f)).isEqualTo(0f)
        assertThat(iconSwipeNudge(SwipeDirection.DOWN, -20f, 30f, 48f)).isEqualTo(0f)
        assertThat(iconSwipeNudge(SwipeDirection.NONE, -200f, 30f, 48f)).isEqualTo(0f)
        assertThat(iconSwipeNudge(SwipeDirection.UP, 0f, 30f, 48f)).isEqualTo(0f)
    }

    // --- Double tap -----------------------------------------------------------------------------

    private fun tracker() = DoubleTapTracker(maxTapDurationMs = 400, doubleTapTimeoutMs = 300, touchSlop = 10f, doubleTapSlop = 100f)

    @Test
    fun twoQuickTapsOnEmptySpaceAreADoubleTap() {
        val t = tracker()
        t.onDown(100f, 100f, 0, overInteractiveChild = false)
        assertThat(t.onUp(80)).isFalse()
        t.onDown(110f, 105f, 200, overInteractiveChild = false)
        assertThat(t.onUp(260)).isTrue()
        // A third tap starts over rather than firing again.
        t.onDown(110f, 105f, 350, overInteractiveChild = false)
        assertThat(t.onUp(400)).isFalse()
    }

    @Test
    fun everyStillShortReleaseOnEmptySpaceIsATapForLiveWallpapers() {
        val t = tracker()
        t.onDown(100f, 100f, 0, overInteractiveChild = false)
        t.onUp(80)
        assertThat(t.lastUpWasTap).isTrue()
        // On an icon, dragged past the slop, or held too long: not a wallpaper tap.
        t.onDown(100f, 100f, 1_000, overInteractiveChild = true)
        t.onUp(1_050)
        assertThat(t.lastUpWasTap).isFalse()
        t.onDown(100f, 100f, 2_000, overInteractiveChild = false)
        t.onMove(200f, 100f)
        t.onUp(2_050)
        assertThat(t.lastUpWasTap).isFalse()
        t.onDown(100f, 100f, 3_000, overInteractiveChild = false)
        t.onUp(3_900)
        assertThat(t.lastUpWasTap).isFalse()
    }

    @Test
    fun tapsOnIconsSlowTapsAndDistantTapsAreNotDoubleTaps() {
        val onIcon = tracker()
        onIcon.onDown(100f, 100f, 0, overInteractiveChild = true)
        onIcon.onUp(50)
        onIcon.onDown(100f, 100f, 100, overInteractiveChild = true)
        assertThat(onIcon.onUp(150)).isFalse()

        val tooSlow = tracker()
        tooSlow.onDown(100f, 100f, 0, false)
        tooSlow.onUp(50)
        tooSlow.onDown(100f, 100f, 500, false)
        assertThat(tooSlow.onUp(550)).isFalse()

        val tooFar = tracker()
        tooFar.onDown(100f, 100f, 0, false)
        tooFar.onUp(50)
        tooFar.onDown(400f, 100f, 100, false)
        assertThat(tooFar.onUp(150)).isFalse()

        val longPress = tracker()
        longPress.onDown(100f, 100f, 0, false)
        longPress.onUp(600)
        longPress.onDown(100f, 100f, 650, false)
        assertThat(longPress.onUp(700)).isFalse()
    }

    @Test
    fun movementOrASecondFingerCancelsTheTap() {
        val moved = tracker()
        moved.onDown(100f, 100f, 0, false)
        moved.onUp(50)
        moved.onDown(100f, 100f, 100, false)
        moved.onMove(100f, 160f) // became a swipe
        assertThat(moved.onUp(150)).isFalse()

        val pinched = tracker()
        pinched.onDown(100f, 100f, 0, false)
        pinched.onUp(50)
        pinched.onDown(100f, 100f, 100, false)
        pinched.cancel()
        assertThat(pinched.onUp(150)).isFalse()
    }

    // --- Settings to actions -------------------------------------------------------------------

    @Test
    fun swipeUpOpensTheDrawerUnlessTheDrawerIsButtonOnly() {
        val defaults = LauncherSettings()
        assertThat(homeSwipeAction(defaults, SwipeDirection.UP)).isEqualTo(GestureAction.OpenDrawer)
        assertThat(homeSwipeAction(defaults, SwipeDirection.DOWN)).isEqualTo(GestureAction.None)
        val buttonOnly = defaults.copy(drawer = defaults.drawer.copy(openMethod = DrawerOpenMethod.BUTTON))
        assertThat(homeSwipeAction(buttonOnly, SwipeDirection.UP)).isEqualTo(GestureAction.None)
        // Other swipe-up actions are not affected by the drawer's open method.
        val search = buttonOnly.copy(gestures = GestureSettings(swipeUp = GestureAction.OpenSearch))
        assertThat(homeSwipeAction(search, SwipeDirection.UP)).isEqualTo(GestureAction.OpenSearch)
    }

    @Test
    fun multiTouchGesturesMapToTheirSettings() {
        val settings = LauncherSettings(
            gestures = GestureSettings(
                pinchIn = GestureAction.LockScreen,
                pinchOut = GestureAction.OpenLauncherSettings,
                twoFingerSwipeUp = GestureAction.Recents,
                twoFingerSwipeDown = GestureAction.ExpandQuickSettings,
            ),
        )
        assertThat(multiTouchAction(settings, MultiTouchGesture.PINCH_IN)).isEqualTo(GestureAction.LockScreen)
        assertThat(multiTouchAction(settings, MultiTouchGesture.PINCH_OUT)).isEqualTo(GestureAction.OpenLauncherSettings)
        assertThat(multiTouchAction(settings, MultiTouchGesture.TWO_FINGER_SWIPE_UP)).isEqualTo(GestureAction.Recents)
        assertThat(multiTouchAction(settings, MultiTouchGesture.TWO_FINGER_SWIPE_DOWN)).isEqualTo(GestureAction.ExpandQuickSettings)
        assertThat(hasMultiTouchActions(settings)).isTrue()
        // Defaults have none, so two-finger touches stay with the pager and icons.
        assertThat(hasMultiTouchActions(LauncherSettings())).isFalse()
    }

    // --- Icon swipes -----------------------------------------------------------------------------

    private val dialer = AppKey("com.example.dialer", "com.example.dialer.Main", 0)
    private val camera = AppKey("com.example.camera", "com.example.camera.Main", 0)

    private fun appItem(key: AppKey, actions: ItemActions = ItemActions()) = HomeItemUi(
        id = "i1",
        placement = CellPlacement(ContainerType.DOCK, 0, 0, 0),
        content = ItemContent.App(key, "Phone", isWork = false, availability = Availability.AVAILABLE),
        actions = actions,
    )

    @Test
    fun iconActionWinsOverAppDefaultWhichWinsOverNothing() {
        val shortcut = GestureAction.LaunchShortcut("com.example.dialer", "call_mum", 0)
        val customizations = mapOf(dialer to AppCustomization(dialer, swipeDownAction = shortcut, swipeUpAction = GestureAction.LaunchApp(camera)))

        // App default applies to any icon of the app.
        assertThat(resolveItemSwipeAction(appItem(dialer), SwipeDirection.DOWN, customizations, true)).isEqualTo(shortcut)
        // The icon's own action wins.
        val own = appItem(dialer, ItemActions(swipeDown = GestureAction.Recents))
        assertThat(resolveItemSwipeAction(own, SwipeDirection.DOWN, customizations, true)).isEqualTo(GestureAction.Recents)
        // Explicit None blocks the app default, so the swipe falls through to the home gesture.
        val blocked = appItem(dialer, ItemActions(swipeUp = GestureAction.None))
        assertThat(resolveItemSwipeAction(blocked, SwipeDirection.UP, customizations, true)).isNull()
        // Nothing configured.
        assertThat(resolveItemSwipeAction(appItem(camera), SwipeDirection.UP, customizations, true)).isNull()
    }

    @Test
    fun folderSwipeActionsHonourTheFolderSetting() {
        val folder = HomeItemUi(
            id = "f",
            placement = CellPlacement(ContainerType.DESKTOP, 0, 1, 1),
            content = ItemContent.Folder("Tools", emptyList()),
            actions = ItemActions(swipeUp = GestureAction.OpenSearch),
        )
        assertThat(resolveItemSwipeAction(folder, SwipeDirection.UP, emptyMap(), folderSwipesEnabled = true)).isEqualTo(GestureAction.OpenSearch)
        assertThat(resolveItemSwipeAction(folder, SwipeDirection.UP, emptyMap(), folderSwipesEnabled = false)).isNull()
    }
}
