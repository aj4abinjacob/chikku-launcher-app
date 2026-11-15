package io.github.aj4abinjacob.chikku.settings.components

import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.AndroidUiFrameClock
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.search.WebSearchUrls
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.core.model.settings.WebSearchEngine
import io.github.aj4abinjacob.chikku.home.TestHomeApplication
import kotlinx.coroutines.android.asCoroutineDispatcher
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import java.time.Duration

/** The web search engine chooser shared by the Dock and Search settings pages. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestHomeApplication::class, qualifiers = "w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WebSearchEngineChoiceTest {

    private class Harness(
        val texts: (ViewRootForTest) -> List<String>,
        val root: ViewRootForTest,
        val settings: () -> LauncherSettings,
    ) {
        fun click(label: String, on: ViewRootForTest = root) {
            val node = on.semanticsOwner.rootSemanticsNode.flatten().last { node ->
                node.config.getOrNull(SemanticsActions.OnClick) != null &&
                    node.flatten().any { n -> n.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == label } }
            }
            node.config[SemanticsActions.OnClick].action!!.invoke()
        }

        fun dialogRoot(): ViewRootForTest {
            texts(root)
            return ShadowDialog.getLatestDialog()!!.window!!.decorView.findRoot()!!
        }
    }

    private fun render(initial: LauncherSettings, check: Harness.() -> Unit) {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        val frameContext = Handler(Looper.getMainLooper()).asCoroutineDispatcher() +
            AndroidUiFrameClock(Choreographer.getInstance())
        val recomposer = activity.window.decorView.createLifecycleAwareWindowRecomposer(frameContext, activity.lifecycle)
        var current = initial
        activity.setContent(parent = recomposer) {
            var settings by remember { mutableStateOf(initial) }
            MaterialTheme {
                Column {
                    WebSearchSettings(settings) { change -> settings = change(settings); current = settings }
                }
            }
        }
        val root = activity.findViewById<ViewGroup>(android.R.id.content).findRoot()!!
        fun texts(on: ViewRootForTest): List<String> {
            repeat(3) {
                Snapshot.sendApplyNotifications()
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
                on.measureAndLayoutForTest()
            }
            return on.semanticsOwner.rootSemanticsNode.flatten()
                .flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty().map { text -> text.text } }
        }
        try {
            Harness(::texts, root) { current }.check()
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun everyEngineOptionShowsItsWebAddress() {
        render(LauncherSettings()) {
            assertThat(texts(root)).contains(WebSearchUrls.engineName(WebSearchEngine.DUCKDUCKGO))
            click(WebSearchUrls.engineName(WebSearchEngine.DUCKDUCKGO))
            val shown = texts(dialogRoot())
            WebSearchEngine.entries.forEach { engine ->
                assertThat(shown)
                    .containsAtLeast(WebSearchUrls.engineName(engine), WebSearchUrls.engineHost(engine)).inOrder()
            }
        }
    }

    @Test
    fun choosingAnEngineSavesIt() {
        render(LauncherSettings()) {
            click(WebSearchUrls.engineName(WebSearchEngine.DUCKDUCKGO))
            val dialog = dialogRoot()
            texts(dialog)
            click(WebSearchUrls.engineName(WebSearchEngine.ECOSIA), dialog)
            texts(root)
            assertThat(settings().search.webEngine).isEqualTo(WebSearchEngine.ECOSIA)
        }
    }

    private companion object {
        fun SemanticsNode.flatten(): List<SemanticsNode> = listOf(this) + children.flatMap { it.flatten() }

        fun View.findRoot(): ViewRootForTest? {
            if (this is ViewRootForTest) return this
            if (this is ViewGroup) for (i in 0 until childCount) getChildAt(i).findRoot()?.let { return it }
            return null
        }
    }
}
