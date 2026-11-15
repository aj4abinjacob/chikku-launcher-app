package io.github.aj4abinjacob.chikku.search

import android.app.KeyguardManager
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.aj4abinjacob.chikku.DeviceTestSupport
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.ContainerType
import io.github.aj4abinjacob.chikku.core.model.EditResult
import io.github.aj4abinjacob.chikku.core.model.ItemTarget
import io.github.aj4abinjacob.chikku.core.model.settings.SearchPresentation
import io.github.aj4abinjacob.chikku.home.HomeActivity
import io.github.aj4abinjacob.chikku.home.WorkspacePager
import io.github.aj4abinjacob.chikku.home.homeDependencies
import io.github.aj4abinjacob.chikku.settings.SettingsActivity
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Actual pointer stream → local framework drag → persisted placement. Restores the debug layout. */
@RunWith(AndroidJUnit4::class)
class SearchDragDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val device get() = UiDevice.getInstance(instrumentation)
    private var activeDownTime: Long? = null
    private var lastX = 0f
    private var lastY = 0f

    @Before
    fun requireUnlockedScreen() {
        device.wakeUp()
        assumeFalse("Unlock the test device to exercise visible UI", DeviceTestSupport.targetContext
            .getSystemService(KeyguardManager::class.java).isKeyguardLocked)
        DeviceTestSupport.resetStartupGuard()
    }

    @Test
    fun searchResultContinuousDragPersistsAndCancellationAndLockPreserveLayout() = runBlocking {
        val context = DeviceTestSupport.targetContext
        val deps = context.homeDependencies()
        val originalSettings = deps.settingsRepository.settings.first()
        val originalLayout = checkNotNull(deps.workspaceRepository.exportContent())
        // Other isolated Chikku installations have the same public label. Give only this
        // component a synthetic alias, so the UI pointer cannot pick another installation.
        val label = "Chikku drag fixture"
        val key = DeviceTestSupport.settingsKey()
        val originalCustomization = deps.appCustomizationRepository.get(key)
        try {
            deps.appCustomizationRepository.update(key) { it.copy(hidden = false, customLabel = label) }
            deps.settingsRepository.update { it.copy(
                home = it.home.copy(layoutLocked = false),
                search = it.search.copy(maxAppResults = 0, presentation = SearchPresentation.FULLSCREEN, onlineSuggestions = false),
                onboarding = it.onboarding.copy(completed = true),
            ) }
            ActivityScenario.launch(HomeActivity::class.java).use { scenario ->
                try {
                assertThat(DeviceTestSupport.waitUntil(DeviceTestSupport.LOAD_TIMEOUT_MS) {
                    scenario.read { it.findViewById<View>(R.id.dock).isShown }
                }).isTrue()
                val page = deps.workspaceRepository.addPage()
                assertThat(DeviceTestSupport.waitUntil {
                    scenario.read { it.findViewById<WorkspacePager>(R.id.workspace_pager).pageCount > page }
                }).isTrue()
                scenario.onActivity { it.findViewById<WorkspacePager>(R.id.workspace_pager).snapToPage(page, animate = false) }
                val beforeDrop = checkNotNull(deps.workspaceRepository.exportContent())
                scenario.onActivity { assertThat(it.showUniversalSearch(label)).isTrue() }
                val row = device.wait(Until.findObject(By.desc(label)), DeviceTestSupport.UI_TIMEOUT_MS)
                assertThat(row).isNotNull()
                assertWithMessage("The fixture must identify exactly one component")
                    .that(device.findObjects(By.desc(label))).hasSize(1)
                val bounds = row.visibleBounds
                val downTime = SystemClock.uptimeMillis()
                pointer(MotionEvent.ACTION_DOWN, bounds.centerX().toFloat(), bounds.centerY().toFloat(), downTime)
                SystemClock.sleep(850)
                // The long press opens the app menu. Moving beyond touch slop converts that same
                // held pointer into a drag, which hides search before workspace movement.
                val firstDragPoint = dragPastMenu(bounds.centerX().toFloat(), bounds.centerY().toFloat(), downTime)
                assertThat(scenario.hasVisibleSearchLayer()).isFalse()
                SystemClock.sleep(300) // allow keyboard insets to settle before choosing a cell
                val target = scenario.read { home ->
                    val layout = checkNotNull(home.findViewById<WorkspacePager>(R.id.workspace_pager).pageAt(page))
                    val location = IntArray(2).also(layout::getLocationOnScreen)
                    val cellWidth = (layout.width - layout.paddingLeft - layout.paddingRight) / layout.grid.columns
                    val cellHeight = (layout.height - layout.paddingTop - layout.paddingBottom) / layout.grid.rows
                    val x = if (layout.isRtl) layout.width - layout.paddingRight - cellWidth / 2 else layout.paddingLeft + cellWidth / 2
                    (location[0] + x).toFloat() to (location[1] + layout.paddingTop + cellHeight / 2).toFloat()
                }
                move(firstDragPoint.first, firstDragPoint.second, target.first, target.second, downTime)
                pointer(MotionEvent.ACTION_UP, target.first, target.second, downTime)
                assertThat(DeviceTestSupport.waitUntil {
                    runBlocking { deps.workspaceRepository.exportContent()?.items?.any { item ->
                        item.id !in beforeDrop.items.map { it.id } && item.target == ItemTarget.App(key) &&
                            item.placement.container == ContainerType.DESKTOP && item.placement.page == page
                    } == true }
                }).isTrue()
                device.takeScreenshot(File(context.getExternalFilesDir(null), "search-drag-workspace.png"))
                val afterDrop = checkNotNull(deps.workspaceRepository.exportContent())

                // The compact strip uses the same source. Stopping HOME while held must cancel
                // Android's drag itself; source detachment alone can leak a shadow to another activity.
                deps.settingsRepository.update { it.copy(search = it.search.copy(maxAppResults = 5)) }
                scenario.onActivity { it.showUniversalSearch(label) }
                val compact = device.wait(Until.findObject(By.desc(label)), DeviceTestSupport.UI_TIMEOUT_MS)
                assertThat(compact).isNotNull()
                val compactBounds = compact.visibleBounds
                val cancelDown = SystemClock.uptimeMillis()
                pointer(MotionEvent.ACTION_DOWN, compactBounds.centerX().toFloat(), compactBounds.centerY().toFloat(), cancelDown)
                SystemClock.sleep(850)
                val cancelDragPoint = dragPastMenu(compactBounds.centerX().toFloat(), compactBounds.centerY().toFloat(), cancelDown)
                assertThat(scenario.hasVisibleSearchLayer()).isFalse()
                scenario.moveToState(Lifecycle.State.CREATED)
                pointer(MotionEvent.ACTION_CANCEL, cancelDragPoint.first, cancelDragPoint.second, cancelDown)
                scenario.moveToState(Lifecycle.State.RESUMED)
                instrumentation.waitForIdleSync()
                assertThat(deps.workspaceRepository.exportContent()).isEqualTo(afterDrop)

                deps.settingsRepository.update { it.copy(home = it.home.copy(layoutLocked = true)) }
                scenario.onActivity { it.showUniversalSearch(label) }
                val locked = device.wait(Until.findObject(By.desc(label)), DeviceTestSupport.UI_TIMEOUT_MS)
                assertThat(locked).isNotNull()
                val lockedBounds = locked.visibleBounds
                val lockedDown = SystemClock.uptimeMillis()
                pointer(MotionEvent.ACTION_DOWN, lockedBounds.centerX().toFloat(), lockedBounds.centerY().toFloat(), lockedDown)
                SystemClock.sleep(850)
                assertThat(scenario.hasVisibleSearchLayer()).isTrue()
                pointer(MotionEvent.ACTION_UP, lockedBounds.centerX().toFloat(), lockedBounds.centerY().toFloat(), lockedDown)
                instrumentation.waitForIdleSync()
                assertThat(deps.workspaceRepository.exportContent()).isEqualTo(afterDrop)
                assertThat(DeviceTestSupport.resumedActivities().any { it is SettingsActivity }).isFalse()
                // Layout lock rejects the drag but still allows the normal app context menu.
                // Close that actual menu before checking that search and its result are restored.
                device.pressBack()
                val searchRestored = DeviceTestSupport.waitUntil {
                    scenario.hasVisibleSearchLayer() && device.hasObject(By.desc(label))
                }
                assertWithMessage("Search/result did not return after closing the locked app menu; visibleSearch=%s; hierarchy=%s",
                    scenario.hasVisibleSearchLayer(), accessibilitySnapshot()).that(searchRestored).isTrue()
                // Normal tapping still opens the correct app after the rejected long press/menu.
                val tapTarget = device.wait(Until.findObject(By.desc(label)), DeviceTestSupport.UI_TIMEOUT_MS)
                assertWithMessage("Search result missing before normal tap; label=%s; visibleSearch=%s; hierarchy=%s",
                    label, scenario.hasVisibleSearchLayer(), accessibilitySnapshot()).that(tapTarget != null).isTrue()
                checkNotNull(tapTarget).click()
                assertThat(DeviceTestSupport.waitForResumed(SettingsActivity::class.java)).isNotNull()
                } finally {
                    // A failing assertion while the finger is down must not leak a drag shadow or
                    // captured input into the next test. Cancel before ActivityScenario detaches HOME.
                    try {
                        scenario.onActivity { it.findViewById<View>(R.id.home_root).cancelDragAndDrop() }
                    } finally {
                        activeDownTime?.let { pointer(MotionEvent.ACTION_CANCEL, lastX, lastY, it) }
                    }
                }
            }
        } finally {
            DeviceTestSupport.finishAll(SettingsActivity::class.java)
            DeviceTestSupport.finishAll(HomeActivity::class.java)
            deps.settingsRepository.update { originalSettings }
            assertThat(deps.workspaceRepository.replaceAll(originalLayout)).isInstanceOf(EditResult.Applied::class.java)
            if (originalCustomization == null) deps.appCustomizationRepository.clear(key)
            else deps.appCustomizationRepository.update(key) { originalCustomization }
            DeviceTestSupport.resetStartupGuard()
        }
    }

    private fun move(fromX: Float, fromY: Float, toX: Float, toY: Float, downTime: Long) {
        for (step in 1..24) {
            val progress = step / 24f
            pointer(MotionEvent.ACTION_MOVE, fromX + (toX - fromX) * progress, fromY + (toY - fromY) * progress, downTime)
            SystemClock.sleep(16)
        }
    }

    private fun dragPastMenu(x: Float, y: Float, downTime: Long): Pair<Float, Float> {
        val distance = 48f * DeviceTestSupport.targetContext.resources.displayMetrics.density
        val nextX = x + distance
        pointer(MotionEvent.ACTION_MOVE, nextX, y, downTime)
        instrumentation.waitForIdleSync()
        return nextX to y
    }

    private fun pointer(action: Int, x: Float, y: Float, downTime: Long) {
        if (action == MotionEvent.ACTION_DOWN) activeDownTime = downTime
        lastX = x
        lastY = y
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        try {
            assertThat(instrumentation.uiAutomation.injectInputEvent(event, true)).isTrue()
        } finally {
            event.recycle()
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) activeDownTime = null
        }
    }

    private fun ActivityScenario<HomeActivity>.hasVisibleSearchLayer(): Boolean = read { home ->
        val root = home.findViewById<FrameLayout>(R.id.home_root)
        (0 until root.childCount).any { root.getChildAt(it) is ComposeView && root.getChildAt(it).visibility == View.VISIBLE }
    }

    private fun <T> ActivityScenario<HomeActivity>.read(block: (HomeActivity) -> T): T {
        var value: Any? = null
        onActivity { value = block(it) }
        @Suppress("UNCHECKED_CAST")
        return value as T
    }

    private fun accessibilitySnapshot(): String {
        val root = instrumentation.uiAutomation.rootInActiveWindow
            ?: return "<no active accessibility root>"
        val lines = mutableListOf<String>()
        fun visit(node: android.view.accessibility.AccessibilityNodeInfo, depth: Int) {
            if (lines.size >= 180) return
            val text = node.text?.toString().orEmpty()
            val description = node.contentDescription?.toString().orEmpty()
            lines += "${" ".repeat(depth)}${node.className} text=$text desc=$description clickable=${node.isClickable}"
            for (index in 0 until node.childCount) node.getChild(index)?.let { visit(it, depth + 1) }
        }
        visit(root, 0)
        return lines.joinToString(" | ")
    }
}
