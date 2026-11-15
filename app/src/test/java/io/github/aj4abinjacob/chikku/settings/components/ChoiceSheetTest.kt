package io.github.aj4abinjacob.chikku.settings.components

import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.AndroidUiFrameClock
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.home.TestHomeApplication
import io.github.aj4abinjacob.chikku.settings.ChikkuSettingsTheme
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
 * The settings choice and colour sheets, drawn without the sheet window (Robolectric's semantics
 * root here is the activity content), so these check the sheet contents, not the sheet animation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestHomeApplication::class, qualifiers = "w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChoiceSheetTest {

    private class Rendered(val root: ViewRootForTest) {
        fun nodes(): List<SemanticsNode> {
            repeat(3) {
                Snapshot.sendApplyNotifications()
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
                root.measureAndLayoutForTest()
            }
            return root.semanticsOwner.rootSemanticsNode.flatten()
        }

        fun texts(): List<String> = nodes().flatMap { node ->
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
        }

        /** Taps the clickable node that carries [text] (a row merges its label into itself). */
        fun click(text: String) {
            val node = nodes().first { node ->
                node.config.getOrNull(SemanticsActions.OnClick) != null &&
                    (node.texts().contains(text) || node.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(text) == true)
            }
            node.config[SemanticsActions.OnClick].action!!.invoke()
            nodes()
        }

        private fun SemanticsNode.texts(): List<String> =
            (config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }) + children.flatMap { it.texts() }
    }

    private fun render(theme: @Composable (@Composable () -> Unit) -> Unit = { MaterialTheme(content = it) }, body: @Composable ColumnScope.() -> Unit, check: (Rendered) -> Unit) {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        val frameContext = Handler(Looper.getMainLooper()).asCoroutineDispatcher() +
            AndroidUiFrameClock(Choreographer.getInstance())
        val recomposer = activity.window.decorView.createLifecycleAwareWindowRecomposer(frameContext, activity.lifecycle)
        activity.setContent(parent = recomposer) {
            theme { SettingsSheetBody("Sheet title", body) }
        }
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        val root = (content.getChildAt(0) as ViewGroup).getChildAt(0) as ViewRootForTest
        try {
            check(Rendered(root))
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private val many = (1..40).map { "Option $it" }

    @Test
    fun opensWithTheCurrentChoiceInView() {
        render(body = { ChoiceSheetOptions(many, "Option 30", { it }, onPick = {}) }) { sheet ->
            val shown = sheet.texts()
            assertThat(shown).containsAtLeast("Sheet title", "Option 29", "Option 30").inOrder()
            assertThat(shown).doesNotContain("Option 1")
        }
    }

    @Test
    fun shortListsOpenAtTheTop() {
        render(body = { ChoiceSheetOptions(listOf("A", "B", "C"), "C", { it }, onPick = {}) }) { sheet ->
            assertThat(sheet.texts()).containsAtLeast("Sheet title", "A", "B", "C").inOrder()
        }
    }

    @Test
    fun aListThatFitsWrapsItsRowsWithNoGapBelow() {
        val seven = (1..7).map { "Icon $it" }
        render(body = { ChoiceSheetOptions(seven, "Icon 1", { it }, onPick = {}) }) { sheet ->
            val nodes = sheet.nodes()
            val list = nodes.single { it.config.getOrNull(SemanticsProperties.SelectableGroup) != null }.boundsInRoot
            val rows = nodes.filter { it.config.getOrNull(SemanticsActions.OnClick) != null }.map { it.boundsInRoot }
            assertThat(rows).hasSize(7)
            // Each row has a 2dp margin above and below its tinted shape (4px at xhdpi); nothing else.
            val margin = 4f + 0.5f
            assertThat(rows.first().top - list.top).isAtMost(margin)
            assertThat(list.bottom - rows.last().bottom).isAtMost(margin)
            val rowsHeight = rows.sumOf { it.height.toDouble() }.toFloat() + rows.size * 8f
            assertThat(list.height).isWithin(1f).of(rowsHeight)
        }
    }

    @Test
    fun rowHighlightsSitOnTheSharedSheetInset() {
        render(body = { ChoiceSheetOptions(listOf("A", "B"), "A", { it }, onPick = {}) }) { sheet ->
            val rows = sheet.nodes().filter { it.config.getOrNull(SemanticsActions.OnClick) != null }.map { it.boundsInRoot }
            assertThat(rows).hasSize(2)
            // SheetListPadding (12dp = 24px at xhdpi) from each edge of the 360dp (720px) window,
            // the same as the gesture picker's rows (GestureActionPickerSheetTest).
            rows.forEach { row ->
                assertThat(row.left).isWithin(0.5f).of(24f)
                assertThat(row.right).isWithin(0.5f).of(720f - 24f)
            }
        }
    }

    @Test
    fun sectionsAndSummariesAreShown() {
        val sections = mapOf("a" to "First", "b" to "First", "c" to "Second")
        render(body = {
            ChoiceSheetOptions(
                listOf("a", "b", "c"), "a", { it.uppercase() }, onPick = {},
                optionSummary = { "about $it" },
                optionSection = { sections[it] },
            )
        }) { sheet ->
            assertThat(sheet.texts()).containsAtLeast("First", "A", "about a", "B", "Second", "C", "about c").inOrder()
        }
    }

    @Test
    fun tappingAnOptionPicksIt() {
        val picked = mutableListOf<String>()
        render(body = { ChoiceSheetOptions(listOf("A", "B"), "A", { it }, onPick = { picked += it }) }) { sheet ->
            sheet.click("B")
            assertThat(picked).containsExactly("B")
        }
    }

    @Test
    fun previewVariantShowsEveryOptionAsATile() {
        val picked = mutableListOf<String>()
        render(body = {
            ChoiceSheetOptions(listOf("Grid", "List"), "Grid", { it }, onPick = { picked += it }, optionPreview = {})
        }) { sheet ->
            assertThat(sheet.texts()).containsAtLeast("Grid", "List")
            sheet.click("List")
            assertThat(picked).containsExactly("List")
        }
    }

    @Test
    fun colorSheetOffersDefaultAndSwatches() {
        val picked = mutableListOf<Int?>()
        val palette = listOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt())
        render(body = { ColorSheetOptions(palette, palette[0], allowDefault = true, defaultLabel = "Default") { picked += it } }) { sheet ->
            assertThat(sheet.texts()).contains("Default")
            sheet.click("#FFFFFFFF")
            sheet.click("Default")
            assertThat(picked).containsExactly(0xFFFFFFFF.toInt(), null).inOrder()
        }
    }

    @Test
    fun colorSheetHidesDefaultWhenNotAllowed() {
        render(body = { ColorSheetOptions(listOf(0xFF000000.toInt()), null, allowDefault = false, defaultLabel = "Default") {} }) { sheet ->
            assertThat(sheet.texts()).doesNotContain("Default")
        }
    }

    @Test
    fun settingsThemeGivesSheetsAndDialogsNeutralContainers() {
        val seen = mutableListOf<Color>()
        render(
            theme = { content -> ChikkuSettingsTheme(content = content) },
            body = {
                val colors = MaterialTheme.colorScheme
                SideEffect {
                    seen.clear()
                    seen += listOf(colors.surfaceContainerLow, colors.surfaceContainerHigh, colors.surfaceContainerHighest)
                }
            },
        ) { sheet ->
            sheet.nodes()
            assertThat(seen).hasSize(3)
            // Greys (no dynamic-colour tint), each a distinct step so fills stay visible on the sheet.
            seen.forEach { assertThat(it.red).isEqualTo(it.green); assertThat(it.green).isEqualTo(it.blue) }
            assertThat(seen.distinct()).hasSize(3)
        }
    }

}

private fun SemanticsNode.flatten(): List<SemanticsNode> = listOf(this) + children.flatMap { it.flatten() }
