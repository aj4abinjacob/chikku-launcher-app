package io.github.aj4abinjacob.chikku.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.applock.LockTarget
import io.github.aj4abinjacob.chikku.applock.RelockMode
import io.github.aj4abinjacob.chikku.applock.StoredAppLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SharedPreferencesAppLockStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs = context.getSharedPreferences(SharedPreferencesAppLockStore.PREFS_NAME, Context.MODE_PRIVATE)

    @Test
    fun roundTripsLocksAndRelockMode() = runTest {
        val value = StoredAppLock(setOf(LockTarget("example.bank", 0), LockTarget("example.bank", 10)), RelockMode.AFTER_FIVE_MINUTES)
        assertThat(SharedPreferencesAppLockStore(context, Dispatchers.Unconfined).write(value)).isTrue()
        assertThat(SharedPreferencesAppLockStore(context, Dispatchers.Unconfined).read()).isEqualTo(value)
    }

    @Test
    fun emptyStoreIsUnlockedAndAsksEveryTime() = runTest {
        assertThat(SharedPreferencesAppLockStore(context, Dispatchers.Unconfined).read()).isEqualTo(StoredAppLock())
    }

    @Test
    fun malformedEntriesAreSkippedAndUnknownModeIsStrictest() = runTest {
        prefs.edit()
            .putStringSet("locked", setOf("0:example.bank", "nonsense", ":x", "7:", "abc:example.notes"))
            .putString("relock", "FOREVER")
            .commit()
        val read = SharedPreferencesAppLockStore(context, Dispatchers.Unconfined).read()
        assertThat(read.locked).containsExactly(LockTarget("example.bank", 0))
        assertThat(read.relock).isEqualTo(RelockMode.EVERY_LAUNCH)
    }

    @Test
    fun storedApartFromLauncherSettings() {
        // Chikku backups export and restore launcher settings; locks must never travel with them.
        assertThat(SharedPreferencesAppLockStore.PREFS_NAME).isNotEqualTo(SharedPreferencesSettingsRepository.PREFS_NAME)
    }
}
