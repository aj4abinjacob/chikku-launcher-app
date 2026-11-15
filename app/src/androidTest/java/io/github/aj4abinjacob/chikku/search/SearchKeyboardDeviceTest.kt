package io.github.aj4abinjacob.chikku.search

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.view.KeyEvent
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.DeviceTestSupport
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.settings.HomeSearchBarPosition
import io.github.aj4abinjacob.chikku.core.model.settings.SearchPresentation
import io.github.aj4abinjacob.chikku.data.SharedPreferencesSettingsRepository
import io.github.aj4abinjacob.chikku.home.HomeActivity
import io.github.aj4abinjacob.chikku.home.homeDependencies
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeFalse
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies the actual IME and focused editor without tapping the opened search field. */
@RunWith(AndroidJUnit4::class)
class SearchKeyboardDeviceTest {
    private val device get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private var savedRawSettings: Map<String, String?>? = null

    @Before
    fun requireUnlockedScreen() {
        device.wakeUp()
        assumeFalse("Unlock the test device to exercise visible UI", DeviceTestSupport.targetContext
            .getSystemService(KeyguardManager::class.java).isKeyguardLocked)
        val prefs = DeviceTestSupport.targetContext.getSharedPreferences(
            SharedPreferencesSettingsRepository.PREFS_NAME, Context.MODE_PRIVATE,
        )
        savedRawSettings = listOf(
            SharedPreferencesSettingsRepository.KEY_SETTINGS,
            SharedPreferencesSettingsRepository.KEY_DAMAGED,
        ).associateWith { prefs.getString(it, null) }
        DeviceTestSupport.resetStartupGuard()
    }

    @After
    fun preserveRawSettingsPayload() {
        val saved = savedRawSettings ?: return
        DeviceTestSupport.finishAll(HomeActivity::class.java)
        DeviceTestSupport.finishAll(SearchActivity::class.java)
        val prefs = DeviceTestSupport.targetContext.getSharedPreferences(
            SharedPreferencesSettingsRepository.PREFS_NAME, Context.MODE_PRIVATE,
        )
        // Restoring the decoded model above can rewrite an older payload with newly added defaults.
        // Keep its original serialized data too, including unknown fields and damage evidence.
        val editor = prefs.edit()
        saved.forEach { (key, value) ->
            if (value == null) editor.remove(key) else editor.putString(key, value)
        }
        assertThat(editor.commit()).isTrue()
        assertThat(saved.all { (key, value) -> prefs.getString(key, null) == value }).isTrue()
        savedRawSettings = null
    }

    @Test
    fun bothHomeBarsOpenKeyboardInBothPresentationsAndOnReopen() = runBlocking {
        val context = DeviceTestSupport.targetContext
        val repository = context.homeDependencies().settingsRepository
        val original = repository.settings.first()
        try {
            repository.update { it.copy(onboarding = it.onboarding.copy(completed = true)) }
            ActivityScenario.launch(HomeActivity::class.java).use { scenario ->
                for (presentation in SearchPresentation.entries) {
                    for (position in listOf(HomeSearchBarPosition.TOP, HomeSearchBarPosition.BELOW_DOCK)) {
                        repository.update { it.copy(
                            home = it.home.copy(showSearchBar = true, searchBarPosition = position),
                            search = it.search.copy(presentation = presentation, onlineSuggestions = false),
                        ) }
                        val barId = if (position == HomeSearchBarPosition.TOP) R.id.home_search_bar_top else R.id.home_search_bar_bottom
                        assertThat(DeviceTestSupport.waitUntil {
                            var shown = false
                            scenario.onActivity { shown = it.findViewById<View>(barId).isShown }
                            shown
                        }).isTrue()
                        repeat(2) {
                            scenario.onActivity { home -> assertThat(home.findViewById<View>(barId).performClick()).isTrue() }
                            assertReadyToType(scenario)
                            device.pressKeyCode(KeyEvent.KEYCODE_A)
                            assertThat(device.wait(Until.hasObject(By.clazz("android.widget.EditText").text("a")), DeviceTestSupport.UI_TIMEOUT_MS)).isTrue()
                            // Query/results recomposition must not reopen a deliberately hidden IME.
                            scenario.onActivity { home ->
                                WindowCompat.getInsetsController(home.window, home.window.decorView).hide(WindowInsetsCompat.Type.ime())
                            }
                            assertImeVisible(scenario, false)
                            device.waitForIdle()
                            scenario.onActivity { home -> home.onBackPressedDispatcher.onBackPressed() }
                            assertThat(device.wait(Until.gone(By.clazz("android.widget.EditText")), DeviceTestSupport.UI_TIMEOUT_MS)).isTrue()
                        }
                    }
                }
            }
        } finally {
            DeviceTestSupport.finishAll(HomeActivity::class.java)
            repository.update { original }
            DeviceTestSupport.resetStartupGuard()
        }
    }

    @Test
    fun standaloneSearchOpensKeyboardAndPreservesQueryOnRecreation() = runBlocking {
        val context = DeviceTestSupport.targetContext
        val repository = context.homeDependencies().settingsRepository
        val original = repository.settings.first().search
        try {
            repository.update { it.copy(search = it.search.copy(onlineSuggestions = false)) }
            ActivityScenario.launch<SearchActivity>(SearchLauncher.intent(context, "initial")).use { scenario ->
                assertReadyToType(scenario)
                device.pressKeyCode(KeyEvent.KEYCODE_A)
                assertThat(device.wait(Until.hasObject(By.clazz("android.widget.EditText").text("initiala")), DeviceTestSupport.UI_TIMEOUT_MS)).isTrue()
                scenario.recreate()
                assertReadyToType(scenario)
                assertThat(device.hasObject(By.clazz("android.widget.EditText").text("initiala"))).isTrue()
            }
        } finally {
            DeviceTestSupport.finishAll(SearchActivity::class.java)
            repository.update { it.copy(search = original) }
        }
    }

    private fun <A : Activity> assertReadyToType(scenario: ActivityScenario<A>) {
        assertThat(device.wait(Until.hasObject(By.clazz("android.widget.EditText").focused(true)), DeviceTestSupport.UI_TIMEOUT_MS)).isTrue()
        assertImeVisible(scenario, true)
    }

    private fun <A : Activity> assertImeVisible(scenario: ActivityScenario<A>, visible: Boolean) {
        assertThat(DeviceTestSupport.waitUntil {
            var matches = false
            scenario.onActivity { activity ->
                matches = ViewCompat.getRootWindowInsets(activity.window.decorView)
                    ?.isVisible(WindowInsetsCompat.Type.ime()) == visible
            }
            matches
        }).isTrue()
    }
}
