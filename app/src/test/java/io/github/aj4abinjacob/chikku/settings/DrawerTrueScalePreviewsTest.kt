package io.github.aj4abinjacob.chikku.settings

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.badges.BadgeOverlay
import io.github.aj4abinjacob.chikku.core.model.settings.BadgeSettings
import io.github.aj4abinjacob.chikku.core.model.settings.BadgeStyle
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerSearchBarPosition
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerSearchBarStyle
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerSettings
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerStyle
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerTabStyle
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.core.model.settings.NightSettings
import io.github.aj4abinjacob.chikku.home.AppIconView
import io.github.aj4abinjacob.chikku.home.TestHomeApplication
import io.github.aj4abinjacob.chikku.settings.components.CHIKKU_DEFAULT_ACCENT
import io.github.aj4abinjacob.chikku.settings.drawer.DrawerGridPreview
import io.github.aj4abinjacob.chikku.settings.drawer.DrawerGridPreviewFrame
import io.github.aj4abinjacob.chikku.settings.drawer.DrawerSearchSampleView
import io.github.aj4abinjacob.chikku.settings.drawer.DrawerTabSampleView
import io.github.aj4abinjacob.chikku.settings.drawer.drawerSearchSampleColors
import io.github.aj4abinjacob.chikku.settings.previews.DrawerSampleColors
import io.github.aj4abinjacob.chikku.settings.sections.BadgeSamplePreview
import io.github.aj4abinjacob.chikku.theme.SurfaceColors
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import kotlin.math.roundToInt

/** App drawer Grid, Tabs and Search pages and the Badges page: true-size previews follow their settings. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestHomeApplication::class, qualifiers = "w411dp-h891dp-xxhdpi")
class DrawerTrueScalePreviewsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val density = context.resources.displayMetrics.density
    private val colors = DrawerSampleColors(0xFF1B1B1F.toInt(), 0xFFF2F0F4.toInt())
    private fun dp(value: Float) = (value * density).roundToInt()

    private fun render(frame: DrawerGridPreviewFrame, drawer: DrawerSettings, iconScale: Float = drawer.iconScale,
        fullGrid: Boolean = false) {
        frame.show(drawer, iconScale, colors, fullGrid)
        frame.measure(View.MeasureSpec.makeMeasureSpec(dp(411f), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        frame.layout(0, 0, frame.measuredWidth, frame.measuredHeight)
    }

    private fun shown(frame: DrawerGridPreviewFrame) = frame.cells.filter { it.visibility == View.VISIBLE }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun fullGridShowsAllFiveFittedRowsIncludingTheLastRowsPixels() {
        val frame = DrawerGridPreviewFrame(context)
        // The reported 5 by 5 configuration uses fitted spacing, not automatic content height.
        val drawer = DrawerSettings(columns = 5, rows = 5, automaticRowSpacing = false)
        render(frame, drawer, fullGrid = true)
        assertThat(frame.layout!!.visibleRows).isEqualTo(5)
        assertThat(frame.visibleCells).isEqualTo(25)
        assertThat(frame.drawScale).isGreaterThan(0f)
        assertThat(frame.drawScale).isLessThan(1f)
        val bitmap = Bitmap.createBitmap(frame.measuredWidth, frame.measuredHeight, Bitmap.Config.ARGB_8888)
        frame.draw(Canvas(bitmap))
        // Check actual drawing, not just allocated cells: unscaled parent clipping used to
        // remove lower rows even if a canvas transform or a larger sample count was supplied.
        for (row in 0 until 5) {
            val cell = shown(frame)[row * 5]
            val icon = cell.icon!!.bounds
            val x = (frame.drawOffsetX + (cell.left + icon.exactCenterX()) * frame.drawScale).roundToInt()
            val y = (frame.drawOffsetY + (cell.top + icon.exactCenterY()) * frame.drawScale).roundToInt()
            assertThat(x).isAtLeast(0)
            assertThat(x).isLessThan(bitmap.width)
            assertThat(y).isAtLeast(0)
            assertThat(y).isLessThan(bitmap.height)
            assertThat(bitmap.getPixel(x, y)).isNotEqualTo(colors.background)
        }
    }

    @Test
    fun fittedRowDraftsUpdateTheWholeGridWithoutMovingTheControls() {
        val frame = DrawerGridPreviewFrame(context)
        render(frame, DrawerSettings(columns = 5, rows = 5, automaticRowSpacing = false), fullGrid = true)
        val height = frame.measuredHeight
        val slot = frame.rowHeightPx
        for (rows in 3..8) {
            render(frame, DrawerSettings(columns = 5, rows = rows, automaticRowSpacing = false), fullGrid = true)
            assertThat(frame.layout!!.visibleRows).isEqualTo(rows)
            assertThat(frame.visibleCells).isEqualTo(5 * rows)
            assertThat(frame.measuredHeight).isEqualTo(height)
            val last = shown(frame).last()
            assertThat(frame.drawOffsetY + (last.bottom * frame.drawScale)).isAtMost(height.toFloat())
        }
        assertThat(frame.rowHeightPx).isLessThan(slot)
        // A mode-only change must remeasure even though the draft settings are identical.
        val drawer = DrawerSettings(columns = 5, rows = 8, automaticRowSpacing = false)
        render(frame, drawer, fullGrid = false)
        assertThat(frame.drawScale).isEqualTo(1f)
        assertThat(frame.visibleCells).isLessThan(40)
        assertThat(frame.measuredHeight).isEqualTo(height)
        render(frame, drawer, fullGrid = true)
        assertThat(frame.visibleCells).isEqualTo(40)
        assertThat(frame.measuredHeight).isEqualTo(height)
    }

    @Test
    fun fullGridFitsConstrainedHeightWithLargeTextAndRtl() {
        val configuration = Configuration(context.resources.configuration).apply { fontScale = 2f }
        val largeTextContext = context.createConfigurationContext(configuration)
        val frame = DrawerGridPreviewFrame(largeTextContext).apply { layoutDirection = View.LAYOUT_DIRECTION_RTL }
        frame.show(DrawerSettings(columns = 5, rows = 5, automaticRowSpacing = false,
            iconScale = 1.5f, labelScale = 1.5f, labelSingleLine = false), 1.5f, colors, fullGrid = true)
        frame.measure(View.MeasureSpec.makeMeasureSpec(dp(411f), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(dp(112f), View.MeasureSpec.AT_MOST))
        frame.layout(0, 0, frame.measuredWidth, frame.measuredHeight)
        assertThat(frame.measuredHeight).isEqualTo(dp(112f))
        assertThat(frame.visibleCells).isEqualTo(frame.layout!!.capacity)
        assertThat(frame.drawOffsetY + frame.sceneHeightPx * frame.drawScale).isAtMost(frame.measuredHeight.toFloat())
        val cells = shown(frame)
        assertThat(cells[0].left).isGreaterThan(cells[1].left)
        assertThat(cells.all { !it.isFocusable && !it.isClickable }).isTrue()
        frame.measure(View.MeasureSpec.makeMeasureSpec(dp(411f), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(dp(96f), View.MeasureSpec.EXACTLY))
        assertThat(frame.measuredHeight).isEqualTo(dp(96f))
    }

    @Test
    fun gridIsDrawnAtTrueScaleAcrossTheFullWidth() {
        val frame = DrawerGridPreviewFrame(context)
        render(frame, DrawerSettings(columns = 4, showFastScroller = false))
        val cells = shown(frame)
        // Four true-size cells span the grid (the 411dp width less the drawer's 8dp sides).
        assertThat(frame.layout!!.columns).isEqualTo(4)
        assertThat(cells[0].measuredWidth).isEqualTo(((411f - 16f) * density / 4).toInt())
        assertThat(cells[0].scaleX).isEqualTo(1f)
        assertThat(cells[0].measuredHeight).isAtLeast(AppIconView.desiredHeight(context, AppIconView.Style.DRAWER))
        assertThat(cells.all { !it.isFocusable && !it.isClickable }).isTrue()
    }

    @Test
    fun columnsAndIconSizeFollowTheSettingWithoutMovingThePreviewHeight() {
        val frame = DrawerGridPreviewFrame(context)
        render(frame, DrawerSettings(columns = 4, iconScale = .5f))
        val height = frame.measuredHeight
        val narrowCell = shown(frame)[0].measuredWidth
        assertThat(shown(frame)[0].iconScale).isEqualTo(.5f)

        render(frame, DrawerSettings(columns = 6, iconScale = 1.5f, labelScale = 1.5f, labelSingleLine = false))
        assertThat(frame.layout!!.columns).isEqualTo(6)
        assertThat(shown(frame)[0].iconScale).isEqualTo(1.5f)
        assertThat(narrowCell - shown(frame)[0].measuredWidth).isAtLeast(dp(20f))
        assertThat(frame.measuredHeight).isEqualTo(height)

        render(frame, DrawerSettings(columns = 6, showLabels = false, style = DrawerStyle.LIST))
        assertThat(frame.measuredHeight).isEqualTo(height)
        assertThat(frame.layout!!.columns).isEqualTo(1)
        assertThat(shown(frame)[0].listMode).isTrue()
    }

    @Test
    fun eachVisibleRowsStepChangesTheRowSpacingByAVisibleAmount() {
        val frame = DrawerGridPreviewFrame(context)
        render(frame, DrawerSettings(automaticRowSpacing = true))
        val height = frame.measuredHeight
        val automatic = frame.rowHeightPx
        var previous = Int.MAX_VALUE
        for (rows in 3..8) {
            render(frame, DrawerSettings(automaticRowSpacing = false, rows = rows))
            // Neighbouring steps differ by at least 8dp at true scale (7 vs 8 rows is the closest pair).
            assertThat(previous - frame.rowHeightPx).isAtLeast(dp(8f))
            assertThat(frame.measuredHeight).isEqualTo(height)
            previous = frame.rowHeightPx
        }
        // Turning on "fit rows" with few rows spreads them well beyond the content-sized rows.
        render(frame, DrawerSettings(automaticRowSpacing = false, rows = 4))
        assertThat(frame.rowHeightPx - automatic).isAtLeast(dp(40f))
        // Cells sit in their slots, the first row trimmed up as the drawer trims it.
        val first = shown(frame)[0]
        assertThat(first.measuredHeight).isEqualTo(frame.rowHeightPx)
        assertThat(first.top).isLessThan(0)
    }

    @Test
    fun matchDesktopSizeUsesTheHomeIconSize() {
        // A fresh first composition per value: recomposing an existing view is unreliable under
        // Robolectric once an earlier test in the JVM created Compose's main dispatcher.
        fun frameFor(drawer: DrawerSettings): DrawerGridPreviewFrame {
            val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
            activity.setContent { DrawerGridPreview(drawer, NightSettings(), desktopIconScale = 1.3f) }
            ShadowLooper.idleMainLooper()
            return activity.findViewById<View>(android.R.id.content).find<DrawerGridPreviewFrame>()!!
        }
        val own = frameFor(DrawerSettings(iconScale = .6f, matchDesktopSize = false))
        assertThat(shown(own)[0].iconScale).isEqualTo(.6f)
        val height = own.height
        val matched = frameFor(DrawerSettings(iconScale = .6f, matchDesktopSize = true))
        assertThat(shown(matched)[0].iconScale).isEqualTo(1.3f)
        assertThat(matched.height).isEqualTo(height)
    }

    @Test
    fun searchSampleDrawsEachFieldStyleAsTheDrawerDoes() {
        val search = SurfaceColors.DARK
        assertThat(drawerSearchSampleColors(search, DrawerSettings(searchBarStyle = DrawerSearchBarStyle.SUBTLE), colors).outline).isNull()
        val outlined = drawerSearchSampleColors(search, DrawerSettings(searchBarStyle = DrawerSearchBarStyle.OUTLINED), colors).outline!!
        val strong = drawerSearchSampleColors(search, DrawerSettings(searchBarStyle = DrawerSearchBarStyle.HIGH_CONTRAST), colors).outline!!
        assertThat(outlined ushr 24).isEqualTo(0x29)
        assertThat(strong ushr 24).isEqualTo(0x80)

        val view = DrawerSearchSampleView(context)
        fun measure(drawer: DrawerSettings): Int {
            view.bind(drawer, colors, drawerSearchSampleColors(search, drawer, colors))
            view.measure(View.MeasureSpec.makeMeasureSpec(dp(411f), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            return view.measuredHeight
        }
        val height = measure(DrawerSettings())
        assertThat(view.field.text.toString()).isEqualTo(context.getString(R.string.home_search_hint))
        assertThat(view.field.background).isInstanceOf(GradientDrawable::class.java)
        assertThat(measure(DrawerSettings(searchBarPosition = DrawerSearchBarPosition.HIDDEN))).isEqualTo(height)
        assertThat(view.field.text.toString()).isEqualTo(context.getString(R.string.preview_drawer_search_hidden))
    }

    @Test
    fun tabSampleShowsTheDrawerTabsForTheChosenOptionsAtAStableHeight() {
        val view = DrawerTabSampleView(context)
        fun titles(drawer: DrawerSettings): List<String> {
            view.bind(drawer, colors)
            view.measure(View.MeasureSpec.makeMeasureSpec(dp(411f), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            return (0 until view.row.childCount).map { (view.row.getChildAt(it) as TextView).text.toString() }
        }
        assertThat(titles(DrawerSettings(groupsEnabled = false, showWorkTab = false)))
            .containsExactly(context.getString(R.string.preview_drawer_tabs_off))
        val height = view.measuredHeight
        assertThat(titles(DrawerSettings(groupsEnabled = true, showWorkTab = false, tabStyle = DrawerTabStyle.COMPACT))).containsExactly(
            context.getString(R.string.drawer_tab_all), context.getString(R.string.preview_df_group_name)).inOrder()
        assertThat(view.measuredHeight).isEqualTo(height)
        assertThat(titles(DrawerSettings(groupsEnabled = true, showWorkTab = true, tabStyle = DrawerTabStyle.SEGMENTED))).containsExactly(
            context.getString(R.string.drawer_tab_apps), context.getString(R.string.drawer_tab_work),
            context.getString(R.string.preview_df_group_name)).inOrder()
        assertThat(view.measuredHeight).isEqualTo(height)
        // Segmented tabs share the width; compact ones wrap their titles.
        assertThat((view.row.getChildAt(0).layoutParams as android.widget.LinearLayout.LayoutParams).weight).isEqualTo(1f)
        titles(DrawerSettings(groupsEnabled = true, showWorkTab = true, tabStyle = DrawerTabStyle.COMPACT))
        assertThat((view.row.getChildAt(0).layoutParams as android.widget.LinearLayout.LayoutParams).weight).isEqualTo(0f)
    }

    @Test
    fun badgeSampleUsesTheLauncherBadgeColourAndIsNotFocusable() {
        fun iconFor(settings: LauncherSettings): AppIconView {
            val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
            activity.setContent { BadgeSamplePreview(settings, sampleCount = 3) }
            ShadowLooper.idleMainLooper()
            return activity.findViewById<View>(android.R.id.content).find<AppIconView>()!!
        }
        val settings = LauncherSettings(badges = BadgeSettings(style = BadgeStyle.COUNT))
        val icon = iconFor(settings)
        assertThat(icon.badge!!.color).isEqualTo(CHIKKU_DEFAULT_ACCENT)
        assertThat(icon.isFocusable).isFalse()
        val green = iconFor(settings.copy(badges = settings.badges.copy(dotColor = 0xFF2E9D6A.toInt())))
        assertThat((green.badge as BadgeOverlay).color).isEqualTo(0xFF2E9D6A.toInt())
    }

    private inline fun <reified T : View> View.find(): T? = findOf(T::class.java)

    private fun <T : View> View.findOf(type: Class<T>): T? = when {
        type.isInstance(this) -> type.cast(this)
        this is ViewGroup -> (0 until childCount).firstNotNullOfOrNull { getChildAt(it).findOf(type) }
        else -> null
    }
}


