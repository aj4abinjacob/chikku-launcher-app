package io.github.aj4abinjacob.chikku.home.widget

import android.app.Activity
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.isEmpty
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.theme.Motion
import io.github.aj4abinjacob.chikku.core.model.AppsState
import io.github.aj4abinjacob.chikku.core.model.CellPlacement
import io.github.aj4abinjacob.chikku.core.model.ComponentKey
import io.github.aj4abinjacob.chikku.core.model.ContainerType
import io.github.aj4abinjacob.chikku.core.model.EditResult
import io.github.aj4abinjacob.chikku.core.model.GridSpec
import io.github.aj4abinjacob.chikku.core.model.RejectReason
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.core.model.settings.PaddingSize
import io.github.aj4abinjacob.chikku.home.CellLayout
import io.github.aj4abinjacob.chikku.home.DragPayload
import io.github.aj4abinjacob.chikku.home.HomeDependencies
import io.github.aj4abinjacob.chikku.home.Haptics
import io.github.aj4abinjacob.chikku.home.HomeItemUi
import io.github.aj4abinjacob.chikku.home.HomeLayoutMetrics
import io.github.aj4abinjacob.chikku.home.HomeMenu
import io.github.aj4abinjacob.chikku.home.HomeMenuEntry
import io.github.aj4abinjacob.chikku.home.HomeMenuGroup
import io.github.aj4abinjacob.chikku.home.ItemContent
import io.github.aj4abinjacob.chikku.home.WorkspaceController
import io.github.aj4abinjacob.chikku.home.WorkspacePager
import io.github.aj4abinjacob.chikku.home.WorkspaceUi
import io.github.aj4abinjacob.chikku.message.ChikkuMessages
import io.github.aj4abinjacob.chikku.platform.openStoreListing
import io.github.aj4abinjacob.chikku.prompt.ChikkuPrompts
import io.github.aj4abinjacob.chikku.prompt.PromptAction
import io.github.aj4abinjacob.chikku.prompt.PromptHandle
import io.github.aj4abinjacob.chikku.prompt.PromptSpec
import io.github.aj4abinjacob.chikku.prompt.PromptTone
import io.github.aj4abinjacob.chikku.settings.SettingsActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Widgets on HOME (G16): hosting, picker, bind/consent/configure flow, options (move, resize,
 * reconfigure, remove), the "Widget unavailable" placeholder with rebind, and the long-press-home
 * menu entries (G21). Create it in `onCreate` after `super.onCreate`; it observes the activity
 * lifecycle itself (host listening while started).
 */
class WidgetsController(
    private val activity: ComponentActivity,
    private val deps: HomeDependencies,
    private val root: FrameLayout,
    private val pager: WorkspacePager,
    private val workspace: WorkspaceController,
    private val settings: () -> LauncherSettings,
    /** Removes an item with the normal undo bar. */
    private val removeItem: (itemId: String) -> Unit,
    /** Chikku's own prompts (HOME passes its shared, accent-aware instance). */
    private val prompts: ChikkuPrompts = ChikkuPrompts(activity),
    /** Chikku's message bar for short notices; never a platform toast. */
    private val messages: ChikkuMessages = ChikkuMessages.of(activity),
    private val externalIntents: io.github.aj4abinjacob.chikku.platform.ExternalIntentStarter = io.github.aj4abinjacob.chikku.platform.ExternalIntentStarter.DENY,
) : WidgetViewBinder, DefaultLifecycleObserver {
    /** This controller's prompt on HOME's shared [ChikkuPrompts]; closing it never closes another's. */
    private var prompt: PromptHandle? = null

    private val host = ChikkuWidgetHost(activity) { activity.runOnUiThread(::refreshAll) }
    private val platform = AndroidWidgetPlatform(activity, host)
    // A rebind is undoable. Old IDs are retired by the next orphan sweep, which also respects the
    // durable restore checkpoint; deleting immediately would destroy the binding needed by Undo.
    private val flow = WidgetBindFlow(platform, deps.workspaceRepository, releaseUnreferencedId = { id ->
        deps.backupService?.withWidgetReferences { referenced ->
            if (id !in referenced) platform.deleteId(id)
        }
    }, retireOldId = {})
    private val operations = deps.widgetOperations ?: WidgetOperationCoordinator()
    private val catalog = WidgetCatalog(activity)
    private val io = Dispatchers.IO
    private val density = activity.resources.displayMetrics.density

    private val cells = LinkedHashSet<WidgetCellView>()

    /**
     * Host views of widgets whose page left the workspace bind window, by app widget id. The
     * public AppWidgetHost API keeps a strong reference to every view it created until the id is
     * deleted or a new view is created for it, so recreating would not free memory sooner; parking
     * keeps them out of the view tree (no measure, layout, draw or display lists) and lets the page
     * show the same widget again at once, without re-inflation, when it comes back. Pruned to the
     * ids still on the workspace after every render.
     */
    private val parked = HashMap<Int, AppWidgetHostView>()
    private val sentSizes = HashMap<Int, Pair<Int, Int>>()
    private val bindingJobs = HashMap<WidgetCellView, Job>()
    private var pending: PendingWidget? = null
    private var flowInFlight = false
    private var sweptOrphans = false
    private var picker: WidgetPicker? = null
    private val optionsMenu = WidgetOptionsMenu(activity, settings)
    private var resizeFrame: WidgetResizeFrame? = null
    private val resizeBack = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = closeResizeFrame()
    }
    private val pickerBack = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            picker?.dismiss()
        }
    }

    /** The picker hidden while a drag from it runs; removed once the drag ends. */
    private var draggingFromPicker: WidgetPicker? = null

    private val consent = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val p = pending ?: return@registerForActivityResult
        val granted = result.resultCode == Activity.RESULT_OK
        runFlow { flow.onConsentResult(p, granted) }
    }

    init {
        activity.savedStateRegistry.consumeRestoredStateForKey(STATE_KEY)?.let { pending = it.toPending() }
        pending?.let { operations.reserveRestored(it.appWidgetId) }
        activity.savedStateRegistry.registerSavedStateProvider(STATE_KEY) { pending?.toBundle() ?: Bundle() }
        activity.onBackPressedDispatcher.addCallback(activity, resizeBack)
        activity.onBackPressedDispatcher.addCallback(activity, pickerBack)
        workspace.onDragEnded = {
            draggingFromPicker?.dismiss()
            draggingFromPicker = null
        }
        activity.lifecycle.addObserver(this)
    }

    // --- Lifecycle ---------------------------------------------------------------------------

    override fun onStart(owner: LifecycleOwner) {
        try {
            host.startListening()
        } catch (_: RuntimeException) {
            // Widgets then show their last state; HOME must stay usable.
        }
        refreshAll()
    }

    override fun onStop(owner: LifecycleOwner) {
        bindingJobs.values.forEach(Job::cancel)
        bindingJobs.clear()
        try {
            host.stopListening()
        } catch (_: RuntimeException) {
            // Ignore: the host is re-registered on the next start.
        }
    }

    /** Called before a widget options menu opens, so HOME can close its other menus. */
    var onMenuShown: (() -> Unit)? = null

    /** Closes the widget picker, options menu, resize frame and dialogs (another menu opened, or HOME pressed). */
    fun dismissMenus() {
        if (draggingFromPicker == null) picker?.dismiss()
        optionsMenu.dismiss()
        prompt?.dismiss()
        prompt = null
        closeResizeFrame()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        parked.clear()
        picker?.dismiss()
        prompt?.dismiss()
        prompt = null
        optionsMenu.dismiss()
        closeResizeFrame()
    }

    /** Forward of `Activity.onActivityResult` for the configuration activity, which only supports that path. */
    fun onActivityResult(requestCode: Int, resultCode: Int): Boolean {
        when (requestCode) {
            REQUEST_CONFIGURE -> {
                val p = pending ?: return true
                runFlow { flow.onConfigurationResult(p, resultCode == Activity.RESULT_OK) }
            }
            REQUEST_RECONFIGURE -> Unit // the widget keeps working whatever the result
            else -> return false
        }
        return true
    }

    // --- Home menu (G21) ---------------------------------------------------------------------

    fun installHomeMenuEntries(menu: HomeMenu) {
        menu.addEntry(HomeMenuEntry(HomeMenu.ENTRY_WIDGETS, R.string.widgets_home_menu_widgets, 100, icon = R.drawable.ic_menu_widgets) { openPicker() })
        menu.addEntry(HomeMenuEntry(HomeMenu.ENTRY_WALLPAPERS, R.string.widgets_home_menu_wallpapers, 200, icon = R.drawable.ic_menu_wallpaper) { openWallpaperPicker() })
        menu.addEntry(
            HomeMenuEntry(HomeMenu.ENTRY_SETTINGS, R.string.widgets_home_menu_settings, 300, icon = R.drawable.ic_menu_tune, group = HomeMenuGroup.SETTINGS) {
                openSettings()
            },
        )
    }

    private fun openWallpaperPicker() {
        val chooser = Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), activity.getString(R.string.widgets_home_menu_wallpapers))
        launchSafely(chooser)
    }

    private fun openSettings() = launchSafely(Intent(activity, SettingsActivity::class.java))

    private fun launchSafely(intent: Intent) {
        try {
            activity.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            showMessage(R.string.home_launch_unavailable)
        } catch (_: SecurityException) {
            showMessage(R.string.home_launch_unavailable)
        }
    }

    // --- Picker ------------------------------------------------------------------------------

    fun openPicker() {
        if (settings().home.layoutLocked) {
            showMessage(R.string.widgets_layout_locked)
            return
        }
        if (picker?.isShowing == true || draggingFromPicker != null) return
        val (cellW, cellH, grid) = cellMetrics()
        lateinit var dialog: WidgetPicker
        dialog = WidgetPicker(
            root, activity.lifecycleScope, io, cellW, cellH,
            onPick = { entry -> startFlow(WidgetRequest.Add(entry.key, entry.spanX, entry.spanY, pager.currentPage)) },
            onDragOut = { entry, view -> dragFromPicker(dialog, entry, view) },
            onClosed = {
                if (picker === dialog) picker = null
                pickerBack.isEnabled = picker != null
            },
            accentColor = settings().lookFeel.accentColor,
        )
        picker = dialog
        onMenuShown?.invoke()
        dialog.show()
        pickerBack.isEnabled = true
        activity.lifecycleScope.launch {
            val serials = allowedProfiles()
            val (groups, infos) = withContext(io) {
                catalog.load(serials) { info -> WidgetSizing.defaultSpan(info.sizeSpec(), cellW, cellH, grid) }
            }
            dialog.setContent(groups, infos)
        }
    }

    /**
     * Touch and hold in the picker: hide it and drag the preview onto the workspace. The picker
     * stays attached (the drag belongs to its view) until [WorkspaceController.onDragEnded].
     */
    private fun dragFromPicker(source: WidgetPicker, entry: WidgetProviderEntry, view: View): Boolean {
        if (flowInFlight || pending != null) return false
        source.setHiddenForDrag(true)
        pickerBack.isEnabled = false
        val started = workspace.startDrag(view, DragPayload.NewWidget(entry.key, entry.spanX, entry.spanY))
        if (started) {
            draggingFromPicker = source
        } else {
            source.setHiddenForDrag(false)
            pickerBack.isEnabled = true
        }
        return started
    }

    /** A widget from the picker was dropped on [target]; the dock means "any free desktop spot". */
    fun addDropped(payload: DragPayload.NewWidget, target: CellPlacement) {
        val at = target.takeIf { it.container == ContainerType.DESKTOP }
        startFlow(WidgetRequest.Add(payload.provider, payload.spanX, payload.spanY, at?.page ?: pager.currentPage, at))
    }

    /** Profiles whose apps are listed (never a locked/paused private profile); the main profile if unknown. */
    private suspend fun allowedProfiles(): Set<Long> {
        val apps = withTimeoutOrNull(APPS_TIMEOUT_MS) {
            try {
                deps.appsRepository.apps.first { it !is AppsState.Loading }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }
        val listed = (apps as? AppsState.Loaded)?.listedProfiles
        return if (listed.isNullOrEmpty()) setOf(catalog.mainSerial()) else listed
    }

    private fun cellMetrics(): Triple<Int, Int, GridSpec> {
        val page = pager.pageAt(pager.currentPage)
        val grid = page?.grid ?: GridSpec(settings().home.desktopColumns, settings().home.desktopRows)
        val w = page?.let { it.width - it.paddingLeft - it.paddingRight } ?: 0
        val h = page?.let { it.height - it.paddingTop - it.paddingBottom } ?: 0
        val metrics = activity.resources.displayMetrics
        val cellW = if (w > 0) w / grid.columns else metrics.widthPixels / grid.columns
        val cellH = if (h > 0) h / grid.rows else (metrics.heightPixels * 2 / 3) / grid.rows
        return Triple(cellW, cellH, grid)
    }

    // --- Bind flow ---------------------------------------------------------------------------

    private fun startFlow(request: WidgetRequest) {
        if (flowInFlight || pending != null) return
        runFlow { flow.begin(request) }
    }

    /** Runs a flow step off the main thread (in the app scope, so a rotation cannot strand an id). */
    private fun runFlow(step: suspend () -> WidgetStep) {
        flowInFlight = true
        val previous = pending
        deps.appScope.launch {
            val next = try {
                withContext(io) { operations.step(previous, step) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                WidgetStep.Done(WidgetOutcome.Rejected(RejectReason.STORAGE_ERROR))
            }
            handleStep(next)
        }
    }

    private fun handleStep(step: WidgetStep) {
        if (activity.isDestroyed) {
            // The new activity cannot receive this result: release the id rather than leak it.
            when (step) {
                is WidgetStep.NeedsConsent -> releaseInBackground(step.pending)
                is WidgetStep.NeedsConfiguration -> releaseInBackground(step.pending)
                is WidgetStep.Done -> Unit
            }
            return
        }
        flowInFlight = false
        when (step) {
            is WidgetStep.NeedsConsent -> {
                pending = step.pending
                val p = step.pending
                val user = runCatching { activity.getSystemService(android.os.UserManager::class.java)?.getUserForSerialNumber(p.provider.userSerial) }.getOrNull()
                val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, p.appWidgetId)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, android.content.ComponentName(p.provider.packageName, p.provider.className))
                user?.let { intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, it) }
                try {
                    consent.launch(intent)
                } catch (_: RuntimeException) {
                    abandon(p)
                }
            }
            is WidgetStep.NeedsConfiguration -> {
                pending = step.pending
                try {
                    host.startAppWidgetConfigureActivityForResult(activity, step.pending.appWidgetId, 0, REQUEST_CONFIGURE, null)
                } catch (_: ActivityNotFoundException) {
                    abandon(step.pending)
                } catch (_: SecurityException) {
                    abandon(step.pending)
                }
            }
            is WidgetStep.Done -> {
                pending = null
                when (val outcome = step.outcome) {
                    is WidgetOutcome.Added -> Unit
                    is WidgetOutcome.Rebound -> refreshAll()
                    WidgetOutcome.Cancelled -> Unit
                    is WidgetOutcome.Rejected -> showMessage(
                        if (outcome.reason == RejectReason.OCCUPIED || outcome.reason == RejectReason.NO_SPACE) {
                            R.string.home_edit_no_space
                        } else {
                            R.string.widgets_add_failed
                        },
                    )
                }
            }
        }
    }

    private fun abandon(p: PendingWidget) {
        showMessage(R.string.widgets_add_failed)
        runFlow { flow.cancel(p) }
    }

    private fun releaseInBackground(p: PendingWidget) {
        deps.appScope.launch { withContext(io) { operations.step(p) { flow.cancel(p) } } }
    }

    // --- Rendering (WidgetViewBinder) ----------------------------------------------------------

    override fun createWidgetView(): View = WidgetCellView(activity).also { cell ->
        cell.onLongPress = { showOptions(it) }
        cell.onDragStart = { startMove(it) }
        cell.onSizeChanged = { v, w, h -> sendSizeOptions(v, w, h) }
        cell.placeholder.setOnClickListener { itemOf(cell)?.let(::rebind) }
        ViewCompat.addAccessibilityAction(cell, activity.getString(R.string.widgets_options)) { v, _ ->
            showOptions(v as WidgetCellView)
            true
        }
        cells += cell
    }

    override fun isWidgetView(view: View): Boolean = view is WidgetCellView

    override fun bindWidget(view: View, item: HomeItemUi, content: ItemContent.Widget) {
        val cell = view as? WidgetCellView ?: return
        cells += cell
        cell.setTag(R.id.home_item_model, item)
        val pad = HomeLayoutMetrics.widgetPaddingPx(settings().home, density, item.widgetPadding)
        if (cell.paddingLeft != pad) {
            cell.setPadding(pad, pad, pad, pad)
            // Padding changes the widget's content size without a cell size change; report it.
            if (cell.isLaidOut) sendSizeOptions(cell, cell.width, cell.height)
        }
        cell.cornerRadiusPx = settings().home.widgetCornerRadiusDp * activity.resources.displayMetrics.density
        bindState(cell, content, force = false)
    }

    override fun recycleWidget(view: View) {
        val cell = view as? WidgetCellView ?: return
        val hosted = cell.hostView
        if (hosted != null && cell.hostedId != 0) parked[cell.hostedId] = hosted
        cell.clearWidget()
        bindingJobs.remove(cell)?.cancel()
        cells -= cell
    }

    override fun onWorkspaceRendered(ui: WorkspaceUi.Ready) {
        if (parked.isNotEmpty()) {
            val present = (ui.pages.asSequence().flatten() + ui.dock.asSequence())
                .mapNotNullTo(HashSet()) { (it.content as? ItemContent.Widget)?.appWidgetId }
            parked.keys.retainAll(present)
        }
        if (deps.widgetOperations == null || sweptOrphans || pending != null || flowInFlight) return
        sweptOrphans = true
        activity.lifecycleScope.launch {
            withContext(io) {
                operations.sweep { reservations ->
                    val service = deps.backupService
                    if (service != null) service.withWidgetReferences { referenced ->
                        flow.orphanIds(referenced, reservations).forEach(platform::deleteId)
                    } else {
                        // Include persisted out-of-viewport items, not only currently rendered cells.
                        val current = deps.workspaceRepository.exportContent() ?: return@sweep
                        val retained = deps.workspaceRepository.retainedWidgetIds() ?: return@sweep
                        val referenced = WidgetBindFlow.referencedIds(current.items.asSequence().map { it.target }) + retained
                        flow.orphanIds(referenced, reservations).forEach(platform::deleteId)
                    }
                }
            }
        }
    }

    private fun bindState(cell: WidgetCellView, content: ItemContent.Widget, force: Boolean) {
        val id = content.appWidgetId
        bindingJobs.remove(cell)?.cancel()
        // A forced re-check (onStart, provider changes) keeps the live view on screen; tearing it
        // down to the placeholder first made every widget flicker on unlock and on return to HOME.
        // A page coming back into the bind window shows its parked view at once (checked below).
        val reused = if (cell.hostedId != id) {
            val previous = parked.remove(id)
            if (previous != null) cell.showWidget(previous, id) else showPlaceholder(cell)
            previous != null
        } else {
            false
        }
        bindingJobs[cell] = activity.lifecycleScope.launch {
            val (info, key) = withContext(io) { platform.info(id).let { it to it?.let(platform::keyOf) } }
            if (activity.isDestroyed || cell !in cells || itemOf(cell)?.content != content) return@launch
            val state = widgetDisplayState(id, content.provider, key)
            if (state == WidgetDisplayState.Hosted && info != null) {
                val existing = cell.hostView
                val providerChanged = existing?.appWidgetInfo?.provider != info.provider
                if (existing == null || cell.hostedId != id || ((force || reused) && providerChanged)) {
                    // A new view replaces the host's listener for this id; an older parked one would go stale.
                    parked.remove(id)
                    val hostView = try {
                        host.createView(activity, id, info)
                    } catch (_: RuntimeException) {
                        null
                    }
                    if (hostView == null) {
                        showPlaceholder(cell)
                        return@launch
                    }
                    cell.showWidget(hostView, id)
                }
                cell.contentDescription = null
            } else {
                showPlaceholder(cell)
            }
        }
    }

    private fun showPlaceholder(cell: WidgetCellView) {
        cell.showPlaceholder(
            activity.getString(R.string.widgets_unavailable),
            activity.getString(R.string.widgets_unavailable_description),
        )
    }

    /** Providers changed (update, uninstall, profile change): re-check every live cell. */
    private fun refreshAll() {
        if (activity.isDestroyed) return
        for (cell in cells.toList()) {
            val item = itemOf(cell) ?: continue
            val content = item.content as? ItemContent.Widget ?: continue
            bindState(cell, content, force = true)
        }
    }

    private fun sendSizeOptions(cell: WidgetCellView, w: Int, h: Int) {
        val id = cell.hostedId.takeIf { it != 0 } ?: return
        val wDp = ((w - cell.paddingLeft - cell.paddingRight).coerceAtLeast(0) / density).toInt()
        val hDp = ((h - cell.paddingTop - cell.paddingBottom).coerceAtLeast(0) / density).toInt()
        if (sentSizes[id] == wDp to hDp) return
        val options = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, wDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, wDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, hDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, hDp)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, arrayListOf(SizeF(wDp.toFloat(), hDp.toFloat())))
            }
        }
        activity.lifecycleScope.launch {
            val sent = withContext(io) {
                try {
                    val manager = AppWidgetManager.getInstance(activity.applicationContext) ?: return@withContext false
                    manager.updateAppWidgetOptions(id, options)
                    true
                } catch (_: RuntimeException) {
                    // A provider that went away keeps its placeholder; nothing to update.
                    false
                }
            }
            if (sent) sentSizes[id] = wDp to hDp
        }
    }

    private fun itemOf(view: View): HomeItemUi? = view.getTag(R.id.home_item_model) as? HomeItemUi

    // --- Options -------------------------------------------------------------------------------

    private fun showOptions(cell: WidgetCellView) {
        val item = itemOf(cell) ?: return
        val content = item.content as? ItemContent.Widget ?: return
        activity.lifecycleScope.launch {
            val info = withContext(io) { platform.info(content.appWidgetId) }
            if (activity.isDestroyed || cell !in cells || itemOf(cell) != item) return@launch
            showOptions(cell, item, content, info)
        }
    }

    private fun showOptions(cell: WidgetCellView, item: HomeItemUi, content: ItemContent.Widget,
        info: android.appwidget.AppWidgetProviderInfo?) {
        val locked = settings().home.layoutLocked
        optionsMenu.dismiss()
        val actions = buildList {
            if (info == null) add(WidgetOptionsMenu.Action(R.string.widgets_rebind, R.drawable.ic_menu_refresh) { rebind(item) })
            if (info != null && platform.isReconfigurable(info)) {
                add(WidgetOptionsMenu.Action(R.string.widgets_reconfigure, R.drawable.ic_menu_tune) { reconfigure(cell.hostedId) })
            }
            if (!locked && info != null && info.sizeSpec().isResizable) {
                add(WidgetOptionsMenu.Action(R.string.widgets_resize, R.drawable.ic_menu_resize) {
                    openResizeFrame(cell, item, info.sizeSpec())
                })
            }
            // A drag can only start while a finger is down, so moving is long-press-then-drag.
            if (!locked) add(WidgetOptionsMenu.Action(R.string.widgets_move, R.drawable.ic_menu_move) { showMessage(R.string.widgets_move_hint) })
        }
        val padding = if (locked) null else WidgetOptionsMenu.Padding(item.widgetPadding, settings().home.widgetPadding) { value ->
            setPadding(item, value)
        }
        val remove = if (locked) null else WidgetOptionsMenu.Action(R.string.home_action_remove_from_home, R.drawable.ic_menu_remove) {
            remove(item, content)
        }
        if (actions.isEmpty() && padding == null && remove == null) {
            showMessage(R.string.widgets_layout_locked)
            return
        }
        onMenuShown?.invoke()
        optionsMenu.show(cell, actions, padding, remove)
    }

    /** Applied live from the menu; the repository ignores a value equal to the stored one. */
    private fun setPadding(item: HomeItemUi, padding: PaddingSize?) {
        activity.lifecycleScope.launch {
            val result = withContext(io) { deps.workspaceRepository.setWidgetPadding(item.id, padding) }
            if (result is EditResult.Rejected && !activity.isDestroyed) showMessage(R.string.widgets_padding_failed)
        }
    }

    /** Long press, then the finger moved: close the options and drag the widget. */
    private fun startMove(cell: WidgetCellView) {
        val item = itemOf(cell) ?: return
        if (settings().home.layoutLocked) return // the options menu already shows what is allowed
        optionsMenu.dismiss()
        closeResizeFrame()
        if (workspace.startDrag(cell, DragPayload.Existing(item.id, item.placement))) Haptics.lift(cell)
    }

    private fun remove(item: HomeItemUi, content: ItemContent.Widget) {
        removeItem(item.id)
        val id = content.appWidgetId
        if (id == 0) return
        // Keep the id while Undo is possible; release it afterwards unless the placement came back.
        deps.appScope.launch {
            delay(UNDO_GRACE_MS)
            withContext(io) {
                deps.backupService?.let { service ->
                    service.withWidgetReferences { referenced -> if (id !in referenced) platform.deleteId(id) }
                    return@withContext
                }
                val current = try {
                    deps.workspaceRepository.exportContent()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                } ?: return@withContext
                val stillUsed = WidgetBindFlow.referencedIds(current.items.asSequence().map { it.target })
                if (id !in stillUsed) platform.deleteId(id)
            }
        }
    }

    private fun reconfigure(appWidgetId: Int) {
        try {
            host.startAppWidgetConfigureActivityForResult(activity, appWidgetId, 0, REQUEST_RECONFIGURE, null)
        } catch (_: ActivityNotFoundException) {
            showMessage(R.string.home_launch_unavailable)
        } catch (_: SecurityException) {
            showMessage(R.string.home_launch_unavailable)
        }
    }

    /** Placeholder tap: bind a fresh id to the persisted provider, or explain why it cannot. */
    private fun rebind(item: HomeItemUi) {
        val content = item.content as? ItemContent.Widget ?: return
        if (flowInFlight || pending != null) return
        activity.lifecycleScope.launch {
            val (installed, mainProfile) = withContext(io) {
                (catalog.find(content.provider) != null) to (content.provider.userSerial == catalog.mainSerial())
            }
            if (installed) {
                startFlow(WidgetRequest.Rebind(item.id, content.appWidgetId, content.provider))
            } else {
                showProviderMissing(item.id, content.provider.packageName.takeIf { mainProfile })
            }
        }
    }

    /**
     * The provider is not installed (or its profile is paused). The placement is kept until the user
     * removes it. [storePackage] is set only for a main-profile provider: a Play Store page opened
     * from here would install into the personal profile, which is wrong for a work widget.
     */
    private fun showProviderMissing(itemId: String, storePackage: String?) {
        prompt = prompts.show(
            PromptSpec(
                icon = R.drawable.ic_menu_widgets,
                title = activity.getString(R.string.prompt_widget_missing_title),
                message = activity.getString(R.string.prompt_widget_missing_message),
                primary = PromptAction(activity.getString(R.string.home_action_remove_from_home)) { removeItem(itemId) },
                dismissLabel = activity.getString(R.string.prompt_keep),
                tone = PromptTone.DESTRUCTIVE,
                secondary = storePackage?.let { pkg ->
                    PromptAction(activity.getString(R.string.prompt_find_in_play_store)) { if (!activity.openStoreListing(pkg, externalIntents)) showMessage(R.string.prompt_open_store_failed) }
                },
            ),
        )
    }

    // --- Resize ---------------------------------------------------------------------------------

    private fun openResizeFrame(cell: WidgetCellView, item: HomeItemUi, spec: WidgetSizeSpec) {
        closeResizeFrame()
        val layout = cell.parent as? CellLayout ?: return
        val grid = layout.grid
        val cellW = (layout.width - layout.paddingLeft - layout.paddingRight) / grid.columns
        val cellH = (layout.height - layout.paddingTop - layout.paddingBottom) / grid.rows
        if (cellW <= 0 || cellH <= 0) return
        val range = WidgetSizing.resizeRange(spec, cellW, cellH, grid, item.placement)
        val bounds = Rect(0, 0, cell.width, cell.height)
        root.offsetDescendantRectToMyCoords(cell, bounds)
        val frame = WidgetResizeFrame(
            context = activity,
            widgetRect = RectF(bounds),
            cellWidth = cellW,
            cellHeight = cellH,
            range = range,
            startPlacement = item.placement,
            grid = grid,
            rtl = layout.isRtl,
            onCommit = { placement, complete -> commitResize(item.id, placement, complete) },
            onClose = ::closeResizeFrame,
        )
        frame.motion = Motion.current(settings().lookFeel.animationSpeed)
        root.addView(frame, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        resizeFrame = frame
        resizeBack.isEnabled = true
        frame.requestFocus()
    }

    private fun commitResize(itemId: String, placement: CellPlacement, complete: (Boolean) -> Unit) {
        val allowOverlap = settings().home.allowWidgetOverlap
        deps.appScope.launch {
            val result = try {
                deps.workspaceRepository.moveItem(itemId, placement, allowOverlap)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                EditResult.Rejected(RejectReason.STORAGE_ERROR)
            }
            withContext(Dispatchers.Main.immediate) {
                complete(result is EditResult.Applied)
                if (result is EditResult.Rejected && !activity.isDestroyed) {
                    showMessage(if (result.reason == RejectReason.OCCUPIED) R.string.home_edit_occupied else R.string.home_edit_failed)
                }
            }
        }
    }

    private fun closeResizeFrame() {
        resizeFrame?.let { frame -> frame.dismiss { root.removeView(frame) } }
        resizeFrame = null
        resizeBack.isEnabled = false
    }

    private fun showMessage(message: Int) = messages.show(message)

    // --- Saved state ----------------------------------------------------------------------------

    private fun PendingWidget.toBundle(): Bundle = Bundle().apply {
        putInt("id", appWidgetId)
        putString("pkg", provider.packageName)
        putString("cls", provider.className)
        putLong("serial", provider.userSerial)
        when (val r = request) {
            is WidgetRequest.Add -> {
                putString("kind", "add")
                putInt("spanX", r.spanX)
                putInt("spanY", r.spanY)
                putInt("page", r.page)
                r.at?.let { at ->
                    putInt("atPage", at.page)
                    putInt("atX", at.x)
                    putInt("atY", at.y)
                }
            }
            is WidgetRequest.Rebind -> {
                putString("kind", "rebind")
                putString("item", r.itemId)
                putInt("oldId", r.oldId)
            }
        }
    }

    private fun Bundle.toPending(): PendingWidget? {
        val id = getInt("id")
        val pkg = getString("pkg") ?: return null
        if (id == 0 || pkg.isBlank()) return null
        val provider = ComponentKey(pkg, getString("cls").orEmpty(), getLong("serial"))
        val request = when (getString("kind")) {
            "add" -> {
                val spanX = getInt("spanX", 1)
                val spanY = getInt("spanY", 1)
                val at = if (containsKey("atPage")) {
                    CellPlacement(ContainerType.DESKTOP, getInt("atPage"), getInt("atX"), getInt("atY"), spanX, spanY)
                } else {
                    null
                }
                WidgetRequest.Add(provider, spanX, spanY, getInt("page"), at)
            }
            "rebind" -> WidgetRequest.Rebind(getString("item") ?: return null, getInt("oldId"), provider)
            else -> return null
        }
        return PendingWidget(id, request)
    }

    private companion object {
        const val STATE_KEY = "chikku.widgets.pending"
        const val REQUEST_CONFIGURE = 0x5701
        const val REQUEST_RECONFIGURE = 0x5702
        const val UNDO_GRACE_MS = 15_000L
        const val APPS_TIMEOUT_MS = 2_000L
    }
}
