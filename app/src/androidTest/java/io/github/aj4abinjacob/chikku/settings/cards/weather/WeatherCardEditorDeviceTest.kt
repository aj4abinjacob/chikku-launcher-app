package io.github.aj4abinjacob.chikku.settings.cards.weather

import android.app.KeyguardManager
import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.aj4abinjacob.chikku.DeviceTestSupport
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.cards.CardContent
import io.github.aj4abinjacob.chikku.core.model.cards.WeatherLocation
import io.github.aj4abinjacob.chikku.platform.cards.weather.CardWeatherSource
import io.github.aj4abinjacob.chikku.platform.cards.weather.WeatherLocationsResult
import io.github.aj4abinjacob.chikku.platform.cards.weather.WeatherState
import io.github.aj4abinjacob.chikku.settings.SettingsActivity
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.awaitCancellation
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic editor draft/source only: no real city query, preferences or workspace changes. */
@RunWith(AndroidJUnit4::class)
class WeatherCardEditorDeviceTest {
    private val context get() = DeviceTestSupport.targetContext
    private val device get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Before fun requireUnlockedScreen() {
        device.wakeUp()
        assumeFalse("Unlock the device to exercise visible UI", context.getSystemService(KeyguardManager::class.java).isKeyguardLocked)
    }

    @Test fun offlineCityChoiceWorksBeforeOnlineConsentAndDisablingKeepsIt() {
        val queries = ConcurrentLinkedQueue<String>()
        val draft = AtomicReference(CardContent.Weather())
        val source = object : CardWeatherSource {
            override suspend fun searchLocations(query: String): WeatherLocationsResult {
                queries += query
                return WeatherLocationsResult.Found(listOf(WeatherLocation("Synthetic city", 1.0, 2.0)))
            }
            override suspend fun forecast(config: CardContent.Weather, forceRefresh: Boolean) = WeatherState.Unavailable
        }
        try {
            ActivityScenario.launch<SettingsActivity>(Intent(context, SettingsActivity::class.java)).use { scenario ->
                scenario.onActivity { activity -> activity.setContent {
                    var config by remember { mutableStateOf(CardContent.Weather()) }
                    MaterialTheme { Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState())) {
                        WeatherCardEditorControls(config, { config = it; draft.set(it) }, source)
                    } }
                } }
                val query = device.wait(Until.findObject(By.clazz("android.widget.EditText")), DeviceTestSupport.UI_TIMEOUT_MS)
                assertThat(query).isNotNull()
                assertThat(query.isEnabled).isTrue()
                assertThat(queries).isEmpty()
                device.findObject(By.clazz("android.widget.EditText")).text = "Sample city"
                device.waitForIdle()
                assertThat(queries).isEmpty()
                device.findObject(By.text(context.getString(R.string.card_weather_search))).click()
                val choice = device.wait(Until.findObject(By.text("Synthetic city")), DeviceTestSupport.UI_TIMEOUT_MS)
                assertThat(choice).isNotNull()
                choice.click()
                assertThat(DeviceTestSupport.waitUntil { draft.get().location?.label == "Synthetic city" }).isTrue()
                assertThat(queries).containsExactly("Sample city")
                assertThat(draft.get().onlineEnabled).isFalse()
                device.findObject(By.text(context.getString(R.string.card_weather_online))).click()
                assertThat(DeviceTestSupport.waitUntil { draft.get().onlineEnabled }).isTrue()
                device.findObject(By.text(context.getString(R.string.card_weather_online))).click()
                assertThat(DeviceTestSupport.waitUntil { !draft.get().onlineEnabled }).isTrue()
                assertThat(device.findObject(By.clazz("android.widget.EditText")).isEnabled).isTrue()
                assertThat(draft.get().location?.label).isEqualTo("Synthetic city")
            }
        } finally { DeviceTestSupport.finishAll(SettingsActivity::class.java) }
    }

    private fun accessibilitySnapshot(): String {
        val root = InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow
            ?: return "<no active accessibility root>"
        val lines = mutableListOf<String>()
        fun visit(node: android.view.accessibility.AccessibilityNodeInfo, depth: Int) {
            if (lines.size >= 160) return
            val text = node.text?.toString().orEmpty()
            val description = node.contentDescription?.toString().orEmpty()
            lines += "${" ".repeat(depth)}${node.className} text=$text desc=$description clickable=${node.isClickable}"
            for (index in 0 until node.childCount) node.getChild(index)?.let { visit(it, depth + 1) }
        }
        visit(root, 0)
        return lines.joinToString(" | ")
    }

    @Test fun editorStopCancelsPendingCityRequest() {
        val entered = AtomicBoolean(false)
        val cancelled = AtomicBoolean(false)
        val source = object : CardWeatherSource {
            override suspend fun searchLocations(query: String): WeatherLocationsResult {
                entered.set(true)
                try { awaitCancellation() } finally { cancelled.set(true) }
            }
            override suspend fun forecast(config: CardContent.Weather, forceRefresh: Boolean) = WeatherState.Unavailable
        }
        try {
            ActivityScenario.launch<SettingsActivity>(Intent(context, SettingsActivity::class.java)).use { scenario ->
                scenario.onActivity { activity -> activity.setContent {
                    MaterialTheme { Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState())) {
                        WeatherCardEditorControls(CardContent.Weather(onlineEnabled = true), {}, source)
                    } }
                } }
                device.wait(Until.findObject(By.clazz("android.widget.EditText")), DeviceTestSupport.UI_TIMEOUT_MS).text = "Sample city"
                device.findObject(By.text(context.getString(R.string.card_weather_search))).click()
                assertThat(DeviceTestSupport.waitUntil { entered.get() }).isTrue()
                scenario.moveToState(Lifecycle.State.CREATED)
                assertThat(DeviceTestSupport.waitUntil { cancelled.get() }).isTrue()
            }
        } finally { DeviceTestSupport.finishAll(SettingsActivity::class.java) }
    }
}
