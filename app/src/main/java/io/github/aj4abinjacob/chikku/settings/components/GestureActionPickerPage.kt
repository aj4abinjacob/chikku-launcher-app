package io.github.aj4abinjacob.chikku.settings.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.aj4abinjacob.chikku.R

/**
 * A gesture-action picker page inside a sheet: back row with the page title, then [body] in a box.
 * [bodyBoxModifier] sets the box height (fixed or capped) and is the caller's decision; the
 * horizontal padding is added here. Back steps out of a sub-page first, then calls [onClose].
 */
@Composable
internal fun GestureActionPickerPage(
    picker: GestureActionPickerState,
    title: String,
    onClose: () -> Unit,
    // Sizes only the inner body box, not the page's outer Column, so it is not the main `modifier`.
    @Suppress("ModifierParameter") bodyBoxModifier: Modifier,
    body: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = SheetListPadding, end = SheetPadding), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (!picker.back()) onClose() }) {
                Icon(painterResource(R.drawable.ic_settings_back), contentDescription = stringResource(R.string.gesture_picker_back))
            }
            Spacer(Modifier.width(4.dp))
            Text(
                gestureActionPickerTitle(picker, title),
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(8.dp))
        Box(bodyBoxModifier.padding(horizontal = SheetListPadding)) { body() }
    }
}
