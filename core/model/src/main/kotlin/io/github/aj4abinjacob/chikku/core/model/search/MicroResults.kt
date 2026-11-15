package io.github.aj4abinjacob.chikku.core.model.search

import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URI
import java.util.Locale

/** Computed locally. External actions are suggestions; no URI is opened while typing. */
sealed interface MicroResult {
    data class Conversion(val display: String) : MicroResult
    data class Action(val kind: Kind, val value: String) : MicroResult
    enum class Kind { URL, MAP, DIAL, SMS, PACKAGE, FLIGHT, BITCOIN, ETHEREUM }
}

/** Bounded, stateless recognizers. No history, accounts, network or Android dependencies. */
object MicroResults {
    const val MAX_QUERY_LENGTH = 256
    private val conversion = Regex("^([+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+))\\s*([a-z° ]+?)(?:\\s+(?:to|in)\\s+([a-z° ]+))?$", RegexOption.IGNORE_CASE)
    private val flight = Regex("^(?:flight\\s+)?([A-Z0-9]{2})\\s*([0-9]{1,4}[A-Z]?)$", RegexOption.IGNORE_CASE)
    private val phone = Regex("^\\+?[0-9][0-9 ().-]*[0-9]$")
    private val street = Regex("^\\d+[a-z]?\\s+.+\\b(st|street|rd|road|ave|avenue|lane|ln|drive|dr|blvd|boulevard|way|court|ct)\\b.*$", RegexOption.IGNORE_CASE)
    private val ethereum = Regex("^0x[0-9a-fA-F]{40}$")
    private val bitcoin = Regex("^(?:[13][a-km-zA-HJ-NP-Z1-9]{25,34}|bc1[ac-hj-np-z02-9]{11,71})$")
    private val tracking = Regex("^[a-zA-Z0-9-]{8,40}$")

    fun recognize(query: String): List<MicroResult> {
        val q = query.trim()
        if (q.isEmpty() || q.length > MAX_QUERY_LENGTH || q.any { it.isISOControl() }) return emptyList()
        convert(q)?.let { return listOf(it) }
        if (safeWebUrl(q)) return listOf(MicroResult.Action(MicroResult.Kind.URL, q))
        if (ethereum.matches(q)) return listOf(MicroResult.Action(MicroResult.Kind.ETHEREUM, q))
        if (bitcoin.matches(q)) return listOf(MicroResult.Action(MicroResult.Kind.BITCOIN, q))
        val lower = q.lowercase(Locale.ROOT)
        if (lower.startsWith("maps ") || street.matches(q)) {
            val address = if (lower.startsWith("maps ")) q.drop(5).trim() else q
            if (address.isNotEmpty()) return listOf(MicroResult.Action(MicroResult.Kind.MAP, address))
        }
        if (lower.startsWith("track ")) {
            val number = q.drop(6).trim()
            if (tracking.matches(number)) return listOf(MicroResult.Action(MicroResult.Kind.PACKAGE, number))
        }
        if (Regex("^1Z[A-Z0-9]{16}$", RegexOption.IGNORE_CASE).matches(q) || Regex("^[A-Z]{2}[0-9]{9}[A-Z]{2}$").matches(q)) {
            return listOf(MicroResult.Action(MicroResult.Kind.PACKAGE, q))
        }
        flight.matchEntire(q)?.let { match ->
            // Two-character airline codes contain a letter; plain numbers remain phone candidates.
            if (match.groupValues[1].any { it.isLetter() } &&
                (lower.startsWith("flight ") || match.groupValues[1].first().isLetter())) {
                return listOf(MicroResult.Action(MicroResult.Kind.FLIGHT, (match.groupValues[1] + match.groupValues[2]).uppercase(Locale.ROOT)))
            }
        }
        if (phone.matches(q) && q.count { it.isDigit() } in 7..15) {
            val number = q.filter { it.isDigit() || it == '+' }
            return listOf(MicroResult.Action(MicroResult.Kind.DIAL, number), MicroResult.Action(MicroResult.Kind.SMS, number))
        }
        return emptyList()
    }

    /** Only ordinary HTTP(S) URLs, with a host and without embedded credentials. */
    fun safeWebUrl(value: String): Boolean = try {
        val uri = URI(value)
        value.length <= MAX_QUERY_LENGTH && value.none { it.isWhitespace() || it.isISOControl() } &&
            uri.scheme?.lowercase(Locale.ROOT) in setOf("http", "https") && !uri.host.isNullOrBlank() && uri.rawUserInfo == null
    } catch (_: Exception) {
        false
    }

    /** Fixed provider origins; identifiers cannot inject paths, fragments or query parameters. */
    fun webUrl(action: MicroResult.Action): String? = when (action.kind) {
        MicroResult.Kind.URL -> action.value.takeIf(::safeWebUrl)
        MicroResult.Kind.PACKAGE -> action.value.takeIf { tracking.matches(it) }?.let { "https://t.17track.net/en#nums=${WebSearchUrls.encode(it)}" }
        MicroResult.Kind.FLIGHT -> action.value.takeIf { flight.matches(it) }?.let { "https://www.flightradar24.com/data/flights/${it.lowercase(Locale.ROOT)}" }
        MicroResult.Kind.BITCOIN -> action.value.takeIf { bitcoin.matches(it) }?.let { "https://www.blockchain.com/explorer/addresses/btc/${WebSearchUrls.encode(it)}" }
        MicroResult.Kind.ETHEREUM -> action.value.takeIf { ethereum.matches(it) }?.let { "https://etherscan.io/address/$it" }
        else -> null
    }

    private data class UnitSpec(val symbol: String, val dimension: String, val factor: Double, val offset: Double = 0.0)
    private val units = buildMap<String, UnitSpec> {
        fun unit(symbol: String, dimension: String, factor: Double, offset: Double = 0.0, vararg names: String) {
            val spec = UnitSpec(symbol, dimension, factor, offset)
            (names.toList() + symbol).forEach { put(it.lowercase(Locale.ROOT), spec) }
        }
        unit("°C", "temperature", 1.0, 273.15, "c", "celsius")
        unit("°F", "temperature", 5.0 / 9.0, 459.67 * 5.0 / 9.0, "f", "fahrenheit")
        unit("K", "temperature", 1.0, 0.0, "kelvin")
        unit("m", "length", 1.0, 0.0, "meter", "meters", "metre", "metres")
        unit("km", "length", 1000.0, 0.0, "kilometer", "kilometers")
        unit("cm", "length", 0.01, 0.0, "centimeter", "centimeters")
        unit("mm", "length", 0.001)
        unit("ft", "length", 0.3048, 0.0, "foot", "feet")
        unit("in", "length", 0.0254, 0.0, "inch", "inches")
        unit("yd", "length", 0.9144, 0.0, "yard", "yards")
        unit("mi", "length", 1609.344, 0.0, "mile", "miles")
        unit("g", "mass", 1.0, 0.0, "gram", "grams")
        unit("kg", "mass", 1000.0, 0.0, "kilogram", "kilograms")
        unit("mg", "mass", 0.001)
        unit("oz", "mass", 28.349523125, 0.0, "ounce", "ounces")
        unit("lb", "mass", 453.59237, 0.0, "lbs", "pound", "pounds")
        // US customary cooking measures; do not silently mix imperial fluid ounces.
        unit("ml", "volume", 1.0, 0.0, "milliliter", "milliliters")
        unit("L", "volume", 1000.0, 0.0, "liter", "liters", "litre", "litres")
        unit("tsp", "volume", 4.92892159375, 0.0, "teaspoon", "teaspoons")
        unit("tbsp", "volume", 14.78676478125, 0.0, "tablespoon", "tablespoons")
        unit("cup", "volume", 236.5882365, 0.0, "cups")
        unit("fl oz", "volume", 29.5735295625, 0.0, "fluid ounce", "fluid ounces")
        unit("gal", "volume", 3785.411784, 0.0, "gallon", "gallons")
        unit("s", "time", 1.0, 0.0, "sec", "second", "seconds")
        unit("min", "time", 60.0, 0.0, "minute", "minutes")
        unit("h", "time", 3600.0, 0.0, "hour", "hours")
        unit("day", "time", 86400.0, 0.0, "days")
    }
    private val defaults = mapOf("°C" to "f", "°F" to "c", "K" to "c", "m" to "ft", "km" to "mi", "cm" to "in", "mm" to "in", "ft" to "m", "in" to "cm", "yd" to "m", "mi" to "km", "g" to "oz", "kg" to "lb", "mg" to "g", "oz" to "g", "lb" to "kg", "tsp" to "tbsp", "tbsp" to "tsp", "cup" to "ml", "fl oz" to "ml", "gal" to "l", "ml" to "fl oz", "L" to "gal", "s" to "min", "min" to "s", "h" to "min", "day" to "h")

    private fun convert(query: String): MicroResult.Conversion? {
        val match = conversion.matchEntire(query) ?: return null
        val value = match.groupValues[1].toDoubleOrNull()?.takeIf { it.isFinite() && kotlin.math.abs(it) <= 1e15 } ?: return null
        val from = units[match.groupValues[2].trim().lowercase(Locale.ROOT)] ?: return null
        val target = match.groupValues[3].trim().lowercase(Locale.ROOT).ifEmpty { defaults[from.symbol] ?: return null }
        val to = units[target] ?: return null
        if (from.dimension != to.dimension) return null
        val base = value * from.factor + from.offset
        if (from.dimension == "temperature" && base < -1e-9) return null
        val result = (base - to.offset) / to.factor
        if (!result.isFinite()) return null
        val rounded = BigDecimal.valueOf(result).setScale(8, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
        return MicroResult.Conversion("$rounded ${to.symbol}")
    }
}
