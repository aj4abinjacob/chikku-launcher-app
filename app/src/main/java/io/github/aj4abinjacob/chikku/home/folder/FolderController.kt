package io.github.aj4abinjacob.chikku.home.folder

import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.annotation.StringRes
import androidx.core.view.ViewCompat
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.CellPlacement
import io.github.aj4abinjacob.chikku.core.model.EditResult
import io.github.aj4abinjacob.chikku.core.model.GestureAction
import io.github.aj4abinjacob.chikku.core.model.ItemTarget
import io.github.aj4abinjacob.chikku.core.model.LauncherApp
import io.github.aj4abinjacob.chikku.core.model.ProfileType
import io.github.aj4abinjacob.chikku.core.model.WorkspaceRepository
import io.github.aj4abinjacob.chikku.core.model.settings.FolderCover
import io.github.aj4abinjacob.chikku.core.model.settings.FolderCoverMode
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.home.AppIconView
import io.github.aj4abinjacob.chikku.home.Availability
import io.github.aj4abinjacob.chikku.home.DragPayload
import io.github.aj4abinjacob.chikku.home.FolderEntryUi
import io.github.aj4abinjacob.chikku.home.FolderViewBinder
import io.github.aj4abinjacob.chikku.home.HomeItemUi
import io.github.aj4abinjacob.chikku.home.HomeUiState
import io.github.aj4abinjacob.chikku.home.IconBinder
import io.github.aj4abinjacob.chikku.home.ItemContent
import io.github.aj4abinjacob.chikku.home.WorkspaceUi
import io.github.aj4abinjacob.chikku.home.appmenu.AppMenuHandle
import io.github.aj4abinjacob.chikku.home.appmenu.AppMenuTarget
import io.github.aj4abinjacob.chikku.home.appmenu.LongPressMenuDrag
import io.github.aj4abinjacob.chikku.home.appmenu.ShortcutMenuTarget
import io.github.aj4abinjacob.chikku.home.showItemFootprint
import io.github.aj4abinjacob.chikku.platform.IconProvider
import io.github.aj4abinjacob.chikku.platform.ShortcutIconLoader
import io.github.aj4abinjacob.chikku.platform.openStoreListing
import io.github.aj4abinjacob.chikku.prompt.ChikkuPrompts
import io.github.aj4abinjacob.chikku.prompt.PromptAction
import io.github.aj4abinjacob.chikku.prompt.PromptHandle
import io.github.aj4abinjacob.chikku.prompt.PromptSpec
import io.github.aj4abinjacob.chikku.prompt.PromptSubject
import io.github.aj4abinjacob.chikku.prompt.PromptTone
import io.github.aj4abinjacob.chikku.theme.LauncherSurface
import io.github.aj4abinjacob.chikku.theme.Motion
import io.github.aj4abinjacob.chikku.theme.NightModeResolver
import io.github.aj4abinjacob.chikku.theme.SurfaceColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the folders feature needs from HOME. HomeActivity implements it. */
interface FolderHost {
    /** Runs a workspace edit; [undoable] shows the undo bar when it applies (removals). */
    fun edit(undoable: Boolean = false, block: suspend (WorkspaceRepository) -> EditResult)
    fun launchApp(key: AppKey, view: View)

    /** Runs a folder tap/swipe action (to be unified with the gestures feature's executor). */
    fun performAction(action: GestureAction, view: View)
    fun startDrag(view: View, payload: DragPayload): Boolean

    /** The long-press app menu for an app inside a folder; null when the host has none. */
    fun showAppMenu(view: View, target: AppMenuTarget): AppMenuHandle? = null

    /** The long-press menu for a pinned shortcut inside a folder; null when the host has none. */
    fun showShortcutMenu(view: View, target: ShortcutMenuTarget): AppMenuHandle? = null
    fun rebindFolders()

    /** Shows the Compose folder editor ([FolderEditSheet]). */
    fun showFolderEditor(request: FolderEditRequest)

    /** Launchable apps as currently listed, to tell a missing app from a missing profile. */
    fun apps(): List<LauncherApp>
    fun showMessage(@StringRes message: Int)
    fun setWorkspaceAccessibilityHidden(hidden: Boolean)
}

/**
 * Folders on HOME: folder icons and their gestures, the open-folder popup, folder drops and the
 * per-folder editor. Owns no state that outlives the activity; everything persistent goes
 * through [WorkspaceRepository] via [FolderHost.edit].
 */
class FolderController(
    private val activity: ComponentActivity,
    root: FrameLayout,
    private val iconBinder: IconBinder,
    iconProvider: IconProvider,
    scope: CoroutineScope,
    private val host: FolderHost,
    private val onCoverChanged: (String, FolderCover) -> Unit = { _, _ -> },
    /** Chikku's own prompts (HOME passes its shared, accent-aware instance). */
    private val prompts: ChikkuPrompts = ChikkuPrompts(activity),
    private val metadata: io.github.aj4abinjacob.chikku.platform.ShortcutMetadataPolicy = io.github.aj4abinjacob.chikku.platform.ShortcutMetadataPolicy.DENY,
    private val externalIntents: io.github.aj4abinjacob.chikku.platform.ExternalIntentStarter = io.github.aj4abinjacob.chikku.platform.ExternalIntentStarter.DENY,
    /** Emits when the app lock may have changed which shortcut icons [metadata] allows. */
    metadataChanges: kotlinx.coroutines.flow.Flow<Any> = kotlinx.coroutines.flow.emptyFlow(),
    /** The app-wide icon-pack repository for shortcut icons; null builds a private one. */
    iconPacks: io.github.aj4abinjacob.chikku.platform.icons.IconPackRepository? = null,
) : FolderViewBinder {
    /** This controller's prompt on HOME's shared [ChikkuPrompts]; closing it never closes another's. */
    private var prompt: PromptHandle? = null

    private val icons = FolderIconBinder(activity, scope, iconProvider, ShortcutIconLoader(activity, metadata, iconProvider, iconPacks)::load)
    private var settings = LauncherSettings()
    private var workspace: WorkspaceUi.Ready? = null
    private var menu: FolderMenuPopup? = null

    private val back = object : OnBackPressedCallback(false) {
        override fun handleOnBackStarted(backEvent: BackEventCompat) = handleOnBackProgressed(backEvent)

        override fun handleOnBackProgressed(backEvent: BackEventCompat) =
            popup.setBackProgress(backEvent.progress, backEvent.swipeEdge == BackEventCompat.EDGE_LEFT)

        override fun handleOnBackCancelled() = popup.cancelBackProgress()

        override fun handleOnBackPressed() = popup.close(animate = true)
    }

    private val popup: FolderPopup = FolderPopup(root, iconBinder, PopupListener())

    private val wallpaperManager: WallpaperManager? = runCatching { WallpaperManager.getInstance(activity) }.getOrNull()
    private val wallpaperColorsListener = WallpaperManager.OnColorsChangedListener { colors, which ->
        if (which and WallpaperManager.FLAG_SYSTEM != 0) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) accentStale = true // re-read on the next render
            else popup.wallpaperTint = colors?.primaryColor?.toArgb()
        }
    }

    /** Android 12+ system accent, read once and again after a wallpaper or night-mode change. */
    private var accentStale = true
    private var accentConfiguration: android.content.res.Configuration? = null

    init {
        // Added after HOME's own callbacks, so Back closes an open folder first.
        activity.onBackPressedDispatcher.addCallback(activity, back)
        // The panel takes a hint of the wallpaper's colour. Android 12+ exposes the wallpaper-derived
        // system palette as resources; older versions read WallpaperColors (IPC, off the main thread).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            wallpaperManager?.addOnColorsChangedListener(wallpaperColorsListener, Handler(Looper.getMainLooper()))
        } else {
            wallpaperManager?.let { manager ->
                scope.launch(Dispatchers.IO) {
                    val primary = runCatching { manager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM) }.getOrNull()?.primaryColor?.toArgb()
                    withContext(Dispatchers.Main) { popup.wallpaperTint = primary }
                }
                manager.addOnColorsChangedListener(wallpaperColorsListener, Handler(Looper.getMainLooper()))
            }
        }
        // App icons on HOME reload themselves on a theme or per-app icon change; folder previews are
        // drawn from their own copies, so they redraw too.
        scope.launch { iconProvider.iconRevision.drop(1).collect { host.rebindFolders() } }
        // An app update that changed its icon redraws only the folders previewing that package.
        scope.launch { iconProvider.packageInvalidations.collect { icons.invalidatePackage(it); host.rebindFolders() } }
        // Locking or unlocking an app flips its shortcut previews to the placeholder or back.
        scope.launch { metadataChanges.collect { onShortcutsChanged() } }
    }

    /** An app changed its pinned shortcuts; folder previews showing one of them reload. */
    fun onShortcutsChanged() {
        icons.invalidateShortcuts()
        host.rebindFolders()
    }

    val isOpen: Boolean get() = popup.isOpen

    private fun refreshAccent() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val configuration = activity.resources.configuration
        if (accentStale || configuration != accentConfiguration) {
            accentStale = false
            accentConfiguration = android.content.res.Configuration(configuration)
            popup.wallpaperTint = activity.getColor(android.R.color.system_accent1_500)
        }
    }

    fun render(state: HomeUiState) {
        val previousFolders = settings.folders
        settings = state.settings
        workspace = state.workspace as? WorkspaceUi.Ready
        popup.motion = Motion.current(settings.lookFeel.animationSpeed)
        popup.accentColor = settings.lookFeel.accentColor
        popup.themePanelColor = themePanelColor(settings)
        // Re-read after a wallpaper or configuration change: HOME may keep running across a palette change.
        refreshAccent()
        if (previousFolders != settings.folders) host.rebindFolders()
        val openId = popup.openFolderId ?: return
        val item = workspace?.findItem(openId)
        if (item == null || item.content !is ItemContent.Folder) popup.close() else popup.update(item, popupSettings())
    }

    private fun popupSettings() = settings.folders.let { folders ->
        if (folders.matchDesktopSize) folders.copy(iconScale = settings.home.iconScale) else folders
    }

    /** Explicit folder-search/navigation action, independent of a cover app's custom tap action. */
    fun openFolder(item: HomeItemUi) {
        if (item.content !is ItemContent.Folder) return
        refreshAccent() // a wallpaper change since the last render must tint this open too
        popup.open(item, popupSettings())
    }

    fun close() {
        menu?.dismiss()
        menu = null
        popup.close()
    }

    /** The app shown as this folder's icon (cover), or null when the icon is the usual preview. */
    fun coverApp(item: HomeItemUi): ItemContent.App? {
        val folder = item.content as? ItemContent.Folder ?: return null
        return folderCoverEntry(folder, settings.folders.covers[item.id]) as? ItemContent.App
    }

    fun showMenu(item: HomeItemUi, anchor: View): AppMenuHandle? {
        if (item.content !is ItemContent.Folder) return null
        menu?.dismiss()
        return FolderMenuPopup(
            activity, settings,
            onEdit = { showEditDialog(item) },
            onOpen = { openFolder(item) },
            onRemove = { host.edit(undoable = true) { it.removeItem(item.id) } },
            onLocked = { host.showMessage(R.string.home_layout_locked) },
            isLocked = { settings.home.layoutLocked },
        ).also { menu = it; it.show(anchor, onDismiss = showItemFootprint(anchor, settings)) }
    }

    /**
     * Folder panel base color from night mode ("dark folders"): the dark tokens while it applies,
     * light tokens when night mode is off for folders but the configuration is night, else the theme.
     */
    private fun themePanelColor(settings: LauncherSettings): Int? {
        val colors = SurfaceColors.resolve(LauncherSurface.FOLDER, settings.night, activity)
        return when {
            colors.isDark -> colors.background
            NightModeResolver.isConfigurationNight(activity.resources.configuration) -> colors.background
            else -> null
        }
    }

    fun onDestroy() {
        wallpaperManager?.removeOnColorsChangedListener(wallpaperColorsListener)
        menu?.dismiss()
        menu = null
        prompt?.dismiss()
        prompt = null
    }

    // --- Folder icon -----------------------------------------------------------------------------

    // The touch listener never swallows taps (performClick still runs); swipes have TalkBack actions.
    @SuppressLint("ClickableViewAccessibility")
    override fun bindFolder(view: AppIconView, item: HomeItemUi) {
        val content = item.content as? ItemContent.Folder ?: return
        iconBinder.releaseApp(view) // the view may have shown an app before
        icons.bind(view, content, settings.folders, item.id)
        // Aggregate notification dot for the folder's apps (badges feature), after the description.
        iconBinder.badges?.bindFolder(view, content.entries.mapNotNull { (it.content as? ItemContent.App)?.key })
        bindSwipeAccessibilityActions(view, item)
        if (view.getTag(R.id.folder_gestures_bound) == null) {
            view.setTag(R.id.folder_gestures_bound, true)
            val swipes = FolderSwipeDetector(
                claims = { gesture ->
                    val current = view.itemModel() ?: return@FolderSwipeDetector false
                    FolderGestures.claimsSwipe(gesture, current.actions, settings.folders.swipeActionsEnabled)
                },
                onSwipe = { v, gesture -> v.itemModel()?.let { onGesture(it, gesture, v) } },
            )
            // Workspace icons already carry the long-press menu/drag touch helper; chain onto it.
            val longPress = view.getTag(R.id.home_icon_touch) as? LongPressMenuDrag
            if (longPress != null) longPress.secondary = swipes else view.setOnTouchListener(swipes)
            ViewCompat.addAccessibilityAction(view, activity.getString(R.string.folder_action_open)) { v, _ ->
                v.itemModel()?.let { refreshAccent(); popup.open(it, popupSettings(), anchor = v) }
                true
            }
            ViewCompat.addAccessibilityAction(view, activity.getString(R.string.folder_action_edit)) { v, _ ->
                v.itemModel()?.let(::showEditDialog)
                true
            }
        }
    }

    /** TalkBack equivalents of the folder's swipes, present only while a swipe does something. */
    private fun bindSwipeAccessibilityActions(view: AppIconView, item: HomeItemUi) {
        @Suppress("UNCHECKED_CAST")
        (view.getTag(R.id.folder_swipe_action_ids) as? List<Int>)?.forEach { ViewCompat.removeAccessibilityAction(view, it) }
        val enabled = settings.folders.swipeActionsEnabled
        val ids = listOf(
            FolderGesture.SWIPE_UP to R.string.folder_action_swipe_up,
            FolderGesture.SWIPE_DOWN to R.string.folder_action_swipe_down,
        ).filter { (gesture, _) -> FolderGestures.claimsSwipe(gesture, item.actions, enabled) }
            .map { (gesture, label) ->
                ViewCompat.addAccessibilityAction(view, activity.getString(label)) { v, _ ->
                    v.itemModel()?.let { onGesture(it, gesture, v) }
                    true
                }
            }
        view.setTag(R.id.folder_swipe_action_ids, ids)
    }

    /** Tap on a folder icon. */
    fun onFolderClick(item: HomeItemUi, view: View) = onGesture(item, FolderGesture.TAP, view)

    private fun onGesture(item: HomeItemUi, gesture: FolderGesture, view: View) {
        when (val outcome = FolderGestures.resolve(gesture, item.actions, settings.folders.swipeActionsEnabled)) {
            FolderGestureOutcome.OpenFolder -> { refreshAccent(); popup.open(item, popupSettings(), anchor = view) }
            is FolderGestureOutcome.Perform -> host.performAction(outcome.action, view)
            FolderGestureOutcome.PassThrough -> Unit
        }
    }

    // --- Drops -----------------------------------------------------------------------------------

    /** Handles folder drops; returns false when the host's plain move/add should run instead. */
    fun handleDrop(payload: DragPayload, target: CellPlacement): Boolean {
        when (val plan = FolderDropPlanner.plan(payload, target, workspace)) {
            DropPlan.Default -> return false
            DropPlan.Ignore -> Unit
            is DropPlan.CreateFolder -> host.edit { it.createFolder(plan.anchorItemId, plan.source, title = "") }
            is DropPlan.AddToFolder -> host.edit { it.addToFolder(plan.folderId, plan.source) }
            is DropPlan.MoveOutOfFolder -> host.edit { it.removeFromFolder(plan.folderId, plan.entryId, plan.to) }
        }
        return true
    }

    fun removeEntry(folderId: String, entryId: String) =
        host.edit(undoable = true) { it.removeFromFolder(folderId, entryId) }

    // --- Editor ----------------------------------------------------------------------------------

    private fun showEditDialog(item: HomeItemUi) {
        val content = item.content as? ItemContent.Folder ?: return
        if (settings.home.layoutLocked) {
            host.showMessage(R.string.home_layout_locked)
            return
        }
        popup.close()
        showFolderEditor(item, content)
    }

    /** The settings-styled editor; saving re-checks the layout lock and that the folder still exists. */
    private fun showFolderEditor(item: HomeItemUi, content: ItemContent.Folder) {
        val entries = content.entries.mapNotNull { entry ->
            when (val c = entry.content) {
                is ItemContent.App -> if (c.availability != Availability.AVAILABLE) null
                    else FolderEditApp(c.key, c.label.orEmpty(), c.isWork)
                is ItemContent.Shortcut -> if (c.availability != Availability.AVAILABLE) null
                    else FolderEditShortcut(
                        GestureAction.LaunchShortcut(c.packageName, c.shortcutId, c.userSerial),
                        c.label ?: activity.getString(R.string.home_unknown_app),
                        c.iconPackPackage, c.iconDrawableName,
                    )
                is ItemContent.Folder, is ItemContent.Widget -> null
            }
        }
        val currentCover = settings.folders.covers[item.id] ?: FolderCover()
        val request = FolderEditRequest(
            title = content.title,
            entries = entries,
            actions = item.actions,
            cover = currentCover,
            gesturesEnabled = settings.folders.swipeActionsEnabled,
        ) { title, actions, cover ->
            val liveFolder = workspace?.findItem(item.id)?.content as? ItemContent.Folder
            when {
                settings.home.layoutLocked -> {
                    host.showMessage(R.string.home_layout_locked)
                    false
                }
                liveFolder == null -> false
                else -> {
                    if (title != liveFolder.title) host.edit { it.renameFolder(item.id, title) }
                    if (actions != item.actions) host.edit { it.setItemActions(item.id, actions) }
                    if (cover != currentCover) {
                        if (cover.mode != FolderCoverMode.SELECTED_APP || folderCoverEntry(liveFolder, cover) != null) {
                            onCoverChanged(item.id, cover)
                        } else {
                            host.showMessage(R.string.folder_entry_unavailable_message)
                        }
                    }
                    true
                }
            }
        }
        host.showFolderEditor(request)
    }

    /**
     * Tap on an entry whose app is gone (the profile is listed, the package is not). The place is
     * kept until the user removes it; a personal-profile app can be looked up in Play Store (a work
     * or other profile app would install into the wrong profile from here, so it is not offered).
     */
    private fun showUnavailableEntry(folderId: String, entryId: String, content: ItemContent) {
        val (packageName, userSerial, label, isShortcut) = when (content) {
            is ItemContent.App -> UnavailableEntry(content.key.packageName, content.key.userSerial, content.label, false)
            is ItemContent.Shortcut -> UnavailableEntry(content.packageName, content.userSerial, content.label, true)
            else -> return
        }
        val personal = host.apps().any { it.key.userSerial == userSerial && it.profileType == ProfileType.PERSONAL }
        prompt = prompts.show(
            PromptSpec(
                icon = R.drawable.ic_menu_remove,
                title = activity.getString(
                    if (isShortcut) R.string.prompt_folder_shortcut_unavailable_title else R.string.prompt_folder_app_unavailable_title,
                ),
                message = activity.getString(
                    if (isShortcut) R.string.prompt_folder_shortcut_unavailable_message else R.string.prompt_folder_app_unavailable_message,
                ),
                primary = PromptAction(activity.getString(R.string.folder_action_remove_entry)) { removeEntry(folderId, entryId) },
                dismissLabel = activity.getString(R.string.prompt_keep),
                tone = PromptTone.DESTRUCTIVE,
                subject = label?.takeIf { it.isNotBlank() }?.let {
                    PromptSubject(it, status = activity.getString(R.string.prompt_status_unavailable))
                },
                secondary = if (personal) {
                    PromptAction(activity.getString(R.string.prompt_find_in_play_store)) { if (!activity.openStoreListing(packageName, externalIntents)) host.showMessage(R.string.prompt_open_store_failed) }
                } else {
                    null
                },
            ),
        )
    }

    private data class UnavailableEntry(val packageName: String, val userSerial: Long, val label: String?, val isShortcut: Boolean)

    private inner class PopupListener : FolderPopup.Listener {
        override fun onEntryClick(folder: HomeItemUi, entry: FolderEntryUi, view: View) {
            when (val c = entry.content) {
                is ItemContent.App ->
                    if (c.availability == Availability.UNAVAILABLE) {
                        showUnavailableEntry(folder.id, entry.id, c)
                    } else {
                        host.launchApp(c.key, view)
                        if (settings.folders.closeOnLaunch) popup.close()
                    }
                is ItemContent.Shortcut ->
                    if (c.availability == Availability.UNAVAILABLE) {
                        showUnavailableEntry(folder.id, entry.id, c)
                    } else {
                        host.performAction(GestureAction.LaunchShortcut(c.packageName, c.shortcutId, c.userSerial), view)
                        if (settings.folders.closeOnLaunch) popup.close()
                    }
                is ItemContent.Folder, is ItemContent.Widget -> Unit
            }
        }

        override fun onEntryLongClick(folder: HomeItemUi, entry: FolderEntryUi, view: View): Boolean {
            val target = when (val c = entry.content) {
                is ItemContent.App -> ItemTarget.App(c.key)
                is ItemContent.Shortcut ->
                    ItemTarget.Shortcut(c.packageName, c.shortcutId, c.userSerial, c.label, c.iconPackPackage, c.iconDrawableName)
                is ItemContent.Folder, is ItemContent.Widget -> return false
            }
            return host.startDrag(view, DragPayload.FromFolder(folder.id, entry.id, target))
        }

        override fun onEntryMenu(folder: HomeItemUi, entry: FolderEntryUi, view: View): AppMenuHandle? {
            if (entry.content is ItemContent.Shortcut) {
                return host.showShortcutMenu(view, ShortcutMenuTarget(entry.id, folderId = folder.id))
            }
            val c = entry.content as? ItemContent.App ?: return null
            if (c.availability == Availability.UNAVAILABLE) return null
            val label = c.label ?: activity.getString(R.string.home_unknown_app)
            return host.showAppMenu(view, AppMenuTarget(c.key, label, homeItemId = entry.id, folderId = folder.id))
        }

        override fun onReorder(folderId: String, orderedEntryIds: List<String>) =
            host.edit { it.reorderFolder(folderId, orderedEntryIds) }

        override fun onRename(folderId: String, title: String) = host.edit { it.renameFolder(folderId, title) }

        override fun onMoveToHome(folderId: String, entryId: String) {
            val cell = workspace?.let { FolderDropPlanner.firstFreeDesktopCell(it) }
            if (cell == null) {
                host.showMessage(R.string.folder_no_space)
                return
            }
            host.edit { it.removeFromFolder(folderId, entryId, cell) }
        }

        override fun onRemoveEntry(folderId: String, entryId: String) = removeEntry(folderId, entryId)

        override fun onEdit(folder: HomeItemUi) = showEditDialog(folder)

        override fun onOpenChanged(open: Boolean) {
            back.isEnabled = open
            host.setWorkspaceAccessibilityHidden(open)
        }
    }

    private fun View.itemModel(): HomeItemUi? = getTag(R.id.home_item_model) as? HomeItemUi

    private fun WorkspaceUi.Ready.findItem(id: String): HomeItemUi? =
        pages.asSequence().flatten().firstOrNull { it.id == id } ?: dock.firstOrNull { it.id == id }

}
