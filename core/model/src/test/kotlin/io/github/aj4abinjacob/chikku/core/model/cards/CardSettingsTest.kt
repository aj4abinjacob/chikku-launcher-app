package io.github.aj4abinjacob.chikku.core.model.cards

import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.GestureAction
import io.github.aj4abinjacob.chikku.core.model.WorkspaceContent
import io.github.aj4abinjacob.chikku.core.model.WorkspaceItem
import io.github.aj4abinjacob.chikku.core.model.ItemTarget
import io.github.aj4abinjacob.chikku.core.model.ComponentKey
import io.github.aj4abinjacob.chikku.core.model.CellPlacement
import io.github.aj4abinjacob.chikku.core.model.ContainerType
import io.github.aj4abinjacob.chikku.core.model.backup.BackupCodec
import io.github.aj4abinjacob.chikku.core.model.backup.BackupDecodeResult
import io.github.aj4abinjacob.chikku.core.model.backup.ChikkuBackup
import io.github.aj4abinjacob.chikku.core.model.settings.FolderScrollStyle
import io.github.aj4abinjacob.chikku.core.model.settings.FolderSettings
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettingsCodec
import io.github.aj4abinjacob.chikku.core.model.settings.SettingsFormatException
import org.junit.Assert.assertThrows
import org.junit.Test

class CardSettingsTest {
    @Test fun oldSettingsKeepVerticalFoldersAndNoCards() {
        val settings = LauncherSettingsCodec.decode("{}")
        assertThat(settings.folders.scrollStyle).isEqualTo(FolderScrollStyle.VERTICAL)
        assertThat(settings.folders.rows).isEqualTo(3)
        assertThat(settings.cards.cards).isEmpty()
    }

    @Test fun allFeaturedTypesAndActionsRoundTripInBackupMapAndJson() {
        val app = AppKey("sample.package", "sample.Activity", 5)
        val settings = LauncherSettings(folders = FolderSettings(scrollStyle = FolderScrollStyle.HORIZONTAL_PAGED, rows = 6),
            cards = CardsSettings(listOf(CardFeatured.None, CardFeatured.App(app),
                CardFeatured.Shortcut("sample.package", "colon:shortcut/id", 5), CardFeatured.Widget("stable-workspace-id")).mapIndexed { index, featured ->
                AutomationCard("card-$index", "Evening", index != 0, featured,
                    listOf(GestureAction.LaunchApp(app), GestureAction.LaunchShortcut("sample.package", "a:b", 5),
                        GestureAction.OpenDrawer, GestureAction.ToggleDock, GestureAction.OpenSearch, GestureAction.None),
                    CardConditions(setOf(1, 7), CardTimeRange(1320, 360), true, true))
            }))
        assertThat(LauncherSettingsCodec.decode(LauncherSettingsCodec.encode(settings))).isEqualTo(settings)
        val backup = ChikkuBackup(123, "debug", settings, WorkspaceContent(1, emptyList()), emptyList(), emptyList())
        val decoded = BackupCodec.decode(BackupCodec.encode(backup)) as BackupDecodeResult.Success
        assertThat(decoded.backup.settings).isEqualTo(settings)
    }

    @Test fun overnightUsesStartingWeekdayAndExclusiveEnd() {
        val mondayNight = CardConditions(setOf(1), CardTimeRange(1320, 360))
        assertThat(CardConditionEvaluator.matches(mondayNight, CardEnvironment(1, 1320))).isTrue()
        assertThat(CardConditionEvaluator.matches(mondayNight, CardEnvironment(2, 359))).isTrue()
        assertThat(CardConditionEvaluator.matches(mondayNight, CardEnvironment(2, 360))).isFalse()
        assertThat(CardConditionEvaluator.matches(mondayNight, CardEnvironment(1, 100))).isFalse()
        assertThat(CardConditionEvaluator.matches(mondayNight, CardEnvironment(1, 1319))).isFalse()
        assertThat(CardConditionEvaluator.matches(CardConditions(setOf(7), CardTimeRange(1320, 360)), CardEnvironment(1, 100))).isTrue()
    }

    @Test fun daytimeAndAllDayBoundsArePredictable() {
        val daytime = CardConditions(timeRange = CardTimeRange(600, 720))
        assertThat(CardConditionEvaluator.matches(daytime, CardEnvironment(4, 600))).isTrue()
        assertThat(CardConditionEvaluator.matches(daytime, CardEnvironment(4, 719))).isTrue()
        assertThat(CardConditionEvaluator.matches(daytime, CardEnvironment(4, 720))).isFalse()
        assertThat(CardConditionEvaluator.matches(CardConditions(setOf(2), CardTimeRange(300, 300)), CardEnvironment(2, 0))).isTrue()
        assertThat(CardConditionEvaluator.matches(CardConditions(), CardEnvironment(8, 0))).isFalse()
        assertThat(CardConditionEvaluator.matches(CardConditions(), CardEnvironment(1, 1440))).isFalse()
    }

    @Test fun requiredUnknownConditionsFailClosedAndAllMustMatch() {
        val conditions = CardConditions(chargingOnly = true, wifiOnly = true)
        assertThat(CardConditionEvaluator.matches(conditions, CardEnvironment(1, 100))).isFalse()
        assertThat(CardConditionEvaluator.matches(conditions, CardEnvironment(1, 100, true, null))).isFalse()
        assertThat(CardConditionEvaluator.matches(conditions, CardEnvironment(1, 100, false, true))).isFalse()
        assertThat(CardConditionEvaluator.matches(conditions, CardEnvironment(1, 100, true, true))).isTrue()
        assertThat(CardConditionEvaluator.matches(CardConditions(), CardEnvironment(1, 100))).isTrue()
    }

    @Test fun malformedCardsRejectEntireSettingsInsteadOfDroppingUserData() {
        val invalid = listOf(
            "null", "{\"items\":null}",
            "{\"items\":[{\"id\":\"a\",\"title\":\"A\",\"actions\":null}]}",
            "{\"items\":[{\"id\":\"a\",\"title\":\"A\",\"featured\":null}]}",
            "{\"items\":[{\"id\":\"a\",\"title\":\"A\",\"conditions\":null}]}",
            "{\"items\":[{\"id\":\"a\",\"title\":\"A\",\"conditions\":{\"weekdays\":null}}]}",
            "[]", "{\"items\":false}", "{\"items\":[{}]}",
            "{\"items\":[{\"id\":\"a\",\"title\":\"A\",\"actions\":[\"unknown\"]}]}",
            "{\"items\":[{\"id\":\"a\",\"title\":\"A\",\"featured\":{\"kind\":\"INVALID\"}}]}",
            "{\"items\":[{\"id\":\"a\",\"title\":\"A\",\"enabled\":\"yes\"}]}",
            "{\"items\":[{\"id\":\"a\",\"title\":\"A\",\"conditions\":{\"weekdays\":[1.5]}}]}",
            "{\"items\":[{\"id\":\"a\",\"title\":\"A\",\"conditions\":{\"weekdays\":[0]}}]}",
            "{\"items\":[{\"id\":\"a\",\"title\":\"A\"},{\"id\":\"a\",\"title\":\"B\"}]}"
        )
        invalid.forEach { cards ->
            assertThrows(SettingsFormatException::class.java) { LauncherSettingsCodec.decode("{\"cards\":$cards}") }
            assertThat(LauncherSettingsCodec.decodeOrNull("{\"cards\":$cards}")).isNull()
        }
    }

    @Test fun widgetCardsKeepPlacementIdentityAndRestoreRequiresFreshBinding() {
        val widget = WorkspaceItem("stable-widget", CellPlacement(ContainerType.DESKTOP, 0, 0, 0, 2, 2),
            ItemTarget.Widget(12345, ComponentKey("sample.widget", "sample.Widget", 0)))
        val settings = LauncherSettings(cards = CardsSettings(listOf(AutomationCard("card", "Widget card", featured = CardFeatured.Widget(widget.id)))))
        val backup = ChikkuBackup(123, "debug", settings, WorkspaceContent(1, listOf(widget)), emptyList(), emptyList())
        val encoded = BackupCodec.encode(backup)
        assertThat(encoded).doesNotContain("12345")
        val restored = (BackupCodec.decode(encoded) as BackupDecodeResult.Success).backup
        assertThat(restored.workspace.items.single().id).isEqualTo(widget.id)
        assertThat((restored.workspace.items.single().target as ItemTarget.Widget).appWidgetId).isEqualTo(0)
        assertThat(restored.settings.cards.cards.single().featured).isEqualTo(CardFeatured.Widget(widget.id))
    }

    @Test fun malformedCardsInBackupRejectTheWholeArchive() {
        val backup = ChikkuBackup(123, "debug", LauncherSettings(), WorkspaceContent(1, emptyList()), emptyList(), emptyList())
        val json = BackupCodec.encode(backup).replace("\"cards\":{\"items\":[]}", "\"cards\":{\"items\":[{}]}")
        assertThat(BackupCodec.decode(json)).isInstanceOf(BackupDecodeResult.Failure::class.java)
    }

    @Test fun folderOnlyActionsCannotBecomeInertCardButtonsThroughImport() {
        val contextual = AutomationCard("a", "A", actions = listOf(GestureAction.OpenFolder))
        assertThrows(IllegalArgumentException::class.java) { contextual.coerced() }
        assertThat(LauncherSettingsCodec.decodeOrNull("""{"cards":{"items":[{"id":"a","title":"A","actions":["open_folder"]}]}}""")).isNull()
    }

    @Test fun limitsRejectLossyTruncationAndSafeBoundsCoerce() {
        val empty = AutomationCard("a", "A", actions = emptyList(), conditions = CardConditions(timeRange = CardTimeRange(-1, 9999)))
        assertThat(empty.coerced().actions).hasSize(6)
        assertThat(empty.coerced().conditions.timeRange).isEqualTo(CardTimeRange(0, 1439))
        assertThrows(IllegalArgumentException::class.java) { empty.copy(actions = List(7) { GestureAction.None }).coerced() }
        assertThrows(IllegalArgumentException::class.java) { CardsSettings(List(25) { empty.copy(id = "$it") }).coerced() }
        assertThrows(IllegalArgumentException::class.java) { empty.copy(title = "x".repeat(81)).coerced() }
        assertThat(LauncherSettings(folders = FolderSettings(rows = 999)).coerced().folders.rows).isEqualTo(6)
    }
}
