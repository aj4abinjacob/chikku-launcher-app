package io.github.aj4abinjacob.chikku.settings

import android.graphics.Color
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.GestureAction
import io.github.aj4abinjacob.chikku.core.model.AppCustomization
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.ItemActions
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerOpenMethod
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerSettings
import io.github.aj4abinjacob.chikku.core.model.settings.FolderSettings
import io.github.aj4abinjacob.chikku.core.model.settings.GestureSettings
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.core.model.settings.LookFeelSettings
import io.github.aj4abinjacob.chikku.settings.previews.GesturePracticePad
import io.github.aj4abinjacob.chikku.settings.previews.folderNightSampleColors
import io.github.aj4abinjacob.chikku.settings.previews.folderPracticeSwipeActions
import io.github.aj4abinjacob.chikku.settings.previews.iconPracticeSwipeActions
import io.github.aj4abinjacob.chikku.settings.previews.loadAppearanceSamples
import io.github.aj4abinjacob.chikku.settings.previews.practiceGestureAction
import io.github.aj4abinjacob.chikku.settings.previews.previewMenuActions
import io.github.aj4abinjacob.chikku.settings.sections.HomeGesture
import io.github.aj4abinjacob.chikku.theme.SurfaceColors
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PreviewAppearanceGesturesTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test fun iconPracticeResolvesInheritedDefaultsExplicitNoneAndHomeFallback() {
        val key = AppKey("sample.app", "sample.app.Main", 0)
        val settings = LauncherSettings(gestures = GestureSettings(swipeUp = GestureAction.OpenDrawer, swipeDown = GestureAction.OpenSearch))
        val inherited = AppCustomization(key, swipeUpAction = GestureAction.LockScreen, swipeDownAction = GestureAction.Recents)
        assertThat(iconPracticeSwipeActions(key, ItemActions(), inherited, settings)).isEqualTo(GestureAction.LockScreen to GestureAction.Recents)
        assertThat(iconPracticeSwipeActions(key, ItemActions(swipeUp = GestureAction.None, swipeDown = GestureAction.OpenLauncherSettings), inherited, settings))
            .isEqualTo(GestureAction.OpenDrawer to GestureAction.OpenLauncherSettings)
        val buttonOnly = settings.copy(drawer = settings.drawer.copy(openMethod = DrawerOpenMethod.BUTTON))
        assertThat(iconPracticeSwipeActions(key, ItemActions(), null, buttonOnly)).isEqualTo(GestureAction.None to GestureAction.OpenSearch)
    }

    @Test fun folderPracticeRespectsDisabledSwipesAndCoverFallback() {
        val cover = ItemActions(tap = GestureAction.OpenSearch, swipeDown = GestureAction.LockScreen)
        assertThat(folderPracticeSwipeActions(cover, true)).isEqualTo(GestureAction.OpenFolder to GestureAction.LockScreen)
        assertThat(folderPracticeSwipeActions(cover, false)).isEqualTo(null to null)
        assertThat(folderPracticeSwipeActions(cover.copy(swipeUp = GestureAction.OpenFolder), false)).isEqualTo(GestureAction.OpenFolder to null)
    }

    @Test fun practiceResolvesDrawerRestrictionAndHomeButtonScopeWithoutExecutingActions() {
        val settings = LauncherSettings(
            drawer = DrawerSettings(openMethod = DrawerOpenMethod.BUTTON),
            gestures = GestureSettings(swipeUp = GestureAction.OpenDrawer, doubleTap = GestureAction.LockScreen,
                homeGesture = GestureAction.Recents, homeButtonOnlyOnDefaultPage = true),
        )
        assertThat(practiceGestureAction(settings, HomeGesture.SWIPE_UP)).isEqualTo(GestureAction.None)
        assertThat(practiceGestureAction(settings, HomeGesture.DOUBLE_TAP)).isEqualTo(GestureAction.LockScreen)
        assertThat(practiceGestureAction(settings, HomeGesture.HOME_BUTTON, false)).isEqualTo(GestureAction.GoToDefaultPage)
        assertThat(practiceGestureAction(settings, HomeGesture.HOME_BUTTON, true)).isEqualTo(GestureAction.Recents)
    }

    @Test fun menuSamplesRespectIndependentHomeAndDrawerChoices() {
        val look = LookFeelSettings(menuShowHideHome = false, menuShowStoreHome = true, menuShowUninstallHome = false,
            menuShowHideDrawer = true, menuShowStoreDrawer = false, menuShowUninstallDrawer = true)
        assertThat(previewMenuActions(look, false).map { it.title }).containsExactly(R.string.app_menu_edit,
            R.string.app_menu_app_info, R.string.app_menu_store, R.string.app_menu_remove).inOrder()
        assertThat(previewMenuActions(look, true).map { it.title }).containsExactly(R.string.app_menu_edit,
            R.string.app_menu_app_info, R.string.app_menu_hide, R.string.app_menu_uninstall).inOrder()
    }

    @Test fun missingPackStillProducesAllSamplesWithoutResettingStoredSelection() {
        val look = LookFeelSettings(iconPackPackage = "missing.example.pack")
        val sample = loadAppearanceSamples(context, look, 48)
        assertThat(sample.missingPack).isTrue()
        assertThat(sample.icons).hasSize(4)
        assertThat(sample.packIconCount).isEqualTo(0)
        assertThat(look.iconPackPackage).isEqualTo("missing.example.pack")
    }

    @Test fun folderPreviewUsesCustomColorAndTransparentPanelContrast() {
        val opaque = LauncherSettings(folders = FolderSettings(backgroundColor = Color.WHITE, backgroundAlpha = 1f))
        val custom = folderNightSampleColors(SurfaceColors.BLACK, opaque)
        assertThat(custom.background).isEqualTo(Color.WHITE)
        assertThat(custom.content).isEqualTo(SurfaceColors.LIGHT.onSurface)
        val translucent = folderNightSampleColors(SurfaceColors.BLACK, opaque.copy(folders = opaque.folders.copy(backgroundAlpha = .25f)))
        assertThat(Color.alpha(translucent.background)).isEqualTo(63)
        assertThat(translucent.content).isEqualTo(SurfaceColors.DARK.onSurface)
    }

    @Test fun touchPadReportsSwipeButCancelledTouchAndDiagonalDoNotEmit() {
        val observed = mutableListOf<HomeGesture>()
        val pad = GesturePracticePad(context).apply { onGesture = { observed += it } }
        touch(pad, MotionEvent.ACTION_DOWN, 50f, 200f, 0)
        touch(pad, MotionEvent.ACTION_MOVE, 50f, 50f, 30)
        touch(pad, MotionEvent.ACTION_UP, 50f, 50f, 60)
        assertThat(observed).containsExactly(HomeGesture.SWIPE_UP)
        observed.clear()
        touch(pad, MotionEvent.ACTION_DOWN, 50f, 200f, 100)
        touch(pad, MotionEvent.ACTION_MOVE, 50f, 50f, 130)
        touch(pad, MotionEvent.ACTION_CANCEL, 50f, 50f, 150)
        touch(pad, MotionEvent.ACTION_UP, 50f, 50f, 160)
        touch(pad, MotionEvent.ACTION_DOWN, 50f, 200f, 200)
        touch(pad, MotionEvent.ACTION_MOVE, 150f, 100f, 230)
        touch(pad, MotionEvent.ACTION_UP, 150f, 100f, 260)
        assertThat(observed).isEmpty()
    }

    @Test fun iconPadIgnoresHorizontalSwipesAndDoubleTaps() {
        val observed = mutableListOf<HomeGesture>()
        val pad = GesturePracticePad(context, verticalOnly = true).apply { onGesture = { observed += it } }
        touch(pad, MotionEvent.ACTION_DOWN, 50f, 50f, 0)
        touch(pad, MotionEvent.ACTION_MOVE, 250f, 50f, 30)
        touch(pad, MotionEvent.ACTION_UP, 250f, 50f, 60)
        touch(pad, MotionEvent.ACTION_DOWN, 50f, 50f, 100)
        touch(pad, MotionEvent.ACTION_UP, 50f, 50f, 120)
        touch(pad, MotionEvent.ACTION_DOWN, 50f, 50f, 180)
        touch(pad, MotionEvent.ACTION_UP, 50f, 50f, 200)
        assertThat(observed).isEmpty()
    }

    @Test fun thirdFingerCancelsTwoFingerRecognitionUntilTheNextGesture() {
        val observed = mutableListOf<HomeGesture>()
        val pad = GesturePracticePad(context).apply { onGesture = { observed += it } }
        touch(pad, MotionEvent.ACTION_DOWN, 50f, 100f, 0)
        multiTouch(pad, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), listOf(50f to 100f, 250f to 100f), 10)
        multiTouch(pad, MotionEvent.ACTION_MOVE, listOf(120f to 100f, 180f to 100f), 30)
        multiTouch(pad, MotionEvent.ACTION_POINTER_DOWN or (2 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), listOf(120f to 100f, 180f to 100f, 150f to 180f), 40)
        multiTouch(pad, MotionEvent.ACTION_MOVE, listOf(50f to 100f, 250f to 100f), 50)
        touch(pad, MotionEvent.ACTION_UP, 50f, 100f, 60)
        assertThat(observed).isEmpty()
        touch(pad, MotionEvent.ACTION_DOWN, 50f, 50f, 100)
        touch(pad, MotionEvent.ACTION_UP, 50f, 220f, 150)
        assertThat(observed).containsExactly(HomeGesture.SWIPE_DOWN)
    }

    private fun touch(pad: GesturePracticePad, action: Int, x: Float, y: Float, time: Long) {
        MotionEvent.obtain(0, time, action, x, y, 0).let { event -> pad.onTouchEvent(event); event.recycle() }
    }

    private fun multiTouch(pad: GesturePracticePad, action: Int, points: List<Pair<Float, Float>>, time: Long) {
        val properties = points.indices.map { id -> MotionEvent.PointerProperties().apply { this.id = id; toolType = MotionEvent.TOOL_TYPE_FINGER } }.toTypedArray()
        val coordinates = points.map { (px, py) -> MotionEvent.PointerCoords().apply { x = px; y = py; pressure = 1f; size = 1f } }.toTypedArray()
        MotionEvent.obtain(0, time, action, points.size, properties, coordinates, 0, 0, 1f, 1f, 0, 0, 0, 0)
            .let { event -> pad.onTouchEvent(event); event.recycle() }
    }
}
