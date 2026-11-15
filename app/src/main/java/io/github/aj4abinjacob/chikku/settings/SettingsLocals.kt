package io.github.aj4abinjacob.chikku.settings

import androidx.compose.runtime.staticCompositionLocalOf
import io.github.aj4abinjacob.chikku.applock.AppUnlockPrompt
import io.github.aj4abinjacob.chikku.home.HomeDependencies

/**
 * The app's dependencies for settings sections that need more than [LauncherSettings][io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings]
 * (app list for pickers, customizations, drawer groups, workspace export). Provided by [SettingsActivity].
 */
val LocalHomeDependencies = staticCompositionLocalOf<HomeDependencies> {
    error("LocalHomeDependencies not provided")
}

/** System authentication for App lock (locked apps opened from settings, and the App lock page). */
val LocalAppUnlockPrompt = staticCompositionLocalOf<AppUnlockPrompt> {
    error("LocalAppUnlockPrompt not provided")
}
