package io.github.aj4abinjacob.chikku.theme

import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.settings.StatusBarMode
import io.github.aj4abinjacob.chikku.theme.WindowStyler.LightBars
import org.junit.Test

class SystemBarAppearanceTest {
    @Test
    fun lightWallpaperGivesDarkIcons() {
        assertThat(WindowStyler.lightSystemBars(false, false, StatusBarMode.TRANSPARENT, wallpaperDarkText = true))
            .isEqualTo(LightBars(status = true, navigation = true))
    }

    @Test
    fun darkWallpaperKeepsLightIcons() {
        assertThat(WindowStyler.lightSystemBars(false, true, StatusBarMode.TRANSPARENT, wallpaperDarkText = false))
            .isEqualTo(LightBars(status = false, navigation = false))
    }

    @Test
    fun statusBarScrimKeepsLightStatusIcons() {
        assertThat(WindowStyler.lightSystemBars(false, false, StatusBarMode.SHOWN, wallpaperDarkText = true))
            .isEqualTo(LightBars(status = false, navigation = true))
    }

    @Test
    fun openDrawerDecidesByItsOwnSurface() {
        assertThat(WindowStyler.lightSystemBars(true, false, StatusBarMode.TRANSPARENT, wallpaperDarkText = true))
            .isEqualTo(LightBars(status = false, navigation = false))
        assertThat(WindowStyler.lightSystemBars(true, true, StatusBarMode.SHOWN, wallpaperDarkText = false))
            .isEqualTo(LightBars(status = true, navigation = true))
    }
}
