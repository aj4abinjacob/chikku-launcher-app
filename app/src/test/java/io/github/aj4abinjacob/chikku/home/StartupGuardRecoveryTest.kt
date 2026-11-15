package io.github.aj4abinjacob.chikku.home

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import java.lang.reflect.Proxy
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestHomeApplication::class)
class StartupGuardRecoveryTest {
    @Test fun overflowCannotDisableSafeMode() {
        fixture { prefs ->
            prefs.edit().putInt("unfinished_startups", Int.MAX_VALUE).commit()
            val guard = StartupGuard({ prefs }, Dispatchers.IO)
            assertThat(guard.recordStartup()).isTrue()
            assertThat(prefs.getInt("unfinished_startups", 0)).isEqualTo(StartupGuard.DEFAULT_THRESHOLD)
        }
    }

    @Test fun damagedGuardEntersSafeModeAndKeepsEvidenceUntilExplicitExit() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "guard-${UUID.randomUUID()}"
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        try {
            for (badCount in listOf<Any>("broken", -1)) {
                prefs.edit().clear().commit()
                if (badCount is String) prefs.edit().putString("unfinished_startups", badCount).commit()
                else prefs.edit().putInt("unfinished_startups", badCount as Int).commit()
                val before = prefs.all
                val guard = StartupGuard({ prefs }, Dispatchers.IO)
                assertThat(guard.recordStartup()).isTrue()
                assertThat(guard.isSafeMode()).isTrue()
                guard.onStartupStable()
                assertThat(prefs.all).isEqualTo(before)
                guard.leaveSafeMode()
                assertThat(guard.recordStartup()).isFalse()
            }
            prefs.edit().clear().putString("safe_mode", "broken").commit()
            assertThat(StartupGuard({ prefs }, Dispatchers.IO).recordStartup()).isTrue()
            assertThat(prefs.getString("safe_mode", null)).isEqualTo("broken")
        } finally { context.deleteSharedPreferences(name) }
    }

    @Test fun failedStartupCommitUsesSafeMode() {
        fixture { prefs ->
            val failing = object : SharedPreferences by prefs {
                override fun edit(): SharedPreferences.Editor {
                    val editor = prefs.edit()
                    lateinit var proxy: SharedPreferences.Editor
                    proxy = Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader,
                        arrayOf(SharedPreferences.Editor::class.java)) { _, method, args ->
                        if (method.name == "commit") false
                        else method.invoke(editor, *(args ?: emptyArray())).let { if (it === editor) proxy else it }
                    } as SharedPreferences.Editor
                    return proxy
                }
            }
            assertThat(StartupGuard({ failing }, Dispatchers.IO).recordStartup()).isTrue()
        }
    }

    private fun fixture(block: (SharedPreferences) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "guard-${UUID.randomUUID()}"
        try { block(context.getSharedPreferences(name, Context.MODE_PRIVATE)) }
        finally { context.deleteSharedPreferences(name) }
    }
}
