package io.github.aj4abinjacob.chikku.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberCoroutineScope
import io.github.aj4abinjacob.chikku.theme.LocalMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.AppSearcher
import io.github.aj4abinjacob.chikku.core.model.LauncherApp
import io.github.aj4abinjacob.chikku.settings.LocalHomeDependencies
import io.github.aj4abinjacob.chikku.settings.iconpicker.IconBitmapCache
import io.github.aj4abinjacob.chikku.settings.iconpicker.IconImage
import io.github.aj4abinjacob.chikku.settings.iconpicker.PickerSearchField
import io.github.aj4abinjacob.chikku.settings.iconpicker.rememberIconBitmap

/*
 * One app list for every place that picks from installed apps (gesture targets, swipe actions,
 * drawer groups, folder actions). Hundreds of apps stay usable: a search field pinned once scrolled
 * to, app icons, a lettered fast scroller, and the current or checked apps pinned above the full
 * list so they never have to be hunted for. App labels are shown, never logged.
 */

/** How a row shows and changes its state. */
enum class AppPickerStyle {
    /** Pick one app; the current one has a filled radio button. */
    RADIO,

    /** Toggle membership; checked apps have a ticked box. */
    CHECKBOX,

    /** Open something for the app; a chevron, no selection mark. */
    NAVIGATION,
}

/**
 * Searchable app list. [isSelected] apps are marked and, with [pinnedTitle], also listed under it
 * above the full list while not searching. [header] adds items above the search field;
 * [headerCount] must be how many it adds, so the fast scroller lands on the right row. Without
 * a header, search stays outside the scrolling rows so filtering cannot recycle focused input.
 * Give the list a bounded height in a dialog. [inset] is the
 * horizontal padding of rows, labels and (unless [searchInset] differs) search (wider on a full
 * settings page). By default the list fills the height it is given; with [fillHeight] false it
 * hugs its rows instead, up to that height, and keeps its unfiltered height while searching (for
 * sheets, which should leave no gap below a short list).
 */
@Composable
fun AppPickerList(
    apps: List<LauncherApp>?,
    isSelected: (LauncherApp) -> Boolean,
    onClick: (LauncherApp) -> Unit,
    modifier: Modifier = Modifier,
    style: AppPickerStyle = AppPickerStyle.RADIO,
    pinnedTitle: String? = null,
    allTitle: String? = null,
    label: @Composable (LauncherApp) -> String = { it.label },
    summary: (@Composable (LauncherApp) -> String?)? = null,
    searchBackground: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    emptyText: String? = null,
    contentPadding: PaddingValues = settingsFastScrollerPadding(),
    inset: Dp = 0.dp,
    searchInset: Dp = inset,
    fillHeight: Boolean = true,
    headerCount: Int = 0,
    header: LazyListScope.() -> Unit = {},
) {
    val deps = LocalHomeDependencies.current
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(apps, query) { apps?.let { filterApps(deps.appSearcher, it, query) } }
    val searching = query.isNotBlank()
    // A tap shows its result at once; the stored selection catches up (or the mark returns).
    val pending = remember { mutableStateMapOf<AppKey, Boolean>() }
    val scope = rememberCoroutineScope()
    val motion = LocalMotion.current
    val selected: (LauncherApp) -> Boolean = { pending[it.key] ?: isSelected(it) }
    val tapped: (LauncherApp) -> Unit = { app ->
        val next = !selected(app)
        pending[app.key] = next
        scope.launch {
            delay(OPTIMISTIC_MS)
            if (pending[app.key] == next) pending.remove(app.key)
        }
        onClick(app)
    }
    val pinned = if (searching || pinnedTitle == null) emptyList() else apps.orEmpty().filter(selected)
    val cache = rememberAppIconCache()

    // header, search, pinned label + rows, all-apps label
    val pinnedItems = if (pinned.isEmpty()) 0 else 1 + pinned.size
    val searchInList = headerCount > 0
    val listStart = headerCount + (if (searchInList) 1 else 0) + pinnedItems + if (!searching && allTitle != null && pinned.isNotEmpty()) 1 else 0
    val listState = rememberLazyListState()
    val scrollIndex = rememberAppScrollIndex(if (searching) emptyList() else shown.orEmpty(), start = listStart) { it.label }
    val unfilteredHeight = rememberUnfilteredHeight()

    Column(if (fillHeight) modifier else modifier.keepUnfilteredHeight(unfilteredHeight, searching)) {
        // Keep focused text input outside the lazy items as search changes their count and keys.
        if (!searchInList) {
            AppSearchField(query, { query = it }, searchBackground, Modifier.padding(horizontal = searchInset))
        }
        Box(Modifier.weight(1f, fill = fillHeight)) {
            LazyColumn(
                modifier = if (fillHeight) Modifier.fillMaxSize() else Modifier.fillMaxWidth(),
                state = listState,
                contentPadding = contentPadding,
            ) {
                header()
                if (searchInList) {
                    stickyHeader(key = "app-search", contentType = "search") {
                        AppSearchField(query, { query = it }, searchBackground, Modifier.padding(horizontal = searchInset))
                    }
                }
                if (pinned.isNotEmpty()) {
                    item(key = "pinned-label") { Box(animateItemWith(motion)) { ListLabel(pinnedTitle.orEmpty(), inset) } }
                    items(pinned, key = { "pinned|" + it.key.listKey() }, contentType = { "app" }) { app ->
                        Box(animateItemWith(motion)) {
                            AppPickerRow(app.key, label(app), summary?.invoke(app), true, style, cache, inset) { tapped(app) }
                        }
                    }
                    if (allTitle != null) item(key = "all-label") { Box(animateItemWith(motion)) { ListLabel(allTitle, inset) } }
                }
                when {
                    shown == null -> item(key = "loading") { InfoLine(stringResource(R.string.gesture_picker_loading), inset) }
                    apps.isNullOrEmpty() -> item(key = "empty") { InfoLine(emptyText ?: stringResource(R.string.gesture_picker_no_apps), inset) }
                    shown.isEmpty() -> item(key = "no-match") { InfoLine(stringResource(R.string.app_picker_no_matches), inset) }
                    else -> items(shown, key = { it.key.listKey() }, contentType = { "app" }) { app ->
                        Box(animateItemWith(motion)) {
                            AppPickerRow(app.key, label(app), summary?.invoke(app), selected(app), style, cache, inset) { tapped(app) }
                        }
                    }
                }
            }
            SettingsFastScroller(listState, sections = scrollIndex.sections, letterAt = scrollIndex::letterAt)
        }
    }
}

/** Search results in relevance order; a blank query keeps [apps] as they are (alphabetical). */
internal fun filterApps(searcher: AppSearcher, apps: List<LauncherApp>, query: String): List<LauncherApp> =
    if (query.isBlank()) apps else searcher.search(apps, query.trim())

/** Icon cache for one app list; bounded so scrolling hundreds of apps cannot grow it without limit. */
@Composable
internal fun rememberAppIconCache(): IconBitmapCache = remember { IconBitmapCache(ICON_CACHE_BYTES) }

/** The app lists' search field, on [background] so rows scrolling under it stay hidden. */
@Composable
internal fun AppSearchField(query: String, onQueryChange: (String) -> Unit, background: Color, modifier: Modifier = Modifier) {
    PickerSearchField(
        value = query,
        onValueChange = onQueryChange,
        hint = stringResource(R.string.app_picker_search),
        fill = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.background(background).then(modifier).padding(vertical = 8.dp),
    )
}

/** One app row: its launcher icon, [title], optional [summary], then the [style]'s mark. */
@Composable
internal fun AppPickerRow(
    appKey: AppKey,
    title: String,
    summary: String?,
    selected: Boolean,
    style: AppPickerStyle,
    cache: IconBitmapCache,
    inset: Dp = 0.dp,
    onClick: () -> Unit,
) {
    val deps = LocalHomeDependencies.current
    val revision by deps.iconProvider.iconRevision.collectAsState()
    val icon = rememberIconBitmap("app|" + appKey.listKey() + "|" + revision, AppIconSize, cache) { deps.iconProvider.icon(appKey) }
    val action = when (style) {
        AppPickerStyle.RADIO -> Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
        AppPickerStyle.CHECKBOX -> Modifier.toggleable(value = selected, role = Role.Checkbox) { onClick() }
        AppPickerStyle.NAVIGATION -> Modifier.selectable(selected = false, role = Role.Button, onClick = onClick)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(action)
            .padding(horizontal = inset + AppPickerRowPadding, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconImage(icon, AppIconSize)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (summary != null) {
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        when (style) {
            AppPickerStyle.RADIO -> RadioButton(selected = selected, onClick = null)
            AppPickerStyle.CHECKBOX -> Checkbox(checked = selected, onCheckedChange = null)
            AppPickerStyle.NAVIGATION -> Icon(
                painterResource(R.drawable.ic_settings_chevron),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ListLabel(text: String, inset: Dp) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = inset + AppPickerRowPadding, top = 12.dp, bottom = 4.dp).semantics { heading() },
    )
}

@Composable
private fun InfoLine(text: String, inset: Dp) {
    Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = inset + AppPickerRowPadding, vertical = 12.dp))
}

private val AppIconSize: Dp = 36.dp

/** Side padding app rows, labels and info lines add to their inset. */
internal val AppPickerRowPadding: Dp = 8.dp
private const val ICON_CACHE_BYTES = 6 * 1024 * 1024

private const val OPTIMISTIC_MS = 900L
