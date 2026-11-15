package io.github.aj4abinjacob.chikku.home

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.DrawerGroup
import io.github.aj4abinjacob.chikku.core.model.LauncherApp
import io.github.aj4abinjacob.chikku.core.model.drawer.DrawerSection
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerFastScrollerSide
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerFastScrollerStyle
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerSearchBarPosition
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerSearchBarStyle
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerSettings
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerStyle
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerTabBarPosition
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerTransition
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.home.drawer.DrawerController
import io.github.aj4abinjacob.chikku.home.drawer.DrawerFastScroller
import io.github.aj4abinjacob.chikku.home.drawer.DrawerPageDots
import io.github.aj4abinjacob.chikku.home.drawer.DrawerPagesAdapter
import io.github.aj4abinjacob.chikku.home.drawer.DrawerViews
import kotlin.math.roundToInt
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
@Config(sdk = [36], application = TestHomeApplication::class)
class DrawerCustomizationTest {
    private val context: Context = ContextThemeWrapper(ApplicationProvider.getApplicationContext<Context>(), R.style.Theme_Chikku_Home)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val binder = IconBinder(context, scope, FakeIconProvider())
    private val listener = object : DrawerItemListener {
        override fun onAppClick(app: LauncherApp, view: View) = Unit
        override fun onAppLongClick(app: LauncherApp, view: View) = false
        override fun onAddToHome(app: LauncherApp) = Unit
        override fun onAppInfo(app: LauncherApp, view: View) = Unit
    }
    private val apps = (0 until 50).map { app("App%02d".format(it)) }

    @After
    fun tearDown() = scope.cancel()

    private fun layout(view: View, height: Int = 500) {
        view.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 1080, height)
    }

    private fun idleUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline)
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
    }

    private inner class Fixture {
        val drawer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val search = EditText(context)
        val tabs = LinearLayout(context)
        val tabsScroll = HorizontalScrollView(context).apply { addView(tabs) }
        val status = TextView(context)
        val statusContainer = LinearLayout(context).apply { addView(status) }
        val list = RecyclerView(context)
        val pages = io.github.aj4abinjacob.chikku.home.drawer.HorizontalDrawerRecyclerView(context)
        val body = FrameLayout(context).apply { addView(list); addView(pages) }
        val scroller = DrawerFastScroller(context).also {
            body.addView(it, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT, Gravity.RIGHT))
        }
        val dots = DrawerPageDots(context)
        val controller: DrawerController
        init {
            drawer.addView(search)
            drawer.addView(statusContainer)
            drawer.addView(tabsScroll)
            drawer.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
            drawer.addView(dots)
            controller = DrawerController(DrawerViews(drawer, search, tabsScroll, tabs, statusContainer, status,
                Button(context), list, pages, scroller, dots), binder, listener)
        }
        fun render(settings: DrawerSettings, query: String = "", shown: List<LauncherApp> = apps) {
            controller.render(DrawerUi.Apps(shown), query, LauncherSettings(drawer = settings))
            if (settings.style != DrawerStyle.HORIZONTAL_PAGED || query.isNotBlank()) idleUntil { list.adapter?.itemCount == shown.size }
        }
    }

    @Test
    fun staleNoMatchesIsNotShownForADifferentQuery() {
        val f = Fixture()
        f.controller.render(DrawerUi.NoMatches, "xyz", LauncherSettings(), resultCurrent = true)
        shadowOf(Looper.getMainLooper()).idle()
        assertThat(f.statusContainer.visibility).isEqualTo(View.VISIBLE)
        assertThat(f.status.text.toString()).isEqualTo(context.getString(R.string.home_drawer_no_matches))
        // Backspace: the field says "xy" but the content is still the result for "xyz".
        f.controller.render(DrawerUi.NoMatches, "xyz", LauncherSettings(), resultCurrent = false)
        shadowOf(Looper.getMainLooper()).idle()
        assertThat(f.statusContainer.visibility).isNotEqualTo(View.VISIBLE)
    }

    @Test
    fun pendingFlagFlipOnlyTouchesTheStatusLine() {
        val f = Fixture()
        f.render(DrawerSettings())
        val adapterItems = f.list.adapter?.itemCount
        f.controller.render(DrawerUi.NoMatches, "xyz", LauncherSettings(), resultCurrent = false)
        assertThat(f.statusContainer.visibility).isNotEqualTo(View.VISIBLE)
        val childBefore = f.scroller.childCount

        // Same content, results now current: the "no matches" message appears without a rebuild.
        f.controller.render(DrawerUi.NoMatches, "xyz", LauncherSettings(), resultCurrent = true)
        idleUntil { f.statusContainer.visibility == View.VISIBLE }
        assertThat(f.status.text.toString()).isEqualTo(context.getString(R.string.home_drawer_no_matches))
        assertThat(f.scroller.childCount).isEqualTo(childBefore)

        // Flipping back hides it again and keeps the displayed content.
        f.controller.render(DrawerUi.NoMatches, "xyz", LauncherSettings(), resultCurrent = false)
        idleUntil { f.statusContainer.visibility != View.VISIBLE }
        assertThat(adapterItems).isEqualTo(50)
    }

    @Test
    fun listStyleRendersAccessibleRowsAndReturnsToGridWithoutLosingApps() {
        val f = Fixture()
        f.render(DrawerSettings(style = DrawerStyle.LIST, columns = 5))
        layout(f.list)
        assertThat((f.list.layoutManager as GridLayoutManager).spanCount).isEqualTo(1)
        assertThat((f.list.getChildAt(0) as AppIconView).listMode).isTrue()
        assertThat(f.list.getChildAt(0).contentDescription).isEqualTo(apps.first().label)
        f.render(DrawerSettings(style = DrawerStyle.VERTICAL, columns = 5))
        layout(f.list)
        assertThat((f.list.layoutManager as GridLayoutManager).spanCount).isEqualTo(5)
        assertThat((f.list.getChildAt(0) as AppIconView).listMode).isFalse()
    }

    private fun listGroups(list: RecyclerView) = (0 until list.itemDecorationCount)
        .map(list::getItemDecorationAt).filterIsInstance<io.github.aj4abinjacob.chikku.home.drawer.DrawerListGroups>().single()

    @Test
    fun listStyleMarksEachLetterGroupOnceAndIndentsRowsForTheLetters() {
        val f = Fixture()
        val shown = listOf(app("Airbnb"), app("Amazon"), app("Bank"), app("Camera"), app("Clock"))
        f.render(DrawerSettings(style = DrawerStyle.LIST), shown = shown)
        settleLayout(f.list)
        assertThat(listGroups(f.list).shownLetters).containsExactly(0, "A", 2, "B", 3, "C")
        val indented = f.list.getChildAt(0).left
        assertThat(indented - f.list.paddingLeft).isEqualTo(context.resources.getDimensionPixelSize(R.dimen.drawer_list_section_gutter))

        // Ranked search results and the grid carry no letters or indent.
        f.render(DrawerSettings(style = DrawerStyle.LIST), query = "a", shown = shown)
        settleLayout(f.list)
        assertThat(listGroups(f.list).shownLetters).isEmpty()
        assertThat(f.list.getChildAt(0).left).isEqualTo(f.list.paddingLeft)
        f.render(DrawerSettings(style = DrawerStyle.VERTICAL), shown = shown)
        settleLayout(f.list)
        assertThat(listGroups(f.list).shownLetters).isEmpty()
    }

    @Test
    fun listPinsTheTopGroupLetterUntilTheNextGroupPushesItUp() {
        val f = Fixture()
        val shown = "ABC".flatMap { letter -> (1..10).map { app("$letter app %02d".format(it)) } }
        f.render(DrawerSettings(style = DrawerStyle.LIST), shown = shown)
        settleLayout(f.list)
        val groups = listGroups(f.list)
        val row = f.list.getChildAt(0).height
        fun topLetter() = shown[(f.list.layoutManager as GridLayoutManager).findFirstVisibleItemPosition()].label.take(1)

        // At the top the letter sits level with the group's first row.
        assertThat(groups.pinned(f.list)!!.second).isWithin(1f).of(f.list.paddingTop + row / 2f)

        // Midway through A, with the top row half gone: A rests at the top edge and agrees with
        // the bubble's top row.
        f.list.scrollBy(0, row * 4 + row / 2)
        settleLayout(f.list)
        val (letter, resting) = groups.pinned(f.list)!!
        assertThat(letter).isEqualTo("A")
        assertThat(letter).isEqualTo(topLetter())
        assertThat(resting).isAtLeast(f.list.paddingTop.toFloat())
        assertThat(resting).isLessThan(f.list.paddingTop + row / 2f)

        // B's first row is almost at the top: it has pushed A above its resting place.
        f.list.scrollBy(0, row * 5 + row * 4 / 10)
        settleLayout(f.list)
        assertThat(groups.pinned(f.list)!!.first).isEqualTo("A")
        assertThat(groups.pinned(f.list)!!.second).isLessThan(resting)

        // Into B: B is pinned, matching the top row again.
        f.list.scrollBy(0, row)
        settleLayout(f.list)
        assertThat(groups.pinned(f.list)!!.first).isEqualTo("B")
        assertThat(groups.pinned(f.list)!!.first).isEqualTo(topLetter())
    }

    @Test
    fun listThumbBubbleNamesTheRowsBesideItAndLeavesThemInPlace() {
        val f = Fixture()
        val shown = "ABCDEF".flatMap { letter -> (1..10).map { app("$letter app %02d".format(it)) } }
        f.render(DrawerSettings(style = DrawerStyle.LIST, showFastScroller = true,
            fastScrollerStyle = DrawerFastScrollerStyle.THUMB), shown = shown)
        settleLayout(f.body, height = 1600)
        val row = f.list.getChildAt(0).height
        // The bubble beside the 25th row names C, not the top row's A.
        val y = row * 24 + row / 2f
        assertThat(f.scroller.letterBeside!!(y)).isEqualTo("C")
        assertThat(shown[(f.list.layoutManager as GridLayoutManager).findFirstVisibleItemPosition()].label.take(1)).isEqualTo("A")
        // List rows stay in place beside the bubble: their labels start at the far side.
        f.scroller.onBubbleMoved?.invoke(android.graphics.Rect(f.list.width - 200, row * 24, f.list.width, row * 26))
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1))
        assertThat((0 until f.list.childCount).all { f.list.getChildAt(it).translationX == 0f && f.list.getChildAt(it).scaleX == 1f }).isTrue()
    }

    @Test
    fun listRowsReadByNameWithALargerLabelAndSmallerIconThanGridCells() {
        val grid = AppIconView.desiredHeight(context, AppIconView.Style.DRAWER)
        val list = AppIconView.desiredHeight(context, AppIconView.Style.DRAWER, listMode = true)
        assertThat(list).isLessThan(grid)
        assertThat(list).isAtLeast(context.resources.getDimensionPixelSize(R.dimen.home_min_touch_target))
        val f = Fixture()
        f.render(DrawerSettings(style = DrawerStyle.LIST))
        settleLayout(f.list)
        assertThat(f.list.getChildAt(0).height).isEqualTo(list)
    }

    @Test
    fun alphabetRailBulgesTowardTheListUnderTheFingerAndSettlesOnRelease() {
        val f = Fixture()
        val shown = ('A'..'Z').map { app("$it app") }
        f.render(DrawerSettings(showFastScroller = true), shown = shown)
        layout(f.body, height = 1300)
        fun touch(action: Int, y: Float) = MotionEvent.obtain(0, 0, action, 20f, y, 0).also { f.scroller.onTouchEvent(it); it.recycle() }
        val middle = f.scroller.getChildAt(13)
        val y = middle.top + middle.height / 2f
        touch(MotionEvent.ACTION_DOWN, y)
        // Right-hand rail: letters move left, the one under the finger the furthest; distant ones stay.
        assertThat(middle.translationX).isLessThan(0f)
        assertThat(middle.scaleX).isGreaterThan(1f)
        assertThat(f.scroller.getChildAt(12).translationX).isGreaterThan(middle.translationX)
        assertThat(f.scroller.getChildAt(0).translationX).isEqualTo(0f)
        assertThat(f.body.clipChildren).isFalse()
        touch(MotionEvent.ACTION_UP, y)
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1))
        assertThat((0 until f.scroller.childCount).all { f.scroller.getChildAt(it).translationX == 0f && f.scroller.getChildAt(it).scaleX == 1f }).isTrue()
        // The wave only unclipped the parent while it was out; it is restored afterwards.
        assertThat(f.body.clipChildren).isTrue()
    }

    @Test
    fun listRowsKeepContentHeightWhenRowsAreFitted() {
        val f = Fixture()
        f.render(DrawerSettings(style = DrawerStyle.LIST, rows = 4, automaticRowSpacing = false))
        settleLayout(f.list)
        assertThat((f.list.adapter as DrawerAdapter).rowHeightPx).isEqualTo(ViewGroup.LayoutParams.WRAP_CONTENT)
        val first = f.list.getChildAt(0)
        assertThat(first.height).isLessThan(f.list.height / 4)
        assertThat(f.list.getChildAt(1).top - first.top).isEqualTo(first.height)
        assertThat(f.list.adapter?.itemCount).isEqualTo(apps.size)
    }

    @Test
    fun gridThumbBubbleNamesTheRowBesideItByItsLeadingApp() {
        val f = Fixture()
        // Mixed rows: each row of 4 starts with a different letter from the one before it.
        val shown = ('A'..'Z').flatMap { letter -> (1..3).map { app("$letter app $it") } }
        f.render(DrawerSettings(columns = 4, showFastScroller = true, fastScrollerStyle = DrawerFastScrollerStyle.THUMB), shown = shown)
        settleLayout(f.body, height = 1600)
        val row = f.list.getChildAt(0).height
        // Row 6 holds apps 20..23: its leading app is G's third, so the bubble says G, not A.
        assertThat(f.scroller.letterBeside!!(row * 5 + row / 2f)).isEqualTo(shown[20].label.take(1))
        assertThat(shown[20].label.take(1)).isEqualTo("G")
        assertThat(f.scroller.letterBeside!!(-1f)).isNull()
    }

    private fun settleLayout(view: View, height: Int = 1000) {
        repeat(3) {
            layout(view, height)
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    @Test
    fun rowCountChangesVerticalSpacingInGridWithoutLosingApps() {
        val f = Fixture()
        val automaticAnimator = f.list.itemAnimator
        val settings = DrawerSettings(columns = 2, rows = 4, automaticRowSpacing = false)
        f.render(settings)
        assertThat(f.list.itemAnimator).isNull()
        settleLayout(f.list)
        val first = f.list.getChildAt(0)
        val nextRow = f.list.getChildAt(2)
        val originalHeight = first.height
        assertThat(originalHeight).isAtLeast(AppIconView.desiredHeight(context, AppIconView.Style.DRAWER))
        assertThat(nextRow.top - first.top).isEqualTo(originalHeight)
        f.render(settings.copy(rows = 5))
        settleLayout(f.list)
        val denserHeight = (f.list.adapter as DrawerAdapter).rowHeightPx
        assertThat(denserHeight).isLessThan(originalHeight)
        assertThat(f.list.getChildAt(0).layoutParams.height).isEqualTo(denserHeight)
        assertThat(f.list.getChildAt(0).height).isEqualTo(denserHeight)
        assertThat(f.list.adapter?.itemCount).isEqualTo(apps.size)
        f.list.getChildAt(0).performClick()
        assertThat(f.list.getChildAt(0).contentDescription).isEqualTo(apps.first().label)
        f.render(settings.copy(automaticRowSpacing = true))
        assertThat(f.list.itemAnimator).isSameInstanceAs(automaticAnimator)
        settleLayout(f.list)
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(500))
        settleLayout(f.list)
        assertThat((f.list.adapter as DrawerAdapter).rowHeightPx).isEqualTo(ViewGroup.LayoutParams.WRAP_CONTENT)
        assertThat(f.list.getChildAt(0).height).isLessThan(denserHeight)
    }

    @Test
    fun rowSpacingRecalculatesForResizingAndBottomInsets() {
        val f = Fixture()
        f.render(DrawerSettings(columns = 2, rows = 4, automaticRowSpacing = false))
        settleLayout(f.list)
        val originalHeight = f.list.getChildAt(0).height
        f.controller.setBottomInset(200)
        settleLayout(f.list)
        val insetHeight = (f.list.adapter as DrawerAdapter).rowHeightPx
        assertThat(insetHeight).isLessThan(originalHeight)
        assertThat(f.list.getChildAt(0).layoutParams.height).isEqualTo(insetHeight)
        assertThat(f.list.getChildAt(0).height).isEqualTo(insetHeight)
        settleLayout(f.list, 1200)
        assertThat(f.list.getChildAt(0).height).isEqualTo(originalHeight)
        f.controller.setBottomInset(0)
        settleLayout(f.list, 1200)
        assertThat(f.list.getChildAt(0).height).isGreaterThan(originalHeight)
    }

    @Test
    fun keyboardDoesNotSqueezeRowSpacing() {
        val f = Fixture()
        f.render(DrawerSettings(columns = 2, rows = 4, automaticRowSpacing = false))
        settleLayout(f.list, 1200)
        val originalHeight = f.list.getChildAt(0).height
        f.controller.setBottomInset(600, keyboard = 600)
        settleLayout(f.list, 1200)
        assertThat(f.list.paddingBottom).isEqualTo(600)
        assertThat(f.list.getChildAt(0).height).isEqualTo(originalHeight)
    }

    @Test
    fun automaticSpacingRestoresContentHeightAndPagedSearchKeepsContentRows() {
        val f = Fixture()
        val settings = DrawerSettings(columns = 2, rows = 4, automaticRowSpacing = false)
        f.render(settings)
        settleLayout(f.list)
        f.render(settings.copy(automaticRowSpacing = true))
        settleLayout(f.list)
        assertThat(f.list.getChildAt(0).height).isEqualTo(AppIconView.desiredHeight(context, AppIconView.Style.DRAWER))
        f.render(settings.copy(style = DrawerStyle.HORIZONTAL_PAGED), query = "App")
        settleLayout(f.list)
        assertThat(f.list.getChildAt(0).height).isEqualTo(AppIconView.desiredHeight(context, AppIconView.Style.DRAWER))
    }

    @Test
    fun denseRowsPreserveLargeIconsMultilineLabelsAndTouchTargets() {
        val f = Fixture()
        val settings = DrawerSettings(columns = 2, rows = 12, automaticRowSpacing = false,
            iconScale = 1.5f, labelScale = 1.5f, labelSingleLine = false)
        f.render(settings)
        settleLayout(f.list, 300)
        val minimum = AppIconView.desiredHeight(context, AppIconView.Style.DRAWER,
            settings.iconScale, settings.labelScale, true, false)
            .coerceAtLeast(context.resources.getDimensionPixelSize(R.dimen.home_min_touch_target))
        assertThat(f.list.getChildAt(0).height).isAtLeast(minimum)
        assertThat(f.list.getChildAt(2).top).isAtLeast(f.list.getChildAt(0).bottom)
        assertThat(f.list.canScrollVertically(1)).isTrue()
    }

    @Test
    fun closeResetsOrRemembersVerticalScrollAccordingToPreference() {
        for (remember in listOf(false, true)) {
            val f = Fixture()
            f.render(DrawerSettings(style = DrawerStyle.LIST, rememberScrollPosition = remember))
            layout(f.list)
            val manager = f.list.layoutManager as GridLayoutManager
            manager.scrollToPositionWithOffset(20, 0)
            layout(f.list)
            assertThat(manager.findFirstVisibleItemPosition()).isEqualTo(20)
            f.controller.onClosed()
            layout(f.list)
            assertThat(manager.findFirstVisibleItemPosition()).isEqualTo(if (remember) 20 else 0)
        }
    }

    @Test
    fun searchingStartsAtBestMatchThenReturnsToUnfilteredPosition() {
        val f = Fixture()
        val settings = DrawerSettings(style = DrawerStyle.LIST, rememberScrollPosition = true)
        f.render(settings)
        layout(f.list)
        val manager = f.list.layoutManager as GridLayoutManager
        manager.scrollToPositionWithOffset(20, 0)
        layout(f.list)
        f.render(settings, "App01", listOf(apps[1]))
        layout(f.list)
        assertThat(manager.findFirstVisibleItemPosition()).isEqualTo(0)
        f.render(settings)
        layout(f.list)
        assertThat(manager.findFirstVisibleItemPosition()).isEqualTo(20)
    }

    @Test
    fun bottomTabsStayAboveBottomSearchAndRestoreToTop() {
        val f = Fixture()
        f.controller.setGroups(listOf(DrawerGroup("tools", "Tools", 0, members = listOf(apps.first().key))))
        f.render(DrawerSettings(groupsEnabled = true, tabBarPosition = DrawerTabBarPosition.BOTTOM,
            searchBarPosition = DrawerSearchBarPosition.BOTTOM))
        assertThat(f.drawer.indexOfChild(f.tabsScroll)).isGreaterThan(f.drawer.indexOfChild(f.body))
        assertThat(f.drawer.indexOfChild(f.search)).isEqualTo(f.drawer.childCount - 1)
        f.render(DrawerSettings(groupsEnabled = true))
        assertThat(f.drawer.indexOfChild(f.search)).isEqualTo(0)
        assertThat(f.drawer.indexOfChild(f.tabsScroll)).isLessThan(f.drawer.indexOfChild(f.body))
    }

    @Test
    fun searchBarStyleControlsTheOutline() {
        val f = Fixture()
        fun strokeWidth() = shadowOf(f.search.background as GradientDrawable).strokeWidth
        f.render(DrawerSettings())
        assertThat(strokeWidth()).isAtMost(0) // subtle: fill only, like Material 3's search bar
        f.render(DrawerSettings(searchBarStyle = DrawerSearchBarStyle.OUTLINED))
        val light = shadowOf(f.search.background as GradientDrawable).strokeColor
        assertThat(strokeWidth()).isGreaterThan(0)
        f.render(DrawerSettings(searchBarStyle = DrawerSearchBarStyle.HIGH_CONTRAST))
        val strong = shadowOf(f.search.background as GradientDrawable).strokeColor
        assertThat(strokeWidth()).isGreaterThan(0)
        assertThat(Color.alpha(strong)).isGreaterThan(Color.alpha(light))
    }

    @Test
    fun emptyWorkTabKeepsTopNavigationAboveItsStatus() {
        val f = Fixture()
        f.render(DrawerSettings(showWorkTab = true,
            tabStyle = io.github.aj4abinjacob.chikku.core.model.settings.DrawerTabStyle.SEGMENTED,
            searchBarPosition = DrawerSearchBarPosition.HIDDEN))
        f.tabs.getChildAt(1).performClick()
        assertThat(f.statusContainer.visibility).isEqualTo(View.VISIBLE)
        assertThat(f.drawer.indexOfChild(f.tabsScroll)).isLessThan(f.drawer.indexOfChild(f.statusContainer))
        f.tabs.getChildAt(0).performClick()
        assertThat(f.statusContainer.visibility).isEqualTo(View.GONE)
    }

    @Test
    fun horizontalPageControlsDriveRealTransformsIndicatorsAndReducedMotion() {
        val f = Fixture()
        val preferences = DrawerSettings(style = DrawerStyle.HORIZONTAL_PAGED, columns = 2, rows = 2,
            pageTransitionEffect = io.github.aj4abinjacob.chikku.core.model.settings.PageTransitionEffect.CUBE,
            pageEffectStrength = .5f, pageSnapDurationMs = 410,
            pageSwipeSensitivity = io.github.aj4abinjacob.chikku.core.model.settings.PageSwipeSensitivity.LOW,
            pageIndicatorStyle = io.github.aj4abinjacob.chikku.core.model.settings.PageIndicatorStyle.LINE)
        f.render(preferences)
        settleLayout(f.drawer, 1000)
        f.pages.scrollBy(f.pages.width / 3, 0)
        assertThat(f.pages.getChildAt(0).rotationY).isNotEqualTo(0f)
        assertThat(f.dots.scrollPosition % 1f).isNotEqualTo(0f)
        assertThat(f.dots.style).isEqualTo(preferences.pageIndicatorStyle)
        assertThat(f.pages.snapDurationMs).isEqualTo(410)
        assertThat(f.pages.swipeSensitivity).isEqualTo(preferences.pageSwipeSensitivity)
        val motionOff = LauncherSettings(drawer = preferences,
            lookFeel = io.github.aj4abinjacob.chikku.core.model.settings.LookFeelSettings(animationSpeed = io.github.aj4abinjacob.chikku.core.model.settings.AnimationSpeed.NONE))
        f.controller.render(DrawerUi.Apps(apps), "", motionOff)
        assertThat(f.pages.animationScale).isEqualTo(0f)
        for (index in 0 until f.pages.childCount) {
            assertThat(f.pages.getChildAt(index).rotationY).isEqualTo(0f)
            assertThat(f.pages.getChildAt(index).scaleX).isEqualTo(1f)
            assertThat(f.pages.getChildAt(index).alpha).isEqualTo(1f)
        }
        f.dots.onPageSelected!!.invoke(3)
        settleLayout(f.drawer, 1000)
        assertThat(f.dots.contentDescription.toString()).contains("4")
    }

    @Test
    fun infinitePagesWrapBothEdgesAndSinglePageDoesNotDuplicate() {
        val adapter = DrawerPagesAdapter(binder, listener)
        adapter.submit(listOf(apps.take(2), apps.drop(2).take(2), apps.drop(4).take(2)), 2, 1, DrawerAppearance(), true)
        assertThat(adapter.itemCount).isEqualTo(5)
        assertThat(adapter.logicalPage(0)).isEqualTo(2)
        assertThat(adapter.normalizedPosition(0)).isEqualTo(3)
        assertThat(adapter.logicalPage(4)).isEqualTo(0)
        assertThat(adapter.normalizedPosition(4)).isEqualTo(1)
        adapter.submit(listOf(apps.take(2)), 2, 1, DrawerAppearance(), true)
        assertThat(adapter.itemCount).isEqualTo(1)
        assertThat(adapter.adapterPosition(0)).isEqualTo(0)
        adapter.submit(emptyList(), 2, 1, DrawerAppearance(), true)
        assertThat(adapter.itemCount).isEqualTo(0)
        assertThat(adapter.logicalPage(100)).isEqualTo(0)
    }

    @Test
    fun drawerSearchAndVerticalStyleReclaimTheIndicatorHeight() {
        val f = Fixture()
        val settings = DrawerSettings(style = DrawerStyle.HORIZONTAL_PAGED, columns = 2, rows = 2)
        f.render(settings)
        settleLayout(f.drawer)
        assertThat(f.dots.visibility).isEqualTo(View.VISIBLE)
        val pagedBodyHeight = f.body.height
        val indicatorHeight = f.dots.height

        f.render(settings, query = "App")
        settleLayout(f.drawer)
        assertThat(f.dots.visibility).isEqualTo(View.GONE)
        assertThat(f.body.height).isEqualTo(pagedBodyHeight + indicatorHeight)

        f.render(settings)
        settleLayout(f.drawer)
        assertThat(f.dots.visibility).isEqualTo(View.VISIBLE)
        assertThat(f.body.height).isEqualTo(pagedBodyHeight)

        f.render(settings.copy(style = DrawerStyle.VERTICAL))
        settleLayout(f.drawer)
        assertThat(f.dots.visibility).isEqualTo(View.GONE)
        assertThat(f.body.height).isEqualTo(pagedBodyHeight + indicatorHeight)
    }

    @Test
    fun singlePageDrawerDoesNotReserveIndicatorHeight() {
        val f = Fixture()
        val settings = DrawerSettings(style = DrawerStyle.HORIZONTAL_PAGED, columns = 2, rows = 2)
        f.render(settings)
        settleLayout(f.drawer)
        val pagedBodyHeight = f.body.height
        val indicatorHeight = f.dots.height
        f.render(settings, shown = apps.take(2))
        settleLayout(f.drawer)
        assertThat(f.dots.visibility).isEqualTo(View.GONE)
        assertThat(f.body.height).isEqualTo(pagedBodyHeight + indicatorHeight)
    }

    @Test
    fun fastScrollerAccentAppliesDuringTouchAndRestoresAfterCancellation() {
        val f = Fixture()
        val settings = DrawerSettings(showFastScroller = true, fastScrollerColor = 0xFF00FF00.toInt())
        f.render(settings, shown = listOf(app("Alpha"), app("Bravo")))
        layout(f.scroller)
        val first = f.scroller.getChildAt(0) as TextView
        val normalColor = first.currentTextColor
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 20f, 1f, 0)
        f.scroller.onTouchEvent(down)
        down.recycle()
        assertThat(first.currentTextColor).isEqualTo(settings.fastScrollerColor)
        val cancel = MotionEvent.obtain(0, 100, MotionEvent.ACTION_CANCEL, 20f, 1f, 0)
        f.scroller.onTouchEvent(cancel)
        cancel.recycle()
        assertThat(first.currentTextColor).isEqualTo(normalColor)
    }

    @Test
    fun appsBesideTheLetterBubbleMoveAsideAndReturnAtAnyColumnCount() {
        for (columns in 4..8) {
            val f = Fixture()
            f.render(DrawerSettings(showFastScroller = true, columns = columns), shown = (1..columns * 2).map { app("App$it") })
            layout(f.list, height = 3000)
            fun settle() = shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1))
            val firstRow = (0 until columns).map { f.list.getChildAt(it) }
            val secondRow = (columns until columns * 2).map { f.list.getChildAt(it) }
            // Visual left and right edges, after the nudge's translation and scale about the centre.
            fun View.drawnLeft() = (left + right) / 2f + translationX - width * scaleX / 2
            fun View.drawnRight() = (left + right) / 2f + translationX + width * scaleX / 2
            val bubbleLeft = 860
            f.scroller.onBubbleMoved?.invoke(android.graphics.Rect(bubbleLeft, 0, 1028, firstRow[0].height / 2))
            settle()
            // The row clears the bubble, keeps its order without overlaps, and stays on screen.
            assertThat(firstRow.last().drawnRight()).isAtMost(bubbleLeft + 1f)
            assertThat(firstRow.first().drawnLeft()).isAtLeast(f.list.paddingLeft - 1f)
            assertThat(firstRow.zipWithNext().all { (a, b) -> a.drawnRight() <= b.drawnLeft() + 1f }).isTrue()
            assertThat(firstRow.map { it.scaleX }.distinct()).hasSize(1)
            assertThat(secondRow.all { it.translationX == 0f && it.scaleX == 1f }).isTrue()
            f.scroller.onBubbleMoved?.invoke(null)
            settle()
            assertThat(firstRow.all { it.translationX == 0f && it.scaleX == 1f }).isTrue()
        }
    }

    @Test
    fun fastScrollerLetterBubbleFollowsTheDragAndCanBeTurnedOff() {
        val f = Fixture()
        val shown = listOf(app("Alpha"), app("Bravo"))
        f.render(DrawerSettings(showFastScroller = true), shown = shown)
        layout(f.scroller)
        val bottom = f.scroller.height.toFloat() - 1
        fun touch(action: Int, y: Float) = MotionEvent.obtain(0, 0, action, 20f, y, 0).also { f.scroller.onTouchEvent(it); it.recycle() }
        assertThat(f.scroller.popupLetter).isNull()
        touch(MotionEvent.ACTION_DOWN, 1f)
        assertThat(f.scroller.popupLetter).isEqualTo("A")
        touch(MotionEvent.ACTION_MOVE, bottom)
        assertThat(f.scroller.popupLetter).isEqualTo("B")
        touch(MotionEvent.ACTION_UP, bottom)
        assertThat(f.scroller.popupLetter).isNull()

        f.render(DrawerSettings(showFastScroller = true, fastScrollerPopup = false), shown = shown)
        touch(MotionEvent.ACTION_DOWN, 1f)
        assertThat(f.scroller.popupLetter).isNull()
        touch(MotionEvent.ACTION_UP, 1f)
    }

    @Test
    fun anEmissionThatChangesNothingDoesNotEndARailDrag() {
        val f = Fixture()
        val shown = listOf(app("Alpha"), app("Bravo"))
        f.render(DrawerSettings(showFastScroller = true), shown = shown)
        layout(f.scroller)
        fun touch(action: Int, y: Float) = MotionEvent.obtain(0, 0, action, 20f, y, 0).also { f.scroller.onTouchEvent(it); it.recycle() }
        touch(MotionEvent.ACTION_DOWN, 1f)
        assertThat(f.scroller.popupLetter).isEqualTo("A")
        // Same apps, only a cosmetic setting differs: the drag and its bubble carry on.
        f.render(DrawerSettings(showFastScroller = true, backgroundAlpha = 0.9f), shown = shown)
        assertThat(f.scroller.popupLetter).isEqualTo("A")
        // A different list does end it.
        f.render(DrawerSettings(showFastScroller = true, backgroundAlpha = 0.9f), shown = listOf(app("Charlie"), app("Delta")))
        assertThat(f.scroller.popupLetter).isNull()
    }

    @Test
    fun crowdedRailDrawsSpacedLabelsButKeepsEveryEntryReachable() {
        val scroller = DrawerFastScroller(context)
        val letters = listOf("#") + ('A'..'Z').map(Char::toString) + listOf("あ", "か", "さ", "た", "な", "は", "ま", "や", "ら", "わ", "…")
        scroller.setSections(letters.mapIndexed { i, l -> DrawerSection(l, i) }, Color.WHITE)
        layout(scroller, height = 300)
        shadowOf(Looper.getMainLooper()).idle()
        val labels = (0 until scroller.childCount).map { scroller.getChildAt(it) as TextView }
        val drawn = labels.filter { it.currentTextColor != Color.TRANSPARENT }
        assertThat(drawn.size).isLessThan(labels.size)
        assertThat(drawn.size).isAtLeast(2)
        assertThat(labels.all { it.isFocusable && it.contentDescription.isNotEmpty() }).isTrue()
        assertThat(labels.first().contentDescription.toString()).isEqualTo(context.getString(R.string.drawer_fast_scroller_jump, context.getString(R.string.drawer_fast_scroller_other)))
        assertThat(labels.last().contentDescription.toString()).isEqualTo(context.getString(R.string.drawer_fast_scroller_jump, context.getString(R.string.drawer_fast_scroller_overflow)))
    }

    @Test
    fun fastScrollerStripNeverOverlapsAppCellsOnEitherSide() {
        val res = context.resources
        val density = res.displayMetrics.density
        val sidePadding = res.getDimensionPixelSize(R.dimen.home_page_padding)
        for (style in DrawerFastScrollerStyle.entries) for (side in DrawerFastScrollerSide.entries) for (gapDp in listOf(0, 8, 20)) {
            val f = Fixture()
            f.render(
                DrawerSettings(showFastScroller = true, fastScrollerStyle = style, fastScrollerSide = side, fastScrollerGapDp = gapDp),
                shown = listOf(app("Alpha"), app("Bravo")),
            )
            if (style == DrawerFastScrollerStyle.ALPHABET) assertThat(f.scroller.visibility).isEqualTo(View.VISIBLE)
            val bar = res.getDimensionPixelSize(
                if (style == DrawerFastScrollerStyle.THUMB) R.dimen.drawer_fast_scroller_thumb_width else R.dimen.drawer_fast_scroller_width,
            )
            val strip = bar + (gapDp * density).roundToInt()
            val params = f.scroller.layoutParams as FrameLayout.LayoutParams
            // The touch strip is exactly the bar plus the gap, and the grid starts where it ends.
            assertThat(params.width).isEqualTo(strip)
            if (side == DrawerFastScrollerSide.LEFT) {
                assertThat(params.gravity).isEqualTo(Gravity.LEFT)
                assertThat(f.list.paddingLeft).isEqualTo(if (style == DrawerFastScrollerStyle.THUMB) sidePadding else strip)
                assertThat(f.list.paddingRight).isEqualTo(sidePadding)
            } else {
                assertThat(params.gravity).isEqualTo(Gravity.RIGHT)
                assertThat(f.list.paddingRight).isEqualTo(if (style == DrawerFastScrollerStyle.THUMB) sidePadding else strip)
                assertThat(f.list.paddingLeft).isEqualTo(sidePadding)
            }
            if (style == DrawerFastScrollerStyle.THUMB) {
                assertThat(f.list.isVerticalScrollBarEnabled).isFalse()
                assertThat(f.scroller.thumbOnLeft).isEqualTo(side == DrawerFastScrollerSide.LEFT)
            }
        }
    }

    @Test
    fun thumbScrollerIgnoresStrayTapsButFollowsDrags() {
        val f = Fixture()
        val fractions = mutableListOf<Float>()
        f.render(
            DrawerSettings(showFastScroller = true, fastScrollerStyle = DrawerFastScrollerStyle.THUMB),
            shown = listOf(app("Alpha"), app("Bravo"), app("Charlie")),
        )
        f.scroller.thumbMode = true
        f.scroller.onThumbDragged = { fractions += it }
        f.scroller.updateThumb(0, 100, 500, "A")
        layout(f.scroller)
        fun touch(action: Int, y: Float) = MotionEvent.obtain(0, 0, action, 20f, y, 0).also {
            f.scroller.onTouchEvent(it)
            it.recycle()
        }
        touch(MotionEvent.ACTION_DOWN, 450f)
        touch(MotionEvent.ACTION_UP, 450f)
        assertThat(fractions).isEmpty()
        touch(MotionEvent.ACTION_DOWN, 450f)
        touch(MotionEvent.ACTION_MOVE, 10f)
        touch(MotionEvent.ACTION_UP, 10f)
        assertThat(fractions).containsExactly(0f)
    }

    @Test
    fun thumbUsesScrollPixelsAcrossUnevenSectionsAndPreservesGrabOffset() {
        val scroller = DrawerFastScroller(context).apply {
            thumbMode = true
            setSections(listOf(DrawerSection("A", 0), DrawerSection("B", 1), DrawerSection("C", 2)), Color.WHITE)
            updateThumb(200, 100, 500, "C")
        }
        layout(scroller, height = 500)
        val initial = scroller.thumbBounds
        assertThat(initial.top).isEqualTo(200)
        assertThat(initial.height()).isEqualTo(100)
        val fractions = mutableListOf<Float>()
        scroller.onThumbDragged = { fractions += it }
        fun touch(action: Int, y: Float) = MotionEvent.obtain(0, 0, action, 2f, y, 0).also {
            scroller.onTouchEvent(it)
            it.recycle()
        }
        touch(MotionEvent.ACTION_DOWN, 225f)
        touch(MotionEvent.ACTION_MOVE, 325f)
        assertThat(fractions.last()).isWithin(0.001f).of(0.75f)
        touch(MotionEvent.ACTION_MOVE, -100f)
        assertThat(fractions.last()).isEqualTo(0f)
        touch(MotionEvent.ACTION_MOVE, 1000f)
        assertThat(fractions.last()).isEqualTo(1f)
        touch(MotionEvent.ACTION_CANCEL, 1000f)
        assertThat(scroller.popupLetter).isNull()
    }

    @Test
    fun thumbPopupNamesActualTopVisibleRowInMixedLetterGrid() {
        val f = Fixture()
        val shown = listOf(app("Alpha"), app("Bravo")) +
            (0 until 45).map { app("Charlie%02d".format(it)) } +
            listOf(app("Delta")) + (0 until 40).map { app("Echo%02d".format(it)) }
        f.render(DrawerSettings(columns = 2, showFastScroller = true,
            fastScrollerStyle = DrawerFastScrollerStyle.THUMB), shown = shown)
        settleLayout(f.drawer, 1000)
        // Robolectric does not run a second FrameLayout pass after the posted visibility change.
        val scrollerWidth = (f.scroller.layoutParams as FrameLayout.LayoutParams).width
        f.scroller.measure(View.MeasureSpec.makeMeasureSpec(scrollerWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(f.list.height, View.MeasureSpec.EXACTLY))
        f.scroller.layout(0, 0, scrollerWidth, f.list.height)
        check(f.list.height > 0 && f.scroller.height > 0 &&
            f.list.computeVerticalScrollRange() > f.list.computeVerticalScrollExtent()) {
            "list=${f.list.height} scroller=${f.scroller.height} range=${f.list.computeVerticalScrollRange()} extent=${f.list.computeVerticalScrollExtent()}"
        }
        val manager = f.list.layoutManager as GridLayoutManager
        val bounds = f.scroller.thumbBounds
        fun touch(action: Int, y: Float) = MotionEvent.obtain(0, 0, action, 2f, y, 0).also {
            f.scroller.onTouchEvent(it)
            it.recycle()
        }
        touch(MotionEvent.ACTION_DOWN, bounds.top + bounds.height() / 2f)
        touch(MotionEvent.ACTION_MOVE, f.scroller.height / 2f)
        val middle = manager.findFirstVisibleItemPosition()
        assertThat(middle).isAtLeast(2)
        assertThat(middle).isLessThan(47)
        assertThat(f.scroller.popupLetter).isEqualTo("C")
        touch(MotionEvent.ACTION_MOVE, f.scroller.height.toFloat() + 100f)
        assertThat(f.list.canScrollVertically(1)).isFalse()
        // Delta shares a row with Charlie; the final viewport begins within the Echo run.
        assertThat(f.scroller.popupLetter).isEqualTo("E")
        touch(MotionEvent.ACTION_MOVE, -100f)
        assertThat(f.list.canScrollVertically(-1)).isFalse()
        assertThat(f.scroller.popupLetter).isEqualTo("A")
        touch(MotionEvent.ACTION_UP, -100f)
        assertThat(f.scroller.popupLetter).isNull()

        // A normal swipe/scroll updates the thumb without creating a stale bubble.
        f.list.scrollBy(0, 400)
        assertThat(f.scroller.thumbBounds.top).isGreaterThan(0)
        assertThat(f.scroller.popupLetter).isNull()
    }

    @Test
    fun thumbVisibilityFollowsActualScrollabilityAndCurrentDrawerMode() {
        val f = Fixture()
        val singleSection = (0 until 60).map { app("Alpha%02d".format(it)) }
        f.render(DrawerSettings(columns = 2, showFastScroller = true,
            fastScrollerStyle = DrawerFastScrollerStyle.THUMB), shown = singleSection)
        settleLayout(f.drawer, 1000)
        assertThat(f.scroller.visibility).isEqualTo(View.VISIBLE)
        f.render(DrawerSettings(columns = 2, showFastScroller = false,
            fastScrollerStyle = DrawerFastScrollerStyle.THUMB), shown = singleSection)
        settleLayout(f.drawer, 1000)
        assertThat(f.scroller.visibility).isEqualTo(View.GONE)
        f.render(DrawerSettings(columns = 2, showFastScroller = true,
            fastScrollerStyle = DrawerFastScrollerStyle.THUMB), query = "Alpha", shown = singleSection)
        settleLayout(f.drawer, 1000)
        assertThat(f.scroller.visibility).isEqualTo(View.GONE)
    }

    @Test
    fun sharedPrivateKeyboardPreferenceAlsoUpdatesDrawerEditor() {
        val f = Fixture()
        f.render(DrawerSettings())
        assertThat(f.search.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING).isNotEqualTo(0)
        val initial = LauncherSettings()
        f.controller.render(DrawerUi.Apps(apps), "", initial.copy(search = initial.search.copy(privateKeyboard = false)))
        assertThat(f.search.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING).isEqualTo(0)
    }

    @Test
    fun hiddenSearchDisablesPullToSearchEvenIfPreferenceEnabled() {
        val f = Fixture()
        f.render(DrawerSettings(pullToSearch = true, searchBarPosition = DrawerSearchBarPosition.HIDDEN))
        assertThat(f.controller.canPullToSearch).isFalse()
        f.render(DrawerSettings(pullToSearch = true))
        assertThat(f.controller.canPullToSearch).isTrue()
    }

    @Test
    fun pullToSearchIsCancelledWithoutClosingAndCommittedOnceOnRelease() {
        val view = View(context)
        layout(view, 1000)
        var closes = 0
        var searches = 0
        val motion = DrawerMotion(view) { if (!it) closes++ }
        motion.jumpTo(true)
        motion.canPullToSearch = { true }
        motion.onPullToSearch = { searches++ }
        val swipe = motion.drawerSwipes { true }
        swipe.onSwipeStart(SwipeDirection.DOWN)
        swipe.onSwipeMove(500f)
        swipe.onSwipeEnd(2000f, cancelled = true)
        assertThat(searches).isEqualTo(0)
        assertThat(motion.targetOpen).isTrue()
        swipe.onSwipeStart(SwipeDirection.DOWN)
        swipe.onSwipeMove(500f)
        swipe.onSwipeEnd(2000f, cancelled = false)
        assertThat(searches).isEqualTo(1)
        assertThat(closes).isEqualTo(0)
        assertThat(view.translationY).isEqualTo(0f)
    }

    @Test
    fun backSwipePreviewsTheCloseAndCancellingSettlesOpenAgain() {
        val view = View(context)
        layout(view, 1000)
        val motion = DrawerMotion(view) {}
        motion.transition = DrawerTransition.SLIDE
        motion.jumpTo(true)
        motion.backProgress(1f)
        assertThat(view.translationY).isWithin(0.01f).of(150f)
        motion.backProgress(0.5f)
        assertThat(view.translationY).isWithin(0.01f).of(75f)
        motion.cancelBack()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1))
        assertThat(view.translationY).isEqualTo(0f)
        assertThat(motion.targetOpen).isTrue()
    }

    @Test
    fun backSwipeLeavesAClosedOrUnanimatedDrawerAlone() {
        val view = View(context)
        layout(view, 1000)
        val motion = DrawerMotion(view) {}
        motion.jumpTo(false)
        motion.backProgress(1f)
        assertThat(view.visibility).isEqualTo(View.INVISIBLE)
        motion.jumpTo(true)
        motion.animationScale = 0f
        motion.backProgress(1f)
        assertThat(view.translationY).isEqualTo(0f)
    }

    @Test
    fun allTransitionsHonorDisabledAnimationsAndLeaveNoScaleOrAlphaOnReopen() {
        val view = View(context)
        layout(view, 1000)
        val motion = DrawerMotion(view) {}
        motion.animationScale = 0f
        DrawerTransition.entries.forEach { transition ->
            motion.transition = transition
            motion.jumpTo(false)
            motion.animateTo(true)
            assertThat(view.visibility).isEqualTo(View.VISIBLE)
            assertThat(view.alpha).isEqualTo(1f)
            assertThat(view.scaleX).isEqualTo(1f)
            assertThat(view.translationY).isEqualTo(0f)
            motion.animateTo(false)
            assertThat(view.visibility).isEqualTo(View.INVISIBLE)
        }
    }
    @Test fun wideTabsAndThumbModeKeepAccessTargetsAndRestoreCompactGeometry() {
        val f = Fixture()
        f.render(DrawerSettings(showWorkTab = true, automaticRowSpacing = false, showFastScroller = true,
            tabStyle = io.github.aj4abinjacob.chikku.core.model.settings.DrawerTabStyle.SEGMENTED,
            fastScrollerStyle = io.github.aj4abinjacob.chikku.core.model.settings.DrawerFastScrollerStyle.THUMB))
        assertThat(f.tabs.childCount).isEqualTo(2)
        assertThat((f.tabs.getChildAt(0).layoutParams as LinearLayout.LayoutParams).weight).isEqualTo(1f)
        assertThat(f.scroller.thumbMode).isTrue()
        assertThat(f.scroller.childCount).isGreaterThan(0)
        assertThat(f.scroller.getChildAt(0).contentDescription).isNotNull()
        assertThat(f.list.paddingTop).isGreaterThan(0)
        f.render(DrawerSettings(showFastScroller = true))
        assertThat(f.scroller.thumbMode).isFalse()
        assertThat(f.list.paddingTop).isEqualTo(0)
    }

    // --- Memory: release rows while the drawer is hidden ------------------------------------------

    private fun idleFor(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(ms))

    @Test
    fun hiddenDrawerReleasesRowsAndRebindsAtTheSamePositionWhenShown() {
        val f = Fixture()
        val settings = DrawerSettings(style = DrawerStyle.LIST, rememberScrollPosition = true)
        f.render(settings)
        layout(f.list)
        val manager = f.list.layoutManager as GridLayoutManager
        manager.scrollToPositionWithOffset(20, 0)
        layout(f.list)
        assertThat(f.list.childCount).isGreaterThan(0)

        f.controller.onDrawerShown(false)
        idleFor(500)
        assertThat(f.controller.isContentAttached).isTrue() // a quick reopen keeps the rows
        idleFor(600)
        assertThat(f.controller.isContentAttached).isFalse()
        assertThat(f.list.adapter).isNull()
        assertThat(f.list.childCount).isEqualTo(0)
        assertThat(f.list.recycledViewPool.getRecycledViewCount(0)).isEqualTo(0)

        // State changes while closed bind nothing.
        f.controller.render(DrawerUi.Apps(apps.dropLast(1)), "", LauncherSettings(drawer = settings))
        idleFor(50)
        layout(f.list)
        assertThat(f.list.childCount).isEqualTo(0)

        f.controller.onDrawerShown(true)
        assertThat(f.controller.isContentAttached).isTrue()
        idleUntil { f.list.adapter?.itemCount == apps.size - 1 }
        layout(f.list)
        assertThat(f.list.childCount).isGreaterThan(0)
        assertThat(manager.findFirstVisibleItemPosition()).isEqualTo(20)
    }

    @Test
    fun quickReopenCancelsTheRelease() {
        val f = Fixture()
        f.render(DrawerSettings(style = DrawerStyle.VERTICAL))
        layout(f.list)
        val bound = f.list.getChildAt(0)
        f.controller.onDrawerShown(false)
        idleFor(300)
        f.controller.onDrawerShown(true)
        idleFor(2_000)
        assertThat(f.controller.isContentAttached).isTrue()
        assertThat(f.list.getChildAt(0)).isSameInstanceAs(bound)
    }

    @Test
    fun closingWithoutRememberingWhileReleasedReopensAtTheTop() {
        val f = Fixture()
        f.render(DrawerSettings(style = DrawerStyle.LIST, rememberScrollPosition = false))
        layout(f.list)
        val manager = f.list.layoutManager as GridLayoutManager
        manager.scrollToPositionWithOffset(20, 0)
        layout(f.list)
        f.controller.onDrawerShown(false)
        idleFor(1_100)
        f.controller.onClosed()
        f.controller.onDrawerShown(true)
        layout(f.list)
        assertThat(manager.findFirstVisibleItemPosition()).isEqualTo(0)
    }

    @Test
    fun pagedDrawerKeepsItsPageAcrossReleaseAndRerender() {
        val f = Fixture()
        val settings = DrawerSettings(style = DrawerStyle.HORIZONTAL_PAGED, columns = 2, rows = 2, rememberScrollPosition = true)
        f.render(settings)
        settleLayout(f.drawer, 1000)
        f.dots.onPageSelected!!.invoke(3)
        settleLayout(f.drawer, 1000)
        assertThat(f.dots.contentDescription.toString()).contains("4")

        f.controller.onDrawerShown(false)
        idleFor(1_100)
        assertThat(f.pages.adapter).isNull()
        assertThat(f.pages.childCount).isEqualTo(0)
        f.controller.render(DrawerUi.Apps(apps.dropLast(1)), "", LauncherSettings(drawer = settings))
        settleLayout(f.drawer, 1000)
        assertThat(f.pages.childCount).isEqualTo(0)

        f.controller.onDrawerShown(true)
        settleLayout(f.drawer, 1000)
        val shown = (f.pages.layoutManager as LinearLayoutManager).findFirstCompletelyVisibleItemPosition()
        assertThat(shown).isEqualTo(3)
        assertThat(f.dots.contentDescription.toString()).contains("4")
    }

    @Test
    fun drawerMotionReportsWhenTheDrawerIsShownAndHidden() {
        val view = View(context)
        layout(view, 1000)
        val shown = mutableListOf<Boolean>()
        val motion = DrawerMotion(view) {}
        motion.onShownChanged = { shown += it }
        motion.jumpTo(false)
        motion.animationScale = 0f
        motion.animateTo(true)
        motion.animateTo(false)
        assertThat(shown).containsExactly(false, true, false).inOrder()
        assertThat(view.visibility).isEqualTo(View.INVISIBLE)

        shown.clear()
        motion.animationScale = 1f
        motion.homeSwipes.onSwipeStart(SwipeDirection.UP)
        assertThat(shown).containsExactly(true) // the open gesture starts the rebind
        motion.homeSwipes.onSwipeEnd(0f, cancelled = true)
        idleFor(1_000)
        assertThat(shown).containsExactly(true, false).inOrder()
    }

    /** A restored backup can carry any ARGB accent; the contrast check must not throw on alpha. */
    @Test fun translucentTabAccentStillStylesLauncherAndPreviewTabs() {
        val translucentYellow = 0x80FFEB3B.toInt()
        val translucentNavy = 0x801A237E.toInt()
        for (accent in listOf(translucentYellow, translucentNavy)) {
            val drawer = DrawerSettings(showWorkTab = true, tabAccentColor = accent,
                tabStyle = io.github.aj4abinjacob.chikku.core.model.settings.DrawerTabStyle.SEGMENTED)
            val f = Fixture()
            f.render(drawer)
            val preview = io.github.aj4abinjacob.chikku.settings.drawer.DrawerTabSampleView(context)
            preview.bind(drawer, io.github.aj4abinjacob.chikku.settings.previews.DrawerSampleColors(Color.BLACK, Color.WHITE))
            val launcherTab = f.tabs.getChildAt(0) as TextView
            val previewTab = preview.row.getChildAt(0) as TextView
            assertThat(previewTab.currentTextColor).isEqualTo(launcherTab.currentTextColor)
        }
        // Judged as the opaque colour: light accents take dark text, dark accents white.
        assertThat(io.github.aj4abinjacob.chikku.home.drawer.DrawerChrome.accentTabTextColor(translucentYellow)).isEqualTo(0xFF1B1B1F.toInt())
        assertThat(io.github.aj4abinjacob.chikku.home.drawer.DrawerChrome.accentTabTextColor(translucentNavy)).isEqualTo(Color.WHITE)
    }

    @Test fun previewTabsUseTheDrawersTabColoursForLightAndDarkAccents() {
        val yellow = 0xFFFFEB3B.toInt()
        val navy = 0xFF1A237E.toInt()
        for (accent in listOf(yellow, navy)) {
            val drawer = DrawerSettings(showWorkTab = true, tabAccentColor = accent,
                tabStyle = io.github.aj4abinjacob.chikku.core.model.settings.DrawerTabStyle.SEGMENTED)
            val f = Fixture()
            f.render(drawer)
            val preview = io.github.aj4abinjacob.chikku.settings.drawer.DrawerTabSampleView(context)
            preview.bind(drawer, io.github.aj4abinjacob.chikku.settings.previews.DrawerSampleColors(Color.BLACK, Color.WHITE))
            fun fill(tab: View) = (((tab.background as android.graphics.drawable.InsetDrawable).drawable) as android.graphics.drawable.GradientDrawable).color!!.defaultColor
            val launcherTab = f.tabs.getChildAt(0) as TextView
            val previewTab = preview.row.getChildAt(0) as TextView
            assertThat(previewTab.currentTextColor).isEqualTo(launcherTab.currentTextColor)
            assertThat(fill(previewTab)).isEqualTo(fill(launcherTab))
            assertThat(fill(launcherTab)).isEqualTo(accent)
        }
        // Light accents take dark text, dark accents white.
        assertThat(io.github.aj4abinjacob.chikku.home.drawer.DrawerChrome.accentTabTextColor(yellow)).isEqualTo(0xFF1B1B1F.toInt())
        assertThat(io.github.aj4abinjacob.chikku.home.drawer.DrawerChrome.accentTabTextColor(navy)).isEqualTo(Color.WHITE)
    }
}
