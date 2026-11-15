package io.github.aj4abinjacob.chikku.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.ContextThemeWrapper
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.settings.DockBackground
import io.github.aj4abinjacob.chikku.core.model.settings.DockSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Activity-held drawables reuse their buffers and the dock photo is released and reloaded on trim. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestHomeApplication::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeMemoryTrimTest {
    private val context: Context = ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_Chikku_Home)

    @Test
    fun dockPhotoIsReleasedOnTrimAndReloadedBeforeHomeShowsAgain() {
        val dock = CellLayout(context)
        val slot = FrameLayout(context).apply { addView(dock) }
        var loads = 0
        val controller = DockSurfaceController(dock, slot, FrameLayout(context), imageScope = CoroutineScope(Dispatchers.Unconfined)) {
            loads++
            Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        }
        val settings = DockSettings(backgroundEnabled = true, background = DockBackground.ROUNDED, backgroundImageUri = "content://picked/photo")
        controller.render(settings, true)
        assertThat(loads).isEqualTo(1)
        assertThat(dock.background).isInstanceOf(DockImageDrawable::class.java)
        controller.render(settings, true)
        assertThat(loads).isEqualTo(1)

        controller.trimMemory()
        assertThat(controller.imageTrimmed).isTrue()
        assertThat(dock.background).isNotNull() // the shape stays; only the photo is released
        assertThat(dock.background).isNotInstanceOf(DockImageDrawable::class.java)

        controller.restoreAfterTrim()
        assertThat(loads).isEqualTo(2)
        assertThat(controller.imageTrimmed).isFalse()
        assertThat(dock.background).isInstanceOf(DockImageDrawable::class.java)
        controller.restoreAfterTrim()
        assertThat(loads).isEqualTo(2)
    }

    @Test
    fun trimWithoutPhotoKeepsBackground() {
        val dock = CellLayout(context)
        val controller = DockSurfaceController(dock, FrameLayout(context).apply { addView(dock) }, FrameLayout(context))
        controller.render(DockSettings(), true)
        val background = dock.background
        controller.trimMemory()
        assertThat(controller.imageTrimmed).isFalse()
        assertThat(dock.background).isSameInstanceAs(background)
    }

    @Test
    fun dockPhotoIsComposedOncePerSize() {
        val photo = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val drawable = DockImageDrawable(GradientDrawable().apply { setColor(Color.BLACK) }, photo).apply { setBounds(0, 0, 50, 20) }
        val target = Canvas(Bitmap.createBitmap(100, 60, Bitmap.Config.ARGB_8888))
        repeat(3) { drawable.draw(target) }
        assertThat(drawable.compositions).isEqualTo(1)
        drawable.setBounds(0, 0, 60, 20)
        drawable.draw(target)
        drawable.setBounds(0, 0, 60, 20)
        drawable.draw(target)
        assertThat(drawable.compositions).isEqualTo(2)
    }

    @Test
    fun dockPhotoAtOffsetBoundsKeepsMask() {
        val photo = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val mask = GradientDrawable().apply { setColor(0x800000FF.toInt()); cornerRadius = 16f }
        val drawable = DockImageDrawable(mask, photo).apply { setBounds(10, 20, 110, 80) }
        val target = Bitmap.createBitmap(120, 90, Bitmap.Config.ARGB_8888)
        drawable.draw(Canvas(target))
        assertThat(Color.alpha(target.getPixel(60, 50))).isWithin(1).of(128)
        assertThat(Color.red(target.getPixel(60, 50))).isEqualTo(255)
        assertThat(Color.alpha(target.getPixel(10, 20))).isEqualTo(0)
        assertThat(Color.alpha(target.getPixel(5, 5))).isEqualTo(0)
    }

    @Test
    fun edgeShadowBuildsShadersOncePerBounds() {
        val shadow = ScreenEdgeShadow(30f).apply { setBounds(0, 0, 100, 300) }
        val bitmap = Bitmap.createBitmap(100, 300, Bitmap.Config.ARGB_8888)
        repeat(3) { shadow.draw(Canvas(bitmap)) }
        assertThat(shadow.shaderBuilds).isEqualTo(1)
        assertThat(Color.alpha(bitmap.getPixel(50, 0))).isGreaterThan(0x50)
        assertThat(Color.alpha(bitmap.getPixel(50, 150))).isEqualTo(0)
        assertThat(Color.alpha(bitmap.getPixel(50, 299))).isGreaterThan(0x50)
        shadow.setBounds(0, 0, 100, 400)
        shadow.draw(Canvas(Bitmap.createBitmap(100, 400, Bitmap.Config.ARGB_8888)))
        assertThat(shadow.shaderBuilds).isEqualTo(2)
    }
}
