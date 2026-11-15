package io.github.aj4abinjacob.chikku.settings.sections

import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.AndroidUiFrameClock
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.home.FakeHomeDependencies
import io.github.aj4abinjacob.chikku.home.TestHomeApplication
import io.github.aj4abinjacob.chikku.settings.LocalHomeDependencies
import io.github.aj4abinjacob.chikku.settings.components.AppPickerRow
import io.github.aj4abinjacob.chikku.settings.components.AppPickerStyle
import io.github.aj4abinjacob.chikku.settings.components.SettingsSheetBody
import io.github.aj4abinjacob.chikku.settings.components.SheetAppListInset
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
 * The searchable list sheet of the gesture settings ("Home screen and dock icons"), drawn without
 * the sheet window like ChoiceSheetTest, with synthetic rows: a short list hugs its rows, and
 * filtering keeps the unfiltered height so the sheet does not jump. The keyboard is not shown here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestHomeApplication::class, qualifiers = "w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ListSheetTest {

    private val names = listOf("Alpha", "Bravo", "Charlie", "Delta")
    private var query by mutableStateOf("")

    private class Rendered(val root: ViewRootForTest, private val height: () -> Int) {
        val sheetHeight: Int get() = height()

        fun nodes(): List<SemanticsNode> {
            repeat(3) {
                Snapshot.sendApplyNotifications()
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
                root.measureAndLayoutForTest()
            }
            return root.semanticsOwner.rootSemanticsNode.flatten()
        }

        fun rows(names: List<String>): List<Rect> = nodes().filter { node ->
            node.config.getOrNull(SemanticsActions.OnClick) != null && node.allTexts().any { it in names }
        }.map { it.boundsInRoot }

        fun searchField(): Rect = nodes().first { it.config.getOrNull(SemanticsActions.SetText) != null }.boundsInRoot

        private fun SemanticsNode.allTexts(): List<String> =
            config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } + children.flatMap { it.allTexts() }
    }

    private fun render(check: (Rendered) -> Unit) {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        val frameContext = Handler(Looper.getMainLooper()).asCoroutineDispatcher() +
            AndroidUiFrameClock(Choreographer.getInstance())
        val recomposer = activity.window.decorView.createLifecycleAwareWindowRecomposer(frameContext, activity.lifecycle)
        var sheetHeight = 0
        activity.setContent(parent = recomposer) {
            CompositionLocalProvider(LocalHomeDependencies provides FakeHomeDependencies()) {
                MaterialTheme {
                    // Free to wrap its height, as the sheet lets it.
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.onSizeChanged { sheetHeight = it.height }) {
                            SettingsSheetBody("Home screen and dock icons") {
                                ListSheetContent(emptyText = null, query = query, onQueryChange = { query = it }) { cache ->
                                    val shown = names.filter { it.contains(query.trim(), ignoreCase = true) }
                                    items(shown, key = { it }) { name ->
                                        AppPickerRow(
                                            AppKey("sample.${name.lowercase()}", "Main", 0), name, null, false,
                                            AppPickerStyle.NAVIGATION, cache, SheetAppListInset,
                                        ) {}
                                    }
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
            val rendered = Rendered(root) { sheetHeight }
            rendered.nodes()
            check(rendered)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun aShortListHugsItsRowsWithNoGapBelow() {
        render { sheet ->
            val rows = sheet.rows(names)
            assertThat(rows).hasSize(4)
            // Below the last row: the sheet body's 16dp bottom padding (32px at xhdpi), nothing else.
            assertThat(sheet.sheetHeight - rows.last().bottom).isWithin(1f).of(32f)
            // Far below the sheet's cap (80% of the 800dp window = 1280px).
            assertThat(sheet.sheetHeight).isLessThan(1000)
        }
    }

    @Test
    fun filteringKeepsTheUnfilteredHeightAndClearingHugsAgain() {
        render { sheet ->
            val unfiltered = sheet.sheetHeight
            val field = sheet.searchField()

            query = "alp"
            assertThat(sheet.rows(names)).hasSize(1)
            assertThat(sheet.sheetHeight).isEqualTo(unfiltered)
            assertThat(sheet.searchField()).isEqualTo(field)

            query = ""
            val rows = sheet.rows(names)
            assertThat(rows).hasSize(4)
            assertThat(sheet.sheetHeight).isEqualTo(unfiltered)
            assertThat(sheet.sheetHeight - rows.last().bottom).isWithin(1f).of(32f)
        }
    }

    @Test
    fun rowsAndSearchSitOnTheSharedSheetInsets() {
        render { sheet ->
            // Search field edges on SheetListPadding (12dp = 24px at xhdpi), like row highlights elsewhere.
            assertThat(sheet.searchField().left).isWithin(0.5f).of(24f)
            assertThat(sheet.rows(names).first().left).isWithin(0.5f).of(24f)
        }
    }
}

private fun SemanticsNode.flatten(): List<SemanticsNode> = listOf(this) + children.flatMap { it.flatten() }
