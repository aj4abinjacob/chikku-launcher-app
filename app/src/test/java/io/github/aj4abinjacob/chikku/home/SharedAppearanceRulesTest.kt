package io.github.aj4abinjacob.chikku.home

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.settings.DockLabelFont
import io.github.aj4abinjacob.chikku.core.model.settings.HomeSearchBarStyle
import io.github.aj4abinjacob.chikku.core.model.settings.SearchSettings
import io.github.aj4abinjacob.chikku.home.folder.FolderPanelRules
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The appearance rules the launcher and the Settings previews share. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SharedAppearanceRulesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun searchBarMetricsClampDraftsAndApplyTheMinimalMargins() {
        val wild = SearchSettings(widgetWidthPercent = 5, widgetHeightDp = 500, widgetDockGapDp = -3)
        val m = HomeSearchBarMetrics.of(wild, 2f, 1000, 32, 32, 16, belowDock = true)
        assertThat(m.height).isEqualTo(192) // 96 dp
        assertThat(m.dockGap).isEqualTo(0)
        assertThat(m.width).isEqualTo(936 * 40 / 100) // 40 %
        val minimal = SearchSettings(widgetStyle = HomeSearchBarStyle.MINIMAL)
        val minimalBelow = HomeSearchBarMetrics.of(minimal, 2f, 1000, 32, 32, 16, belowDock = true)
        assertThat(minimalBelow.startMargin).isEqualTo(24)
        assertThat(minimalBelow.endMargin).isEqualTo(24)
        assertThat(minimalBelow.bottomMargin).isEqualTo(42)
        assertThat(minimalBelow.width).isNull() // 100 % fills
        assertThat(HomeSearchBarMetrics.of(minimal, 2f, 1000, 32, 32, 16, belowDock = false).bottomMargin).isEqualTo(16)
    }

    @Test
    fun iconLabelStyleAppliesEveryOptionToAView() {
        val view = AppIconView(context, AppIconView.Style.WORKSPACE)
        IconLabelStyle(iconScale = 1.3f, showLabels = false, labelScale = 1.2f, labelFont = DockLabelFont.MEDIUM, labelShadow = true,
            labelSingleLine = false, labelColor = 0xFF123456.toInt(), listMode = true, labelGapDp = 4, verticalOffsetDp = 3).applyTo(view)
        assertThat(view.iconScale).isEqualTo(1.3f)
        assertThat(view.showLabel).isFalse()
        assertThat(view.labelScale).isEqualTo(1.2f)
        assertThat(view.labelFont).isEqualTo(DockLabelFont.MEDIUM)
        assertThat(view.labelShadow).isTrue()
        assertThat(view.labelSingleLine).isFalse()
        assertThat(view.listMode).isTrue()
        assertThat(view.labelGapDp).isEqualTo(4)
        assertThat(view.verticalOffsetDp).isEqualTo(3)
    }

    @Test
    fun folderPanelToneFollowsOpacityAndLuminance() {
        assertThat(FolderPanelRules.isDark(0xFFFFFFFF.toInt(), 0.4f)).isTrue() // see-through
        assertThat(FolderPanelRules.isDark(0xFFFFFFFF.toInt(), 1f)).isFalse()
        assertThat(FolderPanelRules.isDark(0xFF101010.toInt(), 1f)).isTrue()
        assertThat(FolderPanelRules.iconStyle(0.4f)).isEqualTo(AppIconView.Style.WORKSPACE)
        assertThat(FolderPanelRules.iconStyle(0.5f)).isEqualTo(AppIconView.Style.DRAWER)
    }
}
