package io.github.aj4abinjacob.chikku.home.cards

import android.content.Intent
import android.graphics.Rect
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.applock.AppLockState
import io.github.aj4abinjacob.chikku.applock.AppUnlocker
import io.github.aj4abinjacob.chikku.core.model.AppCustomization
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.AppsState
import io.github.aj4abinjacob.chikku.core.model.GestureAction
import io.github.aj4abinjacob.chikku.core.model.LauncherApp
import io.github.aj4abinjacob.chikku.core.model.WorkspaceItem
import io.github.aj4abinjacob.chikku.core.model.WorkspaceState
import io.github.aj4abinjacob.chikku.core.model.cards.AutomationCard
import io.github.aj4abinjacob.chikku.core.model.cards.CardConditionEvaluator
import io.github.aj4abinjacob.chikku.core.model.cards.CardContent
import io.github.aj4abinjacob.chikku.core.model.cards.CardFeatured
import io.github.aj4abinjacob.chikku.core.model.cards.CardVisibility
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.home.HomeDependencies
import io.github.aj4abinjacob.chikku.home.screenBounds
import io.github.aj4abinjacob.chikku.home.cards.media.MediaCardView
import io.github.aj4abinjacob.chikku.home.cards.weather.WeatherCardView
import io.github.aj4abinjacob.chikku.home.cards.widget.EmbeddedCardWidgetController
import io.github.aj4abinjacob.chikku.home.gestures.showAccessibilityExplanation
import io.github.aj4abinjacob.chikku.message.ChikkuMessages
import io.github.aj4abinjacob.chikku.platform.GestureActionExecutor
import io.github.aj4abinjacob.chikku.platform.GestureResult
import io.github.aj4abinjacob.chikku.platform.LaunchResult
import io.github.aj4abinjacob.chikku.platform.ShortcutIconLoader
import io.github.aj4abinjacob.chikku.platform.cards.AndroidCardEnvironmentSource
import io.github.aj4abinjacob.chikku.platform.cards.AndroidCardsTargetValidator
import io.github.aj4abinjacob.chikku.platform.cards.CardEnvironmentSource
import io.github.aj4abinjacob.chikku.platform.cards.CardValidation
import io.github.aj4abinjacob.chikku.platform.cards.CardsTargetValidator
import io.github.aj4abinjacob.chikku.prompt.ChikkuPrompts
import io.github.aj4abinjacob.chikku.prompt.PromptHandle
import io.github.aj4abinjacob.chikku.settings.SettingsActivity
import io.github.aj4abinjacob.chikku.settings.SettingsSection
import io.github.aj4abinjacob.chikku.settings.components.simpleGestureActionLabel
import io.github.aj4abinjacob.chikku.theme.LauncherSurface
import io.github.aj4abinjacob.chikku.theme.Motion
import io.github.aj4abinjacob.chikku.theme.SurfaceColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Local, explicitly opened contextual cards. Observations live only while open and STARTED. */
class CardsController(
    private val activity: ComponentActivity,
    private val root: FrameLayout,
    private val deps: HomeDependencies,
    private val executor: GestureActionExecutor,
    private val onOpenWidget: (workspaceItemId: String) -> Unit,
    private val environment: CardEnvironmentSource = AndroidCardEnvironmentSource(activity),
    private val validator: CardsTargetValidator = AndroidCardsTargetValidator(activity, deps.appLock),
    /** Default: a locked app simply does not open from a card. HOME passes its unlock prompt. */
    private val unlocker: AppUnlocker = AppUnlocker { _, _ -> },
    /** HOME passes its shared instance so only one Chikku prompt shows at a time. */
    private val prompts: ChikkuPrompts = ChikkuPrompts(activity),
    /** Chikku's message bar for short notices; never a platform toast. */
    private val messages: ChikkuMessages = ChikkuMessages.of(activity),
) {
    private data class Input(
        val settings: LauncherSettings,
        val apps: List<LauncherApp>,
        val customizations: Map<AppKey, AppCustomization>,
        val items: List<WorkspaceItem>,
        val ready: Boolean,
        val loading: Boolean,
        val lockState: AppLockState = AppLockState(),
    ) {
        val hidden get() = customizations.values.filter { it.hidden }.mapTo(mutableSetOf()) { it.key }
    }

    private val inputFlow = combine(deps.settingsRepository.settings, deps.appsRepository.apps,
        deps.appCustomizationRepository.customizations, deps.workspaceRepository.workspace, deps.corruptLayoutNotice,
    ) { settings, apps, customizations, workspace, corrupt ->
        Input(settings, (apps as? AppsState.Loaded)?.apps.orEmpty(), customizations,
            (workspace as? WorkspaceState.Ready)?.workspace?.items.orEmpty(),
            apps is AppsState.Loaded && workspace is WorkspaceState.Ready && !corrupt,
            !corrupt && apps !is AppsState.Failed && workspace !is WorkspaceState.Failed &&
                (apps is AppsState.Loading || workspace is WorkspaceState.Loading))
    }.flowOn(Dispatchers.IO).combine(deps.appLock.state) { input, lockState -> input.copy(lockState = lockState) }

    private val embeddedWidgets = EmbeddedCardWidgetController(activity, deps, prompts = prompts, messages = messages)
    private val shortcutIcons by lazy { ShortcutIconLoader(activity, deps.appLock, sharedPacks = deps.iconPackRepository) }

    private var surface: CardsSurface? = null
    /** A surface sliding out after a user dismissal; its widgets are released when it is gone. */
    private var exiting: CardsSurface? = null
    private var observation: Job? = null
    private var privacyObservation: Job? = null
    private var action: Job? = null
    private var prompt: PromptHandle? = null
    private var resolvedInput: Input? = null
    private var renderedItems: List<CardSurfaceItem> = emptyList()
    private var renderedEmpty = CardsPanelEmpty.PENDING
    private var generation = 0L
    private val back = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = dismiss()
    }
    val isOpen: Boolean get() = surface != null

    init { activity.onBackPressedDispatcher.addCallback(activity, back) }

    fun open() {
        if (isOpen) return
        exiting?.close() // finishes at once; releases its widgets before the new panel opens
        val openedGeneration = ++generation
        surface = CardsSurface(root, ::dismiss, ::openCardSettings, ::tap, loadIcon = ::loadIcon,
            onCardDiscarded = embeddedWidgets::releaseCard) { card, colors ->
            when (val content = card.content) {
                CardContent.Actions -> null
                is CardContent.Weather -> WeatherCardView(activity, content, deps.cardWeatherSource, colors, ::openCardSettings)
                CardContent.Media -> MediaCardView(activity, deps.cardMediaSource, colors, messages)
                is CardContent.Widget -> embeddedWidgets.createView(card, colors)
            }
        }
        embeddedWidgets.openPanel()
        back.isEnabled = true
        render(emptyList(), LauncherSettings(), CardsPanelEmpty.PENDING, animate = true)
        // Separate from collectLatest validation: an uncooperative Binder read must not delay
        // withdrawing already visible private labels and artwork after a lock change.
        privacyObservation = activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                deps.appLock.state.collect {
                    if (openedGeneration == generation) render(renderedItems, resolvedInput?.settings ?: LauncherSettings(), renderedEmpty)
                }
            }
        }
        observation = activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                try {
                    combine(inputFlow, deps.settingsRepository.settings
                        .map { it.cards.cards.any { card -> card.enabled && card.conditions.bluetoothConnectedOnly } }
                        .distinctUntilChanged().flatMapLatest { environment.observe(it) }) { input, env -> input to env }
                        .collectLatest { (input, env) ->
                            if (openedGeneration != generation) return@collectLatest
                            if (input != resolvedInput) render(emptyList(), input.settings, CardsPanelEmpty.PENDING)
                            else render(renderedItems.filter { CardConditionEvaluator.matches(it.card.conditions, env) },
                                input.settings, cardsPanelEmpty(input.settings.cards.cards))
                            val candidates = if (input.ready) input.settings.cards.cards.filter {
                                it.enabled && CardConditionEvaluator.matches(it.conditions, env)
                            } else emptyList()
                            val validated = withContext(Dispatchers.IO) {
                                validator.validate(candidates, input.apps, input.hidden, input.items)
                            }
                            val visible = candidates.filter { CardVisibility.targetsAllowed(it, validated.targets) }
                                .map { surfaceItem(it, input, validated) }
                            if (openedGeneration != generation || input.lockState != deps.appLock.state.value) return@collectLatest
                            resolvedInput = input
                            render(visible, input.settings, cardsPanelEmpty(input.settings.cards.cards))
                        }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Failure withholds all titles, rather than rendering cached target names.
                    if (openedGeneration == generation) render(emptyList(), resolvedInput?.settings ?: LauncherSettings(),
                        cardsPanelEmpty(resolvedInput?.settings?.cards?.cards))
                } finally {
                    if (openedGeneration == generation) {
                        resolvedInput = null
                        render(emptyList(), LauncherSettings(), CardsPanelEmpty.PENDING)
                    }
                }
            }
        }
    }

    fun close() = closeSurface(animate = false)

    /** Back, scrim tap, grabber drag and the close button: slides out instead of vanishing. */
    private fun dismiss() = closeSurface(animate = true)

    private fun closeSurface(animate: Boolean) {
        generation++
        observation?.cancel()
        observation = null
        privacyObservation?.cancel()
        privacyObservation = null
        action?.cancel()
        action = null
        val closing = surface
        surface = null
        if (animate && closing != null) {
            exiting?.close()
            exiting = closing
            closing.close(animate = true) {
                if (exiting === closing) exiting = null
                embeddedWidgets.closePanel()
            }
        } else {
            exiting?.close()
            exiting = null
            embeddedWidgets.closePanel()
            closing?.close()
        }
        back.isEnabled = false
        resolvedInput = null
        renderedItems = emptyList()
        renderedEmpty = CardsPanelEmpty.PENDING
        prompt?.dismiss()
        prompt = null
    }

    fun onActivityResult(requestCode: Int, resultCode: Int): Boolean =
        embeddedWidgets.onActivityResult(requestCode, resultCode)

    fun onDestroy() {
        close()
        embeddedWidgets.onDestroy()
        back.remove()
    }

    private fun metadataAllowed(card: AutomationCard): Boolean {
        val featured = card.featured
        if (featured is CardFeatured.Shortcut && !deps.appLock.mayRead(featured.packageName, featured.userSerial)) return false
        return card.actions.none { it is GestureAction.LaunchShortcut && !deps.appLock.mayRead(it.packageName, it.userSerial) }
    }

    /** [empty] is what the panel says if no card is left to show. */
    private fun render(items: List<CardSurfaceItem>, settings: LauncherSettings, empty: CardsPanelEmpty, animate: Boolean = false) {
        val safeItems = items.filter { metadataAllowed(it.card) }
        if (safeItems == renderedItems && empty == renderedEmpty && settings == resolvedInput?.settings && !animate) return
        renderedItems = safeItems
        renderedEmpty = empty
        // Unchanged cards keep their views (and hosted widgets); the surface releases only the rest.
        surface?.render(safeItems, SurfaceColors.resolve(LauncherSurface.MENU, settings.night, activity),
            Motion.current(settings.lookFeel.animationSpeed), animate, empty)
    }

    /** The panel's edit button, its empty-state action and a weather card's "Set up weather". */
    private fun openCardSettings() {
        close()
        runCatching { activity.startActivity(Intent(activity, SettingsActivity::class.java)
            .putExtra(SettingsActivity.EXTRA_SECTION, SettingsSection.CARDS.name)) }
            .onFailure { messages.show(R.string.gesture_unavailable) }
    }

    private fun surfaceItem(card: AutomationCard, input: Input, validated: CardValidation): CardSurfaceItem {
        fun appLabel(key: AppKey) = input.customizations[key]?.customLabel?.takeIf { it.isNotBlank() }
            ?: input.apps.firstOrNull { it.key == key }?.label.orEmpty()
        fun actionLabel(action: GestureAction): String = when (action) {
            is GestureAction.LaunchApp -> activity.getString(R.string.gesture_action_launch_app, appLabel(action.key))
            is GestureAction.LaunchShortcut -> activity.getString(R.string.gesture_action_launch_shortcut,
                validated.shortcutLabels[CardFeatured.Shortcut(action.packageName, action.shortcutId, action.userSerial)].orEmpty())
            else -> activity.getString(simpleGestureActionLabel(action) ?: R.string.gesture_action_none)
        }
        val featuredLabel = when (val featured = card.featured) {
            CardFeatured.None -> null
            is CardFeatured.App -> actionLabel(GestureAction.LaunchApp(featured.key))
            is CardFeatured.Shortcut -> actionLabel(GestureAction.LaunchShortcut(featured.packageName, featured.shortcutId, featured.userSerial))
            is CardFeatured.Widget -> activity.getString(R.string.cards_surface_open_widget, validated.widgetLabels[featured.itemId].orEmpty())
        }
        return CardSurfaceItem(card, featuredLabel, card.actions.mapIndexedNotNull { index, button ->
            if (button == GestureAction.None) null else index to actionLabel(button)
        }, cardConditionSummary(activity, card.conditions))
    }

    /** Icons load only for targets the validator already allowed into the rendered panel. */
    private fun loadIcon(action: GestureAction, view: ImageView) {
        if (action is GestureAction.LaunchShortcut && !deps.appLock.mayRead(action.packageName, action.userSerial)) return
        val token = Any()
        view.tag = token
        val openedGeneration = generation
        activity.lifecycleScope.launch {
            val icon = try {
                when (action) {
                    is GestureAction.LaunchApp -> deps.iconProvider.icon(action.key)
                    is GestureAction.LaunchShortcut -> shortcutIcons.load(action.packageName, action.shortcutId, action.userSerial)
                    else -> null
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) { null }
            val readable = action !is GestureAction.LaunchShortcut || deps.appLock.mayRead(action.packageName, action.userSerial)
            if (readable && icon != null && view.tag === token && openedGeneration == generation) view.setImageDrawable(icon)
        }
    }

    private fun tap(id: String, index: Int?, source: View) {
        if (action?.isActive == true) return
        // Measured now: the panel closes before the validated action runs.
        val sourceBounds = source.screenBounds()
        val expected = renderedItems.firstOrNull { it.card.id == id }?.card ?: return
        val tappedGeneration = generation
        action = activity.lifecycleScope.launch {
            try {
                val first = readCompletedInput()
                val card = first.settings.cards.cards.firstOrNull { it.id == id } ?: return@launch unavailable()
                if (card != expected || !first.ready || !card.enabled ||
                    !CardConditionEvaluator.matches(card.conditions, environment.current())) return@launch unavailable()
                val validated = withContext(Dispatchers.IO) {
                    validator.validate(listOf(card), first.apps, first.hidden, first.items)
                }
                // A package lookup can suspend across hiding, profile changes, recovery or edits.
                // Re-read the complete repositories before allowing any of this card's actions.
                val fresh = readCompletedInput()
                if (!isOpen || tappedGeneration != generation || !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return@launch
                if (fresh != first || !CardVisibility.targetsAllowed(card, validated.targets) ||
                    !CardConditionEvaluator.matches(card.conditions, environment.current())) return@launch unavailable()
                val selected = if (index != null) card.actions.getOrNull(index) else when (val featured = card.featured) {
                    CardFeatured.None -> GestureAction.None
                    is CardFeatured.App -> GestureAction.LaunchApp(featured.key)
                    is CardFeatured.Shortcut -> GestureAction.LaunchShortcut(featured.packageName, featured.shortcutId, featured.userSerial)
                    is CardFeatured.Widget -> {
                        finishTap()
                        onOpenWidget(featured.itemId)
                        return@launch
                    }
                }
                selected?.let {
                    finishTap()
                    run(it, sourceBounds)
                }
            } catch (_: TimeoutCancellationException) {
                if (tappedGeneration == generation) unavailable()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (tappedGeneration == generation) unavailable()
            }
        }
    }

    /** Avoid cancelling the coroutine currently delivering an explicitly validated action. */
    private fun finishTap() { action = null; close() }

    /** Room emits Loading for each new collector; terminal errors must still fail immediately. */
    private suspend fun readCompletedInput(): Input = withContext(Dispatchers.IO) {
        withTimeout(5_000) { inputFlow.first { !it.loading } }
    }

    private fun unavailable() {
        render(emptyList(), resolvedInput?.settings ?: LauncherSettings(), cardsPanelEmpty(resolvedInput?.settings?.cards?.cards))
        messages.show(R.string.cards_surface_unavailable)
    }

    private fun run(action: GestureAction, sourceBounds: Rect?) {
        when (val result = executor.execute(action, sourceBounds)) {
            is GestureResult.Locked -> unlocker.unlock(result.locked) { run(action, sourceBounds) }
            else -> present(result)
        }
    }

    private fun present(result: GestureResult) {
        when (result) {
            GestureResult.Performed, GestureResult.NoAction, is GestureResult.Locked -> Unit
            is GestureResult.Unavailable -> messages.show(
                if (result.reason == LaunchResult.Reason.SHORTCUT_UNAVAILABLE)
                    R.string.shortcut_needs_recreation else R.string.gesture_unavailable,
            )
            GestureResult.NeedsAccessibilityService -> prompt = prompts.showAccessibilityExplanation(activity, messages)
        }
    }
}
