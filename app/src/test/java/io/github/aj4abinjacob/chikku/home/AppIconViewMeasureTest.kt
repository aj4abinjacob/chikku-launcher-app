package io.github.aj4abinjacob.chikku.home

import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.settings.DockLabelFont
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class AppIconViewMeasureTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun measuredHeight(view: AppIconView): Int {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(2000, View.MeasureSpec.AT_MOST),
        )
        return view.measuredHeight
    }

    @Test
    fun measuredHeightMatchesStaticDesiredHeight() {
        for (style in AppIconView.Style.entries) {
            for (list in listOf(false, true)) {
                for (single in listOf(true, false)) {
                    for (show in listOf(true, false)) {
                        val view = AppIconView(context, style).apply {
                            iconScale = 1.2f
                            labelScale = 1.3f
                            showLabel = show
                            labelSingleLine = single
                            labelFont = DockLabelFont.NORMAL
                            listMode = list
                        }
                        val expected = AppIconView.desiredHeight(context, style, 1.2f, 1.3f, show, single, DockLabelFont.NORMAL, list)
                        assertThat(measuredHeight(view)).isEqualTo(maxOf(expected, view.minimumHeight))
                        // Measuring again (cached) gives the same answer.
                        assertThat(measuredHeight(view)).isEqualTo(maxOf(expected, view.minimumHeight))
                    }
                }
            }
        }
    }

    @Test
    fun changingAnInputChangesTheMeasuredHeight() {
        val view = AppIconView(context, AppIconView.Style.DRAWER)
        val base = measuredHeight(view)

        view.showLabel = false
        val noLabel = measuredHeight(view)
        assertThat(noLabel).isLessThan(base)
        view.showLabel = true
        assertThat(measuredHeight(view)).isEqualTo(base)

        view.iconScale = 4f
        assertThat(measuredHeight(view)).isGreaterThan(base)
        view.iconScale = 1f

        view.labelGapDp = 20
        assertThat(measuredHeight(view)).isGreaterThan(base)
        view.labelGapDp = null

        // Robolectric's default text stack can report empty font metrics; the label-size inputs
        // only change the height where text has a height.
        val metrics = android.graphics.Paint().apply { textSize = 40f }.fontMetrics
        if (metrics.descent - metrics.ascent > 0f) {
            view.labelScale = 2f
            val bigLabel = measuredHeight(view)
            assertThat(bigLabel).isGreaterThan(base)
            view.labelSingleLine = false
            assertThat(measuredHeight(view)).isGreaterThan(bigLabel)
        }
    }
}
