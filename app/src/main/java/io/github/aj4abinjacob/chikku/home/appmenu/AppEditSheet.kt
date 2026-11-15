package io.github.aj4abinjacob.chikku.home.appmenu

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.settings.LocalHomeDependencies
import io.github.aj4abinjacob.chikku.settings.components.AppIconImage
import io.github.aj4abinjacob.chikku.settings.components.SectionLabel
import io.github.aj4abinjacob.chikku.settings.components.TonalRowChevron
import io.github.aj4abinjacob.chikku.settings.components.TonalValuePill
import kotlinx.coroutines.launch

/** What the app editor shows and how it saves; built by [AppMenuController]. Labels are shown, never logged. */
class AppEditRequest(
    val key: AppKey,
    /** The app's own label; an empty or identical name clears the custom one. */
    val originalLabel: String,
    val customLabel: String?,
    val hasCustomIcon: Boolean,
    /** Writes the custom label (null restores the app's own). */
    val onSaveLabel: (String?) -> Unit,
    /** Opens the icon picker for this app. */
    val onChangeIcon: () -> Unit,
)

/**
 * Edits one app's name and icon in a bottom sheet that matches the folder editor and Chikku
 * settings (settings theme and accent, tonal rows, a pinned Done/Discard footer). The name is
 * staged and saved when the sheet closes, whether by Done, back, swiping down or tapping outside;
 * only Discard drops it. Changing the icon saves the name first, then opens the icon picker.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppEditSheet(request: AppEditRequest, onDismiss: () -> Unit) {
    val initialName = request.customLabel ?: request.originalLabel
    var name by remember { mutableStateOf(initialName) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current

    /** The custom label [name] stands for: null when it is empty or the app's own name. */
    fun staged(): String? = name.trim().takeIf { it.isNotEmpty() && it != request.originalLabel }
    fun hasChanges() = staged() != request.customLabel
    val changed = hasChanges()
    // Set once the outcome (saved or discarded) is decided, so a closing animation cannot save twice.
    var finished by remember { mutableStateOf(false) }

    fun commit() {
        if (hasChanges()) request.onSaveLabel(staged())
    }

    // HOME and other host events remove the sheet without a dismiss request; keep the edit then too.
    val latestCommit by rememberUpdatedState(::commit)
    DisposableEffect(Unit) {
        onDispose {
            if (!finished) {
                finished = true
                latestCommit()
            }
        }
    }

    fun close(save: Boolean, then: () -> Unit = {}) {
        if (!finished) {
            finished = true
            if (save) commit()
        }
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            onDismiss()
            then()
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (!finished) {
                finished = true
                commit()
            }
            onDismiss()
        },
        sheetState = sheetState,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
            ) {
                Header(request.key, name.trim().ifEmpty { request.originalLabel })
                Spacer(Modifier.height(20.dp))
                val isOriginal = staged() == null
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.app_edit_name)) },
                    placeholder = { Text(request.originalLabel) },
                    // Only when it adds something: the app's own name is already in the field otherwise.
                    supportingText = when {
                        name.isBlank() -> { { Text(stringResource(R.string.app_edit_name_empty)) } }
                        isOriginal -> null
                        else -> { { Text(stringResource(R.string.app_edit_name_original, request.originalLabel)) } }
                    },
                    trailingIcon = if (isOriginal) null else {
                        {
                            IconButton(onClick = { name = request.originalLabel }) {
                                Icon(painterResource(R.drawable.ic_menu_refresh), contentDescription = stringResource(R.string.app_edit_name_reset))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                    modifier = Modifier.fillMaxWidth(),
                )

                SectionLabel(stringResource(R.string.app_edit_icon_section))
                IconRow(
                    key = request.key,
                    custom = request.hasCustomIcon,
                    onClick = { close(save = true, then = request.onChangeIcon) },
                )
            }
            // Pinned below the scrolling content so the way out is always in view.
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(if (changed) R.string.app_edit_saves_on_close else R.string.app_edit_no_changes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                )
                if (changed) {
                    TextButton(onClick = { close(save = false) }) { Text(stringResource(R.string.app_edit_discard)) }
                    Spacer(Modifier.width(8.dp))
                }
                Button(onClick = { close(save = true) }) { Text(stringResource(R.string.app_edit_done)) }
            }
        }
    }
}

@Composable
private fun Header(key: AppKey, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AppIcon(key, HeaderIconSize)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(R.string.app_edit_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "Change icon" as a tonal row: current icon, summary and a Default/Custom pill. */
@Composable
private fun IconRow(key: AppKey, custom: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(OuterCorner))
            .background(colors.surfaceContainerHigh)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 72.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        AppIcon(key, 40.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.app_menu_change_icon), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.app_edit_icon_summary),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        TonalValuePill(
            stringResource(if (custom) R.string.app_edit_icon_custom else R.string.app_edit_icon_default),
            isDefault = !custom,
        )
        TonalRowChevron()
    }
}

@Composable
private fun AppIcon(key: AppKey, size: Dp) {
    AppIconImage(key, size, LocalHomeDependencies.current.iconProvider)
}

private val HeaderIconSize = 56.dp
private val OuterCorner = 24.dp
