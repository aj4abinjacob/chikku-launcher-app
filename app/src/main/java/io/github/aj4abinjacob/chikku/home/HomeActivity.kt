package io.github.aj4abinjacob.chikku.home

import android.app.WallpaperManager
import android.content.Context
import android.content.ComponentCallbacks2
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnPreDraw
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.applock.AppUnlockPrompt
import io.github.aj4abinjacob.chikku.badges.BadgeBinder
import io.github.aj4abinjacob.chikku.core.model.ContainerType
import io.github.aj4abinjacob.chikku.core.model.RejectReason
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.core.model.settings.NightMode
import io.github.aj4abinjacob.chikku.core.model.settings.StatusBarMode
import io.github.aj4abinjacob.chikku.home.appmenu.AppMenuController
import io.github.aj4abinjacob.chikku.home.appmenu.AppMenuTarget
import io.github.aj4abinjacob.chikku.home.appmenu.ShortcutMenuTarget
import io.github.aj4abinjacob.chikku.home.cards.CardsController
import io.github.aj4abinjacob.chikku.home.drawer.DrawerController
import io.github.aj4abinjacob.chikku.home.drawer.DrawerViews
import io.github.aj4abinjacob.chikku.home.folder.FolderController
import io.github.aj4abinjacob.chikku.home.folder.FolderHost
import io.github.aj4abinjacob.chikku.home.gestures.HomeGestureController
import io.github.aj4abinjacob.chikku.home.widget.WidgetsController
import io.github.aj4abinjacob.chikku.labs.DebugOverlay
import io.github.aj4abinjacob.chikku.message.ChikkuMessageBarView
import io.github.aj4abinjacob.chikku.message.ChikkuMessages
import io.github.aj4abinjacob.chikku.onboarding.OnboardingActivity
import io.github.aj4abinjacob.chikku.platform.GestureActionExecutor
import io.github.aj4abinjacob.chikku.platform.GestureHost
import io.github.aj4abinjacob.chikku.platform.GoogleDiscoverClient
import io.github.aj4abinjacob.chikku.platform.LauncherAppsShortcuts
import io.github.aj4abinjacob.chikku.platform.RememberingShortcutStarter
import io.github.aj4abinjacob.chikku.prompt.ChikkuPrompts
import io.github.aj4abinjacob.chikku.search.HomeSearchEntryPoints
import io.github.aj4abinjacob.chikku.search.HomeSearchOverlay
import io.github.aj4abinjacob.chikku.theme.LauncherSurface
import io.github.aj4abinjacob.chikku.theme.Motion
import io.github.aj4abinjacob.chikku.theme.NightModeController
import io.github.aj4abinjacob.chikku.theme.StatusBarScrim
import io.github.aj4abinjacob.chikku.theme.SurfaceColors
import io.github.aj4abinjacob.chikku.theme.WallpaperTextColor
import io.github.aj4abinjacob.chikku.theme.WindowStyler
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * HOME. Renders [HomeUiState] and forwards user intent to [HomeViewModel]; owns no business logic.
 *
 * `android:stateNotNeeded` stays true in the manifest: after process death HOME restarts clean
 * (drawer closed, empty search), which is the expected launcher behaviour and avoids restoring a
 * stale search query. Configuration changes still keep the drawer and query through the
 * ViewModel's SavedStateHandle.
 */
class HomeActivity : ComponentActivity(), ChikkuMessages.Owner {

    private lateinit var deps: HomeDependencies
    private lateinit var startupGuard: StartupGuard
    private var startupSafeMode: Boolean? = null
    private val viewModel: HomeViewModel by viewModels {
        HomeViewModel.factory(deps, startupGuard, startupSafeMode)
    }

    private lateinit var root: View
    private lateinit var content: VerticalSwipeLayout
    private lateinit var workspacePager: WorkspacePager
    private lateinit var dockSurface: DockSurfaceController
    private lateinit var dock: CellLayout
    private lateinit var pageIndicator: PageIndicatorView
    private lateinit var allAppsButton: View
    private lateinit var recoveryPanel: View
    private lateinit var recoveryTitle: TextView
    private lateinit var recoveryMessage: TextView
    private lateinit var recoveryRetry: Button
    private lateinit var recoveryLeaveSafeMode: Button
    private lateinit var defaultBanner: View
    private lateinit var corruptNotice: View
    private lateinit var drawer: VerticalSwipeLayout
    private lateinit var search: EditText
    private lateinit var drawerStatusContainer: View
    private lateinit var drawerStatus: TextView
    private lateinit var drawerRetry: Button
    private lateinit var drawerList: RecyclerView
    private lateinit var undoBar: UndoBarView

    /**
     * Chikku's message bar (replaces platform toasts), at the undo bar's position. Precedence: an
     * undo bar hides the message showing, and a message shown while an undo bar is up sits just
     * above it, so a message never covers Undo.
     */
    private lateinit var messageBar: ChikkuMessageBarView
    override val chikkuMessages: ChikkuMessages
        get() = if (::messageBar.isInitialized) messageBar else ChikkuMessages.None

    private lateinit var iconBinder: IconBinder
    private lateinit var workspace: WorkspaceController
    private lateinit var drawerController: DrawerController
    private lateinit var drawerBack: OnBackPressedCallback
    private lateinit var drawerMotion: DrawerMotion
    private lateinit var universalSearch: HomeSearchOverlay
    private lateinit var searchEntryPoints: HomeSearchEntryPoints
    private lateinit var gestures: HomeGestureController
    private lateinit var externalIntents: GuardedExternalActions
    private lateinit var discover: GoogleDiscoverClient

    private lateinit var widgets: WidgetsController
    private lateinit var homeMenu: HomeMenu
    private lateinit var cards: CardsController
    private lateinit var folders: FolderController
    private lateinit var composeDialogs: ComposeDialogHost

    // Hosts the controllers call back into; each is created just before its first consumer.
    private lateinit var workspaceActions: WorkspaceActions
    private lateinit var gestureHost: GestureHost
    private lateinit var folderHost: FolderHost
    private lateinit var drawerListener: DrawerItemListener

    private lateinit var discoverOverlay: HomeDiscoverOverlay
    private lateinit var searchFolderOpener: HomeSearchFolderOpener
    private lateinit var pageEditor: HomePageEditor
    private var renderedDrawerOpen: Boolean? = null
    /** One instance reused across settings emissions; it caches its own shaders. */
    private val edgeShadow by lazy { ScreenEdgeShadow(96f * resources.displayMetrics.density) }
    /** HOME's Chikku-styled prompts (one at a time), shared with the controllers; never platform dialogs. */
    private val prompts by lazy { ChikkuPrompts(this) { viewModel.uiState.value.settings.lookFeel.accentColor } }

    // Style (night mode, status bar, app menu); see the theme package.
    private var createdNight: NightMode = NightMode.SYSTEM
    private lateinit var appMenu: AppMenuController
    private lateinit var unlockPrompt: AppUnlockPrompt
    private lateinit var statusBarScrim: StatusBarScrim
    private var statusBarMode = StatusBarMode.TRANSPARENT
    private val wallpaperText = WallpaperTextColor(this, { WallpaperManager.getInstance(this) }) {
        if (::drawerController.isInitialized) updateSystemBarAppearance()
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        createdNight = NightModeController.applyOverride(this, newBase)
    }

    private val roleRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.refreshDefaultHomeStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        lockOrientationOnSmallScreens()
        startStartupGuard(savedInstanceState)
        super.onCreate(savedInstanceState)
        deps = homeDependencies()
        setUpWindow()
        createDockSurface(savedInstanceState)
        applyWindowInsets()
        createDiscoverClient()
        createIconBinder()
        registerSystemObservers()
        createDialogsAndMenus()
        createCollaborators()
        createWorkspace()
        createDrawerAndGestures()
        setUpButtons()
        setUpBack()
        setUpWidgetsAndHomeMenu()
        createFoldersAndCards()
        createUniversalSearch()
        restorePendingSearchFolder(savedInstanceState)
        startObservingState()
        attachLabsAndOnboarding(savedInstanceState)
        armStartupStableCheck()
    }

    private fun lockOrientationOnSmallScreens() {
        requestedOrientation = if (HomeLayoutMetrics.locksToPortrait(resources.configuration.smallestScreenWidthDp)) {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    /** Runs before `super.onCreate` on purpose, see docs/ARCHITECTURE.md (StartupGuard). */
    private fun startStartupGuard(savedInstanceState: Bundle?) {
        // Count this start before anything that could crash, so early crash loops reach safe mode.
        // A configuration change (saved state present) is not a new start.
        startupGuard = StartupGuard.create(this)
        if (savedInstanceState == null) {
            startupSafeMode = try {
                startupGuard.recordStartup()
            } catch (_: Exception) {
                false // the guard itself must never block HOME
            }
        }
    }

    private fun setUpWindow() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_home)
        findViews()
    }

    private fun createDockSurface(savedInstanceState: Bundle?) {
        dockSurface = DockSurfaceController(dock, findViewById(R.id.dock_slot), findViewById(R.id.workspace_area),
            savedInstanceState?.getBoolean("dock_hidden") ?: false, root as FrameLayout, imageScope = lifecycleScope)
        ViewCompat.addAccessibilityAction(workspacePager, getString(R.string.gesture_action_toggle_dock)) { _, _ ->
            dockSurface.toggle()
            true
        }
    }

    private fun createDiscoverClient() {
        discover = GoogleDiscoverClient(this, deps.googleDiscoverResolver, lifecycleScope,
            externalIntents = io.github.aj4abinjacob.chikku.platform.ExternalIntentStarter { externalIntents.start(it) },
            metadata = deps.appLock, metadataChanges = deps.appLock.state) { progress ->
            val direction = if (content.layoutDirection == View.LAYOUT_DIRECTION_RTL) -1 else 1
            content.translationX = progress * content.width * direction
            content.importantForAccessibility = if (progress >= 1f || cardsAreOpen() || searchIsOpen()) View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                else View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        }
        lifecycle.addObserver(discover)
    }

    private fun createIconBinder() {
        val customLabels = deps.appCustomizationRepository.customizations
            .map { all -> all.mapNotNull { (key, c) -> c.customLabel?.let { key to it } }.toMap() }
            .catch { emit(emptyMap()) }
        iconBinder = IconBinder(this, lifecycleScope, deps.iconProvider, BadgeBinder(this), customLabels,
            io.github.aj4abinjacob.chikku.platform.ShortcutIconLoader(this, deps.appLock, deps.iconProvider, deps.iconPackRepository)::load,
            shortcutAccess = deps.appLock.state.map { Triple(it.loaded, it.readFailed, it.locked) }.distinctUntilChanged())
    }

    /** The calendar/date receiver and the shortcut-change observer; both are released in onDestroy. */
    private fun registerSystemObservers() {
        val calendarReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: Intent?) {
                deps.iconProvider.refreshCalendarDate()
            }
        }
        androidx.core.content.ContextCompat.registerReceiver(this, calendarReceiver,
            android.content.IntentFilter().apply {
                addAction(Intent.ACTION_DATE_CHANGED)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            }, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        lifecycle.addObserver(object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onDestroy(owner: androidx.lifecycle.LifecycleOwner) { unregisterReceiver(calendarReceiver) }
        })
        // Apps update, disable and remove their shortcuts without a package event.
        val shortcutChanges = io.github.aj4abinjacob.chikku.platform.observeShortcutChanges(this) { packageName, userSerial ->
            iconBinder.reloadShortcuts(packageName, userSerial)
            if (::folders.isInitialized) folders.onShortcutsChanged()
        }
        lifecycle.addObserver(object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onDestroy(owner: androidx.lifecycle.LifecycleOwner) { shortcutChanges?.close() }
        })
    }

    private fun createDialogsAndMenus() {
        statusBarScrim = StatusBarScrim(root)
        composeDialogs = ComposeDialogHost(root as FrameLayout, deps) { viewModel.uiState.value.settings }
        unlockPrompt = AppUnlockPrompt(this, deps.appLock, prompts, messageBar)
        externalIntents = GuardedExternalActions(this, deps.appLock, deps.settingsRepository.settings, unlockPrompt) {
            messageBar.show(R.string.home_launch_unavailable)
        }
        appMenu = AppMenuController(
            this, lifecycleScope, deps, { viewModel.uiState.value.settings }, viewModel::removeItem,
            onRemoveFromFolder = { folderId, entryId -> folders.removeEntry(folderId, entryId) },
            unlocker = unlockPrompt,
            onEditSwipeActions = ::editSwipeActions,
            showEditor = composeDialogs::showAppEditor,
            dismissEditor = composeDialogs::dismiss,
            onShortcutLaunched = { onSuccessfulLaunch() },
            messages = messageBar,
            externalIntents = externalIntents,
        )
    }

    /** Small collaborators that only need views, prompts and the view model. */
    private fun createCollaborators() {
        discoverOverlay = HomeDiscoverOverlay(this, discover, workspacePager, deps, prompts, messageBar, closeCards = { cards.close() })
        searchFolderOpener = HomeSearchFolderOpener(
            this, viewModel.searchableFolderItems, messageBar,
            uiState = { viewModel.uiState.value },
            foldersReady = { ::folders.isInitialized },
            closeFolders = { folders.close() },
            showFolder = ::showSearchFolder,
        )
        pageEditor = HomePageEditor(this, viewModel, workspacePager, prompts, messageBar)
    }

    private fun createWorkspace() {
        workspaceActions = HomeWorkspaceActions(
            this, deps, viewModel, messageBar, prompts, externalIntents, appMenu,
            folders = { folders }, widgets = { widgets }, gestures = { gestures }, homeMenu = { homeMenu },
        )
        var dockPageWrite: kotlinx.coroutines.Job? = null
        workspace = WorkspaceController(workspacePager, dock, pageIndicator, findViewById(R.id.remove_target), iconBinder, workspaceActions, messageBar) { page ->
            // A multi-page fling writes once, after the dock has settled on its last page.
            dockPageWrite?.cancel()
            dockPageWrite = deps.appScope.launch {
                kotlinx.coroutines.delay(300L)
                deps.settingsRepository.update { current ->
                    if (current.dock.rememberPosition) current.copy(dock = current.dock.copy(lastPage = page)) else current
                }
            }
        }
    }

    /** Drawer motion, home gestures, the drawer itself and the search entry points. */
    private fun createDrawerAndGestures() {
        // Swipe up anywhere on home (desktop, dock, "All apps") pulls the drawer up under the finger;
        // swipe down at the top of the drawer list pulls it closed. Back and the "All apps" button
        // remain the accessible paths.
        drawerMotion = DrawerMotion(drawer) { open -> if (open) openDrawer() else closeDrawer() }
        gestureHost = HomeGestureHost(
            this, dockSurface, workspace,
            openDrawerAction = ::openDrawer,
            openDrawerWithSearchFocused = ::openDrawerWithSearchFocused,
            openCardsAction = ::openCards,
            onSuccessfulLaunch = ::onSuccessfulLaunch,
        )
        // Home gestures (swipe up/down, pinch, two-finger swipes, double tap) and icon swipe actions;
        // swipe up bound to "Open app drawer" still pulls the drawer under the finger.
        gestures = HomeGestureController(
            activity = this,
            surface = content,
            drawerMotion = drawerMotion,
            executor = GestureActionExecutor(this, deps.appLauncher, gestureHost, shortcuts = rememberingShortcuts(), externalIntents = externalIntents),
            unlocker = unlockPrompt,
            settings = { viewModel.uiState.value.settings },
            customizations = deps.appCustomizationRepository.customizations,
            isOnDefaultPage = {
                workspacePager.currentPage == viewModel.uiState.value.settings.home.defaultPage
                    .coerceIn(0, (workspacePager.pageCount - 1).coerceAtLeast(0))
            },
            prompts = prompts,
            messages = messageBar,
        )
        content.listener = gestures
        content.gestureListener = gestures
        drawer.listener = drawerMotion.drawerSwipes { drawerController.isAtTop() }
        setUpDrawer()
        drawerMotion.swipeOpenAllowed = { drawerController.allowsSwipeOpen }
        drawerMotion.canPullToSearch = { drawerController.canPullToSearch }
        drawerMotion.onPullToSearch = { drawerController.onPullToSearch() }
        drawerMotion.onShownChanged = drawerController::onDrawerShown
        searchEntryPoints = HomeSearchEntryPoints(this, search, externalIntents).also { it.bind() }
    }

    /**
     * Folders, cards and their home-menu entries; runs after [setUpBack]. The hosts built here and
     * in the other setup steps take controllers by value, so the order of the setup calls in
     * [onCreate] is load-bearing: a step must not move ahead of the step that creates what it uses.
     */
    private fun createFoldersAndCards() {
        // After setUpBack so Back closes an open folder before anything else.
        folderHost = HomeFolderHost(
            viewModel, gestures, workspace, widgets, homeMenu, appMenu, messageBar, composeDialogs, content,
            cardsAreOpen = ::cardsAreOpen, searchIsOpen = ::searchIsOpen,
        )
        folders = FolderController(this, findViewById(R.id.home_root), iconBinder, deps.iconProvider, lifecycleScope, folderHost,
            iconPacks = deps.iconPackRepository,
            onCoverChanged = { folderId, cover ->
                if (!viewModel.rejectIfLayoutLocked() && viewModel.uiState.value.workspace is WorkspaceUi.Ready) {
                    lifecycleScope.launch {
                        deps.settingsRepository.update { current ->
                            if (current.home.layoutLocked) current else current.copy(folders = current.folders.copy(
                                covers = if (cover.mode == io.github.aj4abinjacob.chikku.core.model.settings.FolderCoverMode.PREVIEW) {
                                    current.folders.covers - folderId
                                } else current.folders.covers + (folderId to cover),
                            ))
                        }
                    }
                }
            },
            prompts = prompts,
            externalIntents = externalIntents,
            metadata = deps.appLock,
            metadataChanges = deps.appLock.state.map { Triple(it.loaded, it.readFailed, it.locked) }.distinctUntilChanged(),
        )
        workspace.folderBinder = folders
        cards = CardsController(this, root as FrameLayout, deps,
            GestureActionExecutor(this, deps.appLauncher, gestureHost, shortcuts = rememberingShortcuts(), externalIntents = externalIntents),
            ::openCardWidget, unlocker = unlockPrompt, prompts = prompts, messages = messageBar)
        homeMenu.addEntry(HomeMenuEntry("cards", R.string.settings_section_cards, 250,
            isVisible = { viewModel.uiState.value.safeMode == false && viewModel.uiState.value.workspace is WorkspaceUi.Ready },
            icon = R.drawable.ic_settings_cards,
            onClick = ::openCards))
        homeMenu.addEntry(HomeMenuEntry("add_page", R.string.home_menu_add_page, 100,
            isVisible = { pageEditor.canEditPages() },
            icon = R.drawable.ic_menu_add_page,
            group = HomeMenuGroup.PAGES,
            onClick = viewModel::addPage))
        // An empty page goes at once. A page with items asks first, then removes them with it; one undo
        // brings the page and everything on it back. The only page cannot be removed.
        homeMenu.addEntry(HomeMenuEntry("remove_page", R.string.home_menu_remove_page, 200,
            isVisible = { pageEditor.canEditPages() },
            icon = R.drawable.ic_menu_remove_page,
            group = HomeMenuGroup.PAGES,
            onClick = pageEditor::removeCurrentPage))
        ViewCompat.addAccessibilityAction(workspacePager, getString(R.string.settings_section_cards)) { _, _ ->
            openCards()
            true
        }
    }

    private fun createUniversalSearch() {
        universalSearch = HomeSearchOverlay(this, root as FrameLayout, deps,
            startAppDrag = { app, source ->
                if (viewModel.uiState.value.safeMode != false || viewModel.uiState.value.workspace !is WorkspaceUi.Ready) false
                else {
                    closeDrawer()
                    drawerMotion.jumpTo(open = false)
                    drawer.visibility = View.GONE
                    workspace.startDrag(source, DragPayload.NewApp(app.key))
                }
            },
            showAppMenu = { app, anchor -> appMenu.show(anchor, AppMenuTarget(app.key, app.label, fromDrawer = true)) },
            unlocker = unlockPrompt,
            openFolder = searchFolderOpener::request,
            onShowingChanged = { showing ->
                content.importantForAccessibility = if (showing || cardsAreOpen() || drawerMotion.targetOpen) {
                    View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                } else View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
            },
        )
        val priorDragEnded = workspace.onDragEnded
        workspace.onDragEnded = {
            priorDragEnded?.invoke()
            universalSearch.finishDrag()
        }
    }

    private fun restorePendingSearchFolder(savedInstanceState: Bundle?) {
        searchFolderOpener.restore(savedInstanceState)
    }

    /** The single STARTED-scoped collection of [HomeViewModel.uiState] that drives [render], plus the side feeds. */
    private fun startObservingState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(::render) }
                launch { viewModel.homeEvents.collect(::onEvent) }
                deps.badgeRepository?.let { repo ->
                    launch {
                        combine(repo.badges, deps.settingsRepository.settings, ::Pair)
                            .collect { (badges, settings) -> iconBinder.badges?.update(badges, settings) }
                    }
                }
                launch { deps.drawerGroupRepository.groups.catch { emit(emptyList()) }.collect(drawerController::setGroups) }
                // Straight from storage (not uiState's defaults), so night mode never flips on a placeholder value.
                launch { deps.settingsRepository.settings.catch { }.collect(::applyLookFeel) }
            }
        }
    }

    /** Labs debug overlay (hidden unless enabled) and first-run onboarding (backup-onboarding feature). */
    private fun attachLabsAndOnboarding(savedInstanceState: Bundle?) {
        DebugOverlay.attach(this, deps.settingsRepository, deps.workspaceRepository)
        if (savedInstanceState == null && startupSafeMode != true) OnboardingActivity.launchIfNeeded(this, deps.settingsRepository)
    }

    /** Crash-loop guard: a startup counts as successful once a frame has stayed up for a while. */
    private fun armStartupStableCheck() {
        root.doOnPreDraw {
            lifecycleScope.launch {
                delay(StartupGuard.STABLE_DELAY_MS)
                viewModel.onStartupStable()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("dock_hidden", dockSurface.hidden)
        outState.putString("search_pending_folder", searchFolderOpener.pendingFolderId)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        deps.iconProvider.refreshCalendarDate()
        viewModel.refreshDefaultHomeStatus()
        if (::workspace.isInitialized) workspace.resetDockPosition()
        applyMotion(viewModel.uiState.value.settings) // the system animation switch may have changed while away
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (::discover.isInitialized) discover.attachWindow()
    }

    override fun onDetachedFromWindow() {
        if (::discover.isInitialized) discover.detachWindow()
        super.onDetachedFromWindow()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleHomeIntent(intent)
    }

    /**
     * HOME pressed again: close the drawer. If HOME was already in front with the drawer closed, run
     * the "Home gesture" action (default: open search); with the drawer or search open, go to the
     * default page as before.
     */
    internal fun handleHomeIntent(intent: Intent) {
        if (searchFolderOpener.queueFolderSearchIntent(intent)) return
        val isHome = intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)
        if (!isHome) return
        searchFolderOpener.reset()
        workspace.resetDockPosition()
        // HOME pressed while HOME was already in front (not brought back from another app).
        val alreadyOnHome = hasWindowFocus() && (intent.flags and Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT) == 0
        // Returning from an app: the system is already animating HOME in, so overlays vanish at once
        // instead of animating shut on top of that transition (visible as a flicker).
        if (!alreadyOnHome) drawerMotion.jumpTo(open = false)
        val searchWasOpen = universalSearch.isShowing
        universalSearch.dismiss()
        val drawerWasOpen = drawerMotion.targetOpen || searchWasOpen
        // HOME that closes the cards panel only closes it; it does not also run the Home gesture.
        val cardsWereOpen = cardsAreOpen()
        discover.close()
        prompts.dismiss()
        gestures.dismissDialog()
        homeMenu.dismiss()
        folders.close()
        cards.close()
        appMenu.dismissAll()
        widgets.dismissMenus()
        composeDialogs.dismiss()
        closeDrawer()
        when {
            !alreadyOnHome || cardsWereOpen -> Unit
            drawerWasOpen -> gestureHost.goToDefaultPage()
            else -> gestures.onHomeButton()
        }
    }

    @Deprecated("Widget configuration activities only report results through this callback.")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (!cards.onActivityResult(requestCode, resultCode) && !widgets.onActivityResult(requestCode, resultCode)) super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onStart() {
        super.onStart()
        // Reload what onTrimMemory released while HOME was hidden, before it is shown again.
        if (::dockSurface.isInitialized) dockSurface.restoreAfterTrim()
    }

    /**
     * Releases activity-held caches that can be rebuilt without the user seeing a reset. Process-wide
     * caches (icons) are trimmed by [io.github.aj4abinjacob.chikku.ChikkuApplication].
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level < ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) return // HOME is visible: keep what is drawn
        // A late signal after a quick return: HOME is on screen again, keep what it draws.
        if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) return
        if (::drawerController.isInitialized) drawerController.onTrimMemory()
        // UI_HIDDEN arrives on every app launch; the dock photo would flash on each return. Drop it
        // only under real pressure, when the process is a background candidate.
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND && ::dockSurface.isInitialized) dockSurface.trimMemory()
    }

    override fun onStop() {
        if (::workspace.isInitialized) workspace.cancelDrag()
        if (::universalSearch.isInitialized) universalSearch.dismiss()
        super.onStop()
    }

    override fun onDestroy() {
        if (::universalSearch.isInitialized) universalSearch.destroy()
        cards.onDestroy()
        folders.onDestroy()
        homeMenu.dismiss()
        prompts.dismiss()
        if (::appMenu.isInitialized) appMenu.dismissAll()
        if (::composeDialogs.isInitialized) composeDialogs.dismiss()
        super.onDestroy()
    }

    private fun findViews() {
        root = findViewById(R.id.home_root)
        content = findViewById(R.id.home_content)
        workspacePager = findViewById(R.id.workspace_pager)
        dock = findViewById(R.id.dock)
        pageIndicator = findViewById(R.id.page_indicator)
        allAppsButton = findViewById(R.id.all_apps_button)
        recoveryPanel = findViewById(R.id.recovery_panel)
        recoveryTitle = findViewById(R.id.recovery_title)
        recoveryMessage = findViewById(R.id.recovery_message)
        recoveryRetry = findViewById(R.id.recovery_retry)
        recoveryLeaveSafeMode = findViewById(R.id.recovery_leave_safe_mode)
        defaultBanner = findViewById(R.id.default_banner)
        corruptNotice = findViewById(R.id.corrupt_notice)
        drawer = findViewById(R.id.drawer)
        search = findViewById(R.id.drawer_search)
        drawerStatusContainer = findViewById(R.id.drawer_status_container)
        drawerStatus = findViewById(R.id.drawer_status)
        drawerRetry = findViewById(R.id.drawer_retry)
        drawerList = findViewById(R.id.drawer_list)
        undoBar = findViewById(R.id.undo_bar)
        messageBar = ChikkuMessageBarView.attach(root as FrameLayout, stackAbove = undoBar)
    }

    private fun applyWindowInsets() {
        val undoMargin = resources.getDimensionPixelSize(R.dimen.home_panel_margin)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            content.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            drawer.setPadding(bars.left, bars.top, bars.right, 0)
            if (::drawerController.isInitialized) drawerController.setBottomInset(max(bars.bottom, ime.bottom), keyboard = max(0, ime.bottom - bars.bottom))
            undoBar.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = undoMargin + max(bars.bottom, ime.bottom)
                leftMargin = undoMargin + bars.left
                rightMargin = undoMargin + bars.right
            }
            insets
        }
    }

    // --- Setup -----------------------------------------------------------------------------------

    private fun setUpDrawer() {
        drawerListener = HomeDrawerListener(viewModel, workspace, drawerMotion, drawer, appMenu, closeDrawer = ::closeDrawer)
        drawerController = DrawerController(
            DrawerViews(
                drawer = drawer,
                search = search,
                tabsScroll = findViewById(R.id.drawer_tabs_scroll),
                tabs = findViewById(R.id.drawer_tabs),
                statusContainer = drawerStatusContainer,
                status = drawerStatus,
                retry = drawerRetry,
                list = drawerList,
                pages = findViewById(R.id.drawer_pages),
                fastScroller = findViewById(R.id.drawer_fast_scroller),
                dots = findViewById(R.id.drawer_page_dots),
                searchMore = findViewById(R.id.drawer_search_more),
            ),
            iconBinder,
            drawerListener,
        )
        ViewCompat.requestApplyInsets(root) // deliver the bottom inset to the controller

        // Restore a query kept across a configuration change; afterwards the field is the source.
        search.setText(viewModel.uiState.value.query)
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = viewModel.setQuery(s?.toString().orEmpty())
        })
        search.setOnEditorActionListener { _, actionId, event ->
            val isGo = actionId == EditorInfo.IME_ACTION_GO ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            if (isGo && viewModel.isDrawerResultCurrent()) {
                // No matching app: hand the query to universal search (web, contacts, shortcuts).
                viewModel.firstResult()?.let { key -> viewModel.launchApp(key, search.screenBounds()) }
                    ?: searchEntryPoints.openWithDrawerQuery()
            }
            isGo
        }
        drawerRetry.setOnClickListener { viewModel.retryApps() }
    }

    private fun setUpButtons() {
        allAppsButton.setOnClickListener { openDrawer() }
        // Always reachable for accessibility services, even when the button is hidden (swipe-up only).
        ViewCompat.addAccessibilityAction(content, getString(R.string.home_open_all_apps)) { _, _ ->
            openDrawer()
            true
        }
        recoveryRetry.setOnClickListener { viewModel.retryWorkspace() }
        recoveryLeaveSafeMode.setOnClickListener { viewModel.leaveSafeMode() }
        findViewById<View>(R.id.recovery_open_drawer).setOnClickListener { openDrawer() }
        findViewById<View>(R.id.recovery_choose_home).setOnClickListener { openHomeSettings() }
        findViewById<View>(R.id.default_banner_set).setOnClickListener { requestDefaultHome() }
        findViewById<View>(R.id.default_banner_dismiss).setOnClickListener { viewModel.dismissDefaultHomeBanner() }
        findViewById<View>(R.id.corrupt_notice_ok).setOnClickListener { viewModel.dismissCorruptNotice() }
    }

    private fun setUpBack() {
        // HOME is the bottom of the stack: Back on the workspace does nothing rather than finishing.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (discover.isOpen) discover.close()
            }
        })
        drawerBack = object : OnBackPressedCallback(false) {
            // Back clears a typed query first; only a swipe that will close the drawer previews it.
            override fun handleOnBackStarted(backEvent: androidx.activity.BackEventCompat) = handleOnBackProgressed(backEvent)

            override fun handleOnBackProgressed(backEvent: androidx.activity.BackEventCompat) {
                if (search.text.isEmpty()) drawerMotion.backProgress(backEvent.progress)
            }

            override fun handleOnBackCancelled() = drawerMotion.cancelBack()

            override fun handleOnBackPressed() {
                if (search.text.isNotEmpty()) search.setText("") else closeDrawer()
            }
        }
        onBackPressedDispatcher.addCallback(this, drawerBack)
    }

    /** Widgets (G16) and the long-press-home menu (G21). Other features add entries to [homeMenu]. */
    private fun setUpWidgetsAndHomeMenu() {
        widgets = WidgetsController(
            this, deps, root as FrameLayout, workspacePager, workspace,
            settings = { viewModel.uiState.value.settings },
            removeItem = viewModel::removeItem,
            prompts = prompts,
            messages = messageBar,
            externalIntents = externalIntents,
        )
        workspace.widgetBinder = widgets
        homeMenu = HomeMenu(this) { viewModel.uiState.value.settings }.also(widgets::installHomeMenuEntries)
        // Only one HOME menu at a time: the home menu, an app menu or a widget's options.
        widgets.onMenuShown = {
            appMenu.dismiss()
            homeMenu.dismiss()
        }
        workspace.onEmptyLongPress = { _, x, y ->
            appMenu.dismiss()
            widgets.dismissMenus()
            homeMenu.show(root, x, y)
            true
        }
        ViewCompat.addAccessibilityAction(workspacePager, getString(R.string.widgets_home_menu_action)) { v, _ ->
            val loc = IntArray(2)
            v.getLocationOnScreen(loc)
            homeMenu.show(root, loc[0] + v.width / 2f, loc[1] + v.height / 2f)
            true
        }
    }

    /** Dismisses the other overlays and opens a verified search-result folder. */
    private fun showSearchFolder(rendered: HomeItemUi) {
        discover.close()
        appMenu.dismissAll()
        homeMenu.dismiss()
        widgets.dismissMenus()
        closeDrawer()
        if (rendered.placement.container == ContainerType.DESKTOP) workspacePager.snapToPage(rendered.placement.page, animate = false)
        folders.openFolder(rendered)
    }

    private fun searchIsOpen(): Boolean = ::universalSearch.isInitialized && universalSearch.isShowing

    /** Search shares HOME's window so result drags stay in the same Android drag session. */
    fun showUniversalSearch(initialQuery: String? = null): Boolean {
        if (!::universalSearch.isInitialized) return false
        discover.close()
        folders.close()
        cards.close()
        prompts.dismiss()
        gestures.dismissDialog()
        homeMenu.dismiss()
        appMenu.dismissAll()
        widgets.dismissMenus()
        return universalSearch.open(initialQuery)
    }

    private fun cardsAreOpen(): Boolean = ::cards.isInitialized && cards.isOpen

    private fun openCards() {
        val state = viewModel.uiState.value
        if (state.safeMode != false || state.workspace !is WorkspaceUi.Ready) return
        if (::universalSearch.isInitialized) universalSearch.dismiss()
        discover.close()
        folders.close()
        prompts.dismiss()
        gestures.dismissDialog()
        appMenu.dismissAll()
        homeMenu.dismiss()
        widgets.dismissMenus()
        composeDialogs.dismiss()
        closeDrawer()
        // The drawer close renders asynchronously; the modal remembers the unobscured value.
        content.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        cards.open()
    }

    /** The card controller revalidates the provider/profile immediately before this callback. */
    private fun openCardWidget(itemId: String) {
        val state = viewModel.uiState.value
        val ready = state.workspace as? WorkspaceUi.Ready ?: return
        if (state.safeMode != false) return
        val item = (ready.pages.flatten() + ready.dock).firstOrNull { it.id == itemId } ?: return
        if (item.content !is ItemContent.Widget) return
        cards.close()
        if (item.placement.container == ContainerType.DESKTOP) workspacePager.snapToPage(item.placement.page, animate = false)
        val grid = if (item.placement.container == ContainerType.DESKTOP) workspacePager.pageAt(item.placement.page) else dock
        grid?.post {
            for (index in 0 until grid.childCount) {
                val child = grid.getChildAt(index)
                if ((child.getTag(R.id.home_item_model) as? HomeItemUi)?.id == itemId) {
                    child.requestFocus()
                    child.performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null)
                    break
                }
            }
        }
    }

    // --- Rendering -------------------------------------------------------------------------------

    private fun render(state: HomeUiState) {
        if (state.safeMode != false || state.workspace !is WorkspaceUi.Ready) cards.close()
        if (state.safeMode == true || state.workspace is WorkspaceUi.Disabled || state.workspace is WorkspaceUi.Failed) {
            if (::universalSearch.isInitialized) universalSearch.dismiss()
        }
        renderWorkspace(state)
        discoverOverlay.render(state)
        folders.render(state)
        drawerController.render(state.drawer, state.drawerQuery, state.settings, resultCurrent = state.drawerQuery == state.query)
        allAppsButton.visibility = if (drawerController.showsOpenButton) View.VISIBLE else View.GONE
        renderDrawerOpen(state.drawerOpen)
        searchEntryPoints.render(state.settings.home, state.settings.search)
        defaultBanner.visibility = if (state.showDefaultHomeBanner) View.VISIBLE else View.GONE
        corruptNotice.visibility = if (state.showCorruptLayoutNotice) View.VISIBLE else View.GONE
        searchFolderOpener.tryOpenSearchFolder(state)
    }

    private fun renderWorkspace(state: HomeUiState) {
        val ui = state.workspace
        val ready = ui is WorkspaceUi.Ready
        workspace.applySettings(state.settings)
        when (ui) {
            is WorkspaceUi.Ready -> {
                workspace.render(ui)
                pageEditor.showAddedPage()
            }
            WorkspaceUi.Loading -> Unit
            WorkspaceUi.Disabled, is WorkspaceUi.Failed -> workspace.clear()
        }
        workspacePager.visibility = if (ready) View.VISIBLE else View.INVISIBLE
        dockSurface.render(state.settings.dock, ready,
            recoveryVisible = ui is WorkspaceUi.Disabled || ui is WorkspaceUi.Failed)
        pageIndicator.visibility = when {
            state.settings.home.pageIndicatorStyle == io.github.aj4abinjacob.chikku.core.model.settings.PageIndicatorStyle.NONE -> View.GONE
            ready && workspacePager.pageCount > 1 -> View.VISIBLE
            else -> View.INVISIBLE
        }

        val safeMode = ui is WorkspaceUi.Disabled
        recoveryPanel.visibility = if (safeMode || ui is WorkspaceUi.Failed) View.VISIBLE else View.GONE
        if (safeMode) {
            recoveryTitle.setText(R.string.home_safe_mode_title)
            recoveryMessage.setText(R.string.home_safe_mode_message)
        } else {
            recoveryTitle.setText(R.string.home_recovery_title)
            recoveryMessage.setText(R.string.home_recovery_message)
        }
        recoveryRetry.visibility = if (ui is WorkspaceUi.Failed) View.VISIBLE else View.GONE
        recoveryLeaveSafeMode.visibility = if (safeMode) View.VISIBLE else View.GONE
    }

    private fun renderDrawerOpen(open: Boolean) {
        drawerBack.isEnabled = open
        if (renderedDrawerOpen == open) return
        val animate = renderedDrawerOpen != null
        renderedDrawerOpen = open
        if (animate) drawerMotion.animateTo(open) else drawerMotion.jumpTo(open)
        if (open) {
            content.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            drawerController.onOpened()
        } else {
            hideKeyboard()
            content.importantForAccessibility = if (cardsAreOpen() || searchIsOpen()) View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                else View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
            drawerController.onClosed()
        }
        updateSystemBarAppearance()
    }

    private fun updateSystemBarAppearance() {
        // Follows the drawer background (theme, night mode or the user's color and opacity) when
        // the drawer is open, and the wallpaper otherwise.
        val light = WindowStyler.lightSystemBars(
            drawerOpen = renderedDrawerOpen == true,
            drawerLight = drawerController.lightSystemBars,
            mode = statusBarMode,
            wallpaperDarkText = wallpaperText.darkText,
        )
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = light.status
            isAppearanceLightNavigationBars = light.navigation
        }
    }

    /** Pushes the animation speed, including the system "Remove animations" switch, to the animated parts. */
    private fun applyMotion(settings: LauncherSettings) {
        val motion = Motion.current(settings.lookFeel.animationSpeed)
        drawerMotion.animationScale = motion.scale
        if (::universalSearch.isInitialized) universalSearch.motion = motion
        workspacePager.snapDurationScale = motion.scale
        undoBar.motion = motion
        dockSurface.motion = motion
        if (::workspace.isInitialized) workspace.refreshMotion()
    }

    /** Night mode, orientation, status bar, animation speed and drawer surface colors (G01, G09, G15). */
    private fun applyLookFeel(settings: LauncherSettings) {
        NightModeController.onModeChanged(this, createdNight, settings.night.mode)
        WindowStyler.applyOrientation(this, settings.lookFeel.orientation)
        WindowStyler.applyStatusBar(window, settings.lookFeel.statusBar, statusBarScrim)
        statusBarMode = settings.lookFeel.statusBar
        applyMotion(settings)
        drawerMotion.transition = settings.drawer.transition
        workspacePager.transitionEffect = settings.home.transitionEffect
        val shadow = if (settings.home.showEdgeShadow) edgeShadow else null
        if (content.foreground !== shadow) content.foreground = shadow
        val drawerColors = SurfaceColors.resolve(LauncherSurface.DRAWER, settings.night, this)
        val searchColors = SurfaceColors.resolve(LauncherSurface.SEARCH, settings.night, this)
        // The drawer's own background color/opacity setting wins; night tokens apply when it is unset.
        drawerController.setSurfaceColors(drawerColors, searchColors)
        updateSystemBarAppearance()
    }

    private fun onEvent(event: HomeEvent) {
        when (event) {
            HomeEvent.ItemRemoved -> showUndo(R.string.home_item_removed)
            HomeEvent.PageRemoved -> showUndo(R.string.home_page_removed)
            is HomeEvent.PageAdded -> {
                // Slide to the new page so it is obvious where it went.
                pageEditor.onPageAdded(event.page)
                showUndo(R.string.home_page_added)
            }
            HomeEvent.AddedToHome -> messageBar.show(R.string.home_added_to_home)
            HomeEvent.UndoUnavailable -> messageBar.show(R.string.home_undo_unavailable)
            HomeEvent.AppLaunched -> onSuccessfulLaunch()
            HomeEvent.LaunchUnavailable -> messageBar.show(R.string.home_launch_unavailable)
            is HomeEvent.AppLocked -> unlockPrompt.unlock(event.locked, event.retry)
            HomeEvent.LayoutLocked -> {
                workspacePager.cancelPageRemoval()
                messageBar.show(R.string.home_layout_locked)
            }
            is HomeEvent.EditRejected -> {
                // A refused page removal brings its faded page back.
                workspacePager.cancelPageRemoval()
                messageBar.show(
                    when (event.reason) {
                        RejectReason.OCCUPIED -> R.string.home_edit_occupied
                        RejectReason.NO_SPACE, RejectReason.OUT_OF_BOUNDS -> R.string.home_edit_no_space
                        RejectReason.DUPLICATE -> R.string.home_edit_duplicate
                        RejectReason.UNKNOWN_ITEM, RejectReason.STORAGE_ERROR, RejectReason.INVALID -> R.string.home_edit_failed
                    },
                )
            }
        }
    }

    /** The undo bar takes precedence over a message: it carries the action for the user's own edit. */
    private fun showUndo(text: Int) {
        messageBar.hide()
        undoBar.show(getString(text)) { viewModel.undoLastEdit() }
    }

    // --- Actions ---------------------------------------------------------------------------------

    private fun onSuccessfulLaunch() {
        dockSurface.onAppLaunched()
        val state = viewModel.uiState.value
        if (state.drawerOpen && state.settings.drawer.automaticallyClose) closeDrawer()
    }

    private fun openDrawer() {
        cards.close()
        discover.close()
        undoBar.hide()
        viewModel.openDrawer()
    }

    private fun closeDrawer() {
        if (search.text.isNotEmpty()) search.setText("")
        viewModel.closeDrawer()
    }

    private fun openDrawerWithSearchFocused() {
        openDrawer()
        // Make the drawer visible now so the field can take focus; render() then finds it open.
        drawerMotion.animateTo(open = true)
        if (search.requestFocus()) {
            WindowCompat.getInsetsController(window, search).show(WindowInsetsCompat.Type.ime())
        }
    }

    /** App menu "Swipe actions" for a workspace icon: the gestures feature's editor over HOME. */
    /** Shortcut taps on home, in folders, on cards and from gestures count toward frequent search suggestions. */
    private fun rememberingShortcuts() =
        RememberingShortcutStarter(LauncherAppsShortcuts(this, deps.appLock), deps.searchHistoryRepository, deps.appScope)

    private fun editSwipeActions(target: AppMenuTarget) {
        val itemId = target.homeItemId ?: return
        val ready = viewModel.uiState.value.workspace as? WorkspaceUi.Ready ?: return
        val item = (ready.pages.flatten() + ready.dock).firstOrNull { it.id == itemId } ?: return
        composeDialogs.showSwipeActionsEditor(target.label, target.key, item.actions) { actions ->
            viewModel.editWorkspace { it.setItemActions(itemId, actions) }
        }
    }

    private fun requestDefaultHome() = DefaultHomeRequest.request(
        this, deps, roleRequest::launch, { messageBar.show(R.string.home_settings_unavailable) },
    )

    private fun openHomeSettings() = DefaultHomeRequest.openHomeSettings(
        this, deps, { messageBar.show(R.string.home_settings_unavailable) },
    )

    private fun hideKeyboard() {
        getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(search.windowToken, 0)
        search.clearFocus()
    }
}
