package io.github.aj4abinjacob.chikku.home.folder

import io.github.aj4abinjacob.chikku.core.model.GestureAction
import io.github.aj4abinjacob.chikku.core.model.ItemActions

/** A gesture on a folder icon. */
enum class FolderGesture { TAP, SWIPE_UP, SWIPE_DOWN }

/** What a folder gesture resolves to. */
sealed interface FolderGestureOutcome {
    data object OpenFolder : FolderGestureOutcome
    data class Perform(val action: GestureAction) : FolderGestureOutcome

    /** Not handled by the folder: the swipe goes to the home screen (e.g. opens the drawer). */
    data object PassThrough : FolderGestureOutcome
}

/**
 * Folder tap and swipe gestures. With `folders.swipeActionsEnabled` off, a tap opens the folder and
 * swipes belong to the home screen. With it on:
 * - a tap runs `actions.tap` when set (e.g. launch the first app), otherwise opens the folder;
 * - a swipe runs its action when set; if not, and the tap was overridden, swipe up opens the
 *   folder ("swipe up to open"), so its contents always stay reachable. An unassigned swipe down
 *   always passes through, so it keeps opening the notification shade (or the home action).
 * [GestureAction.None] counts as "not set".
 */
object FolderGestures {

    fun resolve(gesture: FolderGesture, actions: ItemActions, enabled: Boolean): FolderGestureOutcome {
        val assigned = when (gesture) {
            FolderGesture.TAP -> actions.tap
            FolderGesture.SWIPE_UP -> actions.swipeUp
            FolderGesture.SWIPE_DOWN -> actions.swipeDown
        }
        // An explicit folder-open action remains reachable on a cover, even with global actions off.
        if (assigned == GestureAction.OpenFolder) return FolderGestureOutcome.OpenFolder
        if (!enabled) {
            return if (gesture == FolderGesture.TAP) FolderGestureOutcome.OpenFolder else FolderGestureOutcome.PassThrough
        }
        val tap = actions.tap.orNullIfNone()
        val action = when (gesture) {
            FolderGesture.TAP -> tap
            FolderGesture.SWIPE_UP -> actions.swipeUp.orNullIfNone()
            FolderGesture.SWIPE_DOWN -> actions.swipeDown.orNullIfNone()
        }
        return when {
            action != null -> FolderGestureOutcome.Perform(action)
            gesture == FolderGesture.TAP -> FolderGestureOutcome.OpenFolder
            tap != null && gesture == FolderGesture.SWIPE_UP -> FolderGestureOutcome.OpenFolder
            else -> FolderGestureOutcome.PassThrough
        }
    }

    /** Whether the folder icon must keep vertical swipes from the home screen's own swipe handling. */
    fun claimsSwipe(gesture: FolderGesture, actions: ItemActions, enabled: Boolean): Boolean =
        resolve(gesture, actions, enabled) != FolderGestureOutcome.PassThrough

    /** True when any gesture of this folder is customised, so the icon needs swipe tracking. */
    fun hasCustomGestures(actions: ItemActions, enabled: Boolean): Boolean =
        listOf(actions.tap, actions.swipeUp, actions.swipeDown).any {
            it == GestureAction.OpenFolder || (enabled && it.orNullIfNone() != null)
        }

    private fun GestureAction?.orNullIfNone(): GestureAction? = this?.takeUnless { it == GestureAction.None }
}
