package io.github.aj4abinjacob.chikku.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.aj4abinjacob.chikku.R

/** Accent label above a group, like the section headers in Chikku settings. */
@Composable
internal fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 24.dp, bottom = 10.dp).semantics { heading() },
    )
}

/** A value shown as a tonal pill: muted while it is the default, accented once chosen. */
@Composable
internal fun TonalValuePill(
    text: String,
    isDefault: Boolean,
    modifier: Modifier = Modifier,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val colors = MaterialTheme.colorScheme
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = if (isDefault) colors.onSurfaceVariant else colors.onSecondaryContainer,
        maxLines = 1,
        overflow = overflow,
        modifier = modifier
            .clip(CircleShape)
            .background(if (isDefault) colors.surfaceContainerHighest else colors.secondaryContainer)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** The trailing chevron of a tonal row. */
@Composable
internal fun TonalRowChevron() {
    Icon(
        painterResource(R.drawable.ic_settings_chevron),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp),
    )
}

/**
 * One gesture or swipe direction: icon badge, label and the current action as a pill (muted while
 * default). [maxPillWidth] caps the pill when a caller needs to.
 */
@Composable
internal fun GestureValueRow(
    icon: Int,
    label: String,
    value: String,
    isDefault: Boolean,
    shape: RoundedCornerShape,
    onClick: () -> Unit,
    maxPillWidth: Dp = Dp.Unspecified,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surfaceContainerHigh)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.gestures_sheet_change), onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(colors.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = null, tint = colors.onPrimaryContainer, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            TonalValuePill(value, isDefault, Modifier.widthIn(max = maxPillWidth), TextOverflow.Ellipsis)
        }
        TonalRowChevron()
    }
}
