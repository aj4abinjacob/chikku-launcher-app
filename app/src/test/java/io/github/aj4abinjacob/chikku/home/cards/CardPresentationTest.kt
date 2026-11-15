package io.github.aj4abinjacob.chikku.home.cards

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.GestureAction
import io.github.aj4abinjacob.chikku.core.model.cards.CardConditions
import io.github.aj4abinjacob.chikku.core.model.cards.CardTimeRange
import io.github.aj4abinjacob.chikku.home.TestHomeApplication
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestHomeApplication::class, qualifiers = "en-rUS")
class CardPresentationTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Before fun use24HourClock() {
        Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, "24")
    }

    @Test fun unconditionalCardHasNoSummary() {
        assertThat(cardConditionSummary(context, CardConditions())).isNull()
        assertThat(cardConditionSummary(context, CardConditions(weekdays = (1..7).toSet()))).isNull()
    }

    @Test fun summaryNamesDayGroupsTimesAndDeviceStates() {
        val conditions = CardConditions(weekdays = setOf(1, 2, 3, 4, 5), timeRange = CardTimeRange(540, 1080),
            chargingOnly = true, wifiOnly = true, bluetoothConnectedOnly = true)
        assertThat(cardConditionSummary(context, conditions))
            .isEqualTo("Weekdays · 09:00–18:00 · Charging · On Wi‑Fi · Bluetooth audio")
        assertThat(cardConditionSummary(context, CardConditions(weekdays = setOf(6, 7)))).isEqualTo("Weekends")
        assertThat(cardConditionSummary(context, CardConditions(weekdays = setOf(2, 3, 4)))).isEqualTo("Tue–Thu")
        assertThat(cardConditionSummary(context, CardConditions(weekdays = setOf(1, 3, 5)))).isEqualTo("Mon, Wed, Fri")
        assertThat(cardConditionSummary(context, CardConditions(timeRange = CardTimeRange(600, 600)))).isEqualTo("All day")
    }

    @Test fun twelveHourPhonesGetTwelveHourTimes() {
        Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, "12")
        assertThat(cardClock(context, 13 * 60 + 5)).isEqualTo("1:05 PM")
    }

    @Test fun everyCardActionHasAGlyph() {
        (GestureAction.simpleActions + GestureAction.None).forEach { action ->
            assertThat(context.getDrawable(cardActionGlyph(action))).isNotNull()
        }
    }
}
