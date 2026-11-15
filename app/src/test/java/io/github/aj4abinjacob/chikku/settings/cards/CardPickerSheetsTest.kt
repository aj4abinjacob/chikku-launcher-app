package io.github.aj4abinjacob.chikku.settings.cards

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.AndroidUiFrameClock
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.ComponentKey
import io.github.aj4abinjacob.chikku.home.TestHomeApplication
import io.github.aj4abinjacob.chikku.home.cards.widget.CardWidgetProviderOption
import io.github.aj4abinjacob.chikku.settings.cards.widget.WidgetProviderList
import io.github.aj4abinjacob.chikku.settings.components.SettingsSheetBody
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
 * Contents of the card pickers that moved from dialogs to settings bottom sheets ("Add card",
 * widget provider, featured widget), drawn in [SettingsSheetBody] without the sheet window, so these
 * check the contents, not the sheet animation or its window stacking. Synthetic data only.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestHomeApplication::class, qualifiers = "w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CardPickerSheetsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

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

        fun click(text: String) {
            val node = nodes().first { node ->
                node.config.getOrNull(SemanticsActions.OnClick) != null && node.allTexts().contains(text)
            }
            node.config[SemanticsActions.OnClick].action!!.invoke()
            nodes()
        }

        fun type(text: String) {
            val field = nodes().first { it.config.getOrNull(SemanticsActions.SetText) != null }
            field.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString(text))
            nodes()
        }

        private fun SemanticsNode.allTexts(): List<String> =
            config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } + children.flatMap { it.allTexts() }
    }

    private fun render(title: String = "Sheet title", body: @androidx.compose.runtime.Composable ColumnScope.() -> Unit, check: (Rendered) -> Unit) {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        val frameContext = Handler(Looper.getMainLooper()).asCoroutineDispatcher() +
            AndroidUiFrameClock(Choreographer.getInstance())
        val recomposer = activity.window.decorView.createLifecycleAwareWindowRecomposer(frameContext, activity.lifecycle)
        activity.setContent(parent = recomposer) { MaterialTheme { SettingsSheetBody(title, body) } }
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        val root = (content.getChildAt(0) as ViewGroup).getChildAt(0) as ViewRootForTest
        try {
            check(Rendered(root))
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private fun titleIsAHeading(sheet: Rendered, title: String) {
        val heading = sheet.nodes().single { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == title } }
        assertThat(heading.config.getOrNull(SemanticsProperties.Heading)).isNotNull()
    }

    @Test
    fun addCardSheetListsEveryTemplateAndPicksOne() {
        val picked = mutableListOf<CardTemplate>()
        render(title = "Add card", body = { CardTemplateList { picked += it } }) { sheet ->
            titleIsAHeading(sheet, "Add card")
            val labels = CardTemplate.entries.map { context.getString(it.label) }
            assertThat(sheet.texts()).containsAtLeastElementsIn(labels).inOrder()
            sheet.click(context.getString(CardTemplate.CHARGING.label))
            assertThat(picked).containsExactly(CardTemplate.CHARGING)
        }
    }

    private fun widget(app: String, name: String, appLabel: String) =
        CardWidgetProviderOption(ComponentKey("synthetic.$app", "synthetic.$app.$name", 0L), "$appLabel $name", appLabel)

    private val providers = listOf(
        widget("alpha", "Clock", "Alpha"), widget("alpha", "Notes", "Alpha"), widget("beta", "Weather", "Beta"),
    )

    @Test
    fun widgetProviderSheetGroupsByAppPinsTheCurrentOneAndPicks() {
        val picked = mutableListOf<CardWidgetProviderOption>()
        render(body = { WidgetProviderList(providers, providers[2].key) { picked += it } }) { sheet ->
            val texts = sheet.texts()
            assertThat(texts).containsAtLeast(
                context.getString(R.string.gesture_picker_current), "Beta Weather", "Alpha", "Alpha Clock", "Alpha Notes", "Beta",
            ).inOrder()
            sheet.click("Alpha Notes")
            assertThat(picked).containsExactly(providers[1])
        }
    }

    @Test
    fun widgetProviderSearchFiltersAndKeepsTheFieldAboveTheList() {
        val many = (1..60).map { widget("app$it", "Widget", "App $it") }
        render(body = { WidgetProviderList(many, null) {} }) { sheet ->
            sheet.type("App 42")
            val texts = sheet.texts()
            assertThat(texts).contains("App 42 Widget")
            assertThat(texts).doesNotContain("App 1 Widget")
            sheet.type("no such widget")
            assertThat(sheet.texts()).contains(context.getString(R.string.card_widget_no_matches))
            // The field is laid out within the sheet, above the list.
            val field = sheet.nodes().first { it.config.getOrNull(SemanticsActions.SetText) != null }.boundsInRoot
            assertThat(field.top).isGreaterThan(0f)
        }
    }

    @Test
    fun widgetProviderSheetExplainsAnEmptyCatalog() {
        render(body = { WidgetProviderList(emptyList(), null) {} }) { sheet ->
            assertThat(sheet.texts()).contains(context.getString(R.string.card_widget_catalog_empty))
            assertThat(sheet.nodes().none { it.config.getOrNull(SemanticsActions.SetText) != null }).isTrue()
        }
    }

    private val homeWidgets = listOf(CardWidgetOption("w1", "Synthetic clock"), CardWidgetOption("w2", "Synthetic notes"))

    @Test
    fun featuredWidgetSheetMarksTheChoiceAndPicks() {
        val picked = mutableListOf<CardWidgetOption>()
        render(body = { FeaturedWidgetList(homeWidgets, "w2") { picked += it } }) { sheet ->
            assertThat(sheet.texts()).containsAtLeast(
                context.getString(R.string.cards_widget_reference_summary), "Synthetic clock", "Synthetic notes",
            ).inOrder()
            val selected = sheet.nodes().filter { it.config.getOrNull(SemanticsProperties.Selected) == true }
            assertThat(selected).hasSize(1)
            sheet.click("Synthetic clock")
            assertThat(picked).containsExactly(homeWidgets[0])
        }
    }

    @Test
    fun featuredWidgetSheetExplainsWhenNoWidgetIsOnHome() {
        render(body = { FeaturedWidgetList(emptyList(), null) {} }) { sheet ->
            assertThat(sheet.texts()).contains(context.getString(R.string.cards_no_widgets))
        }
    }
}

private fun SemanticsNode.flatten(): List<SemanticsNode> = listOf(this) + children.flatMap { it.flatten() }
