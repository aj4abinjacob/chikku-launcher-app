package io.github.aj4abinjacob.chikku.settings.iconpicker

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.DisposableEffectResult
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.platform.icons.IconPackInfo
import io.github.aj4abinjacob.chikku.settings.components.SettingsFastScroller
import io.github.aj4abinjacob.chikku.settings.components.rememberAppScrollIndex
import io.github.aj4abinjacob.chikku.settings.components.settingsPageColor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * The launcher-wide icon pack, chosen from a full-screen list that stays usable with dozens of packs:
 * search, an A-Z fast scroller, the current choice pinned at the top, and a strip of each pack's own
 * icons so packs can be told apart without applying them. Pack labels are shown, never logged.
 */

/**
 * The installed icon packs, listed again whenever an app is installed, updated or removed while the
 * list is open. The previous list stays shown until the new one arrives.
 */
@Composable
internal fun rememberInstalledPacks(loader: IconPickerLoader): State<List<IconPackInfo>?> {
    val context = LocalContext.current
    var generation by remember { mutableIntStateOf(0) }
    DisposableEffect(context) { onPackagesChanged(context) { generation++ } }
    return produceState<List<IconPackInfo>?>(null, loader, generation) { value = loader.installedPacks() }
}

/** Calls [onChange] on every package install, update or removal until the returned handle is disposed. */
internal fun onPackagesChanged(context: Context, onChange: () -> Unit): DisposableEffectResult {
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = onChange()
    }
    val filter = IntentFilter().apply {
        addAction(Intent.ACTION_PACKAGE_ADDED)
        addAction(Intent.ACTION_PACKAGE_REMOVED)
        addAction(Intent.ACTION_PACKAGE_REPLACED)
        addDataScheme("package")
    }
    ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    return object : DisposableEffectResult {
        override fun dispose() {
            try { context.unregisterReceiver(receiver) } catch (_: IllegalArgumentException) { }
        }
    }
}

/**
 * [selected] is the stored pack (null = system icons); [settingsLoaded] is false until it is known.
 * A stored pack that is no longer installed stays listed, so the choice is visible and can be changed.
 */
@Composable
internal fun IconPackChooserStep(
    selected: String?,
    settingsLoaded: Boolean,
    loader: IconPickerLoader,
    cache: IconBitmapCache,
    onSelect: (String?) -> Unit,
) {
    val installed by rememberInstalledPacks(loader)
    val packs = installed?.takeIf { settingsLoaded }
    var query by rememberSaveable { mutableStateOf("") }
    val visible = remember(packs, query) { packs?.let { filterPacks(it, query) } }
    val searching = query.isNotBlank()
    val selectedPack = selected?.let { pkg -> packs?.firstOrNull { it.packageName == pkg } }

    // Items above the pack rows when not searching, so the fast scroller lands on the right row.
    val leadingItems = 1 + if (searching) 0 else 2 + (if (selected != null) 2 else 0) + 1
    val listState = rememberLazyListState()
    val scrollIndex = rememberAppScrollIndex(if (searching) emptyList() else visible.orEmpty(), start = leadingItems) { it.label }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(start = PagePadding, end = PagePadding, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(GroupGap),
        ) {
            // Always shown and pinned: people with dozens of packs should never have to scroll to find one.
            stickyHeader(key = "search", contentType = "search") {
                PickerSearchField(
                    value = query,
                    onValueChange = { query = it },
                    hint = stringResource(R.string.icon_pack_chooser_search),
                    modifier = Modifier.background(settingsPageColor()).padding(bottom = 14.dp),
                )
            }
            if (!searching) {
                item(key = "system-label") { SectionLabel(stringResource(R.string.icon_picker_section_default)) }
                item(key = "system") {
                    SystemIconsRow(selected = settingsLoaded && selected == null) { onSelect(null) }
                }
                if (selected != null) {
                    item(key = "current-label") { SectionLabel(stringResource(R.string.icon_picker_pack_in_use)) }
                    item(key = "current") {
                        when {
                            packs == null -> PlaceholderRow(groupShape(0, 1))
                            selectedPack != null -> PackChoiceRow(selectedPack, true, groupShape(0, 1), loader, cache) {
                                onSelect(selectedPack.packageName)
                            }
                            else -> MissingPackRow(selected)
                        }
                    }
                }
                item(key = "packs-label") {
                    SectionLabel(
                        stringResource(R.string.icon_picker_section_packs),
                        trailing = packs?.takeIf { it.isNotEmpty() }?.size?.toString(),
                    )
                }
            }
            when {
                visible == null -> items(PLACEHOLDER_ROWS) { index -> PlaceholderRow(groupShape(index, PLACEHOLDER_ROWS)) }
                packs.isNullOrEmpty() -> item(key = "empty") { EmptyPacksCard() }
                visible.isEmpty() -> item(key = "no-match") { EmptyText(stringResource(R.string.icon_pack_chooser_no_matches)) }
                else -> itemsIndexed(visible, key = { _, pack -> "pack|${pack.packageName}" }, contentType = { _, _ -> "pack" }) { index, pack ->
                    PackChoiceRow(pack, pack.packageName == selected, groupShape(index, visible.size), loader, cache) {
                        onSelect(pack.packageName)
                    }
                }
            }
        }
        SettingsFastScroller(listState, sections = scrollIndex.sections, letterAt = scrollIndex::letterAt)
    }
}

/** Radio-style row: what the choice is, then how it draws icons. */
@Composable
private fun ChoiceRow(
    title: String,
    selected: Boolean,
    shape: Shape,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
    detail: @Composable () -> Unit,
) {
    val selectedText = stringResource(R.string.icon_picker_selected)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(tonalColor())
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { if (selected) stateDescription = selectedText }
            .heightIn(min = 72.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            detail()
        }
        if (selected) SelectedCheck(Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun SystemIconsRow(selected: Boolean, onClick: () -> Unit) {
    ChoiceRow(
        title = stringResource(R.string.look_icon_pack_system),
        selected = selected,
        shape = groupShape(0, 1),
        onClick = onClick,
        leading = { BadgeIcon(R.drawable.ic_settings_palette) },
        detail = { DetailText(stringResource(R.string.icon_pack_chooser_system_summary)) },
    )
}

@Composable
private fun PackChoiceRow(
    pack: IconPackInfo,
    selected: Boolean,
    shape: Shape,
    loader: IconPickerLoader,
    cache: IconBitmapCache,
    onClick: () -> Unit,
) {
    val packIcon = rememberIconBitmap("packapp|${pack.packageName}", ListIconSize, cache) { loader.packAppIcon(pack.packageName) }
    ChoiceRow(
        title = pack.label,
        selected = selected,
        shape = shape,
        onClick = onClick,
        leading = { IconImage(packIcon, ListIconSize) },
        detail = { PackSampleStrip(pack.packageName, loader, cache) },
    )
}

/** The stored pack is gone (uninstalled, or a restore on a new phone): say so instead of hiding it. */
@Composable
private fun MissingPackRow(packageName: String) {
    ChoiceRow(
        title = stringResource(R.string.look_icon_pack_missing),
        selected = true,
        shape = groupShape(0, 1),
        onClick = {},
        leading = { BadgeIcon(R.drawable.ic_settings_palette) },
        detail = { DetailText(stringResource(R.string.icon_pack_chooser_missing_summary, packageName)) },
    )
}

/** A few of the pack's icons; decorative, since the row's title already names the pack for TalkBack. */
@Composable
private fun PackSampleStrip(packageName: String, loader: IconPickerLoader, cache: IconBitmapCache) {
    val sizePx = with(LocalDensity.current) { SampleIconSize.roundToPx() }
    val keys = remember(packageName, sizePx) { List(SAMPLE_COUNT) { "$sizePx|packsample|$packageName|$it" } }
    val samples by produceState(keys.mapNotNull(cache::get).takeIf { it.size == SAMPLE_COUNT }, keys) {
        if (value != null) return@produceState
        value = try {
            loader.packSamples(packageName, SAMPLE_COUNT).let { drawables ->
                withContext(Dispatchers.IO) { drawables.map { it.toBitmap(sizePx, sizePx).asImageBitmap() } }
            }.also { bitmaps -> bitmaps.forEachIndexed { i, bitmap -> cache.put(keys[i], bitmap) } }
        } catch (e: CancellationException) {
            throw e
        } catch (_: RuntimeException) {
            emptyList()
        }
    }
    Row(Modifier.height(SampleIconSize), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        when (val shown = samples) {
            null -> repeat(SAMPLE_COUNT) { PlaceholderBlock(Modifier.size(SampleIconSize), RoundedCornerShape(SampleIconSize / 3)) }
            else -> if (shown.isEmpty()) {
                DetailText(stringResource(R.string.icon_pack_chooser_no_preview))
            } else {
                shown.forEach { SampleImage(it) }
            }
        }
    }
}

@Composable
private fun SampleImage(bitmap: ImageBitmap) {
    Image(bitmap, contentDescription = null, modifier = Modifier.size(SampleIconSize))
}

@Composable
private fun BadgeIcon(icon: Int, size: Dp = ListIconSize) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = BADGE_TINT_ALPHA)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(size / 2))
    }
}

@Composable
private fun DetailText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

private val ListIconSize = 40.dp
private val SampleIconSize = 24.dp
private const val SAMPLE_COUNT = 5
private const val PLACEHOLDER_ROWS = 6
private const val BADGE_TINT_ALPHA = 0.18f
