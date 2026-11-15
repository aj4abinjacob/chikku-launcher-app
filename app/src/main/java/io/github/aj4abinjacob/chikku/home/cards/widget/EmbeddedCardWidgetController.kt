package io.github.aj4abinjacob.chikku.home.cards.widget

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.AppsState
import io.github.aj4abinjacob.chikku.core.model.WorkspaceState
import io.github.aj4abinjacob.chikku.core.model.cards.AutomationCard
import io.github.aj4abinjacob.chikku.core.model.cards.CardContent
import io.github.aj4abinjacob.chikku.core.model.cards.CardVisibility
import io.github.aj4abinjacob.chikku.data.cards.CardWidgetBinding
import io.github.aj4abinjacob.chikku.data.cards.CardWidgetBindingPhase
import io.github.aj4abinjacob.chikku.data.cards.CardWidgetBindingState
import io.github.aj4abinjacob.chikku.data.cards.CardWidgetBindingStore
import io.github.aj4abinjacob.chikku.data.cards.MemoryCardWidgetBindingStore
import io.github.aj4abinjacob.chikku.home.HomeDependencies
import io.github.aj4abinjacob.chikku.message.ChikkuMessages
import io.github.aj4abinjacob.chikku.platform.cards.AndroidCardsTargetValidator
import io.github.aj4abinjacob.chikku.prompt.ChikkuPrompts
import io.github.aj4abinjacob.chikku.prompt.PromptAction
import io.github.aj4abinjacob.chikku.prompt.PromptHandle
import io.github.aj4abinjacob.chikku.prompt.PromptSpec
import io.github.aj4abinjacob.chikku.prompt.PromptTone
import io.github.aj4abinjacob.chikku.settings.SettingsActivity
import io.github.aj4abinjacob.chikku.settings.SettingsSection
import io.github.aj4abinjacob.chikku.theme.SurfaceColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Dedicated card host. A closed panel releases views/listening, never its persisted bindings. */
class EmbeddedCardWidgetController(
    private val activity: ComponentActivity,
    private val deps: HomeDependencies,
    private val store: CardWidgetBindingStore = deps.cardWidgetBindingStore ?: MemoryCardWidgetBindingStore(),
    platform: EmbeddedCardWidgetPlatform? = null,
    private val catalog: CardWidgetCatalogSource = AndroidCardWidgetCatalogSource(activity),
    /** HOME's shared instance (through CardsController) so only one Chikku prompt shows at a time. */
    private val prompts: ChikkuPrompts = ChikkuPrompts(activity),
    /** Chikku's message bar for short notices; never a platform toast. */
    private val messages: ChikkuMessages = ChikkuMessages.of(activity),
) : DefaultLifecycleObserver {
    private val platform = platform ?: AndroidEmbeddedCardWidgetPlatform(activity) { activity.runOnUiThread(::refreshAll) }
    private val coordinator = CardWidgetBindCoordinator(store, this.platform, ::stillValid)
    private val validator = AndroidCardsTargetValidator(activity, deps.appLock)
    private data class Cell(val card: AutomationCard, val colors: SurfaceColors, var job: Job? = null,
        var layoutListener: View.OnLayoutChangeListener? = null)
    private val cells = LinkedHashMap<FrameLayout, Cell>()
    private var panelOpen = false
    private var started = false
    private var listening = false
    private var inFlight = false
    private var pending: CardWidgetBinding? = null
    private var destroyed = false
    private var prompt: PromptHandle? = null
    private var observation: Job? = null

    private val consent = activity.activityResultRegistry.register(CONSENT_RESULT_KEY, ActivityResultContracts.StartActivityForResult()) { result ->
        runFlow { currentPending(CardWidgetBindingPhase.CONSENT)?.let {
            coordinator.consentResult(it, result.resultCode == Activity.RESULT_OK)
        } ?: CardWidgetStep.Ignored }
    }

    init {
        activity.savedStateRegistry.consumeRestoredStateForKey(STATE_KEY)?.let { pending = it.binding() }
        activity.savedStateRegistry.registerSavedStateProvider(STATE_KEY) { pending?.bundle() ?: Bundle() }
        activity.lifecycle.addObserver(this)
        observation = activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                deps.settingsRepository.settings.collectLatest { settings ->
                    withContext(Dispatchers.IO) {
                        val requests = settings.cards.cards.mapNotNull { card -> request(card)?.let { card.id to it } }.toMap()
                        try { coordinator.reconcile(requests) }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { /* Keep durable state; visible cells offer a retry. */ }
                    }
                    refreshAll()
                }
            }
        }
    }

    fun openPanel() { panelOpen = true; updateListening(); refreshAll() }

    fun closePanel() {
        panelOpen = false
        updateListening()
        clearPanelViews()
    }

    /** One card's view is being replaced or removed; other cards keep their hosted widgets. */
    fun releaseCard(cardId: String) {
        val gone = cells.filter { (_, cell) -> cell.card.id == cardId }.keys.toList()
        gone.forEach { view ->
            cells.remove(view)?.let { cell ->
                cell.job?.cancel(); cell.layoutListener?.let(view::removeOnLayoutChangeListener); cell.layoutListener = null
            }
            view.removeAllViews()
        }
    }

    /** Called before the card list replaces its widget rows. Never changes bindings or host IDs. */
    fun clearPanelViews() {
        cells.forEach { (view, cell) ->
            cell.job?.cancel(); cell.layoutListener?.let(view::removeOnLayoutChangeListener); cell.layoutListener = null
            view.removeAllViews()
        }
        cells.clear()
        platform.clearViews()
    }

    /** Call on main while rendering a visible, validated card. Setup starts only on explicit tap. */
    fun createView(card: AutomationCard, colors: SurfaceColors): View {
        val widget = card.content as? CardContent.Widget ?: return View(activity)
        val view = FrameLayout(activity).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(widget.heightDp))
            minimumHeight = dp(96)
            background = GradientDrawable().apply { setColor(colors.field); cornerRadius = dp(20).toFloat() }
            clipToOutline = true
            tag = "card_widget:${card.id}"
        }
        cells[view] = Cell(card, colors)
        if (panelOpen && started) refresh(view)
        return view
    }

    /** Provider configure activities support the legacy host result path. */
    fun onActivityResult(requestCode: Int, resultCode: Int): Boolean {
        if (requestCode != REQUEST_CONFIGURE) return false
        runFlow { currentPending(CardWidgetBindingPhase.CONFIGURATION)?.let {
            coordinator.configurationResult(it, resultCode == Activity.RESULT_OK)
        } ?: CardWidgetStep.Ignored }
        return true
    }

    override fun onStart(owner: LifecycleOwner) { started = true; updateListening(); refreshAll() }
    override fun onStop(owner: LifecycleOwner) {
        started = false
        updateListening()
        cells.forEach { (view, cell) ->
            cell.job?.cancel(); cell.layoutListener?.let(view::removeOnLayoutChangeListener); cell.layoutListener = null
            view.removeAllViews()
        }
        platform.clearViews()
    }
    override fun onDestroy(owner: LifecycleOwner) = onDestroy()
    fun onDestroy() {
        if (destroyed) return
        destroyed = true
        closePanel()
        prompt?.dismiss()
        prompt = null
        observation?.cancel()
        consent.unregister()
        activity.savedStateRegistry.unregisterSavedStateProvider(STATE_KEY)
        activity.lifecycle.removeObserver(this)
    }

    private fun updateListening() {
        val wanted = panelOpen && started && !destroyed
        if (wanted == listening) return
        listening = wanted
        // AppWidgetHost.startListening can apply returned RemoteViews to live views.
        runCatching { if (wanted) platform.startListening() else platform.stopListening() }
    }

    private fun refreshAll() { if (panelOpen && started && !destroyed) cells.keys.toList().forEach(::refresh) }

    private fun refresh(view: FrameLayout) {
        val cell = cells[view] ?: return
        cell.job?.cancel()
        cell.job = activity.lifecycleScope.launch {
            val requested = request(cell.card)
            val loaded = try { withContext(Dispatchers.IO) {
                val state = store.load()
                val binding = (state as? CardWidgetBindingState.Ready)?.bindings?.get(cell.card.id)
                    ?.takeIf { requested != null && it.instanceId == requested.instanceId && it.provider == requested.provider }
                val allowed = requested == null || stillValid(requested)
                val info = if (allowed && binding?.phase == CardWidgetBindingPhase.BOUND &&
                    platform.boundProvider(binding.appWidgetId) == requested?.provider) platform.info(binding.appWidgetId) else null
                Triple(state, binding, if (allowed) info else null) to allowed
            } } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (panelOpen && started && cells[view] === cell) {
                    cell.layoutListener?.let(view::removeOnLayoutChangeListener); cell.layoutListener = null
                    view.removeAllViews()
                    placeholder(view, cell.colors, R.string.card_widget_unavailable, R.string.card_widget_retry) { refresh(view) }
                }
                return@launch
            }
            if (!panelOpen || !started || cells[view] !== cell) return@launch
            cell.layoutListener?.let(view::removeOnLayoutChangeListener); cell.layoutListener = null
            view.removeAllViews()
            if (!loaded.second) { view.visibility = View.GONE; return@launch }
            view.visibility = View.VISIBLE
            val (state, binding, info) = loaded.first
            when {
                state is CardWidgetBindingState.Corrupt -> placeholder(view, cell.colors, R.string.card_widget_storage_error,
                    R.string.card_widget_recover) { confirmRecovery() }
                requested == null -> placeholder(view, cell.colors, R.string.card_widget_choose_summary,
                    R.string.card_widget_choose) { openEditor() }
                binding?.phase == CardWidgetBindingPhase.BOUND && info != null -> {
                    val hosted = runCatching { platform.createView(activity, binding.appWidgetId, info) }.getOrNull()
                    if (hosted == null) setupPlaceholder(view, cell, requested)
                    else {
                        view.addView(hosted, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                        val listener = View.OnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
                            if (right > left && bottom > top && panelOpen && started) activity.lifecycleScope.launch {
                                withContext(Dispatchers.IO) { runCatching { platform.updateSize(binding.appWidgetId,
                                    ((right - left) / density).toInt(), ((bottom - top) / density).toInt()) } }
                            }
                        }
                        cell.layoutListener = listener
                        view.addOnLayoutChangeListener(listener)
                    }
                }
                else -> setupPlaceholder(view, cell, requested)
            }
        }
    }

    private fun setupPlaceholder(view: FrameLayout, cell: Cell, request: CardWidgetRequest) {
        placeholder(view, cell.colors, R.string.card_widget_setup_summary, R.string.card_widget_setup) {
            if (!inFlight && started && panelOpen) runFlow { coordinator.begin(request) }
        }
    }

    private fun placeholder(view: FrameLayout, colors: SurfaceColors, message: Int, button: Int, onClick: () -> Unit) {
        view.addView(LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(8), dp(12), dp(8))
            addView(TextView(activity).apply { text = activity.getString(message); textSize = 15f; setTextColor(colors.onSurfaceVariant) })
            addView(Button(activity, null, android.R.attr.borderlessButtonStyle).apply {
                text = activity.getString(button); contentDescription = text; isAllCaps = false
                minHeight = dp(48); setTextColor(colors.onSurface); setOnClickListener {
                    if (panelOpen && started && !destroyed && view in cells) onClick()
                }
            })
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun runFlow(step: suspend () -> CardWidgetStep) {
        if (inFlight || destroyed) return
        inFlight = true
        deps.appScope.launch {
            val result = try { withContext(Dispatchers.IO) {
                val next = step()
                val binding = when (next) { is CardWidgetStep.Consent -> next.binding; is CardWidgetStep.Configure -> next.binding; else -> null }
                if (binding != null && !stillValid(CardWidgetRequest(binding.cardId, binding.instanceId, binding.provider))) coordinator.cancel(binding)
                else next
            } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { CardWidgetStep.StorageFailure }
            withContext(Dispatchers.Main.immediate) {
                inFlight = false
                if (destroyed || activity.isDestroyed) return@withContext // durable pending remains recoverable
                when (result) {
                    is CardWidgetStep.Consent -> {
                        pending = result.binding
                        if (!panelOpen || !started) { refreshAll(); return@withContext }
                        runCatching { consent.launch(platform.consentIntent(result.binding.appWidgetId, result.binding.provider)) }
                            .onFailure { runFlow { coordinator.cancel(result.binding) } }
                    }
                    is CardWidgetStep.Configure -> {
                        pending = result.binding
                        if (!panelOpen || !started) { refreshAll(); return@withContext }
                        runCatching { platform.configure(activity, result.binding.appWidgetId, REQUEST_CONFIGURE) }
                            .onFailure { runFlow { coordinator.cancel(result.binding) } }
                    }
                    is CardWidgetStep.Bound -> { pending = null; refreshAll() }
                    CardWidgetStep.Rejected -> { pending = null; showMessage(R.string.card_widget_unavailable); refreshAll() }
                    CardWidgetStep.Ignored -> Unit
                    CardWidgetStep.StorageFailure -> { showMessage(R.string.card_widget_storage_error); refreshAll() }
                }
            }
        }
    }

    private suspend fun currentPending(phase: CardWidgetBindingPhase): CardWidgetBinding? {
        val state = store.load() as? CardWidgetBindingState.Ready ?: return null
        val expected = pending ?: return null // no result token: require an explicit setup resume
        return state.bindings[expected.cardId]?.takeIf {
            it.token == expected.token && it.instanceId == expected.instanceId && it.appWidgetId == expected.appWidgetId && it.phase == phase
        }
    }

    private suspend fun stillValid(requested: CardWidgetRequest): Boolean = withTimeoutOrNull(5_000) {
        val settings = deps.settingsRepository.settings.first()
        val card = settings.cards.cards.firstOrNull { it.id == requested.cardId } ?: return@withTimeoutOrNull false
        if (request(card) != requested || !card.enabled || deps.corruptLayoutNotice.value) return@withTimeoutOrNull false
        if (catalog.personalSerial() != requested.provider.userSerial) return@withTimeoutOrNull false
        val apps = deps.appsRepository.apps.first { it !is AppsState.Loading } as? AppsState.Loaded ?: return@withTimeoutOrNull false
        val customizations = deps.appCustomizationRepository.customizations.first()
        val workspace = deps.workspaceRepository.workspace.first { it !is WorkspaceState.Loading } as? WorkspaceState.Ready ?: return@withTimeoutOrNull false
        val hidden = customizations.values.filter { it.hidden }.mapTo(mutableSetOf()) { it.key }
        if (hidden.any { it.packageName == requested.provider.packageName && it.userSerial == requested.provider.userSerial }) return@withTimeoutOrNull false
        val targets = validator.validate(listOf(card), apps.apps, hidden, workspace.workspace.items)
        val fresh = deps.settingsRepository.settings.first().cards.cards.firstOrNull { it.id == requested.cardId }
        val freshApps = deps.appsRepository.apps.first { it !is AppsState.Loading } as? AppsState.Loaded ?: return@withTimeoutOrNull false
        val freshHidden = deps.appCustomizationRepository.customizations.first().values.filter { it.hidden }.mapTo(mutableSetOf()) { it.key }
        val freshWorkspace = deps.workspaceRepository.workspace.first { it !is WorkspaceState.Loading } as? WorkspaceState.Ready ?: return@withTimeoutOrNull false
        val freshTargets = targets.targets.copy(hidden = freshHidden,
            apps = freshApps.apps.filter { current -> targets.targets.apps.any { it.key == current.key } },
            widgets = targets.targets.widgets.filter { (id, provider) -> freshWorkspace.workspace.items.any {
                it.id == id && (it.target as? io.github.aj4abinjacob.chikku.core.model.ItemTarget.Widget)?.provider == provider
            } })
        fresh == card && !deps.corruptLayoutNotice.value && CardVisibility.targetsAllowed(card, freshTargets)
    } ?: false

    /** Resetting drops every card's widget binding, so it is confirmed first (warning tone). */
    private fun confirmRecovery() {
        prompt = prompts.show(PromptSpec(
            icon = R.drawable.ic_menu_refresh,
            title = activity.getString(R.string.prompt_card_widget_recover_title),
            message = activity.getString(R.string.prompt_card_widget_recover_message),
            primary = PromptAction(activity.getString(R.string.prompt_card_widget_recover_action)) {
                deps.appScope.launch {
                    val recovered = try { withContext(Dispatchers.IO) { coordinator.recover() } }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { false }
                    if (!recovered) showMessage(R.string.card_widget_storage_error)
                    refreshAll()
                }
            },
            dismissLabel = activity.getString(R.string.prompt_not_now),
            tone = PromptTone.WARNING,
        ))
    }

    private fun openEditor() { runCatching { activity.startActivity(Intent(activity, SettingsActivity::class.java)
        .putExtra(SettingsActivity.EXTRA_SECTION, SettingsSection.CARDS.name)) }.onFailure { showMessage(R.string.card_widget_unavailable) } }
    private fun request(card: AutomationCard): CardWidgetRequest? = (card.content as? CardContent.Widget)?.let { content ->
        content.provider?.let { CardWidgetRequest(card.id, content.instanceId, it) }
    }
    private val density get() = activity.resources.displayMetrics.density
    private fun dp(value: Int) = (value * density).toInt()
    private fun showMessage(resource: Int) = messages.show(resource)

    private fun CardWidgetBinding.bundle() = Bundle().apply {
        putString("card", cardId); putString("instance", instanceId); putString("package", provider.packageName)
        putString("class", provider.className); putLong("serial", provider.userSerial); putInt("id", appWidgetId)
        putString("token", token); putString("phase", phase.name); previousId?.let { putInt("previous", it) }
    }
    private fun Bundle.binding(): CardWidgetBinding? = runCatching {
        CardWidgetBinding(getString("card") ?: return null, getString("instance") ?: return null,
            io.github.aj4abinjacob.chikku.core.model.ComponentKey(getString("package") ?: return null,
                getString("class") ?: return null, getLong("serial")), getInt("id"), getString("token") ?: return null,
            CardWidgetBindingPhase.valueOf(getString("phase") ?: return null), if (containsKey("previous")) getInt("previous") else null)
    }.getOrNull()
    companion object { const val REQUEST_CONFIGURE = 0xCA71; private const val STATE_KEY = "chikku.card.widgets.pending"; private const val CONSENT_RESULT_KEY = "chikku.card.widgets.consent" }
}
