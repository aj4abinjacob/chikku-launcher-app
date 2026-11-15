package io.github.aj4abinjacob.chikku.home.widget

import android.app.Application
import android.graphics.Outline
import android.graphics.Rect
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WidgetCornersTest {
    @Test
    fun clipsOnlyPaddedContentAndCapsRadiusAtHalfTheShortEdge() {
        val cell = WidgetCellView(ApplicationProvider.getApplicationContext<Application>())
        cell.layout(0, 0, 200, 100)
        cell.setPadding(10, 10, 10, 10)
        cell.cornerRadiusPx = 100f
        val outline = Outline()
        cell.outlineProvider.getOutline(cell, outline)
        val rect = Rect()
        assertThat(outline.getRect(rect)).isTrue()
        assertThat(rect).isEqualTo(Rect(10, 10, 190, 90))
        assertThat(outline.radius).isEqualTo(40f)
        assertThat(cell.clipToOutline).isTrue()
        cell.cornerRadiusPx = 0f
        assertThat(cell.clipToOutline).isFalse()
        cell.cornerRadiusPx = Float.NaN
        assertThat(cell.clipToOutline).isFalse()
    }
}
