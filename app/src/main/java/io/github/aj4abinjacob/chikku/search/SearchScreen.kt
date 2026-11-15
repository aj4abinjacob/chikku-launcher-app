package io.github.aj4abinjacob.chikku.search

import android.graphics.BitmapFactory
import android.os.Build
import android.graphics.drawable.Drawable
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.key
import io.github.aj4abinjacob.chikku.theme.LocalMotion
import io.github.aj4abinjacob.chikku.theme.Motion
import io.github.aj4abinjacob.chikku.theme.spec
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import io.github.aj4abinjacob.chikku.core.model.search.HistoryTarget
import io.github.aj4abinjacob.chikku.core.model.search.RecentItem
import io.github.aj4abinjacob.chikku.prompt.PromptAction
import io.github.aj4abinjacob.chikku.prompt.PromptSheet
import io.github.aj4abinjacob.chikku.prompt.PromptSpec
import io.github.aj4abinjacob.chikku.prompt.PromptTone
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.border
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.aj4abinjacob.chikku.home.appmenu.AppMenuHandle
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.theme.withChikkuAccent
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.LauncherApp
import io.github.aj4abinjacob.chikku.core.model.search.CalculationResult
import io.github.aj4abinjacob.chikku.core.model.search.ContactHit
import io.github.aj4abinjacob.chikku.core.model.search.ContactCardButton
import io.github.aj4abinjacob.chikku.core.model.search.ContactServices
import io.github.aj4abinjacob.chikku.core.model.search.MicroResult
import io.github.aj4abinjacob.chikku.core.model.search.SearchResults
import io.github.aj4abinjacob.chikku.core.model.search.SearchRow
import io.github.aj4abinjacob.chikku.core.model.search.WebSuggestions
import io.github.aj4abinjacob.chikku.core.model.search.WebSearchUrls
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.core.model.settings.SearchSettings
import io.github.aj4abinjacob.chikku.core.model.settings.SearchPresentation
import io.github.aj4abinjacob.chikku.core.model.settings.SearchResultsAnchor
import io.github.aj4abinjacob.chikku.core.model.settings.SearchAppPriorityDirection
import io.github.aj4abinjacob.chikku.theme.LauncherSurface
import io.github.aj4abinjacob.chikku.theme.NightModeResolver
import io.github.aj4abinjacob.chikku.theme.SurfaceTone
import io.github.aj4abinjacob.chikku.platform.IconProvider
import io.github.aj4abinjacob.chikku.settings.components.CHIKKU_DEFAULT_ACCENT
import io.github.aj4abinjacob.chikku.settings.components.rememberAppIconBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first

/**
 * Dark when night mode applies and "dark search" is on (see `SurfaceColors`). Accent colors
 * follow the user's Chikku accent like [io.github.aj4abinjacob.chikku.settings.ChikkuSettingsTheme];
 * surfaces keep search's own look (dynamic surface, pure black at night, custom background).
 */
@Composable
fun SearchTheme(settings: LauncherSettings?, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val night = settings?.night
    // Same rule as every other surface (style settings SurfaceColors / NightModeResolver).
    val dark = if (night == null) systemDark else NightModeResolver.tone(LauncherSurface.SEARCH, night, systemDark) != SurfaceTone.LIGHT
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }.withChikkuAccent(settings?.lookFeel?.accentColor, dark).let { if (dark && night?.pureBlack == true) it.copy(surface = Color.Black) else it }.let { palette ->
        settings?.search?.backgroundColor?.let { custom ->
            val background = Color(custom).copy(alpha = 1f)
            val foreground = if (background.luminance() < 0.18f) Color.White else Color(0xFF1B1B1F)
            // Containers (the query field, cards) too: left on the dynamic scheme, a light custom
            // background in system dark mode put dark text on a dark field.
            val container = foreground.copy(alpha = .08f).compositeOver(background)
            val containerHigh = foreground.copy(alpha = .12f).compositeOver(background)
            // The accent stays when it reads on the chosen background; otherwise fall back to the text color.
            val primary = palette.primary.takeIf { contrastRatio(it, background) >= MIN_ACCENT_CONTRAST } ?: foreground
            palette.copy(surface = background, onSurface = foreground, onSurfaceVariant = foreground.copy(alpha = .8f),
                primary = primary, onPrimary = if (primary.luminance() > 0.179f) Color.Black else Color.White,
                secondaryContainer = foreground.copy(alpha = .12f), onSecondaryContainer = foreground,
                surfaceVariant = container, surfaceContainerLowest = background, surfaceContainerLow = container,
                surfaceContainer = container, surfaceContainerHigh = containerHigh, surfaceContainerHighest = containerHigh)
        } ?: palette
    }
    MaterialTheme(colorScheme = colors) {
        // No Surface wraps the screen, so set the default text/icon color explicitly (Compose falls back to black).
        // Read per composition so returning from Android's "Remove animations" takes effect.
        val motion = Motion.current(settings?.lookFeel?.animationSpeed ?: io.github.aj4abinjacob.chikku.core.model.settings.AnimationSpeed.NORMAL)
        CompositionLocalProvider(LocalContentColor provides colors.onSurface, LocalMotion provides motion, content = content)
    }
}

/** WCAG non-text contrast (3:1) for accent headers and icons on a custom search background. */
private const val MIN_ACCENT_CONTRAST = 3f

internal fun contrastRatio(a: Color, b: Color): Float {
    val la = a.luminance() + 0.05f
    val lb = b.luminance() + 0.05f
    return if (la > lb) la / lb else lb / la
}

/** [title] 0: the section draws its own heading (the person card). */
private enum class Section(@StringRes val title: Int) {
    PERSON(0),
    CALCULATOR(R.string.search_section_calculator),
    MICRO(R.string.search_section_micro),
    APPS(R.string.search_section_apps),
    FOLDERS(R.string.search_section_folders),
    SHORTCUTS(R.string.search_section_shortcuts),
    CONTACTS(R.string.search_section_contacts),
    RECENT(R.string.search_section_recent),
    DID_YOU_MEAN(R.string.search_section_did_you_mean),
    WEB(R.string.search_section_web),
}

private val SearchRow.section: Section
    get() = when (this) {
        is SearchRow.Person -> Section.PERSON
        is SearchRow.Calculation -> Section.CALCULATOR
        is SearchRow.Micro -> Section.MICRO
        is SearchRow.App -> Section.APPS
        is SearchRow.Folder -> Section.FOLDERS
        is SearchRow.Shortcut -> Section.SHORTCUTS
        is SearchRow.Contact, SearchRow.ContactsPermissionNeeded -> Section.CONTACTS
        is SearchRow.SuggestedApp, is SearchRow.SuggestedContact -> Section.DID_YOU_MEAN
        is SearchRow.Recent -> Section.RECENT
        is SearchRow.WebSearch, is SearchRow.PlayStore -> Section.WEB
    }

/** The row the IME "Go" key performs: the best local match, else the web row. */
internal fun SearchResults.goRow(): SearchRow? =
    rows.firstOrNull { it is SearchRow.Person || it is SearchRow.App || it is SearchRow.Folder || it is SearchRow.Shortcut || it is SearchRow.Contact }
        ?: rows.firstOrNull { it is SearchRow.Micro && it.result is MicroResult.Action }
        ?: rows.firstOrNull { it is SearchRow.WebSearch }

/** Adds Android's standard no-learning request after Compose creates the real editor connection. */
@OptIn(ExperimentalComposeUiApi::class)
internal fun privateKeyboardRequest(request: PlatformTextInputMethodRequest, enabled: Boolean): PlatformTextInputMethodRequest =
    object : PlatformTextInputMethodRequest {
        override fun createInputConnection(outAttributes: EditorInfo): InputConnection {
            val connection = request.createInputConnection(outAttributes)
            if (enabled) outAttributes.imeOptions = outAttributes.imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            return connection
        }
    }

/** Limits only app rows; contacts, shortcuts and explicit web actions retain their own availability. */
internal fun limitSearchAppRows(rows: List<SearchRow>, maximum: Int): List<SearchRow> {
    if (maximum <= 0) return rows
    var appCount = 0
    return rows.filter { it !is SearchRow.App || ++appCount <= maximum }
}

/** What the empty-search suggestions can do with search history; the host wires them to the store and settings. */
class SearchHistoryControls(
    val onForget: (HistoryTarget) -> Unit,
    val onClear: () -> Unit,
    val onSetLearning: (Boolean) -> Unit,
    val onOpenSettings: () -> Unit,
)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SearchScreen(
    initialQuery: String,
    results: SearchResults,
    iconProvider: IconProvider,
    onQueryChange: (String) -> Unit,
    onRowClick: (SearchRow) -> Unit,
    onClose: () -> Unit,
    suggestions: WebSuggestions = WebSuggestions(),
    settings: SearchSettings = SearchSettings(),
    onAppDrag: ((LauncherApp, View) -> Boolean)? = null,
    /** Long-press app menu (shortcuts, App info, ...) shown before a drag; null drags at once. */
    onAppMenu: ((LauncherApp, View) -> AppMenuHandle?)? = null,
    onPersonAction: (ContactHit, PersonAction) -> Unit = { contact, _ -> onRowClick(SearchRow.Person(contact)) },
    notice: SearchNotice? = null,
    onNoticeShown: (SearchNotice) -> Unit = {},
    historyControls: SearchHistoryControls? = null,
    /** Records taps so a launched result can animate from where it was tapped. */
    tapOrigin: TapOrigin? = null,
) {
    val snackbar = remember { SnackbarHostState() }
    val noticeText = notice?.let { stringResource(it.message) }
    LaunchedEffect(notice) {
        // A newer notice restarts this effect, which cancels (dismisses) the one on screen.
        if (notice != null && noticeText != null) {
            snackbar.showSnackbar(noticeText)
            onNoticeShown(notice)
        }
    }
    var field by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initialQuery, TextRange(initialQuery.length)))
    }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val windowInfo = LocalWindowInfo.current
    var fieldPlaced by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // HOME overlays and new activity windows can compose before the editor is ready.
        // Request once per opening so Back can still dismiss the keyboard afterwards.
        snapshotFlow { fieldPlaced && windowInfo.isWindowFocused }.first { it }
        focusRequester.requestFocus()
        // Let Compose start the focused field's input connection before requesting the IME.
        withFrameNanos { }
        keyboard?.show()
    }
    // Results lag the field by the debounce. Keep showing the previous list meanwhile (no flicker),
    // but the IME action only acts on results for exactly what is typed.
    val current = results.takeIf { it.query.trim() == field.text.trim() }
    val shown = results.takeIf { it.query.isNotBlank() || it.apps.isNotEmpty() || it.recent.isNotEmpty() }
    val currentSuggestions = suggestions.takeIf { it.query == field.text.trim() } ?: WebSuggestions()

    InterceptPlatformTextInput(
        interceptor = { request, next -> next.startInputMethod(privateKeyboardRequest(request, settings.privateKeyboard)) },
    ) {
        val windowed = settings.presentation == SearchPresentation.WINDOW
        val darkSurface = MaterialTheme.colorScheme.surface.luminance() < 0.5f
        val nearKeyboard = settings.resultsAnchor == SearchResultsAnchor.KEYBOARD
        // Window mode docks the panel to the keyboard when results are kept near it, so the setting still means something.
        val dockBottom = windowed && nearKeyboard
        BoxWithConstraints(
            Modifier.fillMaxSize().recordTaps(tapOrigin)
                .then(if (windowed) Modifier.background(Color.Black.copy(alpha = if (darkSurface) .6f else .45f)) else Modifier)
                .systemBarsPadding().imePadding(),
            contentAlignment = if (dockBottom) Alignment.BottomCenter else Alignment.TopCenter,
        ) {
            // The scrim is a pointer target only: TalkBack users dismiss with Back, not a second "back" button.
            if (windowed) Box(Modifier.fillMaxSize().clearAndSetSemantics { }.clickable(interactionSource = null, indication = null, onClick = onClose))
            // Capped corners keep the panel a card (a 48dp radius would make a pill inside a pill); never full screen.
            val shape = RoundedCornerShape(
                (if (windowed) settings.cornerRadiusDp.coerceIn(0f, 28f) else settings.cornerRadiusDp.coerceIn(0f, 48f)).dp,
            )
            val panelMax = maxOf(maxHeight * 0.72f, minOf(maxHeight - 16.dp, 360.dp)).coerceAtLeast(120.dp)
            val paneTitle = stringResource(R.string.search_hint)
            val outline = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f)
            Column(
                Modifier.then(
                    if (windowed) Modifier.padding(horizontal = 16.dp, vertical = 8.dp).widthIn(max = 560.dp)
                        .fillMaxWidth().heightIn(max = panelMax)
                        .shadow(12.dp, shape).clip(shape).border(1.dp, outline, shape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .semantics { this.paneTitle = paneTitle }
                    else Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
                )
                    // Hit-testable, so taps on empty panel space don't reach the window scrim behind it. It must
                    // not consume: an ancestor consuming moves cancels the result list's drag before touch slop,
                    // so only quick flicks scrolled.
                    .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
            ) {
                val fieldRow: @Composable () -> Unit = {
                    TextField(
                        value = field,
                        onValueChange = {
                            field = it
                            onQueryChange(it.text)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (windowed) Modifier.heightIn(min = 56.dp) else Modifier.padding(12.dp))
                            .focusRequester(focusRequester)
                            .onGloballyPositioned { fieldPlaced = true },
                        placeholder = { Text(stringResource(R.string.search_hint)) },
                        leadingIcon = {
                            if (windowed) {
                                // A floating panel is dismissed by the scrim, Back or the keyboard; the glyph only says "search".
                                Icon(painterResource(R.drawable.ic_search), contentDescription = null)
                            } else {
                                IconButton(onClick = onClose) {
                                    Icon(painterResource(R.drawable.ic_settings_back), contentDescription = stringResource(R.string.search_back))
                                }
                            }
                        },
                        trailingIcon = {
                            if (field.text.isNotEmpty()) {
                                IconButton(onClick = {
                                    field = TextFieldValue("")
                                    onQueryChange("")
                                }) {
                                    Icon(painterResource(R.drawable.ic_search_clear), contentDescription = stringResource(R.string.search_clear))
                                }
                            }
                        },
                        singleLine = true,
                        shape = if (windowed) RectangleShape else RoundedCornerShape(settings.cornerRadiusDp.coerceIn(0f, 48f).dp),
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            // In the panel the field is the header row, not a second filled box inside it.
                            focusedContainerColor = if (windowed) Color.Transparent else TextFieldDefaults.colors().focusedContainerColor,
                            unfocusedContainerColor = if (windowed) Color.Transparent else TextFieldDefaults.colors().unfocusedContainerColor,
                        ),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                            imeAction = ImeAction.Go,
                        ),
                        keyboardActions = KeyboardActions(onGo = { current?.goRow()?.let(onRowClick) }),
                    )
                }

                // A recent search goes back into the field (to edit or send); it never runs by itself.
                val onResultClick: (SearchRow) -> Unit = { row ->
                    val recentQuery = ((row as? SearchRow.Recent)?.item as? RecentItem.Query)?.text
                    if (recentQuery != null) {
                        field = TextFieldValue(recentQuery, TextRange(recentQuery.length))
                        onQueryChange(recentQuery)
                    } else {
                        onRowClick(row)
                    }
                }
                // Near the keyboard, a lone message sits where results would start, in thumb reach. The window
                // panel wraps its content, so its message just sits in a row.
                val message: (@Composable (String) -> Unit) = { text ->
                    if (nearKeyboard && !windowed) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) { Message(text) }
                    else Message(text, windowed)
                }
                val hasBody = (field.text.isBlank() && shown == null) || (current != null && current.rows.isEmpty()) || shown != null
                val body: @Composable () -> Unit = {
                    when {
                        field.text.isBlank() && shown == null -> message(
                            stringResource(
                                if (settings.historyLearningEnabled && settings.historySuggestionsEnabled) R.string.search_history_empty
                                else R.string.search_empty_prompt,
                            ),
                        )
                        current != null && current.rows.isEmpty() -> message(stringResource(R.string.search_no_results))
                        shown != null -> ResultList(
                            shown, iconProvider, onResultClick, currentSuggestions, settings.maxAppResults, onAppDrag, onAppMenu, onPersonAction,
                            ContactServices.buttons(settings), historyControls.takeIf { shown.query.isBlank() }, settings.historyLearningEnabled,
                            nearKeyboard,
                            topResult = current?.takeIf { it.query.isNotBlank() }?.goRow(),
                            appPriorityDirection = settings.appPriorityDirection,
                            wrapHeight = windowed,
                        )
                    }
                }
                val divider: @Composable () -> Unit = {
                    if (windowed && hasBody) HorizontalDivider(color = outline)
                }
                if (dockBottom) {
                    // Results grow upward from the field, which rests on the keyboard.
                    Column(Modifier.weight(1f, fill = false)) { body() }
                    divider()
                    fieldRow()
                } else {
                    fieldRow()
                    divider()
                    Column(Modifier.weight(1f, fill = false)) { body() }
                }
            }
            // Above the keyboard (imePadding on the box), over results and the window scrim alike.
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).widthIn(max = 600.dp))
        }
    }
}

@Composable
private fun Message(text: String, compact: Boolean = false) {
    Text(
        text,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp).then(if (compact) Modifier.heightIn(min = 24.dp) else Modifier),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ResultList(
    results: SearchResults,
    iconProvider: IconProvider,
    onRowClick: (SearchRow) -> Unit,
    suggestions: WebSuggestions,
    maxAppResults: Int,
    onAppDrag: ((LauncherApp, View) -> Boolean)?,
    onAppMenu: ((LauncherApp, View) -> AppMenuHandle?)?,
    onPersonAction: (ContactHit, PersonAction) -> Unit,
    cardButtons: List<ContactCardButton>,
    history: SearchHistoryControls? = null,
    learning: Boolean = false,
    nearKeyboard: Boolean = false,
    topResult: SearchRow? = null,
    appPriorityDirection: SearchAppPriorityDirection = SearchAppPriorityDirection.FOLLOW_LANGUAGE,
    wrapHeight: Boolean = false,
) {
    val motion = LocalMotion.current
    val rows = limitSearchAppRows(results.rows, maxAppResults)
    var confirmClear by remember { mutableStateOf(false) }
    if (confirmClear && history != null) {
        PromptSheet(
            PromptSpec(
                icon = R.drawable.ic_search_history,
                title = stringResource(R.string.search_history_clear_title),
                message = stringResource(R.string.search_history_clear_message),
                primary = PromptAction(stringResource(R.string.search_history_clear_confirm), history.onClear),
                dismissLabel = stringResource(R.string.search_history_cancel),
                tone = PromptTone.DESTRUCTIVE,
            ),
        ) { confirmClear = false }
    }
    // Online suggestions follow every local result and precede the explicit web/store handoff rows.
    val firstWebRow = rows.indexOfFirst { it is SearchRow.WebSearch || it is SearchRow.PlayStore }.let { if (it < 0) rows.size else it }
    val showSuggestions = suggestions.values.isNotEmpty() || suggestions.unavailable
    // Contacts and shortcuts arrive after the offline matches. Without this, the list stays anchored
    // to the item that was first and a late leading person card would open scrolled out of view.
    val listState = rememberLazyListState()
    LaunchedEffect(results.query, rows.firstOrNull()) {
        if (!listState.isScrollInProgress) listState.scrollToItem(0)
    }
    // Each heading starts a block. Near the keyboard the list is laid out bottom-up: block order is
    // kept (the first block, the best match, sits on the keyboard) but each block is reversed so its
    // heading still reads above its items.
    val blocks = mutableListOf(mutableListOf<ResultItem>())
    val usedKeys = HashSet<Any>()
    fun add(key: Any? = null, contentType: Any? = null, content: @Composable () -> Unit) {
        // Lazy keys must be unique; a repeated identity (same app from two sources) gets a suffix.
        val unique = key?.let { base -> var k: Any = base; var n = 1; while (!usedKeys.add(k)) k = "$base#${n++}"; k }
        blocks.last() += ResultItem(unique, contentType, content)
    }
    fun header(key: Any, content: @Composable () -> Unit) {
        if (blocks.last().isNotEmpty()) blocks += mutableListOf<ResultItem>()
        add(key, "header", content)
    }
    run {
        fun suggestionItems() {
            header(key = "suggestions-header") {
                Text(
                    stringResource(R.string.search_suggestions_header, WebSearchUrls.engineName(requireNotNull(suggestions.engine))),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp).semantics { heading() },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (suggestions.unavailable) {
                add(key = "suggestions-unavailable") { Message(stringResource(R.string.search_suggestions_unavailable)) }
            }
            suggestions.values.forEachIndexed { index, value ->
                add(key = "suggestion-$index", contentType = "suggestion") {
                    RowLayout(
                        title = value,
                        leading = { SearchGlyph() },
                        onClickLabel = stringResource(R.string.search_web_row, WebSearchUrls.engineName(requireNotNull(suggestions.engine)), value),
                        onClick = { onRowClick(SearchRow.WebSearch(requireNotNull(suggestions.engine), value)) },
                    )
                }
            }
        }
        rows.forEachIndexed { index, row ->
            if (index == firstWebRow && showSuggestions) suggestionItems()
            if (row.section.title != 0 && (index == 0 || rows[index - 1].section != row.section)) {
                header(key = "header-${row.section}") {
                    Header(row.section, suggested = results.query.isBlank(), menu = if (index == 0 && history != null) {
                        { HistoryMenu(learning, history) { confirmClear = true } }
                    } else null)
                }
            }
            if (row is SearchRow.App && maxAppResults > 0) {
                if (rows.take(index).none { it is SearchRow.App }) add(key = "app-strip", contentType = "app-strip") {
                    SearchAppPriorityRow(appPriorityDirection, Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                        rows.filterIsInstance<SearchRow.App>().forEachIndexed { rank, appRow ->
                            // Keyed by app so a tile (and its loaded icon) follows the app when ranks change.
                            key(appRow.app.key) {
                            val tileModifier = Modifier.weight(1f).semantics { traversalIndex = rank.toFloat() }
                            if (onAppDrag != null) {
                                DraggableAppResult(appRow, iconProvider, onRowClick, onAppDrag, onAppMenu, compact = true,
                                    modifier = tileModifier, topResult = appRow == topResult, reserveTopLabel = topResult != null)
                            } else {
                                val priorityDescription = stringResource(R.string.search_top_result_description)
                                Column(tileModifier.topSearchResult(appRow == topResult)
                                    .semantics { if (appRow == topResult) stateDescription = priorityDescription }
                                    .clickable(onClickLabel = appRow.app.label) { onRowClick(appRow) }
                                    .padding(horizontal = 4.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    AppIcon(appRow.app.key, iconProvider)
                                    AnimatedTopResultLabel(appRow == topResult)
                                    Text(appRow.app.label, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    if (appRow.suggested) Text(stringResource(R.string.search_suggested_match), style = MaterialTheme.typography.labelSmall, maxLines = 2)
                                }
                            }
                            }
                        }
                    }
                }
            } else if (row is SearchRow.Person) {
                // Keyed by lookup key: focus and position survive results refreshing under the card.
                val contact = row.contact
                val buttons = ContactServices.resolve(cardButtons, contact, results.contactServices)
                add(key = "person-${contact.lookupKey}", contentType = "person") {
                    PersonCard(contact, buttons, iconProvider, topResult = row == topResult) { onPersonAction(contact, it) }
                }
            } else add(key = row.identityKey(), contentType = row::class) {
                if (row is SearchRow.App && onAppDrag != null) DraggableAppResult(row, iconProvider, onRowClick, onAppDrag, onAppMenu, topResult = row == topResult)
                else if (row is SearchRow.Recent) RecentRow(row, iconProvider, onRowClick, history?.onForget)
                else ResultRow(row, iconProvider, onRowClick, topResult = row == topResult)
            }
        }
        if (firstWebRow == rows.size && showSuggestions) suggestionItems()
    }
    LazyColumn(
        (if (wrapHeight) Modifier.fillMaxWidth() else Modifier.fillMaxSize()).then(if (nearKeyboard) Modifier.semantics { isTraversalGroup = true } else Modifier),
        state = listState,
        reverseLayout = nearKeyboard,
    ) {
        var order = 0
        blocks.forEach { block ->
            val first = order
            order += block.size
            (if (nearKeyboard) block.asReversed() else block).forEachIndexed { index, entry ->
                item(entry.key, entry.contentType) {
                    // Short, speed-aware move/fade-in so rows slide instead of jumping while typing. No
                    // fade-out: a leaving row is otherwise drawn at its old spot over the new results
                    // (worst with reverseLayout), so removed rows go at once.
                    val animate = if (entry.key != null) Modifier.animateItem(
                        fadeInSpec = motion.spec(FADE_MS), placementSpec = motion.spec(PLACE_MS), fadeOutSpec = null,
                    ) else Modifier
                    if (nearKeyboard) {
                        // TalkBack reads the best match first, top-down within each block, not screen order.
                        val reading = first + block.size - 1 - index
                        Box(animate.semantics { traversalIndex = reading.toFloat() }) { entry.content() }
                    } else Box(animate) { entry.content() }
                }
            }
        }
    }
}

private const val FADE_MS = 120
private const val PLACE_MS = 180

/** Stable identity for lazy keys and animations: what the row is, never its position. */
internal fun SearchRow.identityKey(): String = when (this) {
    is SearchRow.Calculation -> "calc"
    is SearchRow.Micro -> when (val r = result) {
        is MicroResult.Conversion -> "micro-conversion"
        is MicroResult.Action -> "micro-${r.kind}"
    }
    is SearchRow.App -> "app:${app.key.packageName}/${app.key.className}/${app.key.userSerial}"
    is SearchRow.Folder -> "folder:${folder.itemId}"
    is SearchRow.Shortcut -> "shortcut:${shortcut.packageName}/${shortcut.shortcutId}/${shortcut.userSerial}"
    is SearchRow.Contact -> "contact:${contact.lookupKey}"
    is SearchRow.Person -> "person-${contact.lookupKey}"
    SearchRow.ContactsPermissionNeeded -> "contacts-permission"
    is SearchRow.WebSearch -> "web:${engine.name}"
    is SearchRow.PlayStore -> "playstore"
    is SearchRow.SuggestedApp -> "suggested-app:${app.key.packageName}/${app.key.className}/${app.key.userSerial}"
    is SearchRow.SuggestedContact -> "suggested-contact:${contact.lookupKey}"
    is SearchRow.Recent -> "recent:${item.target}"
}

private class ResultItem(val key: Any?, val contentType: Any?, val content: @Composable () -> Unit)

@Composable
private fun DraggableAppResult(
    row: SearchRow.App,
    iconProvider: IconProvider,
    onRowClick: (SearchRow) -> Unit,
    onAppDrag: (LauncherApp, View) -> Boolean,
    onAppMenu: ((LauncherApp, View) -> AppMenuHandle?)?,
    compact: Boolean = false,
    modifier: Modifier = Modifier.fillMaxWidth(),
    topResult: Boolean = false,
    /** Keeps the "Top result" line's height when this tile is not the top result so tiles don't resize. */
    reserveTopLabel: Boolean = false,
) {
    val revision by iconProvider.iconRevision.collectAsState()
    val drawable by produceState<Drawable?>(initialValue = null, row.app.key, iconProvider, revision) {
        value = try {
            iconProvider.icon(row.app.key)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: RuntimeException) {
            null
        }
    }
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val motion = LocalMotion.current
    AndroidView(
        factory = { SearchAppResultView(it, compact) },
        modifier = modifier.topSearchResult(topResult),
        update = { view -> view.bind(row.app, drawable, textColor, { onRowClick(row) }, onAppDrag, onAppMenu, row.suggested,
            topResult = topResult, reserveTopLabel = reserveTopLabel, motion = motion) },
    )
}

@Composable
private fun Header(section: Section, suggested: Boolean = false, menu: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Text(
            stringResource(if (suggested && section == Section.APPS) R.string.search_section_suggested else section.title),
            modifier = Modifier
                .weight(1f)
                .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp)
                .semantics { heading() },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        menu?.invoke()
    }
}

/** ⋮ on the first empty-search header: clear history (confirmed), pause or resume learning, settings. */
@Composable
private fun HistoryMenu(learning: Boolean, controls: SearchHistoryControls, onClear: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.padding(end = 8.dp)) {
        IconButton(onClick = { open = true }) {
            Icon(
                painterResource(R.drawable.ic_menu_more),
                contentDescription = stringResource(R.string.search_history_options),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.search_history_clear)) }, onClick = { open = false; onClear() })
            DropdownMenuItem(
                text = { Text(stringResource(if (learning) R.string.search_history_stop_learning else R.string.search_history_resume_learning)) },
                onClick = { open = false; controls.onSetLearning(!learning) },
            )
            DropdownMenuItem(text = { Text(stringResource(R.string.search_history_settings)) }, onClick = { open = false; controls.onOpenSettings() })
        }
    }
}

@Composable
private fun ResultRow(row: SearchRow, iconProvider: IconProvider, onRowClick: (SearchRow) -> Unit, topResult: Boolean = false) {
    when (row) {
        is SearchRow.Micro -> when (val result = row.result) {
            is MicroResult.Conversion -> RowLayout(
                title = result.display,
                subtitle = stringResource(R.string.search_micro_copy_hint),
                leading = { Glyph("≈") },
                onClickLabel = stringResource(R.string.search_calc_copy, result.display),
                onClick = { onRowClick(row) },
            )
            is MicroResult.Action -> RowLayout(
                topResult = topResult,
                title = stringResource(result.kind.titleResource()),
                subtitle = result.value,
                leading = { SearchGlyph() },
                onClick = { onRowClick(row) },
            )
        }
        is SearchRow.Calculation -> when (val result = row.result) {
            is CalculationResult.Value -> RowLayout(
                title = "= ${result.display}",
                subtitle = row.expression,
                leading = { Glyph("=") },
                onClickLabel = stringResource(R.string.search_calc_copy, result.display),
                onClick = { onRowClick(row) },
            )
            is CalculationResult.Error -> RowLayout(
                title = stringResource(
                    when (result.reason) {
                        CalculationResult.Reason.DIVISION_BY_ZERO -> R.string.search_calc_division_by_zero
                        CalculationResult.Reason.OVERFLOW -> R.string.search_calc_overflow
                        CalculationResult.Reason.UNDEFINED -> R.string.search_calc_undefined
                    },
                ),
                subtitle = row.expression,
                leading = { Glyph("=") },
                onClick = null,
            )
            CalculationResult.NotAnExpression -> Unit
        }
        is SearchRow.Folder -> RowLayout(
            topResult = topResult,
            title = row.folder.title,
            subtitle = stringResource(R.string.search_folder_item_count, row.folder.itemCount),
            leading = { Icon(painterResource(R.drawable.ic_settings_folder), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            onClick = { onRowClick(row) },
        )
        is SearchRow.App -> RowLayout(
            topResult = topResult,
            title = row.app.label,
            subtitle = if (row.suggested) stringResource(R.string.search_suggested_match) else null,
            leading = { AppIcon(row.app.key, iconProvider) },
            onClick = { onRowClick(row) },
        )
        is SearchRow.Shortcut -> RowLayout(
            topResult = topResult,
            title = row.shortcut.label,
            subtitle = stringResource(R.string.search_shortcut_of),
            leading = { AppIcon(row.shortcut.appKey, iconProvider) },
            onClick = { onRowClick(row) },
        )
        is SearchRow.Contact -> RowLayout(
            topResult = topResult,
            title = row.contact.name,
            subtitle = if (row.suggested) stringResource(R.string.search_suggested_match) else null,
            leading = { ContactAvatar(row.contact, LeadingSize) },
            onClick = { onRowClick(row) },
        )
        is SearchRow.Person -> PersonCard(row.contact, emptyList(), iconProvider) { onRowClick(row) } // ResultList shows the full card
        is SearchRow.Recent -> RecentRow(row, iconProvider, onRowClick, onForget = null)
        is SearchRow.SuggestedApp -> RowLayout(
            title = row.app.label,
            subtitle = stringResource(R.string.search_suggested_app),
            leading = { AppIcon(row.app.key, iconProvider) },
            onClick = { onRowClick(row) },
        )
        is SearchRow.SuggestedContact -> RowLayout(
            title = row.contact.name,
            subtitle = stringResource(R.string.search_suggested_contact),
            leading = { ContactAvatar(row.contact, LeadingSize) },
            onClick = { onRowClick(row) },
        )
        SearchRow.ContactsPermissionNeeded -> RowLayout(
            title = stringResource(R.string.search_contacts_permission_needed),
            leading = { Glyph("!") },
            onClick = { onRowClick(row) },
        )
        is SearchRow.WebSearch -> RowLayout(
            topResult = topResult,
            title = stringResource(R.string.search_web_row, WebSearchUrls.engineName(row.engine), row.query),
            subtitle = stringResource(R.string.search_web_row_summary),
            leading = { SearchGlyph() },
            onClick = { onRowClick(row) },
        )
        is SearchRow.PlayStore -> RowLayout(
            title = stringResource(R.string.search_play_store_row, row.query),
            subtitle = stringResource(R.string.search_play_store_summary),
            leading = { SearchGlyph() },
            onClick = { onRowClick(row) },
        )
    }
}

/** A remembered item; long-press (or the accessibility action) removes it from history. */
@Composable
private fun RecentRow(row: SearchRow.Recent, iconProvider: IconProvider, onRowClick: (SearchRow) -> Unit, onForget: ((HistoryTarget) -> Unit)?) {
    val remove = onForget?.let { forget -> { forget(row.item.target) } }
    val removeLabel = stringResource(R.string.search_history_remove)
    when (val item = row.item) {
        is RecentItem.Query -> RowLayout(
            title = item.text,
            subtitle = stringResource(R.string.search_recent_query),
            leading = { Icon(painterResource(R.drawable.ic_search_history), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            onClickLabel = stringResource(R.string.search_recent_query_fill, item.text),
            onClick = { onRowClick(row) },
            onLongClick = remove,
            onLongClickLabel = removeLabel,
        )
        is RecentItem.Contact -> RowLayout(
            title = item.contact.name,
            subtitle = stringResource(R.string.search_suggested_contact),
            leading = { ContactAvatar(item.contact, LeadingSize) },
            onClick = { onRowClick(row) },
            onLongClick = remove,
            onLongClickLabel = removeLabel,
        )
        is RecentItem.Shortcut -> RowLayout(
            title = item.shortcut.label,
            subtitle = stringResource(R.string.search_shortcut_of),
            leading = { AppIcon(item.shortcut.appKey, iconProvider) },
            onClick = { onRowClick(row) },
            onLongClick = remove,
            onLongClickLabel = removeLabel,
        )
        is RecentItem.Folder -> RowLayout(
            title = item.folder.title,
            subtitle = stringResource(R.string.search_folder_item_count, item.folder.itemCount),
            leading = { Icon(painterResource(R.drawable.ic_settings_folder), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            onClick = { onRowClick(row) },
            onLongClick = remove,
            onLongClickLabel = removeLabel,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RowLayout(
    title: String,
    leading: @Composable () -> Unit,
    onClick: (() -> Unit)?,
    subtitle: String? = null,
    onClickLabel: String? = null,
    /** Smaller row for settings previews; search itself uses the default size. */
    compact: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    topResult: Boolean = false,
) {
    val priorityDescription = stringResource(R.string.search_top_result_description)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Inset so the highlight clears rounded screen corners and screen guards; the content
            // padding below shrinks by the same amount, so rows don't shift.
            .padding(horizontal = 8.dp)
            .heightIn(min = if (compact) 40.dp else 56.dp)
            .topSearchResult(topResult)
            .semantics { if (topResult) stateDescription = priorityDescription }
            .then(
                when {
                    onClick != null && onLongClick != null -> Modifier.combinedClickable(
                        onClickLabel = onClickLabel, onLongClickLabel = onLongClickLabel, onLongClick = onLongClick, onClick = onClick,
                    )
                    onClick != null -> Modifier.clickable(onClickLabel = onClickLabel, onClick = onClick)
                    else -> Modifier
                },
            )
            .padding(horizontal = if (compact) 4.dp else 12.dp, vertical = if (compact) 4.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(if (compact) 32.dp else LeadingSize), contentAlignment = Alignment.Center) { leading() }
        Spacer(Modifier.width(if (compact) 10.dp else 16.dp))
        Column(Modifier.weight(1f)) {
            AnimatedTopResultLabel(topResult)
            Text(title, style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The contact's thumbnail, decoded off the main thread, else the initial on a circle. */
@Composable
internal fun ContactAvatar(contact: ContactHit, size: Dp) {
    val context = LocalContext.current
    val photo by produceState<ImageBitmap?>(initialValue = null, contact.photoUri) {
        value = contact.photoUri?.let { uri ->
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(android.net.Uri.parse(uri))?.use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null // revoked permission, deleted photo or undecodable image
            }
        }
    }
    val current = photo
    if (current != null) {
        Image(current, contentDescription = null, modifier = Modifier.size(size).clip(CircleShape))
    } else {
        Glyph(contact.name.trim().take(1).uppercase(), size)
    }
}

@Composable
private fun Glyph(text: String, size: Dp = LeadingSize) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@Composable
private fun SearchGlyph() {
    Icon(painterResource(R.drawable.ic_search), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun AppIcon(key: AppKey?, iconProvider: IconProvider) {
    val bitmap = rememberAppIconBitmap(key, LeadingSize, iconProvider)
    bitmap?.let { Image(it, contentDescription = null, modifier = Modifier.size(LeadingSize)) }
}

private val LeadingSize = 40.dp

@StringRes
private fun MicroResult.Kind.titleResource(): Int = when (this) {
    MicroResult.Kind.URL -> R.string.search_micro_url
    MicroResult.Kind.MAP -> R.string.search_micro_map
    MicroResult.Kind.DIAL -> R.string.search_micro_dial
    MicroResult.Kind.SMS -> R.string.search_micro_sms
    MicroResult.Kind.PACKAGE -> R.string.search_micro_package
    MicroResult.Kind.FLIGHT -> R.string.search_micro_flight
    MicroResult.Kind.BITCOIN -> R.string.search_micro_bitcoin
    MicroResult.Kind.ETHEREUM -> R.string.search_micro_ethereum
}
