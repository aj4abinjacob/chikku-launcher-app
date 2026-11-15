package io.github.aj4abinjacob.chikku.settings.cards

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.cards.AutomationCard
import io.github.aj4abinjacob.chikku.core.model.cards.CardConditions
import io.github.aj4abinjacob.chikku.core.model.cards.CardContent
import io.github.aj4abinjacob.chikku.core.model.cards.CardTimeRange
import io.github.aj4abinjacob.chikku.home.cards.cardConditionSummary
import io.github.aj4abinjacob.chikku.settings.components.GestureActionRow
import io.github.aj4abinjacob.chikku.settings.components.SettingsBottomSheet
import io.github.aj4abinjacob.chikku.settings.components.SheetListPadding

/**
 * Starting points offered by "Add card". A template only pre-fills an unsaved editor draft: it
 * never picks real apps, shortcuts or widgets, never turns on online weather (ADR 0006) and never
 * asks for a permission itself. Nothing is stored until the user presses Save in the editor.
 */
internal enum class CardTemplate(
    @StringRes val label: Int,
    @StringRes val summary: Int,
    /** Pre-filled card name; null leaves the name empty for the user to type (blank card). */
    @StringRes val defaultTitle: Int?,
    @DrawableRes val icon: Int,
) {
    BLANK(R.string.cards_template_blank, R.string.cards_template_blank_summary, null, R.drawable.ic_action_add),
    MORNING(R.string.cards_template_morning, R.string.cards_template_morning_summary,
        R.string.cards_template_morning_title, R.drawable.ic_action_schedule),
    BLUETOOTH_AUDIO(R.string.cards_template_bluetooth, R.string.cards_template_bluetooth_summary,
        R.string.cards_template_bluetooth_title, R.drawable.ic_action_music),
    CHARGING(R.string.cards_template_charging, R.string.cards_template_charging_summary,
        R.string.cards_template_charging_title, R.drawable.ic_settings_cards),
    WEATHER(R.string.cards_template_weather, R.string.cards_template_weather_summary,
        R.string.cards_template_weather_title, R.drawable.ic_action_weather),
}

/**
 * The editor draft for [template]: a new card with identity [id] and name [title] (ignored for
 * [CardTemplate.BLANK]). Actions and the featured item stay empty; the Bluetooth template only
 * sets the condition, and the editor's own control explains and requests access when tapped.
 */
internal fun cardTemplateDraft(template: CardTemplate, id: String, title: String): AutomationCard {
    val base = AutomationCard(id, if (template == CardTemplate.BLANK) "" else title)
    return when (template) {
        CardTemplate.BLANK -> base
        CardTemplate.MORNING -> base.copy(content = CardContent.Actions, conditions = CardConditions(
            weekdays = setOf(1, 2, 3, 4, 5), timeRange = CardTimeRange(startMinute = 6 * 60, endMinute = 10 * 60)))
        CardTemplate.BLUETOOTH_AUDIO -> base.copy(content = CardContent.Media,
            conditions = CardConditions(bluetoothConnectedOnly = true))
        CardTemplate.CHARGING -> base.copy(content = CardContent.Actions, conditions = CardConditions(chargingOnly = true))
        CardTemplate.WEATHER -> base.copy(content = CardContent.Weather(location = null, onlineEnabled = false))
    }
}

/**
 * Bottom sheet for "Add card", in the shared settings sheet style ([SettingsBottomSheet] with
 * [GestureActionRow]s). Each row opens the card editor, so it is a navigating row (chevron, button
 * role) rather than a radio: the sheet slides away first, then [onPick] opens the editor. Swipe
 * down, back or a scrim tap call [onDismiss]. Rows describe only synthetic template content; no
 * installed-app data is read or shown.
 */
@Composable
internal fun CardTemplateDialog(onPick: (CardTemplate) -> Unit, onDismiss: () -> Unit) {
    var picked by remember { mutableStateOf<CardTemplate?>(null) }
    var finished by remember { mutableStateOf(false) }
    SettingsBottomSheet(
        title = stringResource(R.string.cards_template_title),
        onDismiss = {
            if (!finished) {
                finished = true
                picked?.let(onPick) ?: onDismiss()
            }
        },
    ) { close ->
        CardTemplateList { template ->
            if (picked == null) {
                picked = template
                close()
            }
        }
    }
}

/** The template rows of [CardTemplateDialog], without the sheet window (so tests can draw them). */
@Composable
internal fun ColumnScope.CardTemplateList(onPick: (CardTemplate) -> Unit) {
    val context = LocalContext.current
    LazyColumn(
        Modifier.weight(1f, fill = false).fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = SheetListPadding),
    ) {
        items(CardTemplate.entries.size) { index ->
            val template = CardTemplate.entries[index]
            val conditions = cardTemplateDraft(template, "template", "").conditions
            val summary = stringResource(template.summary)
            val supporting = cardConditionSummary(context, conditions)
                ?.let { stringResource(R.string.cards_template_summary_when, summary, it) } ?: summary
            GestureActionRow(
                text = stringResource(template.label),
                selected = false,
                icon = template.icon,
                supporting = supporting,
                navigates = true,
            ) { onPick(template) }
        }
    }
}
