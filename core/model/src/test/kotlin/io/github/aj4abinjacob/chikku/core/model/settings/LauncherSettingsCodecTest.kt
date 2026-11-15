package io.github.aj4abinjacob.chikku.core.model.settings

import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.GestureAction
import io.github.aj4abinjacob.chikku.core.model.GridSpec
import org.junit.Assert.assertThrows
import org.junit.Test

class LauncherSettingsCodecTest {

    @Test
    fun defaultsMatchM0Behaviour() {
        val d = LauncherSettings()
        assertThat(d.desktopGrid).isEqualTo(GridSpec(5, 5))
        assertThat(d.dockGrid).isEqualTo(GridSpec(5, 1))
        assertThat(d.dockStorageGrid).isEqualTo(GridSpec(5, 1))
        val paged = d.copy(dock = d.dock.copy(iconCount = 4, pages = 3))
        assertThat(paged.dockGrid).isEqualTo(GridSpec(4, 1)) // one page
        assertThat(paged.dockStorageGrid).isEqualTo(GridSpec(12, 1)) // every page, side by side
        assertThat(d.gestures.swipeUp).isEqualTo(GestureAction.OpenDrawer)
        assertThat(d.drawer.openMethod).isEqualTo(DrawerOpenMethod.BOTH)
        assertThat(d.drawer.columns).isEqualTo(DrawerSettings.AUTO_COLUMNS)
        assertThat(d.home.layoutLocked).isFalse()
        assertThat(d.home.googleDiscoverEnabled).isFalse()
        assertThat(d.search.onlineSuggestions).isFalse()
        assertThat(d.search.contactsEnabled).isFalse() // optional permission: off until enabled
        assertThat(d.search.historyLearningEnabled).isFalse() // usage history: opt-in
        assertThat(d.search.historySuggestionsEnabled).isTrue()
        assertThat(d.badges.style).isEqualTo(BadgeStyle.OFF) // notification access: off until enabled
        assertThat(d.labs.unlocked).isFalse()
        assertThat(d.coerced()).isEqualTo(d)
    }

    @Test
    fun drawerRowSpacingDefaultsAndCustomCountSurviveSettingsRoundtrip() {
        val legacy = LauncherSettingsCodec.decode("""{"drawer":{"rows":7}}""")
        assertThat(legacy.drawer.automaticRowSpacing).isTrue()
        assertThat(legacy.drawer.rows).isEqualTo(7)
        val custom = legacy.copy(drawer = legacy.drawer.copy(automaticRowSpacing = false))
        assertThat(LauncherSettingsCodec.decode(LauncherSettingsCodec.encode(custom))).isEqualTo(custom)
    }

    @Test
    fun roundTripPreservesEveryField() {
        val custom = LauncherSettings(
            home = HomeSettings(
                desktopColumns = 6, desktopRows = 7, iconScale = 1.25f, showLabels = false, labelScale = 0.75f,
                horizontalPadding = PaddingSize.LARGE, verticalPadding = PaddingSize.NONE, widgetPadding = PaddingSize.MEDIUM,
                wallpaperScrolling = false, pageIndicatorStyle = PageIndicatorStyle.LINE,
                pageIndicatorAutoHide = true, infiniteScroll = true,
                layoutLocked = true, allowWidgetOverlap = true, defaultPage = 2, showSearchBar = true,
                searchBarPosition = HomeSearchBarPosition.BELOW_DOCK,
                googleDiscoverEnabled = true,
                pageSwipeSensitivity = PageSwipeSensitivity.LOW, pageSnapDurationMs = 410,
                pageEffectStrength = .4f, pageEdgeFeedback = PageEdgeFeedback.NONE,
            ),
            dock = DockSettings(false, 4, 1.1f, false, DockBackground.CARD, 0.3f, 0xFF112233.toInt(), 2),
            drawer = DrawerSettings(
                5, 6, 0.9f, false, DrawerStyle.HORIZONTAL_PAGED, 0.4f, 0x80FFFFFF.toInt(),
                DrawerSearchBarPosition.BOTTOM, true, DrawerOpenMethod.BUTTON, true, true, true,
                fastScrollerStyle = DrawerFastScrollerStyle.THUMB,
                fastScrollerSide = DrawerFastScrollerSide.LEFT,
                fastScrollerGapDp = 3,
                fastScrollerPopup = false,
                searchBarStyle = DrawerSearchBarStyle.HIGH_CONTRAST,
                pageSwipeSensitivity = PageSwipeSensitivity.HIGH, pageSnapDurationMs = 120,
                pageEffectStrength = .7f, pageEdgeFeedback = PageEdgeFeedback.NONE,
                pageTransitionEffect = PageTransitionEffect.CUBE, pageIndicatorStyle = PageIndicatorStyle.LINE,
                pageIndicatorAutoHide = true, listScrollSpeed = ListScrollSpeed.FAST, listEdgeEffect = ListEdgeEffect.NONE,
            ),
            folders = FolderSettings(FolderPreviewStyle.FAN, FolderIconBackground.NONE, 0.2f, 0x00000000, false),
            gestures = GestureSettings(
                swipeUp = GestureAction.OpenSearch,
                swipeDown = GestureAction.ExpandNotifications,
                doubleTap = GestureAction.LockScreen,
                pinchIn = GestureAction.Recents,
                pinchOut = GestureAction.LaunchApp(AppKey("a.b", "a.b.C", 10)),
                twoFingerSwipeUp = GestureAction.LaunchShortcut("a.b", "sc:1", 0),
                twoFingerSwipeDown = GestureAction.ExpandQuickSettings,
                homeGesture = GestureAction.None,
            ),
            search = SearchSettings(
                WebSearchEngine.ECOSIA, false, false, true, false, false, onlineSuggestions = true,
                historyLearningEnabled = true, historySuggestionsEnabled = false, resultsAnchor = SearchResultsAnchor.KEYBOARD,
                widgetIcon = HomeSearchBarIcon.PAW,
                widgetTapAction = HomeSearchBarTapAction.WEB_SEARCH,
                widgetChikkuButton = HomeSearchBarButtonSlot.END,
                widgetWebButton = HomeSearchBarButtonSlot.START,
                appPriorityDirection = SearchAppPriorityDirection.RIGHT_TO_LEFT,
                contactCardButtons = listOf("signal:message", "phone:voice_call", "future_app:video_call"),
            ),
            badges = BadgeSettings(BadgeStyle.DYNAMIC, 0xFFFF0000.toInt()),
            lookFeel = LookFeelSettings(
                "com.icons", IconShape.TEARDROP, true, AnimationSpeed.NONE, ScreenOrientation.PORTRAIT,
                StatusBarMode.HIDDEN, AppMenuStyle.DETAILED, 0xFF00FF00.toInt(),
            ),
            night = NightSettings(NightMode.ON, false, false, false, false, true),
            labs = LabsSettings(true, true, true, true),
            onboarding = OnboardingSettings(true),
        )
        assertThat(LauncherSettingsCodec.decode(LauncherSettingsCodec.encode(custom))).isEqualTo(custom)
        assertThat(LauncherSettingsCodec.decode(LauncherSettingsCodec.encode(LauncherSettings()))).isEqualTo(LauncherSettings())
    }

    @Test
    fun fastScrollerGapIsClampedOnDecode() {
        val wide = LauncherSettings(drawer = DrawerSettings(fastScrollerGapDp = 500))
        val decoded = LauncherSettingsCodec.decode(LauncherSettingsCodec.encode(wide))
        assertThat(decoded.drawer.fastScrollerGapDp).isEqualTo(DrawerSettings.FAST_SCROLLER_GAP_RANGE_DP.last)
    }

    @Test
    fun missingAndUnknownKeysFallBackToDefaults() {
        assertThat(LauncherSettingsCodec.decode("{}")).isEqualTo(LauncherSettings())
        val json = """
            {"formatVersion": 99, "futureSection": {"x": 1},
             "home": {"desktopColumns": 6, "iconScale": "big", "pageIndicatorStyle": "SPIRAL", "newField": true},
             "dock": "not an object",
             "gestures": {"swipeDown": "teleport", "doubleTap": "lock_screen"},
             "lookFeel": {"iconPackPackage": null, "accentColor": 4278255360}}
        """.trimIndent()
        val decoded = LauncherSettingsCodec.decode(json)
        assertThat(decoded.home.desktopColumns).isEqualTo(6)
        assertThat(decoded.home.iconScale).isEqualTo(1f)
        assertThat(decoded.home.pageIndicatorStyle).isEqualTo(PageIndicatorStyle.DOTS)
        assertThat(decoded.dock).isEqualTo(DockSettings())
        assertThat(decoded.gestures.swipeDown).isEqualTo(GestureSettings().swipeDown)
        assertThat(decoded.gestures.doubleTap).isEqualTo(GestureAction.LockScreen)
        assertThat(decoded.lookFeel.iconPackPackage).isNull()
        assertThat(decoded.lookFeel.accentColor).isEqualTo(0xFF00FF00.toInt())
    }

    @Test
    fun outOfRangeValuesAreClamped() {
        val decoded = LauncherSettingsCodec.decode(
            """{"home":{"desktopColumns":100,"desktopRows":0,"iconScale":9.5,"defaultPage":-3},
               "dock":{"iconCount":1,"backgroundAlpha":-1,"pages":50},"drawer":{"columns":-4,"rows":99}}""",
        )
        assertThat(decoded.home.desktopColumns).isEqualTo(GridSpec.MAX_DIMENSION)
        assertThat(decoded.home.desktopRows).isEqualTo(LauncherSettings.MIN_GRID)
        assertThat(decoded.home.iconScale).isEqualTo(LauncherSettings.MAX_SCALE)
        assertThat(decoded.home.defaultPage).isEqualTo(0)
        assertThat(decoded.dock.iconCount).isEqualTo(LauncherSettings.MIN_GRID)
        assertThat(decoded.dock.backgroundAlpha).isEqualTo(0f)
        assertThat(decoded.dock.pages).isEqualTo(LauncherSettings.MAX_DOCK_PAGES)
        assertThat(decoded.drawer.columns).isEqualTo(DrawerSettings.AUTO_COLUMNS)
        assertThat(decoded.drawer.rows).isEqualTo(GridSpec.MAX_DIMENSION)
    }

    @Test
    fun homeGestureOpensSearchAndIsSeparateFromSwipeUp() {
        val d = LauncherSettings()
        assertThat(d.gestures.homeGesture).isEqualTo(GestureAction.OpenSearch)
        assertThat(d.gestures.swipeUp).isEqualTo(GestureAction.OpenDrawer)
        val custom = d.copy(gestures = d.gestures.copy(homeGesture = GestureAction.Recents))
        assertThat(LauncherSettingsCodec.decode(LauncherSettingsCodec.encode(custom)).gestures.homeGesture)
            .isEqualTo(GestureAction.Recents)
    }

    @Test
    fun legacyHomeButtonKeepsAChangedChoiceAndDropsTheOldDefault() {
        fun legacy(action: GestureAction) = LauncherSettingsCodec.encode(LauncherSettings())
            .replace("\"homeGesture\"", "\"homeButton\"")
            .replace("\"homeButton\":\"${GestureAction.encode(GestureAction.OpenSearch)}\"", "\"homeButton\":\"${GestureAction.encode(action)}\"")
        assertThat(LauncherSettingsCodec.decode(legacy(GestureAction.GoToDefaultPage)).gestures.homeGesture)
            .isEqualTo(GestureAction.OpenSearch)
        assertThat(LauncherSettingsCodec.decode(legacy(GestureAction.Recents)).gestures.homeGesture)
            .isEqualTo(GestureAction.Recents)
    }

    @Test
    fun searchBarButtonsKeepTheLookOfSettingsFromBeforeButtons() {
        fun decode(search: String) = LauncherSettingsCodec.decode("""{"search": {$search}}""").search
        // A Provider bar without the web result showed no end mark, so its web button starts hidden.
        assertThat(decode(""""showWebSuggestionRow": false""").widgetWebButton).isEqualTo(HomeSearchBarButtonSlot.HIDDEN)
        assertThat(decode(""""showWebSuggestionRow": true""").widgetWebButton).isEqualTo(HomeSearchBarButtonSlot.END)
        // Minimal always showed its neutral end mark.
        assertThat(decode(""""showWebSuggestionRow": false, "widgetStyle": "MINIMAL"""").widgetWebButton)
            .isEqualTo(HomeSearchBarButtonSlot.END)
        // A saved choice wins over the migration, and the bar itself still opens Chikku search.
        val saved = decode(""""showWebSuggestionRow": false, "widgetWebButton": "START"""")
        assertThat(saved.widgetWebButton).isEqualTo(HomeSearchBarButtonSlot.START)
        assertThat(saved.widgetChikkuButton).isEqualTo(HomeSearchBarButtonSlot.START)
        assertThat(saved.widgetTapAction).isEqualTo(HomeSearchBarTapAction.CHIKKU_SEARCH)
        assertThat(decode(""""widgetTapAction": "LENS", "widgetChikkuButton": 3""").let { it.widgetTapAction to it.widgetChikkuButton })
            .isEqualTo(HomeSearchBarTapAction.CHIKKU_SEARCH to HomeSearchBarButtonSlot.START)
    }

    @Test
    fun nonObjectInputIsRejected() {
        listOf("", "[]", "42", "{", "{\"home\":}", "null").forEach { bad ->
            assertThrows(SettingsFormatException::class.java) { LauncherSettingsCodec.decode(bad) }
            assertThat(LauncherSettingsCodec.decodeOrNull(bad)).isNull()
        }
    }
    @Test
    fun contactCardButtonsDecodeFromOldAndDamagedBackups() {
        // Backups written before the setting existed get Call, Message, WhatsApp.
        assertThat(LauncherSettingsCodec.decode("""{"search": {"contactsEnabled": true}}""").search.contactCardButtons)
            .containsExactly("phone:voice_call", "sms:message", "whatsapp:message").inOrder()
        val damaged = """{"search": {"contactCardButtons": ["signal:message", 7, "", "signal:message", null, "x:teleport", "phone:voice_call"]}}"""
        assertThat(LauncherSettingsCodec.decode(damaged).search.contactCardButtons)
            .containsExactly("signal:message", "phone:voice_call").inOrder()
        // Nothing usable falls back to the default; more than four keeps the first four.
        assertThat(LauncherSettingsCodec.decode("""{"search": {"contactCardButtons": "whatsapp"}}""").search.contactCardButtons)
            .isEqualTo(SearchSettings().contactCardButtons)
        assertThat(LauncherSettingsCodec.decode("""{"search": {"contactCardButtons": []}}""").search.contactCardButtons)
            .isEqualTo(SearchSettings().contactCardButtons)
        val many = LauncherSettingsCodec.decode("""{"search": {"contactCardButtons": [${(1..9).joinToString { "\"app$it:message\"" }}]}}""")
        assertThat(many.search.contactCardButtons).hasSize(LauncherSettings.MAX_CARD_BUTTONS)
    }

    /**
     * Backup-coverage guard: every field of every settings group must be written by the codec, so a
     * new setting cannot silently fall out of backups. If this fails, add the field to
     * [LauncherSettingsCodec.toMap] and `fromMap` (and to [roundTripPreservesEveryField]); see AGENTS.md.
     */
    @Test
    fun everySettingsFieldIsWrittenToBackups() {
        val encoded = LauncherSettingsCodec.toMap(LauncherSettings())
        val missing = instanceFields(LauncherSettings::class.java).flatMap { group ->
            val groupMap = encoded[group.name] as? Map<*, *> ?: return@flatMap listOf(group.name)
            instanceFields(group.type)
                .map { "${group.name}.${it.name}" }
                .filter { path -> (STORED_AS[path] ?: path.substringAfter('.')) !in groupMap.keys && path !in NOT_STORED }
        }
        assertThat(missing).isEmpty()
    }

    private fun instanceFields(type: Class<*>) =
        type.declaredFields.filter { !java.lang.reflect.Modifier.isStatic(it.modifiers) && !it.isSynthetic }

    private companion object {
        /** Fields written under a different JSON key. */
        val STORED_AS: Map<String, String> = mapOf("cards.cards" to "items")

        /** Fields intentionally not persisted, with the reason next to each entry. */
        val NOT_STORED: Set<String> = emptySet()
    }
}
