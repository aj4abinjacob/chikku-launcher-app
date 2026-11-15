package io.github.aj4abinjacob.chikku.settings.iconpicker

import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.AppsState
import io.github.aj4abinjacob.chikku.core.model.LauncherApp
import io.github.aj4abinjacob.chikku.core.model.ProfileType
import io.github.aj4abinjacob.chikku.core.model.CellPlacement
import io.github.aj4abinjacob.chikku.core.model.ContainerType
import io.github.aj4abinjacob.chikku.core.model.FolderEntry
import io.github.aj4abinjacob.chikku.core.model.GridSpec
import io.github.aj4abinjacob.chikku.core.model.ItemTarget
import io.github.aj4abinjacob.chikku.core.model.Workspace
import io.github.aj4abinjacob.chikku.core.model.WorkspaceItem
import org.junit.Test

/** The shortcut icon picker finds its shortcut on HOME or inside a folder, and nothing else. */
class ShortcutIconTargetTest {
    private val web = ItemTarget.Shortcut("org.mozilla.firefox", "url:example", 0, "Example", "com.pack", "ic_web")
    private val app = ItemTarget.App(AppKey("com.example", "com.example.Main", 0))
    private fun at(x: Int) = CellPlacement(ContainerType.DESKTOP, 0, x, 0)

    private val workspace = Workspace(
        GridSpec(5, 5), GridSpec(5, 1), 1,
        listOf(
            WorkspaceItem("home", at(0), web),
            WorkspaceItem("app", at(1), app),
            WorkspaceItem("folder", at(2), ItemTarget.Folder("Web", listOf(FolderEntry("entry", web), FolderEntry("member", app)))),
        ),
    )

    @Test
    fun findsShortcutsOnHomeAndInFolders() {
        assertThat(ShortcutRef("home", null).find(workspace)).isEqualTo(web)
        assertThat(ShortcutRef("entry", "folder").find(workspace)).isEqualTo(web)
    }

    @Test
    fun appsMissingItemsAndWrongFoldersAreNotShortcuts() {
        assertThat(ShortcutRef("app", null).find(workspace)).isNull()
        assertThat(ShortcutRef("member", "folder").find(workspace)).isNull()
        assertThat(ShortcutRef("entry", null).find(workspace)).isNull()
        assertThat(ShortcutRef("entry", "app").find(workspace)).isNull()
        assertThat(ShortcutRef("gone", null).find(workspace)).isNull()
    }

    @Test
    fun shortcutLabelsDisappearWhenTheirProfileIsUnavailable() {
        val app = LauncherApp(AppKey(web.packageName, "example.Main", 0), "Sample", ProfileType.PERSONAL)
        val ref = ShortcutRef("home", null)
        assertThat(ref.findAvailable(workspace, AppsState.Loaded(listOf(app)))).isEqualTo(web)
        assertThat(ref.findAvailable(workspace, AppsState.Loading)).isNull()
        assertThat(ref.findAvailable(workspace, AppsState.Loaded(emptyList()))).isNull()
        assertThat(ref.findAvailable(workspace, AppsState.Loaded(listOf(app.copy(key = app.key.copy(userSerial = 10)))))).isNull()
        assertThat(ref.findAvailable(workspace, AppsState.Loaded(listOf(app.copy(profileType = ProfileType.OTHER))))).isNull()
    }

    @Test
    fun cacheKeyChangesWithTheIcon() {
        val shown = IconTarget.Shortcut("home", null, web.packageName, web.shortcutId, 0, CurrentOverride("com.pack", "ic_web"))
        assertThat(shown.copy(override = null).cacheKey).isNotEqualTo(shown.cacheKey)
    }
}
