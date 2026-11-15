package io.github.aj4abinjacob.chikku.settings

import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.settings.BadgeSettings
import io.github.aj4abinjacob.chikku.core.model.settings.BadgeStyle
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.home.AppIconView
import io.github.aj4abinjacob.chikku.home.TestHomeApplication
import io.github.aj4abinjacob.chikku.settings.sections.BadgeSamplePreview
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/** The Badges settings preview redraws the sample badge at the size under the slider thumb. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestHomeApplication::class)
class BadgeSizePreviewTest {

    private fun View.findIcon(): AppIconView? = when (this) {
        is AppIconView -> this
        is ViewGroup -> (0 until childCount).firstNotNullOfOrNull { getChildAt(it).findIcon() }
        else -> null
    }

    /**
     * Composes a fresh preview at [scale], the unsaved value the slider passes while dragging. Each
     * size gets a new ComposeView because only a first composition runs at once. Recomposition waits
     * for a frame from Compose's per-thread UI dispatcher, which Robolectric can leave bound to an
     * earlier test's reset Choreographer; changing a value in place then never redrew in some test
     * orders, and this test failed about one full run in three.
     */
    private fun ComponentActivity.previewIcon(settings: LauncherSettings, scale: Float): AppIconView {
        val preview = ComposeView(this)
        preview.setContent { BadgeSamplePreview(settings, sampleCount = 3, badgeScale = scale) }
        setContentView(preview)
        ShadowLooper.idleMainLooper()
        return checkNotNull(findViewById<View>(android.R.id.content).findIcon())
    }

    @Test
    fun previewFollowsUnsavedDragValue() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        // The saved size stays 100%; the preview draws whatever size is under the thumb.
        val settings = LauncherSettings(badges = BadgeSettings(style = BadgeStyle.COUNT, badgeScale = 1f))
        for (scale in listOf(1f, 1.35f, .55f)) {
            assertThat(activity.previewIcon(settings, scale).badge!!.badgeScale).isEqualTo(scale)
        }
    }
}
