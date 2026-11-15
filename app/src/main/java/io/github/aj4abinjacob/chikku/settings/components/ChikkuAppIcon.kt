package io.github.aj4abinjacob.chikku.settings.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.aj4abinjacob.chikku.R

/**
 * The full-colour Chikku "White Coat" launcher icon on its slate tile, for settings headers.
 * Decorative: the title next to it already names the app.
 *
 * The adaptive foreground is drawn on a 108dp canvas with a wide safe margin, so it is scaled up
 * and clipped to the tile to fill it the way the launcher mask does.
 */
@Composable
fun ChikkuAppIcon(size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.32f))
            .background(colorResource(R.color.chikku_icon_background)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.requiredSize(size * 1.9f),
        )
    }
}
