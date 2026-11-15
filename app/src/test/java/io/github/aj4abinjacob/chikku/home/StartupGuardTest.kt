package io.github.aj4abinjacob.chikku.home

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestHomeApplication::class)
class StartupGuardTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dispatcher = UnconfinedTestDispatcher()
    private val prefs get() = context.getSharedPreferences("guard", Context.MODE_PRIVATE)

    /** A fresh guard per "process start", sharing the same file, like the real app. */
    private fun guard() = StartupGuard({ prefs }, dispatcher)

    @Before
    fun clear() {
        prefs.edit().clear().commit()
    }

    @Test
    fun belowThresholdIsNormalMode() = runTest(dispatcher) {
        assertThat(guard().onStartupBegin()).isFalse()
        assertThat(guard().onStartupBegin()).isFalse()
    }

    @Test
    fun thirdConsecutiveUnfinishedStartupEntersSafeMode() = runTest(dispatcher) {
        repeat(2) { guard().onStartupBegin() }
        assertThat(guard().onStartupBegin()).isTrue()
    }

    @Test
    fun stableStartupResetsTheCounter() = runTest(dispatcher) {
        repeat(2) { guard().onStartupBegin() }
        guard().onStartupStable()
        assertThat(guard().onStartupBegin()).isFalse()
        assertThat(guard().onStartupBegin()).isFalse()
        assertThat(guard().onStartupBegin()).isTrue()
    }

    @Test
    fun safeModeIsStickyUntilTheUserLeavesIt() = runTest(dispatcher) {
        repeat(3) { guard().onStartupBegin() }
        guard().onStartupStable() // a stable start in safe mode does not silently leave it
        assertThat(guard().onStartupBegin()).isTrue()

        guard().leaveSafeMode()
        assertThat(guard().onStartupBegin()).isFalse()
    }

    @Test
    fun counterIsPersistedSynchronously() = runTest(dispatcher) {
        guard().onStartupBegin()
        assertThat(prefs.getInt("unfinished_startups", -1)).isEqualTo(1)
    }

    @Test
    fun customThresholdIsHonoured() = runTest(dispatcher) {
        val strict = StartupGuard({ prefs }, dispatcher, threshold = 1)
        assertThat(strict.onStartupBegin()).isTrue()
    }
}
