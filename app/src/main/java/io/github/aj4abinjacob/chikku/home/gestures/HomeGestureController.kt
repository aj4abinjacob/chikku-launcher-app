package io.github.aj4abinjacob.chikku.home.gestures

import android.animation.ValueAnimator
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import androidx.activity.ComponentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.message.ChikkuMessages
import io.github.aj4abinjacob.chikku.applock.AppUnlocker
import io.github.aj4abinjacob.chikku.core.model.AppCustomization
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.GestureAction
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.home.Haptics
import io.github.aj4abinjacob.chikku.home.screenBounds
import io.github.aj4abinjacob.chikku.home.WallpaperCommands
import io.github.aj4abinjacob.chikku.home.DrawerMotion
import io.github.aj4abinjacob.chikku.home.HomeItemUi
import io.github.aj4abinjacob.chikku.home.SwipeDirection
import io.github.aj4abinjacob.chikku.home.VerticalSwipeLayout
import io.github.aj4abinjacob.chikku.platform.GestureActionExecutor
import io.github.aj4abinjacob.chikku.platform.GestureResult
import io.github.aj4abinjacob.chikku.platform.LaunchResult
import io.github.aj4abinjacob.chikku.prompt.ChikkuPrompts
import io.github.aj4abinjacob.chikku.prompt.PromptHandle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * Routes home-surface gestures to actions (G10) and icon swipes to item/app actions (G07).
 *
 * One-finger swipes: a swipe that starts on an icon with a swipe action runs that action; otherwise
 * swipe up bound to "Open app drawer" keeps the drawer following the finger ([DrawerMotion]), and
 * downward notification/quick-settings swipes open the system shade during a deliberate drag;
 * other actions run once the swipe is released far or fast enough. An icon with a swipe action
 * follows the finger a little ([iconSwipeNudge]), confirms by touch once the swipe has gone far
 * enough to run, and springs back on release. Two-finger gestures and
 * double taps come from [VerticalSwipeLayout] through [HomeGestureListener]. Gestures are ignored
 * while the drawer is open (it has its own swipe-down-to-close).
 */
internal class HomeGestureController(
    private val activity: ComponentActivity,
    private val surface: VerticalSwipeLayout,
    private val drawerMotion: DrawerMotion,
    private val executor: GestureActionExecutor,
    private val settings: () -> LauncherSettings,
    customizations: Flow<Map<AppKey, AppCustomization>>,
    private val unlocker: AppUnlocker,
    private val isOnDefaultPage: () -> Boolean = { true },
    /** HOME passes its shared instance so only one Chikku prompt shows at a time. */
    private val prompts: ChikkuPrompts = ChikkuPrompts(activity),
    /** Chikku's message bar for short notices; never a platform toast. */
    private val messages: ChikkuMessages = ChikkuMessages.of(activity),
) : VerticalSwipeLayout.Listener, HomeGestureListener {

    private sealed interface Active {
        data object Drawer : Active
        data object Completed : Active
        data class Action(val action: GestureAction, val direction: SwipeDirection, val source: View?) : Active
    }

    private val drawerSwipes = drawerMotion.homeSwipes
    private val minDistance = activity.resources.getDimension(R.dimen.gestures_min_swipe_distance)
    private val shadeDistance = activity.resources.getDimension(R.dimen.gestures_shade_drag_distance)
    private val minFlingVelocity = ViewConfiguration.get(activity).scaledMinimumFlingVelocity * FLING_MULTIPLIER.toFloat()
    private val maxNudge = activity.resources.getDimension(R.dimen.gestures_icon_swipe_nudge)

    private var active: Active? = null
    private var offsetY = 0f
    /** The icon currently moved by [nudge], so it can be put back. */
    private var nudged: View? = null
    /** The icon swipe passed its run distance and the user already felt it. */
    private var thresholdFelt = false
    private var appCustomizations: Map<AppKey, AppCustomization> = emptyMap()
    private var prompt: PromptHandle? = null

    init {
        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                customizations.catch { emit(emptyMap()) }.collect { appCustomizations = it }
            }
        }
        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) = dismissDialog()
        })
    }

    // --- One-finger swipes ---------------------------------------------------------------------

    override fun canStartSwipe(direction: SwipeDirection): Boolean {
        active = null
        releaseNudge()
        if (drawerMotion.targetOpen) return false
        val prefs = settings()
        val source = surface.touchDownTarget
        val item = source?.getTag(R.id.home_item_model) as? HomeItemUi
        val itemAction = item?.let { resolveItemSwipeAction(it, direction, appCustomizations, prefs.folders.swipeActionsEnabled) }
        val next: Active? = when {
            itemAction != null -> Active.Action(itemAction, direction, source)
            else -> when (val action = homeSwipeAction(prefs, direction)) {
                GestureAction.None -> null
                GestureAction.OpenDrawer ->
                    if (direction == SwipeDirection.UP) {
                        Active.Drawer.takeIf { drawerSwipes.canStartSwipe(direction) }
                    } else {
                        Active.Action(action, direction, null)
                    }
                else -> Active.Action(action, direction, null)
            }
        }
        active = next
        return next != null
    }

    override fun onSwipeStart(direction: SwipeDirection) {
        offsetY = 0f
        thresholdFelt = false
        if (active == Active.Drawer) drawerSwipes.onSwipeStart(direction)
    }

    override fun onSwipeMove(offsetY: Float) {
        if (active == Active.Drawer) {
            drawerSwipes.onSwipeMove(offsetY)
            return
        }
        this.offsetY = offsetY
        val current = active as? Active.Action ?: return
        current.source?.let { nudge(it, current.direction, offsetY) }
        if (current.direction == SwipeDirection.DOWN && offsetY >= shadeDistance &&
            (current.action == GestureAction.ExpandNotifications || current.action == GestureAction.ExpandQuickSettings)
        ) {
            // SystemUI owns the animation. Hand off during the drag rather than waiting for UP.
            // Mark it first: opening the shade can cancel our touch stream synchronously.
            active = Active.Completed
            releaseNudge()
            runGesture(current.action, current.source, haptic = !thresholdFelt)
        }
    }

    override fun onSwipeEnd(velocityY: Float, cancelled: Boolean) {
        releaseNudge()
        when (val current = active) {
            Active.Drawer -> drawerSwipes.onSwipeEnd(velocityY, cancelled)
            is Active.Action ->
                if (!cancelled && isSwipeCommitted(current.direction, offsetY, velocityY, minDistance, minFlingVelocity)) {
                    runGesture(current.action, current.source, haptic = !thresholdFelt)
                }
            Active.Completed, null -> Unit
        }
        active = null
    }

    /**
     * Moves the swiped icon with the finger and confirms by touch when the swipe would now run, so
     * the user sees which icon caught the swipe and knows when letting go will act. No movement
     * when system animations are off; the touch confirmation stays.
     */
    private fun nudge(icon: View, direction: SwipeDirection, offsetY: Float) {
        if (ValueAnimator.areAnimatorsEnabled()) {
            icon.animate().cancel()
            icon.translationY = iconSwipeNudge(direction, offsetY, maxNudge, minDistance)
            nudged = icon
        }
        val farEnough = isSwipeCommitted(direction, offsetY, 0f, minDistance, Float.MAX_VALUE)
        if (farEnough && !thresholdFelt) Haptics.gesture(surface)
        thresholdFelt = farEnough
    }

    /** Springs a nudged icon back to its cell. */
    private fun releaseNudge() {
        val icon = nudged ?: return
        nudged = null
        icon.animate().translationY(0f).setDuration(NUDGE_RELEASE_MS).setInterpolator(DecelerateInterpolator()).start()
    }

    // --- Two fingers, double tap, home button --------------------------------------------------

    override fun canStartMultiTouch(): Boolean = !drawerMotion.targetOpen && hasMultiTouchActions(settings())

    override fun onMultiTouchGesture(gesture: MultiTouchGesture) {
        if (!drawerMotion.targetOpen) runGesture(multiTouchAction(settings(), gesture), null)
    }

    override fun onDoubleTap() {
        if (!drawerMotion.targetOpen) runGesture(settings().gestures.doubleTap, null)
    }

    /** Live wallpapers react to taps on empty HOME space, as under other launchers. */
    override fun onEmptyTap(screenX: Int, screenY: Int) {
        if (!drawerMotion.targetOpen) WallpaperCommands.tap(surface, screenX, screenY)
    }

    override fun canStartHorizontalSwipe(left: Boolean): Boolean =
        !drawerMotion.targetOpen && horizontalSwipeAction(settings(), left) != GestureAction.None

    override fun onHorizontalSwipe(left: Boolean) {
        if (!drawerMotion.targetOpen) runGesture(horizontalSwipeAction(settings(), left), null)
    }

    override fun canStartDoubleTapSwipe(direction: SwipeDirection): Boolean =
        !drawerMotion.targetOpen && doubleTapSwipeAction(settings(), direction) != GestureAction.None

    override fun onDoubleTapSwipe(direction: SwipeDirection) {
        if (!drawerMotion.targetOpen) runGesture(doubleTapSwipeAction(settings(), direction), null)
    }

    /** HOME pressed while HOME was already in front with the drawer closed. */
    fun onHomeButton() = run(homeButtonAction(settings(), isOnDefaultPage()), null)

    /** Closes this controller's prompt, if it is still showing. */
    fun dismissDialog() {
        prompt?.dismiss()
        prompt = null
    }

    // --- Running and explaining ----------------------------------------------------------------

    /** Runs [action] (also used by folder taps/swipes), explaining failures the same way as gestures. */
    fun perform(action: GestureAction, source: View?) = run(action, source)

    /** A finger gesture ran: confirm it by touch too, since some actions (lock, shade) show nothing in Chikku. */
    private fun runGesture(action: GestureAction, source: View?, haptic: Boolean = true) {
        if (haptic && action != GestureAction.None) Haptics.gesture(surface)
        run(action, source)
    }

    private fun run(action: GestureAction, source: View?) {
        if (action == GestureAction.None) return
        when (val result = executor.execute(action, source?.screenBounds())) {
            is GestureResult.Locked -> unlocker.unlock(result.locked) { run(action, source) }
            else -> present(result)
        }
    }

    private fun present(result: GestureResult) {
        when (result) {
            GestureResult.Performed, GestureResult.NoAction -> Unit
            GestureResult.NeedsAccessibilityService -> showAccessibilityExplanation()
            is GestureResult.Unavailable -> messages.show(
                if (result.reason == LaunchResult.Reason.SHORTCUT_UNAVAILABLE)
                    R.string.shortcut_needs_recreation else R.string.gesture_unavailable,
            )
            is GestureResult.Locked -> Unit // handled in run()
        }
    }

    private fun showAccessibilityExplanation() {
        prompt = prompts.showAccessibilityExplanation(activity, messages)
    }

    private companion object {
        /** Same fling threshold as the drawer, so a swipe action needs a deliberate flick. */
        const val FLING_MULTIPLIER = 4
        const val NUDGE_RELEASE_MS = 160L
    }
}
