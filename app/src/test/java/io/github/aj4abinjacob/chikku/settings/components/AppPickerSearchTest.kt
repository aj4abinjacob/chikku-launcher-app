package io.github.aj4abinjacob.chikku.settings.components

import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.AndroidUiFrameClock
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.GestureAction
import io.github.aj4abinjacob.chikku.core.model.LauncherApp
import io.github.aj4abinjacob.chikku.core.model.ProfileType
import io.github.aj4abinjacob.chikku.core.model.search.DefaultAppSearcher
import io.github.aj4abinjacob.chikku.home.FakeHomeDependencies
import io.github.aj4abinjacob.chikku.home.HomeDependencies
import io.github.aj4abinjacob.chikku.home.TestHomeApplication
import io.github.aj4abinjacob.chikku.settings.LocalHomeDependencies
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
@Config(sdk = [36], application = TestHomeApplication::class, qualifiers = "w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppPickerSearchTest {
    private val apps = listOf("Alpha", "Beta", "Gamma").map {
        LauncherApp(AppKey("sample.${it.lowercase()}", "Main", 0), it, ProfileType.PERSONAL)
    }

    @Test fun swipeAppPickerCanTypeClearAndSelectWithACurrentTarget() {
        val base = FakeHomeDependencies()
        val deps = object : HomeDependencies by base { override val appSearcher = DefaultAppSearcher() }
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        var picked: GestureAction? = null
        // A fresh dispatcher/clock avoids Compose reusing a previous test's Choreographer.
        val frameContext = Handler(Looper.getMainLooper()).asCoroutineDispatcher() +
            AndroidUiFrameClock(Choreographer.getInstance())
        val recomposer = activity.window.decorView.createLifecycleAwareWindowRecomposer(frameContext, activity.lifecycle)
        activity.setContent(parent = recomposer) {
            CompositionLocalProvider(LocalHomeDependencies provides deps) {
                MaterialTheme {
                    Box(Modifier.height(440.dp)) {
                        GestureActionPickerBody(
                            state = rememberGestureActionPickerState().apply { page = PickerPage.APPS },
                            current = GestureAction.LaunchApp(apps[0].key),
                            apps = apps,
                            onPick = { picked = it },
                            allowDefault = true,
                            actions = GestureAction.simpleActions,
                        )
                    }
                }
            }
        }
        ShadowLooper.idleMainLooper()
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        val root = (content.getChildAt(0) as ViewGroup).getChildAt(0) as ViewRootForTest
        fun nodes(): List<SemanticsNode> {
            // Recomposition runs on a scheduled frame; draining only due tasks can read stale UI.
            Snapshot.sendApplyNotifications()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
            root.measureAndLayoutForTest()
            return root.semanticsOwner.rootSemanticsNode.flatten()
        }
        fun search(text: String) {
            val field = nodes().single { it.config.contains(SemanticsActions.SetText) }
            field.config[SemanticsActions.RequestFocus].action!!.invoke()
            field.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString(text))
            nodes()
        }
        try {
            for (query in listOf("b", "be", "beta", "no match")) search(query)
            assertThat(nodes().texts()).contains("No apps match your search.")
            search("")
            assertThat(nodes().texts()).containsAtLeast("Alpha", "Beta", "Gamma")
            search("beta")
            val row = nodes().single { it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == "Beta" } == true }
            row.config[SemanticsActions.OnClick].action!!.invoke()
            assertThat(picked).isEqualTo(GestureAction.LaunchApp(apps[1].key))
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private fun SemanticsNode.flatten(): List<SemanticsNode> = listOf(this) + children.flatMap { it.flatten() }
    private fun List<SemanticsNode>.texts() = flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty().map { text -> text.text } }
}
