package io.github.aj4abinjacob.chikku.core.model.cards

import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.GestureAction

/** Local contextual cards. Conditions affect visibility; actions run only on an explicit tap. */
data class CardsSettings(val cards: List<AutomationCard> = emptyList()) {
    fun coerced(): CardsSettings {
        require(cards.size <= MAX_CARDS) { "too many cards" }
        require(cards.map { it.id }.distinct().size == cards.size) { "duplicate card identity" }
        return copy(cards = cards.map { it.coerced() })
    }

    companion object {
        const val MAX_CARDS = 24
        const val MAX_TITLE_LENGTH = 80
        const val ACTION_COUNT = 6
    }
}

data class AutomationCard(
    val id: String,
    val title: String,
    val enabled: Boolean = true,
    val featured: CardFeatured = CardFeatured.None,
    val actions: List<GestureAction> = List(CardsSettings.ACTION_COUNT) { GestureAction.None },
    val conditions: CardConditions = CardConditions(),
    val content: CardContent = CardContent.Actions,
) {
    fun coerced(): AutomationCard {
        require(id.isNotBlank() && id.length <= 80) { "invalid card identity" }
        require(title.isNotBlank() && title.length <= CardsSettings.MAX_TITLE_LENGTH && title.none { it.isISOControl() }) { "invalid card title" }
        require(actions.size <= CardsSettings.ACTION_COUNT) { "too many card actions" }
        require(id.none { it.isISOControl() }) { "invalid card identity" }
        fun validAction(action: GestureAction) {
            require(action in GestureAction.simpleActions || action is GestureAction.LaunchApp ||
                action is GestureAction.LaunchShortcut) { "action requires another surface" }
            require(GestureAction.encode(action).length <= 4096) { "invalid card action" }
            when (action) {
                is GestureAction.LaunchApp -> require(action.key.userSerial >= 0) { "invalid app profile" }
                is GestureAction.LaunchShortcut -> require(action.userSerial >= 0) { "invalid shortcut profile" }
                else -> Unit
            }
        }
        actions.forEach(::validAction)
        when (val item = featured) {
            is CardFeatured.App -> validAction(GestureAction.LaunchApp(item.key))
            is CardFeatured.Shortcut -> validAction(GestureAction.LaunchShortcut(item.packageName, item.shortcutId, item.userSerial))
            else -> Unit
        }
        if (featured is CardFeatured.Widget) require(featured.itemId.isNotBlank() && featured.itemId.length <= 200) { "invalid widget reference" }
        if (featured is CardFeatured.Shortcut) require(featured.packageName.isNotBlank() && featured.shortcutId.isNotEmpty() && featured.userSerial >= 0) { "invalid shortcut reference" }
        return copy(actions = actions.toList() + List(CardsSettings.ACTION_COUNT - actions.size) { GestureAction.None },
            conditions = conditions.coerced(), content = content.validated())
    }
}

/** Component/profile identity or a stable workspace placement ID; never a device-local widget ID. */
sealed interface CardFeatured {
    data object None : CardFeatured
    data class App(val key: AppKey) : CardFeatured
    data class Shortcut(val packageName: String, val shortcutId: String, val userSerial: Long) : CardFeatured
    data class Widget(val itemId: String) : CardFeatured
}

data class CardConditions(
    /** ISO weekday numbers 1 (Monday) to 7 (Sunday); empty means every day. */
    val weekdays: Set<Int> = emptySet(),
    val timeRange: CardTimeRange? = null,
    val chargingOnly: Boolean = false,
    val wifiOnly: Boolean = false,
    val bluetoothConnectedOnly: Boolean = false,
) {
    fun coerced(): CardConditions {
        require(weekdays.all { it in 1..7 }) { "invalid card weekday" }
        return copy(weekdays = weekdays.toSet(), timeRange = timeRange?.coerced())
    }
}

/** Local wall-clock minutes. Equal endpoints mean all day; overnight weekdays refer to the start. */
data class CardTimeRange(val startMinute: Int, val endMinute: Int) {
    fun coerced(): CardTimeRange = copy(startMinute = startMinute.coerceIn(0, 1439), endMinute = endMinute.coerceIn(0, 1439))
}

data class CardEnvironment(
    val isoWeekday: Int,
    val minuteOfDay: Int,
    val isCharging: Boolean? = null,
    /** The app's default network has Wi-Fi transport; no SSID, location or online request. */
    val isWifi: Boolean? = null,
    /** Connected public HEADSET/A2DP profiles; null without permission or known state. */
    val isBluetoothConnected: Boolean? = null,
)

object CardConditionEvaluator {
    fun matches(conditions: CardConditions, environment: CardEnvironment): Boolean {
        if (environment.isoWeekday !in 1..7 || environment.minuteOfDay !in 0..1439) return false
        if (conditions.chargingOnly && environment.isCharging != true) return false
        if (conditions.wifiOnly && environment.isWifi != true) return false
        if (conditions.bluetoothConnectedOnly && environment.isBluetoothConnected != true) return false
        val range = conditions.timeRange?.coerced()
        val minute = environment.minuteOfDay
        var day = environment.isoWeekday
        if (range != null && range.startMinute != range.endMinute) {
            if (range.startMinute < range.endMinute) {
                if (minute !in range.startMinute until range.endMinute) return false
            } else {
                if (minute < range.endMinute) day = if (day == 1) 7 else day - 1
                else if (minute < range.startMinute) return false
            }
        }
        return conditions.weekdays.isEmpty() || day in conditions.weekdays
    }
}
