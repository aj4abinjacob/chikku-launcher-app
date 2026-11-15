package io.github.aj4abinjacob.chikku.settings.cards

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.cards.AutomationCard
import io.github.aj4abinjacob.chikku.core.model.cards.CardConditionEvaluator
import io.github.aj4abinjacob.chikku.core.model.cards.CardEnvironment
import io.github.aj4abinjacob.chikku.home.cards.cardClock
import io.github.aj4abinjacob.chikku.settings.components.SettingsInfoText
import io.github.aj4abinjacob.chikku.settings.components.SettingsNavigationRow
import io.github.aj4abinjacob.chikku.settings.components.SettingsSwitchRow
import java.time.DayOfWeek
import java.time.format.TextStyle

/** Temporary examples, independent of the phone clock, networks, permissions and saved card. */
internal data class CardPreviewSample(
    val day: Int = 1,
    val minute: Int = 540,
    val charging: Boolean = false,
    val wifi: Boolean = true,
    val bluetooth: Boolean = false,
) {
    fun environment() = CardEnvironment(day, minute, charging, wifi, bluetooth)
}

internal enum class CardPreviewVisibility(@StringRes val message: Int) {
    VISIBLE(R.string.preview_card_visible), DISABLED(R.string.preview_card_hidden_disabled),
    CHARGING(R.string.preview_card_hidden_charging), WIFI(R.string.preview_card_hidden_wifi),
    BLUETOOTH(R.string.preview_card_hidden_bluetooth), TIME(R.string.preview_card_hidden_time),
    DAY(R.string.preview_card_hidden_day);
}

/** Reuses Home's evaluator, including overnight ranges whose weekday belongs to the start day. */
internal fun cardPreviewVisibility(card: AutomationCard, sample: CardPreviewSample): CardPreviewVisibility {
    if (!card.enabled) return CardPreviewVisibility.DISABLED
    val conditions = card.conditions
    val environment = sample.environment()
    if (CardConditionEvaluator.matches(conditions, environment)) return CardPreviewVisibility.VISIBLE
    return when {
        conditions.chargingOnly && !sample.charging -> CardPreviewVisibility.CHARGING
        conditions.wifiOnly && !sample.wifi -> CardPreviewVisibility.WIFI
        conditions.bluetoothConnectedOnly && !sample.bluetooth -> CardPreviewVisibility.BLUETOOTH
        !CardConditionEvaluator.matches(conditions.copy(weekdays = emptySet(), chargingOnly = false,
            wifiOnly = false, bluetoothConnectedOnly = false), environment) -> CardPreviewVisibility.TIME
        else -> CardPreviewVisibility.DAY
    }
}

@Composable
internal fun CardConditionPreviewControls(
    sample: CardPreviewSample,
    onChange: (CardPreviewSample) -> Unit,
    onChooseTime: () -> Unit,
) {
    val locale = LocalResources.current.configuration.locales[0]
    val context = LocalContext.current
    var dayMenu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        SettingsInfoText(stringResource(R.string.preview_card_simulation_summary))
        Column {
            SettingsNavigationRow(stringResource(R.string.preview_card_sample_day), { dayMenu = true },
                summary = DayOfWeek.of(sample.day).getDisplayName(TextStyle.FULL, locale))
            DropdownMenu(expanded = dayMenu, onDismissRequest = { dayMenu = false }) {
                (1..7).forEach { day ->
                    DropdownMenuItem(text = { Text(DayOfWeek.of(day).getDisplayName(TextStyle.FULL, locale)) },
                        onClick = { onChange(sample.copy(day = day)); dayMenu = false })
                }
            }
        }
        SettingsNavigationRow(stringResource(R.string.preview_card_sample_time), onChooseTime,
            summary = cardClock(context, sample.minute))
        SettingsSwitchRow(stringResource(R.string.preview_card_sample_charging), sample.charging,
            { onChange(sample.copy(charging = it)) })
        SettingsSwitchRow(stringResource(R.string.preview_card_sample_wifi), sample.wifi,
            { onChange(sample.copy(wifi = it)) })
        SettingsSwitchRow(stringResource(R.string.preview_card_sample_bluetooth), sample.bluetooth,
            { onChange(sample.copy(bluetooth = it)) })
    }
}
