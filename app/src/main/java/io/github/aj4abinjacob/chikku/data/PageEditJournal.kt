package io.github.aj4abinjacob.chikku.data

import androidx.room.withTransaction
import io.github.aj4abinjacob.chikku.core.model.settings.SettingsRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Device-local write-ahead recovery for the Room/default-page settings boundary.
 * The before image and edit commit together. An interrupted edit rolls back before access resumes.
 * No external file, portable backup, or profile-derived diagnostic receives this record.
 */
internal class PageEditJournal(
    private val db: ChikkuDatabase,
    private val settings: SettingsRepository,
    private val recoveryRequired: () -> Unit,
) {
    private val dao get() = db.workspaceDao()

    suspend fun <T> apply(before: SnapshotPayload, defaultBefore: Int, defaultAfter: Int, edit: suspend () -> T): T {
        check(dao.meta(KEY) == null) { "Page recovery is required" }
        try {
            val result = db.withTransaction {
                val snapshots = JSONArray()
                dao.retainedSnapshots().forEach {
                    snapshots.put(
                        JSONObject()
                            .put(
                                "id",
                                it.id,
                            ).put("createdAt", it.createdAt).put("reason", it.reason).put("payload", it.payloadJson),
                    )
                }
                val record = JSONObject().put("version", 1)
                    .put("workspace", WorkspaceSnapshotCodec.encode(before)).put("snapshots", snapshots)
                    .put("defaultBefore", defaultBefore).put("defaultAfter", defaultAfter)
                dao.putMeta(WorkspaceMetaEntity(KEY, record.toString()))
                edit()
            }
            settings.update { current ->
                if (current.home.defaultPage == defaultBefore) {
                    current.copy(home = current.home.copy(defaultPage = defaultAfter))
                } else {
                    current
                }
            }
            dao.deleteMeta(KEY)
            return result
        } catch (failure: Throwable) {
            withContext(NonCancellable) { if (!recover()) recoveryRequired() }
            throw failure
        }
    }

    /** Idempotent, preserves a damaged/failed record and blocks access rather than resetting it. */
    suspend fun recover(): Boolean = try {
        val text = dao.meta(KEY)
        if (text == null) {
            true
        } else {
            val record = JSONObject(text)
            require(record.getInt("version") == 1)
            val before = WorkspaceSnapshotCodec.decode(record.getString("workspace"))
            validateRecoverySnapshot(before)
            val oldDefault = record.getInt("defaultBefore")
            val newDefault = record.getInt("defaultAfter")
            require(oldDefault >= 0 && newDefault >= 0)
            val rows = record.getJSONArray("snapshots")
            val snapshots = (0 until rows.length()).map { index ->
                val row = rows.getJSONObject(index)
                WorkspaceSnapshotEntity(
                    row.getLong("id"),
                    row.getLong("createdAt"),
                    row.getString("reason"),
                    row.getString("payload"),
                )
            }
            db.withTransaction {
                dao.deleteAllItems()
                dao.deleteAllFolderItems()
                dao.insertItems(before.items)
                dao.insertFolderItems(before.folderItems)
                dao.putMeta(WorkspaceMetaEntity("pageCount", before.pageCount.toString()))
                dao.deleteAllSnapshots()
                snapshots.forEach { dao.insertSnapshot(it) }
            }
            settings.update { current ->
                if (current.home.defaultPage == newDefault) {
                    current.copy(home = current.home.copy(defaultPage = oldDefault))
                } else {
                    current
                }
            }
            dao.deleteMeta(KEY)
            true
        }
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        false
    }

    companion object {
        private const val KEY = "pendingPageEdit"
    }
}
