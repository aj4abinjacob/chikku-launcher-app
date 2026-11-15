package io.github.aj4abinjacob.chikku.data

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import java.io.File

/**
 * Builds the production [ChikkuDatabase].
 *
 * Corruption policy: SQLite's default corruption handler deletes the database file, which would
 * silently reset the user's layout. The open helper is wrapped so that, on corruption, the damaged
 * file (and its -wal/-shm/-journal companions) is first copied to
 * `filesDir/recovery/workspace-corrupt-<timestamp>.db` and a marker is written. Only after the copy
 * is preserved is the default handler allowed to delete and recreate the database; if the copy
 * fails, the corrupt file is left in place and opening fails, which the repository surfaces as
 * `WorkspaceState.Failed` instead of an empty layout. The pending marker drives the UI notice
 * (live via the `create` listener, and [hasPreservedCorruptCopy] at process start); it is cleared
 * by [acknowledgeCorruptCopies] once the user has been told, and re-created by any later corruption.
 *
 * No destructive migration fallback is configured; a missing migration is a bug that must fail loudly.
 */
object ChikkuDatabaseFactory {
    private const val RECOVERY_DIR = "recovery"
    private const val MARKER_FILE = "workspace-corruption-pending"
    private const val COPY_PREFIX = "workspace-corrupt-"
    private val COMPANION_SUFFIXES = listOf("-wal", "-shm", "-journal")

    /** [onCorruptCopyPreserved] is invoked (on the database thread) after a damaged file was preserved. */
    fun create(
        context: Context,
        databaseName: String = ChikkuDatabase.NAME,
        onCorruptCopyPreserved: () -> Unit = {},
    ): ChikkuDatabase {
        val appContext = context.applicationContext
        return Room.databaseBuilder(appContext, ChikkuDatabase::class.java, databaseName)
            .openHelperFactory(CorruptionPreservingOpenHelperFactory(FrameworkSQLiteOpenHelperFactory()) { path ->
                preserveCorruptDatabase(appContext, path, System.currentTimeMillis())
                onCorruptCopyPreserved()
            })
            .addMigrations(*ChikkuMigrations.ALL)
            .addCallback(MetadataCallback)
            .build()
    }

    /** True when a corrupt database copy was preserved and the user has not yet been told. */
    fun hasPreservedCorruptCopy(context: Context): Boolean = File(recoveryDir(context), MARKER_FILE).exists()

    /** Preserved corrupt main database files, oldest first. Companion files share the same stem. */
    fun corruptCopies(context: Context): List<File> =
        recoveryDir(context).listFiles { file -> file.name.startsWith(COPY_PREFIX) && file.name.endsWith(".db") }
            ?.sortedBy { it.name }
            .orEmpty()

    /** Clears the pending notice after the user has been told. Preserved copies are kept. */
    fun acknowledgeCorruptCopies(context: Context) {
        File(recoveryDir(context), MARKER_FILE).delete()
    }

    /**
     * Copies the database at [databasePath] and its companions into the recovery directory and
     * writes the pending marker. Throws if the main file cannot be copied.
     */
    internal fun preserveCorruptDatabase(context: Context, databasePath: String, timestamp: Long) {
        val source = File(databasePath)
        val dir = recoveryDir(context).apply { mkdirs() }
        val stem = "$COPY_PREFIX$timestamp.db"
        if (source.exists()) source.copyTo(File(dir, stem), overwrite = true)
        COMPANION_SUFFIXES.forEach { suffix ->
            val companion = File(databasePath + suffix)
            if (companion.exists()) companion.copyTo(File(dir, stem + suffix), overwrite = true)
        }
        File(dir, MARKER_FILE).writeText(stem)
    }

    private fun recoveryDir(context: Context) = File(context.filesDir, RECOVERY_DIR)

    /** Records schema facts that are not visible from column types alone. */
    internal object MetadataCallback : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "INSERT OR IGNORE INTO workspace_meta(`key`, value) VALUES ('placementUnitsPerCell', '$UNITS_PER_CELL')",
            )
        }
    }
}

/**
 * Wraps [delegate] so the Room callback's `onCorruption` runs only after [preserve] has copied the
 * damaged file. If [preserve] throws, the original handler (which deletes the file) is skipped.
 */
internal class CorruptionPreservingOpenHelperFactory(
    private val delegate: SupportSQLiteOpenHelper.Factory,
    private val preserve: (databasePath: String) -> Unit,
) : SupportSQLiteOpenHelper.Factory {

    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper {
        val inner = configuration.callback
        val wrapped = object : SupportSQLiteOpenHelper.Callback(inner.version) {
            override fun onConfigure(db: SupportSQLiteDatabase) = inner.onConfigure(db)
            override fun onCreate(db: SupportSQLiteDatabase) = inner.onCreate(db)
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
                inner.onUpgrade(db, oldVersion, newVersion)
            override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
                inner.onDowngrade(db, oldVersion, newVersion)
            override fun onOpen(db: SupportSQLiteDatabase) = inner.onOpen(db)

            override fun onCorruption(db: SupportSQLiteDatabase) {
                val path = db.path
                if (path == null || path == ":memory:") {
                    inner.onCorruption(db)
                    return
                }
                val preserved = runCatching { preserve(path) }.isSuccess
                if (preserved) inner.onCorruption(db)
            }
        }
        return delegate.create(
            SupportSQLiteOpenHelper.Configuration.builder(configuration.context)
                .name(configuration.name)
                .callback(wrapped)
                .noBackupDirectory(configuration.useNoBackupDirectory)
                // Never let the framework delete the file on open errors; corruption goes through onCorruption.
                .allowDataLossOnRecovery(false)
                .build(),
        )
    }
}
