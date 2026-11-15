package io.github.aj4abinjacob.chikku.core.model.backup

import io.github.aj4abinjacob.chikku.core.model.AppCustomization
import io.github.aj4abinjacob.chikku.core.model.DrawerGroup
import io.github.aj4abinjacob.chikku.core.model.WorkspaceContent

/** Device-local recovery state. Unlike portable backups, live widget identities are retained. */
data class RestoreCheckpoint(
    val settingsJson: String,
    val workspace: WorkspaceContent,
    val customizations: List<AppCustomization>,
    val groups: List<DrawerGroup>,
)

data class RestoreRecord(
    val previous: RestoreCheckpoint,
    val committed: Boolean = false,
    val retainUndo: Boolean = true,
)

/** A write must be atomic and durable before returning true. A damaged read must throw, not return null. */
interface RestoreJournal {
    suspend fun read(): RestoreRecord?
    suspend fun write(record: RestoreRecord): Boolean
    suspend fun clear(): Boolean
}

/** For isolated tests; production supplies a private, no-backup file journal. */
class MemoryRestoreJournal : RestoreJournal {
    private var record: RestoreRecord? = null
    override suspend fun read() = record
    override suspend fun write(record: RestoreRecord): Boolean { this.record = record; return true }
    override suspend fun clear(): Boolean { record = null; return true }
}
