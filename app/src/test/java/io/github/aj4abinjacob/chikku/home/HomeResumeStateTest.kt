package io.github.aj4abinjacob.chikku.home

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.AppsRepository
import io.github.aj4abinjacob.chikku.core.model.CellPlacement
import io.github.aj4abinjacob.chikku.core.model.ContainerType
import io.github.aj4abinjacob.chikku.core.model.FolderEntry
import io.github.aj4abinjacob.chikku.core.model.GridSpec
import io.github.aj4abinjacob.chikku.core.model.ItemTarget
import io.github.aj4abinjacob.chikku.core.model.ProfileType
import io.github.aj4abinjacob.chikku.core.model.Workspace
import io.github.aj4abinjacob.chikku.core.model.WorkspaceItem
import io.github.aj4abinjacob.chikku.core.model.WorkspaceState
import io.github.aj4abinjacob.chikku.core.model.settings.FolderCover
import io.github.aj4abinjacob.chikku.core.model.settings.FolderCoverMode
import io.github.aj4abinjacob.chikku.home.folder.FolderIconBinder
import io.github.aj4abinjacob.chikku.home.folder.FolderIconDrawable
import io.github.aj4abinjacob.chikku.platform.AppsPlatformSource
import io.github.aj4abinjacob.chikku.platform.LauncherAppsRepository
import io.github.aj4abinjacob.chikku.platform.RawActivity
import io.github.aj4abinjacob.chikku.platform.SourceProfile
import java.text.Collator
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Real app-observation sharing and HOME state, with synthetic platform data and virtual time. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestHomeApplication::class)
class HomeResumeStateTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val maps = app("Maps")
    private val transit = app("Transit")
    private val work = app("Work", profile = ProfileType.WORK)

    private class Source : AppsPlatformSource {
        var includeWork = true
        var enumerations = 0
        var registrations = 0
        var listener: ((String?) -> Unit)? = null

        override fun profiles(): List<SourceProfile> {
            enumerations++
            return buildList {
                add(SourceProfile(null, 0, ProfileType.PERSONAL))
                if (includeWork) add(SourceProfile(null, 10, ProfileType.WORK))
            }
        }

        override fun activities(profile: SourceProfile): List<RawActivity> =
            (if (profile.serial == 0L) listOf("Maps", "Transit") else listOf("Work")).map {
                RawActivity("pkg.${it.lowercase(Locale.ROOT)}", "pkg.${it.lowercase(Locale.ROOT)}.Main", it)
            }

        override fun observe(onEvent: (String?) -> Unit): AutoCloseable {
            registrations++
            listener = onEvent
            return AutoCloseable { registrations--; listener = null }
        }
    }

    private val workspace = WorkspaceState.Ready(Workspace(
        GridSpec(4, 5), GridSpec(5, 1), 1,
        listOf(
            WorkspaceItem("folder", CellPlacement(ContainerType.DESKTOP, 0, 0, 0), ItemTarget.Folder(
                "Travel", listOf(FolderEntry("maps", ItemTarget.App(maps.key)), FolderEntry("transit", ItemTarget.App(transit.key))),
            )),
            WorkspaceItem("work", CellPlacement(ContainerType.DESKTOP, 0, 1, 0), ItemTarget.App(work.key)),
        ),
    ))

    @Test
    fun returningAfterLongBackgroundKeepsResolvedHomeAndFolderCoverWithoutReloading() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val store = ViewModelStore()
        try {
            val source = Source()
            val apps = LauncherAppsRepository(source, backgroundScope, dispatcher, {}, { Collator.getInstance(Locale.US) }, 250)
            val fake = FakeHomeDependencies(appScope = backgroundScope, workspaceRepository = FakeWorkspaceRepository(workspace))
            fake.settingsRepository.state.value = fake.settingsRepository.state.value.let {
                it.copy(folders = it.folders.copy(covers = mapOf("folder" to FolderCover(FolderCoverMode.FIRST_APP))))
            }
            val deps = object : HomeDependencies by fake { override val appsRepository: AppsRepository = apps }
            val vm = HomeViewModel(deps, SavedStateHandle(), StartupGuard({ context.getSharedPreferences("resume_guard", 0) }, dispatcher), false, dispatcher, dispatcher)
            store.put("home", vm)
            runCurrent()
            assertThat(source.enumerations).isEqualTo(0) // Start only once HOME is first observed.
            val foreground = backgroundScope.launch { vm.uiState.collect {} }
            runCurrent()
            val before = vm.uiState.value
            assertThat(before.workspace).isInstanceOf(WorkspaceUi.Ready::class.java)

            val view = AppIconView(context, AppIconView.Style.WORKSPACE)
            val binder = FolderIconBinder(context, backgroundScope, FakeIconProvider())
            fun bind(state: HomeUiState) {
                val folder = (state.workspace as? WorkspaceUi.Ready)?.pages?.first()?.first()?.content as? ItemContent.Folder ?: return
                binder.bind(view, folder, state.settings.folders, "folder")
            }
            bind(before)
            runCurrent()
            val cover = view.icon
            assertThat(cover).isNotInstanceOf(FolderIconDrawable::class.java)

            foreground.cancel()
            advanceTimeBy(20_000) // Beyond both HOME's and the app repository's stop timeouts.
            runCurrent()
            val returned = mutableListOf<HomeUiState>()
            val returnCollector = backgroundScope.launch {
                vm.uiState.collect { returned += it; bind(it) }
            }
            runCurrent()
            assertThat(returned).containsExactly(before)
            assertThat(view.icon).isSameInstanceAs(cover)
            assertThat(source.enumerations).isEqualTo(1)
            assertThat(source.registrations).isEqualTo(1)
            assertThat(fake.workspaceRepository.totalSubscriptions).isEqualTo(1)
            advanceTimeBy(300)
            runCurrent()
            assertThat(returned).containsExactly(before)
            returnCollector.cancel()
        } finally {
            store.clear()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun backgroundProfileChangesStillInvalidateImmediatelyAndClearingHomeReleasesObservation() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val store = ViewModelStore()
        try {
            val source = Source()
            val apps = LauncherAppsRepository(source, backgroundScope, dispatcher, {}, { Collator.getInstance(Locale.US) }, 250)
            val fake = FakeHomeDependencies(appScope = backgroundScope, workspaceRepository = FakeWorkspaceRepository(workspace))
            val deps = object : HomeDependencies by fake { override val appsRepository: AppsRepository = apps }
            val vm = HomeViewModel(deps, SavedStateHandle(), StartupGuard({ context.getSharedPreferences("resume_guard", 0) }, dispatcher), false, dispatcher, dispatcher)
            store.put("home", vm)
            val foreground = backgroundScope.launch { vm.uiState.collect {} }
            runCurrent()
            assertThat((vm.uiState.value.drawer as DrawerUi.Apps).apps).contains(work)
            foreground.cancel()
            advanceTimeBy(20_000)
            runCurrent()

            assertThat(source.listener).isNotNull()
            source.includeWork = false
            source.listener!!.invoke(null)
            runCurrent()
            assertThat(vm.uiState.value.drawer).isEqualTo(DrawerUi.Loading)
            val pendingWork = (vm.uiState.value.workspace as WorkspaceUi.Ready).pages[0][1].content as ItemContent.App
            assertThat(pendingWork.label).isNull()
            assertThat(pendingWork.availability).isEqualTo(Availability.UNKNOWN)
            advanceTimeBy(251)
            runCurrent()
            assertThat((vm.uiState.value.drawer as DrawerUi.Apps).apps).containsExactly(maps, transit)
            val returned = mutableListOf<HomeUiState>()
            backgroundScope.launch { vm.uiState.toList(returned) }
            runCurrent()
            assertThat(returned).hasSize(1)
            assertThat((returned.single().drawer as DrawerUi.Apps).apps).doesNotContain(work)

            store.clear()
            advanceTimeBy(LauncherAppsRepository.STOP_TIMEOUT_MILLIS + 1)
            runCurrent()
            assertThat(source.registrations).isEqualTo(0)
            assertThat(fake.workspaceRepository.activeCollectors).isEqualTo(0)
        } finally {
            store.clear()
            runCurrent()
            Dispatchers.resetMain()
        }
    }
}
