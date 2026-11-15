package io.github.aj4abinjacob.chikku.settings

import android.content.Intent
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.home.FakeHomeDependencies
import io.github.aj4abinjacob.chikku.home.FakeHomeRoleController
import io.github.aj4abinjacob.chikku.home.FakeSettingsRepository
import io.github.aj4abinjacob.chikku.home.TestHomeApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestHomeApplication::class)
class SettingsTest {

    @Test
    fun viewModelReportsRoleStatusWithoutChangingIt() {
        val dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
        try {
            val role = FakeHomeRoleController(isDefault = false)
            val vm = SettingsViewModel(role, FakeSettingsRepository(), CoroutineScope(dispatcher), dispatcher)
            assertThat(vm.isDefaultHome.value).isNull()
            vm.refresh()
            assertThat(vm.isDefaultHome.value).isFalse()
            role.isDefault = true
            vm.refresh()
            assertThat(vm.isDefaultHome.value).isTrue()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun viewModelExposesAndUpdatesSettings() {
        val dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
        try {
            val repo = FakeSettingsRepository()
            val vm = SettingsViewModel(FakeHomeRoleController(), repo, CoroutineScope(dispatcher), dispatcher)
            assertThat(vm.settings.value).isEqualTo(LauncherSettings())
            vm.update { it.copy(home = it.home.copy(layoutLocked = true)) }
            assertThat(repo.state.value.home.layoutLocked).isTrue()
            assertThat(vm.settings.value?.home?.layoutLocked).isTrue()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun everySectionOpensFromDeepLinkWithoutCrashing() {
        val deps = FakeHomeDependencies()
        deps.settingsRepository.state.value = LauncherSettings().let { it.copy(labs = it.labs.copy(unlocked = true)) }
        ApplicationProvider.getApplicationContext<TestHomeApplication>().homeDependencies = deps
        SettingsSection.entries.forEach { section ->
            val intent = Intent(ApplicationProvider.getApplicationContext(), SettingsActivity::class.java)
                .putExtra(SettingsActivity.EXTRA_SECTION, section.name)
            ActivityScenario.launch<SettingsActivity>(intent).use { scenario ->
                scenario.onActivity { activity ->
                    assertThat(activity.window.decorView.visibility).isEqualTo(View.VISIBLE)
                }
            }
        }
    }

    @Test
    fun settingsActivityLaunchesWithFakes() {
        ApplicationProvider.getApplicationContext<TestHomeApplication>().homeDependencies = FakeHomeDependencies()
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertThat(activity.window.decorView.visibility).isEqualTo(View.VISIBLE)
            }
        }
    }
}
