package io.github.aj4abinjacob.chikku.core.model

/**
 * What a gesture (home swipe/pinch/double tap, home button, icon or folder swipe) does.
 *
 * Persisted as a stable string via [encode]/[decode]; never rename the encoded names. Actions that
 * need an optional capability (accessibility for [LockScreen]/[Recents], EXPAND_STATUS_BAR for the
 * shade) must degrade to a visible explanation when it is missing, never to a crash.
 */
sealed interface GestureAction {
    data object None : GestureAction
    data object OpenDrawer : GestureAction
    data object OpenSearch : GestureAction

    /** Opens the Cards panel (the same one the long-press Home menu opens). */
    data object OpenCards : GestureAction
    /** Contextual folder action; resolved by the folder controller, never a global picker. */
    data object OpenFolder : GestureAction
    data object ToggleDock : GestureAction
    data object ExpandNotifications : GestureAction
    data object ExpandQuickSettings : GestureAction
    data object LockScreen : GestureAction
    data object Recents : GestureAction
    data object OpenLauncherSettings : GestureAction
    data object Assistant : GestureAction
    data object VoiceSearch : GestureAction

    /** Scroll the workspace to `home.defaultPage` (the Home button's "default page"). */
    data object GoToDefaultPage : GestureAction

    data class LaunchApp(val key: AppKey) : GestureAction

    /** A pinned/dynamic app shortcut, resolved through LauncherApps at run time. */
    data class LaunchShortcut(val packageName: String, val shortcutId: String, val userSerial: Long) : GestureAction {
        init {
            require(packageName.isNotBlank() && shortcutId.isNotEmpty()) { "invalid shortcut" }
        }
    }

    companion object {
        private val SIMPLE: Map<String, GestureAction> = mapOf(
            "none" to None,
            "open_drawer" to OpenDrawer,
            "open_search" to OpenSearch,
            "open_cards" to OpenCards,
            "open_folder" to OpenFolder,
            "toggle_dock" to ToggleDock,
            "expand_notifications" to ExpandNotifications,
            "expand_quick_settings" to ExpandQuickSettings,
            "lock_screen" to LockScreen,
            "recents" to Recents,
            "open_launcher_settings" to OpenLauncherSettings,
            "assistant" to Assistant,
            "voice_search" to VoiceSearch,
            "default_page" to GoToDefaultPage,
        )

        /**
         * Actions without arguments, in picker order: nothing, then launcher, system and voice
         * actions (pickers group them in that order). Display order only; encoding uses [SIMPLE].
         */
        val simpleActions: List<GestureAction> = listOf(
            None,
            OpenDrawer, OpenSearch, OpenCards, GoToDefaultPage, ToggleDock, OpenLauncherSettings,
            ExpandNotifications, ExpandQuickSettings, Recents, LockScreen,
            Assistant, VoiceSearch,
        )

        /**
         * Stable encoding: a simple name, `app:<userSerial>:<package>/<class>` or
         * `shortcut:<userSerial>:<package>:<shortcutId>` (the id is the remainder and may contain ':').
         */
        fun encode(action: GestureAction): String = when (action) {
            is LaunchApp -> "app:${action.key.userSerial}:${action.key.packageName}/${action.key.className}"
            is LaunchShortcut -> "shortcut:${action.userSerial}:${action.packageName}:${action.shortcutId}"
            else -> SIMPLE.entries.first { it.value == action }.key
        }

        /** Returns null for anything unrecognised or malformed; never throws. */
        fun decode(value: String?): GestureAction? {
            if (value == null) return null
            SIMPLE[value]?.let { return it }
            return runCatching {
                when {
                    value.startsWith("app:") -> {
                        val parts = value.removePrefix("app:").split(':', limit = 2)
                        val component = parts[1].split('/', limit = 2)
                        LaunchApp(AppKey(component[0], component[1], parts[0].toLong()))
                    }
                    value.startsWith("shortcut:") -> {
                        val parts = value.removePrefix("shortcut:").split(':', limit = 3)
                        LaunchShortcut(parts[1], parts[2], parts[0].toLong())
                    }
                    else -> null
                }
            }.getOrNull()
        }

        fun decodeOrDefault(value: String?, default: GestureAction): GestureAction = decode(value) ?: default
    }
}
