package io.github.aj4abinjacob.chikku.platform

import android.app.Application
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Rect
import android.os.Bundle
import android.os.Process
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

/** The long-press menu's shortcut launch has the same lock and profile guards as every other path. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], shadows = [AppMenuStartShortcutTest.Started::class])
class AppMenuStartShortcutTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val users = app.getSystemService(UserManager::class.java)
    private val shortcut = AppShortcut(
        "id", "Label", null,
        ShortcutInfo.Builder(app, "id").setShortLabel("Label").setIntent(android.content.Intent("x")).build(),
    )

    @Before fun setUp() {
        Started.calls = 0
        shadowOf(users).setUserState(Process.myUserHandle(), ShadowUserManager.UserState.STATE_RUNNING_UNLOCKED)
    }

    @Test fun availableProfileStarts() {
        assertThat(AppMenuPlatform(app, AppLockGuard.NONE).startShortcut(shortcut, null)).isEqualTo(LaunchResult.Started)
        assertThat(Started.calls).isEqualTo(1)
    }

    @Test fun lockedProfileIsRefusedBeforeLauncherApps() {
        shadowOf(users).setUserState(Process.myUserHandle(), ShadowUserManager.UserState.STATE_RUNNING_LOCKED)
        assertThat(AppMenuPlatform(app, AppLockGuard.NONE).startShortcut(shortcut, null))
            .isEqualTo(LaunchResult.Unavailable(LaunchResult.Reason.PROFILE_UNAVAILABLE))
        assertThat(Started.calls).isEqualTo(0)
    }

    @Test fun lockedAppIsRefusedBeforeLauncherApps() {
        assertThat(AppMenuPlatform(app, AppLockGuard { _, _ -> false }).startShortcut(shortcut, null))
            .isInstanceOf(LaunchResult.Locked::class.java)
        assertThat(Started.calls).isEqualTo(0)
    }

    @Implements(LauncherApps::class)
    class Started {
        @Implementation fun startShortcut(info: ShortcutInfo, bounds: Rect?, options: Bundle?) {
            calls++
        }
        companion object { var calls = 0 }
    }
}
