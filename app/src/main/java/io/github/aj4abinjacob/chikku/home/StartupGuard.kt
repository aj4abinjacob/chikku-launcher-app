package io.github.aj4abinjacob.chikku.home

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Crash-loop guard for HOME (docs/USER_PAIN_POINTS.md U02/U10).
 *
 * Each HOME start increments an "unfinished startups" counter, written synchronously with
 * `commit()` so it survives an immediate crash. Once the UI has drawn and stayed up for a while the
 * counter is cleared. After [threshold] consecutive unfinished startups HOME enters safe mode, which
 * is sticky until the user leaves it explicitly. The guard never deletes layout data.
 *
 * [recordStartup] runs synchronously on the main thread as the very first step of
 * `HomeActivity.onCreate`, before anything that could crash: a tiny `commit()` is the price of
 * counting early crashes. Everything else runs on [io].
 */
// commit() is deliberate: the write must be durable before a possible crash, and it runs on [io].
@SuppressLint("ApplySharedPref")
class StartupGuard(
    prefsFactory: () -> SharedPreferences,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val threshold: Int = DEFAULT_THRESHOLD,
) {
    private val prefs by lazy(prefsFactory)

    init { require(threshold > 0) { "Startup threshold must be positive" } }

    private fun unfinishedStarts(): Int? = try {
        prefs.getInt(KEY_UNFINISHED, 0).takeIf { it >= 0 }
    } catch (_: RuntimeException) { null }

    private fun savedSafeMode(): Boolean? = try {
        prefs.getBoolean(KEY_SAFE_MODE, false)
    } catch (_: RuntimeException) { null }

    /** Records a startup attempt durably, on the calling thread; returns true if HOME must start in safe mode. */
    fun recordStartup(): Boolean {
        // Bad optional flags cannot crash before HOME's recovery UI exists. Keep their evidence
        // until explicit exit, and saturate the counter so integer overflow cannot disable recovery.
        val previous = unfinishedStarts() ?: return true
        val alreadySafe = savedSafeMode() ?: return true
        val unfinished = if (previous >= threshold) threshold else previous + 1
        val safeMode = alreadySafe || unfinished >= threshold
        val committed = try {
            prefs.edit().putInt(KEY_UNFINISHED, unfinished).putBoolean(KEY_SAFE_MODE, safeMode).commit()
        } catch (_: RuntimeException) { false }
        return safeMode || !committed
    }

    /** [recordStartup] on [io], for callers that are not on the startup path. */
    suspend fun onStartupBegin(): Boolean = withContext(io) { recordStartup() }

    /** Reads the persisted safe-mode flag without counting a startup (e.g. a recreated activity). */
    suspend fun isSafeMode(): Boolean = withContext(io) {
        unfinishedStarts() == null || savedSafeMode() != false
    }

    /** Called once the first frame has been visible long enough; clears the crash counter only. */
    suspend fun onStartupStable() {
        withContext(io) {
            if (unfinishedStarts() != null && savedSafeMode() != null) {
                prefs.edit(commit = true) { putInt(KEY_UNFINISHED, 0) }
            }
        }
    }

    /** User chose to leave safe mode. */
    suspend fun leaveSafeMode() {
        withContext(io) { prefs.edit(commit = true) { putInt(KEY_UNFINISHED, 0).putBoolean(KEY_SAFE_MODE, false) } }
    }

    companion object {
        const val DEFAULT_THRESHOLD = 3

        /** Delay after the first drawn frame before a startup counts as successful. */
        const val STABLE_DELAY_MS = 5_000L

        private const val PREFS_NAME = "chikku_startup_guard"
        private const val KEY_UNFINISHED = "unfinished_startups"
        private const val KEY_SAFE_MODE = "safe_mode"

        fun create(context: Context): StartupGuard {
            val appContext = context.applicationContext
            return StartupGuard({ appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) })
        }
    }
}
