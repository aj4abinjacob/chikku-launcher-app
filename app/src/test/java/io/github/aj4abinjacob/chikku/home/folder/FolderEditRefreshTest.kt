package io.github.aj4abinjacob.chikku.home.folder

import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.CellPlacement
import io.github.aj4abinjacob.chikku.core.model.ContainerType
import io.github.aj4abinjacob.chikku.core.model.GestureAction
import io.github.aj4abinjacob.chikku.core.model.ItemActions
import io.github.aj4abinjacob.chikku.core.model.settings.FolderCover
import io.github.aj4abinjacob.chikku.core.model.settings.FolderCoverMode
import io.github.aj4abinjacob.chikku.core.model.settings.FolderCoverShortcut
import io.github.aj4abinjacob.chikku.core.model.settings.FolderSettings
import io.github.aj4abinjacob.chikku.home.AppIconView
import io.github.aj4abinjacob.chikku.home.FakeIconProvider
import io.github.aj4abinjacob.chikku.home.Availability
import io.github.aj4abinjacob.chikku.home.FolderEntryUi
import io.github.aj4abinjacob.chikku.home.HomeItemUi
import io.github.aj4abinjacob.chikku.home.ItemContent
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Folder gesture and cover rules with synthetic folder membership; no device gesture claim. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FolderEditRefreshTest {
    private fun app(id: String, profile: Long = 0, available: Boolean = true) = ItemContent.App(
        AppKey("pkg", "pkg.$id", profile), id, profile != 0L,
        if (available) Availability.AVAILABLE else Availability.UNAVAILABLE,
    )
    private val first = app("Zulu")
    private val last = app("Alpha", 10)
    private val folder = ItemContent.Folder("Tools", listOf(FolderEntryUi("first", first), FolderEntryUi("last", last)))
    private val item = HomeItemUi("folder", CellPlacement(ContainerType.DESKTOP, 0, 0, 0), folder)

    @Test fun explicitOpenFolderIsClaimedOnBothSwipesWithGlobalActionsOff() {
        val actions = ItemActions(tap = GestureAction.OpenFolder, swipeUp = GestureAction.OpenFolder, swipeDown = GestureAction.OpenFolder)
        FolderGesture.entries.forEach {
            assertThat(FolderGestures.resolve(it, actions, false)).isEqualTo(FolderGestureOutcome.OpenFolder)
        }
        assertThat(FolderGestures.claimsSwipe(FolderGesture.SWIPE_DOWN, actions, false)).isTrue()
        assertThat(FolderGestures.hasCustomGestures(actions, false)).isTrue()
    }

    @Test fun coverUsesManualMembershipAndMissingOrUnavailableSelectionFallsBack() {
        assertThat(folderCoverEntry(folder, FolderCover(FolderCoverMode.FIRST_APP))).isEqualTo(first)
        assertThat(folderCoverEntry(folder, FolderCover(FolderCoverMode.LAST_APP))).isEqualTo(last)
        assertThat(folderCoverEntry(folder, FolderCover(FolderCoverMode.SELECTED_APP, last.key))).isEqualTo(last)
        assertThat(folderCoverEntry(folder, FolderCover(FolderCoverMode.SELECTED_APP, last.key.copy(userSerial = 0)))).isNull()
        val missing = folder.copy(entries = listOf(FolderEntryUi("unavailable", app("Gone", available = false))))
        assertThat(folderCoverEntry(missing, FolderCover(FolderCoverMode.FIRST_APP))).isNull()
        assertThat(folderCoverEntry(folder, FolderCover())).isNull()
    }

    @Test fun shortcutCanBeCoverAndUnavailableShortcutFallsBack() {
        val pinned = ItemContent.Shortcut("browser", "pin-1", 0, "X", Availability.AVAILABLE)
        val mixed = ItemContent.Folder("Mixed", listOf(FolderEntryUi("app", first), FolderEntryUi("shortcut", pinned)))
        val selected = FolderCover(FolderCoverMode.SELECTED_APP, shortcut = FolderCoverShortcut("browser", "pin-1", 0))
        assertThat(folderCoverEntry(mixed, selected)).isEqualTo(pinned)
        assertThat(folderCoverEntry(mixed, FolderCover(FolderCoverMode.LAST_APP))).isEqualTo(pinned)
        assertThat(folderCoverEntry(mixed, selected.copy(shortcut = FolderCoverShortcut("browser", "pin-1", 10)))).isNull()
        val gone = mixed.copy(entries = listOf(FolderEntryUi("shortcut", pinned.copy(availability = Availability.UNAVAILABLE))))
        assertThat(folderCoverEntry(gone, selected)).isNull()
    }

    @Test fun bindingCoverUsesFullAppDrawableAndRestoringPreviewRebuildsFolderIcon() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Unconfined)
        try {
            val view = AppIconView(context, AppIconView.Style.WORKSPACE)
            val binder = FolderIconBinder(context, scope, FakeIconProvider())
            binder.bind(view, folder, FolderSettings(covers = mapOf("folder" to FolderCover(FolderCoverMode.LAST_APP))), "folder")
            assertThat(view.icon).isNotInstanceOf(FolderIconDrawable::class.java)
            assertThat(view.contentDescription.toString()).contains("Folder Tools")
            binder.bind(view, folder, FolderSettings(), "folder")
            assertThat(view.icon).isInstanceOf(FolderIconDrawable::class.java)
        } finally { scope.coroutineContext[kotlinx.coroutines.Job]?.cancel() }
    }
}
