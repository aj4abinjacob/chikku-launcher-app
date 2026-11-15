package io.github.aj4abinjacob.chikku.platform

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowUserManager

/** Synthetic Android shortcut loss after reinstall; no real Firefox or website is opened. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 34], shadows = [RestoredWebsiteShortcutTest.Shortcuts::class])
class RestoredWebsiteShortcutTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val users = app.getSystemService(UserManager::class.java)
    private val serial get() = users.getSerialNumberForUser(Process.myUserHandle())
    private val context = RecordingContext(app)
    private val url = "https://example.com/a%20page?q=hello%26world#section"

    @Before fun setUp() {
        shadowOf(users).setUserUnlocked(true)
        unlocked(Process.myUserHandle())
        Shortcuts.calls = 0
        Shortcuts.failure = ActivityNotFoundException()
        Shortcuts.afterStart = {}
    }

    @Test fun missingFirefoxRecordOpensExactPageInFirefoxWithOneLockAdmission() {
        var admits = 0
        val launcher = LauncherAppsShortcuts(context, AppLockGuard { pkg, user ->
            assertThat(pkg).isEqualTo("org.mozilla.firefox")
            assertThat(user).isEqualTo(serial)
            ++admits == 1
        })
        val bounds = Rect(10, 20, 60, 70)
        assertThat(launcher.start("org.mozilla.firefox", url, serial, bounds)).isEqualTo(LaunchResult.Started)
        assertThat(Shortcuts.calls).isEqualTo(1)
        assertThat(admits).isEqualTo(1)
        val intent = context.started.single()
        assertThat(intent.action).isEqualTo(Intent.ACTION_VIEW)
        assertThat(intent.`package`).isEqualTo("org.mozilla.firefox")
        assertThat(intent.dataString).isEqualTo(url)
        assertThat(intent.sourceBounds).isEqualTo(bounds)
        assertThat(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK).isNotEqualTo(0)
        assertThat(intent.extras).isNull()
    }

    @Test fun existingRecordAlwaysUsesNativeShortcutIncludingPwa() {
        Shortcuts.failure = null
        assertThat(start()).isEqualTo(LaunchResult.Started)
        assertThat(Shortcuts.calls).isEqualTo(1)
        assertThat(context.started).isEmpty()
    }

    @Test fun opaqueIdsOtherPublishersAndExecutableSchemesNeverBecomeWebIntents() {
        for (id in listOf("opaque-id", "intent://example.com/#Intent;scheme=https;end", "file:///tmp/a",
            "javascript:alert(1)", "https:///missing-host", "https://example.com/\nbad", "https://bad host/")) {
            assertThat(LauncherAppsShortcuts(context, AppLockGuard.NONE).start("org.mozilla.firefox", id, serial, null))
                .isEqualTo(LaunchResult.Unavailable(LaunchResult.Reason.SHORTCUT_UNAVAILABLE))
        }
        assertThat(LauncherAppsShortcuts(context, AppLockGuard.NONE).start("com.other", url, serial, null))
            .isEqualTo(LaunchResult.Unavailable(LaunchResult.Reason.SHORTCUT_UNAVAILABLE))
        assertThat(context.started).isEmpty()
    }

    @Test fun lockedAppAndUnavailableProfileCannotReachEitherLaunchPath() {
        assertThat(LauncherAppsShortcuts(context, AppLockGuard { _, _ -> false })
            .start("org.mozilla.firefox", url, serial, null)).isInstanceOf(LaunchResult.Locked::class.java)
        assertThat(LauncherAppsShortcuts(context, AppLockGuard.NONE)
            .start("org.mozilla.firefox", url, 999L, null))
            .isEqualTo(LaunchResult.Unavailable(LaunchResult.Reason.PROFILE_UNAVAILABLE))
        shadowOf(users).setUserState(Process.myUserHandle(), ShadowUserManager.UserState.STATE_RUNNING_LOCKED)
        assertThat(start()).isEqualTo(LaunchResult.Unavailable(LaunchResult.Reason.PROFILE_UNAVAILABLE))
        assertThat(Shortcuts.calls).isEqualTo(0)
        assertThat(context.started).isEmpty()
    }

    @Test fun workShortcutNeverFallsBackToPersonalFirefox() {
        val work = org.robolectric.util.ReflectionHelpers.callConstructor(
            UserHandle::class.java, org.robolectric.util.ReflectionHelpers.ClassParameter.from(Int::class.javaPrimitiveType, 10),
        )
        shadowOf(users).setSerialNumberForUser(work, 42L)
        unlocked(work)
        assertThat(LauncherAppsShortcuts(context, AppLockGuard.NONE).start("org.mozilla.firefox", url, 42L, null))
            .isEqualTo(LaunchResult.Unavailable(LaunchResult.Reason.PROFILE_UNAVAILABLE))
        assertThat(Shortcuts.calls).isEqualTo(1)
        assertThat(context.started).isEmpty()
    }

    @Test fun profileBecomingLockedDuringNativeFailureBlocksFallback() {
        Shortcuts.afterStart = {
            shadowOf(users).setUserState(Process.myUserHandle(), ShadowUserManager.UserState.STATE_RUNNING_LOCKED)
        }
        assertThat(start()).isEqualTo(LaunchResult.Unavailable(LaunchResult.Reason.PROFILE_UNAVAILABLE))
        assertThat(context.started).isEmpty()
    }

    @Test fun securityAndProfileFailuresNeverFallBack() {
        Shortcuts.failure = SecurityException()
        assertThat(start()).isEqualTo(LaunchResult.Unavailable(LaunchResult.Reason.SECURITY))
        Shortcuts.failure = IllegalStateException()
        assertThat(start()).isEqualTo(LaunchResult.Unavailable(LaunchResult.Reason.PROFILE_UNAVAILABLE))
        assertThat(context.started).isEmpty()
    }

    @Test fun missingOrRejectedFirefoxDoesNotUseAnotherBrowserOrReportSuccess() {
        context.failure = ActivityNotFoundException()
        assertThat(start()).isEqualTo(LaunchResult.Unavailable(LaunchResult.Reason.NOT_FOUND))
        context.failure = SecurityException()
        assertThat(start()).isEqualTo(LaunchResult.Unavailable(LaunchResult.Reason.SECURITY))
        assertThat(context.started).isEmpty()
    }

    @Test fun searchUsesTheSameRecoveryBoundary() {
        assertThat(io.github.aj4abinjacob.chikku.search.ShortcutStarter(context, AppLockGuard.NONE)
            .start("org.mozilla.firefox", url, serial, null)).isEqualTo(LaunchResult.Started)
        assertThat(context.started.single().dataString).isEqualTo(url)
    }

    private fun start() = LauncherAppsShortcuts(context, AppLockGuard.NONE).start("org.mozilla.firefox", url, serial, null)

    private fun unlocked(user: UserHandle) =
        shadowOf(users).setUserState(user, ShadowUserManager.UserState.STATE_RUNNING_UNLOCKED)

    private class RecordingContext(base: Context) : ContextWrapper(base) {
        val started = mutableListOf<Intent>()
        var failure: RuntimeException? = null
        override fun startActivity(intent: Intent, options: Bundle?) {
            failure?.let { throw it }
            started += Intent(intent)
        }
    }

    @Implements(LauncherApps::class)
    class Shortcuts {
        @Implementation fun startShortcut(pkg: String, id: String, bounds: Rect?, options: Bundle?, user: UserHandle) {
            calls++
            afterStart()
            failure?.let { throw it }
        }
        companion object {
            var calls = 0
            var failure: RuntimeException? = null
            var afterStart: () -> Unit = {}
        }
    }
}
