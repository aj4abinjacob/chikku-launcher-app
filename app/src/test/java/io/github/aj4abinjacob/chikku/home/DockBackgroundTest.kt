package io.github.aj4abinjacob.chikku.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.settings.DockBackground
import io.github.aj4abinjacob.chikku.core.model.settings.DockCorner
import io.github.aj4abinjacob.chikku.core.model.settings.DockCornerShape
import io.github.aj4abinjacob.chikku.core.model.settings.DockSettings
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestHomeApplication::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DockBackgroundTest {
    private val context: Context = ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_Chikku_Home)

    private fun draw(corner: DockCorner): Bitmap {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val drawable = checkNotNull(DockBackgroundStyle.create(context, DockSettings(
            background = DockBackground.CUSTOM, backgroundColor = Color.RED, backgroundAlpha = 1f, topLeftCorner = corner,
        )))
        drawable.setBounds(0, 0, 100, 100)
        drawable.draw(Canvas(bitmap))
        return bitmap
    }

    @Test
    fun individualCutAndRoundCornersLeaveTheOtherCornersIntact() {
        val cut = draw(DockCorner(1f, DockCornerShape.CUT))
        val round = draw(DockCorner(1f, DockCornerShape.ROUND))
        assertThat(cut.getPixel(15, 15)).isEqualTo(Color.TRANSPARENT)
        assertThat(round.getPixel(15, 15)).isEqualTo(Color.RED)
        assertThat(cut.getPixel(90, 10)).isEqualTo(Color.RED)
        assertThat(cut.getPixel(50, 50)).isEqualTo(Color.RED)
    }

    @Test
    fun disablingBackgroundPreservesItsStoredShapeAndColor() {
        val preferences = DockSettings(background = DockBackground.CUSTOM, backgroundColor = Color.RED, backgroundEnabled = false)
        assertThat(DockBackgroundStyle.create(context, preferences)).isNull()
        assertThat(DockBackgroundStyle.create(context, preferences.copy(backgroundEnabled = true))).isNotNull()
    }

    @Test
    fun lightOpaqueDocksGetDarkLabelsOtherwiseWallpaperLabelsStay() {
        fun label(color: Int?, alpha: Float, enabled: Boolean = true) = DockBackgroundStyle.labelColor(context, DockSettings(
            background = DockBackground.ROUNDED, backgroundEnabled = enabled, backgroundColor = color, backgroundAlpha = alpha,
        ))
        assertThat(label(Color.WHITE, 1f)).isNotNull()
        assertThat(label(Color.WHITE, 0.3f)).isNull() // mostly wallpaper shows through
        assertThat(label(Color.WHITE, 1f, enabled = false)).isNull()
        assertThat(label(null, 1f)).isNull() // default dark dock
        assertThat(label(Color.BLACK, 1f)).isNull()
    }
}
