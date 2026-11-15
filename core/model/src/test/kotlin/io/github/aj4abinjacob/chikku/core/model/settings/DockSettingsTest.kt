package io.github.aj4abinjacob.chikku.core.model.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DockSettingsTest {
    @Test
    fun legacySettingsKeepSizingAndPadding() {
        val settings = LauncherSettingsCodec.decode("""{"home":{"labelScale":1.2,"horizontalPadding":"LARGE"},"dock":{"iconScale":0.8}}""")
        assertThat(settings.dock.effectiveIconScale(settings.home)).isEqualTo(0.8f)
        assertThat(settings.dock.effectiveLabelScale(settings.home)).isEqualTo(1.2f)
        assertThat(settings.dock.horizontalPadding).isNull()
        assertThat(settings.dock.verticalPadding).isEqualTo(PaddingSize.NONE)
        assertThat(settings.dock.displayAsOverlay).isFalse()
        assertThat(settings.dock.infiniteScroll).isFalse()
    }

    @Test
    fun customizationSurvivesBackupEncodingAndClampsDamagedInput() {
        val dock = DockSettings(
            iconCount = 4, pages = 3, matchDesktopSize = true, labelScale = 1.4f,
            labelFont = DockLabelFont.CONDENSED, labelShadow = false, labelSingleLine = false,
            horizontalPadding = PaddingSize.LARGE, verticalPadding = PaddingSize.SMALL,
            background = DockBackground.CUSTOM, backgroundEnabled = false, infiniteScroll = true,
            displayAsOverlay = true, automaticallyClose = true, rememberPosition = true, lastPage = 2,
            drawBehindNavigationBar = true, backgroundInset = true,
            topLeftCorner = DockCorner(0.6f, DockCornerShape.CUT), topRightCorner = DockCorner(0.2f),
            bottomLeftCorner = DockCorner(1f), bottomRightCorner = DockCorner(0.4f, DockCornerShape.CUT),
        )
        val settings = LauncherSettings(dock = dock)
        assertThat(LauncherSettingsCodec.decode(LauncherSettingsCodec.encode(settings))).isEqualTo(settings)
        val restored = LauncherSettingsCodec.decode("""{"dock":{"pages":1,"lastPage":99,"labelScale":99,"topLeftCorner":{"size":-1,"shape":"UNKNOWN"},"topRightCorner":{"size":9}}}""")
        assertThat(restored.dock.lastPage).isEqualTo(0)
        assertThat(restored.dock.labelScale).isEqualTo(LauncherSettings.MAX_SCALE)
        assertThat(restored.dock.topLeftCorner).isEqualTo(DockCorner())
        assertThat(restored.dock.topRightCorner.size).isEqualTo(1f)
    }

    @Test
    fun desktopMatchingRetainsIndependentSizeForLater() {
        val home = HomeSettings(iconScale = 1.4f)
        val dock = DockSettings(iconScale = 0.8f, matchDesktopSize = true)
        assertThat(dock.effectiveIconScale(home)).isEqualTo(1.4f)
        assertThat(dock.copy(matchDesktopSize = false).effectiveIconScale(home)).isEqualTo(0.8f)
    }
}
