package io.github.aj4abinjacob.chikku.platform

import android.accessibilityservice.AccessibilityService
import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.speech.RecognizerIntent
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.GestureAction
import io.github.aj4abinjacob.chikku.home.FakeAppLauncher
import io.github.aj4abinjacob.chikku.settings.SettingsActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GestureActionExecutorTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val launcher = FakeAppLauncher()
    private val hostCalls = mutableListOf<String>()
    private val host = object : GestureHost {
        override fun openDrawer() {
            hostCalls += "drawer"
        }

        override fun openSearch() {
            hostCalls += "search"
        }

        override fun openCards() { hostCalls += "cards" }

        override fun toggleDock() { hostCalls += "dock" }

        override fun onAppLaunched() { hostCalls += "launched" }

        override fun goToDefaultPage() {
            hostCalls += "page"
        }
    }
    private var statusBarWorks = true
    private val statusBarCalls = mutableListOf<Boolean>()
    private var serviceConnected = false
    private val globalCalls = mutableListOf<Int>()
    private var shortcutResult: LaunchResult = LaunchResult.Started
    private val startedShortcuts = mutableListOf<String>()

    private val executor = GestureActionExecutor(
        context = app,
        appLauncher = launcher,
        host = host,
        statusBar = { quick ->
            statusBarCalls += quick
            statusBarWorks
        },
        globalActions = { action ->
            globalCalls += action
            serviceConnected
        },
        externalIntents = ExternalIntentStarter { intents ->
            runBlocking { GuardedIntentLauncher(app, AppLockGuard.NONE, Dispatchers.Unconfined).startFirst(intents) == IntentHandoffResult.Started }
        },
        shortcuts = { pkg, id, _, _ ->
            startedShortcuts += "$pkg/$id"
            shortcutResult
        },
    )

    @Before
    fun setUp() {
        // Unresolvable intents throw ActivityNotFoundException, as on a device.
        shadowOf(app).checkActivities(true)
    }

    @Test
    fun noneDoesNothing() {
        assertThat(executor.execute(GestureAction.None)).isEqualTo(GestureResult.NoAction)
        assertThat(hostCalls).isEmpty()
    }

    @Test
    fun homeSurfaceActionsGoToTheHost() {
        assertThat(executor.execute(GestureAction.OpenDrawer)).isEqualTo(GestureResult.Performed)
        assertThat(executor.execute(GestureAction.OpenSearch)).isEqualTo(GestureResult.Performed)
        assertThat(executor.execute(GestureAction.GoToDefaultPage)).isEqualTo(GestureResult.Performed)
        assertThat(executor.execute(GestureAction.ToggleDock)).isEqualTo(GestureResult.Performed)
        assertThat(hostCalls).containsExactly("drawer", "search", "page", "dock").inOrder()
    }

    @Test
    fun openCardsAsksTheHostForTheCardsPanel() {
        assertThat(executor.execute(GestureAction.OpenCards)).isEqualTo(GestureResult.Performed)
        assertThat(hostCalls).containsExactly("cards")
    }

    @Test
    fun onlySuccessfulAppActionsNotifyAutoCloseHost() {
        val key = AppKey("com.example", "com.example.Main", 0)
        executor.execute(GestureAction.LaunchApp(key))
        executor.execute(GestureAction.LaunchShortcut("com.example", "shortcut", 0))
        launcher.result = LaunchResult.Unavailable(LaunchResult.Reason.NOT_FOUND)
        shortcutResult = LaunchResult.Unavailable(LaunchResult.Reason.NOT_FOUND)
        executor.execute(GestureAction.LaunchApp(key))
        executor.execute(GestureAction.LaunchShortcut("com.example", "shortcut", 0))
        assertThat(hostCalls).containsExactly("launched", "launched")
    }

    @Test
    fun shadeUsesTheStatusBarFirst() {
        assertThat(executor.execute(GestureAction.ExpandNotifications)).isEqualTo(GestureResult.Performed)
        assertThat(executor.execute(GestureAction.ExpandQuickSettings)).isEqualTo(GestureResult.Performed)
        assertThat(statusBarCalls).containsExactly(false, true).inOrder()
        assertThat(globalCalls).isEmpty()
    }

    @Test
    fun blockedStatusBarFallsBackToTheAccessibilityServiceOrExplains() {
        statusBarWorks = false
        assertThat(executor.execute(GestureAction.ExpandNotifications)).isEqualTo(GestureResult.NeedsAccessibilityService)
        serviceConnected = true
        assertThat(executor.execute(GestureAction.ExpandQuickSettings)).isEqualTo(GestureResult.Performed)
        assertThat(globalCalls).containsExactly(
            AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS,
            AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS,
        ).inOrder()
    }

    @Test
    fun lockScreenAndRecentsNeedTheAccessibilityService() {
        assertThat(executor.execute(GestureAction.LockScreen)).isEqualTo(GestureResult.NeedsAccessibilityService)
        assertThat(executor.execute(GestureAction.Recents)).isEqualTo(GestureResult.NeedsAccessibilityService)
        serviceConnected = true
        assertThat(executor.execute(GestureAction.LockScreen)).isEqualTo(GestureResult.Performed)
        assertThat(executor.execute(GestureAction.Recents)).isEqualTo(GestureResult.Performed)
        assertThat(globalCalls).containsExactly(
            AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN,
            AccessibilityService.GLOBAL_ACTION_RECENTS,
            AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN,
            AccessibilityService.GLOBAL_ACTION_RECENTS,
        ).inOrder()
    }

    @Test
    fun theAccessibilityServiceIsNotConnectedInTests() {
        // The real performer reports "not connected" instead of throwing when the user has not enabled it.
        assertThat(ChikkuAccessibilityService.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)).isFalse()
        assertThat(ChikkuAccessibilityService.isEnabled(app)).isFalse()
        assertThat(ChikkuAccessibilityService.settingsIntent(app).action).isEqualTo(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
    }

    @Test
    fun launcherSettingsOpensChikkuSettings() {
        assertThat(executor.execute(GestureAction.OpenLauncherSettings)).isEqualTo(GestureResult.Performed)
        assertThat(shadowOf(app).nextStartedActivity.component?.className).isEqualTo(SettingsActivity::class.java.name)
    }

    @Test
    fun assistantFallsBackToVoiceCommandAndReportsWhenNothingHandlesIt() {
        assertThat(executor.execute(GestureAction.Assistant)).isEqualTo(GestureResult.Unavailable(LaunchResult.Reason.NOT_FOUND))

        registerActivity(Intent(Intent.ACTION_VOICE_COMMAND))
        assertThat(executor.execute(GestureAction.Assistant)).isEqualTo(GestureResult.Performed)
        assertThat(shadowOf(app).nextStartedActivity.action).isEqualTo(Intent.ACTION_VOICE_COMMAND)
    }

    @Test
    fun voiceSearchPrefersWebSearchThenHandsFree() {
        registerActivity(Intent(RecognizerIntent.ACTION_VOICE_SEARCH_HANDS_FREE))
        assertThat(executor.execute(GestureAction.VoiceSearch)).isEqualTo(GestureResult.Performed)
        assertThat(shadowOf(app).nextStartedActivity.action).isEqualTo(RecognizerIntent.ACTION_VOICE_SEARCH_HANDS_FREE)

        registerActivity(Intent(RecognizerIntent.ACTION_WEB_SEARCH))
        assertThat(executor.execute(GestureAction.VoiceSearch)).isEqualTo(GestureResult.Performed)
        assertThat(shadowOf(app).nextStartedActivity.action).isEqualTo(RecognizerIntent.ACTION_WEB_SEARCH)
    }

    @Test
    fun launchAppUsesTheAppLauncherAndMapsFailures() {
        val key = AppKey("com.example", "com.example.Main", 10)
        assertThat(executor.execute(GestureAction.LaunchApp(key), Rect(0, 0, 1, 1))).isEqualTo(GestureResult.Performed)
        assertThat(launcher.launched).containsExactly(key)

        launcher.result = LaunchResult.Unavailable(LaunchResult.Reason.PROFILE_UNAVAILABLE)
        assertThat(executor.execute(GestureAction.LaunchApp(key)))
            .isEqualTo(GestureResult.Unavailable(LaunchResult.Reason.PROFILE_UNAVAILABLE))
    }

    @Test
    fun launchShortcutUsesTheShortcutStarter() {
        val action = GestureAction.LaunchShortcut("com.example.dialer", "call:mum", 0)
        assertThat(executor.execute(action)).isEqualTo(GestureResult.Performed)
        shortcutResult = LaunchResult.Unavailable(LaunchResult.Reason.SECURITY)
        assertThat(executor.execute(action)).isEqualTo(GestureResult.Unavailable(LaunchResult.Reason.SECURITY))
        assertThat(startedShortcuts).containsExactly("com.example.dialer/call:mum", "com.example.dialer/call:mum")
    }

    @Test
    fun hostFailuresBecomeResultsInsteadOfCrashes() {
        val failing = GestureActionExecutor(
            context = app,
            appLauncher = launcher,
            host = object : GestureHost {
                override fun openDrawer() = throw IllegalStateException("boom")
                override fun openSearch() = throw SecurityException("no")
                override fun goToDefaultPage() = Unit
            },
            statusBar = { throw IllegalStateException("hidden api") },
            globalActions = { false },
            shortcuts = { _, _, _, _ -> LaunchResult.Started },
        )
        assertThat(failing.execute(GestureAction.OpenDrawer)).isEqualTo(GestureResult.Unavailable(LaunchResult.Reason.PROFILE_UNAVAILABLE))
        assertThat(failing.execute(GestureAction.OpenSearch)).isEqualTo(GestureResult.Unavailable(LaunchResult.Reason.SECURITY))
        assertThat(failing.execute(GestureAction.ExpandNotifications)).isEqualTo(GestureResult.NeedsAccessibilityService)
    }

    @Test
    fun theRealStatusBarExpanderNeverThrows() {
        // Robolectric has no status bar service behind the hidden methods; the call must just report false or true.
        ReflectionStatusBarExpander(app).expand(quickSettings = false)
    }

    private fun registerActivity(intent: Intent) {
        val handler = ComponentName("com.example.handler", "com.example.handler.Main")
        val pm = shadowOf(app.packageManager)
        pm.addActivityIfNotPresent(handler).apply { exported = true; enabled = true; applicationInfo.enabled = true }
        pm.addIntentFilterForActivity(handler, IntentFilter(intent.action).apply { addCategory(Intent.CATEGORY_DEFAULT) })
    }

    @Test
    fun lockedTargetsReportLockedInsteadOfUnavailable() {
        launcher.result = LaunchResult.Locked("example.locked", 0)
        assertThat(executor.execute(GestureAction.LaunchApp(io.github.aj4abinjacob.chikku.core.model.AppKey("example.locked", "example.locked.Main", 0))))
            .isEqualTo(GestureResult.Locked(LaunchResult.Locked("example.locked", 0)))
        shortcutResult = LaunchResult.Locked("example.locked", 0)
        assertThat(executor.execute(GestureAction.LaunchShortcut("example.locked", "chat", 0)))
            .isEqualTo(GestureResult.Locked(LaunchResult.Locked("example.locked", 0)))
    }
}
