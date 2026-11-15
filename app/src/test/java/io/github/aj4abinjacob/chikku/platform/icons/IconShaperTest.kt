package io.github.aj4abinjacob.chikku.platform.icons

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.AppCustomization
import io.github.aj4abinjacob.chikku.core.model.settings.IconShape
import io.github.aj4abinjacob.chikku.core.model.settings.LookFeelSettings
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class IconShaperTest {

    private fun adaptive() = AdaptiveIconDrawable(ColorDrawable(Color.RED), ColorDrawable(Color.BLUE))

    @Test
    fun systemShapeKeepsAdaptiveIconsAsTheyAre() {
        val icon = adaptive()
        assertThat(IconShaper.apply(icon, IconShape.SYSTEM, reshapeLegacy = false)).isSameInstanceAs(icon)
    }

    @Test
    fun legacyIconsStayUntouchedUnlessReshaping() {
        val legacy = ColorDrawable(Color.GREEN)
        assertThat(IconShaper.apply(legacy, IconShape.CIRCLE, reshapeLegacy = false)).isSameInstanceAs(legacy)
        assertThat(IconShaper.apply(legacy, IconShape.SYSTEM, reshapeLegacy = true)).isInstanceOf(AdaptiveIconDrawable::class.java)
        assertThat(IconShaper.apply(legacy, IconShape.CIRCLE, reshapeLegacy = true)).isInstanceOf(ShapedIconDrawable::class.java)
    }

    @Test
    fun customShapeClipsAdaptiveIconsAndCopiesKeepTheShape() {
        val shaped = IconShaper.apply(adaptive(), IconShape.SQUIRCLE, reshapeLegacy = false)
        assertThat(shaped).isInstanceOf(ShapedIconDrawable::class.java)
        val copy = shaped.constantState?.newDrawable()
        assertThat(copy).isInstanceOf(ShapedIconDrawable::class.java)
        assertThat((copy as ShapedIconDrawable).shape).isEqualTo(IconShape.SQUIRCLE)

        // Draws without throwing at an ordinary icon size.
        copy.setBounds(0, 0, 96, 96)
        copy.draw(Canvas(Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)))
    }

    @Test
    fun iconStyleAndOverridesFromSettings() {
        val style = IconStyle.from(LookFeelSettings(iconPackPackage = "pack.a", iconShape = IconShape.CIRCLE))
        assertThat(style.isSystemDefault).isFalse()
        assertThat(IconStyle.from(LookFeelSettings()).isSystemDefault).isTrue()

        val a = AppKey("com.a", "com.a.Main", 0)
        val b = AppKey("com.b", "com.b.Main", 0)
        val overrides = IconTheme.overridesOf(
            mapOf(
                a to AppCustomization(a, iconPackPackage = "pack.b", iconDrawableName = "a_icon"),
                b to AppCustomization(b, customLabel = "Bee"), // no icon override
            ),
        )
        assertThat(overrides).containsExactly(a, IconOverride("pack.b", "a_icon"))
        assertThat(IconTheme(style, overrides).packPackages).containsExactly("pack.a", "pack.b")
    }
}
