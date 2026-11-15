package io.github.aj4abinjacob.chikku.home.drawer

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.LauncherApp
import io.github.aj4abinjacob.chikku.core.model.ProfileType
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerFastScrollerSide
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerFastScrollerStyle
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerSettings
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.home.DrawerAdapter
import io.github.aj4abinjacob.chikku.home.DrawerItemListener
import io.github.aj4abinjacob.chikku.home.DrawerUi
import io.github.aj4abinjacob.chikku.home.IconBinder
import io.github.aj4abinjacob.chikku.platform.IconProvider
import io.github.aj4abinjacob.chikku.platform.LocaleSectionIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Native Android layout/input checks with an in-memory drawer and synthetic apps. No activity,
 * package inventory, settings, HOME role or saved placement is read or changed. These checks do
 * not establish on-screen finger feel or screenshot appearance.
 */
@RunWith(AndroidJUnit4::class)
class DrawerFastScrollerDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test
    fun unevenLettersTrackActualGridOnBothSidesAndReachBothEnds() {
        for (side in DrawerFastScrollerSide.entries) {
            lateinit var fixture: Fixture
            main { fixture = Fixture(side) }
            try {
                settle(fixture)
                main {
                    assertThat(fixture.list.adapter!!.itemCount).isEqualTo(fixture.apps.size)
                    assertThat(fixture.list.isVerticalScrollBarEnabled).isFalse()
                    fixture.touch(MotionEvent.ACTION_DOWN, 1f)
                    fixture.touch(MotionEvent.ACTION_MOVE, fixture.scroller.height * .5f)
                }
                settle(fixture)
                main {
                    fixture.assertPopupMatchesVisibleApp()
                    // Most apps start with A. Equal-sized letter buckets incorrectly select C here.
                    assertThat(fixture.scroller.popupLetter).isEqualTo("A")
                    val first = fixture.grid.findFirstVisibleItemPosition()
                    assertThat(first).isGreaterThan(40)
                    assertThat(first).isLessThan(110)
                    assertThat(fixture.scroller.thumbBounds.top).isGreaterThan(0)
                    fixture.touch(MotionEvent.ACTION_MOVE, fixture.scroller.height + 100f)
                }
                settle(fixture)
                main {
                    fixture.assertPopupMatchesVisibleApp()
                    assertThat(fixture.grid.findLastVisibleItemPosition()).isEqualTo(fixture.apps.lastIndex)
                    assertThat(fixture.list.canScrollVertically(1)).isFalse()
                    assertThat(fixture.scroller.thumbBounds.bottom).isEqualTo(fixture.scroller.height)
                    // The last row is deliberately incomplete; no letter headers or filler cells.
                    assertThat(fixture.list.adapter!!.itemCount).isEqualTo(163)
                    fixture.touch(MotionEvent.ACTION_MOVE, -100f)
                }
                settle(fixture)
                main {
                    assertThat(fixture.grid.findFirstVisibleItemPosition()).isEqualTo(0)
                    assertThat(fixture.list.canScrollVertically(-1)).isFalse()
                    assertThat(fixture.scroller.thumbBounds.top).isEqualTo(0)
                    fixture.assertPopupMatchesVisibleApp()
                    fixture.touch(MotionEvent.ACTION_CANCEL, 0f)
                    assertThat(fixture.scroller.popupLetter).isNull()
                }
            } finally {
                main { fixture.close() }
            }
        }
    }

    @Test
    fun mixedLetterRowKeepsItsCellsAndPopupUsesItsFirstVisibleApp() {
        lateinit var fixture: Fixture
        main { fixture = Fixture(DrawerFastScrollerSide.RIGHT) }
        try {
            settle(fixture)
            main {
                // Positions 140..143 contain A, B, C and Z in one compact row.
                fixture.grid.scrollToPositionWithOffset(140, 0)
            }
            settle(fixture)
            main {
                val views = (140..143).map { checkNotNull(fixture.grid.findViewByPosition(it)) }
                assertThat(views.map { it.top }.distinct()).hasSize(1)
                assertThat(fixture.apps.slice(140..143).map { it.label.first() })
                    .containsExactly('A', 'B', 'C', 'Z').inOrder()
                val before = fixture.grid.findFirstVisibleItemPosition()
                // A tap on the strip should leave the current list position intact.
                fixture.touch(MotionEvent.ACTION_DOWN, fixture.scroller.height * .25f)
                fixture.touch(MotionEvent.ACTION_UP, fixture.scroller.height * .25f)
                assertThat(fixture.grid.findFirstVisibleItemPosition()).isEqualTo(before)
                assertThat(fixture.scroller.popupLetter).isNull()
                val thumb = fixture.scroller.thumbBounds
                val grabbedY = thumb.top + thumb.height() / 3f
                fixture.touch(MotionEvent.ACTION_DOWN, grabbedY)
                fixture.touch(MotionEvent.ACTION_MOVE, grabbedY - 60f)
            }
            settle(fixture)
            main { fixture.assertPopupMatchesVisibleApp() }
        } finally {
            main { fixture.close() }
        }
    }

    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)

    private fun settle(fixture: Fixture) {
        repeat(4) {
            main { fixture.layout() }
            instrumentation.waitForIdleSync()
        }
    }

    private class Fixture(side: DrawerFastScrollerSide) {
        private val context = ContextThemeWrapper(
            InstrumentationRegistry.getInstrumentation().targetContext, R.style.Theme_Chikku_Home,
        )
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private val binder = IconBinder(context, scope, object : IconProvider {
            override suspend fun icon(key: AppKey) = ColorDrawable(Color.BLUE)
            override fun invalidate(packageName: String) = Unit
        })
        private val listener = object : DrawerItemListener {
            override fun onAppClick(app: LauncherApp, view: View) = Unit
            override fun onAppLongClick(app: LauncherApp, view: View) = false
            override fun onAddToHome(app: LauncherApp) = Unit
            override fun onAppInfo(app: LauncherApp, view: View) = Unit
        }
        val apps = (0 until 163).map { index ->
            val letter = when {
                index <= 140 -> "A"
                index == 141 -> "B"
                index == 142 -> "C"
                else -> "Z"
            }
            LauncherApp(AppKey("test.scroller", "App$index", 0), "$letter$index", ProfileType.PERSONAL)
        }
        private val drawer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        private val search = EditText(context)
        private val tabs = LinearLayout(context)
        private val tabsScroll = HorizontalScrollView(context).apply { addView(tabs) }
        private val status = TextView(context)
        private val statusContainer = LinearLayout(context).apply { addView(status) }
        val list = RecyclerView(context)
        private val pages = RecyclerView(context)
        val scroller = DrawerFastScroller(context)
        private val body = FrameLayout(context).apply {
            addView(list, FrameLayout.LayoutParams(-1, -1))
            addView(pages, FrameLayout.LayoutParams(-1, -1))
            addView(scroller, FrameLayout.LayoutParams(-2, -1))
        }
        private val dots = DrawerPageDots(context)
        private val controller: DrawerController
        val grid get() = list.layoutManager as GridLayoutManager
        private val downTime = SystemClock.uptimeMillis()

        init {
            drawer.addView(search)
            drawer.addView(statusContainer)
            drawer.addView(tabsScroll)
            drawer.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
            drawer.addView(dots)
            controller = DrawerController(
                DrawerViews(drawer, search, tabsScroll, tabs, statusContainer, status,
                    Button(context), list, pages, scroller, dots), binder, listener,
            )
            controller.render(DrawerUi.Apps(apps), "", LauncherSettings(drawer = DrawerSettings(
                columns = 4, rows = 4, automaticRowSpacing = false,
                showFastScroller = true, fastScrollerStyle = DrawerFastScrollerStyle.THUMB,
                fastScrollerSide = side, fastScrollerPopup = true,
            )))
        }

        fun layout() {
            drawer.measure(View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.EXACTLY))
            drawer.layout(0, 0, 720, 1200)
        }

        fun touch(action: Int, y: Float) {
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action,
                scroller.width / 2f, y, 0)
            try { scroller.dispatchTouchEvent(event) } finally { event.recycle() }
        }

        fun assertPopupMatchesVisibleApp() {
            val first = grid.findFirstVisibleItemPosition()
            assertThat(first).isAtLeast(0)
            val app = (list.adapter as DrawerAdapter).currentList[first]
            assertThat(scroller.popupLetter).isEqualTo(LocaleSectionIndex().sectionOf(app.label))
        }

        fun close() {
            touch(MotionEvent.ACTION_CANCEL, 0f)
            scope.cancel()
            list.adapter = null
        }
    }
}
