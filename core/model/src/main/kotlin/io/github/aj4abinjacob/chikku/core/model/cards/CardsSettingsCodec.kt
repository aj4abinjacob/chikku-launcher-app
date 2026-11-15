package io.github.aj4abinjacob.chikku.core.model.cards

import io.github.aj4abinjacob.chikku.core.model.GestureAction
import io.github.aj4abinjacob.chikku.core.model.ComponentKey
import io.github.aj4abinjacob.chikku.core.model.settings.SettingsFormatException

/** Strict nested card decoding preserves damaged settings for the existing recovery flow. */
object CardsSettingsCodec {
    fun toMap(settings: CardsSettings): Map<String, Any?> = mapOf("items" to settings.coerced().cards.map { card ->
        linkedMapOf(
            "id" to card.id, "title" to card.title, "enabled" to card.enabled,
            "featured" to when (val featured = card.featured) {
                CardFeatured.None -> mapOf("kind" to "NONE")
                is CardFeatured.App -> mapOf("kind" to "APP", "action" to GestureAction.encode(GestureAction.LaunchApp(featured.key)))
                is CardFeatured.Shortcut -> mapOf("kind" to "SHORTCUT", "action" to GestureAction.encode(GestureAction.LaunchShortcut(featured.packageName, featured.shortcutId, featured.userSerial)))
                is CardFeatured.Widget -> mapOf("kind" to "WIDGET", "itemId" to featured.itemId)
            },
            "actions" to card.actions.map(GestureAction::encode),
            "content" to contentMap(card.content),
            "conditions" to with(card.conditions) { linkedMapOf(
                "weekdays" to weekdays.sorted(), "chargingOnly" to chargingOnly, "wifiOnly" to wifiOnly,
                "bluetoothConnectedOnly" to bluetoothConnectedOnly,
                "timeRange" to timeRange?.let { mapOf("startMinute" to it.startMinute, "endMinute" to it.endMinute) },
            ) },
        )
    })

    fun fromMap(value: Any?): CardsSettings {
        if (value == null) return CardsSettings()
        try {
            val root = value.objectValue()
            if (!root.containsKey("items")) return CardsSettings()
            val items = root["items"] ?: error("expected card items")
            val list = items.listValue()
            require(list.size <= CardsSettings.MAX_CARDS)
            return CardsSettings(list.map { item ->
                val card = item.objectValue()
                val featured = card.optional("featured")?.objectValue()?.let { f ->
                    when (f["kind"].stringValue()) {
                        "NONE" -> CardFeatured.None
                        "APP" -> (decodeAction(f["action"]) as? GestureAction.LaunchApp)?.let { CardFeatured.App(it.key) } ?: error("invalid featured app")
                        "SHORTCUT" -> (decodeAction(f["action"]) as? GestureAction.LaunchShortcut)?.let { CardFeatured.Shortcut(it.packageName, it.shortcutId, it.userSerial) } ?: error("invalid featured shortcut")
                        "WIDGET" -> CardFeatured.Widget(f["itemId"].stringValue())
                        else -> error("invalid featured kind")
                    }
                } ?: CardFeatured.None
                val conditions = card.optional("conditions")?.objectValue()?.let { c ->
                    val days = c.optional("weekdays")?.listValue()?.map { it.intValue().also { d -> require(d in 1..7) } } ?: emptyList()
                    require(days.size <= 7 && days.distinct().size == days.size)
                    CardConditions(days.toSet(), c["timeRange"]?.objectValue()?.let { time ->
                        CardTimeRange(time["startMinute"].intValue(), time["endMinute"].intValue())
                    }, c.bool("chargingOnly", false), c.bool("wifiOnly", false), c.bool("bluetoothConnectedOnly", false))
                } ?: CardConditions()
                AutomationCard(card["id"].stringValue(), card["title"].stringValue(), card.bool("enabled", true), featured,
                    card.optional("actions")?.listValue()?.map(::decodeAction) ?: emptyList(), conditions,
                    card.optional("content")?.objectValue()?.let(::decodeContent) ?: CardContent.Actions)
            }).coerced()
        } catch (failure: IllegalArgumentException) {
            throw SettingsFormatException("invalid card settings", failure)
        } catch (failure: IllegalStateException) {
            throw SettingsFormatException("invalid card settings", failure)
        }
    }

    private fun contentMap(content: CardContent): Map<String, Any?> = when (content) {
        CardContent.Actions -> mapOf("kind" to "ACTIONS")
        CardContent.Media -> mapOf("kind" to "MEDIA")
        is CardContent.Weather -> mapOf("kind" to "WEATHER", "onlineEnabled" to content.onlineEnabled,
            "unit" to content.unit.name, "location" to content.location?.let {
                mapOf("label" to it.label, "latitude" to it.latitude, "longitude" to it.longitude)
            })
        is CardContent.Widget -> mapOf("kind" to "EMBEDDED_WIDGET", "heightDp" to content.heightDp,
            "instanceId" to content.instanceId, "provider" to content.provider?.let {
                mapOf("packageName" to it.packageName, "className" to it.className, "userSerial" to it.userSerial)
            })
    }

    private fun decodeContent(value: Map<*, *>): CardContent = when (value["kind"].stringValue()) {
        "ACTIONS" -> CardContent.Actions
        "MEDIA" -> CardContent.Media
        "WEATHER" -> CardContent.Weather(value["location"]?.objectValue()?.let {
            WeatherLocation(it["label"].stringValue(), it["latitude"].doubleValue(), it["longitude"].doubleValue())
        }, value.optional("unit")?.stringValue()?.let(WeatherUnit::valueOf) ?: WeatherUnit.CELSIUS,
            value.bool("onlineEnabled", false))
        "EMBEDDED_WIDGET" -> CardContent.Widget(value["provider"]?.objectValue()?.let {
            ComponentKey(it["packageName"].stringValue(), it["className"].stringValue(), it["userSerial"].longValue())
        }, value.optional("heightDp")?.intValue() ?: 180,
            value["instanceId"].stringValue())
        else -> error("invalid card content")
    }.validated()

    private fun decodeAction(value: Any?): GestureAction {
        val encoded = value.stringValue()
        require(encoded.length <= 4096)
        val action = GestureAction.decode(encoded) ?: error("invalid card action")
        when (action) {
            is GestureAction.LaunchApp -> require(action.key.userSerial >= 0)
            is GestureAction.LaunchShortcut -> require(action.userSerial >= 0)
            else -> Unit
        }
        return action
    }
    private fun Any?.objectValue(): Map<*, *> = this as? Map<*, *> ?: error("expected object")
    private fun Any?.listValue(): List<*> = this as? List<*> ?: error("expected array")
    private fun Any?.stringValue(): String = this as? String ?: error("expected string")
    private fun Any?.intValue(): Int {
        require(this is Number && toDouble().isFinite() && toDouble() == toInt().toDouble())
        return toInt()
    }
    private fun Any?.doubleValue(): Double {
        require(this is Number && toDouble().isFinite())
        return toDouble()
    }
    private fun Any?.longValue(): Long {
        require(this is Number && toDouble().isFinite() && toDouble() == toLong().toDouble())
        return toLong()
    }
    private fun Map<*, *>.optional(key: String): Any? = if (!containsKey(key)) null else this[key] ?: error("unexpected null")
    private fun Map<*, *>.bool(key: String, default: Boolean): Boolean = if (!containsKey(key)) default else this[key] as? Boolean ?: error("expected boolean")
}
