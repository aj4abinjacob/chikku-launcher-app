package io.github.aj4abinjacob.chikku.applock

import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.search.ShortcutSource
import io.github.aj4abinjacob.chikku.platform.AppLockGuard
import io.github.aj4abinjacob.chikku.platform.ShortcutMetadataPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A locked app: a package in one profile. Locking the package (not one launcher component) covers
 * every launcher activity and shortcut it has, and survives updates, reinstalls and custom labels.
 */
data class LockTarget(val packageName: String, val userSerial: Long) {
    constructor(key: AppKey) : this(key.packageName, key.userSerial)
}

/** How long a successful unlock lasts. Every grant also ends when the screen turns off. */
enum class RelockMode(val graceMillis: Long) {
    /** Ask on every launch; an unlock opens the app once. */
    EVERY_LAUNCH(0),
    AFTER_ONE_MINUTE(60_000),
    AFTER_FIVE_MINUTES(5 * 60_000),

    /** Stay unlocked until the screen turns off. */
    SCREEN_OFF(Long.MAX_VALUE),
}

/** What [AppLockStore] persists. Kept apart from launcher settings and backups (see [AppLockStore]). */
data class StoredAppLock(
    val locked: Set<LockTarget> = emptySet(),
    val relock: RelockMode = RelockMode.EVERY_LAUNCH,
)

/**
 * Persistence for the app lock. Deliberately not part of `LauncherSettings` or Chikku backups:
 * restoring an old backup must not be a way to remove locks.
 */
interface AppLockStore {
    /** Throws if the stored value cannot be read; the manager then fails closed. */
    suspend fun read(): StoredAppLock

    /** True once the value is on disk. */
    suspend fun write(value: StoredAppLock): Boolean
}

data class AppLockState(
    /** False until the store has been read; launches wait for it instead of guessing. */
    val loaded: Boolean = false,
    val locked: Set<LockTarget> = emptySet(),
    val relock: RelockMode = RelockMode.EVERY_LAUNCH,
    /** The store could not be read: every app counts as locked until it can. */
    val readFailed: Boolean = false,
) {
    /** Snapshot-safe metadata decision, observable directly by Compose without consuming a grant. */
    fun mayRead(packageName: String, userSerial: Long): Boolean =
        loaded && !readFailed && LockTarget(packageName, userSerial) !in locked
}

/**
 * Chikku's app lock policy: which apps are locked and which have been unlocked recently.
 *
 * [admit] runs on the main thread at launch time, so it only reads memory. Unlock grants live in
 * memory only, are timed with [clock] (elapsed realtime, so changing the wall clock cannot extend
 * them) and end on [relockAll] (screen off), process death or any change to the lock settings.
 * Authentication itself is the system's (see `AppUnlockPrompt`); this class only records success.
 */
class AppLockManager(
    private val store: AppLockStore,
    private val scope: CoroutineScope,
    private val clock: () -> Long,
) : AppLockGuard, ShortcutMetadataPolicy {
    private val mutableState = MutableStateFlow(AppLockState())
    val state: StateFlow<AppLockState> = mutableState.asStateFlow()

    private val writeMutex = Mutex()

    /** Target → elapsed-realtime expiry. Guarded by `this`. */
    private val grants = HashMap<LockTarget, Long>()

    init {
        scope.launch { load() }
    }

    private suspend fun load() {
        val stored = try {
            store.read()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        mutableState.value = if (stored == null) {
            AppLockState(loaded = true, readFailed = true)
        } else {
            AppLockState(loaded = true, locked = stored.locked, relock = stored.relock)
        }
    }

    /** Suspends until the stored locks are known. */
    suspend fun awaitLoaded(): AppLockState = state.first { it.loaded }

    /** Locked, or not yet known to be unlocked (store loading or unreadable). */
    fun isLocked(packageName: String, userSerial: Long): Boolean {
        val current = state.value
        return !current.mayRead(packageName, userSerial)
    }

    override fun mayRead(packageName: String, userSerial: Long): Boolean = !isLocked(packageName, userSerial)

    fun isLocked(key: AppKey): Boolean = isLocked(key.packageName, key.userSerial)

    @Synchronized
    override fun admit(packageName: String, userSerial: Long): Boolean {
        if (!state.value.loaded || state.value.readFailed) return false
        if (!isLocked(packageName, userSerial)) return true
        val target = LockTarget(packageName, userSerial)
        val expiry = grants[target] ?: return false
        if (clock() >= expiry) {
            grants.remove(target)
            return false
        }
        // "Every launch" grants open the app once.
        if (state.value.relock == RelockMode.EVERY_LAUNCH) grants.remove(target)
        return true
    }

    /** Records a successful system authentication for [target]. */
    @Synchronized
    fun grant(target: LockTarget) {
        val grace = state.value.relock.graceMillis
        val now = clock()
        grants[target] = when {
            grace == 0L -> now + RETRY_WINDOW_MILLIS
            grace == Long.MAX_VALUE -> Long.MAX_VALUE
            else -> now + grace
        }
    }

    /** Ends every unlock (screen off, or a lock setting changed). */
    @Synchronized
    fun relockAll() = grants.clear()

    /** Locks or unlocks [target]. False if the store could not save it (nothing changes then). */
    suspend fun setLocked(target: LockTarget, locked: Boolean): Boolean =
        change { it.copy(locked = if (locked) it.locked + target else it.locked - target) }

    suspend fun setRelock(mode: RelockMode): Boolean = change { it.copy(relock = mode) }

    private suspend fun change(transform: (StoredAppLock) -> StoredAppLock): Boolean = writeMutex.withLock {
        val current = awaitLoaded()
        // Never write over a value that could not be read: that would silently drop locks.
        if (current.readFailed) return false
        val next = transform(StoredAppLock(current.locked, current.relock))
        val saved = try {
            store.write(next)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
        if (saved) {
            relockAll()
            mutableState.update { it.copy(locked = next.locked, relock = next.relock) }
        }
        saved
    }

    private companion object {
        /** An "every launch" unlock must be used by the retried launch within this time. */
        const val RETRY_WINDOW_MILLIS = 10_000L
    }
}

/** Search shortcuts without locked apps' shortcuts, whose labels can reveal their content. */
fun ShortcutSource.excludingLocked(lock: AppLockManager): ShortcutSource =
    ShortcutSource { apps -> shortcuts(apps.filterNot { lock.isLocked(it.key) }) }
