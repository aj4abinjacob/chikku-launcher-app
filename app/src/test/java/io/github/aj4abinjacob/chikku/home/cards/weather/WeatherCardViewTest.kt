package io.github.aj4abinjacob.chikku.home.cards.weather

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.cards.CardContent
import io.github.aj4abinjacob.chikku.core.model.cards.WeatherLocation
import io.github.aj4abinjacob.chikku.core.model.cards.WeatherUnit
import io.github.aj4abinjacob.chikku.home.TestHomeApplication
import io.github.aj4abinjacob.chikku.platform.cards.weather.CardWeatherSource
import io.github.aj4abinjacob.chikku.platform.cards.weather.WeatherLocationsResult
import io.github.aj4abinjacob.chikku.platform.cards.weather.WeatherReading
import io.github.aj4abinjacob.chikku.platform.cards.weather.WeatherState
import io.github.aj4abinjacob.chikku.theme.SurfaceColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestHomeApplication::class)
class WeatherCardViewTest {
    private val config = CardContent.Weather(WeatherLocation("Synthetic city", 1.0, 2.0), onlineEnabled = true)
    private class PendingSource : CardWeatherSource {
        var calls = 0
        var cancellations = 0
        override suspend fun searchLocations(query: String) = WeatherLocationsResult.Found(emptyList())
        override suspend fun forecast(config: CardContent.Weather, forceRefresh: Boolean): WeatherState {
            calls++
            try { awaitCancellation() } finally { cancellations++ }
        }
    }

    @Test fun detachedAndStoppedCardsCancelRequestsAndResumeOnlyWhenVisibleAgain() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            val activity = controller.get()
            val source = PendingSource()
            val view = WeatherCardView(activity, config, source, SurfaceColors.DARK) {}
            runCurrent()
            assertThat(source.calls).isEqualTo(0)
            activity.setContentView(view)
            runCurrent()
            assertThat(source.calls).isEqualTo(1)
            controller.pause().stop()
            runCurrent()
            assertThat(source.cancellations).isEqualTo(1)
            view.refresh()
            runCurrent()
            assertThat(source.calls).isEqualTo(1)
            controller.start().resume()
            runCurrent()
            assertThat(source.calls).isEqualTo(2)
            (view.parent as ViewGroup).removeView(view)
            runCurrent()
            assertThat(source.cancellations).isEqualTo(2)
            view.refresh()
            runCurrent()
            assertThat(source.calls).isEqualTo(2)
        } finally {
            controller.pause().stop().destroy()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    @Test fun unconfiguredCardsOfferOnlySetUpAndNeverInvokeSource() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            val activity = controller.get()
            val source = PendingSource()
            listOf(
                config.copy(onlineEnabled = false) to R.string.card_weather_needs_online,
                config.copy(location = null) to R.string.card_weather_needs_city,
                config.copy(location = null, onlineEnabled = false) to R.string.card_weather_needs_city_and_online,
            ).forEach { (unconfigured, message) ->
                var setUps = 0
                val view = WeatherCardView(activity, unconfigured, source, SurfaceColors.LIGHT) { setUps++ }
                activity.setContentView(view)
                runCurrent()
                view.refresh()
                runCurrent()
                val texts = descendants(view).filterIsInstance<TextView>().filter { it !is Button && it.visibility == View.VISIBLE }
                    .map { it.text.toString() }
                assertThat(texts).contains(activity.getString(message))
                assertThat(texts).doesNotContain(activity.getString(R.string.card_weather_choose_city))
                val button = descendants(view).filterIsInstance<Button>().single()
                assertThat(button.text.toString()).isEqualTo(activity.getString(R.string.card_weather_setup))
                assertThat(button.isEnabled).isTrue()
                assertThat(button.contentDescription.toString()).isEqualTo(activity.getString(R.string.card_weather_setup_description))
                button.performClick()
                assertThat(setUps).isEqualTo(1)
                // Attribution stays while a chosen city's name or online data can be shown.
                val attributed = descendants(view).filterIsInstance<TextView>().any { it.text.contains("GeoNames") }
                assertThat(attributed).isEqualTo(unconfigured.location != null || unconfigured.onlineEnabled)
            }
            assertThat(source.calls).isEqualTo(0)
        } finally {
            controller.pause().stop().destroy()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    @Test fun readyReadingLabelsUnitsConditionsRangeAndLinkedAttributionAndRefresh() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            val activity = controller.get()
            val refreshes = mutableListOf<Boolean>()
            val source = object : CardWeatherSource {
                override suspend fun searchLocations(query: String) = WeatherLocationsResult.Found(emptyList())
                override suspend fun forecast(config: CardContent.Weather, forceRefresh: Boolean): WeatherState {
                    refreshes += forceRefresh
                    return WeatherState.Ready(WeatherReading(71.6, 78.0, 60.2, 95, 10L, WeatherUnit.FAHRENHEIT))
                }
            }
            val view = WeatherCardView(activity, config.copy(unit = WeatherUnit.FAHRENHEIT), source, SurfaceColors.LIGHT) {}
            activity.setContentView(view)
            runCurrent()
            val texts = descendants(view).filterIsInstance<TextView>().filter { it.visibility == View.VISIBLE }.map { it.text.toString() }
            assertThat(texts).contains("72°F")
            assertThat(texts).contains(activity.getString(R.string.card_weather_thunderstorm))
            assertThat(texts).contains(activity.getString(R.string.card_weather_range, 78, 60, "°F"))
            val attribution = descendants(view).filterIsInstance<TextView>().single { it.text.contains("GeoNames") }
            val spans = (attribution.text as android.text.Spanned).getSpans(0, attribution.text.length, android.text.style.URLSpan::class.java)
            assertThat(spans.map { it.url }).containsAtLeast("https://www.met.no/", "https://www.geonames.org/", "https://creativecommons.org/licenses/by/4.0/")
            view.refresh()
            runCurrent()
            assertThat(refreshes).containsExactly(false, true).inOrder()
        } finally {
            controller.pause().stop().destroy()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup) {
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) }
    } else emptyList()
}
