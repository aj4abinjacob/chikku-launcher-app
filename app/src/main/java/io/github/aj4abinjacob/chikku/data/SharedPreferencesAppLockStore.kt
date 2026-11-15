package io.github.aj4abinjacob.chikku.data

import android.annotation.SuppressLint
import android.content.Context
import io.github.aj4abinjacob.chikku.applock.AppLockStore
import io.github.aj4abinjacob.chikku.applock.LockTarget
import io.github.aj4abinjacob.chikku.applock.RelockMode
import io.github.aj4abinjacob.chikku.applock.StoredAppLock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * [AppLockStore] in its own SharedPreferences file, separate from launcher settings so Chikku's
 * backup export/restore never reads or replaces it. Platform backup is disabled for the whole app
 * (`data_extraction_rules.xml`). Entries are `<userSerial>:<packageName>`; malformed ones are
 * skipped, an unknown relock mode falls back to the strictest one.
 */
class SharedPreferencesAppLockStore(
    context: Context,
    private val io: CoroutineDispatcher,
) : AppLockStore {
    private val appContext = context.applicationContext
    private val prefs by lazy { appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    override suspend fun read(): StoredAppLock = withContext(io) {
        val locked = prefs.getStringSet(KEY_LOCKED, emptySet()).orEmpty().mapNotNullTo(HashSet(), ::decode)
        val relock = prefs.getString(KEY_RELOCK, null)
            ?.let { name -> RelockMode.entries.firstOrNull { it.name == name } ?: RelockMode.EVERY_LAUNCH }
            ?: RelockMode.EVERY_LAUNCH
        StoredAppLock(locked, relock)
    }

    // Plain commit(): the KTX edit() helper discards commit()'s result, which callers need.
    @SuppressLint("UseKtx")
    override suspend fun write(value: StoredAppLock): Boolean = withContext(io) {
        prefs.edit()
            .putStringSet(KEY_LOCKED, value.locked.mapTo(HashSet(), ::encode))
            .putString(KEY_RELOCK, value.relock.name)
            .commit()
    }

    internal companion object {
        const val PREFS_NAME = "chikku_app_lock"
        private const val KEY_LOCKED = "locked"
        private const val KEY_RELOCK = "relock"

        fun encode(target: LockTarget) = "${target.userSerial}:${target.packageName}"

        fun decode(entry: String): LockTarget? {
            val separator = entry.indexOf(':')
            if (separator <= 0 || separator == entry.lastIndex) return null
            val serial = entry.substring(0, separator).toLongOrNull() ?: return null
            return LockTarget(entry.substring(separator + 1), serial)
        }
    }
}
