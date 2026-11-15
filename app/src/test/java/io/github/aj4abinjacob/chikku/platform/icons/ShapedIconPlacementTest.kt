package io.github.aj4abinjacob.chikku.platform.icons

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.settings.FolderIconBackground
import io.github.aj4abinjacob.chikku.core.model.settings.FolderPreviewStyle
import io.github.aj4abinjacob.chikku.core.model.settings.IconShape
import io.github.aj4abinjacob.chikku.home.folder.FolderIconDrawable
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A reshaped legacy icon must sit centred in its shape wherever the icon's bounds are, because
 * AdaptiveIconDrawable positions its layers relative to the canvas origin, not to its bounds.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShapedIconPlacementTest {

    /** A legacy icon: opaque green square on transparent, as MIUI's pre-masked bitmaps roughly are. */
    private fun legacy(): Drawable = BitmapDrawable(
        null,
        Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) },
    )

    private fun render(canvasSize: Int, draw: (Canvas) -> Unit): Bitmap =
        Bitmap.createBitmap(canvasSize, canvasSize, Bitmap.Config.ARGB_8888).also { draw(Canvas(it)) }

    /** Bounding box of the green pixels. */
    private fun greenBox(bitmap: Bitmap): android.graphics.Rect {
        val box = android.graphics.Rect(Int.MAX_VALUE, Int.MAX_VALUE, Int.MIN_VALUE, Int.MIN_VALUE)
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            if (bitmap.getPixel(x, y) == Color.GREEN) {
                box.left = minOf(box.left, x); box.top = minOf(box.top, y)
                box.right = maxOf(box.right, x + 1); box.bottom = maxOf(box.bottom, y + 1)
            }
        }
        return box
    }

    @Test
    fun legacyIconIsCentredInTheShapeAtAnyOffsetAndSize() {
        for (shape in listOf(IconShape.TEARDROP, IconShape.CIRCLE, IconShape.SQUIRCLE)) {
            for ((left, top, size) in listOf(Triple(0, 0, 100), Triple(40, 25, 100), Triple(70, 90, 64), Triple(13, 7, 150))) {
                val icon = IconShaper.apply(legacy(), shape, reshapeLegacy = true)
                icon.setBounds(left, top, left + size, top + size)
                val box = greenBox(render(300) { icon.draw(it) })
                assertThat(box.isEmpty).isFalse()
                val tolerance = size / 10
                assertThat(box.centerX()).isWithin(tolerance).of(left + size / 2)
                assertThat(box.centerY()).isWithin(tolerance).of(top + size / 2)
                // Legacy art fills 1.5 * (1 - 2 * 0.22) of the shape: never tiny, never overflowing.
                assertThat(box.width().toFloat() / size).isWithin(0.1f).of(0.84f)
                assertThat(box.left).isAtLeast(left)
                assertThat(box.right).isAtMost(left + size)
            }
        }
    }

    @Test
    fun nonSquareBoundsCentreTheShape() {
        val icon = IconShaper.apply(legacy(), IconShape.CIRCLE, reshapeLegacy = true)
        icon.setBounds(20, 40, 20 + 100, 40 + 140)
        val box = greenBox(render(300) { icon.draw(it) })
        assertThat(box.centerX()).isWithin(6).of(70)
        assertThat(box.centerY()).isWithin(6).of(110)
    }

    @Test
    fun boundsChangesAfterDrawingFollowTheNewBounds() {
        val icon = IconShaper.apply(legacy(), IconShape.TEARDROP, reshapeLegacy = true)
        icon.setBounds(0, 0, 100, 100)
        render(300) { icon.draw(it) }
        icon.setBounds(120, 150, 220, 250)
        val box = greenBox(render(300) { icon.draw(it) })
        assertThat(box.centerX()).isWithin(10).of(170)
        assertThat(box.centerY()).isWithin(10).of(200)
    }

    @Test
    fun copiedIconsAreCentredAtAnyOffset() {
        // IconBinder and FolderIconBinder bind a newDrawable() copy of the cached icon.
        val cached = IconShaper.apply(legacy(), IconShape.TEARDROP, reshapeLegacy = true)
        val copy = cached.constantState!!.newDrawable().mutate()
        copy.setBounds(120, 150, 220, 250)
        val box = greenBox(render(300) { copy.draw(it) })
        assertThat(box.isEmpty).isFalse()
        assertThat(box.centerX()).isWithin(10).of(170)
        assertThat(box.centerY()).isWithin(10).of(200)
        // The cached original is untouched by the copy's bounds.
        cached.setBounds(0, 0, 100, 100)
        val original = greenBox(render(300) { cached.draw(it) })
        assertThat(original.centerX()).isWithin(10).of(50)
        assertThat(original.centerY()).isWithin(10).of(50)
    }

    @Test
    fun folderPreviewShowsItsShapedMiniIcons() {
        val previews = List(4) { IconShaper.apply(legacy(), IconShape.TEARDROP, reshapeLegacy = true) }
        val folder = FolderIconDrawable(previews, FolderPreviewStyle.GRID, FolderIconBackground.CIRCLE, Color.LTGRAY)
        folder.setBounds(30, 30, 230, 230)
        val box = greenBox(render(260) { folder.draw(it) })
        assertThat(box.isEmpty).isFalse()
        // The four mini icons together stay inside the folder disc and are centred on it.
        assertThat(box.centerX()).isWithin(12).of(130)
        assertThat(box.centerY()).isWithin(12).of(130)
        assertThat(box.left).isAtLeast(30)
        assertThat(box.right).isAtMost(230)
    }

    @Test
    fun adaptiveIconsKeepTheirLayersCentred() {
        val adaptive = android.graphics.drawable.AdaptiveIconDrawable(ColorDrawable(Color.GREEN), ColorDrawable(Color.TRANSPARENT))
        val icon = IconShaper.apply(adaptive, IconShape.CIRCLE, reshapeLegacy = false)
        icon.setBounds(50, 60, 150, 160)
        val box = greenBox(render(220) { icon.draw(it) })
        assertThat(box.centerX()).isWithin(2).of(100)
        assertThat(box.centerY()).isWithin(2).of(110)
        assertThat(box.width()).isAtLeast(95)
    }
}
