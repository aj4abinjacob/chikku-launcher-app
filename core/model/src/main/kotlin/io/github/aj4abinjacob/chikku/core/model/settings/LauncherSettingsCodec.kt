package io.github.aj4abinjacob.chikku.core.model.settings

import io.github.aj4abinjacob.chikku.core.model.GestureAction
import io.github.aj4abinjacob.chikku.core.model.cards.CardsSettingsCodec

/**
 * Versioned JSON form of [LauncherSettings], used for storage and backups.
 *
 * Tolerant by design: unknown keys are ignored, missing or wrongly-typed keys and unknown enum
 * names fall back to the default, and the result is [LauncherSettings.coerced]. Malformed roots and structured card data are rejected recoverably
 * instead of silently discarding user-created cards. Adding a field: give it a default, add one line to [encode]
 * and one to [decode]; no version bump needed. Bump [FORMAT_VERSION] only when a field changes
 * meaning, and migrate the old meaning in [decode].
 */
object LauncherSettingsCodec {
    const val FORMAT_VERSION = 1

    fun encode(settings: LauncherSettings): String = MiniJson.stringify(toMap(settings))

    /** @throws SettingsFormatException when [json] is not a JSON object. */
    fun decode(json: String): LauncherSettings {
        val root = try {
            MiniJson.parse(json)
        } catch (e: MiniJson.ParseException) {
            throw SettingsFormatException("settings are not valid JSON", e)
        }
        if (root !is Map<*, *>) throw SettingsFormatException("settings root is not an object")
        return fromMap(root)
    }

    fun decodeOrNull(json: String): LauncherSettings? = try {
        decode(json)
    } catch (_: SettingsFormatException) {
        null
    }

    /** Map form, so a backup file can embed settings inside a larger document. */
    fun toMap(s: LauncherSettings): Map<String, Any?> = linkedMapOf(
        "formatVersion" to FORMAT_VERSION,
        "cards" to CardsSettingsCodec.toMap(s.cards),
        "home" to homeToMap(s.home),
        "dock" to dockToMap(s.dock),
        "drawer" to drawerToMap(s.drawer),
        "folders" to foldersToMap(s.folders),
        "gestures" to gesturesToMap(s.gestures),
        "search" to searchToMap(s.search),
        "badges" to badgesToMap(s.badges),
        "lookFeel" to lookFeelToMap(s.lookFeel),
        "night" to nightToMap(s.night),
        "labs" to labsToMap(s.labs),
        "onboarding" to linkedMapOf("completed" to s.onboarding.completed),
    )

    fun fromMap(root: Map<*, *>): LauncherSettings {
        if (root.containsKey("cards") && root["cards"] == null) throw SettingsFormatException("invalid card settings")
        val r = Section(root)
        return LauncherSettings(
            home = decodeHome(r.section("home")),
            dock = decodeDock(r.section("dock")),
            drawer = decodeDrawer(r.section("drawer")),
            folders = decodeFolders(r.section("folders")),
            gestures = decodeGestures(r.section("gestures")),
            search = decodeSearch(r.section("search")),
            badges = decodeBadges(r.section("badges")),
            lookFeel = decodeLookFeel(r.section("lookFeel")),
            night = decodeNight(r.section("night")),
            labs = decodeLabs(r.section("labs")),
            onboarding = decodeOnboarding(r.section("onboarding")),
            cards = CardsSettingsCodec.fromMap(root["cards"]),
        ).coerced()
    }

    private fun homeToMap(x: HomeSettings): Map<String, Any?> = with(x) {
        linkedMapOf(
            "desktopColumns" to desktopColumns,
            "desktopRows" to desktopRows,
            "iconScale" to iconScale,
            "showLabels" to showLabels,
            "labelScale" to labelScale,
            "iconLabelGapDp" to iconLabelGapDp,
            "iconVerticalOffsetDp" to iconVerticalOffsetDp,
            "horizontalPadding" to horizontalPadding,
            "verticalPadding" to verticalPadding,
            "widgetPadding" to widgetPadding,
            "widgetCornerRadiusDp" to widgetCornerRadiusDp,
            "wallpaperScrolling" to wallpaperScrolling,
            "pageIndicatorStyle" to pageIndicatorStyle,
            "pageIndicatorAutoHide" to pageIndicatorAutoHide,
            "infiniteScroll" to infiniteScroll,
            "layoutLocked" to layoutLocked,
            "allowWidgetOverlap" to allowWidgetOverlap,
            "defaultPage" to defaultPage,
            "showSearchBar" to showSearchBar,
            "searchBarPosition" to searchBarPosition,
            "googleDiscoverEnabled" to googleDiscoverEnabled,
            "labelFont" to labelFont,
            "labelShadow" to labelShadow,
            "labelSingleLine" to labelSingleLine,
            "labelColor" to labelColor,
            "transitionEffect" to transitionEffect,
            "addNewApps" to addNewApps,
            "showEdgeShadow" to showEdgeShadow,
            "pageSwipeSensitivity" to pageSwipeSensitivity,
            "pageSnapDurationMs" to pageSnapDurationMs,
            "pageEffectStrength" to pageEffectStrength,
            "pageEdgeFeedback" to pageEdgeFeedback,
        )
    }

    private fun dockToMap(x: DockSettings): Map<String, Any?> = with(x) {
        linkedMapOf(
            "enabled" to enabled,
            "iconCount" to iconCount,
            "iconScale" to iconScale,
            "showLabels" to showLabels,
            "background" to background,
            "backgroundImageUri" to backgroundImageUri,
            "backgroundAlpha" to backgroundAlpha,
            "backgroundColor" to backgroundColor,
            "pages" to pages,
            "matchDesktopSize" to matchDesktopSize,
            "labelScale" to labelScale,
            "labelFont" to labelFont,
            "labelShadow" to labelShadow,
            "labelSingleLine" to labelSingleLine,
            "horizontalPadding" to horizontalPadding,
            "verticalPadding" to verticalPadding,
            "backgroundEnabled" to backgroundEnabled,
            "infiniteScroll" to infiniteScroll,
            "displayAsOverlay" to displayAsOverlay,
            "automaticallyClose" to automaticallyClose,
            "rememberPosition" to rememberPosition,
            "lastPage" to lastPage,
            "drawBehindNavigationBar" to drawBehindNavigationBar,
            "backgroundInset" to backgroundInset,
            "topLeftCorner" to linkedMapOf("size" to topLeftCorner.size, "shape" to topLeftCorner.shape),
            "topRightCorner" to linkedMapOf("size" to topRightCorner.size, "shape" to topRightCorner.shape),
            "bottomLeftCorner" to linkedMapOf("size" to bottomLeftCorner.size, "shape" to bottomLeftCorner.shape),
            "bottomRightCorner" to linkedMapOf("size" to bottomRightCorner.size, "shape" to bottomRightCorner.shape),

        )
    }

    private fun drawerToMap(x: DrawerSettings): Map<String, Any?> = with(x) {
        linkedMapOf(
            "columns" to columns,
            "rows" to rows,
            "automaticRowSpacing" to automaticRowSpacing,
            "iconScale" to iconScale,
            "showLabels" to showLabels,
            "style" to style,
            "backgroundAlpha" to backgroundAlpha,
            "backgroundColor" to backgroundColor,
            "searchBarPosition" to searchBarPosition,
            "openKeyboardOnOpen" to openKeyboardOnOpen,
            "openMethod" to openMethod,
            "showFastScroller" to showFastScroller,
            "fastScrollerStyle" to fastScrollerStyle,
            "fastScrollerSide" to fastScrollerSide,
            "fastScrollerGapDp" to fastScrollerGapDp,
            "fastScrollerPopup" to fastScrollerPopup,
            "searchBarStyle" to searchBarStyle,
            "showWorkTab" to showWorkTab,
            "groupsEnabled" to groupsEnabled,
            "labelScale" to labelScale,
            "labelFont" to labelFont,
            "labelShadow" to labelShadow,
            "labelSingleLine" to labelSingleLine,
            "labelColor" to labelColor,
            "matchDesktopSize" to matchDesktopSize,
            "infiniteScroll" to infiniteScroll,
            "rememberScrollPosition" to rememberScrollPosition,
            "fastScrollerColor" to fastScrollerColor,
            "tabBarPosition" to tabBarPosition,
            "tabStyle" to tabStyle,
            "tabAccentColor" to tabAccentColor,
            "pullToSearch" to pullToSearch,
            "automaticallyClose" to automaticallyClose,
            "transition" to transition,
            "pageSwipeSensitivity" to pageSwipeSensitivity,
            "pageSnapDurationMs" to pageSnapDurationMs,
            "pageEffectStrength" to pageEffectStrength,
            "pageEdgeFeedback" to pageEdgeFeedback,
            "pageTransitionEffect" to pageTransitionEffect,
            "pageIndicatorStyle" to pageIndicatorStyle,
            "pageIndicatorAutoHide" to pageIndicatorAutoHide,
            "listScrollSpeed" to listScrollSpeed,
            "listEdgeEffect" to listEdgeEffect,
        )
    }

    private fun foldersToMap(x: FolderSettings): Map<String, Any?> = with(x) {
        linkedMapOf(
            "previewStyle" to previewStyle,
            "iconBackground" to iconBackground,
            "iconBackgroundColor" to iconBackgroundColor,
            "backgroundAlpha" to backgroundAlpha,
            "backgroundColor" to backgroundColor,
            "swipeActionsEnabled" to swipeActionsEnabled,
            "windowStyle" to windowStyle,
            "columns" to columns,
            "iconScale" to iconScale,
            "showLabels" to showLabels,
            "labelScale" to labelScale,
            "sortOrder" to sortOrder,
            "previewCount" to previewCount,
            "closeOnLaunch" to closeOnLaunch,
            "matchDesktopSize" to matchDesktopSize,
            "labelFont" to labelFont,
            "labelShadow" to labelShadow,
            "labelSingleLine" to labelSingleLine,
            "labelColor" to labelColor,
            "showName" to showName,
            "cornerRadiusDp" to cornerRadiusDp,
            "windowShadow" to windowShadow,
            "scrollStyle" to scrollStyle,
            "rows" to rows,
            "covers" to FolderCoverCodec.toMap(covers),
        )
    }

    private fun gesturesToMap(x: GestureSettings): Map<String, Any?> = with(x) {
        linkedMapOf(
            "swipeUp" to GestureAction.encode(swipeUp),
            "swipeDown" to GestureAction.encode(swipeDown),
            "doubleTap" to GestureAction.encode(doubleTap),
            "pinchIn" to GestureAction.encode(pinchIn),
            "pinchOut" to GestureAction.encode(pinchOut),
            "twoFingerSwipeUp" to GestureAction.encode(twoFingerSwipeUp),
            "twoFingerSwipeDown" to GestureAction.encode(twoFingerSwipeDown),
            "homeGesture" to GestureAction.encode(homeGesture),
            "swipeLeft" to GestureAction.encode(swipeLeft),
            "swipeRight" to GestureAction.encode(swipeRight),
            "doubleTapSwipeUp" to GestureAction.encode(doubleTapSwipeUp),
            "doubleTapSwipeDown" to GestureAction.encode(doubleTapSwipeDown),
            "rotateClockwise" to GestureAction.encode(rotateClockwise),
            "rotateCounterclockwise" to GestureAction.encode(rotateCounterclockwise),
            "homeButtonOnlyOnDefaultPage" to homeButtonOnlyOnDefaultPage,
        )
    }

    private fun searchToMap(x: SearchSettings): Map<String, Any?> = with(x) {
        linkedMapOf(
            "webEngine" to webEngine,
            "showWebSuggestionRow" to showWebSuggestionRow,
            "calculatorEnabled" to calculatorEnabled,
            "microResultsEnabled" to microResultsEnabled,
            "historyLearningEnabled" to historyLearningEnabled,
            "historySuggestionsEnabled" to historySuggestionsEnabled,
            "contactsEnabled" to contactsEnabled,
            "shortcutsEnabled" to shortcutsEnabled,
            "playStoreRow" to playStoreRow,
            "onlineSuggestions" to onlineSuggestions,
            "presentation" to presentation,
            "resultsAnchor" to resultsAnchor,
            "backgroundColor" to backgroundColor,
            "cornerRadiusDp" to cornerRadiusDp,
            "maxAppResults" to maxAppResults,
            "privateKeyboard" to privateKeyboard,
            "widgetColor" to widgetColor,
            "widgetStyle" to widgetStyle,
            "widgetIcon" to widgetIcon,
            "widgetTapAction" to widgetTapAction,
            "widgetChikkuButton" to widgetChikkuButton,
            "widgetWebButton" to widgetWebButton,
            "widgetCornerRadiusDp" to widgetCornerRadiusDp,
            "widgetShadow" to widgetShadow,
            "widgetWidthPercent" to widgetWidthPercent,
            "widgetHeightDp" to widgetHeightDp,
            "widgetHorizontalPaddingDp" to widgetHorizontalPaddingDp,
            "widgetVerticalPaddingDp" to widgetVerticalPaddingDp,
            "widgetDockGapDp" to widgetDockGapDp,
            "foldersEnabled" to foldersEnabled,
            "contactCardButtons" to contactCardButtons,
            "appPriorityDirection" to appPriorityDirection,
        )
    }

    private fun badgesToMap(x: BadgeSettings): Map<String, Any?> = with(x) {
        linkedMapOf("style" to style, "dotColor" to dotColor, "position" to position, "badgeScale" to badgeScale)
    }

    private fun lookFeelToMap(x: LookFeelSettings): Map<String, Any?> = with(x) {
        linkedMapOf(
            "iconPackPackage" to iconPackPackage,
            "iconShape" to iconShape,
            "reshapeLegacyIcons" to reshapeLegacyIcons,
            "animationSpeed" to animationSpeed,
            "orientation" to orientation,
            "statusBar" to statusBar,
            "appMenuStyle" to appMenuStyle,
            "accentColor" to accentColor,
            "menuBackgroundColor" to menuBackgroundColor,
            "menuTextColor" to menuTextColor,
            "menuCornerRadiusDp" to menuCornerRadiusDp,
            "menuShowUninstallHome" to menuShowUninstallHome,
            "menuShowUninstallDrawer" to menuShowUninstallDrawer,
            "menuShowStoreHome" to menuShowStoreHome,
            "menuShowStoreDrawer" to menuShowStoreDrawer,
            "menuShowHideHome" to menuShowHideHome,
            "menuShowHideDrawer" to menuShowHideDrawer,
            "menuShowEditHome" to menuShowEditHome,
            "menuShowEditDrawer" to menuShowEditDrawer,
            "menuShowInfoHome" to menuShowInfoHome,
            "menuShowInfoDrawer" to menuShowInfoDrawer,
            "menuShowSwipeHome" to menuShowSwipeHome,
            "menuShowRemoveHome" to menuShowRemoveHome,
        )
    }

    private fun nightToMap(x: NightSettings): Map<String, Any?> = with(x) {
        linkedMapOf(
            "mode" to mode,
            "darkDrawer" to darkDrawer,
            "darkFolders" to darkFolders,
            "darkSearch" to darkSearch,
            "darkMenus" to darkMenus,
            "pureBlack" to pureBlack,
        )
    }

    private fun labsToMap(x: LabsSettings): Map<String, Any?> = with(x) {
        linkedMapOf(
            "unlocked" to unlocked,
            "debugOverlay" to debugOverlay,
            "fasterIconCache" to fasterIconCache,
            "extendedGrid" to extendedGrid,
        )
    }

    private fun decodeHome(h: Section): HomeSettings = HomeSettings().let { d ->
        HomeSettings(
            desktopColumns = h.int("desktopColumns", d.desktopColumns),
            desktopRows = h.int("desktopRows", d.desktopRows),
            iconScale = h.float("iconScale", d.iconScale),
            showLabels = h.bool("showLabels", d.showLabels),
            labelScale = h.float("labelScale", d.labelScale),
            iconLabelGapDp = h.int("iconLabelGapDp", d.iconLabelGapDp),
            iconVerticalOffsetDp = h.int("iconVerticalOffsetDp", d.iconVerticalOffsetDp),
            horizontalPadding = h.enum("horizontalPadding", d.horizontalPadding),
            verticalPadding = h.enum("verticalPadding", d.verticalPadding),
            widgetPadding = h.enum("widgetPadding", d.widgetPadding),
            widgetCornerRadiusDp = h.float("widgetCornerRadiusDp", d.widgetCornerRadiusDp),
            wallpaperScrolling = h.bool("wallpaperScrolling", d.wallpaperScrolling),
            pageIndicatorStyle = h.enum("pageIndicatorStyle", d.pageIndicatorStyle),
            pageIndicatorAutoHide = h.bool("pageIndicatorAutoHide", d.pageIndicatorAutoHide),
            infiniteScroll = h.bool("infiniteScroll", d.infiniteScroll),
            layoutLocked = h.bool("layoutLocked", d.layoutLocked),
            allowWidgetOverlap = h.bool("allowWidgetOverlap", d.allowWidgetOverlap),
            defaultPage = h.int("defaultPage", d.defaultPage),
            showSearchBar = h.bool("showSearchBar", d.showSearchBar),
            searchBarPosition = h.enum("searchBarPosition", d.searchBarPosition),
            googleDiscoverEnabled = h.bool("googleDiscoverEnabled", d.googleDiscoverEnabled),
            labelFont = h.enum("labelFont", d.labelFont),
            labelShadow = h.bool("labelShadow", d.labelShadow),
            labelSingleLine = h.bool("labelSingleLine", d.labelSingleLine),
            labelColor = h.color("labelColor", d.labelColor),
            transitionEffect = h.enum("transitionEffect", d.transitionEffect),
            addNewApps = h.bool("addNewApps", d.addNewApps),
            showEdgeShadow = h.bool("showEdgeShadow", d.showEdgeShadow),
            pageSwipeSensitivity = h.enum("pageSwipeSensitivity", d.pageSwipeSensitivity),
            pageSnapDurationMs = h.int("pageSnapDurationMs", d.pageSnapDurationMs),
            pageEffectStrength = h.float("pageEffectStrength", d.pageEffectStrength),
            pageEdgeFeedback = h.enum("pageEdgeFeedback", d.pageEdgeFeedback),
        )
    }

    private fun decodeDock(k: Section): DockSettings = DockSettings().let { d ->
        DockSettings(
            enabled = k.bool("enabled", d.enabled),
            iconCount = k.int("iconCount", d.iconCount),
            iconScale = k.float("iconScale", d.iconScale),
            showLabels = k.bool("showLabels", d.showLabels),
            background = k.enum("background", d.background),
            backgroundImageUri = k.string("backgroundImageUri", d.backgroundImageUri),
            backgroundAlpha = k.float("backgroundAlpha", d.backgroundAlpha),
            backgroundColor = k.color("backgroundColor", d.backgroundColor),
            pages = k.int("pages", d.pages),
            matchDesktopSize = k.bool("matchDesktopSize", d.matchDesktopSize),
            labelScale = k.nullableFloat("labelScale", d.labelScale),
            labelFont = k.enum("labelFont", d.labelFont),
            labelShadow = k.bool("labelShadow", d.labelShadow),
            labelSingleLine = k.bool("labelSingleLine", d.labelSingleLine),
            horizontalPadding = k.nullableEnum("horizontalPadding", d.horizontalPadding),
            verticalPadding = k.enum("verticalPadding", d.verticalPadding),
            backgroundEnabled = k.bool("backgroundEnabled", d.backgroundEnabled),
            infiniteScroll = k.bool("infiniteScroll", d.infiniteScroll),
            displayAsOverlay = k.bool("displayAsOverlay", d.displayAsOverlay),
            automaticallyClose = k.bool("automaticallyClose", d.automaticallyClose),
            rememberPosition = k.bool("rememberPosition", d.rememberPosition),
            lastPage = k.int("lastPage", d.lastPage),
            drawBehindNavigationBar = k.bool("drawBehindNavigationBar", d.drawBehindNavigationBar),
            backgroundInset = k.bool("backgroundInset", d.backgroundInset),
            topLeftCorner = k.section("topLeftCorner").corner(d.topLeftCorner),
            topRightCorner = k.section("topRightCorner").corner(d.topRightCorner),
            bottomLeftCorner = k.section("bottomLeftCorner").corner(d.bottomLeftCorner),
            bottomRightCorner = k.section("bottomRightCorner").corner(d.bottomRightCorner),

        )
    }

    private fun decodeDrawer(w: Section): DrawerSettings = DrawerSettings().let { d ->
        DrawerSettings(
            columns = w.int("columns", d.columns),
            rows = w.int("rows", d.rows),
            automaticRowSpacing = w.bool("automaticRowSpacing", d.automaticRowSpacing),
            iconScale = w.float("iconScale", d.iconScale),
            showLabels = w.bool("showLabels", d.showLabels),
            style = w.enum("style", d.style),
            backgroundAlpha = w.float("backgroundAlpha", d.backgroundAlpha),
            backgroundColor = w.color("backgroundColor", d.backgroundColor),
            searchBarPosition = w.enum("searchBarPosition", d.searchBarPosition),
            openKeyboardOnOpen = w.bool("openKeyboardOnOpen", d.openKeyboardOnOpen),
            openMethod = w.enum("openMethod", d.openMethod),
            showFastScroller = w.bool("showFastScroller", d.showFastScroller),
            fastScrollerStyle = w.enum("fastScrollerStyle", d.fastScrollerStyle),
            fastScrollerSide = w.enum("fastScrollerSide", d.fastScrollerSide),
            fastScrollerGapDp = w.int("fastScrollerGapDp", d.fastScrollerGapDp),
            fastScrollerPopup = w.bool("fastScrollerPopup", d.fastScrollerPopup),
            searchBarStyle = w.enum("searchBarStyle", d.searchBarStyle),
            showWorkTab = w.bool("showWorkTab", d.showWorkTab),
            groupsEnabled = w.bool("groupsEnabled", d.groupsEnabled),
            labelScale = w.float("labelScale", d.labelScale),
            labelFont = w.enum("labelFont", d.labelFont),
            labelShadow = w.bool("labelShadow", d.labelShadow),
            labelSingleLine = w.bool("labelSingleLine", d.labelSingleLine),
            labelColor = w.color("labelColor", d.labelColor),
            matchDesktopSize = w.bool("matchDesktopSize", d.matchDesktopSize),
            infiniteScroll = w.bool("infiniteScroll", d.infiniteScroll),
            rememberScrollPosition = w.bool("rememberScrollPosition", d.rememberScrollPosition),
            fastScrollerColor = w.color("fastScrollerColor", d.fastScrollerColor),
            tabBarPosition = w.enum("tabBarPosition", d.tabBarPosition),
            tabStyle = w.enum("tabStyle", d.tabStyle),
            tabAccentColor = w.color("tabAccentColor", d.tabAccentColor),
            pullToSearch = w.bool("pullToSearch", d.pullToSearch),
            automaticallyClose = w.bool("automaticallyClose", d.automaticallyClose),
            transition = w.enum("transition", d.transition),
            pageSwipeSensitivity = w.enum("pageSwipeSensitivity", d.pageSwipeSensitivity),
            pageSnapDurationMs = w.int("pageSnapDurationMs", d.pageSnapDurationMs),
            pageEffectStrength = w.float("pageEffectStrength", d.pageEffectStrength),
            pageEdgeFeedback = w.enum("pageEdgeFeedback", d.pageEdgeFeedback),
            pageTransitionEffect = w.enum("pageTransitionEffect", d.pageTransitionEffect),
            pageIndicatorStyle = w.enum("pageIndicatorStyle", d.pageIndicatorStyle),
            pageIndicatorAutoHide = w.bool("pageIndicatorAutoHide", d.pageIndicatorAutoHide),
            listScrollSpeed = w.enum("listScrollSpeed", d.listScrollSpeed),
            listEdgeEffect = w.enum("listEdgeEffect", d.listEdgeEffect),
        )
    }

    private fun decodeFolders(f: Section): FolderSettings = FolderSettings().let { d ->
        FolderSettings(
            previewStyle = f.enum("previewStyle", d.previewStyle),
            iconBackground = f.enum("iconBackground", d.iconBackground),
            // Older versions used one color for the preview and window. Preserve that
            // preview color once during decode; an explicit null means the icon default.
            iconBackgroundColor = f.color("iconBackgroundColor", f.color("backgroundColor", d.iconBackgroundColor)),
            backgroundAlpha = f.float("backgroundAlpha", d.backgroundAlpha),
            backgroundColor = f.color("backgroundColor", d.backgroundColor),
            swipeActionsEnabled = f.bool("swipeActionsEnabled", d.swipeActionsEnabled),
            windowStyle = f.enum("windowStyle", d.windowStyle),
            columns = f.int("columns", d.columns),
            iconScale = f.float("iconScale", d.iconScale),
            showLabels = f.bool("showLabels", d.showLabels),
            labelScale = f.float("labelScale", d.labelScale),
            sortOrder = f.enum("sortOrder", d.sortOrder),
            previewCount = f.int("previewCount", d.previewCount),
            closeOnLaunch = f.bool("closeOnLaunch", d.closeOnLaunch),
            matchDesktopSize = f.bool("matchDesktopSize", d.matchDesktopSize),
            labelFont = f.enum("labelFont", d.labelFont),
            labelShadow = f.bool("labelShadow", d.labelShadow),
            labelSingleLine = f.bool("labelSingleLine", d.labelSingleLine),
            labelColor = f.color("labelColor", d.labelColor),
            showName = f.bool("showName", d.showName),
            cornerRadiusDp = f.float("cornerRadiusDp", d.cornerRadiusDp),
            windowShadow = f.bool("windowShadow", d.windowShadow),
            scrollStyle = f.enum("scrollStyle", d.scrollStyle),
            rows = f.int("rows", d.rows),
            covers = FolderCoverCodec.fromMap(f.raw("covers")),
        )
    }

    private fun decodeGestures(g: Section): GestureSettings = GestureSettings().let { d ->
        GestureSettings(
            swipeUp = g.action("swipeUp", d.swipeUp),
            swipeDown = g.action("swipeDown", d.swipeDown),
            doubleTap = g.action("doubleTap", d.doubleTap),
            pinchIn = g.action("pinchIn", d.pinchIn),
            pinchOut = g.action("pinchOut", d.pinchOut),
            twoFingerSwipeUp = g.action("twoFingerSwipeUp", d.twoFingerSwipeUp),
            twoFingerSwipeDown = g.action("twoFingerSwipeDown", d.twoFingerSwipeDown),
            // Stored as "homeButton" before Home opened search by default. Only a changed choice
            // carries over; the old default (go to the main page) takes the new default.
            homeGesture = g.action(
                "homeGesture",
                g.action("homeButton", d.homeGesture).takeUnless { it == GestureAction.GoToDefaultPage } ?: d.homeGesture,
            ),
            swipeLeft = g.action("swipeLeft", d.swipeLeft),
            swipeRight = g.action("swipeRight", d.swipeRight),
            doubleTapSwipeUp = g.action("doubleTapSwipeUp", d.doubleTapSwipeUp),
            doubleTapSwipeDown = g.action("doubleTapSwipeDown", d.doubleTapSwipeDown),
            rotateClockwise = g.action("rotateClockwise", d.rotateClockwise),
            rotateCounterclockwise = g.action("rotateCounterclockwise", d.rotateCounterclockwise),
            homeButtonOnlyOnDefaultPage = g.bool("homeButtonOnlyOnDefaultPage", d.homeButtonOnlyOnDefaultPage),
        )
    }

    private fun decodeSearch(s: Section): SearchSettings = SearchSettings().let { d ->
        val webRow = s.bool("showWebSuggestionRow", d.showWebSuggestionRow)
        val style = s.enum("widgetStyle", d.widgetStyle)
        // Before bar buttons, the end mark showed unless a Provider bar had the web result off.
        val legacyWebButton = if (style == HomeSearchBarStyle.PROVIDER && !webRow) HomeSearchBarButtonSlot.HIDDEN else d.widgetWebButton
        SearchSettings(
            webEngine = s.enum("webEngine", d.webEngine),
            showWebSuggestionRow = webRow,
            calculatorEnabled = s.bool("calculatorEnabled", d.calculatorEnabled),
            microResultsEnabled = s.bool("microResultsEnabled", d.microResultsEnabled),
            // Settings and backups from before search history used "frequentAppsEnabled".
            historyLearningEnabled = s.bool("historyLearningEnabled", s.bool("frequentAppsEnabled", d.historyLearningEnabled)),
            historySuggestionsEnabled = s.bool("historySuggestionsEnabled", d.historySuggestionsEnabled),
            contactsEnabled = s.bool("contactsEnabled", d.contactsEnabled),
            shortcutsEnabled = s.bool("shortcutsEnabled", d.shortcutsEnabled),
            playStoreRow = s.bool("playStoreRow", d.playStoreRow),
            onlineSuggestions = s.bool("onlineSuggestions", d.onlineSuggestions),
            presentation = s.enum("presentation", d.presentation),
            resultsAnchor = s.enum("resultsAnchor", d.resultsAnchor),
            backgroundColor = s.color("backgroundColor", d.backgroundColor),
            cornerRadiusDp = s.float("cornerRadiusDp", d.cornerRadiusDp),
            maxAppResults = s.int("maxAppResults", d.maxAppResults),
            privateKeyboard = s.bool("privateKeyboard", d.privateKeyboard),
            widgetColor = s.color("widgetColor", d.widgetColor),
            widgetStyle = style,
            widgetIcon = s.enum("widgetIcon", d.widgetIcon),
            widgetTapAction = s.enum("widgetTapAction", d.widgetTapAction),
            widgetChikkuButton = s.enum("widgetChikkuButton", d.widgetChikkuButton),
            widgetWebButton = s.enum("widgetWebButton", legacyWebButton),
            widgetCornerRadiusDp = s.float("widgetCornerRadiusDp", d.widgetCornerRadiusDp),
            widgetShadow = s.bool("widgetShadow", d.widgetShadow),
            widgetWidthPercent = s.int("widgetWidthPercent", d.widgetWidthPercent),
            widgetHeightDp = s.int("widgetHeightDp", d.widgetHeightDp),
            widgetHorizontalPaddingDp = s.int("widgetHorizontalPaddingDp", d.widgetHorizontalPaddingDp),
            widgetVerticalPaddingDp = s.int("widgetVerticalPaddingDp", d.widgetVerticalPaddingDp),
            widgetDockGapDp = s.int("widgetDockGapDp", d.widgetDockGapDp),
            foldersEnabled = s.bool("foldersEnabled", d.foldersEnabled),
            contactCardButtons = s.strings("contactCardButtons", d.contactCardButtons),
            appPriorityDirection = s.enum("appPriorityDirection", d.appPriorityDirection),
        )
    }

    private fun decodeBadges(b: Section): BadgeSettings = BadgeSettings().let { d ->
        BadgeSettings(
            style = b.enum("style", d.style), dotColor = b.color("dotColor", d.dotColor),
            position = b.enum("position", d.position), badgeScale = b.float("badgeScale", d.badgeScale),
        )
    }

    private fun decodeLookFeel(l: Section): LookFeelSettings = LookFeelSettings().let { d ->
        LookFeelSettings(
            iconPackPackage = l.string("iconPackPackage", d.iconPackPackage),
            iconShape = l.enum("iconShape", d.iconShape),
            reshapeLegacyIcons = l.bool("reshapeLegacyIcons", d.reshapeLegacyIcons),
            animationSpeed = l.enum("animationSpeed", d.animationSpeed),
            orientation = l.enum("orientation", d.orientation),
            statusBar = l.enum("statusBar", d.statusBar),
            appMenuStyle = l.enum("appMenuStyle", d.appMenuStyle),
            accentColor = l.color("accentColor", d.accentColor),
            menuBackgroundColor = l.color("menuBackgroundColor", d.menuBackgroundColor),
            menuTextColor = l.color("menuTextColor", d.menuTextColor),
            menuCornerRadiusDp = l.float("menuCornerRadiusDp", d.menuCornerRadiusDp),
            menuShowUninstallHome = l.bool("menuShowUninstallHome", d.menuShowUninstallHome),
            menuShowUninstallDrawer = l.bool("menuShowUninstallDrawer", d.menuShowUninstallDrawer),
            menuShowStoreHome = l.bool("menuShowStoreHome", d.menuShowStoreHome),
            menuShowStoreDrawer = l.bool("menuShowStoreDrawer", d.menuShowStoreDrawer),
            menuShowHideHome = l.bool("menuShowHideHome", d.menuShowHideHome),
            menuShowHideDrawer = l.bool("menuShowHideDrawer", d.menuShowHideDrawer),
            menuShowEditHome = l.bool("menuShowEditHome", d.menuShowEditHome),
            menuShowEditDrawer = l.bool("menuShowEditDrawer", d.menuShowEditDrawer),
            menuShowInfoHome = l.bool("menuShowInfoHome", d.menuShowInfoHome),
            menuShowInfoDrawer = l.bool("menuShowInfoDrawer", d.menuShowInfoDrawer),
            menuShowSwipeHome = l.bool("menuShowSwipeHome", d.menuShowSwipeHome),
            menuShowRemoveHome = l.bool("menuShowRemoveHome", d.menuShowRemoveHome),
        )
    }

    private fun decodeNight(n: Section): NightSettings = NightSettings().let { d ->
        NightSettings(
            mode = n.enum("mode", d.mode),
            darkDrawer = n.bool("darkDrawer", d.darkDrawer),
            darkFolders = n.bool("darkFolders", d.darkFolders),
            darkSearch = n.bool("darkSearch", d.darkSearch),
            darkMenus = n.bool("darkMenus", d.darkMenus),
            pureBlack = n.bool("pureBlack", d.pureBlack),
        )
    }

    private fun decodeLabs(x: Section): LabsSettings = LabsSettings().let { d ->
        LabsSettings(
            unlocked = x.bool("unlocked", d.unlocked),
            debugOverlay = x.bool("debugOverlay", d.debugOverlay),
            fasterIconCache = x.bool("fasterIconCache", d.fasterIconCache),
            extendedGrid = x.bool("extendedGrid", d.extendedGrid),
        )
    }

    private fun decodeOnboarding(o: Section): OnboardingSettings =
        OnboardingSettings(completed = o.bool("completed", OnboardingSettings().completed))

    /** Typed, default-on-mismatch accessors over one JSON object (an absent section reads as empty). */
    private class Section(private val map: Map<*, *>) {
        fun section(key: String) = Section(map[key] as? Map<*, *> ?: emptyMap<String, Any?>())

        /** The untyped value, for sub-codecs that do their own validation. */
        fun raw(key: String): Any? = map[key]

        fun int(key: String, default: Int): Int = when (val v = map[key]) {
            is Long -> if (v in Int.MIN_VALUE..Int.MAX_VALUE) v.toInt() else default
            is Double -> if (v.isFinite() && v in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble()) v.toInt() else default
            else -> default
        }

        fun float(key: String, default: Float): Float = when (val v = map[key]) {
            is Long -> v.toFloat()
            is Double -> if (v.isFinite()) v.toFloat() else default
            else -> default
        }

        fun nullableFloat(key: String, default: Float?): Float? = when (map[key]) {
            null -> default
            is Long, is Double -> float(key, default ?: 1f)
            else -> default
        }

        inline fun <reified E : Enum<E>> nullableEnum(key: String, default: E?): E? {
            val name = map[key] as? String ?: return default
            return enumValues<E>().firstOrNull { it.name == name } ?: default
        }

        fun bool(key: String, default: Boolean): Boolean = map[key] as? Boolean ?: default

        fun string(key: String, default: String?): String? =
            if (map.containsKey(key)) (map[key] as? String)?.takeIf { it.isNotBlank() } else default

        /** A JSON array of strings; non-string entries are dropped, a non-array reads as [default]. */
        fun strings(key: String, default: List<String>): List<String> =
            (map[key] as? List<*>)?.filterIsInstance<String>() ?: default

        /** ARGB stored as a number; explicit null means "theme color". */
        fun color(key: String, default: Int?): Int? {
            if (!map.containsKey(key)) return default
            return when (val v = map[key]) {
                null -> null
                is Long -> v.toInt() // accepts both signed and unsigned 32-bit renderings
                else -> default
            }
        }

        inline fun <reified E : Enum<E>> enum(key: String, default: E): E {
            val name = map[key] as? String ?: return default
            return enumValues<E>().firstOrNull { it.name == name } ?: default
        }

        fun corner(default: DockCorner): DockCorner = DockCorner(float("size", default.size), enum("shape", default.shape))

        fun action(key: String, default: GestureAction): GestureAction =
            GestureAction.decodeOrDefault(map[key] as? String, default)
    }
}

class SettingsFormatException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)
