package io.github.aj4abinjacob.chikku.settings.sections

import android.content.ActivityNotFoundException
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import android.view.View
import androidx.core.content.ContextCompat
import io.github.aj4abinjacob.chikku.badges.BadgeOverlay
import io.github.aj4abinjacob.chikku.badges.badgeColor
import io.github.aj4abinjacob.chikku.home.AppIconView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.message.rememberChikkuMessages
import io.github.aj4abinjacob.chikku.settings.previews.BadgePositionPreview
import io.github.aj4abinjacob.chikku.settings.previews.BadgeStylePreview
import io.github.aj4abinjacob.chikku.badges.BadgeAccess
import io.github.aj4abinjacob.chikku.badges.BadgeRepository
import io.github.aj4abinjacob.chikku.core.model.settings.BadgeStyle
import io.github.aj4abinjacob.chikku.core.model.settings.BadgePosition
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.settings.components.SettingsChoiceRow
import io.github.aj4abinjacob.chikku.settings.components.SettingsSliderRow
import io.github.aj4abinjacob.chikku.settings.LocalHomeDependencies
import io.github.aj4abinjacob.chikku.settings.components.SettingsColorRow
import io.github.aj4abinjacob.chikku.settings.components.SettingsInfoText
import io.github.aj4abinjacob.chikku.settings.components.SettingsNavigationRow
import io.github.aj4abinjacob.chikku.settings.components.SettingsPage
import io.github.aj4abinjacob.chikku.settings.components.SettingsSectionHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Notification badges (G08): style, color and notification access. Access is never requested on
 * its own: the user first picks a style, reads why access is needed, then opens Android's
 * notification-access page from here.
 */
@Composable
fun BadgesSettingsSection(settings: LauncherSettings, update: ((LauncherSettings) -> LauncherSettings) -> Unit) {
    val context = LocalContext.current
    // Configuration-aware, unlike context.getString, for the slider's value label.
    val resources = LocalResources.current
    val messages = rememberChikkuMessages()
    val access = remember(context) { BadgeAccess(context) }
    val repository = LocalHomeDependencies.current.badgeRepository ?: remember { BadgeRepository() }
    val connected by repository.connected.collectAsState()
    val scope = rememberCoroutineScope()
    var granted by remember { mutableStateOf<Boolean?>(null) }
    var sampleCount by remember { mutableStateOf(3) }
    // Unsaved size under the slider thumb, so the preview tracks the drag before the value is written.
    var liveBadgeScale by remember(settings.badges.badgeScale) { mutableStateOf<Float?>(null) }
    val style = settings.badges.style
    val currentStyle by rememberUpdatedState(style)
    val currentMediaEnabled by rememberUpdatedState(settings.cards.cards.any {
        it.enabled && it.content == io.github.aj4abinjacob.chikku.core.model.cards.CardContent.Media
    })
    // Re-check whenever the user comes back from Android settings. With badges off, the listener is
    // also needed by saved enabled media cards; preserve that shared access on return.
    LifecycleResumeEffect(access) {
        val job = scope.launch {
            granted = withContext(Dispatchers.IO) {
                access.apply(currentStyle, repository, Dispatchers.IO, currentMediaEnabled)
                access.isGranted()
            }
        }
        onPauseOrDispose { job.cancel() }
    }

    fun openAccessSettings() {
        scope.launch {
            // The listener must be enabled to appear in Android's list (re-disabled on return if badges are off).
            withContext(Dispatchers.IO) { runCatching { access.setListenerEnabled(true) } }
            val opened = access.settingsIntents().any { intent ->
                try {
                    context.startActivity(intent)
                    true
                } catch (_: ActivityNotFoundException) {
                    false
                } catch (_: SecurityException) {
                    false
                }
            }
            if (!opened) messages.show(R.string.settings_badges_settings_unavailable, long = true)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            BadgeSamplePreview(settings, sampleCount, Modifier.size(104.dp), badgeScale = liveBadgeScale ?: settings.badges.badgeScale)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.badges_custom_preview_title), style = MaterialTheme.typography.labelLarge)
                TextButton(onClick = { sampleCount = if (sampleCount == 3) 12 else 3 }, enabled = style != BadgeStyle.OFF,
                    modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.badges_custom_test))
                }
            }
        }
    SettingsPage(Modifier.weight(1f)) {
        SettingsChoiceRow(
            title = stringResource(R.string.settings_badges_style),
            optionPreview = { BadgeStylePreview(it) },
            optionSummary = { style -> stringResource(when (style) {
                BadgeStyle.OFF -> R.string.hint_badge_off
                BadgeStyle.DOT -> R.string.hint_badge_dot
                BadgeStyle.COUNT -> R.string.hint_badge_count
                BadgeStyle.DYNAMIC -> R.string.hint_badge_dynamic
            }) },
            options = BadgeStyle.entries.toList(),
            selected = style,
            optionLabel = { stringResource(it.labelRes()) },
            onSelect = { chosen -> update { it.copy(badges = it.badges.copy(style = chosen)) } },
            icon = R.drawable.ic_settings_row_notifications,
        )
        SettingsChoiceRow(
            title = stringResource(R.string.badges_custom_position),
            optionPreview = { BadgePositionPreview(it) },
            options = BadgePosition.entries,
            selected = settings.badges.position,
            optionLabel = { stringResource(it.positionLabelRes()) },
            onSelect = { position -> update { it.copy(badges = it.badges.copy(position = position)) } },
            enabled = style != BadgeStyle.OFF,
            icon = R.drawable.ic_settings_row_picture_in_picture_alt,
        )
        // 5% steps across the full scale range; the sample icon follows the thumb while dragging.
        SettingsSliderRow(
            title = stringResource(R.string.badges_custom_size),
            value = settings.badges.badgeScale,
            onValueChangeFinished = { scale ->
                liveBadgeScale = null
                update { it.copy(badges = it.badges.copy(badgeScale = (scale * 20).roundToInt() / 20f)) }
            },
            valueRange = LauncherSettings.MIN_SCALE..LauncherSettings.MAX_SCALE,
            steps = 19,
            valueLabel = { scale ->
                val percent = (scale * 100).roundToInt()
                val preset = when (percent) {
                    75 -> R.string.badges_custom_small
                    100 -> R.string.badges_custom_medium
                    125 -> R.string.badges_custom_large
                    else -> null
                }
                if (preset == null) resources.getString(R.string.settings_badge_scale_custom, percent)
                else resources.getString(R.string.settings_badge_scale_preset, resources.getString(preset), percent)
            },
            onValueChanging = { scale -> liveBadgeScale = scale },
            enabled = style != BadgeStyle.OFF,
            rangeLabels = stringResource(R.string.badges_custom_small) to stringResource(R.string.badges_custom_large),
            icon = R.drawable.ic_settings_row_photo_size_select_large,
        )
        SettingsColorRow(
            title = stringResource(R.string.settings_badges_color),
            color = settings.badges.dotColor,
            onColorChange = { color -> update { it.copy(badges = it.badges.copy(dotColor = color)) } },
            enabled = style != BadgeStyle.OFF,
            icon = R.drawable.ic_settings_row_palette,
        )

        SettingsSectionHeader(stringResource(R.string.settings_badges_access_title))
        if (style == BadgeStyle.OFF) {
            SettingsInfoText(stringResource(R.string.settings_badges_off_hint), details = stringResource(R.string.settings_badges_off_hint_details))
            if (granted == true) {
                SettingsNavigationRow(
                    title = stringResource(R.string.settings_badges_manage),
                    onClick = ::openAccessSettings,
                    icon = R.drawable.ic_settings_row_open_in_new,
                )
            }
        } else {
            when (granted) {
                true -> {
                    SettingsInfoText(stringResource(if (connected) R.string.settings_badges_access_granted else R.string.settings_badges_service_waiting))
                    if (!connected) SettingsNavigationRow(
                        title = stringResource(R.string.settings_badges_reconnect),
                        onClick = { scope.launch { withContext(Dispatchers.IO) { access.requestRebind() } } },
                        icon = R.drawable.ic_settings_row_sync,
                    )
                    SettingsNavigationRow(
                        title = stringResource(R.string.settings_badges_manage),
                        onClick = ::openAccessSettings,
                        icon = R.drawable.ic_settings_row_open_in_new,
                    )
                }
                false -> {
                    SettingsInfoText(stringResource(R.string.settings_badges_access_missing))
                    SettingsNavigationRow(
                        title = stringResource(R.string.settings_badges_grant),
                        onClick = ::openAccessSettings,
                        icon = R.drawable.ic_settings_row_lock_open,
                    )
                }
                null -> Unit
            }
            SettingsInfoText(stringResource(R.string.settings_badges_profiles_note))
        }
        // Page footer: how badges use notification access, kept below the controls so they lead the page.
        SettingsInfoText(stringResource(R.string.settings_badges_explanation), details = stringResource(R.string.settings_badges_explanation_details))
    }
    }
}

private fun BadgeStyle.labelRes(): Int = when (this) {
    BadgeStyle.OFF -> R.string.settings_badges_style_off
    BadgeStyle.DOT -> R.string.settings_badges_style_dot
    BadgeStyle.COUNT -> R.string.settings_badges_style_count
    BadgeStyle.DYNAMIC -> R.string.settings_badges_style_dynamic
}

/** Uses only a synthetic count and the bundled placeholder; notification access is irrelevant. */
@Composable
internal fun BadgeSamplePreview(
    settings: LauncherSettings,
    sampleCount: Int,
    modifier: Modifier = Modifier,
    badgeScale: Float = settings.badges.badgeScale,
) {
    val label = stringResource(R.string.badges_custom_sample_app)
    val description = if (settings.badges.style == BadgeStyle.OFF) stringResource(R.string.preview_badges_off)
        else stringResource(R.string.badges_custom_sample_description, sampleCount)
    // The launcher's own rule; the settings theme's dark-mode primary is not the badge colour.
    val color = badgeColor(settings)
    val labelColor = MaterialTheme.colorScheme.onSurface.toArgb()
    Box(modifier.padding(vertical = 8.dp).clearAndSetSemantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { context ->
                AppIconView(context, AppIconView.Style.DRAWER).apply {
                    icon = ContextCompat.getDrawable(context, R.drawable.ic_app_placeholder)
                    isClickable = false
                    isLongClickable = false
                    isFocusable = false
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }
            },
            update = { view ->
                view.label = label
                view.setLabelColor(labelColor)
                view.badge = BadgeOverlay(
                    settings.badges.style,
                    sampleCount, color, position = settings.badges.position, badgeScale = badgeScale,
                )
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private fun BadgePosition.positionLabelRes(): Int = when (this) {
    BadgePosition.TOP_END -> R.string.badges_custom_top_end
    BadgePosition.TOP_START -> R.string.badges_custom_top_start
    BadgePosition.BOTTOM_END -> R.string.badges_custom_bottom_end
    BadgePosition.BOTTOM_START -> R.string.badges_custom_bottom_start
}
