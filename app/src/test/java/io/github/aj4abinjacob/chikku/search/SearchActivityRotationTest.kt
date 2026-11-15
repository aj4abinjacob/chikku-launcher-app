package io.github.aj4abinjacob.chikku.search

import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.Looper
import android.os.Process
import android.os.UserManager
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.AppsState
import io.github.aj4abinjacob.chikku.core.model.search.ShortcutLookup
import io.github.aj4abinjacob.chikku.home.FakeHomeDependencies
import io.github.aj4abinjacob.chikku.home.app
import io.github.aj4abinjacob.chikku.home.TestHomeApplication
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowUserManager

/**
 * After a rotation the retained [SearchViewModel] keeps its [SearchHostWiring]: the shortcut change
 * signal must stay live, so a shortcut change delivered after the recreate still refreshes results
 * (an Activity that closed the wiring on destroy left the retained ViewModel deaf).
 */
@RunWith(RobolectricTestRunner::class)
// SDK 29: from SDK 35 the profile check needs LauncherUserInfo, which Robolectric cannot supply.
@Config(sdk = [29], application = TestHomeApplication::class)
class SearchActivityRotationTest {

    private val application = ApplicationProvider.getApplicationContext<TestHomeApplication>()
    private val deps = FakeHomeDependencies()

    @Before
    fun setUp() {
        deps.appsRepository.state.value = AppsState.Loaded(listOf(app("Alpha", pkg = application.packageName)))
        shadowOf(application.getSystemService(LauncherApps::class.java)).setHasShortcutHostPermission(true)
        // The shortcut source resolves the owner's user from its serial number.
        shadowOf(application.getSystemService(UserManager::class.java)).setSerialNumberForUser(Process.myUserHandle(), 0L)
        shadowOf(application.getSystemService(UserManager::class.java)).setUserUnlocked(true)
        // The handle-taking isUserUnlocked reads a separate per-user state in Robolectric.
        shadowOf(application.getSystemService(UserManager::class.java))
            .setUserState(Process.myUserHandle(), ShadowUserManager.UserState.STATE_RUNNING_UNLOCKED)
        application.homeDependencies = deps
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    /** The search pipeline runs on Dispatchers.Default; wait (bounded) for [condition], keeping the main looper moving. */
    private fun awaitTrue(what: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (!condition()) {
            check(System.nanoTime() < deadline) { "timed out waiting for $what" }
            idle()
            Thread.sleep(20)
        }
    }

    private fun addShortcutAndNotify(id: String, label: String) {
        val info = ShortcutInfo.Builder(application, id).setShortLabel(label).setIntent(Intent("synthetic")).build()
        shadowOf(application.getSystemService(LauncherApps::class.java)).addDynamicShortcut(info)
        idle()
    }

    private fun shortcutLabels(vm: SearchViewModel): List<String> =
        (vm.results.value.shortcuts as? ShortcutLookup.Found)?.shortcuts.orEmpty().map { it.label }

    @Test
    fun theViewModelRetainedAcrossRotationStillRefreshesOnShortcutChanges() {
        ActivityScenario.launch(SearchActivity::class.java).use { scenario ->
            idle()
            var before: SearchViewModel? = null
            scenario.onActivity { activity ->
                before = ViewModelProvider(activity)[SearchViewModel::class.java]
                before!!.setQuery("alpha")
            }
            awaitTrue("the first search") { before!!.results.value.apps.isNotEmpty() && before!!.results.value.shortcuts is ShortcutLookup.Found }

            scenario.recreate()
            idle()
            var after: SearchViewModel? = null
            scenario.onActivity { after = ViewModelProvider(it)[SearchViewModel::class.java] }
            assertThat(after).isSameInstanceAs(before)
            awaitTrue("the re-collected search") { after!!.results.value.shortcuts is ShortcutLookup.Found }
            assertThat(shortcutLabels(after!!)).isEmpty()

            // A change delivered to the platform callback after the recreate must reach the retained ViewModel.
            addShortcutAndNotify("after-rotation", "Alpha shortcut")
            awaitTrue("the refresh after the shortcut change") { "Alpha shortcut" in shortcutLabels(after!!) }
        }
    }
}
