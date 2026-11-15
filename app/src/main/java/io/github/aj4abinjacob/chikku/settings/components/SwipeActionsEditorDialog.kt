package io.github.aj4abinjacob.chikku.settings.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import io.github.aj4abinjacob.chikku.theme.LocalMotion
import io.github.aj4abinjacob.chikku.theme.tweenSpec
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.AppCustomization
import io.github.aj4abinjacob.chikku.core.model.GestureAction
import io.github.aj4abinjacob.chikku.core.model.ItemActions
import io.github.aj4abinjacob.chikku.core.model.LauncherApp
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.settings.previews.SwipeActionsPracticePreview
import io.github.aj4abinjacob.chikku.settings.previews.iconPracticeSwipeActions
import io.github.aj4abinjacob.chikku.settings.LocalHomeDependencies
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch

private sealed interface SwipePreviewPolicy {
    data object Loading : SwipePreviewPolicy
    data object Unavailable : SwipePreviewPolicy
    data class Ready(val key: AppKey, val settings: LauncherSettings, val customization: AppCustomization?) : SwipePreviewPolicy
}

/**
 * Edits the swipe-up/swipe-down pair of one icon (or folder) in a bottom sheet, so it opens near
 * the icon's long-press menu and within thumb reach. Each change is reported at once through
 * [onChange]; null means "use the default" ([defaultLabel] describes it). [appKey], when given,
 * shows the app's icon in the header. Choosing an action replaces the sheet's content with the
 * picker pages instead of stacking a dialog on top. Reusable by the folders feature and an icon long-press menu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeActionsEditorDialog(
    title: String,
    swipeUp: GestureAction?,
    swipeDown: GestureAction?,
    apps: List<LauncherApp>?,
    defaultLabel: String,
    onChange: (swipeUp: GestureAction?, swipeDown: GestureAction?) -> Unit,
    onDismiss: () -> Unit,
    actions: List<GestureAction> = GestureAction.simpleActions,
    appKey: AppKey? = null,
    /** False when editing the app's own defaults: "Use default" then falls back to the home swipe. */
    defaultUsesAppDefaults: Boolean = true,
) {
    var picking by rememberSaveable { mutableStateOf<Boolean?>(null) } // true = up, false = down
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val upTitle = stringResource(R.string.gestures_sheet_swipe_up)
    val downTitle = stringResource(R.string.gestures_sheet_swipe_down)
    val deps = LocalHomeDependencies.current
    val previewPolicy by produceState<SwipePreviewPolicy>(SwipePreviewPolicy.Loading, deps, appKey) {
        value = SwipePreviewPolicy.Loading
        if (appKey != null) {
            combine(deps.settingsRepository.settings, deps.appCustomizationRepository.customizations) { settings, customizations ->
                SwipePreviewPolicy.Ready(appKey, settings, customizations[appKey]) as SwipePreviewPolicy
            }.catch { emit(SwipePreviewPolicy.Unavailable) }.collect { value = it }
        }
    }
    val resolved = (previewPolicy as? SwipePreviewPolicy.Ready)?.takeIf { it.key == appKey }?.let { policy ->
        appKey?.let { iconPracticeSwipeActions(it, ItemActions(swipeUp = swipeUp, swipeDown = swipeDown), policy.customization, policy.settings) }
    }
    // Explicit actions are known while defaults load. None blocks the app default but can still
    // fall through to a home gesture, so it needs the complete policy before showing a result.
    // What "Use default" does for this icon right now (app default, else the home swipe).
    val defaults = (previewPolicy as? SwipePreviewPolicy.Ready)?.takeIf { it.key == appKey }?.let { policy ->
        appKey?.let { iconPracticeSwipeActions(it, ItemActions(), policy.customization.takeIf { defaultUsesAppDefaults }, policy.settings) }
    }
    val previewUp = resolved?.first ?: swipeUp?.takeUnless { it == GestureAction.None }
    val previewDown = resolved?.second ?: swipeDown?.takeUnless { it == GestureAction.None }
    val previewDefault = when {
        appKey == null -> defaultLabel
        previewPolicy == SwipePreviewPolicy.Unavailable -> stringResource(R.string.preview_gesture_defaults_unavailable)
        else -> stringResource(R.string.preview_gesture_defaults_loading)
    }

    val picker = rememberGestureActionPickerState()

    fun closePicker() {
        picking = null
        picker.reset()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        // Back steps out of the picker pages before it closes the sheet.
        BackHandler(enabled = picking != null) { if (!picker.back()) closePicker() }
        val motion = LocalMotion.current
        AnimatedContent(
            targetState = picking,
            transitionSpec = {
                val forward = targetState != null
                (slideInHorizontally(motion.tweenSpec(300)) { if (forward) it / 4 else -it / 4 } + fadeIn(motion.tweenSpec(300))) togetherWith
                    (slideOutHorizontally(motion.tweenSpec(300)) { if (forward) -it / 4 else it / 4 } + fadeOut(motion.tweenSpec(150))) using
                    SizeTransform(clip = false)
            },
            label = "swipeActionsPage",
        ) { up ->
            if (up == null) {
                Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 16.dp)) {
                    Column(Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState())) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (appKey != null) {
                                HeaderIcon(appKey)
                                Spacer(Modifier.width(16.dp))
                            }
                            Column {
                                Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    stringResource(R.string.gestures_sheet_subtitle),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            GestureValueRow(
                                icon = R.drawable.ic_swipe_up,
                                label = upTitle,
                                value = gestureActionLabel(swipeUp, apps, defaultLabel),
                                isDefault = swipeUp == null,
                                shape = RoundedCornerShape(OuterCorner, OuterCorner, InnerCorner, InnerCorner),
                                onClick = { picking = true },
                            )
                            GestureValueRow(
                                icon = R.drawable.ic_swipe_down,
                                label = downTitle,
                                value = gestureActionLabel(swipeDown, apps, defaultLabel),
                                isDefault = swipeDown == null,
                                shape = RoundedCornerShape(InnerCorner, InnerCorner, OuterCorner, OuterCorner),
                                onClick = { picking = false },
                            )
                        }
                        SwipeActionsPracticePreview(previewUp, previewDown, previewDefault, Modifier.padding(top = 12.dp))
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(top = 16.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = { onChange(null, null) },
                            enabled = swipeUp != null || swipeDown != null,
                        ) { Text(stringResource(R.string.gestures_clear_actions)) }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = {
                            scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                        }) { Text(stringResource(R.string.gestures_done)) }
                    }
                }
            } else {
                // Keep the picker viewport steady when filtering removes rows. A wrap-content
                // list lets the sheet collapse around its focused sticky search header while
                // the IME is open, leaving results outside the visible viewport.
                GestureActionPickerPage(picker, if (up) upTitle else downTitle, ::closePicker, Modifier.height(PickerMaxHeight)) {
                    GestureActionPickerBody(
                        state = picker,
                        current = if (up) swipeUp else swipeDown,
                        apps = apps,
                        onPick = { picked ->
                            if (up) onChange(picked, swipeDown) else onChange(swipeUp, picked)
                            closePicker()
                        },
                        allowDefault = true,
                        actions = actions,
                        searchBackground = BottomSheetDefaults.ContainerColor,
                        defaultSummary = defaults?.let { gestureActionLabel(if (up) it.first else it.second, apps) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderIcon(key: AppKey) {
    AppIconImage(key, HeaderIconSize, LocalHomeDependencies.current.iconProvider)
}

private val HeaderIconSize = 48.dp
private val PickerMaxHeight = 480.dp
private val OuterCorner = 24.dp
private val InnerCorner = 6.dp
