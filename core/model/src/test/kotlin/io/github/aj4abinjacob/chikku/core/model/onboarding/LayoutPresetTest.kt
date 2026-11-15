package io.github.aj4abinjacob.chikku.core.model.onboarding

import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import org.junit.Test

class LayoutPresetTest {
    @Test
    fun defaultSettingsMatchStandardPreset() {
        assertThat(LayoutPreset.of(LauncherSettings())).isEqualTo(LayoutPreset.STANDARD)
    }

    @Test
    fun everyPresetAppliesWithinRangeAndIsRecognised() {
        LayoutPreset.entries.forEach { preset ->
            val applied = preset.applyTo(LauncherSettings())
            assertThat(applied).isEqualTo(applied.coerced())
            assertThat(LayoutPreset.of(applied)).isEqualTo(preset)
        }
    }

    @Test
    fun extraLargeUsesBigIconsAndFewerCells() {
        val s = LayoutPreset.EXTRA_LARGE.applyTo(LauncherSettings())
        assertThat(s.home.iconScale).isEqualTo(LauncherSettings.MAX_SCALE)
        assertThat(s.home.desktopColumns).isLessThan(LauncherSettings().home.desktopColumns)
        assertThat(s.dock.iconCount).isAtMost(4)
    }
}
