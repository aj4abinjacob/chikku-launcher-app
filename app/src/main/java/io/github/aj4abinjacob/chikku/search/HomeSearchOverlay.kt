package io.github.aj4abinjacob.chikku.search

import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.aj4abinjacob.chikku.applock.AppUnlocker
import io.github.aj4abinjacob.chikku.core.model.AppCustomization
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.AppsState
import io.github.aj4abinjacob.chikku.core.model.LauncherApp
import io.github.aj4abinjacob.chikku.core.model.search.ContactServices
import io.github.aj4abinjacob.chikku.core.model.search.SearchResults
import io.github.aj4abinjacob.chikku.core.model.search.SearchRow
import io.github.aj4abinjacob.chikku.core.model.search.ShortcutLookup
import io.github.aj4abinjacob.chikku.core.model.settings.SearchSettings
import io.github.aj4abinjacob.chikku.home.HomeDependencies
import io.github.aj4abinjacob.chikku.home.appmenu.AppMenuHandle
import io.github.aj4abinjacob.chikku.message.ChikkuMessages
import io.github.aj4abinjacob.chikku.theme.Motion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Search lives in HOME's window, so a long press can continue directly into the existing local
 * workspace drag. No app identity is serialized into a public/global drag or an activity intent.
 * The invisible source remains attached until HOME reports the drag end (including cancellation).
 */
class HomeSearchOverlay(
    private val activity: ComponentActivity,
    private val root: FrameLayout,
    private val deps: HomeDependencies,
    private val startAppDrag: (LauncherApp, View) -> Boolean,
    /** HOME's long-press app menu for a result, anchored to its row. */
    private val showAppMenu: (LauncherApp, View) -> AppMenuHandle?,
    private val openFolder: (String) -> Unit,
    private val unlocker: AppUnlocker,
    private val onShowingChanged: (Boolean) -> Unit = {},
) : DefaultLifecycleObserver {
    private var session: Session? = null
    private var dragging = false
    private val back = object : OnBackPressedCallback(false) {
        override fun handleOnBackStarted(backEvent: BackEventCompat) = handleOnBackProgressed(backEvent)

        override fun handleOnBackProgressed(backEvent: BackEventCompat) {
            val view = session?.view ?: return
            val scale = 1f - BACK_SCALE_DROP * backEvent.progress
            view.animate().cancel()
            view.scaleX = scale
            view.scaleY = scale
        }

        override fun handleOnBackCancelled() {
            session?.view?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(motion.duration(CLOSE_DURATION_MS))?.start()
        }

        override fun handleOnBackPressed() = dismiss(animate = true)
    }

    /** Animation speed (style settings); [Motion.duration] 0 shows and hides search at once. */
    var motion: Motion = Motion.DEFAULT

    val isShowing: Boolean get() = session != null && !dragging

    init {
        activity.lifecycle.addObserver(this)
        activity.onBackPressedDispatcher.addCallback(activity, back)
    }

    fun open(initialQuery: String? = null): Boolean {
        if (dragging || activity.isFinishing || activity.isDestroyed) return false
        dismiss()
        val scope = CoroutineScope(activity.lifecycleScope.coroutineContext + SupervisorJob(activity.lifecycleScope.coroutineContext[Job]))
        val store = ViewModelStore()
        val owner = object : ViewModelStoreOwner { override val viewModelStore = store }
        val wiring = SearchHostWiring(activity.applicationContext, deps)
        scope.coroutineContext[Job]?.invokeOnCompletion { wiring.close() }
        val viewModel = ViewModelProvider(owner, viewModelFactory {
            initializer { wiring.newViewModel(SavedStateHandle()) }
        })[SearchViewModel::class.java].apply { setQuery(initialQuery.orEmpty()) }
        // Independent of slow contact/shortcut reads: removed, hidden or locked apps disappear
        // immediately and old rows cannot become drag payloads while replacement results load.
        val visible = combine(
            deps.appsRepository.apps.catch { emit(AppsState.Loading) },
            deps.appCustomizationRepository.customizations
                .map<Map<AppKey, AppCustomization>, Map<AppKey, AppCustomization>?> { it }
                .catch { emit(null) },
        ) { state, custom ->
            if (custom == null) emptySet() else (state as? AppsState.Loaded)?.let { loaded ->
                loaded.apps.filter { it.key.userSerial in loaded.listedProfiles && custom[it.key]?.hidden != true }
                    .mapTo(HashSet()) { it.key }.toSet()
            }.orEmpty()
        }.stateIn(scope, SharingStarted.Eagerly, emptySet())
        val oldSoftInputMode = activity.window.attributes.softInputMode
        activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        val compose = ComposeView(activity).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        }
        val current = Session(compose, scope, store, visible, oldSoftInputMode)
        val notices = SearchNotices()
        val actions = SearchResultActions(activity, deps, unlocker, { if (session === current) dismiss() }, { message ->
            // An unlock prompt can outlive the overlay; then there is no screen to host a snackbar.
            if (session === current) notices.show(message) else ChikkuMessages.of(activity).show(message) // reaches HOME's message bar
        }, isActive = { session === current && !dragging }, actionScope = scope, openFolder = openFolder, currentQuery = { viewModel.query.value })
        current.actions = actions
        session = current
        for (index in 0 until root.childCount) {
            val child = root.getChildAt(index)
            current.accessibility[child] = child.importantForAccessibility
            child.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        compose.setContent {
            val state = viewModel.collectUiState()
            val settings = state.settings
            val keys by visible.collectAsStateWithLifecycle()
            val notice by notices.current.collectAsStateWithLifecycle()
            SearchTheme(settings) {
                SearchScreen(
                    initialQuery = initialQuery.orEmpty(),
                    results = visibleSearchResults(state.results, keys),
                    suggestions = state.suggestions,
                    settings = settings?.search ?: SearchSettings(),
                    iconProvider = deps.iconProvider,
                    onQueryChange = viewModel::setQuery,
                    onRowClick = { row ->
                        val key = (row as? SearchRow.App)?.app?.key ?: (row as? SearchRow.SuggestedApp)?.app?.key
                        if (key == null || key in visible.value) actions.perform(row)
                    },
                    onPersonAction = actions::performPerson,
                    onClose = { dismiss(animate = true) },
                    onAppDrag = { app, source -> startDrag(current, app, source) },
                    onAppMenu = { app, anchor -> showMenu(current, app, anchor) },
                    notice = notice,
                    onNoticeShown = notices::shown,
                    historyControls = actions.historyControls,
                    tapOrigin = actions.tapOrigin,
                )
            }
        }
        root.addView(compose, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        val duration = motion.duration(OPEN_DURATION_MS)
        if (duration > 0L) {
            // Rises slightly into place, like the drawer, instead of popping over HOME.
            compose.alpha = 0f
            compose.translationY = SLIDE_DP * activity.resources.displayMetrics.density
            compose.animate().alpha(1f).translationY(0f).setDuration(duration)
                .setInterpolator(DecelerateInterpolator(DECELERATE_FACTOR)).start()
        }
        back.isEnabled = true
        onShowingChanged(true)
        return true
    }

    private fun startDrag(current: Session, app: LauncherApp, source: View): Boolean {
        if (session !== current || dragging || app.key !in current.visible.value) return false
        hideKeyboard()
        current.view.clearFocus()
        // TextField must be hidden before DRAG_STARTED, or it registers as a competing drop target.
        // INVISIBLE keeps the source view's size/window attachment for DragShadowBuilder.
        current.view.visibility = View.INVISIBLE
        val started = startAppDrag(app, source)
        if (started) {
            dragging = true
            back.isEnabled = false
            restoreAccessibility(current)
            current.actions?.close()
            current.scope.cancel()
            current.store.clear()
            restoreSoftInputMode(current)
            onShowingChanged(false)
        } else {
            current.view.visibility = View.VISIBLE
        }
        return started
    }

    private fun showMenu(current: Session, app: LauncherApp, anchor: View): AppMenuHandle? {
        if (session !== current || dragging || app.key !in current.visible.value) return null
        // The keyboard would cover the menu; the overlay stays, so a menu action returns to results.
        hideKeyboard()
        return showAppMenu(app, anchor)
    }

    /** Called from workspace.onDragEnded for both accepted drops and cancelled drags. */
    fun finishDrag() {
        if (dragging) dispose(cancelNativeDrag = false)
    }

    /**
     * Closes search. [animate] fades it out (Back, the close button); launching a result, a drag,
     * HOME and stopping close it at once.
     */
    fun dismiss(animate: Boolean = false) = dispose(cancelNativeDrag = true, animate = animate)

    private fun dispose(cancelNativeDrag: Boolean, animate: Boolean = false) {
        val current = session ?: return
        session = null
        val wasShowing = !dragging
        val wasDragging = dragging
        dragging = false
        back.isEnabled = false
        // Detaching an invisible source does not reliably end the native drag. End it while the
        // window is still attached on stop/HOME/destroy. Clear the session first because native
        // cancellation can synchronously invoke workspace.onDragEnded and finishDrag again.
        if (wasDragging && cancelNativeDrag) {
            try {
                root.cancelDragAndDrop()
            } catch (_: RuntimeException) {
                // An already-disconnected window must still release its overlay and scopes.
            }
        }
        hideKeyboard()
        val duration = if (animate && wasShowing) motion.duration(CLOSE_DURATION_MS) else 0L
        if (duration > 0L) animateOut(current.view, duration) else root.removeView(current.view)
        restoreAccessibility(current)
        current.actions?.close()
        current.store.clear()
        current.scope.cancel()
        restoreSoftInputMode(current)
        if (wasShowing) onShowingChanged(false)
    }

    /** Fades the closing [view] out under a touch blocker, so nothing in it can be tapped, then removes both. */
    private fun animateOut(view: View, duration: Long) {
        view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        val blocker = View(activity).apply { setOnTouchListener { _, _ -> true } }
        root.addView(blocker, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        view.animate().cancel()
        view.animate().alpha(0f).translationY(SLIDE_DP * activity.resources.displayMetrics.density)
            .setDuration(duration).setInterpolator(AccelerateInterpolator(DECELERATE_FACTOR))
            .withEndAction {
                root.removeView(view)
                root.removeView(blocker)
            }.start()
    }

    fun destroy() {
        dismiss()
        back.remove()
        activity.lifecycle.removeObserver(this)
    }

    override fun onStop(owner: LifecycleOwner) = dismiss()

    override fun onDestroy(owner: LifecycleOwner) = destroy()

    private fun restoreSoftInputMode(current: Session) {
        activity.window.setSoftInputMode(current.softInputMode)
    }

    private fun restoreAccessibility(current: Session) {
        current.accessibility.forEach { (view, importance) -> view.importantForAccessibility = importance }
        current.accessibility.clear()
    }

    private fun hideKeyboard() = WindowCompat.getInsetsController(activity.window, root).hide(WindowInsetsCompat.Type.ime())

    private class Session(
        val view: ComposeView,
        val scope: CoroutineScope,
        val store: ViewModelStore,
        val visible: kotlinx.coroutines.flow.StateFlow<Set<AppKey>>,
        val softInputMode: Int,
    ) {
        var actions: SearchResultActions? = null
        val accessibility = mutableMapOf<View, Int>()
    }
}

/** Immediate app/profile filtering is separate from query debouncing and optional slow sources. */
internal fun visibleSearchResults(results: SearchResults, keys: Set<AppKey>): SearchResults {
    val services = results.contactServices.filter { it.app.key in keys }
    return visibleAppResults(results, keys).copy(contactServices = services, contacts = ContactServices.filter(results.contacts, services))
}

private fun visibleAppResults(results: SearchResults, keys: Set<AppKey>): SearchResults = results.copy(
    apps = results.apps.filter { it.key in keys },
    appMatchRanks = results.appMatchRanks.filterKeys { it in keys },
    suggestedApps = results.suggestedApps.filter { it.key in keys },
    shortcuts = (results.shortcuts as? ShortcutLookup.Found)?.let { found ->
        ShortcutLookup.Found(found.shortcuts.filter { hit ->
            hit.appKey?.let { it in keys } ?: keys.any { it.packageName == hit.packageName && it.userSerial == hit.userSerial }
        })
    } ?: results.shortcuts,
)

private const val OPEN_DURATION_MS = 180L
private const val CLOSE_DURATION_MS = 140L
private const val SLIDE_DP = 24f
private const val DECELERATE_FACTOR = 1.5f
/** How far a full back swipe shrinks search before it closes. */
private const val BACK_SCALE_DROP = 0.08f
