package io.github.aj4abinjacob.chikku.settings.drawer

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.AppKey

private val MenuIconSize = 24.dp

/**
 * Chikku's touch-and-hold menu for a Hidden apps cell, replacing the platform PopupMenu so it
 * follows the settings theme instead of the phone maker's skin. It is anchored to the cell's
 * bounds inside the hosting [HiddenAppsGridView], which must share this composable's parent origin.
 */
@Composable
internal fun HiddenAppMenu(
    request: HiddenAppMenuRequest,
    expanded: Boolean,
    onOpen: (AppKey) -> Unit,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    val anchor = request.anchor
    val iconPx = with(density) { MenuIconSize.roundToPx() }
    // One small render of the icon the cell already shows; no package-manager work here.
    val iconBitmap = remember(request.icon, iconPx) { request.icon?.toBitmap(iconPx, iconPx)?.asImageBitmap() }
    val openDescription = stringResource(R.string.drawer_hidden_open_description, request.label)

    // The anchor rect is in absolute view coordinates, so it must not be mirrored for RTL.
    Box(
        Modifier
            .absoluteOffset { IntOffset(anchor.left, anchor.top) }
            .size(with(density) { anchor.width().toDp() }, with(density) { anchor.height().toDp() }),
    ) {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismiss,
            shape = MaterialTheme.shapes.large,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.drawer_hidden_open)) },
                leadingIcon = iconBitmap?.let { bitmap ->
                    { Image(bitmap, contentDescription = null, modifier = Modifier.size(MenuIconSize)) }
                },
                onClick = {
                    onDismiss()
                    onOpen(request.key)
                },
                modifier = Modifier.semantics { contentDescription = openDescription },
            )
        }
    }
}
