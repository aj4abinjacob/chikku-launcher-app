package io.github.aj4abinjacob.chikku.data.restore

import android.util.AtomicFile
import io.github.aj4abinjacob.chikku.core.model.ItemTarget
import io.github.aj4abinjacob.chikku.core.model.backup.BackupCodec
import io.github.aj4abinjacob.chikku.core.model.backup.BackupDecodeResult
import io.github.aj4abinjacob.chikku.core.model.backup.ChikkuBackup
import io.github.aj4abinjacob.chikku.core.model.backup.RestoreCheckpoint
import io.github.aj4abinjacob.chikku.core.model.backup.RestoreJournal
import io.github.aj4abinjacob.chikku.core.model.backup.RestoreRecord
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettingsCodec
import io.github.aj4abinjacob.chikku.core.model.settings.MiniJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Private, device-local journal; caller passes noBackupFilesDir. Never shared as an archive. */
class FileRestoreJournal(private val directory: () -> File, private val io: CoroutineDispatcher) : RestoreJournal {
    constructor(directory: File, io: CoroutineDispatcher) : this({ directory }, io)
    // Android may create noBackupFilesDir on first access; resolve it only inside an IO operation.
    private val path by lazy { File(directory(), "restore-journal.json") }
    private val atomic by lazy { AtomicFile(path) }

    override suspend fun read(): RestoreRecord? = withContext(io) {
        if (!path.exists() && !File(path.path + ".bak").exists()) return@withContext null
        try {
            val bytes = atomic.openRead().use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= MAX_BYTES)
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            require(bytes.size <= MAX_BYTES)
            decode(bytes.toString(Charsets.UTF_8))
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { throw IOException("Restore recovery record could not be read") }
    }

    override suspend fun write(record: RestoreRecord): Boolean = withContext(io) {
        var output: java.io.FileOutputStream? = null
        try {
            val bytes = encode(record).toByteArray(Charsets.UTF_8)
            require(bytes.size <= MAX_BYTES)
            // Reject a snapshot that cannot be recovered before touching any store.
            require(decode(bytes.toString(Charsets.UTF_8)) == record)
            check(path.parentFile!!.isDirectory || path.parentFile!!.mkdirs())
            output = atomic.startWrite()
            output.write(bytes)
            output.fd.sync()
            atomic.finishWrite(output)
            path.readBytes().contentEquals(bytes)
        } catch (cancelled: CancellationException) { atomic.failWrite(output); throw cancelled }
        catch (_: Exception) { atomic.failWrite(output); false }
    }

    override suspend fun clear(): Boolean = withContext(io) {
        atomic.delete()
        !path.exists() && !File(path.path + ".bak").exists()
    }

    private fun encode(record: RestoreRecord): String {
        val previous = record.previous
        val settings = requireNotNull(LauncherSettingsCodec.decodeOrNull(previous.settingsJson))
        val archive = ChikkuBackup(0, "local-recovery", settings, previous.workspace, previous.customizations, previous.groups)
        return MiniJson.stringify(linkedMapOf(
            "version" to 1,
            "committed" to record.committed,
            "retainUndo" to record.retainUndo,
            // Raw settings preserve embedded-widget instance identities; portable codecs renew them.
            "settings" to previous.settingsJson,
            "archive" to BackupCodec.encodeLocalCheckpoint(archive),
            "widgetIds" to previous.workspace.items.mapNotNull { item ->
                (item.target as? ItemTarget.Widget)?.let { item.id to it.appWidgetId }
            }.toMap(),
        ))
    }

    private fun decode(text: String): RestoreRecord {
        val root = MiniJson.parse(text) as Map<*, *>
        require(root["version"] == 1L)
        val settingsJson = root["settings"] as String
        requireNotNull(LauncherSettingsCodec.decodeOrNull(settingsJson))
        val archive = (BackupCodec.decodeLocalCheckpoint(root["archive"] as String) as BackupDecodeResult.Success).backup
        val widgetIds = root["widgetIds"] as Map<*, *>
        val widgetItems = archive.workspace.items.filter { it.target is ItemTarget.Widget }
        require(widgetIds.keys == widgetItems.map { it.id }.toSet())
        val workspace = archive.workspace.copy(items = archive.workspace.items.map { item ->
            val widget = item.target as? ItemTarget.Widget ?: return@map item
            val id = widgetIds[item.id] as Long
            require(id in 0..Int.MAX_VALUE.toLong())
            item.copy(target = widget.copy(appWidgetId = id.toInt()))
        })
        return RestoreRecord(
            RestoreCheckpoint(settingsJson, workspace, archive.customizations, archive.drawerGroups),
            root["committed"] as Boolean,
            root["retainUndo"] as Boolean,
        )
    }

    companion object { private const val MAX_BYTES = 16 * 1024 * 1024 }
}
