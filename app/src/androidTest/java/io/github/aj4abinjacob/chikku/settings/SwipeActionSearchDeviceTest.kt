package io.github.aj4abinjacob.chikku.settings

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.widget.FrameLayout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.StaleObjectException
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.DeviceTestSupport
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.AppCustomization
import io.github.aj4abinjacob.chikku.core.model.AppCustomizationRepository
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.AppsRepository
import io.github.aj4abinjacob.chikku.core.model.AppsState
import io.github.aj4abinjacob.chikku.core.model.GestureAction
import io.github.aj4abinjacob.chikku.core.model.ItemActions
import io.github.aj4abinjacob.chikku.core.model.LauncherApp
import io.github.aj4abinjacob.chikku.core.model.ProfileType
import io.github.aj4abinjacob.chikku.core.model.search.DefaultAppSearcher
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.core.model.settings.SettingsRepository
import io.github.aj4abinjacob.chikku.home.ComposeDialogHost
import io.github.aj4abinjacob.chikku.home.HomeDependencies
import io.github.aj4abinjacob.chikku.home.homeDependencies
import io.github.aj4abinjacob.chikku.platform.IconProvider
import io.github.aj4abinjacob.chikku.settings.components.SwipeActionsEditorDialog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.Proxy

/** The actual HOME dialog host and keyboard, with synthetic apps and no persisted edits. */
@RunWith(AndroidJUnit4::class)
class SwipeActionSearchDeviceTest {
    @Test fun searchCanFocusTypeClearAndPickInBothSwipeDirections() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        device.wakeUp()
        val real = context.homeDependencies()
        val before = runBlocking { real.settingsRepository.exportJson() }
        val apps = listOf("Alpha", "Beta", "Gamma").map {
            LauncherApp(AppKey("sample.${it.lowercase()}", "Main", 0), it, ProfileType.PERSONAL)
        }
        fun <T> frozen(type: Class<T>, getter: String, value: Any): T {
            @Suppress("UNCHECKED_CAST")
            return Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, _ ->
                check(method.name == getter) { "Unexpected repository operation" }
                value
            } as T
        }
        val deps = object : HomeDependencies by real {
            override val appSearcher = DefaultAppSearcher()
            override val appsRepository = frozen(AppsRepository::class.java, "getApps", MutableStateFlow<AppsState>(AppsState.Loaded(apps)))
            override val settingsRepository = frozen(SettingsRepository::class.java, "getSettings", MutableStateFlow(LauncherSettings()))
            override val appCustomizationRepository = frozen(AppCustomizationRepository::class.java, "getCustomizations",
                MutableStateFlow(emptyMap<AppKey, AppCustomization>()))
            override val iconProvider = object : IconProvider {
                override suspend fun icon(key: AppKey) = ColorDrawable(Color.GRAY)
                override fun invalidate(packageName: String) = Unit
            }
        }
        var changed = ItemActions(swipeUp = GestureAction.LaunchApp(apps[0].key))
        lateinit var host: ComposeDialogHost
        fun find(selector: BySelector): androidx.test.uiautomator.UiObject2 {
            var previous: android.graphics.Rect? = null
            var stable = 0
            val settled = DeviceTestSupport.waitUntil {
                val bounds = try {
                    device.findObject(selector)?.visibleBounds
                } catch (_: StaleObjectException) {
                    null // A page transition replaced the node; reacquire on the next poll.
                }
                stable = if (bounds != null && bounds == previous) stable + 1 else 0
                previous = bounds
                stable >= 3
            }
            if (!settled) {
                device.dumpWindowHierarchy(java.io.File(context.cacheDir, "picker-failure.xml"))
                error("Synthetic picker control not visible")
            }
            return checkNotNull(device.findObject(selector))
        }
        fun findLabel(label: String) = find(By.text(label))
        fun findText(resource: Int) = findLabel(context.getString(resource))
        try {
            ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val root = FrameLayout(activity)
                    activity.setContentView(root)
                    host = ComposeDialogHost(root, deps) { LauncherSettings() }
                    host.show { dismiss ->
                        var draft by remember { mutableStateOf(changed) }
                        SwipeActionsEditorDialog("Sample icon", draft.swipeUp, draft.swipeDown, apps,
                            "Use default", onChange = { up, down ->
                                draft = draft.copy(swipeUp = up, swipeDown = down)
                                changed = draft
                            }, onDismiss = dismiss, actions = emptyList(), appKey = apps[0].key)
                    }
                }
                for (direction in listOf(R.string.gestures_sheet_swipe_up, R.string.gestures_sheet_swipe_down)) {
                    findText(direction).click()
                    // A current target changes the action label but still opens the same app page.
                    val choose = if (direction == R.string.gestures_sheet_swipe_up) {
                        findLabel(context.getString(R.string.gesture_action_launch_app, "Alpha"))
                    } else findText(R.string.gesture_picker_choose_app)
                    choose.click()
                    val field = find(By.clazz("android.widget.EditText"))
                    field.click()
                    field.text = "b"
                    field.text = "be"
                    field.text = "beta"
                    findLabel("Beta")
                    field.text = "zzzzzz"
                    findText(R.string.app_picker_no_matches)
                    checkNotNull(device.findObject(By.desc(context.getString(R.string.icon_picker_clear_search)))).click()
                    findLabel("Alpha")
                    find(By.clazz("android.widget.EditText")).text = "beta"
                    findLabel("Beta").click()
                    findText(R.string.gestures_sheet_swipe_up)
                }
                assertThat(changed.swipeUp).isEqualTo(GestureAction.LaunchApp(apps[1].key))
                assertThat(changed.swipeDown).isEqualTo(GestureAction.LaunchApp(apps[1].key))
                scenario.onActivity { host.dismiss() }
            }
        } finally {
            assertThat(runBlocking { real.settingsRepository.exportJson() } == before).isTrue()
        }
    }
}
