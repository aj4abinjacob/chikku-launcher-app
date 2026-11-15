package io.github.aj4abinjacob.chikku.settings.drawer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import io.github.aj4abinjacob.chikku.theme.LocalMotion
import io.github.aj4abinjacob.chikku.theme.tweenSpec
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.max
import io.github.aj4abinjacob.chikku.settings.components.AppSearchField
import io.github.aj4abinjacob.chikku.settings.components.settingsPageColor
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.message.rememberChikkuMessages
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.AppsState
import io.github.aj4abinjacob.chikku.core.model.drawer.DrawerModel
import io.github.aj4abinjacob.chikku.core.model.search.CustomLabelSearch
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerSettings
import io.github.aj4abinjacob.chikku.home.IconBinder
import io.github.aj4abinjacob.chikku.platform.LaunchResult
import io.github.aj4abinjacob.chikku.settings.LocalAppUnlockPrompt
import io.github.aj4abinjacob.chikku.settings.LocalHomeDependencies

/** Drawer-style multi-selection; checked apps remain here so they can also be unhidden. */
@Composable
fun HiddenAppsScreen(drawerSettings: DrawerSettings, desktopIconScale: Float = 1f) {
    val settings = drawerSettings.withEffectiveIconScale(desktopIconScale)
    val deps = LocalHomeDependencies.current
    val unlocker = LocalAppUnlockPrompt.current
    val context = LocalContext.current
    val messages = rememberChikkuMessages()
    val scope = rememberCoroutineScope()
    val binder = remember(context, scope, deps) { IconBinder(context, scope, deps.iconProvider) }
    val appsState by rememberAppsState(deps)
    val customizations by rememberCustomizations(deps)
    val hidden = DrawerModel.hiddenKeys(customizations)
    // Keep the repository's alphabetical order and section labels, just as the All apps drawer does.
    val apps = (appsState as? AppsState.Loaded)?.apps.orEmpty()
    val hiddenCount = apps.count { it.key in hidden }
    var query by rememberSaveable { mutableStateOf("") }
    var hiddenOnly by rememberSaveable { mutableStateOf(false) }
    // Filtered in place, so the grid keeps its alphabetical order and section letters.
    val shown = remember(apps, hidden, query, hiddenOnly) {
        val matches = if (query.isBlank()) null else deps.appSearcher.search(apps, query.trim()).mapTo(HashSet()) { it.key }
        apps.filter { (matches == null || it.key in matches) && (!hiddenOnly || it.key in hidden) }
    }
    val labelColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val accentColor = MaterialTheme.colorScheme.primary.toArgb()
    val onAccentColor = MaterialTheme.colorScheme.onPrimary.toArgb()
    val surfaceColor = settingsPageColor().toArgb()
    var menuRequest by remember { mutableStateOf<HiddenAppMenuRequest?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }
    // An app that disappears (uninstalled, profile locked) while its menu is open takes the menu with it.
    val menuShown = menuExpanded && menuRequest?.key?.let { key -> apps.any { it.key == key } } == true

    fun openApp(key: AppKey) {
        fun open() {
            when (val result = try { deps.appLauncher.launch(key, null) } catch (_: Exception) { null }) {
                LaunchResult.Started -> Unit
                is LaunchResult.Locked -> unlocker.unlock(result, ::open)
                else -> messages.show(R.string.drawer_open_failed)
            }
        }
        open()
    }

    // The hint gives way once the grid is scrolled and returns at its top, so on a short screen or
    // with large text it does not leave room for only a couple of rows. Search and filter stay put.
    var gridAtTop by remember { mutableStateOf(true) }

    Column(Modifier.fillMaxSize()) {
        val motion = LocalMotion.current
        AnimatedVisibility(
            gridAtTop,
            enter = expandVertically(motion.tweenSpec(220)) + fadeIn(motion.tweenSpec(220)),
            exit = shrinkVertically(motion.tweenSpec(220)) + fadeOut(motion.tweenSpec(120)),
        ) {
            HiddenAppsHint()
        }
        // Hundreds of apps: search, or narrow to the hidden ones to review or unhide them. The
        // filter carries the hidden count, so it needs no line of its own.
        val countText = pluralStringResource(R.plurals.drawer_hidden_selected_count, hiddenCount, hiddenCount)
        val filterDescription = stringResource(R.string.drawer_hidden_only_description, countText)
        SearchBesideFilter(Modifier.fillMaxWidth().padding(horizontal = 16.dp), search = {
            AppSearchField(query, { query = it }, settingsPageColor())
        }) {
            // Same height and pill shape as the search field.
            FilterChip(
                selected = hiddenOnly,
                onClick = { hiddenOnly = !hiddenOnly },
                // "0 hidden" reads as an error; with nothing hidden the filter just names itself.
                label = {
                    Text(
                        if (hiddenCount == 0) stringResource(R.string.drawer_hidden_only)
                        else pluralStringResource(R.plurals.drawer_hidden_filter, hiddenCount, hiddenCount),
                    )
                },
                shape = RoundedCornerShape(28.dp),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = hiddenOnly,
                    borderColor = Color.Transparent,
                ),
                modifier = Modifier.heightIn(min = 56.dp).semantics { contentDescription = filterDescription },
            )
        }
        AppsStatus(appsState)
        if (appsState is AppsState.Loaded && shown.isEmpty()) {
            Text(
                stringResource(if (hiddenOnly && query.isBlank()) R.string.drawer_hidden_none else R.string.app_picker_no_matches),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            )
        }
        // The grid and its menu share one origin so the menu can be anchored at the held cell.
        Box(Modifier.fillMaxWidth().weight(1f)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { viewContext ->
                    HiddenAppsGridView(
                        viewContext,
                        binder,
                        onHiddenChanged = { key, checked ->
                            deps.write(context) { deps.appCustomizationRepository.setHidden(key, checked) }
                        },
                        onOpen = ::openApp,
                        onAtTopChanged = { gridAtTop = it },
                        onLongPress = { request ->
                            menuRequest = request
                            menuExpanded = true
                        },
                    )
                },
                update = {
                    it.motion = motion
                    it.render(shown, hidden, settings, labelColor, accentColor, CustomLabelSearch.customLabels(customizations),
                        onAccentColor, surfaceColor)
                },
                onRelease = { it.release() },
            )
            menuRequest?.let { request ->
                HiddenAppMenu(
                    request = request,
                    expanded = menuShown,
                    onOpen = ::openApp,
                    onDismiss = { menuExpanded = false },
                )
            }
        }
    }
}

/**
 * The page's hint with "Learn more" inline at its end. The shared info row puts the link on a 48dp
 * line of its own, which left a band of empty space above this page's search field; here the whole
 * hint is the touch target instead.
 */
@Composable
private fun HiddenAppsHint() {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val link = stringResource(if (expanded) R.string.settings_show_less else R.string.settings_learn_more)
    val linkColor = MaterialTheme.colorScheme.primary
    val hint = stringResource(R.string.drawer_hidden_selection_hint)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClickLabel = link) { expanded = !expanded }
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_settings_info),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 1.dp, end = 10.dp).size(16.dp),
        )
        Column(Modifier.animateContentSize()) {
            Text(
                buildAnnotatedString {
                    append(hint)
                    append("  ")
                    withStyle(SpanStyle(color = linkColor, fontWeight = FontWeight.Medium)) { append(link) }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (expanded) {
                Text(
                    stringResource(R.string.drawer_hidden_explanation_details),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

/** Narrowest the search field may get beside the filter before the filter moves under it. */
private val MinSearchWidth = 200.dp

/**
 * [search] with [filter] at its end while the search keeps at least [MinSearchWidth]; on a narrow
 * screen or with large text the filter drops below the search instead of squeezing it to "Sear…".
 */
@Composable
private fun SearchBesideFilter(modifier: Modifier, search: @Composable () -> Unit, filter: @Composable () -> Unit) {
    Layout(contents = listOf(search, filter), modifier = modifier) { (searchPart, filterPart), constraints ->
        val gap = 8.dp.roundToPx()
        val width = constraints.maxWidth
        val chip = filterPart.single().measure(Constraints(maxWidth = width))
        val besideWidth = width - chip.width - gap
        if (besideWidth >= MinSearchWidth.roundToPx()) {
            val field = searchPart.single().measure(Constraints.fixedWidth(besideWidth))
            val height = max(field.height, chip.height)
            layout(width, height) {
                field.placeRelative(0, (height - field.height) / 2)
                chip.placeRelative(width - chip.width, (height - chip.height) / 2)
            }
        } else {
            val field = searchPart.single().measure(Constraints.fixedWidth(width))
            layout(width, field.height + chip.height + gap) {
                field.placeRelative(0, 0)
                chip.placeRelative(0, field.height)
            }
        }
    }
}

/** The drawer's own rule (DrawerController.iconScale): Match desktop size uses the home icon scale. */
internal fun DrawerSettings.withEffectiveIconScale(desktopIconScale: Float): DrawerSettings =
    if (matchDesktopSize) copy(iconScale = desktopIconScale) else this
