package io.github.aj4abinjacob.chikku.settings

import android.content.Context
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.settings.DockBackground
import io.github.aj4abinjacob.chikku.core.model.settings.HomeSearchBarIcon
import io.github.aj4abinjacob.chikku.core.model.settings.HomeSearchBarButtonSlot
import io.github.aj4abinjacob.chikku.core.model.settings.HomeSearchBarPosition
import android.widget.ImageView
import org.robolectric.Shadows.shadowOf
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.home.AppIconView
import io.github.aj4abinjacob.chikku.settings.dock.DockAppearancePreviewFrame
import io.github.aj4abinjacob.chikku.settings.dock.DockAppearancePreviewMode
import io.github.aj4abinjacob.chikku.settings.dock.HomeBarSceneFrame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.roundToInt

/** Dock Icons/Background and the home search bar previews draw the real views at actual size. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DockSearchTrueScalePreviewTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val density = context.resources.displayMetrics.density
    private fun dp(value: Int) = (value * density).roundToInt()
    private fun dpBar(value: Int) = (value * density + .5f).toInt()
    private val width get() = dp(411)

    private fun render(frame: DockAppearancePreviewFrame, settings: LauncherSettings, mode: DockAppearancePreviewMode) {
        frame.show(settings, null, mode)
        frame.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        frame.layout(0, 0, frame.measuredWidth, frame.measuredHeight)
    }

    private fun render(frame: HomeBarSceneFrame, settings: LauncherSettings, mark: Boolean = true) {
        frame.show(settings, mark)
        frame.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(dp(232), View.MeasureSpec.EXACTLY))
        frame.layout(0, 0, frame.measuredWidth, frame.measuredHeight)
    }

    private fun icons(scale: Float, labels: Boolean = true, labelScale: Float = 1f) = LauncherSettings().let {
        it.copy(dock = it.dock.copy(matchDesktopSize = false, iconScale = scale, labelScale = labelScale, showLabels = labels))
    }

    @Test
    fun iconSizeDraftResizesActualSizeIconsWithoutMovingControls() {
        val frame = DockAppearancePreviewFrame(context)
        render(frame, icons(0.5f), DockAppearancePreviewMode.ICONS)
        val height = frame.measuredHeight
        val small = frame.dock.getChildAt(0) as AppIconView
        assertThat(frame.dock.measuredWidth).isEqualTo(width)
        assertThat(frame.dock.scaleX).isEqualTo(1f)
        assertThat(small.iconScale).isEqualTo(0.5f)
        val smallCell = frame.dock.minCellHeight
        render(frame, icons(1.5f, labelScale = 1.5f), DockAppearancePreviewMode.ICONS)
        assertThat((frame.dock.getChildAt(0) as AppIconView).iconScale).isEqualTo(1.5f)
        // 52dp icons at 50% and 150% differ by 52dp at actual size.
        assertThat(frame.dock.minCellHeight - smallCell).isAtLeast(dp(52))
        assertThat(frame.measuredHeight).isEqualTo(height)
        assertThat(frame.dockTop).isAtLeast(0)
        render(frame, icons(1f, labels = false), DockAppearancePreviewMode.ICONS)
        assertThat((frame.dock.getChildAt(0) as AppIconView).showLabel).isFalse()
        assertThat(frame.measuredHeight).isEqualTo(height)
    }

    @Test
    fun iconsPreviewHasALongSampleNameSoSingleLineIsVisible() {
        val frame = DockAppearancePreviewFrame(context)
        render(frame, LauncherSettings(), DockAppearancePreviewMode.ICONS)
        val last = frame.dock.getChildAt(frame.dock.childCount - 1) as AppIconView
        assertThat(last.label).isEqualTo(context.getString(R.string.preview_dock_long_label))
        assertThat(last.isClickable).isFalse()
        assertThat(last.isFocusable).isFalse()
    }

    @Test
    fun backgroundFollowsTheSettingAndBehindNavigationExtendsIt() {
        val frame = DockAppearancePreviewFrame(context)
        val base = LauncherSettings().let { it.copy(dock = it.dock.copy(backgroundEnabled = true, background = DockBackground.ROUNDED)) }
        render(frame, base.copy(dock = base.dock.copy(backgroundEnabled = false)), DockAppearancePreviewMode.BACKGROUND)
        assertThat(frame.backdrop.background).isNull()
        val height = frame.measuredHeight
        render(frame, base, DockAppearancePreviewMode.BACKGROUND)
        assertThat(frame.backdrop.background).isNotNull()
        assertThat(frame.backdrop.measuredWidth).isEqualTo(width)
        assertThat(frame.backdropHeight).isEqualTo(frame.dock.measuredHeight)
        render(frame, base.copy(dock = base.dock.copy(drawBehindNavigationBar = true)), DockAppearancePreviewMode.BACKGROUND)
        assertThat(frame.backdropHeight).isEqualTo(frame.dock.measuredHeight + frame.navigationHeight)
        assertThat(frame.backdrop.bottom).isEqualTo(frame.measuredHeight)
        assertThat(frame.measuredHeight).isEqualTo(height)
    }

    private val belowDock = LauncherSettings().let {
        it.copy(home = it.home.copy(showSearchBar = true, searchBarPosition = HomeSearchBarPosition.BELOW_DOCK))
    }

    @Test
    fun barSizeDraftsChangeTheActualSizeBar() {
        val frame = HomeBarSceneFrame(context)
        render(frame, belowDock.copy(search = belowDock.search.copy(widgetWidthPercent = 100, widgetHeightDp = 48)))
        val margin = context.resources.getDimensionPixelSize(R.dimen.home_panel_margin)
        assertThat(frame.bar.measuredWidth).isEqualTo(width - 2 * margin)
        assertThat(frame.bar.measuredHeight).isEqualTo(dpBar(48))
        render(frame, belowDock.copy(search = belowDock.search.copy(widgetWidthPercent = 50, widgetHeightDp = 96)))
        assertThat(frame.bar.measuredWidth).isEqualTo((width - 2 * margin) * 50 / 100)
        assertThat(frame.bar.measuredHeight).isEqualTo(dpBar(96))
        assertThat(frame.bar.scaleX).isEqualTo(1f)
        assertThat(frame.measuredHeight).isEqualTo(dp(232))
    }

    @Test
    fun dockGapDraftMovesTheDockByTheGapAndTheBarStays() {
        val frame = HomeBarSceneFrame(context)
        render(frame, belowDock.copy(search = belowDock.search.copy(widgetDockGapDp = 0)))
        val barTop = frame.barTop!!
        assertThat(frame.dockBottom).isEqualTo(barTop)
        render(frame, belowDock.copy(search = belowDock.search.copy(widgetDockGapDp = 64)))
        assertThat(frame.barTop).isEqualTo(barTop)
        assertThat(barTop - frame.dockBottom).isEqualTo(dpBar(64))
        // Part of the dock stays visible even at the largest gap and bar.
        render(frame, belowDock.copy(search = belowDock.search.copy(widgetDockGapDp = 64, widgetHeightDp = 96)))
        assertThat(frame.dockBottom).isGreaterThan(0)
    }

    @Test
    fun topBarIsShownInTheTopOfTheHomeScreenAndHiddenBarShowsNone() {
        val frame = HomeBarSceneFrame(context)
        val top = belowDock.copy(home = belowDock.home.copy(searchBarPosition = HomeSearchBarPosition.TOP))
        render(frame, top)
        assertThat(frame.barTop).isEqualTo(dpBar(8))
        assertThat(frame.bar.visibility).isEqualTo(View.VISIBLE)
        assertThat(frame.dock.visibility).isEqualTo(View.GONE)
        render(frame, top.copy(home = top.home.copy(showSearchBar = false)))
        assertThat(frame.barTop).isNull()
        assertThat(frame.bar.visibility).isEqualTo(View.GONE)
        assertThat(frame.dock.visibility).isEqualTo(View.VISIBLE)
    }

    @Test
    fun searchIconChoiceRedrawsTheActualSizeBarGlyph() {
        val frame = HomeBarSceneFrame(context)
        val glyph = frame.bar.chikkuButton.getChildAt(0) as ImageView
        render(frame, belowDock)
        assertThat(frame.bar.icon).isEqualTo(HomeSearchBarIcon.NOSE)
        assertThat(shadowOf(glyph.drawable).createdFromResId).isEqualTo(R.drawable.ic_search_bar_nose)
        render(frame, belowDock.copy(search = belowDock.search.copy(widgetIcon = HomeSearchBarIcon.PAW)))
        assertThat(frame.bar.icon).isEqualTo(HomeSearchBarIcon.PAW)
        assertThat(shadowOf(glyph.drawable).createdFromResId).isEqualTo(R.drawable.ic_search_bar_paw)
        render(frame, belowDock.copy(search = belowDock.search.copy(widgetIcon = HomeSearchBarIcon.PLAIN)))
        assertThat(shadowOf(glyph.drawable).createdFromResId).isEqualTo(R.drawable.ic_search_bar_plain)
    }

    @Test
    fun barButtonChoicesRedrawTheActualSizeBarAndStayInert() {
        val frame = HomeBarSceneFrame(context)
        render(frame, belowDock)
        assertThat(frame.bar.chikkuButton.parent).isSameInstanceAs(frame.bar)
        assertThat(frame.bar.webButton.parent).isSameInstanceAs(frame.bar)
        assertThat(frame.bar.chikkuButton.right).isLessThan(frame.bar.webButton.left)
        render(frame, belowDock.copy(search = belowDock.search.copy(
            widgetChikkuButton = HomeSearchBarButtonSlot.END, widgetWebButton = HomeSearchBarButtonSlot.START)))
        assertThat(frame.bar.webButton.right).isLessThan(frame.bar.chikkuButton.left)
        render(frame, belowDock.copy(search = belowDock.search.copy(widgetWebButton = HomeSearchBarButtonSlot.HIDDEN)))
        assertThat(frame.bar.webButton.parent).isNull()
        assertThat(frame.bar.engine).isNull()
        assertThat(frame.bar.chikkuButton.isClickable || frame.bar.chikkuButton.isFocusable).isFalse()
    }
}
