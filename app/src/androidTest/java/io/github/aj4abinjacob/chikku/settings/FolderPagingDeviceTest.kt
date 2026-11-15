package io.github.aj4abinjacob.chikku.settings

import android.app.KeyguardManager
import android.content.Intent
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.lifecycle.lifecycleScope
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.DeviceTestSupport
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.CellPlacement
import io.github.aj4abinjacob.chikku.core.model.ContainerType
import io.github.aj4abinjacob.chikku.core.model.settings.AnimationSpeed
import io.github.aj4abinjacob.chikku.core.model.settings.FolderScrollStyle
import io.github.aj4abinjacob.chikku.core.model.settings.FolderSettings
import io.github.aj4abinjacob.chikku.core.model.settings.FolderWindowStyle
import io.github.aj4abinjacob.chikku.home.AppIconView
import io.github.aj4abinjacob.chikku.home.Availability
import io.github.aj4abinjacob.chikku.home.FolderEntryUi
import io.github.aj4abinjacob.chikku.home.HomeItemUi
import io.github.aj4abinjacob.chikku.home.IconBinder
import io.github.aj4abinjacob.chikku.home.ItemContent
import io.github.aj4abinjacob.chikku.home.folder.FolderPopup
import io.github.aj4abinjacob.chikku.home.homeDependencies
import io.github.aj4abinjacob.chikku.theme.Motion
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic folder contents and reversible preference edits; no workspace placements are touched. */
@RunWith(AndroidJUnit4::class)
class FolderPagingDeviceTest {
    private val context get() = DeviceTestSupport.targetContext
    private val device get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Before fun requireUnlockedScreen() {
        device.wakeUp()
        assumeFalse("Unlock the device to exercise visible UI", context.getSystemService(KeyguardManager::class.java).isKeyguardLocked)
    }

    @Test fun folderScrollDirectionAndRowsPersistThroughSettingsRecreation() = runBlocking {
        val repository = context.homeDependencies().settingsRepository
        val original = repository.settings.first().folders
        try {
            repository.update { it.copy(folders = it.folders.copy(scrollStyle = FolderScrollStyle.VERTICAL, rows = 3)) }
            val intent = Intent(context, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_SECTION, SettingsSection.FOLDERS.name)
            ActivityScenario.launch<SettingsActivity>(intent).use { scenario ->
                val direction = scrollToSetting(By.text(context.getString(R.string.folders_paging_direction)))
                direction.click()
                device.wait(Until.findObject(By.text(context.getString(R.string.folders_paging_horizontal))), DeviceTestSupport.UI_TIMEOUT_MS).click()
                assertThat(DeviceTestSupport.waitUntil { runBlocking { repository.settings.first().folders.scrollStyle == FolderScrollStyle.HORIZONTAL_PAGED } }).isTrue()
                val slider = scrollToSetting(By.desc(context.getString(R.string.folders_paging_rows)))
                assertThat(slider.isEnabled).isTrue()
                val bounds = slider.visibleBounds
                device.swipe(bounds.centerX(), bounds.centerY(), bounds.right - 4, bounds.centerY(), 15)
                assertThat(DeviceTestSupport.waitUntil { runBlocking { repository.settings.first().folders.rows == 6 } }).isTrue()
                scenario.recreate()
                assertThat(scrollToSetting(By.text(context.getString(R.string.folders_paging_horizontal)))).isNotNull()
                assertThat(scrollToSetting(By.desc(context.getString(R.string.folders_paging_rows))).isEnabled).isTrue()
                val recreated = repository.settings.first().folders
                assertThat(recreated.scrollStyle).isEqualTo(FolderScrollStyle.HORIZONTAL_PAGED)
                assertThat(recreated.rows).isEqualTo(6)
            }
        } finally {
            DeviceTestSupport.finishAll(SettingsActivity::class.java)
            repository.update { it.copy(folders = original) }
        }
    }

    /** Scroll the actual settings pane, keeping clipped controls away from its gesture edges. */
    private fun scrollToSetting(selector: BySelector): UiObject2 {
        val paneSelector = By.scrollable(true)
        assertThat(device.wait(Until.hasObject(paneSelector), DeviceTestSupport.UI_TIMEOUT_MS)).isTrue()
        val inset = (16 * context.resources.displayMetrics.density).toInt()
        for (attempt in 0 until 32) {
            device.waitForIdle()
            val pane = device.findObject(paneSelector)
                ?: throw AssertionError("The settings scroll pane disappeared")
            val viewport = pane.visibleBounds
            val candidate = device.findObject(selector)
            if (candidate != null) {
                val bounds = candidate.visibleBounds
                if (bounds.height() >= inset && bounds.top >= viewport.top + inset && bounds.bottom <= viewport.bottom - inset) {
                    return candidate
                }
            }
            // Compose may report false even when the gesture moves its nested scroll content.
            // Keep each attempt bounded, reacquire the pane after movement, and judge progress by
            // whether the target is visible rather than by UiObject2.scroll's return value.
            pane.scroll(Direction.DOWN, 0.6f)
            device.waitForIdle()
        }
        // Only generic settings labels are displayed in this workflow.
        val directory = context.getExternalFilesDir(null)
        runCatching { device.takeScreenshot(java.io.File(directory, "folder-paging-settings-failure.png")) }
        runCatching { device.dumpWindowHierarchy(java.io.File(directory, "folder-paging-settings-failure.xml")) }
        throw AssertionError("Target folder setting is not visible after 32 scroll attempts: $selector")
    }

    @Test fun syntheticPagesSupportButtonsSwipesRtlAndStyleUpdatesWithoutAnimation() {
        val dependencies = context.homeDependencies()
        val key = DeviceTestSupport.settingsKey()
        val folder = HomeItemUi("synthetic-paged-folder", CellPlacement(ContainerType.DESKTOP, 0, 0, 0), ItemContent.Folder(
            "Synthetic pages", (1..7).map { number -> FolderEntryUi("sample-$number", ItemContent.App(
                key, "Folder page sample $number", false, Availability.AVAILABLE,
            )) },
        ))
        val appearance = FolderSettings(scrollStyle = FolderScrollStyle.HORIZONTAL_PAGED, columns = 2, rows = 1, windowStyle = FolderWindowStyle.IMMERSIVE)
        var popup: FolderPopup? = null
        try {
            ActivityScenario.launch<SettingsActivity>(Intent(context, SettingsActivity::class.java)).use { scenario ->
                lateinit var root: FrameLayout
                scenario.onActivity { activity ->
                    root = FrameLayout(activity)
                    activity.setContentView(root)
                    popup = FolderPopup(root, IconBinder(activity, activity.lifecycleScope, dependencies.iconProvider), NoEditsListener()).apply {
                        motion = Motion(AnimationSpeed.NONE)
                        open(folder, appearance)
                    }
                }
                assertThat(device.wait(Until.hasObject(By.desc("Folder page sample 1")), DeviceTestSupport.UI_TIMEOUT_MS)).isTrue()
                val next = device.wait(Until.findObject(By.desc(context.getString(R.string.folder_page_next_description))), DeviceTestSupport.UI_TIMEOUT_MS)
                next.click()
                assertPage(2, "Folder page sample 3")
                assertThat(device.hasObject(By.desc("Folder page sample 1"))).isFalse()
                fun swipe(forward: Boolean, rtl: Boolean) {
                    device.waitForIdle()
                    val rect = Rect()
                    scenario.onActivity {
                        val icons = descendants(root).filterIsInstance<AppIconView>()
                        icons.forEach { icon -> val bounds = Rect(); icon.getGlobalVisibleRect(bounds); rect.union(bounds) }
                    }
                    val increasing = forward == rtl
                    val left = rect.left + rect.width() / 8
                    val right = rect.right - rect.width() / 8
                    device.swipe(if (increasing) left else right, rect.centerY(), if (increasing) right else left, rect.centerY(), 20)
                }
                swipe(forward = true, rtl = false)
                assertPage(3, "Folder page sample 5")
                scenario.onActivity { popup!!.update(folder, appearance.copy(labelScale = 1.15f)); root.layoutDirection = View.LAYOUT_DIRECTION_RTL }
                assertPage(3, "Folder page sample 5")
                swipe(forward = true, rtl = true)
                assertPage(4, "Folder page sample 7")
                assertThat(device.findObject(By.desc(context.getString(R.string.folder_page_next_description))).isEnabled).isFalse()
                device.findObject(By.desc(context.getString(R.string.folder_page_previous_description))).click()
                assertPage(3, "Folder page sample 5")
                device.takeScreenshot(java.io.File(context.getExternalFilesDir(null), "folder-horizontal-pages-synthetic.png"))
                device.findObject(By.text(context.getString(R.string.folder_close_button))).click()
                assertThat(device.wait(Until.gone(By.desc("Folder page sample 5")), DeviceTestSupport.UI_TIMEOUT_MS)).isTrue()
            }
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { popup?.close() }
            DeviceTestSupport.finishAll(SettingsActivity::class.java)
        }
    }

    private fun assertPage(page: Int, description: String) {
        assertThat(device.wait(Until.hasObject(By.text(context.getString(R.string.folder_page_indicator, page, 4))), DeviceTestSupport.UI_TIMEOUT_MS)).isTrue()
        assertThat(device.wait(Until.hasObject(By.desc(description)), DeviceTestSupport.UI_TIMEOUT_MS)).isTrue()
    }

    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup) {
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) }
    } else emptyList()

    private class NoEditsListener : FolderPopup.Listener {
        override fun onEntryClick(folder: HomeItemUi, entry: FolderEntryUi, view: View) = Unit
        override fun onEntryLongClick(folder: HomeItemUi, entry: FolderEntryUi, view: View) = false
        override fun onReorder(folderId: String, orderedEntryIds: List<String>) = Unit
        override fun onRename(folderId: String, title: String) = Unit
        override fun onMoveToHome(folderId: String, entryId: String) = Unit
        override fun onRemoveEntry(folderId: String, entryId: String) = Unit
        override fun onEdit(folder: HomeItemUi) = Unit
        override fun onOpenChanged(open: Boolean) = Unit
    }
}
