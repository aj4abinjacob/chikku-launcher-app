package io.github.aj4abinjacob.chikku.home

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.settings.DockSettings
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestHomeApplication::class)
class DockSurfaceControllerTest {
    private val context: Context = ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_Chikku_Home)
    private val dock = CellLayout(context)
    private val slot = FrameLayout(context).apply { addView(dock) }
    private val overlay = FrameLayout(context)
    private val controller = DockSurfaceController(dock, slot, overlay)

    @Test
    fun overlayMovesExistingDockWithoutReplacingItsContents() {
        val icon = View(context)
        dock.addView(icon, CellLayout.LayoutParams(0, 0))
        controller.render(DockSettings(displayAsOverlay = true), true)
        assertThat(dock.parent).isSameInstanceAs(overlay)
        assertThat(dock.getChildAt(0)).isSameInstanceAs(icon)
        assertThat(slot.childCount).isEqualTo(0)
        controller.render(DockSettings(), true)
        assertThat(dock.parent).isSameInstanceAs(slot)
        assertThat(dock.getChildAt(0)).isSameInstanceAs(icon)
    }

    @Test
    fun successfulLaunchClosesOverlayUntilToggledAndRecoveryHidesIt() {
        val preferences = DockSettings(displayAsOverlay = true, automaticallyClose = true)
        controller.render(preferences, true)
        controller.onAppLaunched()
        controller.render(preferences, true)
        assertThat(dock.visibility).isEqualTo(View.GONE)
        controller.toggle()
        assertThat(dock.visibility).isEqualTo(View.VISIBLE)
        controller.render(preferences, false)
        assertThat(dock.visibility).isEqualTo(View.INVISIBLE)
        controller.toggle()
        assertThat(dock.visibility).isEqualTo(View.INVISIBLE)
        controller.render(preferences.copy(enabled = false), true)
        assertThat(dock.visibility).isEqualTo(View.GONE)
    }

    @Test
    fun hiddenOverlaySurvivesInitialLoadingAfterRecreation() {
        val restored = DockSurfaceController(dock, slot, overlay, hiddenInitially = true)
        restored.render(DockSettings(), false)
        restored.render(DockSettings(displayAsOverlay = true), true)
        assertThat(dock.visibility).isEqualTo(View.GONE)
        restored.toggle()
        assertThat(dock.visibility).isEqualTo(View.VISIBLE)
    }

    @Test
    fun autoCloseDoesNotHideNormalDock() {
        controller.render(DockSettings(automaticallyClose = true), true)
        controller.onAppLaunched()
        assertThat(dock.visibility).isEqualTo(View.VISIBLE)
    }

    @Test
    fun unchangedInputsKeepBackgroundAndRunningFade() {
        val solid = DockSettings(background = io.github.aj4abinjacob.chikku.core.model.settings.DockBackground.RECTANGLE)
        controller.render(solid, true)
        val background = dock.background
        assertThat(background).isNotNull()
        dock.animate().alpha(0.5f).setDuration(10_000).start()

        controller.render(solid.copy(lastPage = 3), true)
        assertThat(dock.background).isSameInstanceAs(background)
        assertThat(dock.animate()).isNotNull()
        assertThat(dock.alpha).isEqualTo(1f) // untouched; updateVisibility would also reset alpha

        controller.render(solid.copy(backgroundAlpha = 0.9f), true)
        assertThat(dock.background).isNotSameInstanceAs(background)
    }
}
