package io.github.aj4abinjacob.chikku.home

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.AppsState
import io.github.aj4abinjacob.chikku.core.model.CellPlacement
import io.github.aj4abinjacob.chikku.core.model.ComponentKey
import io.github.aj4abinjacob.chikku.core.model.ContainerType
import io.github.aj4abinjacob.chikku.core.model.FolderEntry
import io.github.aj4abinjacob.chikku.core.model.GestureAction
import io.github.aj4abinjacob.chikku.core.model.GridSpec
import io.github.aj4abinjacob.chikku.core.model.ItemActions
import io.github.aj4abinjacob.chikku.core.model.ItemTarget
import io.github.aj4abinjacob.chikku.core.model.Workspace
import io.github.aj4abinjacob.chikku.core.model.WorkspaceItem
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Foundation hooks: settings in [HomeUiState] and resolution of folder/widget/shortcut items. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestHomeApplication::class)
class HomeFoundationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dispatcher = UnconfinedTestDispatcher()
    private val appScope = CoroutineScope(SupervisorJob() + dispatcher)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() {
        appScope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun uiStateFollowsSettingsRepository() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        val deps = FakeHomeDependencies(appScope = appScope, settingsRepository = settings)
        val guard = StartupGuard({ context.getSharedPreferences("foundation_guard", Context.MODE_PRIVATE) }, dispatcher)
        val vm = HomeViewModel(deps, SavedStateHandle(), guard, false, dispatcher, dispatcher)
        backgroundScope.launch(dispatcher) { vm.uiState.collect {} }

        assertThat(vm.uiState.value.settings).isEqualTo(LauncherSettings())
        settings.update { it.copy(home = it.home.copy(layoutLocked = true)) }
        assertThat(vm.uiState.value.settings.home.layoutLocked).isTrue()
    }

    @Test
    fun buildWorkspaceUiResolvesNewItemKinds() {
        val chrome = app("Chrome")
        val gone = ItemTarget.Shortcut("pkg.gone", "s1", 0, "Gone")
        val workspace = Workspace(
            GridSpec(5, 5), GridSpec(5, 1), 1,
            listOf(
                WorkspaceItem(
                    "f", CellPlacement(ContainerType.DESKTOP, 0, 0, 0),
                    ItemTarget.Folder("Tools", listOf(FolderEntry("e1", ItemTarget.App(chrome.key)), FolderEntry("e2", gone))),
                    actions = ItemActions(swipeUp = GestureAction.OpenDrawer),
                ),
                WorkspaceItem(
                    "w", CellPlacement(ContainerType.DESKTOP, 0, 1, 0, 2, 2),
                    ItemTarget.Widget(3, ComponentKey("pkg.widgets", "pkg.widgets.P", 0)), zIndex = 2,
                ),
            ),
        )
        val ui = buildWorkspaceUi(workspace, AppsState.Loaded(listOf(chrome)))
        val folder = ui.pages[0].single { it.id == "f" }
        assertThat(folder.actions.swipeUp).isEqualTo(GestureAction.OpenDrawer)
        val content = folder.content as ItemContent.Folder
        assertThat(content.title).isEqualTo("Tools")
        assertThat((content.entries[0].content as ItemContent.App).availability).isEqualTo(Availability.AVAILABLE)
        assertThat((content.entries[1].content as ItemContent.Shortcut).availability).isEqualTo(Availability.UNAVAILABLE)

        val widget = ui.pages[0].single { it.id == "w" }
        assertThat(widget.zIndex).isEqualTo(2)
        assertThat((widget.content as ItemContent.Widget).availability).isEqualTo(Availability.UNKNOWN)
    }
}
