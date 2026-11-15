package io.github.aj4abinjacob.chikku.settings.components

import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.AndroidUiFrameClock
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
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

/**
 * The gesture action picker's sheet contents (header, back arrow, pages), drawn without the sheet
 * window like [ChoiceSheetTest]: this checks the paging the sheet wires up, not the sheet animation
 * or the system back dispatch inside the sheet window.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestHomeApplication::class, qualifiers = "w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GestureActionPickerSheetTest {

    private val app = LauncherApp(AppKey("sample.alpha", "Main", 0), "Alpha", ProfileType.PERSONAL)

    private class Rendered(val root: ViewRootForTest, val state: GestureActionPickerState, private val height: () -> Int) {
        /** Height of the sheet body (title and page), as the sheet would wrap it. */
        val sheetHeight: Int get() = height()

        /** Bounds of the clickable row that carries [text]. */
        fun rowBounds(text: String) = nodes().first { node ->
            node.config.getOrNull(SemanticsActions.OnClick) != null && node.allTexts().contains(text)
        }.boundsInRoot

        fun nodes(): List<SemanticsNode> {
            repeat(3) {
                Snapshot.sendApplyNotifications()
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
                root.measureAndLayoutForTest()
            }
            return root.semanticsOwner.rootSemanticsNode.flatten()
        }

        fun headings(): List<String> = nodes().filter { it.config.contains(SemanticsProperties.Heading) }
            .flatMap { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }

        fun backArrows(): List<SemanticsNode> = nodes().filter { node ->
            node.config.getOrNull(SemanticsActions.OnClick) != null &&
                node.allDescriptions().contains("Back")
        }

        fun click(text: String) {
            val node = nodes().first { node ->
                node.config.getOrNull(SemanticsActions.OnClick) != null && node.allTexts().contains(text)
            }
            node.config[SemanticsActions.OnClick].action!!.invoke()
            nodes()
        }

        fun clickBack() {
            backArrows().single().config[SemanticsActions.OnClick].action!!.invoke()
            nodes()
        }

        private fun SemanticsNode.allTexts(): List<String> =
            config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } + children.flatMap { it.allTexts() }

        private fun SemanticsNode.allDescriptions(): List<String> =
            config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() + children.flatMap { it.allDescriptions() }
    }

    private fun render(onPick: (GestureAction?) -> Unit = {}, check: (Rendered) -> Unit) {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        val frameContext = Handler(Looper.getMainLooper()).asCoroutineDispatcher() +
            AndroidUiFrameClock(Choreographer.getInstance())
        val recomposer = activity.window.decorView.createLifecycleAwareWindowRecomposer(frameContext, activity.lifecycle)
        var pickerState: GestureActionPickerState? = null
        var sheetHeight = 0
        activity.setContent(parent = recomposer) {
            CompositionLocalProvider(LocalHomeDependencies provides FakeHomeDependencies()) {
                MaterialTheme {
                    val state = rememberGestureActionPickerState()
                    pickerState = state
                    // The same pieces GestureActionPickerDialog puts in its SettingsBottomSheet, free
                    // to wrap their height as the sheet lets them.
                    Column(Modifier.fillMaxSize()) {
                    Box(Modifier.onSizeChanged { sheetHeight = it.height }) {
                    SettingsSheetBody(gestureActionPickerTitle(state, "Swipe up"), state.headerBack()) {
                        GestureActionPickerSheetContent {
                            GestureActionPickerBody(
                                state = state,
                                current = null,
                                apps = listOf(app),
                                onPick = onPick,
                                allowDefault = true,
                                actions = GestureAction.simpleActions,
                            )
                        }
                    }
                    }
                    }
                }
            }
        }
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        val root = (content.getChildAt(0) as ViewGroup).getChildAt(0) as ViewRootForTest
        try {
            val rendered = Rendered(root, checkNotNull(pickerState)) { sheetHeight }
            rendered.nodes()
            check(rendered)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun firstPageHasOneHeadingAndNoBackArrow() {
        render { sheet ->
            assertThat(sheet.headings()).contains("Swipe up")
            assertThat(sheet.headings().count { it == "Swipe up" || it == "Choose an app" }).isEqualTo(1)
            assertThat(sheet.backArrows()).isEmpty()
        }
    }

    @Test
    fun appPageHasABackArrowThatReturnsToTheActions() {
        render { sheet ->
            sheet.click("App…")
            assertThat(sheet.state.page).isEqualTo(PickerPage.APPS)
            assertThat(sheet.headings()).contains("Choose an app")
            assertThat(sheet.headings()).doesNotContain("Swipe up")
            assertThat(sheet.backArrows()).hasSize(1)

            sheet.clickBack()
            assertThat(sheet.state.page).isEqualTo(PickerPage.ACTIONS)
            assertThat(sheet.headings()).contains("Swipe up")
            assertThat(sheet.backArrows()).isEmpty()
        }
    }

    @Test
    fun aShortAppListHugsItsRowsWithNoGapBelow() {
        render { sheet ->
            sheet.click("App…")
            val row = sheet.rowBounds("Alpha")
            // Below the only app: the sheet body's 16dp bottom padding (32px at xhdpi), nothing else.
            assertThat(sheet.sheetHeight - row.bottom).isWithin(1f).of(32f)
            // Far below the sheet's cap (80% of the 800dp window = 1280px).
            assertThat(sheet.sheetHeight).isLessThan(640)
        }
    }

    @Test
    fun rowHighlightsSitOnTheSharedSheetInset() {
        render { sheet ->
            // "Use default" is the current choice (current = null), so its tint spans these bounds.
            val row = sheet.rowBounds("Use default")
            // SheetListPadding (12dp = 24px at xhdpi) from each edge of the 360dp (720px) window,
            // as in ChoiceSheet, so its icon and label start on SheetPadding under the title.
            assertThat(row.left).isWithin(0.5f).of(24f)
            assertThat(row.right).isWithin(0.5f).of(720f - 24f)
        }
    }

    @Test
    fun pickingAnAppReportsIt() {
        val picked = mutableListOf<GestureAction?>()
        render(onPick = { picked += it }) { sheet ->
            sheet.click("App…")
            sheet.click("Alpha")
            assertThat(picked).containsExactly(GestureAction.LaunchApp(app.key))
        }
    }

    @Test
    fun pickingUseDefaultReportsNull() {
        val picked = mutableListOf<GestureAction?>()
        render(onPick = { picked += it }) { sheet ->
            sheet.click("Use default")
            assertThat(picked).containsExactly(null)
        }
    }

    @Test
    fun headerBackStepsFromShortcutsToTheirAppsThenToTheActions() {
        render { sheet ->
            sheet.state.shortcutApp = app
            sheet.state.page = PickerPage.SHORTCUTS
            sheet.nodes()
            assertThat(sheet.headings()).contains("Choose a shortcut")
            sheet.clickBack()
            assertThat(sheet.state.page).isEqualTo(PickerPage.SHORTCUT_APPS)
            assertThat(sheet.headings()).contains("Choose an app")
            sheet.clickBack()
            assertThat(sheet.state.page).isEqualTo(PickerPage.ACTIONS)
            // On the first page there is nothing to step back to: system back dismisses the sheet.
            assertThat(sheet.state.headerBack()).isNull()
            assertThat(sheet.state.back()).isFalse()
        }
    }
}

private fun SemanticsNode.flatten(): List<SemanticsNode> = listOf(this) + children.flatMap { it.flatten() }
