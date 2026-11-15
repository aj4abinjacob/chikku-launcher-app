package io.github.aj4abinjacob.chikku.settings

import android.content.Context
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.settings.DockBackground
import io.github.aj4abinjacob.chikku.core.model.settings.DockSettings
import io.github.aj4abinjacob.chikku.core.model.settings.HomeSearchBarPosition
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.core.model.settings.SearchSettings
import io.github.aj4abinjacob.chikku.settings.dock.DockSearchPreviewFrame
import io.github.aj4abinjacob.chikku.settings.previews.previewDockAfterLaunch
import io.github.aj4abinjacob.chikku.settings.previews.previewDockReopenedPage
import io.github.aj4abinjacob.chikku.settings.previews.previewPageTarget
import io.github.aj4abinjacob.chikku.settings.previews.previewSearchAppRows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PreviewHomeDockSearchTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val density = context.resources.displayMetrics.density
    private fun dp(value: Int) = (value * density).roundToInt()
    private val below = LauncherSettings().let {
        it.copy(home = it.home.copy(showSearchBar = true, searchBarPosition = HomeSearchBarPosition.BELOW_DOCK),
            dock = it.dock.copy(background = DockBackground.RECTANGLE))
    }

    private fun render(frame: DockSearchPreviewFrame, settings: LauncherSettings, visibility: Float = 1f) {
        frame.show(settings, null, 0, 0, 1f, 1, visibility, true)
        frame.measure(View.MeasureSpec.makeMeasureSpec(dp(360), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(dp(184), View.MeasureSpec.EXACTLY))
        frame.layout(0, 0, frame.measuredWidth, frame.measuredHeight)
    }

    @Test
    fun gapDraftMovesDockAndPreservesBarHeightAndPreviewScale() {
        val frame = DockSearchPreviewFrame(context)
        render(frame, below)
        val initial = frame.geometry
        val initialScale = frame.dock.scaleY
        val initialBarHeight = frame.bar.measuredHeight
        render(frame, below.copy(search = below.search.copy(widgetDockGapDp = 64)))
        assertThat(frame.geometry.searchTop).isEqualTo(initial.searchTop)
        assertThat(initial.dockTop - frame.geometry.dockTop).isEqualTo(dp(64 - 12))
        assertThat(frame.geometry.sceneHeight).isEqualTo(initial.sceneHeight)
        assertThat(frame.dock.scaleY).isEqualTo(initialScale)
        assertThat(frame.bar.measuredHeight).isEqualTo(initialBarHeight)
        assertThat(below.search.widgetDockGapDp).isEqualTo(12)
    }

    @Test
    fun barHeightDraftKeepsEnvelopeAndChangesActualNativeCapsuleSize() {
        val frame = DockSearchPreviewFrame(context)
        render(frame, below)
        val initial = frame.geometry
        render(frame, below.copy(search = below.search.copy(widgetHeightDp = 96)))
        assertThat(frame.bar.measuredHeight).isEqualTo(dp(96))
        assertThat(frame.geometry.sceneHeight).isEqualTo(initial.sceneHeight)
        assertThat(initial.dockTop - frame.geometry.dockTop).isEqualTo(dp(96 - 56))
    }

    @Test
    fun barPositionAndVisibilityAreRepresentedInTheSameNativeScene() {
        val frame = DockSearchPreviewFrame(context)
        render(frame, below)
        assertThat(frame.geometry.searchTop!!).isGreaterThan(frame.geometry.dockTop + frame.geometry.dockHeight)
        render(frame, below.copy(home = below.home.copy(searchBarPosition = HomeSearchBarPosition.TOP)))
        assertThat(frame.geometry.searchTop!!).isLessThan(frame.geometry.dockTop)
        assertThat(frame.bar.visibility).isEqualTo(View.VISIBLE)
        render(frame, below.copy(home = below.home.copy(showSearchBar = false)))
        assertThat(frame.bar.visibility).isEqualTo(View.GONE)
        assertThat(frame.geometry.searchTop).isNull()
    }

    @Test
    fun behindNavigationExtendsBackgroundAndOverlayPreservesUnderlyingHomeSpace() {
        val frame = DockSearchPreviewFrame(context)
        render(frame, below)
        assertThat(frame.getChildAt(1).measuredHeight).isEqualTo(frame.geometry.dockHeight)
        assertThat(frame.getChildAt(0).measuredHeight).isEqualTo(frame.geometry.dockTop)
        render(frame, below.copy(dock = below.dock.copy(drawBehindNavigationBar = true, displayAsOverlay = true)))
        assertThat(frame.getChildAt(1).measuredHeight).isEqualTo(frame.geometry.sceneHeight - frame.geometry.dockTop)
        assertThat(frame.getChildAt(0).measuredHeight).isEqualTo(frame.geometry.navigationTop)
        render(frame, below, visibility = 0f)
        assertThat(frame.dock.visibility).isEqualTo(View.INVISIBLE)
        assertThat(frame.getChildAt(1).visibility).isEqualTo(View.INVISIBLE)
    }

    @Test
    fun samplePagingStopsWrapsAndYieldsDiscoverStartEdgeWithoutChangingPageIdentity() {
        assertThat(previewPageTarget(2, 3, 1, false)).isEqualTo(2)
        assertThat(previewPageTarget(2, 3, 1, true)).isEqualTo(0)
        assertThat(previewPageTarget(0, 3, -1, true)).isEqualTo(2)
        assertThat(previewPageTarget(0, 3, -1, true, allowStartWrap = false)).isEqualTo(0)
        assertThat(previewPageTarget(1, 3, -1, true, allowStartWrap = false)).isEqualTo(0)
        assertThat(previewPageTarget(0, 1, -1, true)).isEqualTo(0)
    }

    @Test
    fun sampleLaunchOnlyClosesAnOverlayThatHasAutomaticClosingEnabled() {
        assertThat(previewDockAfterLaunch(DockSettings(automaticallyClose = true), true)).isTrue()
        assertThat(previewDockAfterLaunch(DockSettings(displayAsOverlay = true), true)).isTrue()
        assertThat(previewDockAfterLaunch(DockSettings(displayAsOverlay = true, automaticallyClose = true), true)).isFalse()
        assertThat(previewDockAfterLaunch(DockSettings(), false)).isFalse()
    }

    @Test
    fun reopeningTheSampleDockDemonstratesRememberedPositionWithoutSavingIt() {
        val dock = DockSettings(pages = 2, rememberPosition = true)
        assertThat(previewDockReopenedPage(dock, 1)).isEqualTo(1)
        assertThat(previewDockReopenedPage(dock.copy(rememberPosition = false), 1)).isEqualTo(0)
        assertThat(previewDockReopenedPage(dock, 20)).isEqualTo(1)
        assertThat(dock.lastPage).isEqualTo(0)
    }

    @Test
    fun appLimitChangesCompositionRatherThanOnlyItsDescription() {
        assertThat(previewSearchAppRows(SearchSettings()).map { it.size }).containsExactly(1, 1, 1).inOrder()
        assertThat(previewSearchAppRows(SearchSettings(maxAppResults = 5)).map { it.size }).containsExactly(5)
        assertThat(previewSearchAppRows(SearchSettings(maxAppResults = 2)).flatten()).containsExactly(0, 1).inOrder()
        assertThat(previewSearchAppRows(SearchSettings(maxAppResults = 50)).single()).hasSize(5)
    }
}
