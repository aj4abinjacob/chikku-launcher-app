package io.github.aj4abinjacob.chikku.settings

import android.content.Context
import android.graphics.Color
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.settings.LookFeelSettings
import io.github.aj4abinjacob.chikku.labs.createDebugOverlayText
import io.github.aj4abinjacob.chikku.settings.previews.DiscoverSample
import io.github.aj4abinjacob.chikku.settings.previews.discoverSampleAfterSwipe
import io.github.aj4abinjacob.chikku.settings.previews.homePreviewPageCount
import io.github.aj4abinjacob.chikku.settings.sections.menuPreviewSurfaceAfterEdit
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExtraPreviewGapsTest {
    @Test fun addedPagesRemainDistinguishableFromOptionalDemoPages() {
        assertThat((1..4).map { homePreviewPageCount(it, false) }).containsExactly(1, 2, 3, 4).inOrder()
        assertThat(homePreviewPageCount(1, true)).isEqualTo(3)
        assertThat(homePreviewPageCount(5, true)).isEqualTo(5)
        assertThat(homePreviewPageCount(0, false)).isEqualTo(1)
        assertThat(homePreviewPageCount(Int.MAX_VALUE, false)).isEqualTo(100)
    }

    @Test fun menuEditsSelectTheirOwnSurfaceAndSharedStylingKeepsTheSelection() {
        val before = LookFeelSettings()
        assertThat(menuPreviewSurfaceAfterEdit(before, before.copy(menuShowStoreDrawer = !before.menuShowStoreDrawer), false)).isTrue()
        assertThat(menuPreviewSurfaceAfterEdit(before, before.copy(menuShowHideHome = !before.menuShowHideHome), true)).isFalse()
        assertThat(menuPreviewSurfaceAfterEdit(before, before.copy(menuCornerRadiusDp = 40f), true)).isTrue()
        assertThat(menuPreviewSurfaceAfterEdit(before, before.copy(menuBackgroundColor = Color.BLUE), false)).isFalse()
    }

    @Test fun discoverSampleRespectsTheEnabledSettingAndFirstPageSwipe() {
        assertThat(discoverSampleAfterSwipe(false, true)).isEqualTo(DiscoverSample.HOME)
        assertThat(discoverSampleAfterSwipe(true, false)).isEqualTo(DiscoverSample.HOME)
        assertThat(discoverSampleAfterSwipe(true, true)).isEqualTo(DiscoverSample.FEED)
    }

    @Test fun debugSampleSharesNativeHomeStylingWithoutStartingObservationOrActions() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val view = createDebugOverlayText(context)
        assertThat(view.currentTextColor).isEqualTo(Color.WHITE)
        assertThat(view.isClickable).isFalse()
        assertThat(view.isFocusable).isFalse()
        assertThat(view.visibility).isEqualTo(View.GONE)
        assertThat(view.paddingLeft).isGreaterThan(0)
    }
}
