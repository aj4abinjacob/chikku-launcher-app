package io.github.aj4abinjacob.chikku.home.cards

import android.content.Context
import androidx.annotation.DrawableRes
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.GestureAction
import io.github.aj4abinjacob.chikku.core.model.cards.CardConditions
import io.github.aj4abinjacob.chikku.core.model.cards.CardContent
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

/**
 * Glyph for an action tile; app and shortcut actions show the real icon once it loads. Deliberately
 * differs from the picker table [io.github.aj4abinjacob.chikku.settings.components.gestureActionIcon]
 * for None (an empty "add" slot), OpenDrawer and the app/shortcut placeholders; keep the two in step.
 */
@DrawableRes
internal fun cardActionGlyph(action: GestureAction): Int = when (action) {
    GestureAction.None -> R.drawable.ic_action_add
    GestureAction.OpenDrawer -> R.drawable.ic_all_apps
    GestureAction.OpenSearch -> R.drawable.ic_search
    GestureAction.OpenCards -> R.drawable.ic_settings_cards
    GestureAction.ExpandNotifications -> R.drawable.ic_action_notifications
    GestureAction.ExpandQuickSettings -> R.drawable.ic_action_quick_settings
    GestureAction.LockScreen -> R.drawable.ic_settings_lock
    GestureAction.Recents -> R.drawable.ic_action_recents
    GestureAction.OpenLauncherSettings -> R.drawable.ic_menu_tune
    GestureAction.Assistant -> R.drawable.ic_action_assistant
    GestureAction.VoiceSearch -> R.drawable.ic_action_mic
    GestureAction.ToggleDock -> R.drawable.ic_settings_dock
    GestureAction.OpenFolder -> R.drawable.ic_settings_folder
    GestureAction.GoToDefaultPage -> R.drawable.ic_settings_home
    is GestureAction.LaunchApp, is GestureAction.LaunchShortcut -> R.drawable.ic_app_placeholder
}

/** Glyph standing for a card in lists when it has no featured app. */
@DrawableRes
internal fun cardContentGlyph(content: CardContent): Int = when (content) {
    CardContent.Actions -> R.drawable.ic_settings_cards
    is CardContent.Weather -> R.drawable.ic_action_weather
    CardContent.Media -> R.drawable.ic_action_music
    is CardContent.Widget -> R.drawable.ic_menu_widgets
}

/**
 * One line saying when a card shows, e.g. "Weekdays · 09:00–18:00 · Charging". Null when the card
 * has no conditions. Uses the phone's 12/24-hour preference; nothing here leaves the device.
 */
internal fun cardConditionSummary(context: Context, conditions: CardConditions): String? {
    val parts = mutableListOf<String>()
    val days = conditions.weekdays
    if (days.isNotEmpty() && days.size < 7) parts += when (days) {
        setOf(1, 2, 3, 4, 5) -> context.getString(R.string.cards_when_weekdays)
        setOf(6, 7) -> context.getString(R.string.cards_when_weekends)
        else -> cardDayRun(days, context.resources.configuration.locales[0])
    }
    conditions.timeRange?.let { range ->
        parts += if (range.startMinute == range.endMinute) context.getString(R.string.cards_when_all_day)
        else context.getString(R.string.cards_when_time, cardClock(context, range.startMinute), cardClock(context, range.endMinute))
    }
    if (conditions.chargingOnly) parts += context.getString(R.string.cards_when_charging)
    if (conditions.wifiOnly) parts += context.getString(R.string.cards_when_wifi)
    if (conditions.bluetoothConnectedOnly) parts += context.getString(R.string.cards_when_bluetooth)
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/** "Mon–Thu" for a consecutive run, otherwise "Mon, Wed, Fri". */
private fun cardDayRun(days: Set<Int>, locale: java.util.Locale): String {
    val sorted = days.sorted()
    fun name(day: Int) = DayOfWeek.of(day).getDisplayName(TextStyle.SHORT, locale)
    val consecutive = sorted.size >= 3 && sorted.zipWithNext().all { (a, b) -> b == a + 1 }
    return if (consecutive) "${name(sorted.first())}–${name(sorted.last())}" else sorted.joinToString(", ") { name(it) }
}

/** Minute of day in the phone's clock style. */
internal fun cardClock(context: Context, minute: Int): String {
    val pattern = if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
    return LocalTime.of(minute / 60, minute % 60)
        .format(DateTimeFormatter.ofPattern(pattern, context.resources.configuration.locales[0]))
}
