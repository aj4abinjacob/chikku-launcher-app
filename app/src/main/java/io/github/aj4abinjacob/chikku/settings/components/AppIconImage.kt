package io.github.aj4abinjacob.chikku.settings.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.core.graphics.drawable.toBitmap
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.platform.IconProvider
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Loads [key]'s launcher icon at [size] with the bitmap decoded off the main thread. Reloads when
 * [key], [size] or the provider's [IconProvider.iconRevision] changes; null while loading, when
 * [key] is null, and when loading fails. Callers that must withhold an icon (app lock, private
 * profiles) decide that before passing a key.
 */
@Composable
internal fun rememberAppIconBitmap(key: AppKey?, size: Dp, iconProvider: IconProvider): ImageBitmap? {
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val revision by iconProvider.iconRevision.collectAsState()
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key, sizePx, revision, iconProvider) {
        value = key?.let { loadAppIcon(iconProvider, it, sizePx) }
    }
    return bitmap
}

/** An app icon in a box of [size]; the box stays empty until the icon has loaded. */
@Composable
internal fun AppIconImage(
    key: AppKey?,
    size: Dp,
    iconProvider: IconProvider,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
) {
    val bitmap = rememberAppIconBitmap(key, size, iconProvider)
    Box(modifier.size(size), contentAlignment = contentAlignment) {
        bitmap?.let { Image(it, contentDescription = null, modifier = Modifier.size(size)) }
    }
}

internal suspend fun loadAppIcon(iconProvider: IconProvider, key: AppKey, sizePx: Int): ImageBitmap? = try {
    val drawable = iconProvider.icon(key)
    withContext(Dispatchers.Default) { drawable.toBitmap(sizePx, sizePx).asImageBitmap() }
} catch (e: CancellationException) {
    throw e
} catch (_: RuntimeException) {
    null
}
