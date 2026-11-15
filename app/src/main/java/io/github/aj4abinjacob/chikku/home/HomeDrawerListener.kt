package io.github.aj4abinjacob.chikku.home

import android.view.View
import io.github.aj4abinjacob.chikku.core.model.LauncherApp
import io.github.aj4abinjacob.chikku.home.appmenu.AppMenuController
import io.github.aj4abinjacob.chikku.home.appmenu.AppMenuHandle
import io.github.aj4abinjacob.chikku.home.appmenu.AppMenuTarget

/** App drawer row callbacks: launch, drag to the workspace, add to home, info and the app menu. */
internal class HomeDrawerListener(
    private val viewModel: HomeViewModel,
    private val workspace: WorkspaceController,
    private val drawerMotion: DrawerMotion,
    private val drawer: View,
    private val appMenu: AppMenuController,
    private val closeDrawer: () -> Unit,
) : DrawerItemListener {
    override fun onAppClick(app: LauncherApp, view: View) = viewModel.launchApp(app.key, view.screenBounds())

    override fun onAppLongClick(app: LauncherApp, view: View): Boolean {
        if (!workspace.isReady) return false
        if (workspace.refuseIfLocked()) return false // refused up front: the drawer must not blink away and back
        // Hide the drawer before the drag starts: views that are visible at DRAG_STARTED (the
        // search field accepts any drag) stay registered as drop targets even after hiding,
        // which swallowed drops meant for the workspace on a real device.
        drawerMotion.jumpTo(open = false)
        drawer.visibility = View.GONE
        val started = workspace.startDrag(view, DragPayload.NewApp(app.key))
        if (started) closeDrawer() else drawerMotion.jumpTo(open = true)
        return started
    }

    override fun onAddToHome(app: LauncherApp) = viewModel.addApp(app.key, null)

    override fun onAppInfo(app: LauncherApp, view: View) = viewModel.openAppInfo(app.key, view.screenBounds())

    override fun onAppMenu(app: LauncherApp, view: View): AppMenuHandle? =
        appMenu.show(view, AppMenuTarget(app.key, app.label, fromDrawer = true))
}
