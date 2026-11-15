package io.github.aj4abinjacob.chikku.settings

import android.graphics.drawable.LayerDrawable
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.settings.IconShape
import io.github.aj4abinjacob.chikku.platform.icons.ShapedIconDrawable
import io.github.aj4abinjacob.chikku.settings.previews.previewSampleIcon
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The synthetic sample icons follow the icon shape setting, still without any real app data. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PreviewSampleIconShapeTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test fun systemShapeKeepsTheOriginalSampleDisc() {
        assertThat(previewSampleIcon(context, 0)).isInstanceOf(LayerDrawable::class.java)
        assertThat(previewSampleIcon(context, 0, IconShape.SYSTEM)).isInstanceOf(LayerDrawable::class.java)
    }

    @Test fun otherShapesGoThroughTheLaunchersIconShaper() {
        IconShape.entries.filter { it != IconShape.SYSTEM }.forEach { shape ->
            assertThat(previewSampleIcon(context, 1, shape)).isInstanceOf(ShapedIconDrawable::class.java)
        }
    }
}
