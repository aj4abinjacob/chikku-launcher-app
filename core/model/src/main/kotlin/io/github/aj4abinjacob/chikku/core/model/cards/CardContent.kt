package io.github.aj4abinjacob.chikku.core.model.cards

import io.github.aj4abinjacob.chikku.core.model.ComponentKey
import java.util.UUID

/** Portable card content. Neither media metadata nor device-local widget IDs belong here. */
sealed interface CardContent {
    data object Actions : CardContent
    data class Weather(
        val location: WeatherLocation? = null,
        val unit: WeatherUnit = WeatherUnit.CELSIUS,
        val onlineEnabled: Boolean = false,
    ) : CardContent
    data object Media : CardContent
    data class Widget(
        val provider: ComponentKey? = null,
        val heightDp: Int = 180,
        /** Logical binding generation; renewed on restore, never an Android widget ID. */
        val instanceId: String = UUID.randomUUID().toString(),
    ) : CardContent
}

enum class WeatherUnit { CELSIUS, FAHRENHEIT }

data class WeatherLocation(val label: String, val latitude: Double, val longitude: Double) {
    fun validated(): WeatherLocation {
        require(label.isNotBlank() && label.length <= 160 && label.none { it.isISOControl() }) { "invalid weather location" }
        require(latitude.isFinite() && latitude in -90.0..90.0 && longitude.isFinite() && longitude in -180.0..180.0) { "invalid coordinates" }
        return this
    }
}

fun CardContent.validated(): CardContent = when (this) {
    CardContent.Actions, CardContent.Media -> this
    is CardContent.Weather -> copy(location = location?.validated())
    is CardContent.Widget -> {
        require(heightDp in 96..600) { "invalid widget height" }
        require(instanceId.isNotBlank() && instanceId.length <= 80 && instanceId.none { it.isISOControl() }) { "invalid widget binding identity" }
        provider?.let { require(it.packageName.isNotBlank() && it.className.isNotBlank() && it.userSerial >= 0 &&
            it.packageName.length <= 256 && it.className.length <= 512 && (it.packageName + it.className).none { c -> c.isISOControl() }) { "invalid widget provider" } }
        this
    }
}
