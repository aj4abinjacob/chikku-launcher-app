package io.github.aj4abinjacob.chikku.settings.cards

import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.ComponentKey
import io.github.aj4abinjacob.chikku.core.model.cards.CardFeatured
import io.github.aj4abinjacob.chikku.core.model.cards.AutomationCard
import io.github.aj4abinjacob.chikku.core.model.cards.CardConditions
import io.github.aj4abinjacob.chikku.core.model.cards.CardContent
import io.github.aj4abinjacob.chikku.core.model.cards.CardTimeRange
import io.github.aj4abinjacob.chikku.core.model.cards.WeatherLocation
import org.junit.Test

class CardPreviewGapsTest {
    private val card = AutomationCard("sample", "Synthetic card")
    private val sample = CardPreviewSample()

    @Test fun enablingCardRestoresItsSamplePlacement() {
        assertThat(cardPreviewVisibility(card.copy(enabled = false), sample)).isEqualTo(CardPreviewVisibility.DISABLED)
        assertThat(cardPreviewVisibility(card.copy(enabled = true), sample)).isEqualTo(CardPreviewVisibility.VISIBLE)
    }

    @Test fun weekdayChangesImmediatelyWithCardAndSampleControls() {
        val monday = card.copy(conditions = CardConditions(weekdays = setOf(1)))
        assertThat(cardPreviewVisibility(monday, sample)).isEqualTo(CardPreviewVisibility.VISIBLE)
        assertThat(cardPreviewVisibility(monday, sample.copy(day = 2))).isEqualTo(CardPreviewVisibility.DAY)
        assertThat(cardPreviewVisibility(monday.copy(conditions = CardConditions(weekdays = setOf(2))),
            sample.copy(day = 2))).isEqualTo(CardPreviewVisibility.VISIBLE)
        assertThat(cardPreviewVisibility(card, sample.copy(day = 7))).isEqualTo(CardPreviewVisibility.VISIBLE)
    }

    @Test fun timeRangeUsesInclusiveStartAndExclusiveEnd() {
        val scheduled = card.copy(conditions = CardConditions(timeRange = CardTimeRange(540, 600)))
        assertThat(cardPreviewVisibility(scheduled, sample.copy(minute = 539))).isEqualTo(CardPreviewVisibility.TIME)
        assertThat(cardPreviewVisibility(scheduled, sample.copy(minute = 540))).isEqualTo(CardPreviewVisibility.VISIBLE)
        assertThat(cardPreviewVisibility(scheduled, sample.copy(minute = 599))).isEqualTo(CardPreviewVisibility.VISIBLE)
        assertThat(cardPreviewVisibility(scheduled, sample.copy(minute = 600))).isEqualTo(CardPreviewVisibility.TIME)
    }

    @Test fun overnightPreviewAttributesAfterMidnightToPreviousWeekday() {
        val mondayNight = card.copy(conditions = CardConditions(weekdays = setOf(1), timeRange = CardTimeRange(1320, 120)))
        assertThat(cardPreviewVisibility(mondayNight, sample.copy(day = 1, minute = 1380)))
            .isEqualTo(CardPreviewVisibility.VISIBLE)
        assertThat(cardPreviewVisibility(mondayNight, sample.copy(day = 2, minute = 60)))
            .isEqualTo(CardPreviewVisibility.VISIBLE)
        assertThat(cardPreviewVisibility(mondayNight, sample.copy(day = 1, minute = 60)))
            .isEqualTo(CardPreviewVisibility.DAY)
        assertThat(cardPreviewVisibility(mondayNight, sample.copy(day = 2, minute = 120)))
            .isEqualTo(CardPreviewVisibility.TIME)
        val sundayNight = mondayNight.copy(conditions = mondayNight.conditions.copy(weekdays = setOf(7)))
        assertThat(cardPreviewVisibility(sundayNight, sample.copy(day = 1, minute = 60)))
            .isEqualTo(CardPreviewVisibility.VISIBLE)
    }

    @Test fun powerWifiAndBluetoothEachFollowTheSampleWithoutAlteringCard() {
        val conditioned = card.copy(conditions = CardConditions(chargingOnly = true, wifiOnly = true,
            bluetoothConnectedOnly = true))
        val matching = sample.copy(charging = true, wifi = true, bluetooth = true)
        assertThat(cardPreviewVisibility(conditioned, matching)).isEqualTo(CardPreviewVisibility.VISIBLE)
        assertThat(cardPreviewVisibility(conditioned, matching.copy(charging = false)))
            .isEqualTo(CardPreviewVisibility.CHARGING)
        assertThat(cardPreviewVisibility(conditioned, matching.copy(wifi = false)))
            .isEqualTo(CardPreviewVisibility.WIFI)
        assertThat(cardPreviewVisibility(conditioned, matching.copy(bluetooth = false)))
            .isEqualTo(CardPreviewVisibility.BLUETOOTH)
        assertThat(cardPreviewVisibility(conditioned.copy(conditions = CardConditions()),
            sample.copy(charging = false, wifi = false, bluetooth = false))).isEqualTo(CardPreviewVisibility.VISIBLE)
        assertThat(conditioned.conditions).isEqualTo(CardConditions(chargingOnly = true, wifiOnly = true,
            bluetoothConnectedOnly = true))
    }

    @Test fun weatherSampleFollowsOnlineAndCityAvailability() {
        val weather = CardContent.Weather()
        assertThat(cardPreviewWeatherMessage(weather)).isEqualTo(R.string.card_weather_disabled)
        assertThat(cardPreviewWeatherMessage(weather.copy(onlineEnabled = true)))
            .isEqualTo(R.string.card_weather_choose_city)
        val selected = weather.copy(onlineEnabled = true, location = WeatherLocation("Sample", 0.0, 0.0))
        assertThat(cardPreviewWeatherMessage(selected)).isEqualTo(R.string.preview_card_weather_ready)
        assertThat(cardPreviewWeatherMessage(selected.copy(onlineEnabled = false))).isEqualTo(R.string.card_weather_disabled)
    }

    @Test fun featuredDescriptionFollowsSyntheticTargetKindWithoutUsingIdentity() {
        assertThat(cardPreviewFeaturedMessage(CardFeatured.None)).isEqualTo(R.string.preview_card_featured_empty)
        assertThat(cardPreviewFeaturedMessage(CardFeatured.App(AppKey("sample.app", "sample.Activity", 0))))
            .isEqualTo(R.string.preview_card_sample_app)
        assertThat(cardPreviewFeaturedMessage(CardFeatured.Shortcut("sample.app", "sample-shortcut", 0)))
            .isEqualTo(R.string.preview_card_sample_shortcut)
        assertThat(cardPreviewFeaturedMessage(CardFeatured.Widget("sample-widget")))
            .isEqualTo(R.string.preview_card_sample_widget)
    }

    @Test fun widgetDescriptionFollowsSelectionAndEmptyPlaceholder() {
        val widget = CardContent.Widget(instanceId = "sample")
        assertThat(cardPreviewWidgetMessage(widget)).isEqualTo(R.string.preview_card_widget_empty)
        val selected = widget.copy(provider = ComponentKey("sample.widget", "sample.Provider", 0))
        assertThat(cardPreviewWidgetMessage(selected)).isEqualTo(R.string.preview_card_sample_widget)
        assertThat(cardPreviewWidgetMessage(selected.copy(provider = null))).isEqualTo(R.string.preview_card_widget_empty)
    }
}
