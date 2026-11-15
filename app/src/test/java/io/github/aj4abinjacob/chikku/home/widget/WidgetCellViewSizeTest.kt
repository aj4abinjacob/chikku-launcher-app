package io.github.aj4abinjacob.chikku.home.widget

import android.appwidget.AppWidgetHostView
import android.content.Context
import android.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WidgetCellViewSizeTest {
    private val context: Context = ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_Chikku_Home)

    @Test fun delayedAndReplacementBindingsReceiveTheExistingSizeWithoutAResize() {
        val cell = WidgetCellView(context)
        cell.layout(0, 0, 300, 160) // The placeholder was measured before the asynchronous bind.
        val reports = mutableListOf<Triple<Int, Int, Int>>()
        cell.onSizeChanged = { view, width, height -> reports += Triple(view.hostedId, width, height) }
        val first = AppWidgetHostView(context)
        cell.showWidget(first, 42)
        assertThat(reports).containsExactly(Triple(42, 300, 160))
        cell.showWidget(first, 42)
        assertThat(reports).hasSize(1)
        cell.showPlaceholder("Synthetic unavailable", "Synthetic widget")
        cell.showWidget(AppWidgetHostView(context), 43)
        assertThat(reports).containsExactly(Triple(42, 300, 160), Triple(43, 300, 160)).inOrder()
    }

    @Test fun anUnmeasuredBindingWaitsForItsFirstPositiveLayout() {
        val cell = WidgetCellView(context)
        val reports = mutableListOf<Triple<Int, Int, Int>>()
        cell.onSizeChanged = { view, width, height -> reports += Triple(view.hostedId, width, height) }
        cell.showWidget(AppWidgetHostView(context), 44)
        assertThat(reports).isEmpty()
        cell.layout(0, 0, 280, 150)
        assertThat(reports).containsExactly(Triple(44, 280, 150))
    }
}
