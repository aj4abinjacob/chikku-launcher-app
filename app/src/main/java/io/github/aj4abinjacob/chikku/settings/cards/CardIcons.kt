package io.github.aj4abinjacob.chikku.settings.cards

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.core.graphics.drawable.toBitmap
import io.github.aj4abinjacob.chikku.core.model.GestureAction
import io.github.aj4abinjacob.chikku.home.cards.cardActionGlyph
import io.github.aj4abinjacob.chikku.platform.ShortcutIconLoader
import io.github.aj4abinjacob.chikku.settings.LocalHomeDependencies
import kotlinx.coroutines.CancellationException

/**
 * The icon of a card action: the real app or shortcut icon, or a glyph on a tonal circle for
 * launcher actions. Callers only pass targets already filtered to visible personal apps.
 */
@Composable
internal fun CardActionIcon(
    action: GestureAction,
    size: Dp,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    val launches = action is GestureAction.LaunchApp || action is GestureAction.LaunchShortcut
    if (launches) {
        val bitmap = rememberCardTargetIcon(action, size)
        Box(modifier.size(size), contentAlignment = Alignment.Center) {
            if (bitmap != null) Image(bitmap, contentDescription = null, modifier = Modifier.size(size))
            else Box(Modifier.size(size * 0.8f).clip(CircleShape).background(container))
        }
    } else {
        Box(modifier.size(size).clip(CircleShape).background(container), contentAlignment = Alignment.Center) {
            Icon(painterResource(cardActionGlyph(action)), contentDescription = null, tint = content,
                modifier = Modifier.size(size * 0.46f))
        }
    }
}

@Composable
private fun rememberCardTargetIcon(action: GestureAction, size: Dp): ImageBitmap? {
    val deps = LocalHomeDependencies.current
    val context = LocalContext.current
    val lockState by deps.appLock.state.collectAsState()
    if (action is GestureAction.LaunchShortcut &&
        (!lockState.mayRead(action.packageName, action.userSerial) || !deps.appLock.mayRead(action.packageName, action.userSerial))) return null
    val shortcuts = remember(context, deps.appLock) { ShortcutIconLoader(context, deps.appLock, sharedPacks = deps.iconPackRepository) }
    val px = with(LocalDensity.current) { size.roundToPx() }.coerceAtLeast(1)
    val revision by deps.iconProvider.iconRevision.collectAsState()
    val bitmap by produceState<ImageBitmap?>(null, action, px, lockState, revision) {
        value = try {
            when (action) {
                is GestureAction.LaunchApp -> deps.iconProvider.icon(action.key)
                is GestureAction.LaunchShortcut -> shortcuts.load(action.packageName, action.shortcutId, action.userSerial)
                else -> null
            }?.toBitmap(px, px)?.asImageBitmap()
        } catch (e: CancellationException) {
            throw e
        } catch (_: RuntimeException) {
            null
        }
    }
    return bitmap
}
