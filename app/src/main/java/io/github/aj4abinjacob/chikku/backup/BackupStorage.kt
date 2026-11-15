package io.github.aj4abinjacob.chikku.backup

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import io.github.aj4abinjacob.chikku.core.model.backup.BackupCodec
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Outcome of reading a backup file. */
sealed interface BackupReadResult {
    data class Text(val text: String) : BackupReadResult

    /** Larger than [BackupCodec.MAX_BYTES]; nothing beyond the limit was read. */
    data object TooLarge : BackupReadResult

    data object Failed : BackupReadResult
}

/**
 * File I/O for backups. Everything runs on the injected IO dispatcher; nothing goes to the network.
 * Writes and reads go through the Storage Access Framework (user-picked documents); sharing writes
 * a copy to the app cache, exposed read-only through the non-exported [FileProvider].
 */
interface BackupStorage {
    suspend fun write(uri: Uri, text: String): Boolean

    suspend fun read(uri: Uri): BackupReadResult

    /** Writes [text] to a cache file named [fileName] and returns a content URI to share, or null. */
    suspend fun shareableCopy(fileName: String, text: String): Uri?
}

class ContentResolverBackupStorage(
    context: Context,
    private val io: CoroutineDispatcher,
) : BackupStorage {
    private val appContext = context.applicationContext

    override suspend fun write(uri: Uri, text: String): Boolean = withContext(io) {
        try {
            // "wt" truncates an existing document instead of leaving stale bytes at the end.
            val stream = appContext.contentResolver.openOutputStream(uri, "wt") ?: return@withContext false
            stream.bufferedWriter(Charsets.UTF_8).use { it.write(text) }
            true
        } catch (_: IOException) {
            false
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    override suspend fun read(uri: Uri): BackupReadResult = withContext(io) {
        try {
            val stream = appContext.contentResolver.openInputStream(uri) ?: return@withContext BackupReadResult.Failed
            stream.bufferedReader(Charsets.UTF_8).use { reader ->
                val buffer = CharArray(BUFFER_CHARS)
                val out = StringBuilder()
                while (true) {
                    val n = reader.read(buffer)
                    if (n < 0) break
                    out.append(buffer, 0, n)
                    if (out.length > BackupCodec.MAX_BYTES) return@withContext BackupReadResult.TooLarge
                }
                BackupReadResult.Text(out.toString())
            }
        } catch (_: IOException) {
            BackupReadResult.Failed
        } catch (_: SecurityException) {
            BackupReadResult.Failed
        } catch (_: IllegalArgumentException) {
            BackupReadResult.Failed
        }
    }

    override suspend fun shareableCopy(fileName: String, text: String): Uri? = withContext(io) {
        try {
            val dir = File(appContext.cacheDir, SHARE_DIR).apply { mkdirs() }
            // Keep only the newest shared copy in the cache.
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, fileName)
            file.writeText(text, Charsets.UTF_8)
            FileProvider.getUriForFile(appContext, authority(appContext), file)
        } catch (_: IOException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    companion object {
        /** Must match `res/xml/backup_file_paths.xml`. */
        const val SHARE_DIR = "backups"
        private const val BUFFER_CHARS = 16 * 1024

        /** Must match the provider's authorities in the manifest. */
        fun authority(context: Context): String = "${context.packageName}.backupfiles"
    }
}

object BackupFileNames {
    const val MIME_TYPE = "application/json"

    /** For opening a backup: some file managers label JSON as text or binary; the content is validated after reading. */
    val OPEN_MIME_TYPES: Array<String> get() = arrayOf(MIME_TYPE, "text/plain", "application/octet-stream")

    /** `chikku-backup-YYYYMMDD.json`. */
    fun defaultName(date: LocalDate = LocalDate.now()): String =
        "chikku-backup-${date.format(DateTimeFormatter.BASIC_ISO_DATE)}.json"
}
