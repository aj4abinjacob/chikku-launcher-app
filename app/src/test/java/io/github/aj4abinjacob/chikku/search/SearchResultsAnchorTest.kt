package io.github.aj4abinjacob.chikku.search

import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.LauncherApp
import io.github.aj4abinjacob.chikku.core.model.ProfileType
import io.github.aj4abinjacob.chikku.core.model.search.SearchResults
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.core.model.settings.SearchPresentation
import io.github.aj4abinjacob.chikku.core.model.settings.SearchResultsAnchor
import io.github.aj4abinjacob.chikku.core.model.settings.SearchSettings
import io.github.aj4abinjacob.chikku.core.model.settings.WebSearchEngine
import io.github.aj4abinjacob.chikku.home.FakeIconProvider
import io.github.aj4abinjacob.chikku.home.TestHomeApplication
import io.github.aj4abinjacob.chikku.resetComposeMainDispatcher
import io.github.aj4abinjacob.chikku.settings.previews.SearchLivePreview
import io.github.aj4abinjacob.chikku.settings.previews.SearchPreviewMode
import java.util.concurrent.TimeUnit
import androidx.test.core.app.ApplicationProvider
import io.github.aj4abinjacob.chikku.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/** "Results start: Near the keyboard" stacks sections up from the keyboard without scrambling reading order. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestHomeApplication::class)
class SearchResultsAnchorTest {

    private val apps = (1..3).map { LauncherApp(AppKey("sample.app$it", "sample.app$it.Main", 0), "Sample $it", ProfileType.PERSONAL) }
    private val results = SearchResults("sample", apps = apps, webEngine = WebSearchEngine.DUCKDUCKGO)

    /** A row's top before and after a slow drag that crosses touch slop over many small moves. */
    private fun slowDrag(anchor: SearchResultsAnchor): Pair<Float, Float> {
        resetComposeMainDispatcher()
        val many = (1..30).map { LauncherApp(AppKey("sample.app$it", "sample.app$it.Main", 0), "Sample $it", ProfileType.PERSONAL) }
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent {
            SearchScreen("sample", SearchResults("sample", apps = many), FakeIconProvider(), {}, {}, {},
                settings = SearchSettings(resultsAnchor = anchor, onlineSuggestions = false))
        }
        ShadowLooper.idleMainLooper()
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        val root = (content.getChildAt(0) as ViewGroup).getChildAt(0) as ViewRootForTest
        val label = root.semanticsOwner.rootSemanticsNode.flatten().mapNotNull { it.text() }.first { it.startsWith("Sample ") }
        // A row scrolled out of the list is no longer composed: count it as moved off the drag's edge.
        val gone = if (anchor == SearchResultsAnchor.KEYBOARD) Float.POSITIVE_INFINITY else Float.NEGATIVE_INFINITY
        fun top() = root.semanticsOwner.rootSemanticsNode.flatten().firstOrNull { it.text() == label }?.boundsInRoot?.top ?: gone
        val before = top()
        val x = content.width / 2f
        // The list rests at its start: under the field (drag up to see more) or on the keyboard (drag down).
        val step = if (anchor == SearchResultsAnchor.KEYBOARD) 3f else -3f
        val startY = content.height * (if (anchor == SearchResultsAnchor.KEYBOARD) 0.3f else 0.6f)
        // One move per 16 ms frame, letting the clock and frames advance as on a device. Without this the
        // drag depended on clock state left by earlier tests in the same JVM.
        fun frame() = ShadowLooper.idleMainLooper(16, TimeUnit.MILLISECONDS)
        val down = SystemClock.uptimeMillis()
        content.dispatchTouchEvent(MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, x, startY, 0))
        var y = startY
        repeat(60) {
            frame()
            y += step
            content.dispatchTouchEvent(MotionEvent.obtain(down, SystemClock.uptimeMillis(), MotionEvent.ACTION_MOVE, x, y, 0))
        }
        frame()
        content.dispatchTouchEvent(MotionEvent.obtain(down, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0))
        ShadowLooper.idleMainLooper(1, TimeUnit.SECONDS)
        return before to top()
    }

    @Test
    fun slowDragScrollsResultsInBothModes() {
        // 180 px in 3 px steps; only a fast flick used to scroll.
        val (topBefore, topAfter) = slowDrag(SearchResultsAnchor.TOP)
        assertThat(topAfter).isLessThan(topBefore - 100f)
        val (keyboardBefore, keyboardAfter) = slowDrag(SearchResultsAnchor.KEYBOARD)
        assertThat(keyboardAfter).isGreaterThan(keyboardBefore + 100f)
    }

    @Test
    fun windowPanelStillKeepsTapsFromTheScrimWhileOutsideTapsClose() {
        var closes = 0
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent {
            SearchScreen("sample", results, FakeIconProvider(), {}, {}, { closes++ },
                settings = SearchSettings(presentation = SearchPresentation.WINDOW, onlineSuggestions = false))
        }
        ShadowLooper.idleMainLooper()
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        fun tap(x: Float, y: Float) {
            val now = SystemClock.uptimeMillis()
            content.dispatchTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0))
            content.dispatchTouchEvent(MotionEvent.obtain(now, now + 50, MotionEvent.ACTION_UP, x, y, 0))
            ShadowLooper.idleMainLooper()
        }
        val density = activity.resources.displayMetrics.density
        // The panel wraps its content: its header row keeps taps from the scrim, while the space below the
        // few results (it no longer fills the screen) and the margin beside the panel are scrim and close.
        tap(content.width / 2f, 40 * density)
        assertThat(closes).isEqualTo(0)
        tap(content.width / 2f, content.height - 40 * density)
        assertThat(closes).isEqualTo(1)
        tap(4 * density, 40 * density)
        assertThat(closes).isEqualTo(2)
    }

    @Test
    fun windowPanelHasNoBackButtonOrScrimBackNodeButFullscreenKeepsIt() {
        fun backNodes(presentation: SearchPresentation) = nodes {
            SearchScreen("sample", results, FakeIconProvider(), {}, {}, {},
                settings = SearchSettings(presentation = presentation, onlineSuggestions = false))
        }.count { node ->
            node.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription)
                ?.contains(ApplicationProvider.getApplicationContext<android.content.Context>().getString(R.string.search_back)) == true
        }
        assertThat(backNodes(SearchPresentation.WINDOW)).isEqualTo(0)
        assertThat(backNodes(SearchPresentation.FULLSCREEN)).isEqualTo(1)
    }

    private fun SemanticsNode.flatten(): List<SemanticsNode> = listOf(this) + children.flatMap { it.flatten() }

    private fun nodes(content: @Composable () -> Unit): List<SemanticsNode> {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent(content = content)
        ShadowLooper.idleMainLooper()
        val root = (activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as ViewGroup).getChildAt(0) as ViewRootForTest
        return root.semanticsOwner.rootSemanticsNode.flatten()
    }

    private fun screen(anchor: SearchResultsAnchor): List<SemanticsNode> {
        val settings = SearchSettings(resultsAnchor = anchor, onlineSuggestions = false)
        return nodes { SearchScreen("sample", results, FakeIconProvider(), {}, {}, {}, settings = settings) }
    }

    private fun SemanticsNode.text() = config.getOrNull(SemanticsProperties.Text)?.joinToString()

    /** Top of each labelled line on screen, by its text. */
    private fun List<SemanticsNode>.tops(): Map<String, Float> =
        mapNotNull { node -> node.text()?.let { it to node.boundsInRoot.top } }.toMap()

    private val webRow = "Search DuckDuckGo"

    @Test
    fun topKeepsFieldOrderDownTheScreen() {
        val tops = screen(SearchResultsAnchor.TOP).tops()
        val web = tops.entries.first { it.key.startsWith(webRow) }.value
        assertThat(tops.getValue("Apps")).isLessThan(tops.getValue("Sample 1"))
        assertThat(tops.getValue("Sample 3")).isLessThan(web)
    }

    @Test
    fun nearKeyboardPutsTheFirstSectionLowestWithItsHeadingStillAbove() {
        val tops = screen(SearchResultsAnchor.KEYBOARD).tops()
        val web = tops.entries.first { it.key.startsWith(webRow) }.value
        // The best-match block rests on the keyboard; the web block stacks above it.
        assertThat(web).isLessThan(tops.getValue("Apps"))
        assertThat(tops.getValue("Apps")).isLessThan(tops.getValue("Sample 1"))
        assertThat(tops.getValue("Sample 1")).isLessThan(tops.getValue("Sample 2"))
        assertThat(tops.getValue("Sample 2")).isLessThan(tops.getValue("Sample 3"))
    }

    @Test
    fun nearKeyboardReadsBestMatchFirstForTalkBack() {
        val order = screen(SearchResultsAnchor.KEYBOARD)
            .mapNotNull { node -> node.config.getOrNull(SemanticsProperties.TraversalIndex)?.let { it to node } }
            .sortedBy { it.first }
            .map { (_, node) -> node.flatten().firstNotNullOf { it.text() } }
        assertThat(order.take(4)).containsExactly("Apps", "Sample 1", "Sample 2", "Sample 3").inOrder()
        assertThat(order.last()).startsWith(webRow)
    }

    @Test
    fun pinnedPreviewFollowsTheSetting() {
        fun description(anchor: SearchResultsAnchor) = nodes {
            SearchLivePreview(LauncherSettings(search = SearchSettings(resultsAnchor = anchor)), SearchPreviewMode.WINDOW)
        }.firstNotNullOf { it.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString() }
        assertThat(description(SearchResultsAnchor.TOP)).contains("Results: Under the search field")
        assertThat(description(SearchResultsAnchor.KEYBOARD)).contains("Results: Near the keyboard")
    }
}
