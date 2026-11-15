package io.github.aj4abinjacob.chikku.settings.iconpicker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.AppSearcher
import io.github.aj4abinjacob.chikku.core.model.LauncherApp
import io.github.aj4abinjacob.chikku.platform.icons.IconPackInfo
import io.github.aj4abinjacob.chikku.platform.icons.IconStyle
import io.github.aj4abinjacob.chikku.settings.components.SettingsFastScroller
import io.github.aj4abinjacob.chikku.settings.components.rememberAppScrollIndex
import io.github.aj4abinjacob.chikku.settings.components.settingsPageColor

/*
 * The picker's three steps. Each takes plain data and callbacks; every platform read goes through
 * [IconPickerLoader] off the main thread. App and pack labels are shown, never logged.
 */

private fun appCacheKey(key: AppKey) = "app|${key.packageName}/${key.className}#${key.userSerial}"

// --- Step 1: choose an app (only when opened without a target) -----------------------------------

@Composable
internal fun AppStep(
    apps: List<LauncherApp>?,
    searcher: AppSearcher,
    loader: IconPickerLoader,
    cache: IconBitmapCache,
    onPick: (AppKey) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val visible = remember(apps, query) {
        when {
            apps == null -> null
            query.isBlank() -> apps
            else -> searcher.search(apps, query)
        }
    }
    val listState = rememberLazyListState()
    // Letters only for the alphabetical list; search results are in relevance order.
    val scrollIndex = rememberAppScrollIndex(if (query.isBlank()) visible.orEmpty() else emptyList(), start = 1) { it.label }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(start = PagePadding, end = PagePadding, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(GroupGap),
        ) {
            // Pinned while the apps scroll under it, so filtering stays one tap away on a long list.
            stickyHeader(key = "search", contentType = "search") {
                PickerSearchField(
                    value = query,
                    onValueChange = { query = it },
                    hint = stringResource(R.string.icon_picker_search_apps),
                    modifier = Modifier.background(settingsPageColor()).padding(bottom = 14.dp),
                )
            }
            when {
                visible == null -> items(PLACEHOLDER_ROWS) { index ->
                    PlaceholderRow(groupShape(index, PLACEHOLDER_ROWS))
                }
                visible.isEmpty() -> item(key = "none") { EmptyText(stringResource(R.string.icon_picker_no_apps)) }
                else -> itemsIndexed(visible, key = { _, app -> appCacheKey(app.key) }, contentType = { _, _ -> "app" }) { index, app ->
                    val icon = rememberIconBitmap(appCacheKey(app.key), ListIconSize, cache) { loader.currentIcon(app.key) }
                    TonalRow(
                        title = app.label,
                        shape = groupShape(index, visible.size),
                        onClick = { onPick(app.key) },
                        leading = { IconImage(icon, ListIconSize) },
                        trailing = { Chevron() },
                    )
                }
            }
        }
        SettingsFastScroller(listState, sections = scrollIndex.sections, letterAt = scrollIndex::letterAt)
    }
}

// --- Step 2: default icon or an icon pack ---------------------------------------------------------

@Composable
internal fun PackStep(
    target: IconTarget,
    appLabel: String,
    current: CurrentOverride?,
    /** The global icon style; null until settings have loaded. */
    style: IconStyle?,
    loader: IconPickerLoader,
    cache: IconBitmapCache,
    onDefault: () -> Unit,
    onPack: (IconPackInfo) -> Unit,
) {
    val installed by rememberInstalledPacks(loader)
    val packs = installed
    var query by rememberSaveable { mutableStateOf("") }
    val searching = query.isNotBlank()
    val visible = remember(packs, query) { packs?.let { filterPacks(it, query) } }
    val listState = rememberLazyListState()
    // Header, search, default label, default row and packs label sit above the first pack row.
    val scrollIndex = rememberAppScrollIndex(if (searching) emptyList() else visible.orEmpty(), start = if (searching) 1 else 5) { it.label }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(start = PagePadding, end = PagePadding, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(GroupGap),
        ) {
            if (!searching) item(key = "header") { TargetHeader(target, appLabel, current != null, loader, cache) }
            // Pinned once scrolled to, so a pack is one search away even with dozens installed.
            stickyHeader(key = "search", contentType = "search") {
                PickerSearchField(
                    value = query,
                    onValueChange = { query = it },
                    hint = stringResource(R.string.icon_pack_chooser_search),
                    modifier = Modifier.background(settingsPageColor()).padding(vertical = 8.dp),
                )
            }
            if (!searching) {
                item(key = "default-label") { SectionLabel(stringResource(R.string.icon_picker_section_default)) }
                item(key = "default") { DefaultRow(target, current, style, loader, cache, onDefault) }
                item(key = "packs-label") {
                    SectionLabel(
                        stringResource(R.string.icon_picker_section_packs),
                        trailing = packs?.takeIf { it.isNotEmpty() }?.size?.toString(),
                    )
                }
            }
            when {
                visible == null -> items(PACK_PLACEHOLDER_ROWS) { index ->
                    PlaceholderRow(groupShape(index, PACK_PLACEHOLDER_ROWS))
                }
                packs.isNullOrEmpty() -> item(key = "empty") { EmptyPacksCard() }
                visible.isEmpty() -> item(key = "no-match") { EmptyText(stringResource(R.string.icon_pack_chooser_no_matches)) }
                else -> itemsIndexed(visible, key = { _, pack -> "pack|${pack.packageName}" }) { index, pack ->
                    PackRow(
                        pack = pack,
                        target = target,
                        appLabel = appLabel,
                        inUse = current?.packPackage == pack.packageName,
                        shape = groupShape(index, visible.size),
                        loader = loader,
                        cache = cache,
                        onClick = { onPack(pack) },
                    )
                }
            }
        }
        SettingsFastScroller(listState, sections = scrollIndex.sections, letterAt = scrollIndex::letterAt)
    }
}

/** The app being changed: its icon as the launcher shows it now, its name and what this screen does. */
@Composable
private fun TargetHeader(target: IconTarget, appLabel: String, custom: Boolean, loader: IconPickerLoader, cache: IconBitmapCache) {
    // Keyed by the override state so the header refreshes after returning from a change.
    val icon = rememberIconBitmap("current|$custom|" + target.cacheKey, HeaderIconSize, cache) { loader.currentIcon(target) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(OuterCorner))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = HEADER_TINT_ALPHA))
            .padding(horizontal = 20.dp, vertical = 20.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconImage(icon, HeaderIconSize)
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                appLabel,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(if (target is IconTarget.Shortcut) R.string.icon_picker_header_subtitle_shortcut else R.string.icon_picker_header_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Pill(
                stringResource(if (custom) R.string.icon_picker_state_custom else R.string.icon_picker_state_default),
                emphasized = custom,
            )
        }
    }
}

@Composable
private fun DefaultRow(
    target: IconTarget,
    current: CurrentOverride?,
    style: IconStyle?,
    loader: IconPickerLoader,
    cache: IconBitmapCache,
    onClick: () -> Unit,
) {
    val selected = current == null
    val sizePx = with(LocalDensity.current) { ListIconSize.roundToPx() }
    // Without an override the launcher's current icon is exactly the default; otherwise rebuild it from the global style.
    val icon = rememberIconBitmap("default|$selected|$style|" + target.cacheKey, ListIconSize, cache) {
        when {
            selected -> loader.currentIcon(target)
            style != null -> loader.defaultIcon(target, style, sizePx)
            else -> null
        }
    }
    val selectedText = stringResource(R.string.icon_picker_selected)
    TonalRow(
        title = stringResource(R.string.icon_picker_default_icon),
        summary = stringResource(if (target is IconTarget.Shortcut) R.string.icon_picker_default_summary_shortcut else R.string.icon_picker_default_summary),
        shape = groupShape(0, 1),
        onClick = onClick,
        modifier = Modifier.semantics { if (selected) stateDescription = selectedText },
        leading = { IconImage(icon, ListIconSize) },
        trailing = { if (selected) SelectedCheck(Modifier.padding(start = 12.dp)) },
    )
}

@Composable
private fun PackRow(
    pack: IconPackInfo,
    target: IconTarget,
    appLabel: String,
    inUse: Boolean,
    shape: androidx.compose.ui.graphics.Shape,
    loader: IconPickerLoader,
    cache: IconBitmapCache,
    onClick: () -> Unit,
) {
    val packIcon = rememberIconBitmap("packapp|${pack.packageName}", ListIconSize, cache) { loader.packAppIcon(pack.packageName) }
    val preview = rememberIconBitmap("packfor|${pack.packageName}|" + target.cacheKey, PreviewIconSize, cache) {
        loader.packIconFor(pack.packageName, target)
    }
    val hasMatch = preview.bitmap != null
    TonalRow(
        title = pack.label,
        summary = if (hasMatch) {
            stringResource(R.string.icon_picker_pack_has_match, appLabel)
        } else {
            stringResource(R.string.icon_picker_pack_browse)
        },
        shape = shape,
        onClick = onClick,
        leading = { IconImage(packIcon, ListIconSize) },
        trailing = {
            if (inUse) Pill(stringResource(R.string.icon_picker_pack_in_use), emphasized = true, modifier = Modifier.padding(start = 8.dp))
            if (!preview.done || hasMatch) {
                // This app as the pack would draw it, on a small tile.
                Box(
                    Modifier
                        .padding(start = 8.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .padding(6.dp),
                ) { IconImage(preview, PreviewIconSize) }
            }
            Chevron()
        },
    )
}

/** Shown when nothing on the device declares itself an icon pack. No store links: Chikku stays offline. */
@Composable
internal fun EmptyPacksCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(OuterCorner))
            .background(tonalColor())
            .padding(24.dp)
            .semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = HEADER_TINT_ALPHA)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_settings_palette),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.icon_picker_empty_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.icon_picker_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

// --- Step 3: an icon from the chosen pack ---------------------------------------------------------

@Composable
internal fun IconStep(
    target: IconTarget,
    appLabel: String,
    packPackage: String,
    current: CurrentOverride?,
    loader: IconPickerLoader,
    cache: IconBitmapCache,
    onPick: (String) -> Unit,
) {
    val content by produceState<PackContent?>(null, packPackage, target) { value = loader.packContent(packPackage, target) }
    var query by rememberSaveable { mutableStateOf("") }
    val chosen = current?.takeIf { it.packPackage == packPackage }?.drawableName
    Column(Modifier.fillMaxSize()) {
        PickerSearchField(
            value = query,
            onValueChange = { query = it },
            hint = stringResource(R.string.icon_picker_search),
            modifier = Modifier.padding(start = PagePadding, end = PagePadding, top = 8.dp, bottom = 4.dp),
        )
        val loaded = content
        val pack = loaded?.pack
        when {
            loaded == null -> PlaceholderGrid()
            pack == null || (loaded.names.isEmpty() && loaded.suggested == null) ->
                EmptyText(stringResource(R.string.icon_picker_pack_empty), Modifier.padding(horizontal = PagePadding))
            else -> {
                val visible = remember(loaded.names, query) { filterIconNames(loaded.names, query) }
                val suggested = loaded.suggested?.takeIf { filterIconNames(listOf(it), query).isNotEmpty() }
                val gridState = rememberLazyGridState()
                Box(Modifier.fillMaxSize()) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(GridCellMinWidth),
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (suggested != null) {
                            item(key = "suggested-label", span = { GridItemSpan(maxLineSpan) }) {
                                SectionLabel(stringResource(R.string.icon_picker_suggested))
                            }
                            item(key = "suggested", span = { GridItemSpan(maxLineSpan) }) {
                                SuggestedCard(
                                    name = suggested,
                                    appLabel = appLabel,
                                    selected = suggested == chosen,
                                    load = { loader.packDrawable(pack, suggested) },
                                    cacheKey = "icon|$packPackage|$suggested",
                                    cache = cache,
                                    onClick = { onPick(suggested) },
                                )
                            }
                        }
                        item(key = "all-label", span = { GridItemSpan(maxLineSpan) }) {
                            SectionLabel(
                                stringResource(R.string.icon_picker_all_icons),
                                trailing = pluralStringResource(R.plurals.icon_picker_icon_count, visible.size, visible.size),
                            )
                        }
                        if (visible.isEmpty()) {
                            item(key = "no-matches", span = { GridItemSpan(maxLineSpan) }) {
                                EmptyText(stringResource(R.string.icon_picker_no_matches))
                            }
                        }
                        items(visible, key = { "i|$it" }, contentType = { "icon" }) { name ->
                            IconCell(
                                name = name,
                                selected = name == chosen,
                                cacheKey = "icon|$packPackage|$name",
                                cache = cache,
                                load = { loader.packDrawable(pack, name) },
                                onClick = { onPick(name) },
                            )
                        }
                    }
                    // Packs hold thousands of icons; the drawer's thumb crosses them in one drag.
                    SettingsFastScroller(gridState)
                }
            }
        }
    }
}

/** The pack's own icon for this app, given pride of place above the full grid. */
@Composable
private fun SuggestedCard(
    name: String,
    appLabel: String,
    selected: Boolean,
    load: suspend () -> android.graphics.drawable.Drawable?,
    cacheKey: String,
    cache: IconBitmapCache,
    onClick: () -> Unit,
) {
    val icon = rememberIconBitmap(cacheKey, HeaderIconSize, cache, load)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .clip(RoundedCornerShape(OuterCorner))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(OuterCorner)) else Modifier)
            .selectable(selected = selected, role = Role.Button, onClick = onClick)
            .heightIn(min = 88.dp)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconImage(icon, HeaderIconSize)
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                iconLabel(name),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(R.string.icon_picker_suggested_summary, appLabel),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = SECONDARY_TEXT_ALPHA),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) SelectedCheck(Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun IconCell(
    name: String,
    selected: Boolean,
    cacheKey: String,
    cache: IconBitmapCache,
    load: suspend () -> android.graphics.drawable.Drawable?,
    onClick: () -> Unit,
) {
    val icon = rememberIconBitmap(cacheKey, GridIconSize, cache, load)
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .selectable(selected = selected, role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconImage(icon, GridIconSize)
        Spacer(Modifier.height(6.dp))
        Text(
            iconLabel(name),
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Shape of the grid while a pack opens, so the screen never sits blank. */
@Composable
private fun PlaceholderGrid() {
    val loading = stringResource(R.string.icon_picker_loading)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(GridCellMinWidth),
        modifier = Modifier.fillMaxSize().semantics { contentDescription = loading },
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 52.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        userScrollEnabled = false,
    ) {
        items(PLACEHOLDER_CELLS) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PlaceholderBlock(Modifier.size(GridIconSize).padding(GridIconSize / 10), RoundedCornerShape(GridIconSize / 3))
                Spacer(Modifier.height(8.dp))
                PlaceholderBlock(Modifier.width(48.dp).height(10.dp), RoundedCornerShape(5.dp))
            }
        }
    }
}

@Composable
internal fun EmptyText(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp, horizontal = 8.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

private val ListIconSize = 40.dp
private val PreviewIconSize = 32.dp
private val HeaderIconSize = 64.dp
private val GridIconSize = 52.dp
private val GridCellMinWidth = 84.dp

private const val PLACEHOLDER_ROWS = 8
private const val PACK_PLACEHOLDER_ROWS = 3
private const val PLACEHOLDER_CELLS = 24
private const val HEADER_TINT_ALPHA = 0.18f
private const val SECONDARY_TEXT_ALPHA = 0.8f
