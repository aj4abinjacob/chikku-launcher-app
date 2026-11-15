package io.github.aj4abinjacob.chikku.home

import android.app.Activity
import io.github.aj4abinjacob.chikku.platform.GestureHost
import io.github.aj4abinjacob.chikku.search.SearchLauncher

/** What gesture actions may do to HOME itself. */
internal class HomeGestureHost(
    private val activity: Activity,
    private val dockSurface: DockSurfaceController,
    private val workspace: WorkspaceController,
    private val openDrawerAction: () -> Unit,
    private val openDrawerWithSearchFocused: () -> Unit,
    private val openCardsAction: () -> Unit,
    private val onSuccessfulLaunch: () -> Unit,
) : GestureHost {
    override fun openDrawer() = openDrawerAction()

    override fun openSearch() {
        if (!SearchLauncher.open(activity)) openDrawerWithSearchFocused()
    }

    override fun openCards() = openCardsAction()

    override fun toggleDock() = dockSurface.toggle()

    override fun onAppLaunched() = onSuccessfulLaunch()

    override fun goToDefaultPage() = workspace.goToDefaultPage()
}
