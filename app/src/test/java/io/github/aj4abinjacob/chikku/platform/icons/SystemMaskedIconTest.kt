package io.github.aj4abinjacob.chikku.platform.icons

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.settings.IconShape
import io.github.aj4abinjacob.chikku.settings.iconpicker.IconPickerLoader
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * MIUI configures a plain square `config_icon_mask`; a raw adaptive icon (the Google Calendar daily
 * icon, with an opaque white background layer) then drew as a white square plate with a stray
 * outline offset from the icon.
 *
 * Robolectric's system mask is not square and cannot be changed, so the platform's own square-mask
 * drawing cannot be reproduced here; these tests pin what Chikku draws instead, at an offset origin
 * like a real icon cell.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SystemMaskedIconTest {

    private val miuiSquareMask = "M50,0L100,0 100,100 0,100 0,0z"
    private val circleMask = "M50,0 A50,50 0 1,1 50,100 A50,50 0 1,1 50,0 Z"

    @After
    fun restoreMask() = IconShaper.overrideSquareMaskForTest(null)

    private fun adaptive() = AdaptiveIconDrawable(ColorDrawable(Color.WHITE), ColorDrawable(Color.BLUE))

    private fun render(icon: Drawable): Bitmap {
        val bitmap = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888)
        icon.setBounds(60, 60, 240, 240)
        icon.draw(Canvas(bitmap))
        return bitmap
    }

    @Test
    fun squareMaskIsRecognisedAndCurvedMasksAreNot() {
        assertThat(isSquareMask(miuiSquareMask)).isTrue()
        assertThat(isSquareMask(circleMask)).isFalse()
        assertThat(isSquareMask(null)).isFalse()
        // A diamond is straight-edged but covers half the viewport only.
        assertThat(isSquareMask("M50,0L100,50 50,100 0,50z")).isFalse()
    }

    @Test
    fun rawAdaptiveIconOnSquareMaskIsDrawnAsCircleInsideItsBounds() {
        val icon = IconShaper.systemSafe(adaptive(), squareMask = true)
        assertThat(icon).isInstanceOf(SystemMaskedIconDrawable::class.java)

        val bitmap = render(icon)
        // Centre carries the icon, the corners of the bounds are clipped away (no square plate)
        // and nothing is drawn outside the bounds (no stray box offset up and left).
        assertThat(bitmap.getPixel(150, 150)).isEqualTo(Color.BLUE)
        assertThat(Color.alpha(bitmap.getPixel(64, 64))).isEqualTo(0)
        assertThat(Color.alpha(bitmap.getPixel(236, 236))).isEqualTo(0)
        for (offset in listOf(0, 10, 30, 55, 245, 270, 299)) {
            assertThat(Color.alpha(bitmap.getPixel(offset, offset))).isEqualTo(0)
            assertThat(Color.alpha(bitmap.getPixel(offset, 150))).isEqualTo(0)
        }
    }

    @Test
    fun copiesKeepTheCircleAlphaAndFilterAndDrawIndependently() {
        val icon = IconShaper.systemSafe(adaptive(), squareMask = true)
        icon.alpha = 128
        icon.colorFilter = PorterDuffColorFilter(Color.RED, PorterDuff.Mode.SRC_IN)
        val copy = icon.constantState!!.newDrawable()
        assertThat(copy).isInstanceOf(SystemMaskedIconDrawable::class.java)
        assertThat(copy).isNotSameInstanceAs(icon)
        assertThat(copy.alpha).isEqualTo(128)
        assertThat(copy.colorFilter).isNotNull()
        val bitmap = render(copy)
        assertThat(Color.alpha(bitmap.getPixel(64, 64))).isEqualTo(0)
        assertThat(Color.red(bitmap.getPixel(150, 150))).isGreaterThan(Color.blue(bitmap.getPixel(150, 150)))
    }

    @Test
    fun normalMasksAndNonAdaptiveIconsAreLeftAlone() {
        val icon = adaptive()
        assertThat(IconShaper.systemSafe(icon, squareMask = false)).isSameInstanceAs(icon)
        val legacy = ColorDrawable(Color.GREEN)
        assertThat(IconShaper.systemSafe(legacy, squareMask = true)).isSameInstanceAs(legacy)
    }

    @Test
    fun systemShapeAndPackAppIconFollowTheSquareMask() {
        IconShaper.overrideSquareMaskForTest(true)
        assertThat(IconShaper.apply(adaptive(), IconShape.SYSTEM, reshapeLegacy = false))
            .isInstanceOf(SystemMaskedIconDrawable::class.java)

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        shadowOf(context.packageManager).apply {
            installPackage(android.content.pm.PackageInfo().apply {
                packageName = "pack.app"
                applicationInfo = android.content.pm.ApplicationInfo().apply { packageName = "pack.app" }
            })
            setApplicationIcon("pack.app", adaptive())
        }
        val noIcons = object : io.github.aj4abinjacob.chikku.platform.IconProvider {
            override suspend fun icon(key: io.github.aj4abinjacob.chikku.core.model.AppKey): Drawable = ColorDrawable()
            override fun invalidate(packageName: String) = Unit
        }
        val loader = IconPickerLoader(context, IconPackRepository(context), noIcons, io.github.aj4abinjacob.chikku.platform.ShortcutIconLoader(context))
        val icon = runBlocking { loader.packAppIcon("pack.app") }
        assertThat(icon).isInstanceOf(SystemMaskedIconDrawable::class.java)

        IconShaper.overrideSquareMaskForTest(false)
        assertThat(runBlocking { loader.packAppIcon("pack.app") }).isInstanceOf(AdaptiveIconDrawable::class.java)
    }
}
