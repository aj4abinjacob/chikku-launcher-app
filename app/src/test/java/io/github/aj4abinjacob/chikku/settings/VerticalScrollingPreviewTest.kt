package io.github.aj4abinjacob.chikku.settings

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.LayerDrawable
import android.view.ContextThemeWrapper
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.settings.ListEdgeEffect
import io.github.aj4abinjacob.chikku.core.model.settings.ListScrollSpeed
import io.github.aj4abinjacob.chikku.settings.previews.VerticalScrollingPreviewView
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VerticalScrollingPreviewTest {
    @Test fun previewFollowsSpeedEdgeEffectStyleAndDrawerColours() {
        val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext<Context>(), R.style.Theme_Chikku_Home)
        val preview = VerticalScrollingPreviewView(context)
        preview.apply(ListScrollSpeed.SLOW, ListEdgeEffect.NONE, listStyle = false, 0xCC102030.toInt(), Color.WHITE)
        assertThat(preview.list.scrollSpeed).isEqualTo(ListScrollSpeed.SLOW)
        assertThat(preview.list.overScrollMode).isEqualTo(View.OVER_SCROLL_NEVER)
        assertThat((preview.list.layoutManager as GridLayoutManager).spanCount).isEqualTo(VerticalScrollingPreviewView.COLUMNS)
        assertThat(((preview.background as LayerDrawable).getDrawable(1) as ColorDrawable).color).isEqualTo(0xCC102030.toInt())
        preview.apply(ListScrollSpeed.FAST, ListEdgeEffect.SYSTEM, listStyle = true, Color.TRANSPARENT, Color.BLACK)
        assertThat(preview.list.scrollSpeed).isEqualTo(ListScrollSpeed.FAST)
        assertThat(preview.list.overScrollMode).isNotEqualTo(View.OVER_SCROLL_NEVER)
        assertThat((preview.list.layoutManager as GridLayoutManager).spanCount).isEqualTo(1)
        // Synthetic samples only, never focusable or announced one by one.
        assertThat(preview.importantForAccessibility).isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS)
        assertThat(preview.list.isFocusable).isFalse()
        assertThat(preview.list.adapter!!.itemCount).isEqualTo(VerticalScrollingPreviewView.SAMPLE_COUNT)
    }
}
