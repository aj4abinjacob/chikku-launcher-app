package io.github.aj4abinjacob.chikku.settings.cards.widget

import io.github.aj4abinjacob.chikku.platform.icons.IconShaper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.saveable.rememberSaveable
import io.github.aj4abinjacob.chikku.core.model.ComponentKey
import io.github.aj4abinjacob.chikku.settings.components.rememberAppIconCache
import io.github.aj4abinjacob.chikku.settings.iconpicker.IconBitmapCache
import io.github.aj4abinjacob.chikku.settings.iconpicker.IconImage
import io.github.aj4abinjacob.chikku.settings.iconpicker.PickerSearchField
import io.github.aj4abinjacob.chikku.settings.iconpicker.rememberIconBitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.AppCustomization
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.cards.CardContent
import io.github.aj4abinjacob.chikku.home.HomeDependencies
import io.github.aj4abinjacob.chikku.home.cards.widget.AndroidCardWidgetCatalogSource
import io.github.aj4abinjacob.chikku.home.cards.widget.CardWidgetCatalogSource
import io.github.aj4abinjacob.chikku.home.cards.widget.CardWidgetProviderOption
import io.github.aj4abinjacob.chikku.settings.components.GestureActionRow
import io.github.aj4abinjacob.chikku.settings.components.GestureActionSectionLabel
import io.github.aj4abinjacob.chikku.settings.components.SettingsBottomSheet
import io.github.aj4abinjacob.chikku.settings.components.SettingsFastScroller
import io.github.aj4abinjacob.chikku.settings.components.SheetListPadding
import io.github.aj4abinjacob.chikku.settings.components.SheetPadding
import io.github.aj4abinjacob.chikku.settings.components.keepUnfilteredHeight
import io.github.aj4abinjacob.chikku.settings.components.rememberUnfilteredHeight
import io.github.aj4abinjacob.chikku.settings.components.SettingsInfoText
import io.github.aj4abinjacob.chikku.settings.components.SettingsNavigationRow
import io.github.aj4abinjacob.chikku.settings.components.SettingsSliderRow
import io.github.aj4abinjacob.chikku.settings.components.settingsFastScrollerPadding
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Selection changes only the editor draft; widget allocation/consent occurs in the saved card. */
@Composable
fun CardWidgetEditorControls(
    content: CardContent.Widget,
    onChange: (CardContent.Widget) -> Unit,
    deps: HomeDependencies,
    enabled: Boolean = true,
    source: CardWidgetCatalogSource? = null,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val catalog = remember(context, source) { source ?: AndroidCardWidgetCatalogSource(context) }
    val customizations: Map<AppKey, AppCustomization>? by deps.appCustomizationRepository.customizations.collectAsState(null)
    val apps by deps.appsRepository.apps.collectAsState(null)
    val hidden = customizations?.values?.filter { it.hidden }?.mapTo(mutableSetOf()) { it.key }
    val options by produceState<List<CardWidgetProviderOption>?>(null, catalog, hidden, apps) {
        value = null
        if (hidden != null) value = withContext(Dispatchers.IO) { catalog.load(hidden) }
    }
    val visible = options?.filter { option -> hidden?.none {
        it.packageName == option.key.packageName && it.userSerial == option.key.userSerial
    } == true }
    var picking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column {
        SettingsNavigationRow(stringResource(R.string.card_widget_choose), { picking = true },
            summary = when {
                options == null -> stringResource(R.string.card_widget_catalog_loading)
                content.provider == null -> stringResource(R.string.card_widget_choose_summary)
                else -> visible?.firstOrNull { it.key == content.provider }?.label ?: stringResource(R.string.card_widget_provider_missing)
            }, enabled = enabled && visible != null)
        SettingsSliderRow(title = stringResource(R.string.card_widget_height), value = content.heightDp.toFloat(),
            valueRange = 96f..600f, valueLabel = { resources.getString(R.string.card_widget_height_value, it.roundToInt()) },
            onValueChangeFinished = { onChange(content.copy(heightDp = it.roundToInt().coerceIn(96, 600))) }, enabled = enabled,
            onValueChanging = { onChange(content.copy(heightDp = it.roundToInt().coerceIn(96, 600))) })
        SettingsInfoText(stringResource(R.string.card_widget_separate_summary))
    }
    if (picking) {
        WidgetProviderDialog(
            options = visible.orEmpty(),
            current = content.provider,
            enabled = enabled,
            onDismiss = { picking = false },
            onPick = { option ->
                scope.launch {
                    val stillAvailable = withContext(Dispatchers.IO) {
                        val latestHidden = deps.appCustomizationRepository.customizations.first().values
                            .filter { it.hidden }.mapTo(mutableSetOf()) { it.key }
                        catalog.load(latestHidden).any { it.key == option.key }
                    }
                    if (stillAvailable) onChange(content.copy(provider = option.key,
                        instanceId = if (option.key == content.provider) content.instanceId else UUID.randomUUID().toString()))
                    picking = false
                }
            },
        )
    }
}

/**
 * Every installed widget provider, often well over a hundred, in a settings bottom sheet: searchable
 * by widget or app name, grouped under each app with its icon, and the current widget pinned at the
 * top. The search field stays above the list (and above the keyboard); only the list scrolls.
 */
@Composable
private fun WidgetProviderDialog(
    options: List<CardWidgetProviderOption>,
    current: ComponentKey?,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onPick: (CardWidgetProviderOption) -> Unit,
) {
    SettingsBottomSheet(stringResource(R.string.card_widget_choose), onDismiss) { close ->
        WidgetProviderList(options, current) { option ->
            if (enabled) {
                onPick(option)
                close()
            }
        }
    }
}

/** Search field and grouped list of [WidgetProviderDialog], without the sheet window (for tests). */
@Composable
internal fun ColumnScope.WidgetProviderList(
    options: List<CardWidgetProviderOption>,
    current: ComponentKey?,
    onPick: (CardWidgetProviderOption) -> Unit,
) {
    if (options.isEmpty()) {
        Text(
            stringResource(R.string.card_widget_catalog_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = SheetPadding, vertical = 8.dp),
        )
        return
    }
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(options, query) { filterWidgetOptions(options, query) }
    val selected = options.firstOrNull { it.key == current }?.takeIf { query.isBlank() }
    val cache = rememberAppIconCache()
    val unfilteredHeight = rememberUnfilteredHeight()
    // The sheet's height cap includes the keyboard, so the list shrinks above it and the field stays
    // visible. It hugs its rows, but keeps its unfiltered height while searching, so it does not jump.
    Column(
        Modifier.weight(1f, fill = false).fillMaxWidth().imePadding().keepUnfilteredHeight(unfilteredHeight, query.isNotBlank()),
    ) {
        PickerSearchField(
            value = query,
            onValueChange = { query = it },
            hint = stringResource(R.string.card_widget_search),
            fill = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.padding(start = SheetListPadding, end = SheetListPadding, bottom = 8.dp),
        )
        Box(Modifier.weight(1f, fill = false).fillMaxWidth()) {
            val listState = rememberLazyListState()
            LazyColumn(
                Modifier.fillMaxWidth(),
                state = listState,
                contentPadding = settingsFastScrollerPadding(otherSide = SheetListPadding),
            ) {
                if (selected != null) {
                    item(key = "current-label") { GestureActionSectionLabel(stringResource(R.string.gesture_picker_current)) }
                    item(key = "current") { WidgetRow(selected, true, cache, showApp = true) { onPick(selected) } }
                }
                if (shown.isEmpty()) {
                    item(key = "no-match") {
                        Text(
                            stringResource(R.string.card_widget_no_matches),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
                shown.groupBy { it.appLabel }.forEach { (app, widgets) ->
                    item(key = "app|${widgets.first().key.packageName}|$app") { GestureActionSectionLabel(app) }
                    items(widgets, key = { "${it.key.userSerial}/${it.key.packageName}/${it.key.className}" }) { option ->
                        WidgetRow(option, option.key == current, cache, showApp = false) { onPick(option) }
                    }
                }
            }
            SettingsFastScroller(listState)
        }
    }
}

/** Widgets whose own name or app name contains [query], ignoring case; blank keeps all. */
internal fun filterWidgetOptions(options: List<CardWidgetProviderOption>, query: String): List<CardWidgetProviderOption> {
    val q = query.trim()
    if (q.isEmpty()) return options
    return options.filter { it.label.contains(q, ignoreCase = true) || it.appLabel.contains(q, ignoreCase = true) }
}

/** One widget as a [GestureActionRow]: its app's icon, name, and (when [showApp]) the app's name. */
@Composable
private fun WidgetRow(
    option: CardWidgetProviderOption,
    selected: Boolean,
    cache: IconBitmapCache,
    showApp: Boolean,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val icon = rememberIconBitmap("widgetapp|${option.key.packageName}", WidgetIconSize, cache) {
        withContext(Dispatchers.IO) { runCatching { IconShaper.systemSafe(context.packageManager.getApplicationIcon(option.key.packageName)) }.getOrNull() }
    }
    GestureActionRow(
        text = option.label,
        selected = selected,
        supporting = option.appLabel.takeIf { showApp && it.isNotEmpty() },
        leading = { IconImage(icon, WidgetIconSize) },
        onClick = onClick,
    )
}

/** Fits the 28dp leading slot of [GestureActionRow]. */
private val WidgetIconSize = 28.dp
