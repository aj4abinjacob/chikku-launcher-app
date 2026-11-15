package io.github.aj4abinjacob.chikku.settings

import android.content.Context
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.core.model.settings.PaddingSize
import io.github.aj4abinjacob.chikku.settings.dock.DockSpacingPreviewFrame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DockSpacingPreviewTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val density = context.resources.displayMetrics.density
    private fun dp(value: Int) = (value * density).roundToInt()

    private fun render(frame: DockSpacingPreviewFrame, settings: LauncherSettings) {
        frame.show(settings, null)
        frame.measure(View.MeasureSpec.makeMeasureSpec(dp(360), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        frame.layout(0, 0, frame.measuredWidth, frame.measuredHeight)
    }

    private fun spacing(side: PaddingSize?, vertical: PaddingSize) = LauncherSettings().let {
        it.copy(home = it.home.copy(horizontalPadding = PaddingSize.MEDIUM),
            dock = it.dock.copy(horizontalPadding = side, verticalPadding = vertical))
    }

    @Test
    fun dockSpansFullWidthAtTrueScaleWithTheChosenSpacing() {
        val frame = DockSpacingPreviewFrame(context)
        render(frame, spacing(PaddingSize.LARGE, PaddingSize.SMALL))
        assertThat(frame.dock.measuredWidth).isEqualTo(dp(360))
        assertThat(frame.dock.scaleX).isEqualTo(1f)
        assertThat(frame.dock.paddingStart).isEqualTo(dp(16))
        assertThat(frame.dock.paddingEnd).isEqualTo(dp(16))
        assertThat(frame.dock.paddingTop).isEqualTo(dp(4))
        assertThat(frame.dock.paddingBottom).isEqualTo(dp(4))
    }

    @Test
    fun followingHomeGridUsesTheHomeSideSpace() {
        val frame = DockSpacingPreviewFrame(context)
        render(frame, spacing(null, PaddingSize.NONE))
        assertThat(frame.dock.paddingStart).isEqualTo(dp(8))
        assertThat(frame.dock.paddingTop).isEqualTo(0)
    }

    @Test
    fun steppingSpacingGrowsTheDockButKeepsThePreviewHeight() {
        val frame = DockSpacingPreviewFrame(context)
        render(frame, spacing(PaddingSize.NONE, PaddingSize.NONE))
        val height = frame.measuredHeight
        val dockHeight = frame.dock.measuredHeight
        render(frame, spacing(PaddingSize.LARGE, PaddingSize.LARGE))
        assertThat(frame.measuredHeight).isEqualTo(height)
        assertThat(frame.dock.measuredHeight - dockHeight).isEqualTo(dp(32))
        assertThat(frame.dockTop).isAtLeast(0)
        assertThat(frame.dock.bottom).isAtMost(frame.measuredHeight)
    }
}
