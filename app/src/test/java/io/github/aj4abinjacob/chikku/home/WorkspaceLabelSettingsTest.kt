package io.github.aj4abinjacob.chikku.home

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.CellPlacement
import io.github.aj4abinjacob.chikku.core.model.ContainerType
import io.github.aj4abinjacob.chikku.core.model.GridSpec
import io.github.aj4abinjacob.chikku.core.model.settings.DockLabelFont
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestHomeApplication::class)
class WorkspaceLabelSettingsTest {
    private val context: Context = ContextThemeWrapper(
        ApplicationProvider.getApplicationContext<Context>(), R.style.Theme_Chikku_Home,
    )
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun homeStyleChangeUpdatesRenderedAppsAndFoldersWhileDockKeepsItsOwnStyle() {
        val pager = WorkspacePager(context)
        val dock = CellLayout(context)
        val actions = object : WorkspaceActions {
            override fun onItemClick(item: HomeItemUi, view: View) = Unit
            override fun onDrop(payload: DragPayload, target: CellPlacement) = Unit
            override fun onRemoveItem(itemId: String) = Unit
            override fun onAppInfo(key: AppKey, view: View) = Unit
        }
        val controller = WorkspaceController(pager, dock, PageIndicatorView(context), View(context),
            IconBinder(context, scope, FakeIconProvider()), actions)
        val app = ItemContent.App(AppKey("pkg", "pkg.Main", 0), "Long application label", false, Availability.AVAILABLE)
        controller.render(WorkspaceUi.Ready(
            GridSpec(5, 5), GridSpec(5, 1),
            listOf(listOf(HomeItemUi("home", CellPlacement(ContainerType.DESKTOP, 0, 0, 0), app),
                HomeItemUi("folder", CellPlacement(ContainerType.DESKTOP, 0, 1, 0), ItemContent.Folder("Tools", emptyList())))),
            listOf(HomeItemUi("dock", CellPlacement(ContainerType.DOCK, 0, 0, 0), app)),
        ))
        val homeView = pager.pageAt(0)!!.getChildAt(0) as AppIconView
        val dockView = dock.getChildAt(0) as AppIconView
        val folderView = pager.pageAt(0)!!.getChildAt(1) as AppIconView
        val initial = LauncherSettings()
        controller.applySettings(initial.copy(home = initial.home.copy(
            labelFont = DockLabelFont.LIGHT, labelShadow = false, labelSingleLine = false, labelScale = 1.4f,
        )))
        assertThat(pager.pageAt(0)!!.getChildAt(0)).isSameInstanceAs(homeView)
        assertThat(homeView.labelFont).isEqualTo(DockLabelFont.LIGHT)
        assertThat(homeView.labelShadow).isFalse()
        assertThat(homeView.labelSingleLine).isFalse()
        assertThat(homeView.labelScale).isEqualTo(1.4f)
        assertThat(folderView.labelFont).isEqualTo(DockLabelFont.LIGHT)
        assertThat(folderView.labelShadow).isFalse()
        assertThat(folderView.labelSingleLine).isFalse()
        assertThat(dockView.labelFont).isEqualTo(initial.dock.labelFont)
        assertThat(dockView.labelShadow).isEqualTo(initial.dock.labelShadow)
        assertThat(dockView.labelSingleLine).isEqualTo(initial.dock.labelSingleLine)
        controller.applySettings(initial)
        assertThat(homeView.labelFont).isEqualTo(DockLabelFont.NORMAL)
        assertThat(homeView.labelShadow).isTrue()
        assertThat(homeView.labelSingleLine).isTrue()
        assertThat(homeView.labelScale).isEqualTo(1f)
    }
}
