package io.github.aj4abinjacob.chikku.theme

import android.content.pm.ActivityInfo
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.settings.AnimationSpeed
import io.github.aj4abinjacob.chikku.core.model.settings.NightMode
import io.github.aj4abinjacob.chikku.core.model.settings.NightSettings
import io.github.aj4abinjacob.chikku.core.model.settings.ScreenOrientation
import io.github.aj4abinjacob.chikku.home.appmenu.LongPressMenuDrag
import io.github.aj4abinjacob.chikku.settings.iconpicker.filterIconNames
import org.junit.Test

/** Duration scaling, night-mode resolution and other pure look-and-feel decisions. */
class LookFeelThemeTest {

    // --- Motion -----------------------------------------------------------------------------

    @Test
    fun durationsScaleWithSpeed() {
        assertThat(Motion(AnimationSpeed.NORMAL).duration(300)).isEqualTo(300)
        assertThat(Motion(AnimationSpeed.FAST).duration(300)).isEqualTo(150)
        assertThat(Motion(AnimationSpeed.SLOW).duration(300)).isEqualTo(450)
        assertThat(Motion(AnimationSpeed.RELAXED).duration(300)).isEqualTo(600)
        assertThat(Motion(AnimationSpeed.FAST).scale).isEqualTo(0.5f)
    }

    @Test
    fun noneDisablesAnimations() {
        val motion = Motion(AnimationSpeed.NONE)
        assertThat(motion.enabled).isFalse()
        assertThat(motion.scale).isEqualTo(0f)
        assertThat(motion.duration(300)).isEqualTo(0)
    }

    @Test
    fun systemAnimatorScaleZeroDisablesAnimationsAtAnySpeed() {
        AnimationSpeed.entries.forEach { speed ->
            val motion = Motion(speed, systemAnimationsEnabled = false)
            assertThat(motion.enabled).isFalse()
            assertThat(motion.duration(250)).isEqualTo(0)
        }
    }

    @Test
    fun negativeBaseNeverYieldsNegativeDuration() {
        assertThat(Motion(AnimationSpeed.SLOW).duration(-10)).isEqualTo(0)
    }

    // --- Night mode ---------------------------------------------------------------------------

    @Test
    fun nightModeResolution() {
        assertThat(NightModeResolver.isNight(NightMode.SYSTEM, systemNight = true)).isTrue()
        assertThat(NightModeResolver.isNight(NightMode.SYSTEM, systemNight = false)).isFalse()
        assertThat(NightModeResolver.isNight(NightMode.ON, systemNight = false)).isTrue()
        assertThat(NightModeResolver.isNight(NightMode.OFF, systemNight = true)).isFalse()
    }

    @Test
    fun surfacesAreLightOutsideNightMode() {
        LauncherSurface.entries.forEach { surface ->
            assertThat(NightModeResolver.tone(surface, NightSettings(mode = NightMode.OFF), systemNight = true))
                .isEqualTo(SurfaceTone.LIGHT)
            assertThat(NightModeResolver.tone(surface, NightSettings(mode = NightMode.SYSTEM), systemNight = false))
                .isEqualTo(SurfaceTone.LIGHT)
        }
    }

    @Test
    fun perSurfaceTogglesAndPureBlack() {
        val night = NightSettings(mode = NightMode.ON, darkDrawer = true, darkFolders = false, darkSearch = true, darkMenus = false)
        assertThat(NightModeResolver.tone(LauncherSurface.DRAWER, night, false)).isEqualTo(SurfaceTone.DARK)
        assertThat(NightModeResolver.tone(LauncherSurface.FOLDER, night, false)).isEqualTo(SurfaceTone.LIGHT)
        assertThat(NightModeResolver.tone(LauncherSurface.SEARCH, night, false)).isEqualTo(SurfaceTone.DARK)
        assertThat(NightModeResolver.tone(LauncherSurface.MENU, night, false)).isEqualTo(SurfaceTone.LIGHT)

        val black = night.copy(pureBlack = true)
        assertThat(NightModeResolver.tone(LauncherSurface.DRAWER, black, false)).isEqualTo(SurfaceTone.BLACK)
        assertThat(NightModeResolver.tone(LauncherSurface.FOLDER, black, false)).isEqualTo(SurfaceTone.LIGHT)
        assertThat(SurfaceColors.resolve(LauncherSurface.DRAWER, black, false)).isEqualTo(SurfaceColors.BLACK)
        assertThat(SurfaceColors.BLACK.background).isEqualTo(0xFF000000.toInt())
    }

    @Test
    fun darkTokensContrastWithTheirSurface() {
        listOf(SurfaceColors.LIGHT, SurfaceColors.DARK, SurfaceColors.BLACK).forEach { colors ->
            assertThat(colors.onSurface).isNotEqualTo(colors.background)
            assertThat(colors.isDark).isEqualTo(colors.tone != SurfaceTone.LIGHT)
        }
    }

    @Test
    fun uiModeOverrideOnlyForExplicitModes() {
        assertThat(NightModeResolver.uiModeNightBits(NightMode.SYSTEM)).isNull()
        assertThat(NightModeResolver.uiModeNightBits(NightMode.ON)).isEqualTo(android.content.res.Configuration.UI_MODE_NIGHT_YES)
        assertThat(NightModeResolver.uiModeNightBits(NightMode.OFF)).isEqualTo(android.content.res.Configuration.UI_MODE_NIGHT_NO)
    }

    // --- Window, menu, picker ------------------------------------------------------------------

    @Test
    fun orientationMapping() {
        assertThat(WindowStyler.requestedOrientation(ScreenOrientation.AUTO)).isEqualTo(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
        assertThat(WindowStyler.requestedOrientation(ScreenOrientation.PORTRAIT)).isEqualTo(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT)
        assertThat(WindowStyler.requestedOrientation(ScreenOrientation.LANDSCAPE)).isEqualTo(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE)
    }

    @Test
    fun menuTurnsIntoDragOnlyPastTouchSlop() {
        assertThat(LongPressMenuDrag.shouldStartDrag(3f, 4f, touchSlop = 8)).isFalse()
        assertThat(LongPressMenuDrag.shouldStartDrag(6f, 8f, touchSlop = 8)).isTrue()
    }

    @Test
    fun iconSearchMatchesUnderscoresAsSpaces() {
        val names = listOf("google_maps", "gmail", "maps_alt")
        assertThat(filterIconNames(names, "")).isEqualTo(names)
        assertThat(filterIconNames(names, "Maps")).containsExactly("google_maps", "maps_alt").inOrder()
        assertThat(filterIconNames(names, "google maps")).containsExactly("google_maps")
    }
}
