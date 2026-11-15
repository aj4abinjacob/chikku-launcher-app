package io.github.aj4abinjacob.chikku.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.ContextThemeWrapper
import android.view.View
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.settings.DockLabelFont
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestHomeApplication::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LabelPresentationTest {
    private val context: Context = ContextThemeWrapper(
        ApplicationProvider.getApplicationContext<Context>(), R.style.Theme_Chikku_Home,
    )

    private fun icon() = AppIconView(context, AppIconView.Style.DRAWER).apply {
        label = "A long application label that needs room"
        contentDescription = label
        icon = ColorDrawable(Color.RED)
    }

    private fun layout(view: AppIconView, width: Int = 280) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }

    private fun bitmap(view: AppIconView): Bitmap = Bitmap.createBitmap(
        view.width, view.height, Bitmap.Config.ARGB_8888,
    ).also { view.draw(Canvas(it)) }

    private fun Bitmap.containsColor(color: Int): Boolean =
        (0 until height).any { y -> (0 until width).any { x -> getPixel(x, y) == color } }

    @Test
    fun multilineAndLargerFontReserveMoreHeightAndHiddenLabelsKeepAccessibleName() {
        val view = icon()
        layout(view)
        val oneLineHeight = view.height
        view.labelSingleLine = false
        layout(view)
        assertThat(view.height).isGreaterThan(oneLineHeight)
        val twoLineHeight = view.height
        view.labelScale = 1.5f
        layout(view)
        assertThat(view.height).isGreaterThan(twoLineHeight)
        view.showLabel = false
        layout(view)
        assertThat(view.height).isLessThan(oneLineHeight)
        assertThat(view.contentDescription).isEqualTo(view.label)
    }

    @Test
    fun recycledDrawerCellClearsCustomColorFontAndLineMode() {
        val view = icon()
        DrawerAppearance(labelColor = Color.BLUE, labelScale = 1.4f,
            labelFont = DockLabelFont.CONDENSED, labelShadow = true, labelSingleLine = false).applyTo(view)
        layout(view)
        assertThat(bitmap(view).containsColor(Color.BLUE)).isTrue()
        DrawerAppearance().applyTo(view)
        layout(view)
        assertThat(bitmap(view).containsColor(Color.BLUE)).isFalse()
        assertThat(bitmap(view).containsColor(ContextCompat.getColor(context, R.color.chikku_on_drawer))).isTrue()
        assertThat(view.labelScale).isEqualTo(1f)
        assertThat(view.labelFont).isEqualTo(DockLabelFont.NORMAL)
        assertThat(view.labelShadow).isFalse()
        assertThat(view.labelSingleLine).isTrue()
    }

    @Test
    fun listRowPlacesIconAtLeadingEdgeAndMirrorsWhenDirectionChanges() {
        val view = icon()
        layout(view)
        val gridHeight = view.height
        DrawerAppearance(listMode = true).applyTo(view)
        layout(view)
        assertThat(view.height).isLessThan(gridHeight)
        assertThat(view.icon!!.bounds.left).isEqualTo(view.paddingLeft)
        val bitmap = bitmap(view)
        val labelColor = ContextCompat.getColor(context, R.color.chikku_on_drawer)
        assertThat((view.icon!!.bounds.right until view.width).any { x ->
            (0 until view.height).any { y -> bitmap.getPixel(x, y) == labelColor }
        }).isTrue()
        view.layoutDirection = View.LAYOUT_DIRECTION_RTL
        layout(view)
        assertThat(view.icon!!.bounds.right).isEqualTo(view.width - view.paddingRight)
        val rtlBitmap = bitmap(view)
        assertThat((0 until view.icon!!.bounds.left).any { x ->
            (0 until view.height).any { y -> rtlBitmap.getPixel(x, y) == labelColor }
        }).isTrue()
        assertThat(view.contentDescription).isEqualTo(view.label)
    }

    @Test
    fun invalidLabelScaleCannotBreakCellMeasurement() {
        val view = icon()
        listOf(Float.NaN, Float.POSITIVE_INFINITY, 0f, -1f).forEach { scale ->
            view.labelScale = scale
            layout(view)
            assertThat(view.labelScale).isEqualTo(1f)
            assertThat(view.height).isGreaterThan(0)
        }
    }
}
