package io.github.aj4abinjacob.chikku.theme

import android.content.Context
import android.content.res.Configuration
import io.github.aj4abinjacob.chikku.core.model.settings.NightMode
import io.github.aj4abinjacob.chikku.core.model.settings.NightSettings

/** Launcher surfaces that night mode can darken individually. */
enum class LauncherSurface { DRAWER, FOLDER, SEARCH, MENU }

/** Tone of one surface after night mode is applied. */
enum class SurfaceTone { LIGHT, DARK, BLACK }

/**
 * Color tokens for one launcher surface (ARGB). Feature code draws its surface with these instead
 * of hard-coded colors, so night mode and its per-surface toggles apply everywhere:
 * `SurfaceColors.resolve(LauncherSurface.FOLDER, settings.night, context)`.
 */
data class SurfaceColors(
    val tone: SurfaceTone,
    val background: Int,
    val onSurface: Int,
    val onSurfaceVariant: Int,
    /** Search field / input fill on this surface. */
    val field: Int,
    val divider: Int,
) {
    val isDark: Boolean get() = tone != SurfaceTone.LIGHT

    companion object {
        val LIGHT = SurfaceColors(SurfaceTone.LIGHT, 0xFFFFFFFF.toInt(), 0xFF1B1B1F.toInt(), 0xFF46464F.toInt(), 0x14000000, 0x1F000000)
        val DARK = SurfaceColors(SurfaceTone.DARK, 0xFF1B1B1F.toInt(), 0xFFE4E1E6.toInt(), 0xFFC7C5D0.toInt(), 0x1FFFFFFF, 0x1FFFFFFF)
        val BLACK = SurfaceColors(SurfaceTone.BLACK, 0xFF000000.toInt(), 0xFFE4E1E6.toInt(), 0xFFC7C5D0.toInt(), 0x1FFFFFFF, 0x1FFFFFFF)

        fun of(tone: SurfaceTone): SurfaceColors = when (tone) {
            SurfaceTone.LIGHT -> LIGHT
            SurfaceTone.DARK -> DARK
            SurfaceTone.BLACK -> BLACK
        }

        /** Colors for [surface] given the user's [night] settings and whether the system is in night mode. */
        fun resolve(surface: LauncherSurface, night: NightSettings, systemNight: Boolean): SurfaceColors =
            of(NightModeResolver.tone(surface, night, systemNight))

        fun resolve(surface: LauncherSurface, night: NightSettings, context: Context): SurfaceColors =
            resolve(surface, night, NightModeResolver.isConfigurationNight(context.resources.configuration))
    }
}

/** Pure night-mode decisions, shared by activities and surfaces. */
object NightModeResolver {

    /** Whether the launcher is in night mode. [systemNight] is the platform's own night state. */
    fun isNight(mode: NightMode, systemNight: Boolean): Boolean = when (mode) {
        NightMode.SYSTEM -> systemNight
        NightMode.ON -> true
        NightMode.OFF -> false
    }

    /**
     * Tone for [surface]: dark (or pure black) only while night mode is active and the surface's
     * toggle is on; otherwise light.
     */
    fun tone(surface: LauncherSurface, night: NightSettings, systemNight: Boolean): SurfaceTone {
        if (!isNight(night.mode, systemNight)) return SurfaceTone.LIGHT
        val enabled = when (surface) {
            LauncherSurface.DRAWER -> night.darkDrawer
            LauncherSurface.FOLDER -> night.darkFolders
            LauncherSurface.SEARCH -> night.darkSearch
            LauncherSurface.MENU -> night.darkMenus
        }
        return when {
            !enabled -> SurfaceTone.LIGHT
            night.pureBlack -> SurfaceTone.BLACK
            else -> SurfaceTone.DARK
        }
    }

    /** `uiMode` night bits for an activity override; null for [NightMode.SYSTEM] (no override). */
    fun uiModeNightBits(mode: NightMode): Int? = when (mode) {
        NightMode.SYSTEM -> null
        NightMode.ON -> Configuration.UI_MODE_NIGHT_YES
        NightMode.OFF -> Configuration.UI_MODE_NIGHT_NO
    }

    fun isConfigurationNight(configuration: Configuration): Boolean =
        (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
}
