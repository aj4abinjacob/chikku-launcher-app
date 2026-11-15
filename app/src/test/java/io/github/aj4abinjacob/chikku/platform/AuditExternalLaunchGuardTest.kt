package io.github.aj4abinjacob.chikku.platform

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Process
import android.os.UserManager
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.applock.AppLockManager
import io.github.aj4abinjacob.chikku.applock.LockTarget
import io.github.aj4abinjacob.chikku.applock.StoredAppLock
import io.github.aj4abinjacob.chikku.core.model.GestureAction
import io.github.aj4abinjacob.chikku.home.FakeAppLauncher
import io.github.aj4abinjacob.chikku.home.InMemoryAppLockStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 36])
@OptIn(ExperimentalCoroutinesApi::class)
class AuditExternalLaunchGuardTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val serial get() = app.getSystemService(
        UserManager::class.java,
    ).getSerialNumberForUser(Process.myUserHandle())
    private val host = object : GestureHost {
        override fun openDrawer() = Unit
        override fun openSearch() = Unit
        override fun goToDefaultPage() = Unit
    }

    private fun register(intent: Intent, pkg: String): ComponentName {
        val component = ComponentName(pkg, "$pkg.Handler")
        shadowOf(app.packageManager).apply {
            addActivityIfNotPresent(component).apply {
                exported = true
                enabled = true
                applicationInfo.enabled = true
            }
            addIntentFilterForActivity(
                component,
                IntentFilter(intent.action).apply {
                    addCategory(Intent.CATEGORY_DEFAULT)
                    intent.data?.scheme?.let(::addDataScheme)
                },
            )
        }
        return component
    }

    @Test fun assistantVoiceAndStorePreserveExactLockedTargetAndNeverFallback() = runTest {
        val requests = mutableListOf<List<Intent>>()
        val executor = GestureActionExecutor(
            app,
            FakeAppLauncher(),
            host,
            shortcuts = { _, _, _, _ -> LaunchResult.Started },
            externalIntents = ExternalIntentStarter {
                requests += it
                true
            },
        )
        executor.execute(GestureAction.Assistant)
        executor.execute(GestureAction.VoiceSearch)
        requests += storeListingIntents("synthetic.requested")
        assertThat(
            requests[0].map {
                it.action
            },
        ).containsExactly(Intent.ACTION_ASSIST, Intent.ACTION_VOICE_COMMAND).inOrder()
        assertThat(
            requests[1].map {
                it.action
            },
        ).containsExactly(RecognizerIntent.ACTION_WEB_SEARCH, RecognizerIntent.ACTION_VOICE_SEARCH_HANDS_FREE).inOrder()
        for ((index, intents) in requests.withIndex()) {
            val pkg = intents.first().`package` ?: "synthetic.primary$index"
            val component = register(intents.first(), pkg)
            register(intents.last(), "synthetic.fallback$index")
            val lock =
                AppLockManager(InMemoryAppLockStore(StoredAppLock(setOf(LockTarget(pkg, serial)))), backgroundScope) {
                    0L
                }
            lock.awaitLoaded()
            val launcher = GuardedIntentLauncher(app, lock, Dispatchers.Unconfined)
            val blocked = launcher.startFirst(intents) as IntentHandoffResult.Locked
            assertThat(shadowOf(app).nextStartedActivity).isNull() // cancellation simply never retries
            lock.grant(LockTarget(pkg, serial))
            assertThat(launcher.start(blocked.target)).isEqualTo(IntentHandoffResult.Started)
            val started = shadowOf(app).nextStartedActivity
            assertThat(started.component).isEqualTo(component)
            assertThat(started.action).isEqualTo(intents.first().action)
            assertThat(started.data).isEqualTo(intents.first().data)
            assertThat(launcher.start(blocked.target)).isInstanceOf(IntentHandoffResult.Locked::class.java)
        }
    }

    @Test fun missingHandlerAndAmbiguousChooserFailClosedUntilExactChoice() = runTest {
        val launcher = GuardedIntentLauncher(app, AppLockGuard { _, _ -> false }, Dispatchers.Unconfined)
        assertThat(launcher.startFirst(listOf(Intent("synthetic.missing")))).isEqualTo(IntentHandoffResult.Unavailable)
        val request = Intent(Intent.ACTION_VIEW, Uri.parse("https://synthetic.invalid"))
        register(request, "synthetic.one")
        register(request, "synthetic.two")
        val chosen = launcher.startFirst(listOf(Intent.createChooser(request, "Choose"))) as IntentHandoffResult.Choose
        assertThat(chosen.targets).hasSize(2)
        assertThat(shadowOf(app).nextStartedActivity).isNull()
        assertThat(launcher.start(chosen.targets.first())).isInstanceOf(IntentHandoffResult.Locked::class.java)
        assertThat(shadowOf(app).nextStartedActivity).isNull()
    }

    @Test fun discoverFallbackOnlySubmitsToGuardAndUnwiredClientCannotLaunch() = runTest {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        val request = Intent(
            Intent.ACTION_MAIN,
        ).setComponent(ComponentName("synthetic.google", "synthetic.google.Main"))
        val requests = mutableListOf<List<Intent>>()
        val client =
            GoogleDiscoverClient(
                activity,
                GoogleDiscoverResolver {
                    DiscoverProvider.AppOnly(request)
                },
                backgroundScope,
                io = Dispatchers.Unconfined,
                externalIntents = ExternalIntentStarter {
                    requests += it
                    true
                },
                onProgress = {},
            )
        try {
            client.setEnabled(true)
            client.onStart(activity)
            runCurrent()
            assertThat(client.open()).isEqualTo(DiscoverOpenResult.OPENED_GOOGLE_APP)
            assertThat(requests.single().single().component).isEqualTo(request.component)
            assertThat(shadowOf(app).nextStartedActivity).isNull()
            val denied =
                GoogleDiscoverClient(
                    activity,
                    GoogleDiscoverResolver {
                        DiscoverProvider.AppOnly(request)
                    },
                    backgroundScope,
                    io = Dispatchers.Unconfined,
                    onProgress = {},
                )
            denied.setEnabled(true)
            denied.onStart(activity)
            runCurrent()
            assertThat(denied.openGoogleApp()).isFalse()
            assertThat(shadowOf(app).nextStartedActivity).isNull()
            denied.onDestroy(activity)
        } finally {
            client.onDestroy(activity)
            controller.pause().stop().destroy()
        }
    }

    @Test fun unreadableAndLoadingLocksBlockEveryExternalRoute() = runTest {
        val intents =
            listOf(
                Intent(Intent.ACTION_ASSIST),
                Intent(RecognizerIntent.ACTION_WEB_SEARCH),
                storeListingIntents("synthetic").first(),
            )
        for ((index, intent) in intents.withIndex()) register(intent, intent.`package` ?: "synthetic.handler$index")
        val lock = AppLockManager(InMemoryAppLockStore(failReads = true), backgroundScope) { 0L }
        val launcher = GuardedIntentLauncher(app, lock, Dispatchers.Unconfined)
        for (intent in intents) {
            assertThat(
                launcher.startFirst(listOf(intent)),
            ).isInstanceOf(IntentHandoffResult.Locked::class.java)
        }
        lock.awaitLoaded()
        for (intent in intents) {
            assertThat(
                launcher.startFirst(listOf(intent)),
            ).isInstanceOf(IntentHandoffResult.Locked::class.java)
        }
        assertThat(shadowOf(app).nextStartedActivity).isNull()
    }
}
