package io.github.aj4abinjacob.chikku.platform

import android.content.pm.PackageManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.DeviceTestSupport
import io.github.aj4abinjacob.chikku.core.model.ProfileType
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettingsCodec
import io.github.aj4abinjacob.chikku.data.SharedPreferencesSettingsRepository
import io.github.aj4abinjacob.chikku.settings.SettingsActivity
import io.github.aj4abinjacob.chikku.home.HomeActivity
import io.github.aj4abinjacob.chikku.home.homeDependencies
import java.io.FileInputStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Explicit cloud runner only. The host creates disposable profiles and installs this acceptance
 * app into them. No package inventories, labels or profile metadata enter assertion output.
 * API29–34 pause/resume uses a temporarily selected, foreground acceptance HOME with explicit
 * opt-in and restores the previous HOME in finally. API35+ scopes MODIFY_QUIET_MODE setup identity
 * to its own profile group and drops it before every platform read. Shell lacks MANAGE_USERS.
 * This fixture does not establish enterprise provisioning, Private Space UI or OEM acceptance.
 */
@RunWith(AndroidJUnit4::class)
class ManagedProfileAcceptanceDeviceTest {
    @Test fun freshUnprivilegedEnumerationReflectsSyntheticProfileStage() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Explicit disposable profile orchestration required", args.getString("cloudProfileFixture") == "true")
        val context = instrumentation.targetContext
        check(ACCEPTANCE_ID.matches(context.packageName)) { "Isolated acceptance application ID required" }
        check(Build.HARDWARE in setOf("ranchu", "goldfish")) { "Android emulator required" }
        check(Process.myUid() == context.applicationInfo.uid) { "Target application UID required" }
        val stage = args.getString("cloudProfileStage")
        check(stage in STAGES) { "Unknown profile acceptance stage" }
        val profileId = args.getString("cloudProfileUser")?.toIntOrNull()
        check(profileId != null && profileId in 1..9999) { "Synthetic profile ID required" }
        // Android assigns UIDs in per-user ranges; this public API avoids hidden UserHandle.of().
        val profileUser = UserHandle.getUserHandleForUid(profileId * 100_000)
        val manager = checkNotNull(context.getSystemService(UserManager::class.java))
        check(manager.isSystemUser && manager.isUserUnlocked) { "Unlocked primary emulator user required" }
        check(shell("getprop ro.kernel.qemu").trim() == "1") { "Disposable emulator required" }
        if (stage == "work-paused" || stage == "work-resumed") {
            check(profileUser in manager.userProfiles) { "Synthetic profile must belong to the primary user" }
            if (Build.VERSION.SDK_INT < 35) {
                assumeTrue("Foreground acceptance HOME setup requires explicit opt-in", args.getString("cloudProfileLauncherSetup") == "true")
                withTemporaryForegroundHome(context) {
                    check(manager.requestQuietModeEnabled(stage == "work-paused", profileUser)) {
                        "Synthetic quiet-mode transition requires credential-free setup"
                    }
                }
            } else {
                instrumentation.uiAutomation.adoptShellPermissionIdentity("android.permission.MODIFY_QUIET_MODE")
                try {
                    check(manager.requestQuietModeEnabled(stage == "work-paused", profileUser)) {
                        "Synthetic quiet-mode transition requires credential-free setup"
                    }
                } finally {
                    instrumentation.uiAutomation.dropShellPermissionIdentity()
                }
            }
        }
        // Detect accidentally retained setup identity before using production enumeration.
        assertThat(context.checkSelfPermission("android.permission.MANAGE_USERS"))
            .isEqualTo(PackageManager.PERMISSION_DENIED)
        assertThat(context.checkSelfPermission("android.permission.MODIFY_QUIET_MODE"))
            .isEqualTo(PackageManager.PERMISSION_DENIED)
        assertThat(context.checkSelfPermission("android.permission.ACCESS_HIDDEN_PROFILES"))
            .isEqualTo(PackageManager.PERMISSION_DENIED)

        val expectsWork = stage == "work-active" || stage == "work-resumed"
        var matching = emptyList<SourceProfile>()
        assertThat(DeviceTestSupport.waitUntil(DeviceTestSupport.LOAD_TIMEOUT_MS) {
            // A new adapter per poll: no replayed repository cache can hide a real transition.
            matching = AndroidAppsPlatformSource(context).profiles().filter { it.user == profileUser }
            if (expectsWork) matching.size == 1 else matching.isEmpty()
        }).isTrue()

        val source = AndroidAppsPlatformSource(context)
        val fresh = source.profiles()
        val personal = fresh.singleOrNull { it.user == Process.myUserHandle() }
        assertThat(personal != null && personal.type == ProfileType.PERSONAL).isTrue()
        val settingsClass = SettingsActivity::class.java.name
        fun ownEntry(profile: SourceProfile): Boolean = source.activities(profile).count {
            it.packageName == context.packageName && it.className == settingsClass
        } == 1
        assertThat(ownEntry(checkNotNull(personal))).isTrue()
        if (expectsWork) {
            val work = fresh.singleOrNull { it.user == profileUser }
            assertThat(work != null).isTrue()
            checkNotNull(work)
            val expectedType = if (Build.VERSION.SDK_INT >= 35) ProfileType.WORK else ProfileType.OTHER
            assertThat(work.type == expectedType && work.serial != personal.serial).isTrue()
            assertThat(ownEntry(work)).isTrue()
            // The same component must remain two distinct persisted identities.
            val personalKey = RawActivity(context.packageName, settingsClass, null).toLauncherApp(personal)?.key
            val workKey = RawActivity(context.packageName, settingsClass, null).toLauncherApp(work)?.key
            assertThat(personalKey != null && workKey != null && personalKey != workKey).isTrue()
        } else {
            assertThat(fresh.none { it.user == profileUser }).isTrue()
        }
        // For private-running/stopped stages, absence is deliberate unsupported-M0 behavior;
        // stopping a profile is not evidence of the actual Private Space lock/credential UI.
    }

    private fun resolvedHome(context: Context): ComponentName? {
        val info = context.packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY,
        )?.activityInfo ?: return null
        return ComponentName(info.packageName, info.name)
    }

    private fun withTemporaryForegroundHome(context: Context, setup: () -> Unit) {
        val previous = checkNotNull(resolvedHome(context)) { "An existing resolved HOME is required for safe restoration" }
        check(previous.packageName != "android") { "A HOME chooser cannot be restored as a default launcher" }
        val target = ComponentName(context, HomeActivity::class.java)
        check(SAFE_HOME_COMPONENT.matches(previous.flattenToString()) && SAFE_HOME_COMPONENT.matches(target.flattenToString())) {
            "Both HOME components must be safely restorable before temporary setup"
        }
        val preferences = context.getSharedPreferences(SharedPreferencesSettingsRepository.PREFS_NAME, Context.MODE_PRIVATE)
        val raw = preferences.getString(SharedPreferencesSettingsRepository.KEY_SETTINGS, null)
        check(raw == null || LauncherSettingsCodec.decodeOrNull(raw) != null) { "Readable acceptance settings required for temporary HOME setup" }
        val originalPreferences = preferences.all.toMap()
        val settings = context.homeDependencies().settingsRepository
        val originalSettings = runBlocking { settings.settings.first() }
        var restorationRequired = false
        var settingsRestorationRequired = false
        try {
            // The Cards acceptance target may still be first-run. Temporarily complete only
            // onboarding so HomeActivity stays foreground instead of routing to onboarding UI.
            settingsRestorationRequired = true
            runBlocking { settings.update { it.copy(onboarding = it.onboarding.copy(completed = true)) } }
            // Register restoration before the command: a platform command may change state but
            // fail its result check. Only these resolved/isolated components enter the command.
            restorationRequired = true
            setHome(target)
            check(DeviceTestSupport.waitUntil { resolvedHome(context) == target }) { "Acceptance HOME selection did not complete" }
            ActivityScenario.launch<HomeActivity>(Intent(context, HomeActivity::class.java)
                .setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)).use { scenario ->
                check(DeviceTestSupport.waitUntil {
                    var foreground = false
                    scenario.onActivity { foreground = it.hasWindowFocus() && it.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) }
                    foreground
                }) { "Acceptance HOME did not become foreground" }
                // This public request is unprivileged: the actual foreground default launcher is
                // an allowed API29 caller. No extra permission can leak into later enumeration.
                setup()
            }
        } finally {
            try {
                if (settingsRestorationRequired) {
                    runBlocking {
                        settings.update { it.copy(onboarding = originalSettings.onboarding) }
                        check(settings.settings.first() == originalSettings) { "Other acceptance settings changed during profile setup" }
                    }
                    val editor = preferences.edit()
                    if (raw == null) editor.remove(SharedPreferencesSettingsRepository.KEY_SETTINGS)
                    else editor.putString(SharedPreferencesSettingsRepository.KEY_SETTINGS, raw)
                    check(editor.commit()) { "Original acceptance settings could not be restored" }
                    check(preferences.all == originalPreferences) { "Original acceptance preferences were not preserved" }
                }
            } finally {
                if (restorationRequired) {
                    setHome(previous)
                    check(DeviceTestSupport.waitUntil { resolvedHome(context) == previous }) { "Previous HOME restoration failed" }
                }
            }
        }
    }

    private fun setHome(component: ComponentName) {
        val value = component.flattenToString()
        check(SAFE_HOME_COMPONENT.matches(value)) { "Resolved HOME component is not safe for the guarded command" }
        check(shell("cmd package set-home-activity --user 0 $value").lineSequence().any { it.trim() == "Success" }) {
            "Guarded HOME selection command failed"
        }
    }

    private fun shell(command: String): String = InstrumentationRegistry.getInstrumentation().uiAutomation
        .executeShellCommand(command).use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).use { it.readBytes().toString(Charsets.UTF_8) }
        }

    private companion object {
        val SAFE_HOME_COMPONENT = Regex("[A-Za-z0-9_.]+/[A-Za-z0-9_.]+")
        val ACCEPTANCE_ID = Regex("io\\.github\\.aj4abinjacob\\.chikku\\.acceptance\\.[a-z][a-z0-9_]{0,47}")
        val STAGES = setOf("work-active", "work-paused", "work-resumed", "work-removed", "private-running", "private-stopped")
    }
}
