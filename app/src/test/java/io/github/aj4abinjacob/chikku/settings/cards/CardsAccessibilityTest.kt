package io.github.aj4abinjacob.chikku.settings.cards

import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.AndroidUiFrameClock
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.home.TestHomeApplication
import io.github.aj4abinjacob.chikku.settings.sections.CardsAddButton
import kotlinx.coroutines.android.asCoroutineDispatcher
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

/** Real Material3 controls and merged semantics; no saved settings or platform inventories. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 36], application = TestHomeApplication::class, qualifiers = "w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CardsAccessibilityTest {
    @Test fun populatedListAddActionHasResourceNameClickAndMinimumBounds() {
        var activated = false
        fixture({ CardsAddButton(enabled = true, onClick = { activated = true }) }) { activity, nodes ->
            val label = activity.getString(R.string.cards_add)
            val action = nodes.single { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(label) == true }
            assertThat(action.config.contains(SemanticsProperties.Disabled)).isFalse()
            assertMinimumBounds(action, activity.resources.displayMetrics.density)
            action.config[SemanticsActions.OnClick].action!!.invoke()
            assertThat(activated).isTrue()
        }
    }

    @Test fun disabledPopulatedListActionKeepsItsNameAndCannotActivate() {
        var activated = false
        fixture({ CardsAddButton(enabled = false, onClick = { activated = true }) }) { activity, nodes ->
            val label = activity.getString(R.string.cards_add)
            val action = nodes.single { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(label) == true }
            assertThat(action.config.contains(SemanticsProperties.Disabled)).isTrue()
            assertMinimumBounds(action, activity.resources.displayMetrics.density)
            action.config[SemanticsActions.OnClick].action!!.invoke()
            assertThat(activated).isFalse()
        }
    }

    @Test fun emptyListCreateActionHasTextClickAndMinimumBounds() {
        var activated = false
        fixture({ CardsCreateButton(enabled = true, onCreate = { activated = true }) }) { activity, nodes ->
            val label = activity.getString(R.string.cards_intro_create)
            val action = nodes.single { it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == label } == true }
            assertThat(action.config.contains(SemanticsProperties.Disabled)).isFalse()
            assertMinimumBounds(action, activity.resources.displayMetrics.density)
            action.config[SemanticsActions.OnClick].action!!.invoke()
            assertThat(activated).isTrue()
        }
    }

    @Test fun disabledEmptyListCreateActionExposesDisabledSemantics() {
        fixture({ CardsCreateButton(enabled = false, onCreate = { error("A disabled creation button was activated") }) }) { activity, nodes ->
            val label = activity.getString(R.string.cards_intro_create)
            val action = nodes.single { it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == label } == true }
            assertThat(action.config.contains(SemanticsProperties.Disabled)).isTrue()
            assertMinimumBounds(action, activity.resources.displayMetrics.density)
        }
    }

    private fun assertMinimumBounds(action: SemanticsNode, density: Float) {
        assertThat(action.boundsInRoot.width / density).isAtLeast(48f)
        assertThat(action.boundsInRoot.height / density).isAtLeast(48f)
    }

    private fun fixture(content: @Composable () -> Unit,
        verify: (ComponentActivity, List<SemanticsNode>) -> Unit) {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        val frameContext = Handler(Looper.getMainLooper()).asCoroutineDispatcher() +
            AndroidUiFrameClock(Choreographer.getInstance())
        val recomposer = activity.window.decorView.createLifecycleAwareWindowRecomposer(frameContext, activity.lifecycle)
        activity.setContent(parent = recomposer) { MaterialTheme { content() } }
        try {
            Snapshot.sendApplyNotifications()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
            val host = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as ViewGroup
            val root = host.getChildAt(0) as ViewRootForTest
            root.measureAndLayoutForTest()
            verify(activity, root.semanticsOwner.rootSemanticsNode.flatten())
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private fun SemanticsNode.flatten(): List<SemanticsNode> = listOf(this) + children.flatMap { it.flatten() }
}
