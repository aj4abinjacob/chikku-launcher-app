package io.github.aj4abinjacob.chikku.message

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.home.TestHomeApplication
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

class OwnerActivity : ComponentActivity(), ChikkuMessages.Owner {
    val recorded = RecordingMessages()
    override val chikkuMessages: ChikkuMessages get() = recorded
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestHomeApplication::class)
class ChikkuMessagesTest {

    private val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
    private val activity: ComponentActivity get() = controller.get()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @After
    fun tearDown() {
        scope.cancel()
        controller.pause().stop().destroy()
    }

    private fun bars(): List<ChikkuMessageBarView> {
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        return (0 until content.childCount).map(content::getChildAt).filterIsInstance<ChikkuMessageBarView>()
    }

    @Test
    fun activityWithoutItsOwnSurfaceGetsOneChikkuBarAnnouncedAsALiveRegion() {
        val messages = ChikkuMessages.of(activity)

        messages.show(R.string.home_layout_locked)
        ShadowLooper.idleMainLooper()

        assertThat(activity.shownChikkuMessage()).isEqualTo(activity.getString(R.string.home_layout_locked))
        val text = activity.findViewById<TextView>(R.id.message_bar_text)
        assertThat(text.accessibilityLiveRegion).isEqualTo(View.ACCESSIBILITY_LIVE_REGION_POLITE)
        assertThat(activity.findViewById<Button>(R.id.message_bar_action).visibility).isEqualTo(View.GONE)

        // One at a time: a new message replaces the current one in the same bar.
        messages.show(R.string.home_launch_unavailable)
        ShadowLooper.idleMainLooper()
        assertThat(activity.shownChikkuMessage()).isEqualTo(activity.getString(R.string.home_launch_unavailable))
        assertThat(bars()).hasSize(1)
    }

    @Test
    fun messageHidesItself() {
        ChikkuMessages.of(activity).show(R.string.home_layout_locked)
        ShadowLooper.idleMainLooper()
        assertThat(activity.shownChikkuMessage()).isNotNull()

        ShadowLooper.idleMainLooper(30, TimeUnit.SECONDS)

        assertThat(activity.shownChikkuMessage()).isNull()
    }

    @Test
    fun actionRunsOnceAndClosesTheBar() {
        var clicks = 0
        ChikkuMessages.of(activity).show(R.string.home_launch_unavailable, MessageAction(R.string.message_dismiss) { clicks++ })
        ShadowLooper.idleMainLooper()
        val button = activity.findViewById<Button>(R.id.message_bar_action)
        assertThat(button.visibility).isEqualTo(View.VISIBLE)
        assertThat(button.text.toString()).isEqualTo(activity.getString(R.string.message_dismiss))

        button.performClick()
        button.performClick()

        assertThat(clicks).isEqualTo(1)
        assertThat(activity.shownChikkuMessage()).isNull()
    }

    @Test
    fun accessibilityDismissClosesTheBar() {
        ChikkuMessages.of(activity).show(R.string.home_layout_locked)
        ShadowLooper.idleMainLooper()

        bars().single().performAccessibilityAction(AccessibilityNodeInfo.ACTION_DISMISS, null)

        assertThat(activity.shownChikkuMessage()).isNull()
    }

    @Test
    fun messageSitsAboveAVisibleUndoBar() {
        val content = activity.findViewById<FrameLayout>(android.R.id.content)
        val undo = View(activity)
        content.addView(undo, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 100, Gravity.BOTTOM))
        val bar = ChikkuMessageBarView.attach(content, stackAbove = undo)

        bar.show(R.string.home_layout_locked)
        ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS) // a layout pass
        assertThat(bar.translationY).isLessThan(-99f)

        undo.visibility = View.GONE
        ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS)
        assertThat(bar.translationY).isEqualTo(0f)
    }

    @Test
    fun ownerSurfaceIsUsedInsteadOfAnOverlay() {
        val owner = Robolectric.buildActivity(OwnerActivity::class.java).setup()
        try {
            ChikkuMessages.of(owner.get()).show(R.string.home_layout_locked)
            assertThat(owner.get().recorded.shown).containsExactly(R.string.home_layout_locked)
            assertThat(owner.get().shownChikkuMessage()).isNull()
        } finally {
            owner.pause().stop().destroy()
        }
    }

    @Test
    fun messagesFromAnotherThreadAreShownOnTheMainThread() {
        val messages = ChikkuMessages.of(activity)
        val worker = Thread { messages.show(R.string.home_layout_locked) }
        worker.start()
        worker.join()
        ShadowLooper.idleMainLooper()

        assertThat(activity.shownChikkuMessage()).isEqualTo(activity.getString(R.string.home_layout_locked))
    }

    @Test
    fun contextWithoutAnActivityDropsTheMessage() {
        ChikkuMessages.of(ApplicationProvider.getApplicationContext<Context>()).show(R.string.home_layout_locked)
        ShadowLooper.idleMainLooper()
        assertThat(bars()).isEmpty()
    }

    @Test
    fun settingsSnackbarCarriesTextActionAndLongDuration() {
        val state = SnackbarHostState()
        val messages = SnackbarChikkuMessages(activity, scope, state)
        var clicked = false

        messages.show(R.string.home_launch_unavailable, MessageAction(R.string.message_dismiss) { clicked = true })
        ShadowLooper.idleMainLooper()

        val data = state.currentSnackbarData!!
        assertThat(data.visuals.message).isEqualTo(activity.getString(R.string.home_launch_unavailable))
        assertThat(data.visuals.actionLabel).isEqualTo(activity.getString(R.string.message_dismiss))
        assertThat(data.visuals.duration).isEqualTo(SnackbarDuration.Long)
        data.performAction()
        ShadowLooper.idleMainLooper()
        assertThat(clicked).isTrue()

        messages.show(R.string.home_layout_locked)
        ShadowLooper.idleMainLooper()
        assertThat(state.currentSnackbarData!!.visuals.duration).isEqualTo(SnackbarDuration.Short)
    }
}
