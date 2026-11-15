package io.github.aj4abinjacob.chikku.settings.sections

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import io.github.aj4abinjacob.chikku.settings.cards.ListAutoScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.AppCustomization
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.ItemTarget
import io.github.aj4abinjacob.chikku.core.model.LauncherApp
import io.github.aj4abinjacob.chikku.core.model.WorkspaceItem
import io.github.aj4abinjacob.chikku.core.model.cards.CardTargetSnapshot
import io.github.aj4abinjacob.chikku.home.HomeDependencies
import androidx.compose.material3.SwipeToDismissBoxState
import io.github.aj4abinjacob.chikku.core.model.ProfileType
import io.github.aj4abinjacob.chikku.core.model.WorkspaceState
import io.github.aj4abinjacob.chikku.core.model.cards.AutomationCard
import io.github.aj4abinjacob.chikku.core.model.cards.CardFeatured
import io.github.aj4abinjacob.chikku.core.model.cards.CardVisibility
import io.github.aj4abinjacob.chikku.core.model.cards.CardsSettings
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.home.cards.widget.CardWidgetCatalogSource
import io.github.aj4abinjacob.chikku.platform.cards.AndroidCardsTargetValidator
import io.github.aj4abinjacob.chikku.platform.cards.CardValidation
import io.github.aj4abinjacob.chikku.settings.LocalHomeDependencies
import io.github.aj4abinjacob.chikku.settings.cards.CardEditorDialog
import io.github.aj4abinjacob.chikku.settings.cards.CardListRow
import io.github.aj4abinjacob.chikku.settings.cards.CardReorderList
import io.github.aj4abinjacob.chikku.settings.cards.CardTemplate
import io.github.aj4abinjacob.chikku.settings.cards.CardTemplateDialog
import io.github.aj4abinjacob.chikku.settings.cards.CardWidgetOption
import io.github.aj4abinjacob.chikku.settings.cards.CardsCreateButton
import io.github.aj4abinjacob.chikku.settings.cards.CardsIntro
import io.github.aj4abinjacob.chikku.settings.cards.CardsOnboarding
import io.github.aj4abinjacob.chikku.settings.cards.DeleteBackground
import io.github.aj4abinjacob.chikku.settings.cards.RemovedCard
import io.github.aj4abinjacob.chikku.settings.cards.cardEditorTitle
import io.github.aj4abinjacob.chikku.settings.cards.cardTargetsAvailable
import io.github.aj4abinjacob.chikku.settings.cards.cardTemplateDraft
import io.github.aj4abinjacob.chikku.settings.cards.moveCard
import io.github.aj4abinjacob.chikku.settings.cards.moveCardTo
import io.github.aj4abinjacob.chikku.settings.cards.persistCardMutation
import io.github.aj4abinjacob.chikku.settings.cards.readCardEditorSnapshot
import io.github.aj4abinjacob.chikku.settings.cards.restoreRemovedCard
import io.github.aj4abinjacob.chikku.settings.cards.saveCardDraft
import io.github.aj4abinjacob.chikku.settings.cards.validateCardDraftSnapshot
import io.github.aj4abinjacob.chikku.settings.components.SettingsInfoText
import io.github.aj4abinjacob.chikku.settings.components.collectLauncherApps
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/** Ordered local cards; content comes only from currently visible personal apps and placements. */
@Composable
@Suppress("UNUSED_PARAMETER") // Standard section contract; card mutations await durable storage below.
fun CardsSettingsSection(settings: LauncherSettings, update: ((LauncherSettings) -> LauncherSettings) -> Unit,
    widgetCatalogSource: CardWidgetCatalogSource? = null) {
    val deps = LocalHomeDependencies.current
    val context = LocalContext.current
    val lockState by deps.appLock.state.collectAsState()
    val validator = remember(context, deps.appLock) { AndroidCardsTargetValidator(context, deps.appLock) }
    val scope = rememberCoroutineScope()
    val apps = collectLauncherApps()
    val customFlow = remember(deps) {
        deps.appCustomizationRepository.customizations.map { it as Map<AppKey, AppCustomization>? }.catch { emit(null) }
    }
    val customizations by customFlow.collectAsState(null)
    val workspaceFlow = remember(deps) { deps.workspaceRepository.workspace.catch { emit(WorkspaceState.Failed("unavailable")) } }
    val workspace by workspaceFlow.collectAsState(WorkspaceState.Loading)
    val hidden = customizations.orEmpty().filterValues { it.hidden }.keys
    val hiddenOwners = hidden.mapTo(HashSet()) { it.packageName to it.userSerial }
    val items = (workspace as? WorkspaceState.Ready)?.workspace?.items.orEmpty()
    val cards = settings.cards.cards
    val choices by produceState<CardValidation?>(null, apps, customizations, items, cards, lockState) {
        value = null
        if (apps != null && customizations != null) {
            val requested = cardValidationRequest(apps.orEmpty(), items, cards)
            value = try {
                withContext(Dispatchers.IO) { validator.validate(requested, apps.orEmpty(), hidden, items) }
            } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
        }
    }
    val personalApps = if (customizations == null || choices == null) null
        else cardPersonalApps(choices?.targets, hidden, apps.orEmpty())
    val keys = personalApps.orEmpty().mapTo(HashSet()) { it.key }
    val widgets = if (customizations == null) emptyList() else items.mapNotNull { item ->
        val label = cardWidgetLabel(item, choices, hiddenOwners)
        if (label == null) null
        else CardWidgetOption(item.id, stringResource(R.string.cards_widget_name, label, item.placement.page + 1))
    }
    // Overlay current raw visibility immediately, while a new native validation is pending.
    // A stale result must never keep a now-hidden/locked title or removed widget visible.
    val visibleTargets = if (customizations == null) null else choices?.targets?.let {
        cardVisibleTargets(it, personalApps.orEmpty(), hidden, items, deps.appLock::mayRead)
    }
    fun editable(card: AutomationCard): Boolean = cardEditorTitle(card, visibleTargets) != null
    var editing by remember { mutableStateOf<AutomationCard?>(null) }
    var editingExisting by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }
    var removed by remember { mutableStateOf<RemovedCard?>(null) }

    fun persistCards(
        transform: (List<AutomationCard>) -> List<AutomationCard>?,
        validate: suspend () -> Boolean = { true },
        onSuccess: () -> Unit = {},
        onFailure: () -> Unit = {},
    ) {
        if (saving) return
        saving = true
        saveFailed = false
        scope.launch {
            val accepted = try {
                deps.appScope.async {
                    persistCardMutation(deps.settingsRepository, transform, validate)
                }.await()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) { false }
            saving = false
            if (accepted) onSuccess() else {
                saveFailed = true
                onFailure()
            }
        }
    }

    val snackbar = remember { SnackbarHostState() }
    val deletedMessage = stringResource(R.string.cards_deleted)
    val undoLabel = stringResource(R.string.cards_undo)
    // Order shown while a drag-and-drop move is being written; cleared once settings catch up.
    var optimisticOrder by remember { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(cards) { optimisticOrder = null }
    val shown = cardsInShownOrder(cards, optimisticOrder)

    fun delete(card: AutomationCard, onFailure: () -> Unit = {}) {
        var deleted: RemovedCard? = null
        persistCards({ current ->
            val position = current.indexOfFirst { it.id == card.id }
            if (position < 0) null else {
                deleted = RemovedCard(current[position], position)
                current.filterNot { it.id == card.id }
            }
        }, onFailure = onFailure, onSuccess = {
            removed = deleted
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                if (snackbar.showSnackbar(deletedMessage, undoLabel, duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) {
                    removed?.let { undo ->
                        persistCards({ current -> restoreRemovedCard(current, undo).takeIf { it != current } }, onSuccess = { removed = null })
                    }
                }
            }
        })
    }

    // "Add card" first offers starting points; the chosen template only pre-fills an unsaved draft.
    var choosingTemplate by remember { mutableStateOf(false) }
    fun addCard() {
        if (!saving && cards.size < CardsSettings.MAX_CARDS) choosingTemplate = true
    }
    fun startCard(template: CardTemplate) {
        choosingTemplate = false
        if (!saving && cards.size < CardsSettings.MAX_CARDS) {
            editing = cardTemplateDraft(template, UUID.randomUUID().toString(),
                template.defaultTitle?.let(context::getString).orEmpty())
            editingExisting = false
            saveFailed = false
        }
    }

    Box(Modifier.fillMaxSize()) {
        val pageScroll = rememberScrollState()
        val autoScroll = remember(pageScroll) { ListAutoScroll(pageScroll) }
        Column(Modifier.fillMaxSize().onGloballyPositioned { autoScroll.viewport = it }.verticalScroll(pageScroll)
            .padding(top = 4.dp, bottom = if (cards.isEmpty()) 96.dp else 112.dp)) {
            if (cards.isEmpty()) CardsOnboarding() else CardsIntro()
            CardReorderList(
                cards = shown,
                enabled = !saving,
                key = { it.id },
                autoScroll = autoScroll,
                onMove = { card, to ->
                    val from = shown.indexOfFirst { it.id == card.id }
                    if (from >= 0 && from != to) {
                        optimisticOrder = moveCardTo(shown, card.id, to).map { it.id }
                        persistCards({ current -> moveCardTo(current, card.id, to).takeIf { it != current } },
                            onFailure = { optimisticOrder = null })
                    }
                },
            ) { index, card, handle ->
                CardSwipeRow(
                    card = card,
                    title = cardEditorTitle(card, visibleTargets) ?: stringResource(R.string.cards_unavailable),
                    canEdit = editable(card),
                    saving = saving,
                    handle = handle,
                    canMoveUp = index > 0,
                    canMoveDown = index < shown.lastIndex,
                    onSwipeDelete = { dismiss -> delete(card, onFailure = { scope.launch { dismiss.reset() } }) },
                    onOpen = {
                        editing = card
                        editingExisting = true
                        saveFailed = false
                    },
                    onToggle = { on ->
                        persistCards({ current ->
                            current.map { if (it.id == card.id) it.copy(enabled = on) else it }.takeIf { it != current }
                        })
                    },
                    onMove = { delta ->
                        val moved = moveCard(shown, card.id, delta)
                        if (moved != shown) optimisticOrder = moved.map { it.id }
                        persistCards({ current -> moveCard(current, card.id, delta).takeIf { it != current } },
                            onFailure = { optimisticOrder = null })
                    },
                    onDelete = { delete(card) },
                )
            }
            if (cards.size > 1) SettingsInfoText(stringResource(R.string.cards_list_swipe_hint))
            if (cards.size >= CardsSettings.MAX_CARDS) SettingsInfoText(stringResource(R.string.cards_limit))
            if (saveFailed && editing == null) SettingsInfoText(stringResource(R.string.cards_save_failed))
        }
        Column(Modifier.align(Alignment.BottomEnd).fillMaxWidth(), horizontalAlignment = Alignment.End) {
            // The onboarding has its own create button; one primary action per screen.
            if (cards.isNotEmpty()) CardsAddButton(
                onClick = ::addCard,
                enabled = !saving && cards.size < CardsSettings.MAX_CARDS,
                modifier = Modifier.padding(16.dp),
            )
            SnackbarHost(snackbar)
            if (cards.isEmpty()) CardsCreateButton(onCreate = ::addCard, enabled = !saving)
        }
    }

    if (choosingTemplate) CardTemplateDialog(onPick = ::startCard, onDismiss = { choosingTemplate = false })

    editing?.let { original ->
        if (editingExisting && !editable(original)) {
            CardUnavailableDialog(saving, onDismiss = { if (!saving) editing = null }, onDone = { editing = null })
            return@let
        }
        CardEditorDialog(original, personalApps, widgets, saving, saveFailed, isNew = !editingExisting,
            widgetCatalogSource = widgetCatalogSource,
            onDismiss = { editing = null }, onSave = { draft ->
            if (!cardTargetsAvailable(draft, keys, widgets.mapTo(HashSet()) { it.itemId })) {
                saveFailed = true
            } else {
                val wasExisting = editingExisting
                persistCards({ current -> saveCardDraft(current, draft, requireExisting = wasExisting) },
                    validate = { validateDraftAgainstFreshSnapshot(draft, validator, deps) },
                    onSuccess = { editing = null })
            }
        })
    }
}

/** The target-validation request: one probe per installed app, per placed widget, plus the saved cards. */
internal fun cardValidationRequest(apps: List<LauncherApp>, items: List<WorkspaceItem>, cards: List<AutomationCard>): List<AutomationCard> =
    apps.mapIndexed { index, app -> AutomationCard("app-choice-$index", "", featured = CardFeatured.App(app.key)) } +
        items.filter { it.target is ItemTarget.Widget }.map { AutomationCard("widget-choice", "", featured = CardFeatured.Widget(it.id)) } + cards

/** Visible personal-profile apps that validation approved and that are still installed and not hidden. */
internal fun cardPersonalApps(targets: CardTargetSnapshot?, hidden: Set<AppKey>, currentApps: List<LauncherApp>): List<LauncherApp>? =
    targets?.apps?.filter {
        it.profileType == ProfileType.PERSONAL && it.key.userSerial == targets.personalUserSerial &&
            it.key !in hidden && currentApps.any { current -> current.key == it.key }
    }

/** The widget's label when validation resolved it to the same provider and its owner is not hidden; else null. */
internal fun cardWidgetLabel(item: WorkspaceItem, choices: CardValidation?, hiddenOwners: Set<Pair<String, Long>>): String? {
    val target = item.target as? ItemTarget.Widget ?: return null
    val owner = target.provider.packageName to target.provider.userSerial
    val label = choices?.widgetLabels?.get(item.id)
    return if (label == null || choices?.targets?.widgets?.get(item.id) != target.provider || owner in hiddenOwners) null else label
}

/** Validation result narrowed by the live hidden/locked/placed state, so a stale result cannot leak. */
internal fun cardVisibleTargets(
    validated: CardTargetSnapshot, personalApps: List<LauncherApp>, hidden: Set<AppKey>, items: List<WorkspaceItem>,
    mayRead: (packageName: String, userSerial: Long) -> Boolean,
): CardTargetSnapshot = validated.copy(
    apps = personalApps, hidden = hidden,
    shortcuts = validated.shortcuts.filterTo(mutableSetOf()) { mayRead(it.packageName, it.userSerial) },
    widgets = validated.widgets.filter { (id, provider) ->
        (items.firstOrNull { it.id == id }?.target as? ItemTarget.Widget)?.provider == provider
    },
)

/** The saved order, or the optimistic drag order while it is a permutation of the saved cards. */
internal fun cardsInShownOrder(cards: List<AutomationCard>, optimisticOrder: List<String>?): List<AutomationCard> =
    optimisticOrder?.let { order ->
        if (order.toSet() == cards.mapTo(HashSet()) { it.id }) order.mapNotNull { id -> cards.firstOrNull { it.id == id } } else null
    } ?: cards

/** Re-reads apps, customizations and workspace and checks the draft's targets against that fresh snapshot. */
private suspend fun validateDraftAgainstFreshSnapshot(
    draft: AutomationCard, validator: AndroidCardsTargetValidator, deps: HomeDependencies,
): Boolean = withContext(Dispatchers.IO) {
    validateCardDraftSnapshot(readSnapshot = {
        readCardEditorSnapshot(deps.appsRepository.apps,
            deps.appCustomizationRepository.customizations, deps.workspaceRepository.workspace)
    }, validate = { snapshot ->
        val validated = validator.validate(listOf(draft), snapshot.apps,
            snapshot.customizations.filterValues { it.hidden }.keys, snapshot.items)
        CardVisibility.targetsAllowed(draft, validated.targets)
    })
}

/** One reorderable list row: swipe-to-delete around the card row. */
@Composable
private fun CardSwipeRow(
    // The drag handle's modifier from the reorder list, not the row's own `modifier`.
    card: AutomationCard, title: String, canEdit: Boolean, saving: Boolean, @Suppress("ModifierParameter") handle: Modifier,
    canMoveUp: Boolean, canMoveDown: Boolean,
    onSwipeDelete: (SwipeToDismissBoxState) -> Unit,
    onOpen: () -> Unit, onToggle: (Boolean) -> Unit, onMove: (Int) -> Unit, onDelete: () -> Unit,
) {
    val dismiss = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = dismiss,
        enableDismissFromStartToEnd = false,
        gesturesEnabled = !saving,
        onDismiss = { onSwipeDelete(dismiss) },
        backgroundContent = { DeleteBackground() },
    ) {
        CardListRow(
            card = card,
            title = title,
            canEdit = canEdit,
            enabled = !saving,
            handle = handle,
            onOpen = onOpen,
            onToggle = onToggle,
            canMoveUp = canMoveUp,
            canMoveDown = canMoveDown,
            onMove = onMove,
            onDelete = onDelete,
        )
    }
}

@Composable
private fun CardUnavailableDialog(saving: Boolean, onDismiss: () -> Unit, onDone: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cards_unavailable)) },
        text = { Text(stringResource(R.string.cards_unavailable_summary)) },
        confirmButton = { TextButton(enabled = !saving, onClick = onDone) { Text(stringResource(R.string.cards_done)) } })
}

/** Keep the name on the actual action, even when Material3 suppresses its text-row semantics. */
@Composable
internal fun CardsAddButton(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.cards_add)
    ExtendedFloatingActionButton(
        onClick = { if (enabled) onClick() },
        icon = { Icon(painterResource(R.drawable.ic_action_add), null) },
        text = { Text(label) },
        expanded = true,
        // EFAB has no enabled parameter. State must be exposed as well as visually dimmed, and
        // even an accessibility service invoking the remaining click action cannot activate it.
        modifier = modifier.semantics {
            contentDescription = label
            if (!enabled) disabled()
        }.alpha(if (enabled) 1f else 0.38f),
    )
}
