package io.github.aj4abinjacob.chikku.search

import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.AndroidUiFrameClock
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.ViewCompat
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.search.CalculationResult
import io.github.aj4abinjacob.chikku.core.model.search.ContactHit
import io.github.aj4abinjacob.chikku.core.model.search.ContactLookup
import io.github.aj4abinjacob.chikku.core.model.search.MicroResult
import io.github.aj4abinjacob.chikku.core.model.search.SearchResults
import io.github.aj4abinjacob.chikku.core.model.search.SearchRow
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.core.model.settings.SearchResultsAnchor
import io.github.aj4abinjacob.chikku.core.model.settings.SearchAppPriorityDirection
import io.github.aj4abinjacob.chikku.core.model.settings.SearchSettings
import io.github.aj4abinjacob.chikku.core.model.settings.WebSearchEngine
import io.github.aj4abinjacob.chikku.home.FakeIconProvider
import io.github.aj4abinjacob.chikku.home.TestHomeApplication
import io.github.aj4abinjacob.chikku.home.app
import io.github.aj4abinjacob.chikku.settings.previews.SearchPreviewMode
import io.github.aj4abinjacob.chikku.settings.previews.SearchResultsLivePreview
import io.github.aj4abinjacob.chikku.resetComposeMainDispatcher
import java.time.Duration
import kotlinx.coroutines.android.asCoroutineDispatcher
import org.junit.Test
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

/** Real search composition and editor actions over synthetic data; no external launches or stores. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestHomeApplication::class, qualifiers = "w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SearchTopResultTest {
    private val calculator = app("Calculator")
    private val calendar = app("Calendar")
    private val initial = SearchResults("cal", apps = listOf(calculator, calendar), webEngine = WebSearchEngine.DUCKDUCKGO)

    @After
    fun releaseFrames() {
        ShadowLooper.idleMainLooper()
        resetComposeMainDispatcher()
    }

    private fun SemanticsNode.flatten(): List<SemanticsNode> = listOf(this) + children.flatMap { it.flatten() }
    private fun View.flatten(): List<View> = listOf(this) +
        if (this is ViewGroup) (0 until childCount).flatMap { getChildAt(it).flatten() } else emptyList()
    private fun SemanticsNode.text() = config.getOrNull(SemanticsProperties.Text)?.joinToString().orEmpty()

    private inner class Screen(
        value: SearchResults,
        settings: SearchSettings = SearchSettings(onlineSuggestions = false),
        drag: Boolean = false,
        language: LayoutDirection = LayoutDirection.Ltr,
    ) : AutoCloseable {
        val results = mutableStateOf(value)
        val settings = mutableStateOf(settings)
        val opened = mutableListOf<SearchRow>()
        private val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        val content: ViewGroup
        private val root: ViewRootForTest

        init {
            val frameContext = Handler(Looper.getMainLooper()).asCoroutineDispatcher() +
                AndroidUiFrameClock(Choreographer.getInstance())
            val recomposer = activity.window.decorView.createLifecycleAwareWindowRecomposer(frameContext, activity.lifecycle)
            activity.setContent(parent = recomposer) {
                CompositionLocalProvider(LocalLayoutDirection provides language) {
                    SearchTheme(LauncherSettings(search = this.settings.value)) {
                        SearchScreen(value.query, results.value, FakeIconProvider(), {}, { opened += it }, {},
                            settings = this.settings.value, onAppDrag = if (drag) { _, _ -> false } else null)
                    }
                }
            }
            ShadowLooper.idleMainLooper()
            content = activity.findViewById(android.R.id.content)
            root = (content.getChildAt(0) as ViewGroup).getChildAt(0) as ViewRootForTest
        }

        fun nodes(): List<SemanticsNode> {
            Snapshot.sendApplyNotifications()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
            root.measureAndLayoutForTest()
            return root.semanticsOwner.rootSemanticsNode.flatten()
        }

        fun top(): List<SemanticsNode> = nodes().filter { it.config.contains(SemanticsProperties.StateDescription) }
        fun go() { nodes().single { it.config.contains(SemanticsActions.OnImeAction) }.config[SemanticsActions.OnImeAction].action!!.invoke() }
        fun type(text: String) {
            nodes().single { it.config.contains(SemanticsActions.SetText) }
                .config[SemanticsActions.SetText].action!!.invoke(AnnotatedString(text))
            nodes()
        }
        override fun close() {
            controller.pause().stop().destroy()
            ShadowLooper.idleMainLooper()
        }
    }

    @Test
    fun exactlyTheGoTargetIsHighlightedInListAndCompactModesAtEitherAnchor() {
        for (anchor in SearchResultsAnchor.entries) for (maximum in listOf(0, 3)) {
            Screen(initial, SearchSettings(resultsAnchor = anchor, maxAppResults = maximum)).use { screen ->
                assertThat(screen.top().single().text()).contains("Calculator")
                assertThat(screen.top().single().config[SemanticsProperties.StateDescription])
                    .isEqualTo(screen.activity.getString(R.string.search_top_result_description))
                screen.go()
                assertThat(screen.opened).containsExactly(initial.goRow())
            }
        }
    }

    @Test
    fun lateBetterContactTakesTheHighlightAndGoActionFromTheApp() {
        Screen(initial).use { screen ->
            assertThat(screen.top().single().text()).contains("Calculator")
            val person = ContactHit(1, "sample-person", "Cal Friend")
            screen.results.value = initial.copy(contacts = ContactLookup.Found(listOf(person)))
            assertThat(screen.top().single().text()).contains("Cal Friend")
            screen.go()
            assertThat(screen.opened).containsExactly(SearchRow.Person(person))
        }
    }

    @Test
    fun typingClearsTheHighlightAndGoUntilMatchingResultsArrive() {
        Screen(initial).use { screen ->
            assertThat(screen.top()).hasSize(1)
            screen.type("mail")
            assertThat(screen.top()).isEmpty()
            screen.go()
            assertThat(screen.opened).isEmpty()
            val mail = app("Mail")
            screen.results.value = SearchResults("mail", apps = listOf(mail))
            assertThat(screen.top().single().text()).contains("Mail")
            screen.go()
            assertThat(screen.opened).containsExactly(SearchRow.App(mail))
            screen.type("")
            assertThat(screen.top()).isEmpty()
        }
    }

    @Test
    fun microAndWebFallbacksFollowGoWhileHistoryAndCopyOnlyAnswersHaveNoHighlight() {
        val action = MicroResult.Action(MicroResult.Kind.URL, "https://example.com")
        val cases = listOf(
            SearchResults("example.com", microResults = listOf(action), webEngine = WebSearchEngine.GOOGLE) to "Open link",
            SearchResults("missing", suggestedApps = listOf(calendar), webEngine = WebSearchEngine.GOOGLE) to "Search Google",
            SearchResults("", apps = listOf(calendar)) to null,
            SearchResults("1/0", calculation = CalculationResult.Error(CalculationResult.Reason.DIVISION_BY_ZERO)) to null,
        )
        for ((results, expected) in cases) Screen(results).use { screen ->
            if (expected == null) assertThat(screen.top()).isEmpty()
            else {
                assertThat(screen.top().single().text()).contains(expected)
                screen.go()
                assertThat(screen.opened).containsExactly(results.goRow())
            }
        }
    }

    @Test
    fun nativeDragSourcesMoveAndClearTheAccessibleMarkerWhenResultsChange() {
        for (maximum in listOf(0, 3)) Screen(initial, SearchSettings(maxAppResults = maximum), drag = true).use { screen ->
            fun sources(): List<SearchAppResultView> {
                screen.nodes()
                return screen.content.flatten().filterIsInstance<SearchAppResultView>()
            }
            val source = sources().single { ViewCompat.getStateDescription(it) != null }
            assertThat(source.contentDescription.toString()).isEqualTo("Calculator")
            assertThat(source.flatten().filterIsInstance<TextView>().single { it.text.toString() == "Top result" }.visibility).isEqualTo(View.VISIBLE)
            source.performClick()
            assertThat(screen.opened).containsExactly(SearchRow.App(calculator))
            screen.results.value = initial.copy(apps = listOf(calendar, calculator))
            assertThat(sources().single { ViewCompat.getStateDescription(it) != null }.contentDescription.toString()).isEqualTo("Calendar")
            screen.type("no match")
            assertThat(sources().filter { ViewCompat.getStateDescription(it) != null }).isEmpty()
            assertThat(sources().flatMap { it.flatten() }.filterIsInstance<TextView>()
                .filter { it.text.toString() == "Top result" && it.visibility == View.VISIBLE }).isEmpty()
        }
    }

    @Test
    fun priorityDirectionPlacesTheBestAppOnTheChosenSideInEitherLanguageAndDragMode() {
        for (language in LayoutDirection.entries) for (direction in SearchAppPriorityDirection.entries) for (drag in listOf(false, true)) {
            Screen(initial, SearchSettings(maxAppResults = 3, appPriorityDirection = direction), drag, language).use { screen ->
                val nodes = screen.nodes()
                fun left(label: String): Float = if (drag) {
                    val view = screen.content.flatten().filterIsInstance<SearchAppResultView>()
                        .single { it.contentDescription.toString() == label }
                    assertThat(view.layoutDirection).isEqualTo(if (language == LayoutDirection.Ltr) View.LAYOUT_DIRECTION_LTR else View.LAYOUT_DIRECTION_RTL)
                    val position = IntArray(2)
                    view.getLocationOnScreen(position)
                    position[0].toFloat()
                } else nodes.single { it.text() == label }.boundsInRoot.left
                val bestOnLeft = direction.rowLayoutDirection(language) == LayoutDirection.Ltr
                assertThat(left("Calculator") < left("Calendar")).isEqualTo(bestOnLeft)
                screen.go()
                assertThat(screen.opened).containsExactly(SearchRow.App(calculator))
                if (!drag) assertThat(screen.top().single().text()).contains("Calculator")
            }
        }
    }

    @Test
    fun changingDirectionMovesTheHighlightButKeepsRankAndExpandedLists() {
        Screen(initial, SearchSettings(maxAppResults = 3, appPriorityDirection = SearchAppPriorityDirection.LEFT_TO_RIGHT)).use { screen ->
            fun bestLeft() = screen.top().single().boundsInRoot.left
            val before = bestLeft()
            screen.settings.value = screen.settings.value.copy(appPriorityDirection = SearchAppPriorityDirection.RIGHT_TO_LEFT)
            assertThat(bestLeft()).isGreaterThan(before)
            val readingOrder = screen.nodes().filter { it.config.contains(SemanticsProperties.TraversalIndex) }
                .sortedBy { it.config[SemanticsProperties.TraversalIndex] }.map { it.text() }
            assertThat(readingOrder).containsExactly("Calculator", "Calendar").inOrder()
            screen.settings.value = screen.settings.value.copy(maxAppResults = 0)
            val listPosition = screen.top().single().boundsInRoot
            screen.settings.value = screen.settings.value.copy(appPriorityDirection = SearchAppPriorityDirection.LEFT_TO_RIGHT)
            assertThat(screen.top().single().boundsInRoot).isEqualTo(listPosition)
        }
    }

    @Test
    fun emptyQuerySuggestedAppsUseTheSamePhysicalDirection() {
        val history = SearchResults("", apps = listOf(calculator, calendar))
        Screen(history, SearchSettings(maxAppResults = 3, appPriorityDirection = SearchAppPriorityDirection.RIGHT_TO_LEFT)).use { screen ->
            val nodes = screen.nodes()
            val best = nodes.single { it.text() == "Calculator" }.boundsInRoot.left
            val next = nodes.single { it.text() == "Calendar" }.boundsInRoot.left
            assertThat(best).isGreaterThan(next)
            assertThat(screen.top()).isEmpty()
        }
    }

    @Test
    fun resultsPreviewExplainsItsSyntheticTopResultInBothLayouts() {
        for (maximum in listOf(0, 3)) {
            val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
            try {
                val activity = controller.get()
                val settings = mutableStateOf(SearchSettings(maxAppResults = maximum))
                val frameContext = Handler(Looper.getMainLooper()).asCoroutineDispatcher() +
                    AndroidUiFrameClock(Choreographer.getInstance())
                val recomposer = activity.window.decorView.createLifecycleAwareWindowRecomposer(frameContext, activity.lifecycle)
                activity.setContent(parent = recomposer) {
                    SearchResultsLivePreview(LauncherSettings(search = settings.value), SearchPreviewMode.RESULTS)
                }
                ShadowLooper.idleMainLooper()
                val root = (activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as ViewGroup).getChildAt(0) as ViewRootForTest
                fun preview(): SemanticsNode {
                    Snapshot.sendApplyNotifications()
                    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
                    root.measureAndLayoutForTest()
                    return root.semanticsOwner.rootSemanticsNode.flatten()
                        .single { it.config.contains(SemanticsProperties.ContentDescription) }
                }
                val bounds = preview().boundsInRoot
                for (direction in SearchAppPriorityDirection.entries) {
                    settings.value = settings.value.copy(appPriorityDirection = direction)
                    val node = preview()
                    val description = node.config[SemanticsProperties.ContentDescription].joinToString()
                    assertThat(description).contains("Top result: Notes.")
                    if (maximum > 0) assertThat(description).contains(activity.getString(direction.labelRes()))
                    assertThat(node.boundsInRoot).isEqualTo(bounds)
                }
            } finally {
                controller.pause().stop().destroy()
                ShadowLooper.idleMainLooper()
            }
        }
    }
}
