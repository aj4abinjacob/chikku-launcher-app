package io.github.aj4abinjacob.chikku.settings.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.GestureAction
import io.github.aj4abinjacob.chikku.core.model.LauncherApp
import io.github.aj4abinjacob.chikku.core.model.cards.AutomationCard
import io.github.aj4abinjacob.chikku.core.model.cards.CardConditions
import io.github.aj4abinjacob.chikku.core.model.cards.CardContent
import io.github.aj4abinjacob.chikku.core.model.cards.CardFeatured
import io.github.aj4abinjacob.chikku.core.model.cards.CardTimeRange
import io.github.aj4abinjacob.chikku.core.model.cards.CardsSettings
import io.github.aj4abinjacob.chikku.home.HomeDependencies
import io.github.aj4abinjacob.chikku.home.cards.cardActionGlyph
import io.github.aj4abinjacob.chikku.home.cards.cardClock
import io.github.aj4abinjacob.chikku.home.cards.cardConditionSummary
import io.github.aj4abinjacob.chikku.home.cards.cardContentGlyph
import io.github.aj4abinjacob.chikku.home.cards.widget.CardWidgetCatalogSource
import io.github.aj4abinjacob.chikku.settings.LocalHomeDependencies
import io.github.aj4abinjacob.chikku.settings.cards.weather.WeatherCardEditorControls
import io.github.aj4abinjacob.chikku.settings.cards.widget.CardWidgetEditorControls
import io.github.aj4abinjacob.chikku.settings.components.ChoiceSheetOptions
import io.github.aj4abinjacob.chikku.settings.components.SettingsBottomSheet
import io.github.aj4abinjacob.chikku.settings.components.SettingsInfoText
import io.github.aj4abinjacob.chikku.settings.components.SheetPadding
import io.github.aj4abinjacob.chikku.settings.components.SettingsNavigationRow
import io.github.aj4abinjacob.chikku.settings.components.SettingsSwitchRow
import io.github.aj4abinjacob.chikku.settings.components.gestureActionLabel
import java.time.DayOfWeek
import java.time.format.TextStyle

internal data class CardWidgetOption(val itemId: String, val label: String)

/** Picker targets: the featured item, a new action, or the action at an index of the compact list. */
private sealed interface PickerTarget {
    data object Featured : PickerTarget
    data class Action(val index: Int) : PickerTarget
}

/**
 * Full-screen card editor with a live preview. All edits stay in this draft until Save;
 * closing never modifies stored cards, and asks first when there are unsaved changes.
 *
 * All editor state lives here; the section composables below receive values and callbacks only.
 * [onEdit]-style callbacks take a transform of the *current* draft, so an edit never works from
 * a stale copy.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun CardEditorDialog(
    original: AutomationCard,
    personalApps: List<LauncherApp>?,
    widgets: List<CardWidgetOption>,
    saving: Boolean,
    saveFailed: Boolean,
    onSave: (AutomationCard) -> Unit,
    onDismiss: () -> Unit,
    isNew: Boolean = false,
    widgetCatalogSource: CardWidgetCatalogSource? = null,
) {
    val deps = LocalHomeDependencies.current
    val context = LocalContext.current
    var draft by remember(original.id) { mutableStateOf(original) }
    val contentDrafts = remember(original.id) { mutableMapOf(cardContentKind(original.content) to original.content) }
    var timeEnabled by remember(original.id) { mutableStateOf(original.conditions.timeRange != null) }
    var startMinute by remember(original.id) { mutableIntStateOf(original.conditions.timeRange?.startMinute ?: 480) }
    var endMinute by remember(original.id) { mutableIntStateOf(original.conditions.timeRange?.endMinute ?: 1080) }
    var picker by remember(original.id) { mutableStateOf<PickerTarget?>(null) }
    var chooseFeatured by remember { mutableStateOf(false) }
    var chooseWidget by remember { mutableStateOf(false) }
    var editingTime by remember { mutableStateOf<Boolean?>(null) } // true = start, false = end
    var confirmDiscard by remember { mutableStateOf(false) }
    var previewExpanded by remember(original.id) { mutableStateOf(true) }
    var previewSample by remember(original.id) { mutableStateOf(CardPreviewSample()) }
    var editingSampleTime by remember(original.id) { mutableStateOf(false) }
    val keys = personalApps.orEmpty().mapTo(HashSet()) { it.key }
    val widgetIds = widgets.mapTo(HashSet()) { it.itemId }
    val titleValid = cardTitleValid(draft.title)
    val targetValid = cardTargetsAvailable(draft, keys, widgetIds)
    val actions = draft.actions.filter { it != GestureAction.None }
    fun setActions(list: List<GestureAction>) {
        draft = draft.copy(actions = padCardActions(list))
    }
    val result = draft.copy(conditions = draft.conditions.copy(
        timeRange = if (timeEnabled) CardTimeRange(startMinute, endMinute) else null))
    val dirty = result != original.copy(actions = padCardActions(original.actions.filter { it != GestureAction.None }))
    fun requestClose() {
        if (saving) return
        if (dirty) confirmDiscard = true else onDismiss()
    }
    val featuredLabel = when (val item = draft.featured) {
        CardFeatured.None -> null
        is CardFeatured.App -> gestureActionLabel(GestureAction.LaunchApp(item.key), personalApps)
        is CardFeatured.Shortcut -> gestureActionLabel(
            GestureAction.LaunchShortcut(item.packageName, item.shortcutId, item.userSerial), personalApps)
        is CardFeatured.Widget -> widgets.firstOrNull { it.itemId == item.itemId }?.label
            ?: stringResource(R.string.cards_widget_unavailable)
    }
    val onEdit: ((AutomationCard) -> AutomationCard) -> Unit = { transform -> draft = transform(draft) }

    Dialog(onDismissRequest = ::requestClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                CardEditorTopBar(isNew, saving, canSave = titleValid && targetValid,
                    onClose = ::requestClose, onSave = { onSave(result) })
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val pinnedHeight = (maxHeight * 0.4f).coerceAtMost(240.dp)
                    Column(Modifier.fillMaxSize()) {
                        TextButton(onClick = { previewExpanded = !previewExpanded },
                            modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp)) {
                            Text(stringResource(if (previewExpanded) R.string.preview_cards_hide else R.string.preview_cards_show))
                        }
                        if (previewExpanded) Column(
                            Modifier.fillMaxWidth().height(pinnedHeight)
                                .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                        ) {
                            CardPreview(result, previewSample,
                                cardConditionSummary(context, result.conditions))
                        }
                        Column(
                            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp).padding(bottom = 32.dp),
                        ) {
                            CardTitleSection(draft.title, draft.enabled, titleValid, saving, onEdit)
                            CardContentSection(
                                content = draft.content, saving = saving, deps = deps,
                                widgetCatalogSource = widgetCatalogSource,
                                onKindSelected = { kind ->
                                    contentDrafts[cardContentKind(draft.content)] = draft.content
                                    draft = draft.copy(content = contentDrafts.getOrPut(kind) { kind.defaultContent() })
                                },
                                onContentChange = { draft = draft.copy(content = it) },
                            )
                            CardFeaturedSection(
                                featured = draft.featured, featuredLabel = featuredLabel, saving = saving,
                                menuExpanded = chooseFeatured,
                                onOpenMenu = { chooseFeatured = true },
                                onDismissMenu = { chooseFeatured = false },
                                onChooseApp = { chooseFeatured = false; picker = PickerTarget.Featured },
                                onChooseWidget = { chooseFeatured = false; chooseWidget = true },
                                onClear = { draft = draft.copy(featured = CardFeatured.None) },
                            )
                            CardActionsSection(
                                actions = actions, personalApps = personalApps, saving = saving,
                                onChange = { picker = PickerTarget.Action(it) },
                                onRemove = { index -> setActions(actions.filterIndexed { i, _ -> i != index }) },
                                onAdd = { picker = PickerTarget.Action(actions.size) },
                            )
                            CardConditionsSection(
                                conditions = draft.conditions, timeEnabled = timeEnabled,
                                startMinute = startMinute, endMinute = endMinute, saving = saving,
                                onConditionsChange = { transform -> draft = draft.copy(conditions = transform(draft.conditions)) },
                                onTimeEnabledChange = { timeEnabled = it },
                                onEditTime = { editingTime = it },
                            )
                            SectionTitle(stringResource(R.string.preview_card_simulation_title))
                            Group {
                                CardConditionPreviewControls(previewSample, { previewSample = it },
                                    { editingSampleTime = true })
                            }
                            if (!targetValid) SettingsInfoText(stringResource(R.string.cards_target_error))
                            if (saveFailed) SettingsInfoText(stringResource(R.string.cards_save_failed))
                        }
                    }
                }
            }
        }

        picker?.let { target ->
            CardPickerSheet(target, draft.featured, actions, personalApps,
                onDismiss = { picker = null }, onEdit = onEdit, onSetActions = ::setActions)
        }
        if (chooseWidget) {
            // A ModalBottomSheet opened from this Dialog's composition gets its own window stacked
            // above the editor (as the action picker's sheet does); dismissing it returns here.
            SettingsBottomSheet(stringResource(R.string.cards_featured_widget), onDismiss = { chooseWidget = false }) { close ->
                FeaturedWidgetList(widgets, (draft.featured as? CardFeatured.Widget)?.itemId) { widget ->
                    draft = draft.copy(featured = CardFeatured.Widget(widget.itemId))
                    close()
                }
            }
        }
        editingTime?.let { start ->
            CardTimePickerDialog(
                initialMinute = if (start) startMinute else endMinute,
                title = stringResource(if (start) R.string.cards_time_from else R.string.cards_time_to),
                onDismiss = { editingTime = null },
                onConfirm = { minute -> if (start) startMinute = minute else endMinute = minute; editingTime = null },
            )
        }
        if (editingSampleTime) {
            CardTimePickerDialog(previewSample.minute, stringResource(R.string.preview_card_sample_time),
                onDismiss = { editingSampleTime = false },
                onConfirm = { previewSample = previewSample.copy(minute = it); editingSampleTime = false })
        }
        if (confirmDiscard) {
            AlertDialog(onDismissRequest = { confirmDiscard = false },
                title = { Text(stringResource(R.string.cards_discard_title)) },
                text = { Text(stringResource(R.string.cards_discard_message)) },
                confirmButton = { TextButton(onClick = { confirmDiscard = false; onDismiss() }) { Text(stringResource(R.string.cards_discard)) } },
                dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.cards_keep_editing)) } })
        }
    }
}

/** A card name is 1..MAX_TITLE_LENGTH characters once trimmed, with no control characters. */
internal fun cardTitleValid(title: String): Boolean = title.trim().let {
    it.isNotEmpty() && it.length <= CardsSettings.MAX_TITLE_LENGTH && it.none { character -> character.isISOControl() }
}

/** Pads a compact action list with [GestureAction.None] up to the fixed stored slot count. */
internal fun padCardActions(list: List<GestureAction>): List<GestureAction> =
    list + List(CardsSettings.ACTION_COUNT - list.size) { GestureAction.None }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CardEditorTopBar(isNew: Boolean, saving: Boolean, canSave: Boolean, onClose: () -> Unit, onSave: () -> Unit) {
    TopAppBar(
        title = { Text(stringResource(if (isNew) R.string.cards_new else R.string.cards_edit)) },
        navigationIcon = {
            IconButton(onClick = onClose, enabled = !saving) {
                Icon(painterResource(R.drawable.ic_search_clear), stringResource(R.string.settings_cancel))
            }
        },
        actions = {
            Button(
                onClick = onSave,
                enabled = !saving && canSave,
                modifier = Modifier.padding(end = 12.dp),
            ) {
                if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary)
                else Text(stringResource(R.string.cards_save))
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
    )
}

/** Name field and the enabled switch. */
@Composable
private fun CardTitleSection(
    title: String, enabled: Boolean, titleValid: Boolean, saving: Boolean,
    onEdit: ((AutomationCard) -> AutomationCard) -> Unit,
) {
    OutlinedTextField(
        value = title,
        onValueChange = { value -> onEdit { it.copy(title = value.take(CardsSettings.MAX_TITLE_LENGTH)) } },
        label = { Text(stringResource(R.string.cards_name)) },
        modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !saving,
        isError = title.isNotEmpty() && !titleValid,
        supportingText = if (title.isNotEmpty() && !titleValid) {
            { Text(stringResource(R.string.cards_name_error)) }
        } else null,
        shape = RoundedCornerShape(16.dp),
    )
    Group(Modifier.padding(top = 8.dp)) {
        SettingsSwitchRow(stringResource(R.string.cards_card_enabled), enabled,
            { value -> onEdit { it.copy(enabled = value) } }, enabled = !saving)
    }
}

/** Content kind chips and the controls of the selected kind. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardContentSection(
    content: CardContent, saving: Boolean, deps: HomeDependencies, widgetCatalogSource: CardWidgetCatalogSource?,
    onKindSelected: (CardContentKind) -> Unit, onContentChange: (CardContent) -> Unit,
) {
    SectionTitle(stringResource(R.string.cards_section_content))
    Group {
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CardContentKind.entries.forEach { kind ->
                val selected = cardContentKind(content) == kind
                FilterChip(
                    selected = selected,
                    enabled = !saving,
                    onClick = { onKindSelected(kind) },
                    label = { Text(stringResource(kind.title)) },
                    leadingIcon = {
                        Icon(painterResource(cardContentGlyph(kind.defaultContent())), null,
                            Modifier.size(FilterChipDefaults.IconSize))
                    },
                    shape = CircleShape,
                )
            }
        }
        Column {
            when (content) {
                CardContent.Actions -> Unit
                is CardContent.Weather -> WeatherCardEditorControls(content, {
                    onContentChange(it)
                }, deps.cardWeatherSource, enabled = !saving)
                CardContent.Media -> MediaCardEditorControls(deps.cardMediaSource, enabled = !saving)
                is CardContent.Widget -> CardWidgetEditorControls(content, {
                    onContentChange(it)
                }, deps, enabled = !saving, source = widgetCatalogSource)
            }
        }
    }
}

/** The featured item row with its add/remove affordances and the app-or-widget menu. */
@Composable
private fun CardFeaturedSection(
    featured: CardFeatured, featuredLabel: String?, saving: Boolean, menuExpanded: Boolean,
    onOpenMenu: () -> Unit, onDismissMenu: () -> Unit, onChooseApp: () -> Unit, onChooseWidget: () -> Unit,
    onClear: () -> Unit,
) {
    SectionTitle(stringResource(R.string.cards_section_featured), stringResource(R.string.cards_featured_hint))
    Group {
        Box {
            if (featured == CardFeatured.None) {
                SettingsNavigationRow(stringResource(R.string.cards_featured_add), onOpenMenu,
                    enabled = !saving, leading = { AddBadge() })
            } else {
                SettingsNavigationRow(featuredLabel.orEmpty(), onOpenMenu, enabled = !saving,
                    leading = { FeaturedIcon(featured) },
                    trailing = {
                        IconButton(onClick = onClear, enabled = !saving) {
                            Icon(painterResource(R.drawable.ic_search_clear),
                                stringResource(R.string.cards_featured_remove), Modifier.size(20.dp))
                        }
                    })
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = onDismissMenu) {
                DropdownMenuItem(text = { Text(stringResource(R.string.cards_featured_app_shortcut)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_picker_app), null) },
                    onClick = onChooseApp)
                DropdownMenuItem(text = { Text(stringResource(R.string.cards_featured_widget)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_menu_widgets), null) },
                    onClick = onChooseWidget)
            }
        }
    }
}

/** The compact action tiles; each tile owns its overflow menu state, as it always did. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardActionsSection(
    actions: List<GestureAction>, personalApps: List<LauncherApp>?, saving: Boolean,
    onChange: (Int) -> Unit, onRemove: (Int) -> Unit, onAdd: () -> Unit,
) {
    SectionTitle(stringResource(R.string.cards_section_actions), stringResource(R.string.cards_actions_hint))
    Group {
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            maxItemsInEachRow = 4,
        ) {
            actions.forEachIndexed { index, action ->
                CardActionTileWithMenu(index, action, personalApps, saving, Modifier.weight(1f),
                    onChange = { onChange(index) }, onRemove = { onRemove(index) })
            }
            if (actions.size < CardsSettings.ACTION_COUNT) {
                Box(Modifier.weight(1f)) {
                    ActionTile(stringResource(R.string.cards_action_add),
                        stringResource(R.string.cards_action_add_description), enabled = !saving,
                        onClick = onAdd) { AddBadge(48.dp) }
                }
            }
            // Pad the last row so tiles keep their width.
            repeat(cardActionRowPadding(actions.size)) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/** Filler cells that keep the last row of action tiles (four per row, plus the add tile) at full width. */
internal fun cardActionRowPadding(actionCount: Int): Int {
    val tiles = actionCount + if (actionCount < CardsSettings.ACTION_COUNT) 1 else 0
    return (4 - tiles % 4) % 4
}

@Composable
private fun CardActionTileWithMenu(
    index: Int, action: GestureAction, personalApps: List<LauncherApp>?, saving: Boolean, modifier: Modifier,
    onChange: () -> Unit, onRemove: () -> Unit,
) {
    val label = gestureActionLabel(action, personalApps)
    var menu by remember { mutableStateOf(false) }
    Box(modifier) {
        ActionTile(label, stringResource(R.string.cards_action_menu, index + 1, label),
            enabled = !saving, onClick = { menu = true }) {
            CardActionIcon(action, 48.dp)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.cards_action_change)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_menu_edit), null) },
                onClick = { menu = false; onChange() })
            DropdownMenuItem(text = { Text(stringResource(R.string.cards_action_remove)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_menu_remove), null) },
                onClick = { menu = false; onRemove() })
        }
    }
}

/** Weekday chips, the optional time window, and the device-state conditions. */
@Composable
private fun CardConditionsSection(
    conditions: CardConditions,
    timeEnabled: Boolean, startMinute: Int, endMinute: Int, saving: Boolean,
    onConditionsChange: ((CardConditions) -> CardConditions) -> Unit,
    onTimeEnabledChange: (Boolean) -> Unit,
    onEditTime: (start: Boolean) -> Unit,
) {
    val context = LocalContext.current
    SectionTitle(stringResource(R.string.cards_section_when), stringResource(R.string.cards_conditions_summary))
    Group {
        DayChips(conditions.weekdays, enabled = !saving) { days ->
            onConditionsChange { it.copy(weekdays = days) }
        }
        SettingsSwitchRow(stringResource(R.string.cards_time_enabled), timeEnabled, onTimeEnabledChange,
            enabled = !saving)
        if (timeEnabled) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TimeButton(stringResource(R.string.cards_time_from), cardClock(context, startMinute),
                    stringResource(R.string.cards_time_pick_start, cardClock(context, startMinute)),
                    !saving, Modifier.weight(1f)) { onEditTime(true) }
                TimeButton(stringResource(R.string.cards_time_to), cardClock(context, endMinute),
                    stringResource(R.string.cards_time_pick_end, cardClock(context, endMinute)),
                    !saving, Modifier.weight(1f)) { onEditTime(false) }
            }
            SettingsInfoText(stringResource(R.string.cards_time_hint))
        }
        SettingsSwitchRow(stringResource(R.string.cards_charging_yes), conditions.chargingOnly,
            { value -> onConditionsChange { it.copy(chargingOnly = value) } }, enabled = !saving)
        SettingsSwitchRow(stringResource(R.string.cards_wifi_yes), conditions.wifiOnly,
            { value -> onConditionsChange { it.copy(wifiOnly = value) } },
            summary = stringResource(R.string.cards_wifi_summary), enabled = !saving)
        CardBluetoothConditionControl(conditions.bluetoothConnectedOnly, { value ->
            onConditionsChange { it.copy(bluetoothConnectedOnly = value) }
        }, enabled = !saving)
    }
}

/** What the picker sheet is currently set to, for the featured item or one compact action slot. */
internal fun cardPickerCurrent(isFeatured: Boolean, featured: CardFeatured, actions: List<GestureAction>, actionIndex: Int): GestureAction =
    if (isFeatured) when (featured) {
        is CardFeatured.App -> GestureAction.LaunchApp(featured.key)
        is CardFeatured.Shortcut -> GestureAction.LaunchShortcut(featured.packageName, featured.shortcutId, featured.userSerial)
        else -> GestureAction.None
    } else actions.getOrElse(actionIndex) { GestureAction.None }

/** The compact action list after picking [picked] for slot [index] (None removes it, past the end appends). */
internal fun cardActionsAfterPick(actions: List<GestureAction>, index: Int, picked: GestureAction): List<GestureAction> = when {
    picked == GestureAction.None -> actions.filterIndexed { i, _ -> i != index }
    index >= actions.size -> actions + picked
    else -> actions.mapIndexed { i, old -> if (i == index) picked else old }
}

@Composable
private fun CardPickerSheet(
    target: PickerTarget, featured: CardFeatured, actions: List<GestureAction>, personalApps: List<LauncherApp>?,
    onDismiss: () -> Unit, onEdit: ((AutomationCard) -> AutomationCard) -> Unit,
    onSetActions: (List<GestureAction>) -> Unit,
) {
    val current = cardPickerCurrent(
        isFeatured = target == PickerTarget.Featured, featured = featured, actions = actions,
        actionIndex = (target as? PickerTarget.Action)?.index ?: 0,
    )
    CardActionPickerSheet(
        title = stringResource(when {
            target == PickerTarget.Featured -> R.string.cards_featured
            target is PickerTarget.Action && target.index < actions.size -> R.string.cards_picker_change
            else -> R.string.cards_action_add_description
        }),
        current = current.takeIf { it != GestureAction.None },
        apps = personalApps,
        actions = if (target == PickerTarget.Featured) emptyList() else cardActionChoices,
        onDismiss = onDismiss,
        onPick = { action ->
            when (target) {
                PickerTarget.Featured -> onEdit { it.copy(featured = when (action) {
                    is GestureAction.LaunchApp -> CardFeatured.App(action.key)
                    is GestureAction.LaunchShortcut -> CardFeatured.Shortcut(action.packageName, action.shortcutId, action.userSerial)
                    else -> CardFeatured.None
                }) }
                is PickerTarget.Action -> onSetActions(cardActionsAfterPick(actions, target.index, action))
            }
        },
    )
}

/** What the card will look like on Home, updated as the user edits. */
@Composable
private fun CardPreview(
    card: AutomationCard,
    sample: CardPreviewSample,
    subtitle: String?,
) {
    val colors = MaterialTheme.colorScheme
    val visibility = cardPreviewVisibility(card, sample)
    val status = stringResource(visibility.message)
    val featuredDescription = stringResource(cardPreviewFeaturedMessage(card.featured))
    val featuredLabel = featuredDescription.takeUnless { card.featured == CardFeatured.None }
    val actions = card.actions.filter { it != GestureAction.None }
    val contentDescription = when (val content = card.content) {
        CardContent.Actions -> stringResource(R.string.card_content_actions)
        is CardContent.Weather -> if (content.onlineEnabled && content.location != null) {
            stringResource(if (content.unit == io.github.aj4abinjacob.chikku.core.model.cards.WeatherUnit.CELSIUS)
                R.string.preview_cards_weather_celsius else R.string.preview_cards_weather_fahrenheit)
        } else stringResource(cardPreviewWeatherMessage(content))
        CardContent.Media -> stringResource(R.string.preview_cards_sample_track)
        is CardContent.Widget -> stringResource(R.string.preview_card_widget_description,
            stringResource(R.string.preview_cards_widget_height, content.heightDp),
            stringResource(cardPreviewWidgetMessage(content)))
    }
    val description = stringResource(R.string.preview_card_description, status,
        subtitle ?: stringResource(R.string.preview_card_no_conditions), contentDescription, actions.size,
        featuredDescription)
    Column(Modifier.fillMaxWidth().padding(top = 4.dp).clearAndSetSemantics {
        this.contentDescription = description
    }) {
        Text(stringResource(R.string.cards_preview), style = MaterialTheme.typography.labelLarge,
            color = colors.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        Text(status, style = MaterialTheme.typography.bodyMedium, color = colors.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        if (visibility != CardPreviewVisibility.VISIBLE) {
            Box(Modifier.fillMaxWidth().heightIn(min = 120.dp).clip(RoundedCornerShape(28.dp))
                .background(colors.surfaceContainerHigh).padding(16.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.preview_card_hidden_space), style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant)
            }
            return@Column
        }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(colors.surfaceContainerHigh)
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.preview_card_sample_title),
                        style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        color = if (card.title.isBlank()) colors.onSurfaceVariant else colors.onSurface)
                    if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (card.content != CardContent.Actions) CardContentLivePreview(card.content)
            if (featuredLabel != null) {
                Row(
                    Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp))
                        .background(colors.surfaceContainerHighest).padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SampleFeaturedIcon(card.featured)
                    Spacer(Modifier.width(12.dp))
                    Text(featuredLabel, style = MaterialTheme.typography.titleSmall, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Icon(painterResource(R.drawable.ic_settings_chevron), null, tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(18.dp))
                }
            }
            if (actions.isNotEmpty()) {
                // Same layout as the Home panel: equal slots, at least four, icon over a one-line name.
                Row(Modifier.padding(top = 12.dp).fillMaxWidth()) {
                    actions.forEach { action ->
                        val label = when (action) {
                            is GestureAction.LaunchApp -> stringResource(R.string.preview_card_sample_app)
                            is GestureAction.LaunchShortcut -> stringResource(R.string.preview_card_sample_shortcut)
                            else -> gestureActionLabel(action, null)
                        }
                        Column(Modifier.weight(1f).padding(horizontal = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            SampleCardActionIcon(action, 44.dp)
                            Text(label, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                    repeat((4 - actions.size).coerceAtLeast(0)) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun FeaturedIcon(featured: CardFeatured) {
    when (featured) {
        is CardFeatured.App -> CardActionIcon(GestureAction.LaunchApp(featured.key), 36.dp)
        is CardFeatured.Shortcut -> CardActionIcon(
            GestureAction.LaunchShortcut(featured.packageName, featured.shortcutId, featured.userSerial), 36.dp)
        is CardFeatured.Widget -> Box(Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_menu_widgets), null, tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(18.dp))
        }
        CardFeatured.None -> AddBadge()
    }
}

@Composable
private fun AddBadge(size: androidx.compose.ui.unit.Dp = 36.dp) {
    Box(
        Modifier.size(size).clip(CircleShape)
            .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_action_add), null, tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
private fun ActionTile(label: String, description: String, enabled: Boolean, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(vertical = 8.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        icon()
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp))
    }
}

/** Seven round day toggles; none selected means every day. */
@Composable
private fun DayChips(selected: Set<Int>, enabled: Boolean, onChange: (Set<Int>) -> Unit) {
    val locale = LocalResources.current.configuration.locales[0]
    val colors = MaterialTheme.colorScheme
    val on = stringResource(R.string.cards_day_selected)
    val off = stringResource(R.string.cards_day_not_selected)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(stringResource(R.string.cards_days), style = MaterialTheme.typography.bodyLarge)
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..7).forEach { day ->
                val checked = day in selected
                val name = DayOfWeek.of(day).getDisplayName(TextStyle.FULL, locale)
                val description = stringResource(R.string.cards_day_chip, name, if (checked) on else off)
                Box(
                    Modifier.weight(1f).aspectRatio(1f).widthIn(max = 48.dp).clip(CircleShape)
                        .background(if (checked) colors.primary else colors.surfaceContainerHighest)
                        .toggleable(checked, enabled = enabled, role = Role.Checkbox) { now ->
                            onChange(if (now) selected + day else selected - day)
                        }
                        .semantics { contentDescription = description },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(DayOfWeek.of(day).getDisplayName(TextStyle.NARROW, locale),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (checked) colors.onPrimary else colors.onSurfaceVariant)
                }
            }
        }
        Text(
            if (selected.isEmpty() || selected.size == 7) stringResource(R.string.cards_every_day)
            else selected.sorted().joinToString(", ") { DayOfWeek.of(it).getDisplayName(TextStyle.SHORT, locale) },
            style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun TimeButton(label: String, value: String, description: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_action_schedule), null, Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CardTimePickerDialog(initialMinute: Int, title: String, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val context = LocalContext.current
    val state = rememberTimePickerState(initialMinute / 60, initialMinute % 60,
        is24Hour = android.text.format.DateFormat.is24HourFormat(context))
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp))
                TimePicker(state)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) }
                    TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text(stringResource(R.string.cards_ok)) }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, hint: String? = null) {
    Column(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 24.dp, bottom = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() })
        if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A rounded tonal container holding a group of rows. */
@Composable
private fun Group(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainer)) {
        content()
    }
}

internal enum class CardContentKind(val title: Int) {
    ACTIONS(R.string.card_content_actions), WEATHER(R.string.card_content_weather),
    MEDIA(R.string.card_content_media), WIDGET(R.string.card_content_widget);
    fun defaultContent(): CardContent = when (this) {
        ACTIONS -> CardContent.Actions
        WEATHER -> CardContent.Weather()
        MEDIA -> CardContent.Media
        WIDGET -> CardContent.Widget()
    }
}
internal fun cardContentKind(content: CardContent): CardContentKind = when (content) {
    CardContent.Actions -> CardContentKind.ACTIONS
    is CardContent.Weather -> CardContentKind.WEATHER
    CardContent.Media -> CardContentKind.MEDIA
    is CardContent.Widget -> CardContentKind.WIDGET
}

/** Chikku glyphs only; this preview never consults an app or shortcut icon loader. */
@Composable
private fun SampleCardActionIcon(action: GestureAction, size: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center) {
        Icon(painterResource(cardActionGlyph(action)), null, Modifier.size(size * 0.46f),
            tint = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun SampleFeaturedIcon(featured: CardFeatured) {
    val glyph = when (featured) {
        is CardFeatured.App -> R.drawable.ic_app_placeholder
        is CardFeatured.Shortcut -> R.drawable.ic_app_placeholder
        is CardFeatured.Widget -> R.drawable.ic_menu_widgets
        CardFeatured.None -> R.drawable.ic_action_add
    }
    Box(Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center) {
        Icon(painterResource(glyph), null, Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

/**
 * The featured-widget choice of the card editor: an explanation, then each widget placed on Home
 * (current one marked and in view), or a note when there are none. Synthetic in tests.
 */
@Composable
internal fun ColumnScope.FeaturedWidgetList(
    widgets: List<CardWidgetOption>,
    chosen: String?,
    onPick: (CardWidgetOption) -> Unit,
) {
    // SettingsInfoText pads itself 16dp for settings pages; 8dp more puts its icon on SheetPadding.
    SettingsInfoText(stringResource(R.string.cards_widget_reference_summary), Modifier.padding(horizontal = SheetPadding - 16.dp))
    if (widgets.isEmpty()) {
        Text(
            stringResource(R.string.cards_no_widgets),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = SheetPadding, vertical = 8.dp),
        )
        return
    }
    ChoiceSheetOptions(
        options = widgets,
        selected = widgets.firstOrNull { it.itemId == chosen },
        optionLabel = { it?.label.orEmpty() },
        onPick = { it?.let(onPick) },
        optionIcon = {
            Icon(painterResource(R.drawable.ic_menu_widgets), contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        },
    )
}
