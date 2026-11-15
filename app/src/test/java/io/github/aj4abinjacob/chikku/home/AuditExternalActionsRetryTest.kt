package io.github.aj4abinjacob.chikku.home

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Looper
import android.os.Process
import android.os.UserManager
import androidx.activity.ComponentActivity
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.applock.AppLockManager
import io.github.aj4abinjacob.chikku.applock.AppUnlocker
import io.github.aj4abinjacob.chikku.applock.LockTarget
import io.github.aj4abinjacob.chikku.applock.StoredAppLock
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 36], application = TestHomeApplication::class)
class AuditExternalActionsRetryTest {
    @Test fun cancelledAuthenticationStartsNothingAndSuccessRetriesThePinnedListing() = verify(false, false)

    @Test fun authenticationSuccessWhileStoppedWaitsForStartAndRetriesExactTarget() = verify(true, false)

    @Test fun authenticationSuccessAfterRestartRetriesExactTarget() = verify(true, true)

    private fun verify(stopForAuthentication: Boolean, restartBeforeSuccess: Boolean) {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val component = ComponentName("synthetic.store", "synthetic.store.Main")
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.invalid/item?id=exact"))
            .setPackage(component.packageName).putExtra("pending", "exact-value")
        shadowOf(activity.packageManager).apply {
            addActivityIfNotPresent(component).apply {
                exported = true
                enabled = true
                applicationInfo.enabled = true
            }
            addIntentFilterForActivity(
                component,
                IntentFilter(Intent.ACTION_VIEW).apply {
                    addCategory(Intent.CATEGORY_DEFAULT)
                    addDataScheme("https")
                },
            )
        }
        val serial = activity.getSystemService(UserManager::class.java).getSerialNumberForUser(Process.myUserHandle())
        val target = LockTarget(component.packageName, serial)
        val lock = AppLockManager(InMemoryAppLockStore(StoredAppLock(setOf(target))), scope) { 0L }
        var retry: (() -> Unit)? = null
        var unavailable = 0
        val actions = GuardedExternalActions(
            activity,
            lock,
            MutableStateFlow(LauncherSettings()),
            AppUnlocker { _, callback -> retry = callback },
            { unavailable++ },
        )
        try {
            assertThat(actions.start(listOf(intent))).isTrue()
            waitUntil { retry != null }
            // Cancelling authentication invokes no callback and never launches any fallback.
            assertThat(shadowOf(activity).nextStartedActivity).isNull()
            intent.data = Uri.parse("https://example.invalid/changed")
            if (stopForAuthentication) controller.pause().stop()
            if (restartBeforeSuccess) controller.start().resume()
            lock.grant(target)
            checkNotNull(retry).invoke()
            if (stopForAuthentication && !restartBeforeSuccess) {
                assertThat(shadowOf(activity).nextStartedActivity).isNull()
                controller.start().resume()
            }
            val started = shadowOf(activity).nextStartedActivity
            assertThat(started.component).isEqualTo(component)
            assertThat(started.data.toString()).isEqualTo("https://example.invalid/item?id=exact")
            assertThat(started.getStringExtra("pending")).isEqualTo("exact-value")
            assertThat(unavailable).isEqualTo(0)
        } finally {
            controller.pause().stop().destroy()
            scope.cancel()
        }
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "External handoff did not reach authentication" }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
    }
}
