package io.github.aj4abinjacob.chikku

import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.DeviceTestSupport.targetContext
import io.github.aj4abinjacob.chikku.DeviceTestSupport.targetPackage
import io.github.aj4abinjacob.chikku.home.HomeActivity
import io.github.aj4abinjacob.chikku.settings.SettingsActivity
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves the installed manifest makes Chikku a HOME candidate on this device and exposes exactly one
 * visible launcher entry (settings). Read-only: never changes the default HOME role.
 */
@RunWith(AndroidJUnit4::class)
class HomeIntentResolutionTest {
    private val pm: PackageManager get() = targetContext.packageManager

    @Test
    fun homeActivityIsAHomeCandidate() {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val candidates = pm.queryIntentActivities(home, PackageManager.MATCH_DEFAULT_ONLY)
            .map { it.activityInfo.packageName to it.activityInfo.name }

        assertThat(candidates).contains(targetPackage to HomeActivity::class.java.name)
        // Exactly one HOME activity from this package.
        assertThat(candidates.count { it.first == targetPackage }).isEqualTo(1)
    }

    @Test
    fun homeActivityIsExportedSingleTask() {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(targetPackage)
        val info = pm.queryIntentActivities(home, PackageManager.MATCH_DEFAULT_ONLY).single().activityInfo

        assertThat(info.exported).isTrue()
        assertThat(info.launchMode).isEqualTo(android.content.pm.ActivityInfo.LAUNCH_SINGLE_TASK)
    }

    @Test
    fun launcherEntryResolvesToSettingsOnly() {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(targetPackage)
        val entries = pm.queryIntentActivities(launcher, 0).map { it.activityInfo.name }

        assertThat(entries).containsExactly(SettingsActivity::class.java.name)
        // HOME must not appear as a second icon in other launchers' app lists.
        assertThat(entries).doesNotContain(HomeActivity::class.java.name)
    }
}
