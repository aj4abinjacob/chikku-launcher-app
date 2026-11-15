package io.github.aj4abinjacob.chikku.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import io.github.aj4abinjacob.chikku.theme.LocalMotion
import io.github.aj4abinjacob.chikku.theme.tweenSpec
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.backup.BackupMessage
import io.github.aj4abinjacob.chikku.backup.BackupUiState
import io.github.aj4abinjacob.chikku.core.model.onboarding.LayoutPreset
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerStyle
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.core.model.settings.NightMode
import io.github.aj4abinjacob.chikku.settings.backup.RestoreAttentionCard
import io.github.aj4abinjacob.chikku.settings.backup.RestorePreviewDialog
import io.github.aj4abinjacob.chikku.settings.backup.text
import io.github.aj4abinjacob.chikku.settings.components.layoutChangeControl
import io.github.aj4abinjacob.chikku.settings.previews.HomeGridPreview

/** Onboarding steps, in order. */
enum class OnboardingStep { WELCOME, LAYOUT, DRAWER, NIGHT, DEFAULT_HOME, DONE }

/** First-run restore: the shared backup state plus the actions onboarding needs. */
data class OnboardingRestore(
    val state: BackupUiState = BackupUiState(),
    val onPickFile: () -> Unit = {},
    val onConfirm: () -> Unit = {},
    val onCancel: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
)

/*
 * Visual language: Chikku settings chrome (accent top bar, Chikku surface) with the tonal grouped
 * cards of the folder/app edit sheets (24dp outer, 6dp inner corners, 2dp gaps).
 */
private val OuterCorner = 24.dp
private val InnerCorner = 6.dp
private val PageGutter = 24.dp

@Composable
fun OnboardingScreen(
    settings: LauncherSettings?,
    isDefaultHome: Boolean?,
    update: ((LauncherSettings) -> LauncherSettings) -> Unit,
    onSetDefault: () -> Unit,
    onFinish: () -> Unit,
    restore: OnboardingRestore = OnboardingRestore(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    var index by rememberSaveable { mutableIntStateOf(0) }
    // Kept across recreation; the restored settings replace the remaining setup choices.
    var restored by rememberSaveable { mutableStateOf(false) }
    if (restore.state.restored) restored = true
    if (restored) {
        RestoredScreen(restore.state, isDefaultHome, onSetDefault, onFinish, snackbarHostState)
        return
    }
    val steps = OnboardingStep.entries
    val step = steps[index]
    BackHandler { if (index > 0) index-- else onFinish() }
    restore.state.preview?.let { RestorePreviewDialog(it, onConfirm = restore.onConfirm, onCancel = restore.onCancel) }

    // The step's own action (set as default) is the primary one until it is done; Next steps back to tonal.
    val nextIsSecondary = step == OnboardingStep.DEFAULT_HOME && isDefaultHome != true
    OnboardingScaffold(
        snackbarHostState = snackbarHostState,
        onSkip = if (step != OnboardingStep.DONE) onFinish else null,
        bottomBar = {
            if (index > 0) {
                OutlinedButton(onClick = { index-- }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.onboarding_back))
                }
            }
            Spacer(Modifier.weight(1f))
            val label = stringResource(if (step == OnboardingStep.DONE) R.string.onboarding_done else R.string.onboarding_next)
            val next: () -> Unit = { if (step == OnboardingStep.DONE) onFinish() else index++ }
            if (nextIsSecondary) {
                FilledTonalButton(onClick = next, modifier = Modifier.heightIn(min = 48.dp)) { Text(label) }
            } else {
                Button(onClick = next, modifier = Modifier.heightIn(min = 48.dp)) { Text(label) }
            }
        },
    ) {
        StepProgress(index, steps.size)
        val motion = LocalMotion.current
        AnimatedContent(
            targetState = index,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = {
                if (!motion.enabled) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    // Later steps come in from the end edge; going back reverses it.
                    val sign = if (targetState > initialState) 1 else -1
                    (fadeIn(motion.tweenSpec(STEP_MS)) + slideInHorizontally(motion.tweenSpec(STEP_MS)) { sign * it / STEP_SLIDE_FRACTION })
                        .togetherWith(fadeOut(motion.tweenSpec(STEP_MS / 2)) + slideOutHorizontally(motion.tweenSpec(STEP_MS)) { -sign * it / STEP_SLIDE_FRACTION })
                }
            },
            label = "onboardingStep",
        ) { shownIndex ->
            val shown = steps[shownIndex]
            Column(Modifier.fillMaxSize()) {
                if (shown == OnboardingStep.LAYOUT && settings != null) {
                    // Part of the page, so it slides with the step instead of pushing the content down.
                    HomeGridPreview(
                        columns = settings.home.desktopColumns,
                        rows = settings.home.desktopRows,
                        horizontalPadding = settings.home.horizontalPadding,
                        verticalPadding = settings.home.verticalPadding,
                        dockHorizontalPadding = settings.dock.horizontalPadding,
                        dockIconCount = settings.dock.iconCount,
                        iconScale = settings.home.iconScale,
                        labelScale = settings.home.labelScale,
                        modifier = Modifier.fillMaxWidth().height(180.dp).padding(vertical = 8.dp),
                    )
                }
                // One scroll state per step, so a step never opens scrolled to the previous one's offset.
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = PageGutter, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (settings == null) {
                        Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    } else {
                        StepContent(shown, settings, isDefaultHome, update, onSetDefault)
                        if (shown == OnboardingStep.WELCOME) RestoreEntry(restore)
                    }
                }
            }
        }
    }
}

private const val STEP_MS = 240
private const val STEP_SLIDE_FRACTION = 5

/** Settings-style chrome: accent top bar with Skip, Chikku surface, snackbar host and a pinned button row. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OnboardingScaffold(
    snackbarHostState: SnackbarHostState,
    onSkip: (() -> Unit)?,
    bottomBar: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Scaffold(
        containerColor = colors.surface,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.onboarding_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() },
                    )
                },
                actions = {
                    if (onSkip != null) {
                        TextButton(onClick = onSkip) {
                            Text(stringResource(R.string.onboarding_skip), color = colors.onPrimaryContainer)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.primaryContainer,
                    scrolledContainerColor = colors.primaryContainer,
                    titleContentColor = colors.onPrimaryContainer,
                    actionIconContentColor = colors.onPrimaryContainer,
                ),
            )
        },
        bottomBar = {
            Column(Modifier.fillMaxWidth().background(colors.surface).windowInsetsPadding(WindowInsets.navigationBars)) {
                HorizontalDivider(color = colors.outlineVariant)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = PageGutter, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = bottomBar,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding), content = content)
    }
}

/** Thin accent progress bar under the top bar plus "Step n of m", announced as one value. */
@Composable
private fun StepProgress(index: Int, count: Int) {
    val progress by animateFloatAsState((index + 1f) / count, LocalMotion.current.tweenSpec(STEP_MS), label = "onboardingProgress")
    val label = stringResource(R.string.onboarding_step, index + 1, count)
    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}

@Composable
private fun ColumnScope.StepContent(
    step: OnboardingStep,
    settings: LauncherSettings,
    isDefaultHome: Boolean?,
    update: ((LauncherSettings) -> LauncherSettings) -> Unit,
    onSetDefault: () -> Unit,
) {
    val steps = OnboardingStep.entries
    StepLabel(stringResource(R.string.onboarding_step, step.ordinal + 1, steps.size))
    when (step) {
        OnboardingStep.WELCOME -> Header(R.drawable.ic_settings_palette, R.string.onboarding_welcome_title, R.string.onboarding_welcome_body)
        OnboardingStep.LAYOUT -> {
            Header(R.drawable.ic_settings_home, R.string.onboarding_layout_title, R.string.onboarding_layout_body)
            val layoutChange = layoutChangeControl(settings, update)
            if (settings.home.layoutLocked) Text(stringResource(R.string.home_layout_locked))
            ChoiceGroup(
                enabled = layoutChange.ready && !settings.home.layoutLocked,
                options = LayoutPreset.entries,
                selected = LayoutPreset.of(settings),
                label = { it.labelRes() },
                summary = { it.summaryRes() },
                onSelect = { preset -> layoutChange.request { preset.applyTo(it) } },
            )
        }
        OnboardingStep.DRAWER -> {
            Header(R.drawable.ic_settings_drawer, R.string.onboarding_drawer_title, R.string.onboarding_drawer_body)
            ChoiceGroup(
                options = DrawerStyle.entries,
                selected = settings.drawer.style,
                label = {
                    when (it) {
                        DrawerStyle.VERTICAL -> R.string.onboarding_drawer_vertical
                        DrawerStyle.HORIZONTAL_PAGED -> R.string.onboarding_drawer_paged
                        DrawerStyle.LIST -> R.string.onboarding_drawer_list
                    }
                },
                summary = {
                    when (it) {
                        DrawerStyle.VERTICAL -> R.string.onboarding_drawer_vertical_summary
                        DrawerStyle.HORIZONTAL_PAGED -> R.string.onboarding_drawer_paged_summary
                        DrawerStyle.LIST -> R.string.onboarding_drawer_list_summary
                    }
                },
                onSelect = { style -> update { it.copy(drawer = it.drawer.copy(style = style)) } },
            )
        }
        OnboardingStep.NIGHT -> {
            Header(R.drawable.ic_settings_night, R.string.onboarding_night_title, R.string.onboarding_night_body)
            ChoiceGroup(
                options = NightMode.entries,
                selected = settings.night.mode,
                label = {
                    when (it) {
                        NightMode.SYSTEM -> R.string.settings_night_system
                        NightMode.ON -> R.string.settings_night_on
                        NightMode.OFF -> R.string.settings_night_off
                    }
                },
                summary = { null },
                onSelect = { mode -> update { it.copy(night = it.night.copy(mode = mode)) } },
            )
        }
        OnboardingStep.DEFAULT_HOME -> DefaultHomeContent(isDefaultHome, onSetDefault)
        OnboardingStep.DONE -> Header(R.drawable.ic_settings_check, R.string.onboarding_finish_title, R.string.onboarding_finish_body)
    }
}

@Composable
private fun DefaultHomeContent(isDefaultHome: Boolean?, onSetDefault: () -> Unit) {
    Header(R.drawable.ic_settings_home, R.string.onboarding_default_title, R.string.onboarding_default_body)
    if (isDefaultHome == true) {
        val colors = MaterialTheme.colorScheme
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(OuterCorner))
                .background(colors.surfaceContainerHigh)
                .heightIn(min = 64.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Badge(R.drawable.ic_settings_check, size = 40)
            Spacer(Modifier.width(16.dp))
            Text(stringResource(R.string.onboarding_default_done), style = MaterialTheme.typography.bodyLarge)
        }
    } else {
        Button(onClick = onSetDefault, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text(stringResource(R.string.settings_set_default))
        }
    }
    Text(
        stringResource(R.string.about_default_instructions),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** "Restore from a backup" on the welcome step: a tonal row with progress and any read/restore error. */
@Composable
private fun RestoreEntry(restore: OnboardingRestore) {
    val state = restore.state
    val colors = MaterialTheme.colorScheme
    val message = state.message?.takeIf { it != BackupMessage.RESTORED }
    Spacer(Modifier.height(4.dp))
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val bottomCorner = if (state.working || message != null) InnerCorner else OuterCorner
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(OuterCorner, OuterCorner, bottomCorner, bottomCorner))
                .background(colors.surfaceContainerHigh)
                .clickable(enabled = !state.working, role = Role.Button, onClick = restore.onPickFile)
                .alpha(if (state.working) 0.6f else 1f)
                .heightIn(min = 72.dp)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Badge(R.drawable.ic_settings_backup, size = 40)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.onboarding_restore), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.onboarding_restore_summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
            Icon(
                painterResource(R.drawable.ic_settings_chevron),
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        if (state.working) {
            val label = stringResource(R.string.backup_working)
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(InnerCorner, InnerCorner, if (message != null) InnerCorner else OuterCorner, if (message != null) InnerCorner else OuterCorner))
                    .background(colors.surfaceContainerHigh)
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            ) {
                LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = label })
            }
        }
        if (message != null) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(InnerCorner, InnerCorner, OuterCorner, OuterCorner))
                    .background(colors.surfaceContainerHigh)
                    .padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 4.dp),
            ) {
                Text(
                    stringResource(message.text()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(end = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = restore.onMessageShown) { Text(stringResource(R.string.backup_dismiss)) }
                }
            }
        }
    }
}

/** After a first-run restore: what needs attention on this phone, then the default-home request. */
@Composable
private fun RestoredScreen(
    state: BackupUiState,
    isDefaultHome: Boolean?,
    onSetDefault: () -> Unit,
    onFinish: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    BackHandler(onBack = onFinish)
    OnboardingScaffold(
        snackbarHostState = snackbarHostState,
        onSkip = null,
        bottomBar = {
            Spacer(Modifier.weight(1f))
            Button(onClick = onFinish, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.onboarding_done))
            }
        },
    ) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = PageGutter, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Header(R.drawable.ic_settings_backup, R.string.onboarding_restored_title, R.string.onboarding_restored_body)
            RestoreAttentionCard(state.restoredAttention, onDismiss = null)
            DefaultHomeContent(isDefaultHome, onSetDefault)
        }
    }
}

/** Accent eyebrow above the step title, like the section labels in Chikku settings. */
@Composable
private fun StepLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun Header(@DrawableRes icon: Int, @StringRes title: Int, @StringRes body: Int) {
    Badge(icon, size = 56)
    Text(
        stringResource(title),
        style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.semantics { heading() },
    )
    Text(
        stringResource(body),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Accent circle with a settings glyph, as in the folder edit sheet's gesture rows. */
@Composable
private fun Badge(@DrawableRes icon: Int, size: Int) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(colors.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = colors.onPrimaryContainer,
            modifier = Modifier.size((size * 0.45f).dp),
        )
    }
}

/** Single choice as a tonal grouped card: one row per option, the selected row tinted with the accent. */
@Composable
private fun <T> ChoiceGroup(
    options: List<T>,
    selected: T?,
    label: (T) -> Int,
    summary: (T) -> Int?,
    onSelect: (T) -> Unit,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEachIndexed { i, option ->
            val isSelected = option == selected
            val container by animateColorAsState(
                if (isSelected) colors.primary.copy(alpha = 0.14f).compositeOver(colors.surfaceContainerHigh) else colors.surfaceContainerHigh,
                label = "choiceContainer",
            )
            val top = if (i == 0) OuterCorner else InnerCorner
            val bottom = if (i == options.lastIndex) OuterCorner else InnerCorner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(top, top, bottom, bottom))
                    .background(container)
                    .selectable(selected = isSelected, enabled = enabled, role = Role.RadioButton) { onSelect(option) }
                    .heightIn(min = 64.dp)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = isSelected, onClick = null, enabled = enabled)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(label(option)),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isSelected) FontWeight.Medium else null,
                    )
                    summary(option)?.let {
                        Text(stringResource(it), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

private fun LayoutPreset.labelRes(): Int = when (this) {
    LayoutPreset.COMPACT -> R.string.onboarding_preset_compact
    LayoutPreset.STANDARD -> R.string.onboarding_preset_standard
    LayoutPreset.LARGE -> R.string.onboarding_preset_large
    LayoutPreset.EXTRA_LARGE -> R.string.onboarding_preset_extra_large
}

private fun LayoutPreset.summaryRes(): Int = when (this) {
    LayoutPreset.COMPACT -> R.string.onboarding_preset_compact_summary
    LayoutPreset.STANDARD -> R.string.onboarding_preset_standard_summary
    LayoutPreset.LARGE -> R.string.onboarding_preset_large_summary
    LayoutPreset.EXTRA_LARGE -> R.string.onboarding_preset_extra_large_summary
}
