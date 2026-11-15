package io.github.aj4abinjacob.chikku.core.model.search

import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.CellPlacement
import io.github.aj4abinjacob.chikku.core.model.ContainerType
import io.github.aj4abinjacob.chikku.core.model.FolderEntry
import io.github.aj4abinjacob.chikku.core.model.ItemTarget
import io.github.aj4abinjacob.chikku.core.model.LauncherApp
import io.github.aj4abinjacob.chikku.core.model.ProfileType
import io.github.aj4abinjacob.chikku.core.model.WorkspaceItem
import io.github.aj4abinjacob.chikku.core.model.settings.SearchSettings
import kotlinx.coroutines.test.runTest
import org.junit.Test

class FolderSearchTest {
    private val key = AppKey("personal", "personal.Main", 0)
    private val personal = LauncherApp(key, "App", ProfileType.PERSONAL)
    private fun folder(id: String, title: String, targets: List<ItemTarget> = listOf(ItemTarget.App(key))) =
        WorkspaceItem(id, CellPlacement(ContainerType.DESKTOP, 0, 0, 0),
            ItemTarget.Folder(title, targets.mapIndexed { index, target -> FolderEntry("$id-$index", target) }))

    @Test
    fun matchingRanksNamesAndReturnsStableFolderIdentityAndCount() {
        val folders = listOf(folder("later", "My Tools"), folder("first", "Tools"), folder("other", "Games"))
        assertThat(FolderSearch.search(folders, listOf(personal), emptySet(), "tools"))
            .containsExactly(FolderHit("first", "Tools", 1), FolderHit("later", "My Tools", 1)).inOrder()
        assertThat(FolderSearch.search(folders, listOf(personal), emptySet(), "")).isEmpty()
    }

    @Test
    fun hiddenUnavailableWorkAndOtherProfileContentsWithholdEntireTitle() {
        val workKey = key.copy(userSerial = 10)
        val otherKey = key.copy(userSerial = 11)
        val unavailable = AppKey("missing", "missing.Main", 0)
        val folders = listOf(
            folder("personal", "Tools"),
            folder("work", "Tools work", listOf(ItemTarget.App(workKey))),
            folder("other", "Tools private", listOf(ItemTarget.App(otherKey))),
            folder("mixed", "Tools mixed", listOf(ItemTarget.App(key), ItemTarget.App(unavailable))),
            folder("empty", "Tools empty", emptyList()),
        )
        val apps = listOf(personal, LauncherApp(workKey, "Work", ProfileType.WORK), LauncherApp(otherKey, "Private", ProfileType.OTHER))
        assertThat(FolderSearch.search(folders, apps, emptySet(), "tools"))
            .containsExactly(FolderHit("personal", "Tools", 1))
        assertThat(FolderSearch.search(folders, apps, setOf(key), "tools")).isEmpty()
        assertThat(FolderSearch.search(folders, emptyList(), emptySet(), "tools")).isEmpty()
    }

    @Test
    fun shortcutOwnerMustMatchVisiblePersonalPackageAndProfile() {
        val folders = listOf(
            folder("visible", "Tools", listOf(ItemTarget.Shortcut("personal", "s", 0))),
            folder("wrong-profile", "Tools", listOf(ItemTarget.Shortcut("personal", "s", 10))),
            folder("wrong-package", "Tools", listOf(ItemTarget.Shortcut("missing", "s", 0))),
        )
        assertThat(FolderSearch.visibleFolders(folders, listOf(personal), emptySet()))
            .containsExactly(FolderHit("visible", "Tools", 1))
    }

    @Test
    fun universalSearchRequiresSwitchAndInsertsExplicitFolderBeforeWeb() = runTest {
        val search = UniversalSearch(DefaultAppSearcher(), { ShortcutLookup.NotDefaultHome }, { _, _ -> ContactLookup.Unavailable })
        val folders = listOf(folder("tools", "Tools"))
        assertThat(search.search("tools", listOf(personal), emptySet(), SearchSettings(), folderItems = folders).folders).isEmpty()
        val result = search.search("tools", listOf(personal), emptySet(), SearchSettings(foldersEnabled = true), folderItems = folders)
        assertThat(result.rows.first()).isEqualTo(SearchRow.Folder(FolderHit("tools", "Tools", 1)))
        assertThat(result.folders).hasSize(1)
    }
}
