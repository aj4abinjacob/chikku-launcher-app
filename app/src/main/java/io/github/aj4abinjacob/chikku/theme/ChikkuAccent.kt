package io.github.aj4abinjacob.chikku.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import io.github.aj4abinjacob.chikku.settings.components.CHIKKU_DEFAULT_ACCENT

/**
 * Primary roles from the user's Chikku accent ([CHIKKU_DEFAULT_ACCENT] when unset); an unset
 * accent is lightened on dark surfaces for contrast. Shared by settings, prompts and search so
 * every Compose surface shows the same accent.
 */
fun ColorScheme.withChikkuAccent(accent: Int?, dark: Boolean): ColorScheme {
    val brand = accent?.let { Color(it).copy(alpha = 1f) } ?: Color(CHIKKU_DEFAULT_ACCENT)
    val primary = if (accent != null) brand else if (dark) Color(0xFFFFAD89) else brand
    return copy(
        primary = primary,
        onPrimary = if (primary.luminance() > 0.179f) Color.Black else Color.White,
        primaryContainer = brand,
        onPrimaryContainer = if (brand.luminance() > 0.179f) Color.Black else Color.White,
    )
}

/**
 * Chikku's fallback palette before Android 12 (no wallpaper colors): neutral greys instead of the
 * Material baseline's purple-tinted surfaces, with secondary/tertiary containers tinted by the
 * accent so tonal rows, chips and prompt badges match the accent on every Android version.
 */
fun chikkuFallbackScheme(accent: Int?, dark: Boolean): ColorScheme {
    val brand = accent?.let { Color(it).copy(alpha = 1f) } ?: Color(CHIKKU_DEFAULT_ACCENT)
    val surface = if (dark) Color(0xFF242424) else Color(0xFFFAFAFA)
    val onSurface = if (dark) Color(0xFFE6E6E6) else Color(0xFF1C1C1C)
    val tint = brand.copy(alpha = if (dark) 0.28f else 0.16f).compositeOver(surface)
    val base = if (dark) androidx.compose.material3.darkColorScheme() else androidx.compose.material3.lightColorScheme()
    return base.copy(
        background = surface,
        onBackground = onSurface,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = if (dark) Color(0xFF3A3A3A) else Color(0xFFE2E2E2),
        onSurfaceVariant = if (dark) Color(0xFFC4C4C4) else Color(0xFF4A4A4A),
        surfaceContainerLowest = if (dark) Color(0xFF141414) else Color(0xFFFFFFFF),
        surfaceContainerLow = if (dark) Color(0xFF1E1E1E) else Color(0xFFF4F4F4),
        surfaceContainer = if (dark) Color(0xFF1C1C1C) else Color(0xFFEEEEEE),
        surfaceContainerHigh = if (dark) Color(0xFF2E2E2E) else Color(0xFFE9E9E9),
        surfaceContainerHighest = if (dark) Color(0xFF383838) else Color(0xFFE3E3E3),
        surfaceBright = if (dark) Color(0xFF3A3A3A) else Color(0xFFFAFAFA),
        surfaceDim = if (dark) Color(0xFF141414) else Color(0xFFDADADA),
        inverseSurface = if (dark) Color(0xFFE6E6E6) else Color(0xFF2F2F2F),
        inverseOnSurface = if (dark) Color(0xFF2F2F2F) else Color(0xFFF2F2F2),
        outline = if (dark) Color(0xFF8E8E8E) else Color(0xFF777777),
        outlineVariant = if (dark) Color(0xFF444444) else Color(0xFFC9C9C9),
        secondary = if (dark) Color(0xFFC4C4C4) else Color(0xFF5C5C5C),
        onSecondary = if (dark) Color(0xFF2F2F2F) else Color.White,
        secondaryContainer = tint,
        onSecondaryContainer = onSurface,
        tertiaryContainer = tint,
        onTertiaryContainer = onSurface,
    ).withChikkuAccent(accent, dark)
}
