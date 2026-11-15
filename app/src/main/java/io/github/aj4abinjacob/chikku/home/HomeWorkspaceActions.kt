package io.github.aj4abinjacob.chikku.home

import android.view.View
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.AppsState
import io.github.aj4abinjacob.chikku.core.model.CellPlacement
import io.github.aj4abinjacob.chikku.core.model.GestureAction
import io.github.aj4abinjacob.chikku.home.appmenu.AppMenuController
import io.github.aj4abinjacob.chikku.home.appmenu.AppMenuHandle
import io.github.aj4abinjacob.chikku.home.appmenu.AppMenuTarget
import io.github.aj4abinjacob.chikku.home.appmenu.ShortcutMenuTarget
import io.github.aj4abinjacob.chikku.home.folder.FolderController
import io.github.aj4abinjacob.chikku.home.gestures.HomeGestureController
import io.github.aj4abinjacob.chikku.home.widget.WidgetsController
import io.github.aj4abinjacob.chikku.message.ChikkuMessageBarView
import io.github.aj4abinjacob.chikku.platform.openStoreListing
import io.github.aj4abinjacob.chikku.prompt.ChikkuPrompts
import io.github.aj4abinjacob.chikku.prompt.PromptAction
import io.github.aj4abinjacob.chikku.prompt.PromptSpec
import io.github.aj4abinjacob.chikku.prompt.PromptSubject
import io.github.aj4abinjacob.chikku.prompt.PromptTone
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * What the workspace (icons, drops, long-press menus) may do to HOME. Controllers that are created
 * after this object are passed as providers and read only when an action runs.
 */
internal class HomeWorkspaceActions(
    private val activity: ComponentActivity,
    private val deps: HomeDependencies,
    private val viewModel: HomeViewModel,
    private val messageBar: ChikkuMessageBarView,
    private val prompts: ChikkuPrompts,
    private val externalIntents: GuardedExternalActions,
    private val appMenu: AppMenuController,
    private val folders: () -> FolderController,
    private val widgets: () -> WidgetsController,
    private val gestures: () -> HomeGestureController,
    private val homeMenu: () -> HomeMenu,
) : WorkspaceActions {
    override fun onItemClick(item: HomeItemUi, view: View) {
        when (val content = item.content) {
            is ItemContent.App ->
                if (content.availability == Availability.UNAVAILABLE) {
                    showUnavailableDialog(item.id, content.key, content.label)
                } else {
                    viewModel.launchApp(content.key, view.screenBounds())
                }
            is ItemContent.Folder -> folders().onFolderClick(item, view)
            is ItemContent.Shortcut -> gestures().perform(
                GestureAction.LaunchShortcut(content.packageName, content.shortcutId, content.userSerial), view)
            is ItemContent.Widget -> Unit
        }
    }

    override fun onDrop(payload: DragPayload, target: CellPlacement) {
        if (payload is DragPayload.NewApp) {
            activity.lifecycleScope.launch {
                val (apps, custom) = try {
                    deps.appsRepository.apps.first() to deps.appCustomizationRepository.customizations.first()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    messageBar.show(R.string.home_launch_unavailable)
                    return@launch
                }
                val listed = apps as? AppsState.Loaded
                val state = viewModel.uiState.value
                if (listed?.apps?.none { it.key == payload.key && it.key.userSerial in listed.listedProfiles } != false || custom[payload.key]?.hidden == true ||
                    state.safeMode != false || state.workspace !is WorkspaceUi.Ready) return@launch
                if (viewModel.rejectIfLayoutLocked()) return@launch
                if (!folders().handleDrop(payload, target)) viewModel.addApp(payload.key, target)
            }
            return
        }
        if (folders().handleDrop(payload, target)) return
        when (payload) {
            is DragPayload.Existing -> viewModel.moveItem(payload.itemId, target)
            is DragPayload.NewApp -> Unit // revalidated above, including drops into folders
            is DragPayload.NewWidget -> widgets().addDropped(payload, target)
            is DragPayload.FromFolder -> Unit // always planned by the folders feature
        }
    }

    override fun onRemoveItem(itemId: String) = viewModel.removeItem(itemId)

    override fun onRemoveFolderEntry(folderId: String, entryId: String) = folders().removeEntry(folderId, entryId)

    override fun onAppInfo(key: AppKey, view: View) = viewModel.openAppInfo(key, view.screenBounds())

    override fun onShowItemMenu(item: HomeItemUi, view: View): AppMenuHandle? {
        if (item.content is ItemContent.Folder) {
            homeMenu().dismiss()
            appMenu.dismiss()
            widgets().dismissMenus()
            // A folder whose icon is an app offers that app's actions plus the folder's own.
            val cover = folders().coverApp(item)
            if (cover != null && cover.availability != Availability.UNAVAILABLE) {
                val label = cover.label ?: activity.getString(R.string.home_unknown_app)
                return appMenu.show(view, AppMenuTarget(cover.key, label, onFolderOptions = { folders().showMenu(item, view) }))
            }
            return folders().showMenu(item, view)
        }
        if (item.content is ItemContent.Shortcut) {
            homeMenu().dismiss()
            widgets().dismissMenus()
            return appMenu.showShortcut(view, ShortcutMenuTarget(item.id))
        }
        val content = item.content as? ItemContent.App ?: return null
        if (content.availability == Availability.UNAVAILABLE) return null
        val label = content.label ?: activity.getString(R.string.home_unknown_app)
        homeMenu().dismiss()
        widgets().dismissMenus()
        return appMenu.show(view, AppMenuTarget(content.key, label, homeItemId = item.id))
    }

    /** A home icon whose app is gone, disabled or in a turned-off profile; its place is kept until removed. */
    private fun showUnavailableDialog(itemId: String, key: AppKey, label: String?) {
        // The store installs into the personal profile, so it is offered only for a personal app.
        val personal = activity.getSystemService(android.os.UserManager::class.java)
            ?.getSerialNumberForUser(android.os.Process.myUserHandle()) == key.userSerial
        prompts.show(
            PromptSpec(
                icon = R.drawable.ic_menu_remove,
                title = activity.getString(R.string.home_unavailable_title),
                message = activity.getString(R.string.home_unavailable_message),
                tone = PromptTone.DESTRUCTIVE,
                // An unavailable app has no label in the layout model; a nameless card would say nothing.
                subject = label?.let { PromptSubject(it, status = activity.getString(R.string.prompt_status_unavailable)) },
                primary = PromptAction(activity.getString(R.string.home_action_remove_from_home)) { viewModel.removeItem(itemId) },
                secondary = if (personal) {
                    PromptAction(activity.getString(R.string.prompt_find_in_play_store)) {
                        if (!activity.openStoreListing(key.packageName, externalIntents)) messageBar.show(R.string.prompt_open_store_failed)
                    }
                } else {
                    null
                },
                dismissLabel = activity.getString(R.string.prompt_keep),
            ),
        )
    }
}
