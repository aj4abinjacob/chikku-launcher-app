package io.github.aj4abinjacob.chikku.core.model.settings

import io.github.aj4abinjacob.chikku.core.model.AppKey

enum class FolderCoverMode { PREVIEW, FIRST_APP, LAST_APP, SELECTED_APP }

/** A pinned shortcut chosen as a folder cover; identity is package + shortcut id + profile. */
data class FolderCoverShortcut(val packageName: String, val shortcutId: String, val userSerial: Long)

/**
 * A folder's decorative cover; launching and gesture actions remain independent. FIRST_APP and
 * LAST_APP use the first/last app or shortcut entry; SELECTED_APP names exactly one of [app] or
 * [shortcut].
 */
data class FolderCover(
    val mode: FolderCoverMode = FolderCoverMode.PREVIEW,
    val app: AppKey? = null,
    val shortcut: FolderCoverShortcut? = null,
)

internal object FolderCoverCodec {
    fun toMap(covers: Map<String, FolderCover>): Map<String, Any?> = covers.mapValues { (_, cover) ->
        linkedMapOf(
            "mode" to cover.mode.name,
            "app" to cover.app?.let {
                linkedMapOf("packageName" to it.packageName, "className" to it.className, "userSerial" to it.userSerial)
            },
        ).apply {
            // Written only when set, so app-only covers keep the format older builds can read.
            cover.shortcut?.let {
                put("shortcut", linkedMapOf("packageName" to it.packageName, "shortcutId" to it.shortcutId, "userSerial" to it.userSerial))
            }
        }
    }

    fun fromMap(value: Any?): Map<String, FolderCover> {
        if (value == null) return emptyMap()
        val entries = value as? Map<*, *> ?: throw SettingsFormatException("invalid folder covers")
        if (entries.size > 20_000) throw SettingsFormatException("too many folder covers")
        return entries.entries.associate { (id, raw) ->
            val key = (id as? String)?.takeIf { it.isNotBlank() && it.length <= 200 }
                ?: throw SettingsFormatException("invalid folder cover identity")
            val fields = raw as? Map<*, *> ?: throw SettingsFormatException("invalid folder cover")
            val mode = FolderCoverMode.entries.find { it.name == fields["mode"] }
                ?: throw SettingsFormatException("invalid folder cover mode")
            val app = fields["app"]?.let { encoded ->
                val identity = encoded as? Map<*, *> ?: throw SettingsFormatException("invalid folder cover app")
                val packageName = (identity["packageName"] as? String)?.takeIf { it.isNotBlank() && it.length <= 1_000 }
                    ?: throw SettingsFormatException("invalid folder cover package")
                val className = (identity["className"] as? String)?.takeIf { it.isNotBlank() && it.length <= 1_000 }
                    ?: throw SettingsFormatException("invalid folder cover component")
                val serial = identity["userSerial"] as? Long ?: throw SettingsFormatException("invalid folder cover profile")
                if (serial < 0) throw SettingsFormatException("invalid folder cover profile")
                AppKey(packageName, className, serial)
            }
            val shortcut = fields["shortcut"]?.let { encoded ->
                val identity = encoded as? Map<*, *> ?: throw SettingsFormatException("invalid folder cover shortcut")
                val packageName = (identity["packageName"] as? String)?.takeIf { it.isNotBlank() && it.length <= 1_000 }
                    ?: throw SettingsFormatException("invalid folder cover package")
                val shortcutId = (identity["shortcutId"] as? String)?.takeIf { it.isNotEmpty() && it.length <= 1_000 }
                    ?: throw SettingsFormatException("invalid folder cover shortcut id")
                val serial = identity["userSerial"] as? Long ?: throw SettingsFormatException("invalid folder cover profile")
                if (serial < 0) throw SettingsFormatException("invalid folder cover profile")
                FolderCoverShortcut(packageName, shortcutId, serial)
            }
            if (app != null && shortcut != null) throw SettingsFormatException("ambiguous folder cover")
            if (mode == FolderCoverMode.SELECTED_APP && app == null && shortcut == null) {
                throw SettingsFormatException("missing folder cover app")
            }
            key to FolderCover(mode, app, shortcut)
        }
    }
}
