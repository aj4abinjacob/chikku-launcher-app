package io.github.aj4abinjacob.chikku.settings.iconpicker

import android.graphics.drawable.Drawable
import androidx.compose.animation.Crossfade
import io.github.aj4abinjacob.chikku.theme.LocalMotion
import io.github.aj4abinjacob.chikku.theme.tweenSpec
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import io.github.aj4abinjacob.chikku.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * Building blocks shared by the picker's steps. They follow the app and folder edit sheets: accent
 * section labels, tonal rows grouped with 24dp outer and 6dp inner corners, rounded search fields.
 */

internal val OuterCorner = 24.dp
internal val InnerCorner = 6.dp
internal val GroupGap = 2.dp
internal val PagePadding = 16.dp

/** Corners for row [index] of a [count]-row group. */
internal fun groupShape(index: Int, count: Int): Shape {
    val top = if (index == 0) OuterCorner else InnerCorner
    val bottom = if (index == count - 1) OuterCorner else InnerCorner
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

/** Fill of tonal rows and cards. */
@Composable
internal fun tonalColor(): Color = MaterialTheme.colorScheme.surfaceContainerHigh

/** Accent label above a group, like the section headers in settings and the edit sheets. */
@Composable
internal fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: String? = null) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 20.dp, bottom = 10.dp)
            .semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Text(trailing, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * A tonal, clickable row: [leading] icon slot, title/summary, then [trailing] content. One accessible
 * node; [selectedLabel] adds a spoken state.
 */
@Composable
internal fun TonalRow(
    title: String,
    shape: Shape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    leading: @Composable () -> Unit = {},
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(tonalColor())
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 72.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
        trailing()
    }
}

/** Loading stand-in shaped like a [TonalRow], so lists keep their layout while content arrives. */
@Composable
internal fun PlaceholderRow(shape: Shape) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(tonalColor())
            .heightIn(min = 72.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlaceholderBlock(Modifier.size(40.dp), CircleShape)
        Spacer(Modifier.width(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PlaceholderBlock(Modifier.width(140.dp).heightIn(min = 12.dp), RoundedCornerShape(6.dp))
            PlaceholderBlock(Modifier.width(96.dp).heightIn(min = 10.dp), RoundedCornerShape(6.dp))
        }
    }
}

@Composable
internal fun PlaceholderBlock(modifier: Modifier, shape: Shape = RoundedCornerShape(14.dp)) {
    Box(modifier.clip(shape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = PLACEHOLDER_ALPHA)))
}

/** Small tonal pill, e.g. "In use" or "Custom icon". */
@Composable
internal fun Pill(text: String, emphasized: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = if (emphasized) colors.onSecondaryContainer else colors.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(CircleShape)
            .background(if (emphasized) colors.secondaryContainer else colors.surfaceContainerHighest)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** Accent check shown on the chosen option. */
@Composable
internal fun SelectedCheck(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(R.drawable.ic_settings_check),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
internal fun Chevron() {
    Icon(
        painterResource(R.drawable.ic_settings_chevron),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp),
    )
}

/** Rounded, tonal search field, as in settings search. */
@Composable
internal fun PickerSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    fill: Color = tonalColor(),
) {
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.take(MAX_QUERY_LENGTH)) },
        singleLine = true,
        placeholder = { Text(hint, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
        trailingIcon = if (value.isEmpty()) null else {
            {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(painterResource(R.drawable.ic_search_clear), contentDescription = stringResource(R.string.icon_picker_clear_search))
                }
            }
        },
        shape = RoundedCornerShape(28.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = fill,
            unfocusedContainerColor = fill,
            unfocusedBorderColor = Color.Transparent,
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
        modifier = modifier.fillMaxWidth(),
    )
}

/** An icon being loaded: [done] turns true once loading finished, with or without a [bitmap]. */
internal class LoadedIcon(val bitmap: ImageBitmap?, val done: Boolean)

/**
 * Loads a drawable off the main thread and renders it at [size]. Results are kept in [cache] under
 * [cacheKey] so scrolling back does not decode again.
 */
@Composable
internal fun rememberIconBitmap(
    cacheKey: String,
    size: Dp,
    cache: IconBitmapCache,
    load: suspend () -> Drawable?,
): LoadedIcon {
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val fullKey = "$sizePx|$cacheKey"
    val icon by produceState(cache.get(fullKey)?.let { LoadedIcon(it, true) } ?: LoadedIcon(null, false), fullKey) {
        if (value.done) return@produceState
        val bitmap = try {
            load()?.let { drawable ->
                withContext(Dispatchers.IO) { drawable.toBitmap(sizePx, sizePx).asImageBitmap() }
            }?.also { cache.put(fullKey, it) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: RuntimeException) {
            null
        }
        value = LoadedIcon(bitmap, true)
    }
    return icon
}

/** Icon image that fades in once loaded; a soft placeholder holds its place meanwhile or if it fails. */
@Composable
internal fun IconImage(icon: LoadedIcon, size: Dp, modifier: Modifier = Modifier) {
    Crossfade(targetState = icon, animationSpec = LocalMotion.current.tweenSpec(FADE_MS), modifier = modifier.size(size), label = "icon") { state ->
        val bitmap = state.bitmap
        when {
            bitmap != null -> Image(bitmap, contentDescription = null, modifier = Modifier.size(size))
            state.done -> Image(painterResource(R.drawable.ic_app_placeholder), contentDescription = null, modifier = Modifier.size(size))
            else -> PlaceholderBlock(Modifier.size(size).padding(size / 10), RoundedCornerShape(size / 3))
        }
    }
}

private const val PLACEHOLDER_ALPHA = 0.08f
private const val FADE_MS = 150
private const val MAX_QUERY_LENGTH = 100
