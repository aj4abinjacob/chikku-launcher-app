package io.github.aj4abinjacob.chikku.prompt

import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.home.TestHomeApplication
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/**
 * ShortcutPinActivity shows only a prompt and never calls setContentView, so its content root has
 * no lifecycle owner. A prompt must still attach there: before the fix, Firefox's Add to Home screen
 * crashed Chikku with "ViewTreeLifecycleOwner not found".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestHomeApplication::class)
class ChikkuPromptsHostTest {
    @Test
    fun promptShowsInAnActivityWithoutContentView() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        var dismissed = false

        val handle = ChikkuPrompts(activity).show(
            PromptSpec(
                icon = R.drawable.ic_settings_home,
                title = "Sample title",
                message = "Sample message",
                primary = PromptAction("Add") {},
                dismissLabel = "Cancel",
                onDismiss = { dismissed = true },
            ),
        )
        ShadowLooper.idleMainLooper()

        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        assertThat(handle.isShowing).isTrue()
        assertThat(root.getChildAt(root.childCount - 1)).isInstanceOf(ComposeView::class.java)

        handle.dismiss()
        assertThat(dismissed).isTrue()
        assertThat(root.childCount).isEqualTo(0)
    }
}
