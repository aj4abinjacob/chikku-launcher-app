package io.github.aj4abinjacob.chikku.home

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestHomeApplication::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DockImageTest {
    @Test
    fun photoPreservesRoundedMaskAndBackgroundOpacity() {
        val photo = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val mask = GradientDrawable().apply { setColor(0x800000FF.toInt()); cornerRadius = 16f }
        val drawable = DockImageDrawable(mask, photo).apply { setBounds(0, 0, 100, 60) }
        val target = Bitmap.createBitmap(100, 60, Bitmap.Config.ARGB_8888)
        drawable.draw(Canvas(target))
        assertThat(Color.alpha(target.getPixel(50, 30))).isWithin(1).of(128)
        assertThat(Color.red(target.getPixel(50, 30))).isEqualTo(255)
        assertThat(Color.alpha(target.getPixel(0, 0))).isEqualTo(0)
    }

    @Test
    fun importedUnselectedUrisNeverGrantOrReadDocuments() = runTest {
        val loader = DockImageLoader(ApplicationProvider.getApplicationContext<Application>(), Dispatchers.Unconfined)
        listOf(null, "https://example.com/image.jpg", "file:///etc/passwd", "content://unselected/image", "a".repeat(3000)).forEach {
            assertThat(loader.load(it)).isNull()
        }
    }
}
