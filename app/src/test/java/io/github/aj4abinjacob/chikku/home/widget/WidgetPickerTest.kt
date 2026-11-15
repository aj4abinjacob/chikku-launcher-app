package io.github.aj4abinjacob.chikku.home.widget

import android.app.Activity
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.ComponentKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "mdpi")
class WidgetPickerTest {
    private val activity = Robolectric.buildActivity(Activity::class.java).setup().get().apply {
        setTheme(R.style.Theme_Chikku_Home)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val root = FrameLayout(activity)
    private val entries = listOf(
        entry("Countdown", 2, 2), entry("Month", 4, 5), entry("Agenda", 4, 2),
    )
    private var picked: WidgetProviderEntry? = null
    private var dragged: WidgetProviderEntry? = null
    private var dragView: View? = null
    private val picker = WidgetPicker(root, scope, Dispatchers.Unconfined, 80, 120,
        onPick = { picked = it },
        onDragOut = { entry, view -> dragged = entry; dragView = view; true },
        onClosed = {},
    )

    @After
    fun tearDown() {
        picker.dismiss()
        scope.cancel()
        activity.finish()
    }

    private fun entry(label: String, x: Int, y: Int) = WidgetProviderEntry(
        ComponentKey("android", "android.$label", 0), label, x, y,
    )

    private fun show() {
        picker.show()
        val infos = entries.associate { entry ->
            entry.key to AppWidgetProviderInfo().apply {
                provider = ComponentName(entry.key.packageName, entry.key.className)
                previewLayout = android.R.layout.simple_list_item_1
            }
        }
        picker.setContent(listOf(WidgetGroup("android", 0, "Calendar", false, entries)), infos)
    }

    private fun layout(width: Int) {
        shadowOf(Looper.getMainLooper()).idle()
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1400, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, width, 1400)
    }

    private fun list(view: View = root): RecyclerView? = if (view is RecyclerView) view else {
        (view as? ViewGroup)?.let { group -> (0 until group.childCount).firstNotNullOfOrNull { list(group.getChildAt(it)) } }
    }

    private fun card(position: Int) = list()!!.findViewHolderForAdapterPosition(position)!!.itemView as ViewGroup

    @Test
    fun portraitPreviewsShareARowAndWidePreviewsKeepTheirWidth() {
        show()
        layout(400)
        val first = card(1)
        val second = card(2)
        val wide = card(3)
        assertThat(first.top).isEqualTo(second.top)
        assertThat(first.right).isAtMost(second.left)
        assertThat(wide.top).isAtLeast(first.bottom)
        assertThat(wide.width).isGreaterThan(first.width)
        for (position in 1..3) {
            val preview = card(position).getChildAt(0) as ViewGroup
            val remote = preview.getChildAt(0)
            assertThat(remote.width * remote.scaleX).isAtMost(preview.width.toFloat() + 1f)
            assertThat(remote.height * remote.scaleY).isAtMost(preview.height.toFloat() + 1f)
        }
    }

    @Test
    fun windowNarrowingReflowsWithoutClippingAndExpandingRestoresTheGrid() {
        show()
        layout(400)
        layout(280)
        assertThat(card(2).top).isAtLeast(card(1).bottom)
        assertThat(card(3).getChildAt(0).width).isAtMost(card(3).width - card(3).paddingLeft - card(3).paddingRight)
        layout(400)
        assertThat(card(1).top).isEqualTo(card(2).top)
    }

    @Test
    @Config(qualifiers = "ldrtl-mdpi")
    fun rtlMirrorsCardsAndLongPressStillUsesTheSelectedPreview() {
        root.layoutDirection = View.LAYOUT_DIRECTION_RTL
        show()
        layout(400)
        assertThat(card(1).left).isAtLeast(card(2).right)
        val second = card(2)
        assertThat(second.performLongClick()).isTrue()
        assertThat(dragged).isEqualTo(entries[1])
        assertThat(dragView).isSameInstanceAs(second.getChildAt(0))
        assertThat(picker.isShowing).isTrue()
        second.performClick()
        assertThat(picked).isEqualTo(entries[1])
        assertThat(picker.isShowing).isFalse()
    }

    @Test
    fun largeFontsUseOneColumnAndKeepTheWholeLabel() {
        activity.resources.updateConfiguration(activity.resources.configuration.apply { fontScale = 1.5f }, activity.resources.displayMetrics)
        show()
        layout(400)
        assertThat(card(2).top).isAtLeast(card(1).bottom)
        assertThat(card(1).contentDescription.toString()).contains("Countdown")
    }
}
