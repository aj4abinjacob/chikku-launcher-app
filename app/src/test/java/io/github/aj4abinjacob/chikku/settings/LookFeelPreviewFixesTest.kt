package io.github.aj4abinjacob.chikku.settings

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.GestureAction
import io.github.aj4abinjacob.chikku.core.model.settings.AppMenuStyle
import io.github.aj4abinjacob.chikku.core.model.settings.GestureSettings
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.core.model.settings.LookFeelSettings
import io.github.aj4abinjacob.chikku.home.TestHomeApplication
import io.github.aj4abinjacob.chikku.settings.previews.AppMenuPreviewFrame
import io.github.aj4abinjacob.chikku.settings.previews.GesturePracticePreview
import io.github.aj4abinjacob.chikku.settings.previews.previewMenuActions
import io.github.aj4abinjacob.chikku.settings.previews.previewMenuFocusIndex
import io.github.aj4abinjacob.chikku.settings.sections.HomeGesture
import io.github.aj4abinjacob.chikku.settings.sections.menuPreviewFocusAfterEdit
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import kotlin.math.roundToInt

/**
 * Look & feel → Menus draws the launcher's menu near real size (it used to fit a detailed menu
 * into ~84dp, about 0.2x, where corner radius and labels were invisible), and Gestures shows the
 * home button off the default page when "only on default page" is toggled.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestHomeApplication::class)
class LookFeelPreviewFixesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val density = context.resources.displayMetrics.density
    private fun dp(value: Int) = (value * density).roundToInt()

    /** The page's frame: full width of a ~411dp phone, 132dp tall. */
    private fun render(look: LookFeelSettings, focus: Int? = null, fromDrawer: Boolean = false): AppMenuPreviewFrame {
        val frame = AppMenuPreviewFrame(context)
        frame.show(LauncherSettings(lookFeel = look), previewMenuActions(look, fromDrawer), focus)
        frame.measure(View.MeasureSpec.makeMeasureSpec(dp(411), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(dp(132), View.MeasureSpec.EXACTLY))
        frame.layout(0, 0, frame.measuredWidth, frame.measuredHeight)
        return frame
    }

    // Four actions, the most a menu shows: Edit, Swipe actions, App info and Uninstall.
    private val allActions = LookFeelSettings(appMenuStyle = AppMenuStyle.DETAILED, menuShowSwipeHome = true,
        menuShowRemoveHome = false, menuShowUninstallHome = true)

    private fun AppMenuPreviewFrame.visibleCenter(view: View): Float {
        var y = view.top + view.height / 2f
        var parent = view.parent as View
        val root = getChildAt(0)
        while (parent !== root) { y += parent.top; parent = parent.parent as View }
        return root.translationY + y * root.scaleY
    }

    @Test
    fun detailedMenuIsNeverShrunkBelowLegibleScale() {
        val frame = render(allActions)
        assertThat(frame.menuScale).isAtLeast(AppMenuPreviewFrame.MIN_SCALE)
        assertThat(frame.actionViews).hasSize(4)
        assertThat(frame.measuredHeight).isEqualTo(dp(132))
    }

    @Test
    fun previewShowsFourChipsInOneRowAndNeverMoreThanFour() {
        val four = render(allActions.copy(appMenuStyle = AppMenuStyle.POPUP))
        assertThat(four.actionViews.none { it.tag == "action-bar" }).isTrue()
        // Chips are inert in the preview too, and the menu still fits the frame at a legible scale.
        assertThat(four.actionViews.none { it.isClickable || it.isFocusable }).isTrue()
        assertThat(four.menuScale).isAtLeast(AppMenuPreviewFrame.MIN_SCALE)
        assertThat(four.actionViews.map { it.top }.distinct()).hasSize(1)

        // Settings from an older backup can have every switch on; the preview shows what the launcher shows.
        val everything = allActions.copy(appMenuStyle = AppMenuStyle.POPUP, menuShowHideHome = true, menuShowStoreHome = true,
            menuShowRemoveHome = true)
        assertThat(render(everything).actionViews).hasSize(4)
    }

    @Test
    fun popupMenuShowsTheLauncherLabelledButtonsInEvenRows() {
        val frame = render(allActions.copy(appMenuStyle = AppMenuStyle.POPUP))
        // Same grid as the launcher: four labelled buttons in one row.
        assertThat(frame.actionViews.map { it.top }.distinct()).hasSize(1)
        assertThat(frame.actionViews).hasSize(4)
        assertThat(frame.actionViews.map { it.contentDescription?.toString() })
            .contains(context.getString(R.string.app_menu_app_info))
        // Inert: nothing in the preview takes focus or clicks.
        assertThat(frame.actionViews.none { it.isClickable || it.isFocusable }).isTrue()
    }

    @Test
    fun cardFollowsCornerRadiusAndColors() {
        listOf(0f, 20f, 48f).forEach { corner ->
            val frame = render(allActions.copy(menuCornerRadiusDp = corner, menuBackgroundColor = Color.RED))
            val background = frame.actionCard!!.background as GradientDrawable
            assertThat(background.cornerRadius).isWithin(.5f).of(corner * density)
            assertThat(background.color!!.defaultColor).isEqualTo(Color.RED)
        }
    }

    @Test
    fun toggledActionStaysInViewAndStyleOrCornerEditsReturnToTheTop() {
        val focus = menuPreviewFocusAfterEdit(allActions.copy(menuShowSwipeHome = false), allActions, null)
        assertThat(focus).isEqualTo(R.string.app_menu_swipe_actions)
        val frame = render(allActions, focus)
        val store = frame.actionViews[previewMenuFocusIndex(previewMenuActions(allActions, false), focus)!!]
        assertThat(frame.focusView).isSameInstanceAs(store)
        assertThat(frame.visibleCenter(store)).isIn(com.google.common.collect.Range.closed(0f, dp(132).toFloat()))
        assertThat(frame.visibleCenter(frame.actionViews.first())).isLessThan(0f)

        assertThat(menuPreviewFocusAfterEdit(allActions, allActions.copy(menuCornerRadiusDp = 4f), focus)).isNull()
        assertThat(menuPreviewFocusAfterEdit(allActions, allActions.copy(menuTextColor = Color.BLACK), focus)).isEqualTo(focus)
        val top = render(allActions, null)
        assertThat(top.visibleCenter(top.actionViews.first())).isIn(com.google.common.collect.Range.closed(0f, dp(132).toFloat()))
    }

    @Test
    fun removedActionKeepsItsSlotInView() {
        val withoutSwipe = allActions.copy(menuShowSwipeHome = false)
        val actions = previewMenuActions(withoutSwipe, false)
        // Swipe actions sat between Edit and App info; App info now holds its place.
        assertThat(actions[previewMenuFocusIndex(actions, R.string.app_menu_swipe_actions)!!].title).isEqualTo(R.string.app_menu_app_info)
        val drawerOnly = previewMenuActions(withoutSwipe.copy(menuShowUninstallDrawer = false, menuShowStoreDrawer = false), true)
        assertThat(previewMenuFocusIndex(drawerOnly, R.string.app_menu_uninstall)).isEqualTo(drawerOnly.lastIndex)
    }

    private fun SemanticsNode.flatten(): List<SemanticsNode> = listOf(this) + children.flatMap { it.flatten() }

    private fun practiceTexts(settings: LauncherSettings, onDefaultPage: Boolean): List<String> {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent { GesturePracticePreview(settings, HomeGesture.HOME_BUTTON, {}, onDefaultPage = onDefaultPage) }
        ShadowLooper.idleMainLooper()
        val root = (activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as ViewGroup).getChildAt(0) as ViewRootForTest
        return root.semanticsOwner.rootSemanticsNode.flatten().flatMap { node ->
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
        }
    }

    @Test
    fun homeButtonPreviewFollowsOnlyOnDefaultPageAwayFromTheDefaultPage() {
        val settings = LauncherSettings(gestures = GestureSettings(homeGesture = GestureAction.Recents, homeButtonOnlyOnDefaultPage = true))
        val home = context.getString(R.string.gestures_home_button)
        assertThat(practiceTexts(settings, onDefaultPage = false))
            .contains(context.getString(R.string.preview_gesture_would_run, home, context.getString(R.string.gesture_action_default_page)))
        assertThat(practiceTexts(settings.copy(gestures = settings.gestures.copy(homeButtonOnlyOnDefaultPage = false)), onDefaultPage = false))
            .contains(context.getString(R.string.preview_gesture_would_run, home, context.getString(R.string.gesture_action_recents)))
    }
}
