package io.github.aj4abinjacob.chikku.settings.sections

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.IconButton
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.message.rememberChikkuMessages
import io.github.aj4abinjacob.chikku.applock.AppLockState
import io.github.aj4abinjacob.chikku.applock.AppUnlockPrompt
import io.github.aj4abinjacob.chikku.applock.LockTarget
import io.github.aj4abinjacob.chikku.applock.RelockMode
import io.github.aj4abinjacob.chikku.core.model.AppsState
import io.github.aj4abinjacob.chikku.core.model.LauncherApp
import io.github.aj4abinjacob.chikku.home.HomeDependencies
import io.github.aj4abinjacob.chikku.settings.LocalAppUnlockPrompt
import io.github.aj4abinjacob.chikku.settings.LocalHomeDependencies
import io.github.aj4abinjacob.chikku.settings.components.AppIconImage
import io.github.aj4abinjacob.chikku.settings.components.LocalDrawerScrollSettings
import io.github.aj4abinjacob.chikku.settings.components.listKey
import io.github.aj4abinjacob.chikku.settings.components.SettingsFastScroller
import io.github.aj4abinjacob.chikku.settings.components.SettingsInfoText
import io.github.aj4abinjacob.chikku.settings.components.SettingsSectionHeader
import io.github.aj4abinjacob.chikku.settings.components.rememberAppScrollIndex
import io.github.aj4abinjacob.chikku.settings.components.settingsFastScrollerPadding
import io.github.aj4abinjacob.chikku.settings.components.settingsPageColor
import io.github.aj4abinjacob.chikku.settings.components.settingsSegment
import io.github.aj4abinjacob.chikku.settings.drawer.AppsStatus
import io.github.aj4abinjacob.chikku.settings.drawer.appTitle
import io.github.aj4abinjacob.chikku.settings.drawer.rememberAppsState
import kotlinx.coroutines.launch

/**
 * App lock: which apps need the system screen lock to open from Chikku, and how long an unlock
 * lasts. While any app is locked, this page itself needs the same confirmation, and it locks
 * again whenever settings leaves the screen. App labels are shown on screen only, never logged.
 */
@Composable
fun AppLockSettingsSection() {
    val deps = LocalHomeDependencies.current
    val prompt = LocalAppUnlockPrompt.current
    val lockState by deps.appLock.state.collectAsStateWithLifecycle()
    var confirmed by remember { mutableStateOf(false) }
    // Ask automatically when the page opens or returns to the screen, not after "Lock this page".
    var askOnShow by remember { mutableStateOf(true) }
    var secure by remember { mutableStateOf(prompt.isDeviceSecure()) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        confirmed = false
        askOnShow = true
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { secure = prompt.isDeviceSecure() }

    val guarded = lockState.locked.isNotEmpty() || lockState.readFailed
    LaunchedEffect(lockState.loaded, guarded, confirmed, askOnShow) {
        if (lockState.loaded && guarded && !confirmed && askOnShow) {
            askOnShow = false
            prompt.confirmOwner { confirmed = true }
        }
    }
    when {
        !lockState.loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        guarded && !confirmed -> OwnerGate(onUnlock = { prompt.confirmOwner { confirmed = true } })
        else -> AppLockContent(
            deps, prompt, lockState, secure,
            onLockPage = if (guarded) ({ confirmed = false }) else null,
        )
    }
}

@Composable
private fun OwnerGate(onUnlock: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        LockBadge(locked = true, size = 112)
        Spacer(Modifier.height(28.dp))
        Text(
            stringResource(R.string.app_lock_gate_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.app_lock_gate_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Button(onClick = onUnlock, modifier = Modifier.heightIn(min = 48.dp)) {
            Icon(painterResource(R.drawable.ic_settings_lock), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.app_lock_gate_unlock))
        }
    }
}

@Composable
private fun AppLockContent(
    deps: HomeDependencies,
    prompt: AppUnlockPrompt,
    lockState: AppLockState,
    secure: Boolean,
    onLockPage: (() -> Unit)?,
) {
    val context = LocalContext.current
    val messages = rememberChikkuMessages()
    val appsState by rememberAppsState(deps)
    var query by rememberSaveable { mutableStateOf("") }
    val apps = (appsState as? AppsState.Loaded)?.apps.orEmpty()
    val locked = lockState.locked
    // Locked apps first; otherwise the drawer's alphabetical order.
    val shown = remember(apps, locked, query) {
        val needle = query.trim()
        apps.filter { needle.isEmpty() || it.label.contains(needle, ignoreCase = true) }
            .sortedByDescending { LockTarget(it.key) in locked }
    }
    val lockedCount = apps.count { LockTarget(it.key) in locked }
    val listState = rememberLazyListState()
    val searchScope = rememberCoroutineScope()
    val lockedShown = shown.filter { LockTarget(it.key) in locked }
    val otherShown = shown.filter { LockTarget(it.key) !in locked }
    // Items above the first group header: summary, info, pinned search, status.
    val headItems = 4 + (if (lockState.readFailed) 1 else 0) + (if (!secure) 1 else 0)
    // Locked apps lead; the "Other apps" header between the two groups is one more item to skip.
    val scrollIndex = rememberAppScrollIndex(
        shown, headItems + 1,
        alphabeticalFrom = lockedShown.size,
        alphabeticalGap = if (lockedShown.isNotEmpty() && otherShown.isNotEmpty()) 1 else 0,
    ) { it.label }

    fun save(block: suspend () -> Boolean) {
        // Process scope: leaving the page cannot cancel a write half way.
        deps.appScope.launch {
            if (!block()) messages.show(R.string.app_lock_save_failed)
        }
    }

    fun toggle(app: LauncherApp, lock: Boolean) {
        val target = LockTarget(app.key)
        when {
            !lock -> save { deps.appLock.setLocked(target, false) }
            // The first lock proves the screen lock works here, so the owner cannot lock themselves out.
            locked.isEmpty() -> prompt.confirmOwner { save { deps.appLock.setLocked(target, true) } }
            else -> save { deps.appLock.setLocked(target, true) }
        }
    }

    // imePadding: edge-to-edge does not resize for the keyboard, which would cover the matches.
    Box(Modifier.fillMaxSize().imePadding()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = settingsFastScrollerPadding(bottom = 24.dp),
        ) {
            item(key = "summary") {
                Summary(
                    lockedCount = lockedCount,
                    relock = lockState.relock,
                    enabled = !lockState.readFailed,
                    onRelock = { mode -> save { deps.appLock.setRelock(mode) } },
                    onLockPage = onLockPage,
                )
            }
            if (lockState.readFailed) {
                item(key = "read-failed") { Notice(stringResource(R.string.app_lock_read_failed), action = null) }
            }
            if (!secure) {
                item(key = "no-screen-lock") {
                    Notice(
                        title = stringResource(R.string.app_lock_no_screen_lock_title),
                        text = stringResource(R.string.app_lock_no_screen_lock_body),
                        action = stringResource(R.string.app_lock_set_screen_lock) to {
                            try {
                                context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
                            } catch (_: ActivityNotFoundException) {
                                messages.show(R.string.app_lock_prompt_unavailable)
                            }
                        },
                    )
                }
            }
            item(key = "scope") {
                SettingsInfoText(
                    stringResource(R.string.app_lock_scope_short),
                    details = stringResource(R.string.app_lock_scope) + "\n\n" + stringResource(R.string.app_lock_scope_details),
                )
            }
            // Pinned while the apps scroll under it, so filtering stays one tap away on a long list.
            stickyHeader(key = "search") {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.take(MAX_QUERY) },
                    label = { Text(stringResource(R.string.app_lock_search_label)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        // Bring the pinned field to the top so the matches sit above the keyboard.
                        .onFocusChanged { if (it.isFocused) searchScope.launch { listState.animateScrollToItem(headItems - 2) } }
                        .background(settingsPageColor())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            item(key = "status") { AppsStatus(appsState) }
            if (appsState is AppsState.Loaded && shown.isEmpty()) {
                item(key = "empty") {
                    Text(
                        stringResource(R.string.app_lock_no_matches),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
            appGroup(
                header = R.string.app_lock_locked_header, key = "locked-header", apps = lockedShown,
                locked = locked, lockState = lockState, secure = secure, deps = deps, onToggle = ::toggle,
            )
            appGroup(
                header = if (lockedShown.isEmpty()) R.string.app_lock_apps_header else R.string.app_lock_other_header,
                key = "other-header", apps = otherShown,
                locked = locked, lockState = lockState, secure = secure, deps = deps, onToggle = ::toggle,
            )
        }
        SettingsFastScroller(
            state = listState,
            // Locked apps lead the list, so any letter can be at the top: a letter bubble would mislead.
            drawer = LocalDrawerScrollSettings.current.copy(fastScrollerPopup = false),
            sections = scrollIndex.sections,
            letterAt = scrollIndex::letterAt,
        )
    }
}

private fun LazyListScope.appGroup(
    header: Int,
    key: String,
    apps: List<LauncherApp>,
    locked: Set<LockTarget>,
    lockState: AppLockState,
    secure: Boolean,
    deps: HomeDependencies,
    onToggle: (LauncherApp, Boolean) -> Unit,
) {
    if (apps.isEmpty()) return
    item(key = key) { SettingsSectionHeader(stringResource(header)) }
    itemsIndexed(apps, key = { _, app -> app.key.listKey() }) { index, app ->
        val isLocked = LockTarget(app.key) in locked
        AppLockRow(
            app = app,
            locked = isLocked,
            enabled = !lockState.readFailed && (secure || isLocked),
            index = index,
            count = apps.size,
            deps = deps,
            onToggle = { onToggle(app, it) },
            modifier = Modifier.animateItem(),
        )
    }
}

/** Status and "ask again" choice in one compact card; the long explanation lives behind "Learn more". */
@Composable
private fun Summary(
    lockedCount: Int,
    relock: RelockMode,
    enabled: Boolean,
    onRelock: (RelockMode) -> Unit,
    onLockPage: (() -> Unit)?,
) {
    val scheme = MaterialTheme.colorScheme
    val active = lockedCount > 0
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .background(scheme.surfaceContainerHigh, RoundedCornerShape(28.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LockBadge(locked = active, size = 48)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                AnimatedContent(targetState = lockedCount, label = "app-lock-count") { count ->
                    Text(
                        if (count == 0) stringResource(R.string.app_lock_none_locked)
                        else pluralStringResource(R.plurals.app_lock_hero_count, count, count),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { heading() },
                    )
                }
                Text(
                    stringResource(if (active) relock.summary() else R.string.app_lock_pick_apps),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
            if (onLockPage != null) {
                IconButton(onClick = onLockPage) {
                    Icon(
                        painterResource(R.drawable.ic_settings_lock),
                        contentDescription = stringResource(R.string.app_lock_relock_page),
                    )
                }
            }
        }
        Text(
            stringResource(R.string.app_lock_relock_title),
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant,
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            RelockMode.entries.forEachIndexed { i, mode ->
                SegmentedButton(
                    selected = relock == mode,
                    onClick = { onRelock(mode) },
                    shape = SegmentedButtonDefaults.itemShape(i, RelockMode.entries.size),
                    enabled = enabled,
                    icon = {},
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    label = {
                        Text(
                            stringResource(mode.label()),
                            style = MaterialTheme.typography.labelMedium,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                        )
                    },
                )
            }
        }
    }
}

/** Round lock glyph; closes with a small spring when something becomes locked. */
@Composable
private fun LockBadge(locked: Boolean, size: Int, onContainer: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    val tint = if (onContainer) scheme.onPrimaryContainer else scheme.primary
    val fill by animateColorAsState(
        if (locked) tint.copy(alpha = 0.16f) else tint.copy(alpha = 0.08f),
        label = "lock-badge-fill",
    )
    val scale by animateFloatAsState(if (locked) 1f else 0.94f, spring(Spring.DampingRatioMediumBouncy), label = "lock-badge-scale")
    Box(
        modifier = Modifier.size(size.dp).scale(scale).background(fill, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = locked,
            transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.7f)) togetherWith (fadeOut() + scaleOut(targetScale = 0.7f)) },
            label = "lock-badge-icon",
        ) { isLocked ->
            Icon(
                painterResource(if (isLocked) R.drawable.ic_lock_shield else R.drawable.ic_lock_open),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size((size * 0.46f).dp),
            )
        }
    }
}

@Composable
private fun Notice(title: String, text: String? = null, action: Pair<String, () -> Unit>?) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .background(scheme.errorContainer, RoundedCornerShape(24.dp))
            .padding(20.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = scheme.onErrorContainer)
        if (text != null) {
            Spacer(Modifier.height(4.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = scheme.onErrorContainer)
        }
        if (action != null) {
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(onClick = action.second, modifier = Modifier.heightIn(min = 48.dp)) { Text(action.first) }
        }
    }
}

@Composable
private fun AppLockRow(
    app: LauncherApp,
    locked: Boolean,
    enabled: Boolean,
    index: Int,
    count: Int,
    deps: HomeDependencies,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val stateText = stringResource(if (locked) R.string.app_lock_state_locked else R.string.app_lock_state_unlocked)
    Row(
        modifier = modifier
            .settingsSegment(index, count)
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .toggleable(value = locked, enabled = enabled, role = Role.Switch, onValueChange = onToggle)
            .semantics { stateDescription = stateText }
            .alpha(if (enabled) 1f else 0.38f)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app, deps)
        Spacer(Modifier.width(16.dp))
        Text(
            appTitle(app),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        LockToggle(locked)
    }
}

/** Filled accent circle with a closed lock when locked; quiet outline with an open lock otherwise. */
@Composable
private fun LockToggle(locked: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val fill by animateColorAsState(if (locked) scheme.primary else scheme.surface, label = "lock-toggle-fill")
    val tint by animateColorAsState(if (locked) scheme.onPrimary else scheme.onSurfaceVariant, label = "lock-toggle-tint")
    val border by animateColorAsState(if (locked) scheme.primary else scheme.outlineVariant, label = "lock-toggle-border")
    val scale by animateFloatAsState(if (locked) 1f else 0.9f, spring(Spring.DampingRatioMediumBouncy), label = "lock-toggle-scale")
    Box(
        modifier = Modifier
            .size(40.dp)
            .scale(scale)
            .background(fill, CircleShape)
            .border(1.5.dp, border, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = locked,
            transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.6f)) togetherWith (fadeOut() + scaleOut(targetScale = 0.6f)) },
            label = "lock-toggle-icon",
        ) { isLocked ->
            Icon(
                painterResource(if (isLocked) R.drawable.ic_settings_lock else R.drawable.ic_lock_open),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun AppIcon(app: LauncherApp, deps: HomeDependencies) {
    AppIconImage(app.key, 40.dp, deps.iconProvider, contentAlignment = Alignment.Center)
}

private fun RelockMode.label(): Int = when (this) {
    RelockMode.EVERY_LAUNCH -> R.string.app_lock_relock_every_launch
    RelockMode.AFTER_ONE_MINUTE -> R.string.app_lock_relock_one_minute
    RelockMode.AFTER_FIVE_MINUTES -> R.string.app_lock_relock_five_minutes
    RelockMode.SCREEN_OFF -> R.string.app_lock_relock_screen_off
}

private fun RelockMode.summary(): Int = when (this) {
    RelockMode.EVERY_LAUNCH -> R.string.app_lock_relock_summary_every_launch
    RelockMode.AFTER_ONE_MINUTE -> R.string.app_lock_relock_summary_one_minute
    RelockMode.AFTER_FIVE_MINUTES -> R.string.app_lock_relock_summary_five_minutes
    RelockMode.SCREEN_OFF -> R.string.app_lock_relock_summary_screen_off
}

private const val MAX_QUERY = 100
