package io.github.aj4abinjacob.chikku.core.model.settings

import io.github.aj4abinjacob.chikku.core.model.GestureAction
import io.github.aj4abinjacob.chikku.core.model.GridSpec
import io.github.aj4abinjacob.chikku.core.model.cards.CardsSettings

/**
 * Every user preference of the launcher, as one immutable value.
 *
 * Defaults reproduce the M0 behaviour. Colors are ARGB ints; `null` means "follow the theme".
 * Scales are multipliers of the default size. Persisted through [LauncherSettingsCodec]; values read
 * from storage or a backup are passed through [coerced] so out-of-range input cannot break layout.
 */
data class LauncherSettings(
    val home: HomeSettings = HomeSettings(),
    val dock: DockSettings = DockSettings(),
    val drawer: DrawerSettings = DrawerSettings(),
    val folders: FolderSettings = FolderSettings(),
    val gestures: GestureSettings = GestureSettings(),
    val search: SearchSettings = SearchSettings(),
    val badges: BadgeSettings = BadgeSettings(),
    val lookFeel: LookFeelSettings = LookFeelSettings(),
    val night: NightSettings = NightSettings(),
    val labs: LabsSettings = LabsSettings(),
    val onboarding: OnboardingSettings = OnboardingSettings(),
    val cards: CardsSettings = CardsSettings(),
) {
    /** Clamps every numeric field into its documented range. Idempotent. */
    fun coerced(): LauncherSettings = copy(
        cards = cards.coerced(),
        home = home.copy(
            desktopColumns = home.desktopColumns.coerceIn(MIN_GRID, GridSpec.MAX_DIMENSION),
            desktopRows = home.desktopRows.coerceIn(MIN_GRID, GridSpec.MAX_DIMENSION),
            iconScale = home.iconScale.coerceScale(),
            labelScale = home.labelScale.coerceScale(),
            iconLabelGapDp = home.iconLabelGapDp.coerceIn(0, 24),
            iconVerticalOffsetDp = home.iconVerticalOffsetDp.coerceIn(0, 24),
            widgetCornerRadiusDp = if (home.widgetCornerRadiusDp.isFinite()) home.widgetCornerRadiusDp.coerceIn(0f, 48f) else 0f,
            defaultPage = home.defaultPage.coerceAtLeast(0),
            pageSnapDurationMs = home.pageSnapDurationMs.coerceIn(100, 500),
            pageEffectStrength = home.pageEffectStrength.coerceAlpha(),
        ),
        dock = dock.copy(
            iconCount = dock.iconCount.coerceIn(MIN_GRID, GridSpec.MAX_DIMENSION),
            iconScale = dock.iconScale.coerceScale(),
            labelScale = dock.labelScale?.coerceScale(),
            backgroundAlpha = dock.backgroundAlpha.coerceAlpha(),
            pages = dock.pages.coerceIn(1, MAX_DOCK_PAGES),
            lastPage = dock.lastPage.coerceIn(0, dock.effectivePages() - 1),
            topLeftCorner = dock.topLeftCorner.coerced(),
            topRightCorner = dock.topRightCorner.coerced(),
            bottomLeftCorner = dock.bottomLeftCorner.coerced(),
            bottomRightCorner = dock.bottomRightCorner.coerced(),
        ),
        drawer = drawer.copy(
            columns = if (drawer.columns <= DrawerSettings.AUTO_COLUMNS) {
                DrawerSettings.AUTO_COLUMNS
            } else {
                drawer.columns.coerceIn(MIN_GRID, GridSpec.MAX_DIMENSION)
            },
            rows = drawer.rows.coerceIn(MIN_GRID, GridSpec.MAX_DIMENSION),
            fastScrollerGapDp = drawer.fastScrollerGapDp.coerceIn(DrawerSettings.FAST_SCROLLER_GAP_RANGE_DP),
            iconScale = drawer.iconScale.coerceScale(),
            labelScale = drawer.labelScale.coerceScale(),
            backgroundAlpha = drawer.backgroundAlpha.coerceAlpha(),
            pageSnapDurationMs = drawer.pageSnapDurationMs.coerceIn(100, 500),
            pageEffectStrength = drawer.pageEffectStrength.coerceAlpha(),
        ),
        lookFeel = lookFeel.copy(menuCornerRadiusDp = if (lookFeel.menuCornerRadiusDp.isFinite()) lookFeel.menuCornerRadiusDp.coerceIn(0f, 48f) else 14f),
        badges = badges.copy(badgeScale = badges.badgeScale.coerceScale()),
        search = search.copy(
            cornerRadiusDp = if (search.cornerRadiusDp.isFinite()) search.cornerRadiusDp.coerceIn(0f, 48f) else 20f,
            widgetCornerRadiusDp = if (search.widgetCornerRadiusDp.isFinite()) search.widgetCornerRadiusDp.coerceIn(0f, 48f) else 32f,
            maxAppResults = search.maxAppResults.coerceIn(0, 50),
            widgetWidthPercent = search.widgetWidthPercent.coerceIn(40, 100),
            widgetHeightDp = search.widgetHeightDp.coerceIn(48, 96),
            widgetHorizontalPaddingDp = search.widgetHorizontalPaddingDp.coerceIn(0, 48),
            widgetVerticalPaddingDp = search.widgetVerticalPaddingDp.coerceIn(0, 24),
            widgetDockGapDp = search.widgetDockGapDp.coerceIn(0, 64),
            contactCardButtons = search.contactCardButtons.filter(::validButtonId).distinct().take(MAX_CARD_BUTTONS)
                .ifEmpty { SearchSettings().contactCardButtons },
        ),
        folders = folders.copy(
            backgroundAlpha = folders.backgroundAlpha.coerceAlpha(),
            columns = if (folders.columns == 0) 0 else folders.columns.coerceIn(1, 6),
            iconScale = folders.iconScale.coerceScale(),
            labelScale = folders.labelScale.coerceScale(),
            previewCount = folders.previewCount.coerceIn(1, 9),
            rows = folders.rows.coerceIn(1, 6),
            cornerRadiusDp = if (folders.cornerRadiusDp.isFinite()) folders.cornerRadiusDp.coerceIn(0f, 48f) else 20f,
        ),
    )

    /** Desktop grid implied by [home]. */
    val desktopGrid: GridSpec get() = GridSpec(home.desktopColumns, home.desktopRows)

    /**
     * One dock page: [DockSettings.iconCount] columns, one row. This is dock page 0, where defaults
     * are seeded; the stored dock row holds every page side by side ([dockStorageGrid]).
     */
    val dockGrid: GridSpec get() = GridSpec(dock.iconCount, 1)

    /** Stored dock row: [DockSettings.effectivePages] pages of [dockGrid] side by side. */
    val dockStorageGrid: GridSpec
        get() = GridSpec(dock.iconCount.coerceIn(1, GridSpec.MAX_DIMENSION) * dock.effectivePages(), 1)

    companion object {
        /** Person-card buttons that fit one row; damaged backups cannot grow the list. */
        const val MAX_CARD_BUTTONS = 4
        private val BUTTON_ID = Regex("^[a-z0-9_.]{1,48}:(message|voice_call|video_call)$")
        private fun validButtonId(id: String) = BUTTON_ID.matches(id)
        const val MIN_GRID = 2
        const val MIN_SCALE = 0.5f
        const val MAX_SCALE = 1.5f
        const val MAX_DOCK_PAGES = 3

        private fun Float.coerceScale() = if (isNaN()) 1f else coerceIn(MIN_SCALE, MAX_SCALE)
        private fun Float.coerceAlpha() = if (isNaN()) 1f else coerceIn(0f, 1f)
    }
}

enum class PaddingSize { NONE, SMALL, MEDIUM, LARGE }

enum class PageIndicatorStyle { DOTS, LINE, NONE }

/** Where the home-screen search bar sits when [HomeSettings.showSearchBar] is on. */
enum class HomeSearchBarPosition { TOP, BELOW_DOCK, NONE }

data class HomeSettings(
    val desktopColumns: Int = 5,
    val desktopRows: Int = 5,
    val iconScale: Float = 1f,
    val showLabels: Boolean = true,
    val labelScale: Float = 1f,
    val horizontalPadding: PaddingSize = PaddingSize.MEDIUM,
    val verticalPadding: PaddingSize = PaddingSize.MEDIUM,
    val widgetPadding: PaddingSize = PaddingSize.SMALL,
    val wallpaperScrolling: Boolean = true,
    val pageIndicatorStyle: PageIndicatorStyle = PageIndicatorStyle.DOTS,
    /** The page indicator fades in while the pages scroll and fades out shortly after. */
    val pageIndicatorAutoHide: Boolean = false,
    val infiniteScroll: Boolean = false,
    /** Blocks drag, remove, resize and add on the workspace ("Lock desktop"). */
    val layoutLocked: Boolean = false,
    val allowWidgetOverlap: Boolean = false,
    /** Page the home button / [GestureAction.GoToDefaultPage] returns to. */
    val defaultPage: Int = 0,
    val showSearchBar: Boolean = false,
    val searchBarPosition: HomeSearchBarPosition = HomeSearchBarPosition.TOP,
    /** Optional Google-owned overlay with an installed-Google-app fallback. */
    val googleDiscoverEnabled: Boolean = false,
    /** Zero preserves provider corners; positive values clip widget content within its padding. */
    val widgetCornerRadiusDp: Float = 0f,
    val labelFont: DockLabelFont = DockLabelFont.NORMAL,
    val labelShadow: Boolean = true,
    val labelSingleLine: Boolean = true,
    val labelColor: Int? = null,
    val transitionEffect: PageTransitionEffect = PageTransitionEffect.SLIDE,
    val addNewApps: Boolean = false,
    val showEdgeShadow: Boolean = false,
    val iconLabelGapDp: Int = 6,
    val iconVerticalOffsetDp: Int = 0,
    val pageSwipeSensitivity: PageSwipeSensitivity = PageSwipeSensitivity.NORMAL,
    /** Base settling duration; the global/system reduced-motion policy still takes precedence. */
    val pageSnapDurationMs: Int = 250,
    val pageEffectStrength: Float = 1f,
    val pageEdgeFeedback: PageEdgeFeedback = PageEdgeFeedback.RESISTANCE,
)

enum class DockBackground { NONE, RECTANGLE, ROUNDED, CARD, CUSTOM }
enum class DockCornerShape { ROUND, CUT }
data class DockCorner(val size: Float = 0f, val shape: DockCornerShape = DockCornerShape.ROUND) {
    fun coerced(): DockCorner = copy(size = if (size.isNaN()) 0f else size.coerceIn(0f, 1f))
}
enum class DockLabelFont { NORMAL, MEDIUM, CONDENSED, LIGHT }

data class DockSettings(
    val enabled: Boolean = true,
    /** Dock columns per page. */
    val iconCount: Int = 5,
    val iconScale: Float = 1f,
    val showLabels: Boolean = true,
    val background: DockBackground = DockBackground.NONE,
    val backgroundAlpha: Float = 0.5f,
    val backgroundColor: Int? = null,
    /** Scrollable dock pages (1 = no scrolling). */
    val pages: Int = 1,
    val matchDesktopSize: Boolean = false,
    /** null follows the desktop, preserving older settings and backups. */
    val labelScale: Float? = null,
    val labelFont: DockLabelFont = DockLabelFont.NORMAL,
    val labelShadow: Boolean = true,
    val labelSingleLine: Boolean = true,
    /** Auto follows desktop padding; explicit choices are independent. */
    val horizontalPadding: PaddingSize? = null,
    val verticalPadding: PaddingSize = PaddingSize.NONE,
    val backgroundEnabled: Boolean = true,
    val infiniteScroll: Boolean = false,
    val displayAsOverlay: Boolean = false,
    val automaticallyClose: Boolean = false,
    val rememberPosition: Boolean = false,
    val lastPage: Int = 0,
    val drawBehindNavigationBar: Boolean = false,
    val backgroundInset: Boolean = false,
    val topLeftCorner: DockCorner = DockCorner(),
    val topRightCorner: DockCorner = DockCorner(),
    val bottomLeftCorner: DockCorner = DockCorner(),
    val bottomRightCorner: DockCorner = DockCorner(),
    /** User-selected SAF image; retained as an unresolved reference if access/provider is lost. */
    val backgroundImageUri: String? = null,
) {
    fun effectiveIconScale(home: HomeSettings): Float = if (matchDesktopSize) home.iconScale else iconScale
    fun effectiveLabelScale(home: HomeSettings): Float = labelScale ?: home.labelScale

    /** Dock pages actually stored: [pages] capped so the stored row fits [GridSpec.MAX_DIMENSION]. */
    fun effectivePages(): Int {
        val count = iconCount.coerceIn(1, GridSpec.MAX_DIMENSION)
        val fit = (GridSpec.MAX_DIMENSION / count).coerceAtLeast(1)
        return pages.coerceIn(1, LauncherSettings.MAX_DOCK_PAGES).coerceAtMost(fit)
    }
}

enum class DrawerStyle { VERTICAL, HORIZONTAL_PAGED, LIST }

enum class DrawerTabBarPosition { TOP, BOTTOM }
enum class DrawerTransition { SLIDE, FADE, SCALE }
enum class PageTransitionEffect { SLIDE, FADE, ZOOM, CUBE }
enum class FolderWindowStyle { FLOATING, IMMERSIVE }
enum class FolderSortOrder { MANUAL, ALPHABETICAL }
enum class FolderScrollStyle { VERTICAL, HORIZONTAL_PAGED }

enum class DrawerSearchBarPosition { TOP, BOTTOM, HIDDEN }

/**
 * How the drawer search field stands apart from the grid. [SUBTLE] follows Material 3's search bar
 * (tonal fill, leading icon, no outline); the others add an outline, [HIGH_CONTRAST] one that meets
 * WCAG's 3:1 non-text contrast.
 */
enum class DrawerSearchBarStyle { SUBTLE, OUTLINED, HIGH_CONTRAST }

enum class DrawerOpenMethod { SWIPE_UP, BUTTON, BOTH }

data class DrawerSettings(
    /** [AUTO_COLUMNS] fits columns to the screen width (M0 behaviour). */
    val columns: Int = AUTO_COLUMNS,
    /** Rows per page, or visible rows in scrolling styles when [automaticRowSpacing] is off. */
    val rows: Int = 5,
    val iconScale: Float = 1f,
    val showLabels: Boolean = true,
    val style: DrawerStyle = DrawerStyle.VERTICAL,
    val backgroundAlpha: Float = 1f,
    val backgroundColor: Int? = null,
    val searchBarPosition: DrawerSearchBarPosition = DrawerSearchBarPosition.TOP,
    val openKeyboardOnOpen: Boolean = false,
    val openMethod: DrawerOpenMethod = DrawerOpenMethod.BOTH,
    val showFastScroller: Boolean = false,
    /** Separate personal/work tabs when a work profile exists. */
    val showWorkTab: Boolean = false,
    /** Drawer groups (tabs) from DrawerGroupRepository. */
    val groupsEnabled: Boolean = false,
    val labelScale: Float = 1f,
    val labelFont: DockLabelFont = DockLabelFont.NORMAL,
    val labelShadow: Boolean = false,
    val labelSingleLine: Boolean = true,
    val labelColor: Int? = null,
    val matchDesktopSize: Boolean = false,
    val infiniteScroll: Boolean = false,
    val rememberScrollPosition: Boolean = false,
    val fastScrollerColor: Int? = null,
    val tabBarPosition: DrawerTabBarPosition = DrawerTabBarPosition.TOP,
    val pullToSearch: Boolean = false,
    val automaticallyClose: Boolean = true,
    val transition: DrawerTransition = DrawerTransition.SLIDE,
    /** Preserve content-sized scrolling rows by default; paged grids always use [rows]. */
    val automaticRowSpacing: Boolean = true,
    val fastScrollerStyle: DrawerFastScrollerStyle = DrawerFastScrollerStyle.ALPHABET,
    /** Screen side of the fast scroller; a physical side, so left-handed use is not flipped by RTL. */
    val fastScrollerSide: DrawerFastScrollerSide = DrawerFastScrollerSide.RIGHT,
    /** Space in dp between the fast scroller and the app grid. */
    val fastScrollerGapDp: Int = DEFAULT_FAST_SCROLLER_GAP_DP,
    /** Letter bubble beside the fast scroller while it is dragged. */
    val fastScrollerPopup: Boolean = true,
    val tabStyle: DrawerTabStyle = DrawerTabStyle.COMPACT,
    val tabAccentColor: Int? = null,
    val searchBarStyle: DrawerSearchBarStyle = DrawerSearchBarStyle.SUBTLE,
    val pageSwipeSensitivity: PageSwipeSensitivity = PageSwipeSensitivity.NORMAL,
    /** Base settling duration; the global/system reduced-motion policy still takes precedence. */
    val pageSnapDurationMs: Int = 250,
    val pageEffectStrength: Float = 1f,
    val pageEdgeFeedback: PageEdgeFeedback = PageEdgeFeedback.RESISTANCE,
    val pageTransitionEffect: PageTransitionEffect = PageTransitionEffect.SLIDE,
    val pageIndicatorStyle: PageIndicatorStyle = PageIndicatorStyle.DOTS,
    val pageIndicatorAutoHide: Boolean = false,
    /** Vertical and List styles: how far a flick carries the list. */
    val listScrollSpeed: ListScrollSpeed = ListScrollSpeed.NORMAL,
    /** Vertical and List styles: what the list shows at its top and bottom. */
    val listEdgeEffect: ListEdgeEffect = ListEdgeEffect.SYSTEM,
) {
    companion object {
        const val AUTO_COLUMNS = 0
        const val DEFAULT_FAST_SCROLLER_GAP_DP = 8
        val FAST_SCROLLER_GAP_RANGE_DP = 0..32
    }
}

enum class FolderPreviewStyle { GRID, STACK, FAN, LINE }

enum class FolderIconBackground { NONE, CIRCLE, ROUNDED_SQUARE }

data class FolderSettings(
    val previewStyle: FolderPreviewStyle = FolderPreviewStyle.GRID,
    val iconBackground: FolderIconBackground = FolderIconBackground.CIRCLE,
    val backgroundAlpha: Float = 1f,
    val backgroundColor: Int? = null,
    /** Honour per-folder tap/swipe actions. */
    val swipeActionsEnabled: Boolean = true,
    val windowStyle: FolderWindowStyle = FolderWindowStyle.FLOATING,
    val columns: Int = 0,
    val iconScale: Float = 1f,
    val showLabels: Boolean = true,
    val labelScale: Float = 1f,
    val sortOrder: FolderSortOrder = FolderSortOrder.MANUAL,
    val previewCount: Int = 4,
    val closeOnLaunch: Boolean = true,
    val matchDesktopSize: Boolean = false,
    val labelFont: DockLabelFont = DockLabelFont.NORMAL,
    val labelShadow: Boolean = false,
    val labelSingleLine: Boolean = true,
    val labelColor: Int? = null,
    val showName: Boolean = true,
    val cornerRadiusDp: Float = 20f,
    val windowShadow: Boolean = false,
    val scrollStyle: FolderScrollStyle = FolderScrollStyle.VERTICAL,
    val rows: Int = 3,
    /** Per-folder icon choices, keyed by stable workspace item ID. */
    val covers: Map<String, FolderCover> = emptyMap(),
    /** Preview backdrop color, independent of the opened folder window. */
    val iconBackgroundColor: Int? = null,
)

/** Home-screen gestures. Icon and folder swipes live on the item or app customization. */
data class GestureSettings(
    val swipeUp: GestureAction = GestureAction.OpenDrawer,
    val swipeDown: GestureAction = GestureAction.None,
    val doubleTap: GestureAction = GestureAction.None,
    val pinchIn: GestureAction = GestureAction.None,
    val pinchOut: GestureAction = GestureAction.None,
    val twoFingerSwipeUp: GestureAction = GestureAction.None,
    val twoFingerSwipeDown: GestureAction = GestureAction.None,
    /**
     * The system Home gesture (swipe up from the navigation bar) or Home button, used while already on
     * home with the drawer closed. Separate from [swipeUp], which is a swipe on the home screen itself.
     */
    val homeGesture: GestureAction = GestureAction.OpenSearch,
    val swipeLeft: GestureAction = GestureAction.None,
    val swipeRight: GestureAction = GestureAction.None,
    val doubleTapSwipeUp: GestureAction = GestureAction.None,
    val doubleTapSwipeDown: GestureAction = GestureAction.None,
    val rotateClockwise: GestureAction = GestureAction.None,
    val rotateCounterclockwise: GestureAction = GestureAction.None,
    val homeButtonOnlyOnDefaultPage: Boolean = false,
)

enum class WebSearchEngine { GOOGLE, DUCKDUCKGO, BING, BRAVE, STARTPAGE, ECOSIA }

data class SearchSettings(
    val webEngine: WebSearchEngine = WebSearchEngine.DUCKDUCKGO,
    /** "Search the web for …" row; hands the query to a browser by intent. Opens only on an explicit tap. */
    val showWebSuggestionRow: Boolean = true,
    val calculatorEnabled: Boolean = true,
    /** Needs READ_CONTACTS, requested only when the user turns this on. */
    val contactsEnabled: Boolean = false,
    val shortcutsEnabled: Boolean = true,
    val playStoreRow: Boolean = true,
    /** Opt-in: typed text is sent to the selected web provider for autocomplete. */
    val onlineSuggestions: Boolean = false,
    /** Offline conversions and explicit URL/map/phone/tracking handoffs. */
    val microResultsEnabled: Boolean = true,
    /**
     * Opt-in: remember personal apps launched from Chikku, and contacts, shortcuts, folders and sent
     * web queries opened from search, on this phone only. Turning off pauses learning and keeps
     * what was learned; clearing is a separate action. Was `frequentAppsEnabled`.
     */
    val historyLearningEnabled: Boolean = false,
    /** Show learned suggestions when search opens with an empty query. Independent of learning. */
    val historySuggestionsEnabled: Boolean = true,
    val presentation: SearchPresentation = SearchPresentation.FULLSCREEN,
    /** Where opened search stacks its results: under the field, or up from the keyboard for one-handed reach. */
    val resultsAnchor: SearchResultsAnchor = SearchResultsAnchor.TOP,
    val backgroundColor: Int? = null,
    val cornerRadiusDp: Float = 20f,
    val maxAppResults: Int = 0,
    val privateKeyboard: Boolean = true,
    val widgetColor: Int? = null,
    /** Minimal shows neutral search marks; the selected engine remains in the accessible name. */
    val widgetStyle: HomeSearchBarStyle = HomeSearchBarStyle.PROVIDER,
    /** Glyph drawn on the home search bar's Chikku button. */
    val widgetIcon: HomeSearchBarIcon = HomeSearchBarIcon.NOSE,
    /** What a tap on the bar itself (outside its buttons) opens. */
    val widgetTapAction: HomeSearchBarTapAction = HomeSearchBarTapAction.CHIKKU_SEARCH,
    /** Side of the bar for the button that always opens Chikku search, or hidden. */
    val widgetChikkuButton: HomeSearchBarButtonSlot = HomeSearchBarButtonSlot.START,
    /** Side of the bar for the button that opens [webEngine] (the Google app for Google), or hidden. */
    val widgetWebButton: HomeSearchBarButtonSlot = HomeSearchBarButtonSlot.END,
    val widgetCornerRadiusDp: Float = 32f,
    val widgetShadow: Boolean = false,
    /** Home search capsule width as a percentage of its available parent width (40–100). */
    val widgetWidthPercent: Int = 100,
    /** Capsule height in dp (48–96), preserving a minimum accessible touch target. */
    val widgetHeightDp: Int = 56,
    /** Independent horizontal/vertical insets inside the capsule, in dp. */
    val widgetHorizontalPaddingDp: Int = 20,
    val widgetVerticalPaddingDp: Int = 10,
    /** Space outside the below-dock capsule, separating it from the dock (0–64 dp). */
    val widgetDockGapDp: Int = 12,
    val foldersEnabled: Boolean = false,
    /**
     * Person-card buttons in order, as `ContactCardButton` ids ("phone:voice_call", "sms:message",
     * "whatsapp:message"). Default Call, Message (SMS), WhatsApp. Kept as restored when an app is absent.
     */
    val contactCardButtons: List<String> = listOf("phone:voice_call", "sms:message", "whatsapp:message"),
    /** Physical priority direction for compact app rows; FOLLOW_LANGUAGE preserves existing RTL behavior. */
    val appPriorityDirection: SearchAppPriorityDirection = SearchAppPriorityDirection.FOLLOW_LANGUAGE,
)

enum class HomeSearchBarStyle { PROVIDER, MINIMAL }

/** [WEB_SEARCH] hands off to the selected engine, so nothing is typed into Chikku first. */
enum class HomeSearchBarTapAction { CHIKKU_SEARCH, WEB_SEARCH }

/** START/END follow the layout direction, so START is the right side in right-to-left languages. */
enum class HomeSearchBarButtonSlot { START, END, HIDDEN }

/** Magnifier glyphs for the home search capsule: Chikku's dog motifs, or a plain magnifier. */
enum class HomeSearchBarIcon { NOSE, PAW, EARS, FACE, NOTCH, MARK, PLAIN }

enum class SearchPresentation { FULLSCREEN, WINDOW }

/** KEYBOARD puts the first section (best match) just above the keyboard; later sections stack upward. */
enum class SearchResultsAnchor { TOP, KEYBOARD }

enum class SearchAppPriorityDirection { FOLLOW_LANGUAGE, LEFT_TO_RIGHT, RIGHT_TO_LEFT }
enum class BadgePosition { TOP_END, TOP_START, BOTTOM_END, BOTTOM_START }

enum class BadgeStyle { OFF, DOT, COUNT, DYNAMIC }

data class BadgeSettings(
    /** Anything but OFF needs notification-listener access, granted by the user from settings. */
    val style: BadgeStyle = BadgeStyle.OFF,
    val dotColor: Int? = null,
    val position: BadgePosition = BadgePosition.TOP_END,
    val badgeScale: Float = 1f,
)

enum class IconShape { SYSTEM, CIRCLE, SQUIRCLE, ROUNDED_SQUARE, SQUARE, TEARDROP }

/** Multiplier for animation durations; NONE disables animations. */
enum class AnimationSpeed(val durationScale: Float) {
    NONE(0f), FAST(0.5f), NORMAL(1f), SLOW(1.5f), RELAXED(2f),
}

enum class ScreenOrientation { AUTO, PORTRAIT, LANDSCAPE }

enum class StatusBarMode { SHOWN, TRANSPARENT, HIDDEN }

enum class AppMenuStyle { POPUP, DETAILED }

data class LookFeelSettings(
    /** Package of the selected icon pack, null for system icons. */
    val iconPackPackage: String? = null,
    val iconShape: IconShape = IconShape.SYSTEM,
    /** Put legacy (non-adaptive) icons on the [iconShape] background. */
    val reshapeLegacyIcons: Boolean = false,
    val animationSpeed: AnimationSpeed = AnimationSpeed.NORMAL,
    val orientation: ScreenOrientation = ScreenOrientation.AUTO,
    val statusBar: StatusBarMode = StatusBarMode.TRANSPARENT,
    val appMenuStyle: AppMenuStyle = AppMenuStyle.POPUP,
    /** Accent for settings, search and highlights; null follows the system/dynamic color. */
    val accentColor: Int? = null,
    val menuBackgroundColor: Int? = null,
    val menuTextColor: Int? = null,
    val menuCornerRadiusDp: Float = 14f,
    val menuShowUninstallHome: Boolean = true,
    val menuShowUninstallDrawer: Boolean = true,
    val menuShowStoreHome: Boolean = false,
    val menuShowStoreDrawer: Boolean = false,
    val menuShowHideHome: Boolean = false,
    val menuShowHideDrawer: Boolean = true,
    val menuShowEditHome: Boolean = true,
    val menuShowEditDrawer: Boolean = true,
    val menuShowInfoHome: Boolean = true,
    val menuShowInfoDrawer: Boolean = true,
    val menuShowSwipeHome: Boolean = false,
    val menuShowRemoveHome: Boolean = true,
)

enum class NightMode { SYSTEM, ON, OFF }

data class NightSettings(
    val mode: NightMode = NightMode.SYSTEM,
    val darkDrawer: Boolean = true,
    val darkFolders: Boolean = true,
    val darkSearch: Boolean = true,
    val darkMenus: Boolean = true,
    /** Pure-black surfaces instead of dark grey. */
    val pureBlack: Boolean = false,
)

data class LabsSettings(
    /** Revealed by tapping the version number in Settings → About 7 times. */
    val unlocked: Boolean = false,
    val debugOverlay: Boolean = false,
    val fasterIconCache: Boolean = false,
    /** Allows grids beyond the normal picker range. */
    val extendedGrid: Boolean = false,
)

data class OnboardingSettings(
    val completed: Boolean = false,
)

enum class DrawerTabStyle { COMPACT, SEGMENTED }

enum class DrawerFastScrollerStyle { ALPHABET, THUMB }

enum class DrawerFastScrollerSide { RIGHT, LEFT }
