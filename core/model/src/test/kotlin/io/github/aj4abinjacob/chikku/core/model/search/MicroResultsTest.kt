package io.github.aj4abinjacob.chikku.core.model.search

import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.settings.DockSettings
import io.github.aj4abinjacob.chikku.core.model.settings.HomeSettings
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettingsCodec
import io.github.aj4abinjacob.chikku.core.model.settings.SearchSettings
import kotlinx.coroutines.test.runTest
import org.junit.Test

class MicroResultsTest {
    @Test
    fun convertsArticleExamplesAndAffineTemperatures() {
        mapOf("32F" to "0 °C", "50ft" to "15.24 m", "30 teaspoons to tablespoons" to "10 tbsp", "10oz to g" to "283.49523125 g", "-40C to F" to "-40 °F", "273.15 K to c" to "0 °C", "2 hours to min" to "120 min", "1 cup to ml" to "236.5882365 ml").forEach { (query, expected) ->
            assertThat(MicroResults.recognize(query)).containsExactly(MicroResult.Conversion(expected))
        }
    }

    @Test
    fun incompatibleUnitsMalformedInputAndImpossibleTemperaturesDoNotConvert() {
        listOf("10oz to km", "-1K", "-500C", "NaN m", "Infinity kg", "10 yards to cats", "1e999m", "maps\nsecret", "javascript:alert(1)", "intent://evil", "file:///etc/passwd", "https://user:pass@example.com", "https://").forEach { query ->
            assertThat(MicroResults.recognize(query)).isEmpty()
        }
        assertThat(MicroResults.recognize("a".repeat(10000))).isEmpty()
    }

    @Test
    fun recognizesExplicitActionsAndAvoidsAmbiguousOrdinaryText() {
        assertThat(MicroResults.recognize("https://example.com/a?q=b")).containsExactly(MicroResult.Action(MicroResult.Kind.URL, "https://example.com/a?q=b"))
        assertThat(MicroResults.recognize("1234 W State St")).containsExactly(MicroResult.Action(MicroResult.Kind.MAP, "1234 W State St"))
        assertThat(MicroResults.recognize("maps Bengaluru & cafes")).containsExactly(MicroResult.Action(MicroResult.Kind.MAP, "Bengaluru & cafes"))
        assertThat(MicroResults.recognize("+91 (98765) 43210")).containsExactly(MicroResult.Action(MicroResult.Kind.DIAL, "+919876543210"), MicroResult.Action(MicroResult.Kind.SMS, "+919876543210")).inOrder()
        assertThat(MicroResults.recognize("flight AI101")).containsExactly(MicroResult.Action(MicroResult.Kind.FLIGHT, "AI101"))
        assertThat(MicroResults.recognize("track 123456789012")).containsExactly(MicroResult.Action(MicroResult.Kind.PACKAGE, "123456789012"))
        assertThat(MicroResults.recognize("1Z999AA10123456784")).containsExactly(MicroResult.Action(MicroResult.Kind.PACKAGE, "1Z999AA10123456784"))
        listOf("camera", "track", "maps", "123", "password1234").forEach { assertThat(MicroResults.recognize(it)).isEmpty() }
    }

    @Test
    fun explorerLinksAreReadOnlyAndTrackingIdentifiersCannotChangeHost() {
        val eth = "0x" + "a".repeat(40)
        val btc = "1BoatSLRHtKNngkdXEeobR76b53LETtpyT"
        assertThat(MicroResults.recognize(eth)).containsExactly(MicroResult.Action(MicroResult.Kind.ETHEREUM, eth))
        assertThat(MicroResults.webUrl(MicroResult.Action(MicroResult.Kind.ETHEREUM, eth))).isEqualTo("https://etherscan.io/address/$eth")
        assertThat(MicroResults.webUrl(MicroResult.Action(MicroResult.Kind.BITCOIN, btc))).isEqualTo("https://www.blockchain.com/explorer/addresses/btc/$btc")
        assertThat(MicroResults.webUrl(MicroResult.Action(MicroResult.Kind.PACKAGE, "1234&evil=1"))).isNull()
        assertThat(MicroResults.webUrl(MicroResult.Action(MicroResult.Kind.URL, "javascript:alert(1)"))).isNull()
    }

    @Test
    fun searchHonorsMicroToggleIndependentlyOfCalculatorAndWeb() = runTest {
        val search = UniversalSearch(DefaultAppSearcher(), ShortcutSource { ShortcutLookup.Found(emptyList()) }, ContactSource { _, _ -> ContactLookup.Found(emptyList()) })
        val settings = SearchSettings(calculatorEnabled = false, showWebSuggestionRow = false, playStoreRow = false)
        assertThat(search.search("32F", emptyList(), emptySet(), settings).rows).containsExactly(SearchRow.Micro(MicroResult.Conversion("0 °C")))
        assertThat(search.search("32F", emptyList(), emptySet(), settings.copy(microResultsEnabled = false)).rows).isEmpty()
    }

    @Test
    fun settingsAndBackupsRetainChoicesAndOldFilesPreserveWidgetCorners() {
        val settings = LauncherSettings(home = HomeSettings(widgetCornerRadiusDp = 24f, allowWidgetOverlap = true), dock = DockSettings(backgroundImageUri = "content://selected/image"), search = SearchSettings(microResultsEnabled = false, historyLearningEnabled = true))
        assertThat(LauncherSettingsCodec.decode(LauncherSettingsCodec.encode(settings))).isEqualTo(settings)
        assertThat(LauncherSettingsCodec.decode("{}").home.widgetCornerRadiusDp).isEqualTo(0f)
        assertThat(LauncherSettingsCodec.decode("{}").dock.backgroundImageUri).isNull()
        assertThat(LauncherSettingsCodec.decode("{}").search.historyLearningEnabled).isFalse()
        // Older settings and backups: frequent apps on means learning stays on.
        assertThat(LauncherSettingsCodec.decode("{\"search\":{\"frequentAppsEnabled\":true}}").search.historyLearningEnabled).isTrue()
        assertThat(LauncherSettingsCodec.decode("{\"search\":{\"frequentAppsEnabled\":true,\"historyLearningEnabled\":false}}").search.historyLearningEnabled).isFalse()
        assertThat(LauncherSettingsCodec.decode("{\"home\":{\"widgetCornerRadiusDp\":999}}").home.widgetCornerRadiusDp).isEqualTo(48f)
        assertThat(LauncherSettings(home = HomeSettings(widgetCornerRadiusDp = Float.NaN)).coerced().home.widgetCornerRadiusDp).isEqualTo(0f)
    }
}
