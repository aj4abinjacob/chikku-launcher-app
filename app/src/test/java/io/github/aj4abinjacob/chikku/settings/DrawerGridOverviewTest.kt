package io.github.aj4abinjacob.chikku.settings

import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.AndroidUiFrameClock
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerSettings
import io.github.aj4abinjacob.chikku.core.model.settings.NightSettings
import io.github.aj4abinjacob.chikku.home.TestHomeApplication
import io.github.aj4abinjacob.chikku.settings.drawer.DrawerGridPreview
import io.github.aj4abinjacob.chikku.settings.drawer.DrawerGridPreviewFrame
import kotlinx.coroutines.android.asCoroutineDispatcher
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestHomeApplication::class, qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DrawerGridOverviewTest {
    @Test
    fun actualSizeIsDefaultAndModeFollowsRowAndSizeDraftsAndTheButton() {
        val draft = mutableStateOf(DrawerSettings(columns = 5, rows = 5, automaticRowSpacing = false))
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        // A fresh dispatcher/clock avoids Compose reusing a previous test's Choreographer.
        val frameContext = Handler(Looper.getMainLooper()).asCoroutineDispatcher() +
            AndroidUiFrameClock(Choreographer.getInstance())
        val recomposer = activity.window.decorView.createLifecycleAwareWindowRecomposer(frameContext, activity.lifecycle)
        activity.setContent(parent = recomposer) {
            MaterialTheme { DrawerGridPreview(draft.value, NightSettings(), desktopIconScale = 1f) }
        }
        ShadowLooper.idleMainLooper()
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        val root = (content.getChildAt(0) as ViewGroup).getChildAt(0) as ViewRootForTest
        fun nodes(): List<SemanticsNode> {
            // Draft changes recompose on a scheduled frame before semantics and layout update.
            Snapshot.sendApplyNotifications()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
            root.measureAndLayoutForTest()
            return root.semanticsOwner.rootSemanticsNode.flatten()
        }
        fun toggle(label: String) {
            val button = nodes().single { node ->
                node.config.contains(SemanticsActions.OnClick) &&
                    node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == label } == true
            }
            button.config[SemanticsActions.OnClick].action!!.invoke()
            nodes()
        }
        fun description() = nodes().flatMap { it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() }
            .single { it.startsWith("Drawer preview:") }
        try {
            nodes()
            val frame = content.findFrame()!!
            // Actual size by default: only the upper rows fit, drawn unscaled.
            assertThat(description()).contains("shown at actual size")
            assertThat(frame.drawScale).isEqualTo(1f)
            assertThat(frame.visibleCells).isLessThan(25)
            val height = frame.measuredHeight
            // A row-count draft switches to the full grid, so every fitted row shows.
            draft.value = draft.value.copy(rows = 6)
            assertThat(description()).contains("full grid is scaled to fit")
            assertThat(description()).contains("6 rows per screen")
            assertThat(frame.visibleCells).isEqualTo(30)
            assertThat(frame.measuredHeight).isEqualTo(height)
            // An icon-size draft returns to actual size, where size steps are legible.
            draft.value = draft.value.copy(iconScale = 1.2f)
            assertThat(description()).contains("icons at 120%, shown at actual size")
            assertThat(frame.drawScale).isEqualTo(1f)
            assertThat(frame.measuredHeight).isEqualTo(height)
            // The button still switches by hand, both ways.
            toggle("Full grid")
            assertThat(description()).contains("full grid is scaled to fit")
            assertThat(frame.visibleCells).isEqualTo(30)
            assertThat(frame.measuredHeight).isEqualTo(height)
            toggle("Actual size")
            assertThat(description()).contains("shown at actual size")
            assertThat(frame.visibleCells).isLessThan(30)
            assertThat(frame.measuredHeight).isEqualTo(height)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private fun SemanticsNode.flatten(): List<SemanticsNode> = listOf(this) + children.flatMap { it.flatten() }

    private fun View.findFrame(): DrawerGridPreviewFrame? = when (this) {
        is DrawerGridPreviewFrame -> this
        is ViewGroup -> (0 until childCount).firstNotNullOfOrNull { getChildAt(it).findFrame() }
        else -> null
    }
}
