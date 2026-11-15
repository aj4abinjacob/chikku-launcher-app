package io.github.aj4abinjacob.chikku.settings

import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.settings.PaddingSize
import io.github.aj4abinjacob.chikku.home.TestHomeApplication
import io.github.aj4abinjacob.chikku.settings.previews.HomeGridPreview
import io.github.aj4abinjacob.chikku.settings.previews.HomeWidgetPreview
import io.github.aj4abinjacob.chikku.settings.previews.homeGridGeometry
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/** Home › Grid and › Widgets previews follow every edge-space, dock and widget setting they show. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestHomeApplication::class)
class HomeLayoutPreviewFixesTest {

    private val screen = Rect(0f, 0f, 90f, 160f)

    @Test
    fun eachEdgeSpaceStepInsetsTheGridFurther() {
        val insets = PaddingSize.entries.map { homeGridGeometry(screen, it, it, null) }
            .map { it.grid.left - it.page.left to it.grid.top - it.page.top }
        assertThat(insets.first()).isEqualTo(0f to 0f)
        insets.zipWithNext().forEach { (smaller, larger) ->
            assertThat(larger.first).isGreaterThan(smaller.first)
            assertThat(larger.second).isGreaterThan(smaller.second)
        }
    }

    @Test
    fun dockIconsFollowTheHomeSideSpaceUnlessTheDockHasItsOwn() {
        val following = homeGridGeometry(screen, PaddingSize.LARGE, PaddingSize.NONE, null)
        val none = homeGridGeometry(screen, PaddingSize.NONE, PaddingSize.NONE, null)
        assertThat(following.dockSide).isEqualTo(following.grid.left - following.page.left)
        assertThat(following.dockIconCenters.first().x).isGreaterThan(none.dockIconCenters.first().x)
        assertThat(following.dockIconCenters.last().x).isLessThan(none.dockIconCenters.last().x)

        val own = homeGridGeometry(screen, PaddingSize.LARGE, PaddingSize.NONE, PaddingSize.NONE)
        assertThat(own.dockSide).isEqualTo(0f)
        assertThat(own.dockIconCenters).isEqualTo(none.dockIconCenters)
    }

    @Test
    fun edgeSpaceNeverMovesThePageOrDockArea() {
        val small = homeGridGeometry(screen, PaddingSize.NONE, PaddingSize.NONE, null)
        val large = homeGridGeometry(screen, PaddingSize.LARGE, PaddingSize.LARGE, PaddingSize.LARGE)
        assertThat(large.page).isEqualTo(small.page)
        assertThat(large.dockBar).isEqualTo(small.dockBar)
    }

    @Test
    fun gridDescriptionNamesEdgeSpaceDockAndShadow() {
        val description = descriptions {
            HomeGridPreview(5, 6, PaddingSize.LARGE, PaddingSize.SMALL, showEdgeShadow = true, dockHorizontalPadding = null)
        }.single()
        assertThat(description).contains("5 × 6")
        assertThat(description).contains("Side space Large (16 dp)")
        assertThat(description).contains("top and bottom space Small (4 dp)")
        assertThat(description).contains("dock side space Large (16 dp)")
        assertThat(description).contains("Edge shadow On")

        val own = descriptions {
            HomeGridPreview(5, 6, PaddingSize.LARGE, PaddingSize.SMALL, dockHorizontalPadding = PaddingSize.NONE)
        }.single()
        assertThat(own).contains("dock side space None (0 dp)")
        assertThat(own).contains("Edge shadow Off")
    }

    @Test
    fun widgetDescriptionFollowsCornersAndInnerSpace() {
        val own = descriptions { HomeWidgetPreview(0f, PaddingSize.MEDIUM) }.single()
        assertThat(own).contains("Overlap is off")
        assertThat(own).contains("Inner space: Medium (8 dp)")
        val rounded = descriptions { HomeWidgetPreview(24f, PaddingSize.NONE, allowOverlap = true) }.single()
        assertThat(rounded).contains("Corners: 24.0 dp")
        assertThat(rounded).contains("Inner space: None (0 dp)")
        assertThat(rounded).isNotEqualTo(own)
    }

    private fun SemanticsNode.flatten(): List<SemanticsNode> = listOf(this) + children.flatMap { it.flatten() }

    private fun descriptions(content: @Composable () -> Unit): List<String> {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent { content() }
        ShadowLooper.idleMainLooper()
        val root = (activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as ViewGroup).getChildAt(0) as ViewRootForTest
        return root.semanticsOwner.rootSemanticsNode.flatten()
            .mapNotNull { it.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString() }
    }
}
