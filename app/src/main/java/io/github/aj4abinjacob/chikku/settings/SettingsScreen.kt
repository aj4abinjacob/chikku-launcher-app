package io.github.aj4abinjacob.chikku.settings

import android.os.Build
import io.github.aj4abinjacob.chikku.settings.sections.CardsSettingsSection
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import io.github.aj4abinjacob.chikku.settings.components.ChikkuAppIcon
import io.github.aj4abinjacob.chikku.settings.components.settingsSummaryColor
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import io.github.aj4abinjacob.chikku.core.model.settings.AnimationSpeed
import io.github.aj4abinjacob.chikku.theme.ProvideMotion
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.theme.chikkuFallbackScheme
import io.github.aj4abinjacob.chikku.theme.withChikkuAccent
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.settings.components.LocalSettingsDeepLink
import io.github.aj4abinjacob.chikku.settings.components.LocalSettingsTopBar
import io.github.aj4abinjacob.chikku.settings.components.SettingsInfoText
import io.github.aj4abinjacob.chikku.settings.components.SettingsTopBarState
import io.github.aj4abinjacob.chikku.settings.components.SettingsNavigationRow
import io.github.aj4abinjacob.chikku.settings.components.SettingsPage
import io.github.aj4abinjacob.chikku.settings.components.SettingsRowIconBox
import io.github.aj4abinjacob.chikku.settings.components.SettingsRowIconSize
import io.github.aj4abinjacob.chikku.settings.components.SettingsSectionHeader
import io.github.aj4abinjacob.chikku.settings.components.settingsPageColor
import io.github.aj4abinjacob.chikku.settings.sections.AboutSettingsSection
import io.github.aj4abinjacob.chikku.settings.sections.AppDrawerSettingsSection
import io.github.aj4abinjacob.chikku.settings.sections.AppLockSettingsSection
import io.github.aj4abinjacob.chikku.settings.sections.BackupSettingsSection
import io.github.aj4abinjacob.chikku.settings.sections.BadgesSettingsSection
import io.github.aj4abinjacob.chikku.settings.sections.DockSettingsSection
import io.github.aj4abinjacob.chikku.settings.sections.FoldersSettingsSection
import io.github.aj4abinjacob.chikku.settings.components.CHIKKU_DEFAULT_ACCENT
import io.github.aj4abinjacob.chikku.settings.sections.GesturesSettingsSection
import io.github.aj4abinjacob.chikku.settings.sections.HomeScreenSettingsSection
import io.github.aj4abinjacob.chikku.settings.sections.LabsSettingsSection
import io.github.aj4abinjacob.chikku.settings.sections.LookFeelSettingsSection
import io.github.aj4abinjacob.chikku.settings.sections.NightModeSettingsSection
import io.github.aj4abinjacob.chikku.settings.sections.SearchSettingsSection

/** Material theme for settings; [accent] (ARGB, from Style) replaces the primary color when set. */
@Composable
fun ChikkuSettingsTheme(accent: Int? = null, speed: AnimationSpeed? = null, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        else -> chikkuFallbackScheme(accent, dark)
    }
    val palette = colors.withChikkuAccent(accent, dark).copy(
        surface = if (dark) Color(0xFF242424) else Color(0xFFFAFAFA),
        surfaceContainer = if (dark) Color(0xFF1C1C1C) else Color(0xFFEEEEEE),
        // Neutral containers instead of the dynamic scheme's tinted ones: bottom sheets draw
        // surfaceContainerLow and dialogs surfaceContainerHigh, and rows inside them use
        // High/Highest as tonal fills, so each step stays a little above the page.
        surfaceContainerLow = if (dark) Color(0xFF2A2A2A) else Color(0xFFFFFFFF),
        surfaceContainerHigh = if (dark) Color(0xFF323232) else Color(0xFFF2F2F2),
        surfaceContainerHighest = if (dark) Color(0xFF3C3C3C) else Color(0xFFE8E8E8),
    )
    MaterialTheme(colorScheme = palette) {
        // No Surface wraps these screens, so set the default text/icon color explicitly (Compose falls back to black).
        CompositionLocalProvider(LocalContentColor provides colors.onSurface) {
            ProvideMotion(speed, content)
        }
    }
}

/** Everything [SettingsRoot] needs from the activity besides the settings themselves. */
data class SettingsHost(
    val isDefaultHome: Boolean?,
    val versionName: String,
    val onSetDefault: () -> Unit,
    val onChooseOtherHome: () -> Unit,
    /** A tap on the version number in About; enough of them in a row toggles Labs. */
    val onVersionTap: () -> Unit = {},
)

/**
 * Settings entry: a list of sections, each opening its own page; back returns to the list.
 * [settings] is null until the stored value has loaded, so no control ever shows a stale default.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRoot(
    settings: LauncherSettings?,
    update: ((LauncherSettings) -> LauncherSettings) -> Unit,
    host: SettingsHost,
    initialSection: SettingsSection? = null,
) {
    var current by rememberSaveable { mutableStateOf(initialSection) }
    // Sub-page a search result opened in [current]; the section reads it once via rememberSettingsSubPage.
    var deepLink by rememberSaveable { mutableStateOf<String?>(null) }
    val topBar = remember { SettingsTopBarState() }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    fun dismissSearch() {
        searching = false
        query = ""
        focus.clearFocus()
        keyboard?.hide()
    }
    BackHandler(enabled = searching || current != null) {
        if (searching) dismissSearch() else { current = null; deepLink = null }
    }
    val section = current
    val pageColor = settingsPageColor()
    // Keep each compact section toolbar pinned while its preference list scrolls.
    key(section, searching) {
        val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = pageColor,
            topBar = {
                // The root page has no bar: its header (icon, title, search) scrolls with the list.
                if (searching || section != null) TopAppBar(
                    title = {
                        Text(
                            text = topBar.subPageTitle.takeUnless { searching || section == null }
                                ?: stringResource(if (searching) R.string.settings_search_title else section?.title ?: R.string.settings_title),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.semantics { heading() },
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            when {
                                searching -> dismissSearch()
                                else -> topBar.subPageBack?.invoke() ?: run { current = null }
                            }
                        }) {
                            Icon(painterResource(R.drawable.ic_settings_back), contentDescription = stringResource(R.string.settings_back))
                        }
                    },
                    actions = {
                        if (searching) {
                            TextButton(onClick = ::dismissSearch) {
                                Text(stringResource(R.string.settings_search_cancel), color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    },
                    // Same surface as the page, a step up once content scrolls beneath; the accent
                    // is left to the actions so the bar reads as part of the page.
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = pageColor,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.primary,
                    ),
                    expandedHeight = 56.dp,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when {
                    searching && settings != null -> SettingsSearchResults(
                        settings, query,
                        onQueryChange = { query = it.take(SettingsSearchIndex.MAX_QUERY_LENGTH) },
                        open = { result -> dismissSearch(); current = result.section; deepLink = result.subPage },
                    )
                    section == SettingsSection.ABOUT -> AboutSettingsSection(
                        host.isDefaultHome, host.versionName, host.onSetDefault, host.onChooseOtherHome, host.onVersionTap,
                    )
                    settings == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    section == null -> SectionList(settings, host, onSearch = { searching = true }) { current = it; deepLink = null }
                    else -> CompositionLocalProvider(
                        LocalSettingsTopBar provides topBar,
                        LocalSettingsDeepLink provides deepLink,
                    ) { SectionContent(section, settings, update) }
                }
            }
        }
    }
}

@Composable
private fun SectionContent(
    section: SettingsSection,
    settings: LauncherSettings,
    update: ((LauncherSettings) -> LauncherSettings) -> Unit,
) {
    when (section) {
        SettingsSection.HOME_SCREEN -> HomeScreenSettingsSection(settings, update)
        SettingsSection.APP_DRAWER -> AppDrawerSettingsSection(settings, update)
        SettingsSection.DOCK -> DockSettingsSection(settings, update)
        SettingsSection.FOLDERS -> FoldersSettingsSection(settings, update)
        SettingsSection.LOOK_AND_FEEL -> LookFeelSettingsSection(settings, update)
        SettingsSection.NIGHT_MODE -> NightModeSettingsSection(settings, update)
        SettingsSection.GESTURES -> GesturesSettingsSection(settings, update)
        SettingsSection.BADGES -> BadgesSettingsSection(settings, update)
        SettingsSection.SEARCH -> SearchSettingsSection(settings, update)
        SettingsSection.CARDS -> CardsSettingsSection(settings, update)
        SettingsSection.APP_LOCK -> AppLockSettingsSection()
        SettingsSection.BACKUP -> BackupSettingsSection(settings, update)
        SettingsSection.LABS -> LabsSettingsSection(settings, update)
        SettingsSection.ABOUT -> Unit // rendered by SettingsRoot with host data
    }
}

@Composable
private fun SectionList(
    settings: LauncherSettings,
    host: SettingsHost,
    onSearch: () -> Unit,
    open: (SettingsSection) -> Unit,
) {
    val visible = SettingsSection.entries.filter { it != SettingsSection.LABS || settings.labs.unlocked }
    SettingsPage {
        SettingsHero(host.versionName, onSearch)
        when (host.isDefaultHome) {
            false -> DefaultHomeBanner(host.onSetDefault)
            true -> DefaultHomeStatus()
            null -> Unit
        }
        visible.groupBy { it.group }.forEach { (group, sections) ->
            SettingsSectionHeader(stringResource(group.title))
            if (group == SettingsGroup.LAYOUT) {
                // The pages people open most are larger targets: a two-column grid of tiles.
                SectionTiles(sections, open)
            } else {
                sections.forEach { section ->
                    SettingsNavigationRow(
                        title = stringResource(section.title),
                        summary = stringResource(section.summary),
                        onClick = { open(section) },
                        leading = { SectionIcon(section) },
                        trailing = {
                            Icon(
                                painterResource(R.drawable.ic_settings_chevron),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                }
            }
        }
    }
}

/** Full-colour app icon, title, version and a search field, at the top of the root page. */
@Composable
private fun SettingsHero(versionName: String, onSearch: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ChikkuAppIcon(size = 60.dp)
            Column {
                Text(
                    text = stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(R.string.settings_version, versionName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val searchLabel = stringResource(R.string.settings_search_title)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .clickable(role = Role.Button, onClick = onSearch)
                .padding(horizontal = 16.dp)
                .semantics(mergeDescendants = true) { contentDescription = searchLabel },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(painterResource(R.drawable.ic_search), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Text(searchLabel, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Shown while Chikku is the home app; the not-default case uses [DefaultHomeBanner]. */
@Composable
private fun DefaultHomeStatus() {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val ok = if (dark) Color(0xFF7CCBAE) else Color(0xFF2F7D63)
    val soft = if (dark) Color(0xFF22372F) else Color(0xFFDCEFE7)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .background(soft, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(8.dp).background(ok, CircleShape))
        Text(stringResource(R.string.settings_status_default), style = MaterialTheme.typography.bodyMedium, color = ok)
    }
}

/** Two-column grid of larger tiles for the layout pages. */
@Composable
private fun SectionTiles(sections: List<SettingsSection>, open: (SettingsSection) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        sections.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { section -> SectionTile(section, Modifier.weight(1f).fillMaxHeight()) { open(section) } }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SectionTile(section: SettingsSection, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 104.dp)
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        SectionIcon(section)
        Column {
            Text(stringResource(section.title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(section.summary), style = MaterialTheme.typography.bodySmall, color = settingsSummaryColor())
        }
    }
}

/** Original section glyphs on a tinted chip, in the settings accent. */
@Composable
private fun SectionIcon(section: SettingsSection) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(scheme.primary.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(section.icon), contentDescription = null, tint = scheme.primary, modifier = Modifier.size(SettingsRowIconSize))
    }
}

/** Compact prompt on the root list; the full status and alternatives live under About. */
@Composable
private fun DefaultHomeBanner(onSetDefault: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(24.dp))
            .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.settings_not_default_banner),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Button(onClick = onSetDefault, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.settings_set_default_short))
        }
    }
}

/** Results name the owning page, making the navigation level explicit. */
@Composable
private fun SettingsSearchResults(
    settings: LauncherSettings,
    query: String,
    onQueryChange: (String) -> Unit,
    open: (SettingsSearchEntry) -> Unit,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val entries = remember(configuration, settings.labs.unlocked) {
        SettingsSearchIndex.entries(context.resources, settings.labs.unlocked)
    }
    val matches = remember(entries, query) { SettingsSearchIndex.search(entries, query) }
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) { requester.requestFocus() }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text(stringResource(R.string.settings_search_query_label)) },
            singleLine = true,
            leadingIcon = { Icon(painterResource(R.drawable.ic_search), null) },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(16.dp).focusRequester(requester),
        )
        when {
            query.isBlank() -> SettingsInfoText(stringResource(R.string.settings_search_hint))
            matches.isEmpty() -> Text(
                stringResource(R.string.settings_search_empty),
                modifier = Modifier.padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> LazyColumn(Modifier.weight(1f)) {
                itemsIndexed(matches, key = { index, item -> "${item.section.name}:${item.title}:$index" }) { _, result ->
                    SettingsNavigationRow(
                        title = result.title,
                        summary = stringResource(
                            R.string.settings_search_open_section,
                            result.subPageTitle?.let { stringResource(R.string.settings_search_section_path, result.sectionTitle, it) }
                                ?: result.sectionTitle,
                        ),
                        onClick = { open(result) },
                        leading = { SectionIcon(result.section) },
                        trailing = { Icon(painterResource(R.drawable.ic_settings_chevron), null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    )
                }
            }
        }
    }
}
