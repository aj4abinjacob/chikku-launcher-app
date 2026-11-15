package io.github.aj4abinjacob.chikku.home

import android.graphics.drawable.GradientDrawable
import androidx.core.graphics.ColorUtils
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.theme.SurfaceColors
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Popup cards stay distinguishable from a dark drawer, where their shadow does not show. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LauncherMenuStyleTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun fill(colors: SurfaceColors, custom: Boolean = false): Int =
        LauncherMenuStyle.cardBackground(colors, 20f, custom, context).color!!.defaultColor

    @Test
    fun darkAndBlackMenusAreLiftedAboveTheDrawer() {
        listOf(SurfaceColors.DARK, SurfaceColors.BLACK).forEach { colors ->
            val card = fill(colors)
            assertThat(card).isNotEqualTo(colors.background)
            assertThat(ColorUtils.calculateLuminance(card)).isGreaterThan(ColorUtils.calculateLuminance(colors.background))
            // Text on the lifted card keeps comfortable contrast.
            assertThat(ColorUtils.calculateContrast(colors.onSurface, card)).isAtLeast(7.0)
        }
    }

    @Test
    fun lightMenusAndCustomColorsKeepTheirFill() {
        assertThat(fill(SurfaceColors.LIGHT)).isEqualTo(SurfaceColors.LIGHT.background)
        assertThat(fill(SurfaceColors.DARK, custom = true)).isEqualTo(SurfaceColors.DARK.background)
    }

    @Test
    fun cardRadiusFollowsTheSetting() {
        val density = context.resources.displayMetrics.density
        val card: GradientDrawable = LauncherMenuStyle.cardBackground(SurfaceColors.DARK, 12f, false, context)
        assertThat(card.cornerRadius).isEqualTo(12f * density)
    }

    @Test
    fun outOfRangeRadiusIsClampedToTheSettingsRange() {
        val density = context.resources.displayMetrics.density
        assertThat(LauncherMenuStyle.cardBackground(SurfaceColors.LIGHT, 48f, false, context).cornerRadius).isEqualTo(48f * density)
        assertThat(LauncherMenuStyle.cardBackground(SurfaceColors.LIGHT, 200f, false, context).cornerRadius)
            .isEqualTo(LauncherMenuStyle.MAX_CORNER_RADIUS_DP * density)
    }

    @Test
    fun launcherActionsAreToneDownFromTheShortcutText() {
        listOf(SurfaceColors.DARK, SurfaceColors.LIGHT).forEach { colors ->
            val action = LauncherMenuStyle.actionColor(colors)
            assertThat(action and 0xFFFFFF).isEqualTo(colors.onSurface and 0xFFFFFF)
            assertThat(android.graphics.Color.alpha(action)).isLessThan(android.graphics.Color.alpha(colors.onSurface))
            // Still readable on the card.
            val card = LauncherMenuStyle.cardFill(colors, customBackground = false)
            assertThat(ColorUtils.calculateContrast(ColorUtils.compositeColors(action, card), card)).isAtLeast(4.5)
        }
    }
}
