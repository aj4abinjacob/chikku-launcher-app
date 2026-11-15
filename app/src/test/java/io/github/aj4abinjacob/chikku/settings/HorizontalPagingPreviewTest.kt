package io.github.aj4abinjacob.chikku.settings

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.ContextThemeWrapper
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.settings.*
import io.github.aj4abinjacob.chikku.settings.previews.HorizontalPagingPreviewView
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HorizontalPagingPreviewTest {
    @Test fun previewFollowsEachSurfaceAndHeldSliderDraftsUsingNativeViews() {
        val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext<Context>(), R.style.Theme_Chikku_Home)
        val preview = HorizontalPagingPreviewView(context)
        preview.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY))
        preview.layout(0, 0, 600, 400)
        val settings = LauncherSettings(home = HomeSettings(pageSnapDurationMs = 420, pageEffectStrength = .25f,
            pageSwipeSensitivity = PageSwipeSensitivity.LOW, transitionEffect = PageTransitionEffect.CUBE,
            pageEdgeFeedback = PageEdgeFeedback.NONE, pageIndicatorStyle = PageIndicatorStyle.LINE, infiniteScroll = true),
            drawer = DrawerSettings(pageSnapDurationMs = 120, pageEffectStrength = .75f,
                pageTransitionEffect = PageTransitionEffect.FADE, pageSwipeSensitivity = PageSwipeSensitivity.HIGH,
                pageIndicatorStyle = PageIndicatorStyle.NONE))
        preview.apply(settings, false, 0xFF222222.toInt(), -1)
        assertThat(preview.pager.wallpaperUpdatesEnabled).isFalse()
        assertThat(preview.indicator.isFocusable).isFalse()
        assertThat(preview.pager.snapDurationMs).isEqualTo(420)
        assertThat(preview.pager.swipeSensitivity).isEqualTo(PageSwipeSensitivity.LOW)
        assertThat(preview.pager.effectStrength).isEqualTo(.25f)
        assertThat(preview.pager.edgeFeedback).isEqualTo(PageEdgeFeedback.NONE)
        assertThat(preview.pager.infiniteScroll).isTrue()
        assertThat(preview.indicator.style).isEqualTo(PageIndicatorStyle.LINE)
        preview.pager.scrollTo(300, 0)
        assertThat(preview.pager.getChildAt(0).rotationY).isWithin(.01f).of(7.5f)
        // A held draft changes rendering immediately without any repository/save callback.
        preview.apply(settings.copy(home = settings.home.copy(pageEffectStrength = .5f)), false, 0xFF222222.toInt(), -1, showEffectFrame = true)
        assertThat(preview.pager.getChildAt(0).rotationY).isWithin(.01f).of(15f)
        preview.apply(settings, true, 0xFF222222.toInt(), -1)
        assertThat(preview.pager.snapDurationMs).isEqualTo(120)
        assertThat(preview.pager.swipeSensitivity).isEqualTo(PageSwipeSensitivity.HIGH)
        assertThat(preview.pager.transitionEffect).isEqualTo(PageTransitionEffect.FADE)
        assertThat(preview.pager.getChildAt(0).rotationY).isEqualTo(0f)
        assertThat(preview.indicator.visibility).isEqualTo(View.GONE)
        assertThat(preview.importantForAccessibility).isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS)
    }

    @Test fun sampleSitsOnTheStandInWallpaperWithTheSurfaceColourLayeredOver() {
        val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext<Context>(), R.style.Theme_Chikku_Home)
        val preview = HorizontalPagingPreviewView(context)
        preview.apply(LauncherSettings(), false, Color.TRANSPARENT, Color.WHITE)
        val home = preview.background as LayerDrawable
        assertThat(home.numberOfLayers).isEqualTo(2)
        assertThat(home.getDrawable(0)).isInstanceOf(GradientDrawable::class.java)
        assertThat((home.getDrawable(1) as ColorDrawable).color).isEqualTo(Color.TRANSPARENT)
        preview.apply(LauncherSettings(), true, 0xCC102030.toInt(), Color.WHITE)
        assertThat(((preview.background as LayerDrawable).getDrawable(1) as ColorDrawable).color).isEqualTo(0xCC102030.toInt())
        assertThat(preview.clipToOutline).isTrue()
    }
}
