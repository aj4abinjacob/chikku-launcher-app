package io.github.aj4abinjacob.chikku.platform.icons

import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.settings.IconShape
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShortcutIconNormalizerTest {

    /** A glyph that covers a quarter of its bounds, like a bare "+" or clock shortcut icon. */
    private fun glyph(color: Int) = InsetDrawable(ColorDrawable(color), 0.25f)

    private fun plateOf(icon: android.graphics.drawable.Drawable): Int =
        ((icon as AdaptiveIconDrawable).background as ColorDrawable).color

    @Test
    fun darkGlyphGoesOnALightPlate() {
        val icon = IconShaper.normalizeShortcut(glyph(Color.BLACK), IconShape.SYSTEM)
        assertThat(plateOf(icon)).isEqualTo(IconShaper.LEGACY_BACKGROUND)
    }

    @Test
    fun lightGlyphGoesOnADarkPlate() {
        val icon = IconShaper.normalizeShortcut(glyph(Color.WHITE), IconShape.SYSTEM)
        assertThat(plateOf(icon)).isEqualTo(IconShaper.DARK_PLATE)
    }

    @Test
    fun fullBleedImagesAndEmptyIconsAreLeftAlone() {
        val photo = ColorDrawable(Color.rgb(200, 120, 40))
        assertThat(IconShaper.normalizeShortcut(photo, IconShape.CIRCLE)).isSameInstanceAs(photo)
        val empty = ColorDrawable(Color.TRANSPARENT)
        assertThat(IconShaper.normalizeShortcut(empty, IconShape.SYSTEM)).isSameInstanceAs(empty)
    }

    @Test
    fun translucentAdaptiveBitmapGetsAnOpaquePlate() {
        // Like a browser site shortcut: no background layer, a see-through backdrop and a light glyph.
        val siteIcon = LayerDrawable(arrayOf(ColorDrawable(Color.argb(90, 0, 0, 0)), glyph(Color.argb(200, 255, 255, 255))))
        val icon = IconShaper.normalizeShortcut(AdaptiveIconDrawable(null, siteIcon), IconShape.SYSTEM)
        assertThat(plateOf(icon)).isEqualTo(IconShaper.DARK_PLATE)

        val darkIcon = AdaptiveIconDrawable(null, glyph(Color.argb(200, 0, 0, 0)))
        assertThat(plateOf(IconShaper.normalizeShortcut(darkIcon, IconShape.SYSTEM))).isEqualTo(IconShaper.LEGACY_BACKGROUND)
    }

    @Test
    fun opaqueAdaptiveBitmapIsLeftAlone() {
        val opaque = AdaptiveIconDrawable(null, ColorDrawable(Color.rgb(20, 20, 20)))
        assertThat(IconShaper.normalizeShortcut(opaque, IconShape.SYSTEM)).isSameInstanceAs(opaque)
    }

    @Test
    fun adaptiveAndPlatedIconsTakeTheUserShape() {
        val adaptive = AdaptiveIconDrawable(ColorDrawable(Color.RED), ColorDrawable(Color.BLUE))
        assertThat(IconShaper.normalizeShortcut(adaptive, IconShape.SYSTEM)).isSameInstanceAs(adaptive)
        assertThat(IconShaper.normalizeShortcut(adaptive, IconShape.SQUIRCLE)).isInstanceOf(ShapedIconDrawable::class.java)
        assertThat(IconShaper.normalizeShortcut(glyph(Color.BLACK), IconShape.CIRCLE)).isInstanceOf(ShapedIconDrawable::class.java)
    }
}
