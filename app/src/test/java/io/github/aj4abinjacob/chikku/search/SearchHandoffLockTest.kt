package io.github.aj4abinjacob.chikku.search

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.os.Process
import android.os.UserManager
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.applock.AppLockManager
import io.github.aj4abinjacob.chikku.applock.AppUnlocker
import io.github.aj4abinjacob.chikku.applock.LockTarget
import io.github.aj4abinjacob.chikku.applock.StoredAppLock
import io.github.aj4abinjacob.chikku.core.model.search.ContactHit
import io.github.aj4abinjacob.chikku.home.FakeHomeDependencies
import io.github.aj4abinjacob.chikku.home.InMemoryAppLockStore
import io.github.aj4abinjacob.chikku.home.TestHomeApplication
import io.github.aj4abinjacob.chikku.platform.AppLockGuard
import io.github.aj4abinjacob.chikku.platform.GuardedIntentLauncher
import io.github.aj4abinjacob.chikku.platform.IntentHandoffResult
import io.github.aj4abinjacob.chikku.platform.LaunchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 36], application = TestHomeApplication::class)
class SearchHandoffLockTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val serial get() = app.getSystemService(UserManager::class.java).getSerialNumberForUser(Process.myUserHandle())
    private val contact = ContactHit(7, "synthetic", "Synthetic", phoneNumber = "15550100")
    // A deep link into one app; WhatsApp card buttons now go through ContactActionLauncher.
    private val whatsApp get() = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://wa.me/15550100"))
        .setPackage("com.whatsapp").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    private val target get() = LockTarget("com.whatsapp", serial)

    private fun register(intent: Intent, packageName: String): ComponentName {
        val component = ComponentName(packageName, "$packageName.Handler")
        shadowOf(app.packageManager).apply {
            addActivityIfNotPresent(component).apply {
                exported = true
                enabled = true
                applicationInfo.enabled = true
            }
            addIntentFilterForActivity(component, IntentFilter(intent.action).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                intent.categories?.forEach(::addCategory)
                intent.data?.scheme?.let(::addDataScheme)
                intent.type?.let(::addDataType)
            })
        }
        return component
    }

    @Test fun whatsappCannotStartUntilUnlockedAndOneTimeGrantIsConsumed() = runTest {
        val component = register(whatsApp, "com.whatsapp")
        val lock = AppLockManager(InMemoryAppLockStore(StoredAppLock(setOf(target))), backgroundScope) { 0L }
        lock.awaitLoaded()
        val launcher = GuardedIntentLauncher(app, lock, Dispatchers.Unconfined)
        val blocked = launcher.startFirst(listOf(whatsApp)) as IntentHandoffResult.Locked
        assertThat(blocked.lock).isEqualTo(LaunchResult.Locked("com.whatsapp", serial))
        assertThat(shadowOf(app).nextStartedActivity).isNull()
        lock.grant(target)
        assertThat(launcher.start(blocked.target)).isEqualTo(IntentHandoffResult.Started)
        val started = shadowOf(app).nextStartedActivity
        assertThat(started.component).isEqualTo(component)
        assertThat(started.data).isEqualTo(whatsApp.data)
        assertThat(started.type).isEqualTo(whatsApp.type)
        assertThat(started.action).isEqualTo(Intent.ACTION_VIEW)
        assertThat(launcher.start(blocked.target)).isInstanceOf(IntentHandoffResult.Locked::class.java)
        assertThat(shadowOf(app).nextStartedActivity).isNull()
    }

    @Test fun lockedPrimaryDoesNotFallBackToAnotherApp() = runTest {
        register(whatsApp, "com.whatsapp")
        val dial = checkNotNull(SearchHandoff.personIntent(contact, PersonAction.Call))
        register(dial, "example.dialer")
        val launcher = GuardedIntentLauncher(app, AppLockGuard { pkg, _ -> pkg != "com.whatsapp" }, Dispatchers.Unconfined)
        assertThat(launcher.startFirst(listOf(whatsApp, dial))).isInstanceOf(IntentHandoffResult.Locked::class.java)
        assertThat(shadowOf(app).nextStartedActivity).isNull()
    }

    @Test fun implicitContactDialAndMessageActionsCheckResolvedPackage() = runTest {
        for (action in listOf(PersonAction.Open, PersonAction.Call, PersonAction.TextMessage)) {
            val intent = checkNotNull(SearchHandoff.personIntent(contact, action))
            val component = register(intent, "example.${action::class.simpleName!!.lowercase()}")
            val launcher = GuardedIntentLauncher(app, AppLockGuard { _, _ -> false }, Dispatchers.Unconfined)
            val result = launcher.startFirst(listOf(intent)) as IntentHandoffResult.Locked
            assertThat(result.lock).isEqualTo(LaunchResult.Locked(component.packageName, serial))
            assertThat(shadowOf(app).nextStartedActivity).isNull()
        }
    }

    @Test fun chooserChecksChosenAppAndNeverLaunchesSystemChooser() = runTest {
        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://example.invalid/"))
            .addCategory(Intent.CATEGORY_BROWSABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        register(intent, "example.unlocked")
        register(intent, "example.locked")
        val checked = mutableListOf<LockTarget>()
        val launcher = GuardedIntentLauncher(app, AppLockGuard { pkg, user ->
            checked += LockTarget(pkg, user)
            pkg != "example.locked"
        }, Dispatchers.Unconfined)
        val result = launcher.startFirst(listOf(Intent.createChooser(intent, "Open with"))) as IntentHandoffResult.Choose
        assertThat(result.targets).hasSize(2)
        assertThat(checked).isEmpty()
        assertThat(shadowOf(app).nextStartedActivity).isNull() // dismissing the choice opens nothing
        assertThat(launcher.start(result.targets.first { it.packageName == "example.locked" }))
            .isInstanceOf(IntentHandoffResult.Locked::class.java)
        assertThat(shadowOf(app).nextStartedActivity).isNull()
        assertThat(launcher.start(result.targets.first { it.packageName == "example.unlocked" }))
            .isEqualTo(IntentHandoffResult.Started)
        assertThat(shadowOf(app).nextStartedActivity.component?.packageName).isEqualTo("example.unlocked")
        assertThat(checked).containsExactly(LockTarget("example.locked", serial), LockTarget("example.unlocked", serial)).inOrder()
    }

    @Test fun workProfileLockDoesNotLockPersonalCopy() = runTest {
        register(whatsApp, "com.whatsapp")
        val lock = AppLockManager(InMemoryAppLockStore(StoredAppLock(setOf(target.copy(userSerial = serial + 1)))), backgroundScope) { 0L }
        lock.awaitLoaded()
        assertThat(GuardedIntentLauncher(app, lock, Dispatchers.Unconfined).startFirst(listOf(whatsApp)))
            .isEqualTo(IntentHandoffResult.Started)
    }

    @Test fun unreadableOrNotLoadedLockStoreFailsClosed() = runTest {
        register(whatsApp, "com.whatsapp")
        val lock = AppLockManager(InMemoryAppLockStore(failReads = true), backgroundScope) { 0L }
        val launcher = GuardedIntentLauncher(app, lock, Dispatchers.Unconfined)
        assertThat(launcher.startFirst(listOf(whatsApp))).isInstanceOf(IntentHandoffResult.Locked::class.java)
        lock.awaitLoaded()
        assertThat(launcher.startFirst(listOf(whatsApp))).isInstanceOf(IntentHandoffResult.Locked::class.java)
        assertThat(shadowOf(app).nextStartedActivity).isNull()
    }

    @Test fun removedHandlerAfterUnlockDoesNotLaunchFallback() = runTest {
        val component = register(whatsApp, "com.whatsapp")
        var allowed = false
        val launcher = GuardedIntentLauncher(app, AppLockGuard { _, _ -> allowed }, Dispatchers.Unconfined)
        val blocked = launcher.startFirst(listOf(whatsApp)) as IntentHandoffResult.Locked
        shadowOf(app.packageManager).removeActivity(component)
        shadowOf(app).checkActivities(true)
        allowed = true
        assertThat(launcher.start(blocked.target)).isEqualTo(IntentHandoffResult.Unavailable)
        assertThat(shadowOf(app).nextStartedActivity).isNull()
    }

    @Test fun dismissedSearchDoesNotConsumeUnlockOrLaunch() = runTest {
        register(whatsApp, "com.whatsapp")
        var active = true
        var checks = 0
        val launcher = GuardedIntentLauncher(app, AppLockGuard { _, _ -> checks++; false }, Dispatchers.Unconfined) { active }
        val blocked = launcher.startFirst(listOf(whatsApp)) as IntentHandoffResult.Locked
        active = false
        assertThat(launcher.start(blocked.target)).isEqualTo(IntentHandoffResult.Unavailable)
        assertThat(checks).isEqualTo(1)
        assertThat(shadowOf(app).nextStartedActivity).isNull()
    }

    @Test fun unknownCurrentProfileNeverLaunches() = runTest {
        register(whatsApp, "com.whatsapp")
        shadowOf(app.getSystemService(UserManager::class.java)).setSerialNumberForUser(Process.myUserHandle(), -1L)
        assertThat(GuardedIntentLauncher(app, AppLockGuard.NONE, Dispatchers.Unconfined).startFirst(listOf(whatsApp)))
            .isEqualTo(IntentHandoffResult.Unavailable)
        assertThat(shadowOf(app).nextStartedActivity).isNull()
    }

    @Test fun systemForwarderIsNotTreatedAsAnUnlockedDestination() = runTest {
        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("geo:0,0?q=synthetic"))
        register(intent, "android")
        assertThat(GuardedIntentLauncher(app, AppLockGuard.NONE, Dispatchers.Unconfined).startFirst(listOf(intent)))
            .isEqualTo(IntentHandoffResult.Unavailable)
        assertThat(shadowOf(app).nextStartedActivity).isNull()
    }

    @Test fun personActionPromptsAndRetriesExactDialOnlyAfterSuccess() = runTest {
        val dial = checkNotNull(SearchHandoff.personIntent(contact, PersonAction.Call))
        register(dial, "example.dialer")
        val dialer = LockTarget("example.dialer", serial)
        val lock = AppLockManager(InMemoryAppLockStore(StoredAppLock(setOf(dialer))), backgroundScope) { 0L }
        lock.awaitLoaded()
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            val activity = controller.get()
            var completed = 0
            var onUnlocked: (() -> Unit)? = null
            val notices = mutableListOf<Int>()
            val actions = SearchResultActions(activity, FakeHomeDependencies(appLock = lock), AppUnlocker { locked, retry ->
                assertThat(locked).isEqualTo(LaunchResult.Locked("example.dialer", serial))
                onUnlocked = retry
            }, { completed++ }, notices::add, intentLauncher = GuardedIntentLauncher(activity, lock, Dispatchers.Unconfined), openFolder = {})
            actions.performPerson(contact, PersonAction.Call)
            shadowOf(android.os.Looper.getMainLooper()).idle()
            assertThat(onUnlocked).isNotNull()
            assertThat(completed).isEqualTo(0)
            assertThat(shadowOf(app).nextStartedActivity).isNull() // cancelling authentication never invokes retry
            lock.grant(dialer)
            checkNotNull(onUnlocked).invoke()
            assertThat(completed).isEqualTo(1)
            assertThat(shadowOf(app).nextStartedActivity.data).isEqualTo(dial.data)
            assertThat(notices).isEmpty()
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
