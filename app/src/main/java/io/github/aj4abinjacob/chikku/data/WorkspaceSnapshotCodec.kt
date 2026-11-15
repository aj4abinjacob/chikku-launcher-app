package io.github.aj4abinjacob.chikku.data

import org.json.JSONArray
import org.json.JSONObject

/** Persisted workspace content captured in an undo/recovery snapshot. */
internal data class SnapshotPayload(
    val pageCount: Int,
    val items: List<WorkspaceItemEntity>,
    val folderItems: List<FolderItemEntity> = emptyList(),
    val defaultPageBefore: Int? = null,
    val defaultPageAfter: Int? = null,
)

/**
 * JSON encoding of [SnapshotPayload]. Rows are stored verbatim (half-cell units, timestamps) so a
 * restore is exact. Version 1 snapshots (app rows only, written by schema v1) still decode: the
 * columns added in v2 read as absent/null, which is exactly what the migration gives those rows.
 */
internal object WorkspaceSnapshotCodec {
    const val FORMAT_VERSION = 2
    private val SUPPORTED_VERSIONS = 1..FORMAT_VERSION

    fun encode(payload: SnapshotPayload): String {
        val items = JSONArray()
        payload.items.forEach { item ->
            items.put(
                JSONObject()
                    .put("id", item.id)
                    .put("container", item.container)
                    .put("page", item.page)
                    .put("x2", item.x2)
                    .put("y2", item.y2)
                    .put("width2", item.width2)
                    .put("height2", item.height2)
                    .put("kind", item.kind)
                    .put("packageName", item.packageName)
                    .put("className", item.className)
                    .put("userSerial", item.userSerial)
                    .put("createdAt", item.createdAt)
                    .put("updatedAt", item.updatedAt)
                    .putOpt("title", item.title)
                    .putOpt("appWidgetId", item.appWidgetId)
                    .putOpt("providerPackage", item.providerPackage)
                    .putOpt("providerClass", item.providerClass)
                    .putOpt("shortcutId", item.shortcutId)
                    .putOpt("shortcutIconPack", item.shortcutIconPack)
                    .putOpt("shortcutIconDrawable", item.shortcutIconDrawable)
                    .putOpt("swipeUpAction", item.swipeUpAction)
                    .putOpt("swipeDownAction", item.swipeDownAction)
                    .putOpt("tapAction", item.tapAction)
                    .putOpt("widgetPadding", item.widgetPadding)
                    .put("zIndex", item.zIndex),
            )
        }
        val folderItems = JSONArray()
        payload.folderItems.forEach { entry ->
            folderItems.put(
                JSONObject()
                    .put("id", entry.id)
                    .put("folderId", entry.folderId)
                    .put("rank", entry.rank)
                    .put("kind", entry.kind)
                    .put("packageName", entry.packageName)
                    .put("className", entry.className)
                    .put("userSerial", entry.userSerial)
                    .putOpt("shortcutId", entry.shortcutId)
                    .putOpt("shortcutIconPack", entry.shortcutIconPack)
                    .putOpt("shortcutIconDrawable", entry.shortcutIconDrawable)
                    .putOpt("title", entry.title),
            )
        }
        return JSONObject()
            .put("formatVersion", FORMAT_VERSION)
            .put("pageCount", payload.pageCount)
            .put("items", items)
            .put("folderItems", folderItems)
            .putOpt("defaultPageBefore", payload.defaultPageBefore)
            .putOpt("defaultPageAfter", payload.defaultPageAfter)
            .toString()
    }

    /** @throws org.json.JSONException or [IllegalArgumentException] when the payload is malformed. */
    fun decode(json: String): SnapshotPayload {
        val root = JSONObject(json)
        val version = root.getInt("formatVersion")
        require(version in SUPPORTED_VERSIONS) { "unsupported snapshot format $version" }
        val array = root.getJSONArray("items")
        val items = (0 until array.length()).map { index ->
            val o = array.getJSONObject(index)
            WorkspaceItemEntity(
                id = o.getString("id"),
                container = o.getString("container"),
                page = o.getInt("page"),
                x2 = o.getInt("x2"),
                y2 = o.getInt("y2"),
                width2 = o.getInt("width2"),
                height2 = o.getInt("height2"),
                kind = o.getString("kind"),
                packageName = o.getString("packageName"),
                className = o.getString("className"),
                userSerial = o.getLong("userSerial"),
                createdAt = o.getLong("createdAt"),
                updatedAt = o.getLong("updatedAt"),
                title = o.optStringOrNull("title"),
                appWidgetId = if (o.has("appWidgetId") && !o.isNull("appWidgetId")) o.getInt("appWidgetId") else null,
                providerPackage = o.optStringOrNull("providerPackage"),
                providerClass = o.optStringOrNull("providerClass"),
                shortcutId = o.optStringOrNull("shortcutId"),
                shortcutIconPack = o.optStringOrNull("shortcutIconPack"),
                shortcutIconDrawable = o.optStringOrNull("shortcutIconDrawable"),
                swipeUpAction = o.optStringOrNull("swipeUpAction"),
                swipeDownAction = o.optStringOrNull("swipeDownAction"),
                tapAction = o.optStringOrNull("tapAction"),
                widgetPadding = o.optStringOrNull("widgetPadding"),
                zIndex = o.optInt("zIndex", 0),
            )
        }
        // Missing is valid for older snapshots. A present, malformed value must not silently
        // become an empty folder list and authorize an Undo that loses every member.
        val folderArray = if (root.has("folderItems")) root.getJSONArray("folderItems") else JSONArray()
        val folderItems = (0 until folderArray.length()).map { index ->
            val o = folderArray.getJSONObject(index)
            FolderItemEntity(
                id = o.getString("id"),
                folderId = o.getString("folderId"),
                rank = o.getInt("rank"),
                kind = o.getString("kind"),
                packageName = o.getString("packageName"),
                className = o.getString("className"),
                userSerial = o.getLong("userSerial"),
                shortcutId = o.optStringOrNull("shortcutId"),
                shortcutIconPack = o.optStringOrNull("shortcutIconPack"),
                shortcutIconDrawable = o.optStringOrNull("shortcutIconDrawable"),
                title = o.optStringOrNull("title"),
            )
        }
        val before = if (root.has("defaultPageBefore")) root.getInt("defaultPageBefore") else null
        val after = if (root.has("defaultPageAfter")) root.getInt("defaultPageAfter") else null
        require((before == null && after == null) || (before != null && after != null && before >= 0 && after >= 0))
        return SnapshotPayload(pageCount = root.getInt("pageCount"), items = items, folderItems = folderItems,
            defaultPageBefore = before, defaultPageAfter = after)
    }

    private fun JSONObject.optStringOrNull(key: String): String? = if (has(key) && !isNull(key)) getString(key) else null
}
