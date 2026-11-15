package io.github.aj4abinjacob.chikku.home.widget

import android.graphics.RectF
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.DeviceTestSupport
import io.github.aj4abinjacob.chikku.core.model.CellPlacement
import io.github.aj4abinjacob.chikku.core.model.ContainerType.DESKTOP
import io.github.aj4abinjacob.chikku.core.model.GridSpec
import io.github.aj4abinjacob.chikku.home.HomeActivity
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real input dispatch to a test resize overlay; no widget IDs or persisted layout are changed. */
@RunWith(AndroidJUnit4::class)
class WidgetResizeFrameDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val device get() = UiDevice.getInstance(instrumentation)
    private val start = CellPlacement(DESKTOP, 0, 1, 1, 2, 2)

    @Before
    fun setUp() = DeviceTestSupport.resetStartupGuard()

    @After
    fun tearDown() {
        DeviceTestSupport.finishAll(HomeActivity::class.java)
        DeviceTestSupport.resetStartupGuard()
    }

    @Test
    fun allEdgesAndCornersReceiveRealDragInput() = checkHandles(rtl = false)

    @Test
    fun rtlEdgesAndCornersReceiveRealDragInput() = checkHandles(rtl = true)

    private fun checkHandles(rtl: Boolean) {
        ActivityScenario.launch(HomeActivity::class.java).use { scenario ->
            val rootReady = DeviceTestSupport.waitUntil(DeviceTestSupport.LOAD_TIMEOUT_MS) {
                var ready = false
                scenario.onActivity { activity ->
                    val root = activity.findViewById<FrameLayout>(android.R.id.content)
                    ready = root.isLaidOut && root.width > 0 && root.height > 0
                }
                ready
            }
            assertThat(rootReady).isTrue()
            val edges = listOf(
                intArrayOf(-1, 0), intArrayOf(1, 0), intArrayOf(0, -1), intArrayOf(0, 1),
                intArrayOf(-1, -1), intArrayOf(1, -1), intArrayOf(-1, 1), intArrayOf(1, 1),
            )
            for ((dx, dy) in edges) {
                var frame: WidgetResizeFrame? = null
                var committed: CellPlacement? = null
                var cell = 0
                val origin = IntArray(2)
                val visualStart = if (rtl) 2 else 1
                scenario.onActivity { activity ->
                    val root = activity.findViewById<FrameLayout>(android.R.id.content)
                    cell = minOf(root.width, root.height) / 5
                    assertThat(cell).isGreaterThan(0)
                    frame = WidgetResizeFrame(
                        activity,
                        RectF(visualStart * cell.toFloat(), cell.toFloat(), (visualStart + 2) * cell.toFloat(), 3 * cell.toFloat()),
                        cell, cell, SpanRange(1, 5, 1, 5), start, GridSpec(5, 5), rtl,
                        { placement, done -> committed = placement; done(true) }, {},
                    )
                    root.addView(frame, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                }
                val frameReady = DeviceTestSupport.waitUntil(DeviceTestSupport.LOAD_TIMEOUT_MS) {
                    var ready = false
                    scenario.onActivity { activity ->
                        val root = activity.findViewById<FrameLayout>(android.R.id.content)
                        val overlay = frame
                        ready = overlay != null && overlay.parent === root && overlay.isLaidOut && overlay.isShown &&
                            overlay.width == root.width && overlay.height == root.height && overlay.alpha >= 0.99f
                    }
                    ready
                }
                assertThat(frameReady).isTrue()
                instrumentation.waitForIdleSync()
                scenario.onActivity { frame!!.getLocationOnScreen(origin) }
                val x = origin[0] + (visualStart + 1 + dx) * cell
                val y = origin[1] + (2 + dy) * cell
                assertThat(device.swipe(x, y, x + dx * cell, y + dy * cell, 16)).isTrue()
                instrumentation.waitForIdleSync()
                scenario.onActivity {
                    val modelStart = if (rtl) dx > 0 else dx < 0
                    assertThat(committed).isEqualTo(start.copy(
                        x = if (modelStart) 0 else 1,
                        y = if (dy < 0) 0 else 1,
                        spanX = if (dx == 0) 2 else 3,
                        spanY = if (dy == 0) 2 else 3,
                    ))
                    (frame!!.parent as ViewGroup).removeView(frame)
                }
            }
        }
    }
}
