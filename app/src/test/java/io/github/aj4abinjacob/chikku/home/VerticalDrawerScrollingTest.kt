package io.github.aj4abinjacob.chikku.home

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewConfiguration
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.LauncherApp
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerSettings
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.core.model.settings.ListEdgeEffect
import io.github.aj4abinjacob.chikku.core.model.settings.ListScrollSpeed
import io.github.aj4abinjacob.chikku.home.drawer.DrawerController
import io.github.aj4abinjacob.chikku.home.drawer.DrawerFastScroller
import io.github.aj4abinjacob.chikku.home.drawer.DrawerPageDots
import io.github.aj4abinjacob.chikku.home.drawer.DrawerViews
import io.github.aj4abinjacob.chikku.home.drawer.HorizontalDrawerRecyclerView
import io.github.aj4abinjacob.chikku.home.drawer.VerticalDrawerRecyclerView
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
class VerticalDrawerScrollingTest {
    private val context: Context = ContextThemeWrapper(ApplicationProvider.getApplicationContext<Context>(), R.style.Theme_Chikku_Home)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @After
    fun tearDown() = scope.cancel()

    /** Records the velocity RecyclerView would fling with, after the view's own scaling. */
    private fun VerticalDrawerRecyclerView.flungVelocity(velocity: Int): Int {
        var flung = 0
        layoutManager = LinearLayoutManager(context)
        onFlingListener = object : RecyclerView.OnFlingListener() {
            override fun onFling(velocityX: Int, velocityY: Int): Boolean { flung = velocityY; return true }
        }
        fling(0, velocity)
        return flung
    }

    @Test
    fun scrollSpeedScalesTheDrawersFlingWithinThePlatformMaximum() {
        val list = VerticalDrawerRecyclerView(context)
        val max = ViewConfiguration.get(context).scaledMaximumFlingVelocity
        assertThat(list.flungVelocity(1_000)).isEqualTo(1_000)
        list.scrollSpeed = ListScrollSpeed.SLOW
        assertThat(list.flungVelocity(1_000)).isEqualTo(600)
        list.scrollSpeed = ListScrollSpeed.FAST
        assertThat(list.flungVelocity(-1_000)).isEqualTo(-1_600)
        assertThat(list.flungVelocity(max)).isEqualTo(max)
    }

    @Test
    fun turningTheEdgeEffectOffAndOnRestoresTheSystemMode() {
        val list = VerticalDrawerRecyclerView(context)
        val system = list.overScrollMode
        list.edgeEffect = ListEdgeEffect.NONE
        assertThat(list.overScrollMode).isEqualTo(View.OVER_SCROLL_NEVER)
        list.edgeEffect = ListEdgeEffect.SYSTEM
        assertThat(list.overScrollMode).isEqualTo(system)
    }

    @Test
    fun drawerAppliesSavedVerticalScrollingSettings() {
        val list = VerticalDrawerRecyclerView(context)
        val drawer = LinearLayout(context)
        val tabs = LinearLayout(context)
        val status = TextView(context)
        val body = FrameLayout(context).apply { addView(list); addView(HorizontalDrawerRecyclerView(context)) }
        drawer.addView(body)
        val listener = object : DrawerItemListener {
            override fun onAppClick(app: LauncherApp, view: View) = Unit
            override fun onAppLongClick(app: LauncherApp, view: View) = false
            override fun onAddToHome(app: LauncherApp) = Unit
            override fun onAppInfo(app: LauncherApp, view: View) = Unit
        }
        val controller = DrawerController(DrawerViews(drawer, EditText(context), HorizontalScrollView(context).apply { addView(tabs) },
            tabs, LinearLayout(context).apply { addView(status) }, status, Button(context), list, body.getChildAt(1) as RecyclerView,
            DrawerFastScroller(context), DrawerPageDots(context)), IconBinder(context, scope, FakeIconProvider()), listener)
        controller.render(DrawerUi.Apps(listOf(app("Alpha"))), "", LauncherSettings(drawer = DrawerSettings(
            listScrollSpeed = ListScrollSpeed.FAST, listEdgeEffect = ListEdgeEffect.NONE)))
        assertThat(list.scrollSpeed).isEqualTo(ListScrollSpeed.FAST)
        assertThat(list.overScrollMode).isEqualTo(View.OVER_SCROLL_NEVER)
    }
}
