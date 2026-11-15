package io.github.aj4abinjacob.chikku.home

import android.view.View
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.EditResult
import io.github.aj4abinjacob.chikku.core.model.GestureAction
import io.github.aj4abinjacob.chikku.core.model.LauncherApp
import io.github.aj4abinjacob.chikku.core.model.WorkspaceRepository
import io.github.aj4abinjacob.chikku.home.appmenu.AppMenuController
import io.github.aj4abinjacob.chikku.home.appmenu.AppMenuHandle
import io.github.aj4abinjacob.chikku.home.appmenu.AppMenuTarget
import io.github.aj4abinjacob.chikku.home.appmenu.ShortcutMenuTarget
import io.github.aj4abinjacob.chikku.home.folder.FolderEditRequest
import io.github.aj4abinjacob.chikku.home.folder.FolderHost
import io.github.aj4abinjacob.chikku.home.gestures.HomeGestureController
import io.github.aj4abinjacob.chikku.home.widget.WidgetsController
import io.github.aj4abinjacob.chikku.message.ChikkuMessageBarView

/** Folder feature hooks; folder tap/swipe actions run through the gestures executor. */
internal class HomeFolderHost(
    private val viewModel: HomeViewModel,
    private val gestures: HomeGestureController,
    private val workspace: WorkspaceController,
    private val widgets: WidgetsController,
    private val homeMenu: HomeMenu,
    private val appMenu: AppMenuController,
    private val messageBar: ChikkuMessageBarView,
    private val composeDialogs: ComposeDialogHost,
    private val content: View,
    private val cardsAreOpen: () -> Boolean,
    private val searchIsOpen: () -> Boolean,
) : FolderHost {
    override fun edit(undoable: Boolean, block: suspend (WorkspaceRepository) -> EditResult) {
        // Layout lock (G17) covers folder edits too: create, add, remove, reorder, rename, actions.
        if (viewModel.rejectIfLayoutLocked()) return
        viewModel.editWorkspace(if (undoable) HomeEvent.ItemRemoved else null, block)
    }

    override fun launchApp(key: AppKey, view: View) = viewModel.launchApp(key, view.screenBounds())

    override fun performAction(action: GestureAction, view: View) = gestures.perform(action, view)

    override fun startDrag(view: View, payload: DragPayload): Boolean = workspace.startDrag(view, payload)

    override fun showAppMenu(view: View, target: AppMenuTarget): AppMenuHandle? {
        homeMenu.dismiss()
        widgets.dismissMenus()
        return appMenu.show(view, target)
    }

    override fun showShortcutMenu(view: View, target: ShortcutMenuTarget): AppMenuHandle? {
        homeMenu.dismiss()
        widgets.dismissMenus()
        return appMenu.showShortcut(view, target)
    }

    override fun rebindFolders() = workspace.rebindFolders()

    override fun apps(): List<LauncherApp> = (viewModel.uiState.value.drawer as? DrawerUi.Apps)?.apps.orEmpty()

    override fun showMessage(message: Int) = messageBar.show(message)

    override fun showFolderEditor(request: FolderEditRequest) {
        composeDialogs.showFolderEditor(request)
    }

    override fun setWorkspaceAccessibilityHidden(hidden: Boolean) {
        if (hidden) { // a folder opened: close the other menus
            appMenu.dismiss()
            homeMenu.dismiss()
            widgets.dismissMenus()
        }
        content.importantForAccessibility = if (hidden || cardsAreOpen() || searchIsOpen()) {
            View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        } else {
            View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        }
    }
}
