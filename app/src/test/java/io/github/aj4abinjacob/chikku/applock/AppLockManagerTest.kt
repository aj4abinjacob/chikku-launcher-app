package io.github.aj4abinjacob.chikku.applock

import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.LauncherApp
import io.github.aj4abinjacob.chikku.core.model.ProfileType
import io.github.aj4abinjacob.chikku.core.model.search.ShortcutLookup
import io.github.aj4abinjacob.chikku.core.model.search.ShortcutSource
import io.github.aj4abinjacob.chikku.home.InMemoryAppLockStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppLockManagerTest {
    private val bank = LockTarget("example.bank", 0)
    private val workBank = LockTarget("example.bank", 10)
    private val notes = LockTarget("example.notes", 0)
    private var now = 1_000L

    private fun TestScope.manager(store: InMemoryAppLockStore): AppLockManager =
        AppLockManager(store, backgroundScope) { now }.also { runCurrent() }

    private fun AppLockManager.admit(target: LockTarget) = admit(target.packageName, target.userSerial)

    @Test
    fun unlockedAppsOpenAndLockedAppsAreRefused() = runTest {
        val lock = manager(InMemoryAppLockStore(StoredAppLock(setOf(bank))))
        assertThat(lock.admit(notes)).isTrue()
        assertThat(lock.admit(bank)).isFalse()
        // The same package in another profile is a different app.
        assertThat(lock.admit(workBank)).isTrue()
    }

    @Test
    fun everyLaunchUnlockOpensTheAppOnce() = runTest {
        val lock = manager(InMemoryAppLockStore(StoredAppLock(setOf(bank), RelockMode.EVERY_LAUNCH)))
        lock.grant(bank)
        assertThat(lock.admit(bank)).isTrue()
        assertThat(lock.admit(bank)).isFalse()
    }

    @Test
    fun everyLaunchUnlockExpiresIfTheRetryNeverHappens() = runTest {
        val lock = manager(InMemoryAppLockStore(StoredAppLock(setOf(bank))))
        lock.grant(bank)
        now += 10_000
        assertThat(lock.admit(bank)).isFalse()
    }

    @Test
    fun timedUnlockLastsItsGraceAndNoLonger() = runTest {
        val lock = manager(InMemoryAppLockStore(StoredAppLock(setOf(bank), RelockMode.AFTER_ONE_MINUTE)))
        lock.grant(bank)
        assertThat(lock.admit(bank)).isTrue()
        now += 59_999
        assertThat(lock.admit(bank)).isTrue()
        now += 1
        assertThat(lock.admit(bank)).isFalse()
        // An unlock is for one app only.
        assertThat(lock.admit(LockTarget("example.other", 0))).isTrue()
    }

    @Test
    fun screenOffEndsEveryUnlock() = runTest {
        val lock = manager(InMemoryAppLockStore(StoredAppLock(setOf(bank, notes), RelockMode.SCREEN_OFF)))
        lock.grant(bank)
        lock.grant(notes)
        now += 24 * 60 * 60_000L
        assertThat(lock.admit(bank)).isTrue()
        lock.relockAll()
        assertThat(lock.admit(bank)).isFalse()
        assertThat(lock.admit(notes)).isFalse()
    }

    @Test
    fun changingLockSettingsEndsUnlocks() = runTest {
        val store = InMemoryAppLockStore(StoredAppLock(setOf(bank), RelockMode.SCREEN_OFF))
        val lock = manager(store)
        lock.grant(bank)
        assertThat(lock.setRelock(RelockMode.SCREEN_OFF)).isTrue()
        assertThat(lock.admit(bank)).isFalse()
    }

    @Test
    fun launchesBeforeTheLockListLoadsAreRefused() = runTest {
        val gate = CompletableDeferred<Unit>()
        val store = object : AppLockStore {
            override suspend fun read(): StoredAppLock {
                gate.await()
                return StoredAppLock()
            }
            override suspend fun write(value: StoredAppLock) = true
        }
        val lock = AppLockManager(store, backgroundScope) { now }
        runCurrent()
        assertThat(lock.admit(notes)).isFalse()
        gate.complete(Unit)
        runCurrent()
        assertThat(lock.admit(notes)).isTrue()
    }

    @Test
    fun unreadableStoreLocksEverythingAndIsNeverOverwritten() = runTest {
        val store = InMemoryAppLockStore(StoredAppLock(setOf(bank)), failReads = true)
        val lock = manager(store)
        assertThat(lock.state.value.readFailed).isTrue()
        assertThat(lock.admit(notes)).isFalse()
        assertThat(lock.setLocked(bank, false)).isFalse()
        assertThat(store.value.locked).containsExactly(bank)
    }

    @Test
    fun failedWriteChangesNothing() = runTest {
        val store = InMemoryAppLockStore(StoredAppLock(setOf(bank)), failWrites = true)
        val lock = manager(store)
        assertThat(lock.setLocked(bank, false)).isFalse()
        assertThat(lock.isLocked(bank.packageName, bank.userSerial)).isTrue()
    }

    @Test
    fun lockAndUnlockPersist() = runTest {
        val store = InMemoryAppLockStore()
        val lock = manager(store)
        assertThat(lock.setLocked(bank, true)).isTrue()
        assertThat(store.value.locked).containsExactly(bank)
        assertThat(lock.isLocked(AppKey("example.bank", "example.bank.Other", 0))).isTrue()
        assertThat(lock.setLocked(bank, false)).isTrue()
        assertThat(store.value.locked).isEmpty()
    }

    @Test
    fun searchShortcutsSkipLockedApps() = runTest {
        val lock = manager(InMemoryAppLockStore(StoredAppLock(setOf(bank))))
        val asked = mutableListOf<List<LauncherApp>>()
        val source = ShortcutSource { apps -> asked += apps; ShortcutLookup.Found(emptyList()) }
        val bankApp = LauncherApp(AppKey("example.bank", "example.bank.Main", 0), "Bank", ProfileType.PERSONAL)
        val notesApp = LauncherApp(AppKey("example.notes", "example.notes.Main", 0), "Notes", ProfileType.PERSONAL)
        source.excludingLocked(lock).shortcuts(listOf(bankApp, notesApp))
        assertThat(asked.single()).containsExactly(notesApp)
    }
}
