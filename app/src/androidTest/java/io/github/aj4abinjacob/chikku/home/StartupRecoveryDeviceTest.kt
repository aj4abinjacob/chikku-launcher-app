package io.github.aj4abinjacob.chikku.home

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/** Native, separate preference files only; never changes HOME or the production startup guard. */
@RunWith(AndroidJUnit4::class)
class StartupRecoveryDeviceTest {
    @Test fun corruptFlagsAndOverflowEnterRecoveryWithoutLosingDamagedEvidence() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "startup-recovery-${UUID.randomUUID()}"
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        try {
            prefs.edit().putString("unfinished_startups", "broken").commit()
            val guard = StartupGuard({ prefs }, Dispatchers.IO)
            assertThat(guard.recordStartup()).isTrue()
            guard.onStartupStable()
            assertThat(prefs.getString("unfinished_startups", null)).isEqualTo("broken")
            assertThat(guard.isSafeMode()).isTrue()
            guard.leaveSafeMode()
            assertThat(StartupGuard({ prefs }, Dispatchers.IO).recordStartup()).isFalse()
            prefs.edit().putInt("unfinished_startups", Int.MAX_VALUE).commit()
            assertThat(StartupGuard({ prefs }, Dispatchers.IO).recordStartup()).isTrue()
            assertThat(prefs.getInt("unfinished_startups", 0)).isEqualTo(StartupGuard.DEFAULT_THRESHOLD)
        } finally { context.deleteSharedPreferences(name) }
    }
}
