package io.github.aj4abinjacob.chikku.core.model.settings

import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.GestureAction
import org.junit.Test

class ExpandedSettingsCodecTest {
    @Test
    fun everyPhoneSettingSurvivesStorageAndBackupEncoding() {
        val defaults = LauncherSettings()
        val value = defaults.copy(
            home = defaults.home.copy(labelFont = DockLabelFont.CONDENSED, labelShadow = false,
                labelSingleLine = false, labelColor = 0xFFAABBCC.toInt(), transitionEffect = PageTransitionEffect.CUBE,
                addNewApps = true, showEdgeShadow = true),
            drawer = defaults.drawer.copy(style = DrawerStyle.LIST, labelScale = 1.3f,
                labelFont = DockLabelFont.LIGHT, labelShadow = true, labelSingleLine = false,
                labelColor = 0xFF112233.toInt(), matchDesktopSize = true, infiniteScroll = true,
                rememberScrollPosition = true, fastScrollerColor = 0xFF009900.toInt(),
                tabBarPosition = DrawerTabBarPosition.BOTTOM, pullToSearch = true, automaticallyClose = false,
                transition = DrawerTransition.SCALE),
            folders = defaults.folders.copy(windowStyle = FolderWindowStyle.IMMERSIVE, columns = 6,
                iconScale = 1.2f, showLabels = false, labelScale = 0.8f, sortOrder = FolderSortOrder.ALPHABETICAL,
                previewCount = 9, closeOnLaunch = false, matchDesktopSize = true,
                labelFont = DockLabelFont.MEDIUM, labelShadow = true, labelSingleLine = false,
                labelColor = 0xFFEEDDCC.toInt(), showName = false, cornerRadiusDp = 36f, windowShadow = true),
            gestures = defaults.gestures.copy(swipeLeft = GestureAction.OpenSearch, swipeRight = GestureAction.OpenDrawer,
                doubleTapSwipeUp = GestureAction.ExpandNotifications, doubleTapSwipeDown = GestureAction.ExpandQuickSettings,
                rotateClockwise = GestureAction.GoToDefaultPage, rotateCounterclockwise = GestureAction.LockScreen,
                homeButtonOnlyOnDefaultPage = true),
            badges = defaults.badges.copy(position = BadgePosition.BOTTOM_START, badgeScale = 1.5f),
            search = defaults.search.copy(presentation = SearchPresentation.WINDOW, backgroundColor = 0xFF123456.toInt(),
                cornerRadiusDp = 30f, maxAppResults = 5, privateKeyboard = false, widgetColor = 0xFFABCDEF.toInt(),
                widgetCornerRadiusDp = 10f, widgetShadow = true, foldersEnabled = true),
            lookFeel = defaults.lookFeel.copy(menuBackgroundColor = 0xFF222222.toInt(), menuTextColor = 0xFFFFFFFF.toInt(),
                menuCornerRadiusDp = 8f, menuShowUninstallHome = false, menuShowUninstallDrawer = false,
                menuShowStoreHome = true, menuShowStoreDrawer = true, menuShowHideHome = true, menuShowHideDrawer = false,
                menuShowEditHome = false, menuShowEditDrawer = false, menuShowInfoHome = false, menuShowInfoDrawer = false,
                menuShowSwipeHome = true, menuShowRemoveHome = false),
        )
        assertThat(LauncherSettingsCodec.decode(LauncherSettingsCodec.encode(value))).isEqualTo(value)
    }

    @Test
    fun oldSettingsPreserveDefaultsAndUnknownEnumsDoNotBreakHome() {
        val defaults = LauncherSettings()
        val decoded = LauncherSettingsCodec.decode("""{"home":{"transitionEffect":"UNKNOWN"},"drawer":{"style":"FUTURE","transition":"UNKNOWN"},"search":{"presentation":"UNKNOWN"},"badges":{"position":"UNKNOWN"}}""")
        assertThat(decoded).isEqualTo(defaults)
        assertThat(defaults.home.addNewApps).isFalse()
        assertThat(defaults.search.foldersEnabled).isFalse()
        assertThat(defaults.drawer.style).isEqualTo(DrawerStyle.VERTICAL)
        assertThat(defaults.lookFeel.menuCornerRadiusDp).isEqualTo(14f)
        assertThat(defaults.folders.windowShadow).isFalse()
    }

    @Test
    fun corruptNumericValuesHaveSafeIdempotentRanges() {
        val defaults = LauncherSettings()
        val decoded = defaults.copy(
            drawer = defaults.drawer.copy(labelScale = Float.NaN),
            folders = defaults.folders.copy(columns = -9, iconScale = 99f, labelScale = -2f,
                previewCount = 20, cornerRadiusDp = Float.POSITIVE_INFINITY),
            badges = defaults.badges.copy(badgeScale = Float.NaN),
            search = defaults.search.copy(maxAppResults = 999, cornerRadiusDp = Float.NaN,
                widgetCornerRadiusDp = -1f),
            lookFeel = defaults.lookFeel.copy(menuCornerRadiusDp = Float.NEGATIVE_INFINITY),
        ).coerced()
        assertThat(decoded.drawer.labelScale).isEqualTo(1f)
        assertThat(decoded.folders.columns).isEqualTo(1)
        assertThat(decoded.folders.iconScale).isEqualTo(1.5f)
        assertThat(decoded.folders.labelScale).isEqualTo(0.5f)
        assertThat(decoded.folders.previewCount).isEqualTo(9)
        assertThat(decoded.folders.cornerRadiusDp).isEqualTo(20f)
        assertThat(decoded.badges.badgeScale).isEqualTo(1f)
        assertThat(decoded.search.maxAppResults).isEqualTo(50)
        assertThat(decoded.search.cornerRadiusDp).isEqualTo(20f)
        assertThat(decoded.search.widgetCornerRadiusDp).isEqualTo(0f)
        assertThat(decoded.lookFeel.menuCornerRadiusDp).isEqualTo(14f)
        assertThat(decoded.coerced()).isEqualTo(decoded)
    }
}
