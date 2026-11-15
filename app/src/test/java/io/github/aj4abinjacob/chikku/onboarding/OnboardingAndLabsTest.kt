package io.github.aj4abinjacob.chikku.onboarding

import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.backup.ContentResolverBackupStorage
import io.github.aj4abinjacob.chikku.backup.BackupFileNames
import io.github.aj4abinjacob.chikku.backup.BackupReadResult
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.home.FakeHomeDependencies
import io.github.aj4abinjacob.chikku.home.HomeActivity
import io.github.aj4abinjacob.chikku.home.TestHomeApplication
import io.github.aj4abinjacob.chikku.labs.LabsTapCounter
import io.github.aj4abinjacob.chikku.settings.SettingsActivity
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestHomeApplication::class)
class OnboardingAndLabsTest {
    private val application = ApplicationProvider.getApplicationContext<TestHomeApplication>()
    private lateinit var deps: FakeHomeDependencies

    @Before
    fun setUp() {
        application.getSharedPreferences("chikku_startup_guard", 0).edit().clear().commit()
        deps = FakeHomeDependencies()
        application.homeDependencies = deps
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun completed(done: Boolean) = LauncherSettings().let { it.copy(onboarding = it.onboarding.copy(completed = done)) }

    @Test
    fun homeOpensOnboardingOnlyWhenNotCompleted() {
        deps.settingsRepository.state.value = completed(false)
        ActivityScenario.launch(HomeActivity::class.java).use { scenario ->
            idle()
            scenario.onActivity { activity ->
                val started = shadowOf(activity).nextStartedActivity
                assertThat(started?.component?.className).isEqualTo(OnboardingActivity::class.java.name)
            }
        }
        deps.settingsRepository.state.value = completed(true)
        ActivityScenario.launch(HomeActivity::class.java).use { scenario ->
            idle()
            scenario.onActivity { activity -> assertThat(shadowOf(activity).nextStartedActivity).isNull() }
        }
    }

    @Test
    fun backOnFirstStepSkipsAndMarksOnboardingComplete() {
        deps.settingsRepository.state.value = completed(false)
        ActivityScenario.launch<OnboardingActivity>(Intent(application, OnboardingActivity::class.java)).use { scenario ->
            idle()
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            idle()
            assertThat(deps.settingsRepository.state.value.onboarding.completed).isTrue()
        }
    }

    @Test
    fun sevenVersionTapsInSettingsToggleLabs() {
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            idle()
            scenario.onActivity { activity -> repeat(LabsTapCounter.TAPS_TO_TOGGLE - 1) { activity.labsTaps.onTap() } }
            idle()
            assertThat(deps.settingsRepository.state.value.labs.unlocked).isFalse()

            scenario.onActivity { activity -> activity.labsTaps.onTap() }
            idle()
            assertThat(deps.settingsRepository.state.value.labs.unlocked).isTrue()

            scenario.onActivity { activity -> repeat(LabsTapCounter.TAPS_TO_TOGGLE) { activity.labsTaps.onTap() } }
            idle()
            assertThat(deps.settingsRepository.state.value.labs.unlocked).isFalse()
        }
    }

    @Test
    fun aPauseBetweenVersionTapsStartsTheCountOver() {
        var now = 0L
        var toggles = 0
        val counter = LabsTapCounter(onToggle = { toggles++ }, clock = { now })
        repeat(LabsTapCounter.TAPS_TO_TOGGLE - 1) {
            counter.onTap()
            now += LabsTapCounter.MAX_GAP_MILLIS
        }
        now += 1
        counter.onTap()
        assertThat(toggles).isEqualTo(0)

        repeat(LabsTapCounter.TAPS_TO_TOGGLE - 1) { counter.onTap() }
        assertThat(toggles).isEqualTo(1)
    }

    @Test
    fun shareCopyIsServedByFileProviderAndReadsBack() = runTest {
        val storage = ContentResolverBackupStorage(application, Dispatchers.Unconfined)
        val name = BackupFileNames.defaultName(LocalDate.of(2026, 10, 10))
        assertThat(name).isEqualTo("chikku-backup-20261010.json")
        val uri = storage.shareableCopy(name, """{"x":1}""")!!
        assertThat(uri.authority).isEqualTo(ContentResolverBackupStorage.authority(application))
        assertThat(storage.read(uri)).isEqualTo(BackupReadResult.Text("""{"x":1}"""))
    }
}
