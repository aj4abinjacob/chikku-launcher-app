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
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.AndroidUiFrameClock
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.GestureAction
import io.github.aj4abinjacob.chikku.core.model.LauncherApp
import io.github.aj4abinjacob.chikku.core.model.ProfileType
import io.github.aj4abinjacob.chikku.home.FakeHomeDependencies
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
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestHomeApplication::class, qualifiers = "w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GestureActionPickerLayoutTest {

    // --- Entries (pure) ---------------------------------------------------------------------------

    @Test
    fun actionsAreGroupedWithAppsAfterLauncherActions() {
        val entries = pickerEntries(GestureAction.simpleActions, allowDefault = true)
        assertThat(entries).containsExactly(
            PickerEntry.UseDefault,
            PickerEntry.Simple(GestureAction.None),
            PickerEntry.Heading(PickerSection.LAUNCHER),
            PickerEntry.Simple(GestureAction.OpenDrawer),
            PickerEntry.Simple(GestureAction.OpenSearch),
            PickerEntry.Simple(GestureAction.OpenCards),
            PickerEntry.Simple(GestureAction.GoToDefaultPage),
            PickerEntry.Simple(GestureAction.ToggleDock),
            PickerEntry.Simple(GestureAction.OpenLauncherSettings),
            PickerEntry.Heading(PickerSection.APPS),
            PickerEntry.ChooseApp,
            PickerEntry.ChooseShortcut,
            PickerEntry.Heading(PickerSection.SYSTEM),
            PickerEntry.Simple(GestureAction.ExpandNotifications),
            PickerEntry.Simple(GestureAction.ExpandQuickSettings),
            PickerEntry.Simple(GestureAction.Recents),
            PickerEntry.Simple(GestureAction.LockScreen),
            PickerEntry.Heading(PickerSection.VOICE),
            PickerEntry.Simple(GestureAction.Assistant),
            PickerEntry.Simple(GestureAction.VoiceSearch),
        ).inOrder()
    }

    @Test
    fun emptySectionsAreLeftOutButAppsAreAlwaysOffered() {
        val entries = pickerEntries(listOf(GestureAction.VoiceSearch, GestureAction.ExpandNotifications), allowDefault = false)
        assertThat(entries).containsExactly(
            PickerEntry.Heading(PickerSection.APPS),
            PickerEntry.ChooseApp,
            PickerEntry.ChooseShortcut,
            PickerEntry.Heading(PickerSection.SYSTEM),
            PickerEntry.Simple(GestureAction.ExpandNotifications),
            PickerEntry.Heading(PickerSection.VOICE),
            PickerEntry.Simple(GestureAction.VoiceSearch),
        ).inOrder()
    }

    @Test
    fun exactlyOneEntryIsSelectedForEveryKindOfAction() {
        val entries = pickerEntries(GestureAction.simpleActions, allowDefault = true)
        val currents = listOf(null) + GestureAction.simpleActions + listOf(
            GestureAction.LaunchApp(AppKey("p", "p.A", 0)),
            GestureAction.LaunchShortcut("p", "s", 0),
        )
        currents.forEach { current -> assertThat(entries.count { it.isSelected(current) }).isEqualTo(1) }
    }

    // --- Rendered page ----------------------------------------------------------------------------

    private val app = LauncherApp(AppKey("sample.alpha", "Main", 0), "Alpha", ProfileType.PERSONAL)

    private fun render(current: GestureAction?, defaultSummary: String? = null, check: (texts: () -> List<String>) -> Unit) {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        val frameContext = Handler(Looper.getMainLooper()).asCoroutineDispatcher() +
            AndroidUiFrameClock(Choreographer.getInstance())
        val recomposer = activity.window.decorView.createLifecycleAwareWindowRecomposer(frameContext, activity.lifecycle)
        activity.setContent(parent = recomposer) {
            CompositionLocalProvider(LocalHomeDependencies provides FakeHomeDependencies()) {
                MaterialTheme {
                    Box(Modifier.height(300.dp)) {
                        GestureActionPickerBody(
                            state = rememberGestureActionPickerState(),
                            current = current,
                            apps = listOf(app),
                            onPick = {},
                            allowDefault = true,
                            actions = GestureAction.simpleActions,
                            defaultSummary = defaultSummary,
                        )
                    }
                }
            }
        }
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        val root = (content.getChildAt(0) as ViewGroup).getChildAt(0) as ViewRootForTest
        fun texts(): List<String> {
            repeat(3) {
                Snapshot.sendApplyNotifications()
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
                root.measureAndLayoutForTest()
            }
            return root.semanticsOwner.rootSemanticsNode.flatten()
                .flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty().map { text -> text.text } }
        }
        try {
            check(::texts)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun opensAtTheTopWithDefaultSummaryWhenTheDefaultIsSelected() {
        render(current = null, defaultSummary = "Open app drawer") { texts ->
            assertThat(texts()).containsAtLeast("Use default", "Open app drawer", "Do nothing", "Chikku").inOrder()
        }
    }

    @Test
    fun scrollsTheSelectedActionIntoView() {
        render(current = GestureAction.VoiceSearch) { texts ->
            val shown = texts()
            assertThat(shown).contains("Voice search")
            assertThat(shown).doesNotContain("Use default")
        }
    }

    @Test
    fun chosenAppIsNamedUnderAppRow() {
        render(current = GestureAction.LaunchApp(app.key)) { texts ->
            assertThat(texts()).containsAtLeast("App…", "Open Alpha").inOrder()
        }
    }

    @Test
    fun accessibilityActionsSayTheyNeedTheService() {
        render(current = GestureAction.LockScreen) { texts ->
            assertThat(texts()).containsAtLeast("Lock screen", "Needs the accessibility service").inOrder()
        }
    }

    private fun SemanticsNode.flatten(): List<SemanticsNode> = listOf(this) + children.flatMap { it.flatten() }
}
