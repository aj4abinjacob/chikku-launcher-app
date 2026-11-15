package io.github.aj4abinjacob.chikku.home

import android.app.KeyguardManager
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.DeviceTestSupport
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.CellPlacement
import io.github.aj4abinjacob.chikku.core.model.ComponentKey
import io.github.aj4abinjacob.chikku.core.model.GridSpec
import io.github.aj4abinjacob.chikku.settings.SettingsActivity
import org.junit.Assume.assumeFalse
import org.junit.Test
import org.junit.runner.RunWith

/** Real local native drag with synthetic sources. No platform widget binding or saved-layout edits. */
@RunWith(AndroidJUnit4::class)
class HiddenDockNativeAuditDeviceTest {
    @Test fun newAppAndWidgetWithGoneDockFinishOnDropAndCancellationAndAllowImmediateSecondDrag() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = DeviceTestSupport.targetContext
        UiDevice.getInstance(instrumentation).wakeUp()
        assumeFalse(
            "Unlock the test phone for visible UI",
            context.getSystemService(KeyguardManager::class.java).isKeyguardLocked,
        )
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            lateinit var root: FrameLayout
            lateinit var pager: WorkspacePager
            lateinit var controller: WorkspaceController
            var ended = 0
            var drops = 0
            scenario.onActivity { activity ->
                root = FrameLayout(activity)
                pager = WorkspacePager(activity)
                val dock = CellLayout(activity).apply { visibility = View.GONE }
                root.addView(pager, FrameLayout.LayoutParams(-1, -1))
                root.addView(dock)
                activity.setContentView(root)
                val actions = object : WorkspaceActions {
                    override fun onItemClick(item: HomeItemUi, view: View) = Unit
                    override fun onDrop(payload: DragPayload, target: CellPlacement) {
                        drops++
                    }
                    override fun onRemoveItem(itemId: String) = Unit
                    override fun onAppInfo(key: AppKey, view: View) = Unit
                }
                controller = WorkspaceController(
                    pager,
                    dock,
                    PageIndicatorView(activity),
                    View(activity),
                    IconBinder(activity, activity.lifecycleScope, context.homeDependencies().iconProvider),
                    actions,
                )
                controller.render(WorkspaceUi.Ready(GridSpec(5, 5), GridSpec(5, 1), listOf(emptyList()), emptyList()))
            }
            instrumentation.waitForIdleSync()
            val payloads = listOf(
                DragPayload.NewApp(AppKey("synthetic", "synthetic.Main", 0)),
                DragPayload.NewWidget(ComponentKey("synthetic", "synthetic.Widget", 0), 1, 1),
            )
            for (payload in payloads) {
                for (cancel in listOf(false, true)) {
                    lateinit var source: TextView
                    var start = floatArrayOf()
                    var target = floatArrayOf()
                    var started = false
                    var baseline = 0
                    scenario.onActivity { activity ->
                        baseline = ended
                        source = TextView(activity).apply { text = "Synthetic drag source" }
                        root.addView(
                            source,
                            FrameLayout.LayoutParams(
                                180,
                                100,
                                android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL,
                            ).apply {
                                bottomMargin = (120 * activity.resources.displayMetrics.density).toInt()
                            },
                        )
                        source.setOnLongClickListener {
                            started = controller.startDrag(source, payload)
                            true
                        }
                        controller.onDragEnded = {
                            ended++
                            root.removeView(source)
                        }
                    }
                    instrumentation.waitForIdleSync()
                    scenario.onActivity {
                        val origin = IntArray(2).also(source::getLocationOnScreen)
                        val destination = IntArray(2).also(pager::getLocationOnScreen)
                        start = floatArrayOf(origin[0] + source.width / 2f, origin[1] + source.height / 2f)
                        target = floatArrayOf(destination[0] + pager.width / 2f, destination[1] + pager.height / 2f)
                    }
                    val down = SystemClock.uptimeMillis()
                    fun pointer(action: Int, point: FloatArray) {
                        val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, point[0], point[1], 0)
                        event.source = InputDevice.SOURCE_TOUCHSCREEN
                        try {
                            assertThat(instrumentation.uiAutomation.injectInputEvent(event, true)).isTrue()
                        } finally {
                            event.recycle()
                        }
                    }
                    pointer(MotionEvent.ACTION_DOWN, start)
                    try {
                        SystemClock.sleep(700)
                        scenario.onActivity { assertThat(started).isTrue() }
                        pointer(MotionEvent.ACTION_MOVE, target)
                        SystemClock.sleep(100)
                        pointer(if (cancel) MotionEvent.ACTION_CANCEL else MotionEvent.ACTION_UP, target)
                        assertThat(
                            DeviceTestSupport.waitUntil {
                                var done = false
                                scenario.onActivity { done = ended == baseline + 1 && source.parent == null }
                                done
                            },
                        ).isTrue()
                        scenario.onActivity {
                            controller.cancelDrag()
                            assertThat(ended).isEqualTo(baseline + 1)
                        }
                    } finally {
                        pointer(MotionEvent.ACTION_CANCEL, target)
                        scenario.onActivity {
                            controller.cancelDrag()
                            root.removeView(source)
                        }
                    }
                }
            }
            scenario.onActivity {
                assertThat(ended).isEqualTo(4)
                assertThat(drops).isEqualTo(2)
            }
        }
    }
}
