package io.github.aj4abinjacob.chikku.core.model.backup

import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.AppCustomization
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.CellPlacement
import io.github.aj4abinjacob.chikku.core.model.ComponentKey
import io.github.aj4abinjacob.chikku.core.model.ContainerType
import io.github.aj4abinjacob.chikku.core.model.DrawerGroup
import io.github.aj4abinjacob.chikku.core.model.DrawerSortMode
import io.github.aj4abinjacob.chikku.core.model.FolderEntry
import io.github.aj4abinjacob.chikku.core.model.GestureAction
import io.github.aj4abinjacob.chikku.core.model.ItemActions
import io.github.aj4abinjacob.chikku.core.model.ItemTarget
import io.github.aj4abinjacob.chikku.core.model.WorkspaceContent
import io.github.aj4abinjacob.chikku.core.model.WorkspaceItem
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.core.model.settings.MiniJson
import io.github.aj4abinjacob.chikku.core.model.settings.PaddingSize
import org.junit.Test

class BackupCodecTest {

    private val mail = AppKey("com.mail", "com.mail.Main", 0)
    private val work = AppKey("com.mail", "com.mail.Main", 10)
    private val dialer = AppKey("com.dialer", "com.dialer.Main", 0)

    private fun sample(): ChikkuBackup = ChikkuBackup(
        createdAtMillis = 1_760_000_000_000,
        appVersion = "0.1.0",
        settings = LauncherSettings().let { it.copy(home = it.home.copy(desktopColumns = 4, iconScale = 1.25f)) },
        workspace = WorkspaceContent(
            pageCount = 2,
            items = listOf(
                WorkspaceItem(
                    "a", CellPlacement(ContainerType.DESKTOP, 0, 1, 2), ItemTarget.App(mail),
                    actions = ItemActions(swipeUp = GestureAction.LaunchApp(dialer)),
                ),
                WorkspaceItem("w", CellPlacement(ContainerType.DESKTOP, 1, 0, 0, 4, 2), ItemTarget.Widget(42, ComponentKey("com.clock", "com.clock.Widget", 0)), zIndex = 3, widgetPadding = PaddingSize.NONE),
                WorkspaceItem(
                    "f", CellPlacement(ContainerType.DOCK, 0, 2, 0),
                    ItemTarget.Folder(
                        "Work \"stuff\"",
                        listOf(
                            FolderEntry("e1", ItemTarget.App(work)),
                            FolderEntry("e2", ItemTarget.Shortcut("com.mail", "compose:new", 10, "Compose")),
                        ),
                    ),
                    actions = ItemActions(tap = GestureAction.OpenDrawer),
                ),
                WorkspaceItem("s", CellPlacement(ContainerType.DESKTOP, 0, 0, 0), ItemTarget.Shortcut("com.dialer", "call", 0, null)),
            ),
        ),
        customizations = listOf(AppCustomization(mail, hidden = true, customLabel = "Post", swipeDownAction = GestureAction.OpenSearch)),
        drawerGroups = listOf(DrawerGroup("g1", "Social", 0, DrawerSortMode.MANUAL, listOf(mail, work))),
    )

    private fun decodeOk(text: String): ChikkuBackup =
        (BackupCodec.decode(text) as BackupDecodeResult.Success).backup

    private fun errorOf(text: String): BackupError = (BackupCodec.decode(text) as BackupDecodeResult.Failure).error

    @Suppress("UNCHECKED_CAST")
    private fun mutate(block: (MutableMap<String, Any?>) -> Unit): String {
        val map = MiniJson.parse(BackupCodec.encode(sample())) as MutableMap<String, Any?>
        block(map)
        return MiniJson.stringify(map)
    }

    @Suppress("UNCHECKED_CAST")
    private fun MutableMap<String, Any?>.firstItem(): MutableMap<String, Any?> =
        ((this["workspace"] as Map<String, Any?>)["items"] as List<Any?>)[0] as MutableMap<String, Any?>

    @Test
    fun roundTripKeepsEverythingButWidgetIds() {
        val original = sample()
        val decoded = decodeOk(BackupCodec.encode(original))
        assertThat(decoded).isEqualTo(original.withoutWidgetIds())
        val widget = decoded.workspace.items.single { it.id == "w" }.target as ItemTarget.Widget
        assertThat(widget.appWidgetId).isEqualTo(0)
        assertThat(widget.provider).isEqualTo(ComponentKey("com.clock", "com.clock.Widget", 0))
    }

    @Test
    fun drawerRowCountAndSpacingTravelInPortableBackup() {
        val original = sample().let { backup ->
            backup.copy(settings = backup.settings.copy(drawer = backup.settings.drawer.copy(
                rows = 7, automaticRowSpacing = false,
            )))
        }
        assertThat(decodeOk(BackupCodec.encode(original))).isEqualTo(original.withoutWidgetIds())
    }

    @Test
    fun encodedFileNeverContainsWidgetIds() {
        val text = BackupCodec.encode(sample())
        assertThat(text).doesNotContain("appWidgetId")
    }

    @Test
    fun unknownKeysAreIgnored() {
        val text = mutate {
            it["futureTopLevel"] = mapOf("x" to 1L)
            it.firstItem()["futureItemField"] = true
        }
        assertThat(decodeOk(text)).isEqualTo(sample().withoutWidgetIds())
    }

    @Test
    fun newerFormatVersionIsRejectedWithClearError() {
        val error = errorOf(mutate { it["formatVersion"] = 99L })
        assertThat(error).isEqualTo(BackupError.UnsupportedVersion(99, BackupCodec.FORMAT_VERSION))
    }

    @Test
    fun nonJsonAndForeignJsonAreRejected() {
        assertThat(errorOf("not json {")).isEqualTo(BackupError.NotJson)
        assertThat(errorOf(BackupCodec.encode(sample()).dropLast(5))).isEqualTo(BackupError.NotJson)
        assertThat(errorOf("""{"hello":"world"}""")).isEqualTo(BackupError.NotChikkuBackup)
        assertThat(errorOf("[1,2]")).isEqualTo(BackupError.NotChikkuBackup)
    }

    @Test
    fun oversizedInputIsRejectedBeforeParsing() {
        assertThat(errorOf(" ".repeat(BackupCodec.MAX_BYTES + 1))).isEqualTo(BackupError.TooLarge)
    }

    @Test
    fun damagedContentIsRejected() {
        assertThat(errorOf(mutate { it.remove("workspace") })).isInstanceOf(BackupError.Invalid::class.java)
        assertThat(errorOf(mutate { it.remove("formatVersion") })).isInstanceOf(BackupError.Invalid::class.java)
        assertThat(errorOf(mutate { it.firstItem()["x"] = -1L })).isInstanceOf(BackupError.Invalid::class.java)
        assertThat(errorOf(mutate { it.firstItem()["container"] = "ROOF" })).isInstanceOf(BackupError.Invalid::class.java)
        assertThat(errorOf(mutate { it.firstItem()["id"] = "w" })).isEqualTo(BackupError.Invalid("duplicate item id"))
        assertThat(errorOf(mutate { (it.firstItem()["actions"] as MutableMap<String, Any?>)["swipeUp"] = "format_disk" }))
            .isEqualTo(BackupError.Invalid("unknown action"))
        assertThat(errorOf(mutate { (it.firstItem()["target"] as MutableMap<String, Any?>)["type"] = "script" }))
            .isEqualTo(BackupError.Invalid("item.type"))
        assertThat(errorOf(mutate { (it.firstItem()["target"] as MutableMap<String, Any?>)["package"] = "" }))
            .isInstanceOf(BackupError.Invalid::class.java)
    }

    @Test
    fun optionalSectionsMayBeAbsent() {
        val decoded = decodeOk(mutate {
            it.remove("customizations")
            it.remove("drawerGroups")
            it.remove("createdAt")
        })
        assertThat(decoded.customizations).isEmpty()
        assertThat(decoded.drawerGroups).isEmpty()
    }

    @Test
    fun summaryCountsWithoutNames() {
        val summary = sample().summary(installed = setOf(mail, dialer))
        assertThat(summary).isEqualTo(
            BackupSummary(
                pages = 2, items = 4, dockItems = 1, folders = 1, widgets = 1, shortcuts = 2,
                customizations = 1, drawerGroups = 1, missingApps = 1,
            ),
        )
        assertThat(sample().summary(installed = null).missingApps).isNull()
    }

    @Test
    fun defaultLayoutFillsDockLeftToRight() {
        var n = 0
        val content = DefaultLayout.content(listOf(mail, dialer, mail), dockColumns = 5) { "id${n++}" }
        assertThat(content.pageCount).isEqualTo(1)
        assertThat(content.items.map { it.placement }).containsExactly(
            CellPlacement(ContainerType.DOCK, 0, 0, 0),
            CellPlacement(ContainerType.DOCK, 0, 1, 0),
        ).inOrder()
        assertThat(DefaultLayout.content(listOf(mail, dialer), dockColumns = 1) { "x" }.items).hasSize(1)
    }
}
