package io.github.aj4abinjacob.chikku.core.model.backup

import io.github.aj4abinjacob.chikku.core.model.AppCustomization
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.CellPlacement
import io.github.aj4abinjacob.chikku.core.model.ComponentKey
import io.github.aj4abinjacob.chikku.core.model.ContainerType
import io.github.aj4abinjacob.chikku.core.model.DrawerGroup
import io.github.aj4abinjacob.chikku.core.model.DrawerSortMode
import io.github.aj4abinjacob.chikku.core.model.FolderEntry
import io.github.aj4abinjacob.chikku.core.model.GestureAction
import io.github.aj4abinjacob.chikku.core.model.ItemActions
import io.github.aj4abinjacob.chikku.core.model.ItemTarget
import io.github.aj4abinjacob.chikku.core.model.WorkspaceContent
import io.github.aj4abinjacob.chikku.core.model.WorkspaceItem
import io.github.aj4abinjacob.chikku.core.model.WorkspaceLimits
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettingsCodec
import io.github.aj4abinjacob.chikku.core.model.settings.MiniJson
import io.github.aj4abinjacob.chikku.core.model.settings.PaddingSize

/**
 * JSON form of [ChikkuBackup] (one `.json` file, written through the Storage Access Framework).
 *
 * Layout: `{"format":"chikku-backup","formatVersion":1,"createdAt":<ms>,"appVersion":"…",
 * "settings":{…LauncherSettingsCodec…},"workspace":{"pageCount":n,"items":[…]},
 * "customizations":[…],"drawerGroups":[…]}`.
 *
 * Validation is strict about structure (wrong types, missing required keys, invalid placements,
 * duplicate ids, unknown item kinds or gesture actions reject the whole file, so a damaged backup
 * can never half-apply) and tolerant of unknown keys, so later versions may add fields without a
 * version bump. A [FORMAT_VERSION] higher than this build understands is rejected with
 * [BackupError.UnsupportedVersion]. Settings inside the backup use the settings codec's own
 * tolerant scalar rules (unknown or out-of-range values fall back to defaults); malformed
 * structured cards reject the entire archive rather than discarding saved cards.
 *
 * Widget ids are never written; decoded widgets have `appWidgetId == 0` and must be re-bound.
 */
object BackupCodec {
    const val FORMAT = "chikku-backup"
    const val FORMAT_VERSION = 1

    /** Upper bound on the file size in characters, so a hostile file cannot exhaust memory. */
    const val MAX_BYTES = 4 * 1024 * 1024

    /** Upper bound on items, folder entries, customizations, groups and group members. */
    const val MAX_ENTRIES = 20_000

    /** Never return a file that our portable reader will reject. */
    fun encode(backup: ChikkuBackup): String {
        val text = encodeLocalCheckpoint(backup)
        require(decode(text) is BackupDecodeResult.Success) { "Backup exceeds portable format limits" }
        return text
    }

    /** Device-private checkpoints retain historical layouts beyond portable input bounds. */
    fun encodeLocalCheckpoint(backup: ChikkuBackup): String = MiniJson.stringify(toMap(backup.withoutWidgetIds()))

    fun decodeLocalCheckpoint(text: String): BackupDecodeResult = decodeInternal(text, local = true)

    fun decode(text: String): BackupDecodeResult = decodeInternal(text, local = false)

    private fun decodeInternal(text: String, local: Boolean): BackupDecodeResult {
        if (!local && text.length > MAX_BYTES) return BackupDecodeResult.Failure(BackupError.TooLarge)
        val root = try {
            MiniJson.parse(text)
        } catch (_: MiniJson.ParseException) {
            return BackupDecodeResult.Failure(BackupError.NotJson)
        }
        if (root !is Map<*, *> || root["format"] != FORMAT) {
            return BackupDecodeResult.Failure(BackupError.NotChikkuBackup)
        }
        val version = root["formatVersion"] as? Long
            ?: return BackupDecodeResult.Failure(BackupError.Invalid("formatVersion"))
        if (version > FORMAT_VERSION) {
            return BackupDecodeResult.Failure(BackupError.UnsupportedVersion(version, FORMAT_VERSION))
        }
        if (version < 1) return BackupDecodeResult.Failure(BackupError.Invalid("formatVersion"))
        return try {
            BackupDecodeResult.Success(Decoder(local).fromMap(root))
        } catch (e: InvalidBackup) {
            BackupDecodeResult.Failure(BackupError.Invalid(e.message ?: "invalid"))
        } catch (e: IllegalArgumentException) {
            // Domain constructors (CellPlacement, AppKey, …) reject impossible values.
            BackupDecodeResult.Failure(BackupError.Invalid(e.message ?: "invalid"))
        }
    }

    // --- Encoding --------------------------------------------------------------------------------

    private fun toMap(b: ChikkuBackup): Map<String, Any?> = linkedMapOf(
        "format" to FORMAT,
        "formatVersion" to FORMAT_VERSION,
        "createdAt" to b.createdAtMillis,
        "appVersion" to b.appVersion,
        "settings" to LauncherSettingsCodec.toMap(b.settings),
        "workspace" to linkedMapOf(
            "pageCount" to b.workspace.pageCount,
            "items" to b.workspace.items.map(::itemToMap),
        ),
        "customizations" to b.customizations.map(::customizationToMap),
        "drawerGroups" to b.drawerGroups.map(::groupToMap),
    )

    private fun itemToMap(item: WorkspaceItem): Map<String, Any?> = linkedMapOf(
        "id" to item.id,
        "container" to item.placement.container.name,
        "page" to item.placement.page,
        "x" to item.placement.x,
        "y" to item.placement.y,
        "spanX" to item.placement.spanX,
        "spanY" to item.placement.spanY,
        "zIndex" to item.zIndex,
        "actions" to linkedMapOf(
            "tap" to item.actions.tap?.let(GestureAction::encode),
            "swipeUp" to item.actions.swipeUp?.let(GestureAction::encode),
            "swipeDown" to item.actions.swipeDown?.let(GestureAction::encode),
        ),
        "target" to targetToMap(item.target),
    ).apply { item.widgetPadding?.let { put("widgetPadding", it.name) } }

    private fun targetToMap(t: ItemTarget): Map<String, Any?> = when (t) {
        is ItemTarget.App -> linkedMapOf("type" to "app") + keyToMap(t.key)
        is ItemTarget.Folder -> linkedMapOf(
            "type" to "folder",
            "title" to t.title,
            "entries" to t.entries.map { linkedMapOf("id" to it.id, "target" to targetToMap(it.target)) },
        )
        // appWidgetId is device-local and deliberately not exported.
        is ItemTarget.Widget -> linkedMapOf(
            "type" to "widget",
            "provider" to linkedMapOf(
                "package" to t.provider.packageName,
                "class" to t.provider.className,
                "userSerial" to t.provider.userSerial,
            ),
        )
        is ItemTarget.Shortcut -> linkedMapOf(
            "type" to "shortcut",
            "package" to t.packageName,
            "shortcutId" to t.shortcutId,
            "userSerial" to t.userSerial,
            "label" to t.label,
            "iconPackPackage" to t.iconPackPackage,
            "iconDrawableName" to t.iconDrawableName,
        )
    }

    private fun keyToMap(key: AppKey): Map<String, Any?> =
        linkedMapOf("package" to key.packageName, "class" to key.className, "userSerial" to key.userSerial)

    private fun customizationToMap(c: AppCustomization): Map<String, Any?> = linkedMapOf(
        "app" to keyToMap(c.key),
        "hidden" to c.hidden,
        "customLabel" to c.customLabel,
        "iconPackPackage" to c.iconPackPackage,
        "iconDrawableName" to c.iconDrawableName,
        "swipeUpAction" to c.swipeUpAction?.let(GestureAction::encode),
        "swipeDownAction" to c.swipeDownAction?.let(GestureAction::encode),
    )

    private fun groupToMap(g: DrawerGroup): Map<String, Any?> = linkedMapOf(
        "id" to g.id,
        "name" to g.name,
        "rank" to g.rank,
        "sortMode" to g.sortMode.name,
        "members" to g.members.map(::keyToMap),
    )

    // --- Decoding --------------------------------------------------------------------------------

    private class InvalidBackup(message: String) : IllegalArgumentException(message)

    private fun fail(what: String): Nothing = throw InvalidBackup(what)

    private class Decoder(private val local: Boolean) {
        fun fromMap(root: Map<*, *>): ChikkuBackup {
            val settingsMap = root["settings"] as? Map<*, *> ?: fail("settings")
            val workspaceMap = root["workspace"] as? Map<*, *> ?: fail("workspace")
            val pageCount = workspaceMap.int("pageCount", "workspace.pageCount")
            if (pageCount < 1 || (!local && pageCount > WorkspaceLimits.MAX_PAGES)) fail("workspace.pageCount")
            val items = workspaceMap.list("items", "workspace.items").map { itemFromMap(it as? Map<*, *> ?: fail("item")) }
            val allIds = items.map { it.id } +
                items.flatMap { (it.target as? ItemTarget.Folder)?.entries.orEmpty().map(FolderEntry::id) }
            if (allIds.size != allIds.toSet().size) fail("duplicate item id")

            val customizations = (root["customizations"] ?: emptyList<Any?>()).asList("customizations")
                .map { customizationFromMap(it as? Map<*, *> ?: fail("customization")) }
            if (customizations.map { it.key }.toSet().size != customizations.size) fail("duplicate customization")

            val groups = (root["drawerGroups"] ?: emptyList<Any?>()).asList("drawerGroups")
                .map { groupFromMap(it as? Map<*, *> ?: fail("drawerGroup")) }
            if (groups.map { it.id }.toSet().size != groups.size) fail("duplicate group id")

            return ChikkuBackup(
                createdAtMillis = root["createdAt"] as? Long ?: 0L,
                appVersion = root["appVersion"] as? String ?: "",
                settings = LauncherSettingsCodec.fromMap(settingsMap),
                workspace = WorkspaceContent(pageCount, items),
                customizations = customizations,
                drawerGroups = groups,
            )
        }

        private fun itemFromMap(m: Map<*, *>): WorkspaceItem {
            val container = when (m["container"]) {
                "DESKTOP" -> ContainerType.DESKTOP
                "DOCK" -> ContainerType.DOCK
                else -> fail("item.container")
            }
            val placement = CellPlacement(
                container = container,
                page = m.int("page", "item.page").also {
                    if (!local && it >= WorkspaceLimits.MAX_PAGES) fail("item.page")
                },
                x = m.int("x", "item.x").also { if (!local && it >= MAX_CELL) fail("item.x") },
                y = m.int("y", "item.y").also { if (!local && it >= MAX_CELL) fail("item.y") },
                spanX = m.int("spanX", "item.spanX").also { if (!local && it > MAX_CELL) fail("item.spanX") },
                spanY = m.int("spanY", "item.spanY").also { if (!local && it > MAX_CELL) fail("item.spanY") },
            )
            val actions = (m["actions"] ?: emptyMap<String, Any?>()) as? Map<*, *> ?: fail("item.actions")
            val target = targetFromMap(m["target"] as? Map<*, *> ?: fail("item.target"), allowFolder = true)
            return WorkspaceItem(
                id = m.string("id", "item.id"),
                placement = placement,
                target = target,
                actions = ItemActions(
                    tap = actions.action("tap"),
                    swipeUp = actions.action("swipeUp"),
                    swipeDown = actions.action("swipeDown"),
                ),
                zIndex = (m["zIndex"] ?: 0L).let { it as? Long ?: fail("item.zIndex") }.toIntChecked("item.zIndex"),
                widgetPadding = m.optString("widgetPadding")?.let { name ->
                    PaddingSize.entries.firstOrNull { it.name == name } ?: fail("item.widgetPadding")
                },
            )
        }

        private fun targetFromMap(m: Map<*, *>, allowFolder: Boolean): ItemTarget = when (m["type"]) {
            "app" -> ItemTarget.App(keyFromMap(m))
            "shortcut" -> ItemTarget.Shortcut(
                packageName = m.string("package", "shortcut.package"),
                shortcutId = m.string("shortcutId", "shortcut.id"),
                userSerial = m.long("userSerial", "shortcut.userSerial"),
                label = m.optString("label"),
                iconPackPackage = m.optString("iconPackPackage"),
                iconDrawableName = m.optString("iconDrawableName"),
            )
            "widget" -> {
                if (!allowFolder) fail("widget in folder")
                val p = m["provider"] as? Map<*, *> ?: fail("widget.provider")
                ItemTarget.Widget(
                    appWidgetId = 0,
                    provider = ComponentKey(
                        packageName = p.string("package", "widget.package"),
                        className = p["class"] as? String ?: "",
                        userSerial = p.long("userSerial", "widget.userSerial"),
                    ),
                )
            }
            "folder" -> {
                if (!allowFolder) fail("nested folder")
                val entries = m.list("entries", "folder.entries").map { e ->
                    val em = e as? Map<*, *> ?: fail("folder.entry")
                    FolderEntry(
                        id = em.string("id", "folder.entry.id"),
                        target = targetFromMap(em["target"] as? Map<*, *> ?: fail("folder.entry.target"), allowFolder = false),
                    )
                }
                ItemTarget.Folder(title = m["title"] as? String ?: fail("folder.title"), entries = entries)
            }
            else -> fail("item.type")
        }

        private fun keyFromMap(m: Map<*, *>): AppKey = AppKey(
            packageName = m.string("package", "app.package"),
            className = m.string("class", "app.class"),
            userSerial = m.long("userSerial", "app.userSerial"),
        )

        private fun customizationFromMap(m: Map<*, *>): AppCustomization = AppCustomization(
            key = keyFromMap(m["app"] as? Map<*, *> ?: fail("customization.app")),
            hidden = (m["hidden"] ?: false) as? Boolean ?: fail("customization.hidden"),
            customLabel = m.optString("customLabel"),
            iconPackPackage = m.optString("iconPackPackage"),
            iconDrawableName = m.optString("iconDrawableName"),
            swipeUpAction = m.action("swipeUpAction"),
            swipeDownAction = m.action("swipeDownAction"),
        )

        private fun groupFromMap(m: Map<*, *>): DrawerGroup = DrawerGroup(
            id = m.string("id", "group.id"),
            name = m["name"] as? String ?: fail("group.name"),
            rank = m.int("rank", "group.rank"),
            sortMode = when (m["sortMode"] ?: DrawerSortMode.ALPHABETICAL.name) {
                DrawerSortMode.ALPHABETICAL.name -> DrawerSortMode.ALPHABETICAL
                DrawerSortMode.MANUAL.name -> DrawerSortMode.MANUAL
                else -> fail("group.sortMode")
            },
            members = (m["members"] ?: emptyList<Any?>()).asList("group.members")
                .map { keyFromMap(it as? Map<*, *> ?: fail("group.member")) }
                .distinct(),
        )

        // --- Typed accessors -------------------------------------------------------------------------

        private fun Any?.asList(what: String): List<Any?> {
            val list = this as? List<*> ?: fail(what)
            if (!local && list.size > MAX_ENTRIES) fail("$what too long")
            return list
        }

        private fun Map<*, *>.list(key: String, what: String): List<Any?> = this[key].asList(what)

        private fun Map<*, *>.string(key: String, what: String): String =
            (this[key] as? String)?.takeIf { it.isNotEmpty() } ?: fail(what)

        private fun Map<*, *>.optString(key: String): String? = when (val v = this[key]) {
            null -> null
            is String -> v
            else -> fail(key)
        }

        private fun Map<*, *>.long(key: String, what: String): Long = this[key] as? Long ?: fail(what)

        private fun Map<*, *>.int(key: String, what: String): Int = long(key, what).toIntChecked(what)

        private fun Long.toIntChecked(what: String): Int =
            if (this in Int.MIN_VALUE..Int.MAX_VALUE) toInt() else fail(what)

        /** Absent or null means "default"; an unrecognised action rejects the file. */
        private fun Map<*, *>.action(key: String): GestureAction? = when (val v = this[key]) {
            null -> null
            is String -> GestureAction.decode(v) ?: fail("unknown action")
            else -> fail(key)
        }

    }

    // Local checkpoints preserve historical page counts; portable/new layouts use WorkspaceLimits.
    private const val MAX_CELL = 64
}
