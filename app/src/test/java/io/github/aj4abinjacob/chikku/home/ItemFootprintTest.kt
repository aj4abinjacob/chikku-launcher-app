package io.github.aj4abinjacob.chikku.home

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.view.ContextThemeWrapper
import android.view.View
import androidx.core.graphics.ColorUtils
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.settings.IconShape
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.core.model.settings.LookFeelSettings
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A long-press footprint hugs the icon it belongs to; items without one keep the whole-view outline. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ItemFootprintTest {
    private val context: Context = ContextThemeWrapper(
        ApplicationProvider.getApplicationContext<Context>(), R.style.Theme_Chikku_Home,
    )
    private val res = context.resources

    private fun icon(height: Int) = AppIconView(context, AppIconView.Style.DRAWER).apply {
        label = "Sample"
        icon = ColorDrawable(Color.RED)
        measure(View.MeasureSpec.makeMeasureSpec(280, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        layout(0, 0, 280, height)
    }

    @Test
    fun footprintHugsTheIconWithCornersFollowingItsShape() {
        val view = icon(height = 600)
        val bounds = Rect().also(view::iconBounds)
        val pad = res.getDimension(R.dimen.home_footprint_icon_padding)
        val circle = footprintShape(view, IconShape.CIRCLE)
        assertThat(circle.rect).isEqualTo(RectF(bounds).apply { inset(-pad, -pad) })
        assertThat(circle.radius).isEqualTo(bounds.width() / 2f + pad)
        assertThat(footprintShape(view, IconShape.SQUARE).radius).isLessThan(circle.radius)
        // Not the (much taller) drawer cell.
        assertThat(circle.rect.height()).isLessThan(view.height / 2f)
    }

    @Test
    fun itemsWithoutAnIconKeepTheInsetViewOutline() {
        val widget = View(context).apply { layout(0, 0, 400, 300) }
        val inset = res.getDimension(R.dimen.home_footprint_inset)
        val shape = footprintShape(widget, IconShape.CIRCLE)
        assertThat(shape.rect).isEqualTo(RectF(inset, inset, 400 - inset, 300 - inset))
        assertThat(shape.radius).isEqualTo(res.getDimension(R.dimen.home_footprint_radius))
        assertThat(shape.icon).isNull()
    }

    @Test
    fun contentEndsUnderTheLabelNotAtTheBottomOfATallCell() {
        val view = icon(height = 600)
        val bounds = Rect().also(view::iconBounds)
        assertThat(view.contentBottom()).isGreaterThan(bounds.bottom)
        assertThat(view.contentBottom()).isLessThan(600)
        view.showLabel = false
        view.measure(View.MeasureSpec.makeMeasureSpec(280, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 280, 600)
        assertThat(view.contentBottom()).isEqualTo(Rect().also(view::iconBounds).bottom)
    }

    @Test
    fun anIconsFootprintIsATileInTheMenuCardsColors() {
        val settings = LauncherSettings()
        val menu = LauncherMenuStyle(context, settings).colors
        val colors = footprintColors(context, settings, hasIcon = true)
        assertThat(colors.fill).isEqualTo(LauncherMenuStyle.cardFill(menu, customBackground = false))
        assertThat(Color.alpha(colors.fill)).isEqualTo(0xFF)
        assertThat(colors.stroke).isEqualTo(LauncherMenuStyle.cardEdge(menu))

        val custom = settings.copy(lookFeel = LookFeelSettings(menuBackgroundColor = 0xFF336699.toInt()))
        assertThat(footprintColors(context, custom, hasIcon = true).fill).isEqualTo(0xFF336699.toInt())
    }

    @Test
    fun aWidgetsFootprintKeepsTheAccentOutline() {
        val accent = 0xFF00897B.toInt()
        val settings = LauncherSettings().copy(lookFeel = LookFeelSettings(accentColor = accent))
        val colors = footprintColors(context, settings, hasIcon = false)
        assertThat(colors.stroke).isEqualTo(accent)
        assertThat(colors.fill).isEqualTo(ColorUtils.setAlphaComponent(accent, FOOTPRINT_FILL_ALPHA))
    }
}
