package io.github.aj4abinjacob.chikku.platform.icons

import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.VectorDrawable
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 33])
class LauncherBrandIconTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test
    fun supportedSdkLoadsInkAdaptiveIcon() {
        val icon = context.getDrawable(R.mipmap.ic_launcher)
        assertThat(icon).isInstanceOf(AdaptiveIconDrawable::class.java)
        icon as AdaptiveIconDrawable
        assertThat(icon.foreground).isInstanceOf(VectorDrawable::class.java)
        assertThat(icon.background).isInstanceOf(ColorDrawable::class.java)
        assertThat((icon.background as ColorDrawable).color).isEqualTo(Color.rgb(31, 29, 28)) // White Coat #1F1D1C
    }

    @Test
    @Config(sdk = [33])
    fun android13LoadsExplicitMonochromeLayer() {
        val icon = context.getDrawable(R.mipmap.ic_launcher) as AdaptiveIconDrawable
        assertThat(icon.monochrome).isInstanceOf(VectorDrawable::class.java)
    }

    @Test
    fun foregroundAndMonochromeAreFullBleedPathVectors() {
        val foreground = vectorGeometry(R.drawable.ic_launcher_foreground)
        val monochrome = vectorGeometry(R.drawable.ic_launcher_monochrome)
        for (geometry in listOf(foreground, monochrome)) {
            assertThat(geometry.keys).containsExactly("vector", "path")
            assertThat(geometry["vector"]!!.single().take(2).map { it!!.toFloat() }).containsExactly(108f, 108f)
            assertThat(geometry["path"]!!.map { it.single() }).doesNotContain(null)
        }
        // White Coat: frame, ears, screen tiles and dock; the themed layer is the same mark in fewer shapes.
        assertThat(foreground["path"]!!.size).isGreaterThan(monochrome["path"]!!.size)
        assertThat(monochrome["path"]!!).isNotEmpty()
    }

    private fun vectorGeometry(resource: Int): Map<String, List<List<String?>>> {
        val result = linkedMapOf<String, MutableList<List<String?>>>()
        val android = "http://schemas.android.com/apk/res/android"
        context.resources.getXml(resource).use { parser ->
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType != XmlPullParser.START_TAG) continue
                val names = when (parser.name) {
                    "vector" -> listOf("viewportWidth", "viewportHeight", "width", "height")
                    "path" -> listOf("pathData")
                    else -> error("Unexpected icon geometry element: ${parser.name}")
                }
                result.getOrPut(parser.name) { mutableListOf() } += names.map { parser.getAttributeValue(android, it) }
            }
        }
        return result
    }
}
