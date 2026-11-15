package io.github.aj4abinjacob.chikku.settings.drawer

import android.content.Context
import android.graphics.Color
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.CheckBox
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.LauncherApp
import io.github.aj4abinjacob.chikku.core.model.ProfileType
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerSettings
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerStyle
import io.github.aj4abinjacob.chikku.home.FakeIconProvider
import io.github.aj4abinjacob.chikku.home.AppIconView
import io.github.aj4abinjacob.chikku.home.IconBinder
import io.github.aj4abinjacob.chikku.home.app
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HiddenAppsGridViewTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val changes = mutableListOf<Pair<AppKey, Boolean>>()
    private val opened = mutableListOf<AppKey>()
    private val menuRequests = mutableListOf<HiddenAppMenuRequest>()
    private val view = HiddenAppsGridView(context, IconBinder(context, scope, FakeIconProvider()),
        onHiddenChanged = { key, value -> changes += key to value }, onOpen = opened::add, onLongPress = menuRequests::add)

    @After
    fun tearDown() {
        view.release()
        scope.cancel()
    }

    private fun render(apps: List<LauncherApp>, hidden: Set<AppKey> = emptySet(), settings: DrawerSettings = DrawerSettings()) {
        view.render(apps, hidden, settings, Color.BLACK, Color.BLUE)
        val deadline = System.currentTimeMillis() + 5_000
        do {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
            check(System.currentTimeMillis() < deadline)
        } while (view.list.adapter!!.itemCount != apps.size || currentHiddenPositions() != apps.indices.filter { apps[it].key in hidden }.toSet())
        layout()
    }

    // Read accessibility state rather than relying on the adapter's private data.
    private fun currentHiddenPositions(): Set<Int> {
        @Suppress("UNCHECKED_CAST")
        val adapter = view.list.adapter as androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>
        return (0 until adapter.itemCount).filter { position ->
            val holder = adapter.createViewHolder(view.list, adapter.getItemViewType(position))
            adapter.bindViewHolder(holder, position)
            val checked = holder.itemView.createAccessibilityNodeInfo().isChecked
            adapter.onViewRecycled(holder)
            checked
        }.toSet()
    }

    private var fixtureApps = emptyList<LauncherApp>()

    private fun layout() {
        view.measure(View.MeasureSpec.makeMeasureSpec(700, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 700, 500)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun multiSelectionKeepsAppsVisibleAndUsesComponentAndProfileIdentity() {
        val personal = app("Camera")
        val work = personal.copy(key = personal.key.copy(userSerial = 10), profileType = ProfileType.WORK)
        val secondActivity = personal.copy(key = personal.key.copy(className = "pkg.camera.Second"), label = "Camera 2")
        fixtureApps = listOf(personal, work, secondActivity)
        render(fixtureApps)
        view.list.findViewHolderForAdapterPosition(0)!!.itemView.performClick()
        view.list.findViewHolderForAdapterPosition(1)!!.itemView.performClick()
        assertThat(changes).containsExactly(personal.key to true, work.key to true).inOrder()
        assertThat(opened).isEmpty()
        render(fixtureApps, setOf(personal.key, work.key))
        assertThat(view.list.adapter!!.itemCount).isEqualTo(3)
        val selected = view.list.findViewHolderForAdapterPosition(0)!!.itemView
        assertThat(selected.createAccessibilityNodeInfo().className.toString()).isEqualTo(CheckBox::class.java.name)
        assertThat(selected.createAccessibilityNodeInfo().isCheckable).isTrue()
        selected.performClick()
        assertThat(changes.last()).isEqualTo(personal.key to false)
    }

    @Test
    fun tappedCellShowsItsNewStateAtOnceAndReturnsIfTheStoreNeverConfirms() {
        val camera = app("Camera")
        fixtureApps = listOf(camera)
        render(fixtureApps)
        val cell = view.list.findViewHolderForAdapterPosition(0)!!.itemView
        cell.performClick()
        assertThat(changes).containsExactly(camera.key to true)
        assertThat(cell.createAccessibilityNodeInfo().isChecked).isTrue()
        // No render with the stored change arrives: the mark must not stay lying.
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(3))
        layout()
        assertThat(view.list.findViewHolderForAdapterPosition(0)!!.itemView.createAccessibilityNodeInfo().isChecked).isFalse()
    }

    @Test
    fun drawerIconsKeepCustomLabelsAndAccessibleOpenWithoutLaunchingOnSelection() {
        val camera = app("Camera")
        view.render(listOf(camera), setOf(camera.key), DrawerSettings(iconScale = 1.2f, showLabels = false),
            Color.BLACK, Color.BLUE, mapOf(camera.key to "My camera"))
        layout()
        val cell = view.list.findViewHolderForAdapterPosition(0)!!.itemView as android.view.ViewGroup
        val icon = cell.getChildAt(0) as AppIconView
        assertThat(icon.label).isEqualTo("My camera")
        assertThat(icon.showLabel).isTrue()
        assertThat(icon.iconScale).isEqualTo(1.2f)
        val open = cell.createAccessibilityNodeInfo().actionList.single { it.label?.toString() == "Open" }
        cell.performAccessibilityAction(open.id, null)
        assertThat(opened).containsExactly(camera.key)
    }

    @Test
    fun longPressAsksForChikkuMenuAnchoredAtTheCellWithoutOpeningOrToggling() {
        val camera = app("Camera")
        val clock = app("Clock")
        view.render(listOf(camera, clock), emptySet(), DrawerSettings(columns = 2), Color.BLACK, Color.BLUE, mapOf(clock.key to "My clock"))
        layout()
        val cell = view.list.findViewHolderForAdapterPosition(1)!!.itemView
        assertThat(cell.performLongClick()).isTrue()
        val request = menuRequests.single()
        assertThat(request.key).isEqualTo(clock.key)
        assertThat(request.label).isEqualTo("My clock")
        val expected = android.graphics.Rect(0, 0, cell.width, cell.height)
        view.offsetDescendantRectToMyCoords(cell, expected)
        assertThat(request.anchor).isEqualTo(expected)
        assertThat(request.anchor.width()).isGreaterThan(0)
        assertThat(opened).isEmpty()
        assertThat(changes).isEmpty()
    }

    @Test
    fun letterTapAndDragJumpAcrossTheGridEvenWithPagedDrawerAndRailDisabled() {
        fixtureApps = ('A'..'Z').flatMap { letter -> (1..12).map { app("$letter$it") } }
        render(fixtureApps, settings = DrawerSettings(style = DrawerStyle.HORIZONTAL_PAGED, showFastScroller = false))
        assertThat(view.fastScroller.visibility).isEqualTo(View.VISIBLE)
        view.fastScroller.getChildAt(25).performClick()
        layout()
        assertThat(view.list.findViewHolderForAdapterPosition(300)).isNotNull()
        view.fastScroller.onTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 10f, 0f, 0))
        layout()
        assertThat(view.grid.findFirstVisibleItemPosition()).isEqualTo(0)
        view.fastScroller.onTouchEvent(MotionEvent.obtain(0, 1, MotionEvent.ACTION_MOVE, 10f, view.fastScroller.height.toFloat() - 1, 0))
        layout()
        assertThat(view.list.findViewHolderForAdapterPosition(300)).isNotNull()
    }

    @Test
    fun emptyAndSingleBucketListsHaveNoRailAndNarrowRtlGridKeepsTouchTargets() {
        render(emptyList())
        assertThat(view.fastScroller.visibility).isEqualTo(View.GONE)
        fixtureApps = listOf(app("Alpha"), app("Also"))
        render(fixtureApps, settings = DrawerSettings(columns = 12))
        view.layoutDirection = View.LAYOUT_DIRECTION_RTL
        view.measure(View.MeasureSpec.makeMeasureSpec(180, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 180, 500)
        assertThat(view.fastScroller.visibility).isEqualTo(View.GONE)
        assertThat(view.grid.spanCount).isAtMost(3)
    }
}
