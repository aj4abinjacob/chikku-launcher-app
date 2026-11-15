package io.github.aj4abinjacob.chikku.search

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.ProfileType
import io.github.aj4abinjacob.chikku.core.model.search.SearchResults
import io.github.aj4abinjacob.chikku.core.model.search.ShortcutHit
import io.github.aj4abinjacob.chikku.core.model.search.ShortcutLookup
import io.github.aj4abinjacob.chikku.home.TestHomeApplication
import io.github.aj4abinjacob.chikku.home.appmenu.AppMenuHandle
import io.github.aj4abinjacob.chikku.home.app
import org.junit.Test
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestHomeApplication::class)
class SearchDragSourceTest {
    private val hosts = mutableListOf<ActivityController<Activity>>()

    private fun source(compact: Boolean): SearchAppResultView {
        val host = Robolectric.buildActivity(Activity::class.java).setup()
        hosts += host
        return SearchAppResultView(host.get(), compact).also { host.get().setContentView(it) }
    }

    @After
    fun tearDown() {
        hosts.forEach { it.pause().stop().destroy() }
    }

    @Test
    fun longPressKeepsComponentAndProfileIdentityAndUsesIconShadowInBothPresentations() {
        val work = app("Maps", profile = ProfileType.WORK)
        for (compact in listOf(false, true)) {
            val view = source(compact)
            var launches = 0
            var source: View? = null
            var actualKey: io.github.aj4abinjacob.chikku.core.model.AppKey? = null
            view.bind(work, ColorDrawable(Color.BLUE), Color.BLACK, { launches++ }, { current, icon ->
                source = icon
                actualKey = current.key
                true
            })
            val down = MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), MotionEvent.ACTION_DOWN, 12f, 12f, 0)
            view.onTouchEvent(down)
            down.recycle()
            assertThat(view.performLongClick()).isTrue()
            assertThat(actualKey).isEqualTo(work.key)
            assertThat(source).isSameInstanceAs(view.icon)
            assertThat(source?.parent).isSameInstanceAs(view)
            assertThat(launches).isEqualTo(0)
        }
    }

    @Test
    fun longPressOpensAppMenuForTheResultAndMovingOnClosesItAndStartsTheDrag() {
        val maps = app("Maps", profile = ProfileType.WORK)
        val view = source(compact = false)
        var launched = false
        var dragged: io.github.aj4abinjacob.chikku.core.model.AppKey? = null
        var menuFor: io.github.aj4abinjacob.chikku.core.model.AppKey? = null
        var dismissed = 0
        view.bind(maps, null, Color.BLACK, { launched = true }, { current, _ -> dragged = current.key; true },
            onMenu = { current, _ -> menuFor = current.key; AppMenuHandle { dismissed++ } })
        fun send(action: Int, y: Float) {
            val event = MotionEvent.obtain(1L, 2L, action, 10f, y, 0)
            try { view.dispatchTouchEvent(event) } finally { event.recycle() }
        }
        send(MotionEvent.ACTION_DOWN, 10f)
        assertThat(view.performLongClick()).isTrue()
        assertThat(menuFor).isEqualTo(maps.key)
        assertThat(dragged).isNull()
        send(MotionEvent.ACTION_MOVE, 200f)
        assertThat(dismissed).isEqualTo(1)
        assertThat(dragged).isEqualTo(maps.key)
        send(MotionEvent.ACTION_CANCEL, 200f)
        assertThat(launched).isFalse()
    }

    @Test
    fun nonTouchLongClickOpensTheMenuButNeverADrag() {
        val view = source(compact = true)
        var menus = 0
        var drags = 0
        view.bind(app("Maps"), null, Color.BLACK, {}, { _, _ -> drags++; true }, onMenu = { _, _ -> menus++; AppMenuHandle {} })
        assertThat(view.performLongClick()).isTrue()
        assertThat(menus).isEqualTo(1)
        assertThat(drags).isEqualTo(0)
    }

    @Test
    fun rejectedDragConsumesLongPressAndDoesNotLaunchTheResult() {
        val view = source(compact = false)
        var launched = false
        var attempted = false
        view.bind(app("Maps"), null, Color.BLACK, { launched = true }, { _, _ -> attempted = true; false })
        val down = MotionEvent.obtain(1L, 1L, MotionEvent.ACTION_DOWN, 10f, 10f, 0)
        view.onTouchEvent(down)
        down.recycle()
        assertThat(view.performLongClick()).isTrue()
        assertThat(attempted).isTrue()
        assertThat(launched).isFalse()
    }

    @Test
    fun cancellationAndNonTouchLongClicksCannotCreateADragWhileTapStillLaunches() {
        val view = source(compact = true)
        var launched = false
        var attempts = 0
        view.bind(app("Maps"), null, Color.BLACK, { launched = true }, { _, _ -> attempts++; true })
        assertThat(view.performLongClick()).isFalse()
        val down = MotionEvent.obtain(1L, 1L, MotionEvent.ACTION_DOWN, 10f, 10f, 0)
        val cancel = MotionEvent.obtain(1L, 2L, MotionEvent.ACTION_CANCEL, 10f, 10f, 0)
        view.onTouchEvent(down)
        view.onTouchEvent(cancel)
        down.recycle()
        cancel.recycle()
        assertThat(view.performLongClick()).isFalse()
        assertThat(attempts).isEqualTo(0)
        view.performClick()
        assertThat(launched).isTrue()
    }

    @Test
    fun suggestionLabelIsVisibleAccessibleAndClearedOnRebindInBothPresentations() {
        for (compact in listOf(false, true)) {
            val view = source(compact)
            val blinkit = app("Blinkit")
            view.bind(blinkit, null, Color.BLACK, {}, { _, _ -> false }, suggested = true)
            val labels = view.getChildAt(1) as android.widget.LinearLayout
            val suggestion = labels.getChildAt(2) as android.widget.TextView
            assertThat(suggestion.visibility).isEqualTo(View.VISIBLE)
            assertThat(suggestion.text.toString()).isEqualTo(view.context.getString(io.github.aj4abinjacob.chikku.R.string.search_suggested_match))
            assertThat(view.contentDescription.toString()).contains(suggestion.text.toString())
            view.bind(blinkit, null, Color.BLACK, {}, { _, _ -> false })
            assertThat(suggestion.visibility).isEqualTo(View.GONE)
            assertThat(view.contentDescription.toString()).isEqualTo("Blinkit")
        }
    }

    @Test
    fun immediateVisibilityFilterRemovesStaleProfileAppsAndShortcutsWithoutMixingIdenticalPackages() {
        val personal = app("Maps")
        val work = app("Maps", profile = ProfileType.WORK)
        val shortcut = ShortcutHit(work.key.packageName, "route", work.key.userSerial, "Route", work.key)
        val result = visibleSearchResults(SearchResults("maps", apps = listOf(personal, work), shortcuts = ShortcutLookup.Found(listOf(shortcut)), appMatchRanks = mapOf(personal.key to 0, work.key to 0)), setOf(personal.key))
        assertThat(result.apps).containsExactly(personal)
        assertThat(result.appMatchRanks.keys).containsExactly(personal.key)
        assertThat((result.shortcuts as ShortcutLookup.Found).shortcuts).isEmpty()
        assertThat(visibleSearchResults(result, emptySet()).apps).isEmpty()
    }
}
