package io.github.aj4abinjacob.chikku.core.model.cards

import io.github.aj4abinjacob.chikku.core.model.ComponentKey
import io.github.aj4abinjacob.chikku.core.model.WorkspaceContent
import io.github.aj4abinjacob.chikku.core.model.backup.BackupCodec
import io.github.aj4abinjacob.chikku.core.model.backup.BackupDecodeResult
import io.github.aj4abinjacob.chikku.core.model.backup.ChikkuBackup
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettingsCodec
import io.github.aj4abinjacob.chikku.core.model.settings.SettingsFormatException
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows

class CardContentTest {
    private val provider = ComponentKey("example.widget", "example.widget.Provider", 0)
    private fun settings(content: CardContent) = LauncherSettings(cards = CardsSettings(listOf(
        AutomationCard("card", "Example", content = content, conditions = CardConditions(bluetoothConnectedOnly = true)))))

    @Test fun allContentKindsRoundtripWithoutRuntimeData() {
        listOf(CardContent.Actions, CardContent.Media,
            CardContent.Weather(WeatherLocation("Example city", 12.0, 76.0), WeatherUnit.FAHRENHEIT, true),
            CardContent.Widget(provider, 240, "logical-generation"), CardContent.Widget(instanceId = "unconfigured"),
        ).forEach { content ->
            val expected = settings(content).coerced()
            assertEquals(expected, LauncherSettingsCodec.decode(LauncherSettingsCodec.encode(expected)))
        }
    }

    @Test fun oldCardsDefaultToActionsAndNoBluetoothOrWeatherNetwork() {
        val decoded = CardsSettingsCodec.fromMap(mapOf("items" to listOf(mapOf("id" to "old", "title" to "Old")))).cards.single()
        assertEquals(CardContent.Actions, decoded.content)
        assertFalse(decoded.conditions.bluetoothConnectedOnly)
        assertFalse(CardContent.Weather().onlineEnabled)
    }

    @Test fun bluetoothUnknownOrDisconnectedWithholdsCondition() {
        val condition = CardConditions(bluetoothConnectedOnly = true)
        assertFalse(CardConditionEvaluator.matches(condition, CardEnvironment(1, 600)))
        assertFalse(CardConditionEvaluator.matches(condition, CardEnvironment(1, 600, isBluetoothConnected = false)))
        assertTrue(CardConditionEvaluator.matches(condition, CardEnvironment(1, 600, isBluetoothConnected = true)))
    }

    @Test fun damagedContentRejectsWholeConfiguration() {
        val invalid = listOf(
            null, mapOf("kind" to "FUTURE"), mapOf("kind" to "WEATHER", "unit" to "KELVIN"),
            mapOf("kind" to "WEATHER", "onlineEnabled" to "true"),
            mapOf("kind" to "WEATHER", "location" to mapOf("label" to "City", "latitude" to 91, "longitude" to 0)),
            mapOf("kind" to "WEATHER", "location" to mapOf("label" to "City", "latitude" to Double.NaN, "longitude" to 0)),
            mapOf("kind" to "EMBEDDED_WIDGET", "instanceId" to "revision", "heightDp" to 601),
            mapOf("kind" to "EMBEDDED_WIDGET", "instanceId" to "revision", "provider" to mapOf("packageName" to "app", "className" to "Widget", "userSerial" to -1)),
            mapOf("kind" to "EMBEDDED_WIDGET", "instanceId" to ""),
        )
        invalid.forEach { content -> assertThrows(SettingsFormatException::class.java) {
            CardsSettingsCodec.fromMap(mapOf("items" to listOf(mapOf("id" to "bad", "title" to "Preserved", "content" to content))))
        } }
    }

    @Test fun embeddedWidgetBackupRenewsBindingIdentityAndPreservesProvider() {
        val original = settings(CardContent.Widget(provider, 220, "local-original"))
        val archive = ChikkuBackup(1, "test", original, WorkspaceContent(1, emptyList()), emptyList(), emptyList())
        val decoded = BackupCodec.decode(BackupCodec.encode(archive)) as BackupDecodeResult.Success
        val exported = decoded.backup.settings.cards.cards.single().content as CardContent.Widget
        val restored = decoded.backup.withoutWidgetIds().settings.cards.cards.single().content as CardContent.Widget
        assertEquals(provider, exported.provider)
        assertEquals(220, restored.heightDp)
        assertNotEquals("local-original", exported.instanceId)
        assertNotEquals(exported.instanceId, restored.instanceId)
        assertFalse(BackupCodec.encode(archive).contains("appWidgetId"))
    }

    @Test fun embeddedWidgetPrivateOrHiddenProviderWithholdsEntireTitle() {
        val card = settings(CardContent.Widget(provider, instanceId = "revision")).cards.cards.single()
        val targets = CardTargetSnapshot(emptyList(), emptySet(), 0)
        assertTrue(CardVisibility.targetsAllowed(card, targets)) // Missing provider is a resolvable placement.
        assertFalse(CardVisibility.targetsAllowed(card, targets.copy(personalUserSerial = 10)))
        assertFalse(CardVisibility.targetsAllowed(card, targets.copy(hidden = setOf(
            io.github.aj4abinjacob.chikku.core.model.AppKey(provider.packageName, "OtherActivity", 0)))))
    }
}
